-- Supplier invoice payments now always try to pay themselves out of the
-- cash register (mirroring expenses); this records the shortfall when the
-- register can't cover the full amount.
ALTER TABLE "SupplierInvoice" ADD COLUMN "deficitAmount" DECIMAL(12,2) NOT NULL DEFAULT 0;
