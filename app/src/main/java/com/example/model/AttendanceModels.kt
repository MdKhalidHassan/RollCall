package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Keep
@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val userId: String = "",
    val rollNumber: String = "",
    val name: String = "",
    val phone: String = "",
    val gmail: String = "",
    val present: Boolean = false,
    val comment: String = "",
    val photoData: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ClassConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val courseName: String = "",
    val department: String = "",
    val batch: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AttendanceSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateFormatted: String = "",
    val dateMillis: Long = 0L,
    val courseName: String = "",
    val department: String = "",
    val batch: String = "",
    val presentRolls: List<String> = emptyList(),
    val totalStudents: Long = 0L,
    val formattedOutput: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class AttendanceFilter(val label: String) {
    ALL("All"),
    PRESENT("Present"),
    ABSENT("Absent")
}

object RollSortHelper {

    /**
     * Comparator for roll numbers that sorts purely numeric rolls in ascending numerical order
     * (e.g., 1, 2, 03, 10, 12) and handles mixed alphanumeric rolls (e.g., CS-02 before CS-10)
     * naturally without lexicographical "10 before 2" mistakes.
     */
    val rollComparator: Comparator<String> = Comparator { a, b ->
        val trimmedA = a.trim()
        val trimmedB = b.trim()
        val numA = trimmedA.toLongOrNull()
        val numB = trimmedB.toLongOrNull()
        if (numA != null && numB != null) {
            val numCmp = numA.compareTo(numB)
            if (numCmp != 0) numCmp else trimmedA.compareTo(trimmedB, ignoreCase = true)
        } else {
            compareNatural(trimmedA, trimmedB)
        }
    }

    private fun compareNatural(a: String, b: String): Int {
        val tokensA = tokenize(a)
        val tokensB = tokenize(b)
        val count = minOf(tokensA.size, tokensB.size)
        for (i in 0 until count) {
            val tA = tokensA[i]
            val tB = tokensB[i]
            val nA = tA.toLongOrNull()
            val nB = tB.toLongOrNull()
            val cmp = if (nA != null && nB != null) {
                nA.compareTo(nB)
            } else {
                tA.compareTo(tB, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return tokensA.size.compareTo(tokensB.size)
    }

    private fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            val isDigit = input[i].isDigit()
            val start = i
            while (i < input.length && input[i].isDigit() == isDigit) {
                i++
            }
            tokens.add(input.substring(start, i))
        }
        return tokens
    }

    fun sortRollsAscending(rolls: List<String>): List<String> =
        rolls.map { it.trim() }.filter { it.isNotEmpty() }.sortedWith(rollComparator)

    fun sortStudentsByRoll(students: List<Student>): List<Student> =
        students.sortedWith { s1, s2 -> rollComparator.compare(s1.rollNumber, s2.rollNumber) }

    fun sortedPresentRolls(students: List<Student>): List<String> =
        students
            .filter { it.present && it.rollNumber.isNotBlank() }
            .map { it.rollNumber.trim() }
            .sortedWith(rollComparator)

    fun formatDateDdMmYyyy(timeMillis: Long): String {
        val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        return formatter.format(Date(timeMillis))
    }

    /**
     * Generates the clean teacher-ready output containing ONLY header info and
     * ascending numerically sorted present roll numbers (no names, phones, or emails).
     */
    fun buildTeacherOutput(
        dateFormatted: String,
        courseName: String,
        department: String,
        batch: String,
        students: List<Student>
    ): String {
        val sortedRolls = sortedPresentRolls(students)
        val rollsLine = if (sortedRolls.isEmpty()) "None" else sortedRolls.joinToString(", ")
        return buildString {
            appendLine("Date: ${dateFormatted.ifBlank { formatDateDdMmYyyy(System.currentTimeMillis()) }}")
            appendLine("Course: ${courseName.trim().ifBlank { "—" }}")
            appendLine("Department: ${department.trim().ifBlank { "—" }}")
            appendLine("Batch: ${batch.trim().ifBlank { "—" }}")
            append("Present Rolls: $rollsLine")
        }
    }
}

object SampleBatchProvider {
    data class SampleStudentSeed(
        val rollNumber: String,
        val name: String,
        val phone: String,
        val gmail: String
    )

    val defaultCourse = "CSE 301: Algorithms"
    val defaultDepartment = "Computer Science & Engineering"
    val defaultBatch = "Batch 24"

    val sampleStudents: List<SampleStudentSeed> = listOf(
        SampleStudentSeed("01", "Aarav Chowdhury", "+880 1711-240101", "aarav.chowdhury@gmail.com"),
        SampleStudentSeed("02", "Nafisa Rahman", "+880 1712-380102", "nafisa.rahman@gmail.com"),
        SampleStudentSeed("03", "Tariq Hassan", "+880 1814-510103", "tariq.hassan@gmail.com"),
        SampleStudentSeed("04", "Meherin Sultana", "+880 1911-620104", "meherin.sultana@gmail.com"),
        SampleStudentSeed("05", "Zayed Karim", "+880 1615-730105", "zayed.karim@gmail.com"),
        SampleStudentSeed("06", "Farhana Akter", "+880 1718-840106", "farhana.akter@gmail.com"),
        SampleStudentSeed("07", "Samiul Islam", "+880 1819-950107", "samiul.islam@gmail.com"),
        SampleStudentSeed("08", "Tasnim Jahan", "+880 1720-160108", "tasnim.jahan@gmail.com"),
        SampleStudentSeed("09", "Rafidul Alam", "+880 1922-270109", "rafidul.alam@gmail.com"),
        SampleStudentSeed("10", "Maliha Kabir", "+880 1725-380110", "maliha.kabir@gmail.com"),
        SampleStudentSeed("11", "Imran Mahmud", "+880 1826-490111", "imran.mahmud@gmail.com"),
        SampleStudentSeed("12", "Sadia Afrin", "+880 1628-500112", "sadia.afrin@gmail.com")
    )
}
