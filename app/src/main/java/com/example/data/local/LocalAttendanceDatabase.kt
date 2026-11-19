package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_students")
data class LocalStudentEntity(
    @PrimaryKey val id: String,
    val rollNumber: String,
    val name: String,
    val phone: String,
    val gmail: String,
    val present: Boolean,
    val comment: String,
    val photoData: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "local_class_config")
data class LocalClassConfigEntity(
    @PrimaryKey val id: String = DEFAULT_CONFIG_ID,
    val courseName: String,
    val department: String,
    val batch: String,
    val updatedAtMillis: Long
) {
    companion object {
        const val DEFAULT_CONFIG_ID = "default"
    }
}

@Entity(tableName = "local_attendance_sessions")
data class LocalAttendanceSessionEntity(
    @PrimaryKey val id: String,
    val dateFormatted: String,
    val dateMillis: Long,
    val courseName: String,
    val department: String,
    val batch: String,
    val presentRollsJson: String,
    val totalStudents: Long,
    val formattedOutput: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Dao
interface LocalAttendanceDao {

    @Query("SELECT * FROM local_students")
    fun observeStudents(): Flow<List<LocalStudentEntity>>

    @Query("SELECT * FROM local_students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: String): LocalStudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudent(student: LocalStudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudents(students: List<LocalStudentEntity>)

    @Query("UPDATE local_students SET present = :present, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateStudentPresence(id: String, present: Boolean, updatedAt: Long)

    @Query("UPDATE local_students SET present = :present, updatedAtMillis = :updatedAt")
    suspend fun setAllPresence(present: Boolean, updatedAt: Long)

    @Query("DELETE FROM local_students WHERE id = :id")
    suspend fun deleteStudentById(id: String)

    @Query("SELECT * FROM local_class_config WHERE id = :id LIMIT 1")
    fun observeClassConfig(id: String = LocalClassConfigEntity.DEFAULT_CONFIG_ID): Flow<LocalClassConfigEntity?>

    @Query("SELECT * FROM local_class_config WHERE id = :id LIMIT 1")
    suspend fun getClassConfig(id: String = LocalClassConfigEntity.DEFAULT_CONFIG_ID): LocalClassConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClassConfig(config: LocalClassConfigEntity)

    @Query("SELECT * FROM local_attendance_sessions ORDER BY dateMillis DESC, createdAtMillis DESC")
    fun observeAttendanceSessions(): Flow<List<LocalAttendanceSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceSession(session: LocalAttendanceSessionEntity)

    @Query("DELETE FROM local_attendance_sessions WHERE id = :id")
    suspend fun deleteAttendanceSessionById(id: String)

    @Transaction
    suspend fun applyPresentRolls(rollSetLower: Set<String>, updatedAt: Long) {
        setAllPresence(false, updatedAt)
        val current = getAllStudentsOnce()
        val updated = current.map { student ->
            val shouldBePresent = student.rollNumber.trim().lowercase() in rollSetLower
            if (student.present != shouldBePresent) {
                student.copy(present = shouldBePresent, updatedAtMillis = updatedAt)
            } else {
                student
            }
        }
        if (updated.isNotEmpty()) {
            upsertStudents(updated)
        }
    }

    @Query("SELECT * FROM local_students")
    suspend fun getAllStudentsOnce(): List<LocalStudentEntity>
}

@Database(
    entities = [
        LocalStudentEntity::class,
        LocalClassConfigEntity::class,
        LocalAttendanceSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LocalAttendanceDatabase : RoomDatabase() {
    abstract fun attendanceDao(): LocalAttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: LocalAttendanceDatabase? = null

        fun getInstance(context: Context): LocalAttendanceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalAttendanceDatabase::class.java,
                    "rollcall_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
