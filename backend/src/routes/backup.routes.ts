import { Router } from "express";
import { z } from "zod";
import { asyncHandler } from "../middleware/errorHandler";
import { buildBackupPayload, restoreFromPayload, type BackupPayload } from "../services/backup";

export const backupRouter = Router();

backupRouter.get(
  "/export",
  asyncHandler(async (_req, res) => {
    const payload = await buildBackupPayload();
    const filename = `inventory-backup-${payload.exportedAt.slice(0, 10)}.json`;
    res.setHeader("Content-Disposition", `attachment; filename="${filename}"`);
    res.json(payload);
  })
);

// Row-level shape isn't strictly validated here (a full per-model zod
// schema would duplicate the Prisma schema) - this only confirms a backup
// file produced by our own /export endpoint was uploaded, not a random
// unrelated JSON blob. Restoring is inherently a "trust this file" action.
const backupPayloadSchema = z.object({
  version: z.number(),
  exportedAt: z.string(),
  suppliers: z.array(z.record(z.any())),
  products: z.array(z.record(z.any())),
  supplierInvoices: z.array(z.record(z.any())),
  sales: z.array(z.record(z.any())),
  saleItems: z.array(z.record(z.any())),
  expenses: z.array(z.record(z.any())),
  workingDays: z.array(z.record(z.any())),
  inventoryTransactions: z.array(z.record(z.any())),
});

backupRouter.post(
  "/restore",
  asyncHandler(async (req, res) => {
    const payload = backupPayloadSchema.parse(req.body) as BackupPayload;
    await restoreFromPayload(payload);
    res.json({ success: true, restoredAt: new Date().toISOString() });
  })
);
