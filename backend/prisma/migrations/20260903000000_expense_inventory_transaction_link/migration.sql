-- Back-reference from a spoilage write-off expense to the SPOILAGE stock
-- movement that created it, so undoing the spoilage can remove exactly that
-- expense. Null on every other expense.
ALTER TABLE "Expense" ADD COLUMN "inventoryTransactionId" TEXT;

CREATE INDEX "Expense_inventoryTransactionId_idx" ON "Expense"("inventoryTransactionId");
