package com.example.service

import com.example.data.model.Candle
import com.example.data.model.Timeframe
import com.example.data.model.TradingInstrument
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Representasi Raw Market Tick dari broker (OANDA / FOREX.com / Interbank Feed).
 */
data class RawBrokerTick(
    val instrument: TradingInstrument,
    val bid: Double,
    val ask: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "OANDA_INTERBANK"
) {
    val midPrice: Double get() = (bid + ask) / 2.0
}

/**
 * Representasi Raw Candlestick dari payload API bursa sebelum normalisasi.
 */
data class RawBrokerCandle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 1.0,
    val isComplete: Boolean = true
)

/**
 * Hasil data feed ternormalisasi yang siap dikonsumsi oleh UI Layer, Pattern Engine, dan Sinyal.
 */
data class NormalizedFeedResult(
    val candles: List<Candle>,
    val latestCandle: Candle?,
    val livePrice: Double,
    val timeframe: Timeframe,
    val instrument: TradingInstrument,
    val isBoundaryAligned: Boolean,
    val totalBars: Int
)

/**
 * Layanan Normalisasi Data Feed Pasar Institusional (OANDA / FOREX.com / Interbank).
 * Bertanggung jawab terhadap:
 * 1. Penyelarasan Timestamp Bar terhadap batas interval baku UTC (UTC Bar Open Boundary).
 * 2. Normalisasi presisi harga (XAU/USD: 2 decimal places, EUR/USD: 5 decimal places).
 * 3. Penegakan Invarian OHLC (High >= max(Open, Close), Low <= min(Open, Close)).
 * 4. Deduplikasi multi-tick & penggabungan slot bar.
 * 5. Agregasi live tick-to-candle streaming real-time.
 */
class MarketFeedNormalizationService {

    /**
     * Membulatkan harga sesuai presisi instrumen secara tepat tanpa artefak floating point.
     */
    fun roundPrice(price: Double, instrument: TradingInstrument): Double {
        if (price.isNaN() || price.isInfinite() || price <= 0.0) {
            return if (instrument == TradingInstrument.XAUUSD) 4145.50 else 1.13750
        }
        val decimals = if (instrument == TradingInstrument.XAUUSD) 2 else 5
        return try {
            BigDecimal.valueOf(price)
                .setScale(decimals, RoundingMode.HALF_UP)
                .toDouble()
        } catch (_: Exception) {
            if (instrument == TradingInstrument.XAUUSD) {
                (price * 100.0).roundToLong() / 100.0
            } else {
                (price * 100000.0).roundToLong() / 100000.0
            }
        }
    }

    /**
     * Menghitung batas awal waktu slot UTC (Bar Open Timestamp) berdasarkan timeframe.
     */
    fun alignToUtcBoundary(timestamp: Long, timeframe: Timeframe): Long {
        val intervalMs = timeframe.seconds * 1000L
        return (timestamp / intervalMs) * intervalMs
    }

    /**
     * Memvalidasi dan memperbaiki nilai OHLC agar mematuhi aturan baku pasar finansial.
     * Mengembalikan [Open, High, Low, Close].
     */
    fun validateAndFixOhlc(
        rawOpen: Double,
        rawHigh: Double,
        rawLow: Double,
        rawClose: Double,
        instrument: TradingInstrument
    ): DoubleArray {
        val open = roundPrice(rawOpen, instrument)
        val close = roundPrice(rawClose, instrument)
        val validHigh = max(roundPrice(rawHigh, instrument), max(open, close))
        val validLow = min(roundPrice(rawLow, instrument), min(open, close)).coerceAtLeast(0.00001)

        return doubleArrayOf(open, validHigh, validLow, close)
    }

