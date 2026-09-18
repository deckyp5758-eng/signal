package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TradingInstrument
import com.example.ui.components.AppHeader
import com.example.ui.components.InAppSignalBanner
import com.example.ui.components.InstrumentSelector
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.ChartsScreen
import com.example.ui.screens.RiskManagerScreen
import com.example.ui.screens.SignalsScreen
import com.example.ui.screens.TutorialScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.TradingViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TradingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setAppForeground(true)
    }

    override fun onPause() {
        super.onPause()
        viewModel.setAppForeground(false)
    }
}

@Composable
fun MainAppScreen(viewModel: TradingViewModel) {
    // Request POST_NOTIFICATIONS permission on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.toggleNotifications(isGranted)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.setAppForeground(true)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.setAppForeground(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedInstrument by viewModel.selectedInstrument.collectAsStateWithLifecycle()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val isLiveFeedOnline by viewModel.engine.isLiveFeedOnline.collectAsStateWithLifecycle()
    val latencyMs by viewModel.engine.latencyMs.collectAsStateWithLifecycle()
    val currentSession by viewModel.engine.currentSession.collectAsStateWithLifecycle()
    val bannerSignal by viewModel.bannerSignal.collectAsStateWithLifecycle()
    val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()
    val newsShield by viewModel.newsShield.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val githubRepoOwner by viewModel.githubRepoOwner.collectAsStateWithLifecycle()
    val githubRepoName by viewModel.githubRepoName.collectAsStateWithLifecycle()

    val xauPrice by viewModel.engine.xauPrice.collectAsStateWithLifecycle()
    val eurPrice by viewModel.engine.eurPrice.collectAsStateWithLifecycle()
    val indicatorsMap by viewModel.engine.indicators.collectAsStateWithLifecycle()
    val activeSignalsMap by viewModel.engine.activeSignals.collectAsStateWithLifecycle()

    val signalsHistory by viewModel.signalsHistory.collectAsStateWithLifecycle()
    val tradePlansHistory by viewModel.tradePlansHistory.collectAsStateWithLifecycle()
    val riskInput by viewModel.riskInput.collectAsStateWithLifecycle()

    val showEma by viewModel.showEma.collectAsStateWithLifecycle()
    val showBollinger by viewModel.showBollinger.collectAsStateWithLifecycle()
    val showLevels by viewModel.showLevels.collectAsStateWithLifecycle()

    val currentPrice = if (selectedInstrument == TradingInstrument.XAUUSD) xauPrice else eurPrice
    val activeSignal = activeSignalsMap[selectedInstrument]
    val currentIndicators = indicatorsMap[selectedInstrument]
    val chartCandles = remember(selectedInstrument, selectedTimeframe, xauPrice, eurPrice) {
        viewModel.engine.getCandles(selectedInstrument, selectedTimeframe)
    }

    val calculatedRisk = remember(riskInput, selectedInstrument, currentPrice) {
        viewModel.calculateCurrentRisk()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                AppHeader(
                    notificationsEnabled = notificationsEnabled,
                    isLiveOnline = isLiveFeedOnline,
                    latencyMs = latencyMs,
                    marketSession = currentSession,
                    onToggleNotifications = { viewModel.toggleNotifications(it) },
                    onManualScan = { viewModel.manualScan() }
                )

                // Instrument Switcher shown for Sinyal, Grafik, and Risiko tabs
                if (currentTab != AppTab.TUTORIAL) {
                    InstrumentSelector(
                        selected = selectedInstrument,
                        xauPrice = xauPrice,
                        eurPrice = eurPrice,
                        onSelect = { viewModel.selectInstrument(it) }
                    )
                }

                // In-App Real-time alert banner
                InAppSignalBanner(
                    signal = bannerSignal,
                    onDismiss = { viewModel.dismissBanner() },
                    onViewDetail = { signal ->
                        viewModel.selectInstrument(signal.instrument)
                        viewModel.selectTab(AppTab.SIGNALS)
                        viewModel.dismissBanner()
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                // Tab Sinyal
                NavigationBarItem(
                    selected = currentTab == AppTab.SIGNALS,
                    onClick = { viewModel.selectTab(AppTab.SIGNALS) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.SIGNALS) Icons.Default.Bolt else Icons.Outlined.Bolt,
                            contentDescription = "Sinyal Trading"
                        )
                    },
                    label = { Text("Sinyal", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldPrimary,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("tab_signals")
                )

                // Tab Grafik
                NavigationBarItem(
                    selected = currentTab == AppTab.CHARTS,
                    onClick = { viewModel.selectTab(AppTab.CHARTS) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.CHARTS) Icons.Default.CandlestickChart else Icons.Outlined.CandlestickChart,
                            contentDescription = "Grafik Live"
                        )
                    },
                    label = { Text("Grafik", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldPrimary,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("tab_charts")
                )

                // Tab Kalender Berita (XAU/USD & EUR/USD)
                NavigationBarItem(
                    selected = currentTab == AppTab.CALENDAR,
                    onClick = { viewModel.selectTab(AppTab.CALENDAR) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.CALENDAR) Icons.Default.CalendarMonth else Icons.Outlined.CalendarMonth,
                            contentDescription = "Kalender Berita Ekonomi"
                        )
                    },
                    label = { Text("Kalender", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldPrimary,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("tab_calendar")
                )

                // Tab Risiko
                NavigationBarItem(
                    selected = currentTab == AppTab.RISK_MANAGER,
                    onClick = { viewModel.selectTab(AppTab.RISK_MANAGER) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.RISK_MANAGER) Icons.Default.Shield else Icons.Outlined.Shield,
                            contentDescription = "Manajemen Risiko & SL/TP"
                        )
                    },
                    label = { Text("Risiko SL/TP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldPrimary,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("tab_risk")
                )

                // Tab Tutorial
                NavigationBarItem(
                    selected = currentTab == AppTab.TUTORIAL,
                    onClick = { viewModel.selectTab(AppTab.TUTORIAL) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.TUTORIAL) Icons.Default.School else Icons.Outlined.School,
                            contentDescription = "Panduan Pemula"
                        )
                    },
                    label = { Text("Panduan", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldPrimary,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("tab_tutorial")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.SIGNALS -> {
                    SignalsScreen(
                        selectedInstrument = selectedInstrument,
                        currentPrice = currentPrice,
                        activeSignal = activeSignal,
                        indicators = currentIndicators,
                        signalHistory = signalsHistory,
                        onCopySignal = { text, label -> viewModel.copyToClipboard(text, label) },
                        onApplyToRiskManager = { signal -> viewModel.syncRiskWithSignal(signal) },
                        onScanMarket = { viewModel.manualScan() },
                        onClearHistory = { viewModel.clearSignalsHistory() },
                        newsShield = newsShield,
                        onOpenCalendar = { viewModel.selectTab(AppTab.CALENDAR) }
                    )
                }
                AppTab.CHARTS -> {
                    ChartsScreen(
                        instrument = selectedInstrument,
                        currentPrice = currentPrice,
                        timeframe = selectedTimeframe,
                        candles = chartCandles,
                        activeSignal = activeSignal,
                        indicators = currentIndicators,
                        showEma = showEma,
                        showBollinger = showBollinger,
                        showLevels = showLevels,
                        onSelectTimeframe = { viewModel.selectTimeframe(it) },
                        onToggleEma = { viewModel.toggleEma() },
                        onToggleBollinger = { viewModel.toggleBollinger() },
                        onToggleLevels = { viewModel.toggleLevels() }
                    )
                }
                AppTab.CALENDAR -> {
                    CalendarScreen(
                        events = calendarEvents,
                        newsShield = newsShield,
                        selectedInstrument = selectedInstrument,
                        onSelectInstrument = { viewModel.selectInstrument(it) }
                    )
                }
                AppTab.RISK_MANAGER -> {
                    RiskManagerScreen(
                        selectedInstrument = selectedInstrument,
                        riskInput = riskInput,
                        riskCalculation = calculatedRisk,
                        tradePlans = tradePlansHistory,
                        onUpdateRiskInput = { viewModel.updateRiskInput(it) },
                        onSyncLivePrice = { viewModel.syncRiskEntryWithLivePrice() },
                        onSaveTradePlan = { viewModel.saveTradePlan(it) },
                        onDeleteTradePlan = { viewModel.deleteTradePlan(it) },
                        onCopyText = { text, label -> viewModel.copyToClipboard(text, label) }
                    )
                }
                AppTab.TUTORIAL -> {
                    TutorialScreen(
                        updateState = updateState,
                        repoOwner = githubRepoOwner,
                        repoName = githubRepoName,
                        onCheckUpdate = { viewModel.checkForGitHubUpdates() },
                        onUpdateRepoConfig = { owner, repo -> viewModel.updateGitHubRepoConfig(owner, repo) },
                        onOpenUrl = { viewModel.openGitHubUrl(it) }
                    )
                }
            }
        }
    }
}

// Retained for test compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Android") }
}
