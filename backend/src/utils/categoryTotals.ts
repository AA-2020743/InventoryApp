import { Prisma } from "@prisma/client";

export interface CategoryTotal {
  category: string | null;
  total: Prisma.Decimal;
  count: number;
}

// Groups amount-carrying rows (expenses, other sales) by their free-text
// category and sums each bucket, largest first. Rows with no category (or a
// blank one) collapse into a single `null` bucket the client renders as
// "Uncategorized" - deliberately kept as null rather than an English
// placeholder string so the label stays localizable on the client.
export function totalsByCategory<T extends { category: string | null; amount: Prisma.Decimal }>(
  rows: T[]
): CategoryTotal[] {
  const buckets = new Map<string | null, { total: Prisma.Decimal; count: number }>();

  for (const row of rows) {
    const key = row.category?.trim() ? row.category.trim() : null;
    const bucket = buckets.get(key);
    if (bucket) {
      bucket.total = bucket.total.add(row.amount);
      bucket.count += 1;
    } else {
      buckets.set(key, { total: row.amount, count: 1 });
    }
  }

  return [...buckets.entries()]
    .map(([category, { total, count }]) => ({ category, total, count }))
    .sort((a, b) => b.total.comparedTo(a.total));
}
