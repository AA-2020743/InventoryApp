-- AlterTable
ALTER TABLE "Product" ADD COLUMN     "packageLabel" TEXT,
ADD COLUMN     "unitsPerPackage" DECIMAL(12,3) NOT NULL DEFAULT 1;
