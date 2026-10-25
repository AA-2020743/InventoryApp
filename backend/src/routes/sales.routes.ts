import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { dateOnlyKey, startOfDay, startOfMonth, startOfNextDay, startOfNextMonth } from "../utils/dates";
import { optionalDate, optionalPositiveInt } from "../utils/params";

export const salesRouter = Router();

// Keeps the cash register in sync with a sale's payment state: a
// PAID (non-deferred) sale credits its amount to the till automatically
// (cash actually changed hands at checkout), while a DEFERRED one doesn't
// until it's collected. Called on create and edit so the same logic
// handles "went from deferred to paid" and "amount changed" without
// duplicating entries - it finds the one *sync* entry already linked to
// this sale (if any) via saleId and updates or removes it instead of
// blindly adding. Explicitly excludes isPartialSaleCollection entries
// (collectSaleAmount's own ledger rows) from that lookup, and only ever
// syncs totalAmount-amountCollected - the part partial payments haven't
// What a sale's cash-register entry says in the ledger. The entry carries
// its saleId as a column, so spelling the id out in the note bought nothing
// and cost a line of unreadable hex on the one screen the owner scans by
// eye. The customer's name, when there is one, is the part that actually
// helps place a row.
function saleNote(customerName: string | null | undefined): string {
  const name = customerName?.trim();
  return name ? `Sale to ${name}` : "Sale";
}

function saleCollectionNote(customerName: string | null | undefined): string {
  const name = customerName?.trim();
  return name ? `Payment received from ${name}` : "Payment received";
}

const ZERO = new Prisma.Decimal(0);

// What one line of a sale comes to: price times quantity, rounded to the
// piastre (half up), once. The sale's total is the sum of its rounded lines,
// so a receipt's lines always add up to its total. Before, each line was
// rounded only when stored while the total was rounded once from the
// unrounded lines: three weighed lines of 4.16625 were stored as 4.17 each -
// 12.51 - under a total of 12.50.
function lineAmount(price: Prisma.Decimal, quantity: Prisma.Decimal): Prisma.Decimal {
  return price.mul(quantity).toDecimalPlaces(2, Prisma.Decimal.ROUND_HALF_UP);
}

// Money arriving from the app as a JSON number, to the piastre. A payment
// split across several tabs on the phone can arrive as 50.199999999999996;
// taken as it came, that never reached the 50.20 owed, and the tab stayed
// open owing nothing.
function toMoney(amount: number): Prisma.Decimal {
  return new Prisma.Decimal(amount).toDecimalPlaces(2, Prisma.Decimal.ROUND_HALF_UP);
}

// Quantities per product across a list of lines - the same product can be on
// more than one line.
function quantitiesByProduct(lines: { productId: string; quantity: Prisma.Decimal.Value }[]): Map<string, Prisma.Decimal> {
  const totals = new Map<string, Prisma.Decimal>();
  for (const line of lines) {
    totals.set(line.productId, (totals.get(line.productId) ?? ZERO).add(line.quantity));
  }
  return totals;
}

// Takes `quantity` of a product off the shelf only if it's still there.
// Checking the stock first and decrementing after leaves a gap two sales of
// the last few units can both get through - the check passes for each, and
// the shelf goes negative. The condition on the update closes it: the
// database applies it to the row as it is at that moment.
async function takeStock(
  tx: Prisma.TransactionClient,
  product: { id: string; name: string },
  quantity: Prisma.Decimal
): Promise<void> {
  const { count } = await tx.product.updateMany({
    where: { id: product.id, quantity: { gte: quantity } },
    data: { quantity: { decrement: quantity } },
  });
  if (count === 0) throw new HttpError(400, `Insufficient stock for "${product.name}"`);
}

