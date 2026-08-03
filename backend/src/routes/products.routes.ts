import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { applyCashDeduction } from "./cashRegister.routes";

export const productsRouter = Router();

const productInput = z.object({
  barcode: z.string().trim().min(1).optional().nullable(),
  name: z.string().trim().min(1),
  imageUrl: z.string().trim().optional().nullable(),
  category: z.string().trim().optional().nullable(),
  unit: z.string().trim().min(1).default("pcs"),
  unitsPerPackage: z.number().positive().default(1),
  soldByWeight: z.boolean().default(false),
  purchaseCost: z.number().nonnegative(),
  sellingPrice: z.number().nonnegative(),
  quantity: z.number().nonnegative().default(0),
  lowStockThreshold: z.number().nonnegative().default(0),
});

// GET /api/products?search=&category=&lowStockOnly=true&activeOnly=true
productsRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const search = typeof req.query.search === "string" ? req.query.search : undefined;
    const category = typeof req.query.category === "string" ? req.query.category : undefined;
    const lowStockOnly = req.query.lowStockOnly === "true";
    const activeOnly = req.query.activeOnly !== "false";

    const where: Prisma.ProductWhereInput = {
      ...(activeOnly ? { active: true } : {}),
      ...(category ? { category } : {}),
      ...(search
        ? {
            OR: [
              { name: { contains: search, mode: "insensitive" } },
              { barcode: { contains: search, mode: "insensitive" } },
            ],
          }
        : {}),
    };

    const products = await prisma.product.findMany({ where, orderBy: { name: "asc" } });

    const filtered = lowStockOnly
      ? products.filter((p) => p.quantity.lte(p.lowStockThreshold))
      : products;

    res.json(filtered);
  })
);

productsRouter.get(
  "/barcode/:barcode",
  asyncHandler(async (req, res) => {
    const product = await prisma.product.findUnique({ where: { barcode: req.params.barcode } });
    if (!product) throw new HttpError(404, "Product not found for this barcode");
    res.json(product);
  })
);

// Distinct categories already in use, so the app can suggest them while
// adding a product instead of the owner having to remember/retype exact
// spelling - typing a new one just creates it implicitly (category is a
// plain field on Product, not a separate managed entity).
productsRouter.get(
  "/categories",
  asyncHandler(async (_req, res) => {
    const rows = await prisma.product.findMany({
      where: { category: { not: null } },
      distinct: ["category"],
      select: { category: true },
      orderBy: { category: "asc" },
    });
    res.json(rows.map((r) => r.category).filter((c): c is string => c !== null));
  })
);

productsRouter.get(
  "/:id",
  asyncHandler(async (req, res) => {
    const product = await prisma.product.findUnique({ where: { id: req.params.id } });
    if (!product) throw new HttpError(404, "Product not found");
    res.json(product);
  })
);

productsRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const data = productInput.parse(req.body);

    if (!data.barcode && !data.imageUrl) {
      throw new HttpError(400, "Products without a barcode must have a fallback image");
    }

    const product = await prisma.$transaction(async (tx) => {
      const created = await tx.product.create({
        data: {
          ...data,
          barcode: data.barcode ?? null,
        },
      });

      if (created.quantity.gt(0)) {
        const txn = await tx.inventoryTransaction.create({
          data: {
            productId: created.id,
            type: "RESTOCK",
            quantityChange: created.quantity,
            unitCost: created.purchaseCost,
            note: "Initial stock on product creation",
          },
        });
        // Paying for initial stock always tries the till first, same as
        // any other restock not tied to a supplier invoice.
        const cost = created.purchaseCost.mul(created.quantity);
        const deficit = await applyCashDeduction(
          tx,
          { inventoryTransactionId: txn.id },
          cost,
          `Initial stock: ${created.name}`
        );
        await tx.inventoryTransaction.update({ where: { id: txn.id }, data: { deficitAmount: deficit } });
      }

      return created;
    });

    res.status(201).json(product);
  })
);

