import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";

export const salesRouter = Router();

// Keeps the cash register in sync with a sale's payment state: a
// PAID (non-deferred) sale credits its amount to the till automatically
// (cash actually changed hands at checkout), while a DEFERRED one doesn't
// until it's collected. Called on create, collect, and edit so the same
// logic handles "went from deferred to paid" and "amount changed" without
// duplicating entries - it finds the one entry already linked to this sale
// (if any) via saleId and updates or removes it instead of blindly adding.
async function syncSaleCashEntry(
  tx: Prisma.TransactionClient,
  saleId: string,
  paymentStatus: "PAID" | "DEFERRED",
  totalAmount: Prisma.Decimal
): Promise<void> {
  const existing = await tx.cashRegisterEntry.findFirst({ where: { saleId } });
  if (paymentStatus === "PAID") {
    if (existing) {
      if (!existing.amount.equals(totalAmount)) {
        await tx.cashRegisterEntry.update({ where: { id: existing.id }, data: { amount: totalAmount } });
      }
    } else {
      await tx.cashRegisterEntry.create({ data: { amount: totalAmount, note: `Sale ${saleId}`, saleId } });
    }
  } else if (existing) {
    await tx.cashRegisterEntry.delete({ where: { id: existing.id } });
  }
}

const saleInput = z.object({
  // Set by the Android app's offline sales queue: a sale first created while
  // offline carries the same clientId on every retry, so a retry after a
  // dropped *response* (the first attempt actually succeeded server-side)
  // returns the original sale instead of selling the stock twice.
  clientId: z.string().uuid().optional(),
  // DEFERRED = sold on credit; revenue is still recognized immediately
  // (see Sale.paymentStatus in schema.prisma) but the amount also counts
  // as an outstanding receivable on the dashboard until collected.
  paymentStatus: z.enum(["PAID", "DEFERRED"]).optional().default("PAID"),
  customerName: z.string().trim().optional().nullable(),
  items: z
    .array(
      z.object({
        productId: z.string().uuid(),
        quantity: z.number().positive(),
      })
    )
    .min(1, "A sale must have at least one item"),
});

// GET /api/sales?from=&to=&limit=&paymentStatus=
salesRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const from = typeof req.query.from === "string" ? new Date(req.query.from) : undefined;
    const to = typeof req.query.to === "string" ? new Date(req.query.to) : undefined;
    const limit = req.query.limit ? Number(req.query.limit) : 50;
    const paymentStatus =
      req.query.paymentStatus === "PAID" || req.query.paymentStatus === "DEFERRED"
        ? req.query.paymentStatus
        : undefined;

    const sales = await prisma.sale.findMany({
      where: {
        createdAt: {
          ...(from ? { gte: from } : {}),
          ...(to ? { lte: to } : {}),
        },
        ...(paymentStatus ? { paymentStatus } : {}),
      },
      include: { items: { include: { product: true } } },
      orderBy: { createdAt: "desc" },
      take: limit,
    });
    res.json(sales);
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
    const { clientId, paymentStatus, customerName, items } = saleInput.parse(req.body);

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

    const sale = await prisma.$transaction(async (tx) => {
      const productIds = items.map((i) => i.productId);
      const products = await tx.product.findMany({ where: { id: { in: productIds } } });
      const productMap = new Map(products.map((p) => [p.id, p]));

      let totalAmount = new Prisma.Decimal(0);
      let totalCost = new Prisma.Decimal(0);
      const itemsData: {
        productId: string;
        quantity: number;
        unitPrice: Prisma.Decimal;
        unitCost: Prisma.Decimal;
        subtotal: Prisma.Decimal;
      }[] = [];

      for (const item of items) {
        const product = productMap.get(item.productId);
        if (!product) throw new HttpError(404, `Product ${item.productId} not found`);
        if (product.quantity.lt(item.quantity)) {
          throw new HttpError(400, `Insufficient stock for "${product.name}"`);
        }

        const subtotal = product.sellingPrice.mul(item.quantity);
        totalAmount = totalAmount.add(subtotal);
        totalCost = totalCost.add(product.purchaseCost.mul(item.quantity));

        itemsData.push({
          productId: product.id,
          quantity: item.quantity,
          unitPrice: product.sellingPrice,
          unitCost: product.purchaseCost,
          subtotal,
        });
      }

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

      for (const item of itemsData) {
        await tx.product.update({
          where: { id: item.productId },
          data: { quantity: { decrement: item.quantity } },
        });
        await tx.inventoryTransaction.create({
          data: {
            productId: item.productId,
            type: "SALE",
            quantityChange: new Prisma.Decimal(item.quantity).neg(),
            note: `Sale ${created.id}`,
          },
        });
      }

      await syncSaleCashEntry(tx, created.id, created.paymentStatus, created.totalAmount);

      return created;
    });

    res.status(201).json(sale);
  })
);

