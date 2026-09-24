import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { fromWire } from "./snooze-stats-wire";
import {
  bucketOf,
  canonicalSetId,
  classH,
  commit,
  dayPhraseOf,
  emptyPartition,
  extractKeys,
  isTimedKey,
  labelFor,
  labelParts,
  lastRow,
  quickTimeEpoch,
  quickTimeSlot,
  quickTimes,
  type Row,
  rank,
  resolveKey,
  rowIcon,
  type SnoozeSource,
  undoCommit,
} from "./snooze-suggest";

function at(y: number, m: number, d: number, h = 0, mi = 0): Date {
  return new Date(y, m - 1, d, h, mi, 0, 0);
}

function labelsOf(rows: Row[], now: number): string[] {
  return rows.map((r) => labelFor(r.keyId, r.time, new Date(now)));
}

describe("canonicalSetId", () => {
  test("is order-independent", () => {
    expect(canonicalSetId(["Wd1@0900", "D1@0900"])).toBe(
      canonicalSetId(["D1@0900", "Wd1@0900"]),
    );
  });
});

describe("extractKeys", () => {
  test("1:12 -> 4:00 same day credits the 'in 3 hours' offset and the exact time", () => {
    const keys = extractKeys(at(2024, 3, 4, 13, 12), at(2024, 3, 4, 16, 0));
    expect(keys).toContain("D0@1600");
    expect(keys).toContain("D0_h3");
  });

  test("tomorrow at a fixed morning time matches D1 and the next weekday", () => {
    // 2024-03-04 is a Monday.
    const keys = extractKeys(at(2024, 3, 4, 10, 0), at(2024, 3, 5, 8, 0));
    expect(keys).toContain("D1@0800");
    expect(keys).toContain("Wd2@0800"); // Tuesday = getDay() 2
  });

  test("same clock time next day credits D1_h0, not (D0,h0)", () => {
    const keys = extractKeys(at(2024, 3, 4, 9, 0), at(2024, 3, 5, 9, 0));
    expect(keys).toContain("D1_h0");
    expect(keys).not.toContain("D0_h0");
  });

  test("a Sunday target uses ISO weekday 7, not JS getDay() 0 (must match Android's DayOfWeek.value for cross-device sync)", () => {
    // 2024-03-04 is a Monday; the following Sunday is 2024-03-10.
    const keys = extractKeys(at(2024, 3, 4, 10, 0), at(2024, 3, 10, 8, 0));
    expect(keys).toContain("Wd7@0800");
    expect(keys).not.toContain("Wd0@0800");
  });

  test("a lone far-out custom pick (17 days) matches nothing", () => {
    const keys = extractKeys(at(2024, 3, 4, 9, 0), at(2024, 3, 21, 9, 0));
    expect(keys).toEqual([]);
  });

  test("the next 1st-of-month within range credits Dom1", () => {
    // March 22 -> April 1 is 10 days out (also within the Wn weekday-after-next window).
    const keys = extractKeys(at(2024, 3, 22, 10, 0), at(2024, 4, 1, 9, 0));
    expect(keys).toContain("Dom1@0900");
  });
});

