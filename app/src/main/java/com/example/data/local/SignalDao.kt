package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {
    @Query("SELECT * FROM signals_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllSignals(): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals_history WHERE instrumentSymbol = :symbol ORDER BY timestamp DESC LIMIT 30")
    fun getSignalsByInstrument(symbol: String): Flow<List<SignalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: SignalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignals(signals: List<SignalEntity>)

    @Query("DELETE FROM signals_history")
    suspend fun clearAllSignals()

    // Trade plans
    @Query("SELECT * FROM trade_plans ORDER BY timestamp DESC")
    fun getAllTradePlans(): Flow<List<TradePlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTradePlan(plan: TradePlanEntity)

    @Query("DELETE FROM trade_plans WHERE id = :id")
    suspend fun deleteTradePlan(id: Long)
}
