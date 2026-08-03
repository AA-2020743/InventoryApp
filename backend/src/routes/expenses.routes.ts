import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { dateOnlyKey, startOfDay, startOfMonth, startOfNextDay, startOfNextMonth } from "../utils/dates";
import { getCashRegisterBalance } from "./cashRegister.routes";

export const expensesRouter = Router();

const expenseInput = z.object({
  name: z.string().trim().min(1),
  amount: z.number().positive(),
  date: z.coerce.date().optional(),
  notes: z.string().trim().optional().nullable(),
});

// Always tries to pay an expense out of the till in full. Any prior entry
// linked to this expense is reversed first (so edits recompute cleanly
// instead of stacking), then as much of the amount as the register can
// cover is debited - never pushing the balance negative - and whatever's
// left over is returned so the caller can persist it as the deficit.
async function applyExpenseCashDeduction(
  tx: Prisma.TransactionClient,
  expenseId: string,
  amount: Prisma.Decimal,
  name: string
): Promise<Prisma.Decimal> {
  const existing = await tx.cashRegisterEntry.findFirst({ where: { expenseId } });
  if (existing) {
    await tx.cashRegisterEntry.delete({ where: { id: existing.id } });
  }

  const balance = Prisma.Decimal.max(await getCashRegisterBalance(tx), 0);
  const paid = Prisma.Decimal.min(balance, amount);
  if (paid.gt(0)) {
    await tx.cashRegisterEntry.create({
      data: { amount: paid.neg(), note: `Expense: ${name}`, expenseId },
    });
  }
  return amount.sub(paid);
}

expensesRouter.get(
  "/",
  asyncHandler(async (_req, res) => {
    const expenses = await prisma.expense.findMany({ orderBy: { date: "desc" } });
    res.json(expenses);
  })
);

// GET /api/expenses/for-range?period=day|month&date= - the expense side of
// profit for a specific calendar day or month, mirroring the dashboard
// summary's math for an arbitrary period instead of just "today"/"this
// month". Also surfaces the deficit (amount that couldn't be paid from the
// cash register) accumulated over that same period.
expensesRouter.get(
  "/for-range",
  asyncHandler(async (req, res) => {
    const period = req.query.period === "month" ? "month" : "day";
    const dateParam = typeof req.query.date === "string" ? new Date(req.query.date) : new Date();
    const from = period === "month" ? startOfMonth(dateParam) : startOfDay(dateParam);
    const to = period === "month" ? startOfNextMonth(dateParam) : startOfNextDay(dateParam);

    const items = await prisma.expense.findMany({
      where: { date: { gte: from, lt: to } },
      orderBy: { date: "desc" },
    });
    const total = items.reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
    const deficit = items.reduce((acc, e) => acc.add(e.deficitAmount), new Prisma.Decimal(0));

    res.json({
      period,
      date: dateOnlyKey(dateParam).toISOString().slice(0, 10),
      items,
      total,
      deficit,
    });
  })
);

expensesRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const data = expenseInput.parse(req.body);
    const expense = await prisma.$transaction(async (tx) => {
      const created = await tx.expense.create({ data });
      const deficit = await applyExpenseCashDeduction(tx, created.id, created.amount, created.name);
      return tx.expense.update({ where: { id: created.id }, data: { deficitAmount: deficit } });
    });
    res.status(201).json(expense);
  })
);

expensesRouter.put(
  "/:id",
  asyncHandler(async (req, res) => {
    const data = expenseInput.partial().parse(req.body);
    const expense = await prisma.$transaction(async (tx) => {
      const existing = await tx.expense.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Expense not found");
      const updated = await tx.expense.update({ where: { id: req.params.id }, data });
      const deficit = await applyExpenseCashDeduction(tx, updated.id, updated.amount, updated.name);
      return tx.expense.update({ where: { id: updated.id }, data: { deficitAmount: deficit } });
    });
    res.json(expense);
  })
);

expensesRouter.delete(
  "/:id",
  asyncHandler(async (req, res) => {
    await prisma.$transaction(async (tx) => {
      const existing = await tx.expense.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Expense not found");
      await tx.cashRegisterEntry.deleteMany({ where: { expenseId: existing.id } });
      await tx.expense.delete({ where: { id: req.params.id } });
    });
    res.status(204).send();
  })
);
