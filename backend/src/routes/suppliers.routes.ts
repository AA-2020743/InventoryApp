import { Router } from "express";
import { z } from "zod";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";

export const suppliersRouter = Router();

const supplierInput = z.object({
  name: z.string().trim().min(1),
  contactInfo: z.string().trim().optional().nullable(),
});

suppliersRouter.get(
  "/",
  asyncHandler(async (_req, res) => {
    const suppliers = await prisma.supplier.findMany({
      orderBy: { name: "asc" },
      include: { _count: { select: { invoices: true } } },
    });
    res.json(suppliers);
  })
);

suppliersRouter.get(
  "/:id",
  asyncHandler(async (req, res) => {
    const supplier = await prisma.supplier.findUnique({
      where: { id: req.params.id },
      include: { invoices: { orderBy: { dueDate: "asc" } } },
    });
    if (!supplier) throw new HttpError(404, "Supplier not found");
    res.json(supplier);
  })
);

suppliersRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const data = supplierInput.parse(req.body);
    const supplier = await prisma.supplier.create({ data });
    res.status(201).json(supplier);
  })
);

suppliersRouter.put(
  "/:id",
  asyncHandler(async (req, res) => {
    const data = supplierInput.partial().parse(req.body);
    const existing = await prisma.supplier.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Supplier not found");
    const supplier = await prisma.supplier.update({ where: { id: req.params.id }, data });
    res.json(supplier);
  })
);

suppliersRouter.delete(
  "/:id",
  asyncHandler(async (req, res) => {
    const existing = await prisma.supplier.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Supplier not found");
    const invoiceCount = await prisma.supplierInvoice.count({ where: { supplierId: req.params.id } });
    if (invoiceCount > 0) {
      throw new HttpError(400, "Cannot delete a supplier with existing invoices");
    }
    await prisma.supplier.delete({ where: { id: req.params.id } });
    res.status(204).send();
  })
);
