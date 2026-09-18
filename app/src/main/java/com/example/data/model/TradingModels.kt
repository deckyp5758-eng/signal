package com.example.data.model

enum class TradingInstrument(
    val symbol: String,
    val displayName: String,
    val description: String,
    val decimalDigits: Int,
    val pipMultiplier: Double,
    val contractSize: Double, // standard lot units: 100 oz for Gold, 100,000 units for EUR
    val defaultPrice: Double,
    val defaultSpreadPips: Double
) {
    XAUUSD(
        symbol = "XAU/USD",
        displayName = "Gold / US Dollar",
        description = "Spot Emas terhadap Dolar AS - Sangat Likuid & Volatil",
        decimalDigits = 2,
        pipMultiplier = 0.1, // 1 pip = 0.10 USD pada Gold
        contractSize = 100.0,
        defaultPrice = 2654.50,
        defaultSpreadPips = 1.2
    ),
    EURUSD(
        symbol = "EUR/USD",
        displayName = "Euro / US Dollar",
        description = "Pasangan Mata Uang Utama Dunia - Spread Terkecil",
        decimalDigits = 5,
        pipMultiplier = 0.0001, // 1 pip = 0.00010 EUR
        contractSize = 100000.0,
        defaultPrice = 1.08425,
        defaultSpreadPips = 0.3
    );

    fun formatPrice(price: Double): String {
        return String.format(java.util.Locale.US, "%.${decimalDigits}f", price)
    }

    fun formatRawNumber(price: Double): String {
        return String.format(java.util.Locale.US, "%.${decimalDigits}f", price)
    }

    fun pipsBetween(price1: Double, price2: Double): Double {
        return kotlin.math.abs(price1 - price2) / pipMultiplier
    }
}

enum class SignalAction(val label: String, val badgeText: String) {
    BUY("Beli", "BUY"),
    SELL("Jual", "SELL"),
    NEUTRAL("Netral / Tunggu", "WAIT")
}

enum class SignalStrength(val label: String, val starCount: Int) {
    STRONG("Sangat Kuat", 3),
    MODERATE("Kuat", 2),
    WEAK("Sedang", 1)
}

enum class Timeframe(val code: String, val label: String, val seconds: Long) {
    M1("M1", "1 Menit (Ultra Scalp)", 60),
    M5("M5", "5 Menit (Standard Scalp)", 300),
    M15("M15", "15 Menit (Trend Scalp)", 900)
}

data class IndicatorValues(
    val rsi: Double,
    val emaFast: Double, // EMA 9
    val emaSlow: Double, // EMA 21
    val macdLine: Double,
    val macdSignal: Double,
    val macdHist: Double,
    val bbUpper: Double,
    val bbMiddle: Double,
    val bbLower: Double,
    val atr: Double
)

data class ScalpSignal(
    val id: String,
    val instrument: TradingInstrument,
    val action: SignalAction,
    val strength: SignalStrength,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double,
    val slPips: Double,
    val tp1Pips: Double,
    val tp2Pips: Double,
    val tp3Pips: Double,
    val riskReward: Double,
    val confidence: Int,
    val reason: String,
    val timestamp: Long,
    val timeframe: Timeframe,
    val indicators: IndicatorValues
)

enum class AccountCurrency(val code: String, val symbol: String, val displayName: String, val defaultRateToUsd: Double) {
    USD("USD", "$", "US Dollar ($)", 1.0),
    IDR("IDR", "Rp", "Rupiah Indonesia (Rp)", 16000.0),
    EUR("EUR", "€", "Euro (€)", 0.92);

    fun formatMoney(amount: Double): String {
        return when (this) {
            IDR -> "Rp ${"%,.0f".format(amount).replace(',', '.')}"
            EUR -> "€${"%,.2f".format(amount)}"
            USD -> "$${"%,.2f".format(amount)}"
        }
    }
}

enum class AccountType(
    val code: String,
    val displayName: String,
    val lotMultiplier: Double,
    val minLot: Double,
    val maxLot: Double,
    val description: String
) {
    STANDARD("Standard", "Akun Standar (1 Lot = 100k unit)", 1.0, 0.01, 50.0, "1 Pip = $10.00 / Lot Standar"),
    CENT("Cent / Micro", "Akun Cent (1 Lot = 1,000 unit)", 0.01, 0.01, 500.0, "1 Pip = $0.10 / Lot Cent (Cocok modal kecil)"),
    MINI("Mini", "Akun Mini (1 Lot = 10,000 unit)", 0.1, 0.01, 100.0, "1 Pip = $1.00 / Lot Mini");
}

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class RiskCalculation(
    val instrument: TradingInstrument,
    val action: SignalAction,
    val accountCurrency: AccountCurrency = AccountCurrency.USD,
    val accountType: AccountType = AccountType.STANDARD,
    val accountBalance: Double,
    val riskPercent: Double,
    val riskAmountCurrency: Double,
    val riskAmountUsd: Double,
    val pipValuePerLotCurrency: Double,
    val pipValuePerLotUsd: Double,
    val lotSize: Double,
    val entryPrice: Double,
    val stopLossPrice: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double,
    val slPips: Double,
    val tp1Pips: Double,
    val tp2Pips: Double,
    val tp3Pips: Double,
    val maxLossCurrency: Double,
    val maxLossUsd: Double,
    val potentialProfit1Currency: Double,
    val potentialProfit1Usd: Double,
    val potentialProfit2Currency: Double,
    val potentialProfit2Usd: Double,
    val potentialProfit3Currency: Double,
    val potentialProfit3Usd: Double,
    val riskRewardRatio: Double
)
