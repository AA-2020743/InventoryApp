import type { Session } from "@prisma/client";
import { prisma } from "../db";

// How long a sign-in lasts. The token's own expiry and the row's expiresAt
// are both derived from this, so the list of devices can't disagree with
// which tokens actually still work.
export const SESSION_TTL_DAYS = 30;
const SESSION_TTL_MS = SESSION_TTL_DAYS * 24 * 60 * 60 * 1000;

// lastSeenAt is refreshed at most this often per device. Every authenticated
// request reads the session anyway; writing it back on every one of them
// would turn each screen load into several writes for a timestamp shown to
// the minute.
const TOUCH_INTERVAL_MS = 60 * 1000;

export interface DeviceInfo {
  deviceName?: string;
  deviceModel?: string;
  osVersion?: string;
  appVersion?: string;
}

// Express reports an IPv4 client on a dual-stack socket as "::ffff:1.2.3.4";
// the prefix is noise to anyone reading the address.
export function displayIp(ip: string | undefined): string | null {
  if (!ip) return null;
  return ip.startsWith("::ffff:") ? ip.slice("::ffff:".length) : ip;
}

export async function createSession(userId: string, device: DeviceInfo, ip: string | undefined): Promise<Session> {
  const now = new Date();
  // Housekeeping at the one moment a row is being added anyway: tokens that
  // have run out are already refused, so their rows only clutter the list.
  await prisma.session.deleteMany({ where: { userId, expiresAt: { lte: now } } });
  return prisma.session.create({
    data: {
      userId,
      // An app from before sessions existed sends no device details at all.
      deviceName: device.deviceName || device.deviceModel || "Unknown device",
      deviceModel: device.deviceModel || null,
      osVersion: device.osVersion || null,
      appVersion: device.appVersion || null,
      lastIp: displayIp(ip),
      lastSeenAt: now,
      expiresAt: new Date(now.getTime() + SESSION_TTL_MS),
    },
  });
}

// A session is live while its row exists and hasn't expired. Deleting the row
// is the whole of signing a device out: its token still verifies, but names
// a session that is no longer there.
export function isLive(session: Session | null, userId: string, now = new Date()): session is Session {
  return session !== null && session.userId === userId && session.expiresAt > now;
}

export async function touchSession(session: Session, ip: string | undefined): Promise<void> {
  const now = new Date();
  const address = displayIp(ip);
  const stale = now.getTime() - session.lastSeenAt.getTime() >= TOUCH_INTERVAL_MS;
  // A new address is worth recording straight away even inside the
  // interval - it's the one detail here that changes meaningfully between
  // two requests a few seconds apart.
  if (!stale && address === session.lastIp) return;
  // updateMany, not update: the row can be deleted between the read in
  // requireAuth and this write (signed out from another device), and update
  // would throw for a missing row - failing a request that was valid when it
  // arrived.
  await prisma.session.updateMany({
    where: { id: session.id },
    data: { lastSeenAt: now, lastIp: address },
  });
}
