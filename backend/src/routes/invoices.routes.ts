import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { env } from "../env";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { applyCashDeduction } from "./cashRegister.routes";
import { applyRestockToProduct } from "./products.routes";
import { syncInvoiceTotalFromLines } from "../services/invoiceTotals";
import { recomputeProductCost } from "../services/productCost";

export const invoicesRouter = Router();

const invoiceInput = z.object({
  supplierId: z.string().uuid(),
  invoiceNumber: z.string().trim().optional().nullable(),
  amount: z.number().positive(),
  issueDate: z.coerce.date().optional(),
  dueDate: z.coerce.date(),
  notes: z.string().trim().optional().nullable(),
  imageUrl: z.string().trim().optional().nullable(),
});

function paymentNote(invoiceNumber: string | null, supplierName: string): string {
  return `Paid invoice${invoiceNumber ? ` #${invoiceNumber}` : ""} — ${supplierName}`;
}

// GET /api/invoices?status=PENDING&upcomingDays=7
invoicesRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const status = typeof req.query.status === "string" ? req.query.status : undefined;
    const upcomingDays = req.query.upcomingDays ? Number(req.query.upcomingDays) : undefined;

    const where: Record<string, unknown> = {};
    if (status === "PENDING" || status === "PAID") where.status = status;
    if (upcomingDays !== undefined) {
      const now = new Date();
      const until = new Date(now.getTime() + upcomingDays * 24 * 60 * 60 * 1000);
      where.dueDate = { lte: until };
      where.status = "PENDING";
    }

    const invoices = await prisma.supplierInvoice.findMany({
      where,
      include: { supplier: true },
      orderBy: { dueDate: "asc" },
    });
    res.json(invoices);
  })
);

invoicesRouter.get(
  "/upcoming",
  asyncHandler(async (req, res) => {
    const days = req.query.days ? Number(req.query.days) : env.invoiceReminderDays;
    const now = new Date();
    const until = new Date(now.getTime() + days * 24 * 60 * 60 * 1000);

    const invoices = await prisma.supplierInvoice.findMany({
      where: { status: "PENDING", dueDate: { lte: until } },
      include: { supplier: true },
      orderBy: { dueDate: "asc" },
    });

    const overdue = invoices.filter((i) => i.dueDate < now);
    const dueSoon = invoices.filter((i) => i.dueDate >= now);

    res.json({ overdue, dueSoon });
  })
);

invoicesRouter.get(
  "/:id",
  asyncHandler(async (req, res) => {
    const invoice = await prisma.supplierInvoice.findUnique({
      where: { id: req.params.id },
      include: { supplier: true, inventoryTransactions: true },
    });
    if (!invoice) throw new HttpError(404, "Invoice not found");
    res.json(invoice);
  })
);

// The stock this invoice actually paid for: every RESTOCK linked to it,
// with the product it added to and what that line cost. `linkedTotal` is
// the sum of those lines, which the app compares against the invoice's own
// `amount` so a mismatch (stock booked but not all of it accounted for on
// the invoice, or vice versa) is visible rather than silent.
invoicesRouter.get(
  "/:id/inventory",
  asyncHandler(async (req, res) => {
    const invoice = await prisma.supplierInvoice.findUnique({ where: { id: req.params.id } });
    if (!invoice) throw new HttpError(404, "Invoice not found");

    const items = await prisma.inventoryTransaction.findMany({
      where: { supplierInvoiceId: invoice.id },
      include: { product: true },
      orderBy: { createdAt: "asc" },
    });

    const linkedTotal = items.reduce(
      (acc, t) => acc.add((t.unitCost ?? new Prisma.Decimal(0)).mul(t.quantityChange)),
      new Prisma.Decimal(0)
    );

    res.json({ invoiceId: invoice.id, invoiceAmount: invoice.amount, items, linkedTotal });
  })
);

const linkedStockInput = z.object({
  quantity: z.number().positive(),
  unitCost: z.number().nonnegative().optional(),
});

// Loads a restock that is actually booked against this invoice, refusing
// anything that belongs elsewhere so an id from another invoice can't be
// edited through this one.
async function requireLinkedRestock(invoiceId: string, transactionId: string) {
  const invoice = await prisma.supplierInvoice.findUnique({ where: { id: invoiceId } });
  if (!invoice) throw new HttpError(404, "Invoice not found");

  const movement = await prisma.inventoryTransaction.findUnique({
    where: { id: transactionId },
    include: { product: true },
  });
  if (!movement || movement.supplierInvoiceId !== invoiceId) {
    throw new HttpError(404, "That stock line is not on this invoice");
  }
  if (movement.type !== "RESTOCK") {
    throw new HttpError(400, "Only a restock line can be edited here");
  }
  return { invoice, movement };
}

