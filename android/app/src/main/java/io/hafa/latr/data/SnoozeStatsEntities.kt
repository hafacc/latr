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

/** A lifetime count of committed snoozes per pick log key, "1".."20" or "none". */
@Entity(tableName = "snooze_picks")
data class SnoozePickEntity(
    @PrimaryKey val pick: String,
    val n: Long,
)

@Dao
interface SnoozeStatsDao {
    @Query("SELECT * FROM snooze_sets")
    suspend fun getAllSets(): List<SnoozeSetEntity>

    @Query("SELECT * FROM snooze_tod")
    suspend fun getAllTod(): List<SnoozeTodEntity>

    @Query("SELECT * FROM snooze_picks")
    suspend fun getAllPicks(): List<SnoozePickEntity>

    @Upsert
    suspend fun upsertPick(entity: SnoozePickEntity)

    @Query("DELETE FROM snooze_picks WHERE pick = :pick")
    suspend fun deletePick(pick: String)

    @Query("DELETE FROM snooze_picks")
    suspend fun clearPicks()

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
        setUpserts: List<SnoozeSetEntity>,
        setDelete: String?,
        todUpserts: List<SnoozeTodEntity>,
        todDelete: String?,
        pickUpsert: SnoozePickEntity?,
        pickDelete: String?,
    ) {
        for (set in setUpserts) upsertSet(set)
        setDelete?.let { deleteSet(it) }
        for (slot in todUpserts) upsertTod(slot)
        todDelete?.let { deleteTod(it) }
        pickUpsert?.let { upsertPick(it) }
        pickDelete?.let { deletePick(it) }
    }

    @Transaction
    suspend fun replaceSets(sets: List<SnoozeSetEntity>) {
        clearSets()
        for (set in sets) upsertSet(set)
    }

    @Transaction
    suspend fun clearAll() {
        clearSets()
        clearTod()
        clearPicks()
    }
}
