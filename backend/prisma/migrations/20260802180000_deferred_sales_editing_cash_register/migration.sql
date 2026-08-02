-- AlterEnum
ALTER TYPE "InventoryTransactionType" ADD VALUE 'SALE_CORRECTION';

-- CreateEnum
CREATE TYPE "SalePaymentStatus" AS ENUM ('PAID', 'DEFERRED');

-- AlterTable
ALTER TABLE "Sale" ADD COLUMN "paymentStatus" "SalePaymentStatus" NOT NULL DEFAULT 'PAID';
ALTER TABLE "Sale" ADD COLUMN "customerName" TEXT;
ALTER TABLE "Sale" ADD COLUMN "collectedAt" TIMESTAMP(3);

-- CreateTable
CREATE TABLE "CashRegisterEntry" (
    "id" TEXT NOT NULL,
    "amount" DECIMAL(12,2) NOT NULL,
    "note" TEXT,
    "invoiceId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CashRegisterEntry_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "CashRegisterEntry_createdAt_idx" ON "CashRegisterEntry"("createdAt");
