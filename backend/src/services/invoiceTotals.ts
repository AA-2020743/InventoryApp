import { Prisma } from "@prisma/client";
import { applyCashDeduction } from "../routes/cashRegister.routes";

// A supplier invoice in the purchase-document flow is worth exactly what it
// delivered: the sum of the stock lines booked against it. This recomputes
// that total after any line changes, and keeps the money in step with it.
//
// Only invoices flagged amountFromLines are touched. Invoices created before
// this flow carry a typed amount and no lines at all, and recomputing those
// would wipe a real figure to zero.
//
// When the invoice is already paid - which is what a cash purchase is, an
// invoice settled the moment it's created - its till entry is resized to the
// new total. That is the correction mechanism: removing or shrinking a line
// hands the money back, adding or growing one takes more, and an increase
// the register can't cover is refused like any other cash outflow, rolling
// the whole change back.
export async function syncInvoiceTotalFromLines(
  tx: Prisma.TransactionClient,
  invoiceId: string
): Promise<Prisma.Decimal | null> {
  const invoice = await tx.supplierInvoice.findUnique({
    where: { id: invoiceId },
    include: { supplier: true },
  });
  if (!invoice || !invoice.amountFromLines) return null;

  const lines = await tx.inventoryTransaction.findMany({
    where: { supplierInvoiceId: invoiceId },
  });
  const total = lines.reduce(
    (acc, line) => acc.add((line.unitCost ?? new Prisma.Decimal(0)).mul(line.quantityChange)),
    new Prisma.Decimal(0)
  );

  await tx.supplierInvoice.update({ where: { id: invoiceId }, data: { amount: total } });

  if (invoice.status === "PAID") {
    await applyCashDeduction(
      tx,
      { invoiceId },
      total,
      `Paid invoice${invoice.invoiceNumber ? ` #${invoice.invoiceNumber}` : ""} — ${invoice.supplier.name}`
    );
  }

  return total;
}
