package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalAttendanceDatabase
import com.example.data.local.LocalAttendanceRepository
import com.example.model.RollSortHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalAttendanceRepositoryTest {

    private lateinit var db: LocalAttendanceDatabase
    private lateinit var repository: LocalAttendanceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LocalAttendanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LocalAttendanceRepository(db.attendanceDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun offlineMode_savesConfigStudentsAndSortedAttendanceHistory() = runBlocking {
        val uid = LocalAttendanceRepository.OFFLINE_USER_ID

        assertTrue(repository.saveClassConfig(uid, "CSE 301", "CSE", "Batch 24").isSuccess)
        val config = repository.observeClassConfig(uid).first()
        assertEquals("CSE 301", config?.courseName)

        assertTrue(
            repository.upsertStudent(
                userId = uid,
                studentId = null,
                rollNumber = "12",
                name = "Maliha Kabir",
                phone = "01700000012",
                gmail = "maliha@gmail.com",
                present = true,
                comment = "Lab group B",
                photoData = "SAMPLE_PHOTO"
            ).isSuccess
        )
        assertTrue(
            repository.upsertStudent(
                userId = uid,
                studentId = null,
                rollNumber = "03",
                name = "Tariq Hassan",
                phone = "01700000003",
                gmail = "tariq@gmail.com",
                present = true,
                comment = "CR",
                photoData = ""
            ).isSuccess
        )

        val students = repository.observeStudents(uid).first()
        assertEquals(listOf("03", "12"), students.map { it.rollNumber })
        assertEquals("CR", students.first().comment)
        assertEquals("SAMPLE_PHOTO", students.last().photoData)

        val output = RollSortHelper.buildTeacherOutput(
            dateFormatted = "19-11-2026",
            courseName = "CSE 301",
            department = "CSE",
            batch = "Batch 24",
            students = students
        )
        assertTrue(
            repository.saveAttendanceSession(
                userId = uid,
                dateFormatted = "19-11-2026",
                dateMillis = 1795046400000L,
                courseName = "CSE 301",
                department = "CSE",
                batch = "Batch 24",
                presentRolls = RollSortHelper.sortedPresentRolls(students),
                totalStudents = students.size,
                formattedOutput = output
            ).isSuccess
        )

        val sessions = repository.observeAttendanceSessions(uid).first()
        assertEquals(1, sessions.size)
        assertEquals(listOf("03", "12"), sessions.first().presentRolls)
    }
}
