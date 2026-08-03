-- Drop the recurring-expense system (frequency/payment-day/active/endDate)
-- in favor of a flat one-off expense with an always-on cash deduction and
-- a persisted deficit when the register can't cover it in full.
ALTER TABLE "Expense" DROP COLUMN "active";
ALTER TABLE "Expense" DROP COLUMN "endDate";
ALTER TABLE "Expense" DROP COLUMN "paymentDayOfMonth";
ALTER TABLE "Expense" DROP COLUMN "frequency";
DROP TYPE "ExpenseFrequency";

ALTER TABLE "Expense" RENAME COLUMN "startDate" TO "date";
ALTER TABLE "Expense" ADD COLUMN "deficitAmount" DECIMAL(12,2) NOT NULL DEFAULT 0;

-- The "was the shop open today?" concept no longer applies now that DAILY
-- recurring expenses (the only thing it gated) are gone.
DROP TABLE "WorkingDay";
