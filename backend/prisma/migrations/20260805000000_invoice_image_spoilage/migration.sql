-- AlterEnum
ALTER TYPE "InventoryTransactionType" ADD VALUE 'SPOILAGE';

-- AlterTable
ALTER TABLE "SupplierInvoice" ADD COLUMN "imageUrl" TEXT;
