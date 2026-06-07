package com.example.data.repository

import com.example.data.database.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

class DatabaseRepository(private val userDao: UserDao) {

    val assets: Flow<List<UserAsset>> = userDao.getAllAssetsFlow()
        .onStart { initializeIfNeeded() }

    val transactions: Flow<List<TradeTransaction>> = userDao.getAllTransactionsFlow()

    val profile: Flow<UserProfile?> = userDao.getProfileFlow()
        .onStart { initializeIfNeeded() }

    val lessonProgress: Flow<List<LessonProgress>> = userDao.getAllLessonProgressFlow()
        .onStart { initializeIfNeeded() }

    private var isInitialized = false

    private suspend fun initializeIfNeeded() {
        if (isInitialized) return
        
        // Initialize User Profile
        if (userDao.getProfile() == null) {
            userDao.insertProfile(
                UserProfile(
                    id = 1,
                    xp = 250, // Started with some XP
                    level = 1,
                    rank = "Junior Trader",
                    usdBalance = 45000.0,
                    preferredCurrency = "USD",
                    subscriptionStatus = "STANDARD"
                )
            )
            
            // Starter assets
            userDao.insertAsset(UserAsset("BTC", "Bitcoin", 0.42, 63800.0))
            userDao.insertAsset(UserAsset("ETH", "Ethereum", 4.15, 3120.0))
            userDao.insertAsset(UserAsset("SOL", "Solana", 32.0, 145.0))
            userDao.insertAsset(UserAsset("BNB", "BNB Chain", 5.0, 560.0))

            // Starter lesson states
            userDao.insertLessonProgress(LessonProgress("crypto_basics", false, 0, 0))
            userDao.insertLessonProgress(LessonProgress("risk_mgmt", false, 0, 0))
            userDao.insertLessonProgress(LessonProgress("tech_analysis", false, 0, 0))
            userDao.insertLessonProgress(LessonProgress("psychology", false, 0, 0))

            // Pre-seed a few simulated historical trades for a rich dashboard experience
            userDao.insertTransaction(
                TradeTransaction(
                    symbol = "BTC",
                    type = "BUY",
                    orderType = "MARKET",
                    price = 62500.0,
                    amount = 0.2,
                    totalUsdValue = 12500.0,
                    feeUsd = 12.5,
                    network = "Bitcoin"
                )
            )
            userDao.insertTransaction(
                TradeTransaction(
                    symbol = "ETH",
                    type = "BUY",
                    orderType = "MARKET",
                    price = 3050.0,
                    amount = 2.0,
                    totalUsdValue = 6100.0,
                    feeUsd = 18.3,
                    network = "Ethereum"
                )
            )
            userDao.insertTransaction(
                TradeTransaction(
                    symbol = "SOL",
                    type = "BUY",
                    orderType = "LIMIT",
                    price = 140.0,
                    amount = 15.0,
                    totalUsdValue = 2100.0,
                    feeUsd = 4.2,
                    network = "Solana"
                )
            )
        }
        isInitialized = true
    }

    suspend fun getProfileDirect(): UserProfile? = userDao.getProfile()

    suspend fun updateProfileDirect(updated: UserProfile) {
        userDao.updateProfile(updated)
    }

    suspend fun getAssetDirect(symbol: String): UserAsset? = userDao.getAsset(symbol)

    suspend fun executeTrade(
        symbol: String,
        type: String, // "BUY" or "SELL"
        orderType: String, // "MARKET" or "LIMIT"
        price: Double,
        amount: Double,
        network: String
    ): Result<Unit> {
        val totalCost = price * amount
        val profileDirect = userDao.getProfile() ?: return Result.failure(Exception("Profile not found"))

        if (type == "BUY") {
            if (profileDirect.usdBalance < totalCost) {
                return Result.failure(Exception("Insufficient USD funds ($${String.format("%.2f", totalCost)} required vs $${String.format("%.2f", profileDirect.usdBalance)} available)"))
            }
            
            val updatedBalance = profileDirect.usdBalance - totalCost
            val existingAsset = userDao.getAsset(symbol)
            if (existingAsset != null) {
                val newHoldings = existingAsset.holdings + amount
                val newAvgPrice = ((existingAsset.holdings * existingAsset.averageBuyPrice) + totalCost) / newHoldings
                userDao.updateAsset(UserAsset(symbol, existingAsset.name, newHoldings, newAvgPrice))
            } else {
                userDao.insertAsset(UserAsset(symbol, symbol, amount, price))
            }
            
            // Deduct funds and award some XP for trading activity
            userDao.updateProfile(
                profileDirect.copy(
                    usdBalance = updatedBalance,
                    xp = profileDirect.xp + 25
                )
            )
        } else { // "SELL"
            val existingAsset = userDao.getAsset(symbol) ?: return Result.failure(Exception("You do not hold any $symbol"))
            if (existingAsset.holdings < amount) {
                return Result.failure(Exception("Insufficient $symbol holdings (${existingAsset.holdings} held)"))
            }
            
            val updatedHoldings = existingAsset.holdings - amount
            val profitOrLoss = (price - existingAsset.averageBuyPrice) * amount
            
            if (updatedHoldings <= 0.0) {
                userDao.deleteAsset(existingAsset)
            } else {
                userDao.updateAsset(existingAsset.copy(holdings = updatedHoldings))
            }
            
            val updatedBalance = profileDirect.usdBalance + totalCost
            // Deduct/add funds and award XP
            userDao.updateProfile(
                profileDirect.copy(
                    usdBalance = updatedBalance,
                    xp = profileDirect.xp + 30
                )
            )
        }

        // Log transaction
        val feeRate = if (profileDirect.subscriptionStatus == "PRO") 0.0005 else 0.0015
        userDao.insertTransaction(
            TradeTransaction(
                symbol = symbol,
                type = type,
                orderType = orderType,
                price = price,
                amount = amount,
                totalUsdValue = totalCost,
                feeUsd = totalCost * feeRate,
                network = network
            )
        )

        return Result.success(Unit)
    }

    suspend fun saveLessonProgress(progress: LessonProgress) {
        userDao.insertLessonProgress(progress)
    }

    suspend fun addXp(amount: Int) {
        val profileDirect = userDao.getProfile() ?: return
        val newXp = profileDirect.xp + amount
        
        // Dynamic Level Calculation: 500 XP per level
        val newLevel = (newXp / 500) + 1
        val newRank = when (newLevel) {
            1 -> "Novice Tracker"
            2 -> "Satoshi Disciple"
            3 -> "Derivatives Specialist"
            4 -> "Hedge Fund Lead"
            else -> "Market Mastermind"
        }
        
        userDao.updateProfile(
            profileDirect.copy(
                xp = newXp,
                level = newLevel,
                rank = newRank
            )
        )
    }

    suspend fun resetSimulatedPortfolio() {
        userDao.clearAssets()
        userDao.clearTransactions()
        
        userDao.insertProfile(
            UserProfile(
                id = 1,
                xp = 0,
                level = 1,
                rank = "Novice Tracker",
                usdBalance = 100000.0, // Clean reset to HNW virtual status
                securePin = ""
            )
        )
        
        userDao.insertAsset(UserAsset("BTC", "Bitcoin", 0.15, 64200.0))
        userDao.insertAsset(UserAsset("ETH", "Ethereum", 2.0, 3100.0))
        userDao.insertAsset(UserAsset("SOL", "Solana", 10.0, 140.0))
    }
}
