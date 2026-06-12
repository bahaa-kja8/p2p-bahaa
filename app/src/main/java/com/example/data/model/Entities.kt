package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "trades")
data class Trade(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String, // "BUY" or "SELL"
    val amount: Double,
    val rate: Double,
    val totalSYP: Double,
    val fee: Double, // in USDT
    val profitSYP: Double, // in SYP, computed for SELL trades
    val avgBuyRate: Double, // weighted average buy rate of prior buy trades
    val date: String, // String representation format "YYYY-MM-DD"
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "exchange_rates")
data class ExchangeRate(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val rate: Double,
    val type: String, // "BUY" or "SELL"
    val date: String, // "YYYY-MM-DD HH:MM"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "balances")
data class Balance(
    @PrimaryKey val id: String = "current_balance",
    val balanceSYP: Double = 0.0,
    val balanceUSDT: Double = 0.0
)