describe("commit / decay", () => {
  test("a fresh commit gives the set count 1 and undo removes it", () => {
    const p0 = emptyPartition("device-a");
    const A = at(2024, 3, 4, 10, 0).getTime();
    const T = at(2024, 3, 5, 8, 0).getTime();
    const { next, undoSnapshot } = commit(p0, A, T, "suggestion");
    const setId = canonicalSetId(extractKeys(new Date(A), new Date(T)));
    expect(next.sets[setId]?.c).toBeCloseTo(1);

    const restored = undoCommit(next, undoSnapshot);
    expect(restored).toEqual(p0);
  });

  test("credit is NOT split across matched keys — the whole set gets full credit", () => {
    const p0 = emptyPartition("device-a");
    const A = at(2024, 3, 4, 10, 0).getTime();
    const T = at(2024, 3, 5, 8, 0).getTime();
    const { next } = commit(p0, A, T, "suggestion");
    const keys = extractKeys(new Date(A), new Date(T));
    expect(keys.length).toBeGreaterThan(1);
    const setId = canonicalSetId(keys);
    expect(next.sets[setId]?.c).toBe(1); // not 1/keys.length
  });

  test("a repeat commit 21 days later (one short-class half-life) adds to a halved count", () => {
    let p = emptyPartition("device-a");
    const day0 = at(2024, 4, 1, 10, 0).getTime();
    const day21 = at(2024, 4, 22, 10, 0).getTime();
    const T0 = at(2024, 4, 2, 8, 0).getTime();
    const T21 = at(2024, 4, 23, 8, 0).getTime();
    p = commit(p, day0, T0, "suggestion").next;
    const before = commit(p, day21, T21, "suggestion");
    const setId = canonicalSetId(extractKeys(new Date(day21), new Date(T21)));
    // classH for D1/Wd family is 21 days, so one prior unit decays to 0.5 before the +1 credit.
    expect(before.next.sets[setId]?.c).toBeCloseTo(1.5, 5);
  });

  test("the most-used exact time wins and its 5-minute neighbour folds into the same row", () => {
    let p = emptyPartition("device-a");
    p = commit(
      p,
      at(2024, 3, 4, 10).getTime(),
      at(2024, 3, 5, 8, 0).getTime(),
      "custom",
    ).next;
    p = commit(
      p,
      at(2024, 3, 5, 10).getTime(),
      at(2024, 3, 6, 8, 0).getTime(),
      "custom",
    ).next;
    p = commit(
      p,
      at(2024, 3, 6, 10).getTime(),
      at(2024, 3, 7, 8, 5).getTime(),
      "custom",
    ).next;
    const rows = rank([p], at(2024, 3, 7, 10, 0).getTime());
    expect(rows.map((r) => r.keyId)).toEqual(["D1@0800"]);
    expect(new Date(rows[0].time).getHours()).toBe(8);
    expect(new Date(rows[0].time).getMinutes()).toBe(0);
  });

  test("one date rule can hold two times", () => {
    let p = emptyPartition("device-a");
    p = commit(
      p,
      at(2024, 3, 4, 10).getTime(),
      at(2024, 3, 5, 9).getTime(),
      "custom",
    ).next;
    p = commit(
      p,
      at(2024, 3, 5, 10).getTime(),
      at(2024, 3, 6, 9).getTime(),
      "custom",
    ).next;
    p = commit(
      p,
      at(2024, 3, 4, 11).getTime(),
      at(2024, 3, 5, 20).getTime(),
      "custom",
    ).next;
    p = commit(
      p,
      at(2024, 3, 5, 11).getTime(),
      at(2024, 3, 6, 20).getTime(),
      "custom",
    ).next;
    const labelNow = at(2024, 3, 7, 10, 0).getTime();
    const labels = labelsOf(rank([p], labelNow), labelNow);
    expect(labels).toContain("Tomorrow morning, 09:00");
    expect(labels).toContain("Tomorrow evening, 20:00");
  });

  test("undo reverses the set and the quick-time slot exactly", () => {
    let p = emptyPartition("device-a");
    p = commit(
      p,
      at(2024, 3, 3, 10).getTime(),
      at(2024, 3, 4, 9).getTime(),
      "custom",
    ).next;
    const before = p;
    const { next, undoSnapshot } = commit(
      p,
      at(2024, 3, 4, 10).getTime(),
      at(2024, 3, 5, 9).getTime(),
      "suggestion",
      "D1@0900",
    );
    expect(next.tod["0900"]).not.toEqual(before.tod["0900"]);
    expect(undoCommit(next, undoSnapshot)).toEqual(before);
  });

  test("an hours-offset pick credits its set but no quick-time slot", () => {
    const p0 = emptyPartition("device-a");
    const { next, undoSnapshot } = commit(
      p0,
      at(2024, 3, 4, 10).getTime(),
      at(2024, 3, 5, 12).getTime(),
      "suggestion",
      "D1_h2",
    );
    expect(Object.keys(next.sets)).toHaveLength(1);
    expect(next.tod).toEqual({});
    expect(undoSnapshot.tod).toBeNull();
    expect(undoCommit(next, undoSnapshot)).toEqual(p0);
  });
});

