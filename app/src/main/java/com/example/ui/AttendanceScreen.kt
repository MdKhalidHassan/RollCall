package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AttendanceFilter
import com.example.model.Student
import com.example.model.StudentPhotoCodec
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun AttendanceScreen(
    studentsState: UiState<List<Student>>,
    headerState: HeaderFormState,
    teacherOutputText: String,
    searchQuery: String,
    activeFilter: AttendanceFilter,
    isBusy: Boolean,
    onDateChanged: (Long) -> Unit,
    onCourseChanged: (String) -> Unit,
    onDepartmentChanged: (String) -> Unit,
    onBatchChanged: (String) -> Unit,
    onToggleSetupExpanded: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onFilterChanged: (AttendanceFilter) -> Unit,
    onToggleStudentPresence: (Student) -> Unit,
    onSetAllPresence: (Boolean) -> Unit,
    onLoadSampleBatch: () -> Unit,
    onOpenAddStudent: () -> Unit,
    onSaveClass: () -> Unit,
    onCopiedOutput: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 840.dp
        val allStudents = (studentsState as? UiState.Success)?.data.orEmpty()
        val presentCount = remember(allStudents) { allStudents.count { it.present } }
        val filteredStudents = remember(allStudents, searchQuery, activeFilter) {
            val q = searchQuery.trim().lowercase()
            allStudents.filter { s ->
                val matchesFilter = when (activeFilter) {
                    AttendanceFilter.ALL -> true
                    AttendanceFilter.PRESENT -> s.present
                    AttendanceFilter.ABSENT -> !s.present
                }
                val matchesQuery = q.isEmpty() ||
                    s.rollNumber.lowercase().contains(q) ||
                    s.name.lowercase().contains(q)
                matchesFilter && matchesQuery
            }
        }

        if (isWide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                ) {
                    RollCallControlsHeader(
                        allStudentsCount = allStudents.size,
                        presentCount = presentCount,
                        searchQuery = searchQuery,
                        activeFilter = activeFilter,
                        isBusy = isBusy,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onFilterChanged = onFilterChanged,
                        onSetAllPresence = onSetAllPresence
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ChecklistContent(
                        studentsState = studentsState,
                        filteredStudents = filteredStudents,
                        isBusy = isBusy,
                        onToggleStudentPresence = onToggleStudentPresence,
                        onLoadSampleBatch = onLoadSampleBatch,
                        onOpenAddStudent = onOpenAddStudent,
                        onRetry = onRetry,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 84.dp)
                ) {
                    ClassSetupCard(
                        headerState = headerState,
                        onDateChanged = onDateChanged,
                        onCourseChanged = onCourseChanged,
                        onDepartmentChanged = onDepartmentChanged,
                        onBatchChanged = onBatchChanged,
                        onToggleExpanded = onToggleSetupExpanded
                    )
                    TeacherOutputCard(
                        outputText = teacherOutputText,
                        presentCount = presentCount,
                        isBusy = isBusy,
                        onSaveClass = onSaveClass,
                        onCopied = onCopiedOutput
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = 92.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "setup_card") {
                    ClassSetupCard(
                        headerState = headerState,
                        onDateChanged = onDateChanged,
                        onCourseChanged = onCourseChanged,
                        onDepartmentChanged = onDepartmentChanged,
                        onBatchChanged = onBatchChanged,
                        onToggleExpanded = onToggleSetupExpanded
                    )
                }

                item(key = "teacher_output_card") {
                    TeacherOutputCard(
                        outputText = teacherOutputText,
                        presentCount = presentCount,
                        isBusy = isBusy,
                        onSaveClass = onSaveClass,
                        onCopied = onCopiedOutput
                    )
                }

                item(key = "roll_call_controls") {
                    RollCallControlsHeader(
                        allStudentsCount = allStudents.size,
                        presentCount = presentCount,
                        searchQuery = searchQuery,
                        activeFilter = activeFilter,
                        isBusy = isBusy,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onFilterChanged = onFilterChanged,
                        onSetAllPresence = onSetAllPresence
                    )
                }

                when (studentsState) {
                    UiState.Loading -> {
                        item(key = "loading") {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp)
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    is UiState.Error -> {
                        item(key = "error") {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = studentsState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onRetry) {
                                        Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }
                    }

                    is UiState.Success -> {
                        if (allStudents.isEmpty()) {
                            item(key = "empty_roster") {
                                EmptyRosterCard(
                                    onLoadSampleBatch = onLoadSampleBatch,
                                    onAddStudent = onOpenAddStudent,
                                    isBusy = isBusy
                                )
                            }
                        } else if (filteredStudents.isEmpty()) {
                            item(key = "empty_filter") {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No students match your search or filter.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(20.dp)
                                    )
                                }
                            }
                        } else {
                            items(
                                items = filteredStudents,
                                key = { it.id.ifBlank { "${it.rollNumber}_${it.name}" } }
                            ) { student ->
                                AttendanceStudentRow(
                                    student = student,
                                    onToggle = { onToggleStudentPresence(student) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistContent(
    studentsState: UiState<List<Student>>,
    filteredStudents: List<Student>,
    isBusy: Boolean,
    onToggleStudentPresence: (Student) -> Unit,
    onLoadSampleBatch: () -> Unit,
    onOpenAddStudent: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (studentsState) {
        UiState.Loading -> Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is UiState.Error -> Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = studentsState.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry) {
                    Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        is UiState.Success -> {
            if (studentsState.data.isEmpty()) {
                EmptyRosterCard(
                    onLoadSampleBatch = onLoadSampleBatch,
                    onAddStudent = onOpenAddStudent,
                    isBusy = isBusy,
                    modifier = modifier
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = modifier.fillMaxWidth()
                ) {
                    items(
                        items = filteredStudents,
                        key = { it.id.ifBlank { "${it.rollNumber}_${it.name}" } }
                    ) { student ->
                        AttendanceStudentRow(
                            student = student,
                            onToggle = { onToggleStudentPresence(student) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RollCallControlsHeader(
    allStudentsCount: Int,
    presentCount: Int,
    searchQuery: String,
    activeFilter: AttendanceFilter,
    isBusy: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onFilterChanged: (AttendanceFilter) -> Unit,
    onSetAllPresence: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Roll Call Checklist ($presentCount / $allStudentsCount)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (allStudentsCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { onSetAllPresence(true) },
                        enabled = !isBusy && presentCount < allStudentsCount,
                        modifier = Modifier.testTag("mark_all_present_button")
                    ) {
                        Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("All")
                    }
                    TextButton(
                        onClick = { onSetAllPresence(false) },
                        enabled = !isBusy && presentCount > 0,
                        modifier = Modifier.testTag("clear_all_attendance_button")
                    ) {
                        Icon(Icons.Rounded.Deselect, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }

        if (allStudentsCount > 0) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Quick jump by roll number or name...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attendance_search_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttendanceFilter.entries.forEach { filter ->
                    val count = when (filter) {
                        AttendanceFilter.ALL -> allStudentsCount
                        AttendanceFilter.PRESENT -> presentCount
                        AttendanceFilter.ABSENT -> (allStudentsCount - presentCount).coerceAtLeast(0)
                    }
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { onFilterChanged(filter) },
                        label = { Text("${filter.label} ($count)") },
                        leadingIcon = if (activeFilter == filter) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.CheckBox,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceStudentRow(
    student: Student,
    onToggle: () -> Unit
) {
    val photoBitmap = remember(student.photoData) {
        StudentPhotoCodec.decodeBase64ToImageBitmap(student.photoData)
    }
    val containerColor = if (student.present) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (student.present) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outline
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("attendance_student_card_${student.rollNumber}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap,
                    contentDescription = "${student.name} photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (student.present) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.width(52.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                ) {
                    Text(
                        text = student.rollNumber,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (student.present) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val contactSummary = listOfNotNull(
                    student.phone.takeIf { it.isNotBlank() },
                    student.gmail.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (contactSummary.isNotBlank()) {
                    Text(
                        text = contactSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Checkbox(
                checked = student.present,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.secondary,
                    checkmarkColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("attendance_checkbox_${student.rollNumber}")
            )
        }
    }
}
