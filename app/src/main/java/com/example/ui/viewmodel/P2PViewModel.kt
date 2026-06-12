package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Balance
import com.example.data.model.ExchangeRate
import com.example.data.model.Trade
import com.example.data.repository.P2PRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class P2PViewModel(private val repository: P2PRepository) : ViewModel() {

    // Current screen Tab
    private val _currentTab = MutableStateFlow("HOME")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // Item being edited
    private val _editingTrade = MutableStateFlow<Trade?>(null)
    val editingTrade: StateFlow<Trade?> = _editingTrade.asStateFlow()

    // Repos collections
    val trades: StateFlow<List<Trade>> = repository.allTrades
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rates: StateFlow<List<ExchangeRate>> = repository.allRates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val balance: StateFlow<Balance?> = repository.balance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun navigateTo(tab: String) {
        _currentTab.value = tab
        if (tab != "ADD_TRADE") {
            _editingTrade.value = null
        }
    }

    fun startEditing(trade: Trade) {
        _editingTrade.value = trade
        _currentTab.value = "ADD_TRADE"
    }

    fun cancelEditing() {
        _editingTrade.value = null
        _currentTab.value = "HISTORY"
    }

    fun saveTrade(
        type: String,
        amount: Double,
        rate: Double,
        fee: Double,
        date: String,
        note: String
    ) {
        viewModelScope.launch {
            val editItem = _editingTrade.value
            val trade = Trade(
                id = editItem?.id ?: java.util.UUID.randomUUID().toString(),
                type = type,
                amount = amount,
                rate = rate,
                totalSYP = amount * rate,
                fee = fee,
                profitSYP = 0.0, // Calculated during save
                avgBuyRate = 0.0, // Calculated during save
                date = date,
                note = note,
                timestamp = editItem?.timestamp ?: System.currentTimeMillis()
            )

            if (editItem != null) {
                repository.updateTrade(editItem, trade)
                _editingTrade.value = null
            } else {
                repository.addTrade(trade)
            }
            _currentTab.value = "HISTORY"
        }
    }

    fun deleteTrade(trade: Trade) {
        viewModelScope.launch {
            repository.deleteTrade(trade)
        }
    }

    fun updateBalances(syp: Double, usdt: Double) {
        viewModelScope.launch {
            repository.updateDirectBalance(syp, usdt)
        }
    }

    fun addExchangeRate(rate: Double, type: String) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            repository.insertRate(rate, type, dateStr)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    // --- Dynamic Stats calculation for RTL Home screen ---
    
    fun getTodayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    // Export and Import Logic
    fun generateBackupJson(): String {
        val json = JSONObject()
        val bal = balance.value ?: Balance()
        json.put("balanceSYP", bal.balanceSYP)
        json.put("balanceUSDT", bal.balanceUSDT)

        val tradesArray = JSONArray()
        for (t in trades.value) {
            val tObj = JSONObject().apply {
                put("id", t.id)
                put("type", t.type)
                put("amount", t.amount)
                put("rate", t.rate)
                put("totalSYP", t.totalSYP)
                put("fee", t.fee)
                put("profitSYP", t.profitSYP)
                put("avgBuyRate", t.avgBuyRate)
                put("date", t.date)
                put("note", t.note)
                put("timestamp", t.timestamp)
            }
            tradesArray.put(tObj)
        }
        json.put("trades", tradesArray)

        val ratesArray = JSONArray()
        for (r in rates.value) {
            val rObj = JSONObject().apply {
                put("id", r.id)
                put("rate", r.rate)
                put("type", r.type)
                put("date", r.date)
                put("timestamp", r.timestamp)
            }
            ratesArray.put(rObj)
        }
        json.put("rates", ratesArray)

        return json.toString(4)
    }

    fun generateCSV(): String {
        // columns name in arabic and readable by Excel (with UTF-8 BOM if possible)
        val sb = StringBuilder()
        // Adding UTF-8 byte order mark (BOM) for Excel arabic compatibility
        sb.append('\uFEFF')
        sb.append("النوع,الكمية USDT,السعر,الإجمالي,الرسوم USDT,الربح SYP,التاريخ,الملاحظة\n")
        for (t in trades.value) {
            val typeAr = if (t.type == "BUY") "شراء" else "بيع"
            sb.append("$typeAr,")
            sb.append("${t.amount},")
            sb.append("${t.rate},")
            sb.append("${t.totalSYP},")
            sb.append("${t.fee},")
            sb.append("${if (t.type == "SELL") t.profitSYP else "-"},")
            sb.append("${t.date},")
            sb.append("\"${t.note.replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }

    fun importBackupJson(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            val balSyp = json.optDouble("balanceSYP", 0.0)
            val balUsdt = json.optDouble("balanceUSDT", 0.0)
            val bal = Balance("current_balance", balSyp, balUsdt)

            val tradesList = ArrayList<Trade>()
            val tradesAr = json.optJSONArray("trades")
            if (tradesAr != null) {
                for (i in 0 until tradesAr.length()) {
                    val obj = tradesAr.getJSONObject(i)
                    tradesList.add(
                        Trade(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            type = obj.getString("type"),
                            amount = obj.getDouble("amount"),
                            rate = obj.getDouble("rate"),
                            totalSYP = obj.optDouble("totalSYP", obj.getDouble("amount") * obj.getDouble("rate")),
                            fee = obj.optDouble("fee", 0.0),
                            profitSYP = obj.optDouble("profitSYP", 0.0),
                            avgBuyRate = obj.optDouble("avgBuyRate", 0.0),
                            date = obj.optString("date", getTodayString()),
                            note = obj.optString("note", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            val ratesList = ArrayList<ExchangeRate>()
            val ratesAr = json.optJSONArray("rates")
            if (ratesAr != null) {
                for (i in 0 until ratesAr.length()) {
                    val obj = ratesAr.getJSONObject(i)
                    ratesList.add(
                        ExchangeRate(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            rate = obj.getDouble("rate"),
                            type = obj.getString("type"),
                            date = obj.optString("date", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            viewModelScope.launch {
                repository.importBackup(tradesList, ratesList, bal)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

class P2PViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(P2PViewModel::class.java)) {
            val db = AppDatabase.getDatabase(context)
            val repository = P2PRepository(db.tradeDao(), db.exchangeRateDao(), db.balanceDao())
            @Suppress("UNCHECKED_CAST")
            return P2PViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
