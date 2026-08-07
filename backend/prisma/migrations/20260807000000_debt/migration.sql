-- CreateEnum
CREATE TYPE "DebtStatus" AS ENUM ('OUTSTANDING', 'REPAID');

-- CreateTable
CREATE TABLE "Debt" (
    "id" TEXT NOT NULL,
    "workerName" TEXT NOT NULL,
    "amount" DECIMAL(12,2) NOT NULL,
    "amountRepaid" DECIMAL(12,2) NOT NULL DEFAULT 0,
    "status" "DebtStatus" NOT NULL DEFAULT 'OUTSTANDING',
    "deficitAmount" DECIMAL(12,2) NOT NULL DEFAULT 0,
    "notes" TEXT,
    "repaidAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Debt_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Debt_createdAt_idx" ON "Debt"("createdAt");

-- AlterTable
ALTER TABLE "CashRegisterEntry" ADD COLUMN "debtId" TEXT;
ALTER TABLE "CashRegisterEntry" ADD COLUMN "isPartialDebtRepayment" BOOLEAN NOT NULL DEFAULT false;
