import { expect, test } from "bun:test";
import {
  formatClockMinutes,
  monthGrid,
  nextQuarterHour,
  parseClockInput,
  parseYmd,
  toYmd,
} from "./calendar";

test("September 2026 starts on a Tuesday and fills whole Monday-first weeks", () => {
  const grid = monthGrid(2026, 8, new Date(2026, 8, 22));
  expect(grid).toHaveLength(35);
  expect(grid[0]).toMatchObject({ ymd: "2026-08-31", inMonth: false });
  expect(grid[1]).toMatchObject({ ymd: "2026-09-01", day: 1, inMonth: true });
  expect(grid[34]).toMatchObject({ ymd: "2026-10-04", inMonth: false });
});

test("marks today and the days before it", () => {
  const grid = monthGrid(2026, 8, new Date(2026, 8, 22, 15));
  const today = grid.find((c) => c.today);
  expect(today?.ymd).toBe("2026-09-22");
  expect(grid.find((c) => c.ymd === "2026-09-21")?.past).toBe(true);
  expect(grid.find((c) => c.ymd === "2026-09-22")?.past).toBe(false);
});

test("a month starting on Monday has no leading days", () => {
  const grid = monthGrid(2026, 5, new Date(2026, 0, 1));
  expect(grid[0].ymd).toBe("2026-06-01");
});

test("ymd round-trips", () => {
  expect(toYmd(parseYmd("2026-03-08"))).toBe("2026-03-08");
});

test("typed times parse as 24-hour", () => {
  expect(parseClockInput("9")).toBe(540);
  expect(parseClockInput("930")).toBe(570);
  expect(parseClockInput("09:30")).toBe(570);
  expect(parseClockInput("2105")).toBe(1265);
  expect(parseClockInput(" 0:05 ")).toBe(5);
  expect(parseClockInput("24:00")).toBeNull();
  expect(parseClockInput("9:60")).toBeNull();
  expect(parseClockInput("9pm")).toBeNull();
  expect(formatClockMinutes(545)).toBe("09:05");
});

test("the next quarter hour rolls onto tomorrow after 23:45", () => {
  expect(nextQuarterHour(new Date(2026, 8, 22, 10, 7))).toEqual({
    ymd: "2026-09-22",
    minutes: 10 * 60 + 15,
  });
  expect(nextQuarterHour(new Date(2026, 8, 22, 10, 15))).toEqual({
    ymd: "2026-09-22",
    minutes: 10 * 60 + 30,
  });
  expect(nextQuarterHour(new Date(2026, 8, 22, 23, 50))).toEqual({
    ymd: "2026-09-23",
    minutes: 0,
  });
});
