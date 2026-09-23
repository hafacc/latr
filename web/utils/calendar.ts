export type CalendarDay = {
  ymd: string;
  day: number;
  inMonth: boolean;
  past: boolean;
  today: boolean;
};

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

export function toYmd(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function parseYmd(ymd: string): Date {
  const [y, m, d] = ymd.split("-").map((s) => Number.parseInt(s, 10));
  return new Date(y, m - 1, d);
}

/** Monday-first weeks covering `month` (0-based) of `year`, padded with neighbouring days. */
export function monthGrid(
  year: number,
  month: number,
  today: Date,
): CalendarDay[] {
  const first = new Date(year, month, 1);
  const lead = (first.getDay() + 6) % 7;
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const cells = Math.ceil((lead + daysInMonth) / 7) * 7;
  const todayYmd = toYmd(today);
  const out: CalendarDay[] = [];
  for (let i = 0; i < cells; i++) {
    const d = new Date(year, month, 1 - lead + i);
    const ymd = toYmd(d);
    out.push({
      ymd,
      day: d.getDate(),
      inMonth: d.getMonth() === month,
      past: ymd < todayYmd,
      today: ymd === todayYmd,
    });
  }
  return out;
}

export function monthLabel(year: number, month: number): string {
  return new Intl.DateTimeFormat(undefined, {
    month: "long",
    year: "numeric",
  }).format(new Date(year, month, 1));
}

export const WEEKDAY_SHORT = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];

/** Parses a 24-hour time typed as "9", "930", "09:30" or "21:05" into minutes after midnight. */
export function parseClockInput(raw: string): number | null {
  const match = /^(\d{1,2})(?::?(\d{2}))?$/.exec(raw.trim());
  if (!match) return null;
  const hours = Number.parseInt(match[1], 10);
  const minutes = match[2] ? Number.parseInt(match[2], 10) : 0;
  if (hours > 23 || minutes > 59) return null;
  return hours * 60 + minutes;
}

export function formatClockMinutes(minutes: number): string {
  return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
}

/** The first :00/:15/:30/:45 strictly after `now`, rolling onto the next day past 23:45. */
export function nextQuarterHour(now: Date): { ymd: string; minutes: number } {
  const next = new Date(now);
  next.setSeconds(0, 0);
  next.setMinutes(Math.floor(next.getMinutes() / 15) * 15 + 15);
  return {
    ymd: toYmd(next),
    minutes: next.getHours() * 60 + next.getMinutes(),
  };
}
