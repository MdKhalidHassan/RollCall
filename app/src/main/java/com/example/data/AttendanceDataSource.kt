package com.example.data

import com.example.model.AttendanceSession
import com.example.model.ClassConfig
import com.example.model.Student
import kotlinx.coroutines.flow.Flow

interface AttendanceDataSource {
    fun observeStudents(userId: String): Flow<List<Student>>

    fun observeClassConfig(userId: String): Flow<ClassConfig?>

    suspend fun saveClassConfig(
        userId: String,
        courseName: String,
        department: String,
        batch: String
    ): Result<Unit>

    suspend fun upsertStudent(
        userId: String,
        studentId: String?,
        rollNumber: String,
        name: String,
        phone: String,
        gmail: String,
        present: Boolean,
        comment: String = "",
        photoData: String = ""
    ): Result<Unit>

    suspend fun toggleStudentPresence(
        userId: String,
        studentId: String,
        present: Boolean
    ): Result<Unit>

    suspend fun setAllPresence(
        userId: String,
        students: List<Student>,
        present: Boolean
    ): Result<Unit>

    suspend fun populateSampleBatch(
        userId: String,
        existingRolls: Set<String>,
        seedHeaderIfEmpty: Boolean
    ): Result<Int>

    suspend fun deleteStudent(userId: String, studentId: String): Result<Unit>

    fun observeAttendanceSessions(userId: String): Flow<List<AttendanceSession>>

    suspend fun saveAttendanceSession(
        userId: String,
        dateFormatted: String,
        dateMillis: Long,
        courseName: String,
        department: String,
        batch: String,
        presentRolls: List<String>,
        totalStudents: Int,
        formattedOutput: String
    ): Result<Unit>

    suspend fun applyAttendanceSessionToRoster(
        userId: String,
        students: List<Student>,
        presentRolls: List<String>
    ): Result<Unit>

    suspend fun deleteAttendanceSession(userId: String, sessionId: String): Result<Unit>
}