describe("rank — Sunday -> Monday attribution (motivating example)", () => {
  test("a daily 'tomorrow 8am' habit shows one row, not a weekday-filler duplicate", () => {
    let p = emptyPartition("device-a");
    // 28 days of "tomorrow, 8:00" committed at ~10:00, starting Sunday 2024-02-04.
    for (let day = 0; day < 28; day++) {
      const commitAt = at(2024, 2, 4 + day, 10, 0).getTime();
      const target = at(2024, 2, 5 + day, 8, 0).getTime();
      p = commit(p, commitAt, target, "suggestion").next;
    }
    // Evaluate on the last Sunday, before that day's commit.
    const now = at(2024, 3, 3, 10, 0).getTime(); // a Sunday
    const rows = rank([p], now);
    expect(rows).toHaveLength(1);
    expect(labelsOf(rows, now)[0]).toContain("Tomorrow");
  });

  test("on a tie tomorrow beats the weekday reading, and the weekday shows once tomorrow is a different day", () => {
    const p = commit(
      emptyPartition("device-a"),
      at(2026, 9, 16, 15, 0).getTime(),
      at(2026, 9, 17, 9, 0).getTime(),
      "custom",
    ).next;
    const wedNow = at(2026, 9, 23, 10, 0).getTime();
    const wednesday = rank([p], wedNow);
    expect(wednesday.map((r) => r.keyId)).toEqual(["D1@0900"]);
    expect(labelsOf(wednesday, wedNow)[0]).toBe("Tomorrow morning, 09:00");

    const thursday = rank([p], at(2026, 9, 24, 7, 0).getTime());
    expect(thursday.map((r) => r.keyId)).toEqual(["D1@0900"]);
    expect(thursday[0].time).toBe(at(2026, 9, 25, 9, 0).getTime());
  });

  test("a genuine second habit (Friday -> Monday) surfaces alongside the daily one", () => {
    let p = emptyPartition("device-a");
    for (let day = 0; day < 21; day++) {
      const commitAt = at(2024, 2, 4 + day, 10, 0).getTime();
      const target = at(2024, 2, 5 + day, 8, 0).getTime();
      p = commit(p, commitAt, target, "suggestion").next;
    }
    // Six weeks of Friday -> Monday 8am on top of the daily habit.
    let friday = at(2024, 2, 9, 10, 0);
    for (let week = 0; week < 6; week++) {
      const commitAt = friday.getTime();
      const monday = new Date(friday);
      monday.setDate(monday.getDate() + 3);
      monday.setHours(8, 0, 0, 0);
      p = commit(p, commitAt, monday.getTime(), "suggestion").next;
      friday = new Date(friday);
      friday.setDate(friday.getDate() + 7);
    }
    const now = friday.getTime(); // a Friday, after both habits have run
    const rows = rank([p], now, 5);
    expect(rows.some((r) => r.keyId.startsWith("Wd1"))).toBe(true);
  });
});

describe("rank — a lone one-off stays visible next to a strong habit", () => {
  test("a single 'the 1st' pick resolves right but isn't guaranteed the 'The 1st' label", () => {
    // Ordinal beats count on a tie, but Dom1 (the 1st) and Wn (next weekday) are both
    // ordinal -- shorter period wins between them, so a single ambiguous pick can surface
    // as "Next Monday" instead of "The 1st". The row still resolves to the right instant.
    let p = emptyPartition("device-a");
    p = commit(
      p,
      at(2024, 3, 22, 10, 0).getTime(),
      at(2024, 4, 1, 9, 0).getTime(), // a Monday
      "custom",
    ).next;

    // Mar 20: Apr 1st is 12 days out, inside the Wn ("weekday after next") window, so the
    // freshly-resolved Wn reading still points at Apr 1st itself.
    const now = at(2024, 3, 20, 10, 0).getTime();
    const rows = rank([p], now, 5);
    expect(
      rows.some((r) => {
        const d = new Date(r.time);
        return d.getMonth() === 3 && d.getDate() === 1;
      }),
    ).toBe(true);
  });

  test("'the 1st' only wins outright once picked more than once, not from a single ambiguous pick", () => {
    let p = emptyPartition("device-a");
    // First pick: the 1st falls on a Monday. Ambiguous with "next Monday" alone.
    p = commit(
      p,
      at(2027, 1, 21, 10, 0).getTime(),
      at(2027, 2, 1, 9, 0).getTime(), // a Monday
      "custom",
    ).next;
    // Second pick, a different month where the 1st falls on a different weekday: real,
    // repeated evidence specifically for Dom1 -- now it dominates both individual weekday
    // readings and wins outright, not merely by tie-break.
    p = commit(
      p,
      at(2027, 3, 20, 10, 0).getTime(),
      at(2027, 4, 1, 9, 0).getTime(), // a Thursday
      "custom",
    ).next;

    const now = at(2027, 3, 25, 10, 0).getTime();
    const rows = rank([p], now, 5);
    expect(labelsOf(rows, now).some((l) => l.includes("1st"))).toBe(true);
  });
});

