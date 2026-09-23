package com.example.service

import com.example.data.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object PatternRecognitionEngine {

    fun detectAllPatterns(
        candles: List<Candle>,
        instrument: TradingInstrument,
        currentTimeframe: Timeframe = Timeframe.M5,
        htfCandles: List<Candle>? = null
    ): List<DetectedPattern> {
        if (candles.size < 5) return emptyList()

        val rawPatterns = mutableListOf<DetectedPattern>()

        // 1. Detect Candlestick Formations
        rawPatterns.addAll(detectCandlestickPatterns(candles, instrument))

        // 2. Detect Classical Chart Patterns (Double Top/Bottom, H&S, Triangles, Flags)
        rawPatterns.addAll(detectChartPatterns(candles, instrument))

        // 3. Detect Smart Money Concepts (Order Blocks, FVG, BOS, Liquidity Sweeps)
        rawPatterns.addAll(detectSmcPatterns(candles, instrument))

        // Confluence Rating Grade & Multi-Timeframe Trend Analysis
        val enriched = enrichPatternsWithConfluence(rawPatterns, candles, instrument, currentTimeframe, htfCandles)

        // Filter M1 noise: On ultra-fast 1-minute chart, suppress weak B-grade or counter-trend patterns to prevent false breakouts
        val filtered = if (currentTimeframe == Timeframe.M1) {
            enriched.filter { it.confluenceGrade != ConfluenceGrade.B || it.htfTrendAlignment == TrendAlignment.ALIGNED }
        } else {
            enriched
        }

        // Sort by Confluence Grade, Score & Confidence
        return filtered.sortedWith(
            compareByDescending<DetectedPattern> {
                when (it.confluenceGrade) {
                    ConfluenceGrade.A_PLUS -> 3000
                    ConfluenceGrade.A -> 2000
                    ConfluenceGrade.B -> 1000
                } + it.confluenceScore * 10 + it.confidence
            }
        )
    }

    private fun enrichPatternsWithConfluence(
        rawPatterns: List<DetectedPattern>,
        candles: List<Candle>,
        instrument: TradingInstrument,
        currentTimeframe: Timeframe,
        htfCandles: List<Candle>?
    ): List<DetectedPattern> {
        if (candles.isEmpty()) return rawPatterns

        val lastCandle = candles.last()
        val lastPrice = lastCandle.close
        val closes = candles.map { it.close }
        val ema9List = IndicatorCalculator.calculateEMA(closes, 9)
        val ema21List = IndicatorCalculator.calculateEMA(closes, 21)
        val ema21 = ema21List.lastOrNull() ?: lastPrice
        val ema9 = ema9List.lastOrNull() ?: lastPrice
        val rsi = IndicatorCalculator.calculateRSI(closes, 14)
        val atr = IndicatorCalculator.calculateATR(candles, 14)

        // Determine Higher Timeframe Trend
        val htfTrend = if (!htfCandles.isNullOrEmpty() && htfCandles.size >= 5) {
            IndicatorCalculator.calculateTrend(htfCandles)
        } else {
            val ema50List = IndicatorCalculator.calculateEMA(closes, minOf(30, closes.size))
            val ema50 = ema50List.lastOrNull() ?: lastPrice
            when {
                lastPrice > ema50 && ema9 > ema21 -> TrendDirection.BULLISH
                lastPrice < ema50 && ema9 < ema21 -> TrendDirection.BEARISH
                else -> TrendDirection.NEUTRAL
            }
        }

        return rawPatterns.map { pattern ->
            var score = 0
            val factors = mutableListOf<String>()

            // 1. HTF Trend Alignment (+30 pts)
            val alignment: TrendAlignment
            if (pattern.action == SignalAction.BUY) {
                when (htfTrend) {
                    TrendDirection.BULLISH -> {
                        alignment = TrendAlignment.ALIGNED
                        score += 30
                        factors.add("HTF Trend Bullish (${htfTrend.code}) Sejalan")
                    }
                    TrendDirection.BEARISH -> {
                        alignment = TrendAlignment.COUNTER_TREND
                        score += 5
                        factors.add("⚠️ Melawan Tren HTF (${htfTrend.code})")
                    }
                    TrendDirection.NEUTRAL -> {
                        alignment = TrendAlignment.NEUTRAL
                        score += 18
                        factors.add("HTF Konsolidasi / Range Netral")
                    }
                }
            } else if (pattern.action == SignalAction.SELL) {
                when (htfTrend) {
                    TrendDirection.BEARISH -> {
                        alignment = TrendAlignment.ALIGNED
                        score += 30
                        factors.add("HTF Trend Bearish (${htfTrend.code}) Sejalan")
                    }
                    TrendDirection.BULLISH -> {
                        alignment = TrendAlignment.COUNTER_TREND
                        score += 5
                        factors.add("⚠️ Melawan Tren HTF (${htfTrend.code})")
                    }
                    TrendDirection.NEUTRAL -> {
                        alignment = TrendAlignment.NEUTRAL
                        score += 18
                        factors.add("HTF Konsolidasi / Range Netral")
                    }
                }
            } else {
                alignment = TrendAlignment.NEUTRAL
                score += 15
            }

            // 2. Key Level & Dynamic EMA Rejection (+25 pts)
            val refPrice = if (pattern.keyLevelPrice > 0) pattern.keyLevelPrice else lastPrice
            val distToEma21Pips = instrument.pipsBetween(refPrice, ema21)
            val isAtDynamicLevel = distToEma21Pips <= (atr / instrument.pipMultiplier * 0.8)

            if (isAtDynamicLevel) {
                score += 25
                factors.add("Rejection Dynamic Area EMA 21 / Support-Resistance")
            } else if (pattern.keyLevelPrice > 0) {
                score += 20
                factors.add("Rebound Level Kunci (${instrument.formatPrice(pattern.keyLevelPrice)})")
            } else {
                score += 15
                factors.add("Level Struktur Valid")
            }

            // 3. Momentum Confluence: RSI (+20 pts)
            val rsiFormatted = String.format(java.util.Locale.US, "%.1f", rsi)
            if (pattern.action == SignalAction.BUY) {
                if (rsi < 40) {
                    score += 20
                    factors.add("RSI Oversold Bounce (RSI: $rsiFormatted)")
                } else if (rsi in 40.0..60.0) {
                    score += 15
                    factors.add("RSI Momentum Stabil (RSI: $rsiFormatted)")
                } else {
                    score += 10
                    factors.add("RSI Bullish Extension (RSI: $rsiFormatted)")
                }
            } else if (pattern.action == SignalAction.SELL) {
                if (rsi > 60) {
                    score += 20
                    factors.add("RSI Overbought Rejection (RSI: $rsiFormatted)")
                } else if (rsi in 40.0..60.0) {
                    score += 15
                    factors.add("RSI Momentum Stabil (RSI: $rsiFormatted)")
                } else {
                    score += 10
                    factors.add("RSI Bearish Extension (RSI: $rsiFormatted)")
                }
            } else {
                score += 12
            }

            // 4. Pattern Category Confluence (+15 pts)
            when (pattern.category) {
                PatternTypeCategory.SMC -> {
                    score += 15
                    factors.add("Likuiditas Institusional / Imbalance SMC")
                }
                PatternTypeCategory.CHART_PATTERN -> {
                    score += 13
                    factors.add("Formasi Geometris Multi-Candle Terkonfirmasi")
                }
                PatternTypeCategory.CANDLESTICK -> {
                    score += 11
                    factors.add("Konfirmasi Price Action Candlestick")
                }
                PatternTypeCategory.ALL -> {
                    score += 10
                }
            }

            // 5. Risk / Reward Calculation (+10 pts)
            val pipMult = instrument.pipMultiplier
            val slPips = (atr * 1.5 / pipMult).coerceIn(15.0, 45.0)
            val slDistance = slPips * pipMult
            val suggestedSl: Double
            val suggestedTp: Double

            if (pattern.action == SignalAction.BUY) {
                suggestedSl = if (pattern.keyLevelPrice > 0 && pattern.keyLevelPrice < lastPrice) {
                    pattern.keyLevelPrice - (pipMult * 4.0)
                } else {
                    lastPrice - slDistance
                }
                suggestedTp = lastPrice + (abs(lastPrice - suggestedSl) * 2.2)
            } else {
                suggestedSl = if (pattern.keyLevelPrice > 0 && pattern.keyLevelPrice > lastPrice) {
                    pattern.keyLevelPrice + (pipMult * 4.0)
                } else {
                    lastPrice + slDistance
                }
                suggestedTp = lastPrice - (abs(suggestedSl - lastPrice) * 2.2)
            }

            val actualRr = if (abs(lastPrice - suggestedSl) > 0.000001) {
                abs(suggestedTp - lastPrice) / abs(lastPrice - suggestedSl)
            } else 2.0

            if (actualRr >= 2.0) {
                score += 10
                factors.add("Rasio R:R Optimal (1:${String.format(java.util.Locale.US, "%.1f", actualRr)})")
            } else {
                score += 6
                factors.add("Rasio R:R Scalp (1:${String.format(java.util.Locale.US, "%.1f", actualRr)})")
            }

            // Final Clamping & Confluence Grade Mapping
            val finalScore = score.coerceIn(52, 98)
            val grade = when {
                finalScore >= 82 && alignment == TrendAlignment.ALIGNED -> ConfluenceGrade.A_PLUS
                finalScore >= 70 -> ConfluenceGrade.A
                else -> ConfluenceGrade.B
            }

            pattern.copy(
                confluenceGrade = grade,
                confluenceScore = finalScore,
                confluenceFactors = factors,
                htfTrendAlignment = alignment,
                higherTimeframeTrend = htfTrend,
                estimatedRiskReward = actualRr,
                suggestedStopLoss = suggestedSl,
                suggestedTakeProfit = suggestedTp
            )
        }
    }

    // ==========================================
    // 1. CANDLESTICK RECOGNITION
    // ==========================================
    private fun detectCandlestickPatterns(candles: List<Candle>, instrument: TradingInstrument): List<DetectedPattern> {
        val list = mutableListOf<DetectedPattern>()
        val n = candles.size

        for (i in 2 until n) {
            val c0 = candles[i - 2]
            val c1 = candles[i - 1]
            val c2 = candles[i] // current / evaluated candle

            val body2 = abs(c2.close - c2.open)
            val range2 = c2.high - c2.low
            val isBullish2 = c2.close >= c2.open
            val isBearish2 = c2.close < c2.open

            val body1 = abs(c1.close - c1.open)
            val isBullish1 = c1.close >= c1.open
            val isBearish1 = c1.close < c1.open

            val upperWick2 = c2.high - max(c2.open, c2.close)
            val lowerWick2 = min(c2.open, c2.close) - c2.low

            if (range2 <= 0.000001) continue

            // A. Bullish Engulfing
            if (isBearish1 && isBullish2 && c2.open <= c1.close && c2.close > c1.open && body2 > body1 * 1.1) {
                list.add(
                    DetectedPattern(
                        name = "Bullish Engulfing",
                        category = PatternTypeCategory.CANDLESTICK,
                        action = SignalAction.BUY,
                        confidence = 88,
                        description = "Body candle hijau menelan penuh candle merah sebelumnya di area support.",
                        tradingTip = "Tekanan pembeli agresif mendominasi pasar. Target TP di area resistance terdekat.",
                        startCandleIndex = i - 1,
                        endCandleIndex = i,
                        keyLevelPrice = c2.low,
                        upperZonePrice = c2.high,
                        lowerZonePrice = c2.low
                    )
                )
            }

            // B. Bearish Engulfing
            if (isBullish1 && isBearish2 && c2.open >= c1.close && c2.close < c1.open && body2 > body1 * 1.1) {
                list.add(
                    DetectedPattern(
                        name = "Bearish Engulfing",
                        category = PatternTypeCategory.CANDLESTICK,
                        action = SignalAction.SELL,
                        confidence = 88,
                        description = "Body candle merah menelan penuh candle hijau sebelumnya di area resistance.",
                        tradingTip = "Tekanan penjual agresif membanting harga. Pasang SL di atas jarum candle.",
                        startCandleIndex = i - 1,
                        endCandleIndex = i,
                        keyLevelPrice = c2.high,
                        upperZonePrice = c2.high,
                        lowerZonePrice = c2.low
                    )
                )
            }

            // C. Hammer / Bullish Pinbar (Price Rejection)
            if (lowerWick2 >= body2 * 2.0 && upperWick2 <= range2 * 0.2 && isBullish2) {
                list.add(
                    DetectedPattern(
                        name = "Hammer (Bullish Pinbar)",
                        category = PatternTypeCategory.CANDLESTICK,
                        action = SignalAction.BUY,
                        confidence = 85,
                        description = "Ekor bawah panjang menunjukkan penolakan harga keras dari pihak pembeli.",
                        tradingTip = "Konfirmasi pantulan harga dari level support. Entry buy dengan SL di bawah ekor jarum.",
                        startCandleIndex = i,
                        endCandleIndex = i,
                        keyLevelPrice = c2.low,
                        upperZonePrice = c2.high,
                        lowerZonePrice = c2.low
                    )
                )
            }

            // D. Shooting Star / Bearish Pinbar
            if (upperWick2 >= body2 * 2.0 && lowerWick2 <= range2 * 0.2 && isBearish2) {
                list.add(
                    DetectedPattern(
                        name = "Shooting Star (Bearish Pinbar)",
                        category = PatternTypeCategory.CANDLESTICK,
                        action = SignalAction.SELL,
                        confidence = 85,
                        description = "Ekor atas panjang menunjukkan penolakan kuat dari pihak seller di level resistance.",
                        tradingTip = "Pembalikan arah turun. Target sell scalping dengan rasio Risk/Reward 1:2.",
                        startCandleIndex = i,
                        endCandleIndex = i,
                        keyLevelPrice = c2.high,
                        upperZonePrice = c2.high,
                        lowerZonePrice = c2.low
                    )
                )
            }

            // E. Morning Star (3 Candles Reversal)
            val isBearish0 = c0.close < c0.open
            if (isBearish0 && body1 < abs(c0.close - c0.open) * 0.4 && isBullish2 && c2.close > (c0.open + c0.close) / 2) {
                list.add(
                    DetectedPattern(
                        name = "Morning Star",
                        category = PatternTypeCategory.CANDLESTICK,
                        action = SignalAction.BUY,
                        confidence = 90,
                        description = "Formasi 3 candle klasik pembalikan bullish dari dasar tren turun.",
                        tradingTip = "Sinyal pembalikan arah kuat. Entry buy setelah penutupan candle ketiga.",
                        startCandleIndex = i - 2,
                        endCandleIndex = i,
                        keyLevelPrice = minOf(c0.low, c1.low, c2.low),
                        upperZonePrice = maxOf(c0.high, c1.high, c2.high),
                        lowerZonePrice = minOf(c0.low, c1.low, c2.low)
                    )
                )
            }

            // F. Evening Star (3 Candles Reversal)
            val isBullish0 = c0.close >= c0.open
            if (isBullish0 && body1 < abs(c0.close - c0.open) * 0.4 && isBearish2 && c2.close < (c0.open + c0.close) / 2) {
                list.add(
                    DetectedPattern(
                        name = "Evening Star",
                        category = PatternTypeCategory.CANDLESTICK,
                        action = SignalAction.SELL,
                        confidence = 90,
                        description = "Formasi 3 candle klasik pembalikan bearish dari puncak reli harga.",
                        tradingTip = "Pembalikan arah turun valid. Entry sell saat candle merah tertutup kuat.",
                        startCandleIndex = i - 2,
                        endCandleIndex = i,
                        keyLevelPrice = maxOf(c0.high, c1.high, c2.high),
                        upperZonePrice = maxOf(c0.high, c1.high, c2.high),
                        lowerZonePrice = minOf(c0.low, c1.low, c2.low)
                    )
                )
            }

            // G. Doji (Indecision / Pivot Point)
            if (body2 <= range2 * 0.12 && range2 > 0) {
                val dojiType = when {
                    lowerWick2 > range2 * 0.65 -> "Dragonfly Doji (Bullish Rejection)"
                    upperWick2 > range2 * 0.65 -> "Gravestone Doji (Bearish Rejection)"
                    else -> "Standard Doji (Market Indecision)"
                }
                list.add(
                    DetectedPattern(
                        name = dojiType,
                        category = PatternTypeCategory.CANDLESTICK,
                        action = if (lowerWick2 > upperWick2) SignalAction.BUY else if (upperWick2 > lowerWick2) SignalAction.SELL else SignalAction.NEUTRAL,
                        confidence = 78,
                        description = "Kekuatan buyer dan seller seimbang sempurna, mendahului ledakan volatilitas.",
                        tradingTip = "Tunggu candle berikutnya sebagai konfirmasi arah breakout.",
                        startCandleIndex = i,
                        endCandleIndex = i,
                        keyLevelPrice = c2.close,
                        upperZonePrice = c2.high,
                        lowerZonePrice = c2.low
                    )
                )
            }
        }

        // Return up to 4 most recent distinct candlestick patterns
        return list.takeLast(4)
    }

    // ==========================================
    // 2. CHART PATTERNS (W, M, H&S, Triangles, Flags)
    // ==========================================
    private fun detectChartPatterns(candles: List<Candle>, instrument: TradingInstrument): List<DetectedPattern> {
        val list = mutableListOf<DetectedPattern>()
        val n = candles.size
        if (n < 15) return emptyList()

        // Find swing highs and swing lows (Fractals / Peaks & Valleys)
        val swingHighs = mutableListOf<Pair<Int, Double>>()
        val swingLows = mutableListOf<Pair<Int, Double>>()

        for (i in 2 until n - 2) {
            val h = candles[i].high
            if (h >= candles[i - 1].high && h >= candles[i - 2].high && h >= candles[i + 1].high && h >= candles[i + 2].high) {
                swingHighs.add(Pair(i, h))
            }

            val l = candles[i].low
            if (l <= candles[i - 1].low && l <= candles[i - 2].low && l <= candles[i + 1].low && l <= candles[i + 2].low) {
                swingLows.add(Pair(i, l))
            }
        }

        val lastPrice = candles.last().close

        // A. Double Bottom (W Pattern)
        if (swingLows.size >= 2) {
            val (idx1, low1) = swingLows[swingLows.size - 2]
            val (idx2, low2) = swingLows.last()

            val diffRatio = abs(low1 - low2) / ((low1 + low2) / 2.0)
            if (diffRatio < 0.0035 && idx2 - idx1 in 4..25) {
                // Find central peak between idx1 and idx2
                var peakPrice = Double.MIN_VALUE
                var peakIdx = idx1
                for (k in idx1..idx2) {
                    if (candles[k].high > peakPrice) {
                        peakPrice = candles[k].high
                        peakIdx = k
                    }
                }

                if (peakPrice > max(low1, low2)) {
                    val isBreakout = lastPrice >= peakPrice
                    list.add(
                        DetectedPattern(
                            name = "Double Bottom (Pola W)",
                            category = PatternTypeCategory.CHART_PATTERN,
                            action = SignalAction.BUY,
                            confidence = if (isBreakout) 92 else 84,
                            description = "Dua lembah horizontal menguji support kembar. Pola pembalikan arah naik yang sangat diandalkan.",
                            tradingTip = if (isBreakout) "Breakout Neckline terkonfirmasi! Potensi kenaikan setinggi jarak dasar ke puncak." else "Tunggu konfirmasi penembusan Neckline di level ${instrument.formatPrice(peakPrice)}.",
                            startCandleIndex = idx1,
                            endCandleIndex = n - 1,
                            keyLevelPrice = min(low1, low2),
                            upperZonePrice = peakPrice,
                            lowerZonePrice = min(low1, low2),
                            necklinePrice = peakPrice,
                            swingPoints = listOf(
                                PointCoord(idx1, low1),
                                PointCoord(peakIdx, peakPrice),
                                PointCoord(idx2, low2),
                                PointCoord(n - 1, lastPrice)
                            ),
                            isBreakoutActive = isBreakout
                        )
                    )
                }
            }
        }

        // B. Double Top (M Pattern)
        if (swingHighs.size >= 2) {
            val (idx1, high1) = swingHighs[swingHighs.size - 2]
            val (idx2, high2) = swingHighs.last()

            val diffRatio = abs(high1 - high2) / ((high1 + high2) / 2.0)
            if (diffRatio < 0.0035 && idx2 - idx1 in 4..25) {
                // Find central valley between idx1 and idx2
                var valleyPrice = Double.MAX_VALUE
                var valleyIdx = idx1
                for (k in idx1..idx2) {
                    if (candles[k].low < valleyPrice) {
                        valleyPrice = candles[k].low
                        valleyIdx = k
                    }
                }

                if (valleyPrice < min(high1, high2)) {
                    val isBreakout = lastPrice <= valleyPrice
                    list.add(
                        DetectedPattern(
                            name = "Double Top (Pola M)",
                            category = PatternTypeCategory.CHART_PATTERN,
                            action = SignalAction.SELL,
                            confidence = if (isBreakout) 92 else 84,
                            description = "Dua puncak horizontal gagal menembus resistance kembar. Pola pembalikan arah turun favorit.",
                            tradingTip = if (isBreakout) "Breakdown Neckline terkonfirmasi! Buka posisi Sell dengan target ke bawah." else "Tunggu penembusan Neckline di level ${instrument.formatPrice(valleyPrice)}.",
                            startCandleIndex = idx1,
                            endCandleIndex = n - 1,
                            keyLevelPrice = max(high1, high2),
                            upperZonePrice = max(high1, high2),
                            lowerZonePrice = valleyPrice,
                            necklinePrice = valleyPrice,
                            swingPoints = listOf(
                                PointCoord(idx1, high1),
                                PointCoord(valleyIdx, valleyPrice),
                                PointCoord(idx2, high2),
                                PointCoord(n - 1, lastPrice)
                            ),
                            isBreakoutActive = isBreakout
                        )
                    )
                }
            }
        }

        // C. Head and Shoulders (H&S) / Inverse H&S
        if (swingHighs.size >= 3) {
            val (i1, left) = swingHighs[swingHighs.size - 3]
            val (i2, head) = swingHighs[swingHighs.size - 2]
            val (i3, right) = swingHighs.last()

            if (head > left && head > right && abs(left - right) / ((left + right) / 2.0) < 0.005 && i3 - i1 in 8..35) {
                // Find neckline
                var neck = Double.MAX_VALUE
                for (k in i1..i3) {
                    neck = min(neck, candles[k].low)
                }
                list.add(
                    DetectedPattern(
                        name = "Head & Shoulders",
                        category = PatternTypeCategory.CHART_PATTERN,
                        action = SignalAction.SELL,
                        confidence = 94,
                        description = "Formasi Bahu Kiri, Kepala, dan Bahu Kanan di puncak tren. Akurasi pembalikan tertinggi.",
                        tradingTip = "Eksekusi sell saat harga menembus ke bawah Neckline (${instrument.formatPrice(neck)}).",
                        startCandleIndex = i1,
                        endCandleIndex = n - 1,
                        keyLevelPrice = head,
                        upperZonePrice = head,
                        lowerZonePrice = neck,
                        necklinePrice = neck,
                        swingPoints = listOf(
                            PointCoord(i1, left),
                            PointCoord(i2, head),
                            PointCoord(i3, right),
                            PointCoord(n - 1, lastPrice)
                        )
                    )
                )
            }
        }

        // D. Bullish Flag & Consolidation Channel
        if (n >= 12) {
            val poleStart = candles[n - 12]
            val poleEnd = candles[n - 6]
            val flagEnd = candles.last()

            val poleGain = poleEnd.close - poleStart.open
            val flagRetrace = poleEnd.high - flagEnd.close

            if (poleGain > 0 && flagRetrace in (0.2 * poleGain)..(0.6 * poleGain)) {
                list.add(
                    DetectedPattern(
                        name = "Bullish Flag (Bendera Naik)",
                        category = PatternTypeCategory.CHART_PATTERN,
                        action = SignalAction.BUY,
                        confidence = 86,
                        description = "Konsolidasi sehat miring ke bawah setelah ledakan reli tiang (Pole) bullish.",
                        tradingTip = "Pola kelanjutan tren naik. Siap entry buy saat harga menembus batas atas bendera.",
                        startCandleIndex = n - 12,
                        endCandleIndex = n - 1,
                        keyLevelPrice = poleEnd.high,
                        upperZonePrice = poleEnd.high,
                        lowerZonePrice = poleStart.low,
                        swingPoints = listOf(
                            PointCoord(n - 12, poleStart.open),
                            PointCoord(n - 6, poleEnd.high),
                            PointCoord(n - 1, flagEnd.close)
                        )
                    )
                )
            }
        }

        return list.take(3)
    }

    // ==========================================
    // 3. SMART MONEY CONCEPTS (OB, FVG, BOS, SWEEP)
    // ==========================================
    private fun detectSmcPatterns(candles: List<Candle>, instrument: TradingInstrument): List<DetectedPattern> {
        val list = mutableListOf<DetectedPattern>()
        val n = candles.size
        if (n < 6) return emptyList()

        // A. Fair Value Gap (FVG / Imbalance)
        for (i in 2 until n) {
            val c0 = candles[i - 2]
            val c1 = candles[i - 1]
            val c2 = candles[i]

            // Bullish FVG: Gap between C0 High and C2 Low
            if (c2.low > c0.high && (c2.low - c0.high) >= instrument.pipMultiplier * 1.5) {
                list.add(
                    DetectedPattern(
                        name = "Bullish Fair Value Gap (FVG)",
                        category = PatternTypeCategory.SMC,
                        action = SignalAction.BUY,
                        confidence = 89,
                        description = "Area ketidakseimbangan (Imbalance) pembeli institusi yang meninggalkan celah likuiditas.",
                        tradingTip = "Pasar cenderung kembali turun untuk mengisi area celah ini sebelum memantul naik drastis.",
                        startCandleIndex = i - 2,
                        endCandleIndex = i,
                        keyLevelPrice = (c0.high + c2.low) / 2.0,
                        upperZonePrice = c2.low,
                        lowerZonePrice = c0.high
                    )
                )
            }

            // Bearish FVG: Gap between C0 Low and C2 High
            if (c0.low > c2.high && (c0.low - c2.high) >= instrument.pipMultiplier * 1.5) {
                list.add(
                    DetectedPattern(
                        name = "Bearish Fair Value Gap (FVG)",
                        category = PatternTypeCategory.SMC,
                        action = SignalAction.SELL,
                        confidence = 89,
                        description = "Celah lompatan harga seller institusi (Bearish Imbalance).",
                        tradingTip = "Tunggu harga retrace ke dalam area FVG merah ini untuk mendapatkan entry Sell optimal.",
                        startCandleIndex = i - 2,
                        endCandleIndex = i,
                        keyLevelPrice = (c0.low + c2.high) / 2.0,
                        upperZonePrice = c0.low,
                        lowerZonePrice = c2.high
                    )
                )
            }
        }

        // B. Bullish Order Block (Demand OB)
        for (i in 3 until n - 1) {
            val downCandle = candles[i - 2]
            val imp1 = candles[i - 1]
            val imp2 = candles[i]

            // If downCandle is bearish followed by 2 strong bullish candles breaking highs
            if (downCandle.close < downCandle.open && imp1.close > imp1.open && imp2.close > imp2.open && imp2.close > downCandle.high) {
                list.add(
                    DetectedPattern(
                        name = "Bullish Order Block (Demand OB)",
                        category = PatternTypeCategory.SMC,
                        action = SignalAction.BUY,
                        confidence = 91,
                        description = "Zona jejak pembelian institusi perbankan sebelum terjadinya reli harga tajam.",
                        tradingTip = "Tandai kotak hijau ini sebagai zona beli utama saat harga mengalami diskon (Pullback).",
                        startCandleIndex = i - 2,
                        endCandleIndex = n - 1,
                        keyLevelPrice = downCandle.open,
                        upperZonePrice = downCandle.high,
                        lowerZonePrice = downCandle.low
                    )
                )
                break
            }
        }

        // C. Bearish Order Block (Supply OB)
        for (i in 3 until n - 1) {
            val upCandle = candles[i - 2]
            val imp1 = candles[i - 1]
            val imp2 = candles[i]

            // If upCandle is bullish followed by 2 strong bearish candles breaking lows
            if (upCandle.close > upCandle.open && imp1.close < imp1.open && imp2.close < imp2.open && imp2.close < upCandle.low) {
                list.add(
                    DetectedPattern(
                        name = "Bearish Order Block (Supply OB)",
                        category = PatternTypeCategory.SMC,
                        action = SignalAction.SELL,
                        confidence = 91,
                        description = "Zona benteng pertahanan seller institusi sebelum terjadinya penurunan besar.",
                        tradingTip = "Zona penolakan kuat. Siapkan pending order Sell Limit di dalam kotak merah ini.",
                        startCandleIndex = i - 2,
                        endCandleIndex = n - 1,
                        keyLevelPrice = upCandle.open,
                        upperZonePrice = upCandle.high,
                        lowerZonePrice = upCandle.low
                    )
                )
                break
            }
        }

        // D. Break of Structure (BOS)
        if (n >= 10) {
            val priorHigh = candles.take(n - 3).maxOf { it.high }
            val recentClose = candles.last().close
            if (recentClose > priorHigh) {
                list.add(
                    DetectedPattern(
                        name = "Break of Structure (BOS Bullish)",
                        category = PatternTypeCategory.SMC,
                        action = SignalAction.BUY,
                        confidence = 93,
                        description = "Harga berhasil menembus struktur resistance tertinggi sebelumnya (Higher High).",
                        tradingTip = "Tren naik berlanjut secara resmi. Cari peluang beli saat retest.",
                        startCandleIndex = n - 8,
                        endCandleIndex = n - 1,
                        keyLevelPrice = priorHigh,
                        upperZonePrice = recentClose,
                        lowerZonePrice = priorHigh,
                        isBreakoutActive = true
                    )
                )
            }
        }

        return list.takeLast(3)
    }
}
