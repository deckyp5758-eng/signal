package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*

enum class CalendarFilter(val label: String) {
    ALL("Semua (USD & EUR)"),
    XAU_ONLY("Emas (XAU/USD)"),
    EUR_ONLY("Euro (EUR/USD)"),
    HIGH_IMPACT_ONLY("🔴 High Impact")
}

@Composable
fun CalendarScreen(
    events: List<EconomicEvent>,
    newsShield: NewsShieldStatus,
    selectedInstrument: TradingInstrument,
    onSelectInstrument: (TradingInstrument) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(CalendarFilter.ALL) }
    var expandedEventId by remember { mutableStateOf<String?>(null) }

    val filteredEvents = remember(events, selectedFilter) {
        when (selectedFilter) {
            CalendarFilter.ALL -> events
            CalendarFilter.XAU_ONLY -> events.filter { it.affectedInstruments.contains(TradingInstrument.XAUUSD) }
            CalendarFilter.EUR_ONLY -> events.filter { it.affectedInstruments.contains(TradingInstrument.EURUSD) }
            CalendarFilter.HIGH_IMPACT_ONLY -> events.filter { it.impact == NewsImpact.HIGH }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_calendar"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. News Shield Warning (if active) or Next Event Summary
        item {
            if (newsShield.isShieldActive) {
                ActiveNewsShieldCard(newsShield = newsShield)
            } else {
                NextEventPreviewCard(newsShield = newsShield)
            }
        }

        // 2. Filter Selector Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Filter Berita Ekonomi (XAU/USD & EUR/USD)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalendarFilter.values().forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFilter = filter }
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 3. Section Title & Live Count
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jadwal Rilis Kalender Ekonomi",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${filteredEvents.size} Berita",
                        fontSize = 11.sp,
                        color = GoldPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 4. List of Economic Events
        items(filteredEvents, key = { it.id }) { event ->
            val isExpanded = expandedEventId == event.id
            EconomicEventCard(
                event = event,
                isExpanded = isExpanded,
                onClick = {
                    expandedEventId = if (isExpanded) null else event.id
                }
            )
        }

        // 5. Educational Fundamental Scalping Guide Footer
        item {
            ScalperNewsEducationalCard()
        }
    }
}

@Composable
fun ActiveNewsShieldCard(newsShield: NewsShieldStatus) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = SellRed.copy(alpha = 0.18f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_active_news_shield")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SellRed
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Peringatan Berita",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "NEWS SHIELD AKTIF (ZONA RAWAN)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SellRed
                    )
                    Text(
                        text = "Volatilitas Tinggi Sedang Berlangsung",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = newsShield.warningMessage,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Saran: Hindari open posisi baru. Pasang/kunci Stop Loss pada level Breakeven (BEP).",
                        fontSize = 11.sp,
                        color = GoldPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun NextEventPreviewCard(newsShield: NewsShieldStatus) {
    val event = newsShield.currentEvent
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = CyanEma,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Jadwal Berita Terdekat",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanEma
                    )
                }

                if (event != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (event.impact == NewsImpact.HIGH) SellRed.copy(alpha = 0.2f) else GoldPrimary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = event.getStatusLabel(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (event.impact == NewsImpact.HIGH) SellRed else GoldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (event != null) {
                Text(
                    text = "${event.currency.flag} ${event.title}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Dampak: ${event.currency.code} → ${event.affectedInstruments.joinToString(", ") { it.symbol }}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            } else {
                Text(
                    text = "Tidak ada jadwal berita High Impact dalam waktu dekat.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun EconomicEventCard(
    event: EconomicEvent,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val isHighImpact = event.impact == NewsImpact.HIGH
    val impactColor = if (isHighImpact) SellRed else Color(0xFFFFA000)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("event_card_${event.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Time, Flag, Currency, Impact Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = event.getFormattedTime(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                    Text(
                        text = "${event.currency.flag} ${event.currency.code}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = impactColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isHighImpact) "🔴 HIGH" else "🟠 MEDIUM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = impactColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Title
            Text(
                text = event.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Status & Countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.getFormattedDate(),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = event.getStatusLabel(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (event.isHighImpactNear()) SellRed else CyanEma
                )
            }

            // Data Pills: Forecast vs Previous vs Actual
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Previous", fontSize = 10.sp, color = TextSecondary)
                    Text(text = event.previous, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Forecast", fontSize = 10.sp, color = TextSecondary)
                    Text(text = event.forecast, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanEma)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Actual", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        text = event.actual,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (event.actual != "-") BuyGreen else TextSecondary
                    )
                }
            }

            // Expanded Details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Market Effect
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Dampak Terhadap XAU/USD & EUR/USD:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                        }
                        Text(
                            text = event.marketEffect,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }

                    // Scalper Actionable Advice
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = impactColor.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "💡 Tips Khusus Scalper:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = impactColor
                            )
                            Text(
                                text = event.scalperAdvice,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScalperNewsEducationalCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CyanEma,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Aturan Penting Scalping Saat Berita Besar",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanEma
                )
            }
            Text(
                text = "1. Jangan Scalping 15 Menit Sebelum & Sesudah NFP/CPI: Pada menit-menit ini, spread broker melonjak drastis dan risiko slippage sangat tinggi.\n\n" +
                        "2. Korelasi USD vs Emas (XAU): Emas dinilai dalam Dolar AS. Berita AS yang kuat (USD naik) umumnya menekan harga Emas turun.\n\n" +
                        "3. Pasang Stop Loss Wajib: Berita tak terduga bisa menggerakkan candle 50-100 pips dalam hitungan detik.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