describe("rank — offset habit at varying times", () => {
  test("a consistent '+3 hours' habit out-aggregates the buckets it happens to land in", () => {
    let p = emptyPartition("device-a");
    const commitTimes = [
      [3, 4, 7, 0],
      [3, 5, 9, 0],
      [3, 6, 11, 0],
      [3, 7, 13, 0],
      [3, 8, 15, 0],
    ] as const;
    for (const [m, d, h, mi] of commitTimes) {
      const commitAt = at(2024, m, d, h, mi).getTime();
      const target = commitAt + 3 * 60 * 60 * 1000;
      p = commit(p, commitAt, target, "suggestion").next;
    }
    const now = at(2024, 3, 9, 12, 0).getTime();
    const rows = rank([p], now, 5);
    expect(rows.some((r) => r.keyId === "D0_h3")).toBe(true);
  });
});

describe("labelFor", () => {
  const label = (key: string, nowIso: string) => {
    const now = local(nowIso);
    const time = resolveKey(key, now);
    expect(time).not.toBeNull();
    return labelFor(key, time as number, now);
  };

  test("folds the resolved time into the label", () => {
    expect(label("D1@0800", "2024-03-04T10:00:00")).toBe(
      "Tomorrow morning, 08:00",
    );
  });

  test("the upcoming Thursday seen on Wednesday reads This Thursday", () => {
    expect(label("Wd4@0900", "2026-09-23T10:00:00")).toBe(
      "This Thursday morning, 09:00",
    );
  });

  test("the upcoming Thursday seen on a Thursday reads Next Thursday", () => {
    expect(label("Wd4@0900", "2026-09-24T07:00:00")).toBe(
      "Next Thursday morning, 09:00",
    );
  });

  test("weekday after next 9 days out reads Next", () => {
    expect(label("Wn4@0900", "2026-09-22T10:00:00")).toBe(
      "Next Thursday morning, 09:00",
    );
  });

  test("weekday after next on its own weekday reads '<weekday> in 2 weeks'", () => {
    expect(label("Wn4@0900", "2026-09-24T07:00:00")).toBe(
      "Thursday in 2 weeks, 09:00",
    );
  });

  test("tomorrow morning seen at 04:50 reads this morning", () => {
    expect(label("D1@0900", "2026-09-23T04:50:00")).toBe("This morning, 09:00");
  });

  test("in a week seen at 04:50 reads in 6 days", () => {
    expect(label("W1@0900", "2026-09-23T04:50:00")).toBe("In 6 days, 09:00");
  });

  test("same-day clock times use the time-of-day name", () => {
    expect(label("D0@2000", "2026-09-22T12:00:00")).toBe("This evening, 20:00");
    expect(label("D0@0100", "2026-09-22T22:00:00")).toBe("Tonight, 01:00");
  });

  test("the region word comes from the resolved time", () => {
    expect(label("D1@2030", "2026-09-22T10:00:00")).toBe(
      "Tomorrow evening, 20:30",
    );
  });

  test("same-day hour offsets read in a little while or much later", () => {
    expect(label("D0_h3", "2026-09-22T12:00:00")).toBe(
      "In a little while (15:00)",
    );
    expect(label("D0_h4", "2026-09-22T12:00:00")).toBe("Much later (16:00)");
    expect(label("D0_h2", "2026-09-22T22:00:00")).toBe(
      "In a little while (00:00)",
    );
  });

  test("separate 1h and 3h habits show as two little-while rows with different times", () => {
    const now = local("2026-09-22T12:00:00").getTime();
    const t = now;
    const p = fromWire("a", {
      sets: {
        "D0@1300__D0_h1": { c: 1, t },
        "D0@1305__D0_h1": { c: 1, t },
        "D0@1500__D0_h3": { c: 1, t },
        "D0@1510__D0_h3": { c: 1, t },
      },
    });
    expect(labelsOf(rank([p], now), now)).toEqual([
      "In a little while (13:00)",
      "In a little while (15:00)",
    ]);
  });
});

