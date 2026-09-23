package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.TradingInstrument
import com.example.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewTechnicalWidget(
    instrument: TradingInstrument,
    modifier: Modifier = Modifier
) {
    var selectedFeed by remember(instrument) {
        mutableStateOf(
            if (instrument == TradingInstrument.XAUUSD) "BINANCE:PAXGUSDT" else "BINANCE:EURUSDT"
        )
    }
    var keyReload by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var lastLoadedContent by remember { mutableStateOf("") }

    val rawSymbol = selectedFeed

    val htmlContent = remember(rawSymbol, keyReload) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                body, html {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    min-height: 460px;
                    background-color: #131722;
                    overflow-x: hidden;
                    font-family: -apple-system, BlinkMacSystemFont, 'Trebuchet MS', Roboto, Ubuntu, sans-serif;
                }
                .tradingview-widget-container {
                    width: 100% !important;
                    display: flex;
                    justify-content: center;
                }
                .tradingview-widget-copyright {
                    display: none !important;
                }
            </style>
        </head>
        <body>
            <div class="tradingview-widget-container">
                <div class="tradingview-widget-container__widget"></div>
                <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-technical-analysis.js" async>
                {
                    "interval": "5m",
                    "width": "100%",
                    "isTransparent": true,
                    "height": "450",
                    "symbol": "$rawSymbol",
                    "showIntervalTabs": true,
                    "displayMode": "multiple",
                    "locale": "id",
                    "colorTheme": "dark"
                }
                </script>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF2A2E39), RoundedCornerShape(16.dp))
            .testTag("card_tradingview_technical_widget")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E222D))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(BuyGreen)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TRADINGVIEW TECHNICAL METER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = GoldPrimary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "OFFICIAL TV",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Speedometer & Ringkasan Analisis Teknis Otentik",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = { keyReload++ },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Muat Ulang Widget",
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Symbol / Feed Source Switcher
            Surface(
                color = Color(0xFF131722),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Feed Source TV:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (instrument == TradingInstrument.XAUUSD) {
                            val feeds = listOf(
                                "BINANCE:PAXGUSDT" to "Binance Spot",
                                "OANDA:XAUUSD" to "OANDA Forex"
                            )
                            feeds.forEach { (sym, label) ->
                                val isSel = selectedFeed == sym
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSel) GoldPrimary else Color(0xFF2A2E39),
                                    modifier = Modifier.clickable {
                                        selectedFeed = sym
                                        keyReload++
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.Black else Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        } else {
                            val feeds = listOf(
                                "BINANCE:EURUSDT" to "Binance Spot",
                                "FX:EURUSD" to "FX Interbank"
                            )
                            feeds.forEach { (sym, label) ->
                                val isSel = selectedFeed == sym
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSel) GoldPrimary else Color(0xFF2A2E39),
                                    modifier = Modifier.clickable {
                                        selectedFeed = sym
                                        keyReload++
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.Black else Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // WebView Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
                    .background(Color(0xFF131722))
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }
                            setBackgroundColor(AndroidColor.parseColor("#131722"))
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    hasError = false
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }
                                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                    isLoading = false
                                    hasError = true
                                }
                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                    isLoading = false
                                    hasError = true
                                    try { view?.destroy() } catch (_: Throwable) {}
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
                            isLoading = true
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

                // Loading State
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF131722)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = GoldPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Menghubungkan ke Server TradingView...",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Error State
                if (hasError && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF131722)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = SellRed,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Gagal memuat Widget TradingView",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Button(
                                onClick = { keyReload++ },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                            ) {
                                Text("Coba Lagi", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
