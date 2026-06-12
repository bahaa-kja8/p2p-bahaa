package com.example.data.dao

import androidx.room.*
import com.example.data.model.Balance
import com.example.data.model.ExchangeRate
import com.example.data.model.Trade
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY date DESC, timestamp DESC")
    fun getAllTradesFlow(): Flow<List<Trade>>

    @Query("SELECT * FROM trades ORDER BY date ASC, timestamp ASC")
    suspend fun getAllTradesDirect(): List<Trade>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: Trade)

    @Update
    suspend fun updateTrade(trade: Trade)

    @Delete
    suspend fun deleteTrade(trade: Trade)

    @Query("DELETE FROM trades")
    suspend fun deleteAllTrades()
}

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates ORDER BY timestamp DESC LIMIT 30")
    fun getAllRatesFlow(): Flow<List<ExchangeRate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: ExchangeRate)

    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAllRates()
}

@Dao
interface BalanceDao {
    @Query("SELECT * FROM balances WHERE id = 'current_balance'")
    fun getBalanceFlow(): Flow<Balance?>

    @Query("SELECT * FROM balances WHERE id = 'current_balance'")
    suspend fun getBalanceDirect(): Balance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBalance(balance: Balance)

    @Query("DELETE FROM balances")
    suspend fun deleteBalance()
}
