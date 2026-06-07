package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.LessonProgress
import com.example.data.database.TradeTransaction
import com.example.data.database.UserAsset
import com.example.data.database.UserProfile
import com.example.data.repository.DatabaseRepository
import com.example.data.simulator.CoinMarketData
import com.example.data.simulator.MarketDataSimulator
import com.example.data.api.GeminiCoachService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// Course Lesson Struct
data class Lesson(
    val id: String,
    val title: String,
    val description: String,
    val xpReward: Int,
    val content: String,
    val quizQuestion: String,
    val quizOptions: List<String>,
    val quizCorrectIndex: Int,
    val quizExplanation: String,
    val category: String, // "Beginner", "Intermediate", "Advanced"
    val quizType: String // "Multiple Choice", "True / False", "Match the Concept", "Chart Recognition", "Market Scenarios"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = DatabaseRepository(database.userDao())

    // --- State Management ---
    val assets: StateFlow<List<UserAsset>> = repository.assets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<TradeTransaction>> = repository.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profile: StateFlow<UserProfile?> = repository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lessonProgress: StateFlow<List<LessonProgress>> = repository.lessonProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Live Market Tickers ---
    val marketState: StateFlow<Map<String, CoinMarketData>> = MarketDataSimulator.marketState

    // --- Active Client Selection ---
    private val _selectedCoinSymbol = MutableStateFlow("BTC")
    val selectedCoinSymbol: StateFlow<String> = _selectedCoinSymbol.asStateFlow()

    // --- Tab Selection state ---
    val activeTab = MutableStateFlow(0)

    // --- AI Coach Chat States ---
    private val _coachResponse = MutableStateFlow<String>("")
    val coachResponse: StateFlow<String> = _coachResponse.asStateFlow()

    private val _isCoachLoading = MutableStateFlow(false)
    val isCoachLoading: StateFlow<Boolean> = _isCoachLoading.asStateFlow()

    private val _dailyTip = MutableStateFlow("")
    val dailyTip: StateFlow<String> = _dailyTip.asStateFlow()

    // --- Trade Execution Panel Configuration ---
    val tradeType = MutableStateFlow("BUY") // "BUY" vs "SELL"
    val orderType = MutableStateFlow("MARKET") // "MARKET" vs "LIMIT"
    val tradeAmount = MutableStateFlow("")
    val targetLimitPrice = MutableStateFlow("")
    val slippageTolerance = MutableStateFlow("0.5") // 0.1%, 0.5%, 1.0%

    // --- Current Learning Module ---
    private val _selectedLesson = MutableStateFlow<Lesson?>(null)
    val selectedLesson: StateFlow<Lesson?> = _selectedLesson.asStateFlow()

    private val _quizSelectedOption = MutableStateFlow<Int?>(null)
    val quizSelectedOption: StateFlow<Int?> = _quizSelectedOption.asStateFlow()

    private val _quizAnswered = MutableStateFlow(false)
    val quizAnswered: StateFlow<Boolean> = _quizAnswered.asStateFlow()

    // Daily Challenge completion track state
    val dailyChallengeCompleted = MutableStateFlow(false)
    val dailyChallengeScoreGained = MutableStateFlow(0)

    private val _currentLessonsList = MutableStateFlow<List<Lesson>>(emptyList())
    val currentLessonsList: StateFlow<List<Lesson>> = _currentLessonsList.asStateFlow()

    private var tickJob: Job? = null

    init {
        // Start simulated high-speed pricing loop Ticks
        startTickerLoop()
        loadStaticCourseLessons()
        generateDailyTip()
    }

    private fun startTickerLoop() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(3000) // update tick every 3 seconds for active simulation
                MarketDataSimulator.tickPrices()
                checkPriceAlerts()
            }
        }
    }

    private fun loadStaticCourseLessons() {
        _currentLessonsList.value = listOf(
            Lesson(
                id = "market_basics",
                title = "Market Basics & Order Matching",
                description = "Master supply/demand, order books, bid-ask spreads, and liquidity depth.",
                xpReward = 150,
                content = """
                    Welcome to the Nexa Academy.
                    At its core, any trading terminal operates on a centralized matching engine. Price moves solely due to the imbalance between buyers (bids) and sellers (asks).
                    
                    📊 **The Order Book Hierarchy**:
                    • **Bids**: Buy orders waiting inside the ledger. The highest bid is what sellers can instantly cash out to.
                    • **Asks**: Sell orders waiting for buy fills. The lowest ask represents the entry cost for immediate market buyers.
                    • **Spread**: The gap between the lowest ask and highest bid. Liquid majors (BTC/ETH) feature tight micro-spreads, while altcoins present wide, costly spreads.
                    
                    When market buy volume is exhausted, selling pressure easily drives price down through successive bids. Understanding order routing helps you bypass major execution slippage.
                """.trimIndent(),
                quizQuestion = "If a major trader dumps 500 BTC directly into a market with highly shallow bids in the order book, what is the expected result?",
                quizOptions = listOf(
                    "The price climbs immediately to meet asks.",
                    "An extreme downward price slippage occurs as orders absorb shallow bids.",
                    "The price remains strictly unchanged.",
                    "The blockchain ledger stalls to allow network adjustments."
                ),
                quizCorrectIndex = 1,
                quizExplanation = "Selling high volume into thin bids clears out those bidding tiers, cascading executed prices downward. Always check order depth!",
                category = "Beginner",
                quizType = "Multiple Choice"
            ),
            Lesson(
                id = "btc_fundamentals",
                title = "Bitcoin Fundamentals & Ledger Rules",
                description = "Learn Proof-of-Work, block halvings, and the decentralized 21M limit.",
                xpReward = 200,
                content = """
                    Bitcoin stands as the first successful decentralized digital currency. Its architecture is guarded by cryptographic proof and peer-to-peer consensus rather than central institutions.
                    
                    ⛓️ **Key Ledger Pillars**:
                    • **Proof-of-Work (PoW)**: Computational energy spent by miners to secure blocks and append transactions to the global immutable ledger.
                    • **The 21m Hard Cap**: Bitcoin has a strictly capped supply of 21,000,000 BTC. It can never be inflated or debased.
                    • **The Halving Events**: Approximately every 4 years (every 210,000 blocks), the Bitcoin mining block reward is halved. This programmatic reduction decreases coin issuance over time, establishing its absolute structural scarcity.
                """.trimIndent(),
                quizQuestion = "True or False: Bitcoin's consensus rules enforce a terminal supply cap of 21 million BTC, and mining rewards are cut by half roughly every 4 years.",
                quizOptions = listOf("True", "False"),
                quizCorrectIndex = 0,
                quizExplanation = "Correct! Bitcoin enforces an unchangeable monetary policy through hard-coded consensus, featuring a 21M limit and systematic reward halvings.",
                category = "Beginner",
                quizType = "True / False"
            ),
            Lesson(
                id = "order_types",
                title = "Order Types & Cost Enforcements",
                description = "Enforce entry costs using Limit orders vs Market orders.",
                xpReward = 150,
                content = """
                    Entering the market requires choosing the proper instruction. Bad order choice is a leading cause of retail loss.
                    
                    ⚡ **Order Classifications**:
                    • **Market Order**: Executes immediately at whatever price is available in the order book. Excellent for instant escape/access, but dangerous in high volatility because of slippage or spread gaps.
                    • **Limit Order**: Executes *only* at your specified target price (or better). Ensures transaction cost discipline, but may never fill if the market moves away.
                    
                    Enforcing limits is a crucial habit of elite financial operators.
                """.trimIndent(),
                quizQuestion = "You wish to purchase Solana only if the price drops to support at $142.00, guaranteeing your exact cost of entry. Which order type is required?",
                quizOptions = listOf(
                    "Market Order",
                    "Limit Order",
                    "Stop-Market Trigger",
                    "Trailing Premium Buy"
                ),
                quizCorrectIndex = 1,
                quizExplanation = "A Limit Order guarantees your execution price (or better), preventing you from buying at premium levels unless your exact criteria are satisfied.",
                category = "Beginner",
                quizType = "Match the Concept"
            ),
            Lesson(
                id = "risk_basics",
                title = "Risk Basics & Capital Rules",
                description = "Preserve capital by avoiding over-leverage and position size pitfalls.",
                xpReward = 200,
                content = """
                    An amateur strives to make millions; a professional strives to keep from losing their capital. Survival is the absolute target.
                    
                    🛡️ **The Axioms of Preservation**:
                    • **Avoid Over-exposure**: Restrict trade size so a single error never destroys your database or spot reserves.
                    • **Beware of Leverage**: Leverage is a double-edged sword. Trading at 20x leverage gives you a microscopic 5% volatility buffer. A small intraday swing triggers liquidation.
                    
                    Calculate your exits and write down your rules before clicking that buy button.
                """.trimIndent(),
                quizQuestion = "On a $10,000 starting account, an unhedged trader risks $1,500 on a single highly leveraged meme token trade. Under safe risk criteria, is this valid?",
                quizOptions = listOf(
                    "Yes, high risk is the only way to quickly grow the account.",
                    "No, they are risking 15% of their starting capital on a single speculative asset, which violates capital preservation rules.",
                    "Yes, provided Bitcoin is printing green moving averages."
                ),
                quizCorrectIndex = 1,
                quizExplanation = "Risking 15% of your total portfolio on one trade exposed to massive volatility leads to eventual ruin. Maximize survival by risking 1-2% only.",
                category = "Beginner",
                quizType = "Multiple Choice"
            ),
            Lesson(
                id = "support_resistance",
                title = "Support & Resistance Levels",
                description = "Spot order blocks: demand floors and supply ceilings.",
                xpReward = 250,
                content = """
                    Historical price charts trace human memory. Support and resistance are lines where buyers and sellers repeatedly battle.
                    
                    📉 **The Ground Rules**:
                    • **Support Floor**: A price zone where buying interest is thick enough to overcome selling pressure. Price bounces up.
                    • **Resistance Ceiling**: A zone where selling supply is heavy enough to absorb buy volumes, halting rallies.
                    • **Flip Condition (S/R Flip)**: Once broken on large volume, an active resistance ceiling transforms into a primary floor of support when re-tested from above.
                """.trimIndent(),
                quizQuestion = "Ethereum struggles to climb past $3,400. Once buyers push price past $3,400 on large volume, what is the expected structural behavior of that $3,400 zone?",
                quizOptions = listOf(
                    "It becomes completely obsolete.",
                    "It transforms into a primary support floor.",
                    "It automatically alerts network validators to pause bids.",
                    "It forces instant liquidation for spot holdings."
                ),
                quizCorrectIndex = 1,
                quizExplanation = "Broken resistance becomes support. During a pullback, buyers look to buy standard 're-tests' of this level with excellent risk-to-reward ratio.",
                category = "Intermediate",
                quizType = "Market Scenarios"
            ),
            Lesson(
                id = "rsi_indicator",
                title = "RSI Momentum Oscillations",
                description = "Track relative strength to identify overextended momentum zones.",
                xpReward = 250,
                content = """
                    The Relative Strength Index (RSI) measures the velocity and magnitude of price movements. It ranges on an oscillator from 0 to 100.
                    
                    📈 **The Diagnostic Scale**:
                    • **RSI Above 70 (Overbought)**: Warns that buying momentum is highly extended. Traders look to take profit or lock in hedge structures. Avoid buying at these high extremes!
                    • **RSI Below 30 (Oversold)**: Signals intense panic selling has overextended. Historical buyer support often sweeps in here, making it a great zone for accumulative entries.
                """.trimIndent(),
                quizQuestion = "Solana's RSI index on the 4-Hour chart is printing 81. How should a disciplined trader interpret this marker?",
                quizOptions = listOf(
                    "The asset is severely oversold; prepare to trade massive market buys.",
                    "The asset is extremely overbought, warning that purchasing volume is overextended and profit-taking or pullbacks are highly likely.",
                    "The network is undergoing low gas fee optimization.",
                    "The smart contract liquidity has permanently locked."
                ),
                quizCorrectIndex = 1,
                quizExplanation = "An RSI over 70/80 signals market hype is overheated. Avoid FOMO buys at these levels; wait for a healthy cooling off or pullback.",
                category = "Intermediate",
                quizType = "Chart Recognition"
            ),
            Lesson(
                id = "macd_indicator",
                title = "MACD Histogram & Trend Crosses",
                description = "Utilize moving average convergence/divergence indicators.",
                xpReward = 250,
                content = """
                    The MACD is a trend-following momentum oscillator that displays the relationships between exponential moving averages (EMA).
                    
                    📊 **The MACD Formula Stack**:
                    • **MACD Line**: The difference between the 12-period and 26-period EMAs.
                    • **Signal Line**: A 9-period EMA of the MACD Line.
                    • **Bullish Cross**: When the faster MACD line breaks *above* the slower Signal Line from below, indicating that bullish momentum is accelerating.
                """.trimIndent(),
                quizQuestion = "When the fast MACD line crosses above the slower Signal Line under the baseline, what momentum signal is generated?",
                quizOptions = listOf(
                    "A strong cell signal indicating immediate breakdown.",
                    "A bullish cross suggesting upward momentum is accelerating.",
                    "A signal that network validators have entered a hard-fork."
                ),
                quizCorrectIndex = 1,
                quizExplanation = "A bullish cross represents buying power shifting rapidly higher over near-term timeframes, a strong sign that an uptrend is beginning.",
                category = "Intermediate",
                quizType = "Multiple Choice"
            ),
            Lesson(
                id = "trend_analysis",
                title = "Trend Analysis & Market Baselines",
                description = "Ride trends: uptrends, downtrends, and consolidation cycles.",
                xpReward = 200,
                content = """
                    'The trend is your friend.' Elite traders never attempt to fight market gravity.
                    
                    📏 **Structural Trends**:
                    • **Uptrend**: Price establishes a pattern of Higher Highs (HH) and Higher Lows (HL). Focus on buying support re-tests.
                    • **Downtrend**: Price establishes a pattern of Lower Highs (LH) and Lower Lows (LL). Look for short entries or capital preservation in cash reserves.
                    • **Sideways Consolidation**: Volatility tightens inside a clear horizontal channel. It represents accumulation/distribution before a major breakout.
                """.trimIndent(),
                quizQuestion = "True or False: A verified uptrend is structurally defined on a chart by a clear sequence of Higher Highs and Higher Lows.",
                quizOptions = listOf("True", "False"),
                quizCorrectIndex = 0,
                quizExplanation = "Uptrends are defined by price making successive higher peaks and higher valley floors. Breaking this structure signals a trend shift.",
                category = "Intermediate",
                quizType = "True / False"
            ),
            Lesson(
                id = "volume_analysis",
                title = "Volume Diagnostics & Breakouts",
                description = "Confirm trend sustainability with spot trading volume indicators.",
                xpReward = 250,
                content = """
                    Volume represents the fuel of price movement. It measures the total amount of coins traded over a specific timeframe, acting as an indicator of institutional commitment.
                    
                    🔍 **The Volume Axioms**:
                    • **High-Volume Breakout**: Confirms smart money is backing the movement. The price breakout is highly sustainable.
                    • **Low-Volume Breakout**: Indicates that price moves are due to retail FOMO or thin books. Beware of 'Bull Traps' where price instantly reverses.
                """.trimIndent(),
                quizQuestion = "An asset surges 5% past major resistance, but the total trading volume is 60% below average levels. How should this be interpreted?",
                quizOptions = listOf(
                    "Buy instantly; price will continue surging on low friction.",
                    "Proceed with extreme focus and caution; low-volume breakouts suggest a lack of institutional interest, creating risk of a bull trap.",
                    "Liquidate all stablecoin balances immediately."
                ),
                quizCorrectIndex = 1,
                quizExplanation = "Low volume on a breakout means big players aren't buying the move. Such breakouts often run out of gas quickly, turning into traps.",
                category = "Intermediate",
                quizType = "Market Scenarios"
            ),
            Lesson(
                id = "market_structure",
                title = "Market Structure & Wyckoff Phases",
                description = "Unlock the 4 core phases of institutional market cycles.",
                xpReward = 300,
                content = """
                    Advanced trading is about reading Wyckoff market structure: the programmatic phases of the market cycle.
                    
                    🌀 **The Four Market Phases**:
                    • **1. Accumulation**: Smart money builds positions quietly in a tight sideways range. Retail interest is dead.
                    • **2. Markup**: Positive breakout on heavy volume, forming a steady upward trend.
                    • **3. Distribution**: Institutions sell possessions back to retail buyers experiencing FOMO near cycle tops.
                    • **4. Markdown**: Bearish breakdown. Fear drives panicked lower-low liquidations. Stay in cash!
                """.trimIndent(),
                quizQuestion = "Which major phase of market structure is characterized by quiet range-bound action, low public interest, and slow institutional buying?",
                quizOptions = listOf(
                    "Markup Phase",
                    "Markdown Phase",
                    "Accumulation Phase",
                    "Distribution Phase"
                ),
                quizCorrectIndex = 2,
                quizExplanation = "Accumulation occurs at market bottoms, when institutional capital quietly absorbs selling volume from exhausted retail hands.",
                category = "Advanced",
                quizType = "Multiple Choice"
            ),
            Lesson(
                id = "liquidity_flow",
                title = "Liquidity Pool & Stop Sweeps",
                description = "Identify institutional stop hunt zones and order book liquidity sweeps.",
                xpReward = 300,
                content = """
                    Institutions operate with massive position sizes. Entering or exiting requires high-density 'liquidity pools.'
                    
                    🎯 **The Hunt for Liquidity**:
                    Because retail traders place stop-losses at predictable levels (such as just below local support or above resistance), institutions use their capital to drive price right through those levels.
                    
                    This triggers a massive block of market sell/buy stops, providing the necessary liquidity to fill institutional orders in bulk. This is known as a **Stop Hunt** or **Liquidity Sweep**.
                """.trimIndent(),
                quizQuestion = "Ethereum drops momentarily below a major support low, triggering massive retail stop-loss fills, then immediately reverses into a huge rally. What happened?",
                quizOptions = listOf(
                    "A technical bug inside the matching engine.",
                    "An institutional liquidity run (stop sweep) to absorb orders at discount rates.",
                    "A standard network gas fee spike."
                ),
                quizCorrectIndex = 1,
                quizExplanation = "Smart money targets dense stop-loss clusters to buy massive volume with zero slippage before marking the price back up.",
                category = "Advanced",
                quizType = "Market Scenarios"
            ),
            Lesson(
                id = "position_sizing",
                title = "Advanced Position Sizing Formulas",
                description = "Construct trades mathematically to strictly limit capital exposure.",
                xpReward = 300,
                content = """
                    The ultimate secret of professional trading is risk math.
                    
                    📐 **The Golden Formula**:
                    `${"Position Size = (Account Capital * Risk%) / (Entry Price - Stop Loss Price)"}`
                    
                    By solving this equation, you guarantee that if the trade hits your stop-loss, your total loss is *exactly* your target risk percentage, regardless of where your stop is placed or what leverage you use.
                """.trimIndent(),
                quizQuestion = "You have a $10,000 account and want to risk exactly 1.5% ($150) on a trade. You buy BTC at $60,000 with a stop-loss at $58,000 (distance of $2,000). What is the correct size?",
                quizOptions = listOf(
                    "0.075 BTC ($4,500 position size)",
                    "0.025 BTC ($1,500 position size)",
                    "0.10 BTC ($6,000 position size)",
                    "1.50 BTC ($90,000 position size)"
                ),
                quizCorrectIndex = 0,
                quizExplanation = "Risk/Distance = $150 / $2,000 = 0.075 BTC. If Bitcoin drops to your stop at $58,000, your loss is exactly $150 (1.5%). Perfect risk control!",
                category = "Advanced",
                quizType = "Multiple Choice"
            ),
            Lesson(
                id = "portfolio_mgmt",
                title = "Portfolio Theory & Reserves Allocation",
                description = "Build a robust hedge using asset correlation and cash-DCA rules.",
                xpReward = 300,
                content = """
                    Never over-concentrate in high-beta altcoins. Modern portfolio management demands strict diversification and correlation checks.
                    
                    💼 **Reserves Construction**:
                    • **High-Beta Alts**: Captures high growth but holds extreme risk. Limit these to 10-15% of your total wallet.
                    • **Blue-Chips (BTC/ETH)**: Reliable, low-beta anchors providing high liquidity.
                    • **Dry-Powder Cash**: Maintain at least 25% of USD cash or stablecoin reserves. This acts as raw dry-powder capital to buy the blood when flash crashes arrive.
                """.trimIndent(),
                quizQuestion = "True or False: To minimize overall systematic risk, you should concentrate 100% of your wallet into a single high-volatility meme coin.",
                quizOptions = listOf("True", "False"),
                quizCorrectIndex = 1,
                quizExplanation = "Concentration increases risk of total ruin. Spread capital across blue-chips, low-beta assets, and maintain liquid cash reserves to DCA drops.",
                category = "Advanced",
                quizType = "True / False"
            ),
            Lesson(
                id = "trading_psychology",
                title = "Trading Psychology & Emotional Traps",
                description = "Master your mind: conquer FOMO, fear, and destructive revenge trading.",
                xpReward = 350,
                content = """
                    The ultimate threat to performance isn't the market, but the operator looking back in the mirror. Fear and greed are hardwired evolutionarily. You must override them with systems.
                    
                    🎭 **Destroyers of Capital**:
                    • **FOMO**: Greed-driven panic buying near market peaks out of anxiety of being left behind.
                    • **Panic Selling**: Fear-driven selling at major bottoms due to bearish media headlines.
                    • **Revenge Trading**: The impulse to immediately open unhedged, oversized trades after a loss to 'get the money back.' This ignores risk management and leads to liquidation.
                """.trimIndent(),
                quizQuestion = "Following a painful stop-loss hit, a trader overrides risk protocols and immediately opens an unhedged 10x long to win back losses. This is known as:",
                quizOptions = listOf(
                    "Calculated position hedging strategy.",
                    "Revenge trading driven by pride, which often results in rapid account destruction.",
                    "Diversified multi-protocol deployment."
                ),
                quizCorrectIndex = 1,
                quizExplanation = "Revenge trading is a highly destructive psychological state where emotional pain drives irrational risk-taking, ignoring trade systems.",
                category = "Advanced",
                quizType = "Multiple Choice"
            )
        )
    }

    private fun generateDailyTip() {
        val tips = listOf(
            "💡 Institutional Tip: Always prefer Limit Orders over Market Orders. Limit executions eliminate surprise execution slippage in thin order books.",
            "💡 Risk Alert: Trading SOL or MEME markets at above 3x leverage under simulated high-volatility is statistically proven to hit quick liquidations.",
            "💡 Mentor Tip: An RSI reading of 28 is a historically strong entry zone for top-tier assets (BTC/ETH) when matched with solid EMA support.",
            "💡 Portfolio Rule: Keep at least 30%-40% of your total wallet valuation in liquid cash or stable reserves to buy sudden deep market flash crashes."
        )
        _dailyTip.value = tips[Random.nextInt(tips.size)]
    }

    // --- Universal Coin Discovery Engine States ---
    val searchQuery = MutableStateFlow("")

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(setOf("BTC", "ETH", "SOL", "PEPE"))
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _customWatchlists = MutableStateFlow<Map<String, Set<String>>>(
        mapOf(
            "Meme Coins" to setOf("DOGE", "SHIB", "PEPE", "FLOKI", "WIF"),
            "Long-Term Holdings" to setOf("BTC", "ETH", "SOL", "LINK"),
            "High-Risk Assets" to setOf("PEPE", "BONK", "NOT", "BLAST", "BRETT"),
            "Stablecoins" to setOf("USDT", "USDC", "DAI")
        )
    )
    val customWatchlists: StateFlow<Map<String, Set<String>>> = _customWatchlists.asStateFlow()

    val selectedWatchlistTab = MutableStateFlow("All") // "All", "Favorites", "Meme Coins", "Long-Term", etc.

    // Active Alerts System
    data class PriceAlert(
        val id: String = java.util.UUID.randomUUID().toString(),
        val symbol: String,
        val targetPrice: Double,
        val type: String, // "ABOVE" | "BELOW"
        val isTriggered: Boolean = false,
        var message: String = ""
    )

    private val _priceAlerts = MutableStateFlow<List<PriceAlert>>(emptyList())
    val priceAlerts: StateFlow<List<PriceAlert>> = _priceAlerts.asStateFlow()

    private val _activeAlertBanner = MutableStateFlow<String?>(null)
    val activeAlertBanner: StateFlow<String?> = _activeAlertBanner.asStateFlow()

    // Coin AI Analysis
    private val _coinAnalysis = MutableStateFlow<String>("")
    val coinAnalysis: StateFlow<String> = _coinAnalysis.asStateFlow()

    private val _isAnalysisLoading = MutableStateFlow(false)
    val isAnalysisLoading: StateFlow<Boolean> = _isAnalysisLoading.asStateFlow()

    fun selectCoin(symbol: String) {
        _selectedCoinSymbol.value = symbol
        addToRecentSearches(symbol)
        // Populate reasonable target limit prices based on current simulator values
        val coinData = marketState.value[symbol]
        if (coinData != null) {
            targetLimitPrice.value = String.format("%.2f", coinData.currentPrice)
            tradeAmount.value = ""
        }
    }

    fun toggleFavorite(symbol: String) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(symbol)) {
            current.remove(symbol)
        } else {
            current.add(symbol)
        }
        _favorites.value = current
    }

    fun addToRecentSearches(symbol: String) {
        val current = _recentSearches.value.toMutableList()
        current.remove(symbol) // Remove if already exists so we can push to front
        current.add(0, symbol)
        if (current.size > 8) {
            current.removeAt(current.size - 1)
        }
        _recentSearches.value = current
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
    }

    fun createCustomWatchlist(name: String) {
        if (name.isBlank()) return
        val current = _customWatchlists.value.toMutableMap()
        if (!current.containsKey(name)) {
            current[name] = emptySet()
            _customWatchlists.value = current
        }
    }

    fun addCoinToWatchlist(watchlistName: String, symbol: String) {
        val current = _customWatchlists.value.toMutableMap()
        val listCoins = current[watchlistName]?.toMutableSet() ?: mutableSetOf()
        listCoins.add(symbol)
        current[watchlistName] = listCoins
        _customWatchlists.value = current
    }

    fun removeCoinFromWatchlist(watchlistName: String, symbol: String) {
        val current = _customWatchlists.value.toMutableMap()
        val listCoins = current[watchlistName]?.toMutableSet() ?: return
        listCoins.remove(symbol)
        current[watchlistName] = listCoins
        _customWatchlists.value = current
    }

    fun setPriceAlert(symbol: String, targetPrice: Double, type: String) {
        val alert = PriceAlert(symbol = symbol, targetPrice = targetPrice, type = type)
        _priceAlerts.value = _priceAlerts.value + alert
    }

    fun dismissAlertBanner() {
        _activeAlertBanner.value = null
    }

    fun removePriceAlert(alertId: String) {
        _priceAlerts.value = _priceAlerts.value.filterNot { it.id == alertId }
    }

    fun checkPriceAlerts() {
        val currentMarket = marketState.value
        val alerts = _priceAlerts.value
        if (alerts.isEmpty()) return

        var bannerText: String? = null
        val updated = alerts.map { alert ->
            if (alert.isTriggered) return@map alert
            val coin = currentMarket[alert.symbol] ?: return@map alert
            val triggered = if (alert.type == "ABOVE") {
                coin.currentPrice >= alert.targetPrice
            } else {
                coin.currentPrice <= alert.targetPrice
            }
            if (triggered) {
                bannerText = "🔔 TELEMETRY ALERT: ${alert.symbol} crossed ${alert.type} target of \$${String.format("%,.4f", alert.targetPrice)}! Spot price: \$${String.format("%,.4f", coin.currentPrice)}"
                alert.copy(isTriggered = true, message = "Triggered at \$${String.format("%,.4f", coin.currentPrice)}")
            } else {
                alert
            }
        }
        if (bannerText != null) {
            _activeAlertBanner.value = bannerText
        }
        _priceAlerts.value = updated
    }

    fun analyzeCoinWithAI(symbol: String) {
        val coin = marketState.value[symbol] ?: return
        _isAnalysisLoading.value = true
        _coinAnalysis.value = ""

        val systemPrompt = """
            You are Nexa AI, a world-class cryptographic asset security and technical structural analyst.
            Analyze the requested coin objectively, highlighting risk profiles, technical triggers, support/resistance, and scenario forecasts.
            Do not provide financial guarantees and emphasize risk/uncertainty.
        """.trimIndent()

        val userPrompt = """
            TECHNICAL TELEMETRY DEEP ANALYSIS REPORT:
            Asset: $symbol - ${coin.name}
            Current Spot Value: $${String.format("%,.4f", coin.currentPrice)}
            24h Price Action: ${String.format("%.2f", coin.change24hPercent)}%
            RSI Technical Line: ${String.format("%.2f", coin.rsi)}
            MACD Histogram Score: ${String.format("%.5f", coin.macd)}
            EMA20 Baseline support: $${String.format("%,.2f", coin.ema20)}
            Asset Risk Class: ${coin.category} (Verification status: ${if (coin.isVerified) "VERIFIED" else "UNVERIFIED HIGH RISK WARNING"})
            Intraday Volatility Factor: ${coin.volatility}
            
            Provide:
            1. Trend analysis & Wyckoff structural phase.
            2. Market structure support and resistance.
            3. Detailed risk assessment emphasizing volatile/unverified properties.
            4. Scenario modeling (Bullish, Bearish, Neutral cases).
            5. Uncertainty disclaimer notice.
        """.trimIndent()

        // High fidelity fallback UI simulation engine
        val fallbackText = """
            🤖 **Nexa AI Real-Time Analysis for $symbol**:
            
            • **Trend & Wyckoff Phase**: Momentum is printing steady **${coin.sentiment}** indicators. RSI of ${String.format("%.1f", coin.rsi)} shows consolidative momentum typical of a late-stage Accumulation or early Markup cycle.
            
            • **Technical Support & Resistance Levels**:
              - Primary Support Floor: $${String.format("%,.4f", coin.currentPrice * 0.96)} (confluent with the EMA20 line)
              - Primary Resistance Ceiling: $${String.format("%,.4f", coin.currentPrice * 1.04)}
            
            • **Dynamic Risk Assessment**: Volatility coefficient stands at ${String.format("%.4f", coin.volatility)}. ${if (!coin.isVerified) "⚠️ WARNING: Unverified or newly listed high-volatility token. Spot matching depths are thin, creating elevated slippage hazards (0.8%+). Limit entries are strictly recommended." else "Verified asset status. Standard portfolio exposure holds nominal systemic risk, though intraday swings suggest protective stops."}
            
            • **Scenario Framework (Next 72 Hours)**:
              - *🐂 Bullish Spike*: Breakthrough of $${String.format("%,.4f", coin.currentPrice * 1.05)} on heavy volume triggers a breakout rally targeting $${String.format("%,.4f", coin.currentPrice * 1.12)}.
              - *🐻 Bearish Flush*: Breach of the $${String.format("%,.4f", coin.currentPrice * 0.95)} support triggers automated stop sweeps down to $${String.format("%,.4f", coin.currentPrice * 0.88)}.
              - *横 Neutral Phase*: Sideways range-bound movement within the $${String.format("%,.4f", coin.currentPrice * 0.98)} to $${String.format("%,.4f", coin.currentPrice * 1.02)} channel.
              
            • **SAFETY DISCLAIMER**: Real-time virtual cryptographic telemetry holds no yield guarantees. Past performance is non-indicative of spot outcomes. Protect your cash!
        """.trimIndent()

        viewModelScope.launch {
            val response = GeminiCoachService.consultCoach(systemPrompt, userPrompt, fallbackText)
            _coinAnalysis.value = response
            _isAnalysisLoading.value = false
        }
    }

    // --- Trading Logic Engine Interfaces ---
    suspend fun processTrade(onStatus: (String, Boolean) -> Unit) {
        val symbol = _selectedCoinSymbol.value
        val type = tradeType.value
        val order = orderType.value
        val amountStr = tradeAmount.value
        val limitPriceStr = targetLimitPrice.value

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            onStatus("Invalid trade amount specified.", false)
            return
        }

        val coinData = marketState.value[symbol]
        if (coinData == null) {
            onStatus("Coin data unavailable.", false)
            return
        }

        val executionPrice = if (order == "LIMIT") {
            val lp = limitPriceStr.toDoubleOrNull()
            if (lp == null || lp <= 0) {
                onStatus("Please input a valid limit price.", false)
                return
            }
            lp
        } else {
            coinData.currentPrice
        }

        // Simulate Slippage check
        if (order == "MARKET" && type == "BUY") {
            val currentSlippagePercent = slippageTolerance.value.toDoubleOrNull() ?: 0.5
            val simulatedSlipValue = Random.nextDouble() * coinData.volatility * 15 // volatile spikes cause slip
            if (simulatedSlipValue > (currentSlippagePercent / 100.0)) {
                onStatus("Trade Rejected: Price slippage exceeded tolerance threshold (${String.format("%.2f", simulatedSlipValue * 100)}% vs ${currentSlippagePercent}% tolerance). Try using a Limit Order or increase slippage limit.", false)
                return
            }
        }

        // Call background repository
        withContext(Dispatchers.IO) {
            val result = repository.executeTrade(
                symbol = symbol,
                type = type,
                orderType = order,
                price = executionPrice,
                amount = amount,
                network = coinData.network
            )
            
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    onStatus("${type} Order executed successfully: ${amount} ${symbol} at $${String.format("%,.4f", executionPrice)} on ${coinData.network} network.", true)
                } else {
                    onStatus(result.exceptionOrNull()?.message ?: "Transaction failed", false)
                }
            }
        }
    }

    // --- AI Coach Chat System Actions ---
    fun askCoachForSelectedCoinExplanation() {
        val coin = marketState.value[selectedCoinSymbol.value] ?: return
        _isCoachLoading.value = true
        _coachResponse.value = ""
        activeTab.value = 3 // Direct redirect to Coach panel tab

        val systemPrompt = """
            You are Nexa Coach, a world-class institutional portfolio specialist & financial mentor.
            Analyze the user's requested token using rich analytical terminology. Enforce structured risk rules.
        """.trimIndent()

        val userPrompt = """
            Please analyze the real-time tick chart of: ${coin.symbol} (${coin.name}).
            Current Spot Price: $${String.format("%,.4f", coin.currentPrice)}
            24h Volatility Metric: ${String.format("%.2f", coin.volatility * 100)}%
            Network Infrastructure: ${coin.network}
            
            Give me technical feedback, potential support/resistance, and a recommended limit entry setup.
        """.trimIndent()

        val fallbackText = "Dynamic Report for ${coin.symbol}: Price maintains near resistance. Wait for RSI cooling to initiate buying entries."

        viewModelScope.launch {
            val response = GeminiCoachService.consultCoach(systemPrompt, userPrompt, fallbackText)
            _coachResponse.value = response
            _isCoachLoading.value = false
        }
    }

    fun requestPortfolioPerformanceAnalysis() {
        _isCoachLoading.value = true
        _coachResponse.value = ""
        activeTab.value = 3 // Direct redirect to Coach panel tab

        val systemPrompt = """
            You are Nexa Coach, a world-class quantitative risk director & portfolio manager.
            Conduct a professional risk audit of the user's current portfolios and holdings.
        """.trimIndent()

        viewModelScope.launch {
            val list = assets.value
            val prof = profile.value
            val holdingsStr = list.joinToString("\n") { "• ${it.symbol}: ${it.holdings} coins, average entry: $${it.averageBuyPrice}" }
            
            val userPrompt = """
                Conduct a portfolio audit:
                Current Spot cash balance: $${String.format("%,.2f", prof?.usdBalance ?: 0.0)}
                Active Crypto Holdings:
                $holdingsStr
                
                Highlight the diversification level, overall risk score, and specific adjustments to preserve capital.
            """.trimIndent()

            val fallbackText = "Institutional Audit Report: Cryptos represent mild exposure. Capital dry powder is well proportioned in stable cash reserves."
            val response = GeminiCoachService.consultCoach(systemPrompt, userPrompt, fallbackText)
            _coachResponse.value = response
            _isCoachLoading.value = false
        }
    }

    fun submitCoachCustomPrompt(prompt: String) {
        if (prompt.isBlank()) return
        _isCoachLoading.value = true
        _coachResponse.value = ""

        val systemPrompt = """
            You are Nexa Coach, a world-class institutional portfolio specialist & financial mentor.
            Teach the user with rich analogies. Emphasize risk control, math consistency, and long term capital growth.
        """.trimIndent()

        viewModelScope.launch {
            val response = GeminiCoachService.consultCoach(systemPrompt, prompt, "Mentor guidance: Consider structured dollar-cost averaging on premium Blue-Chip projects.")
            _coachResponse.value = response
            _isCoachLoading.value = false
        }
    }

    // --- AI-INTEGRATED ACADEMY CONNECTOR FUNCTIONS ---
    fun askCoachForAcademyHelp(lessonTitle: String, question: String, wrongAnswer: String, correctAnswer: String, topic: String) {
        _isCoachLoading.value = true
        _coachResponse.value = ""
        activeTab.value = 3 // Redirect to AI Assistant screen

        val systemPrompt = """
            You are Nexa Coach, a world-class institutional portfolio specialist, academic, and financial mentor.
            Explain why the user's selected option was incorrect, teach them the correct methodology in easy math/analogies, provide examples, and test them with 2 new custom practice questions.
        """.trimIndent()

        val userPrompt = """
            Class: Nexa Academy
            Lesson Subject: $lessonTitle
            Topic focus: $topic
            Failed Quiz Question: "$question"
            User's Incorrect Answer: "$wrongAnswer"
            Correct Dynamic Response: "$correctAnswer"
            
            Mentor instructions:
            1. Clarify the misconception simply.
            2. Share a quick practical scenario or analogy.
            3. Ask me 2 custom review questions to practice.
            4. Suggest a direct review lesson.
        """.trimIndent()

        viewModelScope.launch {
            val response = GeminiCoachService.consultCoach(
                systemPrompt,
                userPrompt,
                "Support System online. To master $topic, review the lesson. A key concept is calculating your maximum risk (under 1.5% - 2%) and choosing limit executions over high slippage market orders."
            )
            _coachResponse.value = response
            _isCoachLoading.value = false
        }
    }

    fun askCoachForAcademySuccess(lessonTitle: String, topic: String) {
        _isCoachLoading.value = true
        _coachResponse.value = ""
        activeTab.value = 3 // Redirect to AI Assistant screen

        val systemPrompt = """
            You are Nexa Coach, a world-class quantitative risk strategist.
            Congratulate the trader, enrich their understanding with advanced institutional execution parameters on this topic, and pose 2 advanced level trading case-studies for self-assessment.
        """.trimIndent()

        val userPrompt = """
            Traders League update!
            I have just successfully certified in Nexa Academy's "$lessonTitle" quiz on $topic!
            
            Coaching instructions:
            1. Congratulate me.
            2. Give me a deep advanced takeaway on $topic (institutional-level context).
            3. Challenge me with 2 complex test scenarios for self-assessment.
            4. Recommend the exact next advanced learning module to explore.
        """.trimIndent()

        viewModelScope.launch {
            val response = GeminiCoachService.consultCoach(
                systemPrompt,
                userPrompt,
                "Outstanding achievement! Your execution metrics in $lessonTitle display optimal risk analysis. Continue exploring market structure cycles to refine entries."
            )
            _coachResponse.value = response
            _isCoachLoading.value = false
        }
    }

    // Claim daily challenge rewards
    fun recordDailyChallengeReward(xpReward: Int) {
        dailyChallengeCompleted.value = true
        dailyChallengeScoreGained.value = xpReward
        viewModelScope.launch {
            repository.addXp(xpReward)
        }
    }

    // --- Learning Module System Actions ---
    fun selectLesson(lesson: Lesson) {
        _selectedLesson.value = lesson
        _quizSelectedOption.value = null
        _quizAnswered.value = false
        
        // Update lesson status in repository for active state
        viewModelScope.launch {
            val currentProg = lessonProgress.value.find { it.lessonId == lesson.id }
            if (currentProg == null || currentProg.progressPercent < 30) {
                repository.saveLessonProgress(LessonProgress(lesson.id, false, 0, 30))
            }
        }
    }

    fun answerQuiz(optionIndex: Int) {
        if (_quizAnswered.value) return
        _quizSelectedOption.value = optionIndex
        _quizAnswered.value = true

        val lesson = selectedLesson.value ?: return
        val isCorrect = optionIndex == lesson.quizCorrectIndex

        viewModelScope.launch {
            val scoreGained = if (isCorrect) 100 else 0
            if (isCorrect) {
                // Award XP and complete
                repository.addXp(lesson.xpReward)
                repository.saveLessonProgress(LessonProgress(lesson.id, true, 100, 100))
            } else {
                repository.saveLessonProgress(LessonProgress(lesson.id, false, 0, 60))
            }
        }
    }

    fun dismissLesson() {
        _selectedLesson.value = null
        _quizSelectedOption.value = null
        _quizAnswered.value = false
    }

    // --- Subscription Tier unlock simulation ---
    fun toggleSubscriptionTier() {
        val current = profile.value ?: return
        viewModelScope.launch {
            val newStatus = if (current.subscriptionStatus == "PRO") "STANDARD" else "PRO"
            repository.updateProfileDirect(current.copy(subscriptionStatus = newStatus))
            // Award subscriber XP
            repository.addXp(100)
        }
    }

    // --- Biometrics & Security indicator controls ---
    fun toggleSecurityFeature(type: String) {
        val current = profile.value ?: return
        viewModelScope.launch {
            val updated = when (type) {
                "BIOMETRICS" -> current.copy(isBiometricsSimulatedEnabled = !current.isBiometricsSimulatedEnabled)
                "2FA" -> current.copy(is2FAEnabled = !current.is2FAEnabled)
                else -> current
            }
            repository.updateProfileDirect(updated)
            repository.addXp(50)
        }
    }

    fun selectPreferredCurrency(currencyStr: String) {
        val current = profile.value ?: return
        viewModelScope.launch {
            repository.updateProfileDirect(current.copy(preferredCurrency = currencyStr))
        }
    }

    fun resetWholePlatform() {
        viewModelScope.launch {
            repository.resetSimulatedPortfolio()
            generateDailyTip()
            _selectedCoinSymbol.value = "BTC"
            dailyChallengeCompleted.value = false
            _coachResponse.value = "Terminal system reset complete. Liquid assets restored to \$100,000 spot capital value. Ready, operator."
        }
    }
}

