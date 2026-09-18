package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScalpSignal
import com.example.data.model.SignalAction
import com.example.data.model.TradingInstrument
import com.example.ui.theme.*

@Composable
fun AppHeader(
    notificationsEnabled: Boolean,
    isLiveOnline: Boolean = true,
    latencyMs: Long = 0L,
    marketSession: com.example.service.MarketSession? = null,
    onToggleNotifications: (Boolean) -> Unit,
    onManualScan: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(GoldPrimary, Color(0xFFD97706))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "ScalpSignal Logo",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "ScalpSignal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // 100% Free Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(FreeBadgeBg)
                                .border(1.dp, FreeBadgeGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "100% GRATIS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FreeBadgeGreen
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Pulsing Live dot
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotAlpha"
                        )
                        val dotColor = if (isLiveOnline) BuyGreen else GoldPrimary
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor.copy(alpha = alpha))
                        )
                        val liveText = if (isLiveOnline) {
                            val latencyText = if (latencyMs > 0) " • ${latencyMs}ms" else ""
                            val sessionText = marketSession?.label?.let { " • $it" } ?: ""
                            "Live Feed$latencyText$sessionText"
                        } else {
                            "Menghubungkan Feed Pasar..."
                        }
                        Text(
                            text = liveText,
                            fontSize = 11.sp,
                            color = if (isLiveOnline) BuyGreen else GoldPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Refresh / Scan Button
                IconButton(
                    onClick = onManualScan,
                    modifier = Modifier.testTag("btn_manual_scan")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan Pasar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Notification Bell Switch
                IconButton(
                    onClick = { onToggleNotifications(!notificationsEnabled) },
                    modifier = Modifier.testTag("btn_toggle_notif")
                ) {
                    Icon(
                        imageVector = if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Outlined.NotificationsOff,
                        contentDescription = "Pengaturan Notifikasi",
                        tint = if (notificationsEnabled) BuyGreen else TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun InstrumentSelector(
    selected: TradingInstrument,
    xauPrice: Double,
    eurPrice: Double,
    onSelect: (TradingInstrument) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // XAU/USD Card
        InstrumentChip(
            instrument = TradingInstrument.XAUUSD,
            price = xauPrice,
            isSelected = selected == TradingInstrument.XAUUSD,
            accentColor = GoldPrimary,
            modifier = Modifier
                .weight(1f)
                .testTag("chip_xauusd"),
            onClick = { onSelect(TradingInstrument.XAUUSD) }
        )

        // EUR/USD Card
        InstrumentChip(
            instrument = TradingInstrument.EURUSD,
            price = eurPrice,
            isSelected = selected == TradingInstrument.EURUSD,
            accentColor = EuroBlue,
            modifier = Modifier
                .weight(1f)
                .testTag("chip_eurusd"),
            onClick = { onSelect(TradingInstrument.EURUSD) }
        )
    }
}

@Composable
fun InstrumentChip(
    instrument: TradingInstrument,
    price: Double,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isSelected) {
        accentColor.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val borderCol = if (isSelected) accentColor else Color.Transparent

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = instrument.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = instrument.formatPrice(price),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun InAppSignalBanner(
    signal: ScalpSignal?,
    onDismiss: () -> Unit,
    onViewDetail: (ScalpSignal) -> Unit
) {
    AnimatedVisibility(
        visible = signal != null,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        if (signal != null) {
            val isBuy = signal.action == SignalAction.BUY
            val accent = if (isBuy) BuyGreen else SellRed

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isBuy) BuyGreenContainer else SellRedContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable { onViewDetail(signal) }
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isBuy) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "🚨 SINYAL BARU: ${signal.action.label.uppercase()} ${signal.instrument.symbol}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = accent
                            )
                            Text(
                                text = "Entry: ${signal.instrument.formatPrice(signal.entryPrice)} | SL: ${signal.instrument.formatPrice(signal.stopLoss)} | TP1: ${signal.instrument.formatPrice(signal.takeProfit1)}",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Banner",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
