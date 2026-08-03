-- Restocks (not linked to a supplier invoice) now always try to pay
-- themselves out of the till, mirroring Expense/SupplierInvoice.
ALTER TABLE "InventoryTransaction" ADD COLUMN "deficitAmount" DECIMAL(12,2) NOT NULL DEFAULT 0;

-- Partial repayment tracking on deferred sales.
ALTER TABLE "Sale" ADD COLUMN "amountCollected" DECIMAL(12,2) NOT NULL DEFAULT 0;

-- New cash-register links + the partial-collection flag.
ALTER TABLE "CashRegisterEntry" ADD COLUMN "inventoryTransactionId" TEXT;
ALTER TABLE "CashRegisterEntry" ADD COLUMN "otherSaleId" TEXT;
ALTER TABLE "CashRegisterEntry" ADD COLUMN "isPartialSaleCollection" BOOLEAN NOT NULL DEFAULT false;

-- Miscellaneous profit entries not tied to inventory or a checkout sale.
CREATE TABLE "OtherSale" (
    "id" TEXT NOT NULL,
    "amount" DECIMAL(12,2) NOT NULL,
    "category" TEXT,
    "notes" TEXT,
    "date" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "OtherSale_pkey" PRIMARY KEY ("id")
);
