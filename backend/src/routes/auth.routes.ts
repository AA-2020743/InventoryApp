import { Router } from "express";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import rateLimit from "express-rate-limit";
import { z } from "zod";
import { prisma } from "../db";
import { env } from "../env";
import { asyncHandler, HttpError } from "../middleware/errorHandler";
import { requireAuth } from "../middleware/auth";
import { createSession, SESSION_TTL_DAYS } from "../services/sessions";

export const authRouter = Router();

// Single-owner app with one valid credential pair - the only thing a login
// rate limit needs to stop is brute-forcing the password, not distinguish
// legitimate traffic patterns. Keyed by IP (express-rate-limit's default),
// which is what actually bounds an attacker's guess rate here.
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: "Too many login attempts. Try again later." },
});

// Device details are optional so an app from before sessions existed can
// still sign in; it shows up in the list as "Unknown device". Capped in
// length because they're free text from the client and get shown back.
const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
  deviceName: z.string().trim().max(100).optional(),
  deviceModel: z.string().trim().max(100).optional(),
  osVersion: z.string().trim().max(50).optional(),
  appVersion: z.string().trim().max(30).optional(),
});

authRouter.post(
  "/login",
  loginLimiter,
  asyncHandler(async (req, res) => {
    const { email, password, ...device } = loginSchema.parse(req.body);

    const user = await prisma.user.findUnique({ where: { email } });
    if (!user) {
      throw new HttpError(401, "Invalid email or password");
    }

    const valid = await bcrypt.compare(password, user.passwordHash);
    if (!valid) {
      throw new HttpError(401, "Invalid email or password");
    }

    const session = await createSession(user.id, device, req.ip);
    const token = jwt.sign({ userId: user.id, email: user.email, sid: session.id }, env.jwtSecret, {
      expiresIn: `${SESSION_TTL_DAYS}d`,
    });

    res.json({ token, user: { id: user.id, email: user.email, name: user.name } });
  })
);

const changePasswordSchema = z.object({
  currentPassword: z.string().min(1),
  newPassword: z.string().min(6),
});

// Changing the password signs every other device out. The usual reason to
// change it is suspecting someone else knows it, and a new password that
// leaves their existing sign-in working would change nothing. The device
// making the change stays signed in.
authRouter.post(
  "/change-password",
  requireAuth,
  asyncHandler(async (req, res) => {
    const { userId, sessionId } = req.user!;
    const { currentPassword, newPassword } = changePasswordSchema.parse(req.body);
    const user = await prisma.user.findUnique({ where: { id: userId } });
    if (!user) throw new HttpError(404, "User not found");

    const valid = await bcrypt.compare(currentPassword, user.passwordHash);
    if (!valid) throw new HttpError(401, "Current password is incorrect");

    const passwordHash = await bcrypt.hash(newPassword, 10);
    const [, signedOut] = await prisma.$transaction([
      prisma.user.update({ where: { id: user.id }, data: { passwordHash } }),
      prisma.session.deleteMany({ where: { userId, id: { not: sessionId } } }),
    ]);

    res.json({ success: true, signedOutDevices: signedOut.count });
  })
);

// Every device currently signed in, most recently active first, with the
// one asking marked so the app can tell "this phone" from the rest.
authRouter.get(
  "/sessions",
  requireAuth,
  asyncHandler(async (req, res) => {
    const { userId, sessionId } = req.user!;
    const sessions = await prisma.session.findMany({
      where: { userId, expiresAt: { gt: new Date() } },
      orderBy: { lastSeenAt: "desc" },
    });
    res.json(
      sessions.map((s) => ({
        id: s.id,
        deviceName: s.deviceName,
        deviceModel: s.deviceModel,
        osVersion: s.osVersion,
        appVersion: s.appVersion,
        createdAt: s.createdAt,
        lastSeenAt: s.lastSeenAt,
        lastIp: s.lastIp,
        current: s.id === sessionId,
      }))
    );
  })
);

// Signs out every device except the one asking.
authRouter.delete(
  "/sessions",
  requireAuth,
  asyncHandler(async (req, res) => {
    const { userId, sessionId } = req.user!;
    const { count } = await prisma.session.deleteMany({ where: { userId, id: { not: sessionId } } });
    res.json({ signedOutDevices: count });
  })
);

// Signs out one device. Scoped to the caller's own sessions, so an id from
// someone else's account reads as not found rather than being acted on.
authRouter.delete(
  "/sessions/:id",
  requireAuth,
  asyncHandler(async (req, res) => {
    const { userId } = req.user!;
    const { count } = await prisma.session.deleteMany({ where: { id: req.params.id, userId } });
    if (count === 0) throw new HttpError(404, "Session not found");
    res.json({ success: true });
  })
);

// Signing out from the app ends this device's session on the server too,
// rather than only forgetting the token locally - otherwise a signed-out
// phone would sit in the device list for the rest of the month.
authRouter.post(
  "/logout",
  requireAuth,
  asyncHandler(async (req, res) => {
    await prisma.session.deleteMany({ where: { id: req.user!.sessionId } });
    res.json({ success: true });
  })
);

