package com.example.data.simulator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.sin
import kotlin.random.Random

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class CoinMarketData(
    val symbol: String,
    val name: String,
    val currentPrice: Double,
    val change24hPercent: Double,
    val high24h: Double,
    val low24h: Double,
    val volume24h: Double,
    val volatility: Double, // Random walks scale
    val network: String,
    val candles: List<Candle>,
    val sentiment: String, // "BULLISH", "BEARISH", "NEUTRAL"
    val rsi: Double,
    val macd: Double,
    val ema20: Double,
    val category: String = "Blue Chip",
    val isVerified: Boolean = true,
    val viewsCount: Int = 100
)

object MarketDataSimulator {

    private val _marketState = MutableStateFlow<Map<String, CoinMarketData>>(emptyMap())
    val marketState: StateFlow<Map<String, CoinMarketData>> = _marketState.asStateFlow()

    init {
        preseedMarket()
    }

    private fun preseedMarket() {
        val initialData = mutableMapOf<String, CoinMarketData>()
        
        // Form: symbol, name, basePrice, network, category, isVerified, viewsCount
        val defaultCoins = listOf(
            // Major Blue Chips
            Triple("BTC", "Bitcoin", Triple(67420.0, "Bitcoin", Triple("Blue Chip", true, 9245))),
            Triple("ETH", "Ethereum", Triple(3180.0, "Ethereum", Triple("Blue Chip", true, 7120))),
            Triple("SOL", "Solana", Triple(154.50, "Solana", Triple("Blue Chip", true, 8910))),
            Triple("BNB", "BNB Chain", Triple(582.30, "BSC", Triple("Blue Chip", true, 3120))),
            Triple("XRP", "Ripple", Triple(0.52, "Ripple", Triple("Blue Chip", true, 4102))),
            Triple("ADA", "Cardano", Triple(0.45, "Cardano", Triple("Blue Chip", true, 2190))),
            Triple("DOT", "Polkadot", Triple(6.20, "Polkadot", Triple("Blue Chip", true, 1104))),
            Triple("NEAR", "Near Protocol", Triple(5.80, "Near", Triple("Blue Chip", true, 3420))),
            Triple("LINK", "Chainlink", Triple(15.20, "Ethereum", Triple("Blue Chip", true, 2210))),
            Triple("TON", "Toncoin", Triple(7.10, "TON Network", Triple("Blue Chip", true, 5830))),

            // Meme Coin Zone
            Triple("DOGE", "Dogecoin", Triple(0.142, "Dogecoin", Triple("Meme", false, 6830))),
            Triple("SHIB", "Shiba Inu", Triple(0.000021, "Ethereum", Triple("Meme", false, 5190))),
            Triple("PEPE", "Pepe Coin", Triple(0.000012, "Ethereum", Triple("Meme", false, 8240))),
            Triple("FLOKI", "Floki", Triple(0.00022, "BSC", Triple("Meme", false, 4110))),
            Triple("WIF", "dogwifhat", Triple(2.45, "Solana", Triple("Meme", false, 6720))),
            Triple("BONK", "Bonk", Triple(0.000028, "Solana", Triple("Meme", false, 3491))),
            Triple("BOME", "Book of Meme", Triple(0.011, "Solana", Triple("Meme", false, 2410))),
            Triple("SLERF", "Slerf", Triple(0.35, "Solana", Triple("Meme", false, 1890))),

            // Newly Listed / Trending
            Triple("NOT", "Notcoin", Triple(0.016, "TON Network", Triple("New Listing", false, 7120))),
            Triple("BLAST", "Blast", Triple(0.022, "Blast Network", Triple("New Listing", false, 3810))),
            Triple("ZK", "ZKsync", Triple(0.18, "ZKsync Era", Triple("New Listing", false, 2901))),
            Triple("FRIEND", "friend.tech", Triple(0.85, "Base Network", Triple("New Listing", false, 1205))),
            Triple("BRETT", "Brett", Triple(0.12, "Base Network", Triple("New Listing", false, 5500))),
            Triple("DEGEN", "Degen Token", Triple(0.009, "Base Network", Triple("New Listing", false, 2800))),
            Triple("MEW", "cat in a dogs world", Triple(0.0042, "Solana", Triple("New Listing", false, 4830))),

            // Stablecoins
            Triple("USDT", "Tether", Triple(1.00, "TRON", Triple("Stablecoin", true, 3105))),
            Triple("USDC", "USD Coin", Triple(1.00, "Ethereum", Triple("Stablecoin", true, 2690))),
            Triple("DAI", "Dai Stablecoin", Triple(1.00, "Ethereum", Triple("Stablecoin", true, 1240)))
        )

        for ((symbol, name, dataPack) in defaultCoins) {
            val (basePrice, network, specPack) = dataPack
            val (category, isVerified, viewsCount) = specPack
            
            val volatility = when (category) {
                "Blue Chip" -> if (symbol == "BTC") 0.0015 else if (symbol == "ETH") 0.0025 else 0.0045
                "Meme" -> if (symbol == "PEPE") 0.018 else 0.012
                "New Listing" -> 0.014
                "Stablecoin" -> 0.0001
                else -> 0.005
            }

            // Pre-generate candles
            val candles = mutableListOf<Candle>()
            var lastPrice = basePrice * 0.94
            val now = System.currentTimeMillis()
            for (i in 20 downTo 1) {
                val time = now - (i * 15 * 60 * 1000) // 15-min intervals
                val change = (Random.nextDouble() - 0.48) * volatility * 10
                val open = lastPrice
                val close = open * (1 + change)
                val high = maxOf(open, close) * (1 + Random.nextDouble() * volatility * 2)
                val low = minOf(open, close) * (1 - Random.nextDouble() * volatility * 2)
                val volume = basePrice * Random.nextInt(20, 150)
                candles.add(Candle(time, open, high, low, close, volume))
                lastPrice = close
            }

            // Calculate indicators
            val rsiVal = 45.0 + Random.nextDouble() * 25.0
            val macdVal = (Random.nextDouble() - 0.5) * (basePrice * 0.002)
            val emaVal = lastPrice * (1 + (Random.nextDouble() - 0.5) * 0.005)
            val sentiment = when {
                rsiVal > 62 -> "BULLISH"
                rsiVal < 38 -> "BEARISH"
                else -> "NEUTRAL"
            }

            initialData[symbol] = CoinMarketData(
                symbol = symbol,
                name = name,
                currentPrice = lastPrice,
                change24hPercent = (lastPrice - basePrice * 0.95) / (basePrice * 0.95) * 100,
                high24h = candles.maxOf { it.high },
                low24h = candles.minOf { it.low },
                volume24h = candles.sumOf { it.volume } * 10,
                volatility = volatility,
                network = network,
                candles = candles,
                sentiment = sentiment,
                rsi = rsiVal,
                macd = macdVal,
                ema20 = emaVal,
                category = category,
                isVerified = isVerified,
                viewsCount = viewsCount
            )
        }
        _marketState.value = initialData
    }

