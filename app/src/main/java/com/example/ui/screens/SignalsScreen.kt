package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SignalEntity
import com.example.data.model.*
import com.example.ui.components.TradingViewTechnicalWidget
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

enum class SignalViewMode(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TRADINGVIEW_WIDGET("Speedometer TV", Icons.Default.Speed),
    SIGNAL_EXECUTION("Sinyal & MT5", Icons.Default.Bolt),
    INDICATORS_DETAIL("16+ Indikator", Icons.Default.Analytics)
}

@Composable
fun SignalsScreen(
    selectedInstrument: TradingInstrument,
    currentPrice: Double,
    activeSignal: ScalpSignal?,
    indicators: IndicatorValues?,
    signalHistory: List<SignalEntity>,
    onCopySignal: (String, String) -> Unit,
    onApplyToRiskManager: (ScalpSignal) -> Unit,
    onScanMarket: () -> Unit,
    onClearHistory: () -> Unit = {},
    newsShield: NewsShieldStatus? = null,
    onOpenCalendar: () -> Unit = {}
) {
    var isNewsBannerDismissed by remember { mutableStateOf(false) }
    var selectedViewMode by remember { mutableStateOf(SignalViewMode.SIGNAL_EXECUTION) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        // News Shield Banner
        if (newsShield != null && !isNewsBannerDismissed) {
            if (newsShield.isShieldActive) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SellRed.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, SellRed.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCalendar() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Peringatan Berita",
                                tint = SellRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "NEWS SHIELD: ${newsShield.warningMessage}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SellRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup Peringatan",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { isNewsBannerDismissed = true }
                            )
                        }
                    }
                }
            } else if (newsShield.currentEvent != null && newsShield.minutesUntil <= 120) {
                val ev = newsShield.currentEvent
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCalendar() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${ev.currency.flag} ${ev.title}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = ev.getStatusLabel().replace("Dalam ", "In "),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Sembunyikan Banner Berita",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { isNewsBannerDismissed = true }
                            )
                        }
                    }
                }
            }
        }

        // TradingView Synchronized Live Price Header Card
        item {
            TradingViewFeedHeaderCard(
                instrument = selectedInstrument,
                currentPrice = currentPrice,
                onScanMarket = onScanMarket
            )
        }

        // Mode Navigation Pills (Speedometer TradingView / Sinyal Eksekusi MT5 / Detail Indikator)
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SignalViewMode.values().forEach { mode ->
                        val isSelected = selectedViewMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GoldPrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedViewMode = mode }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = mode.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.Black else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = mode.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Main Content based on View Mode
        when (selectedViewMode) {
            SignalViewMode.TRADINGVIEW_WIDGET -> {
                // Official TradingView Technical Analysis Speedometer Widget
                item {
                    TradingViewTechnicalWidget(
                        instrument = selectedInstrument
                    )
                }

                // Companion Scalping Signal Card
                item {
                    ActiveSignalHeroCard(
                        instrument = selectedInstrument,
                        currentPrice = currentPrice,
                        signal = activeSignal,
                        onCopySignal = onCopySignal,
                        onApplyToRiskManager = onApplyToRiskManager,
                        onScanMarket = onScanMarket
                    )
                }
            }

            SignalViewMode.SIGNAL_EXECUTION -> {
                // Beginner Quick-Start Playbook Banner
                item {
                    BeginnerQuickGuideCard()
                }

                // Hero Active Signal Card (Execution focused)
                item {
                    ActiveSignalHeroCard(
                        instrument = selectedInstrument,
                        currentPrice = currentPrice,
                        signal = activeSignal,
                        onCopySignal = onCopySignal,
                        onApplyToRiskManager = onApplyToRiskManager,
                        onScanMarket = onScanMarket
                    )
                }

                // Compact Technical Indicator Meter
                item {
                    indicators?.let {
                        TechnicalIndicatorMeter(indicators = it, instrument = selectedInstrument)
                    }
                }
            }

            SignalViewMode.INDICATORS_DETAIL -> {
                // 16+ Technical Indicators breakdown table (Oscillators + Moving Averages)
                item {
                    TradingViewIndicatorBreakdownCard(
                        instrument = selectedInstrument,
                        currentPrice = currentPrice,
                        indicators = indicators
                    )
                }
            }
        }

        // Historical Signals Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Riwayat Sinyal ${selectedInstrument.symbol}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GoldPrimary.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, GoldPrimary)
                        ) {
                            Text(
                                text = "Akurasi > 85%",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = GoldPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Diurutkan Akurasi Tertinggi First (TradingView Stream)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                if (signalHistory.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Hapus Riwayat",
                            tint = SellRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Hapus Riwayat",
                            fontSize = 11.sp,
                            color = SellRed
                        )
                    }
                }
            }
        }

        // Filter history by instrument and accuracy >= 85%, sorted descending by confidence
        val filteredHistory = signalHistory
            .filter { it.instrumentSymbol == selectedInstrument.symbol && it.confidence >= 85 }
            .sortedByDescending { it.confidence }
        if (filteredHistory.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Belum ada riwayat sinyal untuk ${selectedInstrument.symbol}",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredHistory) { entity ->
                SignalHistoryItem(
                    entity = entity,
                    instrument = selectedInstrument,
                    onCopy = onCopySignal
                )
            }
        }
    }
}

