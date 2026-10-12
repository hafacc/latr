const ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
const MIDDLE = "i";
const TIME_DIGITS = 13;
const MAX_TIME = 9_999_999_999_999;
const POSITION_PATTERN = /^[0-9]{13}[0-9a-z]*$/;

/** A time as 13 digits, so text order is time order. */
export function timeKey(epoch: number): string {
  const clamped = Number.isFinite(epoch)
    ? Math.min(MAX_TIME, Math.max(0, Math.floor(epoch)))
    : 0;
  return String(clamped).padStart(TIME_DIGITS, "0");
}

/** The time a key starts with. */
export function keyTime(key: string): number {
  return Number(key.slice(0, TIME_DIGITS));
}

/** An id as key text; the ending keeps a key from ending in the lowest character. */
export function idKey(id: string): string {
  return `${id.replace(/[^0-9a-zA-Z]/g, "").toLowerCase()}z`;
}

/** A saved position, or null when it isn't one this build can order by. */
export function readPosition(value: unknown): string | null {
  return typeof value === "string" && POSITION_PATTERN.test(value)
    ? value
    : null;
}

// Digits only ever move to digits, so the leading time stays a time.
function after(rest: string): string {
  for (let index = 0; index < rest.length; index++) {
    const digit = ALPHABET.indexOf(rest[index]);
    const top = digit < 10 ? 9 : ALPHABET.length - 1;
    if (digit < top) {
      return rest.slice(0, index) + ALPHABET[(digit + top + 1) >> 1];
    }
  }
  return rest + MIDDLE;
}

/**
 * Text that sorts strictly between `lo` and `hi` and is never the start of `hi`.
 * Null when `lo` isn't below `hi`, or `hi` is `lo` followed only by zeros.
 */
export function between(lo: string, hi: string): string | null {
  if (!(lo < hi)) return null;
  let shared = 0;
  while (shared < lo.length && lo[shared] === hi[shared]) shared++;
  if (shared === lo.length) {
    let nonZero = shared;
    while (hi[nonZero] === "0") nonZero++;
    if (nonZero === hi.length) return null;
    const digit = ALPHABET.indexOf(hi[nonZero]);
    if (digit >= 2) return hi.slice(0, nonZero) + ALPHABET[digit >> 1];
    else return `${hi.slice(0, nonZero)}0${MIDDLE}`;
  } else {
    const low = ALPHABET.indexOf(lo[shared]);
    const high = ALPHABET.indexOf(hi[shared]);
    if (high - low >= 2) {
      return lo.slice(0, shared) + ALPHABET[(low + high) >> 1];
    } else {
      return lo.slice(0, shared + 1) + after(lo.slice(shared + 1));
    }
  }
}

/**
 * A position for todo `id` between two keys: the time halfway between theirs,
 * or text between them once their times are the same or a millisecond apart.
 * Ends in the id so no two todos share one.
 */
export function positionBetween(
  lo: string,
  hi: string,
  id: string,
): string | null {
  if (!(lo < hi)) return null;
  const low = keyTime(lo);
  const high = keyTime(hi);
  if (high - low >= 2) {
    return timeKey(Math.floor((low + high) / 2)) + idKey(id);
  } else {
    const middle = between(lo, hi);
    return middle === null ? null : middle.padEnd(TIME_DIGITS, "0") + idKey(id);
  }
}

/** A position for todo `id` just above `lo`, within the same millisecond. */
export function positionAbove(lo: string, id: string): string | null {
  return positionBetween(lo, timeKey(keyTime(lo) + 1), id);
}

/** A position for todo `id` just below `hi`, within the same millisecond. */
export function positionBelow(hi: string, id: string): string | null {
  return positionBetween(timeKey(keyTime(hi)), hi, id);
}
