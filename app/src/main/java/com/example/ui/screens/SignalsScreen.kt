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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SignalEntity
import com.example.data.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        // News Shield / Economic News Banner
        if (newsShield != null) {
            item {
                if (newsShield.isShieldActive) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SellRed.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, SellRed.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCalendar() }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SellRed
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Peringatan Berita",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(16.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "NEWS SHIELD AKTIF (High Impact)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SellRed
                                )
                                Text(
                                    text = newsShield.warningMessage,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Buka Kalender",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else if (newsShield.currentEvent != null && newsShield.minutesUntil <= 120) {
                    val ev = newsShield.currentEvent
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCalendar() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = ev.getStatusLabel(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                        }
                    }
                }
            }
        }

        // Hero Active Signal Card
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

        // Real-Time Indicators Meter
        item {
            indicators?.let {
                TechnicalIndicatorMeter(indicators = it, instrument = selectedInstrument)
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
                    Text(
                        text = "Riwayat Sinyal ${selectedInstrument.symbol}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "M5 Live Scalp Feed",
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

        // Filter history by instrument
        val filteredHistory = signalHistory.filter { it.instrumentSymbol == selectedInstrument.symbol }
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
                    text = "Menganalisis Pola Indikator Scalping...",
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

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, actionColor.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .testTag("card_active_signal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Action Badge (BUY / SELL)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(actionColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isBuy) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = signal.action.badgeText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Strength Tag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = actionBg
                    ) {
                        Text(
                            text = signal.strength.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = actionColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Confidence Rating
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Breakeven / Move SL to Entry Alert if TP1 is hit
            val isTp1Hit = if (isBuy) currentPrice >= signal.takeProfit1 else currentPrice <= signal.takeProfit1
            if (isTp1Hit) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BuyGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, BuyGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = BuyGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "TARGET TP1 TELAH TERCAPAI!",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = BuyGreen
                            )
                            Text(
                                text = "Geser Stop Loss ke harga Entry (${instrument.formatPrice(signal.entryPrice)}) untuk mengunci posisi tanpa risiko (Risk-Free Trade).",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Current Price vs Entry Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Harga Masuk (Entry)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = instrument.formatPrice(signal.entryPrice),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Harga Live Sekarang",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = instrument.formatPrice(currentPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = actionColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Automatic Stop Loss & Take Profit Target Table
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // SL Row
                    TargetPriceRow(
                        label = "Stop Loss (SL)",
                        price = instrument.formatPrice(signal.stopLoss),
                        pips = "-${"%.1f".format(signal.slPips)} pips",
                        color = SellRed
                    )

                    Divider(color = DarkBorder, thickness = 0.5.dp)

                    // TP1 Row
                    TargetPriceRow(
                        label = "Take Profit 1 (R:R 1:1)",
                        price = instrument.formatPrice(signal.takeProfit1),
                        pips = "+${"%.1f".format(signal.tp1Pips)} pips",
                        color = BuyGreen
                    )

                    // TP2 Row
                    TargetPriceRow(
                        label = "Take Profit 2 (R:R 1:1.8)",
                        price = instrument.formatPrice(signal.takeProfit2),
                        pips = "+${"%.1f".format(signal.tp2Pips)} pips",
                        color = BuyGreen
                    )

                    // TP3 Row
                    TargetPriceRow(
                        label = "Take Profit 3 (R:R 1:2.6)",
                        price = instrument.formatPrice(signal.takeProfit3),
                        pips = "+${"%.1f".format(signal.tp3Pips)} pips",
                        color = BuyGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Signal Analysis Reason
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyanEma,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = signal.reason,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Copy Signal Button
                OutlinedButton(
                    onClick = {
                        val text = """
                            ${signal.action.badgeText} ${instrument.symbol}
                            Entry: ${instrument.formatPrice(signal.entryPrice)}
                            SL: ${instrument.formatPrice(signal.stopLoss)}
                            TP1: ${instrument.formatPrice(signal.takeProfit1)}
                            TP2: ${instrument.formatPrice(signal.takeProfit2)}
                            TP3: ${instrument.formatPrice(signal.takeProfit3)}
                        """.trimIndent()
                        onCopySignal(text, "Sinyal ${instrument.symbol}")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_copy_signal"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Salin SL/TP", fontSize = 12.sp)
                }

                // Apply to Risk Manager Button
                Button(
                    onClick = { onApplyToRiskManager(signal) },
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("btn_apply_risk"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Hitung Lot & Risiko", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TargetPriceRow(label: String, price: String, pips: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary
        )
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
                // EMA 9/21
                IndicatorStatBox(
                    title = "EMA 9 / 21",
                    value = if (indicators.emaFast > indicators.emaSlow) "Bullish Cross" else "Bearish Cross",
                    color = if (indicators.emaFast > indicators.emaSlow) BuyGreen else SellRed,
                    modifier = Modifier.weight(1f)
                )

                // MACD
                IndicatorStatBox(
                    title = "MACD Momentum",
                    value = if (indicators.macdHist >= 0) "Positif (+)" else "Negatif (-)",
                    color = if (indicators.macdHist >= 0) BuyGreen else SellRed,
                    modifier = Modifier.weight(1f)
                )

                // Volatilitas (ATR)
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
fun SignalHistoryItem(
    entity: SignalEntity,
    instrument: TradingInstrument,
    onCopy: (String, String) -> Unit
) {
    val isBuy = entity.action == "BUY"
    val actionColor = if (isBuy) BuyGreen else SellRed
    val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entity.timestamp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                        fontSize = 10.sp,
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
                        text = "SL: ${instrument.formatPrice(entity.stopLoss)} | TP1: ${instrument.formatPrice(entity.takeProfit1)} (R:R 1:${"%.1f".format(entity.riskReward)})",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            IconButton(
                onClick = {
                    val txt = "Sinyal ${entity.action} ${entity.instrumentSymbol}\nEntry: ${instrument.formatPrice(entity.entryPrice)}\nSL: ${instrument.formatPrice(entity.stopLoss)}\nTP: ${instrument.formatPrice(entity.takeProfit1)}"
                    onCopy(txt, "Sinyal ${entity.instrumentSymbol}")
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Salin Sinyal",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
