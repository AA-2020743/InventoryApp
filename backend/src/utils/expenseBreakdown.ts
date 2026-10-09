import { Prisma, type Expense } from "@prisma/client";

// Server-generated prefix on the write-off a spoilage creates (the spoil
// routes in products.routes.ts and inventoryTransactions.routes.ts). Always
// English regardless of the app's language, since it's written server-side;
// the app translates it for display.
export const SPOILAGE_EXPENSE_PREFIX = "Spoiled: ";

// The single group all spoilage write-offs are folded into. Sent as the
// group's category for clients that only read that field; current clients
// key off kind === "spoilage" and show their own translated label.
const SPOILAGE_GROUP = "Spoiled";

export function isSpoilage(expense: Pick<Expense, "name" | "inventoryTransactionId">): boolean {
  return expense.inventoryTransactionId !== null || expense.name.startsWith(SPOILAGE_EXPENSE_PREFIX);
}

// One line of a group's sub-breakdown: a note under a regular expense
// name, or a product under the spoilage group.
export interface ExpenseSubTotal {
  label: string | null; // null = expenses in the group with no note
  total: Prisma.Decimal;
  count: number;
  // Spoilage only: how much of the product spoiled, and in what unit. Null
  // when any write-off in the line can't be traced to its stock movement
  // (recorded before the link existed) - a partial sum would understate it,
  // so none is shown rather than a wrong one.
  quantity: Prisma.Decimal | null;
  unit: string | null;
}

export interface ExpenseGroup {
  category: string | null;
  kind: "expense" | "spoilage";
  total: Prisma.Decimal;
  count: number;
  // Empty for a group with nothing to break down - a name none of whose
  // expenses carry a note.
  breakdown: ExpenseSubTotal[];
}

// Two notes that differ only in spacing or letter case are the same note:
// "Electricity", "electricity " and "electricity  bill" vs "electricity bill"
// would otherwise split one line of the breakdown into several.
export function normaliseNote(note: string | null | undefined): string | null {
  const cleaned = note?.replace(/\s+/g, " ").trim();
  return cleaned ? cleaned : null;
}

export function noteKey(note: string | null | undefined): string | null {
  return normaliseNote(note)?.toLocaleLowerCase() ?? null;
}

interface Bucket {
  label: string | null;
  total: Prisma.Decimal;
  count: number;
  quantity: Prisma.Decimal | null;
  quantityKnown: boolean;
  unit: string | null;
}

function addTo(
  map: Map<string, Bucket>,
  key: string,
  label: string | null,
  amount: Prisma.Decimal,
  quantity: Prisma.Decimal | null,
  unit: string | null
) {
  const bucket = map.get(key);
  if (!bucket) {
    map.set(key, {
      label,
      total: amount,
      count: 1,
      quantity,
      quantityKnown: quantity !== null,
      unit,
    });
    return;
  }
  bucket.total = bucket.total.add(amount);
  bucket.count += 1;
  if (quantity === null) {
    bucket.quantityKnown = false;
  } else if (bucket.quantity !== null) {
    bucket.quantity = bucket.quantity.add(quantity);
  }
  bucket.unit = bucket.unit ?? unit;
}

// Largest first, but the "no note" line always last: it's the remainder, not
// a thing in its own right, and wherever it sorted it would read as one.
function finish(map: Map<string, Bucket>): ExpenseSubTotal[] {
  return [...map.values()]
    .map((b) => ({
      label: b.label,
      total: b.total,
      count: b.count,
      quantity: b.quantityKnown ? b.quantity : null,
      unit: b.quantityKnown ? b.unit : null,
    }))
    .sort((a, b) => {
      if ((a.label === null) !== (b.label === null)) return a.label === null ? 1 : -1;
      return b.total.comparedTo(a.total);
    });
}

// Either the global client or a transaction's - both expose these two.
type Client = Pick<Prisma.TransactionClient, "inventoryTransaction" | "product">;

