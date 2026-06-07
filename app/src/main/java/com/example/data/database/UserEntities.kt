package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_assets")
data class UserAsset(
    @PrimaryKey val symbol: String, // BTC, ETH, SOL, etc.
    val name: String,
    val holdings: Double, // Amount of coins held
    val averageBuyPrice: Double // Average purchase price in USD
)

@Entity(tableName = "trade_transactions")
data class TradeTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String, // Coin traded
    val type: String, // "BUY", "SELL", "CONVERT_IN", "CONVERT_OUT"
    val orderType: String, // "MARKET", "LIMIT"
    val price: Double, // Price in USD at execution
    val amount: Double, // Amount of coin
    val totalUsdValue: Double, // Total USD valuation
    val feeUsd: Double, // Simulated fee in USD
    val network: String, // Bitcoin, Ethereum, Solana, BSC
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Single row profile
    val xp: Int = 0,
    val level: Int = 1,
    val rank: String = "Novice Analyst",
    val usdBalance: Double = 50000.0, // Starter virtual funds
    val preferredCurrency: String = "USD", // Local currency
    val subscriptionStatus: String = "STANDARD", // "STANDARD", "PRO"
    val isBiometricsSimulatedEnabled: Boolean = false,
    val is2FAEnabled: Boolean = false,
    val securePin: String = ""
)

@Entity(tableName = "lesson_progress")
data class LessonProgress(
    @PrimaryKey val lessonId: String, // "crypto_basics", "risk_mgmt", "tech_analysis", "psychology"
    val isCompleted: Boolean = false,
    val maxQuizScore: Int = 0, // In percent
    val progressPercent: Int = 0
)
