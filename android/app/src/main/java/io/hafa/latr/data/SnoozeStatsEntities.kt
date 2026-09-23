package io.hafa.latr.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Entity(tableName = "snooze_sets")
data class SnoozeSetEntity(
    @PrimaryKey val id: String,
    val c: Double,
    val t: Long,
)

/** One quick-time clock slot's decayed count; [slot] is local "HHMM". */
@Entity(tableName = "snooze_tod")
data class SnoozeTodEntity(
    @PrimaryKey val slot: String,
    val c: Double,
    val t: Long,
)

@Dao
interface SnoozeStatsDao {
    @Query("SELECT * FROM snooze_sets")
    suspend fun getAllSets(): List<SnoozeSetEntity>

    @Query("SELECT * FROM snooze_tod")
    suspend fun getAllTod(): List<SnoozeTodEntity>

    @Upsert
    suspend fun upsertSet(entity: SnoozeSetEntity)

    @Upsert
    suspend fun upsertTod(entity: SnoozeTodEntity)

    @Query("DELETE FROM snooze_sets WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query("DELETE FROM snooze_tod WHERE slot = :slot")
    suspend fun deleteTod(slot: String)

    @Query("DELETE FROM snooze_sets")
    suspend fun clearSets()

    @Query("DELETE FROM snooze_tod")
    suspend fun clearTod()

    /** One commit's/undo's writes, atomically, so process death can't leave them half-applied. */
    @Transaction
    suspend fun applyCommitChanges(
        setUpsert: SnoozeSetEntity?,
        setDelete: String?,
        todUpsert: SnoozeTodEntity?,
        todDelete: String?,
    ) {
        setUpsert?.let { upsertSet(it) }
        setDelete?.let { deleteSet(it) }
        todUpsert?.let { upsertTod(it) }
        todDelete?.let { deleteTod(it) }
    }

    @Transaction
    suspend fun clearAll() {
        clearSets()
        clearTod()
    }
}
