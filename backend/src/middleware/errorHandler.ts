import type { NextFunction, Request, Response } from "express";
import { Prisma } from "@prisma/client";
import { MulterError } from "multer";
import { ZodError, z } from "zod";
import { MAX_IMAGE_BYTES } from "../uploadLimits";

export class HttpError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

// Wraps async route handlers so rejected promises reach the error handler
// instead of crashing the process.
export function asyncHandler<T extends (req: Request, res: Response, next: NextFunction) => Promise<unknown>>(
  fn: T
) {
  return (req: Request, res: Response, next: NextFunction) => {
    fn(req, res, next).catch(next);
  };
}

export function errorHandler(err: unknown, req: Request, res: Response, _next: NextFunction) {
  if (err instanceof ZodError) {
    res.status(400).json({ error: "Validation failed", details: z.flattenError(err) });
    return;
  }
  if (err instanceof HttpError) {
    res.status(err.status).json({ error: err.message });
    return;
  }
  // Rejections multer makes on the client's behalf - a photo over the size
  // cap, a wrong field name. These used to fall through to the 500 below, so
  // an oversized photo reached the app as "Internal server error", which
  // reads as the server breaking rather than the file being too big.
  if (err instanceof MulterError) {
    if (err.code === "LIMIT_FILE_SIZE") {
      const limitMb = Math.round(MAX_IMAGE_BYTES / (1024 * 1024));
      res.status(413).json({ error: `That image is too large. The limit is ${limitMb} MB.` });
      return;
    }
    res.status(400).json({ error: err.message });
    return;
  }
  // The body parser's own rejections - a body that isn't valid JSON, or one
  // over the size limit - are the client's mistake, and it marks them so
  // (status 4xx, expose). They used to reach the 500 below, so a malformed
  // request read as the server breaking.
  const parserError = err as { status?: unknown; expose?: unknown; type?: unknown };
  if (typeof parserError.status === "number" && parserError.status >= 400 && parserError.status < 500 && parserError.expose === true) {
    const message =
      parserError.type === "entity.parse.failed"
        ? "The request body isn't valid JSON"
        : parserError.type === "entity.too.large"
          ? "The request body is too large"
          : "Bad request";
    res.status(parserError.status).json({ error: message });
    return;
  }
  // A record that was deleted between being looked up and being written -
  // say, from another phone - is a 404, not a server fault.
  if (err instanceof Prisma.PrismaClientKnownRequestError && err.code === "P2025") {
    res.status(404).json({ error: "That record no longer exists" });
    return;
  }
  console.error(err);
  res.status(500).json({ error: "Internal server error" });
}
