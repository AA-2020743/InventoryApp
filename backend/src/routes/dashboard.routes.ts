import { Router } from "express";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { env } from "../env";
import { asyncHandler } from "../middleware/errorHandler";

export const dashboardRouter = Router();

function startOfDay(d: Date) {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

function startOfMonth(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

// Recurring expenses converted to a daily rate: DAILY counts as-is,
// MONTHLY is spread over 30 days, ONE_TIME is excluded (it's not recurring).
function dailyRate(expenses: { amount: Prisma.Decimal; frequency: string }[]) {
  return expenses.reduce((acc, e) => {
    if (e.frequency === "DAILY") return acc.add(e.amount);
    if (e.frequency === "MONTHLY") return acc.add(e.amount.div(30));
    return acc;
  }, new Prisma.Decimal(0));
}

// The core "how much is my supermarket worth right now" figure the owner
// asked for: stock at purchase cost, minus what's still owed to suppliers.
dashboardRouter.get(
  "/summary",
  asyncHandler(async (_req, res) => {
    const now = new Date();
    const todayStart = startOfDay(now);
    const monthStart = startOfMonth(now);

    const [products, pendingInvoices, activeExpenses, todaySales, monthSales, upcoming] =
      await Promise.all([
        prisma.product.findMany({ where: { active: true } }),
        prisma.supplierInvoice.findMany({ where: { status: "PENDING" } }),
        prisma.expense.findMany({ where: { active: true } }),
        prisma.sale.findMany({ where: { createdAt: { gte: todayStart } } }),
        prisma.sale.findMany({ where: { createdAt: { gte: monthStart } } }),
        prisma.supplierInvoice.findMany({
          where: {
            status: "PENDING",
            dueDate: { lte: new Date(now.getTime() + env.invoiceReminderDays * 86400000) },
          },
        }),
      ]);

    const inventoryValue = products.reduce(
      (acc, p) => acc.add(p.purchaseCost.mul(p.quantity)),
      new Prisma.Decimal(0)
    );
    const pendingInvoicesTotal = pendingInvoices.reduce(
      (acc, i) => acc.add(i.amount),
      new Prisma.Decimal(0)
    );
    const netValuation = inventoryValue.sub(pendingInvoicesTotal);

    const sumRevenue = (sales: { totalAmount: Prisma.Decimal }[]) =>
      sales.reduce((acc, s) => acc.add(s.totalAmount), new Prisma.Decimal(0));
    const sumCost = (sales: { totalCost: Prisma.Decimal }[]) =>
      sales.reduce((acc, s) => acc.add(s.totalCost), new Prisma.Decimal(0));

    const todayRevenue = sumRevenue(todaySales);
    const todayCost = sumCost(todaySales);
    const monthRevenue = sumRevenue(monthSales);
    const monthCost = sumCost(monthSales);

    const dailyExpenses = dailyRate(activeExpenses);
    const monthlyExpenses = activeExpenses.reduce((acc, e) => {
      if (e.frequency === "MONTHLY") return acc.add(e.amount);
      if (e.frequency === "DAILY") return acc.add(e.amount.mul(30));
      return acc;
    }, new Prisma.Decimal(0));

    const todayProfit = todayRevenue.sub(todayCost).sub(dailyExpenses);
    const monthProfit = monthRevenue.sub(monthCost).sub(monthlyExpenses);

    const lowStockCount = products.filter((p) => p.quantity.lte(p.lowStockThreshold)).length;
    const overdueInvoicesCount = upcoming.filter((i) => i.dueDate < now).length;
    const dueSoonInvoicesCount = upcoming.length - overdueInvoicesCount;

    res.json({
      inventoryValue,
      pendingInvoicesTotal,
      netValuation,
      today: { revenue: todayRevenue, cost: todayCost, profit: todayProfit },
      month: { revenue: monthRevenue, cost: monthCost, profit: monthProfit },
      recurringExpenses: { dailyRate: dailyExpenses, monthlyRate: monthlyExpenses },
      alerts: {
        lowStockCount,
        overdueInvoicesCount,
        dueSoonInvoicesCount,
      },
    });
  })
);