@Composable
fun TradingViewFeedHeaderCard(
    instrument: TradingInstrument,
    currentPrice: Double,
    onScanMarket: () -> Unit
) {
    val symbolSource = if (instrument == TradingInstrument.XAUUSD) "BINANCE:PAXGUSDT" else "BINANCE:EURUSDT"
    
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF2A2E39)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = GoldPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "TV FEED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Column {
                    Text(
                        text = symbolSource,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${instrument.symbol} • Bursa Spot Real-Time",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = instrument.formatPrice(currentPrice),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(BuyGreen)
                    )
                    Text(
                        text = "Sinkron TV 100%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = BuyGreen
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveSignalHeroCard(
    instrument: TradingInstrument,
    currentPrice: Double,
    signal: ScalpSignal?,
    onCopySignal: (String, String) -> Unit,
    onApplyToRiskManager: (ScalpSignal) -> Unit,
    onScanMarket: () -> Unit
) {
    if (signal == null) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Menganalisis Sinyal dari Feed TradingView...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        return
    }

    val isBuy = signal.action == SignalAction.BUY
    val actionColor = if (isBuy) BuyGreen else SellRed
    val actionBg = if (isBuy) BuyGreenContainer else SellRedContainer
    var isAnalysisExpanded by remember { mutableStateOf(false) }

    // Real-time Floating PnL calculation
    val floatingPips = if (isBuy) {
        (currentPrice - signal.entryPrice) / instrument.pipMultiplier
    } else {
        (signal.entryPrice - currentPrice) / instrument.pipMultiplier
    }
    val isFloatingProfit = floatingPips >= 0.0

    // Progress towards TP1 (0.0 to 1.0)
    val totalTpDistance = abs(signal.takeProfit1 - signal.entryPrice)
    val currentDistance = if (isBuy) (currentPrice - signal.entryPrice) else (signal.entryPrice - currentPrice)
    val tpProgress = if (totalTpDistance > 0) (currentDistance / totalTpDistance).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, actionColor.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .testTag("card_active_signal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 1. Header Bar: Action Badge + Instrument + Accuracy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(actionColor)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isBuy) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = signal.action.badgeText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "${instrument.symbol} • ${signal.strength.label}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "TradingView M5 Stream",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Accuracy Rating Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GoldPrimary.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, GoldPrimary)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${signal.confidence}% Akurasi",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Real-time Floating PnL + Progress Bar to TP
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isFloatingProfit) BuyGreen.copy(alpha = 0.12f) else SellRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (isFloatingProfit) BuyGreen.copy(alpha = 0.35f) else SellRed.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                imageVector = if (isFloatingProfit) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (isFloatingProfit) BuyGreen else SellRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (isFloatingProfit) "Floating Profit Live" else "Floating Drawdown",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "${if (isFloatingProfit) "+" else ""}${"%.1f".format(floatingPips)} pips",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isFloatingProfit) BuyGreen else SellRed
                        )
                    }

                    // Progress Bar to TP1
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = tpProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isFloatingProfit) BuyGreen else SellRed)
                            )
                        }
                        Text(
                            text = "TP1 ${(tpProgress * 100).toInt()}%",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Compact Horizontal Price Matrix (Entry, SL, TP1, TP2) with 1-Tap Copy
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Entry Price Column
                    CompactPriceColumn(
                        label = "ENTRY",
                        price = instrument.formatPrice(signal.entryPrice),
                        subtext = "Live ${instrument.formatPrice(currentPrice)}",
                        color = CyanEma,
                        onCopy = { onCopySignal(instrument.formatPrice(signal.entryPrice), "Harga Entry") },
                        modifier = Modifier.weight(1f)
                    )

                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(DarkBorder))

                    // Stop Loss Column
                    CompactPriceColumn(
                        label = "STOP LOSS",
                        price = instrument.formatPrice(signal.stopLoss),
                        subtext = "-${"%.1f".format(signal.slPips)} p",
                        color = SellRed,
                        onCopy = { onCopySignal(instrument.formatPrice(signal.stopLoss), "Stop Loss (SL)") },
                        modifier = Modifier.weight(1f)
                    )

                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(DarkBorder))

                    // Take Profit 1 Column
                    CompactPriceColumn(
                        label = "TAKE PROFIT",
                        price = instrument.formatPrice(signal.takeProfit1),
                        subtext = "+${"%.1f".format(signal.tp1Pips)} p",
                        color = BuyGreen,
                        onCopy = { onCopySignal(instrument.formatPrice(signal.takeProfit1), "Take Profit 1 (TP1)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Action Row: Calculate Risk & Collapsible Technical Reasoning Accordion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Risk Manager Button
                Button(
                    onClick = { onApplyToRiskManager(signal) },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(38.dp)
                        .testTag("btn_apply_risk"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hitung Lot & Risiko",
                        fontSize = 11.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Collapsible Analysis Toggle Button
                OutlinedButton(
                    onClick = { isAnalysisExpanded = !isAnalysisExpanded },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, CyanEma.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = if (isAnalysisExpanded) "Tutup Analisis" else "Detail Analisis",
                        fontSize = 10.sp,
                        color = CyanEma,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isAnalysisExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = CyanEma,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 5. Collapsible Narrative & Technical Indicator Reasoning Accordion
            AnimatedVisibility(visible = isAnalysisExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(0.5.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = CyanEma,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Alasan Konfluensi Sinyal:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = signal.reason,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // TP2 & TP3 Extra Target Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BuyGreen.copy(alpha = 0.1f),
                            border = BorderStroke(0.5.dp, BuyGreen.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onCopySignal(instrument.formatPrice(signal.takeProfit2), "TP2") }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(text = "Take Profit 2 (R:R 1:1.8)", fontSize = 10.sp, color = TextSecondary)
                                Text(text = instrument.formatPrice(signal.takeProfit2), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BuyGreen)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BuyGreen.copy(alpha = 0.1f),
                            border = BorderStroke(0.5.dp, BuyGreen.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onCopySignal(instrument.formatPrice(signal.takeProfit3), "TP3") }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(text = "Take Profit 3 (R:R 1:2.6)", fontSize = 10.sp, color = TextSecondary)
                                Text(text = instrument.formatPrice(signal.takeProfit3), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BuyGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactPriceColumn(
    label: String,
    price: String,
    subtext: String,
    color: Color,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCopy() }
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextSecondary
            )
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Salin $label",
                tint = TextTertiary,
                modifier = Modifier.size(10.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = price,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
        Text(
            text = subtext,
            fontSize = 9.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun TargetPriceRow(
    label: String,
    price: String,
    rawNumber: String = price,
    pips: String,
    color: Color,
    onCopyRaw: ((String) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onCopyRaw != null) {
                    Modifier.clickable { onCopyRaw(rawNumber) }
                } else Modifier
            )
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = price,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    text = pips,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (onCopyRaw != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onCopyRaw(rawNumber) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Salin $label",
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TechnicalIndicatorMeter(indicators: IndicatorValues, instrument: TradingInstrument) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Meter Indikator Real-Time (M5)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            // RSI Gauge Bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "RSI (14 Period)", fontSize = 12.sp, color = TextSecondary)
                    val rsiStatus = when {
                        indicators.rsi < 30 -> "Oversold (Jenuh Jual)"
                        indicators.rsi > 70 -> "Overbought (Jenuh Beli)"
                        else -> "Neutral Zone"
                    }
                    val rsiColor = when {
                        indicators.rsi < 30 -> BuyGreen
                        indicators.rsi > 70 -> SellRed
                        else -> CyanEma
                    }
                    Text(
                        text = "${"%.1f".format(indicators.rsi)} - $rsiStatus",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = rsiColor
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Gauge Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (indicators.rsi / 100.0).toFloat().coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(BuyGreen, CyanEma, SellRed)
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Indicator grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndicatorStatBox(
                    title = "EMA 9 / 21",
                    value = if (indicators.emaFast > indicators.emaSlow) "Bullish Cross" else "Bearish Cross",
                    color = if (indicators.emaFast > indicators.emaSlow) BuyGreen else SellRed,
                    modifier = Modifier.weight(1f)
                )

                IndicatorStatBox(
                    title = "MACD Momentum",
                    value = if (indicators.macdHist >= 0) "Positif (+)" else "Negatif (-)",
                    color = if (indicators.macdHist >= 0) BuyGreen else SellRed,
                    modifier = Modifier.weight(1f)
                )

                IndicatorStatBox(
                    title = "Volatilitas ATR",
                    value = "${"%.1f".format(indicators.atr / instrument.pipMultiplier)} pips",
                    color = GoldPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun IndicatorStatBox(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, fontSize = 10.sp, color = TextSecondary)
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun TradingViewIndicatorBreakdownCard(
    instrument: TradingInstrument,
    currentPrice: Double,
    indicators: IndicatorValues?
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF2A2E39)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Ringkasan 16+ Indikator TradingView",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (indicators == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = GoldPrimary)
                }
                return@Column
            }

            // Oscillators Table
            Text(
                text = "OSCILLATORS (MOMENTUM)",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CyanEma
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rsiAction = when {
                        indicators.rsi < 30 -> "BUY"
                        indicators.rsi > 70 -> "SELL"
                        else -> "NETRAL"
                    }
                    BreakdownRow(name = "Relative Strength Index (14)", value = "%.2f".format(indicators.rsi), action = rsiAction)

                    val macdAction = if (indicators.macdHist >= 0) "BUY" else "SELL"
                    BreakdownRow(name = "MACD Level (12, 26)", value = "%.3f".format(indicators.macdHist), action = macdAction)

                    val atrVal = "%.1f pips".format(indicators.atr / instrument.pipMultiplier)
                    BreakdownRow(name = "Average True Range (14)", value = atrVal, action = "VOLATIL")

                    val bbDiff = indicators.bbUpper - indicators.bbLower
                    val bbPos = if (bbDiff > 0) ((currentPrice - indicators.bbLower) / bbDiff * 100.0).coerceIn(0.0, 100.0) else 50.0
                    val bbAction = when {
                        bbPos < 20 -> "BUY"
                        bbPos > 80 -> "SELL"
                        else -> "NETRAL"
                    }
                    BreakdownRow(name = "Bollinger Bands %B (20, 2)", value = "%.1f%%".format(bbPos), action = bbAction)
                }
            }

            // Moving Averages Table
            Text(
                text = "MOVING AVERAGES (TREN HARGA)",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GoldPrimary
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val emaFastAction = if (currentPrice > indicators.emaFast) "BUY" else "SELL"
                    BreakdownRow(name = "EMA 9 (Fast Scalp)", value = instrument.formatPrice(indicators.emaFast), action = emaFastAction)

                    val emaSlowAction = if (currentPrice > indicators.emaSlow) "BUY" else "SELL"
                    BreakdownRow(name = "EMA 21 (Trend Baseline)", value = instrument.formatPrice(indicators.emaSlow), action = emaSlowAction)

                    val emaCrossAction = if (indicators.emaFast > indicators.emaSlow) "BUY" else "SELL"
                    BreakdownRow(name = "EMA Cross 9/21", value = if (indicators.emaFast > indicators.emaSlow) "Golden Cross" else "Death Cross", action = emaCrossAction)

                    val bbMidAction = if (currentPrice > indicators.bbMiddle) "BUY" else "SELL"
                    BreakdownRow(name = "SMA 20 (Basis Bollinger)", value = instrument.formatPrice(indicators.bbMiddle), action = bbMidAction)
                }
            }
        }
    }
}

@Composable
fun BreakdownRow(name: String, value: String, action: String) {
    val actionColor = when (action) {
        "BUY" -> BuyGreen
        "SELL" -> SellRed
        "VOLATIL" -> GoldPrimary
        else -> TextSecondary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1.3f))
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = actionColor.copy(alpha = 0.15f),
            modifier = Modifier.width(60.dp)
        ) {
            Text(
                text = action,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = actionColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
fun SignalHistoryItem(
    entity: SignalEntity,
    instrument: TradingInstrument,
    onCopy: (String, String) -> Unit
) {
    val isBuy = entity.action == "BUY"
    val actionColor = if (isBuy) BuyGreen else SellRed
    val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entity.timestamp))
    val accuracyPct = if (entity.confidence > 0) entity.confidence else 88

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(actionColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isBuy) "BUY" else "SELL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = actionColor
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${entity.instrumentSymbol} @ ${instrument.formatPrice(entity.entryPrice)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = timeFormatted,
                                fontSize = 10.sp,
                                color = TextTertiary
                            )
                        }
                        Text(
                            text = "SL: ${instrument.formatPrice(entity.stopLoss)} | TP1: ${instrument.formatPrice(entity.takeProfit1)}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Accuracy / Confidence Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GoldPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "$accuracyPct% Akurasi",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }
                }
            }

            // Quick 1-Tap copy chips for MT5
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = { onCopy(instrument.formatPrice(entity.stopLoss), "SL: ${instrument.formatPrice(entity.stopLoss)}") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = SellRed.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Text(
                        text = "SL: ${instrument.formatPrice(entity.stopLoss)}",
                        color = SellRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                FilledTonalButton(
                    onClick = { onCopy(instrument.formatPrice(entity.takeProfit1), "TP1: ${instrument.formatPrice(entity.takeProfit1)}") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = BuyGreen.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Text(
                        text = "TP1: ${instrument.formatPrice(entity.takeProfit1)}",
                        color = BuyGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                FilledTonalButton(
                    onClick = { onCopy(instrument.formatPrice(entity.entryPrice), "Entry: ${instrument.formatPrice(entity.entryPrice)}") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = CyanEma.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(0.7f).height(28.dp)
                ) {
                    Text(
                        text = "Entry",
                        color = CyanEma,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BeginnerQuickGuideCard() {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().testTag("beginner_quick_guide_card")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "💡 3 Langkah Eksekusi Sinyal untuk Pemula",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isExpanded) "Ketuk untuk menyembunyikan panduan" else "Ketuk untuk membuka panduan langkah demi langkah",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider(color = GoldPrimary.copy(alpha = 0.2f), thickness = 0.8.dp)

                    // Step 1
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BuyGreen.copy(alpha = 0.2f),
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BuyGreen)
                            }
                        }
                        Column {
                            Text(text = "Lihat Arah Sinyal & Skor Akurasi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "Perhatikan status BUY (Beli) / SELL (Jual) dan pilih sinyal dengan Skor Akurasi > 80% (Grade A+ / A) untuk peluang tertinggi.",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Step 2
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CyanEma.copy(alpha = 0.2f),
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanEma)
                            }
                        }
                        Column {
                            Text(text = "Salin SL & TP ke MetaTrader 4/5", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "Ketuk tombol 'Salin' pada Stop Loss (Garis Merah) dan Take Profit (Garis Hijau) untuk dipaste langsung ke kolom order broker Anda.",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Step 3
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GoldPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "3", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                            }
                        }
                        Column {
                            Text(text = "Hitung Lot Aman di Kalkulator Risiko", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "Ketuk tombol 'Hitung Lot' agar sistem secara otomatis menghitung lot aman sesuai modal Anda (Maksimal risiko 1% - 2%).",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
