import { Router } from "express";
import multer from "multer";
import crypto from "node:crypto";
import { env } from "../env";
import { HttpError } from "../middleware/errorHandler";
import { MAX_IMAGE_BYTES } from "../uploadLimits";

export const uploadsRouter = Router();

// The extension a photo is saved with comes from this list, never from the
// uploaded file's own name: files are served back publicly from /uploads
// with a type taken from the extension, so "photo.svg" or "photo.html"
// claiming to be an image would otherwise be served from the API's own
// address as a page - and SVG can carry script. Anything not listed - the
// app itself sends "image/*" - is saved as .jpg, as every upload was before.
const IMAGE_EXTENSIONS: Record<string, string> = {
  "image/jpeg": ".jpg",
  "image/png": ".png",
  "image/webp": ".webp",
  "image/gif": ".gif",
  "image/heic": ".heic",
  "image/heif": ".heif",
};

const storage = multer.diskStorage({
  destination: env.uploadsDir,
  filename: (_req, file, cb) => {
    cb(null, `${crypto.randomUUID()}${IMAGE_EXTENSIONS[file.mimetype.toLowerCase()] ?? ".jpg"}`);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: MAX_IMAGE_BYTES },
  fileFilter: (_req, file, cb) => {
    const type = file.mimetype.toLowerCase();
    // SVG is a document that can hold script, not a photo.
    if (!type.startsWith("image/") || type.startsWith("image/svg")) {
      cb(new HttpError(400, "Only image uploads are allowed") as unknown as Error);
      return;
    }
    cb(null, true);
  },
});

// Used for fallback products (no barcode): the app uploads a product photo
// and gets back a URL to store on the Product record.
uploadsRouter.post("/image", upload.single("image"), (req, res) => {
  if (!req.file) throw new HttpError(400, "No image file provided");
  res.status(201).json({ url: `/uploads/${req.file.filename}` });
});
