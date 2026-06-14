package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.widget.DatePicker
import android.widget.Toast
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.Balance
import com.example.data.model.ExchangeRate
import com.example.data.model.Trade
import com.example.ui.theme.*
import com.example.ui.viewmodel.P2PViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import android.graphics.Color as AndroidColor
import java.text.DecimalFormat
import java.util.*

// Dynamic Localized Context configuration wrapper to read strings dynamically on language swaps
fun getLocalizedContext(context: Context, lang: String): Context {
    val locale = Locale(lang)
    Locale.setDefault(locale)
    val config = android.content.res.Configuration(context.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return context.createConfigurationContext(config)
}

@Composable
fun MainAppScreen(viewModel: P2PViewModel) {
    val langState by viewModel.language.collectAsState()
    val localizedContext = getLocalizedContext(LocalContext.current, langState)
    
    fun getString(resId: Int): String {
        return localizedContext.getString(resId)
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides (if (langState == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr)
    ) {
        val currentTab by viewModel.currentTab.collectAsState()
        val editingTrade by viewModel.editingTrade.collectAsState()

        Scaffold(
            containerColor = BgColor,
            topBar = {
                if (currentTab in listOf("HOME", "HISTORY", "CALENDAR", "RATES")) {
                    MarketSelectorBar(viewModel, ::getString)
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = CardColor,
                    contentColor = TextColor,
                    tonalElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    val items = listOf(
                        Triple("HOME", getString(R.string.home), Icons.Default.Home),
                        Triple("ADD_TRADE", if (editingTrade != null) getString(R.string.edit_trade) else getString(R.string.trade), Icons.Default.AddCircle),
                        Triple("HISTORY", getString(R.string.history), Icons.Default.List),
                        Triple("CALENDAR", getString(R.string.calendar), Icons.Default.DateRange),
                        Triple("RATES", getString(R.string.rates), Icons.Default.Star),
                        Triple("SETTINGS", getString(R.string.settings), Icons.Default.Settings)
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
                                    fontSize = 10.sp,
                                    fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                    "HOME" -> HomeScreen(viewModel, { getString(it) })
                    "ADD_TRADE" -> AddTradeScreen(viewModel, editingTrade, { getString(it) })
                    "HISTORY" -> HistoryScreen(viewModel, { getString(it) })
                    "CALENDAR" -> CalendarScreen(viewModel, { getString(it) })
                    "RATES" -> RatesScreen(viewModel, { getString(it) })
                    "SETTINGS" -> SettingsScreen(viewModel, { getString(it) })
                }
            }
        }
    }
}

// --- Market/Portfolio Active Currency Selector (Multi-currency Separated Profits) ---
@Composable
fun MarketSelectorBar(
    viewModel: P2PViewModel,
    getString: (Int) -> String
) {
    val activeFiat by viewModel.selectedFiat.collectAsState()
    val fiats = listOf(
        Triple("SYP", "ل.س", "USDT/SYP"),
        Triple("USD", "$", "USDT/USD"),
        Triple("TRY", "TL", "USDT/TRY"),
        Triple("EUR", "€", "USDT/EUR")
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("market_selector"),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "السوق النشط والتداول المنفصل / Active Market Selection",
                color = TextSecondaryColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fiats.forEach { (code, symbol, pairString) ->
                    val isSelected = activeFiat == code
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) GoldGradient else Brush.verticalGradient(listOf(BgColor, BgColor)))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else BorderColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.setSelectedFiat(code) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = pairString,
                                color = if (isSelected) Color.Black else TextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = symbol,
                                color = if (isSelected) Color.Black.copy(alpha = 0.8f) else TextSecondaryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Section Header with Gradient Icon (Rule 3 & 5) ---
@Composable
fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: Brush = GoldGradient
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(gradient, shape = RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextColor,
            fontWeight = FontWeight.Bold
        )
    }
}

// --- Universal Format Helpers ---
fun formatSyp(value: Double, code: String = "SYP"): String {
    val symbols = java.text.DecimalFormatSymbols(Locale.US)
    val pattern = when (code.uppercase()) {
        "BTC", "ETH" -> "#,##0.000000"
        "USDT", "USDC", "USD", "EUR" -> "#,##0.00"
        else -> "#,###" // Local Syrian Lira / L.S or TRY
    }
    val df = DecimalFormat(pattern, symbols)
    return df.format(value)
}

fun formatCompactSyp(value: Double): String {
    return when {
        Math.abs(value) >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000)
        Math.abs(value) >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000)
        else -> String.format(Locale.US, "%.0f", value)
    }
}

// Custom reusable Segmented Control view component (Requirement 5)
@Composable
fun <T> SegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelection: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CardColor, shape = RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .background(
                        if (isSelected) GoldColor else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onItemSelection(item) }
                    .wrapContentSize(Alignment.Center)
            ) {
                Text(
                    text = itemLabel(item),
                    color = if (isSelected) Color.Black else TextSecondaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Custom Dynamic Dropdown Selector
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selectedOption,
            onValueChange = {},
            label = { Text(label) },
            shape = RoundedCornerShape(14.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(CardColor)
        ) {
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(text = selectionOption, color = TextColor) },
                    onClick = {
                        onOptionSelected(selectionOption)
                        expanded = false
                    }
                )
            }
        }
    }
}

