import { Prisma } from "@prisma/client";

export type ExpenseLike = {
  amount: Prisma.Decimal;
  frequency: string;
  startDate: Date;
  paymentDayOfMonth: number | null;
};

// DAILY-frequency expenses (e.g. a cashier's daily wage) - callers gate
// this by working-day status themselves, since that depends on which day
// is being evaluated.
export function dailyOnlyRate(expenses: ExpenseLike[]): Prisma.Decimal {
  return expenses
    .filter((e) => e.frequency === "DAILY")
    .reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
}

function daysInMonth(year: number, month: number): number {
  return new Date(year, month + 1, 0).getDate();
}

// Clamps a configured payment day (e.g. 31) to the last real day of a
// shorter month (e.g. 28/30), matching how "due at month end" bills work.
function effectivePaymentDay(paymentDayOfMonth: number, year: number, month: number): number {
  return Math.min(paymentDayOfMonth, daysInMonth(year, month));
}

// A MONTHLY expense (e.g. rent) hits as a full outgoing only on the single
// calendar day it's actually paid each month - not smeared evenly across
// every day - so this returns the total due on exactly `date`.
export function monthlyDueOnDate(expenses: ExpenseLike[], date: Date): Prisma.Decimal {
  const year = date.getFullYear();
  const month = date.getMonth();
  const day = date.getDate();
  return expenses
    .filter(
      (e) => e.frequency === "MONTHLY" && effectivePaymentDay(e.paymentDayOfMonth ?? 1, year, month) === day
    )
    .reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
}

// Every MONTHLY expense whose payment day has already occurred this month
// as of `date` - used for month-to-date accrual, so a bill due later this
// month doesn't count against profit before it's actually happened.
export function monthlyAccruedThroughDate(expenses: ExpenseLike[], date: Date): Prisma.Decimal {
  const year = date.getFullYear();
  const month = date.getMonth();
  const day = date.getDate();
  return expenses
    .filter(
      (e) => e.frequency === "MONTHLY" && effectivePaymentDay(e.paymentDayOfMonth ?? 1, year, month) <= day
    )
    .reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
}

// ONE_TIME expenses count only once, on whichever day they're dated -
// `from`/`to` is a half-open range so this works for both a single day and
// a whole month.
export function oneTimeAmountInRange(expenses: ExpenseLike[], from: Date, to: Date): Prisma.Decimal {
  return expenses
    .filter((e) => e.frequency === "ONE_TIME" && e.startDate >= from && e.startDate < to)
    .reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
}
