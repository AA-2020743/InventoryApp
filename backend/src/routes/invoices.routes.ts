import { Router } from "express";
import { z } from "zod";
import { prisma } from "../db";
import { env } from "../env";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { applyCashDeduction } from "./cashRegister.routes";

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

invoicesRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const data = invoiceInput.parse(req.body);
    const supplier = await prisma.supplier.findUnique({ where: { id: data.supplierId } });
    if (!supplier) throw new HttpError(404, "Supplier not found");

    const invoice = await prisma.supplierInvoice.create({ data });
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
      const updated = await tx.supplierInvoice.update({ where: { id: req.params.id }, data });
      if (updated.status !== "PAID") return updated;

      const deficit = await applyCashDeduction(
        tx,
        { invoiceId: updated.id },
        updated.amount,
        paymentNote(updated.invoiceNumber, existing.supplier.name)
      );
      return tx.supplierInvoice.update({ where: { id: updated.id }, data: { deficitAmount: deficit } });
    });
    res.json(invoice);
  })
);

// Always tries to pay the invoice out of the till in full - there's no
// "pay from cash register?" choice anymore, mirroring expenses. Any
// shortfall is recorded as a deficit rather than pushing the register
// negative.
invoicesRouter.post(
  "/:id/pay",
  asyncHandler(async (req, res) => {
    const existing = await prisma.supplierInvoice.findUnique({ where: { id: req.params.id }, include: { supplier: true } });
    if (!existing) throw new HttpError(404, "Invoice not found");
    if (existing.status === "PAID") throw new HttpError(400, "Invoice is already paid");

    const invoice = await prisma.$transaction(async (tx) => {
      const updated = await tx.supplierInvoice.update({
        where: { id: req.params.id },
        data: { status: "PAID", paidAt: new Date() },
      });
      const deficit = await applyCashDeduction(
        tx,
        { invoiceId: existing.id },
        existing.amount,
        paymentNote(existing.invoiceNumber, existing.supplier.name)
      );
      return tx.supplierInvoice.update({ where: { id: existing.id }, data: { deficitAmount: deficit } });
    });
    res.json(invoice);
  })
);

invoicesRouter.delete(
  "/:id",
  asyncHandler(async (req, res) => {
    await prisma.$transaction(async (tx) => {
      const existing = await tx.supplierInvoice.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Invoice not found");
      await tx.cashRegisterEntry.deleteMany({ where: { invoiceId: existing.id } });
      await tx.supplierInvoice.delete({ where: { id: req.params.id } });
    });
    res.status(204).send();
  })
);
