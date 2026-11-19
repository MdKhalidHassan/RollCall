package com.example.data.local

import com.example.data.AttendanceDataSource
import com.example.model.AttendanceSession
import com.example.model.ClassConfig
import com.example.model.RollSortHelper
import com.example.model.SampleBatchProvider
import com.example.model.Student
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.util.Date
import java.util.UUID

class LocalAttendanceRepository(
    private val dao: LocalAttendanceDao
) : AttendanceDataSource {

    override fun observeStudents(userId: String): Flow<List<Student>> =
        dao.observeStudents().map { entities ->
            val mapped = entities.map { entity ->
                Student(
                    id = entity.id,
                    userId = OFFLINE_USER_ID,
                    rollNumber = entity.rollNumber,
                    name = entity.name,
                    phone = entity.phone,
                    gmail = entity.gmail,
                    present = entity.present,
                    comment = entity.comment,
                    photoData = entity.photoData,
                    createdAt = Timestamp(Date(entity.createdAtMillis)),
                    updatedAt = Timestamp(Date(entity.updatedAtMillis))
                )
            }
            RollSortHelper.sortStudentsByRoll(mapped)
        }

    override fun observeClassConfig(userId: String): Flow<ClassConfig?> =
        dao.observeClassConfig().map { entity ->
            entity?.let {
                ClassConfig(
                    id = it.id,
                    userId = OFFLINE_USER_ID,
                    courseName = it.courseName,
                    department = it.department,
                    batch = it.batch,
                    updatedAt = Timestamp(Date(it.updatedAtMillis))
                )
            }
        }

    override suspend fun saveClassConfig(
        userId: String,
        courseName: String,
        department: String,
        batch: String
    ): Result<Unit> = runCatchingSuspending {
        dao.upsertClassConfig(
            LocalClassConfigEntity(
                id = LocalClassConfigEntity.DEFAULT_CONFIG_ID,
                courseName = courseName.trim().take(100),
                department = department.trim().take(100),
                batch = batch.trim().take(50),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun upsertStudent(
        userId: String,
        studentId: String?,
        rollNumber: String,
        name: String,
        phone: String,
        gmail: String,
        present: Boolean,
        comment: String,
        photoData: String
    ): Result<Unit> = runCatchingSuspending {
        val cleanRoll = rollNumber.trim().take(20)
        val cleanName = name.trim().take(100)
        require(cleanRoll.isNotBlank()) { "Roll number is required." }
        require(cleanName.isNotBlank()) { "Student name is required." }

        val now = System.currentTimeMillis()
        val existing = studentId?.takeIf { it.isNotBlank() }?.let { dao.getStudentById(it) }
        val targetId = existing?.id ?: studentId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        dao.upsertStudent(
            LocalStudentEntity(
                id = targetId,
                rollNumber = cleanRoll,
                name = cleanName,
                phone = phone.trim().take(30),
                gmail = gmail.trim().take(120),
                present = present,
                comment = comment.trim().take(500),
                photoData = photoData.take(100_000),
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now
            )
        )
    }

    override suspend fun toggleStudentPresence(
        userId: String,
        studentId: String,
        present: Boolean
    ): Result<Unit> = runCatchingSuspending {
        require(studentId.isNotBlank()) { "Student ID cannot be blank." }
        dao.updateStudentPresence(studentId, present, System.currentTimeMillis())
    }

    override suspend fun setAllPresence(
        userId: String,
        students: List<Student>,
        present: Boolean
    ): Result<Unit> = runCatchingSuspending {
        dao.setAllPresence(present, System.currentTimeMillis())
    }

    override suspend fun populateSampleBatch(
        userId: String,
        existingRolls: Set<String>,
        seedHeaderIfEmpty: Boolean
    ): Result<Int> = runCatchingSuspending {
        val normalizedExisting = existingRolls.map { it.trim().lowercase() }.toSet()
        val toInsert = SampleBatchProvider.sampleStudents.filter {
            it.rollNumber.trim().lowercase() !in normalizedExisting
        }
        val now = System.currentTimeMillis()

        if (toInsert.isNotEmpty()) {
            val entities = toInsert.map { seed ->
                LocalStudentEntity(
                    id = UUID.randomUUID().toString(),
                    rollNumber = seed.rollNumber,
                    name = seed.name,
                    phone = seed.phone,
                    gmail = seed.gmail,
                    present = false,
                    comment = "",
                    photoData = "",
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            }
            dao.upsertStudents(entities)
        }

        if (seedHeaderIfEmpty && dao.getClassConfig() == null) {
            dao.upsertClassConfig(
                LocalClassConfigEntity(
                    id = LocalClassConfigEntity.DEFAULT_CONFIG_ID,
                    courseName = SampleBatchProvider.defaultCourse,
                    department = SampleBatchProvider.defaultDepartment,
                    batch = SampleBatchProvider.defaultBatch,
                    updatedAtMillis = now
                )
            )
        }
        toInsert.size
    }

    override suspend fun deleteStudent(userId: String, studentId: String): Result<Unit> =
        runCatchingSuspending {
            require(studentId.isNotBlank()) { "Student ID cannot be blank." }
            dao.deleteStudentById(studentId)
        }

    override fun observeAttendanceSessions(userId: String): Flow<List<AttendanceSession>> =
        dao.observeAttendanceSessions().map { entities ->
            entities.map { entity ->
                AttendanceSession(
                    id = entity.id,
                    userId = OFFLINE_USER_ID,
                    dateFormatted = entity.dateFormatted,
                    dateMillis = entity.dateMillis,
                    courseName = entity.courseName,
                    department = entity.department,
                    batch = entity.batch,
                    presentRolls = decodeRollsJson(entity.presentRollsJson),
                    totalStudents = entity.totalStudents,
                    formattedOutput = entity.formattedOutput,
                    createdAt = Timestamp(Date(entity.createdAtMillis)),
                    updatedAt = Timestamp(Date(entity.updatedAtMillis))
                )
            }
        }

    override suspend fun saveAttendanceSession(
        userId: String,
        dateFormatted: String,
        dateMillis: Long,
        courseName: String,
        department: String,
        batch: String,
        presentRolls: List<String>,
        totalStudents: Int,
        formattedOutput: String
    ): Result<Unit> = runCatchingSuspending {
        val now = System.currentTimeMillis()
        val cleanDate = dateFormatted.trim().take(30).ifBlank {
            RollSortHelper.formatDateDdMmYyyy(now)
        }
        val cleanRolls = presentRolls.map { it.trim() }.filter { it.isNotEmpty() }.take(500)
        dao.insertAttendanceSession(
            LocalAttendanceSessionEntity(
                id = UUID.randomUUID().toString(),
                dateFormatted = cleanDate,
                dateMillis = dateMillis.coerceAtLeast(0L),
                courseName = courseName.trim().take(100),
                department = department.trim().take(100),
                batch = batch.trim().take(50),
                presentRollsJson = encodeRollsJson(cleanRolls),
                totalStudents = totalStudents.coerceIn(0, 10000).toLong(),
                formattedOutput = formattedOutput.take(5000),
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
    }

    override suspend fun applyAttendanceSessionToRoster(
        userId: String,
        students: List<Student>,
        presentRolls: List<String>
    ): Result<Unit> = runCatchingSuspending {
        val rollSet = presentRolls.map { it.trim().lowercase() }.toSet()
        dao.applyPresentRolls(rollSet, System.currentTimeMillis())
    }

    override suspend fun deleteAttendanceSession(userId: String, sessionId: String): Result<Unit> =
        runCatchingSuspending {
            require(sessionId.isNotBlank()) { "Session ID cannot be blank." }
            dao.deleteAttendanceSessionById(sessionId)
        }

    private suspend inline fun <T> runCatchingSuspending(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun encodeRollsJson(rolls: List<String>): String =
        JSONArray().apply { rolls.forEach { put(it) } }.toString()

    private fun decodeRollsJson(raw: String): List<String> =
        try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }

    companion object {
        const val OFFLINE_USER_ID = "offline_local_device"
    }
}
