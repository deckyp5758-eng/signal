package com.example.data.model

import java.text.SimpleDateFormat
import java.util.*

enum class NewsImpact(val label: String, val level: Int) {
    HIGH("Tinggi (High Impact)", 3),
    MEDIUM("Sedang (Medium Impact)", 2),
    LOW("Rendah (Low Impact)", 1)
}

enum class NewsCurrency(val code: String, val flag: String, val countryName: String) {
    USD("USD", "🇺🇸", "Amerika Serikat"),
    EUR("EUR", "🇪🇺", "Uni Eropa");
}

data class EconomicEvent(
    val id: String,
    val title: String,
    val currency: NewsCurrency,
    val impact: NewsImpact,
    val timestamp: Long,
    val forecast: String = "-",
    val previous: String = "-",
    val actual: String = "-",
    val affectedInstruments: List<TradingInstrument>,
    val marketEffect: String,
    val scalperAdvice: String
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale("id", "ID"))
        return sdf.format(Date(timestamp))
    }

    fun getTimeRemainingMinutes(): Long {
        val diff = timestamp - System.currentTimeMillis()
        return diff / (60 * 1000)
    }

    fun getStatusLabel(): String {
        val diffMinutes = getTimeRemainingMinutes()
        return when {
            diffMinutes > 60 * 24 -> "Dalam ${(diffMinutes / (60 * 24))} hari"
            diffMinutes > 60 -> "Dalam ${(diffMinutes / 60)} jam ${(diffMinutes % 60)} mnt"
            diffMinutes in 1..60 -> "Rilis dalam $diffMinutes menit"
            diffMinutes in -15..0 -> "⚠️ RILIS SEKARANG (Volatilitas Ekstrem)"
            diffMinutes in -60..-16 -> "Dirilis ${-diffMinutes} mnt lalu"
            else -> "Selesai"
        }
    }

    fun isHighImpactNear(thresholdMinutes: Long = 30): Boolean {
        if (impact != NewsImpact.HIGH) return false
        val diff = getTimeRemainingMinutes()
        return diff in -15..thresholdMinutes
    }
}

data class NewsShieldStatus(
    val isShieldActive: Boolean,
    val currentEvent: EconomicEvent?,
    val minutesUntil: Long,
    val warningMessage: String
)
