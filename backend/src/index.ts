import express from "express";
import cors from "cors";
import path from "node:path";
import fs from "node:fs";
import { env } from "./env";
import { authRouter } from "./routes/auth.routes";
import { productsRouter } from "./routes/products.routes";
import { suppliersRouter } from "./routes/suppliers.routes";
import { invoicesRouter } from "./routes/invoices.routes";
import { salesRouter } from "./routes/sales.routes";
import { expensesRouter } from "./routes/expenses.routes";
import { otherSalesRouter } from "./routes/otherSales.routes";
import { assetsRouter } from "./routes/assets.routes";
import { debtsRouter } from "./routes/debts.routes";
import { cashRegisterRouter } from "./routes/cashRegister.routes";
import { dashboardRouter } from "./routes/dashboard.routes";
import { statsRouter } from "./routes/stats.routes";
import { alertsRouter } from "./routes/alerts.routes";
import { uploadsRouter } from "./routes/uploads.routes";
import { settingsRouter } from "./routes/settings.routes";
import { backupRouter } from "./routes/backup.routes";
import { scheduleBackups } from "./services/backupScheduler";
import { requireAuth } from "./middleware/auth";
import { errorHandler } from "./middleware/errorHandler";

fs.mkdirSync(env.uploadsDir, { recursive: true });

const app = express();

app.use(cors());
app.use(express.json());
app.use("/uploads", express.static(path.resolve(env.uploadsDir)));

app.get("/health", (_req, res) => res.json({ status: "ok" }));

// /auth/login must stay public; everything else requires a valid JWT.
app.use("/api/auth", authRouter);
app.use("/api/uploads", requireAuth, uploadsRouter);
app.use("/api/products", requireAuth, productsRouter);
app.use("/api/suppliers", requireAuth, suppliersRouter);
app.use("/api/invoices", requireAuth, invoicesRouter);
app.use("/api/sales", requireAuth, salesRouter);
app.use("/api/expenses", requireAuth, expensesRouter);
app.use("/api/other-sales", requireAuth, otherSalesRouter);
app.use("/api/assets", requireAuth, assetsRouter);
app.use("/api/debts", requireAuth, debtsRouter);
app.use("/api/cash-register", requireAuth, cashRegisterRouter);
app.use("/api/dashboard", requireAuth, dashboardRouter);
app.use("/api/stats", requireAuth, statsRouter);
app.use("/api/alerts", requireAuth, alertsRouter);
app.use("/api/settings", requireAuth, settingsRouter);
app.use("/api/backup", requireAuth, backupRouter);

app.use(errorHandler);

app.listen(env.port, env.host, () => {
  console.log(`Inventory app backend listening on ${env.host}:${env.port}`);
});

scheduleBackups();
