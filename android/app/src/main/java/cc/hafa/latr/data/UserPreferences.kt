package cc.hafa.latr.data

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    // Excluded from backup and device transfer (res/xml), so a restored install gets a fresh identity.
    private val snoozeIdentity = context.getSharedPreferences(SNOOZE_IDENTITY_FILE, Context.MODE_PRIVATE)

    /** True when this install had no snooze identity at startup: a fresh install or a restore from backup. */
    val snoozeIdentityWasMissing: Boolean

    init {
        if (prefs.contains("morning_minutes") || prefs.contains("evening_minutes")) {
            prefs.edit {
                remove("morning_minutes")
                remove("evening_minutes")
            }
        }
        if (!snoozeIdentity.contains(KEY_DEVICE_ID) && prefs.contains(KEY_DEVICE_ID)) {
            snoozeIdentity.edit {
                for (key in IDENTITY_KEYS) {
                    when (val value = prefs.all[key]) {
                        is String -> putString(key, value)
                        is Long -> putLong(key, value)
                    }
                }
            }
            prefs.edit { for (key in IDENTITY_KEYS) remove(key) }
        }
        snoozeIdentityWasMissing = !snoozeIdentity.contains(KEY_DEVICE_ID)
    }

    val deviceId: String
        get() {
            val existing = snoozeIdentity.getString(KEY_DEVICE_ID, null)
            if (existing != null) return existing
            return rotateDeviceId()
        }

    fun rotateDeviceId(): String {
        val generated = UUID.randomUUID().toString()
        snoozeIdentity.edit { putString(KEY_DEVICE_ID, generated) }
        return generated
    }

    var lastCustomTarget: Long?
        get() = snoozeIdentity.getLong(KEY_LAST_CUSTOM_TARGET, -1L).takeIf { it >= 0 }
        set(value) {
            snoozeIdentity.edit {
                if (value == null) remove(KEY_LAST_CUSTOM_TARGET) else putLong(KEY_LAST_CUSTOM_TARGET, value)
            }
        }

    var lastCustomAt: Long?
        get() = snoozeIdentity.getLong(KEY_LAST_CUSTOM_AT, -1L).takeIf { it >= 0 }
        set(value) {
            snoozeIdentity.edit {
                if (value == null) remove(KEY_LAST_CUSTOM_AT) else putLong(KEY_LAST_CUSTOM_AT, value)
            }
        }

    /** Set once the Room todos an older build left behind while signed in have been cleared. */
    var localTodosMoved: Boolean
        get() = prefs.getBoolean(KEY_LOCAL_TODOS_MOVED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_LOCAL_TODOS_MOVED, value) }
        }

    /** The [SnoozeStatsWire.PARTITION_VERSION] this device's Room sets are in; 0 for sets saved before versions existed. */
    var snoozeSetsVersion: Int
        get() = snoozeIdentity.getInt(KEY_SETS_VERSION, 0)
        set(value) {
            snoozeIdentity.edit { putInt(KEY_SETS_VERSION, value) }
        }

    /** Which account's snooze-learning history this device's local partition currently belongs to. */
    var snoozeStatsOwnerUid: String?
        get() = snoozeIdentity.getString(KEY_OWNER_UID, null)
        set(value) {
            snoozeIdentity.edit {
                if (value == null) remove(KEY_OWNER_UID) else putString(KEY_OWNER_UID, value)
            }
        }

    private companion object {
        const val SNOOZE_IDENTITY_FILE = "snooze_identity"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_OWNER_UID = "snooze_stats_owner_uid"
        const val KEY_LAST_CUSTOM_TARGET = "last_custom_target"
        const val KEY_LAST_CUSTOM_AT = "last_custom_at"
        const val KEY_SETS_VERSION = "snooze_sets_version"
        const val KEY_LOCAL_TODOS_MOVED = "local_todos_moved"
        val IDENTITY_KEYS = listOf(KEY_DEVICE_ID, KEY_OWNER_UID, KEY_LAST_CUSTOM_TARGET, KEY_LAST_CUSTOM_AT)
    }
}