function local(iso: string): Date {
  return new Date(iso);
}

type Fixtures = {
  extract: {
    name: string;
    at: string;
    target: string;
    keys: string[];
    setId: string | null;
  }[];
  quickCredit: {
    name: string;
    at: string;
    target: string;
    source: SnoozeSource;
    pickedKey: string | null;
    slot: string | null;
  }[];
  resolve: {
    name: string;
    now: string;
    key: string;
    epochLocal: string | null;
  }[];
  labelDist: {
    name: string;
    now: string;
    key: string;
    resolvedLocal: string;
    family: string;
    dist: number;
    weekday: number;
    phrase: string;
    region?: string;
  }[];
  labelOffset: {
    name: string;
    now: string;
    key: string;
    resolvedLocal: string;
    phrase: "littleWhile" | "muchLater";
  }[];
  quickTimes: {
    name: string;
    now: string;
    partitions: unknown[];
    expect: number[];
  }[];
  rank: {
    name: string;
    now: string;
    partitions: unknown[];
    expectKeys: string[];
  }[];
};

const fixtures = JSON.parse(
  readFileSync(
    new URL("../../testdata/snooze-fixtures.json", import.meta.url),
    "utf8",
  ),
) as Fixtures;

function minutesSince0500(d: Date): number {
  return (d.getHours() * 60 + d.getMinutes() - 300 + 1440) % 1440;
}

describe("shared fixtures (must match Android)", () => {
  test("runs in America/New_York", () => {
    expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe(
      "America/New_York",
    );
  });

  for (const c of fixtures.extract) {
    test(`extract: ${c.name}`, () => {
      const keys = [...extractKeys(local(c.at), local(c.target))].sort();
      expect(keys).toEqual(c.keys);
      expect(keys.length > 0 ? canonicalSetId(keys) : null).toBe(c.setId);
    });
  }

  for (const c of fixtures.quickCredit) {
    test(`quickCredit: ${c.name}`, () => {
      const atMs = local(c.at).getTime();
      const target = local(c.target).getTime();
      expect(quickTimeSlot(atMs, target, c.source, c.pickedKey)).toBe(c.slot);
      const { next } = commit(
        emptyPartition("a"),
        atMs,
        target,
        c.source,
        c.pickedKey,
      );
      expect(Object.keys(next.tod)).toEqual(c.slot === null ? [] : [c.slot]);
    });
  }

  for (const c of fixtures.resolve) {
    test(`resolve: ${c.name}`, () => {
      const epoch = resolveKey(c.key, local(c.now));
      expect(epoch).toBe(c.epochLocal ? local(c.epochLocal).getTime() : null);
    });
  }

  for (const c of fixtures.labelDist) {
    test(`labelDist: ${c.name}`, () => {
      const dateRule = c.key.split(/[@_]/)[0];
      const resolved = local(c.resolvedLocal);
      expect(resolveKey(c.key, local(c.now))).toBe(resolved.getTime());
      expect(dayPhraseOf(dateRule, resolved, local(c.now))).toEqual({
        phrase: c.phrase as never,
        dist: c.dist,
        weekday: c.weekday,
      });
      if (c.region !== undefined) {
        expect(bucketOf(minutesSince0500(resolved))).toBe(c.region as never);
      }
    });
  }

  for (const c of fixtures.labelOffset) {
    test(`labelOffset: ${c.name}`, () => {
      const now = local(c.now);
      const epoch = resolveKey(c.key, now);
      expect(epoch).toBe(local(c.resolvedLocal).getTime());
      const prefix =
        c.phrase === "littleWhile" ? "In a little while (" : "Much later (";
      expect(labelFor(c.key, epoch as number, now).startsWith(prefix)).toBe(
        true,
      );
    });
  }

  for (const c of fixtures.quickTimes) {
    test(`quickTimes: ${c.name}`, () => {
      const partitions = c.partitions.map((tod, i) =>
        fromWire(`p${i}`, { tod }),
      );
      const got = quickTimes(partitions, local(c.now).getTime()).map(
        (q) => q.clockMinutes,
      );
      expect(got).toEqual(c.expect);
    });
  }

  for (const c of fixtures.rank) {
    test(`rank: ${c.name}`, () => {
      const partitions = c.partitions.map((raw, i) => fromWire(`p${i}`, raw));
      const got = rank(partitions, local(c.now).getTime()).map((r) => r.keyId);
      expect(got).toEqual(c.expectKeys);
    });
  }
});

