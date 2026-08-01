import fs from "node:fs";
import path from "node:path";
import { env } from "../env";
import { buildBackupPayload } from "./backup";

const DAY_MS = 24 * 60 * 60 * 1000;

async function runBackup(): Promise<void> {
  const dir = path.resolve(env.backupDir);
  fs.mkdirSync(dir, { recursive: true });

  const payload = await buildBackupPayload();
  const filename = `inventory-backup-${payload.exportedAt.slice(0, 10)}.json`;
  fs.writeFileSync(path.join(dir, filename), JSON.stringify(payload));

  const cutoff = Date.now() - env.backupRetentionDays * DAY_MS;
  for (const entry of fs.readdirSync(dir)) {
    if (!entry.startsWith("inventory-backup-") || !entry.endsWith(".json")) continue;
    const filePath = path.join(dir, entry);
    if (fs.statSync(filePath).mtimeMs < cutoff) {
      fs.unlinkSync(filePath);
    }
  }
}

// Fires once at startup (so a backend that's restarted daily still gets a
// backup) and every 24h after that. Failures are logged, not thrown, so a
// transient DB hiccup can't crash the whole process.
export function scheduleBackups(): void {
  const tick = () => {
    runBackup().catch((err) => console.error("Scheduled backup failed:", err));
  };
  tick();
  setInterval(tick, DAY_MS);
}
