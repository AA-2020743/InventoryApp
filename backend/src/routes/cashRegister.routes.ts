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

// The ledger. Without a month it returns the most recent entries, capped,
// because the register accumulates faster than anything else in the app.
// With ?month=YYYY-MM it returns that month in full instead - what the
// app's history view asks for when a folded month is opened, so an old
// month is never shown as a truncated fragment of itself.
const monthQuery = /^(\d{4})-(\d{2})$/;

function monthRange(month: string): { gte: Date; lt: Date } | null {
  const match = monthQuery.exec(month);
  if (!match) return null;
  const year = Number(match[1]);
  const monthIndex = Number(match[2]) - 1;
  if (monthIndex < 0 || monthIndex > 11) return null;
  return { gte: new Date(Date.UTC(year, monthIndex, 1)), lt: new Date(Date.UTC(year, monthIndex + 1, 1)) };
}

cashRegisterRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const month = typeof req.query.month === "string" ? req.query.month : undefined;
    const range = month ? monthRange(month) : null;
    if (month && !range) throw new HttpError(400, "month must look like YYYY-MM");

    const entries = await prisma.cashRegisterEntry.findMany({
      where: range ? { createdAt: range } : undefined,
      orderBy: { createdAt: "desc" },
      ...(range ? {} : { take: 100 }),
    });
    // The balance is the register's, not this page's: recompute it from
    // every entry so it can't drift once history outgrows the page above.
    const balance = await getCashRegisterBalance();
    res.json({ balance, entries });
  })
);

// One row per month the register has entries in, newest first, with what
// went in and what went out. This is what the folded month cards show
// without having to load the months themselves.
cashRegisterRouter.get(
  "/months",
  asyncHandler(async (_req, res) => {
    const rows = await prisma.$queryRaw<
      { month: string; count: bigint; inflow: Prisma.Decimal; outflow: Prisma.Decimal }[]
    >`
      SELECT to_char("createdAt", 'YYYY-MM') AS month,
             COUNT(*) AS count,
             COALESCE(SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END), 0) AS inflow,
             COALESCE(SUM(CASE WHEN amount < 0 THEN -amount ELSE 0 END), 0) AS outflow
      FROM "CashRegisterEntry"
      GROUP BY 1
      ORDER BY 1 DESC
    `;
    res.json(
      rows.map((row) => ({
        month: row.month,
        count: Number(row.count),
        inflow: row.inflow,
        outflow: row.outflow,
        net: new Prisma.Decimal(row.inflow).sub(row.outflow),
      }))
    );
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

// Removes a manual till entry recorded by mistake - a mistyped top-up, or a
// "reset to zero" reconciliation that shouldn't have been made. The balance
// is the sum of every entry, so deleting one simply restores what it was
// before.
//
// Only entries the owner created by hand can go: every other entry belongs
// to an expense, sale, invoice, restock, other-sale or debt, and deleting
// it here would leave that record claiming a cash movement that no longer
// exists. Those are corrected through the record itself, which is what the
// refusal points at.
cashRegisterRouter.delete(
  "/entries/:id",
  asyncHandler(async (req, res) => {
    const entry = await prisma.cashRegisterEntry.findUnique({ where: { id: req.params.id } });
    if (!entry) throw new HttpError(404, "Cash register entry not found");

    const linkedTo =
      (entry.expenseId && "an expense") ||
      (entry.saleId && "a sale") ||
      (entry.invoiceId && "a supplier invoice") ||
      (entry.inventoryTransactionId && "a restock") ||
      (entry.otherSaleId && "another sale") ||
      (entry.debtId && "a worker debt") ||
      null;
    if (linkedTo) {
      throw new HttpError(
        400,
        `This entry belongs to ${linkedTo}, so it can't be removed on its own. Correct that record instead.`
      );
    }

    await prisma.cashRegisterEntry.delete({ where: { id: entry.id } });
    const balance = await getCashRegisterBalance();
    res.json({ balance, removed: entry });
  })
);
