package io.hafa.latr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.Timestamp

class Converters {
    @TypeConverter
    fun fromTodoState(state: TodoState): String = state.name

    // Not valueOf: rows persisted by an older build still hold "SNOOZED".
    @TypeConverter
    fun toTodoState(value: String): TodoState = Todo.readState(value)

    // Millis-truncated; Room is signed-out only, never feeds the conflict rule.
    @TypeConverter
    fun fromTimestamp(t: Timestamp?): Long? =
        t?.let { it.seconds * 1000 + it.nanoseconds / 1_000_000 }

    // floorDiv/floorMod so negative millis (pre-epoch) round to non-negative
    // nanos — the Timestamp constructor rejects negative nanoseconds.
    @TypeConverter
    fun toTimestamp(millis: Long?): Timestamp? = millis?.let {
        Timestamp(Math.floorDiv(it, 1000), (Math.floorMod(it, 1000) * 1_000_000).toInt())
    }
}

@Database(
    entities = [Todo::class, SnoozeSetEntity::class, SnoozeTodEntity::class],
    version = 11,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun snoozeStatsDao(): SnoozeStatsDao

    companion object {
        @Volatile
        private var INSTANCE: TodoDatabase? = null

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        private const val CREATE_SNOOZE_SETS =
            "CREATE TABLE IF NOT EXISTS snooze_sets (id TEXT NOT NULL PRIMARY KEY, c REAL NOT NULL, t INTEGER NOT NULL)"
        private const val CREATE_SNOOZE_TOD =
            "CREATE TABLE IF NOT EXISTS snooze_tod (slot TEXT NOT NULL PRIMARY KEY, c REAL NOT NULL, t INTEGER NOT NULL)"

        private val MIGRATION_9_11 = object : Migration(9, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_SNOOZE_SETS)
                db.execSQL(CREATE_SNOOZE_TOD)
            }
        }

        // v10 was a pre-release build keyed by time-of-day bucket; its sets can't be converted.
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS snooze_patterns")
                db.execSQL(CREATE_SNOOZE_SETS)
                db.execSQL("DELETE FROM snooze_sets")
                db.execSQL(CREATE_SNOOZE_TOD)
            }
        }

        // v9: serverModifiedAt → nullable (Timestamp via Converter); destructive.
        fun getDatabase(context: Context): TodoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TodoDatabase::class.java,
                    "todo_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_9_11, MIGRATION_10_11)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
