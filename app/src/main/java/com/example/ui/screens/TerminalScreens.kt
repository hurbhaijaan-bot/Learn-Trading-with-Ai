package com.example.ui.screens

import com.example.data.simulator.MarketDataSimulator
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.TradeTransaction
import com.example.data.database.UserAsset
import com.example.data.database.UserProfile
import com.example.data.simulator.Candle
import com.example.data.simulator.CoinMarketData
import com.example.ui.theme.*
import com.example.ui.viewmodel.Lesson
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// Local Converter Constants for Multi-currency support
private val CURRENCY_SYMBOLS = mapOf("USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "AUD" to "A$")
private val CURRENCY_FACTORS = mapOf("USD" to 1.0, "EUR" to 0.92, "GBP" to 0.78, "JPY" to 155.40, "AUD" to 1.51)

/**
 * Custom High-End Candlestick Chart with indicator overlays
 */
@Composable
fun CandlestickChart(
    candles: List<Candle>,
    emaVal: Double,
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Terminal reading candles telemetry...", color = TextMuted, fontSize = 12.sp)
        }
        return
    }

    val minPrice = candles.minOf { it.low }
    val maxPrice = candles.maxOf { it.high }
    val priceRange = (maxPrice - minPrice).coerceAtLeast(0.0001)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Draw dynamic coordinate lines (Grid)
        val gridLines = 5
        val colorGrid = SlateSeparator.copy(alpha = 0.5f)
        for (i in 0..gridLines) {
            val y = (height / gridLines) * i
            drawLine(
                color = colorGrid,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            // Price Tag Indicators on Y-axis
            // Calculated proportionally
        }

        val segmentCount = candles.size
        val candleWidthMultiplier = 0.65f
        val candleSpacing = width / segmentCount

        candles.forEachIndexed { index, candle ->
            val xCenter = (index * candleSpacing) + (candleSpacing / 2f)

            // Normalize Y scale with margin
            val yOpen = (height - ((candle.open - minPrice) / priceRange) * height * 0.85f - (height * 0.07f)).toFloat()
            val yClose = (height - ((candle.close - minPrice) / priceRange) * height * 0.85f - (height * 0.07f)).toFloat()
            val yHigh = (height - ((candle.high - minPrice) / priceRange) * height * 0.85f - (height * 0.07f)).toFloat()
            val yLow = (height - ((candle.low - minPrice) / priceRange) * height * 0.85f - (height * 0.07f)).toFloat()

            val candleColor = if (candle.close >= candle.open) NeonGreen else NeonPink

            // 1. Draw Wicks (high/low poles)
            drawLine(
                color = candleColor,
                start = Offset(xCenter, yHigh),
                end = Offset(xCenter, yLow),
                strokeWidth = 2f
            )

            // 2. Draw Candlestick Body Core
            val cardTop = minOf(yOpen, yClose)
            val cardBottom = maxOf(yOpen, yClose)
            val rectHeight = (cardBottom - cardTop).coerceAtLeast(4f)
            val rectWidth = candleSpacing * candleWidthMultiplier

            drawRect(
                color = candleColor,
                topLeft = Offset(xCenter - (rectWidth / 2f), cardTop),
                size = Size(rectWidth, rectHeight)
            )
        }

        // Draw EMA20 Line Projection on top of candles
        val emaPath = Path()
        candles.forEachIndexed { index, candle ->
            val xCenter = (index * candleSpacing) + (candleSpacing / 2f)
            // Scale ema slightly relative to close
            val simulatedEma = candle.close * 0.992 + (emaVal / candles.last().close) * candle.close * 0.008
            val yEma = (height - ((simulatedEma - minPrice) / priceRange) * height * 0.85f - (height * 0.07f)).toFloat()

            if (index == 0) {
                emaPath.moveTo(xCenter, yEma)
            } else {
                emaPath.lineTo(xCenter, yEma)
            }
        }

        drawPath(
            path = emaPath,
            color = NeonCyan,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

// --- Dynamic Text Ticker Component providing instantaneous flashing on change ---
@Composable
fun LivePriceTickerRow(
    coin: CoinMarketData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Watch for price flashes
    var previousPrice by remember { mutableStateOf(coin.currentPrice) }
    val tickColor = remember { Animatable(Color.Transparent) }

    LaunchedEffect(coin.currentPrice) {
        if (coin.currentPrice > previousPrice) {
            tickColor.animateTo(NeonGreen.copy(alpha = 0.25f), animationSpec = tween(150))
            tickColor.animateTo(Color.Transparent, animationSpec = tween(500))
        } else if (coin.currentPrice < previousPrice) {
            tickColor.animateTo(NeonPink.copy(alpha = 0.25f), animationSpec = tween(150))
            tickColor.animateTo(Color.Transparent, animationSpec = tween(500))
        }
        previousPrice = coin.currentPrice
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                1.dp,
                if (isSelected) NeonCyan.copy(alpha = 0.6f) else SlateSeparator,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("ticker_${coin.symbol}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyberCarbonLight else CyberCarbon
        )
    ) {
        Row(
            modifier = Modifier
                .drawBehind { drawRect(tickColor.value) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Coin Icon Indicator
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSeparator),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = coin.symbol.take(2),
                    color = when (coin.symbol) {
                        "BTC" -> NeonGreen
                        "ETH" -> NeonCyan
                        "SOL" -> NeonOrange
                        else -> TextPrimary
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = coin.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(text = "Net: ${coin.network}", color = TextMuted, fontSize = 12.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${String.format("%,.2f", coin.currentPrice)}",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (coin.change24hPercent >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = "trend",
                        tint = if (coin.change24hPercent >= 0) NeonGreen else NeonPink,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${if (coin.change24hPercent >= 0) "+" else ""}${String.format("%.2f", coin.change24hPercent)}%",
                        color = if (coin.change24hPercent >= 0) NeonGreen else NeonPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// --- PANEL 1: terminal interactive dashboard ---
@Composable
fun DashboardPanel(
    viewModel: MainViewModel,
    coinsList: List<CoinMarketData>,
    selectedCoin: String
) {
    // Collect ViewModel states
    val searchQuery by viewModel.searchQuery.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val customWatchlists by viewModel.customWatchlists.collectAsState()
    val selectedWatchlistTab by viewModel.selectedWatchlistTab.collectAsState()
    val priceAlerts by viewModel.priceAlerts.collectAsState()
    val activeAlertBanner by viewModel.activeAlertBanner.collectAsState()
    val coinAnalysis by viewModel.coinAnalysis.collectAsState()
    val isAnalysisLoading by viewModel.isAnalysisLoading.collectAsState()

    // Local UI states
    var viewingCoinSymbol by remember { mutableStateOf<String?>(null) }
    var showSetAlertDialog by remember { mutableStateOf(false) }
    var alertTargetPrice by remember { mutableStateOf("") }
    var alertTypeState by remember { mutableStateOf("ABOVE") } // "ABOVE" | "BELOW"
    
    // Quick categories for the main discovery hub
    val watchlistsTabs = remember(customWatchlists) {
        listOf("All", "Favorites") + customWatchlists.keys.toList()
    }

    // Filter coinsList based on active watchlist tab selection filters
    val filteredCoins = remember(selectedWatchlistTab, coinsList, favorites, customWatchlists) {
        when (selectedWatchlistTab) {
            "All" -> coinsList
            "Favorites" -> coinsList.filter { favorites.contains(it.symbol) }
            else -> {
                val symbolsInList = customWatchlists[selectedWatchlistTab] ?: emptySet()
                coinsList.filter { symbolsInList.contains(it.symbol) }
            }
        }
    }

    val activeCoin = coinsList.find { it.symbol == (viewingCoinSymbol ?: selectedCoin) } ?: coinsList.first()

    // Alert setting dialog
    if (showSetAlertDialog) {
        AlertDialog(
            onDismissRequest = { showSetAlertDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SET SPOT PRICE ALERT", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Receive dynamic telemetry banners when ${activeCoin.symbol} ticks past your target boundary.", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Trigger Condition", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { alertTypeState = "ABOVE" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (alertTypeState == "ABOVE") NeonGreen.copy(alpha = 0.2f) else CyberCarbon,
                                contentColor = if (alertTypeState == "ABOVE") NeonGreen else TextMuted
                            ),
                            modifier = Modifier.weight(1f).border(1.dp, if (alertTypeState == "ABOVE") NeonGreen else SlateSeparator, RoundedCornerShape(8.dp))
                        ) {
                            Text("Goes ABOVE (📈)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { alertTypeState = "BELOW" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (alertTypeState == "BELOW") NeonPink.copy(alpha = 0.2f) else CyberCarbon,
                                contentColor = if (alertTypeState == "BELOW") NeonPink else TextMuted
                            ),
                            modifier = Modifier.weight(1f).border(1.dp, if (alertTypeState == "BELOW") NeonPink else SlateSeparator, RoundedCornerShape(8.dp))
                        ) {
                            Text("Goes BELOW (📉)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Target Value (USD)", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = alertTargetPrice,
                        onValueChange = { alertTargetPrice = it },
                        placeholder = { Text("e.g. ${String.format("%.2f", activeCoin.currentPrice * 1.05)}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SlateSeparator,
                            focusedContainerColor = CyberBlack,
                            unfocusedContainerColor = CyberBlack
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = alertTargetPrice.toDoubleOrNull()
                        if (target != null && target > 0) {
                            viewModel.setPriceAlert(activeCoin.symbol, target, alertTypeState)
                            showSetAlertDialog = false
                            alertTargetPrice = ""
                        }
                    }
                ) {
                    Text("CREATE TARGET ALERT", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetAlertDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = CyberCarbon,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, SlateSeparator, RoundedCornerShape(16.dp))
        )
    }

    // --- COIN DETAILS VIEW OVERLAY ---
    if (viewingCoinSymbol != null) {
        val coin = coinsList.find { it.symbol == viewingCoinSymbol } ?: activeCoin
        var chartInterval by remember { mutableStateOf("1D") }
        
        // Dynamic interval candles recalculation for simulated interactive feedback
        val filteredCandles = remember(coin, chartInterval) {
            when (chartInterval) {
                "1H" -> coin.candles.takeLast(6)
                "4H" -> coin.candles.takeLast(12)
                "1D" -> coin.candles
                "1W" -> coin.candles.map { it.copy(close = it.close * 1.015, high = it.high * 1.02) }
                "1M" -> coin.candles.mapIndexed { idx, candle -> candle.copy(close = candle.close * (1 + 0.02 * Math.sin(idx.toDouble()))) }
                "1Y" -> coin.candles.mapIndexed { idx, candle -> candle.copy(close = candle.close * (1 + 0.04 * idx)) }
                else -> coin.candles
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewingCoinSymbol = null },
                        modifier = Modifier
                            .background(SlateSeparator, RoundedCornerShape(50))
                            .size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, "back to discovery", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(coin.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (coin.isVerified) NeonGreen.copy(alpha = 0.15f) else NeonPink.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (coin.isVerified) "VERIFIED" else "UNVERIFIED",
                                    color = if (coin.isVerified) NeonGreen else NeonPink,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text("${coin.symbol} / USD · ${coin.network} network", fontSize = 12.sp, color = TextMuted)
                    }

                    // Favorite toggler
                    val isFav = favorites.contains(coin.symbol)
                    IconButton(
                        onClick = { viewModel.toggleFavorite(coin.symbol) }
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "watchlist toggler",
                            tint = if (isFav) Color(0xFFFFD700) else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Current Price + Volatility Hud Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateSeparator, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("SPOT TELEMETRY", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$${String.format("%,.4f", coin.currentPrice)}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (coin.change24hPercent >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = "trend",
                                    tint = if (coin.change24hPercent >= 0) NeonGreen else NeonPink,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${if (coin.change24hPercent >= 0) "+" else ""}${String.format("%.2f", coin.change24hPercent)}% (24h)",
                                    color = if (coin.change24hPercent >= 0) NeonGreen else NeonPink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Mini Metadata Block
                        Column(horizontalAlignment = Alignment.End) {
                            Text("EST. MARKET CAP", color = TextMuted, fontSize = 10.sp)
                            val simulatedCap = coin.currentPrice * when(coin.symbol) {
                                "BTC" -> 19700000.0
                                "ETH" -> 120000000.0
                                "SOL" -> 460000000.0
                                "USDT", "USDC" -> 110000000000.0
                                else -> 8500000000.0
                            }
                            Text(
                                text = "$${if (simulatedCap >= 1e9) String.format("%.2fB", simulatedCap/1e9) else String.format("%.2fM", simulatedCap/1e6)}",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("24H VOL", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = "$${String.format("%,.0f", coin.volume24h)}",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Interactive Candlestick Chart Window with Interval Tab Selector
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateSeparator, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Interval Selector row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBlack, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("1H", "4H", "1D", "1W", "1M", "1Y").forEach { tab ->
                                val active = chartInterval == tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) NeonCyan else Color.Transparent)
                                        .clickable { chartInterval = tab }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab,
                                        color = if (active) CyberBlack else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        // Candlestick Core Drawing
                        CandlestickChart(
                            candles = filteredCandles,
                            emaVal = coin.ema20,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .testTag("candlestick_chart")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive indicators telemetry stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("RSI Momentum", color = TextMuted, fontSize = 10.sp)
                                Text(String.format("%.2f", coin.rsi), color = if (coin.rsi > 70) NeonPink else NeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column {
                                Text("MACD Divergence", color = TextMuted, fontSize = 10.sp)
                                Text(String.format("%.5f", coin.macd), color = if (coin.macd >= 0) NeonGreen else NeonPink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column {
                                Text("EMA 20 Standard", color = TextMuted, fontSize = 10.sp)
                                Text("$${String.format("%,.2f", coin.ema20)}", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column {
                                Text("24H High Spike", color = TextMuted, fontSize = 10.sp)
                                Text("$${String.format("%,.4f", coin.high24h)}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Security warning block for unverified or newly listed volatile assets
            if (!coin.isVerified) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NeonOrange.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = NeonOrange.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = NeonOrange, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("HIGH VOLATILITY UNVERIFIED ASSET ALERT", color = NeonOrange, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                Text(
                                    "This cryptographic asset (${coin.symbol}) belongs heavily to speculation pool launchpads on ${coin.network}. Execution slippage ranges up to 1.25%. Limit entries are strongly advised to preserve trading capital.",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Action Operations Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Create alarm button
                    IconButton(
                        onClick = { showSetAlertDialog = true },
                        modifier = Modifier
                            .background(CyberCarbon, RoundedCornerShape(12.dp))
                            .border(1.dp, SlateSeparator, RoundedCornerShape(12.dp))
                            .size(52.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, "create trigger alert", tint = NeonCyan, modifier = Modifier.size(22.dp))
                    }

                    // Direct SPOT buy order button
                    Button(
                        onClick = {
                            viewModel.selectCoin(coin.symbol)
                            viewModel.tradeType.value = "BUY"
                            viewModel.activeTab.value = 2 // Direct active redirect to Spot Trading Desk
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = CyberBlack),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text("BUY ASSET SPOT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Direct SPOT sell button
                    Button(
                        onClick = {
                            viewModel.selectCoin(coin.symbol)
                            viewModel.tradeType.value = "SELL"
                            viewModel.activeTab.value = 2 // Direct active redirect to Spot Trading Desk
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = TextPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text("SELL ASSET SPOT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // AI COIN DETAILED ANALYSIS CORE SECTION
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateSeparator, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SmartToy, "ai model analysis", tint = NeonCyan, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI TELEMETRY DEEP ANALYZER", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            
                            Button(
                                onClick = { viewModel.analyzeCoinWithAI(coin.symbol) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAnalysisLoading) SlateSeparator else NeonCyan.copy(alpha = 0.15f),
                                    contentColor = NeonCyan
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                if (isAnalysisLoading) {
                                    CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                                } else {
                                    Text("RUN ANALYZER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (coinAnalysis.isNotEmpty()) {
                            Text(
                                text = coinAnalysis,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier
                                    .background(CyberBlack, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberBlack, RoundedCornerShape(10.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Nexa Intelligence offline.", color = TextMuted, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Tap Run Analyzer to perform deep structural scenario modeling via direct generative REST models.", color = TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    } else {
        // --- MAIN DISCOVERY HUB FLOW ---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Triggered banner notice
            if (activeAlertBanner != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.NotificationsActive, null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = activeAlertBanner!!,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissAlertBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "close", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Title Header Space
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🔎 UNIVERSAL COIN DISCOVERY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Search & Telemetry engine",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(NeonCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${coinsList.size} CRYPTO FEED", color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- SMART COIN SEARCH INPUT BAR ---
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search any coin, token, or ticker...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, "search indicator icon", tint = NeonCyan, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(Icons.Default.Close, "clear input value", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SlateSeparator,
                        focusedContainerColor = CyberCarbon,
                        unfocusedContainerColor = CyberCarbon
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("universal_search_bar")
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // If a search query is active, display matching instant suggestion results
            if (searchQuery.isNotBlank()) {
                val matches = coinsList.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.symbol.contains(searchQuery, ignoreCase = true)
                }
                
                if (matches.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCarbon, RoundedCornerShape(12.dp))
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No cryptographic coins match your filter criteria.", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "INSTANT MATCH SUGGESTIONS FEED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.0.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    items(matches) { hit ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(1.dp, SlateSeparator, RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.searchQuery.value = "" // clear search index
                                    viewingCoinSymbol = hit.symbol   // open asset screen
                                },
                            colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Symbol graphic avatar
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateSeparator),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = hit.symbol.take(2),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = when(hit.category) {
                                            "Blue Chip" -> NeonGreen
                                            "Meme" -> NeonOrange
                                            "New Listing" -> NeonCyan
                                            else -> TextPrimary
                                        }
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(hit.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SlateSeparator)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(hit.symbol, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(text = "Net: ${hit.network} · ${hit.category}", color = TextMuted, fontSize = 11.sp)
                                }
                                
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$${String.format("%,.4f", hit.currentPrice)}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "${if (hit.change24hPercent >= 0) "+" else ""}${String.format("%.2f", hit.change24hPercent)}%",
                                        color = if (hit.change24hPercent >= 0) NeonGreen else NeonPink,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // searchQuery is blank: show main Discovery dashboards
                
                // --- RECENT SEARCH HISTORY SECTION ---
                if (recentSearches.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("RECENT SEARCHES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.0.sp)
                            Text(
                                "CLEAR HISTORIC",
                                color = NeonPink,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { viewModel.clearRecentSearches() }
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentSearches.forEach { sym ->
                                val match = coinsList.find { it.symbol == sym }
                                if (match != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CyberCarbon)
                                            .border(1.dp, SlateSeparator, RoundedCornerShape(8.dp))
                                            .clickable { viewingCoinSymbol = sym }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.History, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(match.symbol, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("$${String.format("%,.2f", match.currentPrice)}", color = TextMuted, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // --- TRENDING SEARCH SUGGESTIONS ---
                item {
                    Text("TRENDING SEARCHES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.0.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // High popularity tokens
                        listOf("PEPE", "FLOKI", "SOL", "WIF", "NOT").forEach { sym ->
                            val match = coinsList.find { it.symbol == sym }
                            if (match != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NeonCyan.copy(alpha = 0.05f))
                                        .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .clickable { viewingCoinSymbol = sym }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.TrendingUp, null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(sym, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${if (match.change24hPercent >= 0) "+" else ""}${String.format("%.1f", match.change24hPercent)}%", color = if (match.change24hPercent >= 0) NeonGreen else NeonPink, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // --- WATCHLIST & CATEGORIES SECTION ---
                item {
                    Text("YOUR WATCHLIST GROUPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.0.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        watchlistsTabs.forEach { tab ->
                            val active = selectedWatchlistTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) NeonCyan else CyberCarbon)
                                    .border(1.dp, if (active) NeonCyan else SlateSeparator, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.selectedWatchlistTab.value = tab }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (tab == "Favorites") {
                                        Icon(Icons.Default.Star, null, tint = if (active) CyberBlack else Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = tab.uppercase(),
                                        color = if (active) CyberBlack else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }



                if (filteredCoins.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCarbon, RoundedCornerShape(12.dp))
                                .padding(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedWatchlistTab == "Favorites") "No favorited coins yet! Tap the Star icon on any coin details screen." else "Custom Watchlist is empty.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(filteredCoins) { element ->
                        LivePriceTickerRow(
                            coin = element,
                            isSelected = element.symbol == selectedCoin,
                            onClick = { viewingCoinSymbol = element.symbol }
                        )
                    }
                }

                // --- PREMIUM DISCOVERY HUB SECTIONS / BOARDS ---
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("🔥 GLOBAL DISCOVERY ZONE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan, letterSpacing = 1.5.sp)
                    Text("Aggregated spot listing clusters", fontSize = 14.sp, color = TextMuted, modifier = Modifier.padding(bottom = 12.dp))
                    
                    // Cards Grid sections
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Top Gainers & Losers splits
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Top Gainers Card
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, SlateSeparator, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("TOP 24H GAINERS 🟢", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val gainers = coinsList.sortedByDescending { it.change24hPercent }.take(3)
                                    gainers.forEach { c ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewingCoinSymbol = c.symbol }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(c.symbol, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("+${String.format("%.1f", c.change24hPercent)}%", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            // Top Losers Card
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, SlateSeparator, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("TOP 24H LOSERS 🔴", color = NeonPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val losers = coinsList.sortedBy { it.change24hPercent }.take(3)
                                    losers.forEach { c ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewingCoinSymbol = c.symbol }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(c.symbol, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("${String.format("%.1f", c.change24hPercent)}%", color = NeonPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Meme zone & New Listings split
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Meme Zone list
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, SlateSeparator, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("MEME COIN ZONE 🐕", color = NeonOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val memes = coinsList.filter { it.category == "Meme" }.take(3)
                                    memes.forEach { c ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewingCoinSymbol = c.symbol }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(c.symbol, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("$${String.format("%,.4f", c.currentPrice)}", color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            // New listings list
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, SlateSeparator, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("NEWLY LAUNCHED ✨", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val newly = coinsList.filter { it.category == "New Listing" }.take(3)
                                    newly.forEach { c ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewingCoinSymbol = c.symbol }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(c.symbol, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(NeonPink.copy(alpha = 0.1f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("UNVERIFIED", color = NeonPink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Most Viewed List Row
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SlateSeparator, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("MOST VIEWED ASSETS INDEX 👁️", color = TextNeonCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                val mostViewed = coinsList.sortedByDescending { it.viewsCount }.take(4)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    mostViewed.forEach { c ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CyberBlack)
                                                .border(1.dp, SlateSeparator, RoundedCornerShape(8.dp))
                                                .clickable { viewingCoinSymbol = c.symbol }
                                                .padding(8.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                                Text(c.symbol, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("${c.viewsCount} views", color = TextMuted, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// --- PANEL 2: WALLET SYSTEM COMPONENT ---
@Composable
fun WalletPanel(
    viewModel: MainViewModel,
    assetsList: List<UserAsset>,
    profile: UserProfile?,
    transactionsList: List<TradeTransaction>
) {
    if (profile == null) return

    val factor = CURRENCY_FACTORS[profile.preferredCurrency] ?: 1.0
    val symbolSign = CURRENCY_SYMBOLS[profile.preferredCurrency] ?: "$"

    // Compute total assets valuation in USD
    var cryptosValueUsd = 0.0
    val marketState = MarketDataSimulator.marketState.value
    assetsList.forEach { asset ->
        val currentPrice = marketState[asset.symbol]?.currentPrice ?: asset.averageBuyPrice
        cryptosValueUsd += asset.holdings * currentPrice
    }

    val totalValueUsd = profile.usdBalance + cryptosValueUsd
    val netConvertedVal = totalValueUsd * factor
    val netCashBalance = profile.usdBalance * factor

    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("RESET TRADING WALLET?", color = TextPrimary) },
            text = { Text("This will wipe simulated holdings and restore virtual funds to $100,000 spot capital value for learning. Continue?", color = TextMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetWholePlatform()
                        showResetDialog = false
                    }
                ) {
                    Text("YES, EXECUTE RESET", color = NeonPink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL", color = TextPrimary)
                }
            },
            containerColor = CyberCarbon,
            shape = RoundedCornerShape(16.dp)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Vault Allocation HUD card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (profile.subscriptionStatus == "PRO") NeonCyan else SlateSeparator,
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (profile.subscriptionStatus == "PRO") "NEXA PRO DIGITAL VAULT" else "NEXA STANDARD WALLET",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (profile.subscriptionStatus == "PRO") NeonCyan else TextMuted,
                            letterSpacing = 1.2.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (profile.subscriptionStatus == "PRO") NeonCyan.copy(alpha = 0.2f) else SlateSeparator)
                                .clickable { viewModel.toggleSubscriptionTier() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (profile.subscriptionStatus == "PRO") "PRO ACTIVE" else "UPGRADE PRO (VIRTUAL)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (profile.subscriptionStatus == "PRO") NeonCyan else TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Aggregate Assets Valuation", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = "$symbolSign${String.format("%,.2f", netConvertedVal)}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Virtual Cash Reserves", color = TextMuted, fontSize = 11.sp)
                            Text(
                                "$symbolSign${String.format("%,.2f", netCashBalance)}",
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Crypto Holdings Worth", color = TextMuted, fontSize = 11.sp)
                            Text(
                                "$symbolSign${String.format("%,.2f", cryptosValueUsd * factor)}",
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Simulated Asset bar allocator
                    Text("PORTFOLIO ASSETS STRUCTURATION", color = TextMuted, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(SlateSeparator)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Cash fill
                            val cashRatio = if (totalValueUsd > 0) (profile.usdBalance / totalValueUsd).toFloat() else 0f
                            val btcRatio = if (totalValueUsd > 0) (((marketState["BTC"]?.currentPrice ?: 0.0) * (assetsList.find { it.symbol == "BTC" }?.holdings ?: 0.0)) / totalValueUsd).toFloat() else 0f
                            val ethRatio = if (totalValueUsd > 0) (((marketState["ETH"]?.currentPrice ?: 0.0) * (assetsList.find { it.symbol == "ETH" }?.holdings ?: 0.0)) / totalValueUsd).toFloat() else 0f

                            if (cashRatio > 0) Box(modifier = Modifier.fillMaxHeight().weight(cashRatio.coerceAtLeast(0.01f)).background(NeonGreen))
                            if (btcRatio > 0) Box(modifier = Modifier.fillMaxHeight().weight(btcRatio.coerceAtLeast(0.01f)).background(NeonCyan))
                            if (ethRatio > 0) Box(modifier = Modifier.fillMaxHeight().weight(ethRatio.coerceAtLeast(0.01f)).background(NeonOrange))
                        }
                    }

                    // Legend labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(NeonGreen))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("USD Cash", color = TextMuted, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(NeonCyan))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BTC", color = TextMuted, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(NeonOrange))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ETH", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Multi-Chain Ledger Section Header
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE ASSET ALLOCATIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.2.sp
                )
                
                // Local Currency Preference Switcher
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pref:", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, SlateSeparator, RoundedCornerShape(8.dp))
                            .clickable {
                                val order = listOf("USD", "EUR", "GBP", "JPY", "AUD")
                                val nextIndex = (order.indexOf(profile.preferredCurrency) + 1) % order.size
                                viewModel.selectPreferredCurrency(order[nextIndex])
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(profile.preferredCurrency, color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Display individual physical assets
        if (assetsList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No assets held. Utilize the Trade Desk order terminal.", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            items(assetsList) { asset ->
                val coinMarket = marketState[asset.symbol]
                val itemPrice = coinMarket?.currentPrice ?: asset.averageBuyPrice
                val totalUSDValue = asset.holdings * itemPrice
                val gainPercent = if (asset.averageBuyPrice > 0) ((itemPrice - asset.averageBuyPrice) / asset.averageBuyPrice) * 100 else 0.0

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, SlateSeparator, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = asset.symbol,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${asset.holdings} units @ $${String.format("%.2f", asset.averageBuyPrice)}",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$symbolSign${String.format("%,.2f", totalUSDValue * factor)}",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (gainPercent >= 0) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = if (gainPercent >= 0) NeonGreen else NeonPink,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${if (gainPercent >= 0) "+" else ""}${String.format("%.2f", gainPercent)}%",
                                    color = if (gainPercent >= 0) NeonGreen else NeonPink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Security Layer UI simulated toggles
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "CYBERNETIC SECURITY INDICATORS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateSeparator, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Biometric Encryption Lock", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Simulates fingerprint verification layer", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = profile.isBiometricsSimulatedEnabled,
                            onCheckedChange = { viewModel.toggleSecurityFeature("BIOMETRICS") },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonGreen,
                                checkedTrackColor = NeonGreen.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SlateSeparator
                            )
                        )
                    }

                    Divider(color = SlateSeparator, modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Multi-Factor Authentication (2FA)", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Mandatory network trade authorization", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = profile.is2FAEnabled,
                            onCheckedChange = { viewModel.toggleSecurityFeature("2FA") },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonCyan,
                                checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SlateSeparator
                            )
                        )
                    }
                }
            }
        }

        // Transactions Audit Ledger logs
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRANSACTIONS AUDIT LEDGER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "RESET ALL",
                    color = NeonPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { showResetDialog = true }
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (transactionsList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions logged in current ledger cycle.", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            items(transactionsList) { tx ->
                val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                val timeFormatted = formatter.format(Date(tx.timestamp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCarbon.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (tx.type == "BUY") NeonGreen.copy(alpha = 0.15f) else NeonPink.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (tx.type == "BUY") Icons.Default.ArrowOutward else Icons.Default.CallReceived,
                                contentDescription = tx.type,
                                tint = if (tx.type == "BUY") NeonGreen else NeonPink,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${tx.type} ${tx.symbol} [${tx.orderType}]",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Gas fee: $${String.format("%.4f", tx.feeUsd)} • $timeFormatted",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${if (tx.type == "BUY") "-" else "+"}${tx.amount} ${tx.symbol}",
                                color = if (tx.type == "BUY") NeonPink else NeonGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "$symbolSign${String.format("%,.2f", tx.totalUsdValue * factor)}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- PANEL 3: GLOBAL UNIVERSAL TRADE PANEL ---
@Composable
fun TradePanel(
    viewModel: MainViewModel,
    profile: UserProfile?,
    coins: List<CoinMarketData>,
    selectedCoin: String
) {
    if (profile == null) return

    val activeCoin = coins.find { it.symbol == selectedCoin } ?: return

    val tType by viewModel.tradeType.collectAsState()
    val oType by viewModel.orderType.collectAsState()
    val amountStr by viewModel.tradeAmount.collectAsState()
    val limitPriceStr by viewModel.targetLimitPrice.collectAsState()
    val slippageTolerance by viewModel.slippageTolerance.collectAsState()
    val transactionsList by viewModel.transactions.collectAsState()

    var statusMessage by remember { mutableStateOf("") }
    var actionSuccessStatus by remember { mutableStateOf(true) }
    var processingTradeInQueue by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var timelineSelection by remember { mutableStateOf("5m") }

    var previousPrice by remember(selectedCoin) { mutableStateOf(activeCoin.currentPrice) }
    val priceFlashColor = remember { Animatable(NeonCyan) }

    LaunchedEffect(activeCoin.currentPrice, selectedCoin) {
        if (activeCoin.currentPrice > previousPrice) {
            priceFlashColor.animateTo(NeonGreen, animationSpec = tween(100))
            priceFlashColor.animateTo(NeonCyan, animationSpec = tween(600))
        } else if (activeCoin.currentPrice < previousPrice) {
            priceFlashColor.animateTo(NeonPink, animationSpec = tween(100))
            priceFlashColor.animateTo(NeonCyan, animationSpec = tween(600))
        }
        previousPrice = activeCoin.currentPrice
    }

    LaunchedEffect(selectedCoin) {
        viewModel.selectCoin(selectedCoin)
    }

    val amountDouble = amountStr.toDoubleOrNull() ?: 0.0
    val executionPrice = if (oType == "LIMIT") (limitPriceStr.toDoubleOrNull() ?: activeCoin.currentPrice) else activeCoin.currentPrice
    val totalEstimatedUSD = amountDouble * executionPrice
    val networkGasFeeUsd = when (activeCoin.network) {
        "Bitcoin" -> 8.50
        "Ethereum" -> 14.20
        "Solana" -> 0.002
        "BSC" -> 0.18
        else -> 0.50
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
    ) {
        // ==========================================
        // 📊 1. TOP SYSTEM STATUS BAR & BROWSER BAR (STICKY)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderBg)
                .border(1.dp, SlateSeparator, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Mobile Device OS Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time Display
                Text(
                    text = "8:38",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Network signals & Battery
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalCellular4Bar,
                        contentDescription = "Signal Strength",
                        tint = TextPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Wi-Fi Connected",
                        tint = TextPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    // Custom micro battery container
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(1.dp, TextPrimary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 2.dp, vertical = 0.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 12.dp, height = 6.dp)
                                .background(TextPrimary, RoundedCornerShape(1.dp))
                        )
                    }
                    Text(
                        text = "18%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }

            // Browser URL Capsule Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(CyberBlack.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .border(1.dp, SlateSeparator, RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure Connection",
                        tint = NeonGreen,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "aistudio.google.com/apps/",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Tab Counter visual representing open tabs (like 47)
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.5.dp, TextMuted, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "47",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMuted
                    )
                }
            }
        }

        // ==========================================
        // 🧠 2. APP HEADER (INSIDE TERMINAL - STICKY)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "EXECUTION TRADE TERMINAL",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "Simulated Global Order Book",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
        }

        // ==========================================
        // 📈 3. STICKY MARKET OVERVIEW PANEL
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .border(1.dp, SlateSeparator, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = HeaderBg)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Ticker information line & metadata tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${activeCoin.symbol} / USD",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SPOT",
                                color = NeonCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Interactive Timelines Selection
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("1m", "5m", "1h").forEach { tl ->
                            val isSelected = timelineSelection == tl
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) NeonCyan else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { timelineSelection = tl }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tl.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) CyberBlack else TextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Price display with beautiful indicator offset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "$${String.format("%,.4f", activeCoin.currentPrice)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = priceFlashColor.value,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    val priceChangePct = (activeCoin.currentPrice - activeCoin.candles.first().open) / activeCoin.candles.first().open * 100
                    val isUp = priceChangePct >= 0
                    Box(
                        modifier = Modifier
                            .background(
                                if (isUp) NeonGreen.copy(alpha = 0.15f) else NeonPink.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${if (isUp) "+" else ""}${String.format("%.2f", priceChangePct)}%",
                            color = if (isUp) NeonGreen else NeonPink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Real-time mini candlestick chart view
                CandlestickChart(
                    candles = activeCoin.candles,
                    emaVal = activeCoin.ema20,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .testTag("trading_panel_mini_chart")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Volume Bar Strip representation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val randomVal = remember(activeCoin.symbol) { java.util.Random(42) }
                    repeat(26) { index ->
                        val itemHeight = remember(activeCoin.symbol, index) { (3 + randomVal.nextInt(12)).dp }
                        val isPositive = index % 3 != 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 0.8.dp)
                                .height(itemHeight)
                                .background(
                                    if (isPositive) NeonGreen.copy(alpha = 0.6f) else NeonPink.copy(alpha = 0.6f),
                                    RoundedCornerShape(0.5.dp)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mini indicators strip: Trend, Momentum, and Volatility parameters
                Divider(color = SlateSeparator, thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Trend: ", color = TextMuted, fontSize = 10.sp)
                        val trendColor = if (activeCoin.sentiment.contains("BULL")) NeonGreen else NeonPink
                        Text(
                            text = if (activeCoin.sentiment.contains("BULL")) "BULLISH" else "BEARISH",
                            color = trendColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Momentum: ", color = TextMuted, fontSize = 10.sp)
                        val rsiVal = activeCoin.rsi
                        val momentumLabel = if (rsiVal > 55) "Strong" else "Stable"
                        Text(
                            text = momentumLabel,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Volatility: ", color = TextMuted, fontSize = 10.sp)
                        val volVal = activeCoin.volatility
                        val volStrength = if (volVal > 0.03) "High" else if (volVal > 0.015) "Medium" else "Low"
                        Text(
                            text = volStrength,
                            color = if (volStrength == "High") NeonPink else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // ==========================================
        // 🛠️ LOWER RUNTIME CONSOLIDATED TRANSACTION SPACE (SCROLLABLE)
        // ==========================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ==========================================
            // ⚡ 4. ACTION TOGGLES (BUY / SELL)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                //🟢 BUY / ACCUMULATE (bright neon green when active, secondary styled outline when inactive)
                Button(
                    onClick = { viewModel.tradeType.value = "BUY" },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("buy_mode_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tType == "BUY") NeonGreen else HeaderBg,
                        contentColor = if (tType == "BUY") CyberBlack else TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (tType == "BUY") null else BorderStroke(1.dp, SlateSeparator)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (tType == "BUY") CyberBlack else NeonGreen
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "BUY / ACCUMULATE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                //⚫ SELL / LIQUIDATE (dark gray with white text when active, outline when inactive)
                Button(
                    onClick = { viewModel.tradeType.value = "SELL" },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("sell_mode_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tType == "SELL") CyberCarbonLight else HeaderBg,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (tType == "SELL") BorderStroke(1.5.dp, TextPrimary) else BorderStroke(1.5.dp, SlateSeparator)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (tType == "SELL") TextPrimary else NeonPink
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "SELL / LIQUIDATE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // ==========================================
            // 🧾 5. ORDER CONFIGURATION PANEL
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateSeparator, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = HeaderBg)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header Selected Coin Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TICKER SELECTED: ${activeCoin.symbol}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "Spot dynamic: $${String.format("%,.4f", activeCoin.currentPrice)}",
                            fontSize = 12.sp,
                            color = priceFlashColor.value,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Order Type Scheme switcher toggles
                    Text(
                        text = "Order Execution Scheme",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("MARKET", "LIMIT").forEach { opt ->
                            val isSelected = oType == opt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else SlateSeparator,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.orderType.value = opt }
                                    .background(
                                        if (isSelected) NeonCyan.copy(alpha = 0.15f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    opt,
                                    color = if (isSelected) NeonCyan else TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Limit Spot target input if limit order scheme active
                    AnimatedVisibility(visible = oType == "LIMIT") {
                        Column(modifier = Modifier.padding(bottom = 10.dp)) {
                            Text("Target Limit Spot Price (USD)", color = TextMuted, fontSize = 10.sp)
                            OutlinedTextField(
                                value = limitPriceStr,
                                onValueChange = { viewModel.targetLimitPrice.value = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("limit_price_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = SlateSeparator,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }
                    }

                    // Trading Amount entry text input field
                    Text("Amount to Trade ($selectedCoin)", color = TextMuted, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(3.dp))
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { viewModel.tradeAmount.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("amount_input"),
                        placeholder = { Text("0.000", color = TextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SlateSeparator,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Slippage configuration selectors (Active on MARKET schemes)
                    AnimatedVisibility(visible = oType == "MARKET") {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Slippage Tolerance", color = TextMuted, fontSize = 10.sp)
                                Text("$slippageTolerance%", color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("0.1", "0.5", "1.0", "2.0").forEach { tol ->
                                    val isSelected = slippageTolerance == tol
                                    Box(
                                        modifier = Modifier
                                            .border(
                                                1.dp,
                                                if (isSelected) NeonCyan else SlateSeparator,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { viewModel.slippageTolerance.value = tol }
                                            .background(
                                                if (isSelected) NeonCyan.copy(alpha = 0.12f) else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            "$tol%",
                                            color = if (isSelected) NeonCyan else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // Gas Fees, Slippage Estimates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Network Gas Fee", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = "$${String.format("%.2f", networkGasFeeUsd)} (${activeCoin.network})",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Slippage Estimates", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = if (oType == "LIMIT") "0.00% (Guaranteed)" else "${String.format("%.2f", activeCoin.volatility * 100)}%",
                            color = if (oType == "LIMIT") NeonGreen else NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Divider(color = SlateSeparator, modifier = Modifier.padding(vertical = 10.dp))

                    // Bold total estimative checkout price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL ESTIMATED VALUE", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(
                            text = "$${String.format("%,.4f", totalEstimatedUSD)} USD",
                            color = NeonGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // ==========================================
            // 🚨 6. EXECUTION SUBMIT ACTION ACCESS BAR
            // ==========================================
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    processingTradeInQueue = true
                    statusMessage = ""
                    scope.launch {
                        viewModel.processTrade { msg, isSuccess ->
                            statusMessage = msg
                            actionSuccessStatus = isSuccess
                            processingTradeInQueue = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_trade_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tType == "BUY") NeonGreen else NeonPink
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = !processingTradeInQueue && amountDouble > 0
            ) {
                if (processingTradeInQueue) {
                    CircularProgressIndicator(color = CyberBlack, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = "TRANSACT SECURE $tType ORDER",
                        color = CyberBlack,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }

            // Diagnostic feedback logs on successful state
            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (actionSuccessStatus) NeonGreen.copy(alpha = 0.5f) else NeonPink.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (actionSuccessStatus) NeonGreen.copy(alpha = 0.08f) else NeonPink.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (actionSuccessStatus) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "info",
                            tint = if (actionSuccessStatus) NeonGreen else NeonPink,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (actionSuccessStatus) "ORDER COMPLETED" else "LEDGER WARNING",
                                color = if (actionSuccessStatus) NeonGreen else NeonPink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = statusMessage,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 📜 COLLAPSIBLE RECENT ORDERS HISTORY
            // ==========================================
            var recentOrdersExpanded by remember { mutableStateOf(false) }
            val sortedTxList = remember(transactionsList) {
                transactionsList.sortedByDescending { it.timestamp }.take(5)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateSeparator, RoundedCornerShape(12.dp))
                    .testTag("recent_orders_history_card"),
                colors = CardDefaults.cardColors(containerColor = HeaderBg)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { recentOrdersExpanded = !recentOrdersExpanded }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Order History",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recent Orders History",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${sortedTxList.size}",
                                    color = NeonCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Icon(
                            imageVector = if (recentOrdersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (recentOrdersExpanded) "Collapse" else "Expand",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = recentOrdersExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            if (sortedTxList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No recorded transactions in this ledger state.",
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                sortedTxList.forEach { tx ->
                                    val txFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                                    val txTimeFormatted = txFormatter.format(Date(tx.timestamp))
                                    val isBuy = tx.type == "BUY" || tx.type == "CONVERT_IN"

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .border(1.dp, SlateSeparator, RoundedCornerShape(8.dp))
                                            .background(CyberBlack.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (isBuy) NeonGreen.copy(alpha = 0.15f) else NeonPink.copy(alpha = 0.15f),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 1.5.dp)
                                                ) {
                                                    Text(
                                                        text = tx.type,
                                                        color = if (isBuy) NeonGreen else NeonPink,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = tx.symbol,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = txTimeFormatted,
                                                fontSize = 9.sp,
                                                color = TextMuted
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "$${String.format("%,.4f", tx.price)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isBuy) NeonGreen else NeonPink,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = "Fee: $${String.format("%.2f", tx.feeUsd)}",
                                                fontSize = 8.sp,
                                                color = TextMutedDark
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Warnings, policy Sandbox Notices
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HeaderBg.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sandbox Advisory",
                        tint = NeonOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "All execution assets processed through our universal simulated crypto terminal reside in standard sandbox vaults. No real funds are locked or placed.",
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

// --- PANEL 4: AI COACH / CHAT COMPONENT SCREEN ---
@Composable
fun CoachPanel(
    viewModel: MainViewModel,
    selectedCoinSymbol: String,
    profile: UserProfile?
) {
    val coachMessage by viewModel.coachResponse.collectAsState()
    val isCoachProg by viewModel.isCoachLoading.collectAsState()
    val tipOfDay by viewModel.dailyTip.collectAsState()

    var coachCustomText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, "academy", tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NEXA AI MENTOR IN_DESK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.5.sp
                )
            }
            Text(
                text = "Quantitative Risk Advisors",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Static dynamic ticker advice banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCarbonLight)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lightbulb, "advice", tint = NeonCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = tipOfDay,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }

        // Coach Command Suite Card Buttons
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateSeparator, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "AI EXECUTIVES SELECTION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.askCoachForSelectedCoinExplanation() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("coach_explain_coin_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Analyze $selectedCoinSymbol Chart",
                                color = CyberBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = { viewModel.requestPortfolioPerformanceAnalysis() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("coach_portfolio_audit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Audit Portfolio Risk",
                                color = CyberBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Prompt input question desk
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateSeparator, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "CONSULT QUANT STRATEGY DIRECT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = coachCustomText,
                        onValueChange = { coachCustomText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("coach_prompt_input"),
                        placeholder = { Text("Ask about leverage margins, stop-loss strategy, Fibonacci thresholds...", color = TextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SlateSeparator,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            viewModel.submitCoachCustomPrompt(coachCustomText)
                            coachCustomText = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("coach_submit_custom_prompt_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateSeparator),
                        shape = RoundedCornerShape(10.dp),
                        enabled = coachCustomText.isNotBlank()
                    ) {
                        Text("SUBMIT PROMPT FOR REPORT", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Active Advisor analysis report display box
        item {
            Text(
                "ADVISORY LOG ANALYSIS REPORT",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = TextMuted,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateSeparator, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberBlack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isCoachProg) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = NeonCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Querying Nexa intelligence arrays... please wait.", color = TextMuted, fontSize = 12.sp)
                        }
                    } else if (coachMessage.isNotEmpty()) {
                        Text(
                            text = coachMessage,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("coach_response_display")
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.SmartToy, "robot icon", tint = SlateSeparator, modifier = Modifier.size(42.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Standing by. Click one of the analysis choices above or submit a direct query to produce real-time risk charts audit reports.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- PANEL 5: NEXA ACADEMY LEARNING PORTAL & INTERACTIVE LESSONS ---
@Composable
fun AcademyPanel(
    viewModel: MainViewModel,
    profile: UserProfile?,
    progressList: List<com.example.data.database.LessonProgress>,
    lessonsList: List<Lesson>
) {
    if (profile == null) return

    val activeLesson by viewModel.selectedLesson.collectAsState()
    val optSelected by viewModel.quizSelectedOption.collectAsState()
    val isAnswered by viewModel.quizAnswered.collectAsState()

    // 1. Calculations for Gamified Stats
    val completedCount = lessonsList.count { lesson ->
        progressList.any { it.lessonId == lesson.id && it.isCompleted }
    }
    val totalLessons = lessonsList.size
    
    val gradedQuizzes = progressList.filter { it.progressPercent >= 60 || it.isCompleted }
    val correctQuizzes = progressList.filter { it.isCompleted && it.maxQuizScore == 100 }
    val accuracyPercent = if (gradedQuizzes.isNotEmpty()) {
        (correctQuizzes.size * 100) / gradedQuizzes.size
    } else {
        100
    }
    
    val studyStreak = remember { 3 } // Realistic simulated study streak of 3 active days

    // Slide-out Lesson interface if one is chosen
    if (activeLesson != null) {
        val curLesson = activeLesson!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberBlack)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header return button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.dismissLesson() },
                    modifier = Modifier.background(SlateSeparator, RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.ArrowBack, "back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("BACK TO CURRICULUMS", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Lesson Banner Content card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateSeparator, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PATH: ${curLesson.category.uppercase()} TRADER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 1.2.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = curLesson.quizType.uppercase(),
                                fontSize = 9.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        curLesson.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = curLesson.content,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Interactive Quiz Section
            Text(
                "INTERACTIVE ASSESSMENT QUIZ",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isAnswered && optSelected == curLesson.quizCorrectIndex) NeonGreen else if (isAnswered) NeonPink else SlateSeparator,
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = curLesson.quizQuestion,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    curLesson.quizOptions.forEachIndexed { index, option ->
                        val isCorrectIndex = index == curLesson.quizCorrectIndex
                        val isChosen = optSelected == index

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(
                                    1.dp,
                                    if (isAnswered && isCorrectIndex) NeonGreen
                                    else if (isAnswered && isChosen) NeonPink
                                    else if (isChosen) NeonCyan
                                    else SlateSeparator,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(enabled = !isAnswered) { viewModel.answerQuiz(index) }
                                .background(
                                    if (isAnswered && isCorrectIndex) NeonGreen.copy(alpha = 0.15f)
                                    else if (isAnswered && isChosen) NeonPink.copy(alpha = 0.15f)
                                    else if (isChosen) NeonCyan.copy(alpha = 0.15f)
                                    else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(1.dp, TextMuted, RoundedCornerShape(50))
                                        .background(
                                            if (isAnswered && isCorrectIndex) NeonGreen
                                            else if (isAnswered && isChosen) NeonPink
                                            else if (isChosen) NeonCyan
                                            else Color.Transparent,
                                            RoundedCornerShape(50)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isAnswered && isCorrectIndex) {
                                        Icon(Icons.Default.Check, null, tint = CyberBlack, modifier = Modifier.size(12.dp))
                                    } else if (isAnswered && isChosen) {
                                        Icon(Icons.Default.Close, null, tint = CyberBlack, modifier = Modifier.size(12.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(option, color = TextPrimary, fontSize = 13.sp)
                            }
                        }
                    }

                    // Explanation diagnostics section once answered
                    AnimatedVisibility(visible = isAnswered) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SlateSeparator.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = if (optSelected == curLesson.quizCorrectIndex) "✓ EXCELLENT ASSIGNMENT COMPLETED!" else "❌ DEVIATION DECREASE RISK WARNING!",
                                    color = if (optSelected == curLesson.quizCorrectIndex) NeonGreen else NeonPink,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = curLesson.quizExplanation,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // AI ASSISTANT CONNECTIONS
                            if (optSelected == curLesson.quizCorrectIndex) {
                                Button(
                                    onClick = { viewModel.askCoachForAcademySuccess(curLesson.title, curLesson.category) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_success_consult")
                                ) {
                                    Icon(Icons.Default.SmartToy, null, tint = CyberBlack, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upgrade concepts with AI Mentor", color = CyberBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val wrongText = curLesson.quizOptions.getOrNull(optSelected ?: 0) ?: "Not selected"
                                        val correctText = curLesson.quizOptions.getOrNull(curLesson.quizCorrectIndex) ?: ""
                                        viewModel.askCoachForAcademyHelp(
                                            curLesson.title,
                                            curLesson.quizQuestion,
                                            wrongText,
                                            correctText,
                                            curLesson.id
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_failure_explain")
                                ) {
                                    Icon(Icons.Default.SmartToy, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyze wrong choice with AI Mentor", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        return
    }

    // Default Academy panel listing out items
    var currentPathSelected by remember { mutableStateOf("Beginner") }

    // Pick first incomplete lesson to recommend
    val recommendedLesson = lessonsList.firstOrNull { lesson ->
        !progressList.any { it.lessonId == lesson.id && it.isCompleted }
    } ?: lessonsList.firstOrNull()

    // Daily challenge states
    val challengeCompleted by viewModel.dailyChallengeCompleted.collectAsState()
    val challengeScoreGained by viewModel.dailyChallengeScoreGained.collectAsState()
    var selectedChallengeOption by remember { mutableStateOf<Int?>(null) }
    var showChallengeMistakeFeedback by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Gamified Profile level status card representation
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TRADER EXPERIENCE & LEVEL STATE", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = profile.rank.uppercase(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonGreen
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .border(2.dp, NeonGreen, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LV", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Text("${profile.level}", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress slider line XP to next Level (calculated as level * 500 XP steps)
                    val stepMultiplier = 500
                    val basePriorLevelXp = (profile.level - 1) * stepMultiplier
                    val xpInCurrentLevel = profile.xp - basePriorLevelXp
                    val ratio = (xpInCurrentLevel.toFloat() / stepMultiplier).coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${xpInCurrentLevel} / ${stepMultiplier} XP", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Next rank in ${stepMultiplier - xpInCurrentLevel} XP", fontSize = 11.sp, color = TextMuted)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(SlateSeparator)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ratio)
                                .background(NeonGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Extra detailed real-time statistics HUD (Streak, Accuracy, Completed Count)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("DAILY STREAK", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFFFA500), modifier = Modifier.size(14.dp))
                                Text(" ${studyStreak} Days", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(SlateSeparator))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("ACCURACY RATE", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text("${accuracyPercent}%", fontSize = 13.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(SlateSeparator))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("COMPLETED", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text("${completedCount} / ${totalLessons}", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- HERO: RECOMMENDED LESSON CARD ---
        if (recommendedLesson != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { viewModel.selectLesson(recommendedLesson) },
                    colors = CardDefaults.cardColors(containerColor = CyberCarbon)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.School, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RECOMMENDED NEXT MODULE", color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(recommendedLesson.title, fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                        Text(recommendedLesson.description, fontSize = 12.sp, color = TextMuted, maxLines = 2)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Earn +${recommendedLesson.xpReward} XP", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("LAUNCH LESSON", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowForward, null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // --- INTERACTIVE DAILY CHALLENGE CARD ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateSeparator, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, null, tint = Color(0xFFFF4500), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DAILY ALGORITHMIC CHALLENGE", color = Color(0xFFFF4500), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFF4500).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("100 XP REWARD", fontSize = 9.sp, color = Color(0xFFFF4500), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Identify Trend: Given a BTC liquidity profile where buy bids are dense at $64,100 (520 BTC depth) and sell asks are highly static and shallow up to $64,500 (80 BTC depth). Momentum indicators consolidate. What near term action is expected?",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (challengeCompleted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NeonGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, null, tint = NeonGreen, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("DAILY CHALLENGE COMPLETED", color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("You recognized the buy-in depth correctly and claimed +100 XP!", color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        // Options selection
                        val options = listOf(
                            "Bullish breakout as buyers absorb shallow ask liquidity with low resistance.",
                            "Immediate bearish markdown because bid density acts as a magnet for institutional stop limits."
                        )

                        options.forEachIndexed { idx, option ->
                            val isSelected = selectedChallengeOption == idx
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else SlateSeparator,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(
                                        if (isSelected) NeonCyan.copy(alpha = 0.12f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedChallengeOption = idx }
                                    .padding(10.dp)
                            ) {
                                Text(option, fontSize = 12.sp, color = TextPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (selectedChallengeOption == 0) {
                                    viewModel.recordDailyChallengeReward(100)
                                    showChallengeMistakeFeedback = false
                                } else {
                                    showChallengeMistakeFeedback = true
                                }
                            },
                            enabled = selectedChallengeOption != null,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("SUBMIT ASSESSMENT SYSTEM", color = CyberBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        if (showChallengeMistakeFeedback) {
                            Text(
                                text = "❌ Incorrect analysis. Hint: Thin ask volume represents weak resistance ceilings. Buyers take over easily.",
                                color = NeonPink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- PATHWAY ROUTING SECTORS ---
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(
                    "LEARNING PATHWAYS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val paths = listOf("Beginner", "Intermediate", "Advanced")
                    paths.forEach { pathName ->
                        val isSelected = currentPathSelected == pathName
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    1.dp,
                                    if (isSelected) NeonCyan else SlateSeparator,
                                    RoundedCornerShape(8.dp)
                                )
                                .background(
                                    if (isSelected) NeonCyan.copy(alpha = 0.15f) else CyberCarbon,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { currentPathSelected = pathName }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pathName.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) NeonCyan else TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Filter lessons list based on active path selected
        val filteredLessons = lessonsList.filter { it.category == currentPathSelected }

        // List elements lessons
        items(filteredLessons) { lesson ->
            val stat = progressList.find { it.lessonId == lesson.id }
            val completed = stat?.isCompleted ?: false

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .border(
                        1.dp,
                        if (completed) NeonGreen.copy(alpha = 0.5f) else SlateSeparator,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { viewModel.selectLesson(lesson) }
                    .testTag("lesson_${lesson.id}"),
                colors = CardDefaults.cardColors(containerColor = CyberCarbon)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (completed) NeonGreen.copy(alpha = 0.15f) else SlateSeparator),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (completed) Icons.Default.Verified else Icons.Default.MenuBook,
                            contentDescription = "status",
                            tint = if (completed) NeonGreen else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lesson.title,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Box(
                                modifier = Modifier
                                    .background(SlateSeparator, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(lesson.quizType, fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = lesson.description,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (completed) NeonGreen.copy(alpha = 0.12f) else SlateSeparator)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (completed) "+${lesson.xpReward} XP" else "${lesson.xpReward} XP",
                            color = if (completed) NeonGreen else TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- ACADEMY ACHIEVEMENTS EARNED PANEL ---
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp)) {
                Text(
                    "FINTECH TRADER ACHIEVEMENTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Render 4 grid badges
                val achievements = listOf(
                    Triple("Scholar Cadet", "Complete at least 1 lesson inside the Nexa Academy.", completedCount >= 1),
                    Triple("Technical Guru", "Unlock intermediate path status by scoring lessons.", progressList.count { it.isCompleted } >= 4),
                    Triple("Risk Architect", "Score 100% on the Position Sizing advanced formula quiz.", progressList.any { it.lessonId == "position_sizing" && it.isCompleted }),
                    Triple("Apex Mastermind", "Ascend through experience paths to reach Level 2 or higher.", profile.level >= 2)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    achievements.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (title, desc, isUnlocked) ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            1.dp,
                                            if (isUnlocked) NeonCyan.copy(alpha = 0.6f) else SlateSeparator,
                                            RoundedCornerShape(12.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isUnlocked) CyberCarbon else CyberCarbon.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    if (isUnlocked) NeonCyan.copy(alpha = 0.15f) else SlateSeparator.copy(alpha = 0.1f),
                                                    RoundedCornerShape(50)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                                                contentDescription = "badge",
                                                tint = if (isUnlocked) NeonCyan else TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = title,
                                            color = if (isUnlocked) TextPrimary else TextMuted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            color = TextMuted,
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 3
                                        )
                                    }
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

