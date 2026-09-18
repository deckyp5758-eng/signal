package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signals_history")
data class SignalEntity(
    @PrimaryKey val id: String,
    val instrumentSymbol: String,
    val action: String,
    val strength: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double,
    val slPips: Double,
    val tp1Pips: Double,
    val riskReward: Double,
    val confidence: Int,
    val reason: String,
    val timeframe: String,
    val rsi: Double,
    val emaFast: Double,
    val emaSlow: Double,
    val timestamp: Long,
    val outcomeStatus: String = "ACTIVE" // ACTIVE, TP_HIT, SL_HIT
)

@Entity(tableName = "trade_plans")
data class TradePlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val instrumentSymbol: String,
    val action: String,
    val accountBalance: Double,
    val riskPercent: Double,
    val lotSize: Double,
    val entryPrice: Double,
    val stopLossPrice: Double,
    val takeProfitPrice: Double,
    val maxLossUsd: Double,
    val potentialProfitUsd: Double,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
