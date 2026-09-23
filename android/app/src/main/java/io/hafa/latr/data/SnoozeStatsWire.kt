package io.hafa.latr.data

import io.hafa.latr.util.LastCustom
import io.hafa.latr.util.SetStat
import io.hafa.latr.util.SnoozeStatsSnapshot
import io.hafa.latr.util.SnoozeSuggestions

/** One device partition as stored at `users/{uid}.snoozeStats.<deviceId>`, shared with web. */
object SnoozeStatsWire {

    fun toWire(snapshot: SnoozeStatsSnapshot): Map<String, Any?> = mapOf(
        "sets" to snapshot.sets.mapValues { (_, s) -> counterToWire(s) },
        "tod" to snapshot.tod.mapValues { (_, s) -> counterToWire(s) },
        "lastCustom" to snapshot.lastCustom?.let { mapOf("target" to it.target, "at" to it.at) },
    )

    /** Drops anything malformed, including sets from builds with an older key grammar. */
    fun fromWire(raw: Map<*, *>?): SnoozeStatsSnapshot {
        if (raw == null) return SnoozeStatsSnapshot()
        val sets = counters(raw["sets"]) { SnoozeSuggestions.isValidSetId(it) }
        val tod = counters(raw["tod"]) { SnoozeSuggestions.SLOT_RE.matches(it) }
        val lastCustom = (raw["lastCustom"] as? Map<*, *>)?.let {
            val target = finite(it["target"])
            val at = finite(it["at"])
            if (target != null && at != null) LastCustom(target.toLong(), at.toLong()) else null
        }
        return SnoozeStatsSnapshot(sets, tod, lastCustom)
    }

    private fun counterToWire(s: SetStat): Map<String, Any> = mapOf("c" to s.c, "t" to s.t)

    private fun counters(raw: Any?, validKey: (String) -> Boolean): Map<String, SetStat> =
        entries(raw).mapNotNull { (k, v) ->
            if (!validKey(k)) return@mapNotNull null
            val m = v as? Map<*, *> ?: return@mapNotNull null
            val c = finite(m["c"]) ?: return@mapNotNull null
            val t = finite(m["t"]) ?: return@mapNotNull null
            k to SetStat(c, t.toLong())
        }.toMap()

    private fun entries(raw: Any?): List<Pair<String, Any?>> =
        (raw as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> (k as? String)?.let { it to v } } ?: emptyList()

    private fun finite(raw: Any?): Double? = (raw as? Number)?.toDouble()?.takeIf { it.isFinite() }
}
