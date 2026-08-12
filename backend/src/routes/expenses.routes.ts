import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { dateOnlyKey, startOfDay, startOfMonth, startOfNextDay, startOfNextMonth } from "../utils/dates";
import { applyCashDeduction } from "./cashRegister.routes";

export const expensesRouter = Router();

const expenseInput = z.object({
  name: z.string().trim().min(1),
  amount: z.number().positive(),
  date: z.coerce.date().optional(),
  notes: z.string().trim().optional().nullable(),
});

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
      await applyCashDeduction(tx, { expenseId: created.id }, created.amount, `Expense: ${created.name}`);
      return created;
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
      await applyCashDeduction(tx, { expenseId: updated.id }, updated.amount, `Expense: ${updated.name}`);
      // Reached only if applyCashDeduction succeeded (fully paid), so any
      // deficit this expense carried from before this edit no longer
      // applies - clear it rather than leaving a stale value behind.
      if (updated.deficitAmount.gt(0)) {
        return tx.expense.update({ where: { id: updated.id }, data: { deficitAmount: 0 } });
      }
      return updated;
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