// already covered - so a sale that's been partially collected and then
// force-flipped to PAID via a direct edit doesn't get double-credited.
async function syncSaleCashEntry(
  tx: Prisma.TransactionClient,
  saleId: string,
  paymentStatus: "PAID" | "DEFERRED",
  totalAmount: Prisma.Decimal,
  amountCollected: Prisma.Decimal,
  customerName: string | null
): Promise<void> {
  const existing = await tx.cashRegisterEntry.findFirst({ where: { saleId, isPartialSaleCollection: false } });
  const syncAmount = totalAmount.sub(amountCollected);
  if (paymentStatus === "PAID" && syncAmount.gt(0)) {
    if (existing) {
      if (!existing.amount.equals(syncAmount)) {
        await tx.cashRegisterEntry.update({ where: { id: existing.id }, data: { amount: syncAmount } });
      }
    } else {
      await tx.cashRegisterEntry.create({
        data: { amount: syncAmount, note: saleNote(customerName), saleId },
      });
    }
  } else if (existing) {
    await tx.cashRegisterEntry.delete({ where: { id: existing.id } });
  }
}

// Records a real cash payment toward a DEFERRED sale's outstanding balance
// - full or partial. Each call adds its own cash-register entry (flagged
// isPartialSaleCollection so syncSaleCashEntry's lookup for the sale's own
// create/edit sync entry never touches these), and the sale flips to PAID
// once the running total reaches its totalAmount.
async function collectSaleAmount(
  tx: Prisma.TransactionClient,
  sale: {
    id: string;
    totalAmount: Prisma.Decimal;
    amountCollected: Prisma.Decimal;
    customerName: string | null;
  },
  amount: Prisma.Decimal
) {
  const remaining = sale.totalAmount.sub(sale.amountCollected);
  if (amount.lte(0) || amount.gt(remaining)) {
    throw new HttpError(400, `Amount must be between 0 and the remaining balance (${remaining.toString()})`);
  }

  const newAmountCollected = sale.amountCollected.add(amount);
  const isFullyCollected = newAmountCollected.gte(sale.totalAmount);

  await tx.cashRegisterEntry.create({
    data: {
      amount,
      note: saleCollectionNote(sale.customerName),
      saleId: sale.id,
      isPartialSaleCollection: true,
    },
  });

  return tx.sale.update({
    where: { id: sale.id },
    data: {
      amountCollected: newAmountCollected,
      ...(isFullyCollected ? { paymentStatus: "PAID" as const, collectedAt: new Date() } : {}),
    },
    include: { items: { include: { product: true } } },
  });
}

// guid(): any 8-4-4-4-12 hex id - the check zod 3's uuid() made. zod 4's
// uuid() also insists on RFC version digits, and an id is an id.
const saleInput = z.object({
  // Set by the Android app's offline sales queue: a sale first created while
  // offline carries the same clientId on every retry, so a retry after a
  // dropped *response* (the first attempt actually succeeded server-side)
  // returns the original sale instead of selling the stock twice.
  clientId: z.string().guid().optional(),
  // DEFERRED = sold on credit; revenue is still recognized immediately
  // (see Sale.paymentStatus in schema.prisma) but the amount also counts
  // as an outstanding receivable on the dashboard until collected.
  paymentStatus: z.enum(["PAID", "DEFERRED"]).optional().default("PAID"),
  customerName: z.string().trim().optional().nullable(),
  items: z
    .array(
      z.object({
        productId: z.string().guid(),
        quantity: z.number().positive(),
      })
    )
    .optional()
    .default([]),
  // Used only when items is empty: a manually-entered deferred sale total
  // (e.g. recording an existing customer tab/IOU that isn't tied to
  // specific inventory) rather than one built from real line items.
  manualAmount: z.number().positive().optional(),
});

