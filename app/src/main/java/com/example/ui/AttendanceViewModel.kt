package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AttendanceDataSource
import com.example.model.AttendanceFilter
import com.example.model.AttendanceSession
import com.example.model.ClassConfig
import com.example.model.RollSortHelper
import com.example.model.Student
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class HeaderFormState(
    val dateMillis: Long = System.currentTimeMillis(),
    val courseName: String = "",
    val department: String = "",
    val batch: String = "",
    val isSetupExpanded: Boolean = true
) {
    val dateFormatted: String
        get() = RollSortHelper.formatDateDdMmYyyy(dateMillis)
}

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(
    private val repository: AttendanceDataSource,
    private val currentUserId: String
) : ViewModel() {

    private val studentsRetryTrigger = MutableStateFlow(0)
    private val sessionsRetryTrigger = MutableStateFlow(0)

    val studentsState: StateFlow<UiState<List<Student>>> = studentsRetryTrigger
        .flatMapLatest {
            repository.observeStudents(currentUserId)
                .map<List<Student>, UiState<List<Student>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing students", rootError)
                    emit(UiState.Error(rootError.message ?: "Failed to load student roster"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val sessionsState: StateFlow<UiState<List<AttendanceSession>>> = sessionsRetryTrigger
        .flatMapLatest {
            repository.observeAttendanceSessions(currentUserId)
                .map<List<AttendanceSession>, UiState<List<AttendanceSession>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing attendance sessions", rootError)
                    emit(UiState.Error(rootError.message ?: "Failed to load attendance history"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun retryStudents() {
        studentsRetryTrigger.update { it + 1 }
    }

    fun retrySessions() {
        sessionsRetryTrigger.update { it + 1 }
    }

    private val _headerState = MutableStateFlow(HeaderFormState())
    val headerState: StateFlow<HeaderFormState> = _headerState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _attendanceFilter = MutableStateFlow(AttendanceFilter.ALL)
    val attendanceFilter: StateFlow<AttendanceFilter> = _attendanceFilter.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var hasHydratedRemoteConfig = false
    private var saveConfigJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeClassConfig(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing class config", firestoreError ?: error)
                }
                .collect { config: ClassConfig? ->
                    if (config != null && !hasHydratedRemoteConfig) {
                        hasHydratedRemoteConfig = true
                        _headerState.update { current ->
                            current.copy(
                                courseName = config.courseName,
                                department = config.department,
                                batch = config.batch
                            )
                        }
                    }
                }
        }
    }

    val teacherOutputText: StateFlow<String> = combine(
        _headerState,
        studentsState
    ) { header, studentsUi ->
        val list = (studentsUi as? UiState.Success)?.data.orEmpty()
        RollSortHelper.buildTeacherOutput(
            dateFormatted = header.dateFormatted,
            courseName = header.courseName,
            department = header.department,
            batch = header.batch,
            students = list
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RollSortHelper.buildTeacherOutput(
            dateFormatted = _headerState.value.dateFormatted,
            courseName = "",
            department = "",
            batch = "",
            students = emptyList()
        )
    )

    fun updateDateMillis(millis: Long) {
        _headerState.update { it.copy(dateMillis = millis) }
    }

    fun updateCourseName(value: String) {
        _headerState.update { it.copy(courseName = value) }
        scheduleHeaderSave()
    }

    fun updateDepartment(value: String) {
        _headerState.update { it.copy(department = value) }
        scheduleHeaderSave()
    }

    fun updateBatch(value: String) {
        _headerState.update { it.copy(batch = value) }
        scheduleHeaderSave()
    }

    fun toggleSetupExpanded() {
        _headerState.update { it.copy(isSetupExpanded = !it.isSetupExpanded) }
    }

    private fun scheduleHeaderSave() {
        hasHydratedRemoteConfig = true
        saveConfigJob?.cancel()
        saveConfigJob = viewModelScope.launch {
            delay(500L)
            val current = _headerState.value
            val result = repository.saveClassConfig(
                userId = currentUserId,
                courseName = current.courseName,
                department = current.department,
                batch = current.batch
            )
            if (result.isFailure) {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Couldn't save class setup")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilter(filter: AttendanceFilter) {
        _attendanceFilter.value = filter
    }

    fun toggleStudentPresence(student: Student) {
        if (student.id.isBlank()) return
        viewModelScope.launch {
            val result = repository.toggleStudentPresence(
                userId = currentUserId,
                studentId = student.id,
                present = !student.present
            )
            if (result.isFailure) {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to update attendance")
            }
        }
    }

    fun setAllPresence(present: Boolean) {
        val list = (studentsState.value as? UiState.Success)?.data.orEmpty()
        if (list.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.setAllPresence(currentUserId, list, present)
            _isBusy.value = false
            if (result.isSuccess) {
                _events.tryEmit(if (present) "Marked all students present" else "Cleared all attendance checks")
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to update roster")
            }
        }
    }

    fun upsertStudent(
        studentId: String?,
        rollNumber: String,
        name: String,
        phone: String,
        gmail: String,
        present: Boolean,
        comment: String,
        photoData: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.upsertStudent(
                userId = currentUserId,
                studentId = studentId,
                rollNumber = rollNumber,
                name = name,
                phone = phone,
                gmail = gmail,
                present = present,
                comment = comment,
                photoData = photoData
            )
            _isBusy.value = false
            if (result.isSuccess) {
                onSuccess()
                _events.tryEmit(if (studentId == null) "Student added" else "Student updated")
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to save student")
            }
        }
    }

    fun deleteStudent(student: Student) {
        if (student.id.isBlank()) return
        viewModelScope.launch {
            val result = repository.deleteStudent(currentUserId, student.id)
            if (result.isSuccess) {
                _events.tryEmit("Removed Roll ${student.rollNumber}")
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to remove student")
            }
        }
    }

    fun loadSampleBatch() {
        val currentList = (studentsState.value as? UiState.Success)?.data.orEmpty()
        val existingRolls = currentList.map { it.rollNumber }.toSet()
        val header = _headerState.value
        val shouldSeedHeader = header.courseName.isBlank() && header.department.isBlank() && header.batch.isBlank()
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.populateSampleBatch(
                userId = currentUserId,
                existingRolls = existingRolls,
                seedHeaderIfEmpty = shouldSeedHeader
            )
            _isBusy.value = false
            result.onSuccess { count ->
                if (shouldSeedHeader) {
                    hasHydratedRemoteConfig = false
                }
                val msg = if (count > 0) "Loaded $count sample students" else "Sample batch already loaded"
                _events.tryEmit(msg)
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to load sample batch")
            }
        }
    }

    fun saveCurrentAttendanceSession() {
        val header = _headerState.value
        val students = (studentsState.value as? UiState.Success)?.data.orEmpty()
        val sortedRolls = RollSortHelper.sortedPresentRolls(students)
        val formattedOutput = RollSortHelper.buildTeacherOutput(
            dateFormatted = header.dateFormatted,
            courseName = header.courseName,
            department = header.department,
            batch = header.batch,
            students = students
        )

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveAttendanceSession(
                userId = currentUserId,
                dateFormatted = header.dateFormatted,
                dateMillis = header.dateMillis,
                courseName = header.courseName,
                department = header.department,
                batch = header.batch,
                presentRolls = sortedRolls,
                totalStudents = students.size,
                formattedOutput = formattedOutput
            )
            _isBusy.value = false
            if (result.isSuccess) {
                val label = header.courseName.trim().ifBlank { "Class" }
                _events.tryEmit("Saved $label (${header.dateFormatted}) to History")
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to save class attendance")
            }
        }
    }

    fun deleteAttendanceSession(session: AttendanceSession) {
        if (session.id.isBlank()) return
        viewModelScope.launch {
            val result = repository.deleteAttendanceSession(currentUserId, session.id)
            if (result.isSuccess) {
                _events.tryEmit("Deleted record for ${session.dateFormatted}")
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to delete record")
            }
        }
    }

    fun loadSessionIntoChecklist(session: AttendanceSession, onLoaded: () -> Unit) {
        val students = (studentsState.value as? UiState.Success)?.data.orEmpty()
        _headerState.update {
            it.copy(
                dateMillis = if (session.dateMillis > 0L) session.dateMillis else it.dateMillis,
                courseName = session.courseName,
                department = session.department,
                batch = session.batch
            )
        }
        scheduleHeaderSave()
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.applyAttendanceSessionToRoster(
                userId = currentUserId,
                students = students,
                presentRolls = session.presentRolls
            )
            _isBusy.value = false
            if (result.isSuccess) {
                onLoaded()
                _events.tryEmit("Loaded ${session.dateFormatted} attendance into checklist")
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to load checklist")
            }
        }
    }

    private companion object {
        const val TAG = "AttendanceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
