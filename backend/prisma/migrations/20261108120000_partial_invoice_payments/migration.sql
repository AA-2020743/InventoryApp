-- Supplier invoices can be paid in parts. Every existing invoice starts at
-- nothing paid in parts: a pending one is still owed in full, and a paid one
-- was settled by its single payment, exactly as before.
ALTER TABLE "SupplierInvoice" ADD COLUMN "amountPaid" DECIMAL(12,2) NOT NULL DEFAULT 0;

-- Marks the till entries that are part payments, so an invoice's own
-- settling entry can still be found on its own.
ALTER TABLE "CashRegisterEntry" ADD COLUMN "isPartialInvoicePayment" BOOLEAN NOT NULL DEFAULT false;