// --- Home Screen View ---
@Composable
fun HomeScreen(viewModel: P2PViewModel, getString: (Int) -> String) {
    val context = LocalContext.current
    val trades by viewModel.trades.collectAsState()
    val rates by viewModel.rates.collectAsState()
    val balanceOpt by viewModel.balance.collectAsState()
    val balance = balanceOpt ?: Balance()
    val activeFiat by viewModel.selectedFiat.collectAsState()

    val marketTrades = trades.filter { it.fiatCurrency == activeFiat }
    val totalSypProfit = marketTrades.filter { it.type == "SELL" }.sumOf { it.profitSYP }
    val totalTradesCount = marketTrades.size
    val maxSingleProfit = if (marketTrades.filter { it.type == "SELL" }.isNotEmpty()) {
        marketTrades.filter { it.type == "SELL" }.maxOf { it.profitSYP }
    } else {
        0.0
    }
    val totalFeesUsdt = marketTrades.sumOf { it.fee }

    val todayStr = viewModel.getTodayString()
    val todayTrades = marketTrades.filter { it.date == todayStr }
    val todayTradesCount = todayTrades.size
    val todaySypProfit = todayTrades.filter { it.type == "SELL" }.sumOf { it.profitSYP }
    val currentRate = rates.filter { it.fiatCurrency == activeFiat }.firstOrNull()?.rate ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Aesthetic App Title Header
        SectionHeader(
            title = "${getString(R.string.app_name)} - ${getString(R.string.rates_indicator)} ($activeFiat)",
            icon = Icons.Default.TrendingUp,
            gradient = GoldGradient
        )

        // Expanded Multi-Currency Wallet Card (Requirement 2)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("portfolio_card"),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getString(R.string.wallet_balances),
                        color = TextSecondaryColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { viewModel.updateBalances(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = getString(R.string.wipe_wallet),
                            tint = RedColor.copy(alpha = 0.8f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Crypto currencies row list
                Text("Crypto / الرقمية", color = GoldColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                listOf(
                    "USDT" to balance.balanceUSDT,
                    "USDC" to balance.balanceUSDC,
                    "BTC" to balance.balanceBTC,
                    "ETH" to balance.balanceETH
                ).forEach { (code, amount) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(code, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${formatSyp(amount, code)} $code",
                            color = GreenColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderColor, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Fiat currencies list
                Text("Fiat / المحلية", color = GoldColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                listOf(
                    "SYP" to balance.balanceSYP,
                    "USD" to balance.balanceUSD,
                    "TRY" to balance.balanceTRY,
                    "EUR" to balance.balanceEUR
                ).forEach { (code, amount) ->
                    val isMarketSelected = code == activeFiat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(if (isMarketSelected) GoldColor.copy(alpha = 0.08f) else Color.Transparent, shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = if (isMarketSelected) 4.dp else 0.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(code, color = if (isMarketSelected) GoldColor else TextColor, fontSize = 13.sp, fontWeight = if (isMarketSelected) FontWeight.Bold else FontWeight.Medium)
                            if (isMarketSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("(السوق المختار)", color = GoldColor, fontSize = 10.sp)
                            }
                        }
                        Text(
                            "${formatSyp(amount, code)} $code",
                            color = if (isMarketSelected) GoldColor else TextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Merged Fee inside Total profits KPI card (Asymmetric Layout - Requirement 5)
        KpiCard(
            title = "${getString(R.string.total_profits)} ($activeFiat)",
            value = "${formatSyp(totalSypProfit, activeFiat)} $activeFiat",
            valueColor = if (totalSypProfit >= 0.0) GreenColor else RedColor,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.AccountBalanceWallet,
            gradient = GreenGradient,
            extraRow = {
                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${getString(R.string.fee)} (USDT):",
                        color = TextSecondaryColor,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "${formatSyp(totalFeesUsdt, "USDT")} USDT",
                        color = RedColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )

        // Other two KPIs in Grid row below
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "صفقات $activeFiat",
                value = "$totalTradesCount صفقة",
                valueColor = TextColor,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Receipt,
                gradient = PurpleGradient
            )
            KpiCard(
                title = "أعلى ربح صفقة ($activeFiat)",
                value = "${formatSyp(maxSingleProfit, activeFiat)} $activeFiat",
                valueColor = GreenColor,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.TrendingUp,
                gradient = GoldGradient
            )
        }

        // Today Active Statistics
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(GoldGradient, shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Today,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "${getString(R.string.today_trades)} ($activeFiat)",
                        color = TextColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("نشاط اليوم", color = TextSecondaryColor, style = MaterialTheme.typography.labelMedium)
                        Text("$todayTradesCount صفقات", color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("أرباح اليوم التقديرية", color = TextSecondaryColor, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${formatSyp(todaySypProfit, activeFiat)} $activeFiat",
                            color = if (todaySypProfit >= 0.0) GreenColor else RedColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- Add or Edit Trade Screen View (Requirement 2 & 3 & 5) ---
@Composable
fun AddTradeScreen(viewModel: P2PViewModel, editingTrade: Trade?, getString: (Int) -> String) {
    val context = LocalContext.current
    val todayStr = viewModel.getTodayString()
    val activeFiat by viewModel.selectedFiat.collectAsState()

    // Supported multi-currency selection options
    val cryptos = listOf("USDT", "USDC", "BTC", "ETH")
    val fiats = listOf("SYP", "USD", "TRY", "EUR")

    var type by remember { mutableStateOf(editingTrade?.type ?: "BUY") }
    var selectedCrypto by remember { mutableStateOf(editingTrade?.cryptoCurrency ?: "USDT") }
    var selectedFiat by remember { mutableStateOf(editingTrade?.fiatCurrency ?: activeFiat) }

    LaunchedEffect(activeFiat) {
        if (editingTrade == null) {
            selectedFiat = activeFiat
        }
    }
    var amountInput by remember { mutableStateOf(editingTrade?.amount?.toString() ?: "") }
    var rateInput by remember { mutableStateOf(editingTrade?.rate?.toString() ?: "") }
    var feeInput by remember { mutableStateOf(editingTrade?.fee?.toString() ?: "") }
    var dateInput by remember { mutableStateOf(editingTrade?.date ?: todayStr) }
    var noteInput by remember { mutableStateOf(editingTrade?.note ?: "") }
    var customerNameInput by remember { mutableStateOf(editingTrade?.customerName ?: "") }

    // Instant update preview for items
    val amount = amountInput.toDoubleOrNull() ?: 0.0
    val rate = rateInput.toDoubleOrNull() ?: 0.0
    val totalCalculated = amount * rate

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
            .background(BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionHeader(
            title = if (editingTrade != null) getString(R.string.edit_trade) else getString(R.string.add_new_trade),
            icon = if (editingTrade != null) Icons.Default.Edit else Icons.Default.AddCircle,
            gradient = GoldGradient
        )

        // Segmented Control for Buy/Sell instead of duplicate toggles
        SegmentedControl(
            items = listOf("BUY", "SELL"),
            selectedItem = type,
            onItemSelection = { type = it },
            itemLabel = { if (it == "BUY") getString(R.string.buy) else getString(R.string.sell) }
        )

        // Dropdown Spinners for crypto and local currencies
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CurrencyDropdown(
                label = getString(R.string.crypto_currency),
                options = cryptos,
                selectedOption = selectedCrypto,
                onOptionSelected = { selectedCrypto = it },
                modifier = Modifier.weight(1f)
            )
            CurrencyDropdown(
                label = getString(R.string.fiat_currency),
                options = fiats,
                selectedOption = selectedFiat,
                onOptionSelected = { selectedFiat = it },
                modifier = Modifier.weight(1f)
            )
        }

        // Amount Input Field
        OutlinedTextField(
            value = amountInput,
            onValueChange = { amountInput = it },
            label = { Text("${getString(R.string.amount)} ($selectedCrypto)") },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("amount_input")
        )

        // Exchange Rate Input Field
        OutlinedTextField(
            value = rateInput,
            onValueChange = { rateInput = it },
            label = { Text("${getString(R.string.exchange_rate)} ($selectedFiat / 1 $selectedCrypto)") },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("rate_input")
        )

        // Visual preview calculations
        if (amountInput.isNotEmpty() && rateInput.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(getString(R.string.total_syp_label), color = TextSecondaryColor, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${formatSyp(totalCalculated, selectedFiat)} $selectedFiat",
                        color = GoldColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Fee input b/currency
        OutlinedTextField(
            value = feeInput,
            onValueChange = { feeInput = it },
            label = { Text("${getString(R.string.fee)} ($selectedCrypto)") },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("fee_input")
        )

        // Date Picker field
        OutlinedTextField(
            value = dateInput,
            onValueChange = { dateInput = it },
            label = { Text(getString(R.string.trade_date)) },
            readOnly = true,
            shape = RoundedCornerShape(14.dp),
            trailingIcon = {
                IconButton(onClick = { datePickerDialog.show() }) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = GoldColor)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            modifier = Modifier.fillMaxWidth().clickable { datePickerDialog.show() }
        )

        // Customer Name/Note input (Requirement 3)
        OutlinedTextField(
            value = customerNameInput,
            onValueChange = { customerNameInput = it },
            label = { Text(getString(R.string.customer_name)) },
            placeholder = { Text(getString(R.string.customer_field_hint)) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
        )

        // Internal note
        OutlinedTextField(
            value = noteInput,
            onValueChange = { noteInput = it },
            label = { Text(getString(R.string.note)) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = GoldColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Actions
        Button(
            onClick = {
                val amt = amountInput.toDoubleOrNull() ?: 0.0
                val rt = rateInput.toDoubleOrNull() ?: 0.0
                val fe = feeInput.toDoubleOrNull() ?: 0.0
                if (amt <= 0.0 || rt <= 0.0) {
                    Toast.makeText(context, "الرجاء إدخال الكمية وسعر الصرف بشكل صحيح", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.saveTrade(
                        type = type,
                        amount = amt,
                        rate = rt,
                        fee = fe,
                        date = dateInput,
                        note = noteInput,
                        crypto = selectedCrypto,
                        fiat = selectedFiat,
                        customerName = customerNameInput
                    )
                    Toast.makeText(context, "تم حفظ بيانات العملية", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(GoldGradient, shape = RoundedCornerShape(14.dp))
                .testTag("save_trade_btn")
        ) {
            Text(getString(R.string.save_trade_btn), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
        }

        if (editingTrade != null) {
            Button(
                onClick = { viewModel.cancelEditing() },
                colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(getString(R.string.cancel_btn))
            }
        }
    }
}

// --- Trade History Screen View (Requirement 5 & 6) ---
@Composable
fun HistoryScreen(viewModel: P2PViewModel, getString: (Int) -> String) {
    val context = LocalContext.current
    val trades by viewModel.trades.collectAsState()
    val activeFiat by viewModel.selectedFiat.collectAsState()

    var activeFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    
    var tradeToDelete by remember { mutableStateOf<Trade?>(null) }
    var selectedTradeOptions by remember { mutableStateOf<Trade?>(null) }

    // Multi-faceted SearchView filtering (Date, Client, Type, Currency) - Requirement 6
    val filteredTrades = trades.filter { it.fiatCurrency == activeFiat }.filter { trade ->
        val matchesType = when (activeFilter) {
            "BUY" -> trade.type == "BUY"
            "SELL" -> trade.type == "SELL"
            else -> true
        }
        val query = searchQuery.lowercase().trim()
        val matchesSearch = if (query.isNotEmpty()) {
            trade.customerName.lowercase().contains(query) ||
            trade.note.lowercase().contains(query) ||
            trade.cryptoCurrency.lowercase().contains(query) ||
            trade.fiatCurrency.lowercase().contains(query) ||
            trade.date.contains(query)
        } else {
            true
        }
        matchesType && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionHeader(
            title = "${getString(R.string.history)} ($activeFiat)",
            icon = Icons.Default.List,
            gradient = GoldGradient
        )

        // Custom view Segmented Control replace separate duplicate buttons
        SegmentedControl(
            items = listOf("ALL", "BUY", "SELL"),
            selectedItem = activeFilter,
            onItemSelection = { activeFilter = it },
            itemLabel = {
                when (it) {
                    "BUY" -> getString(R.string.buy)
                    "SELL" -> getString(R.string.sell)
                    else -> getString(R.string.all_types)
                }
            }
        )

        // Modern SearchView (Requirement 6)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(getString(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldColor) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = TextSecondaryColor)
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldColor,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                focusedLabelColor = GoldColor,
                unfocusedLabelColor = TextSecondaryColor
            ),
            modifier = Modifier.fillMaxWidth().testTag("search_view")
        )

        if (filteredTrades.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد عمليات مطابقة لبحثك.", color = TextSecondaryColor, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredTrades, key = { it.id }) { trade ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTradeOptions = trade }, // Open dialog on click (Requirement 6)
                        colors = CardDefaults.cardColors(containerColor = CardColor),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isBuy = trade.type == "BUY"
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(if (isBuy) GreenGradient else RedGradient, shape = RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isBuy) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBuy) getString(R.string.buy) else getString(R.string.sell),
                                        color = if (isBuy) GreenColor else RedColor,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(trade.date, color = TextSecondaryColor, fontSize = 11.sp)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("الكمية", style = MaterialTheme.typography.labelMedium, color = TextSecondaryColor)
                                        Text("${formatSyp(trade.amount, trade.cryptoCurrency)} ${trade.cryptoCurrency}", color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("سعر الصرف", style = MaterialTheme.typography.labelMedium, color = TextSecondaryColor)
                                        Text("${formatSyp(trade.rate, trade.fiatCurrency)} ${trade.fiatCurrency}", color = TextColor, fontSize = 13.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("الإجمالي", style = MaterialTheme.typography.labelMedium, color = TextSecondaryColor)
                                        val totalVal = trade.amount * trade.rate
                                        Text("${formatSyp(totalVal, trade.fiatCurrency)} ${trade.fiatCurrency}", color = GoldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (trade.type == "SELL") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(BgColor, shape = RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("صافي الأرباح:", style = MaterialTheme.typography.labelMedium, color = TextSecondaryColor)
                                        Text(
                                            "${formatSyp(trade.profitSYP, "SYP")} SYP",
                                            color = if (trade.profitSYP >= 0) GreenColor else RedColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                if (trade.customerName.isNotEmpty() || trade.note.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (trade.customerName.isNotEmpty()) {
                                            Text(
                                                text = "العميل: ${trade.customerName}",
                                                color = GoldColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        if (trade.note.isNotEmpty()) {
                                            Text(
                                                text = "ملاحظة: ${trade.note}",
                                                color = TextSecondaryColor,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Click trade options dialog (Requirement 6)
    if (selectedTradeOptions != null) {
        val selected = selectedTradeOptions!!
        Dialog(onDismissRequest = { selectedTradeOptions = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.padding(16.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "خيارات العملية",
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${if (selected.type == "BUY") "شراء" else "بيع"} ${selected.amount} ${selected.cryptoCurrency} @ ${selected.rate}",
                        color = TextSecondaryColor,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            viewModel.startEditing(selected)
                            selectedTradeOptions = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(GoldGradient, shape = RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تعديل تفاصيل الصفقة", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    Button(
                        onClick = {
                            tradeToDelete = selected
                            selectedTradeOptions = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(RedGradient, shape = RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حذف الصفقة نهائياً", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    OutlinedButton(
                        onClick = { selectedTradeOptions = null },
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text(getString(R.string.close), color = TextColor)
                    }
                }
            }
        }
    }

    // Delete confirm popup
    if (tradeToDelete != null) {
        val t = tradeToDelete!!
        AlertDialog(
            onDismissRequest = { tradeToDelete = null },
            shape = RoundedCornerShape(18.dp),
            title = { Text(getString(R.string.delete_confirm_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
            text = { Text(getString(R.string.delete_confirm_msg), color = TextColor, style = MaterialTheme.typography.labelMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrade(t)
                        tradeToDelete = null
                        Toast.makeText(context, "تم الحذف واسترداد تأثير المحفظة", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حذف فعلي")
                }
            },
            dismissButton = {
                Button(
                    onClick = { tradeToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(getString(R.string.cancel_btn))
                }
            }
        )
    }
}

// --- Exchange Rates Screen with MPAndroidChart line-graph (Requirement 2 & 4) ---
@Composable
fun RatesScreen(viewModel: P2PViewModel, getString: (Int) -> String) {
    val context = LocalContext.current
    val rates by viewModel.rates.collectAsState()
    val activeFiat by viewModel.selectedFiat.collectAsState()

    val cryptos = listOf("USDT", "USDC", "BTC", "ETH")
    val fiats = listOf("SYP", "USD", "TRY", "EUR")

    var rateInput by remember { mutableStateOf("") }
    var typeSelector by remember { mutableStateOf("BUY") }
    var selectedCrypto by remember { mutableStateOf("USDT") }
    var selectedFiat by remember { mutableStateOf(activeFiat) }

    LaunchedEffect(activeFiat) {
        selectedFiat = activeFiat
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionHeader(
            title = getString(R.string.rates_today),
            icon = Icons.Default.TrendingUp,
            gradient = GoldGradient
        )

        // Dropdowns for currency selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = getString(R.string.add_rate_desc),
                    color = GoldColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CurrencyDropdown(
                        label = getString(R.string.crypto_currency),
                        options = cryptos,
                        selectedOption = selectedCrypto,
                        onOptionSelected = { selectedCrypto = it },
                        modifier = Modifier.weight(1f)
                    )
                    CurrencyDropdown(
                        label = getString(R.string.fiat_currency),
                        options = fiats,
                        selectedOption = selectedFiat,
                        onOptionSelected = { selectedFiat = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = rateInput,
                    onValueChange = { rateInput = it },
                    label = { Text("${getString(R.string.rate_value)} ($selectedFiat/1 $selectedCrypto)") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldColor,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = GoldColor,
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        unfocusedLabelColor = TextSecondaryColor
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Types toggle Segmented Control
                SegmentedControl(
                    items = listOf("BUY", "SELL"),
                    selectedItem = typeSelector,
                    onItemSelection = { typeSelector = it },
                    itemLabel = { if (it == "BUY") getString(R.string.buy) else getString(R.string.sell) }
                )

                Button(
                    onClick = {
                        val rt = rateInput.toDoubleOrNull() ?: 0.0
                        if (rt <= 0.0) {
                            Toast.makeText(context, "الرجاء كُتابة سعر صرف صالح.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addExchangeRate(rt, typeSelector, selectedCrypto, selectedFiat)
                            rateInput = ""
                            Toast.makeText(context, "تم تسجيل مؤشر السعر التقديري بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(GoldGradient, shape = RoundedCornerShape(14.dp))
                ) {
                    Text(getString(R.string.add_rate_btn), fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        // Subtitle line chart replaces last 30 list (Requirement 4)
        Text(
            text = getString(R.string.custom_rates_chart),
            color = TextColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Plotting Line graph with MPAndroidChart wrapper (embedded inside a Card - Rule 9)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                RatesLineChart(
                    rates = rates.filter { it.fiatCurrency == activeFiat && it.cryptoCurrency == selectedCrypto },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// MPAndroidChart lines wrapper in Dark slate colors (Requirement 4)
@Composable
fun RatesLineChart(rates: List<ExchangeRate>, modifier: Modifier = Modifier) {
    if (rates.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("لا تتوفر حركة كافية لرسّ السعر بياناً حالياً.", color = TextSecondaryColor, style = MaterialTheme.typography.labelMedium)
        }
        return
    }

    // Sort chronologically ascending
    val sortedRates = rates.sortedBy { it.timestamp }
    val buyEntries = ArrayList<Entry>()
    val sellEntries = ArrayList<Entry>()

    sortedRates.forEachIndexed { idx, rate ->
        val xVal = idx.toFloat()
        val yVal = rate.rate.toFloat()
        if (rate.type == "BUY") {
            buyEntries.add(Entry(xVal, yVal))
        } else {
            sellEntries.add(Entry(xVal, yVal))
        }
    }

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                legend.textColor = AndroidColor.WHITE
                legend.textSize = 10f

                xAxis.apply {
                    textColor = AndroidColor.LTGRAY
                    gridColor = AndroidColor.DKGRAY
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawLabels(true)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val idx = value.toInt()
                            if (idx >= 0 && idx < sortedRates.size) {
                                val fullDate = sortedRates[idx].date
                                return if (fullDate.length > 10) fullDate.substring(5, 10) else fullDate
                            }
                            return ""
                        }
                    }
                }

                axisLeft.apply {
                    textColor = AndroidColor.LTGRAY
                    gridColor = AndroidColor.DKGRAY
                    setDrawGridLines(true)
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val dataSets = ArrayList<LineDataSet>()
            if (buyEntries.isNotEmpty()) {
                val buySet = LineDataSet(buyEntries, "طلب شراء / BUY").apply {
                    color = AndroidColor.GREEN
                    setCircleColor(AndroidColor.GREEN)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextColor = AndroidColor.WHITE
                    setDrawValues(false)
                }
                dataSets.add(buySet)
            }
            if (sellEntries.isNotEmpty()) {
                val sellSet = LineDataSet(sellEntries, "عرض بيع / SELL").apply {
                    color = AndroidColor.RED
                    setCircleColor(AndroidColor.RED)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextColor = AndroidColor.WHITE
                    setDrawValues(false)
                }
                dataSets.add(sellSet)
            }
            if (dataSets.isNotEmpty()) {
                chart.data = LineData(dataSets.toList())
                chart.invalidate()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.Transparent)
            .padding(4.dp)
    )
}

// --- Dynamic Monthly Profits Calendar & Charts (Requirement 4 & 7) ---
@Composable
fun CalendarScreen(viewModel: P2PViewModel, getString: (Int) -> String) {
    val context = LocalContext.current
    val trades by viewModel.trades.collectAsState()
    val activeFiat by viewModel.selectedFiat.collectAsState()

    var currentYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var selectedDayDetails by remember { mutableStateOf<Pair<String, List<Trade>>?>(null) }

    val monthNamesArabic = listOf(
        "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
        "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"
    )

    val yearStr = String.format(Locale.US, "%04d", currentYear)
    val monthStr = String.format(Locale.US, "%02d", currentMonth + 1)
    val targetPrefix = "$yearStr-$monthStr"

    val monthlyTrades = trades.filter { it.fiatCurrency == activeFiat && it.date.startsWith(targetPrefix) }
    val workingDaysCount = monthlyTrades.map { it.date }.toSet().size
    val monthlyProfit = monthlyTrades.filter { it.type == "SELL" }.sumOf { it.profitSYP }

    // Advanced computed stats (Requirement 7)
    val monthlySellTradesCount = monthlyTrades.count { it.type == "SELL" }
    val avgProfitPerTrade = if (monthlySellTradesCount > 0) monthlyProfit / monthlySellTradesCount else 0.0

    val profitByDate = monthlyTrades.filter { it.type == "SELL" }
        .groupBy { it.date }
        .mapValues { (_, dayList) -> dayList.sumOf { it.profitSYP } }
    
    val bestDayVal = profitByDate.maxOfOrNull { it.value } ?: 0.0
    val worstDayVal = profitByDate.minOfOrNull { it.value } ?: 0.0

    val bestDayStr = profitByDate.maxByOrNull { it.value }?.key ?: "-"
    val worstDayStr = profitByDate.minByOrNull { it.value }?.key ?: "-"

    val totalInvestedFiat = monthlyTrades.filter { it.type == "BUY" }.sumOf { it.amount * it.rate }
    val roi = if (totalInvestedFiat > 0.0) (monthlyProfit / totalInvestedFiat) * 100.0 else 0.0

    val firstDayCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, currentYear)
        set(Calendar.MONTH, currentMonth)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK)
    val maxDays = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val startOffset = when (firstDayOfWeek) {
        Calendar.SATURDAY -> 0
        Calendar.SUNDAY -> 1
        Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 3
        Calendar.WEDNESDAY -> 4
        Calendar.THURSDAY -> 5
        Calendar.FRIDAY -> 6
        else -> 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionHeader(
            title = getString(R.string.monthly_profit_calendar),
            icon = Icons.Default.DateRange,
            gradient = GoldGradient
        )

        // Calendar Month Controller Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (currentMonth == 0) {
                        currentMonth = 11
                        currentYear -= 1
                    } else {
                        currentMonth -= 1
                    }
                }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Prev", tint = GoldColor)
                }

                Text(
                    text = "${monthNamesArabic[currentMonth]} $currentYear",
                    color = TextColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = {
                    if (currentMonth == 11) {
                        currentMonth = 0
                        currentYear += 1
                    } else {
                        currentMonth += 1
                    }
                }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = GoldColor)
                }
            }
        }

        // First row of KPIs: working days & profit sum
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = getString(R.string.working_days_count),
                value = "$workingDaysCount يوم",
                valueColor = GoldColor,
                gradient = PurpleGradient,
                icon = Icons.Default.DateRange,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = getString(R.string.monthly_profit_total),
                value = "${formatSyp(monthlyProfit, activeFiat)} $activeFiat",
                valueColor = if (monthlyProfit >= 0.0) GreenColor else RedColor,
                gradient = if (monthlyProfit >= 0.0) GreenGradient else RedGradient,
                icon = Icons.Default.TrendingUp,
                modifier = Modifier.weight(1f)
            )
        }

        // Second row of KPIs (Advanced metrics - Requirement 7)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = getString(R.string.avg_profit),
                value = "${formatSyp(avgProfitPerTrade, activeFiat)} $activeFiat",
                valueColor = GreenColor,
                gradient = BlueGradient,
                icon = Icons.Default.Star,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = getString(R.string.roi_label),
                value = String.format(Locale.US, "%.2f%%", roi),
                valueColor = GoldColor,
                gradient = GoldGradient,
                icon = Icons.Default.ShowChart,
                modifier = Modifier.weight(1f)
            )
        }

        // Third row: best vs worst days
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "${getString(R.string.best_day)} ($bestDayStr)",
                value = "${formatSyp(bestDayVal, activeFiat)} $activeFiat",
                valueColor = GreenColor,
                gradient = GreenGradient,
                icon = Icons.Default.ThumbUp,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "${getString(R.string.worst_day)} ($worstDayStr)",
                value = "${formatSyp(worstDayVal, activeFiat)} $activeFiat",
                valueColor = RedColor,
                gradient = RedGradient,
                icon = Icons.Default.ThumbDown,
                modifier = Modifier.weight(1f)
            )
        }

        // Calendar Grid Layout
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val daysOfWeekAr = listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysOfWeekAr.forEach { d ->
                        Text(
                            text = d,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = TextSecondaryColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val totalCells = startOffset + maxDays
                val allCells = (1..startOffset).map { -1 } + (1..maxDays).toList()
                val weeks = allCells.chunked(7)

                weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (dayNum in week) {
                            if (dayNum != -1) {
                                val cellDate = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, dayNum)
                                val dayTrades = monthlyTrades.filter { it.date == cellDate }
                                val isWorkDay = dayTrades.isNotEmpty()
                                val dayProfit = dayTrades.filter { it.type == "SELL" }.sumOf { it.profitSYP }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isWorkDay) GoldColor.copy(alpha = 0.15f) else BgColor)
                                        .border(
                                            width = 1.dp,
                                            color = if (isWorkDay) GoldColor.copy(alpha = 0.4f) else BgColor,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (isWorkDay) {
                                                selectedDayDetails = Pair(cellDate, dayTrades)
                                            } else {
                                                Toast.makeText(context, "لا صفقات مسجلة للفحص اليوم $dayNum", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            color = if (isWorkDay) GoldColor else TextColor,
                                            fontSize = 13.sp,
                                            fontWeight = if (isWorkDay) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isWorkDay) {
                                            if (dayProfit != 0.0) {
                                                Text(
                                                    text = formatCompactSyp(dayProfit),
                                                    color = if (dayProfit >= 0.0) GreenColor else RedColor,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            } else {
                                                if (dayTrades.any { it.type == "BUY" }) {
                                                    Text("شراء📥", color = GreenColor, fontSize = 8.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                        if (week.size < 7) {
                            for (i in 0 until (7 - week.size)) {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }

        // Daily profits trend line graph below (Requirement 4)
        Text(
            text = "تدرج الربح الصافي اليومي للمبيعات (ل.س)",
            color = TextColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Plotting profit graph embedded in a custom design Card - Rule 9
        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                MonthlyProfitChart(
                    monthlyTrades = monthlyTrades,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Day Details Dialog popup
    if (selectedDayDetails != null) {
        val (dayPicked, tradesList) = selectedDayDetails!!
        Dialog(onDismissRequest = { selectedDayDetails = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${getString(R.string.detailed_day_trades)}: $dayPicked",
                        color = TextColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = BorderColor)

                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(tradesList) { trade ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BgColor, shape = RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (trade.type == "BUY") "شراء / BUY" else "بيع / SELL",
                                            color = if (trade.type == "BUY") GreenColor else RedColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text("${trade.amount} ${trade.cryptoCurrency} @ ${trade.rate}", color = TextSecondaryColor, fontSize = 11.sp)
                                    }
                                    if (trade.type == "SELL") {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("الربح", color = TextSecondaryColor, fontSize = 10.sp)
                                            Text("+${formatSyp(trade.profitSYP, "SYP")}", color = GreenColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { selectedDayDetails = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Text(getString(R.string.close), color = TextColor)
                    }
                }
            }
        }
    }
}

// MPAndroidChart lines wrapper in Dark slate colors (Requirement 4)
@Composable
fun MonthlyProfitChart(monthlyTrades: List<Trade>, modifier: Modifier = Modifier) {
    val dailyProfits = monthlyTrades.filter { it.type == "SELL" }
        .groupBy { it.date }
        .mapValues { (_, dayList) -> dayList.sumOf { it.profitSYP } }
        .toList()
        .sortedBy { it.first }

    if (dailyProfits.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("لا تتوفر أرباح مسجلة هذا الشهر للرسم.", color = TextSecondaryColor, fontSize = 12.sp)
        }
        return
    }

    val entries = ArrayList<Entry>()
    dailyProfits.forEachIndexed { index, pair ->
        entries.add(Entry(index.toFloat(), pair.second.toFloat()))
    }

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                legend.textColor = AndroidColor.WHITE
                legend.textSize = 10f

                xAxis.apply {
                    textColor = AndroidColor.LTGRAY
                    gridColor = AndroidColor.DKGRAY
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawLabels(true)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val idx = value.toInt()
                            if (idx >= 0 && idx < dailyProfits.size) {
                                val fullDate = dailyProfits[idx].first
                                return if (fullDate.length >= 10) fullDate.substring(8) else fullDate
                            }
                            return ""
                        }
                    }
                }

                axisLeft.apply {
                    textColor = AndroidColor.LTGRAY
                    gridColor = AndroidColor.DKGRAY
                    setDrawGridLines(true)
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "أرباح يومية / Profits").apply {
                    color = AndroidColor.YELLOW
                    setCircleColor(AndroidColor.YELLOW)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    valueTextColor = AndroidColor.WHITE
                    setDrawValues(false)
                }
                chart.data = LineData(dataSet)
                chart.invalidate()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(CardColor, shape = RoundedCornerShape(12.dp))
            .padding(10.dp)
    )
}

// --- KPI Card Widget with Optional Subtitle/Row (Requirement 5) ---
@Composable
fun KpiCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    gradient: Brush = GoldGradient,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    extraRow: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(gradient, shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (extraRow != null) {
                extraRow()
            }
        }
    }
}

// --- Settings Screen (Language + Multi-Currency Balance Custom Editor - Requirement 1 & 2) ---
@Composable
fun SettingsScreen(viewModel: P2PViewModel, getString: (Int) -> String) {
    val context = LocalContext.current
    val langState by viewModel.language.collectAsState()
    val balanceOpt by viewModel.balance.collectAsState()
    val balance = balanceOpt ?: Balance()

    var showBalanceEditor by remember { mutableStateOf(false) }

    // Backup states
    var backupCodeToImport by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionHeader(
            title = getString(R.string.settings),
            icon = Icons.Default.Settings,
            gradient = GoldGradient
        )

        // Language Section (Requirement 1)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = getString(R.string.app_language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldColor
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.setLanguage("ar") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (langState == "ar") GoldColor else BgColor,
                            contentColor = if (langState == "ar") Color.Black else TextSecondaryColor
                        ),
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = if (langState == "ar") null else BorderStroke(1.dp, BorderColor)
                    ) {
                        Text(getString(R.string.arabic), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.setLanguage("en") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (langState == "en") GoldColor else BgColor,
                            contentColor = if (langState == "en") Color.Black else TextSecondaryColor
                        ),
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = if (langState == "en") null else BorderStroke(1.dp, BorderColor)
                    ) {
                        Text(getString(R.string.english), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Multi-currency balance editor button (Requirement 2 & 5 reorder)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "التحكم اليدوي ورأس المال",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldColor
                )
                Text(
                    text = "قم بتعديل رأس المال المبدئي والأرصدة لجميع العملات الرقمية والمحلية يدوياً في أي وقت.",
                    color = TextSecondaryColor,
                    style = MaterialTheme.typography.labelMedium
                )
                Button(
                    onClick = { showBalanceEditor = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(GoldGradient, shape = RoundedCornerShape(14.dp))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getString(R.string.manual_balance_title), fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        // Backup & sharing
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = getString(R.string.backup_share),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldColor
                )

                Button(
                    onClick = { viewModel.exportBackup(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = GoldColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getString(R.string.export_json))
                }

                Button(
                    onClick = { viewModel.exportCSVReport(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextColor),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = GoldColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getString(R.string.export_csv))
                }

                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = getString(R.string.import_json),
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                    style = MaterialTheme.typography.labelMedium
                )

                OutlinedTextField(
                    value = backupCodeToImport,
                    onValueChange = { backupCodeToImport = it },
                    placeholder = { Text(getString(R.string.import_paste_hint), fontSize = 11.sp) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldColor,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        unfocusedLabelColor = TextSecondaryColor
                    ),
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = TextColor)
                )

                Button(
                    onClick = {
                        if (backupCodeToImport.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء كُتابة نص النسخة الاحتياطية أولاً", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.importBackup(backupCodeToImport, context)
                            backupCodeToImport = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(GoldGradient, shape = RoundedCornerShape(14.dp))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تنفيذ الاستيراد", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        // Clean-slate reset app
        Button(
            onClick = {
                viewModel.clearAllData()
                Toast.makeText(context, "تم مسح كافة البيانات وعودة الأرصدة للصفر.", Toast.LENGTH_LONG).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = RedColor, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("wipe_all_data")
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(getString(R.string.wipe_all_data), fontWeight = FontWeight.Bold)
        }
    }

    // Dynamic Multi-Currency Balance Editor Dialog (Requirement 2 & 5)
    if (showBalanceEditor) {
        var uUSDT by remember { mutableStateOf(balance.balanceUSDT.toString()) }
        var uUSDC by remember { mutableStateOf(balance.balanceUSDC.toString()) }
        var uBTC by remember { mutableStateOf(balance.balanceBTC.toString()) }
        var uETH by remember { mutableStateOf(balance.balanceETH.toString()) }
        var uSYP by remember { mutableStateOf(balance.balanceSYP.toString()) }
        var uUSD by remember { mutableStateOf(balance.balanceUSD.toString()) }
        var uTRY by remember { mutableStateOf(balance.balanceTRY.toString()) }
        var uEUR by remember { mutableStateOf(balance.balanceEUR.toString()) }

        Dialog(onDismissRequest = { showBalanceEditor = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = getString(R.string.manual_balance_title),
                        fontWeight = FontWeight.Bold,
                        color = TextColor,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("أرصدة العملات الرقمية / Crypto", color = GoldColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uUSDT,
                            onValueChange = { uUSDT = it },
                            label = { Text("USDT") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldColor,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextColor,
                                unfocusedTextColor = TextColor,
                                focusedLabelColor = GoldColor,
                                unfocusedLabelColor = TextSecondaryColor
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uUSDC,
                            onValueChange = { uUSDC = it },
                            label = { Text("USDC") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldColor,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextColor,
                                unfocusedTextColor = TextColor,
                                focusedLabelColor = GoldColor,
                                unfocusedLabelColor = TextSecondaryColor
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uBTC,
                            onValueChange = { uBTC = it },
                            label = { Text("BTC") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldColor,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextColor,
                                unfocusedTextColor = TextColor,
                                focusedLabelColor = GoldColor,
                                unfocusedLabelColor = TextSecondaryColor
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uETH,
                            onValueChange = { uETH = it },
                            label = { Text("ETH") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldColor,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextColor,
                                unfocusedTextColor = TextColor,
                                focusedLabelColor = GoldColor,
                                unfocusedLabelColor = TextSecondaryColor
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("أرصدة العملات المحلية / Fiat", color = GoldColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uSYP,
                            onValueChange = { uSYP = it },
                            label = { Text("SYP") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldColor,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextColor,
                                unfocusedTextColor = TextColor,
                                focusedLabelColor = GoldColor,
                                unfocusedLabelColor = TextSecondaryColor
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uUSD,
                            onValueChange = { uUSD = it },
                            label = { Text("USD") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldColor,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextColor,
                                unfocusedTextColor = TextColor,
                                focusedLabelColor = GoldColor,
                                unfocusedLabelColor = TextSecondaryColor
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uTRY,
                            onValueChange = { uTRY = it },
                            label = { Text("TRY") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldColor,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextColor,
                                unfocusedTextColor = TextColor,
                                focusedLabelColor = GoldColor,
                                unfocusedLabelColor = TextSecondaryColor
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uEUR,
                            onValueChange = { uEUR = it },
                            label = { Text("EUR") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldColor,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextColor,
                                unfocusedTextColor = TextColor,
                                focusedLabelColor = GoldColor,
                                unfocusedLabelColor = TextSecondaryColor
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.updateBalances(
                                syp = uSYP.toDoubleOrNull() ?: 0.0,
                                usdt = uUSDT.toDoubleOrNull() ?: 0.0,
                                usd = uUSD.toDoubleOrNull() ?: 0.0,
                                tryVal = uTRY.toDoubleOrNull() ?: 0.0,
                                eur = uEUR.toDoubleOrNull() ?: 0.0,
                                usdc = uUSDC.toDoubleOrNull() ?: 0.0,
                                btc = uBTC.toDoubleOrNull() ?: 0.0,
                                eth = uETH.toDoubleOrNull() ?: 0.0
                            )
                            showBalanceEditor = false
                            Toast.makeText(context, "تم حفظ رأس المال والعملات المعدلة يدوياً بنجاح.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(GoldGradient, shape = RoundedCornerShape(14.dp))
                    ) {
                        Text(getString(R.string.save_balances_btn), fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    OutlinedButton(
                        onClick = { showBalanceEditor = false },
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text(getString(R.string.cancel_btn), color = TextColor)
                    }
                }
            }
        }
    }
}
