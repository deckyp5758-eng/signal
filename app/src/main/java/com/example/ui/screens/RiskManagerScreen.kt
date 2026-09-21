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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TradePlanEntity
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.RiskCalcInput
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RiskManagerScreen(
    selectedInstrument: TradingInstrument,
    riskInput: RiskCalcInput,
    riskCalculation: RiskCalculation,
    tradePlans: List<TradePlanEntity>,
    onUpdateRiskInput: ((RiskCalcInput) -> RiskCalcInput) -> Unit,
    onSyncLivePrice: () -> Unit,
    onSaveTradePlan: (RiskCalculation) -> Unit,
    onDeleteTradePlan: (Long) -> Unit,
    onCopyText: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        // Header info banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GoldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Kalkulator SL & TP Otomatis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lindungi modal Anda dari margin call dengan ukuran lot dan batasan risiko terukur.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Calculator Input Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_risk_calculator")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Parameter Akun & Posisi",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 1. Mata Uang Akun (Currency Selector)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Mata Uang Akun Trading", fontSize = 12.sp, color = TextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AccountCurrency.values().forEach { curr ->
                                val isSelected = riskInput.currency == curr
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val defaultBal = when (curr) {
                                                AccountCurrency.IDR -> if (riskInput.balanceText == "1000" || riskInput.balanceText == "500" || riskInput.balanceText == "100") "10000000" else riskInput.balanceText
                                                AccountCurrency.EUR -> if (riskInput.balanceText == "10000000") "1000" else riskInput.balanceText
                                                AccountCurrency.USD -> if (riskInput.balanceText == "10000000") "1000" else riskInput.balanceText
                                            }
                                            onUpdateRiskInput { it.copy(currency = curr, balanceText = defaultBal) }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = curr.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Tipe Akun Broker (Standard, Cent, Mini)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Tipe Akun Broker", fontSize = 12.sp, color = TextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AccountType.values().forEach { accType ->
                                val isSelected = riskInput.accountType == accType
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) CyanEma else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onUpdateRiskInput { it.copy(accountType = accType) }
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = accType.code,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Pip Value Live Info Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Pip Value Live (${riskInput.accountType.code})",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "${riskCalculation.accountCurrency.formatMoney(riskCalculation.pipValuePerLotCurrency)} / 1.0 Lot",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPrimary
                                    )
                                }
                            }
                            Text(
                                text = "1 Pip = ${selectedInstrument.pipMultiplier}",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Balance Input + Quick Presets
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Saldo Akun (${riskInput.currency.displayName})",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = riskInput.balanceText,
                            onValueChange = { str ->
                                onUpdateRiskInput { it.copy(balanceText = str) }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_balance"),
                            shape = RoundedCornerShape(10.dp),
                            prefix = { Text("${riskInput.currency.symbol} ", fontWeight = FontWeight.Bold) }
                        )

                        // Quick Presets based on currency
                        val presets = if (riskInput.currency == AccountCurrency.IDR) {
                            listOf("1000000", "5000000", "10000000", "50000000")
                        } else {
                            listOf("100", "500", "1000", "5000")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { amount ->
                                val label = if (riskInput.currency == AccountCurrency.IDR) {
                                    val jt = amount.toLong() / 1_000_000
                                    "${jt}jt"
                                } else {
                                    "${riskInput.currency.symbol}$amount"
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (riskInput.balanceText == amount) GoldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onUpdateRiskInput { it.copy(balanceText = amount) }
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (riskInput.balanceText == amount) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Risk Percentage Chips (0.5%, 1%, 2%, 3%, 5%)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Risiko per Transaksi (%)", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = "${riskInput.riskPercent}% = ${riskCalculation.accountCurrency.formatMoney(riskCalculation.riskAmountCurrency)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SellRed
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0.5, 1.0, 2.0, 3.0, 5.0).forEach { pct ->
                                val isSel = riskInput.riskPercent == pct
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) SellRed else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onUpdateRiskInput { it.copy(riskPercent = pct) }
                                        }
                                ) {
                                    Text(
                                        text = "$pct%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Direction Selector (BUY vs SELL)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onUpdateRiskInput { it.copy(action = SignalAction.BUY) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_risk_buy"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (riskInput.action == SignalAction.BUY) BuyGreen else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "BUY (Beli)", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onUpdateRiskInput { it.copy(action = SignalAction.SELL) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_risk_sell"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (riskInput.action == SignalAction.SELL) SellRed else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "SELL (Jual)", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Entry Price + Sync Button
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Harga Masuk (${selectedInstrument.symbol})", fontSize = 12.sp, color = TextSecondary)
                            TextButton(
                                onClick = onSyncLivePrice,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Ambil Harga Live", fontSize = 11.sp)
                            }
                        }

                        OutlinedTextField(
                            value = riskInput.entryPriceText,
                            onValueChange = { str -> onUpdateRiskInput { it.copy(entryPriceText = str) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_entry_price"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Jarak Stop Loss & Rasio Risk:Reward
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Jarak SL (Pips)", fontSize = 12.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = riskInput.slPipsText,
                                onValueChange = { str -> onUpdateRiskInput { it.copy(slPipsText = str) } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Rasio Risk:Reward", fontSize = 12.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(1.5, 2.0, 3.0).forEach { rr ->
                                    val isSel = riskInput.riskReward == rr
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) CyanEma else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onUpdateRiskInput { it.copy(riskReward = rr) } }
                                    ) {
                                        Text(
                                            text = "1:$rr",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(vertical = 14.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Automatic Calculation Output Card
        item {
            AutoCalculatedResultCard(
                calc = riskCalculation,
                onSavePlan = { onSaveTradePlan(riskCalculation) },
                onCopySingle = onCopyText
            )
        }

        // Saved Trade Plans Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jurnal Rencana Trading Tersimpan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${tradePlans.size} Rencana",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        if (tradePlans.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Belum ada rencana tersimpan. Klik 'Simpan ke Jurnal' di atas untuk menyimpan kalkulasi ini.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(tradePlans) { plan ->
                TradePlanItem(
                    plan = plan,
                    onDelete = { onDeleteTradePlan(plan.id) },
                    onCopy = onCopyText
                )
            }
        }
    }
}

@Composable
fun AutoCalculatedResultCard(
    calc: RiskCalculation,
    onSavePlan: () -> Unit,
    onCopySingle: (String, String) -> Unit
) {
    val lotFormatted = String.format(java.util.Locale.US, "%.2f", calc.lotSize)
    val slFormatted = calc.instrument.formatPrice(calc.stopLossPrice)
    val tp1Formatted = calc.instrument.formatPrice(calc.takeProfit1)
    val tp2Formatted = calc.instrument.formatPrice(calc.takeProfit2)
    val entryFormatted = calc.instrument.formatPrice(calc.entryPrice)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, GoldPrimary.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .testTag("card_calc_result")
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
                Text(
                    text = "Hasil Rekomendasi Otomatis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = GoldPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Rasio 1:${calc.riskRewardRatio}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Big Recommended Lot Size Highlight
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "Rekomendasi Ukuran Lot", fontSize = 12.sp, color = TextSecondary)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CyanEma.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = calc.accountType.code,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanEma,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$lotFormatted Lot",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Maksimal Kerugian", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = "-${calc.accountCurrency.formatMoney(calc.maxLossCurrency)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SellRed
                        )
                        if (calc.accountCurrency != AccountCurrency.USD) {
                            Text(
                                text = "(-$${"%.2f".format(calc.maxLossUsd)} USD)",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Quick 1-Tap Copy Chips for MT5 / MT4
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "⚡ Salin Cepat untuk MT5 (1x Tap Siap Paste):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onCopySingle(lotFormatted, "Lot Size") },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = GoldPrimary.copy(alpha = 0.18f)),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Lot: $lotFormatted", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = { onCopySingle(slFormatted, "Stop Loss (SL)") },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SellRed.copy(alpha = 0.18f)),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = SellRed, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("SL: $slFormatted", color = SellRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = { onCopySingle(tp1Formatted, "Take Profit 1 (TP1)") },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = BuyGreen.copy(alpha = 0.18f)),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = BuyGreen, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("TP: $tp1Formatted", color = BuyGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            // SL and TP Levels breakdown
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LevelItemRow(
                        label = "Stop Loss (SL Otomatis)",
                        price = slFormatted,
                        rawNumber = slFormatted,
                        impact = "-${calc.accountCurrency.formatMoney(calc.maxLossCurrency)} (-${"%.1f".format(calc.slPips)} pips)",
                        color = SellRed,
                        onCopyRaw = { raw -> onCopySingle(raw, "Stop Loss (SL)") }
                    )

                    HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

                    LevelItemRow(
                        label = "Take Profit 1 (Target Konservatif)",
                        price = tp1Formatted,
                        rawNumber = tp1Formatted,
                        impact = "+${calc.accountCurrency.formatMoney(calc.potentialProfit1Currency)} (+${"%.1f".format(calc.tp1Pips)} pips)",
                        color = BuyGreen,
                        onCopyRaw = { raw -> onCopySingle(raw, "Take Profit 1 (TP1)") }
                    )

                    LevelItemRow(
                        label = "Take Profit 2 (Target Standar R:R)",
                        price = tp2Formatted,
                        rawNumber = tp2Formatted,
                        impact = "+${calc.accountCurrency.formatMoney(calc.potentialProfit2Currency)} (+${"%.1f".format(calc.tp2Pips)} pips)",
                        color = BuyGreen,
                        onCopyRaw = { raw -> onCopySingle(raw, "Take Profit 2 (TP2)") }
                    )

                    LevelItemRow(
                        label = "Take Profit 3 (Target Runner/Ekstra)",
                        price = calc.instrument.formatPrice(calc.takeProfit3),
                        rawNumber = calc.instrument.formatPrice(calc.takeProfit3),
                        impact = "+${calc.accountCurrency.formatMoney(calc.potentialProfit3Currency)} (+${"%.1f".format(calc.tp3Pips)} pips)",
                        color = BuyGreen,
                        onCopyRaw = { raw -> onCopySingle(raw, "Take Profit 3 (TP3)") }
                    )
                }
            }

            // Action Button: Save to Trading Plan Journal
            Button(
                onClick = onSavePlan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_save_trade_plan"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Simpan Rencana ke Jurnal", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LevelItemRow(
    label: String,
    price: String,
    rawNumber: String = price,
    impact: String,
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
        Column {
            Text(text = label, fontSize = 11.sp, color = TextSecondary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = price, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
                if (onCopyRaw != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onCopyRaw(rawNumber) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Salin $label",
                                tint = TextSecondary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }
        Text(text = impact, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun TradePlanItem(
    plan: TradePlanEntity,
    onDelete: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    val isBuy = plan.action == "BUY"
    val col = if (isBuy) BuyGreen else SellRed
    val timeStr = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(plan.timestamp))
    val isGold = plan.instrumentSymbol.contains("XAU")
    val decDigits = if (isGold) 2 else 5
    val slStr = String.format(java.util.Locale.US, "%.${decDigits}f", plan.stopLossPrice)
    val tpStr = String.format(java.util.Locale.US, "%.${decDigits}f", plan.takeProfitPrice)
    val entryStr = String.format(java.util.Locale.US, "%.${decDigits}f", plan.entryPrice)
    val lotStr = String.format(java.util.Locale.US, "%.2f", plan.lotSize)

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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(col.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = plan.action,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = col
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${plan.instrumentSymbol} • ${plan.lotSize} Lot",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(text = timeStr, fontSize = 10.sp, color = TextTertiary)
                    }
                    Text(
                        text = "Entry: $entryStr | SL: $slStr | TP: $tpStr",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Risiko: -$${"%.2f".format(plan.maxLossUsd)} | Target: +$${"%.2f".format(plan.potentialProfitUsd)}",
                        fontSize = 10.sp,
                        color = GoldPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SellRed.copy(alpha = 0.15f),
                            modifier = Modifier.clickable { onCopy(slStr, "Stop Loss (SL)") }
                        ) {
                            Text(
                                text = "📋 SL: $slStr",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SellRed,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BuyGreen.copy(alpha = 0.15f),
                            modifier = Modifier.clickable { onCopy(tpStr, "Take Profit (TP)") }
                        ) {
                            Text(
                                text = "📋 TP: $tpStr",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BuyGreen,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = SellRed, modifier = Modifier.size(16.dp))
            }
        }
    }
}
