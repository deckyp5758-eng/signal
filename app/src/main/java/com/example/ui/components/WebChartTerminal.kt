package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.DetectedPattern
import com.example.data.model.SignalAction
import com.example.data.model.Timeframe
import com.example.data.model.TradingInstrument
import com.example.ui.theme.*

enum class ChartFeedSource(val displayName: String, val tvSymbol: String) {
    BINANCE("Binance Institutional (Cocok 100% dgn Analisis)", "BINANCE:PAXGUSDT"),
    OANDA("OANDA (Interbank MetaTrader Feed)", "OANDA:XAUUSD"),
    FOREX_COM("FOREX.com (ECN Broker Feed)", "FOREXCOM:XAUUSD"),
    CAPITAL_COM("Capital.com (Broker Gold Spot)", "CAPITALCOM:XAUUSD"),
    TVC("TradingView Spot Gold", "TVC:GOLD")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebChartTerminal(
    instrument: TradingInstrument,
    timeframe: Timeframe,
    detectedPatterns: List<DetectedPattern> = emptyList(),
    onApplyPatternToRisk: (DetectedPattern) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedFeedSource by remember { mutableStateOf(ChartFeedSource.BINANCE) }
    var keyReload by remember { mutableIntStateOf(0) }
    var lastLoadedContent by remember { mutableStateOf("") }
    var isChartLoading by remember { mutableStateOf(true) }
    var hasLoadingError by remember { mutableStateOf(false) }

    val rawSymbol = if (instrument == TradingInstrument.XAUUSD) {
        selectedFeedSource.tvSymbol
    } else {
        "BINANCE:EURUSDT"
    }

    val intervalStr = when (timeframe) {
        Timeframe.M1 -> "1"
        Timeframe.M5 -> "5"
        Timeframe.M15 -> "15"
        Timeframe.M30 -> "30"
        Timeframe.H1 -> "60"
    }

    val htmlContent = remember(rawSymbol, intervalStr, keyReload) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #12151e; overflow: hidden; }
                #tradingview_widget { width: 100%; height: 100%; }
            </style>
        </head>
        <body>
            <div id="tradingview_widget"></div>
            <script type="text/javascript" src="https://s3.tradingview.com/tv.js"></script>
            <script type="text/javascript">
                new TradingView.widget({
                    "autosize": true,
                    "symbol": "$rawSymbol",
                    "interval": "$intervalStr",
                    "timezone": "Etc/UTC",
                    "theme": "dark",
                    "style": "1",
                    "locale": "id",
                    "toolbar_bg": "#12151e",
                    "enable_publishing": false,
                    "allow_symbol_change": true,
                    "hide_top_toolbar": false,
                    "hide_legend": false,
                    "save_image": false,
                    "container_id": "tradingview_widget"
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Feed Source Bar
        if (instrument == TradingInstrument.XAUUSD) {
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Feed Broker:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ChartFeedSource.values().forEach { source ->
                            val isSel = selectedFeedSource == source
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) GoldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { selectedFeedSource = source }
                            ) {
                                Text(
                                    text = source.name,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.Black else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { keyReload++ },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Muat Ulang Grafik",
                                tint = CyanEma,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Web Chart Container with Overlay AI Pattern Scanner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .testTag("web_chart_terminal")
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        }
                        setBackgroundColor(android.graphics.Color.parseColor("#12151e"))
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                isChartLoading = true
                                hasLoadingError = false
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isChartLoading = false
                            }
                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                isChartLoading = false
                                hasLoadingError = true
                            }
                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                // CRITICAL: returning true prevents Chromium from killing the host app
                                isChartLoading = false
                                hasLoadingError = true
                                try {
                                    view?.destroy()
                                } catch (_: Throwable) {}
                                return true
                            }
                        }
                        lastLoadedContent = htmlContent
                        loadDataWithBaseURL("https://s3.tradingview.com", htmlContent, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    if (lastLoadedContent != htmlContent) {
                        lastLoadedContent = htmlContent
                        isChartLoading = true
                        webView.loadDataWithBaseURL("https://s3.tradingview.com", htmlContent, "text/html", "UTF-8", null)
                    }
                },
                onRelease = { webView ->
                    try {
                        webView.stopLoading()
                        webView.destroy()
                    } catch (_: Throwable) {}
                },
                modifier = Modifier.fillMaxSize()
            )

            // Loading Indicator Overlay
            if (isChartLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = GoldPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Memuat Terminal TradingView Live...",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Error Fallback Overlay
            if (hasLoadingError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Koneksi Feed Broker Terkendala",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pastikan koneksi internet aktif untuk memuat chart broker live.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Button(
                                onClick = {
                                    keyReload++
                                    hasLoadingError = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                            ) {
                                Text("Muat Ulang", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            val ctx = LocalContext.current
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val tvUrl = "https://www.tradingview.com/chart/?symbol=${Uri.encode(rawSymbol)}"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tvUrl))
                                        ctx.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                                border = BorderStroke(1.dp, GoldPrimary)
                            ) {
                                Text("Buka Browser", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Live AI Auto-Scan Pattern Banner Overlay (Bottom Floating Bar)
            if (detectedPatterns.isNotEmpty()) {
                val topPattern = detectedPatterns.first()
                val badgeColor = if (topPattern.action == SignalAction.BUY) BuyGreen else SellRed

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, GoldPrimary),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp)
                        .fillMaxWidth()
                        .clickable { onApplyPatternToRisk(topPattern) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoGraph,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "AI Live Scan:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = topPattern.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = badgeColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = topPattern.action.badgeText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "SL: $${instrument.formatPrice(topPattern.suggestedStopLoss)} | TP: $${instrument.formatPrice(topPattern.suggestedTakeProfit)} (R:R ${"%.1f".format(topPattern.estimatedRiskReward)})",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Pakai SL/TP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
