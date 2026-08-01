import { Router } from "express";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { z } from "zod";
import { prisma } from "../db";
import { env } from "../env";
import { asyncHandler, HttpError } from "../middleware/errorHandler";

export const authRouter = Router();

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
});

authRouter.post(
  "/login",
  asyncHandler(async (req, res) => {
    const { email, password } = loginSchema.parse(req.body);

    const user = await prisma.user.findUnique({ where: { email } });
    if (!user) {
      throw new HttpError(401, "Invalid email or password");
    }

    const valid = await bcrypt.compare(password, user.passwordHash);
    if (!valid) {
      throw new HttpError(401, "Invalid email or password");
    }

    const token = jwt.sign({ userId: user.id, email: user.email }, env.jwtSecret, {
      expiresIn: "30d",
    });

    res.json({ token, user: { id: user.id, email: user.email, name: user.name } });
  })
);

const changePasswordSchema = z.object({
  currentPassword: z.string().min(1),
  newPassword: z.string().min(6),
});

authRouter.post(
  "/change-password",
  asyncHandler(async (req, res) => {
    // Auth is required for this one, applied at mount time in index.ts is
    // not possible since /login must stay public; handled inline instead.
    const header = req.headers.authorization;
    if (!header?.startsWith("Bearer ")) {
      throw new HttpError(401, "Missing or invalid Authorization header");
    }
    const token = header.slice("Bearer ".length);
    let payload: { userId: string };
    try {
      payload = jwt.verify(token, env.jwtSecret) as { userId: string };
    } catch {
      throw new HttpError(401, "Invalid or expired token");
    }

    const { currentPassword, newPassword } = changePasswordSchema.parse(req.body);
    const user = await prisma.user.findUnique({ where: { id: payload.userId } });
    if (!user) throw new HttpError(404, "User not found");

    const valid = await bcrypt.compare(currentPassword, user.passwordHash);
    if (!valid) throw new HttpError(401, "Current password is incorrect");

    const passwordHash = await bcrypt.hash(newPassword, 10);
    await prisma.user.update({ where: { id: user.id }, data: { passwordHash } });

    res.json({ success: true });
  })
);
