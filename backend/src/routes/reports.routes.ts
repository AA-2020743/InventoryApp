import { Router } from "express";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { dateOnlyKey, startOfDay, startOfMonth, startOfNextMonth } from "../utils/dates";
import { isSpoilage, normaliseNote } from "../utils/expenseBreakdown";

// Longer-range reports for the Statistics section: how costs move from month
// to month, who buys on credit, and how fast stock sells. Each answers from
// the full set of rows it covers - nothing is capped - and money is summed as
// Decimal throughout, so every total here equals the sum of its parts.
export const reportsRouter = Router();

const DAY_MS = 24 * 60 * 60 * 1000;
const ZERO = new Prisma.Decimal(0);

// "YYYY-MM" of the business (Cairo) month an instant falls in.
function monthKey(d: Date): string {
  return dateOnlyKey(d).toISOString().slice(0, 7);
}

// A mid-month instant for a "YYYY-MM" key, from which the business-timezone
// month boundaries are derived (see utils/dates.ts).
function midMonth(key: string): Date {
  const [y, m] = key.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, 15, 12));
}

function shiftMonth(key: string, delta: number): string {
  const [y, m] = key.split("-").map(Number);
  const d = new Date(Date.UTC(y, m - 1 + delta, 1));
  return d.toISOString().slice(0, 7);
}

function parseMonth(value: unknown): string {
  if (value === undefined) return monthKey(new Date());
  if (typeof value !== "string" || !/^\d{4}-(0[1-9]|1[0-2])$/.test(value)) {
    throw new HttpError(400, "month must look like YYYY-MM");
  }
  return value;
}

function parseIntParam(value: unknown, fallback: number, min: number, max: number, name: string): number {
  if (value === undefined) return fallback;
  const n = Number(value);
  if (!Number.isInteger(n) || n < min || n > max) throw new HttpError(400, `${name} must be a whole number from ${min} to ${max}`);
  return n;
}

const round1 = (n: number) => Math.round(n * 10) / 10;

// ---------------------------------------------------------------------------
// Cost trend
// ---------------------------------------------------------------------------

// GET /api/reports/cost-trend?month=YYYY-MM&months=12
//
// Expenses over `months` business months ending at `month`, split three ways:
//
// - recurring ("fixed") costs: expense names that come back month after
//   month - rent, salaries, electricity. A name counts as recurring when it
//   appears in at least half of the months the business was active in the
//   window (and in at least two), so a bill paid every other month still
//   qualifies and a one-off repair doesn't.
// - variable costs: every other expense name.
// - spoilage: stock written off, kept apart because it's a loss on goods
//   rather than a cost of running the shop.
//
// Alongside each month's costs: its revenue, cost of goods and gross
// profit, and the revenue that month would have needed for its gross profit
// to cover its recurring costs (break-even) - null when there was no positive
// gross profit to measure the margin by.
reportsRouter.get(
  "/cost-trend",
  asyncHandler(async (req, res) => {
    const endMonth = parseMonth(req.query.month);
    const count = parseIntParam(req.query.months, 12, 2, 36, "months");
    const months = Array.from({ length: count }, (_, i) => shiftMonth(endMonth, i - (count - 1)));
    const index = new Map(months.map((m, i) => [m, i]));
    const from = startOfMonth(midMonth(months[0]));
    const to = startOfNextMonth(midMonth(endMonth));

    const [expenses, sales] = await Promise.all([
      prisma.expense.findMany({ where: { date: { gte: from, lt: to } } }),
      prisma.sale.findMany({
        where: { createdAt: { gte: from, lt: to } },
        select: { createdAt: true, totalAmount: true, totalCost: true },
      }),
    ]);

    const zeros = () => months.map(() => ZERO);
    const revenue = zeros();
    const cogs = zeros();
    const spoilage = zeros();
    const byName = new Map<string, { label: string | null; series: Prisma.Decimal[] }>();
    const active = months.map(() => false);

    for (const sale of sales) {
      const i = index.get(monthKey(sale.createdAt));
      if (i === undefined) continue;
      revenue[i] = revenue[i].add(sale.totalAmount);
      cogs[i] = cogs[i].add(sale.totalCost);
      active[i] = true;
    }
    for (const expense of expenses) {
      const i = index.get(monthKey(expense.date));
      if (i === undefined) continue;
      active[i] = true;
      if (isSpoilage(expense)) {
        spoilage[i] = spoilage[i].add(expense.amount);
        continue;
      }
      // The same grouping key the day/month breakdown uses: the trimmed name.
      const label = expense.name.trim() || null;
      const key = label ?? "";
      let entry = byName.get(key);
      if (!entry) {
        entry = { label, series: zeros() };
        byName.set(key, entry);
      }
      entry.series[i] = entry.series[i].add(expense.amount);
    }

    const activeMonths = active.filter(Boolean).length;
    const threshold = Math.max(2, Math.ceil(activeMonths / 2));

    const names = [...byName.values()].map(({ label, series }) => {
      const monthsPresent = series.filter((v) => v.gt(0)).length;
      const total = series.reduce((a, v) => a.add(v), ZERO);
      return {
        name: label,
        series,
        total,
        monthsPresent,
        // Average over the months it actually occurred in: a bill paid every
        // other month averages to what one bill costs, not half of it.
        average: monthsPresent > 0 ? total.div(monthsPresent).toDecimalPlaces(2) : ZERO,
        recurring: monthsPresent >= threshold,
      };
    });
    names.sort((a, b) => Number(b.recurring) - Number(a.recurring) || b.total.comparedTo(a.total));

    const currentMonth = monthKey(new Date());
    const monthRows = months.map((month, i) => {
      let recurring = ZERO;
      let variable = ZERO;
      for (const n of names) {
        if (n.recurring) recurring = recurring.add(n.series[i]);
        else variable = variable.add(n.series[i]);
      }
      const grossProfit = revenue[i].sub(cogs[i]);
      return {
        month,
        active: active[i],
        // The month still under way: its costs are what's been paid so far,
        // not what the month will cost.
        inProgress: month === currentMonth,
        recurring,
        variable,
        spoilage: spoilage[i],
        total: recurring.add(variable).add(spoilage[i]),
        revenue: revenue[i],
        costOfGoods: cogs[i],
        grossProfit,
        // Revenue at which this month's gross margin would just pay for its
        // recurring costs: recurring / (grossProfit / revenue).
        breakEvenRevenue:
          grossProfit.gt(0) && revenue[i].gt(0)
            ? recurring.mul(revenue[i]).div(grossProfit).toDecimalPlaces(2)
            : null,
      };
    });

    // What recurring costs come to in a typical month: the average over the
    // window's active months that are over. The month under way is left out
    // - its bills aren't all in yet - so it can be read against this.
    const completed = monthRows.filter((r) => r.active && !r.inProgress);
    const typicalRecurring = completed.length
      ? completed.reduce((a, r) => a.add(r.recurring), ZERO).div(completed.length).toDecimalPlaces(2)
      : null;

    res.json({ months, activeMonths, recurringThreshold: threshold, typicalRecurring, monthRows, names });
  })
);

