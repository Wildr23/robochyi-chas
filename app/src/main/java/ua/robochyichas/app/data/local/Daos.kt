package ua.robochyichas.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE deletedAtEpochMs IS NULL ORDER BY date ASC, createdAtEpochMs ASC, id ASC")
    fun observeActiveShifts(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE deletedAtEpochMs IS NULL ORDER BY date ASC, createdAtEpochMs ASC, id ASC")
    suspend fun getActiveShifts(): List<ShiftEntity>

    @Query("SELECT * FROM shifts ORDER BY date ASC, createdAtEpochMs ASC, id ASC")
    suspend fun getAllShiftsIncludingDeleted(): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shift: ShiftEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(shifts: List<ShiftEntity>)

    @Query("UPDATE shifts SET deletedAtEpochMs = :deletedAt, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun softDeleteById(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE shifts SET deletedAtEpochMs = NULL, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun restoreById(id: Long, updatedAt: Long)

    @Query("UPDATE shifts SET deletedAtEpochMs = :deletedAt, updatedAtEpochMs = :updatedAt WHERE deletedAtEpochMs IS NULL")
    suspend fun softDeleteAllActive(deletedAt: Long, updatedAt: Long)

    @Query("DELETE FROM shifts")
    suspend fun hardDeleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}