describe("quick times", () => {
  test("a far-out custom pick matches no pattern but still credits its slot, and undo removes it", () => {
    const atMs = local("2026-09-22T10:00:00").getTime();
    const target = local("2026-11-06T10:00:00").getTime();
    const p0 = emptyPartition("a");
    const { next, undoSnapshot } = commit(p0, atMs, target, "custom");
    expect(next.sets).toEqual({});
    expect(next.tod).toEqual({ "1000": { c: 1, t: atMs } });
    expect(undoCommit(next, undoSnapshot)).toEqual(p0);
  });

  test("a same-day snooze credits nothing but later-today keys", () => {
    const atMs = local("2026-09-22T10:00:00").getTime();
    const target = local("2026-09-22T18:00:00").getTime();
    const { next } = commit(emptyPartition("a"), atMs, target, "custom");
    expect(Object.keys(next.sets)).toEqual(["D0@1800__D0_h8"]);
    expect(next.tod).toEqual({});
  });

  test("a newer habit takes over an old one", () => {
    let p = emptyPartition("a");
    const day = 24 * 60 * 60 * 1000;
    const start = local("2026-06-01T10:00:00").getTime();
    for (let i = 0; i < 10; i++) {
      const atMs = start + i * day;
      p = commit(p, atMs, atMs + 22 * 60 * 60 * 1000, "custom").next; // 08:00
    }
    for (let i = 10; i < 22; i++) {
      const atMs = start + i * day;
      p = commit(p, atMs, atMs + 23 * 60 * 60 * 1000, "custom").next; // 09:00
    }
    const [eight, nine] = quickTimes([p], start + 22 * day);
    expect([eight.clockMinutes, nine.clockMinutes]).toEqual([480, 540]);
    expect(nine.weight).toBeGreaterThan(eight.weight);
  });

  test("quickTimeEpoch uses the calendar date as typed", () => {
    expect(quickTimeEpoch("2026-09-24", 60)).toBe(
      local("2026-09-24T01:00:00").getTime(),
    );
    expect(quickTimeEpoch("not a date", 60)).toBeNull();
  });
});

describe("lastRow", () => {
  const now = at(2024, 3, 4, 10, 0).getTime(); // a Monday
  const withLast = (target: number) => ({
    ...emptyPartition("a"),
    lastCustom: { target, at: now },
  });

  test("hidden when past or when a ranked row lands at exactly its time", () => {
    const future = at(2024, 3, 20, 14, 0).getTime();
    const covering: Row[] = [
      {
        time: future,
        text: "x",
        icon: "days",
        keyId: "k",
        score: 1,
      },
    ];
    expect(lastRow([withLast(future)], now, covering)).toBeNull();
    const near: Row[] = [{ ...covering[0], time: future + 20 * MINUTE }];
    expect(lastRow([withLast(future)], now, near)?.time).toBe(future);
    expect(lastRow([withLast(now - 1000)], now, [])).toBeNull();
  });

  test("named like a ranked row, with the date only when no phrase fits", () => {
    const text = (target: Date) =>
      lastRow([withLast(target.getTime())], now, [])?.text;
    expect(text(at(2024, 3, 4, 20, 0))).toBe("Last · This evening");
    expect(text(at(2024, 3, 5, 8, 0))).toBe("Last · Tomorrow morning");
    expect(text(at(2024, 3, 8, 14, 0))).toBe("Last · This Friday afternoon");
    expect(text(at(2024, 3, 12, 8, 0))).toBe("Last · Next Tuesday morning");
    expect(text(at(2024, 3, 18, 8, 0))).toBe("Last · Monday in 2 weeks");
    expect(text(at(2024, 3, 20, 14, 0))).toBe("Last · Mar 20");
  });
});

