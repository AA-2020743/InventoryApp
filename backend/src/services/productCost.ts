import { Prisma } from "@prisma/client";

// Rebuilds a product's weighted-average purchase cost from scratch by
// replaying every stock movement it has ever had, oldest first.
//
// The forward path blends incrementally: each restock folds its price into
// the average and the average is all that's kept. That's fine going
// forwards, but it can't be run backwards - once 10 @ 12 has been blended
// into 10 @ 10 to make 20 @ 11, taking that delivery back out leaves the
// units gone and the 11 behind, because nothing on the product remembers
// what the 11 was made of. Only the movement log does, so a correction
// rebuilds the figure from there instead of trying to unpick it.
//
// The cost matters beyond reporting: removing stock in Inventory refunds
// the till at the product's purchase cost, so a stale average pays back the
// wrong amount.
//
// Movements other than a priced restock move the quantity the average is
// weighted against, but carry no price of their own - a sale doesn't change
// what the stock on the shelf cost.
//
// A restock of zero units carrying a price is a revaluation, not a delivery:
// it states outright what the stock is worth from that point on, and the
// replay takes it as written instead of blending it. Two things are recorded
// that way, and both exist so this replay always has an answer:
//
//   - the cost typed when a product is created, written as its opening
//     valuation, so undoing every delivery a product ever had falls back to
//     the figure it started with rather than leaving the last delivery's
//     price behind on an empty product;
//   - a purchase cost edited by hand on the product form, so a later
//     correction replays over the log without quietly discarding a figure
//     the owner set deliberately.
//
// Products that predate this carry no such row. There is nothing to
// reconstruct their opening cost from, so when a replay finds no priced
// restock at all it leaves the product's cost exactly as it is rather than
// inventing one.
export async function recomputeProductCost(
  tx: Prisma.TransactionClient,
  productId: string
): Promise<Prisma.Decimal | null> {
  const movements = await tx.inventoryTransaction.findMany({
    where: { productId },
    orderBy: [{ createdAt: "asc" }, { id: "asc" }],
  });

  let quantity = new Prisma.Decimal(0);
  let cost: Prisma.Decimal | null = null;

  for (const movement of movements) {
    if (movement.type === "RESTOCK" && movement.unitCost !== null) {
      const incoming = movement.quantityChange;
      if (incoming.isZero()) {
        // A revaluation: says what the stock is worth, moves none of it.
        cost = movement.unitCost;
        continue;
      }
      const total = quantity.add(incoming);
      cost =
        cost !== null && total.gt(0)
          ? quantity.mul(cost).add(incoming.mul(movement.unitCost)).div(total)
          : movement.unitCost;
      quantity = total;
    } else {
      quantity = quantity.add(movement.quantityChange);
    }
    // Stock can't go below empty. If the log ever implies it did - a
    // quantity edited straight onto the product, an import - clamping keeps
    // a nonsense negative from inverting the weighting of the next
    // delivery.
    if (quantity.lt(0)) quantity = new Prisma.Decimal(0);
  }

  if (cost === null) return null;

  const rounded = cost.toDecimalPlaces(2);
  await tx.product.update({ where: { id: productId }, data: { purchaseCost: rounded } });
  return rounded;
}
