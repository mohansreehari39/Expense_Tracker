package et.android.kharcha.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import et.android.kharcha.data.ApiClient
import et.android.kharcha.data.ConnectionStore
import et.android.kharcha.data.CreateTripRequest
import et.android.kharcha.data.HouseholdDto
import et.android.kharcha.data.TripDto
import et.android.kharcha.data.decodeJoinInvite
import et.android.kharcha.data.discoverKharchaServer
import et.android.kharcha.data.ensureMyIdentity
import et.android.kharcha.ui.theme.KharchaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ROUTE_SUMMARY = "summary"
private const val ROUTE_HOUSEHOLD = "household/{householdId}"
private const val ROUTE_ACTIVITY = "activity/{tripId}"

@Composable
fun KharchaApp() {
    val context = LocalContext.current
    val store = remember { ConnectionStore(context) }
    var baseUrl by remember { mutableStateOf<String?>(null) }
    var myName by remember { mutableStateOf<String?>(null) }
    var darkModeOverride by remember { mutableStateOf<Boolean?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var pendingDeepLink by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        baseUrl = store.currentServerBaseUrl()
        myName = store.currentMyName()
        darkModeOverride = store.darkMode.first()
        loaded = true
    }

    KharchaTheme(darkTheme = darkModeOverride ?: systemDark) {
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@KharchaTheme
        }

        val currentMyName = myName
        if (currentMyName == null) {
            SignupScreen(onSignedUp = { name ->
                scope.launch { store.setMyName(name) }
                myName = name
            })
            return@KharchaTheme
        }

        val currentBaseUrl = baseUrl
        if (currentBaseUrl == null) {
            ConnectScreen(onConnected = { url, kind, id ->
                scope.launch { store.setServerBaseUrl(url) }
                if (kind != null && id != null) pendingDeepLink = kind to id
                baseUrl = url
            })
        } else {
            val api = remember(currentBaseUrl) { ApiClient(currentBaseUrl) }
            ConnectedApp(
                api = api,
                store = store,
                myName = currentMyName,
                darkMode = darkModeOverride ?: systemDark,
                onSetDarkMode = { enabled ->
                    scope.launch { store.setDarkMode(enabled) }
                    darkModeOverride = enabled
                },
                initialDeepLink = pendingDeepLink,
                onDeepLinkConsumed = { pendingDeepLink = null },
                onChangeServer = {
                    scope.launch { store.clearServerBaseUrl() }
                    baseUrl = null
                },
                onJoinNew = { url, kind, id ->
                    scope.launch { store.setServerBaseUrl(url) }
                    pendingDeepLink = kind to id
                    baseUrl = url
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedApp(
    api: ApiClient,
    store: ConnectionStore,
    myName: String,
    darkMode: Boolean,
    onSetDarkMode: (Boolean) -> Unit,
    initialDeepLink: Pair<String, String>?,
    onDeepLinkConsumed: () -> Unit,
    onChangeServer: () -> Unit,
    onJoinNew: (baseUrl: String, kind: String, id: String) -> Unit,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var households by remember { mutableStateOf<List<HouseholdDto>>(emptyList()) }
    var trips by remember { mutableStateOf<List<TripDto>>(emptyList()) }
    var refreshSignal by remember { mutableStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var everLoaded by remember { mutableStateOf(false) }
    var showCreateHousehold by remember { mutableStateOf(false) }
    var showCreateTrip by remember { mutableStateOf(false) }

    suspend fun reloadLists() {
        try {
            households = api.households()
            trips = api.trips()
            ensureMyIdentity(api, store, myName, households, trips)
            loadError = null
            everLoaded = true
        } catch (e: Exception) {
            // Keep whatever was last loaded; only block the UI with an
            // error screen if nothing has ever loaded (see below) — a
            // dropped wifi connection during a routine refresh shouldn't
            // kick the user out of what they were looking at.
            loadError = e.message ?: e::class.simpleName ?: "Couldn't reach the server"
        }
    }

    LaunchedEffect(api, refreshSignal) { reloadLists() }

    if (!everLoaded && loadError != null) {
        ConnectionErrorScreen(message = loadError!!, onRetry = { refreshSignal++ }, onChangeServer = onChangeServer)
        return
    }

    LaunchedEffect(initialDeepLink, households, trips) {
        val (kind, id) = initialDeepLink ?: return@LaunchedEffect
        val route = if (kind == "household") "household/$id" else "activity/$id"
        navController.navigate(route)
        onDeepLinkConsumed()
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val invite = decodeJoinInvite(text) ?: return@rememberLauncherForActivityResult
        scope.launch {
            val discovered = discoverKharchaServer(context)
            onJoinNew(discovered?.baseUrl ?: invite.serverBaseUrl, invite.kind, invite.id)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    households = households,
                    trips = trips,
                    onSelectHousehold = { id ->
                        navController.navigate("household/$id")
                        scope.launch { drawerState.close() }
                    },
                    onSelectActivity = { id ->
                        navController.navigate("activity/$id")
                        scope.launch { drawerState.close() }
                    },
                    onAddHousehold = { showCreateHousehold = true },
                    onAddActivity = { showCreateTrip = true },
                    onScanToJoin = { scanLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)) },
                    onChangeServer = onChangeServer,
                    darkMode = darkMode,
                    onSetDarkMode = onSetDarkMode,
                )
            }
        },
    ) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

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
        ) { padding ->
            Box(Modifier.padding(padding)) {
                NavHost(navController = navController, startDestination = ROUTE_SUMMARY) {
                    composable(ROUTE_SUMMARY) {
                        SummaryScreen(
                            api = api,
                            store = store,
                            households = households,
                            trips = trips,
                            onOpenHousehold = { navController.navigate("household/$it") },
                            onOpenActivity = { navController.navigate("activity/$it") },
                        )
                    }
                    composable(ROUTE_HOUSEHOLD) { entry ->
                        val householdId = entry.arguments?.getString("householdId") ?: return@composable
                        HouseholdScreen(api = api, store = store, householdId = householdId, onChanged = { refreshSignal++ })
                    }
                    composable(ROUTE_ACTIVITY) { entry ->
                        val tripId = entry.arguments?.getString("tripId") ?: return@composable
                        ActivityScreen(api = api, store = store, tripId = tripId, onChanged = { refreshSignal++ })
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
                    val created = api.createHousehold(name)
                    showCreateHousehold = false
                    refreshSignal++
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
                    val created = api.createTrip(
                        CreateTripRequest(
                            name = name,
                            startDate = System.currentTimeMillis(),
                            budgetAmountMinorUnits = budgetMinorUnits,
                            currency = currency,
                            participantNames = participantNames,
                        ),
                    )
                    showCreateTrip = false
                    refreshSignal++
                    navController.navigate("activity/${created.id}")
                }
            },
        )
    }
}

@Composable
private fun DrawerContent(
    households: List<HouseholdDto>,
    trips: List<TripDto>,
    onSelectHousehold: (String) -> Unit,
    onSelectActivity: (String) -> Unit,
    onAddHousehold: () -> Unit,
    onAddActivity: () -> Unit,
    onScanToJoin: () -> Unit,
    onChangeServer: () -> Unit,
    darkMode: Boolean,
    onSetDarkMode: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            item { DrawerSectionHeader("HOUSEHOLD", onAdd = onAddHousehold) }
            items(households) { household ->
                NavigationDrawerItem(
                    label = { Text(household.name) },
                    selected = false,
                    onClick = { onSelectHousehold(household.id) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item { DrawerSectionHeader("ACTIVITIES", onAdd = onAddActivity) }
            items(trips) { trip ->
                NavigationDrawerItem(
                    label = { Text(trip.name) },
                    selected = false,
                    onClick = { onSelectActivity(trip.id) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text("Scan QR to join") },
            icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
            selected = false,
            onClick = onScanToJoin,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        NavigationDrawerItem(
            label = { Text("Change server") },
            selected = false,
            onClick = onChangeServer,
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
