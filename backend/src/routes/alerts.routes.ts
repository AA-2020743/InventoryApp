import { Router } from "express";
import { prisma } from "../db";
import { env } from "../env";
import { asyncHandler } from "../middleware/errorHandler";
import { startOfDay } from "../utils/dates";
import { optionalPositiveInt } from "../utils/params";

export const alertsRouter = Router();

// Single endpoint the Android app polls periodically (WorkManager) to
// decide which local notifications to show: low stock + invoice deadlines.
alertsRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const days = optionalPositiveInt(req.query.days, "days") ?? env.invoiceReminderDays;
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

    // Overdue once the due day is over, not from the first minute of it. A
    // due date is a calendar day, stored as that day's UTC midnight - 2 or 3
    // a.m. in Cairo - so comparing it with the current instant marked every
    // invoice overdue on the very day it fell due.
    const today = startOfDay(now);
    const overdueInvoices = invoices.filter((i) => i.dueDate < today);
    const dueSoonInvoices = invoices.filter((i) => i.dueDate >= today);

    res.json({ lowStock, overdueInvoices, dueSoonInvoices });
  })
);
