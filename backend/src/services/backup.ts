import fs from "node:fs";
import path from "node:path";
import AdmZip from "adm-zip";
import { prisma } from "../db";
import { env } from "../env";

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
  inventoryTransactions: any[];
  assets: any[];
  cashRegisterEntries: any[];
  businessSettings: any | null;
}

// The owner's login (User) is deliberately excluded: it isn't "business
// data" the way the rest of this is, and restoring an old password hash
// over the current one could lock the owner out of their own backend.
export async function buildBackupPayload(): Promise<BackupPayload> {
  const [suppliers, products, supplierInvoices, sales, saleItems, expenses, inventoryTransactions, assets, cashRegisterEntries, businessSettings] =
    await Promise.all([
      prisma.supplier.findMany(),
      prisma.product.findMany(),
      prisma.supplierInvoice.findMany(),
      prisma.sale.findMany(),
      prisma.saleItem.findMany(),
      prisma.expense.findMany(),
      prisma.inventoryTransaction.findMany(),
      prisma.asset.findMany(),
      prisma.cashRegisterEntry.findMany(),
      prisma.businessSettings.findFirst(),
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
    inventoryTransactions,
    assets,
    cashRegisterEntries,
    businessSettings,
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
      await tx.asset.deleteMany();
      await tx.cashRegisterEntry.deleteMany();
      await tx.businessSettings.deleteMany();

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
          data: payload.sales.map((r) => ({
            ...r,
            createdAt: toDate(r.createdAt),
            collectedAt: toDate(r.collectedAt),
          })),
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
            date: toDate(r.date),
            createdAt: toDate(r.createdAt),
          })),
        });
      }
      if (payload.assets.length) {
        await tx.asset.createMany({
          data: payload.assets.map((r) => ({
            ...r,
            createdAt: toDate(r.createdAt),
            updatedAt: toDate(r.updatedAt),
          })),
        });
      }
      if (payload.cashRegisterEntries.length) {
        await tx.cashRegisterEntry.createMany({
          data: payload.cashRegisterEntries.map((r) => ({ ...r, createdAt: toDate(r.createdAt) })),
        });
      }
      // Older backups predate this setting, so it may be missing entirely
      // rather than just null.
      if (payload.businessSettings) {
        await tx.businessSettings.create({
          data: { ...payload.businessSettings, updatedAt: toDate(payload.businessSettings.updatedAt) ?? new Date() },
        });
      }
    },
    { timeout: 60_000, maxWait: 10_000 }
  );
}

const ARCHIVE_DATA_ENTRY = "data.json";
const ARCHIVE_UPLOADS_PREFIX = "uploads/";

// The JSON export only ever covered database rows - a product's imageUrl
// would survive a restore, but the actual photo file it points to wouldn't.
// Bundling uploads/ into the same archive means a restore brings images
// back too, not just the row that references them.
export async function buildBackupArchive(): Promise<{ buffer: Buffer; exportedAt: string }> {
  const payload = await buildBackupPayload();
  const zip = new AdmZip();
  zip.addFile(ARCHIVE_DATA_ENTRY, Buffer.from(JSON.stringify(payload)));

  const uploadsDir = path.resolve(env.uploadsDir);
  if (fs.existsSync(uploadsDir)) {
    for (const filename of fs.readdirSync(uploadsDir)) {
      const filePath = path.join(uploadsDir, filename);
      if (fs.statSync(filePath).isFile()) {
        zip.addLocalFile(filePath, "uploads");
      }
    }
  }

  return { buffer: zip.toBuffer(), exportedAt: payload.exportedAt };
}

// Mirrors restoreFromPayload's full wipe-and-replace semantics: every file
// currently in uploads/ is removed before the archive's own files are
// written, so a restore doesn't leave orphaned images from the state it's
// replacing mixed in with the restored ones.
export async function restoreFromArchive(buffer: Buffer): Promise<void> {
  const zip = new AdmZip(buffer);
  const dataEntry = zip.getEntry(ARCHIVE_DATA_ENTRY);
  if (!dataEntry) {
    throw new Error("Backup archive is missing data.json");
  }
  const payload = JSON.parse(dataEntry.getData().toString("utf-8")) as BackupPayload;
  await restoreFromPayload(payload);

  const uploadsDir = path.resolve(env.uploadsDir);
  fs.mkdirSync(uploadsDir, { recursive: true });
  for (const existing of fs.readdirSync(uploadsDir)) {
    fs.rmSync(path.join(uploadsDir, existing), { force: true });
  }
  for (const entry of zip.getEntries()) {
    if (entry.isDirectory || !entry.entryName.startsWith(ARCHIVE_UPLOADS_PREFIX)) continue;
    const filename = entry.entryName.slice(ARCHIVE_UPLOADS_PREFIX.length);
    if (!filename) continue;
    fs.writeFileSync(path.join(uploadsDir, filename), entry.getData());
  }
}
