package com.example.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.data.model.*
import com.example.service.IndicatorCalculator
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewLightweightChart(
    candles: List<Candle>,
    instrument: TradingInstrument,
    timeframe: Timeframe,
    detectedPatterns: List<DetectedPattern> = emptyList(),
    showPatterns: Boolean = true,
    showEma: Boolean = true,
    showVolume: Boolean = true,
    onApplyPatternToRisk: (DetectedPattern) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFitContentRequested by remember { mutableIntStateOf(0) }
    var selectedPatternIndex by remember { mutableIntStateOf(0) }
    var lastLoadedHtml by remember { mutableStateOf("") }

    // Sanitize candles: ensure strictly ascending order with unique second timestamps
    val sanitizedCandles = remember(candles) {
        if (candles.isEmpty()) emptyList()
        else {
            val sorted = candles.sortedBy { it.timestamp }
            val result = mutableListOf<Candle>()
            var lastTimeSec = 0L
            for (c in sorted) {
                var timeSec = c.timestamp / 1000L
                if (timeSec <= lastTimeSec) {
                    timeSec = lastTimeSec + 1
                }
                lastTimeSec = timeSec
                result.add(c.copy(timestamp = timeSec * 1000L))
            }
            result
        }
    }

    // Build JSON data for Candlesticks & Volume
    val (candlesJsonStr, volumeJsonStr) = remember(sanitizedCandles) {
        val candleArray = JSONArray()
        val volumeArray = JSONArray()
        for (c in sanitizedCandles) {
            val timeSec = c.timestamp / 1000L
            val cObj = JSONObject().apply {
                put("time", timeSec)
                put("open", c.open)
                put("high", c.high)
                put("low", c.low)
                put("close", c.close)
            }
            candleArray.put(cObj)

            val vObj = JSONObject().apply {
                put("time", timeSec)
                put("value", c.volume)
                put("color", if (c.close >= c.open) "rgba(8, 153, 129, 0.4)" else "rgba(242, 54, 69, 0.4)")
            }
            volumeArray.put(vObj)
        }
        candleArray.toString() to volumeArray.toString()
    }

    // Calculate EMA 9 & EMA 21
    val (ema9JsonStr, ema21JsonStr) = remember(sanitizedCandles, showEma) {
        if (!showEma || sanitizedCandles.isEmpty()) {
            "[]" to "[]"
        } else {
            val closes = sanitizedCandles.map { it.close }
            val ema9Values = IndicatorCalculator.calculateEMA(closes, 9)
            val ema21Values = IndicatorCalculator.calculateEMA(closes, 21)

            val ema9Array = JSONArray()
            val ema21Array = JSONArray()

            for (i in sanitizedCandles.indices) {
                val timeSec = sanitizedCandles[i].timestamp / 1000L
                if (i < ema9Values.size && !ema9Values[i].isNaN()) {
                    ema9Array.put(JSONObject().apply {
                        put("time", timeSec)
                        put("value", ema9Values[i])
                    })
                }
                if (i < ema21Values.size && !ema21Values[i].isNaN()) {
                    ema21Array.put(JSONObject().apply {
                        put("time", timeSec)
                        put("value", ema21Values[i])
                    })
                }
            }
            ema9Array.toString() to ema21Array.toString()
        }
    }

    // Auto-Draw Pattern Markers (Arrows and Labels on Candles)
    val markersJsonStr = remember(sanitizedCandles, detectedPatterns, showPatterns) {
        if (!showPatterns || detectedPatterns.isEmpty() || sanitizedCandles.isEmpty()) {
            "[]"
        } else {
            val markersArray = JSONArray()
            for (pattern in detectedPatterns) {
                val targetIdx = pattern.endCandleIndex.coerceIn(0, sanitizedCandles.lastIndex)
                val targetCandle = sanitizedCandles[targetIdx]
                val timeSec = targetCandle.timestamp / 1000L
                val isBuy = pattern.action == SignalAction.BUY

                val markerObj = JSONObject().apply {
                    put("time", timeSec)
                    put("position", if (isBuy) "belowBar" else "aboveBar")
                    put("color", if (isBuy) "#089981" else "#F23645")
                    put("shape", if (isBuy) "arrowUp" else "arrowDown")
                    put("text", "${if (isBuy) "▲" else "▼"} ${pattern.name} [${pattern.confluenceGrade.code}]")
                }
                markersArray.put(markerObj)
            }
            markersArray.toString()
        }
    }

    // Auto-Draw Price Lines (SL, TP, Entry, Key Levels)
    val activePattern = remember(detectedPatterns, selectedPatternIndex) {
        if (detectedPatterns.isNotEmpty()) {
            val safeIdx = selectedPatternIndex.coerceIn(0, detectedPatterns.lastIndex)
            detectedPatterns[safeIdx]
        } else null
    }

    val priceLinesJsonStr = remember(activePattern, showPatterns, instrument) {
        if (!showPatterns || activePattern == null) {
            "[]"
        } else {
            val linesArray = JSONArray()
            // Stop Loss Line (Red Dashed)
            if (activePattern.suggestedStopLoss > 0.0) {
                linesArray.put(JSONObject().apply {
                    put("price", activePattern.suggestedStopLoss)
                    put("color", "#F23645")
                    put("title", "SL: ${instrument.formatPrice(activePattern.suggestedStopLoss)}")
                    put("lineStyle", 2) // Dashed
                })
            }
            // Take Profit Line (Green Dashed)
            if (activePattern.suggestedTakeProfit > 0.0) {
                linesArray.put(JSONObject().apply {
                    put("price", activePattern.suggestedTakeProfit)
                    put("color", "#089981")
                    put("title", "TP: ${instrument.formatPrice(activePattern.suggestedTakeProfit)}")
                    put("lineStyle", 2)
                })
            }
            // Key Level / Neckline (Gold Dotted)
            val keyPrice = activePattern.necklinePrice ?: if (activePattern.keyLevelPrice > 0.0) activePattern.keyLevelPrice else null
            if (keyPrice != null && keyPrice > 0.0) {
                linesArray.put(JSONObject().apply {
                    put("price", keyPrice)
                    put("color", "#FFD700")
                    put("title", "Level Kunci: ${instrument.formatPrice(keyPrice)}")
                    put("lineStyle", 1) // Dotted
                })
            }
            linesArray.toString()
        }
    }

    val precision = instrument.decimalDigits
    val minMove = if (precision == 2) 0.01 else 0.00001

    val htmlDocument = remember(
        candlesJsonStr,
        volumeJsonStr,
        ema9JsonStr,
        ema21JsonStr,
        markersJsonStr,
        priceLinesJsonStr,
        precision,
        minMove,
        isFitContentRequested
    ) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body, html { width: 100%; height: 100%; background: #131722; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                #chart-container { width: 100%; height: 100%; position: relative; }
                #watermark {
                    position: absolute;
                    top: 10px;
                    left: 12px;
                    color: rgba(255, 255, 255, 0.12);
                    font-size: 20px;
                    font-weight: 800;
                    pointer-events: none;
                    z-index: 1;
                    letter-spacing: 1px;
                }
            </style>
            <!-- Official TradingView Lightweight Charts Library -->
            <script src="https://unpkg.com/lightweight-charts@4.1.1/dist/lightweight-charts.standalone.production.js"></script>
        </head>
        <body>
            <div id="chart-container">
                <div id="watermark">${instrument.symbol} • ${timeframe.code}</div>
            </div>

            <script>
                (function() {
                    const container = document.getElementById('chart-container');
                    const candleData = $candlesJsonStr;
                    const volumeData = $volumeJsonStr;
                    const markersData = $markersJsonStr;
                    const priceLinesData = $priceLinesJsonStr;

                    if (typeof LightweightCharts === 'undefined') {
                        renderPureCanvasFallback(container, candleData, volumeData, markersData, priceLinesData);
                        return;
                    }

                    function renderPureCanvasFallback(container, candles, volume, markers, priceLines) {
                        if (!candles || candles.length === 0) {
                            container.innerHTML = '<div style="color:#d1d4dc;padding:20px;text-align:center;">Memuat TradingView Engine...</div>';
                            return;
                        }
                        const canvas = document.createElement('canvas');
                        const w = container.clientWidth || window.innerWidth || 360;
                        const h = container.clientHeight || 450;
                        canvas.width = w;
                        canvas.height = h;
                        canvas.style.width = '100%';
                        canvas.style.height = '100%';
                        container.innerHTML = '';
                        container.appendChild(canvas);
                        const ctx = canvas.getContext('2d');

                        let minPrice = Infinity;
                        let maxPrice = -Infinity;
                        candles.forEach(function(c) {
                            if (c.low < minPrice) minPrice = c.low;
                            if (c.high > maxPrice) maxPrice = c.high;
                        });
                        const padPrice = (maxPrice - minPrice) * 0.08 || 1;
                        minPrice -= padPrice;
                        maxPrice += padPrice;

                        // Dark Background
                        ctx.fillStyle = '#131722';
                        ctx.fillRect(0, 0, w, h);

                        // Grid
                        ctx.strokeStyle = 'rgba(42, 46, 57, 0.4)';
                        ctx.lineWidth = 1;
                        for (let y = 40; y < h - 40; y += 60) {
                            ctx.beginPath();
                            ctx.moveTo(0, y);
                            ctx.lineTo(w, y);
                            ctx.stroke();
                        }

                        const rightMargin = 60;
                        const plotW = w - rightMargin;
                        const plotH = h - 25;
                        const n = candles.length;
                        const colW = Math.max(3, plotW / n);
                        const candleW = Math.max(2, colW * 0.7);

                        function priceToY(p) {
                            return plotH - ((p - minPrice) / (maxPrice - minPrice)) * (plotH - 30) - 15;
                        }

                        // Draw Candles
                        candles.forEach(function(c, idx) {
                            const x = idx * colW + colW / 2;
                            const isUp = c.close >= c.open;
                            const color = isUp ? '#089981' : '#f23645';
                            ctx.strokeStyle = color;
                            ctx.fillStyle = color;

                            ctx.beginPath();
                            ctx.moveTo(x, priceToY(c.high));
                            ctx.lineTo(x, priceToY(c.low));
                            ctx.stroke();

                            const yOpen = priceToY(c.open);
                            const yClose = priceToY(c.close);
                            const top = Math.min(yOpen, yClose);
                            const bHeight = Math.max(1.5, Math.abs(yOpen - yClose));
                            ctx.fillRect(x - candleW / 2, top, candleW, bHeight);
                        });

                        // Price Scale on Right
                        ctx.fillStyle = '#758696';
                        ctx.font = '10px sans-serif';
                        ctx.textAlign = 'left';
                        for (let i = 0; i <= 5; i++) {
                            const p = minPrice + (maxPrice - minPrice) * (i / 5);
                            const y = priceToY(p);
                            ctx.fillText(p.toFixed($precision), plotW + 4, y + 3);
                        }

                        // Draw Price Lines (SL / TP / Key Levels)
                        if (priceLines && priceLines.length > 0) {
                            ctx.setLineDash([4, 4]);
                            priceLines.forEach(function(l) {
                                const y = priceToY(l.price);
                                ctx.strokeStyle = l.color;
                                ctx.beginPath();
                                ctx.moveTo(0, y);
                                ctx.lineTo(plotW, y);
                                ctx.stroke();
                                ctx.fillStyle = l.color;
                                ctx.fillText(l.title || '', 6, y - 4);
                            });
                            ctx.setLineDash([]);
                        }
                    }

                    const chart = LightweightCharts.createChart(container, {
                        width: container.clientWidth || window.innerWidth,
                        height: container.clientHeight || 450,
                        layout: {
                            background: { type: 'solid', color: '#131722' },
                            textColor: '#d1d4dc',
                            fontSize: 11
                        },
                        grid: {
                            vertLines: { color: 'rgba(42, 46, 57, 0.5)' },
                            horzLines: { color: 'rgba(42, 46, 57, 0.5)' }
                        },
                        crosshair: {
                            mode: LightweightCharts.CrosshairMode.Normal,
                            vertLine: { color: '#758696', width: 1, style: 3, labelBackgroundColor: '#2a2e39' },
                            horzLine: { color: '#758696', width: 1, style: 3, labelBackgroundColor: '#2a2e39' }
                        },
                        rightPriceScale: {
                            borderColor: '#2a2e39',
                            scaleMargins: { top: 0.1, bottom: 0.2 }
                        },
                        timeScale: {
                            borderColor: '#2a2e39',
                            timeVisible: true,
                            secondsVisible: false
                        }
                    });

                    // 1. Candlestick Series (TradingView Teal & Coral Palette)
                    const candleSeries = chart.addCandlestickSeries({
                        upColor: '#089981',
                        downColor: '#f23645',
                        borderUpColor: '#089981',
                        borderDownColor: '#f23645',
                        wickUpColor: '#089981',
                        wickDownColor: '#f23645',
                        priceFormat: {
                            type: 'price',
                            precision: $precision,
                            minMove: $minMove
                        }
                    });

                    // 2. Volume Histogram (Bottom Overlay)
                    const volumeData = $volumeJsonStr;
                    if (volumeData && volumeData.length > 0 && $showVolume) {
                        const volumeSeries = chart.addHistogramSeries({
                            priceFormat: { type: 'volume' },
                            priceScaleId: '',
                        });
                        volumeSeries.priceScale().applyOptions({
                            scaleMargins: { top: 0.82, bottom: 0 }
                        });
                        volumeSeries.setData(volumeData);
                    }

                    // 3. EMA 9 (Cyan) & EMA 21 (Orange)
                    const ema9Data = $ema9JsonStr;
                    if (ema9Data && ema9Data.length > 0) {
                        const ema9Series = chart.addLineSeries({
                            color: '#00e5ff',
                            lineWidth: 1.5,
                            title: 'EMA 9',
                            priceScaleId: 'right'
                        });
                        ema9Series.setData(ema9Data);
                    }

                    const ema21Data = $ema21JsonStr;
                    if (ema21Data && ema21Data.length > 0) {
                        const ema21Series = chart.addLineSeries({
                            color: '#ff9800',
                            lineWidth: 1.5,
                            title: 'EMA 21',
                            priceScaleId: 'right'
                        });
                        ema21Series.setData(ema21Data);
                    }

                    // 4. Set Candlestick Data
                    const candleData = $candlesJsonStr;
                    if (candleData && candleData.length > 0) {
                        candleSeries.setData(candleData);
                    }

                    // 5. Set Pattern Markers (Auto-drawn arrows on chart)
                    const markersData = $markersJsonStr;
                    if (markersData && markersData.length > 0) {
                        candleSeries.setMarkers(markersData);
                    }

                    // 6. Set Price Lines (SL, TP, Key Level)
                    const priceLinesData = $priceLinesJsonStr;
                    if (priceLinesData && priceLinesData.length > 0) {
                        priceLinesData.forEach(function(line) {
                            candleSeries.createPriceLine({
                                price: line.price,
                                color: line.color,
                                lineWidth: 1.5,
                                lineStyle: line.lineStyle,
                                axisLabelVisible: true,
                                title: line.title
                            });
                        });
                    }

                    // Auto-scale to fit contents nicely
                    chart.timeScale().fitContent();

                    // Resize handler
                    window.addEventListener('resize', function() {
                        chart.applyOptions({
                            width: container.clientWidth,
                            height: container.clientHeight
                        });
                    });
                })();
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
        // Chart Header Controls: Indicator Badges & Fit Content Button
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoGraph,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "TradingView Engine",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = BuyGreen.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "SOLUSI B",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = BuyGreen,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showEma) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CyanEma.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "EMA 9",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanEma,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = OrangeEma.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "EMA 21",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrangeEma,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { isFitContentRequested++ },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Fit Content",
                            tint = GoldPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Lightweight Chart View Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("tradingview_lightweight_chart")
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        setBackgroundColor(android.graphics.Color.parseColor("#131722"))
                        webViewClient = WebViewClient()
                        lastLoadedHtml = htmlDocument
                        loadDataWithBaseURL("https://unpkg.com", htmlDocument, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    if (lastLoadedHtml != htmlDocument) {
                        lastLoadedHtml = htmlDocument
                        webView.loadDataWithBaseURL("https://unpkg.com", htmlDocument, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Pattern Overlay Card at Bottom (If patterns exist)
            if (showPatterns && detectedPatterns.isNotEmpty()) {
                val currentPattern = detectedPatterns[selectedPatternIndex.coerceIn(0, detectedPatterns.lastIndex)]
                val isBuy = currentPattern.action == SignalAction.BUY
                val badgeColor = if (isBuy) BuyGreen else SellRed

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xEB131722),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Prev Pattern
                            if (detectedPatterns.size > 1) {
                                IconButton(
                                    onClick = {
                                        selectedPatternIndex = if (selectedPatternIndex > 0) selectedPatternIndex - 1 else detectedPatterns.lastIndex
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Sebelumnya", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                }
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = badgeColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = currentPattern.action.badgeText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(
                                        text = currentPattern.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPrimary
                                    )
                                    Text(
                                        text = "[${currentPattern.confluenceGrade.code}]",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "SL: ${instrument.formatPrice(currentPattern.suggestedStopLoss)} | TP: ${instrument.formatPrice(currentPattern.suggestedTakeProfit)} (R:R 1:${String.format(java.util.Locale.US, "%.1f", currentPattern.estimatedRiskReward)})",
                                    fontSize = 10.sp,
                                    color = Color.LightGray
                                )
                            }

                            // Next Pattern
                            if (detectedPatterns.size > 1) {
                                IconButton(
                                    onClick = {
                                        selectedPatternIndex = if (selectedPatternIndex < detectedPatterns.lastIndex) selectedPatternIndex + 1 else 0
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Berikutnya", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Button(
                            onClick = { onApplyPatternToRisk(currentPattern) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = "Pakai SL/TP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
