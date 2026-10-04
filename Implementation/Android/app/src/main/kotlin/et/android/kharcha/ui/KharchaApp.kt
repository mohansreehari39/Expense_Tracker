package et.android.kharcha.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import et.android.kharcha.BuildConfig
import et.android.kharcha.data.ApiClient
import et.android.kharcha.data.ConnectionStore
import et.android.kharcha.data.LocalRepository
import et.android.kharcha.data.SyncEngine
import et.android.kharcha.data.UpdateChecker
import et.android.kharcha.data.UpdateInfo
import et.android.kharcha.data.UpdateInstaller
import et.android.kharcha.data.decodeJoinInvite
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.PairedServerEntity
import et.android.kharcha.data.local.ProfileEntity
import et.android.kharcha.ui.theme.KharchaTheme
import et.android.kharcha.ui.theme.kharcha
import et.core.model.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
private const val ROUTE_ME = "me"
private const val ROUTE_HOUSEHOLD = "household/{householdId}"
private const val ROUTE_ACTIVITY = "activity/{activityId}"
private const val SYNC_INTERVAL_MS = 15_000L

@Composable
fun KharchaApp() {
    val context = LocalContext.current
    val repo = remember { LocalRepository(context) }
    val connectionStore = remember { ConnectionStore(context) }
    var profile by remember { mutableStateOf<ProfileEntity?>(null) }
    var darkModeOverride by remember { mutableStateOf<Boolean?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        profile = repo.currentProfile()
        darkModeOverride = connectionStore.currentDarkMode()
        loaded = true
    }

    KharchaTheme(darkTheme = darkModeOverride ?: systemDark) {
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@KharchaTheme
        }

        val currentProfile = profile
        if (currentProfile == null) {
            SignupScreen(onSignedUp = { name, age, gender, phone, email ->
                scope.launch {
                    repo.saveProfile(name, age, gender, phone, email)
                    profile = repo.currentProfile()
                }
            })
            return@KharchaTheme
        }

        MainScreen(
            repo = repo,
            initialName = currentProfile.name,
            darkMode = darkModeOverride ?: systemDark,
            onSetDarkMode = { enabled ->
                scope.launch { connectionStore.setDarkMode(enabled) }
                darkModeOverride = enabled
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(repo: LocalRepository, initialName: String, darkMode: Boolean, onSetDarkMode: (Boolean) -> Unit) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val households by repo.observeHouseholds().collectAsState(initial = emptyList())
    val activities by repo.observeActivities().collectAsState(initial = emptyList())
    val pairedServers by repo.observePairedServers().collectAsState(initial = emptyList())
    val profile by repo.observeProfile().collectAsState(initial = null)
    // Live, so Me → Edit profile takes effect everywhere at once.
    val myName = profile?.name ?: initialName

    var showCreateHousehold by remember { mutableStateOf(false) }
    var showCreateTrip by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var connectingMessage by remember { mutableStateOf<String?>(null) }
    var syncingNow by remember { mutableStateOf(false) }
    var householdSettingsTarget by remember { mutableStateOf<String?>(null) }
    var activitySettingsTarget by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }

    // Silent on-launch check — only interrupts the user if something's
    // actually available. Failures (offline, GitHub unreachable) are
    // swallowed the same as "no update", not surfaced as an error.
    LaunchedEffect(Unit) {
        availableUpdate = runCatching { UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME) }.getOrNull()
    }

    // Best-effort background sync for anything linked to a paired server —
    // a household/activity that's never been joined/paired is untouched by
    // this and works the same with or without it running.
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { SyncEngine.syncAll(context, repo) }
            delay(SYNC_INTERVAL_MS)
        }
    }

    val joinScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val invite = decodeJoinInvite(text)
        if (invite == null) {
            scope.launch { snackbarHostState.showSnackbar("That QR code isn't a valid Kharcha invite.") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            connectingMessage = "Joining ${invite.name.ifBlank { "household/activity" }}…"
            try {
                val pairedServerId = "${invite.host}:${invite.port}"
                val baseUrl = "http://${invite.host}:${invite.port}"
                val deviceId = repo.currentProfile()?.deviceId
                val existingServer = repo.pairedServer(pairedServerId)
                var pairingKey = existingServer?.pairingKey
                if (existingServer == null) {
                    // invite.name here is the household/activity's name, not the
                    // server's — a household/activity QR carries no separate
                    // server-level display name, so fall back to a generic
                    // label rather than mislabeling the server as e.g. "Sharma
                    // Family". "Connect to Server" pairing (below) still uses
                    // the server's own real name. Also mints a real pairingKey
                    // via the trusted pair endpoint — a household/activity join
                    // doubles as device pairing since scanning it required
                    // physical access to the Windows machine's own screen.
                    val paired = deviceId?.let { runCatching { ApiClient(baseUrl).pairDevice(it, myName, invite.pairingSecret) }.getOrNull() }
                    if (paired != null) {
                        repo.pairServer(pairedServerId, "Kharcha Server", invite.host, invite.port, paired.pairingKey)
                        pairingKey = paired.pairingKey
                    }
                }
                val api = ApiClient(baseUrl, deviceId, pairingKey)
                val result = when (invite.kind) {
                    "household" -> runCatching { SyncEngine.joinHousehold(repo, api, pairedServerId, invite.id, myName) }
                        .onSuccess { navController.navigate("household/$it") { popUpTo(ROUTE_HOME); launchSingleTop = true } }
                    "activity" -> runCatching { SyncEngine.joinActivity(repo, api, pairedServerId, invite.id, myName) }
                        .onSuccess { navController.navigate("activity/$it") { popUpTo(ROUTE_HOME); launchSingleTop = true } }
                    else -> Result.failure(IllegalArgumentException("Unexpected QR kind: ${invite.kind}"))
                }
                result.onFailure { error ->
                    snackbarHostState.showSnackbar("Couldn't join: ${error.message ?: error::class.simpleName}")
                }
            } finally {
                connectingMessage = null
            }
        }
    }

    val pairScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val invite = decodeJoinInvite(text)
        if (invite == null) {
            scope.launch { snackbarHostState.showSnackbar("That QR code isn't a valid Kharcha invite.") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val label = invite.name.ifBlank { "Kharcha Server" }
            connectingMessage = "Connecting to $label…"
            try {
                val pairedServerId = "${invite.host}:${invite.port}"
                val deviceId = repo.currentProfile()?.deviceId
                val paired = deviceId?.let {
                    runCatching { ApiClient("http://${invite.host}:${invite.port}").pairDevice(it, myName, invite.pairingSecret) }.getOrNull()
                }
                if (paired != null) {
                    repo.pairServer(pairedServerId, label, invite.host, invite.port, paired.pairingKey)
                    repo.markPairedServerSyncSuccess(pairedServerId, System.currentTimeMillis())
                }
                snackbarHostState.showSnackbar(
                    if (paired != null) "Connected to $label" else "Couldn't reach $label — check the network and try again.",
                )
            } finally {
                connectingMessage = null
            }
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentHouseholdId = if (route == ROUTE_HOUSEHOLD) backStack?.arguments?.getString("householdId") else null
    val currentActivityId = if (route == ROUTE_ACTIVITY) backStack?.arguments?.getString("activityId") else null

    // The household/activity Home shows: the one open now, else the last one opened.
    var lastSpace by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(currentHouseholdId, currentActivityId) {
        currentHouseholdId?.let { lastSpace = "household/$it" }
        currentActivityId?.let { lastSpace = "activity/$it" }
    }
    val firstSpace = households.firstOrNull()?.let { "household/${it.id}" } ?: activities.firstOrNull()?.let { "activity/${it.id}" }
    var showSpaces by remember { mutableStateOf(false) }
    // Id of the household/activity whose screen should open "Add expense" (set by the + button).
    var pendingAddFor by remember { mutableStateOf<String?>(null) }

    fun openSpace(path: String) {
        navController.navigate(path) {
            popUpTo(ROUTE_HOME)
            launchSingleTop = true
        }
    }

    fun onAddPressed() {
        when {
            currentHouseholdId != null -> pendingAddFor = currentHouseholdId
            currentActivityId != null -> pendingAddFor = currentActivityId
            else -> {
                val target = lastSpace ?: firstSpace
                if (target == null) {
                    showSpaces = true
                    scope.launch { snackbarHostState.showSnackbar("Create or join a household or activity first.") }
                } else {
                    openSpace(target)
                    pendingAddFor = target.substringAfter('/')
                }
            }
        }
    }

    val chrome = SpaceChrome(
        myName = myName,
        onSwitchSpace = { showSpaces = true },
        onOpenMe = { navController.navigate(ROUTE_ME) { launchSingleTop = true } },
    )

    Scaffold(
        bottomBar = {
            KharchaBottomBar(
                onHomeSpace = currentHouseholdId != null || currentActivityId != null,
                onSummary = route == ROUTE_HOME,
                onMe = route == ROUTE_ME,
                onHome = {
                    val target = lastSpace ?: firstSpace
                    if (target != null) openSpace(target) else showSpaces = true
                },
                onSpaces = { showSpaces = true },
                onAdd = ::onAddPressed,
                onSummaryClick = { navController.navigate(ROUTE_HOME) { popUpTo(ROUTE_HOME) { inclusive = true }; launchSingleTop = true } },
                onMeClick = { navController.navigate(ROUTE_ME) { launchSingleTop = true } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = ROUTE_HOME) {
                composable(ROUTE_HOME) {
                    SummaryScreen(
                        repo = repo,
                        households = households,
                        activities = activities,
                        myName = myName,
                        onOpenHousehold = { openSpace("household/$it") },
                        onOpenActivity = { openSpace("activity/$it") },
                        onOpenMe = chrome.onOpenMe,
                    )
                }
                composable(ROUTE_HOUSEHOLD) { entry ->
                    val householdId = entry.arguments?.getString("householdId") ?: return@composable
                    HouseholdScreen(
                        repo = repo,
                        householdId = householdId,
                        myName = myName,
                        household = households.find { it.id == householdId },
                        chrome = chrome.copy(onSettings = { householdSettingsTarget = householdId }),
                        addRequested = pendingAddFor == householdId,
                        onAddHandled = { pendingAddFor = null },
                    )
                }
                composable(ROUTE_ACTIVITY) { entry ->
                    val activityId = entry.arguments?.getString("activityId") ?: return@composable
                    ActivityScreen(
                        repo = repo,
                        activityId = activityId,
                        myName = myName,
                        activity = activities.find { it.id == activityId },
                        chrome = chrome.copy(onSettings = { activitySettingsTarget = activityId }),
                        addRequested = pendingAddFor == activityId,
                        onAddHandled = { pendingAddFor = null },
                    )
                }
                composable(ROUTE_ME) {
                    MeScreen(
                        profile = profile,
                        pairedServers = pairedServers,
                        syncingNow = syncingNow,
                        darkMode = darkMode,
                        availableUpdate = availableUpdate,
                        checkingUpdate = checkingUpdate,
                        onOpenProfile = { showProfile = true },
                        onForceSync = {
                            scope.launch {
                                syncingNow = true
                                val ok = runCatching { SyncEngine.syncAll(context, repo) }.isSuccess
                                syncingNow = false
                                snackbarHostState.showSnackbar(if (ok) "Synced" else "Sync failed. It will retry automatically.")
                            }
                        },
                        onRemoveServer = { serverId ->
                            scope.launch {
                                repo.forgetPairedServer(serverId)
                                snackbarHostState.showSnackbar("Server removed")
                            }
                        },
                        onJoinViaQr = { joinScanLauncher.launch(qrScanOptions()) },
                        onConnectToServer = { pairScanLauncher.launch(qrScanOptions()) },
                        onSetDarkMode = onSetDarkMode,
                        onCheckForUpdates = {
                            scope.launch {
                                checkingUpdate = true
                                val found = runCatching { UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME) }.getOrNull()
                                checkingUpdate = false
                                if (found != null) {
                                    availableUpdate = found
                                } else {
                                    snackbarHostState.showSnackbar("You're up to date (v${BuildConfig.VERSION_NAME})")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showSpaces) {
        SpacesSheet(
            households = households,
            activities = activities,
            currentId = currentHouseholdId ?: currentActivityId,
            onDismiss = { showSpaces = false },
            onOpenHousehold = { id -> showSpaces = false; openSpace("household/$id") },
            onOpenActivity = { id -> showSpaces = false; openSpace("activity/$id") },
            onHouseholdSettings = { id -> householdSettingsTarget = id },
            onActivitySettings = { id -> activitySettingsTarget = id },
            onAddHousehold = { showSpaces = false; showCreateHousehold = true },
            onAddActivity = { showSpaces = false; showCreateTrip = true },
            onJoinViaQr = { showSpaces = false; joinScanLauncher.launch(qrScanOptions()) },
            onConnectToServer = { showSpaces = false; pairScanLauncher.launch(qrScanOptions()) },
        )
    }

    if (showCreateHousehold) {
        CreateHouseholdDialog(
            onDismiss = { showCreateHousehold = false },
            onCreate = { name ->
                scope.launch {
                    val created = repo.createHousehold(name, myName)
                    showCreateHousehold = false
                    openSpace("household/${created.id}")
                }
            },
        )
    }

    if (showCreateTrip) {
        CreateTripDialog(
            onDismiss = { showCreateTrip = false },
            onSubmit = { name, budgetMinorUnits, currency, participantNames ->
                scope.launch {
                    val created = repo.createActivity(name, budgetMinorUnits, currency, myName, participantNames)
                    showCreateTrip = false
                    openSpace("activity/${created.id}")
                }
            },
        )
    }

    householdSettingsTarget?.let { id ->
        val household = households.find { it.id == id }
        if (household != null) {
            val members by repo.observeMembers(id).collectAsState(initial = emptyList())
            val dependents by repo.observeDependents(id).collectAsState(initial = emptyList())
            HouseholdSettingsDialog(
                currentName = household.name,
                currentBudgetMinorUnits = household.defaultBudgetMinorUnits,
                currentCurrency = household.currency,
                currentSettlementEnabled = household.settlementEnabled,
                members = members,
                dependents = dependents,
                onDismiss = { householdSettingsTarget = null },
                onSubmit = { name, budgetMinorUnits, budgetCurrency, settlementEnabled ->
                    scope.launch {
                        repo.updateHouseholdConfig(id, name, budgetMinorUnits, budgetCurrency, settlementEnabled)
                        householdSettingsTarget = null
                    }
                },
                onRemoveMember = { memberId ->
                    scope.launch {
                        removeMemberEverywhere(context, repo, household, memberId)
                    }
                },
                onAddDependent = { name, category -> repo.addDependent(id, name, category) },
                onRemoveDependent = { dependentId ->
                    scope.launch {
                        removeDependentEverywhere(context, repo, household, dependentId)
                    }
                },
            )
        }
    }

    activitySettingsTarget?.let { id ->
        val activity = activities.find { it.id == id }
        if (activity != null) {
            val participants by repo.observeParticipants(id).collectAsState(initial = emptyList())
            ActivitySettingsDialog(
                currentName = activity.name,
                currentBudgetMinorUnits = activity.budgetMinorUnits,
                currentCurrency = activity.currency,
                participants = participants,
                onDismiss = { activitySettingsTarget = null },
                onSubmit = { name, budgetMinorUnits, budgetCurrency ->
                    scope.launch {
                        repo.updateActivityConfig(id, name, budgetMinorUnits, budgetCurrency)
                        activitySettingsTarget = null
                    }
                },
                onRemoveParticipant = { participantId ->
                    scope.launch {
                        removeParticipantEverywhere(context, repo, activity, participantId)
                    }
                },
            )
        }
    }

    connectingMessage?.let { message ->
        ConnectingOverlay(message)
    }

    if (showProfile) {
        profile?.let { current ->
            EditProfileSheet(
                profile = current,
                onDismiss = { showProfile = false },
                onSave = { name, age, gender, phone, email ->
                    scope.launch {
                        repo.updateProfile(name, age, gender, phone, email)
                        showProfile = false
                        snackbarHostState.showSnackbar("Profile saved")
                    }
                },
            )
        }
    }

    availableUpdate?.let { update ->
        UpdateAvailableDialog(
            update = update,
            onDismiss = { availableUpdate = null },
            onInstall = {
                if (UpdateInstaller.canInstallPackages(context)) {
                    UpdateInstaller.downloadAndInstall(context, update)
                    scope.launch { snackbarHostState.showSnackbar("Downloading v${update.version}…") }
                    availableUpdate = null
                } else {
                    UpdateInstaller.requestInstallPermission(context)
                }
            },
        )
    }
}

/** Best-effort remote archive (if this household is linked and the member has already synced) followed by an unconditional local removal — mirrors Windows' immediate "✕ Remove" behavior rather than queuing an offline pending-delete. */
private suspend fun removeMemberEverywhere(context: android.content.Context, repo: LocalRepository, household: HouseholdEntity, memberId: String) {
    val member = repo.members(household.id).find { it.id == memberId }
    val remoteHouseholdId = household.remoteId
    val remoteMemberId = member?.remoteId
    val pairedServerId = household.pairedServerId
    if (remoteHouseholdId != null && remoteMemberId != null && pairedServerId != null) {
        val server = repo.pairedServer(pairedServerId)
        if (server != null) {
            val api = runCatching { SyncEngine.resolveApiClient(context, server, repo) }.getOrNull()
            api?.let { runCatching { it.archiveMember(remoteHouseholdId, remoteMemberId) } }
        }
    }
    repo.hardDeleteMember(memberId)
}

private suspend fun removeDependentEverywhere(context: android.content.Context, repo: LocalRepository, household: HouseholdEntity, dependentId: String) {
    val dependent = repo.dependents(household.id).find { it.id == dependentId }
    val remoteHouseholdId = household.remoteId
    val remoteDependentId = dependent?.remoteId
    val pairedServerId = household.pairedServerId
    if (remoteHouseholdId != null && remoteDependentId != null && pairedServerId != null) {
        val server = repo.pairedServer(pairedServerId)
        if (server != null) {
            val api = runCatching { SyncEngine.resolveApiClient(context, server, repo) }.getOrNull()
            api?.let { runCatching { it.archiveHouseholdDependent(remoteHouseholdId, remoteDependentId) } }
        }
    }
    repo.hardDeleteDependent(dependentId)
}

private suspend fun removeParticipantEverywhere(context: android.content.Context, repo: LocalRepository, activity: ActivityEntity, participantId: String) {
    val participant = repo.participants(activity.id).find { it.id == participantId }
    val remoteActivityId = activity.remoteId
    val remoteParticipantId = participant?.remoteId
    val pairedServerId = activity.pairedServerId
    if (remoteActivityId != null && remoteParticipantId != null && pairedServerId != null) {
        val server = repo.pairedServer(pairedServerId)
        if (server != null) {
            val api = runCatching { SyncEngine.resolveApiClient(context, server, repo) }.getOrNull()
            api?.let { runCatching { it.archiveTripParticipant(remoteActivityId, remoteParticipantId) } }
        }
    }
    repo.hardDeleteParticipant(participantId)
}

/** Blocks interaction with the rest of the screen while a scan result is being acted on, so the user can't navigate away mid-join/pair without knowing whether it succeeded. */
@Composable
private fun ConnectingOverlay(message: String) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Card {
            Row(
                Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun UpdateAvailableDialog(update: UpdateInfo, onDismiss: () -> Unit, onInstall: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = {
            Column {
                Text("Kharcha v${update.version} is available.")
                if (update.notes.isNotBlank()) {
                    Text(
                        update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onInstall) { Text("Update") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

/** A paired server counts as "Connected" if it's been reached within the last two sync intervals; older than that reads as "Offline" rather than claiming a live connection that may no longer hold. */
private const val CONNECTED_STALE_AFTER_MS = SYNC_INTERVAL_MS * 2

/** What a household/activity screen needs from the shell: the switcher, its settings, and Me. */
data class SpaceChrome(
    val myName: String,
    val onSwitchSpace: () -> Unit,
    val onOpenMe: () -> Unit,
    val onSettings: () -> Unit = {},
)

private fun qrScanOptions() = ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)

/** Home · Spaces · + · Summary · Me. The center button adds an expense to the open household/activity. */
@Composable
private fun KharchaBottomBar(
    onHomeSpace: Boolean,
    onSummary: Boolean,
    onMe: Boolean,
    onHome: () -> Unit,
    onSpaces: () -> Unit,
    onAdd: () -> Unit,
    onSummaryClick: () -> Unit,
    onMeClick: () -> Unit,
) {
    val itemColors = NavigationBarItemDefaults.colors(
        indicatorColor = kharcha.tonal,
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedIconColor = kharcha.muted,
        unselectedTextColor = kharcha.muted,
    )
    Column {
        HorizontalDivider(color = kharcha.line)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
            NavigationBarItem(selected = onHomeSpace, onClick = onHome, icon = { Icon(Icons.Outlined.Home, null) }, label = { Text("Home") }, colors = itemColors)
            NavigationBarItem(selected = false, onClick = onSpaces, icon = { Icon(Icons.Outlined.SwapHoriz, null) }, label = { Text("Spaces") }, colors = itemColors)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = onAdd,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add expense", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            NavigationBarItem(selected = onSummary, onClick = onSummaryClick, icon = { Icon(Icons.Outlined.PieChart, null) }, label = { Text("Summary") }, colors = itemColors)
            NavigationBarItem(selected = onMe, onClick = onMeClick, icon = { Icon(Icons.Outlined.Person, null) }, label = { Text("Me") }, colors = itemColors)
        }
    }
}

/** Tile colors for activities, so each one is recognisable in lists. Households use the theme's green. */
private val ActivityTileColors = listOf(Color(0xFF1D5F91), Color(0xFF9A5A1A), Color(0xFF7A3E73), Color(0xFF3D5A80), Color(0xFF8A3B3B))

fun activityTileColor(index: Int): Color = ActivityTileColors[index.mod(ActivityTileColors.size)]

/** Replaces the old full-screen drawer: every household and activity, plus join/connect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpacesSheet(
    households: List<HouseholdEntity>,
    activities: List<ActivityEntity>,
    currentId: String?,
    onDismiss: () -> Unit,
    onOpenHousehold: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
    onHouseholdSettings: (String) -> Unit,
    onActivitySettings: (String) -> Unit,
    onAddHousehold: () -> Unit,
    onAddActivity: () -> Unit,
    onJoinViaQr: () -> Unit,
    onConnectToServer: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SheetSectionHeader("Households", addLabel = "New household", onAdd = onAddHousehold)
            if (households.isEmpty()) EmptyHint("No households yet. Create one, or join with a QR code.")
            households.forEach { household ->
                SpaceRow(
                    icon = Icons.Outlined.Home,
                    tile = MaterialTheme.colorScheme.primary,
                    name = household.name,
                    detail = household.defaultBudgetMinorUnits?.let { "Budget ${formatMoney(it, household.currency)} a month" } ?: "No monthly budget set",
                    selected = household.id == currentId,
                    onOpen = { onOpenHousehold(household.id) },
                    onSettings = { onHouseholdSettings(household.id) },
                )
            }
            Box(Modifier.size(4.dp))
            SheetSectionHeader("Activities", addLabel = "New activity", onAdd = onAddActivity)
            if (activities.isEmpty()) EmptyHint("No activities yet. Trips and events go here.")
            activities.forEachIndexed { index, activity ->
                SpaceRow(
                    icon = Icons.Outlined.Luggage,
                    tile = activityTileColor(index),
                    name = activity.name,
                    detail = "Budget ${formatMoney(activity.budgetMinorUnits, activity.currency)}",
                    selected = activity.id == currentId,
                    onOpen = { onOpenActivity(activity.id) },
                    onSettings = { onActivitySettings(activity.id) },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onJoinViaQr, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, kharcha.line)) {
                    Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                    Text("Join with QR", maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onConnectToServer, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, kharcha.line)) {
                    Icon(Icons.Outlined.Wifi, null, modifier = Modifier.size(18.dp))
                    Text("Connect", maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetSectionHeader(title: String, addLabel: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title)
        Surface(onClick = onAdd, shape = CircleShape, color = kharcha.tonal, modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, contentDescription = addLabel, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SpaceRow(icon: ImageVector, tile: Color, name: String, detail: String, selected: Boolean, onOpen: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) kharcha.tonal else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, background = tile, tint = Color.White, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(detail, style = MaterialTheme.typography.bodySmall.tabular(), color = kharcha.muted)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings for $name", tint = kharcha.muted) }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = kharcha.muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
}

/** Profile, server connection, join/connect, updates and dark mode — everything that used to sit in the drawer's lower half. */
@Composable
private fun MeScreen(
    profile: ProfileEntity?,
    pairedServers: List<PairedServerEntity>,
    syncingNow: Boolean,
    darkMode: Boolean,
    availableUpdate: UpdateInfo?,
    checkingUpdate: Boolean,
    onOpenProfile: () -> Unit,
    onForceSync: () -> Unit,
    onRemoveServer: (String) -> Unit,
    onJoinViaQr: () -> Unit,
    onConnectToServer: () -> Unit,
    onSetDarkMode: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitleBar("Me", myName = profile?.name.orEmpty(), onOpenMe = null)

        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onOpenProfile).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(profile?.name.orEmpty(), onClick = onOpenProfile, size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text(profile?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(profile?.email, profile?.phone).joinToString(" · ").ifBlank { "Edit your profile" },
                    style = MaterialTheme.typography.bodySmall,
                    color = kharcha.muted,
                )
            }
        }

        SectionLabel("Server")
        if (pairedServers.isEmpty()) {
            SectionCard {
                Text("Not connected to a server. Everything stays on this phone until you connect.", style = MaterialTheme.typography.bodyMedium, color = kharcha.muted)
                TonalPill("Connect to a server", onClick = onConnectToServer)
            }
        }
        val now = System.currentTimeMillis()
        pairedServers.forEach { server ->
            val connected = server.lastSyncSuccessAt != null && now - server.lastSyncSuccessAt < CONNECTED_STALE_AFTER_MS
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Outlined.Wifi)
                    Column(Modifier.weight(1f)) {
                        Text(server.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            if (connected) "Connected" else "Offline",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (connected) kharcha.ok else kharcha.muted,
                        )
                    }
                }
                // Last failure reason, only while reading as Offline — a healthy connection doesn't keep showing yesterday's blip.
                if (!connected && server.lastSyncError != null) {
                    Text(server.lastSyncError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (syncingNow) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Syncing…", style = MaterialTheme.typography.bodySmall, color = kharcha.muted)
                    } else {
                        TonalPill("Sync now", onClick = onForceSync)
                    }
                    TonalPill("Remove", onClick = { onRemoveServer(server.id) })
                }
            }
        }

        SectionLabel("Join and connect")
        SectionCard {
            MeRow(Icons.Outlined.QrCodeScanner, "Join with QR", "Scan a household or activity invite", onJoinViaQr)
            MeRow(Icons.Outlined.Wifi, "Connect to a server", "Scan the QR shown on the Windows app", onConnectToServer)
        }

        SectionLabel("App")
        SectionCard {
            MeRow(
                Icons.Outlined.SystemUpdate,
                if (availableUpdate != null) "Update available" else "Check for updates",
                if (availableUpdate != null) "v${availableUpdate.version} is ready to install" else "You're on v${BuildConfig.VERSION_NAME}",
                onCheckForUpdates,
                trailing = { if (checkingUpdate) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
            )
            MeRow(
                Icons.Outlined.DarkMode,
                "Dark mode",
                null,
                onClick = { onSetDarkMode(!darkMode) },
                trailing = { Switch(checked = darkMode, onCheckedChange = onSetDarkMode) },
            )
            MeRow(Icons.Outlined.AccountCircle, "Edit profile", "Name, age, gender, phone and email", onOpenProfile)
        }
    }
}

@Composable
private fun MeRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = kharcha.muted)
        }
        trailing()
    }
}