// GET /api/sales?from=&to=&limit=&paymentStatus=
salesRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const from = optionalDate(req.query.from, "from");
    const to = optionalDate(req.query.to, "to");
    // Uncapped when no limit is asked for. Its callers are the deferred-tab
    // lists, which fold their own results by month and so need all of them;
    // a default cap there silently hid tabs, and hid them after the
    // filtering rather than before it.
    const limit = optionalPositiveInt(req.query.limit, "limit");
    const paymentStatus =
      req.query.paymentStatus === "PAID" || req.query.paymentStatus === "DEFERRED"
        ? req.query.paymentStatus
        : undefined;
    // A sale with a collectedAt is a tab that was settled, as opposed to one
    // paid at the counter. Filtering for it here rather than in the app
    // matters: the limit applies before any client-side filter, so asking
    // for "the last 200 paid sales" and then keeping the collected ones
    // could return almost none of them however many there really are.
    const collectedOnly = req.query.collected === "true";

    const sales = await prisma.sale.findMany({
      where: {
        createdAt: {
          ...(from ? { gte: from } : {}),
          ...(to ? { lte: to } : {}),
        },
        ...(paymentStatus ? { paymentStatus } : {}),
        ...(collectedOnly ? { collectedAt: { not: null } } : {}),
      },
      include: { items: { include: { product: true } } },
      orderBy: { createdAt: "desc" },
      ...(limit === undefined ? {} : { take: limit }),
    });
    res.json(sales);
  })
);

// GET /api/sales/for-range?period=day|month&date= - the sales list for a
// specific calendar day or month, boundaries pinned to the business's
// timezone (see utils/dates.ts) rather than whatever instant the client
// happened to compute - this is what the Stats screen's day/month sales
// list uses instead of raw /?from=&to=, so a sale right after midnight in
// Cairo always lands under the right day regardless of the phone's own
// clock/timezone settings. Registered before /:id so "for-range" isn't
// swallowed as an id param.
salesRouter.get(
  "/for-range",
  asyncHandler(async (req, res) => {
    const period = req.query.period === "month" ? "month" : "day";
    const dateParam = optionalDate(req.query.date, "date") ?? new Date();
    const from = period === "month" ? startOfMonth(dateParam) : startOfDay(dateParam);
    const to = period === "month" ? startOfNextMonth(dateParam) : startOfNextDay(dateParam);
    // Uncapped when no limit is asked for: this returns one day or one
    // month, which is bounded by the calendar rather than by a number
    // somebody picked, and its callers show every row they get.
    const limit = optionalPositiveInt(req.query.limit, "limit");

    const sales = await prisma.sale.findMany({
      where: { createdAt: { gte: from, lt: to } },
      include: { items: { include: { product: true } } },
      orderBy: { createdAt: "desc" },
      ...(limit === undefined ? {} : { take: limit }),
    });

    res.json({ period, date: dateOnlyKey(dateParam).toISOString().slice(0, 10), items: sales });
  })
);

// GET /api/sales/months - one row per month that has sales, newest first,
// with what they came to. This is what the receipts browser's folded month
// cards show; a month's own receipts are then fetched from /for-range only
// when that month is opened.
//
// Months are cut on the business's own clock (Africa/Cairo), the same
// boundary /for-range uses, so a sale just after midnight belongs to the
// day - and the month - the shop would say it belongs to.
//
// "createdAt" is a timestamp *without* a zone holding UTC. AT TIME ZONE on
// such a value reads it as wall-clock time in the zone named, so applying
// 'Africa/Cairo' directly - as this once did - shifted every sale three
// hours the wrong way and filed the early hours of each month's first day
// under the month before. It has to be declared UTC first, then converted.
salesRouter.get(
  "/months",
  asyncHandler(async (_req, res) => {
    const rows = await prisma.$queryRaw<
      { month: string; count: bigint; revenue: Prisma.Decimal }[]
    >`
      SELECT to_char(("createdAt" AT TIME ZONE 'UTC') AT TIME ZONE 'Africa/Cairo', 'YYYY-MM') AS month,
             COUNT(*) AS count,
             COALESCE(SUM("totalAmount"), 0) AS revenue
      FROM "Sale"
      GROUP BY 1
      ORDER BY 1 DESC
    `;
    res.json(rows.map((row) => ({ month: row.month, count: Number(row.count), revenue: row.revenue })));
  })
);

// Distinct customer names already used on a deferred sale, so the app can
// suggest them while recording a new one instead of the owner having to
// remember/retype exact spelling - typing a new one just creates it
// implicitly (customerName is a plain field on Sale, not a separate managed
// entity), same convention as Product.category. Registered before /:id so
// "customers" isn't swallowed as an id param.
salesRouter.get(
  "/customers",
  asyncHandler(async (_req, res) => {
    const rows = await prisma.sale.findMany({
      where: { customerName: { not: null } },
      distinct: ["customerName"],
      select: { customerName: true },
      orderBy: { customerName: "asc" },
    });
    res.json(rows.map((r) => r.customerName).filter((c): c is string => !!c && c.trim() !== ""));
  })
);