// Changes how much stock a line on this invoice brought in. The stock the
// invoice covers is the invoice's own record, so correcting the line
// corrects the inventory with it - up or down.
//
// The money follows only if the invoice has already been settled. On a
// still-pending invoice nothing has left the till yet, so a line change
// moves no cash. On a settled one - which is what a cash purchase is - the
// total is recomputed and its till entry resized to match: shrinking a line
// hands money back, growing one takes more.
invoicesRouter.put(
  "/:id/inventory/:transactionId",
  asyncHandler(async (req, res) => {
    const input = linkedStockInput.parse(req.body);
    const { movement } = await requireLinkedRestock(req.params.id, req.params.transactionId);

    const newQuantity = new Prisma.Decimal(input.quantity);
    const delta = newQuantity.sub(movement.quantityChange);
    const resultingStock = movement.product.quantity.add(delta);
    if (resultingStock.lt(0)) {
      throw new HttpError(
        400,
        `Reducing this line that far would leave ${movement.product.name} at negative stock - some of it has already been sold.`
      );
    }

    const updated = await prisma.$transaction(async (tx) => {
      await tx.product.update({
        where: { id: movement.productId },
        data: { quantity: { increment: delta } },
      });
      const line = await tx.inventoryTransaction.update({
        where: { id: movement.id },
        data: {
          quantityChange: newQuantity,
          ...(input.unitCost !== undefined ? { unitCost: new Prisma.Decimal(input.unitCost) } : {}),
        },
      });
      // The line just changed how much of this delivery there was, or what
      // it cost, so the product's blended cost is now built on a figure
      // that no longer exists - rebuild it from what the log now says.
      await recomputeProductCost(tx, movement.productId);
      await syncInvoiceTotalFromLines(tx, req.params.id);
      return line;
    });

    res.json(updated);
  })
);

// Removes a stock line entirely, taking its units back out of inventory and
// off the invoice's total. Same money rule as the edit above: a settled
// invoice hands the line's value back, a pending one has nothing to return.
invoicesRouter.delete(
  "/:id/inventory/:transactionId",
  asyncHandler(async (req, res) => {
    const { movement } = await requireLinkedRestock(req.params.id, req.params.transactionId);

    const resultingStock = movement.product.quantity.sub(movement.quantityChange);
    if (resultingStock.lt(0)) {
      throw new HttpError(
        400,
        `Removing this line would leave ${movement.product.name} at negative stock - some of it has already been sold.`
      );
    }

    await prisma.$transaction(async (tx) => {
      await tx.product.update({
        where: { id: movement.productId },
        data: { quantity: { decrement: movement.quantityChange } },
      });
      await tx.inventoryTransaction.delete({ where: { id: movement.id } });
      // The delivery is gone from the log, so the price it contributed to
      // the product's blended cost has to go with it.
      await recomputeProductCost(tx, movement.productId);
      await syncInvoiceTotalFromLines(tx, req.params.id);
    });

    res.status(204).send();
  })
);

const invoiceCreateInput = invoiceInput.extend({
  // Optional so an invoice can still be recorded on its own; when lines are
  // given they define both the stock and the invoice's total.
  amount: z.number().positive().optional(),
  // CASH settles the invoice immediately out of the till - that's what a
  // cash purchase is here. DEFERRED leaves it pending until it's paid.
  paymentMethod: z.enum(["CASH", "DEFERRED"]).optional().default("DEFERRED"),
  lines: z
    .array(
      z.object({
        productId: z.string(),
        quantity: z.number().positive(),
        unitCost: z.number().nonnegative(),
      })
    )
    .optional(),
});