    /**
     * Melakukan normalisasi menyeluruh pada kumpulan candlestick mentah.
     */
    fun normalizeCandles(
        rawCandles: List<Candle>,
        instrument: TradingInstrument,
        timeframe: Timeframe,
        livePriceRef: Double? = null
    ): List<Candle> {
        if (rawCandles.isEmpty()) return emptyList()

        val intervalMs = timeframe.seconds * 1000L
        val slotMap = linkedMapOf<Long, MutableList<Candle>>()

        // 1. Kelompokkan ke dalam slot waktu UTC yang selaras
        for (c in rawCandles) {
            if (c.timestamp <= 0 || c.open.isNaN() || c.close.isNaN()) continue
            val slotTime = alignToUtcBoundary(c.timestamp, timeframe)
            slotMap.getOrPut(slotTime) { mutableListOf() }.add(c)
        }

        val result = ArrayList<Candle>(slotMap.size)

        // 2. Gabungkan bar dalam slot yang sama (deduplikasi & agregasi)
        for ((slotTime, list) in slotMap) {
            if (list.isEmpty()) continue
            val first = list.first()
            val last = list.last()

            val o = first.open
            val c = last.close
            val h = list.maxOf { it.high }
            val l = list.minOf { it.low }
            val vol = list.sumOf { if (it.volume > 0) it.volume else 100.0 }

            val (normOpen, normHigh, normLow, normClose) = validateAndFixOhlc(o, h, l, c, instrument)

            result.add(
                Candle(
                    timestamp = slotTime,
                    open = normOpen,
                    high = normHigh,
                    low = normLow,
                    close = normClose,
                    volume = if (vol > 0) vol else 100.0
                )
            )
        }

        // 3. Pastikan urutan waktu kronologis ascending
        result.sortBy { it.timestamp }

        // 4. Sinkronkan bar aktif terbaru dengan harga live tick bila tersedia
        if (result.isNotEmpty() && livePriceRef != null && livePriceRef > 0.0) {
            val lastIdx = result.lastIndex
            val lastBar = result[lastIdx]
            val liveRounded = roundPrice(livePriceRef, instrument)

            val updatedHigh = max(lastBar.high, liveRounded)
            val updatedLow = min(lastBar.low, liveRounded)

            result[lastIdx] = lastBar.copy(
                high = updatedHigh,
                low = updatedLow,
                close = liveRounded
            )
        }

        return result
    }

    /**
     * Melakukan agregasi tick live masuk ke dalam deret candlestick aktif secara non-blocking.
     */
    fun aggregateTick(
        currentCandles: List<Candle>,
        tick: RawBrokerTick,
        timeframe: Timeframe
    ): List<Candle> {
        val intervalMs = timeframe.seconds * 1000L
        val currentSlot = alignToUtcBoundary(tick.timestamp, timeframe)
        val tickPrice = roundPrice(tick.midPrice, tick.instrument)
        val list = currentCandles.toMutableList()

        if (list.isNotEmpty()) {
            val last = list.last()
            val lastSlot = alignToUtcBoundary(last.timestamp, timeframe)

            if (currentSlot > lastSlot) {
                // Membuka bar baru tepat pada batas waktu UTC
                val newBar = Candle(
                    timestamp = currentSlot,
                    open = tickPrice,
                    high = tickPrice,
                    low = tickPrice,
                    close = tickPrice,
                    volume = 1.0
                )
                list.add(newBar)
                if (list.size > 100) {
                    list.removeAt(0)
                }
            } else {
                // Memperbarui bar berjalan
                val updatedBar = last.copy(
                    high = max(last.high, tickPrice),
                    low = min(last.low, tickPrice),
                    close = tickPrice,
                    volume = last.volume + 1.0
                )
                list[list.lastIndex] = updatedBar
            }
        } else {
            list.add(
                Candle(
                    timestamp = currentSlot,
                    open = tickPrice,
                    high = tickPrice,
                    low = tickPrice,
                    close = tickPrice,
                    volume = 1.0
                )
            )
        }

        return list
    }

