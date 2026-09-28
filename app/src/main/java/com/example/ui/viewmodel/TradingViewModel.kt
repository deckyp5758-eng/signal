package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SignalEntity
import com.example.data.local.TradePlanEntity
import com.example.data.model.*
import com.example.service.ScalpingSignalEngine
import com.example.service.SignalNotificationHelper
import com.example.service.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    CHARTS("Grafik"),
    CALENDAR("Kalender"),
    TUTORIAL("Panduan Pemula")
}

class TradingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("user_trading_settings", Context.MODE_PRIVATE)
    private val db = AppDatabase.getDatabase(application)
    private val signalDao = db.signalDao()
    private val notificationHelper = SignalNotificationHelper(application)

    val engine = ScalpingSignalEngine(signalDao, notificationHelper, viewModelScope)
    val calendarService = com.example.service.EconomicCalendarService()
    val githubUpdateService = com.example.service.GitHubUpdateService(application)

    // GitHub Auto-Update State
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _githubRepoOwner = MutableStateFlow(
        prefs.getString("gh_repo_owner", "kingdomedantech") ?: "kingdomedantech"
    )
    val githubRepoOwner: StateFlow<String> = _githubRepoOwner.asStateFlow()

    private val _githubRepoName = MutableStateFlow(
        prefs.getString("gh_repo_name", "scalpsignal-app") ?: "scalpsignal-app"
    )
    val githubRepoName: StateFlow<String> = _githubRepoName.asStateFlow()

    // Economic events & news shield for XAU/USD and EUR/USD
    private val _calendarEvents = MutableStateFlow<List<EconomicEvent>>(emptyList())
    val calendarEvents: StateFlow<List<EconomicEvent>> = _calendarEvents.asStateFlow()

    private val _newsShield = MutableStateFlow<NewsShieldStatus>(
        NewsShieldStatus(isShieldActive = false, currentEvent = null, minutesUntil = 999, warningMessage = "")
    )
    val newsShield: StateFlow<NewsShieldStatus> = _newsShield.asStateFlow()

    // Current navigation tab
    private val _currentTab = MutableStateFlow(AppTab.CHARTS)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Selected instrument
    private val _selectedInstrument = MutableStateFlow(TradingInstrument.XAUUSD)
    val selectedInstrument: StateFlow<TradingInstrument> = _selectedInstrument.asStateFlow()

    // Selected timeframe
    private val _selectedTimeframe = MutableStateFlow(Timeframe.M5)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    // Notifications enabled
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    // Latest in-app alert banner
    private val _bannerSignal = MutableStateFlow<ScalpSignal?>(null)
    val bannerSignal: StateFlow<ScalpSignal?> = _bannerSignal.asStateFlow()

    // History from database
    val signalsHistory: StateFlow<List<SignalEntity>> = signalDao.getAllSignals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tradePlansHistory: StateFlow<List<TradePlanEntity>> = signalDao.getAllTradePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chart display settings
    private val _showEma = MutableStateFlow(true)
    val showEma: StateFlow<Boolean> = _showEma.asStateFlow()

    private val _showBollinger = MutableStateFlow(true)
    val showBollinger: StateFlow<Boolean> = _showBollinger.asStateFlow()

    private val _showLevels = MutableStateFlow(true)
    val showLevels: StateFlow<Boolean> = _showLevels.asStateFlow()

    private val _showPatterns = MutableStateFlow(true)
    val showPatterns: StateFlow<Boolean> = _showPatterns.asStateFlow()

    private val _selectedPatternFilter = MutableStateFlow(PatternTypeCategory.ALL)
    val selectedPatternFilter: StateFlow<PatternTypeCategory> = _selectedPatternFilter.asStateFlow()

    // Confluence Grade & MTF Trend Filters
    private val _selectedGradeFilter = MutableStateFlow<ConfluenceGrade?>(null)
    val selectedGradeFilter: StateFlow<ConfluenceGrade?> = _selectedGradeFilter.asStateFlow()

    private val _filterOnlyHtfAligned = MutableStateFlow(false)
    val filterOnlyHtfAligned: StateFlow<Boolean> = _filterOnlyHtfAligned.asStateFlow()

    init {
        // Observe incoming signals for in-app banner
        viewModelScope.launch {
            engine.newSignalEvent.collect { signal ->
                _bannerSignal.value = signal
            }
        }

        // Initialize and periodically refresh economic calendar & news shield
        refreshCalendar()
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000) // update countdowns every 60s
                refreshCalendar()
            }
        }
    }

    fun refreshCalendar() {
        _calendarEvents.value = calendarService.getUpcomingEvents()
        _newsShield.value = calendarService.evaluateNewsShield(_selectedInstrument.value)
    }

    fun setAppForeground(inForeground: Boolean) {
        engine.setAppForeground(inForeground)
        if (inForeground) {
            refreshCalendar()
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun selectInstrument(instrument: TradingInstrument) {
        _selectedInstrument.value = instrument
        _newsShield.value = calendarService.evaluateNewsShield(instrument)
        engine.fetchTimeframeCandlesOnDemand(instrument, _selectedTimeframe.value)
    }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun selectTimeframe(tf: Timeframe) {
        _selectedTimeframe.value = tf
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                engine.fetchTimeframeCandlesOnDemand(_selectedInstrument.value, tf)
                engine.manualScanSignals()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun refreshChartAndScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val liveQuote = engine.liveMarketService.fetchLivePrices()
                if (liveQuote != null && liveQuote.isLiveOnline) {
                    engine.setLiveQuoteDirect(liveQuote)
                }
                engine.fetchTimeframeCandlesOnDemand(_selectedInstrument.value, _selectedTimeframe.value)
                engine.manualScanSignals()
            } catch (_: Exception) {
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        engine.notificationsEnabled = enabled
    }

    fun dismissBanner() {
        _bannerSignal.value = null
    }

    fun manualScan() {
        engine.manualScanSignals()
    }

    fun toggleEma() {
        _showEma.value = !_showEma.value
    }

    fun toggleBollinger() {
        _showBollinger.value = !_showBollinger.value
    }

    fun toggleLevels() {
        _showLevels.value = !_showLevels.value
    }

    fun togglePatterns() {
        _showPatterns.value = !_showPatterns.value
    }

    fun selectPatternFilter(category: PatternTypeCategory) {
        _selectedPatternFilter.value = category
    }

    fun selectGradeFilter(grade: ConfluenceGrade?) {
        _selectedGradeFilter.value = grade
    }

    fun toggleHtfAlignedFilter() {
        _filterOnlyHtfAligned.value = !_filterOnlyHtfAligned.value
    }

    fun getMultiTimeframeTrends(instrument: TradingInstrument): Map<Timeframe, TrendDirection> {
        return Timeframe.values().associateWith { tf ->
            val c = engine.getCandles(instrument, tf)
            com.example.service.IndicatorCalculator.calculateTrend(c)
        }
    }

    fun clearSignalsHistory() {
        engine.clearHistoricalSignals()
        Toast.makeText(getApplication(), "Riwayat sinyal lama berhasil dibersihkan", Toast.LENGTH_SHORT).show()
    }

    fun copyToClipboard(text: String, label: String = "Sinyal Scalping") {
        val cleanText = text.trim()
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, cleanText)
        clipboard.setPrimaryClip(clip)
        
        val isSingleNumber = cleanText.matches(Regex("^[0-9]+(\\.[0-9]+)?$"))
        val toastMsg = if (isSingleNumber) {
            "✅ $label ($cleanText) tersalin! Siap paste di MT5/MT4"
        } else {
            "✅ $label tersalin ke Clipboard!"
        }
        Toast.makeText(getApplication(), toastMsg, Toast.LENGTH_SHORT).show()
    }

    fun updateGitHubRepoConfig(owner: String, repo: String) {
        _githubRepoOwner.value = owner.trim()
        _githubRepoName.value = repo.trim()
        prefs.edit()
            .putString("gh_repo_owner", owner.trim())
            .putString("gh_repo_name", repo.trim())
            .apply()
    }

    fun checkForGitHubUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val release = githubUpdateService.checkLatestRelease(
                customOwner = _githubRepoOwner.value,
                customRepo = _githubRepoName.value
            )
            if (release != null) {
                if (release.isNewerVersion) {
                    _updateState.value = UpdateState.Available(release)
                } else {
                    _updateState.value = UpdateState.UpToDate
                }
            } else {
                _updateState.value = UpdateState.Error("Tidak dapat memeriksa rilis di GitHub (${_githubRepoOwner.value}/${_githubRepoName.value}). Pastikan repo bersifat Publik atau rilis telah dibuat.")
            }
        }
    }

    fun openGitHubUrl(url: String) {
        githubUpdateService.openDownloadUrl(url)
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
    }
}
