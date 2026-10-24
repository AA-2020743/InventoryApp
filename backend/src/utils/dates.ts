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

// The real UTC instant at which the business timezone's wall clock reads
// year-month-day 00:00.
//
// The offset subtracted has to be the one in force *at that midnight*, not
// at whatever instant the caller happened to ask about: across a clock
// change the two differ by an hour. Egypt's change falls right at midnight
// (summer time ends at the end of the last Thursday of October and starts at
// 00:00 on the last Friday of April), so using the caller's offset put a day
// boundary an hour off - Thursday's first hour landing in Wednesday, or a
// whole month starting at 23:00 on the last day of the one before.
//
// Two passes find it: guess with the offset at the naive time, then correct
// with the offset at the guess. On the night the clocks go forward at 00:00
// there is no 00:00 at all - the clock jumps from 23:59 to 01:00 - and this
// lands on the jump itself, which is exactly when that day begins.
function businessMidnight(year: number, month: number, day: number): Date {
  const wallAsUtc = Date.UTC(year, month - 1, day);
  const guess = wallAsUtc - timezoneOffsetMinutes(new Date(wallAsUtc), BUSINESS_TIMEZONE) * 60000;
  return new Date(wallAsUtc - timezoneOffsetMinutes(new Date(guess), BUSINESS_TIMEZONE) * 60000);
}

// Calendar arithmetic on a business date, normalised by Date.UTC (day 32 of
// a month becomes the 1st of the next, month 13 January of the next year).
function shiftedDate(year: number, month: number, day: number): { year: number; month: number; day: number } {
  const d = new Date(Date.UTC(year, month - 1, day));
  return { year: d.getUTCFullYear(), month: d.getUTCMonth() + 1, day: d.getUTCDate() };
}

// Local midnight in the business's timezone, as a real UTC instant, for the
// calendar day containing `d`.
export function startOfDay(d: Date = new Date()): Date {
  const { year, month, day } = businessDateParts(d);
  return businessMidnight(year, month, day);
}

// Local midnight on the 1st of the business month containing `d`.
export function startOfMonth(d: Date = new Date()): Date {
  const { year, month } = businessDateParts(d);
  return businessMidnight(year, month, 1);
}

// The start of the calendar day after the one containing `d`. Worked out on
// the calendar rather than by adding hours: across a clock change a day is
// 23 or 25 hours long.
export function startOfNextDay(d: Date = new Date()): Date {
  const { year, month, day } = businessDateParts(d);
  const next = shiftedDate(year, month, day + 1);
  return businessMidnight(next.year, next.month, next.day);
}

// The start of the month after the one containing `d`.
export function startOfNextMonth(d: Date = new Date()): Date {
  const { year, month } = businessDateParts(d);
  const next = shiftedDate(year, month + 1, 1);
  return businessMidnight(next.year, next.month, 1);
}