// Creating a purchase: the invoice is the document, and the stock it brought
// in are its lines. The total is the sum of those lines rather than a figure
// typed separately, so the paperwork can't disagree with the goods.
//
// paymentMethod decides where the money comes from: CASH settles it against
// the till right away (refused, with nothing recorded, if the register can't
// cover it), DEFERRED leaves it owed until it's marked paid.
invoicesRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const { lines, paymentMethod, amount, ...invoiceFields } = invoiceCreateInput.parse(req.body);

    const supplier = await prisma.supplier.findUnique({ where: { id: invoiceFields.supplierId } });
    if (!supplier) throw new HttpError(404, "Supplier not found");

    const hasLines = (lines?.length ?? 0) > 0;
    if (!hasLines && amount === undefined) {
      throw new HttpError(400, "An invoice needs either its stock lines or an amount");
    }

    const products = hasLines
      ? await prisma.product.findMany({ where: { id: { in: lines!.map((l) => l.productId) } } })
      : [];
    if (hasLines && products.length !== new Set(lines!.map((l) => l.productId)).size) {
      throw new HttpError(404, "One of the products on this invoice no longer exists");
    }

    const linesTotal = (lines ?? []).reduce(
      (acc, l) => acc.add(new Prisma.Decimal(l.unitCost).mul(l.quantity)),
      new Prisma.Decimal(0)
    );

    const invoice = await prisma.$transaction(async (tx) => {
      const created = await tx.supplierInvoice.create({
        data: {
          ...invoiceFields,
          amount: hasLines ? linesTotal : new Prisma.Decimal(amount!),
          amountFromLines: hasLines,
          ...(paymentMethod === "CASH" ? { status: "PAID" as const, paidAt: new Date() } : {}),
        },
      });

      for (const line of lines ?? []) {
        const product = products.find((p) => p.id === line.productId)!;
        const effectiveUnitCost = await applyRestockToProduct(tx, product, line.quantity, line.unitCost);
        await tx.inventoryTransaction.create({
          data: {
            productId: product.id,
            type: "RESTOCK",
            quantityChange: line.quantity,
            unitCost: effectiveUnitCost,
            supplierInvoiceId: created.id,
          },
        });
      }

      // Paid up front means the till covers it now; the same hard block as
      // any other outflow applies, and failing it rolls back the whole
      // invoice - no stock, no document, no half-recorded purchase.
      if (paymentMethod === "CASH") {
        await applyCashDeduction(
          tx,
          { invoiceId: created.id },
          created.amount,
          paymentNote(created.invoiceNumber, supplier.name)
        );
      }

      return created;
    });

    res.status(201).json(invoice);
  })
);

// Editing an already-paid invoice's amount recomputes its cash deduction
// and deficit the same way an expense edit does - a pending invoice hasn't
// touched the register yet, so no deduction runs for it here.
invoicesRouter.put(
  "/:id",
  asyncHandler(async (req, res) => {
    const data = invoiceInput.partial().parse(req.body);
    const invoice = await prisma.$transaction(async (tx) => {
      const existing = await tx.supplierInvoice.findUnique({ where: { id: req.params.id }, include: { supplier: true } });
      if (!existing) throw new HttpError(404, "Invoice not found");
      // A line-managed invoice is worth what its stock came to. Letting an
      // edit type a different figure over that would put the paperwork and
      // the goods back out of step, which is the whole thing this flow
      // exists to prevent - the way to change it is to change the lines.
      if (
        existing.amountFromLines &&
        data.amount !== undefined &&
        !existing.amount.equals(new Prisma.Decimal(data.amount))
      ) {
        throw new HttpError(400, "This invoice's total comes from the stock booked to it - change the stock instead");
      }
      const updated = await tx.supplierInvoice.update({ where: { id: req.params.id }, data });
      if (updated.status !== "PAID") return updated;

      await applyCashDeduction(
        tx,
        { invoiceId: updated.id },
        updated.amount,
        paymentNote(updated.invoiceNumber, existing.supplier.name)
      );
      // Reached only if applyCashDeduction succeeded (fully paid), so any
      // deficit this invoice carried from before this edit no longer
      // applies - clear it rather than leaving a stale value behind.
      if (updated.deficitAmount.gt(0)) {
        return tx.supplierInvoice.update({ where: { id: updated.id }, data: { deficitAmount: 0 } });
      }
      return updated;
    });
    res.json(invoice);
  })
);

