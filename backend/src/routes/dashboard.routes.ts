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
    // The all-time deficit total is aggregated separately (not scoped to
    // this month, and combining both expenses and paid invoices) since it
    // feeds net valuation - a shortfall from any past expense or invoice
    // payment is still money the business doesn't have right now, not
    // something that resets at the start of a new month.
    const [
      products,
      assets,
      pendingInvoices,
      monthExpenses,
      monthOtherSales,
      allTimeExpenseDeficitAgg,
      allTimeInvoiceDeficitAgg,
      allTimeRestockDeficitAgg,
      todaySales,
      monthSales,
      upcoming,
      deferredSales,
      cashRegisterBalance,
    ] = await Promise.all([
      prisma.product.findMany({ where: { active: true } }),
      prisma.asset.findMany(),
      prisma.supplierInvoice.findMany({ where: { status: "PENDING" } }),
      prisma.expense.findMany({ where: { date: { gte: monthStart, lt: monthEnd } } }),
      prisma.otherSale.findMany({ where: { date: { gte: monthStart, lt: monthEnd } } }),
      prisma.expense.aggregate({ _sum: { deficitAmount: true } }),
      prisma.supplierInvoice.aggregate({ _sum: { deficitAmount: true } }),
      prisma.inventoryTransaction.aggregate({ where: { type: "RESTOCK" }, _sum: { deficitAmount: true } }),
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
    // do, symmetric with pending supplier invoices subtracting. Only the
    // remaining balance counts once partial payments have chipped away at
    // it - the collected portion is already reflected in cashRegisterBalance.
    const deferredReceivablesTotal = deferredSales.reduce(
      (acc, s) => acc.add(s.totalAmount.sub(s.amountCollected)),
      new Prisma.Decimal(0)
    );

    const monthExpensesTotal = amountInRange(monthExpenses, monthStart, monthEnd);
    const monthDeficitTotal = deficitInRange(monthExpenses, monthStart, monthEnd);
    const todayExpensesTotal = amountInRange(monthExpenses, todayStart, todayEnd);
    const todayDeficitTotal = deficitInRange(monthExpenses, todayStart, todayEnd);

    // An expense/invoice/restock that couldn't be fully paid from the till
    // is a hole in the business's finances that inventory/cash/receivables
    // don't otherwise reflect - not scoped to this month, since a past
    // shortfall is still missing today.
    const unpaidShortfallTotal = (allTimeExpenseDeficitAgg._sum.deficitAmount ?? new Prisma.Decimal(0))
      .add(allTimeInvoiceDeficitAgg._sum.deficitAmount ?? new Prisma.Decimal(0))
      .add(allTimeRestockDeficitAgg._sum.deficitAmount ?? new Prisma.Decimal(0));

    // What the business is actually holding right now, before factoring in
    // either kind of deficit below.
    const currentNetWorth = inventoryValue
      .add(assetsValue)
      .add(deferredReceivablesTotal)
      .add(cashRegisterBalance)
      .sub(pendingInvoicesTotal);

    // netValuation is deliberately just currentNetWorth minus real unpaid
    // obligations (unpaidShortfallTotal) - NOT also compared against the
    // owner's starting-capital figure. Folding "how far below starting
    // value are we" into this same subtraction double-counts the same gap:
    // being below starting value already shows up as a lower currentNetWorth
    // on its own, so subtracting (startingValue - currentNetWorth) again on
    // top of that computes 2×currentNetWorth − startingValue instead of the
    // actual net worth, going far more negative than reality.
    const allTimeDeficitTotal = unpaidShortfallTotal;
    const netValuation = currentNetWorth.sub(allTimeDeficitTotal);

    const sumRevenue = (sales: { totalAmount: Prisma.Decimal }[]) =>
      sales.reduce((acc, s) => acc.add(s.totalAmount), new Prisma.Decimal(0));
    const sumCost = (sales: { totalCost: Prisma.Decimal }[]) =>
      sales.reduce((acc, s) => acc.add(s.totalCost), new Prisma.Decimal(0));
    // OtherSale entries are pure profit (no cost side, unlike a checkout
    // sale) - counted straight into revenue so they flow through to profit
    // the same way, for whichever day/month they're dated.
    const sumOtherSalesInRange = (from: Date, to: Date) =>
      monthOtherSales
        .filter((o) => o.date >= from && o.date < to)
        .reduce((acc, o) => acc.add(o.amount), new Prisma.Decimal(0));

    const todayRevenue = sumRevenue(todaySales).add(sumOtherSalesInRange(todayStart, todayEnd));
    const todayCost = sumCost(todaySales);
    const monthRevenue = sumRevenue(monthSales).add(sumOtherSalesInRange(monthStart, monthEnd));
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
      allTimeDeficitTotal,
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
