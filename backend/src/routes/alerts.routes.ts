import { Router } from "express";
import { prisma } from "../db";
import { env } from "../env";
import { asyncHandler } from "../middleware/errorHandler";

export const alertsRouter = Router();

// Single endpoint the Android app polls periodically (WorkManager) to
// decide which local notifications to show: low stock + invoice deadlines.
alertsRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const days = req.query.days ? Number(req.query.days) : env.invoiceReminderDays;
    const now = new Date();
    const until = new Date(now.getTime() + days * 86400000);

    const [products, invoices] = await Promise.all([
      prisma.product.findMany({ where: { active: true } }),
      prisma.supplierInvoice.findMany({
        where: { status: "PENDING", dueDate: { lte: until } },
        include: { supplier: true },
        orderBy: { dueDate: "asc" },
      }),
    ]);

    const lowStock = products
      .filter((p) => p.quantity.lte(p.lowStockThreshold))
      .map((p) => ({
        productId: p.id,
        name: p.name,
        quantity: p.quantity,
        lowStockThreshold: p.lowStockThreshold,
      }));

    const overdueInvoices = invoices.filter((i) => i.dueDate < now);
    const dueSoonInvoices = invoices.filter((i) => i.dueDate >= now);

    res.json({ lowStock, overdueInvoices, dueSoonInvoices });
  })
);
