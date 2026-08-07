import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { applyCashDeduction } from "./cashRegister.routes";

export const debtsRouter = Router();

// Repays a debt - full or partial. Each call adds its own cash-register
// entry (flagged isPartialDebtRepayment so the disbursement entry's own
// lookup by debtId never confuses the two), and the debt flips to REPAID
// once the running total reaches its full amount. Mirrors
// collectSaleAmount in sales.routes.ts.
async function repayDebtAmount(
  tx: Prisma.TransactionClient,
  debt: { id: string; workerName: string; amount: Prisma.Decimal; amountRepaid: Prisma.Decimal },
  amount: Prisma.Decimal
) {
  const remaining = debt.amount.sub(debt.amountRepaid);
  if (amount.lte(0) || amount.gt(remaining)) {
    throw new HttpError(400, `Amount must be between 0 and the remaining balance (${remaining.toString()})`);
  }

  const newAmountRepaid = debt.amountRepaid.add(amount);
  const isFullyRepaid = newAmountRepaid.gte(debt.amount);

  await tx.cashRegisterEntry.create({
    data: {
      amount,
      note: `Repayment received from ${debt.workerName}`,
      debtId: debt.id,
      isPartialDebtRepayment: true,
    },
  });

  return tx.debt.update({
    where: { id: debt.id },
    data: {
      amountRepaid: newAmountRepaid,
      ...(isFullyRepaid ? { status: "REPAID" as const, repaidAt: new Date() } : {}),
    },
  });
}

// GET /api/debts?status=OUTSTANDING|REPAID
debtsRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const status = req.query.status === "OUTSTANDING" || req.query.status === "REPAID" ? req.query.status : undefined;
    const debts = await prisma.debt.findMany({
      where: status ? { status } : undefined,
      orderBy: { createdAt: "desc" },
    });
    res.json(debts);
  })
);

// Distinct worker names already used on a debt, so the app can suggest
// them while recording a new one instead of the owner having to remember/
// retype exact spelling - same convention as Sale.customerName. Registered
// before /:id so "workers" isn't swallowed as an id param.
debtsRouter.get(
  "/workers",
  asyncHandler(async (_req, res) => {
    const rows = await prisma.debt.findMany({
      distinct: ["workerName"],
      select: { workerName: true },
      orderBy: { workerName: "asc" },
    });
    res.json(rows.map((r) => r.workerName).filter((n) => n.trim() !== ""));
  })
);

const debtInput = z.object({
  workerName: z.string().trim().min(1),
  amount: z.number().positive(),
  notes: z.string().trim().optional().nullable(),
});

// Always tries to disburse the full amount from the till immediately, same
// always-deduct + deficit-on-shortfall mechanism as an expense - the
// receivable is booked at full face value regardless (see dashboard.routes.ts
// debtReceivableTotal), so netValuation doesn't move either way.
debtsRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const { workerName, amount, notes } = debtInput.parse(req.body);
    const amountDecimal = new Prisma.Decimal(amount);

    const debt = await prisma.$transaction(async (tx) => {
      const created = await tx.debt.create({
        data: { workerName, amount: amountDecimal, notes: notes ?? null },
      });
      const deficit = await applyCashDeduction(
        tx,
        { debtId: created.id },
        amountDecimal,
        `Lent to ${workerName}`
      );
      return tx.debt.update({ where: { id: created.id }, data: { deficitAmount: deficit } });
    });

    res.status(201).json(debt);
  })
);

const repayPartialInput = z.object({ amount: z.number().positive() });

debtsRouter.post(
  "/:id/repay-partial",
  asyncHandler(async (req, res) => {
    const { amount } = repayPartialInput.parse(req.body);
    const debt = await prisma.$transaction(async (tx) => {
      const existing = await tx.debt.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Debt not found");
      return repayDebtAmount(tx, existing, new Prisma.Decimal(amount));
    });
    res.json(debt);
  })
);

// Repays whatever remains in full.
debtsRouter.post(
  "/:id/repay",
  asyncHandler(async (req, res) => {
    const debt = await prisma.$transaction(async (tx) => {
      const existing = await tx.debt.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Debt not found");
      const remaining = existing.amount.sub(existing.amountRepaid);
      if (remaining.lte(0)) throw new HttpError(400, "Debt is already fully repaid");
      return repayDebtAmount(tx, existing, remaining);
    });
    res.json(debt);
  })
);

debtsRouter.delete(
  "/:id",
  asyncHandler(async (req, res) => {
    await prisma.$transaction(async (tx) => {
      const existing = await tx.debt.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Debt not found");
      // Reverses both the original disbursement entry and any repayment
      // entries, so deleting a debt undoes every cash-register effect it
      // ever had, not just the initial one.
      await tx.cashRegisterEntry.deleteMany({ where: { debtId: existing.id } });
      await tx.debt.delete({ where: { id: req.params.id } });
    });
    res.status(204).send();
  })
);
