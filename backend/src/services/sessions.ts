import { Prisma, type Session } from "@prisma/client";
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
  installId?: string | null;
  deviceName?: string | null;
  deviceModel?: string | null;
  osVersion?: string | null;
  appVersion?: string | null;
}

// Express reports an IPv4 client on a dual-stack socket as "::ffff:1.2.3.4";
// the prefix is noise to anyone reading the address.
export function displayIp(ip: string | undefined): string | null {
  if (!ip) return null;
  return ip.startsWith("::ffff:") ? ip.slice("::ffff:".length) : ip;
}

function deviceLabel(device: DeviceInfo): string {
  // An app from before sessions existed sends no device details at all.
  return device.deviceName || device.deviceModel || "Unknown device";
}

// Records the install as known, and says whether this is the first time it
// has been seen. An install that has signed in before - even if it signed
// out in between - is not new.
async function recordDevice(userId: string, device: DeviceInfo, ip: string | undefined): Promise<boolean> {
  const data = {
    userId,
    installId: device.installId || null,
    deviceName: deviceLabel(device),
    deviceModel: device.deviceModel || null,
    osVersion: device.osVersion || null,
    firstIp: displayIp(ip),
  };
  if (!device.installId) {
    // Nothing to recognise it by, so every sign-in from it is reported.
    await prisma.knownDevice.create({ data });
    return true;
  }
  const existing = await prisma.knownDevice.findUnique({
    where: { userId_installId: { userId, installId: device.installId } },
  });
  if (existing) return false;
  try {
    await prisma.knownDevice.create({ data });
    return true;
  } catch (e) {
    // Two first sign-ins from one install racing: the other one recorded it.
    if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === "P2002") return false;
    throw e;
  }
}

export async function createSession(userId: string, device: DeviceInfo, ip: string | undefined): Promise<Session> {
  const now = new Date();
  // Housekeeping at the one moment a row is being added anyway: tokens that
  // have run out are already refused, so their rows only clutter the list.
  await prisma.session.deleteMany({ where: { userId, expiresAt: { lte: now } } });
  const newDevice = await recordDevice(userId, device, ip);
  if (device.installId) {
    // One entry per phone. A phone signing in again means it no longer holds
    // its previous token - it was signed out locally, or reinstalled - so
    // that session can never be used again and would only sit in the list
    // as a second copy of the same device.
    await prisma.session.deleteMany({ where: { userId, installId: device.installId } });
  }
  return prisma.session.create({
    data: {
      userId,
      installId: device.installId || null,
      newDevice,
      deviceName: deviceLabel(device),
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

// The app reports its install and details each time it starts while signed
// in. Two things depend on it:
//
// - A session signed in by an app too old to send an install id has no way
//   to be recognised. Once that phone runs an app that does report one, the
//   session is attached to its install here, and any other session already
//   held by the same install is removed, so the phone is listed once.
// - Details recorded at sign-in go stale - the app is updated, the phone is
//   renamed - and this keeps the list showing what the phone is now.
//
// Attaching an install records it as known without raising a new-device
// alert (adopted): the sign-in it belongs to happened earlier and was
// already reported at the time.
export async function checkIn(session: Session, device: DeviceInfo): Promise<void> {
  const { userId } = session;
  const installId = device.installId || null;
  let recognised = false;

  if (installId && installId !== session.installId) {
    const existing = await prisma.knownDevice.findUnique({
      where: { userId_installId: { userId, installId } },
    });
    // Seen before this session existed, so not a new device after all.
    recognised = existing !== null;
    if (!existing) {
      try {
        await prisma.knownDevice.create({
          data: {
            userId,
            installId,
            deviceName: deviceLabel(device),
            deviceModel: device.deviceModel || null,
            osVersion: device.osVersion || null,
            firstIp: session.lastIp,
            firstSeenAt: session.createdAt,
            adopted: true,
          },
        });
      } catch (e) {
        if (!(e instanceof Prisma.PrismaClientKnownRequestError && e.code === "P2002")) throw e;
      }
    }
  }
  if (installId) {
    await prisma.session.deleteMany({ where: { userId, installId, id: { not: session.id } } });
  }

  // updateMany so a session signed out mid-request doesn't fail it.
  await prisma.session.updateMany({
    where: { id: session.id },
    data: {
      ...(installId ? { installId } : {}),
      ...(device.deviceName || device.deviceModel ? { deviceName: deviceLabel(device) } : {}),
      ...(device.deviceModel ? { deviceModel: device.deviceModel } : {}),
      ...(device.osVersion ? { osVersion: device.osVersion } : {}),
      ...(device.appVersion ? { appVersion: device.appVersion } : {}),
      ...(recognised ? { newDevice: false } : {}),
    },
  });
}
