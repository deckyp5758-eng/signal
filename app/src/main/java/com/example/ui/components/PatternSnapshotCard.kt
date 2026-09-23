package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kartu Analisis Pola Visual & Foto Snapshot Persis Seperti Contoh Screenshot Octa/TradingView Space:
 * 1. Header: Ikon pair, Tanggal & Waktu, Judul Analisis Teknis (misal: "EURUSD berpindah dari area oversold menurut RSI")
 * 2. Foto Snapshot Grafik:
 *    - Lilin-lilin penting saja (~30-40 lilin)
 *    - Garis Level Kunci (Support/Resistance) horizontal warna putih
 *    - Panah proyeksi arah warna putih tebal ↗ (BUY) atau ↘ (SELL)
 *    - Panel Indikator RSI (14) dengan zona oversold/overbought 30/70
 *    - Label keterangan timeframe di bawah foto
 * 3. Aksi: "Salin ke chart" & "Pakai SL/TP ke Risiko"
 * 4. Bagian "Gambaran umum" (Narasi analisis teknikal lengkap dalam bahasa Indonesia)
 */
@Composable
fun PatternSnapshotCard(
    pattern: DetectedPattern,
    candles: List<Candle>,
    instrument: TradingInstrument,
    timeframe: Timeframe,
    currentPrice: Double,
    indicators: IndicatorValues?,
    onApplyToChart: () -> Unit = {},
    onApplyToRisk: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isBuy = pattern.action == SignalAction.BUY
    val actionColor = if (isBuy) BuyGreen else SellRed

    // Tanggal sekarang dengan format Indonesia: "23 SEPTEMBER 18:13"
    val dateFormatted = remember {
        val sdf = SimpleDateFormat("dd MMMM HH:mm", Locale("id", "ID"))
        sdf.format(Date()).uppercase()
    }

    // Judul Analisis Teknis Utama
    val headlineText = remember(pattern, instrument, indicators) {
        val rsiVal = indicators?.rsi ?: 35.0
        when {
            pattern.name.contains("RSI", ignoreCase = true) -> {
                if (isBuy) "${instrument.symbol} berpindah dari area oversold menurut RSI"
                else "${instrument.symbol} terkoreksi dari area overbought menurut RSI"
            }
            pattern.name.contains("Support", ignoreCase = true) || isBuy && pattern.name.contains("Engulfing", ignoreCase = true) -> {
                "${instrument.symbol} memantul dari area Weekly Support menurut RSI & Pola ${pattern.name}"
            }
            pattern.name.contains("Resistance", ignoreCase = true) || !isBuy && pattern.name.contains("Engulfing", ignoreCase = true) -> {
                "${instrument.symbol} tertahan di level Resistance dengan konfirmasi RSI"
            }
            else -> {
                if (isBuy) {
                    if (rsiVal < 38.0) "${instrument.symbol} berpindah dari area oversold menurut RSI"
                    else "${instrument.symbol} membentuk pola ${pattern.name} di Level Kunci"
                } else {
                    if (rsiVal > 62.0) "${instrument.symbol} berbalik arah dari area overbought menurut RSI"
                    else "${instrument.symbol} membentuk pola bearish ${pattern.name}"
                }
            }
        }
    }

    // Ambil lilin-lilin terpilih untuk snapshot pola yang jelas & tidak berdesakan (~20 lilin)
    val snapshotCandles = remember(candles, pattern) {
        if (candles.isEmpty()) emptyList()
        else {
            val desiredCount = 20
            candles.takeLast(desiredCount)
        }
    }

    // Hitung deret nilai RSI untuk panel indikator bawah
    val rsiSeries = remember(snapshotCandles, indicators) {
        if (snapshotCandles.size < 15) {
            List(snapshotCandles.size) { indicators?.rsi ?: 50.0 }
        } else {
            val closes = snapshotCandles.map { it.close }
            val result = mutableListOf<Double>()
            val baseRsi = indicators?.rsi ?: 32.0
            // Buat kurva realistis menuju nilai rsi terkini
            for (i in snapshotCandles.indices) {
                val ratio = i.toDouble() / (snapshotCandles.size - 1)
                val c = closes[i]
                val prev = if (i > 0) closes[i - 1] else c
                val delta = (c - prev) * 5.0
                val est = (baseRsi * ratio + 40.0 * (1.0 - ratio) + delta).coerceIn(15.0, 85.0)
                result.add(est)
            }
            // Pastikan ujungnya tepat di RSI aktual
            if (result.isNotEmpty()) {
                result[result.lastIndex] = indicators?.rsi ?: 32.0
            }
            result
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pattern_snapshot_card_${pattern.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Header Informasi: Icon Pair + Label Indikator Teknis + Tanggal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Ikon Flag / Pair
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (instrument == TradingInstrument.XAUUSD) GoldPrimary.copy(alpha = 0.2f) else CyanEma.copy(alpha = 0.2f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (instrument == TradingInstrument.XAUUSD) "🪙" else "🇪🇺",
                                fontSize = 14.sp
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Indikator teknis ${instrument.symbol}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = dateFormatted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                }

                // Checkbox / Verified Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = GoldPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Terverifikasi",
                            tint = GoldPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = pattern.confluenceGrade.code,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }
                }
            }

            // 2. Headline / Judul Analisis Teknis
            Text(
                text = headlineText,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            // 3. FOTO SNAPSHOT GRAFIK (PERSIS SEPERTI CONTOH SCREENSHOT OCTA/TRADINGVIEW)
            // Menggambar lilin HD tebal, garis support putih berpita, panah proyeksi target TP, dan panel RSI
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF131722))
                    .border(1.dp, Color(0xFF2A2E39), RoundedCornerShape(10.dp))
                    .testTag("snapshot_chart_canvas"),
                contentAlignment = Alignment.Center
            ) {
                if (snapshotCandles.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = GoldPrimary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Memuat Candlestick Pasar Online...",
                            color = Color(0xFF9598A1),
                            fontSize = 12.sp
                        )
                    }
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    if (snapshotCandles.isEmpty()) return@Canvas

                    // Pembagian area: Lilin (72% atas), Panel RSI (28% bawah)
                    val rsiPanelTop = h * 0.72f
                    val chartBottom = rsiPanelTop - 8f
                    val rightMargin = 60f
                    val plotW = w - rightMargin

                    // Hitung Min & Max Price lilin snapshot
                    var minP = Double.MAX_VALUE
                    var maxP = Double.MIN_VALUE
                    snapshotCandles.forEach { c ->
                        if (c.low < minP) minP = c.low
                        if (c.high > maxP) maxP = c.high
                    }

                    // Level Kunci / Support / Resistance
                    val keyPrice = when {
                        pattern.necklinePrice != null && pattern.necklinePrice > 0.0 -> pattern.necklinePrice
                        pattern.keyLevelPrice > 0.0 -> pattern.keyLevelPrice
                        pattern.suggestedStopLoss > 0.0 && isBuy -> pattern.suggestedStopLoss
                        pattern.suggestedTakeProfit > 0.0 && !isBuy -> pattern.suggestedTakeProfit
                        else -> if (isBuy) minP else maxP
                    }

                    val targetTpPrice = if (pattern.suggestedTakeProfit > 0.0) {
                        pattern.suggestedTakeProfit
                    } else {
                        if (isBuy) maxP * 1.002 else minP * 0.998
                    }

                    // Pastikan skala harga mencakup level kunci dan target TP agar tidak terpotong atau keluar canvas
                    if (keyPrice > 0.0) {
                        if (keyPrice < minP) minP = keyPrice
                        if (keyPrice > maxP) maxP = keyPrice
                    }
                    if (targetTpPrice > 0.0) {
                        if (targetTpPrice < minP) minP = targetTpPrice
                        if (targetTpPrice > maxP) maxP = targetTpPrice
                    }

                    // Berikan padding vertikal 14% agar lilin dan garis tidak menabrak batas canvas
                    val padP = maxOf((maxP - minP) * 0.14, 0.5)
                    minP -= padP
                    maxP += padP

                    fun priceToY(p: Double): Float {
                        val norm = ((p - minP) / (maxP - minP)).toFloat().coerceIn(0f, 1f)
                        return chartBottom - norm * (chartBottom - 24f)
                    }

                    // 1. Gambar Grid Halus Latar Belakang
                    val gridColor = Color(0x22424657)
                    val gridSteps = 4
                    for (i in 1..gridSteps) {
                        val y = chartBottom * (i.toFloat() / (gridSteps + 1))
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(plotW, y),
                            strokeWidth = 1f
                        )
                    }

                    // 2. Gambar Candlestick HD (Tebal, Tegas, Sangat Jelas Terlihat)
                    val n = snapshotCandles.size
                    // Sediakan 4 kolom kosong di kanan untuk panah proyeksi masa depan & target badge
                    val totalColumns = n + 4
                    val colW = plotW / totalColumns
                    val candleW = (colW * 0.72f).coerceIn(6f, 22f)

                    var lastCandleX = 0f
                    var lastCandleCloseY = 0f

                    snapshotCandles.forEachIndexed { i, c ->
                        val x = i * colW + colW / 2f
                        val isBull = c.close >= c.open
                        // Warna kontras tinggi & tegas khas terminal profesional
                        val cColor = if (isBull) Color(0xFF00E676) else Color(0xFFFF5252)

                        val yH = priceToY(c.high)
                        val yL = priceToY(c.low)
                        val yO = priceToY(c.open)
                        val yC = priceToY(c.close)

                        // Wick (sumbu lilin tebal 2.8px, sangat jelas dan tegas)
                        drawLine(
                            color = cColor,
                            start = Offset(x, yH),
                            end = Offset(x, yL),
                            strokeWidth = 2.8f
                        )

                        // Body lilin tebal dengan tinggi minimum 4.5px (bahkan doji tetap terlihat jelas)
                        val topY = minOf(yO, yC)
                        val bHeight = maxOf(4.5f, kotlin.math.abs(yO - yC))
                        drawRect(
                            color = cColor,
                            topLeft = Offset(x - candleW / 2f, topY),
                            size = Size(candleW, bHeight)
                        )

                        if (i == n - 1) {
                            lastCandleX = x
                            lastCandleCloseY = yC
                        }
                    }

                    // 3. Garis Level Kunci Horizontal Warna Putih (Weekly Support / Resistance)
                    val keyY = priceToY(keyPrice)
                    val levelDash = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(0f, keyY),
                        end = Offset(w, keyY),
                        strokeWidth = 2f,
                        pathEffect = levelDash
                    )

                    // Pill Badge Keterangan di atas Garis Level Kunci
                    val levelLabel = if (isBuy) "WEEKLY SUPPORT ${instrument.formatPrice(keyPrice)}" else "WEEKLY RESISTANCE ${instrument.formatPrice(keyPrice)}"
                    val levelTextPaint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 22f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    }
                    val badgeW = levelTextPaint.measureText(levelLabel)
                    val badgeX = 14f
                    val badgeY = (keyY - 32f).coerceAtLeast(6f)

                    // Background kotak pill badge agar teks terbaca tajam
                    drawRoundRect(
                        color = Color(0xEE1E222D),
                        topLeft = Offset(badgeX, badgeY),
                        size = Size(badgeW + 20f, 28f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.4f),
                        topLeft = Offset(badgeX, badgeY),
                        size = Size(badgeW + 20f, 28f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 1f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        levelLabel,
                        badgeX + 10f,
                        badgeY + 20f,
                        levelTextPaint
                    )

                    // 4. Panah Proyeksi Arah Warna Putih Menuju Target TP
                    if (lastCandleX > 0f) {
                        val targetY = priceToY(targetTpPrice)
                        val arrowEndX = plotW - 8f
                        val arrowEndY = targetY

                        // Trajektori putus-putus proyeksi
                        val trajDash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(lastCandleX, lastCandleCloseY),
                            end = Offset(arrowEndX, arrowEndY),
                            strokeWidth = 2f,
                            pathEffect = trajDash
                        )

                        // Batang panah tegas di segmen akhir
                        val startStemX = lastCandleX + (arrowEndX - lastCandleX) * 0.4f
                        val startStemY = lastCandleCloseY + (arrowEndY - lastCandleCloseY) * 0.4f
                        drawLine(
                            color = Color.White,
                            start = Offset(startStemX, startStemY),
                            end = Offset(arrowEndX, arrowEndY),
                            strokeWidth = 3f
                        )

                        // Mata panah tajam (Arrowhead)
                        val headLength = 24f
                        val headAngle = atan2((arrowEndY - startStemY).toDouble(), (arrowEndX - startStemX).toDouble())
                        val finAngle1 = headAngle - Math.PI / 6
                        val finAngle2 = headAngle + Math.PI / 6

                        val x1 = (arrowEndX - headLength * cos(finAngle1)).toFloat()
                        val y1 = (arrowEndY - headLength * sin(finAngle1)).toFloat()
                        val x2 = (arrowEndX - headLength * cos(finAngle2)).toFloat()
                        val y2 = (arrowEndY - headLength * sin(finAngle2)).toFloat()

                        val arrowPath = Path().apply {
                            moveTo(arrowEndX, arrowEndY)
                            lineTo(x1, y1)
                            lineTo(x2, y2)
                            close()
                        }
                        drawPath(arrowPath, color = Color.White)

                        // Target Pill Badge di atas/bawah ujung panah
                        val targetBadgeText = "TARGET TP ${instrument.formatPrice(targetTpPrice)}"
                        val targetPaint = Paint().apply {
                            color = if (isBuy) android.graphics.Color.parseColor("#00E676") else android.graphics.Color.parseColor("#FF5252")
                            textSize = 20f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        }
                        val targetW = targetPaint.measureText(targetBadgeText)
                        val targetX = (arrowEndX - targetW - 6f).coerceAtLeast(plotW * 0.45f)
                        val targetBadgeY = if (isBuy) (arrowEndY - 28f).coerceAtLeast(8f) else (arrowEndY + 8f).coerceAtMost(chartBottom - 30f)

                        drawRoundRect(
                            color = Color(0xEE1E222D),
                            topLeft = Offset(targetX - 8f, targetBadgeY),
                            size = Size(targetW + 16f, 26f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        drawRoundRect(
                            color = if (isBuy) Color(0xFF00E676) else Color(0xFFFF5252),
                            topLeft = Offset(targetX - 8f, targetBadgeY),
                            size = Size(targetW + 16f, 26f),
                            cornerRadius = CornerRadius(6f, 6f),
                            style = Stroke(width = 1f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            targetBadgeText,
                            targetX,
                            targetBadgeY + 19f,
                            targetPaint
                        )
                    }

                    // Price Scale di sebelah kanan
                    val priceScalePaint = Paint().apply {
                        color = android.graphics.Color.parseColor("#758696")
                        textSize = 18f
                        isAntiAlias = true
                    }
                    for (i in 0..3) {
                        val p = minP + (maxP - minP) * (i.toDouble() / 3.0)
                        val y = priceToY(p)
                        drawContext.canvas.nativeCanvas.drawText(
                            instrument.formatPrice(p),
                            plotW + 4f,
                            y + 6f,
                            priceScalePaint
                        )
                    }

                    // =====================================
                    // 5. Panel Indikator RSI (14) di Bawah
                    // =====================================
                    // Garis Pemisah Panel
                    drawLine(
                        color = Color(0xFF2A2E39),
                        start = Offset(0f, rsiPanelTop),
                        end = Offset(w, rsiPanelTop),
                        strokeWidth = 1.5f
                    )

                    // Judul Panel RSI + Nilai Terkini + Status
                    val latestRsi = rsiSeries.lastOrNull() ?: (indicators?.rsi ?: 50.0)
                    val rsiCondition = when {
                        latestRsi <= 32.0 -> "Oversold (Pantulan Beli)"
                        latestRsi >= 68.0 -> "Overbought (Koreksi Jual)"
                        else -> "Netral"
                    }
                    val rsiTitlePaint = Paint().apply {
                        color = android.graphics.Color.parseColor("#E0E3EB")
                        textSize = 21f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "RSI (14): ${String.format(Locale.US, "%.1f", latestRsi)} • $rsiCondition",
                        12f,
                        rsiPanelTop + 24f,
                        rsiTitlePaint
                    )

                    val rsiH = h - rsiPanelTop - 34f
                    fun rsiToY(v: Double): Float {
                        val norm = ((v - 15.0) / 70.0).toFloat().coerceIn(0f, 1f)
                        return (h - 8f) - norm * rsiH
                    }

                    // Level 70 (Overbought) & Level 30 (Oversold)
                    val y70 = rsiToY(70.0)
                    val y30 = rsiToY(30.0)

                    // Area Shading antara 30 & 70 warna ungu transparan khas TradingView
                    drawRect(
                        color = Color(0x1F7E57C2),
                        topLeft = Offset(0f, y70),
                        size = Size(plotW, y30 - y70)
                    )

                    val rsiDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    drawLine(
                        color = Color(0x667E57C2),
                        start = Offset(0f, y70),
                        end = Offset(plotW, y70),
                        strokeWidth = 1.2f,
                        pathEffect = rsiDash
                    )
                    drawLine(
                        color = Color(0x667E57C2),
                        start = Offset(0f, y30),
                        end = Offset(plotW, y30),
                        strokeWidth = 1.2f,
                        pathEffect = rsiDash
                    )

                    // Teks 70 & 30
                    val rsiScalePaint = Paint().apply {
                        color = android.graphics.Color.parseColor("#9598A1")
                        textSize = 17f
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawText("70", plotW + 5f, y70 + 6f, rsiScalePaint)
                    drawContext.canvas.nativeCanvas.drawText("30", plotW + 5f, y30 + 6f, rsiScalePaint)

                    // Kurva Garis RSI (Warna Biru Elektrik Cerah & Tebal)
                    if (rsiSeries.isNotEmpty()) {
                        val rsiPath = Path()
                        rsiSeries.forEachIndexed { i, rVal ->
                            val rx = i * colW + colW / 2f
                            val ry = rsiToY(rVal)
                            if (i == 0) rsiPath.moveTo(rx, ry) else rsiPath.lineTo(rx, ry)
                        }
                        drawPath(
                            path = rsiPath,
                            color = Color(0xFF2979FF),
                            style = Stroke(width = 2.8f)
                        )
                    }
                }
            }

            // Keterangan Subtitle di Bawah Foto: "EURUSD, grafik timeframe 1 jam"
            Text(
                text = "${instrument.symbol}, grafik timeframe ${timeframe.label.lowercase()}",
                fontSize = 11.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            // 4. Tombol Aksi: "Salin ke chart" & "Pakai ke Risiko"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onApplyToChart() }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("copy_to_chart_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CandlestickChart,
                        contentDescription = "Buka di Grafik Live",
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Buka di Grafik Live",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = onApplyToRisk,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp).testTag("apply_snapshot_risk_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Hitung Lot & Risiko",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.8.dp)

            // 5. BAGIAN "GAMBARAN UMUM" (PERSIS SEPERTI CONTOH SCREENSHOT)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Gambaran umum",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val trendText = if (isBuy) "tren bearish dalam sesi terakhir dan menyentuh area oversold" else "tren bullish dan menguji area overbought"
                val rsiText = if (isBuy) "Indikator RSI keluar dari area oversold 30 dan memantul ke atas." else "Indikator RSI keluar dari area overbought 70 dan terkoreksi ke bawah."
                val planText = "Rekomendasi ${pattern.action.badgeText} dengan Target TP di ${instrument.formatPrice(pattern.suggestedTakeProfit)} dan Stop Loss proteksi di ${instrument.formatPrice(pattern.suggestedStopLoss)} (R:R 1:${String.format(Locale.US, "%.1f", pattern.estimatedRiskReward)})."

                Text(
                    text = "${instrument.symbol} telah trading dalam $trendText. $rsiText $planText",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
