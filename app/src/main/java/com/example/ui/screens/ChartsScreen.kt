package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.service.IndicatorCalculator
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChartsScreen(
    instrument: TradingInstrument,
    currentPrice: Double,
    timeframe: Timeframe,
    candles: List<Candle>,
    activeSignal: ScalpSignal?,
    indicators: IndicatorValues?,
    showEma: Boolean,
    showBollinger: Boolean,
    showLevels: Boolean,
    onSelectTimeframe: (Timeframe) -> Unit,
    onToggleEma: () -> Unit,
    onToggleBollinger: () -> Unit,
    onToggleLevels: () -> Unit
) {
    var selectedCandleIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Controls Row: Timeframe + Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timeframe Pills
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Timeframe.values().forEach { tf ->
                    val isSel = tf == timeframe
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable { onSelectTimeframe(tf) }
                            .testTag("tf_${tf.code}")
                    ) {
                        Text(
                            text = tf.code,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Indicator Filter Chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = showEma,
                    onClick = onToggleEma,
                    label = { Text("EMA 9/21", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanEma.copy(alpha = 0.2f),
                        selectedLabelColor = CyanEma
                    )
                )

                FilterChip(
                    selected = showLevels,
                    onClick = onToggleLevels,
                    label = { Text("SL/TP", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = GoldPrimary
                    )
                )
            }
        }

        // Selected Candle Tooltip Bar
        val candleToInspect = if (selectedCandleIndex != null && selectedCandleIndex!! in candles.indices) {
            candles[selectedCandleIndex!!]
        } else {
            candles.lastOrNull()
        }

        if (candleToInspect != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isBull = candleToInspect.close >= candleToInspect.open
                    val col = if (isBull) BuyGreen else SellRed
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(candleToInspect.timestamp))
                    Text(
                        text = "[$timeStr] O: ${instrument.formatPrice(candleToInspect.open)}  H: ${instrument.formatPrice(candleToInspect.high)}",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "L: ${instrument.formatPrice(candleToInspect.low)}  C: ${instrument.formatPrice(candleToInspect.close)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = col
                    )
                }
            }
        }

        // Custom Candlestick Canvas
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                if (candles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    CandlestickChart(
                        candles = candles,
                        instrument = instrument,
                        activeSignal = activeSignal,
                        showEma = showEma,
                        showBollinger = showBollinger,
                        showLevels = showLevels,
                        selectedCandleIndex = selectedCandleIndex,
                        onCandleSelected = { idx -> selectedCandleIndex = idx }
                    )
                }
            }
        }

        // Bottom Market Info & Spread
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Spread Rata-rata", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = "${instrument.defaultSpreadPips} pips",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column {
                    Text(text = "Volatilitas ATR", fontSize = 11.sp, color = TextSecondary)
                    val atrText = indicators?.let {
                        "${"%.1f".format(it.atr / instrument.pipMultiplier)} pips"
                    } ?: "15.0 pips"
                    Text(
                        text = atrText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Harga Terakhir", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = instrument.formatPrice(currentPrice),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanEma
                    )
                }
            }
        }
    }
}