// ---------------------------------------------------------------------------
// Credit (deferred) customers
// ---------------------------------------------------------------------------

const AGING_BUCKETS = [
  { key: "0-7", maxDays: 7 },
  { key: "8-30", maxDays: 30 },
  { key: "31-60", maxDays: 60 },
  { key: "61+", maxDays: Infinity },
];
const SPARK_MONTHS = 6;

// GET /api/reports/deferred-customers
//
// Every sale that was ever sold on credit - still owed (DEFERRED), or since
// settled (PAID with a collectedAt) - grouped by customer. Names that differ
// only in spacing or letter case are one customer; sales without a name are
// one "unnamed" group. Per customer: how much they've bought on credit and
// how often, what they still owe, how long they take to settle, their oldest
// unpaid sale, and their credit volume over the last six months. Plus the
// age profile of everything still owed, which is where the risk sits.
reportsRouter.get(
  "/deferred-customers",
  asyncHandler(async (_req, res) => {
    const now = new Date();
    const [sales, allRevenue] = await Promise.all([
      prisma.sale.findMany({
        where: { OR: [{ paymentStatus: "DEFERRED" }, { collectedAt: { not: null } }] },
        orderBy: { createdAt: "desc" },
        select: {
          customerName: true,
          totalAmount: true,
          amountCollected: true,
          paymentStatus: true,
          createdAt: true,
          collectedAt: true,
        },
      }),
      prisma.sale.aggregate({ _sum: { totalAmount: true } }),
    ]);

    const sparkMonths = Array.from({ length: SPARK_MONTHS }, (_, i) => shiftMonth(monthKey(now), i - (SPARK_MONTHS - 1)));
    const sparkIndex = new Map(sparkMonths.map((m, i) => [m, i]));

    interface Acc {
      name: string | null;
      total: Prisma.Decimal;
      outstanding: Prisma.Decimal;
      count: number;
      openCount: number;
      settledCount: number;
      settleDaysSum: number;
      oldestOpenAt: Date | null;
      firstAt: Date;
      lastAt: Date;
      monthly: Prisma.Decimal[];
    }
    const customers = new Map<string, Acc>();
    const aging = AGING_BUCKETS.map((b) => ({ bucket: b.key, outstanding: ZERO, count: 0 }));

    for (const sale of sales) {
      // Newest first, so the first spelling seen for a customer is the
      // latest - the one shown.
      const name = normaliseNote(sale.customerName);
      const key = name?.toLocaleLowerCase() ?? "";
      let c = customers.get(key);
      if (!c) {
        c = {
          name,
          total: ZERO,
          outstanding: ZERO,
          count: 0,
          openCount: 0,
          settledCount: 0,
          settleDaysSum: 0,
          oldestOpenAt: null,
          firstAt: sale.createdAt,
          lastAt: sale.createdAt,
          monthly: sparkMonths.map(() => ZERO),
        };
        customers.set(key, c);
      }
      c.total = c.total.add(sale.totalAmount);
      c.count += 1;
      if (sale.createdAt < c.firstAt) c.firstAt = sale.createdAt;
      if (sale.createdAt > c.lastAt) c.lastAt = sale.createdAt;
      const si = sparkIndex.get(monthKey(sale.createdAt));
      if (si !== undefined) c.monthly[si] = c.monthly[si].add(sale.totalAmount);

      if (sale.paymentStatus === "DEFERRED") {
        const owed = Prisma.Decimal.max(sale.totalAmount.sub(sale.amountCollected), ZERO);
        c.outstanding = c.outstanding.add(owed);
        c.openCount += 1;
        if (!c.oldestOpenAt || sale.createdAt < c.oldestOpenAt) c.oldestOpenAt = sale.createdAt;
        const ageDays = (now.getTime() - sale.createdAt.getTime()) / DAY_MS;
        const b = AGING_BUCKETS.findIndex((x) => ageDays <= x.maxDays);
        aging[b].outstanding = aging[b].outstanding.add(owed);
        aging[b].count += 1;
      } else if (sale.collectedAt) {
        c.settledCount += 1;
        c.settleDaysSum += Math.max(0, (sale.collectedAt.getTime() - sale.createdAt.getTime()) / DAY_MS);
      }
    }

    const rows = [...customers.values()]
      .map((c) => ({
        name: c.name,
        total: c.total,
        count: c.count,
        outstanding: c.outstanding,
        collected: c.total.sub(c.outstanding),
        openCount: c.openCount,
        settledCount: c.settledCount,
        avgDaysToSettle: c.settledCount > 0 ? round1(c.settleDaysSum / c.settledCount) : null,
        oldestOpenAt: c.oldestOpenAt,
        firstAt: c.firstAt,
        lastAt: c.lastAt,
        monthly: c.monthly,
      }))
      .sort((a, b) => b.total.comparedTo(a.total));

    const totalDeferred = rows.reduce((a, r) => a.add(r.total), ZERO);
    const outstanding = rows.reduce((a, r) => a.add(r.outstanding), ZERO);
    res.json({
      totals: {
        totalDeferred,
        outstanding,
        collected: totalDeferred.sub(outstanding),
        salesOnCredit: sales.length,
        customers: rows.filter((r) => r.name !== null).length,
        allSalesRevenue: allRevenue._sum.totalAmount ?? ZERO,
      },
      aging,
      sparkMonths,
      customers: rows,
    });
  })
);

