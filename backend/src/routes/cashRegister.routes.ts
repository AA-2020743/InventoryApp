import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";

export const cashRegisterRouter = Router();

// Accepts an optional transaction client so a caller that's already inside
// a $transaction (e.g. the expense cash-deduction logic) reads a balance
// consistent with writes made earlier in that same transaction, instead of
// racing against it via the global client.
export async function getCashRegisterBalance(
  client: Prisma.TransactionClient | typeof prisma = prisma
): Promise<Prisma.Decimal> {
  const entries = await client.cashRegisterEntry.findMany();
  return entries.reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
}

// Shared by expenses, supplier-invoice payments, non-invoiced restocks, and
// debts - the places that pay themselves out of the till immediately:
// reverses any prior entry linked via `link` (so an edit recomputes
// cleanly instead of stacking), then requires the register to actually
// cover the full `amount` - if it can't, the whole operation is rejected
// rather than partially paying and booking the rest as a shortfall, so the
// register can never be pushed negative and nothing gets recorded as paid
// unless the cash was really there.
export async function applyCashDeduction(
  tx: Prisma.TransactionClient,
  link: { expenseId: string } | { invoiceId: string } | { inventoryTransactionId: string } | { debtId: string },
  amount: Prisma.Decimal,
  note: string
): Promise<void> {
  const existing = await tx.cashRegisterEntry.findFirst({ where: link });
  if (existing) {
    await tx.cashRegisterEntry.delete({ where: { id: existing.id } });
  }

  const balance = Prisma.Decimal.max(await getCashRegisterBalance(tx), 0);
  if (amount.gt(balance)) {
    throw new HttpError(
      400,
      `Insufficient cash register balance: this needs ${amount.toFixed(2)} but only ${balance.toFixed(2)} is available.`
    );
  }
  if (amount.gt(0)) {
    await tx.cashRegisterEntry.create({ data: { amount: amount.neg(), note, ...link } });
  }
}

cashRegisterRouter.get(
  "/",
  asyncHandler(async (_req, res) => {
    const entries = await prisma.cashRegisterEntry.findMany({
      orderBy: { createdAt: "desc" },
      take: 100,
    });
    const balance = entries.reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
    // Balance above only covers the most recent 100 entries fetched for
    // display; recompute the true balance from every entry so it can't
    // silently drift once history exceeds that page size.
    const trueBalance = await getCashRegisterBalance();
    res.json({ balance: trueBalance, entries });
  })
);

const setInput = z.object({
  value: z.number(),
  note: z.string().trim().optional().nullable(),
});

// Reconciles the register to a physically-counted value. The delta is
// computed server-side (not trusted from the client) so two concurrent
// reconciliations can't race each other into an inconsistent balance.
cashRegisterRouter.post(
  "/set",
  asyncHandler(async (req, res) => {
    const { value, note } = setInput.parse(req.body);
    const entry = await prisma.$transaction(async (tx) => {
      const current = await tx.cashRegisterEntry.findMany();
      const currentBalance = current.reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
      const delta = new Prisma.Decimal(value).sub(currentBalance);
      return tx.cashRegisterEntry.create({
        data: { amount: delta, note: note ?? "Manual count adjustment" },
      });
    });
    const balance = await getCashRegisterBalance();
    res.status(201).json({ balance, entry });
  })
);

const entryInput = z.object({
  amount: z.number().refine((v) => v !== 0, "amount must not be zero"),
  note: z.string().trim().optional().nullable(),
});

// Generic manual cash in/out (positive or negative amount) - e.g. paying
// a non-invoice expense out of the till, or adding a cash top-up.
cashRegisterRouter.post(
  "/entries",
  asyncHandler(async (req, res) => {
    const { amount, note } = entryInput.parse(req.body);
    const entry = await prisma.cashRegisterEntry.create({ data: { amount, note } });
    const balance = await getCashRegisterBalance();
    res.status(201).json({ balance, entry });
  })
);
