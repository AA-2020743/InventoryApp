import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { dateOnlyKey, startOfDay, startOfNextDay } from "../utils/dates";
import { dailyOnlyRate, monthlyDueOnDate, oneTimeAmountInRange } from "../services/expenseCalc";

export const expensesRouter = Router();

const expenseInput = z.object({
  name: z.string().trim().min(1),
  amount: z.number().positive(),
  frequency: z.enum(["DAILY", "MONTHLY", "ONE_TIME"]),
  startDate: z.coerce.date().optional(),
  endDate: z.coerce.date().optional().nullable(),
  active: z.boolean().optional(),
  notes: z.string().trim().optional().nullable(),
  paymentDayOfMonth: z.number().int().min(1).max(31).optional().nullable(),
  // Asked on every create/edit regardless of frequency: whether this
  // expense's cash actually left the register. Mirrors syncSaleCashEntry's
  // pattern - see syncExpenseCashEntry below.
  fromCashRegister: z.boolean().optional().default(false),
});

// Keeps the one CashRegisterEntry linked to this expense (if any) in sync
// with whether it's marked as paid from the register and its current
// amount, rather than creating a fresh entry on every edit. Debits the
// register (negative amount) since paying an expense is cash going out.
async function syncExpenseCashEntry(
  tx: Prisma.TransactionClient,
  expenseId: string,
  fromCashRegister: boolean,
  amount: Prisma.Decimal,
  name: string
): Promise<void> {
  const existing = await tx.cashRegisterEntry.findFirst({ where: { expenseId } });
  const debit = amount.neg();
  if (fromCashRegister) {
    if (existing) {
      if (!existing.amount.equals(debit)) {
        await tx.cashRegisterEntry.update({ where: { id: existing.id }, data: { amount: debit } });
      }
    } else {
      await tx.cashRegisterEntry.create({ data: { amount: debit, note: `Expense: ${name}`, expenseId } });
    }
  } else if (existing) {
    await tx.cashRegisterEntry.delete({ where: { id: existing.id } });
  }
}

// fromCashRegister isn't a stored column - it's derived by checking whether
// a CashRegisterEntry currently links to each expense, so the client can
// show an accurate checkbox state without a separate round trip.
async function attachFromCashRegister<T extends { id: string }>(
  expenses: T[]
): Promise<(T & { fromCashRegister: boolean })[]> {
  if (expenses.length === 0) return [];
  const entries = await prisma.cashRegisterEntry.findMany({
    where: { expenseId: { in: expenses.map((e) => e.id) } },
    select: { expenseId: true },
  });
  const linked = new Set(entries.map((e) => e.expenseId));
  return expenses.map((e) => ({ ...e, fromCashRegister: linked.has(e.id) }));
}

expensesRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const activeOnly = req.query.activeOnly === "true";
    const expenses = await prisma.expense.findMany({
      where: activeOnly ? { active: true } : undefined,
      orderBy: { createdAt: "desc" },
    });
    res.json(await attachFromCashRegister(expenses));
  })
);

// GET /api/expenses/for-day?date= - what a specific calendar day's expense
// side of profit is made of: any ONE_TIME expenses dated that day, plus the
// DAILY (if it was a working day) and MONTHLY-due share, mirroring the
// dashboard summary's math for an arbitrary day instead of just "today".
expensesRouter.get(
  "/for-day",
  asyncHandler(async (req, res) => {
    const dateParam = typeof req.query.date === "string" ? new Date(req.query.date) : new Date();
    const dayStart = startOfDay(dateParam);
    const dayEnd = startOfNextDay(dateParam);
    const dayKey = dateOnlyKey(dateParam);

    const [activeExpenses, workingDayRecord] = await Promise.all([
      prisma.expense.findMany({ where: { active: true } }),
      prisma.workingDay.findUnique({ where: { date: dayKey } }),
    ]);
    const isWorking = workingDayRecord?.isWorking ?? true;

    const oneTime = activeExpenses.filter(
      (e) => e.frequency === "ONE_TIME" && e.startDate >= dayStart && e.startDate < dayEnd
    );
    const dailyShare = isWorking ? dailyOnlyRate(activeExpenses) : new Prisma.Decimal(0);
    const monthlyShare = monthlyDueOnDate(activeExpenses, dayStart);
    const oneTimeTotal = oneTimeAmountInRange(activeExpenses, dayStart, dayEnd);

    res.json({
      date: dayKey.toISOString().slice(0, 10),
      oneTime: await attachFromCashRegister(oneTime),
      dailyShare,
      monthlyShare,
      total: dailyShare.add(monthlyShare).add(oneTimeTotal),
    });
  })
);

expensesRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const { fromCashRegister, ...data } = expenseInput.parse(req.body);
    const expense = await prisma.$transaction(async (tx) => {
      const created = await tx.expense.create({ data });
      await syncExpenseCashEntry(tx, created.id, fromCashRegister, created.amount, created.name);
      return created;
    });
    res.status(201).json({ ...expense, fromCashRegister });
  })
);

expensesRouter.put(
  "/:id",
  asyncHandler(async (req, res) => {
    const { fromCashRegister, ...data } = expenseInput.partial().parse(req.body);
    const result = await prisma.$transaction(async (tx) => {
      const existing = await tx.expense.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Expense not found");
      const updated = await tx.expense.update({ where: { id: req.params.id }, data });
      // Omitted (undefined) on a partial edit means "leave as-is" - inferred
      // from whether a cash entry already exists for this expense, rather
      // than defaulting to false and silently reversing a prior payment.
      const effectiveFromCashRegister =
        fromCashRegister !== undefined
          ? fromCashRegister
          : (await tx.cashRegisterEntry.findFirst({ where: { expenseId: updated.id } })) !== null;
      await syncExpenseCashEntry(tx, updated.id, effectiveFromCashRegister, updated.amount, updated.name);
      return { expense: updated, fromCashRegister: effectiveFromCashRegister };
    });
    res.json({ ...result.expense, fromCashRegister: result.fromCashRegister });
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
