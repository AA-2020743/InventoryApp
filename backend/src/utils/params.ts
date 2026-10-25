import { HttpError } from "../middleware/errorHandler";

// Query parameters checked before use. Each of these used to go straight
// into a query - Number("abc") as a row limit, new Date("abc") as a date -
// and came back as a 500 "Internal server error", which reads as the server
// breaking rather than the request being wrong.

// An optional whole number of at least 1 (a row limit, a number of days).
export function optionalPositiveInt(value: unknown, name: string): number | undefined {
  if (value === undefined || value === "") return undefined;
  const n = Number(value);
  if (!Number.isInteger(n) || n < 1) throw new HttpError(400, `${name} must be a whole number of at least 1`);
  return n;
}

// An optional date or instant ("2026-10-24", "2026-10-24T09:30:00Z").
export function optionalDate(value: unknown, name: string): Date | undefined {
  if (value === undefined || value === "") return undefined;
  const d = typeof value === "string" ? new Date(value) : new Date(NaN);
  if (Number.isNaN(d.getTime())) throw new HttpError(400, `${name} must be a date`);
  return d;
}
