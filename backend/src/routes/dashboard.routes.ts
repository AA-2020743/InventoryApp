import { Router } from "express";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { env } from "../env";
import { asyncHandler } from "../middleware/errorHandler";
import { dateOnlyKey, startOfDay, startOfMonth } from "../utils/dates";

export const dashboardRouter = Router();

type ExpenseLike = { amount: Prisma.Decimal; frequency: string };

// DAILY-frequency expenses (e.g. a cashier's daily wage) only apply on
// days actually worked - see WorkingDay. MONTHLY-frequency expenses
// (rent, etc.) accrue regardless of whether the shop opened that day.
function dailyOnlyRate(expenses: ExpenseLike[]) {
  return expenses
    .filter((e) => e.frequency === "DAILY")
    .reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
}

function monthlyProratedRate(expenses: ExpenseLike[]) {
  return expenses
    .filter((e) => e.frequency === "MONTHLY")
    .reduce((acc, e) => acc.add(e.amount.div(30)), new Prisma.Decimal(0));
}

function monthlyFlatRate(expenses: ExpenseLike[]) {
  return expenses
    .filter((e) => e.frequency === "MONTHLY")
    .reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
}

// Working days between [from, to) (calendar-day keys, `to` exclusive).
// A day with no WorkingDay row is assumed worked - only explicit
// isWorking=false rows are subtracted from the plain calendar-day count.
async function countWorkingDays(from: Date, to: Date): Promise<number> {
  const totalDays = Math.round((to.getTime() - from.getTime()) / 86400000);
  const nonWorkingDays = await prisma.workingDay.count({
    where: { date: { gte: from, lt: to }, isWorking: false },
  });
  return Math.max(0, totalDays - nonWorkingDays);
}

// The core "how much is my supermarket worth right now" figure the owner
// asked for: stock at purchase cost, minus what's still owed to suppliers.
dashboardRouter.get(
  "/summary",
  asyncHandler(async (_req, res) => {
    const now = new Date();
    const todayStart = startOfDay(now);
    const monthStart = startOfMonth(now);
    const todayKey = dateOnlyKey(now);
    const tomorrowKey = dateOnlyKey(new Date(now.getTime() + 86400000));
    const monthStartKey = dateOnlyKey(monthStart);

    const [products, assets, pendingInvoices, activeExpenses, todaySales, monthSales, upcoming, todayWorkingDay, workingDaysSoFar] =
      await Promise.all([
        prisma.product.findMany({ where: { active: true } }),
        prisma.asset.findMany(),
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
        prisma.workingDay.findUnique({ where: { date: todayKey } }),
        countWorkingDays(monthStartKey, tomorrowKey),
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
    const netValuation = inventoryValue.add(assetsValue).sub(pendingInvoicesTotal);

    const sumRevenue = (sales: { totalAmount: Prisma.Decimal }[]) =>
      sales.reduce((acc, s) => acc.add(s.totalAmount), new Prisma.Decimal(0));
    const sumCost = (sales: { totalCost: Prisma.Decimal }[]) =>
      sales.reduce((acc, s) => acc.add(s.totalCost), new Prisma.Decimal(0));

    const todayRevenue = sumRevenue(todaySales);
    const todayCost = sumCost(todaySales);
    const monthRevenue = sumRevenue(monthSales);
    const monthCost = sumCost(monthSales);

    const isTodayWorking = todayWorkingDay?.isWorking ?? true;
    const dailyExpenses = (isTodayWorking ? dailyOnlyRate(activeExpenses) : new Prisma.Decimal(0)).add(
      monthlyProratedRate(activeExpenses)
    );
    const monthlyExpenses = dailyOnlyRate(activeExpenses)
      .mul(workingDaysSoFar)
      .add(monthlyFlatRate(activeExpenses));

    const todayProfit = todayRevenue.sub(todayCost).sub(dailyExpenses);
    const monthProfit = monthRevenue.sub(monthCost).sub(monthlyExpenses);

    const lowStockCount = products.filter((p) => p.quantity.lte(p.lowStockThreshold)).length;
    const overdueInvoicesCount = upcoming.filter((i) => i.dueDate < now).length;
    const dueSoonInvoicesCount = upcoming.length - overdueInvoicesCount;

    res.json({
      inventoryValue,
      assetsValue,
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