// The direction a monthly series is heading, as percent per month: the slope
// of a least-squares line through it, relative to its average. Only months
// with activity count, so a supplier taken on in June isn't read as growing
// from the empty months before - and never the month still under way, whose
// half-finished total would read as a fall every time. Null with fewer than
// three points - two points are a change, not a trend.
function trendPercent(values: number[], include: boolean[]): number | null {
  const pts = values.map((v, i) => [i, v] as const).filter((_, i) => include[i]);
  if (pts.length < 3) return null;
  const n = pts.length;
  const mx = pts.reduce((a, [x]) => a + x, 0) / n;
  const my = pts.reduce((a, [, y]) => a + y, 0) / n;
  if (my === 0) return null;
  const sxx = pts.reduce((a, [x]) => a + (x - mx) ** 2, 0);
  const sxy = pts.reduce((a, [x, y]) => a + (x - mx) * (y - my), 0);
  return round1(((sxy / sxx) / my) * 100);
}

// ---------------------------------------------------------------------------
// Purchases by supplier
// ---------------------------------------------------------------------------

// GET /api/reports/suppliers?month=YYYY-MM&months=12
//
// What was bought from each supplier in each of `months` business months
// ending at `month`, by invoice issue date and invoice total - for
// line-managed invoices, the value of the goods delivered. Per supplier:
// the monthly series and its trend, its share of everything bought, how
// many invoices, what's still owed to it (every pending invoice, whatever
// its date), and which products made up its deliveries in the window.
//
// Stock added without a supplier invoice - restocks entered directly - has
// no supplier to put it under; its value is reported per month on its own
// line rather than dropped, so the picture of buying stays complete.
reportsRouter.get(
  "/suppliers",
  asyncHandler(async (req, res) => {
    const endMonth = parseMonth(req.query.month);
    const count = parseIntParam(req.query.months, 12, 2, 36, "months");
    const months = Array.from({ length: count }, (_, i) => shiftMonth(endMonth, i - (count - 1)));
    const index = new Map(months.map((m, i) => [m, i]));
    const from = startOfMonth(midMonth(months[0]));
    const to = startOfNextMonth(midMonth(endMonth));

    const [invoices, pending, unassigned] = await Promise.all([
      prisma.supplierInvoice.findMany({
        where: { issueDate: { gte: from, lt: to } },
        select: {
          supplierId: true,
          amount: true,
          issueDate: true,
          supplier: { select: { name: true } },
          inventoryTransactions: {
            where: { type: "RESTOCK" },
            select: { productId: true, quantityChange: true, unitCost: true, product: { select: { name: true, unit: true } } },
          },
        },
      }),
      prisma.supplierInvoice.groupBy({ by: ["supplierId"], where: { status: "PENDING" }, _sum: { amount: true } }),
      prisma.inventoryTransaction.findMany({
        where: {
          type: "RESTOCK",
          supplierInvoiceId: null,
          quantityChange: { gt: 0 },
          unitCost: { not: null },
          createdAt: { gte: from, lt: to },
        },
        select: { quantityChange: true, unitCost: true, createdAt: true },
      }),
    ]);
    const owed = new Map(pending.map((p) => [p.supplierId, p._sum.amount ?? ZERO]));
    const currentMonth = monthKey(new Date());
    const countsForTrend = (series: Prisma.Decimal[]) => series.map((v, i) => v.gt(0) && months[i] !== currentMonth);

    interface Acc {
      supplierId: string;
      name: string;
      series: Prisma.Decimal[];
      invoices: number;
      products: Map<string, { productId: string; name: string; unit: string; quantity: Prisma.Decimal; value: Prisma.Decimal }>;
    }
    const bySupplier = new Map<string, Acc>();
    for (const inv of invoices) {
      const i = index.get(monthKey(inv.issueDate));
      if (i === undefined) continue;
      let acc = bySupplier.get(inv.supplierId);
      if (!acc) {
        acc = { supplierId: inv.supplierId, name: inv.supplier.name, series: months.map(() => ZERO), invoices: 0, products: new Map() };
        bySupplier.set(inv.supplierId, acc);
      }
      acc.series[i] = acc.series[i].add(inv.amount);
      acc.invoices += 1;
      for (const line of inv.inventoryTransactions) {
        if (line.quantityChange.lte(0)) continue;
        const p = acc.products.get(line.productId) ?? {
          productId: line.productId, name: line.product.name, unit: line.product.unit, quantity: ZERO, value: ZERO,
        };
        p.quantity = p.quantity.add(line.quantityChange);
        p.value = p.value.add(line.quantityChange.mul(line.unitCost ?? ZERO));
        acc.products.set(line.productId, p);
      }
    }

    const unassignedSeries = months.map(() => ZERO);
    for (const m of unassigned) {
      const i = index.get(monthKey(m.createdAt));
      if (i !== undefined) unassignedSeries[i] = unassignedSeries[i].add(m.quantityChange.mul(m.unitCost!)).toDecimalPlaces(2);
    }

    const monthTotals = months.map((month, i) => ({
      month,
      suppliers: [...bySupplier.values()].reduce((a, s) => a.add(s.series[i]), ZERO),
      unassigned: unassignedSeries[i],
    }));
    const grand = monthTotals.reduce((a, m) => a.add(m.suppliers), ZERO);

    const suppliers = [...bySupplier.values()]
      .map((s) => {
        const total = s.series.reduce((a, v) => a.add(v), ZERO);
        const activeMonths = s.series.filter((v) => v.gt(0)).length;
        return {
          supplierId: s.supplierId,
          name: s.name,
          series: s.series,
          total,
          invoices: s.invoices,
          activeMonths,
          averagePerActiveMonth: activeMonths ? total.div(activeMonths).toDecimalPlaces(2) : ZERO,
          sharePercent: grand.gt(0) ? round1(total.div(grand).mul(100).toNumber()) : 0,
          trendPercent: trendPercent(s.series.map((v) => v.toNumber()), countsForTrend(s.series)),
          owed: owed.get(s.supplierId) ?? ZERO,
          products: [...s.products.values()].sort((a, b) => b.value.comparedTo(a.value)),
        };
      })
      .sort((a, b) => b.total.comparedTo(a.total));

    res.json({
      months,
      total: grand,
      trendPercent: trendPercent(
        monthTotals.map((m) => m.suppliers.toNumber()),
        countsForTrend(monthTotals.map((m) => m.suppliers))
      ),
      currentMonth,
      monthTotals,
      suppliers,
    });
  })
);

