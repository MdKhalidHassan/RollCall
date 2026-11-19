package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AttendanceSession
import com.example.model.ClassConfig
import com.example.model.RollSortHelper
import com.example.model.SampleBatchProvider
import com.example.model.Student
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AttendanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : AttendanceDataSource {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireActiveUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user access." }
        return uid
    }

    override fun observeStudents(userId: String): Flow<List<Student>> = flow {
        val uid = requireActiveUid(userId)
        val path = "users/$uid/students"
        emitAll(
            db.collection("users").document(uid).collection("students")
                .snapshots()
                .map { snapshot ->
                    val list = snapshot.toObjects(
                        Student::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    RollSortHelper.sortStudentsByRoll(list)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeClassConfig(userId: String): Flow<ClassConfig?> = flow {
        val uid = requireActiveUid(userId)
        val path = "users/$uid/class_configs/$DEFAULT_CONFIG_ID"
        emitAll(
            db.collection("users").document(uid).collection("class_configs")
                .document(DEFAULT_CONFIG_ID)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            ClassConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun saveClassConfig(
        userId: String,
        courseName: String,
        department: String,
        batch: String
    ): Result<Unit> {
        var docPath = "users/$userId/class_configs/$DEFAULT_CONFIG_ID"
        return try {
            val uid = requireActiveUid(userId)
            val safeCourse = courseName.trim().take(100)
            val safeDept = department.trim().take(100)
            val safeBatch = batch.trim().take(50)

            val docRef = db.collection("users").document(uid)
                .collection("class_configs").document(DEFAULT_CONFIG_ID)
            docPath = docRef.path

            val existingSnapshot = docRef.get().await()
            if (existingSnapshot.exists()) {
                docRef.update(
                    mapOf(
                        "courseName" to safeCourse,
                        "department" to safeDept,
                        "batch" to safeBatch,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "courseName" to safeCourse,
                        "department" to safeDept,
                        "batch" to safeBatch,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
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
    ): Result<Unit> {
        var docPath = "users/$userId/students"
        return try {
            val uid = requireActiveUid(userId)
            val cleanRoll = rollNumber.trim().take(20)
            val cleanName = name.trim().take(100)
            val cleanPhone = phone.trim().take(30)
            val cleanGmail = gmail.trim().take(120)
            val cleanComment = comment.trim().take(500)
            val cleanPhotoData = photoData.take(100_000)

            require(cleanRoll.isNotBlank()) { "Roll number is required." }
            require(cleanName.isNotBlank()) { "Student name is required." }

            val collection = db.collection("users").document(uid).collection("students")
            if (studentId.isNullOrBlank()) {
                val docRef = collection.document()
                docPath = docRef.path
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "rollNumber" to cleanRoll,
                        "name" to cleanName,
                        "phone" to cleanPhone,
                        "gmail" to cleanGmail,
                        "present" to present,
                        "comment" to cleanComment,
                        "photoData" to cleanPhotoData,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val docRef = collection.document(studentId)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "rollNumber" to cleanRoll,
                        "name" to cleanName,
                        "phone" to cleanPhone,
                        "gmail" to cleanGmail,
                        "present" to present,
                        "comment" to cleanComment,
                        "photoData" to cleanPhotoData,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (studentId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    override suspend fun toggleStudentPresence(
        userId: String,
        studentId: String,
        present: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/students/$studentId"
        return try {
            val uid = requireActiveUid(userId)
            require(studentId.isNotBlank()) { "Student ID cannot be blank." }
            val docRef = db.collection("users").document(uid)
                .collection("students").document(studentId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "present" to present,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun setAllPresence(
        userId: String,
        students: List<Student>,
        present: Boolean
    ): Result<Unit> {
        val basePath = "users/$userId/students"
        return try {
            val uid = requireActiveUid(userId)
            val targets = students.filter { it.id.isNotBlank() && it.present != present }
            if (targets.isEmpty()) return Result.success(Unit)

            val collection = db.collection("users").document(uid).collection("students")
            targets.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { student ->
                    batch.update(
                        collection.document(student.id),
                        mapOf(
                            "present" to present,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, basePath)
            Result.failure(e)
        }
    }

    override suspend fun populateSampleBatch(
        userId: String,
        existingRolls: Set<String>,
        seedHeaderIfEmpty: Boolean
    ): Result<Int> {
        val basePath = "users/$userId/students"
        return try {
            val uid = requireActiveUid(userId)
            val normalizedExisting = existingRolls.map { it.trim().lowercase() }.toSet()
            val toInsert = SampleBatchProvider.sampleStudents.filter {
                it.rollNumber.trim().lowercase() !in normalizedExisting
            }

            if (toInsert.isEmpty() && !seedHeaderIfEmpty) {
                return Result.success(0)
            }

            val userDoc = db.collection("users").document(uid)
            val studentsCol = userDoc.collection("students")
            val batch = db.batch()

            toInsert.forEach { seed ->
                val newDoc = studentsCol.document()
                batch.set(
                    newDoc,
                    mapOf(
                        "userId" to uid,
                        "rollNumber" to seed.rollNumber,
                        "name" to seed.name,
                        "phone" to seed.phone,
                        "gmail" to seed.gmail,
                        "present" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (seedHeaderIfEmpty) {
                val configRef = userDoc.collection("class_configs").document(DEFAULT_CONFIG_ID)
                val configSnap = configRef.get().await()
                if (!configSnap.exists()) {
                    batch.set(
                        configRef,
                        mapOf(
                            "userId" to uid,
                            "courseName" to SampleBatchProvider.defaultCourse,
                            "department" to SampleBatchProvider.defaultDepartment,
                            "batch" to SampleBatchProvider.defaultBatch,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(toInsert.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, basePath)
            Result.failure(e)
        }
    }

    override suspend fun deleteStudent(userId: String, studentId: String): Result<Unit> {
        var docPath = "users/$userId/students/$studentId"
        return try {
            val uid = requireActiveUid(userId)
            require(studentId.isNotBlank()) { "Student ID cannot be blank." }
            val docRef = db.collection("users").document(uid)
                .collection("students").document(studentId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override fun observeAttendanceSessions(userId: String): Flow<List<AttendanceSession>> = flow {
        val uid = requireActiveUid(userId)
        val path = "users/$uid/attendance_sessions"
        emitAll(
            db.collection("users").document(uid).collection("attendance_sessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AttendanceSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<AttendanceSession> { it.dateMillis }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
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
    ): Result<Unit> {
        var docPath = "users/$userId/attendance_sessions"
        return try {
            val uid = requireActiveUid(userId)
            val cleanDate = dateFormatted.trim().take(30).ifBlank {
                RollSortHelper.formatDateDdMmYyyy(System.currentTimeMillis())
            }
            val cleanCourse = courseName.trim().take(100)
            val cleanDept = department.trim().take(100)
            val cleanBatch = batch.trim().take(50)
            val cleanRolls = presentRolls.map { it.trim() }.filter { it.isNotEmpty() }.take(500)
            val cleanTotal = totalStudents.coerceIn(0, 10000).toLong()
            val cleanOutput = formattedOutput.take(5000)

            val docRef = db.collection("users").document(uid)
                .collection("attendance_sessions").document()
            docPath = docRef.path

            docRef.set(
                mapOf(
                    "userId" to uid,
                    "dateFormatted" to cleanDate,
                    "dateMillis" to dateMillis.coerceAtLeast(0L),
                    "courseName" to cleanCourse,
                    "department" to cleanDept,
                    "batch" to cleanBatch,
                    "presentRolls" to cleanRolls,
                    "totalStudents" to cleanTotal,
                    "formattedOutput" to cleanOutput,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun applyAttendanceSessionToRoster(
        userId: String,
        students: List<Student>,
        presentRolls: List<String>
    ): Result<Unit> {
        val basePath = "users/$userId/students"
        return try {
            val uid = requireActiveUid(userId)
            val rollSet = presentRolls.map { it.trim().lowercase() }.toSet()
            val targets = students.filter {
                it.id.isNotBlank() && it.present != (it.rollNumber.trim().lowercase() in rollSet)
            }
            if (targets.isEmpty()) return Result.success(Unit)

            val collection = db.collection("users").document(uid).collection("students")
            targets.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { student ->
                    val shouldBePresent = student.rollNumber.trim().lowercase() in rollSet
                    batch.update(
                        collection.document(student.id),
                        mapOf(
                            "present" to shouldBePresent,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, basePath)
            Result.failure(e)
        }
    }

    override suspend fun deleteAttendanceSession(userId: String, sessionId: String): Result<Unit> {
        var docPath = "users/$userId/attendance_sessions/$sessionId"
        return try {
            val uid = requireActiveUid(userId)
            require(sessionId.isNotBlank()) { "Session ID cannot be blank." }
            val docRef = db.collection("users").document(uid)
                .collection("attendance_sessions").document(sessionId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_CONFIG_ID = "default"
    }
}
