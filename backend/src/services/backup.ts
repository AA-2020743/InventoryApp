import { prisma } from "../db";

// Bumped whenever the payload shape changes in a way that would break an
// older restore path; restore doesn't currently branch on it, but it's
// recorded so a future incompatible change has something to check against.
const BACKUP_VERSION = 1;

export interface BackupPayload {
  version: number;
  exportedAt: string;
  suppliers: any[];
  products: any[];
  supplierInvoices: any[];
  sales: any[];
  saleItems: any[];
  expenses: any[];
  workingDays: any[];
  inventoryTransactions: any[];
}

// The owner's login (User) is deliberately excluded: it isn't "business
// data" the way the rest of this is, and restoring an old password hash
// over the current one could lock the owner out of their own backend.
export async function buildBackupPayload(): Promise<BackupPayload> {
  const [suppliers, products, supplierInvoices, sales, saleItems, expenses, workingDays, inventoryTransactions] =
    await Promise.all([
      prisma.supplier.findMany(),
      prisma.product.findMany(),
      prisma.supplierInvoice.findMany(),
      prisma.sale.findMany(),
      prisma.saleItem.findMany(),
      prisma.expense.findMany(),
      prisma.workingDay.findMany(),
      prisma.inventoryTransaction.findMany(),
    ]);

  return {
    version: BACKUP_VERSION,
    exportedAt: new Date().toISOString(),
    suppliers,
    products,
    supplierInvoices,
    sales,
    saleItems,
    expenses,
    workingDays,
    inventoryTransactions,
  };
}

const toDate = (v: unknown): Date | null => (v == null ? null : new Date(v as string));

// Restoring re-inserts every original id/timestamp rather than letting the
// database assign new ones, so relations between tables (SaleItem ->
// Sale/Product, InventoryTransaction -> Product/SupplierInvoice, etc.)
// keep resolving correctly exactly as they did in the backed-up database.
export async function restoreFromPayload(payload: BackupPayload): Promise<void> {
  await prisma.$transaction(
    async (tx) => {
      // Children before parents so foreign keys never point at a
      // still-existing row about to be deleted out from under them.
      await tx.inventoryTransaction.deleteMany();
      await tx.saleItem.deleteMany();
      await tx.sale.deleteMany();
      await tx.supplierInvoice.deleteMany();
      await tx.product.deleteMany();
      await tx.supplier.deleteMany();
      await tx.expense.deleteMany();
      await tx.workingDay.deleteMany();

      // Parents before children, mirroring the delete order above.
      if (payload.suppliers.length) {
        await tx.supplier.createMany({
          data: payload.suppliers.map((r) => ({ ...r, createdAt: toDate(r.createdAt) })),
        });
      }
      if (payload.products.length) {
        await tx.product.createMany({
          data: payload.products.map((r) => ({
            ...r,
            createdAt: toDate(r.createdAt),
            updatedAt: toDate(r.updatedAt),
          })),
        });
      }
      if (payload.supplierInvoices.length) {
        await tx.supplierInvoice.createMany({
          data: payload.supplierInvoices.map((r) => ({
            ...r,
            issueDate: toDate(r.issueDate),
            dueDate: toDate(r.dueDate),
            paidAt: toDate(r.paidAt),
            createdAt: toDate(r.createdAt),
          })),
        });
      }
      if (payload.sales.length) {
        await tx.sale.createMany({
          data: payload.sales.map((r) => ({ ...r, createdAt: toDate(r.createdAt) })),
        });
      }
      if (payload.saleItems.length) {
        await tx.saleItem.createMany({ data: payload.saleItems });
      }
      if (payload.inventoryTransactions.length) {
        await tx.inventoryTransaction.createMany({
          data: payload.inventoryTransactions.map((r) => ({ ...r, createdAt: toDate(r.createdAt) })),
        });
      }
      if (payload.expenses.length) {
        await tx.expense.createMany({
          data: payload.expenses.map((r) => ({
            ...r,
            startDate: toDate(r.startDate),
            endDate: toDate(r.endDate),
            createdAt: toDate(r.createdAt),
          })),
        });
      }
      if (payload.workingDays.length) {
        await tx.workingDay.createMany({
          data: payload.workingDays.map((r) => ({
            ...r,
            date: toDate(r.date),
            createdAt: toDate(r.createdAt),
          })),
        });
      }
    },
    { timeout: 60_000, maxWait: 10_000 }
  );
}