// Marks a DEFERRED sale as collected - cash is now actually received, so
// this is the point syncSaleCashEntry credits the register, not sale
// creation. Re-collecting an already-PAID sale just re-stamps collectedAt
// rather than erroring (harmless, and syncSaleCashEntry is a no-op since
// its linked entry already exists with the right amount).
salesRouter.post(
  "/:id/collect",
  asyncHandler(async (req, res) => {
    const sale = await prisma.$transaction(async (tx) => {
      const existing = await tx.sale.findUnique({ where: { id: req.params.id } });
      if (!existing) throw new HttpError(404, "Sale not found");
      const updated = await tx.sale.update({
        where: { id: existing.id },
        data: { paymentStatus: "PAID", collectedAt: new Date() },
        include: { items: { include: { product: true } } },
      });
      await syncSaleCashEntry(tx, updated.id, "PAID", updated.totalAmount);
      return updated;
    });
    res.json(sale);
  })
);

const saleEditInput = z.object({
  items: z
    .array(
      z.object({
        productId: z.string().uuid(),
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

        const oldQtyByProduct = new Map<string, number>();
        for (const item of existing.items) {
          oldQtyByProduct.set(item.productId, (oldQtyByProduct.get(item.productId) ?? 0) + item.quantity.toNumber());
        }
        const newQtyByProduct = new Map<string, number>();
        for (const item of items) {
          newQtyByProduct.set(item.productId, (newQtyByProduct.get(item.productId) ?? 0) + item.quantity);
        }
        const allProductIds = Array.from(new Set([...oldQtyByProduct.keys(), ...newQtyByProduct.keys()]));

        const products = await tx.product.findMany({ where: { id: { in: allProductIds } } });
        const productMap = new Map(products.map((p) => [p.id, p]));

        // Validate every net stock change up front so a partial failure
        // never leaves some products adjusted and others rejected.
        for (const productId of allProductIds) {
          const product = productMap.get(productId);
          if (!product) throw new HttpError(404, `Product ${productId} not found`);
          const delta = (newQtyByProduct.get(productId) ?? 0) - (oldQtyByProduct.get(productId) ?? 0);
          if (delta > 0 && product.quantity.lt(delta)) {
            throw new HttpError(400, `Insufficient stock for "${product.name}" to apply this edit`);
          }
        }

        let totalAmount = new Prisma.Decimal(0);
        let totalCost = new Prisma.Decimal(0);
        const itemsData = items.map((item) => {
          const product = productMap.get(item.productId)!;
          const subtotal = product.sellingPrice.mul(item.quantity);
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

        await tx.saleItem.deleteMany({ where: { saleId: existing.id } });
        const sale = await tx.sale.update({
          where: { id: existing.id },
          data: {
            totalAmount,
            totalCost,
            ...(customerName !== undefined ? { customerName } : {}),
            ...(paymentStatus !== undefined ? { paymentStatus } : {}),
            items: { create: itemsData },
          },
          include: { items: { include: { product: true } } },
        });

        for (const productId of allProductIds) {
          const delta = (newQtyByProduct.get(productId) ?? 0) - (oldQtyByProduct.get(productId) ?? 0);
          if (delta === 0) continue;
          await tx.product.update({
            where: { id: productId },
            data: { quantity: { decrement: delta } },
          });
          await tx.inventoryTransaction.create({
            data: {
              productId,
              type: "SALE_CORRECTION",
              quantityChange: new Prisma.Decimal(-delta),
              note: `Correction to sale ${existing.id}`,
            },
          });
        }

        await syncSaleCashEntry(tx, sale.id, sale.paymentStatus, sale.totalAmount);

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
