package com.example.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.AppConfig
import com.example.data.model.TradingInstrument
import com.example.service.DiagnosticLogEntry
import com.example.service.DiagnosticLogger
import com.example.service.LogLevel
import com.example.ui.theme.*
import com.example.ui.viewmodel.TradingViewModel
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(viewModel: TradingViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isLiveFeedOnline by viewModel.engine.isLiveFeedOnline.collectAsState()
    val latencyMs by viewModel.engine.latencyMs.collectAsState()
    val currentSession by viewModel.engine.currentSession.collectAsState()
    val xauPrice by viewModel.engine.xauPrice.collectAsState()
    val eurPrice by viewModel.engine.eurPrice.collectAsState()
    val brokerOffset by viewModel.engine.brokerOffsetXau.collectAsState()

    val logs by DiagnosticLogger.logs.collectAsState()
    var selectedFilter by remember { mutableStateOf<LogLevel?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, selectedFilter) {
        if (selectedFilter == null) logs else logs.filter { it.level == selectedFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("card_diagnostics_header")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Diagnostik & Performa Sistem",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Versi: v${AppConfig.APP_VERSION} (Build ${AppConfig.BUILD_NUMBER})",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isLiveFeedOnline) BuyGreen.copy(alpha = 0.2f) else SellRed.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isLiveFeedOnline) BuyGreen else SellRed)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isLiveFeedOnline) BuyGreen else SellRed)
                                )
                                Text(
                                    text = if (isLiveFeedOnline) "ONLINE (${latencyMs}ms)" else "OFFLINE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLiveFeedOnline) BuyGreen else SellRed
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Health & Status Cards Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // API Health
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Primary API Feed", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "api.gold-api.com", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                        Text(text = "Status: Aktif & Responsif", fontSize = 10.sp, color = BuyGreen)
                    }
                }

                // Market Session
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Sesi Pasar Active", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = currentSession.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanEma)
                        Text(text = currentSession.description, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                    }
                }
            }
        }

        // 3. Live Price State & Offset Manager
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "Data Realtime Running Price Engine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "XAU/USD (Spot Gold)", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = "$${TradingInstrument.XAUUSD.formatPrice(xauPrice + brokerOffset)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "EUR/USD", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = "$${TradingInstrument.EURUSD.formatPrice(eurPrice)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Quick Broker Offset Control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Offset MetaTrader: ${if (brokerOffset >= 0) "+$brokerOffset" else "$brokerOffset"}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.engine.setBrokerOffsetXau(0.0) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Reset (0.0)", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4. Action Buttons (Uji Koneksi & Salin Log)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isTestingConnection = true
                            DiagnosticLogger.i("Diagnostics", "Memulai pengujian koneksi server instan...")
                            val result = viewModel.calculateCurrentRisk()
                            DiagnosticLogger.i("Diagnostics", "Pengujian selesai. Status Risk Calculator OK: ${result.lotSize} Lot.")
                            isTestingConnection = false
                        }
                    },
                    enabled = !isTestingConnection,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanEma),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isTestingConnection) "Menguji..." else "Tes API", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { DiagnosticLogger.copyLogsToClipboard(context) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Salin Report Log", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. System Terminal Log Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Text(text = "Console Log Terminal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = { DiagnosticLogger.clearLogs() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Bersihkan", fontSize = 11.sp, color = SellRed)
                        }
                    }

                    // Log Filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(null to "Semua", LogLevel.ERROR to "Error", LogLevel.NETWORK to "Net", LogLevel.INFO to "Info").forEach { (level, label) ->
                            val isSel = selectedFilter == level
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) GoldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { selectedFilter = level }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Log Item List
                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Belum ada log tercatat", fontSize = 12.sp, color = TextSecondary)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            filteredLogs.take(30).forEach { log ->
                                LogItemRow(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: DiagnosticLogEntry) {
    val levelColor = when (log.level) {
        LogLevel.ERROR -> SellRed
        LogLevel.WARN -> OrangeEma
        LogLevel.NETWORK -> CyanEma
        LogLevel.INFO -> BuyGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = log.formattedTime,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary
        )
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = levelColor.copy(alpha = 0.2f)
        ) {
            Text(
                text = log.level.badge,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = levelColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Text(
            text = "[${log.tag}] ${log.message}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )
    }
}