@Composable
fun CandlestickChart(
    candles: List<Candle>,
    instrument: TradingInstrument,
    activeSignal: ScalpSignal?,
    showEma: Boolean,
    showBollinger: Boolean,
    showLevels: Boolean,
    selectedCandleIndex: Int? = null,
    onCandleSelected: (Int?) -> Unit
) {
    val closes = remember(candles) { candles.map { it.close } }
    val ema9 = remember(closes) { IndicatorCalculator.calculateEMA(closes, 9) }
    val ema21 = remember(closes) { IndicatorCalculator.calculateEMA(closes, 21) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(candles) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val count = candles.size
                        if (count > 0) {
                            val candleWidth = size.width / count
                            val idx = (change.position.x / candleWidth).toInt().coerceIn(0, count - 1)
                            onCandleSelected(idx)
                        }
                    }
                )
            }
            .pointerInput(candles) {
                detectTapGestures(
                    onTap = { offset ->
                        val count = candles.size
                        if (count > 0) {
                            val candleWidth = size.width / count
                            val idx = (offset.x / candleWidth).toInt().coerceIn(0, count - 1)
                            onCandleSelected(idx)
                        }
                    }
                )
            }
    ) {
        if (candles.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height

        val minPrice = candles.minOf { it.low } * 0.9995
        val maxPrice = candles.maxOf { it.high } * 1.0005
        val priceRange = maxOf(maxPrice - minPrice, 0.0001)

        fun priceToY(price: Double): Float {
            val normalized = (price - minPrice) / priceRange
            return (h - (normalized * h)).toFloat().coerceIn(0f, h)
        }

        // Background horizontal grid lines
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = (h / gridCount) * i
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        val count = candles.size
        val candleSlotWidth = w / count
        val bodyWidth = candleSlotWidth * 0.65f

        // Draw Candlesticks
        for (i in candles.indices) {
            val candle = candles[i]
            val xCenter = (i * candleSlotWidth) + (candleSlotWidth / 2f)
            val isBull = candle.close >= candle.open
            val color = if (isBull) BuyGreen else SellRed

            val yHigh = priceToY(candle.high)
            val yLow = priceToY(candle.low)
            val yOpen = priceToY(candle.open)
            val yClose = priceToY(candle.close)

            val bodyTop = minOf(yOpen, yClose)
            val bodyHeight = maxOf(kotlin.math.abs(yOpen - yClose), 2f)

            // Wick line
            drawLine(
                color = color,
                start = Offset(xCenter, yHigh),
                end = Offset(xCenter, yLow),
                strokeWidth = 1.5f
            )

            // Body rectangle
            drawRect(
                color = color,
                topLeft = Offset(xCenter - (bodyWidth / 2f), bodyTop),
                size = Size(bodyWidth, bodyHeight)
            )
        }

        // Draw EMA 9 line (Cyan)
        if (showEma && ema9.size == candles.size) {
            val ema9Path = Path()
            for (i in ema9.indices) {
                val x = (i * candleSlotWidth) + (candleSlotWidth / 2f)
                val y = priceToY(ema9[i])
                if (i == 0) ema9Path.moveTo(x, y) else ema9Path.lineTo(x, y)
            }
            drawPath(
                path = ema9Path,
                color = CyanEma,
                style = Stroke(width = 2.5f)
            )
        }

        // Draw EMA 21 line (Orange)
        if (showEma && ema21.size == candles.size) {
            val ema21Path = Path()
            for (i in ema21.indices) {
                val x = (i * candleSlotWidth) + (candleSlotWidth / 2f)
                val y = priceToY(ema21[i])
                if (i == 0) ema21Path.moveTo(x, y) else ema21Path.lineTo(x, y)
            }
            drawPath(
                path = ema21Path,
                color = OrangeEma,
                style = Stroke(width = 2.5f)
            )
        }

        // Draw Active Signal Order Levels (Entry, SL, TP)
        if (showLevels && activeSignal != null && activeSignal.instrument == instrument) {
            val yEntry = priceToY(activeSignal.entryPrice)
            val ySl = priceToY(activeSignal.stopLoss)
            val yTp1 = priceToY(activeSignal.takeProfit1)

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

            // SL Line (Red dashed)
            drawLine(
                color = SellRed,
                start = Offset(0f, ySl),
                end = Offset(w, ySl),
                strokeWidth = 2f,
                pathEffect = dashEffect
            )

            // Entry Line (Cyan dashed)
            drawLine(
                color = CyanEma,
                start = Offset(0f, yEntry),
                end = Offset(w, yEntry),
                strokeWidth = 2f,
                pathEffect = dashEffect
            )

            // TP1 Line (Green dashed)
            drawLine(
                color = BuyGreen,
                start = Offset(0f, yTp1),
                end = Offset(w, yTp1),
                strokeWidth = 2f,
                pathEffect = dashEffect
            )
        }

        // Draw Interactive Crosshair if candle is selected
        if (selectedCandleIndex != null && selectedCandleIndex in candles.indices) {
            val inspected = candles[selectedCandleIndex]
            val x = (selectedCandleIndex * candleSlotWidth) + (candleSlotWidth / 2f)
            val y = priceToY(inspected.close)
            val crosshairDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

            // Vertical crosshair line
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f,
                pathEffect = crosshairDash
            )

            // Horizontal crosshair line
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
                pathEffect = crosshairDash
            )

            // Intersecting target point
            drawCircle(
                color = GoldPrimary,
                radius = 4.5f,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color.White,
                radius = 2f,
                center = Offset(x, y)
            )
        }
    }
}
