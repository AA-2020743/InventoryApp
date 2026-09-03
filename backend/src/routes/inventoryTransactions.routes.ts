import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { applyCashDeduction } from "./cashRegister.routes";
import { newInvoiceForRestockSchema, resolveRestockFinancing } from "./products.routes";

export const inventoryTransactionsRouter = Router();

const refinanceSchema = z.object({
  financing: z.enum(["CASH", "DEFERRED"]),
  supplierInvoiceId: z.string().optional(),
  newInvoice: newInvoiceForRestockSchema.optional(),
});

// Corrects how an already-recorded restock was paid for, when the wrong
// choice was made at entry time (cash when it was actually on a supplier
// invoice, or the reverse - or the right choice but the wrong invoice).
//
// The goods themselves are not in question: the stock arrived either way,
// so quantity and purchase cost are deliberately left alone. Only the
// money side is undone and re-applied:
//
//   -> CASH      unlink from the invoice, then deduct the cost from the
//                till. Subject to the same hard block as any other cash
//                outflow, so a correction that would overdraw the register
//                is refused and the whole thing rolls back untouched.
//   -> DEFERRED  delete the till entry this restock created (the cash goes
//                back into the register) and link the restock to a pending
//                invoice - an existing one, or one created here.
//
// Re-pointing an already-deferred restock at a different invoice is allowed
// too: same call, DEFERRED -> DEFERRED with another invoice id.
inventoryTransactionsRouter.post(
  "/:id/refinance",
  asyncHandler(async (req, res) => {
    const input = refinanceSchema.parse(req.body);

    const existing = await prisma.inventoryTransaction.findUnique({
      where: { id: req.params.id },
      include: { product: true, supplierInvoice: true },
    });
    if (!existing) throw new HttpError(404, "Stock movement not found");
    if (existing.type !== "RESTOCK") {
      throw new HttpError(400, "Only a restock has a cash-or-invoice side to correct");
    }

    const previousFinancing = existing.supplierInvoiceId ? "DEFERRED" : "CASH";
    const previousInvoiceId = existing.supplierInvoiceId;

    if (previousFinancing === "CASH" && input.financing === "CASH") {
      throw new HttpError(400, "This restock is already recorded as paid in cash");
    }

    // Unlinking from an invoice that has already been paid would charge the
    // till a second time for stock the invoice payment already covered.
    // The invoice has to be corrected first (mark it unpaid), so that's the
    // action to point at rather than silently double-charging.
    if (previousFinancing === "DEFERRED" && existing.supplierInvoice?.status === "PAID") {
      throw new HttpError(
        400,
        "That invoice is already paid, so its stock can't be moved off it. Correct the invoice first."
      );
    }

    const cost = (existing.unitCost ?? new Prisma.Decimal(0)).mul(existing.quantityChange);

    const updated = await prisma.$transaction(async (tx) => {
      if (input.financing === "CASH") {
        const txn = await tx.inventoryTransaction.update({
          where: { id: existing.id },
          data: { supplierInvoiceId: null, deficitAmount: 0 },
        });
        // Throws (rolling the correction back) if the till can't cover it.
        await applyCashDeduction(
          tx,
          { inventoryTransactionId: txn.id },
          cost,
          `Restock: ${existing.product.name}`
        );
        return txn;
      }

      // -> DEFERRED: give the till its money back first, then attach the
      // restock to the invoice that really covers it.
      await tx.cashRegisterEntry.deleteMany({ where: { inventoryTransactionId: existing.id } });
      const resolved = await resolveRestockFinancing(tx, input, cost);
      return tx.inventoryTransaction.update({
        where: { id: existing.id },
        data: { supplierInvoiceId: resolved.supplierInvoiceId, deficitAmount: 0 },
      });
    });

    // Moving the last restock off a pending invoice leaves that invoice
    // covering nothing. It isn't deleted here - it may still be a real debt
    // the owner owes - but it's reported so the app can offer to review it.
    let orphanedInvoiceId: string | null = null;
    if (previousInvoiceId && previousInvoiceId !== updated.supplierInvoiceId) {
      const remaining = await prisma.inventoryTransaction.count({
        where: { supplierInvoiceId: previousInvoiceId },
      });
      if (remaining === 0) orphanedInvoiceId = previousInvoiceId;
    }

    res.json({
      transaction: updated,
      previousFinancing,
      financing: input.financing,
      orphanedInvoiceId,
    });
  })
);