    // Call this inside a coroutine tick loop (e.g. every 2-3 seconds) to simulate live trading Desk ticks
    fun tickPrices() {
        val current = _marketState.value.toMutableMap()
        for ((symbol, data) in current) {
            val changeFactor = (Random.nextDouble() - 0.495) // Very slight positive bias
            val percentageMove = changeFactor * data.volatility
            val newPrice = data.currentPrice * (1 + percentageMove)

            // Update current candle high/low
            val updatedCandles = data.candles.toMutableList()
            if (updatedCandles.isNotEmpty()) {
                val lastCandle = updatedCandles.removeAt(updatedCandles.size - 1)
                val updatedLast = lastCandle.copy(
                    high = maxOf(lastCandle.high, newPrice),
                    low = minOf(lastCandle.low, newPrice),
                    close = newPrice,
                    volume = lastCandle.volume + (data.currentPrice * Random.nextInt(1, 5))
                )
                updatedCandles.add(updatedLast)
            }

            val rsiVal = (data.rsi + changeFactor * 2.0).coerceIn(10.0, 90.0)
            val macdVal = data.macd * 0.95 + (Random.nextDouble() - 0.5) * (newPrice * 0.0002)
            val emaVal = data.ema20 * 0.98 + newPrice * 0.02

            val sentiment = when {
                rsiVal > 60 -> "STRONG BULL"
                rsiVal > 52 -> "MODERATE BULL"
                rsiVal < 40 -> "BEARISH"
                else -> "CONSOLIDATING"
            }

            val high24 = maxOf(data.high24h, newPrice)
            val low24 = minOf(data.low24h, newPrice)

            current[symbol] = data.copy(
                currentPrice = newPrice,
                change24hPercent = data.change24hPercent + (percentageMove * 100),
                high24h = high24,
                low24h = low24,
                candles = updatedCandles,
                rsi = rsiVal,
                macd = macdVal,
                ema20 = emaVal,
                sentiment = sentiment
            )
        }
        _marketState.value = current
    }
}
