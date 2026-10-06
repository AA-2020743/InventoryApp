import type { NextFunction, Request, Response } from "express";
import { MulterError } from "multer";
import { ZodError } from "zod";
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
    res.status(400).json({ error: "Validation failed", details: err.flatten() });
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
  console.error(err);
  res.status(500).json({ error: "Internal server error" });
}
