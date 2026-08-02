// The business runs on Egypt local time regardless of what timezone the
// server process/OS itself happens to be configured with (VPS defaults
// are commonly UTC) - every "what day is it" calculation below is pinned
// to this zone explicitly via Intl rather than trusting the server's own
// local clock, so a sale/expense right after midnight in Cairo always
// lands under the new day even if the server thinks it's still evening.
const BUSINESS_TIMEZONE = "Africa/Cairo";

function formatParts(d: Date, timeZone: string): Record<string, string> {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(d);
  const map: Record<string, string> = {};
  for (const p of parts) if (p.type !== "literal") map[p.type] = p.value;
  return map;
}

// The UTC offset (in minutes, positive = ahead of UTC) `timeZone` was
// actually observing at `d` - accounts for DST without hardcoding it,
// since Egypt has flipped DST policy more than once in recent years.
function timezoneOffsetMinutes(d: Date, timeZone: string): number {
  const p = formatParts(d, timeZone);
  const asUtc = Date.UTC(
    Number(p.year),
    Number(p.month) - 1,
    Number(p.day),
    Number(p.hour),
    Number(p.minute),
    Number(p.second)
  );
  return (asUtc - d.getTime()) / 60000;
}

function businessDateParts(d: Date): { year: number; month: number; day: number } {
  const p = formatParts(d, BUSINESS_TIMEZONE);
  return { year: Number(p.year), month: Number(p.month), day: Number(p.day) };
}

// Canonical "calendar day" key: the Y/M/D of `d` as observed in the
// business's timezone, encoded as a UTC-midnight Date so it matches
// Postgres's timezone-less DATE columns 1:1.
export function dateOnlyKey(d: Date = new Date()): Date {
  const { year, month, day } = businessDateParts(d);
  return new Date(Date.UTC(year, month - 1, day));
}

// The real UTC instant of local midnight in the business's timezone, for
// the calendar day containing `d`.
export function startOfDay(d: Date = new Date()): Date {
  const { year, month, day } = businessDateParts(d);
  const offsetMinutes = timezoneOffsetMinutes(d, BUSINESS_TIMEZONE);
  return new Date(Date.UTC(year, month - 1, day, 0, 0, 0) - offsetMinutes * 60000);
}

// The real UTC instant of local midnight on the 1st of the business
// month containing `d`.
export function startOfMonth(d: Date = new Date()): Date {
  const { year, month } = businessDateParts(d);
  const offsetMinutes = timezoneOffsetMinutes(d, BUSINESS_TIMEZONE);
  return new Date(Date.UTC(year, month - 1, 1, 0, 0, 0) - offsetMinutes * 60000);
}

// The exact start of the next calendar day after `d`, in the business
// timezone. Deliberately re-derives from a fresh instant  ~36h ahead
// (rather than naively adding 24h to startOfDay(d)) so it stays correct
// across a DST transition, where a "day" can be 23 or 25 hours long.
export function startOfNextDay(d: Date = new Date()): Date {
  return startOfDay(new Date(startOfDay(d).getTime() + 36 * 3600000));
}

// The exact start of the month after the one containing `d`, in the
// business timezone - same DST-safety reasoning as startOfNextDay,
// jumping well past the end of the longest possible month.
export function startOfNextMonth(d: Date = new Date()): Date {
  return startOfMonth(new Date(startOfMonth(d).getTime() + 33 * 24 * 3600000));
}
