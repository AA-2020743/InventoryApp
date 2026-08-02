import { Router } from "express";
import { z } from "zod";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";

export const assetsRouter = Router();

const assetInput = z.object({
  name: z.string().trim().min(1),
  value: z.number().nonnegative(),
  category: z.string().trim().optional().nullable(),
  notes: z.string().trim().optional().nullable(),
});

assetsRouter.get(
  "/",
  asyncHandler(async (_req, res) => {
    const assets = await prisma.asset.findMany({ orderBy: { createdAt: "desc" } });
    res.json(assets);
  })
);

assetsRouter.post(
  "/",
  asyncHandler(async (req, res) => {
    const data = assetInput.parse(req.body);
    const asset = await prisma.asset.create({ data });
    res.status(201).json(asset);
  })
);

assetsRouter.put(
  "/:id",
  asyncHandler(async (req, res) => {
    const data = assetInput.partial().parse(req.body);
    const existing = await prisma.asset.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Asset not found");
    const asset = await prisma.asset.update({ where: { id: req.params.id }, data });
    res.json(asset);
  })
);

assetsRouter.delete(
  "/:id",
  asyncHandler(async (req, res) => {
    const existing = await prisma.asset.findUnique({ where: { id: req.params.id } });
    if (!existing) throw new HttpError(404, "Asset not found");
    await prisma.asset.delete({ where: { id: req.params.id } });
    res.status(204).send();
  })
);
