import "dotenv/config";

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

export const env = {
  port: parseInt(process.env.PORT ?? "4000", 10),
  host: process.env.HOST ?? "0.0.0.0",
  databaseUrl: required("DATABASE_URL"),
  jwtSecret: required("JWT_SECRET"),
  uploadsDir: process.env.UPLOADS_DIR ?? "uploads",
  lowStockAlertOnly: process.env.LOW_STOCK_ALERT_ONLY === "true",
  invoiceReminderDays: parseInt(process.env.INVOICE_REMINDER_DAYS ?? "7", 10),
};
