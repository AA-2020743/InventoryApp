import { Prisma } from "@prisma/client";

export type ExpenseLike = {
  amount: Prisma.Decimal;
  deficitAmount: Prisma.Decimal;
  date: Date;
};

// Sum of expenses dated within a half-open [from, to) range - works for
// both a single day and a whole month depending on the range passed in.
export function amountInRange(expenses: ExpenseLike[], from: Date, to: Date): Prisma.Decimal {
  return expenses
    .filter((e) => e.date >= from && e.date < to)
    .reduce((acc, e) => acc.add(e.amount), new Prisma.Decimal(0));
}

// Sum of the deficit portion (amount that couldn't be covered by the cash
// register at the time) of expenses dated within [from, to).
export function deficitInRange(expenses: ExpenseLike[], from: Date, to: Date): Prisma.Decimal {
  return expenses
    .filter((e) => e.date >= from && e.date < to)
    .reduce((acc, e) => acc.add(e.deficitAmount), new Prisma.Decimal(0));
}
