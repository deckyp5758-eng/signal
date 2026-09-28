package com.example.service

import com.example.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * State render immutable untuk antarmuka grafik candlestick scalping.
 * Semua kalkulasi matematika, normalisasi OANDA/FOREX.com, EMA, indikator,
 * dan pola SMC/Harmonik telah dihitung di latar belakang (Background Coroutine Dispatcher)
 * sehingga UI Layer (Jetpack Compose Canvas) bebas dari lag/freeze dan dapat me-render pada 60+ FPS.
 */
data class ChartRenderState(
    val instrument: TradingInstrument = TradingInstrument.XAUUSD,
    val timeframe: Timeframe = Timeframe.M5,
    val candles: List<Candle> = emptyList(),
    val ema9: List<Double> = emptyList(),
    val ema21: List<Double> = emptyList(),
    val bollingerBands: IndicatorCalculator.BollingerResult = IndicatorCalculator.BollingerResult(0.0, 0.0, 0.0),
    val indicators: IndicatorValues = IndicatorValues(50.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    val detectedPatterns: List<DetectedPattern> = emptyList(),
    val multiTimeframeTrends: Map<Timeframe, TrendDirection> = emptyMap(),
    val htfCandles: List<Candle> = emptyList(),
    val minPrice: Double = 0.0,
    val maxPrice: Double = 0.0,
    val priceRange: Double = 0.0,
    val isCalculating: Boolean = false,
    val calculationTimestamp: Long = 0L
)

/**
 * Engine kalkulasi grafik berkinerja tinggi yang berjalan sepenuhnya di Background Coroutine Dispatcher (Dispatchers.Default).
 * Mencegah pemblokiran Main Thread (UI thread freeze) saat normalisasi feed data dan kalkulasi candlestick yang kompleks.
 */
class ChartDataEngine(
    private val normalizerService: MarketFeedNormalizationService = MarketFeedNormalizationService(),
    private val scope: CoroutineScope,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val _renderState = MutableStateFlow(ChartRenderState())
    val renderState: StateFlow<ChartRenderState> = _renderState.asStateFlow()

    private var activeCalculationJob: Job? = null

    /**
     * Memproses data grafik secara asinkron di Background Coroutine Dispatcher.
     * Mengisolasi seluruh kalkulasi berat (normalisasi data, penegakan invarian OHLC,
     * kalkulasi EMA, RSI, MACD, Bollinger Bands, Trend MTF, dan Pengenalan Pola SMC).
     */
    fun computeChartDataAsync(
        rawCandles: List<Candle>,
        instrument: TradingInstrument,
        timeframe: Timeframe,
        livePriceRef: Double? = null,
        candleMapProvider: ((TradingInstrument, Timeframe) -> List<Candle>)? = null,
        onComplete: ((ChartRenderState) -> Unit)? = null
    ) {
        activeCalculationJob?.cancel()
        _renderState.value = _renderState.value.copy(isCalculating = true)

        activeCalculationJob = scope.launch(calculationDispatcher) {
            try {
                val processedState = processCalculationsInBackground(
                    rawCandles = rawCandles,
                    instrument = instrument,
                    timeframe = timeframe,
                    livePriceRef = livePriceRef,
                    candleMapProvider = candleMapProvider
                )

                _renderState.value = processedState
                onComplete?.invoke(processedState)
            } catch (e: CancellationException) {
                // Calculation cancelled due to newer tick or timeframe switch
            } catch (_: Exception) {
                _renderState.value = _renderState.value.copy(isCalculating = false)
            }
        }
    }

    /**
     * Logika internal kalkulasi data grafik yang berjalan 100% di background thread.
     */
    suspend fun processCalculationsInBackground(
        rawCandles: List<Candle>,
        instrument: TradingInstrument,
        timeframe: Timeframe,
        livePriceRef: Double? = null,
        candleMapProvider: ((TradingInstrument, Timeframe) -> List<Candle>)? = null
    ): ChartRenderState = withContext(calculationDispatcher) {
        // 1. Normalisasi data pasar & penyelarasan batas interval waktu UTC (OANDA/FOREX.com format)
        val normalizedCandles = if (rawCandles.isNotEmpty()) {
            normalizerService.normalizeCandles(
                rawCandles = rawCandles,
                instrument = instrument,
                timeframe = timeframe,
                livePriceRef = livePriceRef
            )
        } else {
            emptyList()
        }

        val closes = normalizedCandles.map { it.close }

        // 2. Kalkulasi Indikator Teknis Paralel
        val ema9Deferred = async(calculationDispatcher) {
            IndicatorCalculator.calculateEMA(closes, 9)
        }
        val ema21Deferred = async(calculationDispatcher) {
            IndicatorCalculator.calculateEMA(closes, 21)
        }
        val bbDeferred = async(calculationDispatcher) {
            IndicatorCalculator.calculateBollingerBands(closes, 20, 2.0)
        }
        val indicatorsDeferred = async(calculationDispatcher) {
            IndicatorCalculator.calculateIndicators(normalizedCandles)
        }

        // 3. Higher Timeframe (H1) Candles untuk konfluensi tren
        val htfCandles = candleMapProvider?.invoke(instrument, Timeframe.H1) ?: emptyList()

        // 4. Kalkulasi Trend Multi-Timeframe (MTF)
        val mtfTrendsDeferred = async(calculationDispatcher) {
            if (candleMapProvider != null) {
                Timeframe.values().associateWith { tf ->
                    val tfCandles = if (tf == timeframe) normalizedCandles else candleMapProvider.invoke(instrument, tf)
                    IndicatorCalculator.calculateTrend(tfCandles)
                }
            } else {
                mapOf(timeframe to IndicatorCalculator.calculateTrend(normalizedCandles))
            }
        }

        // 5. Mesin Pengenalan Pola & Konfluensi SMC (Smart Money Concepts)
        val patternsDeferred = async(calculationDispatcher) {
            PatternRecognitionEngine.detectAllPatterns(
                candles = normalizedCandles,
                instrument = instrument,
                currentTimeframe = timeframe,
                htfCandles = htfCandles
            )
        }

        val ema9 = ema9Deferred.await()
        val ema21 = ema21Deferred.await()
        val bb = bbDeferred.await()
        val indicators = indicatorsDeferred.await()
        val mtfTrends = mtfTrendsDeferred.await()
        val patterns = patternsDeferred.await()

        // 6. Pre-kalkulasi batas harga (Min, Max, Range) untuk akselerasi rendering Canvas
        val minP = normalizedCandles.minOfOrNull { it.low } ?: (livePriceRef ?: 3015.0)
        val maxP = normalizedCandles.maxOfOrNull { it.high } ?: (livePriceRef ?: 3015.0)
        val rawRange = max(maxP - minP, if (instrument == TradingInstrument.XAUUSD) 0.60 else 0.00030)
        val padding = rawRange * 0.08
        val minPrice = (minP - padding).coerceAtLeast(0.00001)
        val maxPrice = maxP + padding
        val priceRange = max(maxPrice - minPrice, 0.00001)

        ChartRenderState(
            instrument = instrument,
            timeframe = timeframe,
            candles = normalizedCandles,
            ema9 = ema9,
            ema21 = ema21,
            bollingerBands = bb,
            indicators = indicators,
            detectedPatterns = patterns,
            multiTimeframeTrends = mtfTrends,
            htfCandles = htfCandles,
            minPrice = minPrice,
            maxPrice = maxPrice,
            priceRange = priceRange,
            isCalculating = false,
            calculationTimestamp = System.currentTimeMillis()
        )
    }
}
