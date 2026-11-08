package com.mohit.nudge.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines WHERE archivedEpochDay IS NULL ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines")
    fun observeAll(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines")
    suspend fun getAll(): List<Medicine>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun get(id: Long): Medicine?

    @Insert
    suspend fun insert(medicine: Medicine): Long

    @Update
    suspend fun update(medicine: Medicine)
}

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE epochDay = :day")
    fun observeDay(day: Long): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs WHERE epochDay = :day")
    suspend fun getDay(day: Long): List<DoseLog>

    @Query("SELECT * FROM dose_logs WHERE epochDay BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<DoseLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<DoseLog>)

    @Query("DELETE FROM dose_logs WHERE epochDay = :day AND slot = :slot AND medicineId = :medicineId")
    suspend fun delete(day: Long, slot: String, medicineId: Long)
}

@Database(entities = [Medicine::class, DoseLog::class], version = 2, exportSchema = false)
abstract class NudgeDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun doseLogDao(): DoseLogDao
}

/** v2: repeat patterns. Existing medicines become "every day". */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medicines ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE medicines ADD COLUMN weekdayMask INTEGER NOT NULL DEFAULT 0")
    }
}