productsRouter.put(
  "/:id",
  asyncHandler(async (req, res) => {
    const data = productInput.partial().parse(req.body);
    const existing = await prisma.product.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Product not found");

    const product = await prisma.product.update({
      where: { id: req.params.id },
      data: { ...data, barcode: data.barcode === undefined ? undefined : data.barcode ?? null },
    });
    res.json(product);
  })
);

productsRouter.delete(
  "/:id",
  asyncHandler(async (req, res) => {
    const existing = await prisma.product.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Product not found");
    // Soft delete: keeps sale history / valuation history intact.
    await prisma.product.update({ where: { id: req.params.id }, data: { active: false } });
    res.status(204).send();
  })
);

const restockSchema = z.object({
  quantity: z.number().positive(),
  unitCost: z.number().nonnegative().optional(),
  note: z.string().optional(),
  supplierInvoiceId: z.string().uuid().optional(),
});

productsRouter.post(
  "/:id/restock",
  asyncHandler(async (req, res) => {
    const { quantity, unitCost, note, supplierInvoiceId } = restockSchema.parse(req.body);

    const product = await prisma.product.findUnique({ where: { id: req.params.id } });
    if (!product) throw new HttpError(404, "Product not found");

    const effectiveUnitCost = new Prisma.Decimal(unitCost ?? product.purchaseCost);

    const updated = await prisma.$transaction(async (tx) => {
      const updatedProduct = await tx.product.update({
        where: { id: product.id },
        data: {
          quantity: { increment: quantity },
          ...(unitCost !== undefined ? { purchaseCost: unitCost } : {}),
        },
      });
      const txn = await tx.inventoryTransaction.create({
        data: {
          productId: product.id,
          type: "RESTOCK",
          quantityChange: quantity,
          unitCost: effectiveUnitCost,
          note,
          supplierInvoiceId,
        },
      });

      // Only when this restock isn't tied to a supplier invoice - that
      // invoice's own PENDING/PAID lifecycle already governs when cash
      // leaves the register for it, so deducting here too would double-pay.
      if (!supplierInvoiceId) {
        const cost = effectiveUnitCost.mul(quantity);
        const deficit = await applyCashDeduction(
          tx,
          { inventoryTransactionId: txn.id },
          cost,
          `Restock: ${product.name}`
        );
        await tx.inventoryTransaction.update({ where: { id: txn.id }, data: { deficitAmount: deficit } });
      }

      return updatedProduct;
    });

    res.json(updated);
  })
);

const adjustSchema = z.object({
  quantityChange: z.number().refine((v) => v !== 0, "quantityChange must not be zero"),
  note: z.string().min(1, "A reason is required for manual adjustments"),
});

productsRouter.post(
  "/:id/adjust",
  asyncHandler(async (req, res) => {
    const { quantityChange, note } = adjustSchema.parse(req.body);

    const product = await prisma.product.findUnique({ where: { id: req.params.id } });
    if (!product) throw new HttpError(404, "Product not found");

    const newQuantity = product.quantity.toNumber() + quantityChange;
    if (newQuantity < 0) {
      throw new HttpError(400, "Adjustment would result in negative stock");
    }

    const [updated] = await prisma.$transaction([
      prisma.product.update({
        where: { id: product.id },
        data: { quantity: { increment: quantityChange } },
      }),
      prisma.inventoryTransaction.create({
        data: {
          productId: product.id,
          type: "ADJUSTMENT",
          quantityChange,
          note,
        },
      }),
    ]);

    res.json(updated);
  })
);

productsRouter.get(
  "/:id/transactions",
  asyncHandler(async (req, res) => {
    const transactions = await prisma.inventoryTransaction.findMany({
      where: { productId: req.params.id },
      orderBy: { createdAt: "desc" },
    });
    res.json(transactions);
  })
);