describe("5am day-boundary consistency (Dom/DomL/Mo vs D/W/Wd/Wn)", () => {
  test("T1: a commit between midnight and 5am matches Dom1 AND Mo1 (both read the shifted day)", () => {
    // Commit at 2026-10-02 01:00 -> shifted day is Oct 1, not Oct 2.
    const keys = extractKeys(at(2026, 10, 2, 1, 0), at(2026, 11, 1, 9, 0));
    expect(keys.sort()).toEqual(["Dom1@0900", "Mo1@0900"]);
  });

  test("T2: same window, DomL and Mo1 both match a month-end target", () => {
    // Commit at 2026-11-01 02:00 -> shifted day is Oct 31.
    const keys = extractKeys(at(2026, 11, 1, 2, 0), at(2026, 11, 30, 21, 0));
    expect(keys.sort()).toEqual(["DomL@2100", "Mo1@2100"]);
  });

  test("T3: shifted-day reasoning can turn a pick into a one-off (matches nothing)", () => {
    // Commit at 2026-10-01 01:00 -> shifted day is Sep 30; Nov 1 is neither
    // "the next 1st" nor "in a month" from Sep 30.
    const keys = extractKeys(at(2026, 10, 1, 1, 0), at(2026, 11, 1, 9, 0));
    expect(keys).toEqual([]);
  });

  test("T4: the same commit made in daytime (no shift) matches Dom1 AND Mo1 too — nextDom/nextMonthEnd are strict", () => {
    // Not in the midnight-5am window, but the anchor day itself (Oct 1) must
    // still roll forward to next month's occurrence, not treat "today" as a match.
    const keys = extractKeys(at(2026, 10, 1, 10, 0), at(2026, 11, 1, 9, 0));
    expect(keys.sort()).toEqual(["Dom1@0900", "Mo1@0900"]);
  });

  test("guard: an ordinary daytime commit is unaffected by the fix", () => {
    const keys = extractKeys(at(2026, 10, 7, 10, 0), at(2026, 10, 8, 8, 0));
    expect(keys).toContain("D1@0800");
    expect(keys.filter((k) => k.startsWith("Wd"))).toHaveLength(1);
    expect(keys).toHaveLength(2);
    expect(keys.some((k) => k.includes("_h"))).toBe(false);
  });

  test("resolve side: Dom1 at the anchor day itself resolves to next month, not today (T4 reverse)", () => {
    const now = at(2026, 10, 1, 10, 0);
    const time = resolveKey("Dom1@0900", now);
    expect(time).not.toBeNull();
    const resolved = new Date(time as number);
    expect(resolved.getMonth()).toBe(10); // November (0-indexed)
    expect(resolved.getDate()).toBe(1);
  });

  test("resolve side: Mo1 resolved just after midnight uses the shifted day (T1 reverse)", () => {
    const now = at(2026, 10, 2, 1, 0); // shifted day: Oct 1
    const time = resolveKey("Mo1@0900", now);
    expect(time).not.toBeNull();
    const resolved = new Date(time as number);
    expect(resolved.getMonth()).toBe(10); // November, not December
    expect(resolved.getDate()).toBe(1); // the 1st, not the 2nd
  });
});

describe("classH", () => {
  test("short families are 21 days, monthly families are 60", () => {
    expect(classH("D1@0900")).toBe(21);
    expect(classH("Wd1_h3")).toBe(21);
    expect(classH("Dom1@0900")).toBe(60);
    expect(classH("DomL@0900")).toBe(60);
    expect(classH("Mo1@0900")).toBe(60);
  });
});

describe("row icons and label parts (mirror Android rowIcon)", () => {
  const noon = at(2026, 9, 21, 12, 0);
  const icon = (key: string, resolved: Date) =>
    rowIcon(key, resolved.getTime(), noon);

  test("icon follows the resolved day and time", () => {
    expect(icon("D0@2300", at(2026, 9, 21, 23, 0))).toBe("todayNight");
    expect(icon("D0@1500", at(2026, 9, 21, 15, 0))).toBe("todayDay");
    expect(icon("D1@0900", at(2026, 9, 22, 9, 0))).toBe("tomorrow");
    expect(icon("D3@0900", at(2026, 9, 24, 9, 0))).toBe("days");
    expect(icon("Wd5@0900", at(2026, 9, 25, 9, 0))).toBe("weekday");
    expect(icon("Dom1@0900", at(2026, 10, 1, 9, 0))).toBe("monthly");
    expect(icon("D0_h3", at(2026, 9, 21, 15, 0))).toBe("offset");
  });

  test("text drops the time and labelFor recombines it", () => {
    const t = at(2026, 9, 22, 9, 0).getTime();
    expect(labelParts("D1@0900", t, noon)).toEqual({
      text: "Tomorrow morning",
      time: "09:00",
      parenthesized: false,
    });
    const soon = at(2026, 9, 21, 15, 0).getTime();
    expect(labelParts("D0_h3", soon, noon).text).toBe("In a little while");
    expect(labelFor("D0_h3", soon, noon)).toBe("In a little while (15:00)");
  });
});

