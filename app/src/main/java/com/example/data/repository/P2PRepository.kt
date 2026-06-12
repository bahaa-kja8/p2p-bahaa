package com.example.data.repository

import com.example.data.dao.BalanceDao
import com.example.data.dao.ExchangeRateDao
import com.example.data.dao.TradeDao
import com.example.data.model.Balance
import com.example.data.model.ExchangeRate
import com.example.data.model.Trade
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class P2PRepository(
    private val tradeDao: TradeDao,
    private val exchangeRateDao: ExchangeRateDao,
    private val balanceDao: BalanceDao
) {
    val allTrades: Flow<List<Trade>> = tradeDao.getAllTradesFlow()
    val allRates: Flow<List<ExchangeRate>> = exchangeRateDao.getAllRatesFlow()
    val balance: Flow<Balance?> = balanceDao.getBalanceFlow()

    suspend fun getDirectBalance(): Balance {
        return balanceDao.getBalanceDirect() ?: Balance()
    }

    suspend fun updateDirectBalance(syp: Double, usdt: Double) {
        val current = balanceDao.getBalanceDirect() ?: Balance()
        balanceDao.insertOrUpdateBalance(current.copy(balanceSYP = syp, balanceUSDT = usdt))
    }

    suspend fun insertRate(rateStr: Double, type: String, dateStr: String) {
        val rate = ExchangeRate(
            rate = rateStr,
            type = type,
            date = dateStr
        )
        exchangeRateDao.insertRate(rate)
    }

    suspend fun addTrade(trade: Trade) {
        // 1. Setup the trade and its calculations
        val currentTrades = tradeDao.getAllTradesDirect()
        val preparedTrade = if (trade.type == "SELL") {
            val (avgBuy, profit) = calculateSellProfitFor(trade, currentTrades)
            trade.copy(avgBuyRate = avgBuy, profitSYP = profit)
        } else {
            trade
        }

        // 2. Insert to DB
        tradeDao.insertTrade(preparedTrade)

        // 3. Update Balance
        val currentBal = getDirectBalance()
        val newBal = applyTradeEffect(currentBal, preparedTrade)
        balanceDao.insertOrUpdateBalance(newBal)

        // 4. Force global cascading recalculation of all trades to maintain integrity
        triggerGlobalRecalculation()
    }

    suspend fun deleteTrade(trade: Trade) {
        // 1. Delete from DB
        tradeDao.deleteTrade(trade)

        // 2. Revert Balance
        val currentBal = getDirectBalance()
        val revertedBal = revertTradeEffect(currentBal, trade)
        balanceDao.insertOrUpdateBalance(revertedBal)

        // 3. Recalculate remaining
        triggerGlobalRecalculation()
    }

    suspend fun updateTrade(oldTrade: Trade, newTrade: Trade) {
        // 1. Revert Old balance impact
        var currentBal = getDirectBalance()
        currentBal = revertTradeEffect(currentBal, oldTrade)

        // 2. Temp delete/update so calculation works
        tradeDao.deleteTrade(oldTrade)
        
        // 3. Get currently existing trades to determine the new trade's rate
        val remainingTrades = tradeDao.getAllTradesDirect()
        val preparedNewTrade = if (newTrade.type == "SELL") {
            val (avgBuy, profit) = calculateSellProfitFor(newTrade, remainingTrades)
            newTrade.copy(avgBuyRate = avgBuy, profitSYP = profit)
        } else {
            newTrade
        }

        // 4. Insert Updated
        tradeDao.insertTrade(preparedNewTrade)

        // 5. Apply New balance impact
        val finalBal = applyTradeEffect(currentBal, preparedNewTrade)
        balanceDao.insertOrUpdateBalance(finalBal)

        // 6. Recalculate
        triggerGlobalRecalculation()
    }

    suspend fun clearAllData() {
        tradeDao.deleteAllTrades()
        exchangeRateDao.deleteAllRates()
        balanceDao.deleteBalance()
    }

    // --- Private Calculation Helpers ---

    private fun getPriorBuyTrades(forTrade: Trade, allTrades: List<Trade>): List<Trade> {
        // Sort chronologically: Date asc, Timestamp asc, ID asc
        val sorted = allTrades.sortedWith(compareBy({ it.date }, { it.timestamp }, { it.id }))
        val forTradeIndexInSorted = sorted.indexOfFirst { it.id == forTrade.id }

        return if (forTradeIndexInSorted == -1) {
            // New trade, filter by chronological order
            sorted.filter {
                it.type == "BUY" && (it.date < forTrade.date || (it.date == forTrade.date && it.timestamp < forTrade.timestamp))
            }
        } else {
            // Existing trade, take preceding
            sorted.subList(0, forTradeIndexInSorted).filter { it.type == "BUY" }
        }
    }

    private fun calculateSellProfitFor(trade: Trade, allTrades: List<Trade>): Pair<Double, Double> {
        val priorBuys = getPriorBuyTrades(trade, allTrades)
        val totalBuyAmount = priorBuys.sumOf { it.amount }
        if (totalBuyAmount == 0.0) {
            val profit = (trade.amount * trade.rate) - (trade.fee * trade.rate)
            return Pair(0.0, Math.round(profit).toDouble())
        }
        val weightedSum = priorBuys.sumOf { it.rate * it.amount }
        val avgBuyRate = weightedSum / totalBuyAmount
        val profit = (trade.rate - avgBuyRate) * trade.amount - (trade.fee * trade.rate)
        return Pair(avgBuyRate, Math.round(profit).toDouble())
    }

    private fun applyTradeEffect(balance: Balance, trade: Trade): Balance {
        return if (trade.type == "BUY") {
            balance.copy(
                balanceSYP = balance.balanceSYP - (trade.amount * trade.rate),
                balanceUSDT = balance.balanceUSDT + (trade.amount - trade.fee)
            )
        } else {
            balance.copy(
                balanceSYP = balance.balanceSYP + (trade.amount * trade.rate),
                balanceUSDT = balance.balanceUSDT - (trade.amount + trade.fee)
            )
        }
    }

    private fun revertTradeEffect(balance: Balance, trade: Trade): Balance {
        return if (trade.type == "BUY") {
            balance.copy(
                balanceSYP = balance.balanceSYP + (trade.amount * trade.rate),
                balanceUSDT = balance.balanceUSDT - (trade.amount - trade.fee)
            )
        } else {
            balance.copy(
                balanceSYP = balance.balanceSYP - (trade.amount * trade.rate),
                balanceUSDT = balance.balanceUSDT + (trade.amount + trade.fee)
            )
        }
    }

    /**
     * Iterates through all trades chronologically, re-calculating profits for all SELL trades
     * and ensuring they are stored correctly.
     */
    private suspend fun triggerGlobalRecalculation() {
        val all = tradeDao.getAllTradesDirect()
        val sorted = all.sortedWith(compareBy({ it.date }, { it.timestamp }, { it.id }))
        
        val recalculatedList = ArrayList<Trade>()
        
        for (trade in sorted) {
            val updated = if (trade.type == "SELL") {
                val (avgBuy, profit) = calculateSellProfitFor(trade, recalculatedList)
                trade.copy(avgBuyRate = avgBuy, profitSYP = profit)
            } else {
                trade
            }
            recalculatedList.add(updated)
            // Update in DB if anything changed
            if (updated.profitSYP != trade.profitSYP || updated.avgBuyRate != trade.avgBuyRate) {
                tradeDao.updateTrade(updated)
            }
        }
    }
    
    suspend fun importBackup(importedTrades: List<Trade>, importedRates: List<ExchangeRate>, initialBal: Balance) {
        clearAllData()
        for (trade in importedTrades) {
            tradeDao.insertTrade(trade)
        }
        for (rate in importedRates) {
            exchangeRateDao.insertRate(rate)
        }
        balanceDao.insertOrUpdateBalance(initialBal)
        triggerGlobalRecalculation()
    }
}
