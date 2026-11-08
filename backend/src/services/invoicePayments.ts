import { Prisma } from "@prisma/client";
import { HttpError } from "../middleware/errorHandler";
import { applyCashDeduction } from "../routes/cashRegister.routes";

// What the till entries for a supplier invoice say, and the one rule that
// keeps them true.
//
// An invoice is paid from the till in one of two ways:
//
//   * in parts, while it is still pending - each part is its own entry,
//     flagged isPartialInvoicePayment, and adds to `amountPaid`;
//   * settled - the rest (the whole amount, if nothing was paid in parts)
//     goes out as the invoice's single settling entry, the one
//     applyCashDeduction keeps against its invoiceId.
//
// So a PAID invoice's entries always add up to its amount, and a pending
// one's add up to `amountPaid`.

export function invoicePaymentNote(
  kind: "full" | "part" | "rest",
  invoiceNumber: string | null,
  supplierName: string
): string {
  const ref = `invoice${invoiceNumber ? ` #${invoiceNumber}` : ""} — ${supplierName}`;
  if (kind === "part") return `Part payment on ${ref}`;
  if (kind === "rest") return `Paid the rest of ${ref}`;
  return `Paid ${ref}`;
}

type InvoiceForPayment = {
  id: string;
  amount: Prisma.Decimal;
  amountPaid: Prisma.Decimal;
  invoiceNumber: string | null;
  supplier: { name: string };
};

// Pays whatever is left of the invoice out of the till as its settling
// entry, resizing that entry if there already is one. Refused outright, like
// every other outflow, if the till can't cover it.
export async function settleInvoiceCash(tx: Prisma.TransactionClient, invoice: InvoiceForPayment): Promise<void> {
  const rest = invoice.amount.sub(invoice.amountPaid);
  await applyCashDeduction(
    tx,
    { invoiceId: invoice.id },
    rest,
    invoicePaymentNote(invoice.amountPaid.gt(0) ? "rest" : "full", invoice.invoiceNumber, invoice.supplier.name)
  );
}

// Brings the money in line after an invoice's total has changed - an edit,
// or a stock line added, changed or removed.
//
//   * The total can't go below what has already been paid in parts: that
//     money has left the till, and there's nothing on the invoice for it to
//     have paid for. Refused, which rolls back whatever changed the total.
//   * A settled invoice resizes its settling entry to the new rest - a
//     smaller total hands money back, a bigger one takes more.
//   * A pending invoice whose total has come down to exactly what it has
//     been paid is settled: nothing is left to pay.
export async function syncInvoicePayments(
  tx: Prisma.TransactionClient,
  invoice: InvoiceForPayment & { status: "PENDING" | "PAID" }
): Promise<void> {
  if (invoice.amount.lt(invoice.amountPaid)) {
    throw new HttpError(
      400,
      `${invoice.amountPaid.toFixed(2)} has already been paid toward this invoice, so its total can't go below that. Undo its payments first.`
    );
  }
  if (invoice.status === "PAID") {
    await settleInvoiceCash(tx, invoice);
    return;
  }
  if (invoice.amountPaid.gt(0) && invoice.amount.equals(invoice.amountPaid)) {
    await tx.supplierInvoice.update({ where: { id: invoice.id }, data: { status: "PAID", paidAt: new Date() } });
  }
}
