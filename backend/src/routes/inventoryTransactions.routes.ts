import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { applyCashDeduction } from "./cashRegister.routes";
import { newInvoiceForRestockSchema, resolveRestockFinancing } from "./products.routes";
import { syncInvoiceTotalFromLines } from "../services/invoiceTotals";

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
    // A zero-unit restock is a revaluation, not a delivery - it brought no
    // goods and cost nothing, so there is no payment to move.
    if (existing.quantityChange.lte(0)) {
      throw new HttpError(400, "That entry is a cost correction, not a delivery, so it has nothing to pay for");
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
        // The invoice it just left is worth the sum of the lines it still
        // has, which is one line fewer than a moment ago.
        if (previousInvoiceId) await syncInvoiceTotalFromLines(tx, previousInvoiceId);
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
      const txn = await tx.inventoryTransaction.update({
        where: { id: existing.id },
        data: { supplierInvoiceId: resolved.supplierInvoiceId, deficitAmount: 0 },
      });

      // Both invoices have to be re-totalled: the one losing the line and
      // the one gaining it. Without this the stock could land on a settled
      // cash invoice - whose total and payment resize to its lines - while
      // the till kept the refund above, leaving the goods paid for by
      // nobody. On a settled invoice the sync charges the difference and is
      // refused outright, rolling the whole correction back, if the
      // register can't cover it.
      if (previousInvoiceId && previousInvoiceId !== resolved.supplierInvoiceId) {
        await syncInvoiceTotalFromLines(tx, previousInvoiceId);
      }
      if (resolved.supplierInvoiceId) {
        await syncInvoiceTotalFromLines(tx, resolved.supplierInvoiceId);
      }
      return txn;
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

// Undoes a spoilage recorded by mistake - stock written off that was
// actually sold, or simply the wrong product or quantity. Puts the units
// back on the shelf and removes the write-off expense the spoilage created,
// so the day's profit stops carrying a loss that never happened.
//
// Spoilage never touched the till (a write-off isn't a cash outflow), so
// there is no cash side to reverse here.
inventoryTransactionsRouter.post(
  "/:id/undo-spoilage",
  asyncHandler(async (req, res) => {
    const existing = await prisma.inventoryTransaction.findUnique({
      where: { id: req.params.id },
      include: { product: true },
    });
    if (!existing) throw new HttpError(404, "Stock movement not found");
    if (existing.type !== "SPOILAGE") throw new HttpError(400, "That movement is not a spoilage");

    // quantityChange is negative for a spoilage, so subtracting it puts the
    // units back.
    const restored = existing.quantityChange.neg();

    const result = await prisma.$transaction(async (tx) => {
      const product = await tx.product.update({
        where: { id: existing.productId },
        data: { quantity: { increment: restored } },
      });

      // Spoilages recorded before write-offs were linked carry no back
      // reference. Rather than guess which expense belongs to this one,
      // fall back to an exact name-and-amount match that is itself
      // unlinked, and only when exactly one such row exists - anything
      // ambiguous is left alone and reported, so no unrelated expense can
      // be deleted by a correction.
      const expenseName = `Spoiled: ${existing.product.name}`;
      const cost = (existing.unitCost ?? new Prisma.Decimal(0)).mul(restored);
      const candidates = await tx.expense.findMany({
        where: {
          OR: [
            { inventoryTransactionId: existing.id },
            { inventoryTransactionId: null, name: expenseName, amount: cost },
          ],
        },
      });
      const linked = candidates.filter((e) => e.inventoryTransactionId === existing.id);
      const toRemove = linked.length > 0 ? linked : candidates.length === 1 ? candidates : [];

      if (toRemove.length > 0) {
        const ids = toRemove.map((e) => e.id);
        await tx.cashRegisterEntry.deleteMany({ where: { expenseId: { in: ids } } });
        await tx.expense.deleteMany({ where: { id: { in: ids } } });
      }

      await tx.inventoryTransaction.delete({ where: { id: existing.id } });
      return { product, expensesRemoved: toRemove.length };
    });

    res.json({
      product: result.product,
      quantityRestored: restored,
      // False when the write-off couldn't be identified unambiguously (an
      // old unlinked spoilage), so the app can say the stock came back but
      // the expense still needs removing by hand.
      expenseReversed: result.expensesRemoved > 0,
    });
  })
);
