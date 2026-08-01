import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";

export const salesRouter = Router();

const saleInput = z.object({
  // Set by the Android app's offline sales queue: a sale first created while
  // offline carries the same clientId on every retry, so a retry after a
  // dropped *response* (the first attempt actually succeeded server-side)
  // returns the original sale instead of selling the stock twice.
  clientId: z.string().uuid().optional(),
  items: z
    .array(
      z.object({
        productId: z.string().uuid(),
        quantity: z.number().positive(),
      })
    )
    .min(1, "A sale must have at least one item"),
});

// GET /api/sales?from=&to=&limit=
salesRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const from = typeof req.query.from === "string" ? new Date(req.query.from) : undefined;
    const to = typeof req.query.to === "string" ? new Date(req.query.to) : undefined;
    const limit = req.query.limit ? Number(req.query.limit) : 50;

    const sales = await prisma.sale.findMany({
      where: {
        createdAt: {
          ...(from ? { gte: from } : {}),
          ...(to ? { lte: to } : {}),
        },
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
    const { clientId, items } = saleInput.parse(req.body);

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

      return created;
    });

    res.status(201).json(sale);
  })
);
