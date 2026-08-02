import { Router } from "express";
import express from "express";
import { asyncHandler } from "../middleware/errorHandler";
import { buildBackupArchive, restoreFromArchive } from "../services/backup";

export const backupRouter = Router();

backupRouter.get(
  "/export",
  asyncHandler(async (_req, res) => {
    const { buffer, exportedAt } = await buildBackupArchive();
    const filename = `inventory-backup-${exportedAt.slice(0, 10)}.zip`;
    res.setHeader("Content-Type", "application/zip");
    res.setHeader("Content-Disposition", `attachment; filename="${filename}"`);
    res.send(buffer);
  })
);

// The archive's data.json isn't strictly schema-validated here (a full
// per-model zod schema would duplicate the Prisma schema) - restoring is
// inherently a "trust this file" action, same as before this was zipped.
backupRouter.post(
  "/restore",
  express.raw({ type: "application/zip", limit: "200mb" }),
  asyncHandler(async (req, res) => {
    await restoreFromArchive(req.body as Buffer);
    res.json({ success: true, restoredAt: new Date().toISOString() });
  })
);
