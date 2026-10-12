package cc.hafa.latr.util

/** Text keys the lists order by; the reading half of web's `utils/position.ts` (moves are made on web only), run against the same `testdata/position-fixtures.json`. */
object Position {
    private const val TIME_DIGITS = 13
    private const val MAX_TIME = 9_999_999_999_999L
    private val POSITION_PATTERN = Regex("^[0-9]{13}[0-9a-z]*$")

    /** A time as 13 digits, so text order is time order. */
    fun timeKey(epochMillis: Long): String =
        epochMillis.coerceIn(0L, MAX_TIME).toString().padStart(TIME_DIGITS, '0')

    /** The time a key starts with. */
    fun keyTime(key: String): Long = key.take(TIME_DIGITS).toLong()

    /** An id as key text; the ending keeps a key from ending in the lowest character. */
    fun idKey(id: String): String =
        id.filter { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }.lowercase() + "z"

    /** A saved position, or null when it isn't one this build can order by. */
    fun readPosition(value: Any?): String? =
        (value as? String)?.takeIf { POSITION_PATTERN.matches(it) }
}
