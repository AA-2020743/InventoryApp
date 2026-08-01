import { Router } from "express";
import multer from "multer";
import path from "node:path";
import crypto from "node:crypto";
import { env } from "../env";
import { HttpError } from "../middleware/errorHandler";

export const uploadsRouter = Router();

const storage = multer.diskStorage({
  destination: env.uploadsDir,
  filename: (_req, file, cb) => {
    const ext = path.extname(file.originalname) || ".jpg";
    cb(null, `${crypto.randomUUID()}${ext}`);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: 8 * 1024 * 1024 },
  fileFilter: (_req, file, cb) => {
    if (!file.mimetype.startsWith("image/")) {
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
