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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    SIGNALS("Sinyal"),
    CHARTS("Grafik"),
    CALENDAR("Kalender"),
    RISK_MANAGER("Risiko & SL/TP"),
    TUTORIAL("Panduan Pemula")
}

data class RiskCalcInput(
    val balanceText: String = "1000",
    val riskPercent: Double = 1.5,
    val currency: AccountCurrency = AccountCurrency.USD,
    val accountType: AccountType = AccountType.STANDARD,
    val idrUsdRate: Double = 16000.0,
    val action: SignalAction = SignalAction.BUY,
    val entryPriceText: String = "",
    val slPipsText: String = "20",
    val riskReward: Double = 2.0
)

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
    private val _currentTab = MutableStateFlow(AppTab.SIGNALS)
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

    // Risk calculator input state with persistent user balance, currency, and risk percentage
    private val savedBalance = prefs.getString("user_balance", "1000") ?: "1000"
    private val savedRiskPct = prefs.getFloat("user_risk_pct", 1.5f).toDouble()
    private val savedCurrencyCode = prefs.getString("user_currency", AccountCurrency.USD.name) ?: AccountCurrency.USD.name
    private val savedCurrency = try { AccountCurrency.valueOf(savedCurrencyCode) } catch (_: Exception) { AccountCurrency.USD }
    private val savedAccountTypeCode = prefs.getString("user_account_type", AccountType.STANDARD.name) ?: AccountType.STANDARD.name
    private val savedAccountType = try { AccountType.valueOf(savedAccountTypeCode) } catch (_: Exception) { AccountType.STANDARD }
    private val savedIdrRate = prefs.getFloat("user_idr_rate", 16000f).toDouble()

    private val _riskInput = MutableStateFlow(
        RiskCalcInput(
            balanceText = savedBalance,
            riskPercent = savedRiskPct,
            currency = savedCurrency,
            accountType = savedAccountType,
            idrUsdRate = savedIdrRate
        )
    )
    val riskInput: StateFlow<RiskCalcInput> = _riskInput.asStateFlow()

    // Chart display settings
    private val _showEma = MutableStateFlow(true)
    val showEma: StateFlow<Boolean> = _showEma.asStateFlow()

    private val _showBollinger = MutableStateFlow(true)
    val showBollinger: StateFlow<Boolean> = _showBollinger.asStateFlow()

    private val _showLevels = MutableStateFlow(true)
    val showLevels: StateFlow<Boolean> = _showLevels.asStateFlow()

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

        // Initialize entry price for risk calculator with default gold price
        updateRiskInput {
            it.copy(
                entryPriceText = TradingInstrument.XAUUSD.formatPrice(engine.xauPrice.value),
                slPipsText = "25"
            )
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
        val price = if (instrument == TradingInstrument.XAUUSD) engine.xauPrice.value else engine.eurPrice.value
        val defaultPips = if (instrument == TradingInstrument.XAUUSD) "25" else "15"
        updateRiskInput {
            it.copy(
                entryPriceText = instrument.formatPrice(price),
                slPipsText = defaultPips
            )
        }
    }

    fun selectTimeframe(tf: Timeframe) {
        _selectedTimeframe.value = tf
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

    // Risk calculator handlers
    fun updateRiskInput(transform: (RiskCalcInput) -> RiskCalcInput) {
        val updated = transform(_riskInput.value)
        _riskInput.value = updated
        // Save user balance, currency, account type and risk preferences
        prefs.edit()
            .putString("user_balance", updated.balanceText)
            .putFloat("user_risk_pct", updated.riskPercent.toFloat())
            .putString("user_currency", updated.currency.name)
            .putString("user_account_type", updated.accountType.name)
            .putFloat("user_idr_rate", updated.idrUsdRate.toFloat())
            .apply()
    }

    fun syncRiskEntryWithLivePrice() {
        val currentPrice = if (_selectedInstrument.value == TradingInstrument.XAUUSD) {
            engine.xauPrice.value
        } else {
            engine.eurPrice.value
        }
        updateRiskInput { it.copy(entryPriceText = _selectedInstrument.value.formatPrice(currentPrice)) }
    }

    fun syncRiskWithSignal(signal: ScalpSignal) {
        _selectedInstrument.value = signal.instrument
        updateRiskInput {
            it.copy(
                action = signal.action,
                entryPriceText = signal.instrument.formatPrice(signal.entryPrice),
                slPipsText = "%.1f".format(signal.slPips),
                riskReward = signal.riskReward
            )
        }
        _currentTab.value = AppTab.RISK_MANAGER
    }

    fun calculateCurrentRisk(): RiskCalculation {
        val input = _riskInput.value
        val balance = input.balanceText.toDoubleOrNull() ?: 1000.0
        val inst = _selectedInstrument.value
        val livePrice = if (inst == TradingInstrument.XAUUSD) engine.xauPrice.value else engine.eurPrice.value
        val entry = input.entryPriceText.toDoubleOrNull() ?: livePrice
        val slPips = input.slPipsText.toDoubleOrNull() ?: (if (inst == TradingInstrument.XAUUSD) 25.0 else 15.0)

        return engine.calculateRiskManagement(
            instrument = inst,
            action = input.action,
            accountBalance = balance,
            riskPercent = input.riskPercent,
            entryPrice = entry,
            customSlPips = slPips,
            riskRewardRatio = input.riskReward,
            accountCurrency = input.currency,
            accountType = input.accountType,
            idrUsdRate = input.idrUsdRate
        )
    }

    fun saveTradePlan(calc: RiskCalculation) {
        viewModelScope.launch(Dispatchers.IO) {
            val currencySymbol = calc.accountCurrency.symbol
            val entity = TradePlanEntity(
                instrumentSymbol = calc.instrument.symbol,
                action = calc.action.name,
                accountBalance = calc.accountBalance,
                riskPercent = calc.riskPercent,
                lotSize = calc.lotSize,
                entryPrice = calc.entryPrice,
                stopLossPrice = calc.stopLossPrice,
                takeProfitPrice = calc.takeProfit2,
                maxLossUsd = calc.maxLossCurrency,
                potentialProfitUsd = calc.potentialProfit2Currency,
                notes = "${calc.accountType.displayName} | R:R 1:${calc.riskRewardRatio} | Pip Value: $currencySymbol${"%.2f".format(calc.pipValuePerLotCurrency)}/lot"
            )
            signalDao.insertTradePlan(entity)
        }
        Toast.makeText(getApplication(), "Rencana trading berhasil disimpan!", Toast.LENGTH_SHORT).show()
    }

    fun deleteTradePlan(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            signalDao.deleteTradePlan(id)
        }
    }

    fun clearSignalsHistory() {
        engine.clearHistoricalSignals()
        Toast.makeText(getApplication(), "Riwayat sinyal lama berhasil dibersihkan", Toast.LENGTH_SHORT).show()
    }

    fun copyToClipboard(text: String, label: String = "Sinyal Scalping") {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(getApplication(), "Tersalin: $label", Toast.LENGTH_SHORT).show()
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