const MINUTE = 60_000;
const DAY = 24 * 60 * MINUTE;

function snoozeDayOf(d: Date): number {
  const shifted = new Date(d);
  shifted.setHours(shifted.getHours() - 5);
  shifted.setHours(0, 0, 0, 0);
  return shifted.getTime();
}

const WEEKDAYS = [
  "Sunday",
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
];

// Written out independently of the label code: days counted from today's calendar date to the target's 05:00 day.
function expectedLabel(target: Date, now: Date, keyId: string): string {
  const hour = target.getHours();
  const region =
    hour >= 5 && hour < 12
      ? "morning"
      : hour >= 12 && hour < 17
        ? "afternoon"
        : hour >= 17 && hour < 21
          ? "evening"
          : "night";
  const today = new Date(now);
  today.setHours(0, 0, 0, 0);
  const days = Math.round((snoozeDayOf(target) - today.getTime()) / DAY);
  const weekday = WEEKDAYS[new Date(snoozeDayOf(target)).getDay()];
  const clock = `${String(hour).padStart(2, "0")}:${String(target.getMinutes()).padStart(2, "0")}`;
  let text: string;
  if (days <= 0) {
    text = region === "night" ? "Tonight" : `This ${region}`;
  } else if (days === 1 && !/^(Wd|Wn)/.test(keyId)) {
    text = `Tomorrow ${region}`;
  } else if (days <= 6) {
    text = `This ${weekday} ${region}`;
  } else if (days <= 13) {
    text = `Next ${weekday} ${region}`;
  } else {
    text = `${weekday} in 2 weeks`;
  }
  return `${text}, ${clock}`;
}

describe("a single snooze is suggested back as that exact time, by its best name", () => {
  const clockTimes = [
    [0, 30],
    [3, 0],
    [7, 0],
    [8, 0],
    [12, 15],
    [17, 0],
    [20, 0],
    [22, 45],
  ];
  // A September week, and the week the clocks fall back.
  for (const [year, month, day] of [
    [2026, 9, 21],
    [2026, 10, 29],
  ]) {
    test(`sweep from ${year}-${month}-${day}`, () => {
      const failures: string[] = [];
      for (let step = 0; step < 8 * 16; step++) {
        const commitAt = at(year, month, day, 0, step * 90);
        const now = commitAt.getTime() + MINUTE;
        for (let days = 0; days <= 14; days++) {
          for (const [hour, minute] of clockTimes) {
            const target = new Date(commitAt);
            target.setDate(target.getDate() + days);
            target.setHours(hour, minute, 0, 0);
            const dist = Math.round(
              (snoozeDayOf(target) - snoozeDayOf(commitAt)) / DAY,
            );
            if (target.getTime() <= now + 5 * MINUTE || dist > 14) continue;
            const { next } = commit(
              emptyPartition("a"),
              commitAt.getTime(),
              target.getTime(),
              "custom",
            );
            const top = rank([next], now)[0];
            const named =
              dist >= 2 ? /^(Wd|Wn)\d/.test(top?.keyId ?? "") : true;
            const text = top
              ? labelFor(top.keyId, top.time, new Date(now))
              : "";
            if (
              !top ||
              !isTimedKey(top.keyId) ||
              top.time !== target.getTime() ||
              !named ||
              text !== expectedLabel(target, new Date(now), top?.keyId ?? "")
            ) {
              failures.push(
                `${commitAt.toString().slice(0, 21)} -> ${target.toString().slice(0, 21)}: ${top?.keyId} ${text} (want ${expectedLabel(target, new Date(now), top?.keyId ?? "")})`,
              );
            }
          }
        }
      }
      expect(failures.slice(0, 10)).toEqual([]);
    });
  }
});
