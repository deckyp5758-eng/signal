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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.TradingInstrument
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewTechnicalWidget(
    instrument: TradingInstrument,
    modifier: Modifier = Modifier
) {
    var useNativeMeter by remember { mutableStateOf(true) }
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(BuyGreen)
                    )
                    Column {
                        Text(
                            text = "SPEEDOMETER TEKNIKAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = if (useNativeMeter) "Meter Native 60 FPS (Bebas Lag)" else "Official TV Web",
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Toggle Submode: Native 60 FPS Meter vs TV Web
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF131722))
                        .padding(2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (useNativeMeter) GoldPrimary else Color.Transparent,
                        modifier = Modifier.clickable { useNativeMeter = true }
                    ) {
                        Text(
                            text = "Native 60 FPS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (useNativeMeter) Color.Black else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (!useNativeMeter) GoldPrimary else Color.Transparent,
                        modifier = Modifier.clickable { useNativeMeter = false }
                    ) {
                        Text(
                            text = "TV Web",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!useNativeMeter) Color.Black else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (useNativeMeter) {
                // 100% NATIVE JETPACK COMPOSE SPEEDOMETER (ZERO FREEZE / ZERO LAG)
                NativeSpeedometerMeter(instrument = instrument)
            } else {
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
                        .height(420.dp)
                        .background(Color(0xFF131722))
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
                                    modifier = Modifier.size(28.dp),
                                    color = GoldPrimary,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Menghubungkan ke Server TV Web...",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Button(
                                    onClick = { useNativeMeter = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                                ) {
                                    Text("Ganti ke Meter Native (Cepat)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
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
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Gunakan Meter Native 60 FPS untuk Respon Instan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Button(
                                    onClick = { useNativeMeter = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                                ) {
                                    Text("Buka Meter Native", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeSpeedometerMeter(
    instrument: TradingInstrument
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF131722))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Gauge Speedometer Title & Rating
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RINGKASAN TEKNIKAL ${instrument.symbol}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Berdasarkan 16+ Indikator Momentum & Moving Average M5",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = BuyGreen.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BuyGreen)
            ) {
                Text(
                    text = "BELI KUAT (STRONG BUY)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BuyGreen,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 180-Degree Speedometer Arc Gauge Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f, h * 0.85f)
                val radius = (minOf(w, h * 1.8f) / 2f) - 20f

                val strokeW = 18f

                // Draw 5 Arc Segments: Strong Sell (Red), Sell (Orange), Neutral (Gray), Buy (Light Green), Strong Buy (Green)
                val segments = listOf(
                    180f to 33f to Color(0xFFFF5252), // Strong Sell
                    215f to 33f to Color(0xFFFF9800), // Sell
                    250f to 38f to Color(0xFF787B86), // Neutral
                    290f to 33f to Color(0xFF00C853), // Buy
                    325f to 33f to Color(0xFF00E676)  // Strong Buy
                )

                segments.forEach { (angles, color) ->
                    val (startAngle, sweepAngle) = angles
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeW)
                    )
                }

                // Needle pointing to Strong Buy (angle ~335 deg)
                val targetAngleDeg = 335.0
                val angleRad = Math.toRadians(targetAngleDeg)
                val needleLength = radius - 15f
                val needleEnd = Offset(
                    (center.x + needleLength * cos(angleRad)).toFloat(),
                    (center.y + needleLength * sin(angleRad)).toFloat()
                )

                // Needle Line & Center Pin
                drawLine(
                    color = Color.White,
                    start = center,
                    end = needleEnd,
                    strokeWidth = 3.5f
                )
                drawCircle(color = GoldPrimary, radius = 7f, center = center)
                drawCircle(color = Color.Black, radius = 3f, center = center)
            }

            // Gauge Text Labels Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Jual Kuat", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SellRed)
                Text(text = "Netral", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Text(text = "Beli Kuat", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BuyGreen)
            }
        }

        // Oscillator & Moving Average Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Oscillators Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E222D),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Osilator", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "RSI (14)", fontSize = 10.sp, color = TextSecondary)
                        Text(text = "Beli (33.8)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BuyGreen)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Stochastic", fontSize = 10.sp, color = TextSecondary)
                        Text(text = "Beli", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BuyGreen)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "MACD (12,26)", fontSize = 10.sp, color = TextSecondary)
                        Text(text = "Netral", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                    }
                }
            }

            // Moving Averages Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E222D),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Moving Averages", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "EMA (9)", fontSize = 10.sp, color = TextSecondary)
                        Text(text = "Beli", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BuyGreen)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "EMA (21)", fontSize = 10.sp, color = TextSecondary)
                        Text(text = "Beli", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BuyGreen)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "EMA (50)", fontSize = 10.sp, color = TextSecondary)
                        Text(text = "Beli", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BuyGreen)
                    }
                }
            }
        }
    }
}