salesRouter.get(
  "/:id",
  asyncHandler(async (req, res) => {
    const sale = await prisma.sale.findUnique({
      where: { id: req.params.id },
      include: { items: { include: { product: true } } },
    });
    if (!sale) throw new HttpError(404, "Sale not found");
    res.json(sale);
  })
);

// Creates a sale: validates stock, snapshots price/cost per item, decrements
// inventory and records an audit trail — all inside one transaction so a
// partial failure never leaves stock counts inconsistent.
salesRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const { clientId, paymentStatus, customerName, items, manualAmount } = saleInput.parse(req.body);

    if (items.length === 0 && (paymentStatus !== "DEFERRED" || manualAmount === undefined)) {
      throw new HttpError(400, "A sale must have at least one item, or be a deferred sale with a manual amount");
    }

    if (clientId) {
      const existing = await prisma.sale.findUnique({
        where: { clientId },
        include: { items: { include: { product: true } } },
      });
      if (existing) {
        res.status(200).json(existing);
        return;
      }
    }

    // A manual deferred sale (no real line items) just records a total
    // owed by a customer directly - no stock to validate or decrement.
    if (items.length === 0) {
      const sale = await prisma.$transaction(async (tx) => {
        const created = await tx.sale.create({
          data: {
            clientId,
            paymentStatus,
            customerName: customerName ?? null,
            totalAmount: toMoney(manualAmount!),
            totalCost: 0,
          },
          include: { items: { include: { product: true } } },
        });
        await syncSaleCashEntry(
        tx, created.id, created.paymentStatus, created.totalAmount, created.amountCollected, created.customerName,
      );
        return created;
      });
      res.status(201).json(sale);
      return;
    }

    const sale = await prisma.$transaction(async (tx) => {
      const productIds = items.map((i) => i.productId);
      const products = await tx.product.findMany({ where: { id: { in: productIds } } });
      const productMap = new Map(products.map((p) => [p.id, p]));

      // Checked per product, not per line: two lines of 4 against 5 on the
      // shelf each passed on their own, and the sale took the stock to -3.
      const wanted = quantitiesByProduct(items);
      for (const [productId, quantity] of wanted) {
        const product = productMap.get(productId);
        if (!product) throw new HttpError(404, `Product ${productId} not found`);
        if (product.quantity.lt(quantity)) {
          throw new HttpError(400, `Insufficient stock for "${product.name}"`);
        }
      }

      let totalAmount = ZERO;
      let totalCost = ZERO;
      const itemsData = items.map((item) => {
        const product = productMap.get(item.productId)!;
        const subtotal = lineAmount(product.sellingPrice, new Prisma.Decimal(item.quantity));
        totalAmount = totalAmount.add(subtotal);
        totalCost = totalCost.add(product.purchaseCost.mul(item.quantity));
        return {
          productId: product.id,
          quantity: item.quantity,
          unitPrice: product.sellingPrice,
          unitCost: product.purchaseCost,
          subtotal,
        };
      });

      const created = await tx.sale.create({
        data: {
          clientId,
          paymentStatus,
          customerName: customerName ?? null,
          totalAmount,
          totalCost,
          items: { create: itemsData },
        },
        include: { items: { include: { product: true } } },
      });

      for (const [productId, quantity] of wanted) {
        await takeStock(tx, productMap.get(productId)!, quantity);
      }
      for (const item of itemsData) {
        await tx.inventoryTransaction.create({
          data: {
            productId: item.productId,
            type: "SALE",
            quantityChange: new Prisma.Decimal(item.quantity).neg(),
            note: `Sale ${created.id}`,
          },
        });
      }

      await syncSaleCashEntry(
        tx, created.id, created.paymentStatus, created.totalAmount, created.amountCollected, created.customerName,
      );

      return created;
    });

    res.status(201).json(sale);
  })
);