// ---------------------------------------------------------------------------
// Pulse
// ---------------------------------------------------------------------------

const HEAT_DAYS = 90;
const PARETO_SHARE = 0.8;

// Weekday (0 = Sunday ... 6 = Saturday) and hour of an instant on the
// shop's clock.
function cairoWeekdayHour(d: Date): { weekday: number; hour: number } {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Africa/Cairo",
    weekday: "short",
    hour: "2-digit",
    hourCycle: "h23",
  }).formatToParts(d);
  const wd = parts.find((p) => p.type === "weekday")!.value;
  const hour = Number(parts.find((p) => p.type === "hour")!.value);
  return { weekday: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].indexOf(wd), hour };
}

// GET /api/reports/pulse?month=YYYY-MM
//
// A one-screen read of how the month is going, for deciding rather than
// bookkeeping:
//
// - pace: the month so far against the same stretch of last month (day of
//   month for day of month), and - while the month is under way - where it
//   is heading: what's been sold, plus the average day so far for each day
//   that's left.
// - basket: what a sale is worth on average, and how many lines it has.
// - rhythm: revenue by weekday and hour over the 90 days to the end of the
//   month, on the shop's clock - when the shop is busy and when it isn't.
// - concentration: products ranked by the profit they made this month, and
//   how few of them make 80% of it.
reportsRouter.get(
  "/pulse",
  asyncHandler(async (req, res) => {
    const month = parseMonth(req.query.month);
    const now = new Date();
    const monthStart = startOfMonth(midMonth(month));
    const monthEnd = startOfNextMonth(midMonth(month));
    const inProgress = now >= monthStart && now < monthEnd;
    const cutoff = inProgress ? now : monthEnd;

    // The same stretch of the previous month: from its start to the same
    // day-of-month and time - or all of it when this month is over. Clamped
    // to that month's end, so 31 March compares with all of February.
    const prevStart = startOfMonth(midMonth(shiftMonth(month, -1)));
    const prevEnd = monthStart;
    const prevCutoff = inProgress
      ? new Date(Math.min(prevEnd.getTime(), prevStart.getTime() + (cutoff.getTime() - monthStart.getTime())))
      : prevEnd;

    const heatFrom = new Date(cutoff.getTime() - HEAT_DAYS * DAY_MS);
    const [current, previous, heatSales, lines] = await Promise.all([
      prisma.sale.findMany({
        where: { createdAt: { gte: monthStart, lt: cutoff } },
        select: { totalAmount: true, totalCost: true, createdAt: true, paymentStatus: true, collectedAt: true, _count: { select: { items: true } } },
      }),
      prisma.sale.findMany({
        where: { createdAt: { gte: prevStart, lt: prevCutoff } },
        select: { totalAmount: true, totalCost: true },
      }),
      prisma.sale.findMany({
        where: { createdAt: { gte: heatFrom, lt: cutoff } },
        select: { totalAmount: true, createdAt: true },
      }),
      prisma.saleItem.findMany({
        where: { sale: { createdAt: { gte: monthStart, lt: cutoff } } },
        select: { productId: true, quantity: true, subtotal: true, unitCost: true, product: { select: { name: true } } },
      }),
    ]);

    const sumOf = (rows: { totalAmount: Prisma.Decimal; totalCost: Prisma.Decimal }[]) => {
      const revenue = rows.reduce((a, r) => a.add(r.totalAmount), ZERO);
      const cost = rows.reduce((a, r) => a.add(r.totalCost), ZERO);
      return { revenue, profit: revenue.sub(cost), sales: rows.length };
    };
    const cur = sumOf(current);
    const prev = sumOf(previous);

    // Where the month is heading, counted in days rather than hours. It used
    // to scale the takings by the share of the month's hours gone, nights
    // included: at 2 a.m. part of a day counted as gone with nothing sold in
    // it, at 10 p.m. most of one with nearly all of its sales, so the figure
    // drifted through the day; two hours into the 1st, one sale was
    // multiplied by 370; and it never matched the sum anyone would check it
    // with - takings over days gone, times days in the month.
    //
    // Now: the average of the days that are over, for each day still to
    // come. Today counts as an average day, or as what it has already taken
    // if that's more - so its sales aren't counted twice, and a busy day
    // isn't marked down to average. No projection until a whole day is over.
    const [year, monthNumber] = month.split("-").map(Number);
    const daysInMonth = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
    const dayOfMonth = dateOnlyKey(now).getUTCDate();
    const daysCompleted = inProgress ? dayOfMonth - 1 : daysInMonth;
    const elapsed = (cutoff.getTime() - monthStart.getTime()) / (monthEnd.getTime() - monthStart.getTime());
    let projection = null;
    if (inProgress && daysCompleted > 0) {
      const todayStart = startOfDay(now);
      const done = sumOf(current.filter((s) => s.createdAt < todayStart));
      const today = sumOf(current.filter((s) => s.createdAt >= todayStart));
      const averageRevenue = done.revenue.div(daysCompleted);
      const averageProfit = done.profit.div(daysCompleted);
      const daysAfterToday = daysInMonth - dayOfMonth;
      const todayCounted = today.revenue.gt(averageRevenue)
        ? { revenue: today.revenue, profit: today.profit }
        : { revenue: averageRevenue, profit: averageProfit };
      const revenue = done.revenue.add(todayCounted.revenue).add(averageRevenue.mul(daysAfterToday));
      const profit = done.profit.add(todayCounted.profit).add(averageProfit.mul(daysAfterToday));
      projection = {
        revenue: revenue.toDecimalPlaces(2),
        profit: profit.toDecimalPlaces(2),
        // Still to come on top of what's been sold.
        remainingRevenue: revenue.sub(cur.revenue).toDecimalPlaces(2),
        remainingProfit: profit.sub(cur.profit).toDecimalPlaces(2),
        averageDayRevenue: averageRevenue.toDecimalPlaces(2),
        averageDayProfit: averageProfit.toDecimalPlaces(2),
        dayOfMonth,
        daysInMonth,
        daysCompleted,
        // Today included.
        daysLeft: daysInMonth - daysCompleted,
        elapsedPercent: round1(elapsed * 100),
      };
    }

    const lineCount = current.reduce((a, s) => a + s._count.items, 0);
    const credit = current
      .filter((s) => s.paymentStatus === "DEFERRED" || s.collectedAt !== null)
      .reduce((a, s) => a.add(s.totalAmount), ZERO);

    // Best and quietest days of the month so far, by revenue.
    const byDay = new Map<string, Prisma.Decimal>();
    for (const s of current) {
      const k = dateOnlyKey(s.createdAt).toISOString().slice(0, 10);
      byDay.set(k, (byDay.get(k) ?? ZERO).add(s.totalAmount));
    }
    const days = [...byDay.entries()].sort((a, b) => b[1].comparedTo(a[1]));

    const heat = Array.from({ length: 7 }, () => Array.from({ length: 24 }, () => ({ revenue: ZERO, sales: 0 })));
    for (const s of heatSales) {
      const { weekday, hour } = cairoWeekdayHour(s.createdAt);
      heat[weekday][hour].revenue = heat[weekday][hour].revenue.add(s.totalAmount);
      heat[weekday][hour].sales += 1;
    }

    const byProduct = new Map<string, { productId: string; name: string; revenue: Prisma.Decimal; profit: Prisma.Decimal; quantity: Prisma.Decimal }>();
    for (const l of lines) {
      const p = byProduct.get(l.productId) ?? { productId: l.productId, name: l.product.name, revenue: ZERO, profit: ZERO, quantity: ZERO };
      p.revenue = p.revenue.add(l.subtotal);
      p.profit = p.profit.add(l.subtotal.sub(l.unitCost.mul(l.quantity)));
      p.quantity = p.quantity.add(l.quantity);
      byProduct.set(l.productId, p);
    }
    const ranked = [...byProduct.values()].sort((a, b) => b.profit.comparedTo(a.profit));
    const totalProfit = ranked.reduce((a, p) => a.add(Prisma.Decimal.max(p.profit, ZERO)), ZERO);
    // The fewest products, taken from the most profitable down, whose profit
    // reaches 80% of the month's. Loss-makers count as zero toward the share
    // rather than eating into it.
    let running = ZERO;
    let paretoCount = 0;
    const products = ranked.map((p) => {
      const reachedBefore = totalProfit.gt(0) && running.gte(totalProfit.mul(PARETO_SHARE));
      running = running.add(Prisma.Decimal.max(p.profit, ZERO));
      if (totalProfit.gt(0) && p.profit.gt(0) && !reachedBefore) paretoCount += 1;
      const cumulative = totalProfit.gt(0) ? running.div(totalProfit).toNumber() : 0;
      return { ...p, cumulativeSharePercent: round1(cumulative * 100) };
    });

    res.json({
      month,
      inProgress,
      current: cur,
      previous: prev,
      projection,
      basket: {
        averageSale: cur.sales ? cur.revenue.div(cur.sales).toDecimalPlaces(2) : null,
        averageLines: cur.sales ? round1(lineCount / cur.sales) : null,
        previousAverageSale: prev.sales ? prev.revenue.div(prev.sales).toDecimalPlaces(2) : null,
      },
      creditSharePercent: cur.revenue.gt(0) ? round1(credit.div(cur.revenue).mul(100).toNumber()) : 0,
      bestDay: days.length ? { date: days[0][0], revenue: days[0][1] } : null,
      quietestDay: days.length ? { date: days[days.length - 1][0], revenue: days[days.length - 1][1] } : null,
      heatmap: { days: HEAT_DAYS, cells: heat },
      pareto: { sharePercent: PARETO_SHARE * 100, productsForShare: paretoCount, productsSold: ranked.length, products },
    });
  })
);

