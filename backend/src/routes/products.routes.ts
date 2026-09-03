import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { applyCashDeduction } from "./cashRegister.routes";

export const productsRouter = Router();

// How a restock (or a new product's initial stock) is paid for: CASH pays
// out of the till immediately (rejected outright if the till can't cover
// it - see applyCashDeduction), DEFERRED skips the till entirely and links
// the stock to a supplier invoice instead, whose own PENDING/PAID
// lifecycle governs when cash actually leaves for it. Not provided
// explicitly by older callers - inferred as DEFERRED when an invoice to
// link is present, CASH otherwise, so the field stays optional.
export const newInvoiceForRestockSchema = z.object({
  supplierId: z.string().uuid(),
  invoiceNumber: z.string().trim().optional().nullable(),
  dueDate: z.coerce.date(),
  notes: z.string().trim().optional().nullable(),
});

const financingFields = {
  financing: z.enum(["CASH", "DEFERRED"]).optional(),
  supplierInvoiceId: z.string().uuid().optional(),
  newInvoice: newInvoiceForRestockSchema.optional(),
};

// Resolves how a restock's cost is financed: CASH deducts from the till
// (via applyCashDeduction, hard-blocking on insufficient funds); DEFERRED
// must link to a PENDING supplier invoice - either one that already
// exists, or one created here on the fly - and returns its id so the
// caller can stamp it onto the InventoryTransaction instead of touching
// cash at all.
export async function resolveRestockFinancing(
  tx: Prisma.TransactionClient,
  input: {
    financing?: "CASH" | "DEFERRED";
    supplierInvoiceId?: string;
    newInvoice?: z.infer<typeof newInvoiceForRestockSchema>;
  },
  cost: Prisma.Decimal
): Promise<{ financing: "CASH" | "DEFERRED"; supplierInvoiceId?: string }> {
  const financing = input.financing ?? (input.supplierInvoiceId || input.newInvoice ? "DEFERRED" : "CASH");

  if (financing === "CASH") {
    return { financing };
  }

  if (input.supplierInvoiceId) {
    const invoice = await tx.supplierInvoice.findUnique({ where: { id: input.supplierInvoiceId } });
    if (!invoice) throw new HttpError(404, "Supplier invoice not found");
    if (invoice.status !== "PENDING") {
      throw new HttpError(400, "Can only link a deferred restock to a pending invoice");
    }
    return { financing, supplierInvoiceId: invoice.id };
  }

  if (input.newInvoice) {
    const supplier = await tx.supplier.findUnique({ where: { id: input.newInvoice.supplierId } });
    if (!supplier) throw new HttpError(404, "Supplier not found");
    const created = await tx.supplierInvoice.create({
      data: {
        supplierId: input.newInvoice.supplierId,
        invoiceNumber: input.newInvoice.invoiceNumber ?? null,
        amount: cost,
        dueDate: input.newInvoice.dueDate,
        notes: input.newInvoice.notes ?? null,
      },
    });
    return { financing, supplierInvoiceId: created.id };
  }

  throw new HttpError(
    400,
    "Deferred restocks need a supplier invoice to link to - either an existing pending one or details to create a new one"
  );
}

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
  ...financingFields,
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
    const { financing, supplierInvoiceId, newInvoice, ...productFields } = data;

    if (!productFields.barcode && !productFields.imageUrl) {
      throw new HttpError(400, "Products without a barcode must have a fallback image");
    }

    const product = await prisma.$transaction(async (tx) => {
      const created = await tx.product.create({
        data: {
          ...productFields,
          barcode: productFields.barcode ?? null,
        },
      });

      if (created.quantity.gt(0)) {
        const cost = created.purchaseCost.mul(created.quantity);
        const resolved = await resolveRestockFinancing(tx, data, cost);

        const txn = await tx.inventoryTransaction.create({
          data: {
            productId: created.id,
            type: "RESTOCK",
            quantityChange: created.quantity,
            unitCost: created.purchaseCost,
            note: "Initial stock on product creation",
            supplierInvoiceId: resolved.supplierInvoiceId,
          },
        });

        if (resolved.financing === "CASH") {
          await applyCashDeduction(tx, { inventoryTransactionId: txn.id }, cost, `Initial stock: ${created.name}`);
        }
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
    // financing/supplierInvoiceId/newInvoice only make sense for initial
    // stock at creation time - editing a product's own fields never
    // touches quantity or how it was paid for, so strip them here rather
    // than passing them into Product.update (not real Product columns).
    const { financing: _financing, supplierInvoiceId: _supplierInvoiceId, newInvoice: _newInvoice, ...productFields } = data;
    const existing = await prisma.product.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Product not found");

    const product = await prisma.product.update({
      where: { id: req.params.id },
      data: {
        ...productFields,
        barcode: productFields.barcode === undefined ? undefined : productFields.barcode ?? null,
      },
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
  ...financingFields,
});

productsRouter.post(
  "/:id/restock",
  asyncHandler(async (req, res) => {
    const data = restockSchema.parse(req.body);
    const { quantity, unitCost, note } = data;

    const product = await prisma.product.findUnique({ where: { id: req.params.id } });
    if (!product) throw new HttpError(404, "Product not found");

    // The cash/invoice side always reflects what was actually paid for
    // *this* restock (quantity at the price entered now) - never the
    // blended figure below, which is a valuation concept, not a real
    // transaction amount.
    const effectiveUnitCost = new Prisma.Decimal(unitCost ?? product.purchaseCost);

    // A restock at a different price doesn't overwrite the cost of stock
    // already on hand - it blends in proportionally (quantity-weighted
    // average), same as standard inventory costing. E.g. 10 units @ $5
    // already in stock + 10 new units @ $7 -> purchaseCost becomes $6 for
    // all 20, not $7 for all 20. Degenerates to just the new cost when
    // there's no existing stock (quantity was 0).
    const newQuantityDecimal = new Prisma.Decimal(quantity);
    const blendedPurchaseCost =
      unitCost !== undefined
        ? (() => {
            const totalQuantity = product.quantity.add(newQuantityDecimal);
            return totalQuantity.gt(0)
              ? product.quantity
                  .mul(product.purchaseCost)
                  .add(newQuantityDecimal.mul(effectiveUnitCost))
                  .div(totalQuantity)
              : effectiveUnitCost;
          })()
        : undefined;

    const updated = await prisma.$transaction(async (tx) => {
      const cost = effectiveUnitCost.mul(quantity);
      const resolved = await resolveRestockFinancing(tx, data, cost);

      const updatedProduct = await tx.product.update({
        where: { id: product.id },
        data: {
          quantity: { increment: quantity },
          ...(blendedPurchaseCost !== undefined ? { purchaseCost: blendedPurchaseCost } : {}),
        },
      });
      const txn = await tx.inventoryTransaction.create({
        data: {
          productId: product.id,
          type: "RESTOCK",
          quantityChange: quantity,
          unitCost: effectiveUnitCost,
          note,
          supplierInvoiceId: resolved.supplierInvoiceId,
        },
      });

      if (resolved.financing === "CASH") {
        await applyCashDeduction(tx, { inventoryTransactionId: txn.id }, cost, `Restock: ${product.name}`);
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

const spoilSchema = z.object({
  quantity: z.number().positive(),
  notes: z.string().trim().optional().nullable(),
});

// Removes spoiled/damaged stock from inventory and books its original cost
// as a plain Expense record (shows up in the Expenses tab and day/month
// expense totals) - deliberately does NOT run it through
// applyCashDeduction/the cash register. No cash actually changes hands when
// stock spoils (unlike paying a supplier or a bill), and the loss is
// already fully reflected the moment quantity drops out of inventoryValue;
// also deducting/deficit-tracking the same amount against the cash
// register would double-count it in netValuation.
productsRouter.post(
  "/:id/spoil",
  asyncHandler(async (req, res) => {
    const { quantity, notes } = spoilSchema.parse(req.body);

    const product = await prisma.product.findUnique({ where: { id: req.params.id } });
    if (!product) throw new HttpError(404, "Product not found");
    if (quantity > product.quantity.toNumber()) {
      throw new HttpError(400, "Spoiled quantity exceeds current stock");
    }

    const cost = product.purchaseCost.mul(quantity);
    const expenseName = `Spoiled: ${product.name}`;

    const result = await prisma.$transaction(async (tx) => {
      const updatedProduct = await tx.product.update({
        where: { id: product.id },
        data: { quantity: { decrement: quantity } },
      });
      await tx.inventoryTransaction.create({
        data: {
          productId: product.id,
          type: "SPOILAGE",
          quantityChange: new Prisma.Decimal(quantity).neg(),
          unitCost: product.purchaseCost,
          note: notes ?? expenseName,
        },
      });

      const expense = await tx.expense.create({
        data: { name: expenseName, amount: cost, notes: notes ?? null },
      });

      return { product: updatedProduct, expense };
    });

    res.json(result);
  })
);

productsRouter.get(
  "/:id/transactions",
  asyncHandler(async (req, res) => {
    const transactions = await prisma.inventoryTransaction.findMany({
      where: { productId: req.params.id },
      include: { supplierInvoice: { include: { supplier: true } } },
      orderBy: { createdAt: "desc" },
    });
    res.json(transactions);
  })
);
