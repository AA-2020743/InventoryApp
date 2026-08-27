import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { dateOnlyKey, startOfDay, startOfMonth, startOfNextDay, startOfNextMonth } from "../utils/dates";
import { totalsByCategory } from "../utils/categoryTotals";

export const otherSalesRouter = Router();

const otherSaleInput = z.object({
  amount: z.number().positive(),
  category: z.string().trim().optional().nullable(),
  notes: z.string().trim().optional().nullable(),
  date: z.coerce.date().optional(),
});

// Keeps the one cash-register entry linked to an OtherSale in sync with
// its current amount - the mirror image of an expense's always-deduct
// entry, except there's no deficit concept: receiving money can't come up
// short, so this always credits the full amount.
async function syncOtherSaleCashEntry(
  tx: Prisma.TransactionClient,
  otherSaleId: string,
  amount: Prisma.Decimal,
  category: string | null
): Promise<void> {
  const existing = await tx.cashRegisterEntry.findFirst({ where: { otherSaleId } });
  const note = category ? `Other income: ${category}` : "Other income";
  if (existing) {
    if (!existing.amount.equals(amount)) {
      await tx.cashRegisterEntry.update({ where: { id: existing.id }, data: { amount, note } });
    }
  } else {
    await tx.cashRegisterEntry.create({ data: { amount, note, otherSaleId } });
  }
}

otherSalesRouter.get(
  "/",
  asyncHandler(async (_req, res) => {
    const entries = await prisma.otherSale.findMany({ orderBy: { date: "desc" } });
    res.json(entries);
  })
);

// Distinct categories already in use, so the app can suggest them while
// adding an entry instead of the owner having to remember/retype exact
// spelling - typing a new one just creates it implicitly.
otherSalesRouter.get(
  "/categories",
  asyncHandler(async (_req, res) => {
    const rows = await prisma.otherSale.findMany({
      where: { category: { not: null } },
      distinct: ["category"],
      select: { category: true },
      orderBy: { category: "asc" },
    });
    res.json(rows.map((r) => r.category).filter((c): c is string => c !== null));
  })
);

// GET /api/other-sales/for-range?period=day|month&date= - mirrors the
// expenses endpoint of the same shape, for the Stats screen.
otherSalesRouter.get(
  "/for-range",
  asyncHandler(async (req, res) => {
    const period = req.query.period === "month" ? "month" : "day";
    const dateParam = typeof req.query.date === "string" ? new Date(req.query.date) : new Date();
    const from = period === "month" ? startOfMonth(dateParam) : startOfDay(dateParam);
    const to = period === "month" ? startOfNextMonth(dateParam) : startOfNextDay(dateParam);

    const items = await prisma.otherSale.findMany({
      where: { date: { gte: from, lt: to } },
      orderBy: { date: "desc" },
    });
    const total = items.reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));

    res.json({
      period,
      date: dateOnlyKey(dateParam).toISOString().slice(0, 10),
      items,
      total,
      byCategory: totalsByCategory(items),
    });
  })
);

otherSalesRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const data = otherSaleInput.parse(req.body);
    const entry = await prisma.$transaction(async (tx) => {
      const created = await tx.otherSale.create({ data });
      await syncOtherSaleCashEntry(tx, created.id, created.amount, created.category);
      return created;
    });
    res.status(201).json(entry);
  })
);

otherSalesRouter.put(
  "/:id",
  asyncHandler(async (req, res) => {
    const data = otherSaleInput.partial().parse(req.body);
    const entry = await prisma.$transaction(async (tx) => {
      const existing = await tx.otherSale.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Entry not found");
      const updated = await tx.otherSale.update({ where: { id: req.params.id }, data });
      await syncOtherSaleCashEntry(tx, updated.id, updated.amount, updated.category);
      return updated;
    });
    res.json(entry);
  })
);

otherSalesRouter.delete(
  "/:id",
  asyncHandler(async (req, res) => {
    await prisma.$transaction(async (tx) => {
      const existing = await tx.otherSale.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Entry not found");
      await tx.cashRegisterEntry.deleteMany({ where: { otherSaleId: existing.id } });
      await tx.otherSale.delete({ where: { id: req.params.id } });
    });
    res.status(204).send();
  })
);
