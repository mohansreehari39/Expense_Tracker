package et.android.kharcha.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import et.android.kharcha.data.local.MemberEntity
import et.android.kharcha.data.local.ParticipantEntity
import et.android.kharcha.data.local.ProfileEntity
import et.android.kharcha.ui.theme.KharchaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
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
            myName = currentProfile.name,
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
private fun MainScreen(repo: LocalRepository, myName: String, darkMode: Boolean, onSetDarkMode: (Boolean) -> Unit) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val households by repo.observeHouseholds().collectAsState(initial = emptyList())
    val activities by repo.observeActivities().collectAsState(initial = emptyList())
    val pairedServers by repo.observePairedServers().collectAsState(initial = emptyList())
    val profile by repo.observeProfile().collectAsState(initial = null)

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
                        .onSuccess { navController.navigate("household/$it") }
                    "activity" -> runCatching { SyncEngine.joinActivity(repo, api, pairedServerId, invite.id, myName) }
                        .onSuccess { navController.navigate("activity/$it") }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    households = households,
                    activities = activities,
                    onSelectHousehold = { id ->
                        navController.navigate("household/$id")
                        scope.launch { drawerState.close() }
                    },
                    onSelectActivity = { id ->
                        navController.navigate("activity/$id")
                        scope.launch { drawerState.close() }
                    },
                    onOpenHouseholdSettings = { id -> householdSettingsTarget = id },
                    onOpenActivitySettings = { id -> activitySettingsTarget = id },
                    onAddHousehold = { showCreateHousehold = true },
                    onAddActivity = { showCreateTrip = true },
                    onJoinViaQr = { joinScanLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)) },
                    onConnectToServer = { pairScanLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)) },
                    pairedServers = pairedServers,
                    syncingNow = syncingNow,
                    onForceSync = {
                        scope.launch {
                            syncingNow = true
                            val ok = runCatching { SyncEngine.syncAll(context, repo) }.isSuccess
                            syncingNow = false
                            snackbarHostState.showSnackbar(if (ok) "Synced" else "Sync failed — will retry automatically")
                        }
                    },
                    onRemoveServer = { serverId ->
                        scope.launch {
                            repo.forgetPairedServer(serverId)
                            snackbarHostState.showSnackbar("Server removed")
                        }
                    },
                    darkMode = darkMode,
                    onSetDarkMode = onSetDarkMode,
                    onOpenProfile = { showProfile = true },
                    availableUpdate = availableUpdate,
                    checkingUpdate = checkingUpdate,
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
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kharcha") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
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
                            onOpenHousehold = { navController.navigate("household/$it") },
                            onOpenActivity = { navController.navigate("activity/$it") },
                        )
                    }
                    composable(ROUTE_HOUSEHOLD) { entry ->
                        val householdId = entry.arguments?.getString("householdId") ?: return@composable
                        HouseholdScreen(repo = repo, householdId = householdId, myName = myName, household = households.find { it.id == householdId })
                    }
                    composable(ROUTE_ACTIVITY) { entry ->
                        val activityId = entry.arguments?.getString("activityId") ?: return@composable
                        ActivityScreen(repo = repo, activityId = activityId, myName = myName, activity = activities.find { it.id == activityId })
                    }
                }
            }
        }
    }

    if (showCreateHousehold) {
        CreateHouseholdDialog(
            onDismiss = { showCreateHousehold = false },
            onCreate = { name ->
                scope.launch {
                    val created = repo.createHousehold(name, myName)
                    showCreateHousehold = false
                    navController.navigate("household/${created.id}")
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
                    navController.navigate("activity/${created.id}")
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
        profile?.let { ProfileDialog(profile = it, onDismiss = { showProfile = false }) }
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

@Composable
private fun DrawerContent(
    households: List<HouseholdEntity>,
    activities: List<ActivityEntity>,
    onSelectHousehold: (String) -> Unit,
    onSelectActivity: (String) -> Unit,
    onOpenHouseholdSettings: (String) -> Unit,
    onOpenActivitySettings: (String) -> Unit,
    onAddHousehold: () -> Unit,
    onAddActivity: () -> Unit,
    onJoinViaQr: () -> Unit,
    onConnectToServer: () -> Unit,
    pairedServers: List<et.android.kharcha.data.local.PairedServerEntity>,
    syncingNow: Boolean,
    onForceSync: () -> Unit,
    onRemoveServer: (String) -> Unit,
    darkMode: Boolean,
    onSetDarkMode: (Boolean) -> Unit,
    onOpenProfile: () -> Unit,
    availableUpdate: UpdateInfo?,
    checkingUpdate: Boolean,
    onCheckForUpdates: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            item { DrawerSectionHeader("HOUSEHOLD", onAdd = onAddHousehold) }
            items(households) { household ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NavigationDrawerItem(
                        label = { Text(household.name) },
                        selected = false,
                        onClick = { onSelectHousehold(household.id) },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    IconButton(onClick = { onOpenHouseholdSettings(household.id) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Household Settings", modifier = Modifier.size(18.dp))
                    }
                }
            }
            item { DrawerSectionHeader("ACTIVITIES", onAdd = onAddActivity) }
            items(activities) { activity ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NavigationDrawerItem(
                        label = { Text(activity.name) },
                        selected = false,
                        onClick = { onSelectActivity(activity.id) },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    IconButton(onClick = { onOpenActivitySettings(activity.id) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Activity Settings", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text("Join Household/Activity") },
            icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
            selected = false,
            onClick = onJoinViaQr,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        NavigationDrawerItem(
            label = { Text("Connect to Server") },
            icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
            selected = false,
            onClick = onConnectToServer,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (pairedServers.isNotEmpty()) {
            val now = System.currentTimeMillis()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                pairedServers.forEach { server ->
                    val connected = server.lastSyncSuccessAt != null && now - server.lastSyncSuccessAt < CONNECTED_STALE_AFTER_MS
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.size(8.dp).background(
                                    if (connected) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                ),
                            )
                            Text(
                                "${server.label}: ${if (connected) "Connected" else "Offline"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onForceSync, enabled = !syncingNow, modifier = Modifier.size(28.dp)) {
                                if (syncingNow) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Sync now", modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = { onRemoveServer(server.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove server", modifier = Modifier.size(16.dp))
                            }
                        }
                        // Last failure reason, kept even after a later success
                        // clears lastSyncSuccessAt staleness — only shown while
                        // actually reading as Offline, so a healthy connection
                        // doesn't show yesterday's transient blip forever.
                        if (!connected && server.lastSyncError != null) {
                            Text(
                                server.lastSyncError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        NavigationDrawerItem(
            label = { Text(if (availableUpdate != null) "Update available (v${availableUpdate.version})" else "Check for Updates") },
            icon = {
                if (checkingUpdate) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                }
            },
            selected = false,
            onClick = onCheckForUpdates,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("My Profile") },
            icon = { Icon(Icons.Filled.Person, contentDescription = null) },
            selected = false,
            onClick = onOpenProfile,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (darkMode) "Dark Mode" else "Light Mode", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = darkMode, onCheckedChange = onSetDarkMode)
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onAdd, modifier = Modifier.size(24.dp)) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}