// Pays the invoice out of the till in full - there's no "pay from cash
// register?" choice anymore, mirroring expenses. Rejected outright (before
// the invoice is touched) if the register can't cover it.
invoicesRouter.post(
  "/:id/pay",
  asyncHandler(async (req, res) => {
    const existing = await prisma.supplierInvoice.findUnique({ where: { id: req.params.id }, include: { supplier: true } });
    if (!existing) throw new HttpError(404, "Invoice not found");
    if (existing.status === "PAID") throw new HttpError(400, "Invoice is already paid");

    const invoice = await prisma.$transaction(async (tx) => {
      await applyCashDeduction(
        tx,
        { invoiceId: existing.id },
        existing.amount,
        paymentNote(existing.invoiceNumber, existing.supplier.name)
      );
      return tx.supplierInvoice.update({
        where: { id: req.params.id },
        data: { status: "PAID", paidAt: new Date() },
      });
    });
    res.json(invoice);
  })
);

// Undoes a payment made by mistake: puts the money back in the till and
// returns the invoice to PENDING. The stock the invoice covers is left
// linked to it - that side wasn't wrong, only the payment was.
//
// This is also what unblocks moving stock off an invoice: a restock can't
// be re-financed away from an invoice that's already paid (it would charge
// the till twice), so the invoice gets unpaid here first.
invoicesRouter.post(
  "/:id/unpay",
  asyncHandler(async (req, res) => {
    const existing = await prisma.supplierInvoice.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Invoice not found");
    if (existing.status !== "PAID") throw new HttpError(400, "Invoice is not marked paid");

    const invoice = await prisma.$transaction(async (tx) => {
      await tx.cashRegisterEntry.deleteMany({ where: { invoiceId: existing.id } });
      return tx.supplierInvoice.update({
        where: { id: existing.id },
        // deficitAmount described a shortfall at the moment of payment;
        // with the payment undone there is no shortfall to carry.
        data: { status: "PENDING", paidAt: null, deficitAmount: 0 },
      });
    });
    res.json(invoice);
  })
);

// Deleting an invoice takes the stock it brought in with it. The invoice is
// the only way that stock entered, so an invoice that shouldn't exist means
// its deliveries shouldn't either - leaving them behind would inflate the
// inventory with goods no record accounts for.
//
// Removing that stock credits nothing to the till: it was never paid for
// from there, so there is no purchase value to hand back. (The invoice's
// own payment entry is still removed, as before - if the invoice was paid,
// that money did leave the till and returns with the invoice.)
invoicesRouter.delete(
  "/:id",
  asyncHandler(async (req, res) => {
    const existing = await prisma.supplierInvoice.findUnique({
      where: { id: req.params.id },
      include: { inventoryTransactions: { include: { product: true } } },
    });
    if (!existing) throw new HttpError(404, "Invoice not found");

    // Several lines can point at the same product, so the check has to be
    // against each product's total across the invoice, not line by line.
    const removalByProduct = new Map<string, { name: string; onHand: Prisma.Decimal; removing: Prisma.Decimal }>();
    for (const line of existing.inventoryTransactions) {
      const entry = removalByProduct.get(line.productId) ?? {
        name: line.product.name,
        onHand: line.product.quantity,
        removing: new Prisma.Decimal(0),
      };
      entry.removing = entry.removing.add(line.quantityChange);
      removalByProduct.set(line.productId, entry);
    }
    for (const { name, onHand, removing } of removalByProduct.values()) {
      if (removing.gt(onHand)) {
        throw new HttpError(
          400,
          `Deleting this invoice would leave ${name} at negative stock - some of what it delivered has already been sold.`
        );
      }
    }

    await prisma.$transaction(async (tx) => {
      for (const line of existing.inventoryTransactions) {
        await tx.product.update({
          where: { id: line.productId },
          data: { quantity: { decrement: line.quantityChange } },
        });
      }
      const lineIds = existing.inventoryTransactions.map((line) => line.id);
      if (lineIds.length > 0) {
        // Defensive: a line on an invoice shouldn't carry a till entry, but
        // one re-financed to cash and back could have left one behind.
        await tx.cashRegisterEntry.deleteMany({ where: { inventoryTransactionId: { in: lineIds } } });
        await tx.inventoryTransaction.deleteMany({ where: { id: { in: lineIds } } });
        // Every delivery this invoice made is now out of the log, so each
        // product it touched has to have its blended cost rebuilt without
        // them. Once per product, however many lines it had.
        for (const productId of removalByProduct.keys()) {
          await recomputeProductCost(tx, productId);
        }
      }
      await tx.cashRegisterEntry.deleteMany({ where: { invoiceId: existing.id } });
      await tx.supplierInvoice.delete({ where: { id: existing.id } });
    });

    res.status(204).send();
  })
);
