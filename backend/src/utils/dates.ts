// Canonical "calendar day" key: takes the Y/M/D of `d` in the server's
// local time (the single self-hosted deployment's timezone) and encodes
// it as a UTC-midnight Date, matching Postgres's timezone-less DATE
// columns 1:1 regardless of what time of day `d` itself represents.
export function dateOnlyKey(d: Date = new Date()): Date {
  return new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
}

export function startOfDay(d: Date = new Date()): Date {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

export function startOfMonth(d: Date = new Date()): Date {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}
