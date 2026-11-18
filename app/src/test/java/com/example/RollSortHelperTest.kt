package com.example

import com.example.model.RollSortHelper
import com.example.model.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RollSortHelperTest {

    @Test
    fun sortRollsAscending_sortsNumericallyRegardlessOfSelectionOrder() {
        val randomInput = listOf("15", "03", "12", "01", "07", "2")
        val sorted = RollSortHelper.sortRollsAscending(randomInput)
        assertEquals(listOf("01", "2", "03", "07", "12", "15"), sorted)
    }

    @Test
    fun sortRollsAscending_handlesAlphanumericRollsNaturally() {
        val mixed = listOf("CS-10", "CS-02", "CS-01", "CS-12")
        val sorted = RollSortHelper.sortRollsAscending(mixed)
        assertEquals(listOf("CS-01", "CS-02", "CS-10", "CS-12"), sorted)
    }

    @Test
    fun buildTeacherOutput_containsOnlyHeaderAndSortedPresentRollsWithoutPii() {
        val students = listOf(
            Student(id = "1", rollNumber = "12", name = "Maliha Kabir", phone = "+8801725380110", gmail = "maliha@gmail.com", present = true, comment = "CR note", photoData = "PHOTO_BASE64"),
            Student(id = "2", rollNumber = "03", name = "Tariq Hassan", phone = "+8801814510103", gmail = "tariq@gmail.com", present = true, comment = "Front row", photoData = "PHOTO_2"),
            Student(id = "3", rollNumber = "02", name = "Nafisa Rahman", phone = "+8801712380102", gmail = "nafisa@gmail.com", present = false),
            Student(id = "4", rollNumber = "01", name = "Aarav Chowdhury", phone = "+8801711240101", gmail = "aarav@gmail.com", present = true)
        )

        val output = RollSortHelper.buildTeacherOutput(
            dateFormatted = "17-11-2026",
            courseName = "CSE 301",
            department = "CSE",
            batch = "Batch 24",
            students = students
        )

        val expected = """
            Date: 17-11-2026
            Course: CSE 301
            Department: CSE
            Batch: Batch 24
            Present Rolls: 01, 03, 12
        """.trimIndent()

        assertEquals(expected, output)
        assertFalse(output.contains("Maliha"))
        assertFalse(output.contains("tariq@gmail.com"))
        assertFalse(output.contains("+880"))
        assertFalse(output.contains("CR note"))
        assertFalse(output.contains("PHOTO_BASE64"))
        val presentLine = output.lines().last()
        assertFalse(presentLine.contains("02"))
    }
}
