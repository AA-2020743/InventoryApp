import { Router } from "express";
import { z } from "zod";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";

export const expensesRouter = Router();

const expenseInput = z.object({
  name: z.string().trim().min(1),
  amount: z.number().positive(),
  frequency: z.enum(["DAILY", "MONTHLY", "ONE_TIME"]),
  startDate: z.coerce.date().optional(),
  endDate: z.coerce.date().optional().nullable(),
  active: z.boolean().optional(),
  notes: z.string().trim().optional().nullable(),
});

expensesRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const activeOnly = req.query.activeOnly === "true";
    const expenses = await prisma.expense.findMany({
      where: activeOnly ? { active: true } : undefined,
      orderBy: { createdAt: "desc" },
    });
    res.json(expenses);
  })
);

expensesRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const data = expenseInput.parse(req.body);
    const expense = await prisma.expense.create({ data });
    res.status(201).json(expense);
  })
);

expensesRouter.put(
  "/:id",
  asyncHandler(async (req, res) => {
    const data = expenseInput.partial().parse(req.body);
    const existing = await prisma.expense.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Expense not found");
    const expense = await prisma.expense.update({ where: { id: req.params.id }, data });
    res.json(expense);
  })
);

expensesRouter.delete(
  "/:id",
  asyncHandler(async (req, res) => {
    const existing = await prisma.expense.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Expense not found");
    await prisma.expense.delete({ where: { id: req.params.id } });
    res.status(204).send();
  })
);
