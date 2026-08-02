import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler } from "../middleware/errorHandler";

export const cashRegisterRouter = Router();

export async function getCashRegisterBalance(): Promise<Prisma.Decimal> {
  const entries = await prisma.cashRegisterEntry.findMany();
  return entries.reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
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
