import jwt from "jsonwebtoken";
import { prisma } from "../db";
import { env } from "../env";
import { isLive, touchSession } from "../services/sessions";
import { asyncHandler, HttpError } from "./errorHandler";

// What a token carries. sid names the device's row in Session; a token
// without one was issued before sessions existed.
export interface TokenPayload {
  userId: string;
  email: string;
  sid?: string;
}

export interface AuthPayload {
  userId: string;
  email: string;
  sessionId: string;
}

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      user?: AuthPayload;
    }
  }
}

// A valid signature is no longer enough: the token must also name a session
// that still exists. That's what makes signing a device out from another one
// take effect immediately rather than when its token would have expired.
//
// Tokens from before sessions existed carry no sid and are refused. The app
// treats any 401 as "sign in again", so each device signs in once after the
// upgrade and from then on appears in the list - which is better than
// letting unlisted tokens keep working, since a device you can't see is a
// device you can't sign out.
export const requireAuth = asyncHandler(async (req, _res, next) => {
  const header = req.headers.authorization;
  if (!header?.startsWith("Bearer ")) {
    throw new HttpError(401, "Missing or invalid Authorization header");
  }
  const token = header.slice("Bearer ".length);

  let payload: TokenPayload;
  try {
    payload = jwt.verify(token, env.jwtSecret) as TokenPayload;
  } catch {
    throw new HttpError(401, "Invalid or expired token");
  }
  if (!payload.sid) {
    throw new HttpError(401, "Please sign in again");
  }

  const session = await prisma.session.findUnique({ where: { id: payload.sid } });
  if (!isLive(session, payload.userId)) {
    throw new HttpError(401, "This device has been signed out");
  }
  await touchSession(session, req.ip);

  req.user = { userId: payload.userId, email: payload.email, sessionId: session.id };
  next();
});
