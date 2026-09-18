package com.example.service

import com.example.data.model.Candle
import com.example.data.model.Timeframe
import com.example.data.model.TradingInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LiveQuote(
    val xauPrice: Double,
    val eurPrice: Double,
    val timestamp: Long,
    val isLiveOnline: Boolean,
    val latencyMs: Long = 0L
)

enum class MarketSession(val label: String, val description: String) {
    ASIAN("Sesi Asia / Tokyo", "Volatilitas Rendah - Sedang"),
    LONDON("Sesi London", "Likuiditas Tinggi (Optimal Scalping)"),
    NEW_YORK("Sesi New York", "Volatilitas Tinggi (Overlap London)"),
    WEEKEND("Akhir Pekan", "Pasar Forex Interbank Tutup")
}

object MarketSessionHelper {
    fun getCurrentSession(): MarketSession {
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT"))
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)

        // Weekend: Saturday full day, Sunday before 21:00 GMT
        if (dayOfWeek == java.util.Calendar.SATURDAY || (dayOfWeek == java.util.Calendar.SUNDAY && hour < 21)) {
            return MarketSession.WEEKEND
        }

        return when (hour) {
            in 8..12 -> MarketSession.LONDON
            in 13..16 -> MarketSession.NEW_YORK // London-NY Overlap
            in 17..21 -> MarketSession.NEW_YORK
            else -> MarketSession.ASIAN
        }
    }
}

class LiveMarketService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    // Redundant mirror endpoints to ensure high availability across all networks and regions
    private val endpointHosts = listOf(
        "https://data-api.binance.vision",
        "https://api.binance.com",
        "https://api1.binance.com",
        "https://api2.binance.com",
        "https://api3.binance.com"
    )

    suspend fun fetchLivePrices(): LiveQuote? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        for (host in endpointHosts) {
            try {
                // Fetch live prices for PAXGUSDT (Gold spot proxy) and EURUSDT (Euro spot)
                val url = "$host/api/v3/ticker/price?symbols=%5B%22PAXGUSDT%22,%22EURUSDT%22%5D"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "ScalpSignal/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyString = response.body?.string() ?: return@use

                    val jsonArray = JSONArray(bodyString)
                    var xau: Double? = null
                    var eur: Double? = null

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val symbol = item.getString("symbol")
                        val price = item.getString("price").toDoubleOrNull()

                        if (symbol == "PAXGUSDT") {
                            xau = price
                        } else if (symbol == "EURUSDT") {
                            eur = price
                        }
                    }

                    if (xau != null && eur != null) {
                        val latency = System.currentTimeMillis() - startTime
                        return@withContext LiveQuote(
                            xauPrice = xau,
                            eurPrice = eur,
                            timestamp = System.currentTimeMillis(),
                            isLiveOnline = true,
                            latencyMs = latency.coerceAtLeast(5L)
                        )
                    }
                }
            } catch (_: Exception) {
                // Try next mirror host
            }
        }
        return@withContext null
    }

    suspend fun fetchLiveCandles(instrument: TradingInstrument, timeframe: Timeframe): List<Candle>? = withContext(Dispatchers.IO) {
        val symbol = if (instrument == TradingInstrument.XAUUSD) "PAXGUSDT" else "EURUSDT"
        val interval = when (timeframe) {
            Timeframe.M1 -> "1m"
            Timeframe.M5 -> "5m"
            Timeframe.M15 -> "15m"
        }
        val limit = 80

        for (host in endpointHosts) {
            try {
                val url = "$host/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "ScalpSignal/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyString = response.body?.string() ?: return@use

                    val jsonArray = JSONArray(bodyString)
                    val candles = mutableListOf<Candle>()

                    for (i in 0 until jsonArray.length()) {
                        val kline = jsonArray.getJSONArray(i)
                        val openTime = kline.getLong(0)
                        val open = kline.getString(1).toDouble()
                        val high = kline.getString(2).toDouble()
                        val low = kline.getString(3).toDouble()
                        val close = kline.getString(4).toDouble()
                        val vol = kline.getString(5).toDouble()

                        candles.add(Candle(openTime, open, high, low, close, vol))
                    }

                    if (candles.isNotEmpty()) {
                        return@withContext candles
                    }
                }
            } catch (_: Exception) {
                // Try next mirror host
            }
        }
        return@withContext null
    }
}
