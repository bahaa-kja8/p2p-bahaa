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

    suspend fun updateDirectBalances(
        syp: Double, usdt: Double, usd: Double, tryVal: Double,
        eur: Double, usdc: Double, btc: Double, eth: Double
    ) {
        val current = balanceDao.getBalanceDirect() ?: Balance()
        balanceDao.insertOrUpdateBalance(current.copy(
            balanceSYP = syp,
            balanceUSDT = usdt,
            balanceUSD = usd,
            balanceTRY = tryVal,
            balanceEUR = eur,
            balanceUSDC = usdc,
            balanceBTC = btc,
            balanceETH = eth
        ))
    }

    suspend fun insertRate(rateStr: Double, type: String, dateStr: String, crypto: String = "USDT", fiat: String = "SYP") {
        val rate = ExchangeRate(
            rate = rateStr,
            type = type,
            date = dateStr,
            cryptoCurrency = crypto,
            fiatCurrency = fiat
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
                it.type == "BUY" && 
                it.cryptoCurrency == forTrade.cryptoCurrency &&
                it.fiatCurrency == forTrade.fiatCurrency &&
                (it.date < forTrade.date || (it.date == forTrade.date && it.timestamp < forTrade.timestamp))
            }
        } else {
            // Existing trade, take preceding
            sorted.subList(0, forTradeIndexInSorted).filter { 
                it.type == "BUY" &&
                it.cryptoCurrency == forTrade.cryptoCurrency &&
                it.fiatCurrency == forTrade.fiatCurrency
            }
        }
    }

    private fun calculateSellProfitFor(trade: Trade, allTrades: List<Trade>): Pair<Double, Double> {
        val priorBuys = getPriorBuyTrades(trade, allTrades)
        val totalBuyAmount = priorBuys.sumOf { it.amount }
        if (totalBuyAmount == 0.0) {
            val profit = (trade.amount * trade.rate) - (trade.fee * trade.rate)
            val roundedProfit = if (trade.fiatCurrency == "USD") {
                Math.round(profit * 100.0) / 100.0
            } else {
                Math.round(profit).toDouble()
            }
            return Pair(0.0, roundedProfit)
        }
        val weightedSum = priorBuys.sumOf { it.rate * it.amount }
        val avgBuyRate = weightedSum / totalBuyAmount
        val profit = (trade.rate - avgBuyRate) * trade.amount - (trade.fee * trade.rate)
        val roundedProfit = if (trade.fiatCurrency == "USD") {
            Math.round(profit * 100.0) / 100.0
        } else {
            Math.round(profit).toDouble()
        }
        return Pair(avgBuyRate, roundedProfit)
    }

    private fun updateBalanceValue(balance: Balance, currency: String, diff: Double): Balance {
        return when (currency.uppercase()) {
            "SYP" -> balance.copy(balanceSYP = balance.balanceSYP + diff)
            "USDT" -> balance.copy(balanceUSDT = balance.balanceUSDT + diff)
            "USD" -> balance.copy(balanceUSD = balance.balanceUSD + diff)
            "TRY" -> balance.copy(balanceTRY = balance.balanceTRY + diff)
            "EUR" -> balance.copy(balanceEUR = balance.balanceEUR + diff)
            "USDC" -> balance.copy(balanceUSDC = balance.balanceUSDC + diff)
            "BTC" -> balance.copy(balanceBTC = balance.balanceBTC + diff)
            "ETH" -> balance.copy(balanceETH = balance.balanceETH + diff)
            else -> balance
        }
    }

    private fun applyTradeEffect(balance: Balance, trade: Trade): Balance {
        return if (trade.type == "BUY") {
            val bal1 = updateBalanceValue(balance, trade.cryptoCurrency, trade.amount - trade.fee)
            updateBalanceValue(bal1, trade.fiatCurrency, -(trade.amount * trade.rate))
        } else {
            val bal1 = updateBalanceValue(balance, trade.cryptoCurrency, -(trade.amount + trade.fee))
            updateBalanceValue(bal1, trade.fiatCurrency, trade.amount * trade.rate)
        }
    }

    private fun revertTradeEffect(balance: Balance, trade: Trade): Balance {
        return if (trade.type == "BUY") {
            val bal1 = updateBalanceValue(balance, trade.cryptoCurrency, -(trade.amount - trade.fee))
            updateBalanceValue(bal1, trade.fiatCurrency, trade.amount * trade.rate)
        } else {
            val bal1 = updateBalanceValue(balance, trade.cryptoCurrency, trade.amount + trade.fee)
            updateBalanceValue(bal1, trade.fiatCurrency, -(trade.amount * trade.rate))
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