// Marks a DEFERRED sale as fully collected in one go - equivalent to a
// collect-partial for the entire remaining balance. Re-collecting an
// already-fully-collected sale just re-stamps collectedAt rather than
// erroring (harmless).
salesRouter.post(
  "/:id/collect",
  asyncHandler(async (req, res) => {
    const sale = await prisma.$transaction(async (tx) => {
      const existing = await tx.sale.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Sale not found");

      const remaining = existing.totalAmount.sub(existing.amountCollected);
      if (remaining.lte(0)) {
        return tx.sale.update({
          where: { id: existing.id },
          data: { paymentStatus: "PAID", collectedAt: new Date() },
          include: { items: { include: { product: true } } },
        });
      }
      return collectSaleAmount(tx, existing, remaining);
    });
    res.json(sale);
  })
);

const collectPartialInput = z.object({ amount: z.number().positive() });

// Records a real partial payment toward a DEFERRED sale's outstanding
// balance - e.g. a customer paying down part of their tab rather than the
// whole thing at once. See collectSaleAmount.
salesRouter.post(
  "/:id/collect-partial",
  asyncHandler(async (req, res) => {
    const { amount } = collectPartialInput.parse(req.body);
    const sale = await prisma.$transaction(async (tx) => {
      const existing = await tx.sale.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Sale not found");
      if (existing.paymentStatus !== "DEFERRED") throw new HttpError(400, "Sale is not deferred");
      return collectSaleAmount(tx, existing, toMoney(amount));
    });
    res.json(sale);
  })
);

const saleEditInput = z.object({
  items: z
    .array(
      z.object({
        productId: z.string().guid(),
        quantity: z.number().positive(),
      })
    )
    .min(1, "A sale must have at least one item"),
  customerName: z.string().trim().optional().nullable(),
  paymentStatus: z.enum(["PAID", "DEFERRED"]).optional(),
});