// Groups a period's expenses for the day/month report.
//
// Regular expenses group by name, as they always have - the name is the
// category ("Rent", "Other") - and within a name, by note, so a catch-all
// like "Other" can be read back as what it was actually spent on.
//
// Spoilage write-offs, which used to appear as one group per product
// ("Spoiled: Eggs", "Spoiled: Corn", ...) scattered through the list, fold
// into a single spoilage group broken down by product, each with the
// quantity that spoiled where it can be traced.
//
// Every expense lands in exactly one group and exactly one line within it,
// and all sums are Decimal, so each group's lines add up to its total and
// the groups add up to the period's total - not approximately, exactly.
export async function expenseGroups(client: Client, items: Expense[]): Promise<ExpenseGroup[]> {
  const spoilage = items.filter(isSpoilage);

  // The stock movements behind linked write-offs: how much spoiled, of which
  // product, in what unit.
  const movementIds = spoilage.map((e) => e.inventoryTransactionId).filter((id): id is string => id !== null);
  const movements = movementIds.length
    ? await client.inventoryTransaction.findMany({
        where: { id: { in: movementIds } },
        select: { id: true, quantityChange: true, productId: true, product: { select: { name: true, unit: true } } },
      })
    : [];
  const movementById = new Map(movements.map((m) => [m.id, m]));

  // Write-offs from before the link existed carry only "Spoiled: <name>".
  // Matching that name to a product still puts them on the same line as the
  // product's linked write-offs, instead of a second line for one product.
  const legacyNames = [
    ...new Set(
      spoilage
        .filter((e) => !e.inventoryTransactionId || !movementById.has(e.inventoryTransactionId))
        .map((e) => e.name.slice(SPOILAGE_EXPENSE_PREFIX.length).trim())
    ),
  ];
  const legacyProducts = legacyNames.length
    ? await client.product.findMany({ where: { name: { in: legacyNames } }, select: { id: true, name: true, unit: true } })
    : [];
  const productByName = new Map(legacyProducts.map((p) => [p.name, p]));

  const groups = new Map<string, { category: string | null; kind: ExpenseGroup["kind"]; total: Prisma.Decimal; count: number; lines: Map<string, Bucket>; anyNote: boolean }>();
  const groupFor = (key: string, category: string | null, kind: ExpenseGroup["kind"]) => {
    let group = groups.get(key);
    if (!group) {
      group = { category, kind, total: new Prisma.Decimal(0), count: 0, lines: new Map(), anyNote: false };
      groups.set(key, group);
    }
    return group;
  };

  for (const expense of items) {
    if (isSpoilage(expense)) {
      const group = groupFor("spoilage", SPOILAGE_GROUP, "spoilage");
      group.total = group.total.add(expense.amount);
      group.count += 1;

      const movement = expense.inventoryTransactionId ? movementById.get(expense.inventoryTransactionId) : undefined;
      if (movement) {
        addTo(
          group.lines,
          `product:${movement.productId}`,
          movement.product.name,
          expense.amount,
          movement.quantityChange.abs(),
          movement.product.unit
        );
      } else {
        const name = expense.name.startsWith(SPOILAGE_EXPENSE_PREFIX)
          ? expense.name.slice(SPOILAGE_EXPENSE_PREFIX.length).trim()
          : expense.name.trim();
        const product = productByName.get(name);
        addTo(
          group.lines,
          product ? `product:${product.id}` : `name:${name.toLocaleLowerCase()}`,
          product?.name ?? name,
          expense.amount,
          null,
          product?.unit ?? null
        );
      }
      continue;
    }

    // Same grouping key as before notes existed: the trimmed name, with a
    // blank one collapsing into the null "uncategorized" group.
    const name = expense.name.trim() || null;
    const group = groupFor(`name:${name ?? ""}`, name, "expense");
    group.total = group.total.add(expense.amount);
    group.count += 1;

    const key = noteKey(expense.notes);
    if (key !== null) group.anyNote = true;
    addTo(group.lines, key === null ? "none" : `note:${key}`, normaliseNote(expense.notes), expense.amount, null, null);
  }

  return [...groups.values()]
    .map((g) => ({
      category: g.category,
      kind: g.kind,
      total: g.total,
      count: g.count,
      // A name whose expenses have no notes at all has nothing to break
      // down; a single "no note" line equal to the whole would be noise.
      breakdown: g.kind === "spoilage" || g.anyNote ? finish(g.lines) : [],
    }))
    .sort((a, b) => b.total.comparedTo(a.total));
}

// Notes already used under an expense name, most used first, for the app to
// offer when logging another one - picking a suggestion keeps the spelling,
// and with it the breakdown line, the same. Each note is shown as it was
// most recently written.
export function noteSuggestions(rows: { notes: string | null; date: Date }[]): string[] {
  const byKey = new Map<string, { label: string; count: number; latest: Date }>();
  for (const row of rows) {
    const label = normaliseNote(row.notes);
    if (!label) continue;
    const key = label.toLocaleLowerCase();
    const seen = byKey.get(key);
    if (!seen) {
      byKey.set(key, { label, count: 1, latest: row.date });
    } else {
      seen.count += 1;
      if (row.date > seen.latest) {
        seen.latest = row.date;
        seen.label = label;
      }
    }
  }
  return [...byKey.values()]
    .sort((a, b) => b.count - a.count || b.latest.getTime() - a.latest.getTime())
    .map((s) => s.label);
}
