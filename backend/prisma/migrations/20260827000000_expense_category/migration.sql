-- Optional free-text grouping for expenses, mirroring OtherSale.category.
ALTER TABLE "Expense" ADD COLUMN "category" TEXT;

-- Existing rows keep NULL (uncategorized) - the app groups those under an
-- "Uncategorized" bucket rather than requiring a backfill.
CREATE INDEX "Expense_category_idx" ON "Expense"("category");
