-- Marks invoices whose amount is the sum of their stock lines, so the
-- recalculation only touches those and leaves older typed-amount invoices
-- (which have no lines) exactly as they are.
ALTER TABLE "SupplierInvoice" ADD COLUMN "amountFromLines" BOOLEAN NOT NULL DEFAULT false;
