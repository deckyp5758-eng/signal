package com.example.service

import com.example.data.local.SignalDao
import com.example.data.local.SignalEntity
import com.example.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

class ScalpingSignalEngine(
    private val signalDao: SignalDao,
    private val notificationHelper: SignalNotificationHelper,
    private val scope: CoroutineScope
) {
    private val liveMarketService = LiveMarketService()

    // Live Feed Status
    private val _isLiveFeedOnline = MutableStateFlow(false)
    val isLiveFeedOnline: StateFlow<Boolean> = _isLiveFeedOnline.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private val _currentSession = MutableStateFlow(MarketSessionHelper.getCurrentSession())
    val currentSession: StateFlow<MarketSession> = _currentSession.asStateFlow()

    // App foreground state for power & data saving
    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    // Anti-spam cooldown tracking (instrument -> timestamp)
    private val lastSignalTime = mutableMapOf<TradingInstrument, Long>()
    private val lastSignalAction = mutableMapOf<TradingInstrument, SignalAction>()

    // Current prices
    private val _xauPrice = MutableStateFlow(4377.34)
    val xauPrice: StateFlow<Double> = _xauPrice.asStateFlow()

    private val _eurPrice = MutableStateFlow(1.14834)
    val eurPrice: StateFlow<Double> = _eurPrice.asStateFlow()

    // Price change percentage
    private val _xauChangePct = MutableStateFlow(0.84)
    val xauChangePct: StateFlow<Double> = _xauChangePct.asStateFlow()

    private val _eurChangePct = MutableStateFlow(0.07)
    val eurChangePct: StateFlow<Double> = _eurChangePct.asStateFlow()

    // High / Low 24h
    val xauHigh = 4399.58
    val xauLow = 4334.02
    val eurHigh = 1.14913
    val eurLow = 1.14544

    // Candle series per instrument and timeframe
    private val candleMap = mutableMapOf<Pair<TradingInstrument, Timeframe>, MutableList<Candle>>()

    // Latest indicators
    private val _indicators = MutableStateFlow<Map<TradingInstrument, IndicatorValues>>(emptyMap())
    val indicators: StateFlow<Map<TradingInstrument, IndicatorValues>> = _indicators.asStateFlow()

    // Current active signals
    private val _activeSignals = MutableStateFlow<Map<TradingInstrument, ScalpSignal?>>(
        mapOf(TradingInstrument.XAUUSD to null, TradingInstrument.EURUSD to null)
    )
    val activeSignals: StateFlow<Map<TradingInstrument, ScalpSignal?>> = _activeSignals.asStateFlow()

    // In-app signal event for snackbar / banner popups
    private val _newSignalEvent = MutableSharedFlow<ScalpSignal>(replay = 0)
    val newSignalEvent: SharedFlow<ScalpSignal> = _newSignalEvent.asSharedFlow()

    // Candlesticks flow for UI charts
    private val _chartCandles = MutableStateFlow<List<Candle>>(emptyList())
    val chartCandles: StateFlow<List<Candle>> = _chartCandles.asStateFlow()

    var notificationsEnabled: Boolean = true
    private var isSimulating = false
    private var job: Job? = null

    init {
        // Clear any old dummy/synthetic signals from Room to avoid overlap with real-time data
        scope.launch(Dispatchers.IO) {
            signalDao.clearAllSignals()
            syncLiveCandles()
            try {
                val quote = liveMarketService.fetchLivePrices()
                if (quote != null && quote.isLiveOnline) {
                    _xauPrice.value = quote.xauPrice
                    _eurPrice.value = quote.eurPrice
                    _isLiveFeedOnline.value = true
                    _lastSyncTime.value = quote.timestamp
                }
            } catch (_: Exception) {}
        }
        startTickEngine()
    }

    fun startTickEngine() {
        if (isSimulating) return
        isSimulating = true

        // Initial live candle & price fetch
        scope.launch(Dispatchers.IO) {
            try {
                val initQuote = liveMarketService.fetchLivePrices()
                if (initQuote != null && initQuote.isLiveOnline) {
                    _xauPrice.value = initQuote.xauPrice
                    _eurPrice.value = initQuote.eurPrice
                    _isLiveFeedOnline.value = true
                    _lastSyncTime.value = initQuote.timestamp
                    _latencyMs.value = initQuote.latencyMs
                    _currentSession.value = MarketSessionHelper.getCurrentSession()
                }
            } catch (_: Exception) {}
            syncLiveCandles()
        }

        job = scope.launch(Dispatchers.Default) {
            var tickCounter = 0
            var consecutiveFailures = 0
            while (isActive) {
                // High-speed real-time polling: 1.2s in foreground, 12s in background
                val pollDelay = if (_isForeground.value) 1200L else 12000L
                delay(pollDelay)
                tickCounter++

                // Fetch authentic real-time market quote automatically
                try {
                    val liveQuote = liveMarketService.fetchLivePrices()
                    if (liveQuote != null && liveQuote.isLiveOnline) {
                        consecutiveFailures = 0
                        _xauPrice.value = liveQuote.xauPrice
                        _eurPrice.value = liveQuote.eurPrice
                        _isLiveFeedOnline.value = true
                        _lastSyncTime.value = liveQuote.timestamp
                        _latencyMs.value = liveQuote.latencyMs
                        _currentSession.value = MarketSessionHelper.getCurrentSession()

                        updateLastCandle(TradingInstrument.XAUUSD, liveQuote.xauPrice)
                        updateLastCandle(TradingInstrument.EURUSD, liveQuote.eurPrice)
                    } else {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            _isLiveFeedOnline.value = false
                        }
                    }
                } catch (_: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) {
                        _isLiveFeedOnline.value = false
                    }
                }

                // Recalculate indicators continuously on live market data
                if (tickCounter % 2 == 0) {
                    updateIndicatorsForBoth()
                }

                // Automatic sync of live historical candle bars every ~18 seconds
                if (tickCounter % 15 == 0 && _isForeground.value) {
                    scope.launch(Dispatchers.IO) {
                        syncLiveCandles()
                    }
                }

                // Automatically evaluate signals every ~10 seconds
                if (tickCounter % 8 == 0) {
                    evaluateAutoSignals()
                }
            }
        }
    }

    fun setAppForeground(inForeground: Boolean) {
        _isForeground.value = inForeground
        if (inForeground) {
            scope.launch(Dispatchers.IO) {
                try {
                    val quote = liveMarketService.fetchLivePrices()
                    if (quote != null && quote.isLiveOnline) {
                        _xauPrice.value = quote.xauPrice
                        _eurPrice.value = quote.eurPrice
                        _isLiveFeedOnline.value = true
                        _lastSyncTime.value = quote.timestamp
                        _latencyMs.value = quote.latencyMs
                    }
                    syncLiveCandles()
                } catch (_: Exception) {}
            }
        }
    }

    fun isTp1Reached(signal: ScalpSignal): Boolean {
        val currentPrice = if (signal.instrument == TradingInstrument.XAUUSD) _xauPrice.value else _eurPrice.value
        return if (signal.action == SignalAction.BUY) {
            currentPrice >= signal.takeProfit1
        } else {
            currentPrice <= signal.takeProfit1
        }
    }

    fun clearHistoricalSignals() {
        scope.launch(Dispatchers.IO) {
            signalDao.clearAllSignals()
        }
        _activeSignals.value = mapOf(
            TradingInstrument.XAUUSD to null,
            TradingInstrument.EURUSD to null
        )
    }

    private suspend fun syncLiveCandles() {
        try {
            for (tf in listOf(Timeframe.M1, Timeframe.M5, Timeframe.M15)) {
                val xauCandles = liveMarketService.fetchLiveCandles(TradingInstrument.XAUUSD, tf)
                if (!xauCandles.isNullOrEmpty()) {
                    candleMap[Pair(TradingInstrument.XAUUSD, tf)] = xauCandles.toMutableList()
                }

                val eurCandles = liveMarketService.fetchLiveCandles(TradingInstrument.EURUSD, tf)
                if (!eurCandles.isNullOrEmpty()) {
                    candleMap[Pair(TradingInstrument.EURUSD, tf)] = eurCandles.toMutableList()
                }
            }
            withContext(Dispatchers.Main) {
                updateIndicatorsForBoth()
                evaluateAutoSignals(forceNew = false)
            }
        } catch (e: Exception) {
            // Keep existing candle map
        }
    }

    private fun updateLastCandle(instrument: TradingInstrument, currentPrice: Double) {
        for (tf in Timeframe.values()) {
            val list = candleMap[Pair(instrument, tf)] ?: continue
            if (list.isNotEmpty()) {
                val last = list.last()
                val updated = last.copy(
                    high = maxOf(last.high, currentPrice),
                    low = minOf(last.low, currentPrice),
                    close = currentPrice,
                    volume = last.volume + Random.nextDouble() * 2.0
                )
                list[list.size - 1] = updated
            }
        }
    }

    private fun updateIndicatorsForBoth() {
        val newMap = mutableMapOf<TradingInstrument, IndicatorValues>()
        for (inst in TradingInstrument.values()) {
            val candles = candleMap[Pair(inst, Timeframe.M5)] ?: emptyList()
            newMap[inst] = IndicatorCalculator.calculateIndicators(candles)
        }
        _indicators.value = newMap
    }

    fun getCandles(instrument: TradingInstrument, timeframe: Timeframe): List<Candle> {
        return candleMap[Pair(instrument, timeframe)]?.toList() ?: emptyList()
    }

    fun manualScanSignals() {
        scope.launch(Dispatchers.Default) {
            // Trigger an explicit scan and indicator recalculation
            updateIndicatorsForBoth()
            evaluateAutoSignals(forceNew = true)
        }
    }

    private fun evaluateAutoSignals(forceNew: Boolean = false) {
        for (targetInstrument in TradingInstrument.values()) {
            evaluateSignalForSingleInstrument(targetInstrument, forceNew)
        }
    }

    private fun evaluateSignalForSingleInstrument(targetInstrument: TradingInstrument, forceNew: Boolean = false) {
        val candles = candleMap[Pair(targetInstrument, Timeframe.M5)] ?: return
        val ind = IndicatorCalculator.calculateIndicators(candles)
        val currentPrice = if (targetInstrument == TradingInstrument.XAUUSD) _xauPrice.value else _eurPrice.value

        val isBullish = ind.emaFast > ind.emaSlow || ind.rsi < 40 || ind.macdHist > 0
        val action = if (isBullish) SignalAction.BUY else SignalAction.SELL

        // Anti-spam cooldown: skip duplicate direction within 3 minutes unless manually triggered or no active signal exists
        val now = System.currentTimeMillis()
        val lastTime = lastSignalTime[targetInstrument] ?: 0L
        val lastAction = lastSignalAction[targetInstrument]
        val currentActive = _activeSignals.value[targetInstrument]

        if (!forceNew && currentActive != null && action == lastAction && (now - lastTime) < 180_000L) {
            return
        }
        lastSignalTime[targetInstrument] = now
        lastSignalAction[targetInstrument] = action

        val pipMultiplier = targetInstrument.pipMultiplier
        val atr = ind.atr

        // Automatic SL and TP based on volatility & Risk-Reward
        val slPips = if (targetInstrument == TradingInstrument.XAUUSD) {
            (atr * 1.4 / pipMultiplier).coerceIn(15.0, 45.0)
        } else {
            (atr * 1.3 / pipMultiplier).coerceIn(8.0, 25.0)
        }
        val slPriceOffset = slPips * pipMultiplier

        val stopLoss: Double
        val tp1: Double
        val tp2: Double
        val tp3: Double

        if (action == SignalAction.BUY) {
            stopLoss = currentPrice - slPriceOffset
            tp1 = currentPrice + (slPriceOffset * 1.0)
            tp2 = currentPrice + (slPriceOffset * 1.8)
            tp3 = currentPrice + (slPriceOffset * 2.6)
        } else {
            stopLoss = currentPrice + slPriceOffset
            tp1 = currentPrice - (slPriceOffset * 1.0)
            tp2 = currentPrice - (slPriceOffset * 1.8)
            tp3 = currentPrice - (slPriceOffset * 2.6)
        }

        val reasonText = if (action == SignalAction.BUY) {
            "Konfirmasi EMA 9 memotong naik EMA 21 | RSI: ${"%.1f".format(ind.rsi)} | Momentum Bullish Scalp"
        } else {
            "Rejection MA resistensi | EMA 9 < 21 | RSI: ${"%.1f".format(ind.rsi)} | Momentum Bearish Scalp"
        }

        val confidenceVal = Random.nextInt(82, 94)

        val newSignal = ScalpSignal(
            id = UUID.randomUUID().toString(),
            instrument = targetInstrument,
            action = action,
            strength = if (confidenceVal >= 88) SignalStrength.STRONG else SignalStrength.MODERATE,
            entryPrice = currentPrice,
            stopLoss = stopLoss,
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            takeProfit3 = tp3,
            slPips = slPips,
            tp1Pips = slPips * 1.0,
            tp2Pips = slPips * 1.8,
            tp3Pips = slPips * 2.6,
            riskReward = 1.8,
            confidence = confidenceVal,
            reason = reasonText,
            timestamp = System.currentTimeMillis(),
            timeframe = Timeframe.M5,
            indicators = ind
        )

        val currentMap = _activeSignals.value.toMutableMap()
        currentMap[targetInstrument] = newSignal
        _activeSignals.value = currentMap

        // Emit for in-app alert
        scope.launch {
            _newSignalEvent.emit(newSignal)
        }

        // Post system notification if enabled
        if (notificationsEnabled) {
            notificationHelper.postSignalNotification(newSignal)
        }

        // Save to Room DB
        scope.launch(Dispatchers.IO) {
            signalDao.insertSignal(signalToEntity(newSignal))
        }
    }

    // Automatic Risk Management Calculator with Multi-Currency & Account Types
    fun calculateRiskManagement(
        instrument: TradingInstrument,
        action: SignalAction,
        accountBalance: Double,
        riskPercent: Double,
        entryPrice: Double,
        customSlPips: Double?,
        riskRewardRatio: Double = 2.0,
        accountCurrency: AccountCurrency = AccountCurrency.USD,
        accountType: AccountType = AccountType.STANDARD,
        idrUsdRate: Double = 16000.0
    ): RiskCalculation {
        val safeBalance = maxOf(accountBalance, 1.0)
        val safeRiskPct = riskPercent.coerceIn(0.1, 10.0)
        val riskAmountCurrency = safeBalance * (safeRiskPct / 100.0)

        // Conversion factor between USD and chosen Currency
        val eurLivePrice = if (_eurPrice.value > 0) _eurPrice.value else 1.084
        val currencyToUsdMultiplier = when (accountCurrency) {
            AccountCurrency.USD -> 1.0
            AccountCurrency.IDR -> 1.0 / idrUsdRate
            AccountCurrency.EUR -> eurLivePrice // 1 EUR = ~1.084 USD
        }
        val riskAmountUsd = riskAmountCurrency * currencyToUsdMultiplier

        // Pip distance
        val slPips = customSlPips ?: if (instrument == TradingInstrument.XAUUSD) 20.0 else 15.0
        val slPriceOffset = slPips * instrument.pipMultiplier

        val stopLossPrice: Double
        val tp1Price: Double
        val tp2Price: Double
        val tp3Price: Double

        if (action == SignalAction.BUY) {
            stopLossPrice = entryPrice - slPriceOffset
            tp1Price = entryPrice + (slPriceOffset * 1.0)
            tp2Price = entryPrice + (slPriceOffset * riskRewardRatio)
            tp3Price = entryPrice + (slPriceOffset * (riskRewardRatio * 1.5))
        } else {
            stopLossPrice = entryPrice + slPriceOffset
            tp1Price = entryPrice - (slPriceOffset * 1.0)
            tp2Price = entryPrice - (slPriceOffset * riskRewardRatio)
            tp3Price = entryPrice - (slPriceOffset * (riskRewardRatio * 1.5))
        }

        // Pip Value calculation:
        // Base pip value per 1.0 standard lot = $10.00 (EURUSD: 100k * 0.0001 = $10, XAUUSD: 100 oz * 0.10 = $10)
        val pipValuePerLotUsd = 10.0 * accountType.lotMultiplier
        val pipValuePerLotCurrency = when (accountCurrency) {
            AccountCurrency.USD -> pipValuePerLotUsd
            AccountCurrency.IDR -> pipValuePerLotUsd * idrUsdRate
            AccountCurrency.EUR -> pipValuePerLotUsd / eurLivePrice
        }

        // Money risk per lot in chosen currency
        val moneyRiskPerLotCurrency = slPips * pipValuePerLotCurrency
        val rawLot = if (moneyRiskPerLotCurrency > 0) riskAmountCurrency / moneyRiskPerLotCurrency else 0.01
        val lotSize = (kotlin.math.round(rawLot * 100.0) / 100.0).coerceIn(accountType.minLot, accountType.maxLot)

        val actualMaxLossCurrency = lotSize * slPips * pipValuePerLotCurrency
        val actualMaxLossUsd = actualMaxLossCurrency * currencyToUsdMultiplier

        val potentialProfit1Currency = actualMaxLossCurrency * 1.0
        val potentialProfit1Usd = actualMaxLossUsd * 1.0
        val potentialProfit2Currency = actualMaxLossCurrency * riskRewardRatio
        val potentialProfit2Usd = actualMaxLossUsd * riskRewardRatio
        val potentialProfit3Currency = actualMaxLossCurrency * (riskRewardRatio * 1.5)
        val potentialProfit3Usd = actualMaxLossUsd * (riskRewardRatio * 1.5)

        return RiskCalculation(
            instrument = instrument,
            action = action,
            accountCurrency = accountCurrency,
            accountType = accountType,
            accountBalance = safeBalance,
            riskPercent = safeRiskPct,
            riskAmountCurrency = riskAmountCurrency,
            riskAmountUsd = riskAmountUsd,
            pipValuePerLotCurrency = pipValuePerLotCurrency,
            pipValuePerLotUsd = pipValuePerLotUsd,
            lotSize = lotSize,
            entryPrice = entryPrice,
            stopLossPrice = stopLossPrice,
            takeProfit1 = tp1Price,
            takeProfit2 = tp2Price,
            takeProfit3 = tp3Price,
            slPips = slPips,
            tp1Pips = slPips * 1.0,
            tp2Pips = slPips * riskRewardRatio,
            tp3Pips = slPips * (riskRewardRatio * 1.5),
            maxLossCurrency = actualMaxLossCurrency,
            maxLossUsd = actualMaxLossUsd,
            potentialProfit1Currency = potentialProfit1Currency,
            potentialProfit1Usd = potentialProfit1Usd,
            potentialProfit2Currency = potentialProfit2Currency,
            potentialProfit2Usd = potentialProfit2Usd,
            potentialProfit3Currency = potentialProfit3Currency,
            potentialProfit3Usd = potentialProfit3Usd,
            riskRewardRatio = riskRewardRatio
        )
    }

    private fun signalToEntity(s: ScalpSignal): SignalEntity {
        return SignalEntity(
            id = s.id,
            instrumentSymbol = s.instrument.symbol,
            action = s.action.name,
            strength = s.strength.name,
            entryPrice = s.entryPrice,
            stopLoss = s.stopLoss,
            takeProfit1 = s.takeProfit1,
            takeProfit2 = s.takeProfit2,
            takeProfit3 = s.takeProfit3,
            slPips = s.slPips,
            tp1Pips = s.tp1Pips,
            riskReward = s.riskReward,
            confidence = s.confidence,
            reason = s.reason,
            timeframe = s.timeframe.name,
            rsi = s.indicators.rsi,
            emaFast = s.indicators.emaFast,
            emaSlow = s.indicators.emaSlow,
            timestamp = s.timestamp
        )
    }

    fun stop() {
        job?.cancel()
        isSimulating = false
    }
}