// Replaces a sale's item list wholesale (the Android edit screen reopens
// the sale like a cart and resubmits the full corrected list) and
// reconciles stock by the *net* per-product delta between the old and new
// item lists - a product appearing in both isn't touched twice, and the
// audit trail records one SALE_CORRECTION per affected product rather than
// an unwind-then-redo pair, so it's clear at a glance what an edit changed.
salesRouter.put(
  "/:id",
  asyncHandler(async (req, res) => {
    const { items, customerName, paymentStatus } = saleEditInput.parse(req.body);

    const updated = await prisma.$transaction(
      async (tx) => {
        const existing = await tx.sale.findUnique({ where: { id: req.params.id }, include: { items: true } });
        if (!existing) throw new HttpError(404, "Sale not found");

        // In Decimal, as stored: summing quantities as floating point made
        // 0.1 + 0.2 differ from 0.3, so an unchanged line could still be
        // booked as a correction.
        const oldQtyByProduct = quantitiesByProduct(existing.items);
        const newQtyByProduct = quantitiesByProduct(items);
        const allProductIds = Array.from(new Set([...oldQtyByProduct.keys(), ...newQtyByProduct.keys()]));
        const deltaOf = (productId: string) =>
          (newQtyByProduct.get(productId) ?? ZERO).sub(oldQtyByProduct.get(productId) ?? ZERO);

        const products = await tx.product.findMany({ where: { id: { in: allProductIds } } });
        const productMap = new Map(products.map((p) => [p.id, p]));

        // Validate every net stock change up front so a partial failure
        // never leaves some products adjusted and others rejected.
        for (const productId of allProductIds) {
          const product = productMap.get(productId);
          if (!product) throw new HttpError(404, `Product ${productId} not found`);
          const delta = deltaOf(productId);
          if (delta.gt(0) && product.quantity.lt(delta)) {
            throw new HttpError(400, `Insufficient stock for "${product.name}" to apply this edit`);
          }
        }

        // A product already on the sale keeps the price and cost it was sold
        // at. The edit used to re-price every line at today's prices, so
        // correcting one quantity on last month's sale quietly rewrote that
        // month's revenue and profit. Only a product added by the edit is
        // priced as of now - that's when it's being sold.
        const soldAt = new Map<string, { unitPrice: Prisma.Decimal; unitCost: Prisma.Decimal }>();
        for (const item of existing.items) {
          if (!soldAt.has(item.productId)) soldAt.set(item.productId, { unitPrice: item.unitPrice, unitCost: item.unitCost });
        }

        let totalAmount = ZERO;
        let totalCost = ZERO;
        const itemsData = items.map((item) => {
          const product = productMap.get(item.productId)!;
          const price = soldAt.get(item.productId) ?? { unitPrice: product.sellingPrice, unitCost: product.purchaseCost };
          const subtotal = lineAmount(price.unitPrice, new Prisma.Decimal(item.quantity));
          totalAmount = totalAmount.add(subtotal);
          totalCost = totalCost.add(price.unitCost.mul(item.quantity));
          return {
            productId: product.id,
            quantity: item.quantity,
            unitPrice: price.unitPrice,
            unitCost: price.unitCost,
            subtotal,
          };
        });

        // Money already received toward this sale stays received. A total
        // below it would leave the customer owing a negative amount; one
        // that equals it means the tab is settled.
        if (totalAmount.lt(existing.amountCollected)) {
          throw new HttpError(
            400,
            `${existing.amountCollected.toFixed(2)} has already been paid toward this sale, so its total can't go below that`
          );
        }
        const nextStatus = paymentStatus ?? existing.paymentStatus;
        const settledByEdit = nextStatus === "DEFERRED" && existing.amountCollected.gt(0) && totalAmount.lte(existing.amountCollected);

        await tx.saleItem.deleteMany({ where: { saleId: existing.id } });
        const sale = await tx.sale.update({
          where: { id: existing.id },
          data: {
            totalAmount,
            totalCost,
            ...(customerName !== undefined ? { customerName } : {}),
            ...(paymentStatus !== undefined ? { paymentStatus } : {}),
            ...(settledByEdit ? { paymentStatus: "PAID" as const, collectedAt: new Date() } : {}),
            items: { create: itemsData },
          },
          include: { items: { include: { product: true } } },
        });

        for (const productId of allProductIds) {
          const delta = deltaOf(productId);
          if (delta.isZero()) continue;
          if (delta.gt(0)) {
            await takeStock(tx, productMap.get(productId)!, delta);
          } else {
            await tx.product.update({ where: { id: productId }, data: { quantity: { increment: delta.neg() } } });
          }
          await tx.inventoryTransaction.create({
            data: {
              productId,
              type: "SALE_CORRECTION",
              quantityChange: delta.neg(),
              note: `Correction to sale ${existing.id}`,
            },
          });
        }

        await syncSaleCashEntry(
          tx, sale.id, sale.paymentStatus, sale.totalAmount, sale.amountCollected, sale.customerName,
        );

        return sale;
      },
      { timeout: 20_000 }
    );

    res.json(updated);
  })
);

// Deletes a sale outright, giving every item's quantity back to stock
// (the inverse of creation) with its own audit trail entries, rather than
// leaving a hole in stock history for goods that (per this correction)
// never actually left the shelf.
salesRouter.delete(
  "/:id",
  asyncHandler(async (req, res) => {
    await prisma.$transaction(async (tx) => {
      const existing = await tx.sale.findUnique({ where: { id: req.params.id }, include: { items: true } });
      if (!existing) throw new HttpError(404, "Sale not found");

      for (const item of existing.items) {
        await tx.product.update({
          where: { id: item.productId },
          data: { quantity: { increment: item.quantity } },
        });
        await tx.inventoryTransaction.create({
          data: {
            productId: item.productId,
            type: "SALE_CORRECTION",
            quantityChange: item.quantity,
            note: `Sale ${existing.id} deleted`,
          },
        });
      }

      // Remove any cash register credit this sale contributed - it never
      // happened, per this deletion.
      await tx.cashRegisterEntry.deleteMany({ where: { saleId: existing.id } });

      await tx.sale.delete({ where: { id: existing.id } });
    });

    res.status(204).send();
  })
);
