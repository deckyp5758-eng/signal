package com.example.service

import com.example.data.model.Candle
import com.example.data.model.IndicatorValues
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object IndicatorCalculator {

    fun calculateIndicators(candles: List<Candle>): IndicatorValues {
        if (candles.isEmpty()) {
            return IndicatorValues(
                rsi = 50.0,
                emaFast = 0.0,
                emaSlow = 0.0,
                macdLine = 0.0,
                macdSignal = 0.0,
                macdHist = 0.0,
                bbUpper = 0.0,
                bbMiddle = 0.0,
                bbLower = 0.0,
                atr = 0.0
            )
        }

        val closes = candles.map { it.close }
        val emaFast = calculateEMA(closes, 9).lastOrNull() ?: closes.last()
        val emaSlow = calculateEMA(closes, 21).lastOrNull() ?: closes.last()

        val rsi = calculateRSI(closes, 14)

        // MACD (12, 26, 9)
        val ema12List = calculateEMA(closes, 12)
        val ema26List = calculateEMA(closes, 26)
        val macdLineList = mutableListOf<Double>()
        val minSize = minOf(ema12List.size, ema26List.size)
        for (i in 0 until minSize) {
            macdLineList.add(ema12List[i] - ema26List[i])
        }
        val macdSignalList = calculateEMA(macdLineList, 9)
        val macdLine = macdLineList.lastOrNull() ?: 0.0
        val macdSignal = macdSignalList.lastOrNull() ?: 0.0
        val macdHist = macdLine - macdSignal

        // Bollinger Bands (20, 2)
        val bb = calculateBollingerBands(closes, 20, 2.0)

        // ATR (14)
        val atr = calculateATR(candles, 14)

        return IndicatorValues(
            rsi = rsi,
            emaFast = emaFast,
            emaSlow = emaSlow,
            macdLine = macdLine,
            macdSignal = macdSignal,
            macdHist = macdHist,
            bbUpper = bb.upper,
            bbMiddle = bb.middle,
            bbLower = bb.lower,
            atr = atr
        )
    }

    fun calculateEMA(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        if (values.size < period) {
            val avg = values.average()
            return List(values.size) { avg }
        }

        val k = 2.0 / (period + 1.0)
        val result = mutableListOf<Double>()

        // Initial SMA for first period
        var prevEma = values.subList(0, period).average()
        for (i in 0 until period - 1) {
            result.add(prevEma)
        }
        result.add(prevEma)

        for (i in period until values.size) {
            val currentEma = (values[i] * k) + (prevEma * (1.0 - k))
            result.add(currentEma)
            prevEma = currentEma
        }

        return result
    }

    fun calculateRSI(closes: List<Double>, period: Int = 14): Double {
        if (closes.size <= period) return 50.0

        var gains = 0.0
        var losses = 0.0

        // First average gain/loss
        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change >= 0) gains += change else losses += abs(change)
        }

        var avgGain = gains / period
        var avgLoss = losses / period

        // Smoothed RSI
        for (i in (period + 1) until closes.size) {
            val change = closes[i] - closes[i - 1]
            val currentGain = if (change >= 0) change else 0.0
            val currentLoss = if (change < 0) abs(change) else 0.0

            avgGain = ((avgGain * (period - 1)) + currentGain) / period
            avgLoss = ((avgLoss * (period - 1)) + currentLoss) / period
        }

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        val rsi = 100.0 - (100.0 / (1.0 + rs))
        return rsi.coerceIn(0.0, 100.0)
    }

    data class BollingerResult(val upper: Double, val middle: Double, val lower: Double)

    fun calculateBollingerBands(closes: List<Double>, period: Int = 20, multiplier: Double = 2.0): BollingerResult {
        if (closes.size < period) {
            val mean = closes.average()
            return BollingerResult(mean * 1.01, mean, mean * 0.99)
        }

        val slice = closes.takeLast(period)
        val sma = slice.average()
        val variance = slice.map { (it - sma) * (it - sma) }.average()
        val stdDev = sqrt(variance)

        return BollingerResult(
            upper = sma + (multiplier * stdDev),
            middle = sma,
            lower = sma - (multiplier * stdDev)
        )
    }

    fun calculateATR(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size < 2) return 0.5

        val trList = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val current = candles[i]
            val prev = candles[i - 1]
            val hl = current.high - current.low
            val hc = abs(current.high - prev.close)
            val lc = abs(current.low - prev.close)
            trList.add(max(hl, max(hc, lc)))
        }

        if (trList.isEmpty()) return 0.5
        val p = minOf(period, trList.size)
        return trList.takeLast(p).average()
    }

    /**
     * Determines trend direction for a given series of candles using EMA 9, EMA 21, and Price Action.
     */
    fun calculateTrend(candles: List<Candle>): com.example.data.model.TrendDirection {
        if (candles.size < 5) return com.example.data.model.TrendDirection.NEUTRAL

        val closes = candles.map { it.close }
        val ema9List = calculateEMA(closes, 9)
        val ema21List = calculateEMA(closes, 21)

        val lastClose = closes.last()
        val lastEma9 = ema9List.lastOrNull() ?: lastClose
        val lastEma21 = ema21List.lastOrNull() ?: lastClose

        // Momentum slope over last 3 bars
        val slope = if (closes.size >= 4) (closes.last() - closes[closes.size - 4]) else 0.0

        return when {
            lastClose > lastEma21 && lastEma9 >= lastEma21 && slope >= 0 -> com.example.data.model.TrendDirection.BULLISH
            lastClose < lastEma21 && lastEma9 <= lastEma21 && slope <= 0 -> com.example.data.model.TrendDirection.BEARISH
            lastClose > lastEma21 && lastEma9 > lastEma21 -> com.example.data.model.TrendDirection.BULLISH
            lastClose < lastEma21 && lastEma9 < lastEma21 -> com.example.data.model.TrendDirection.BEARISH
            else -> com.example.data.model.TrendDirection.NEUTRAL
        }
    }
}

