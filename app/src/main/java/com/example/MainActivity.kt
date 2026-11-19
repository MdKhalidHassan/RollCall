package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AttendanceRepository
import com.example.data.local.AppPreferences
import com.example.data.local.LocalAttendanceDatabase
import com.example.data.local.LocalAttendanceRepository
import com.example.model.Student
import com.example.ui.AttendanceScreen
import com.example.ui.AttendanceViewModel
import com.example.ui.DeveloperIdentitySheet
import com.example.ui.HistoryScreen
import com.example.ui.RosterScreen
import com.example.ui.StudentEditorSheet
import com.example.ui.UiState
import com.example.ui.UserGuideSheet
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.signOut
import com.example.ui.theme.RollCallTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RollCallTheme {
                AppNavigation()
            }
        }
    }
}

private enum class HomeTab {
    ATTENDANCE,
    HISTORY,
    ROSTER
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val appPreferences = remember(context) { AppPreferences(context) }

    var isGuestMode by remember { mutableStateOf(appPreferences.isGuestMode()) }
    var showUserGuide by remember { mutableStateOf(false) }
    var showDeveloperIdentity by remember { mutableStateOf(false) }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null && !isGuestMode) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null && isGuestMode) {
            appPreferences.setGuestMode(false)
            isGuestMode = false
        }
    }

    if (showUserGuide) {
        UserGuideSheet(onDismiss = { showUserGuide = false })
    }

    if (showDeveloperIdentity) {
        DeveloperIdentitySheet(
            onDismiss = { showDeveloperIdentity = false }
        )
    }

    val user = currentUser
    when {
        user != null -> {
            key(user.uid) {
                RollCallHome(
                    currentUser = user,
                    credentialManager = credentialManager,
                    onOpenUserGuide = { showUserGuide = true },
                    onOpenDeveloperIdentity = { showDeveloperIdentity = true },
                    onSignInFromGuest = {},
                    onReturnToWelcome = {}
                )
            }
        }

        isGuestMode -> {
            key(LocalAttendanceRepository.OFFLINE_USER_ID) {
                RollCallHome(
                    currentUser = null,
                    credentialManager = credentialManager,
                    onOpenUserGuide = { showUserGuide = true },
                    onOpenDeveloperIdentity = { showDeveloperIdentity = true },
                    onSignInFromGuest = { onError ->
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                appPreferences.setGuestMode(false)
                                isGuestMode = false
                            },
                            onAuthError = onError,
                            scope = scope
                        )
                    },
                    onReturnToWelcome = {
                        appPreferences.setGuestMode(false)
                        isGuestMode = false
                    }
                )
            }
        }

        else -> {
            AuthScreen(
                onAuthSuccess = {},
                onContinueWithoutSignIn = {
                    appPreferences.setGuestMode(true)
                    isGuestMode = true
                },
                onOpenUserGuide = { showUserGuide = true },
                onOpenDeveloperIdentity = { showDeveloperIdentity = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RollCallHome(
    currentUser: FirebaseUser?,
    credentialManager: CredentialManager,
    onOpenUserGuide: () -> Unit,
    onOpenDeveloperIdentity: () -> Unit,
    onSignInFromGuest: ((String) -> Unit) -> Unit,
    onReturnToWelcome: () -> Unit
) {
    val sessionKey = currentUser?.uid ?: LocalAttendanceRepository.OFFLINE_USER_ID
    val isOfflineGuest = currentUser == null

    val viewModel: AttendanceViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val dataSource = if (currentUser != null) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    AttendanceRepository(db)
                } else {
                    val dao = LocalAttendanceDatabase.getInstance(app).attendanceDao()
                    LocalAttendanceRepository(dao)
                }
                AttendanceViewModel(dataSource, sessionKey)
            }
        }
    )

    val studentsState by viewModel.studentsState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val headerState by viewModel.headerState.collectAsStateWithLifecycle()
    val teacherOutputText by viewModel.teacherOutputText.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.attendanceFilter.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(HomeTab.ATTENDANCE) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showStudentSheet by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (currentTab != HomeTab.ATTENDANCE) {
        BackHandler {
            currentTab = HomeTab.ATTENDANCE
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showStudentSheet) {
        StudentEditorSheet(
            editingStudent = editingStudent,
            isBusy = isBusy,
            onDismiss = {
                showStudentSheet = false
                editingStudent = null
            },
            onSave = { roll, name, phone, gmail, present, comment, photoData ->
                viewModel.upsertStudent(
                    studentId = editingStudent?.id,
                    rollNumber = roll,
                    name = name,
                    phone = phone,
                    gmail = gmail,
                    present = present,
                    comment = comment,
                    photoData = photoData,
                    onSuccess = {
                        showStudentSheet = false
                        editingStudent = null
                    }
                )
            }
        )
    }

    val allStudents = (studentsState as? UiState.Success)?.data.orEmpty()
    val presentCount = remember(allStudents) { allStudents.count { it.present } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (allStudents.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Text(
                                        text = "$presentCount/${allStudents.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (isOfflineGuest) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Offline",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onOpenUserGuide,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("user_guide_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = stringResource(R.string.user_guide)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showAccountMenu = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("account_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = "Account menu"
                                )
                            }
                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                if (currentUser != null) {
                                    currentUser.email?.let { email ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = currentUser.displayName ?: "Signed in",
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    Text(
                                                        text = email,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = { showAccountMenu = false }
                                        )
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.offline_mode),
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = "Saved locally on this device",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = { showAccountMenu = false }
                                    )
                                }

                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.user_guide)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        onOpenUserGuide()
                                    },
                                    modifier = Modifier.testTag("menu_user_guide")
                                )

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.developer_identity)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Code,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        onOpenDeveloperIdentity()
                                    },
                                    modifier = Modifier.testTag("menu_developer_identity")
                                )

                                HorizontalDivider()

                                if (currentUser != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sign_out)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showAccountMenu = false
                                            signOut(
                                                credentialManager = credentialManager,
                                                scope = scope
                                            )
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sign_in_with_google)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Login,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showAccountMenu = false
                                            onSignInFromGuest { msg ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(msg)
                                                }
                                            }
                                        },
                                        modifier = Modifier.testTag("menu_sign_in_google")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Back to Sign-In Screen") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showAccountMenu = false
                                            onReturnToWelcome()
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentTab == HomeTab.ATTENDANCE,
                            onClick = { currentTab = HomeTab.ATTENDANCE },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == HomeTab.ATTENDANCE) {
                                        Icons.AutoMirrored.Filled.FactCheck
                                    } else {
                                        Icons.AutoMirrored.Outlined.FactCheck
                                    },
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.tab_attendance)) },
                            modifier = Modifier.testTag("nav_attendance")
                        )
                        NavigationBarItem(
                            selected = currentTab == HomeTab.HISTORY,
                            onClick = { currentTab = HomeTab.HISTORY },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == HomeTab.HISTORY) {
                                        Icons.Filled.History
                                    } else {
                                        Icons.Outlined.History
                                    },
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.tab_history)) },
                            modifier = Modifier.testTag("nav_history")
                        )
                        NavigationBarItem(
                            selected = currentTab == HomeTab.ROSTER,
                            onClick = { currentTab = HomeTab.ROSTER },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == HomeTab.ROSTER) {
                                        Icons.Filled.Groups
                                    } else {
                                        Icons.Outlined.Groups
                                    },
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.tab_roster)) },
                            modifier = Modifier.testTag("nav_roster")
                        )
                    }
                }
            },
            floatingActionButton = {
                if (currentTab != HomeTab.HISTORY) {
                    FloatingActionButton(
                        onClick = {
                            editingStudent = null
                            showStudentSheet = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_student_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = stringResource(R.string.add_student)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.size(12.dp))
                        NavigationRailItem(
                            selected = currentTab == HomeTab.ATTENDANCE,
                            onClick = { currentTab = HomeTab.ATTENDANCE },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == HomeTab.ATTENDANCE) {
                                        Icons.AutoMirrored.Filled.FactCheck
                                    } else {
                                        Icons.AutoMirrored.Outlined.FactCheck
                                    },
                                    contentDescription = null
                                )
                            },
                            label = { Text("Roll Call") },
                            modifier = Modifier.testTag("rail_attendance")
                        )
                        NavigationRailItem(
                            selected = currentTab == HomeTab.HISTORY,
                            onClick = { currentTab = HomeTab.HISTORY },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == HomeTab.HISTORY) {
                                        Icons.Filled.History
                                    } else {
                                        Icons.Outlined.History
                                    },
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.tab_history)) },
                            modifier = Modifier.testTag("rail_history")
                        )
                        NavigationRailItem(
                            selected = currentTab == HomeTab.ROSTER,
                            onClick = { currentTab = HomeTab.ROSTER },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == HomeTab.ROSTER) {
                                        Icons.Filled.Groups
                                    } else {
                                        Icons.Outlined.Groups
                                    },
                                    contentDescription = null
                                )
                            },
                            label = { Text("Roster") },
                            modifier = Modifier.testTag("rail_roster")
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 1040.dp)
                            .fillMaxSize()
                    ) {
                        when (currentTab) {
                            HomeTab.ATTENDANCE -> {
                                AttendanceScreen(
                                    studentsState = studentsState,
                                    headerState = headerState,
                                    teacherOutputText = teacherOutputText,
                                    searchQuery = searchQuery,
                                    activeFilter = activeFilter,
                                    isBusy = isBusy,
                                    onDateChanged = viewModel::updateDateMillis,
                                    onCourseChanged = viewModel::updateCourseName,
                                    onDepartmentChanged = viewModel::updateDepartment,
                                    onBatchChanged = viewModel::updateBatch,
                                    onToggleSetupExpanded = viewModel::toggleSetupExpanded,
                                    onSearchQueryChanged = viewModel::updateSearchQuery,
                                    onFilterChanged = viewModel::updateFilter,
                                    onToggleStudentPresence = viewModel::toggleStudentPresence,
                                    onSetAllPresence = viewModel::setAllPresence,
                                    onLoadSampleBatch = viewModel::loadSampleBatch,
                                    onOpenAddStudent = {
                                        editingStudent = null
                                        showStudentSheet = true
                                    },
                                    onSaveClass = viewModel::saveCurrentAttendanceSession,
                                    onCopiedOutput = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Copied clean teacher output to clipboard")
                                        }
                                    },
                                    onRetry = viewModel::retryStudents
                                )
                            }

                            HomeTab.HISTORY -> {
                                HistoryScreen(
                                    sessionsState = sessionsState,
                                    rosterStudents = allStudents,
                                    isBusy = isBusy,
                                    onDeleteSession = viewModel::deleteAttendanceSession,
                                    onLoadIntoChecklist = { session ->
                                        viewModel.loadSessionIntoChecklist(session) {
                                            currentTab = HomeTab.ATTENDANCE
                                        }
                                    },
                                    onCopiedOutput = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Copied saved teacher output to clipboard")
                                        }
                                    },
                                    onRetry = viewModel::retrySessions
                                )
                            }

                            HomeTab.ROSTER -> {
                                RosterScreen(
                                    studentsState = studentsState,
                                    searchQuery = searchQuery,
                                    isBusy = isBusy,
                                    onSearchQueryChanged = viewModel::updateSearchQuery,
                                    onLoadSampleBatch = viewModel::loadSampleBatch,
                                    onOpenAddStudent = {
                                        editingStudent = null
                                        showStudentSheet = true
                                    },
                                    onEditStudent = { student ->
                                        editingStudent = student
                                        showStudentSheet = true
                                    },
                                    onDeleteStudent = viewModel::deleteStudent,
                                    onRetry = viewModel::retryStudents
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
