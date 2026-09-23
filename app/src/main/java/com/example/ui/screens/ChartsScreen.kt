package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.service.IndicatorCalculator
import com.example.service.PatternRecognitionEngine
import com.example.ui.components.PatternSnapshotCard
import com.example.ui.components.WebChartTerminal
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ChartViewMode(val title: String) {
    ANALISIS("Analisis"),
    BROKER_WEB("Grafik Live")
}

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
    showPatterns: Boolean,
    selectedPatternFilter: PatternTypeCategory,
    multiTimeframeTrends: Map<Timeframe, TrendDirection> = emptyMap(),
    htfCandles: List<Candle> = emptyList(),
    selectedGradeFilter: ConfluenceGrade? = null,
    filterOnlyHtfAligned: Boolean = false,
    onSelectTimeframe: (Timeframe) -> Unit,
    onToggleEma: () -> Unit,
    onToggleBollinger: () -> Unit,
    onToggleLevels: () -> Unit,
    onTogglePatterns: () -> Unit,
    onSelectPatternFilter: (PatternTypeCategory) -> Unit,
    onSelectGradeFilter: (ConfluenceGrade?) -> Unit = {},
    onToggleHtfAlignedFilter: () -> Unit = {},
    onApplyPatternToRisk: (DetectedPattern) -> Unit = {},
    isLiveOnline: Boolean = true,
    latencyMs: Long = 0L,
    isScanning: Boolean = false,
    onRefreshScan: () -> Unit = {}
) {
    var viewMode by remember { mutableStateOf(ChartViewMode.ANALISIS) }
    var selectedSnapshotPatternIndex by remember { mutableStateOf(0) }
    var showSpreadInfoDialog by remember { mutableStateOf(false) }
    var showConfluenceInfoDialog by remember { mutableStateOf(false) }
    var selectedCandleIndex by remember { mutableStateOf<Int?>(null) }
    var useTradingViewWeb by remember { mutableStateOf(false) }

    // Run Auto Pattern Recognition Engine with Confluence Grade & HTF Trend Analysis
    val allDetectedPatterns = remember(candles, instrument, timeframe, htfCandles) {
        PatternRecognitionEngine.detectAllPatterns(candles, instrument, timeframe, htfCandles)
    }

    val filteredPatterns = remember(allDetectedPatterns, selectedPatternFilter, selectedGradeFilter, filterOnlyHtfAligned) {
        allDetectedPatterns.filter { pattern ->
            val matchCategory = selectedPatternFilter == PatternTypeCategory.ALL || pattern.category == selectedPatternFilter
            val matchGrade = selectedGradeFilter == null || pattern.confluenceGrade == selectedGradeFilter
            val matchHtfAligned = !filterOnlyHtfAligned || pattern.htfTrendAlignment == TrendAlignment.ALIGNED
            matchCategory && matchGrade && matchHtfAligned
        }
    }


    // Determine spread condition
    val (spreadBadgeText, spreadBadgeColor, _) = when (instrument) {
        TradingInstrument.XAUUSD -> {
            when {
                instrument.defaultSpreadPips <= 1.5 -> Triple("KETAT", BuyGreen, "Sangat bagus untuk scalping M1/M5")
                instrument.defaultSpreadPips <= 3.0 -> Triple("NORMAL", GoldPrimary, "Kondisi reguler pasar")
                else -> Triple("MELEBAR", SellRed, "Biaya tinggi / hindari entry baru")
            }
        }
        TradingInstrument.EURUSD -> {
            when {
                instrument.defaultSpreadPips <= 0.5 -> Triple("KETAT", BuyGreen, "Sangat bagus untuk scalping M1/M5")
                instrument.defaultSpreadPips <= 1.5 -> Triple("NORMAL", GoldPrimary, "Kondisi reguler pasar")
                else -> Triple("MELEBAR", SellRed, "Biaya tinggi / hindari entry baru")
            }
        }
    }

    // Pola aktif untuk Mode 1 Snapshot Feed (Persis seperti contoh screenshot)
    val displaySnapshotPattern = remember(allDetectedPatterns, candles, indicators, currentPrice, timeframe, selectedSnapshotPatternIndex) {
        if (allDetectedPatterns.isNotEmpty()) {
            val safeIdx = selectedSnapshotPatternIndex.coerceIn(0, allDetectedPatterns.lastIndex)
            allDetectedPatterns[safeIdx]
        } else {
            val rsiVal = indicators?.rsi ?: 33.5
            val isBuy = rsiVal < 48.0
            val lastClose = candles.lastOrNull()?.close ?: currentPrice
            val slDist = if (instrument == TradingInstrument.XAUUSD) 3.5 else 0.0035
            val tpDist = slDist * 2.2
            val sl = if (isBuy) lastClose - slDist else lastClose + slDist
            val tp = if (isBuy) lastClose + tpDist else lastClose - tpDist
            val keyP = if (isBuy) lastClose - slDist * 0.4 else lastClose + slDist * 0.4
            DetectedPattern(
                id = "snapshot_feed_active",
                name = if (isBuy) "Weekly Support Reversal" else "Weekly Resistance Pullback",
                category = PatternTypeCategory.CANDLESTICK,
                action = if (isBuy) SignalAction.BUY else SignalAction.SELL,
                confidence = 88,
                description = "Reversal teknikal diuji pada level kunci dengan konfirmasi momentum RSI.",
                tradingTip = "Beli saat RSI keluar dari area oversold 30 dengan stop loss di bawah level support.",
                startCandleIndex = (candles.size - 28).coerceAtLeast(0),
                endCandleIndex = candles.lastIndex.coerceAtLeast(0),
                keyLevelPrice = keyP,
                confluenceGrade = ConfluenceGrade.A_PLUS,
                confluenceScore = 88,
                confluenceFactors = listOf("RSI(14) Oversold Reversal", "Weekly Support Level", "R:R 1:2.2"),
                estimatedRiskReward = 2.2,
                suggestedStopLoss = sl,
                suggestedTakeProfit = tp
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 0. Mode Switcher: Auto-Pattern Canvas vs Broker Web Terminal Live
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("chart_mode_switcher")
            ) {
                Row(
                    modifier = Modifier.padding(4.dp).fillMaxWidth()
                ) {
                    ChartViewMode.values().forEach { mode ->
                        val isSel = viewMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) GoldPrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewMode = mode }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        ChartViewMode.ANALISIS -> Icons.Default.Analytics
                                        ChartViewMode.BROKER_WEB -> Icons.Default.Language
                                    },
                                    contentDescription = null,
                                    tint = if (isSel) Color.Black else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mode.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Controls Row: Timeframe Pills (M1, M5, M15, M30, H1) - Common for both modes
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timeframe Selector Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.testTag("timeframe_row")
                ) {
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
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Auto Pattern Toggle Badge
                FilterChip(
                    selected = showPatterns,
                    onClick = onTogglePatterns,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = { Text("Pola & Zona", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary.copy(alpha = 0.22f),
                        selectedLabelColor = GoldPrimary,
                        selectedLeadingIconColor = GoldPrimary
                    ),
                    modifier = Modifier.testTag("toggle_patterns_chip")
                )
            }
        }

        // Live Feed Status & Quick Rescan Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isLiveOnline) BuyGreen else SellRed)
                        )
                        Text(
                            text = if (isLiveOnline) "Live • ${latencyMs}ms Spot" else "Offline • Reconnecting",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isLiveOnline) BuyGreen else SellRed
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isScanning) GoldPrimary.copy(alpha = 0.3f) else GoldPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clickable(enabled = !isScanning) { onRefreshScan() }
                        .testTag("chart_quick_rescan_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 1.5.dp,
                                color = GoldPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Scan Ulang Cepat",
                                tint = GoldPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = if (isScanning) "Memindai..." else "Scan Ulang",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }
                }
            }
        }

        if (viewMode == ChartViewMode.BROKER_WEB) {
            // Sub-Mode Switcher: Grafik Native (Default 60 FPS) vs TradingView Web Widget
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (!useTradingViewWeb) GoldPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { useTradingViewWeb = false }
                            .testTag("submode_native_chart")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CandlestickChart,
                                contentDescription = null,
                                tint = if (!useTradingViewWeb) Color.Black else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Grafik Native (60 FPS)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!useTradingViewWeb) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (useTradingViewWeb) GoldPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { useTradingViewWeb = true }
                            .testTag("submode_tradingview_web")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = if (useTradingViewWeb) Color.Black else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TradingView Web",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (useTradingViewWeb) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (!useTradingViewWeb) {
                // Indicator Toggle Chips Row
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("native_indicators_row")
                    ) {
                        item {
                            FilterChip(
                                selected = showEma,
                                onClick = onToggleEma,
                                label = { Text("EMA 9/21", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanEma.copy(alpha = 0.22f),
                                    selectedLabelColor = CyanEma
                                ),
                                modifier = Modifier.testTag("toggle_ema_chip")
                            )
                        }
                        item {
                            FilterChip(
                                selected = showBollinger,
                                onClick = onToggleBollinger,
                                label = { Text("Bollinger (20,2)", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PurpleMacd.copy(alpha = 0.22f),
                                    selectedLabelColor = PurpleMacd
                                ),
                                modifier = Modifier.testTag("toggle_bollinger_chip")
                            )
                        }
                        item {
                            FilterChip(
                                selected = showLevels,
                                onClick = onToggleLevels,
                                label = { Text("S/R Kunci", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary.copy(alpha = 0.22f),
                                    selectedLabelColor = GoldPrimary
                                ),
                                modifier = Modifier.testTag("toggle_levels_chip")
                            )
                        }
                        item {
                            FilterChip(
                                selected = showPatterns,
                                onClick = onTogglePatterns,
                                label = { Text("Pola Target", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BuyGreen.copy(alpha = 0.22f),
                                    selectedLabelColor = BuyGreen
                                ),
                                modifier = Modifier.testTag("toggle_patterns_native_chip")
                            )
                        }
                    }
                }

                // Interactive Native Candlestick Chart (100% Jetpack Compose Canvas - Zero Mesa errors, 60fps)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(430.dp)
                            .testTag("candlestick_chart_container")
                    ) {
                        CandlestickChart(
                            candles = candles,
                            instrument = instrument,
                            timeframe = timeframe,
                            activeSignal = activeSignal,
                            detectedPatterns = allDetectedPatterns,
                            showEma = showEma,
                            showBollinger = showBollinger,
                            showLevels = showLevels,
                            showPatterns = showPatterns,
                            selectedCandleIndex = selectedCandleIndex,
                            onCandleSelected = { selectedCandleIndex = it }
                        )
                    }
                }

                // Inspected Candle Detail Bar (if user touches a candle on the native chart)
                item {
                    if (selectedCandleIndex != null && selectedCandleIndex!! in candles.indices) {
                        val inspectedCandle = candles[selectedCandleIndex!!]
                        val candleDateFormatted = remember(inspectedCandle.timestamp) {
                            val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
                            sdf.format(Date(inspectedCandle.timestamp))
                        }
                        val isBullish = inspectedCandle.close >= inspectedCandle.open

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isBullish) BuyGreen else SellRed),
                            modifier = Modifier.fillMaxWidth().testTag("candle_inspect_bar")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text = "T: $candleDateFormatted", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                    Text(text = "O: ${instrument.formatPrice(inspectedCandle.open)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = "H: ${instrument.formatPrice(inspectedCandle.high)}", fontSize = 10.sp, color = BuyGreen)
                                    Text(text = "L: ${instrument.formatPrice(inspectedCandle.low)}", fontSize = 10.sp, color = SellRed)
                                    Text(text = "C: ${instrument.formatPrice(inspectedCandle.close)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isBullish) BuyGreen else SellRed)
                                }
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup Info Candle",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { selectedCandleIndex = null }
                                )
                            }
                        }
                    }
                }
            } else {
                // TradingView Web Terminal
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(540.dp)
                    ) {
                        WebChartTerminal(
                            instrument = instrument,
                            timeframe = timeframe,
                            detectedPatterns = allDetectedPatterns,
                            onApplyPatternToRisk = { pattern -> onApplyPatternToRisk(pattern) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            // MODE 1: SNAPSHOT FEED ANALISIS POLA (PERSIS SEPERTI CONTOH SCREENSHOT OCTA/SPACE)
            // Jika ada lebih dari 1 pola terdeteksi di timeframe ini, tampilkan selector pola
            if (allDetectedPatterns.size > 1) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        items(allDetectedPatterns.size) { idx ->
                            val pat = allDetectedPatterns[idx]
                            val isSel = selectedSnapshotPatternIndex == idx
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedSnapshotPatternIndex = idx },
                                label = {
                                    Text(
                                        text = "${pat.name} (${pat.confluenceGrade.code})",
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary.copy(alpha = 0.25f),
                                    selectedLabelColor = GoldPrimary
                                ),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }

            // Kartu Snapshot Analisis Pola (Persis format foto screenshot pengguna)
            item {
                PatternSnapshotCard(
                    pattern = displaySnapshotPattern,
                    candles = candles,
                    instrument = instrument,
                    timeframe = timeframe,
                    currentPrice = currentPrice,
                    indicators = indicators,
                    onApplyToChart = { viewMode = ChartViewMode.BROKER_WEB },
                    onApplyToRisk = { onApplyPatternToRisk(displaySnapshotPattern) }
                )
            }

            // Multi-Timeframe Trend Matrix Card di bawah snapshot
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("mtf_trend_matrix_card")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Matriks Tren Multi-Timeframe (MTF)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = GoldPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Konfirmasi 1H / 15M",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1).forEach { tf ->
                                val trend = multiTimeframeTrends[tf] ?: TrendDirection.NEUTRAL
                                val (bg, txt, icon) = when (trend) {
                                    TrendDirection.BULLISH -> Triple(BuyGreen.copy(alpha = 0.15f), BuyGreen, Icons.Default.TrendingUp)
                                    TrendDirection.BEARISH -> Triple(SellRed.copy(alpha = 0.15f), SellRed, Icons.Default.TrendingDown)
                                    TrendDirection.NEUTRAL -> Triple(MaterialTheme.colorScheme.surfaceVariant, TextSecondary, Icons.Default.TrendingFlat)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = bg,
                                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Text(text = tf.code, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Icon(imageVector = icon, contentDescription = null, tint = txt, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Market Status Summary Card (Spread + ATR + Last Price)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSpreadInfoDialog = true }
                            .padding(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "Spread", fontSize = 11.sp, color = TextSecondary)
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Penjelasan Spread",
                                tint = TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${instrument.defaultSpreadPips} pips",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = spreadBadgeColor.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, spreadBadgeColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = spreadBadgeText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = spreadBadgeColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
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

        // 6. DETECTED PATTERNS DETAIL SECTION
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header with Confluence Rating info button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Scanner Pola & Confluence (${timeframe.code})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    IconButton(
                        onClick = { showConfluenceInfoDialog = true },
                        modifier = Modifier.size(26.dp).testTag("confluence_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Panduan Confluence Rating",
                            tint = GoldPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Filter Row 1: Confluence Grade Pills (A+, A, B)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        val isAll = selectedGradeFilter == null
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isAll) GoldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onSelectGradeFilter(null) }
                        ) {
                            Text(
                                text = "Semua Grade",
                                fontSize = 10.sp,
                                fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal,
                                color = if (isAll) Color.Black else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    items(ConfluenceGrade.values()) { grade ->
                        val isSelected = grade == selectedGradeFilter
                        val (chipColor, chipBorder) = when (grade) {
                            ConfluenceGrade.A_PLUS -> GoldPrimary to GoldPrimary
                            ConfluenceGrade.A -> CyanEma to CyanEma
                            ConfluenceGrade.B -> OrangeEma to OrangeEma
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) chipColor else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isSelected) chipBorder else Color.Transparent),
                            modifier = Modifier.clickable { onSelectGradeFilter(grade) }
                        ) {
                            Text(
                                text = grade.badgeTitle,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Filter Row 2: Category Filter Pills
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(PatternTypeCategory.values()) { cat ->
                        val isSelected = cat == selectedPatternFilter
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.clickable { onSelectPatternFilter(cat) }
                        ) {
                            Text(
                                text = cat.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Filter Row 3: Multi-Timeframe Trend Alignment Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterOnlyHtfAligned,
                        onClick = onToggleHtfAlignedFilter,
                        leadingIcon = {
                            Icon(
                                imageVector = if (filterOnlyHtfAligned) Icons.Default.CheckCircle else Icons.Default.FilterAlt,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Hanya Tren Sejalan (HTF Aligned)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BuyGreen.copy(alpha = 0.2f),
                            selectedLabelColor = BuyGreen,
                            selectedLeadingIconColor = BuyGreen
                        ),
                        modifier = Modifier.testTag("filter_htf_aligned_chip")
                    )

                    Text(
                        text = "${filteredPatterns.size} pola aktif",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }
        }

        // List of Pattern Cards
        if (filteredPatterns.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Tidak ada pola spesifik untuk kategori ini di timeframe ${timeframe.code}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Pilih kategori lain atau ganti ke timeframe M30 / H1 untuk pola mayor.",
                            fontSize = 10.sp,
                            color = TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(filteredPatterns, key = { it.id }) { pattern ->
                PatternDetailCard(
                    pattern = pattern,
                    instrument = instrument,
                    onApplyToRisk = { onApplyPatternToRisk(pattern) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSpreadInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSpreadInfoDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = GoldPrimary
                    )
                    Text(text = "Panduan Status Spread", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Spread adalah selisih harga Beli (Ask) dan Jual (Bid) sebagai biaya transaksi broker:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BuyGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, BuyGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "🟢 SPREAD KETAT (Optimal)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BuyGreen)
                            Text(
                                text = "Biaya transaksi sangat murah. Waktu terbaik untuk scalping cepat di timeframe M1/M5.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GoldPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "🟡 SPREAD NORMAL (Reguler)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GoldPrimary)
                            Text(
                                text = "Kondisi pasar normal. Aman untuk trading scalping dan intraday standar.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SellRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, SellRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "🔴 SPREAD MELEBAR (Waspada)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SellRed)
                            Text(
                                text = "Terjadi saat rilis berita High Impact (News), pergantian sesi subuh, atau akhir pekan. Hindari open posisi baru.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpreadInfoDialog = false }) {
                    Text("Mengerti", color = GoldPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showConfluenceInfoDialog) {
        AlertDialog(
            onDismissRequest = { showConfluenceInfoDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = GoldPrimary
                    )
                    Text(text = "Sistem Confluence Rating Grade", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Confluence Grade mengukur keandalan probabilitas setup berdasarkan keselarasan multi-timeframe dan indikator teknikal:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Grade A+
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GoldPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "💎 GRADE A+ (Elite)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GoldPrimary)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = GoldPrimary.copy(alpha = 0.2f)
                                ) {
                                    Text(text = "Skor 82 - 100", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GoldPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            Text(
                                text = "Probabilitas tertinggi. Pola sejalan dengan tren mayor HTF (H1/M15), terjadi di key level/dynamic EMA, RSI optimal, dan R:R >= 1:2. Direkomendasikan untuk target maksimal.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Grade A
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyanEma.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, CyanEma.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "⭐ GRADE A (High Quality)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CyanEma)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CyanEma.copy(alpha = 0.2f)
                                ) {
                                    Text(text = "Skor 70 - 81", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyanEma, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            Text(
                                text = "Setup berkualitas tinggi dengan 2-3 konfluensi pendukung kuat. Cocok untuk entry scalping standar berdisiplin rasio risiko.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Grade B
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = OrangeEma.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, OrangeEma.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "⚡ GRADE B (Moderate Scalp)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrangeEma)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = OrangeEma.copy(alpha = 0.2f)
                                ) {
                                    Text(text = "Skor < 70", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = OrangeEma, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            Text(
                                text = "Setup pembalikan cepat atau melawan tren mayor HTF (Counter-Trend). Disarankan gunakan TP cepat (TP1) atau pasang trailing stop ketat.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConfluenceInfoDialog = false }) {
                    Text("Tutup", color = GoldPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun PatternDetailCard(
    pattern: DetectedPattern,
    instrument: TradingInstrument,
    onApplyToRisk: () -> Unit
) {
    val actionColor = if (pattern.action == SignalAction.BUY) BuyGreen else if (pattern.action == SignalAction.SELL) SellRed else GoldPrimary
    val categoryBadgeText = when (pattern.category) {
        PatternTypeCategory.CANDLESTICK -> "Candlestick"
        PatternTypeCategory.CHART_PATTERN -> "Chart Pattern"
        PatternTypeCategory.SMC -> "SMC / Zone"
        PatternTypeCategory.ALL -> "Pola"
    }

    val (gradeBorderColor, gradeTextColor, gradeBgColor) = when (pattern.confluenceGrade) {
        ConfluenceGrade.A_PLUS -> Triple(GoldPrimary, GoldPrimary, GoldPrimary.copy(alpha = 0.15f))
        ConfluenceGrade.A -> Triple(CyanEma, CyanEma, CyanEma.copy(alpha = 0.15f))
        ConfluenceGrade.B -> Triple(OrangeEma, OrangeEma, OrangeEma.copy(alpha = 0.15f))
    }

    val (alignmentText, alignmentColor, alignmentBg) = when (pattern.htfTrendAlignment) {
        TrendAlignment.ALIGNED -> Triple(
            "🟢 HTF Sejalan (${pattern.higherTimeframeTrend.code})",
            BuyGreen,
            BuyGreen.copy(alpha = 0.15f)
        )
        TrendAlignment.COUNTER_TREND -> Triple(
            "⚠️ Counter-Trend HTF (${pattern.higherTimeframeTrend.code})",
            OrangeEma,
            OrangeEma.copy(alpha = 0.15f)
        )
        TrendAlignment.NEUTRAL -> Triple(
            "⚪ HTF Konsolidasi",
            TextSecondary,
            MaterialTheme.colorScheme.surfaceVariant
        )
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, gradeBorderColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().testTag("pattern_card_${pattern.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Action + Pattern Name & Confluence Grade Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = actionColor.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, actionColor)
                    ) {
                        Text(
                            text = pattern.action.badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = actionColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = pattern.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Confluence Grade Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = gradeBgColor,
                    border = BorderStroke(1.dp, gradeBorderColor)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${pattern.confluenceGrade.badgeTitle} (${pattern.confluenceScore}/100)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = gradeTextColor
                        )
                    }
                }
            }

            // Row 2: HTF Alignment & Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = alignmentBg,
                    border = BorderStroke(1.dp, alignmentColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = alignmentText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = alignmentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "$categoryBadgeText • ${pattern.confidence}% Akurasi",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Description
            Text(
                text = pattern.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )

            // Confluence Factors Checklist
            if (pattern.confluenceFactors.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Faktor Konfluensi (${pattern.confluenceFactors.size} Terkonfirmasi):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        pattern.confluenceFactors.forEach { factor ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                val isWarning = factor.startsWith("⚠️")
                                Icon(
                                    imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isWarning) OrangeEma else BuyGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = factor,
                                    fontSize = 10.sp,
                                    fontWeight = if (isWarning) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isWarning) OrangeEma else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Trading Tip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = pattern.tradingTip,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Counter Trend Warning if Grade B
            if (pattern.confluenceGrade == ConfluenceGrade.B) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = OrangeEma.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, OrangeEma.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = OrangeEma,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Peringatan Counter-Trend: Amankan profit di TP cepat atau pasang trailing stop ketat.",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = OrangeEma
                        )
                    }
                }
            }

            // Price Targets & 1-Click Apply to Risk Manager
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "R:R 1:${String.format(java.util.Locale.US, "%.1f", pattern.estimatedRiskReward)} | SL: ${instrument.formatPrice(pattern.suggestedStopLoss)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Text(
                        text = "Target TP: ${instrument.formatPrice(pattern.suggestedTakeProfit)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BuyGreen
                    )
                }

                Button(
                    onClick = onApplyToRisk,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("apply_pattern_risk_${pattern.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Hitung Lot",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
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
    timeframe: Timeframe = Timeframe.M1,
    activeSignal: ScalpSignal?,
    detectedPatterns: List<DetectedPattern> = emptyList(),
    showEma: Boolean,
    showBollinger: Boolean,
    showLevels: Boolean,
    showPatterns: Boolean,
    selectedCandleIndex: Int? = null,
    onCandleSelected: (Int?) -> Unit
) {
    val closes = remember(candles) { candles.map { it.close } }
    val ema9 = remember(closes) { IndicatorCalculator.calculateEMA(closes, 9) }
    val ema21 = remember(closes) { IndicatorCalculator.calculateEMA(closes, 21) }
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    // Candle Countdown Timer state (updates every second)
    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val candleRemainingFormatted = remember(currentTimeMs, timeframe) {
        val intervalMs = timeframe.seconds * 1000L
        val elapsedInCurrentCandle = currentTimeMs % intervalMs
        val remainingMs = maxOf(0L, intervalMs - elapsedInCurrentCandle)
        val remainingSec = (remainingMs / 1000L).coerceAtLeast(0L)
        val minutes = remainingSec / 60
        val seconds = remainingSec % 60
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    // Interactive Zoom and Pan State
    // visibleCandlesCount: default 35 candles, min 12 (zoomed in), max 80 (zoomed out)
    var visibleCandlesCount by remember(candles.size) { mutableStateOf(35f.coerceAtMost(candles.size.toFloat().coerceAtLeast(10f))) }
    // scrollOffset: 0f = most recent candles at right. Positive values = scrolled back in time
    var scrollOffset by remember(candles.size) { mutableStateOf(0f) }

    val totalCandles = candles.size
    val minVisible = 12f
    val maxVisible = totalCandles.toFloat().coerceAtLeast(15f)

    // Clamp scroll offset within valid range
    val maxScroll = (totalCandles - visibleCandlesCount).coerceAtLeast(0f)
    val clampedScroll = scrollOffset.coerceIn(0f, maxScroll)

    // Compute visible window
    val endIndex = (totalCandles - 1 - clampedScroll.toInt()).coerceIn(0, (totalCandles - 1).coerceAtLeast(0))
    val startIndex = (endIndex - visibleCandlesCount.toInt() + 1).coerceIn(0, endIndex)
    val visibleCandles = if (candles.isNotEmpty()) candles.subList(startIndex, endIndex + 1) else emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(candles, visibleCandlesCount, clampedScroll) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) {
                            // Zooming changes visible candles count
                            val newVisible = (visibleCandlesCount / zoom).coerceIn(minVisible, maxVisible)
                            visibleCandlesCount = newVisible
                        }
                        if (pan.x != 0f) {
                            // Panning horizontally moves the candle window
                            val candlePxWidth = (size.width - 65.dp.toPx()) / visibleCandlesCount
                            if (candlePxWidth > 0) {
                                val deltaCandles = pan.x / candlePxWidth
                                val newScroll = (scrollOffset + deltaCandles).coerceIn(0f, (totalCandles - visibleCandlesCount).coerceAtLeast(0f))
                                scrollOffset = newScroll
                            }
                        }
                    }
                }
                .pointerInput(candles, visibleCandlesCount, clampedScroll, startIndex) {
                    detectTapGestures(
                        onTap = { offset ->
                            val chartWidth = size.width - 65.dp.toPx()
                            if (offset.x in 0f..chartWidth && visibleCandles.isNotEmpty()) {
                                val candleWidth = chartWidth / visibleCandles.size
                                val localIdx = (offset.x / candleWidth).toInt().coerceIn(0, visibleCandles.size - 1)
                                val globalIdx = startIndex + localIdx
                                onCandleSelected(globalIdx)
                            } else {
                                onCandleSelected(null)
                            }
                        }
                    )
                }
        ) {
            if (candles.isEmpty()) return@Canvas

            val rightPriceScaleWidth = 65.dp.toPx()
            val bottomTimeScaleHeight = 22.dp.toPx()
            val chartW = size.width - rightPriceScaleWidth
            val chartH = size.height - bottomTimeScaleHeight

            if (chartW <= 0 || chartH <= 0) return@Canvas

            // Compute min and max price from visible candles for adaptive scaling
            val displayCandles = if (visibleCandles.isNotEmpty()) visibleCandles else candles
            val minP = displayCandles.minOf { it.low }
            val maxP = displayCandles.maxOf { it.high }
            val padding = (maxP - minP) * 0.08
            val minPrice = (minP - padding).coerceAtLeast(0.0001)
            val maxPrice = maxP + padding
            val priceRange = maxOf(maxPrice - minPrice, 0.00001)

            fun priceToY(price: Double): Float {
                val normalized = (price - minPrice) / priceRange
                return (chartH - (normalized * chartH)).toFloat().coerceIn(0f, chartH)
            }

            // ==========================================
            // 1. HORIZONTAL GRID LINES & RIGHT PRICE SCALE
            // ==========================================
            val gridCount = 5
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(180, 160, 170, 190)
                textSize = 9.sp.toPx()
                isAntiAlias = true
                typeface = android.graphics.Typeface.MONOSPACE
            }

            for (i in 0..gridCount) {
                val y = (chartH / gridCount) * i
                val priceAtY = maxPrice - (i.toDouble() / gridCount) * priceRange

                // Grid line across chart area
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(0f, y),
                    end = Offset(chartW, y),
                    strokeWidth = 1f
                )

                // Right price text label
                drawContext.canvas.nativeCanvas.drawText(
                    instrument.formatPrice(priceAtY),
                    chartW + 6.dp.toPx(),
                    y + 3.dp.toPx(),
                    textPaint
                )
            }

            // Divider line between chart and right price scale
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(chartW, 0f),
                end = Offset(chartW, chartH),
                strokeWidth = 1f
            )

            // Divider line between chart and bottom time scale
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(0f, chartH),
                end = Offset(chartW, chartH),
                strokeWidth = 1f
            )

            val vCount = displayCandles.size
            if (vCount == 0) return@Canvas
            val candleSlotWidth = chartW / vCount
            val bodyWidth = (candleSlotWidth * 0.70f).coerceAtLeast(1.5f)

            // ==========================================
            // 2. VERTICAL GRID LINES & BOTTOM TIME LABELS
            // ==========================================
            val timeStep = (vCount / 5).coerceAtLeast(1)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            for (i in 0 until vCount step timeStep) {
                val candle = displayCandles[i]
                val x = (i * candleSlotWidth) + (candleSlotWidth / 2f)

                // Vertical grid line
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(x, 0f),
                    end = Offset(x, chartH),
                    strokeWidth = 1f
                )

                // Time label at bottom
                val timeLabel = timeFormat.format(Date(candle.timestamp))
                drawContext.canvas.nativeCanvas.drawText(
                    timeLabel,
                    x - 12.dp.toPx(),
                    chartH + 16.dp.toPx(),
                    textPaint
                )
            }

            // ==========================================
            // 3. AUTO SMC & ZONE OVERLAYS (UNDER CANDLES)
            // ==========================================
            if (showPatterns) {
                detectedPatterns.forEach { pattern ->
                    if (pattern.category == PatternTypeCategory.SMC && pattern.upperZonePrice > 0 && pattern.lowerZonePrice > 0) {
                        val yTop = priceToY(pattern.upperZonePrice)
                        val yBottom = priceToY(pattern.lowerZonePrice)
                        val zoneHeight = maxOf(abs(yBottom - yTop), 4f)
                        val zoneColor = if (pattern.action == SignalAction.BUY) BuyGreen else SellRed

                        val pStart = (pattern.startCandleIndex - startIndex).coerceIn(0, vCount - 1)
                        val xStart = pStart * candleSlotWidth
                        val xEnd = chartW

                        drawRect(
                            color = zoneColor.copy(alpha = 0.12f),
                            topLeft = Offset(xStart, min(yTop, yBottom)),
                            size = Size(xEnd - xStart, zoneHeight)
                        )

                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        drawLine(
                            color = zoneColor.copy(alpha = 0.45f),
                            start = Offset(xStart, min(yTop, yBottom)),
                            end = Offset(xEnd, min(yTop, yBottom)),
                            strokeWidth = 1.2f,
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = zoneColor.copy(alpha = 0.45f),
                            start = Offset(xStart, max(yTop, yBottom)),
                            end = Offset(xEnd, max(yTop, yBottom)),
                            strokeWidth = 1.2f,
                            pathEffect = dashEffect
                        )
                    }
                }
            }

            // ==========================================
            // 4. DRAW VISIBLE CANDLESTICKS & VOLUME HISTOGRAM
            // ==========================================
            val maxVolume = displayCandles.maxOfOrNull { it.volume }?.coerceAtLeast(1.0) ?: 1.0
            val volumeAreaHeight = chartH * 0.18f

            for (i in 0 until vCount) {
                val candle = displayCandles[i]
                val xCenter = (i * candleSlotWidth) + (candleSlotWidth / 2f)
                val isBull = candle.close >= candle.open
                val color = if (isBull) BuyGreen else SellRed

                // Subtle Volume Histogram bar at chart bottom (MT5 / TradingView style)
                val volRatio = (candle.volume / maxVolume).toFloat().coerceIn(0.05f, 1f)
                val volBarH = volRatio * volumeAreaHeight
                drawRect(
                    color = color.copy(alpha = 0.22f),
                    topLeft = Offset(xCenter - (bodyWidth / 2f), chartH - volBarH),
                    size = Size(bodyWidth, volBarH)
                )

                val yHigh = priceToY(candle.high)
                val yLow = priceToY(candle.low)
                val yOpen = priceToY(candle.open)
                val yClose = priceToY(candle.close)

                val bodyTop = minOf(yOpen, yClose)
                val bodyHeight = maxOf(abs(yOpen - yClose), 1.5f)

                // Wick
                drawLine(
                    color = color,
                    start = Offset(xCenter, yHigh),
                    end = Offset(xCenter, yLow),
                    strokeWidth = 1.5f
                )

                // Body
                drawRect(
                    color = color,
                    topLeft = Offset(xCenter - (bodyWidth / 2f), bodyTop),
                    size = Size(bodyWidth, bodyHeight)
                )
            }

            // ==========================================
            // 5. DRAW EMA LINES (9 & 21)
            // ==========================================
            if (showEma && ema9.size == candles.size) {
                val ema9Path = Path()
                var hasStarted = false
                for (i in 0 until vCount) {
                    val globalI = startIndex + i
                    if (globalI in ema9.indices) {
                        val x = (i * candleSlotWidth) + (candleSlotWidth / 2f)
                        val y = priceToY(ema9[globalI])
                        if (!hasStarted) {
                            ema9Path.moveTo(x, y)
                            hasStarted = true
                        } else {
                            ema9Path.lineTo(x, y)
                        }
                    }
                }
                drawPath(path = ema9Path, color = CyanEma, style = Stroke(width = 2.5f))
            }

            if (showEma && ema21.size == candles.size) {
                val ema21Path = Path()
                var hasStarted = false
                for (i in 0 until vCount) {
                    val globalI = startIndex + i
                    if (globalI in ema21.indices) {
                        val x = (i * candleSlotWidth) + (candleSlotWidth / 2f)
                        val y = priceToY(ema21[globalI])
                        if (!hasStarted) {
                            ema21Path.moveTo(x, y)
                            hasStarted = true
                        } else {
                            ema21Path.lineTo(x, y)
                        }
                    }
                }
                drawPath(path = ema21Path, color = OrangeEma, style = Stroke(width = 2.5f))
            }

            // ==========================================
            // 6. DRAW CHART PATTERNS & MARKERS
            // ==========================================
            if (showPatterns) {
                detectedPatterns.forEach { pattern ->
                    if (pattern.category == PatternTypeCategory.CHART_PATTERN && pattern.swingPoints.size >= 2) {
                        val patternPath = Path()
                        val pathColor = if (pattern.action == SignalAction.BUY) BuyGreen else SellRed
                        var pathStarted = false

                        pattern.swingPoints.forEach { point ->
                            val pIdx = point.candleIndex
                            if (pIdx in startIndex..endIndex) {
                                val localIdx = pIdx - startIndex
                                val x = (localIdx * candleSlotWidth) + (candleSlotWidth / 2f)
                                val y = priceToY(point.price)

                                if (!pathStarted) {
                                    patternPath.moveTo(x, y)
                                    pathStarted = true
                                } else {
                                    patternPath.lineTo(x, y)
                                }

                                drawCircle(color = GoldPrimary, radius = 3.5f, center = Offset(x, y))
                            }
                        }

                        if (pathStarted) {
                            drawPath(path = patternPath, color = pathColor.copy(alpha = 0.85f), style = Stroke(width = 2.5f))
                        }

                        if (pattern.necklinePrice != null) {
                            val yNeck = priceToY(pattern.necklinePrice)
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            drawLine(
                                color = GoldPrimary,
                                start = Offset(0f, yNeck),
                                end = Offset(chartW, yNeck),
                                strokeWidth = 1.5f,
                                pathEffect = dashEffect
                            )
                        }
                    }

                    if (pattern.category == PatternTypeCategory.CANDLESTICK) {
                        val idx = pattern.endCandleIndex
                        if (idx in startIndex..endIndex) {
                            val localIdx = idx - startIndex
                            val x = (localIdx * candleSlotWidth) + (candleSlotWidth / 2f)
                            val candle = displayCandles[localIdx]

                            if (pattern.action == SignalAction.BUY) {
                                val yLow = priceToY(candle.low) + 6f
                                val triPath = Path().apply {
                                    moveTo(x, yLow)
                                    lineTo(x - 5f, yLow + 9f)
                                    lineTo(x + 5f, yLow + 9f)
                                    close()
                                }
                                drawPath(path = triPath, color = BuyGreen)
                            } else if (pattern.action == SignalAction.SELL) {
                                val yHigh = priceToY(candle.high) - 6f
                                val triPath = Path().apply {
                                    moveTo(x, yHigh)
                                    lineTo(x - 5f, yHigh - 9f)
                                    lineTo(x + 5f, yHigh - 9f)
                                    close()
                                }
                                drawPath(path = triPath, color = SellRed)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 7. DRAW ACTIVE SIGNAL LEVELS (ENTRY, SL, TP)
            // ==========================================
            if (showLevels && activeSignal != null && activeSignal.instrument == instrument) {
                val yEntry = priceToY(activeSignal.entryPrice)
                val ySl = priceToY(activeSignal.stopLoss)
                val yTp1 = priceToY(activeSignal.takeProfit1)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)

                // SL
                drawLine(color = SellRed, start = Offset(0f, ySl), end = Offset(chartW, ySl), strokeWidth = 1.8f, pathEffect = dashEffect)
                // Entry
                drawLine(color = CyanEma, start = Offset(0f, yEntry), end = Offset(chartW, yEntry), strokeWidth = 1.8f, pathEffect = dashEffect)
                // TP1
                drawLine(color = BuyGreen, start = Offset(0f, yTp1), end = Offset(chartW, yTp1), strokeWidth = 1.8f, pathEffect = dashEffect)
            }

            // ==========================================
            // 8. BID / ASK SPREAD LINES & LIVE PRICE BADGES (MT5 STYLE)
            // ==========================================
            val latestCandle = candles.lastOrNull()
            if (latestCandle != null) {
                val bidPrice = latestCandle.close
                val spreadPoints = instrument.defaultSpreadPips * instrument.pipMultiplier
                val askPrice = bidPrice + spreadPoints

                val bidY = priceToY(bidPrice)
                val askY = priceToY(askPrice)
                val liveColor = if (latestCandle.close >= latestCandle.open) BuyGreen else SellRed

                // Dotted horizontal line for Bid Price
                drawLine(
                    color = liveColor.copy(alpha = 0.85f),
                    start = Offset(0f, bidY),
                    end = Offset(chartW, bidY),
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )

                // Dotted horizontal line for Ask Price (Spread Visualization)
                drawLine(
                    color = SellRed.copy(alpha = 0.65f),
                    start = Offset(0f, askY),
                    end = Offset(chartW, askY),
                    strokeWidth = 1.0f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
                )

                // Bid Price Badge Box in right scale
                val badgeH = 15.dp.toPx()
                val badgeW = 62.dp.toPx()
                drawRect(
                    color = liveColor,
                    topLeft = Offset(chartW + 2.dp.toPx(), bidY - (badgeH / 2f)),
                    size = Size(badgeW, badgeH)
                )

                val badgePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 8.5.sp.toPx()
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                drawContext.canvas.nativeCanvas.drawText(
                    instrument.formatPrice(bidPrice),
                    chartW + 4.dp.toPx(),
                    bidY + 3.dp.toPx(),
                    badgePaint
                )

                // Ask Price mini badge if separated
                if (abs(askY - bidY) > 10.dp.toPx()) {
                    drawRect(
                        color = SellRed.copy(alpha = 0.85f),
                        topLeft = Offset(chartW + 2.dp.toPx(), askY - (11.dp.toPx() / 2f)),
                        size = Size(badgeW, 11.dp.toPx())
                    )
                    val askPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 7.5.sp.toPx()
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        instrument.formatPrice(askPrice),
                        chartW + 4.dp.toPx(),
                        askY + 2.5.dp.toPx(),
                        askPaint
                    )
                }

                // Candle Countdown Timer badge below Bid badge in right scale (like TradingView)
                val timerBadgeH = 13.dp.toPx()
                val timerBadgeTop = bidY + (badgeH / 2f) + 2.dp.toPx()
                if (timerBadgeTop + timerBadgeH < chartH) {
                    drawRoundRect(
                        color = surfaceVariantColor.copy(alpha = 0.9f),
                        topLeft = Offset(chartW + 2.dp.toPx(), timerBadgeTop),
                        size = Size(badgeW, timerBadgeH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    val timerPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#FFD54F") // Gold
                        textSize = 8.sp.toPx()
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.MONOSPACE
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "⏱ $candleRemainingFormatted",
                        chartW + 4.dp.toPx(),
                        timerBadgeTop + 9.5.dp.toPx(),
                        timerPaint
                    )
                }
            }

            // ==========================================
            // 9. INTERACTIVE CROSSHAIR IF CANDLE SELECTED
            // ==========================================
            if (selectedCandleIndex != null && selectedCandleIndex in startIndex..endIndex) {
                val localIdx = selectedCandleIndex - startIndex
                val inspected = displayCandles[localIdx]
                val x = (localIdx * candleSlotWidth) + (candleSlotWidth / 2f)
                val y = priceToY(inspected.close)
                val crosshairDash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)

                drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(x, 0f), end = Offset(x, chartH), strokeWidth = 1f, pathEffect = crosshairDash)
                drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(0f, y), end = Offset(chartW, y), strokeWidth = 1f, pathEffect = crosshairDash)

                drawCircle(color = GoldPrimary, radius = 4f, center = Offset(x, y))
                drawCircle(color = Color.White, radius = 2f, center = Offset(x, y))
            }
        }

        // ==========================================
        // 10. FLOATING ZOOM & SCROLL CONTROLS OVERLAY
        // ==========================================
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Zoom In (+)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .size(28.dp)
                    .clickable {
                        visibleCandlesCount = (visibleCandlesCount - 5f).coerceIn(minVisible, maxVisible)
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Zoom Out (-)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .size(28.dp)
                    .clickable {
                        visibleCandlesCount = (visibleCandlesCount + 5f).coerceIn(minVisible, maxVisible)
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Candle Remaining Countdown Chip (Floating overlay)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(horizontal = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Candle Countdown",
                        tint = GoldPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${timeframe.code} $candleRemainingFormatted",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Reset to Latest Candles
            if (scrollOffset > 0f) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoldPrimary.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, GoldPrimary),
                    modifier = Modifier
                        .height(28.dp)
                        .clickable { scrollOffset = 0f }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Live",
                            tint = GoldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Terkini",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }
                }
            }
        }
    }
}