// ---------------------------------------------------------------------------
// Sell-through
// ---------------------------------------------------------------------------

type Movement = { type: string; quantityChange: Prisma.Decimal; createdAt: Date };

interface Batch {
  receivedAt: Date;
  quantity: Prisma.Decimal;
  remaining: Prisma.Decimal;
  clearedAt: Date | null;
  // Stock the movement history doesn't account for - a product older than the
  // history, whose starting quantity was never recorded as a delivery.
  opening: boolean;
}

// Replays a product's stock movements first-in, first-out to see how long
// each delivery took to leave the shelf. Deliveries (restocks) open a batch;
// anything that removes stock - sales, spoilage, downward counts - empties
// the oldest open batch first, and a batch is cleared when its last unit
// goes. Stock coming back (a corrected sale, an upward count) returns to the
// batch it most plausibly left: the oldest one still open, or the one that
// was emptied last.
function replayBatches(movements: Movement[], currentQuantity: Prisma.Decimal, createdAt: Date): Batch[] {
  const batches: Batch[] = [];
  const ledger = movements.reduce((a, m) => a.add(m.quantityChange), ZERO);
  const unrecorded = currentQuantity.sub(ledger);
  if (unrecorded.gt(0)) {
    batches.push({ receivedAt: createdAt, quantity: unrecorded, remaining: unrecorded, clearedAt: null, opening: true });
  }
  let head = 0; // first batch that may still hold stock
  for (const m of movements) {
    const q = m.quantityChange;
    if (q.isZero()) continue; // a cost revaluation, not a movement of stock
    if (q.gt(0) && m.type === "RESTOCK") {
      batches.push({ receivedAt: m.createdAt, quantity: q, remaining: q, clearedAt: null, opening: false });
      continue;
    }
    if (q.gt(0)) {
      const open = batches.slice(head).find((b) => b.clearedAt === null);
      const target = open ?? batches.slice(0, head).reverse().find((b) => b.clearedAt !== null);
      if (target) {
        target.remaining = target.remaining.add(q);
        if (target.clearedAt) {
          target.clearedAt = null;
          head = Math.min(head, batches.indexOf(target));
        }
      } else {
        batches.push({ receivedAt: m.createdAt, quantity: q, remaining: q, clearedAt: null, opening: false });
      }
      continue;
    }
    let toTake = q.neg();
    while (toTake.gt(0) && head < batches.length) {
      const b = batches[head];
      if (b.clearedAt !== null || b.remaining.lte(0)) {
        head += 1;
        continue;
      }
      const taken = Prisma.Decimal.min(b.remaining, toTake);
      b.remaining = b.remaining.sub(taken);
      toTake = toTake.sub(taken);
      if (b.remaining.lte(0)) {
        b.remaining = ZERO;
        b.clearedAt = m.createdAt;
        head += 1;
      }
    }
    // Anything left over took the shelf below zero; there's no batch to
    // charge it to, so it's dropped rather than invented.
  }
  return batches;
}

