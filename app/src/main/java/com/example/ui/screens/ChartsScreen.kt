package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.service.IndicatorCalculator
import com.example.service.PatternRecognitionEngine
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    onApplyPatternToRisk: (DetectedPattern) -> Unit = {}
) {
    var selectedCandleIndex by remember { mutableStateOf<Int?>(null) }
    var showSpreadInfoDialog by remember { mutableStateOf(false) }
    var showConfluenceInfoDialog by remember { mutableStateOf(false) }

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Controls Row: Timeframe Pills (M1, M5, M15, M30, H1)
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

        // 1.B Multi-Timeframe Trend Matrix Card
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Multi-Timeframe Trend Matrix",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Macro Bias Summary
                        val bullCount = multiTimeframeTrends.values.count { it == TrendDirection.BULLISH }
                        val bearCount = multiTimeframeTrends.values.count { it == TrendDirection.BEARISH }
                        val (macroLabel, macroColor) = when {
                            bullCount >= 3 -> "BIAS: BULLISH ($bullCount/5)" to BuyGreen
                            bearCount >= 3 -> "BIAS: BEARISH ($bearCount/5)" to SellRed
                            else -> "BIAS: NETRAL / MIXED" to GoldPrimary
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = macroColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, macroColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = macroLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = macroColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 5 Timeframe Status Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Timeframe.values().forEach { tf ->
                            val trend = multiTimeframeTrends[tf] ?: TrendDirection.NEUTRAL
                            val isCurrentTf = tf == timeframe
                            val (badgeColor, arrowIcon) = when (trend) {
                                TrendDirection.BULLISH -> BuyGreen to Icons.Default.TrendingUp
                                TrendDirection.BEARISH -> SellRed to Icons.Default.TrendingDown
                                TrendDirection.NEUTRAL -> GoldPrimary to Icons.Default.SwapHoriz
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentTf) badgeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isCurrentTf) BorderStroke(1.5.dp, badgeColor) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectTimeframe(tf) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = tf.code,
                                        fontSize = 10.sp,
                                        fontWeight = if (isCurrentTf) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrentTf) badgeColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Icon(
                                            imageVector = arrowIcon,
                                            contentDescription = null,
                                            tint = badgeColor,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = trend.code,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Secondary Overlays Row (EMA & SL/TP Toggles)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        label = { Text("Garis SL/TP", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BuyGreen.copy(alpha = 0.2f),
                            selectedLabelColor = BuyGreen
                        )
                    )
                }

                // Active pattern count badge
                if (allDetectedPatterns.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GoldPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${allDetectedPatterns.size} Pola Terdeteksi",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                        }
                    }
                }
            }
        }

        // 3. Selected Candle Tooltip Bar
        item {
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
        }

        // 4. Custom Candlestick Canvas with Auto-Draw Patterns
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
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
                            detectedPatterns = allDetectedPatterns,
                            showEma = showEma,
                            showBollinger = showBollinger,
                            showLevels = showLevels,
                            showPatterns = showPatterns,
                            selectedCandleIndex = selectedCandleIndex,
                            onCandleSelected = { idx -> selectedCandleIndex = idx }
                        )
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

        // ==========================================
        // DRAW AUTO SMC & ZONE OVERLAYS (UNDER CANDLES)
        // ==========================================
        if (showPatterns) {
            detectedPatterns.forEach { pattern ->
                if (pattern.category == PatternTypeCategory.SMC && pattern.upperZonePrice > 0 && pattern.lowerZonePrice > 0) {
                    val yTop = priceToY(pattern.upperZonePrice)
                    val yBottom = priceToY(pattern.lowerZonePrice)
                    val zoneHeight = maxOf(abs(yBottom - yTop), 6f)
                    val zoneColor = if (pattern.action == SignalAction.BUY) BuyGreen else SellRed

                    val xStart = (pattern.startCandleIndex.coerceIn(0, count - 1) * candleSlotWidth)
                    val xEnd = w

                    // Draw shaded zone box
                    drawRect(
                        color = zoneColor.copy(alpha = 0.12f),
                        topLeft = Offset(xStart, min(yTop, yBottom)),
                        size = Size(xEnd - xStart, zoneHeight)
                    )

                    // Draw dashed border on zone
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    drawLine(
                        color = zoneColor.copy(alpha = 0.5f),
                        start = Offset(xStart, min(yTop, yBottom)),
                        end = Offset(xEnd, min(yTop, yBottom)),
                        strokeWidth = 1.5f,
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = zoneColor.copy(alpha = 0.5f),
                        start = Offset(xStart, max(yTop, yBottom)),
                        end = Offset(xEnd, max(yTop, yBottom)),
                        strokeWidth = 1.5f,
                        pathEffect = dashEffect
                    )
                }
            }
        }

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
            val bodyHeight = maxOf(abs(yOpen - yClose), 2f)

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

        // Draw EMA lines
        if (showEma && ema9.size == candles.size) {
            val ema9Path = Path()
            for (i in ema9.indices) {
                val x = (i * candleSlotWidth) + (candleSlotWidth / 2f)
                val y = priceToY(ema9[i])
                if (i == 0) ema9Path.moveTo(x, y) else ema9Path.lineTo(x, y)
            }
            drawPath(path = ema9Path, color = CyanEma, style = Stroke(width = 2.5f))
        }

        if (showEma && ema21.size == candles.size) {
            val ema21Path = Path()
            for (i in ema21.indices) {
                val x = (i * candleSlotWidth) + (candleSlotWidth / 2f)
                val y = priceToY(ema21[i])
                if (i == 0) ema21Path.moveTo(x, y) else ema21Path.lineTo(x, y)
            }
            drawPath(path = ema21Path, color = OrangeEma, style = Stroke(width = 2.5f))
        }

        // ==========================================
        // DRAW AUTO CHART PATTERNS & CANDLESTICK MARKERS
        // ==========================================
        if (showPatterns) {
            detectedPatterns.forEach { pattern ->
                // A. Draw Chart Pattern Swing Lines (Double Top, Double Bottom, H&S, Flag)
                if (pattern.category == PatternTypeCategory.CHART_PATTERN && pattern.swingPoints.size >= 2) {
                    val patternPath = Path()
                    val pathColor = if (pattern.action == SignalAction.BUY) BuyGreen else SellRed

                    pattern.swingPoints.forEachIndexed { idx, point ->
                        val pIdx = point.candleIndex.coerceIn(0, count - 1)
                        val x = (pIdx * candleSlotWidth) + (candleSlotWidth / 2f)
                        val y = priceToY(point.price)

                        if (idx == 0) {
                            patternPath.moveTo(x, y)
                        } else {
                            patternPath.lineTo(x, y)
                        }

                        // Draw vertex circle dot
                        drawCircle(
                            color = GoldPrimary,
                            radius = 3.5f,
                            center = Offset(x, y)
                        )
                    }

                    // Stroke the swing lines connecting peaks and valleys
                    drawPath(
                        path = patternPath,
                        color = pathColor.copy(alpha = 0.85f),
                        style = Stroke(width = 2.5f)
                    )

                    // Draw Neckline if available
                    if (pattern.necklinePrice != null) {
                        val yNeck = priceToY(pattern.necklinePrice)
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                        drawLine(
                            color = GoldPrimary,
                            start = Offset(0f, yNeck),
                            end = Offset(w, yNeck),
                            strokeWidth = 2f,
                            pathEffect = dashEffect
                        )
                    }
                }

                // B. Draw Candlestick Pattern Indicator Triangles / Markers
                if (pattern.category == PatternTypeCategory.CANDLESTICK) {
                    val idx = pattern.endCandleIndex.coerceIn(0, count - 1)
                    val x = (idx * candleSlotWidth) + (candleSlotWidth / 2f)
                    val candle = candles[idx]

                    if (pattern.action == SignalAction.BUY) {
                        val yLow = priceToY(candle.low) + 6f
                        // Upward green triangle under candle
                        val triPath = Path().apply {
                            moveTo(x, yLow)
                            lineTo(x - 5f, yLow + 9f)
                            lineTo(x + 5f, yLow + 9f)
                            close()
                        }
                        drawPath(path = triPath, color = BuyGreen)
                    } else if (pattern.action == SignalAction.SELL) {
                        val yHigh = priceToY(candle.high) - 6f
                        // Downward red triangle above candle
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

            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f,
                pathEffect = crosshairDash
            )

            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
                pathEffect = crosshairDash
            )

            drawCircle(color = GoldPrimary, radius = 4.5f, center = Offset(x, y))
            drawCircle(color = Color.White, radius = 2f, center = Offset(x, y))
        }
    }
}
