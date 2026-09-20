package com.example

import com.example.data.model.*
import com.example.service.IndicatorCalculator
import com.example.service.PatternRecognitionEngine
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testTrendCalculation() {
    // Bullish trend series
    val bullishCandles = (1..30).map { i ->
      val base = 2600.0 + i * 2.0
      Candle(
        timestamp = 1000L + i * 60000L,
        open = base,
        high = base + 1.5,
        low = base - 0.5,
        close = base + 1.2,
        volume = 100.0
      )
    }

    val trend = IndicatorCalculator.calculateTrend(bullishCandles)
    assertEquals(TrendDirection.BULLISH, trend)

    // Bearish trend series
    val bearishCandles = (1..30).map { i ->
      val base = 2700.0 - i * 2.0
      Candle(
        timestamp = 1000L + i * 60000L,
        open = base,
        high = base + 0.5,
        low = base - 1.5,
        close = base - 1.2,
        volume = 100.0
      )
    }

    val bearTrend = IndicatorCalculator.calculateTrend(bearishCandles)
    assertEquals(TrendDirection.BEARISH, bearTrend)
  }

  @Test
  fun testPatternConfluenceGrading() {
    val candles = (1..30).map { i ->
      val base = 2650.0 + (i % 5) * 1.5
      Candle(
        timestamp = 1000L + i * 60000L,
        open = base,
        high = base + 2.0,
        low = base - 1.0,
        close = base + 0.8,
        volume = 120.0
      )
    }

    val htfCandles = (1..30).map { i ->
      val base = 2600.0 + i * 3.0
      Candle(
        timestamp = 1000L + i * 3600000L,
        open = base,
        high = base + 2.5,
        low = base - 0.5,
        close = base + 2.0,
        volume = 500.0
      )
    }

    val patterns = PatternRecognitionEngine.detectAllPatterns(
      candles = candles,
      instrument = TradingInstrument.XAUUSD,
      currentTimeframe = Timeframe.M5,
      htfCandles = htfCandles
    )

    // Verify all detected patterns have valid confluence fields
    for (pattern in patterns) {
      assertNotNull(pattern.confluenceGrade)
      assertTrue(pattern.confluenceScore in 50..100)
      assertNotNull(pattern.htfTrendAlignment)
      assertTrue(pattern.confluenceFactors.isNotEmpty())
      assertTrue(pattern.estimatedRiskReward > 0.0)
    }
  }
}