const daysBetween = (a: Date, b: Date) => (b.getTime() - a.getTime()) / DAY_MS;

// GET /api/reports/sell-through?days=30
//
// For every product in the catalogue (active, or still holding stock): how
// fast it has sold over the last `days` days, how many days its current stock
// will last at that pace and when it would run out, and - from its whole
// history - how long a delivery of it typically takes to clear. A product
// younger than the window is measured over the days it has existed, so a new
// line isn't made to look slow by days it wasn't on the shelf.
//
// The summary puts a number on stock that isn't moving: what's sitting in
// products with no sales in the window, and in ones that will take more than
// two months to clear at their current pace.
reportsRouter.get(
  "/sell-through",
  asyncHandler(async (req, res) => {
    const days = parseIntParam(req.query.days, 30, 7, 365, "days");
    const now = new Date();
    const since = new Date(now.getTime() - days * DAY_MS);

    const [products, windowItems, movements, lastSold] = await Promise.all([
      prisma.product.findMany({
        where: { OR: [{ active: true }, { quantity: { gt: 0 } }] },
        select: {
          id: true,
          name: true,
          category: true,
          unit: true,
          imageUrl: true,
          quantity: true,
          purchaseCost: true,
          lowStockThreshold: true,
          createdAt: true,
        },
      }),
      prisma.saleItem.findMany({
        where: { sale: { createdAt: { gte: since } } },
        select: { productId: true, quantity: true, subtotal: true },
      }),
      prisma.inventoryTransaction.findMany({
        orderBy: [{ createdAt: "asc" }, { id: "asc" }],
        select: { productId: true, type: true, quantityChange: true, createdAt: true },
      }),
      prisma.$queryRaw<{ productId: string; at: Date }[]>`
        SELECT si."productId" AS "productId", MAX(s."createdAt") AS at
        FROM "SaleItem" si JOIN "Sale" s ON s.id = si."saleId"
        GROUP BY si."productId"
      `,
    ]);

    const sold = new Map<string, { units: Prisma.Decimal; revenue: Prisma.Decimal }>();
    for (const item of windowItems) {
      const s = sold.get(item.productId) ?? { units: ZERO, revenue: ZERO };
      s.units = s.units.add(item.quantity);
      s.revenue = s.revenue.add(item.subtotal);
      sold.set(item.productId, s);
    }
    const movesByProduct = new Map<string, Movement[]>();
    for (const m of movements) {
      const list = movesByProduct.get(m.productId) ?? [];
      list.push(m);
      movesByProduct.set(m.productId, list);
    }
    const lastSoldAt = new Map(lastSold.map((r) => [r.productId, r.at]));

    const rows = products.map((p) => {
      const s = sold.get(p.id) ?? { units: ZERO, revenue: ZERO };
      const measuredDays = Math.max(1, Math.min(days, daysBetween(p.createdAt, now)));
      const perDay = s.units.div(measuredDays);
      const inStock = p.quantity.gt(0);
      const daysOfCover = inStock && perDay.gt(0) ? round1(p.quantity.div(perDay).toNumber()) : null;

      const batches = replayBatches(movesByProduct.get(p.id) ?? [], p.quantity, p.createdAt);
      const cleared = batches.filter((b) => b.clearedAt !== null);
      const clearDays = cleared.map((b) => daysBetween(b.receivedAt, b.clearedAt!));
      const oldestOpen = batches.find((b) => b.clearedAt === null && b.remaining.gt(0));

      let status: "OUT" | "STAGNANT" | "FAST" | "STEADY" | "SLOW";
      if (!inStock) status = "OUT";
      else if (s.units.lte(0)) status = "STAGNANT";
      else if (daysOfCover !== null && daysOfCover <= 7) status = "FAST";
      else if (daysOfCover !== null && daysOfCover <= 60) status = "STEADY";
      else status = "SLOW";

      return {
        productId: p.id,
        name: p.name,
        category: p.category,
        unit: p.unit,
        imageUrl: p.imageUrl,
        quantity: p.quantity,
        stockValue: p.quantity.mul(p.purchaseCost).toDecimalPlaces(2),
        unitsSold: s.units,
        revenue: s.revenue,
        measuredDays: round1(measuredDays),
        perDay: perDay.toDecimalPlaces(3),
        daysOfCover,
        runsOutOn: daysOfCover !== null ? new Date(now.getTime() + daysOfCover * DAY_MS) : null,
        lastSoldAt: lastSoldAt.get(p.id) ?? null,
        batchesCleared: cleared.length,
        avgDaysToClear: clearDays.length ? round1(clearDays.reduce((a, d) => a + d, 0) / clearDays.length) : null,
        fastestClear: clearDays.length ? round1(Math.min(...clearDays)) : null,
        slowestClear: clearDays.length ? round1(Math.max(...clearDays)) : null,
        // How long the oldest stock now on the shelf has been there.
        oldestStockDays: oldestOpen ? round1(daysBetween(oldestOpen.receivedAt, now)) : null,
        status,
      };
    });

    const sum = (filter: (r: (typeof rows)[number]) => boolean) =>
      rows.filter(filter).reduce((a, r) => a.add(r.stockValue), ZERO);
    res.json({
      days,
      summary: {
        products: rows.length,
        stockValue: sum(() => true),
        stagnantValue: sum((r) => r.status === "STAGNANT"),
        slowValue: sum((r) => r.status === "SLOW"),
        counts: {
          OUT: rows.filter((r) => r.status === "OUT").length,
          STAGNANT: rows.filter((r) => r.status === "STAGNANT").length,
          FAST: rows.filter((r) => r.status === "FAST").length,
          STEADY: rows.filter((r) => r.status === "STEADY").length,
          SLOW: rows.filter((r) => r.status === "SLOW").length,
        },
      },
      products: rows,
    });
  })
);

