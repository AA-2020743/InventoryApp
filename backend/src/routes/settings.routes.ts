import { Router } from "express";
import { z } from "zod";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler } from "../middleware/errorHandler";

export const settingsRouter = Router();

// There's only ever one settings row - read/written via this fixed id
// instead of looking one up.
const SETTINGS_ID = "singleton";

settingsRouter.get(
  "/",
  asyncHandler(async (_req, res) => {
    const settings = await prisma.businessSettings.findUnique({ where: { id: SETTINGS_ID } });
    res.json({ startingValue: settings?.startingValue ?? new Prisma.Decimal(0) });
  })
);

const updateSettingsInput = z.object({
  startingValue: z.number().min(0),
});

settingsRouter.put(
  "/",
  asyncHandler(async (req, res) => {
    const { startingValue } = updateSettingsInput.parse(req.body);
    const settings = await prisma.businessSettings.upsert({
      where: { id: SETTINGS_ID },
      create: { id: SETTINGS_ID, startingValue },
      update: { startingValue },
    });
    res.json({ startingValue: settings.startingValue });
  })
);
