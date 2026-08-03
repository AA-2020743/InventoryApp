import { Router } from "express";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { env } from "../env";
import { asyncHandler } from "../middleware/errorHandler";
import { startOfDay, startOfMonth, startOfNextDay, startOfNextMonth } from "../utils/dates";
import { getCashRegisterBalance } from "./cashRegister.routes";
import { amountInRange, deficitInRange } from "../services/expenseCalc";

export const dashboardRouter = Router();

// The core "how much is my supermarket worth right now" figure the owner
// asked for: stock at purchase cost, minus what's still owed to suppliers.
dashboardRouter.get(
  "/summary",
  asyncHandler(async (_req, res) => {
    const now = new Date();
    const todayStart = startOfDay(now);
    const todayEnd = startOfNextDay(now);
    const monthStart = startOfMonth(now);
    const monthEnd = startOfNextMonth(now);

    // "Today" always falls inside "this month", so one fetch of the
    // month's expenses covers both the today and month-to-date figures.
    const [products, assets, pendingInvoices, monthExpenses, todaySales, monthSales, upcoming, deferredSales, cashRegisterBalance] =
      await Promise.all([
        prisma.product.findMany({ where: { active: true } }),
        prisma.asset.findMany(),
        prisma.supplierInvoice.findMany({ where: { status: "PENDING" } }),
        prisma.expense.findMany({ where: { date: { gte: monthStart, lt: monthEnd } } }),
        prisma.sale.findMany({ where: { createdAt: { gte: todayStart } } }),
        prisma.sale.findMany({ where: { createdAt: { gte: monthStart } } }),
        prisma.supplierInvoice.findMany({
          where: {
            status: "PENDING",
            dueDate: { lte: new Date(now.getTime() + env.invoiceReminderDays * 86400000) },
          },
        }),
        prisma.sale.findMany({ where: { paymentStatus: "DEFERRED" } }),
        getCashRegisterBalance(),
      ]);

    const inventoryValue = products.reduce(
      (acc, p) => acc.add(p.purchaseCost.mul(p.quantity)),
      new Prisma.Decimal(0)
    );
    const assetsValue = assets.reduce((acc, a) => acc.add(a.value), new Prisma.Decimal(0));
    const pendingInvoicesTotal = pendingInvoices.reduce(
      (acc, i) => acc.add(i.amount),
      new Prisma.Decimal(0)
    );
    // Outstanding credit sales are a receivable - money owed to the
    // business - so they add to net worth the same way inventory/assets
    // do, symmetric with pending supplier invoices subtracting.
    const deferredReceivablesTotal = deferredSales.reduce(
      (acc, s) => acc.add(s.totalAmount),
      new Prisma.Decimal(0)
    );

    const monthExpensesTotal = amountInRange(monthExpenses, monthStart, monthEnd);
    const monthDeficitTotal = deficitInRange(monthExpenses, monthStart, monthEnd);
    const todayExpensesTotal = amountInRange(monthExpenses, todayStart, todayEnd);
    const todayDeficitTotal = deficitInRange(monthExpenses, todayStart, todayEnd);

    // An expense that couldn't be fully paid from the till is a hole in the
    // business's finances that inventory/cash/receivables don't reflect -
    // it's subtracted here the same way pending supplier invoices are.
    const netValuation = inventoryValue
      .add(assetsValue)
      .add(deferredReceivablesTotal)
      .add(cashRegisterBalance)
      .sub(pendingInvoicesTotal)
      .sub(monthDeficitTotal);

    const sumRevenue = (sales: { totalAmount: Prisma.Decimal }[]) =>
      sales.reduce((acc, s) => acc.add(s.totalAmount), new Prisma.Decimal(0));
    const sumCost = (sales: { totalCost: Prisma.Decimal }[]) =>
      sales.reduce((acc, s) => acc.add(s.totalCost), new Prisma.Decimal(0));

    const todayRevenue = sumRevenue(todaySales);
    const todayCost = sumCost(todaySales);
    const monthRevenue = sumRevenue(monthSales);
    const monthCost = sumCost(monthSales);

    const todayProfit = todayRevenue.sub(todayCost).sub(todayExpensesTotal);
    const monthProfit = monthRevenue.sub(monthCost).sub(monthExpensesTotal);

    const lowStockCount = products.filter((p) => p.quantity.lte(p.lowStockThreshold)).length;
    const overdueInvoicesCount = upcoming.filter((i) => i.dueDate < now).length;
    const dueSoonInvoicesCount = upcoming.length - overdueInvoicesCount;

    res.json({
      inventoryValue,
      assetsValue,
      deferredReceivablesTotal,
      cashRegisterBalance,
      pendingInvoicesTotal,
      netValuation,
      today: {
        revenue: todayRevenue,
        cost: todayCost,
        profit: todayProfit,
        expenses: todayExpensesTotal,
        deficit: todayDeficitTotal,
      },
      month: {
        revenue: monthRevenue,
        cost: monthCost,
        profit: monthProfit,
        expenses: monthExpensesTotal,
        deficit: monthDeficitTotal,
      },
      alerts: {
        lowStockCount,
        overdueInvoicesCount,
        dueSoonInvoicesCount,
      },
    });
  })
);
