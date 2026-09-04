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
// One case can't be rebuilt: undoing a product's only delivery. The cost
// typed when the product was created is not kept anywhere once a delivery
// has blended over it, so the delivery's price stays behind on a product
// that now has no stock and no history. It's harmless where it lands - the
// next delivery into an empty product overwrites it outright rather than
// blending with it - and the product form can set it by hand meanwhile.
// Rather than invent a figure, this leaves the cost alone whenever there is
// no priced restock left to replay.
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
