import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { applyCashDeduction } from "./cashRegister.routes";
import { syncInvoiceTotalFromLines } from "../services/invoiceTotals";

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
// Books `quantity` units of `product` into stock at `unitCost`, blending the
// new cost into the product's purchase cost as a quantity-weighted average
// rather than overwriting it: 10 @ 5 already on hand plus 10 new @ 7 leaves
// all 20 valued at 6, not 7. Degenerates to the new cost when nothing was on
// hand. Passing no unitCost keeps the existing cost untouched.
//
// Shared by the restock route and by invoice creation, which books its lines
// the same way - the stock arrived the same way, whichever screen recorded it.
export async function applyRestockToProduct(
  tx: Prisma.TransactionClient,
  product: { id: string; quantity: Prisma.Decimal; purchaseCost: Prisma.Decimal },
  quantity: number,
  unitCost?: number
): Promise<Prisma.Decimal> {
  const effectiveUnitCost = new Prisma.Decimal(unitCost ?? product.purchaseCost);
  const newQuantity = new Prisma.Decimal(quantity);

  let blended: Prisma.Decimal | undefined;
  if (unitCost !== undefined) {
    const totalQuantity = product.quantity.add(newQuantity);
    blended = totalQuantity.gt(0)
      ? product.quantity
          .mul(product.purchaseCost)
          .add(newQuantity.mul(effectiveUnitCost))
          .div(totalQuantity)
      : effectiveUnitCost;
  }

  await tx.product.update({
    where: { id: product.id },
    data: {
      quantity: { increment: quantity },
      ...(blended !== undefined ? { purchaseCost: blended } : {}),
    },
  });

  return effectiveUnitCost;
}

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
    // A settled invoice normally can't take more stock: its amount was
    // agreed and paid, and adding to it would leave goods the payment never
    // covered. A line-managed invoice is the exception - a cash purchase is
    // paid the moment it's created, and its total and payment both resize
    // to whatever its lines say, so growing it charges the difference.
    if (invoice.status !== "PENDING" && !invoice.amountFromLines) {
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

      // Opening valuation: the cost typed on the form, put into the movement
      // log rather than living only on the product. Zero units, so it moves
      // no stock - it only states what this product's stock is worth to
      // begin with, which is what a correction falls back to once every
      // delivery it ever had has been undone.
      await tx.inventoryTransaction.create({
        data: {
          productId: created.id,
          type: "RESTOCK",
          quantityChange: 0,
          unitCost: created.purchaseCost,
          note: "Opening cost",
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
    // touches how it was paid for, so strip them here rather than passing
    // them into Product.update (not real Product columns).
    //
    // quantity is stripped for a different reason: stock only ever moves
    // through restock/adjust/sale, each of which writes a movement row.
    // Letting an edit set it directly would put stock on the shelf that
    // the log can't account for, and the log is what a correction replays.
    const {
      financing: _financing,
      supplierInvoiceId: _supplierInvoiceId,
      newInvoice: _newInvoice,
      quantity: _quantity,
      ...productFields
    } = data;
    const existing = await prisma.product.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Product not found");

    const product = await prisma.$transaction(async (tx) => {
      const updated = await tx.product.update({
        where: { id: req.params.id },
        data: {
          ...productFields,
          barcode: productFields.barcode === undefined ? undefined : productFields.barcode ?? null,
        },
      });

      // A cost set by hand overrides what the deliveries average out to, so
      // it goes into the log as a revaluation. Without that, the next
      // correction on this product would replay the deliveries straight
      // over the figure the owner just typed.
      if (!existing.purchaseCost.equals(updated.purchaseCost)) {
        await tx.inventoryTransaction.create({
          data: {
            productId: updated.id,
            type: "RESTOCK",
            quantityChange: 0,
            unitCost: updated.purchaseCost,
            note: "Cost corrected by hand",
          },
        });
      }

      return updated;
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
    // blended valuation figure the helper leaves on the product.
    const effectiveUnitCost = new Prisma.Decimal(unitCost ?? product.purchaseCost);

    const updated = await prisma.$transaction(async (tx) => {
      const cost = effectiveUnitCost.mul(quantity);
      const resolved = await resolveRestockFinancing(tx, data, cost);

      await applyRestockToProduct(tx, product, quantity, unitCost);
      const updatedProduct = await tx.product.findUniqueOrThrow({ where: { id: product.id } });
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
      } else if (resolved.supplierInvoiceId) {
        // Stock added to a line-managed invoice grows that invoice's total,
        // and its payment with it if it was already settled.
        await syncInvoiceTotalFromLines(tx, resolved.supplierInvoiceId);
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

    // Stock entered here was paid for out of the till (the inventory screen
    // is the cash-financed path - deferred stock arrives through a supplier
    // invoice instead), so taking stock back out returns what it cost. That
    // makes an over-entered delivery correctable: reduce the stock and the
    // money comes back, exactly reversing the restock that spent it.
    //
    // Priced at the product's current purchase cost, which is the blended
    // weighted average - the same figure the stock is valued at, so removing
    // it leaves inventory value and cash consistent with each other.
    const refund = quantityChange < 0 ? product.purchaseCost.mul(Math.abs(quantityChange)) : null;

    const updated = await prisma.$transaction(async (tx) => {
      const updatedProduct = await tx.product.update({
        where: { id: product.id },
        data: { quantity: { increment: quantityChange } },
      });
      const movement = await tx.inventoryTransaction.create({
        data: {
          productId: product.id,
          type: "ADJUSTMENT",
          quantityChange,
          // Recorded so the refund below is priced from the same figure the
          // stock left at, rather than whatever the cost blends to later.
          unitCost: quantityChange < 0 ? product.purchaseCost : null,
          note,
        },
      });
      if (refund && refund.gt(0)) {
        await tx.cashRegisterEntry.create({
          data: {
            amount: refund,
            note: `Stock removed: ${product.name}`,
            inventoryTransactionId: movement.id,
          },
        });
      }
      return updatedProduct;
    });

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
      const movement = await tx.inventoryTransaction.create({
        data: {
          productId: product.id,
          type: "SPOILAGE",
          quantityChange: new Prisma.Decimal(quantity).neg(),
          unitCost: product.purchaseCost,
          note: notes ?? expenseName,
        },
      });

      const expense = await tx.expense.create({
        // Linked back to the movement above so undoing the spoilage removes
        // exactly this write-off instead of guessing by name and amount.
        data: {
          name: expenseName,
          amount: cost,
          notes: notes ?? null,
          inventoryTransactionId: movement.id,
        },
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
