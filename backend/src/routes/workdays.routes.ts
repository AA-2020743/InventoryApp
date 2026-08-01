import { Router } from "express";
import { z } from "zod";
import { prisma } from "../db";
import { dateOnlyKey } from "../utils/dates";
import { asyncHandler } from "../middleware/errorHandler";

export const workdaysRouter = Router();

// A day with no row is assumed to be a working day; `answered` tells the
// client whether the owner has actually confirmed today yet, so it knows
// whether to prompt ("Is today a working day?").
workdaysRouter.get(
  "/today",
  asyncHandler(async (_req, res) => {
    const today = dateOnlyKey();
    const record = await prisma.workingDay.findUnique({ where: { date: today } });
    res.json({
      date: today.toISOString().slice(0, 10),
      isWorking: record?.isWorking ?? true,
      answered: record !== null,
    });
  })
);

const setTodaySchema = z.object({ isWorking: z.boolean() });

workdaysRouter.post(
  "/today",
  asyncHandler(async (req, res) => {
    const { isWorking } = setTodaySchema.parse(req.body);
    const today = dateOnlyKey();
    const record = await prisma.workingDay.upsert({
      where: { date: today },
      create: { date: today, isWorking },
      update: { isWorking },
    });
    res.json({
      date: today.toISOString().slice(0, 10),
      isWorking: record.isWorking,
      answered: true,
    });
  })
);
