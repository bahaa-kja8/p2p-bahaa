package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.DatePicker
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Balance
import com.example.data.model.ExchangeRate
import com.example.data.model.Trade
import com.example.ui.theme.*
import com.example.ui.viewmodel.P2PViewModel
import java.text.DecimalFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: P2PViewModel) {
    // Force RTL layout representation
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val currentTab by viewModel.currentTab.collectAsState()
        val editingTrade by viewModel.editingTrade.collectAsState()
        val context = LocalContext.current

        Scaffold(
            containerColor = BgColor,
            bottomBar = {
                NavigationBar(
                    containerColor = CardColor,
                    contentColor = TextColor,
                    tonalElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    val items = listOf(
                        Triple("HOME", "الرئيسية", Icons.Default.Home),
                        Triple("ADD_TRADE", if (editingTrade != null) "تعديل صفقة" else "صفقة جديدة", Icons.Default.AddCircle),
                        Triple("HISTORY", "السجل", Icons.Default.List),
                        Triple("RATES", "الأسعار", Icons.Default.Star),
                        Triple("SETTINGS", "الإعدادات", Icons.Default.Settings)
                    )
                    items.forEach { (tab, label, icon) ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { viewModel.navigateTo(tab) },
                            icon = { Icon(icon, contentDescription = label, tint = if (currentTab == tab) GoldColor else TextSecondaryColor) },
                            label = { 
                                Text(
                                    label, 
                                    color = if (currentTab == tab) GoldColor else TextSecondaryColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = BgColor
                            ),
                            modifier = Modifier.testTag("nav_item_$tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    "HOME" -> HomeScreen(viewModel)
                    "ADD_TRADE" -> AddTradeScreen(viewModel, editingTrade)
                    "HISTORY" -> HistoryScreen(viewModel)
                    "RATES" -> RatesScreen(viewModel)
                    "SETTINGS" -> SettingsScreen(viewModel)
                }
            }
        }
    }
}

// --- Composing format helpers ---
fun formatSyp(value: Double): String {
    val df = DecimalFormat("#,###")
    return df.format(value)
}

fun formatUsdt(value: Double): String {
    val df = DecimalFormat("#,##0.00")
    return df.format(value)
}

// --- Home Screen View ---
@Composable
fun HomeScreen(viewModel: P2PViewModel) {
    val context = LocalContext.current
    val trades by viewModel.trades.collectAsState()
    val rates by viewModel.rates.collectAsState()
    val balanceOpt by viewModel.balance.collectAsState()
    val balance = balanceOpt ?: Balance()

    var showBalanceDialog by remember { mutableStateOf(false) }

    // Computations
    val totalSypProfit = trades.filter { it.type == "SELL" }.sumOf { it.profitSYP }
    val totalTradesCount = trades.size
    val maxSingleProfit = if (trades.filter { it.type == "SELL" }.isNotEmpty()) {
        trades.filter { it.type == "SELL" }.maxOf { it.profitSYP }
    } else {
        0.0
    }
    val totalFeesUsdt = trades.sumOf { it.fee }

    // Today statistics
    val todayStr = viewModel.getTodayString()
    val todayTrades = trades.filter { it.date == todayStr }
    val todayTradesCount = todayTrades.size
    val todaySypProfit = todayTrades.filter { it.type == "SELL" }.sumOf { it.profitSYP }
    
    val lastRate = rates.firstOrNull()?.rate ?: 0.0

    // Main layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = GoldColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "تاجر P2P - السوق السورية",
                color = TextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Portfolio Card (محفظتي الحالية)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("portfolio_card"),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoldColor.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "محفظتي الحالية",
                    color = TextSecondaryColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // SYP Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "رصيد الليرة السورية",
                        color = TextSecondaryColor,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${formatSyp(balance.balanceSYP)} ل.س",
                        color = GoldColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                // USDT Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "رصيد الـ USDT",
                        color = TextSecondaryColor,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${formatUsdt(balance.balanceUSDT)} USDT",
                        color = GreenColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { showBalanceDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldColor, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_balances_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تعديل الأرصدة يدوياً", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2x2 KPIs Grid
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Net Profit
                KpiCard(
                    title = "صافي الربح الكلي",
                    value = "${formatSyp(totalSypProfit)} ل.س",
                    valueColor = if (totalSypProfit >= 0) GreenColor else RedColor,
                    modifier = Modifier.weight(1f)
                )
                // Card 2: Total Trades
                KpiCard(
                    title = "عدد الصفقات الكلي",
                    value = "$totalTradesCount صفقة",
                    valueColor = TextColor,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 3: Top Profit
                KpiCard(
                    title = "أعلى ربح صفقة",
                    value = "${formatSyp(maxSingleProfit)} ل.س",
                    valueColor = GreenColor,
                    modifier = Modifier.weight(1f)
                )
                // Card 4: Total Fees
                KpiCard(
                    title = "إجمالي الرسوم",
                    value = "${formatUsdt(totalFeesUsdt)} USDT",
                    valueColor = RedColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Today's statistics card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "اليوم",
                    color = GoldColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("صفقات اليوم", color = TextSecondaryColor, fontSize = 11.sp)
                        Text("$todayTradesCount صفقة", color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("أرباح اليوم", color = TextSecondaryColor, fontSize = 11.sp)
                        Text(
                            "${formatSyp(todaySypProfit)} ل.س", 
                            color = if (todaySypProfit >= 0) GreenColor else RedColor, 
                            fontSize = 15.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("آخر سعر صرف", color = TextSecondaryColor, fontSize = 11.sp)
                        Text(
                            if (lastRate > 0) "${formatSyp(lastRate)} ل.س" else "-", 
                            color = TextColor, 
                            fontSize = 15.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Last 3 trades title and list
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "آخر 3 صفقات",
                color = TextColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "عرض الكل",
                color = GoldColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { viewModel.navigateTo("HISTORY") }
                    .testTag("view_all_trades")
            )
        }

        if (trades.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد صفقات مسجلة بعد.",
                    color = TextSecondaryColor,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                trades.take(3).forEach { trade ->
                    TradeBriefItem(trade = trade)
                }
            }
        }
    }

    // Modal to modify balance
    if (showBalanceDialog) {
        var sypInput by remember { mutableStateOf(balance.balanceSYP.toString()) }
        var usdtInput by remember { mutableStateOf(balance.balanceUSDT.toString()) }

        Dialog(onDismissRequest = { showBalanceDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تعديل رصيد المحفظة",
                        color = TextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = sypInput,
                        onValueChange = { sypInput = it },
                        label = { Text("رصيد الليرة السورية SYP") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldColor,
                            focusedLabelColor = GoldColor,
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            unfocusedLabelColor = TextSecondaryColor
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_syp_input")
                    )

                    OutlinedTextField(
                        value = usdtInput,
                        onValueChange = { usdtInput = it },
                        label = { Text("رصيد الـ USDT") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldColor,
                            focusedLabelColor = GoldColor,
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            unfocusedLabelColor = TextSecondaryColor
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_usdt_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val syp = sypInput.toDoubleOrNull() ?: 0.0
                                val usdt = usdtInput.toDoubleOrNull() ?: 0.0
                                viewModel.updateBalances(syp, usdt)
                                showBalanceDialog = false
                                Toast.makeText(context, "تم تعديل الرصيد بنجاح", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldColor, contentColor = Color.Black),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_balances_button")
                        ) {
                            Text("حفظ", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showBalanceDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                            border = BorderStroke(1.dp, TextSecondaryColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KpiCard(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = title,
                color = TextSecondaryColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TradeBriefItem(trade: Trade) {
    val isBuy = trade.type == "BUY"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isBuy) GreenColor.copy(alpha = 0.15f) else RedColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBuy) "شراء" else "بيع",
                        color = if (isBuy) GreenColor else RedColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${formatUsdt(trade.amount)} USDT",
                        color = TextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = trade.date,
                        color = TextSecondaryColor,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${formatSyp(trade.rate)} ل.س",
                    color = TextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (!isBuy) {
                    Text(
                        text = "الربح: +${formatSyp(trade.profitSYP)}",
                        color = GreenColor,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        text = "الإجمالي: ${formatSyp(trade.totalSYP)}",
                        color = TextSecondaryColor,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}


// --- Add Trade (New or Editing) Screen ---
@Composable
fun AddTradeScreen(viewModel: P2PViewModel, editingTrade: Trade?) {
    val context = LocalContext.current
    val todayStr = viewModel.getTodayString()

    // Inputs
    var type by remember { mutableStateOf(editingTrade?.type ?: "BUY") }
    var amountInput by remember { mutableStateOf(editingTrade?.amount?.toString() ?: "") }
    var rateInput by remember { mutableStateOf(editingTrade?.rate?.toString() ?: "") }
    var feeInput by remember { mutableStateOf(editingTrade?.fee?.toString() ?: "") }
    var dateInput by remember { mutableStateOf(editingTrade?.date ?: todayStr) }
    var noteInput by remember { mutableStateOf(editingTrade?.note ?: "") }

    // Preview
    val amount = amountInput.toDoubleOrNull() ?: 0.0
    val rate = rateInput.toDoubleOrNull() ?: 0.0
    val totalLira = amount * rate

    // Standard DatePickerDialog trigger
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            val monthStr = if (month + 1 < 10) "0${month + 1}" else "${month + 1}"
            val dayStr = if (dayOfMonth < 10) "0$dayOfMonth" else "$dayOfMonth"
            dateInput = "$year-$monthStr-$dayStr"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Header
        Text(
            text = if (editingTrade != null) "تعديل صفقة P2P" else "إضافة صفقة P2P جديدة",
            color = TextColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        // Type selection toggle buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { type = "BUY" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == "BUY") GreenColor else CardColor,
                    contentColor = if (type == "BUY") Color.Black else TextSecondaryColor
                ),
                shape = RoundedCornerShape(10.dp),
                border = if (type != "BUY") BorderStroke(1.dp, TextSecondaryColor.copy(alpha = 0.3f)) else null,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("type_buy_btn")
            ) {
                Text(
                    text = "🟢 شراء USDT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Button(
                onClick = { type = "SELL" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == "SELL") RedColor else CardColor,
                    contentColor = if (type == "SELL") Color.White else TextSecondaryColor
                ),
                shape = RoundedCornerShape(10.dp),
                border = if (type != "SELL") BorderStroke(1.dp, TextSecondaryColor.copy(alpha = 0.3f)) else null,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("type_sell_btn")
            ) {
                Text(
                    text = "🔴 بيع USDT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        // Amount input
        OutlinedTextField(
            value = amountInput,
            onValueChange = { amountInput = it },
            label = { Text("كمية الـ USDT") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("amount_input")
        )

        // Exchange rate input
        OutlinedTextField(
            value = rateInput,
            onValueChange = { rateInput = it },
            label = { Text("سعر الصرف (ل.س لكل 1 USDT)") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rate_input")
        )

        // Instant Preview Block
        if (amountInput.isNotEmpty() && rateInput.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الإجمالي التقديري بالليرة:", color = TextSecondaryColor, fontSize = 13.sp)
                    Text(
                        "${formatSyp(totalLira)} ل.س",
                        color = GoldColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Fee input
        OutlinedTextField(
            value = feeInput,
            onValueChange = { feeInput = it },
            label = { Text("رسوم الصفقة بـ USDT (اختياري)") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("fee_input")
        )

        // Date selector input field with icon
        OutlinedTextField(
            value = dateInput,
            onValueChange = { dateInput = it },
            label = { Text("تاريخ الصفقة") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { datePickerDialog.show() }) {
                    Icon(Icons.Default.DateRange, contentDescription = "اختر تاريخ", tint = GoldColor)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { datePickerDialog.show() }
                .testTag("date_picker_trigger")
        )

        // Note input
        OutlinedTextField(
            value = noteInput,
            onValueChange = { noteInput = it },
            label = { Text("ملاحظات (نص اختياري)") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("note_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Save button
        Button(
            onClick = {
                val amt = amountInput.toDoubleOrNull() ?: 0.0
                val rt = rateInput.toDoubleOrNull() ?: 0.0
                val fe = feeInput.toDoubleOrNull() ?: 0.0

                if (amt <= 0.0 || rt <= 0.0) {
                    Toast.makeText(context, "الرجاء إدخال قيم صحيحة للكمية وسعر الصرف", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.saveTrade(
                        type = type,
                        amount = amt,
                        rate = rt,
                        fee = fe,
                        date = dateInput,
                        note = noteInput
                    )
                    Toast.makeText(context, "تم حفظ الصفقة بنجاح", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GoldColor, contentColor = Color.Black),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_trade_btn")
        ) {
            Text(
                if (editingTrade != null) "تعديل وحفظ الصفقة" else "حفظ الصفقة",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        // Cancel editing button
        if (editingTrade != null) {
            Button(
                onClick = { viewModel.cancelEditing() },
                colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                border = BorderStroke(1.dp, TextSecondaryColor),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("cancel_edit_btn")
            ) {
                Text("إلغاء التعديل")
            }
        }
    }
}


// --- Trade History Screen ---
@Composable
fun HistoryScreen(viewModel: P2PViewModel) {
    val context = LocalContext.current
    val trades by viewModel.trades.collectAsState()

    var activeFilter by remember { mutableStateOf("ALL") } // "ALL", "BUY", "SELL"
    var tradeToDelete by remember { mutableStateOf<Trade?>(null) }

    // Filtered lists
    val filteredTrades = trades.filter {
        when (activeFilter) {
            "BUY" -> it.type == "BUY"
            "SELL" -> it.type == "SELL"
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "سجل الصفقات المسجلة",
            color = TextColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        // Selector filter buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(Pair("ALL", "الكل"), Pair("BUY", "شراء"), Pair("SELL", "بيع"))
            filters.forEach { (typeKey, label) ->
                val active = activeFilter == typeKey
                Button(
                    onClick = { activeFilter = typeKey },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) GoldColor else CardColor,
                        contentColor = if (active) Color.Black else TextSecondaryColor
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = if (!active) BorderStroke(1.dp, TextSecondaryColor.copy(alpha = 0.2f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_$typeKey")
                ) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Display results count
        Text(
            text = "العناصر المعروضة: ${filteredTrades.size} صفقة",
            color = TextSecondaryColor,
            fontSize = 12.sp
        )

        if (filteredTrades.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد صفقات مطابقة للفلاتر المحددة.", color = TextSecondaryColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredTrades, key = { it.id }) { trade ->
                    HistoryTradeItem(
                        trade = trade,
                        onEdit = { viewModel.startEditing(trade) },
                        onDelete = { tradeToDelete = trade }
                    )
                }
            }
        }
    }

    // Deletion Dialog
    if (tradeToDelete != null) {
        val tradeObj = tradeToDelete!!
        AlertDialog(
            onDismissRequest = { tradeToDelete = null },
            title = { Text("تأكيد الحذف", fontWeight = FontWeight.Bold) },
            text = { 
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف هذه الصفقة؟\n" +
                           "سيؤدي ذلك إلى تعديل الأرصدة التلقائية وإعادة توازن العمليات.",
                    color = TextColor
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrade(tradeObj)
                        tradeToDelete = null
                        Toast.makeText(context, "تم حذف الصفقة", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedColor)
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { tradeToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                    border = BorderStroke(1.dp, TextSecondaryColor)
                ) {
                    Text("إلغاء")
                }
            },
            containerColor = CardColor,
            titleContentColor = TextColor,
            textContentColor = TextColor
        )
    }
}

@Composable
fun HistoryTradeItem(trade: Trade, onEdit: () -> Unit, onDelete: () -> Unit) {
    val isBuy = trade.type == "BUY"
    val accentColor = if (isBuy) GreenColor else RedColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trade_card_${trade.id}"),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Color Sidebar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accentColor)
                    .align(Alignment.CenterVertically)
            )

            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top line: Operation type, Date, Note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isBuy) "🟢 شراء USDT" else "🔴 بيع USDT",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = trade.date,
                            color = TextSecondaryColor,
                            fontSize = 11.sp
                        )
                    }

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = GoldColor, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = RedColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Note if present
                if (trade.note.isNotEmpty()) {
                    Text(
                        text = "ملاحظة: ${trade.note}",
                        color = TextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.background(BgColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                // Grid Details: Quantity, Rate, Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("الكمية", color = TextSecondaryColor, fontSize = 11.sp)
                        Text("${formatUsdt(trade.amount)} USDT", color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("السعر", color = TextSecondaryColor, fontSize = 11.sp)
                        Text("${formatSyp(trade.rate)} ل.س", color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("الإجمالي", color = TextSecondaryColor, fontSize = 11.sp)
                        Text("${formatSyp(trade.totalSYP)} ل.س", color = GoldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Fees
                if (trade.fee > 0.0) {
                    val feeEqLira = trade.fee * trade.rate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgColor, RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الرسوم الفعالة:", color = TextSecondaryColor, fontSize = 11.sp)
                        Text("${formatUsdt(trade.fee)} USDT (~${formatSyp(feeEqLira)} ل.س)", color = RedColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Profit Box for SELL only
                if (!isBuy) {
                    val sideBuyRateStr = if (trade.avgBuyRate > 0) formatSyp(trade.avgBuyRate) else "غير محدد"
                    val profitPositive = trade.profitSYP >= 0.0

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (profitPositive) GreenColor.copy(alpha = 0.12f) else RedColor.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (profitPositive) GreenColor.copy(alpha = 0.3f) else RedColor.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("العمليات (متوسط شراء: $sideBuyRateStr ← بيع: ${formatSyp(trade.rate)})", color = TextSecondaryColor, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("العائد المالي الصافي:", color = TextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "${if (profitPositive) "+" else ""}${formatSyp(trade.profitSYP)} ل.س",
                                color = if (profitPositive) GreenColor else RedColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


// --- Exchange Rates Screen ---
@Composable
fun RatesScreen(viewModel: P2PViewModel) {
    val context = LocalContext.current
    val rates by viewModel.rates.collectAsState()

    var rateInput by remember { mutableStateOf("") }
    var typeSelector by remember { mutableStateOf("BUY") } // "BUY", "SELL"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "متابعة أسعار صرف العملات",
            color = TextColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        // Add rate form card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "تسجيل سعر صرف جديد",
                    color = GoldColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = rateInput,
                    onValueChange = { rateInput = it },
                    label = { Text("سعر الصرف (ل.س لـ 1 USDT)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldColor,
                        focusedLabelColor = GoldColor,
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        unfocusedLabelColor = TextSecondaryColor
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نوع السعر:", color = TextSecondaryColor, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { typeSelector = "BUY" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (typeSelector == "BUY") GreenColor else BgColor,
                                contentColor = if (typeSelector == "BUY") Color.Black else TextSecondaryColor
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, TextSecondaryColor.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("شراء")
                        }

                        Button(
                            onClick = { typeSelector = "SELL" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (typeSelector == "SELL") RedColor else BgColor,
                                contentColor = if (typeSelector == "SELL") Color.White else TextSecondaryColor
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, TextSecondaryColor.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("بيع")
                        }
                    }
                }

                Button(
                    onClick = {
                        val rt = rateInput.toDoubleOrNull() ?: 0.0
                        if (rt <= 0.0) {
                            Toast.makeText(context, "الرجاء إدخال قيمة صحيحة لسعر الصرف", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addExchangeRate(rt, typeSelector)
                            rateInput = ""
                            Toast.makeText(context, "تم حفظ سعر الصرف بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldColor, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إضافة السعر الحالي", fontWeight = FontWeight.Bold)
                }
            }
        }

        // List Header
        Text(
            text = "آخر 30 سعر مسجل",
            color = TextColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        // List
        if (rates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد أسعار صرف مسجلة بعد.", color = TextSecondaryColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rates, key = { it.id }) { rate ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (rate.type == "BUY") GreenColor.copy(alpha = 0.15f) else RedColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (rate.type == "BUY") "شراء" else "بيع",
                                        color = if (rate.type == "BUY") GreenColor else RedColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(rate.date, color = TextSecondaryColor, fontSize = 11.sp)
                            }
                            Text("${formatSyp(rate.rate)} ل.س", color = GoldColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}


// --- Settings Screen ---
@Composable
fun SettingsScreen(viewModel: P2PViewModel) {
    val context = LocalContext.current
    val balanceOpt by viewModel.balance.collectAsState()
    val balance = balanceOpt ?: Balance()

    var showClipboardDialog by remember { mutableStateOf(false) }
    var clipboardContent by remember { mutableStateOf("") }
    var clipboardDialogTitle by remember { mutableStateOf("") }

    var importText by remember { mutableStateOf("") }

    // Wiping confirmation triggers
    var showWipeConfirm1 by remember { mutableStateOf(false) }
    var showWipeConfirm2 by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "إعدادات التطبيق الخلفية",
            color = TextColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        // Section 1: Data Backup and Sharing
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "النسخ الاحتياطي ومشاركة البيانات",
                    color = GoldColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // JSON Backup Export
                Button(
                    onClick = {
                        val json = viewModel.generateBackupJson()
                        clipboardContent = json
                        clipboardDialogTitle = "نسخة احتياطية كاملة (JSON)"
                        showClipboardDialog = true

                        // Copy directly to clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("P2P_BackupJSON", json)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "تم توليد النسخ ونسخه للحافظة تلقائياً!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                    border = BorderStroke(1.dp, GoldColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = GoldColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تصدير نسخة احتياطية (JSON)", fontWeight = FontWeight.Medium)
                }

                // CSV Export (Excel)
                Button(
                    onClick = {
                        val csv = viewModel.generateCSV()
                        
                        // Direct native Share Sheet for CSV string
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة تقرير صفقات CSV للأكسل"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                    border = BorderStroke(1.dp, GoldColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.List, contentDescription = null, tint = GoldColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مشاركة تقرير صفقات مميز (CSV)", fontWeight = FontWeight.Medium)
                }
            }
        }

        // Section 2: Backup Import
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "استيراد نسخة احتياطية (JSON)",
                    color = GoldColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("قُم بلصق رمز النسخ JSON هنا...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldColor,
                        focusedLabelColor = GoldColor,
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        unfocusedLabelColor = TextSecondaryColor
                    ),
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )

                Button(
                    onClick = {
                        if (importText.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء لصق كود JSON صحيح للاستيراد", Toast.LENGTH_SHORT).show()
                        } else {
                            val success = viewModel.importBackupJson(importText)
                            if (success) {
                                importText = ""
                                Toast.makeText(context, "تم استعادة البيانات والنسخة بنجاح!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "فشل الاستيراد! تأكد من سلامة هيكلية كود الـ JSON", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldColor, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("استيراد البيانات واستعادة الأرصدة", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section 3: Risk Zone - Wipe Data
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            border = BorderStroke(1.dp, RedColor.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "منطقة الخطر المميتة",
                    color = RedColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "تحذير: سيؤدي هذا الخيار إلى مسح كل الصفقات وأسعار الصرف وتصفير أرصدة المحفظة تماماً بشكل غير قابل للتراجع.",
                    color = TextSecondaryColor,
                    fontSize = 11.sp
                )

                Button(
                    onClick = { showWipeConfirm1 = true },
                    colors = ButtonDefaults.buttonColors(containerColor = RedColor, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("مسح جميع البيانات", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal to display JSON for copy backup safety
    if (showClipboardDialog) {
        Dialog(onDismissRequest = { showClipboardDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = clipboardDialogTitle,
                        color = TextColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(BgColor, RoundedCornerShape(8.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        Text(
                            text = clipboardContent,
                            color = TextSecondaryColor,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("P2P_Export_JSON", clipboardContent)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "تم النسخ للحافظة!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldColor, contentColor = Color.Black),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("نسخ الكود", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showClipboardDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                            border = BorderStroke(1.dp, TextSecondaryColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إغلاق")
                        }
                    }
                }
            }
        }
    }

    // --- Double Confirmation wiping dialogues ---
    if (showWipeConfirm1) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm1 = false },
            title = { Text("تأكيد الحذف الأول (1/2)", fontWeight = FontWeight.Bold, color = RedColor) },
            text = { Text("هل أنت متأكد تماماً من رغبتك في حذف وحذف كل صفقات وبيانات هذا التطبيق؟ سيتم فقدان كل شيء.") },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirm1 = false
                        showWipeConfirm2 = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedColor)
                ) {
                    Text("نعم، تابع للخطوة التالية", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showWipeConfirm1 = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                    border = BorderStroke(1.dp, TextSecondaryColor)
                ) {
                    Text("تراجع")
                }
            },
            containerColor = CardColor,
            titleContentColor = TextColor,
            textContentColor = TextColor
        )
    }

    if (showWipeConfirm2) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm2 = false },
            title = { Text("التأكيد الأخير الحاسم (2/2)", fontWeight = FontWeight.Bold, color = RedColor) },
            text = { Text("تأكيد أخير: لا يمكن التراجع أبداً بعد تفعيل هذا الأمر. هل تريد بالفعل إفراغ قاعدة البيانات كلياً وتصفير الأرصدة؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showWipeConfirm2 = false
                        Toast.makeText(context, "تم مسح جميع البيانات كلياً وتصفير الأرصدة", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedColor)
                ) {
                    Text("تأكيد الحذف النهائي الشامل!", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showWipeConfirm2 = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                    border = BorderStroke(1.dp, TextSecondaryColor)
                ) {
                    Text("إلغاء وتراجع الآن")
                }
            },
            containerColor = CardColor,
            titleContentColor = TextColor,
            textContentColor = TextColor
        )
    }
}