// GET /api/reports/sell-through/:productId/batches
//
// One product's deliveries, newest first: when each arrived, how much, and
// when it cleared - or how much of it is still on the shelf.
reportsRouter.get(
  "/sell-through/:productId/batches",
  asyncHandler(async (req, res) => {
    const product = await prisma.product.findUnique({
      where: { id: req.params.productId },
      select: { id: true, quantity: true, createdAt: true, unit: true },
    });
    if (!product) throw new HttpError(404, "Product not found");
    const movements = await prisma.inventoryTransaction.findMany({
      where: { productId: product.id },
      orderBy: [{ createdAt: "asc" }, { id: "asc" }],
      select: { type: true, quantityChange: true, createdAt: true },
    });
    const now = new Date();
    const batches = replayBatches(movements, product.quantity, product.createdAt)
      .map((b) => ({
        receivedAt: b.receivedAt,
        quantity: b.quantity,
        remaining: b.remaining,
        clearedAt: b.clearedAt,
        daysToClear: b.clearedAt ? round1(daysBetween(b.receivedAt, b.clearedAt)) : null,
        daysOnShelf: b.clearedAt ? null : round1(daysBetween(b.receivedAt, now)),
        opening: b.opening,
      }))
      .reverse();
    res.json({ unit: product.unit, batches });
  })
);
