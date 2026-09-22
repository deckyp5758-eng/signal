package com.example.config

import com.example.data.model.TradingInstrument

object AppConfig {
    const val APP_NAME = "ScalpSignal Pro"
    const val APP_VERSION = "2.4.0"
    const val BUILD_NUMBER = 240

    // Primary API Endpoints
    const val GOLD_API_URL = "https://api.gold-api.com/price/XAU"
    val YAHOO_HOSTS = listOf(
        "https://query1.finance.yahoo.com",
        "https://query2.finance.yahoo.com"
    )

    // Polling Intervals
    const val FOREGROUND_POLL_DELAY_MS = 1200L
    const val BACKGROUND_POLL_DELAY_MS = 12000L
    const val HISTORICAL_CANDLE_SYNC_TICKS = 15

    // Default Risk & Broker Settings
    const val DEFAULT_RISK_PCT = 1.5
    const val DEFAULT_IDR_RATE = 16000.0
    const val FIXED_RATE_IDR = 10000.0
    const val DEFAULT_XAU_SL_PIPS = 20.0
    const val DEFAULT_EUR_SL_PIPS = 15.0

    // GitHub Auto-Update Defaults
    const val DEFAULT_GH_OWNER = "kingdomedantech"
    const val DEFAULT_GH_REPO = "scalpsignal-app"

    // Diagnostics Configuration
    const val MAX_DIAGNOSTIC_LOGS = 100
}