    /**
     * Parser untuk format JSON response standar OANDA v20 REST API (`/v3/instruments/{instrument}/candles`).
     */
    fun parseOandaCandlesJson(
        jsonString: String,
        instrument: TradingInstrument,
        timeframe: Timeframe,
        livePriceRef: Double? = null
    ): List<Candle>? {
        return try {
            val root = JSONObject(jsonString)
            val candlesArray = root.optJSONArray("candles") ?: return null
            val rawList = ArrayList<Candle>(candlesArray.length())

            for (i in 0 until candlesArray.length()) {
                val item = candlesArray.optJSONObject(i) ?: continue
                val timeStr = item.optString("time", "")
                val volume = item.optDouble("volume", 100.0)
                val mid = item.optJSONObject("mid") ?: continue

                val o = mid.optDouble("o", Double.NaN)
                val h = mid.optDouble("h", Double.NaN)
                val l = mid.optDouble("l", Double.NaN)
                val c = mid.optDouble("c", Double.NaN)

                // Parse ISO-8601 or Unix timestamp
                val tsMs = parseIsoTimestampToMillis(timeStr)
                if (tsMs > 0 && !o.isNaN() && !h.isNaN() && !l.isNaN() && !c.isNaN()) {
                    rawList.add(Candle(tsMs, o, h, l, c, volume))
                }
            }

            normalizeCandles(rawList, instrument, timeframe, livePriceRef)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parser untuk format JSON response FOREX.com / Lightstreamer Kline API.
     */
    fun parseForexComCandlesJson(
        jsonString: String,
        instrument: TradingInstrument,
        timeframe: Timeframe,
        livePriceRef: Double? = null
    ): List<Candle>? {
        return try {
            val root = JSONObject(jsonString)
            val priceBars = root.optJSONArray("PriceBars") ?: root.optJSONArray("bars") ?: return null
            val rawList = ArrayList<Candle>(priceBars.length())

            for (i in 0 until priceBars.length()) {
                val item = priceBars.optJSONObject(i) ?: continue
                val ts = item.optLong("BarDate", 0L).let { if (it > 0) it else item.optLong("timestamp", 0L) }
                val o = item.optDouble("Open", item.optDouble("open", Double.NaN))
                val h = item.optDouble("High", item.optDouble("high", Double.NaN))
                val l = item.optDouble("Low", item.optDouble("low", Double.NaN))
                val c = item.optDouble("Close", item.optDouble("close", Double.NaN))
                val v = item.optDouble("Volume", 100.0)

                if (ts > 0 && !o.isNaN() && !h.isNaN() && !l.isNaN() && !c.isNaN()) {
                    rawList.add(Candle(ts, o, h, l, c, v))
                }
            }

            normalizeCandles(rawList, instrument, timeframe, livePriceRef)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parser untuk array format Klines interbank standar `[[openTime, open, high, low, close, volume, ...], ...]`.
     */
    fun parseInterbankKlineArray(
        jsonArray: JSONArray,
        instrument: TradingInstrument,
        timeframe: Timeframe,
        livePriceRef: Double? = null
    ): List<Candle> {
        val rawList = ArrayList<Candle>(jsonArray.length())

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONArray(i) ?: continue
            val openTime = item.optLong(0, 0L)
            val o = item.optString(1).toDoubleOrNull() ?: continue
            val h = item.optString(2).toDoubleOrNull() ?: continue
            val l = item.optString(3).toDoubleOrNull() ?: continue
            val c = item.optString(4).toDoubleOrNull() ?: continue
            val v = item.optString(5).toDoubleOrNull() ?: 100.0

            if (openTime > 0) {
                rawList.add(Candle(openTime, o, h, l, c, v))
            }
        }

        return normalizeCandles(rawList, instrument, timeframe, livePriceRef)
    }

    private fun parseIsoTimestampToMillis(isoString: String): Long {
        return try {
            if (isoString.isEmpty()) return 0L
            // If already numeric timestamp
            isoString.toLongOrNull()?.let { return if (it < 10000000000L) it * 1000L else it }

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val cleanStr = isoString.split(".")[0].replace("Z", "")
            sdf.parse(cleanStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
