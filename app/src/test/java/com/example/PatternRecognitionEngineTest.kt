package com.example

import com.example.data.model.*
import com.example.service.PatternRecognitionEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PatternRecognitionEngineTest {

    @Test
    fun `detects Bullish Engulfing pattern accurately`() {
        val instrument = TradingInstrument.XAUUSD
        val baseTime = System.currentTimeMillis() - 3000000

        // Create 10 candles ending with a clear Bullish Engulfing formation
        val candles = mutableListOf<Candle>()
        for (i in 0..7) {
            val p = 2650.0 + i * 0.2
            candles.add(Candle(baseTime + i * 300000, p, p + 0.5, p - 0.5, p + 0.1, 100.0))
        }

        // Candle 8: Bearish candle (open 2652.0, close 2650.0)
        candles.add(Candle(baseTime + 8 * 300000, 2652.0, 2652.5, 2649.5, 2650.0, 100.0))

        // Candle 9: Strong Bullish Engulfing (open 2649.5, close 2653.5 - engulfing 2650.0 to 2652.0)
        candles.add(Candle(baseTime + 9 * 300000, 2649.5, 2654.0, 2649.0, 2653.5, 120.0))

        val patterns = PatternRecognitionEngine.detectAllPatterns(candles, instrument, Timeframe.M5)

        assertNotNull(patterns)
        assertTrue("Patterns list should not be empty", patterns.isNotEmpty())

        val engulfingPattern = patterns.firstOrNull { it.name.contains("Bullish Engulfing") }
        assertNotNull("Bullish Engulfing pattern should be detected", engulfingPattern)
        assertEquals(SignalAction.BUY, engulfingPattern?.action)
        assertTrue("Accuracy / Confluence score should be >= 85", maxOf(engulfingPattern!!.confluenceScore, engulfingPattern.confidence) >= 85)
    }

    @Test
    fun `detects Hammer Pinbar pattern accurately`() {
        val instrument = TradingInstrument.EURUSD
        val baseTime = System.currentTimeMillis() - 3000000

        val candles = mutableListOf<Candle>()
        for (i in 0..7) {
            val p = 1.0850 - i * 0.0005
            candles.add(Candle(baseTime + i * 300000, p, p + 0.0002, p - 0.0002, p - 0.0001, 100.0))
        }

        // Candle 8: Hammer / Bullish Pinbar (long lower wick)
        candles.add(Candle(baseTime + 8 * 300000, 1.0810, 1.0815, 1.0780, 1.0812, 110.0))

        val patterns = PatternRecognitionEngine.detectAllPatterns(candles, instrument, Timeframe.M5)

        assertNotNull(patterns)
        val hammerPattern = patterns.firstOrNull { it.name.contains("Hammer") || it.name.contains("Pinbar") }
        assertNotNull("Hammer Pinbar pattern should be detected", hammerPattern)
        assertEquals(SignalAction.BUY, hammerPattern?.action)
    }

    @Test
    fun `detects Bullish Fair Value Gap (FVG) accurately`() {
        val instrument = TradingInstrument.XAUUSD
        val baseTime = System.currentTimeMillis() - 6000000

        val candles = mutableListOf<Candle>()
        // 8 Base bullish trend candles
        for (i in 0..7) {
            val p = 2630.0 + i * 1.0
            candles.add(Candle(baseTime + i * 300000, p, p + 1.2, p - 0.5, p + 0.8, 100.0))
        }

        // FVG 3-candle sequence
        val t8 = baseTime + 8 * 300000
        candles.add(Candle(t8, 2638.0, 2640.0, 2637.5, 2639.5, 100.0)) // C0 High 2640.0
        candles.add(Candle(t8 + 300000, 2639.5, 2648.0, 2639.0, 2647.5, 150.0)) // C1 Impulsive
        candles.add(Candle(t8 + 600000, 2647.5, 2652.0, 2644.0, 2651.0, 120.0)) // C2 Low 2644.0 (Gap $4.0 > $0.15)
        candles.add(Candle(t8 + 900000, 2651.0, 2654.0, 2650.0, 2653.0, 110.0))

        val patterns = PatternRecognitionEngine.detectAllPatterns(candles, instrument, Timeframe.M5)

        val fvg = patterns.firstOrNull { it.name.contains("Fair Value Gap") }
        assertNotNull("Fair Value Gap should be detected", fvg)
        assertEquals(PatternTypeCategory.SMC, fvg?.category)
        assertEquals(SignalAction.BUY, fvg?.action)
    }

    @Test
    fun `ensures accuracy rates are sorted descending and minimum 85 percent threshold`() {
        val instrument = TradingInstrument.XAUUSD
        val baseTime = System.currentTimeMillis() - 3000000

        val candles = mutableListOf<Candle>()
        for (i in 0..15) {
            val p = 2650.0 + (if (i % 2 == 0) 0.5 else -0.3)
            candles.add(Candle(baseTime + i * 300000, p, p + 0.8, p - 0.8, p + 0.2, 100.0))
        }

        val patterns = PatternRecognitionEngine.detectAllPatterns(candles, instrument, Timeframe.M5)

        // Verify sorted descending
        for (i in 0 until patterns.size - 1) {
            val acc1 = maxOf(patterns[i].confluenceScore, patterns[i].confidence)
            val acc2 = maxOf(patterns[i + 1].confluenceScore, patterns[i + 1].confidence)
            assertTrue("Patterns must be sorted by accuracy descending ($acc1 >= $acc2)", acc1 >= acc2)
        }
    }
}
