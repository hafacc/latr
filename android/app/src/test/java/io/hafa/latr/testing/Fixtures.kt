package io.hafa.latr.testing

import org.json.JSONArray
import org.json.JSONObject

/** The cross-platform fixture in `testdata/snooze-fixtures.json`, shared with web's specs. */
object Fixtures {
    val snooze: Map<String, Any?> by lazy {
        val text = requireNotNull(javaClass.classLoader?.getResourceAsStream("snooze-fixtures.json")) {
            "testdata/snooze-fixtures.json missing from test resources"
        }.bufferedReader().use { it.readText() }
        @Suppress("UNCHECKED_CAST")
        (plain(JSONObject(text)) as Map<String, Any?>)
    }

    private fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }

    @Suppress("UNCHECKED_CAST")
    fun cases(section: String): List<Map<String, Any?>> = snooze[section] as List<Map<String, Any?>>

    /** Numbers compared by value, so a JSON `2` equals a Kotlin `2.0`. */
    fun numbersAsDouble(value: Any?): Any? = when (value) {
        is Number -> value.toDouble()
        is Map<*, *> -> value.entries.associate { (k, v) -> k to numbersAsDouble(v) }
        is List<*> -> value.map { numbersAsDouble(it) }
        else -> value
    }
}
