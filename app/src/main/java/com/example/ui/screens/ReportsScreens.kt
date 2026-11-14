package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Category
import com.example.data.model.Invoice
import com.example.data.model.ProductCost
import com.example.data.model.StaffAccount
import com.example.data.model.StoreConfig
import com.example.ui.components.KpiStatCard
import com.example.ui.components.PaymentMethodDonutChart
import com.example.ui.components.RevenueBarChart
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SaffronGold
import com.example.ui.theme.SuccessGreen
import com.example.util.InvoicePdfAndPrintHelper
import com.example.util.IstTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DateRangePreset(val label: String) {
  TODAY("Today"),
  YESTERDAY("Yesterday"),
  WEEK("This Week"),
  MONTH("This Month"),
  ALL("All Time")
}

@Composable
fun SalesReportsScreen(
  storeConfig: StoreConfig,
  invoices: List<Invoice>,
  categories: List<Category>,
  staffAccounts: List<StaffAccount>,
  isAdmin: Boolean
) {
  val context = LocalContext.current
  val currency = storeConfig.currencySymbol
  val now = remember { System.currentTimeMillis() }

  var startDateInput by remember { mutableStateOf(IstTime.formatDate(IstTime.monthStartMillis(now))) }
  var endDateInput by remember { mutableStateOf(IstTime.formatDate(now)) }
  var activePreset by remember { mutableStateOf(DateRangePreset.MONTH) }
  var selectedCategoryId by remember { mutableStateOf("ALL") }
  var selectedStaffId by remember { mutableStateOf("ALL") }
  var selectedTab by remember { mutableIntStateOf(0) }

  val dateError = remember(startDateInput, endDateInput) {
    val s = IstTime.parseDateStartMillis(startDateInput)
    val e = IstTime.parseDateEndMillis(endDateInput)
    if (activePreset != DateRangePreset.ALL && (s == null || e == null || s > e)) {
      "Enter valid DD-MM-YYYY start & end dates"
    } else null
  }

  val filteredInvoices = remember(invoices, startDateInput, endDateInput, activePreset, selectedStaffId) {
    val startMs = if (activePreset == DateRangePreset.ALL) Long.MIN_VALUE else (IstTime.parseDateStartMillis(startDateInput) ?: Long.MIN_VALUE)
    val endMs = if (activePreset == DateRangePreset.ALL) Long.MAX_VALUE else (IstTime.parseDateEndMillis(endDateInput) ?: Long.MAX_VALUE)
    invoices.filter { inv ->
      inv.status == "completed" &&
        inv.epochMillis in startMs..endMs &&
        (selectedStaffId == "ALL" || inv.staffId == selectedStaffId)
    }
  }

  val cancelledInPeriod = remember(invoices, startDateInput, endDateInput, activePreset) {
    val startMs = if (activePreset == DateRangePreset.ALL) Long.MIN_VALUE else (IstTime.parseDateStartMillis(startDateInput) ?: Long.MIN_VALUE)
    val endMs = if (activePreset == DateRangePreset.ALL) Long.MAX_VALUE else (IstTime.parseDateEndMillis(endDateInput) ?: Long.MAX_VALUE)
    invoices.count { it.status == "cancelled" && it.epochMillis in startMs..endMs }
  }

  // Category-filtered line items
  val scopedItems = remember(filteredInvoices, selectedCategoryId) {
    filteredInvoices.flatMap { inv ->
      inv.items.filter { selectedCategoryId == "ALL" || it.categoryId == selectedCategoryId }
    }
  }

  val totalRevenue = remember(filteredInvoices, scopedItems, selectedCategoryId) {
    if (selectedCategoryId == "ALL") filteredInvoices.sumOf { it.grandTotal }
    else scopedItems.sumOf { it.lineTotal }
  }
  val totalBills = filteredInvoices.size
  val totalQty = remember(scopedItems) { scopedItems.sumOf { it.quantity } }
  val avgBill = if (totalBills > 0) totalRevenue / totalBills else 0.0

  // Aggregations
  data class ProductSalesRow(val name: String, val category: String, val qty: Long, val revenue: Double)
  val productRows = remember(scopedItems) {
    scopedItems.groupBy { it.productId to it.productName }
      .map { (_, list) ->
        ProductSalesRow(
          name = list.first().productName,
          category = list.first().categoryName,
          qty = list.sumOf { it.quantity },
          revenue = list.sumOf { it.lineTotal }
        )
      }
      .sortedByDescending { it.revenue }
  }

  data class CategorySalesRow(val category: String, val qty: Long, val revenue: Double)
  val categoryRows = remember(scopedItems) {
    scopedItems.groupBy { it.categoryName.ifBlank { "Uncategorized" } }
      .map { (cat, list) ->
        CategorySalesRow(cat, list.sumOf { it.quantity }, list.sumOf { it.lineTotal })
      }
      .sortedByDescending { it.revenue }
  }

  data class StaffSalesRow(val staffName: String, val bills: Int, val qty: Long, val revenue: Double)
  val staffRows = remember(filteredInvoices) {
    filteredInvoices.groupBy { it.staffName }
      .map { (name, list) ->
        StaffSalesRow(
          staffName = name,
          bills = list.size,
          qty = list.sumOf { inv -> inv.items.sumOf { it.quantity } },
          revenue = list.sumOf { it.grandTotal }
        )
      }
      .sortedByDescending { it.revenue }
  }

  data class DailySalesRow(val dateKey: String, val bills: Int, val qty: Long, val revenue: Double)
  val dailyRows = remember(filteredInvoices) {
    filteredInvoices.groupBy { it.dateKey }
      .map { (date, list) ->
        DailySalesRow(
          dateKey = date,
          bills = list.size,
          qty = list.sumOf { inv -> inv.items.sumOf { it.quantity } },
          revenue = list.sumOf { it.grandTotal }
        )
      }
      .sortedByDescending { IstTime.parseDateStartMillis(it.dateKey) ?: 0L }
  }

  val paymentBreakdown = remember(filteredInvoices) {
    filteredInvoices.groupBy { it.paymentMethod }.mapValues { (_, list) -> list.sumOf { it.grandTotal } }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Sales Reports", style = MaterialTheme.typography.headlineSmall)
        Text(
          "IST (Asia/Kolkata) • DD-MM-YYYY • $cancelledInPeriod cancelled excluded",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            val rangeLabel = if (activePreset == DateRangePreset.ALL) "All Time" else "$startDateInput to $endDateInput"
            val headers = listOf("Product", "Category", "Qty", "Revenue")
            val rows = productRows.map { listOf(it.name, it.category, "${it.qty}", IstTime.formatInrExact(it.revenue, currency)) }
            val file = InvoicePdfAndPrintHelper.exportSalesReportPdf(
              context = context,
              store = storeConfig,
              reportTitle = "Sales Report",
              dateRangeLabel = rangeLabel,
              summaryLines = listOf(
                "Total Revenue" to IstTime.formatInrExact(totalRevenue, currency),
                "Total Bills" to "$totalBills",
                "Items Sold" to "$totalQty"
              ),
              tableHeaders = headers,
              tableRows = rows
            )
            InvoicePdfAndPrintHelper.shareOrSaveFile(context, file, "application/pdf", "TURKISHQ Sales Report PDF")
          },
          modifier = Modifier.testTag("export_sales_pdf_button")
        ) {
          Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("PDF")
        }
        OutlinedButton(
          onClick = {
            val csvRows = mutableListOf(listOf("Product", "Category", "Qty Sold", "Revenue (INR)"))
            productRows.forEach {
              csvRows.add(listOf(it.name, it.category, "${it.qty}", String.format(Locale.US, "%.2f", it.revenue)))
            }
            val file = InvoicePdfAndPrintHelper.exportSalesReportCsv(context, "turkishq_sales_report", csvRows)
            InvoicePdfAndPrintHelper.shareOrSaveFile(context, file, "text/csv", "TURKISHQ Sales Report CSV")
          },
          modifier = Modifier.testTag("export_sales_csv_button")
        ) {
          Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("CSV")
        }
      }
    }

    // Presets
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      DateRangePreset.entries.forEach { preset ->
        FilterChip(
          selected = activePreset == preset,
          onClick = {
            activePreset = preset
            val cur = System.currentTimeMillis()
            when (preset) {
              DateRangePreset.TODAY -> {
                val today = IstTime.formatDate(cur)
                startDateInput = today; endDateInput = today
              }
              DateRangePreset.YESTERDAY -> {
                val (ys, _) = IstTime.yesterdayStartEnd(cur)
                val yd = IstTime.formatDate(ys)
                startDateInput = yd; endDateInput = yd
              }
              DateRangePreset.WEEK -> {
                startDateInput = IstTime.formatDate(IstTime.weekStartMillis(cur))
                endDateInput = IstTime.formatDate(cur)
              }
              DateRangePreset.MONTH -> {
                startDateInput = IstTime.formatDate(IstTime.monthStartMillis(cur))
                endDateInput = IstTime.formatDate(cur)
              }
              DateRangePreset.ALL -> Unit
            }
          },
          label = { Text(preset.label) }
        )
      }
    }

    if (activePreset != DateRangePreset.ALL) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = startDateInput,
          onValueChange = { startDateInput = it },
          label = { Text("From (DD-MM-YYYY)") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("report_start_date")
        )
        OutlinedTextField(
          value = endDateInput,
          onValueChange = { endDateInput = it },
          label = { Text("To (DD-MM-YYYY)") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("report_end_date")
        )
      }
      if (dateError != null) {
        Text(dateError, color = CrimsonError, style = MaterialTheme.typography.bodySmall)
      }
    }

    // Category filter chips
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChip(
        selected = selectedCategoryId == "ALL",
        onClick = { selectedCategoryId = "ALL" },
        label = { Text("All Categories") }
      )
      categories.forEach { cat ->
        FilterChip(
          selected = selectedCategoryId == cat.id,
          onClick = { selectedCategoryId = cat.id },
          label = { Text(cat.name) }
        )
      }
    }

    if (isAdmin && staffAccounts.isNotEmpty()) {
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = selectedStaffId == "ALL",
          onClick = { selectedStaffId = "ALL" },
          label = { Text("All Staff") }
        )
        FilterChip(
          selected = selectedStaffId == "master_admin",
          onClick = { selectedStaffId = "master_admin" },
          label = { Text("Master Admin") }
        )
        staffAccounts.forEach { st ->
          FilterChip(
            selected = selectedStaffId == st.id,
            onClick = { selectedStaffId = st.id },
            label = { Text(st.displayName) }
          )
        }
      }
    }

    // KPI Row
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      KpiStatCard(
        title = "Total Sales",
        value = IstTime.formatInr(totalRevenue, currency),
        subtitle = "$totalBills completed bills",
        icon = Icons.Filled.CurrencyRupee,
        accentColor = EmeraldPrimary,
        modifier = Modifier.weight(1f)
      )
      KpiStatCard(
        title = "Qty Sold",
        value = "$totalQty",
        subtitle = "Avg ${IstTime.formatInr(avgBill, currency)} / bill",
        icon = Icons.Filled.ShoppingBag,
        accentColor = SaffronGold,
        modifier = Modifier.weight(1f)
      )
    }

    ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
      listOf("Product-wise", "Category-wise", "Staff-wise", "Daily", "Payments").forEachIndexed { idx, title ->
        Tab(
          selected = selectedTab == idx,
          onClick = { selectedTab = idx },
          text = { Text(title) }
        )
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      when (selectedTab) {
        0 -> {
          if (productRows.isEmpty()) {
            item { EmptyReportPlaceholder("No product sales for this range.") }
          } else {
            items(productRows) { row ->
              ReportMetricCard(
                title = row.name,
                subtitle = "${row.category} • Qty Sold: ${row.qty}",
                trailingValue = IstTime.formatInrExact(row.revenue, currency)
              )
            }
          }
        }
        1 -> {
          if (categoryRows.isEmpty()) {
            item { EmptyReportPlaceholder("No category sales for this range.") }
          } else {
            items(categoryRows) { row ->
              ReportMetricCard(
                title = row.category,
                subtitle = "Qty Sold: ${row.qty}",
                trailingValue = IstTime.formatInrExact(row.revenue, currency)
              )
            }
          }
        }
        2 -> {
          if (staffRows.isEmpty()) {
            item { EmptyReportPlaceholder("No staff sales for this range.") }
          } else {
            items(staffRows) { row ->
              ReportMetricCard(
                title = row.staffName,
                subtitle = "${row.bills} bills • ${row.qty} items sold",
                trailingValue = IstTime.formatInrExact(row.revenue, currency)
              )
            }
          }
        }
        3 -> {
          if (dailyRows.isEmpty()) {
            item { EmptyReportPlaceholder("No daily sales for this range.") }
          } else {
            item {
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Text("Daily Revenue Trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  RevenueBarChart(
                    series = dailyRows.take(7).reversed().map { it.dateKey.take(5) to it.revenue },
                    currencySymbol = currency
                  )
                }
              }
            }
            items(dailyRows) { row ->
              ReportMetricCard(
                title = row.dateKey,
                subtitle = "${row.bills} bills • ${row.qty} items sold",
                trailingValue = IstTime.formatInrExact(row.revenue, currency)
              )
            }
          }
        }
        4 -> {
          item {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Payment Method Breakdown", style = MaterialTheme.typography.titleMedium)
                PaymentMethodDonutChart(breakdown = paymentBreakdown, currencySymbol = currency)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ProfitReportsScreen(
  storeConfig: StoreConfig,
  invoices: List<Invoice>,
  productCosts: Map<String, ProductCost>,
  categories: List<Category>
) {
  val context = LocalContext.current
  val currency = storeConfig.currencySymbol
  val now = remember { System.currentTimeMillis() }

  var startDateInput by remember { mutableStateOf(IstTime.formatDate(IstTime.monthStartMillis(now))) }
  var endDateInput by remember { mutableStateOf(IstTime.formatDate(now)) }
  var activePreset by remember { mutableStateOf(DateRangePreset.MONTH) }
  var selectedTab by remember { mutableIntStateOf(0) }

  val filteredInvoices = remember(invoices, startDateInput, endDateInput, activePreset) {
    val startMs = if (activePreset == DateRangePreset.ALL) Long.MIN_VALUE else (IstTime.parseDateStartMillis(startDateInput) ?: Long.MIN_VALUE)
    val endMs = if (activePreset == DateRangePreset.ALL) Long.MAX_VALUE else (IstTime.parseDateEndMillis(endDateInput) ?: Long.MAX_VALUE)
    invoices.filter { it.status == "completed" && it.epochMillis in startMs..endMs }
  }

  data class LineProfit(
    val productId: String,
    val productName: String,
    val categoryName: String,
    val dateKey: String,
    val monthKey: String,
    val qty: Long,
    val sellUnitPrice: Double,
    val costUnitPrice: Double,
    val netRevenue: Double,
    val totalCost: Double,
    val profit: Double
  )

  val monthFormatter = remember {
    SimpleDateFormat("MM-yyyy", Locale.US).apply { timeZone = IstTime.timeZone }
  }

  val allLines = remember(filteredInvoices, productCosts) {
    filteredInvoices.flatMap { inv ->
      val discRatio = if (inv.subtotal > 0.0) ((inv.subtotal - inv.discountAmount).coerceAtLeast(0.0) / inv.subtotal) else 1.0
      val mKey = monthFormatter.format(Date(inv.epochMillis))
      inv.items.map { item ->
        val unitCost = productCosts[item.productId]?.purchaseCost ?: 0.0
        val netLineRev = item.lineTotal * discRatio
        val lineCost = unitCost * item.quantity
        LineProfit(
          productId = item.productId,
          productName = item.productName,
          categoryName = item.categoryName.ifBlank { "Uncategorized" },
          dateKey = inv.dateKey,
          monthKey = mKey,
          qty = item.quantity,
          sellUnitPrice = item.unitPrice,
          costUnitPrice = unitCost,
          netRevenue = netLineRev,
          totalCost = lineCost,
          profit = netLineRev - lineCost
        )
      }
    }
  }

  val totalNetRevenue = remember(allLines) { allLines.sumOf { it.netRevenue } }
  val totalCogs = remember(allLines) { allLines.sumOf { it.totalCost } }
  val netProfit = totalNetRevenue - totalCogs
  val marginPct = if (totalNetRevenue > 0.0) (netProfit / totalNetRevenue) * 100.0 else 0.0

  data class ProdProfitRow(
    val name: String,
    val sellPrice: Double,
    val costPrice: Double,
    val qty: Long,
    val revenue: Double,
    val cost: Double,
    val profit: Double
  )
  val productProfitRows = remember(allLines) {
    allLines.groupBy { it.productId to it.productName }
      .map { (_, list) ->
        val q = list.sumOf { it.qty }
        val r = list.sumOf { it.netRevenue }
        val c = list.sumOf { it.totalCost }
        ProdProfitRow(
          name = list.first().productName,
          sellPrice = list.last().sellUnitPrice,
          costPrice = list.last().costUnitPrice,
          qty = q,
          revenue = r,
          cost = c,
          profit = r - c
        )
      }
      .sortedByDescending { it.profit }
  }

  data class GroupProfitRow(val label: String, val revenue: Double, val cost: Double, val profit: Double)
  val categoryRows = remember(allLines) {
    allLines.groupBy { it.categoryName }
      .map { (cat, list) ->
        val r = list.sumOf { it.netRevenue }
        val c = list.sumOf { it.totalCost }
        GroupProfitRow(cat, r, c, r - c)
      }
      .sortedByDescending { it.profit }
  }
  val dailyRows = remember(allLines) {
    allLines.groupBy { it.dateKey }
      .map { (date, list) ->
        val r = list.sumOf { it.netRevenue }
        val c = list.sumOf { it.totalCost }
        GroupProfitRow(date, r, c, r - c)
      }
      .sortedByDescending { IstTime.parseDateStartMillis(it.label) ?: 0L }
  }
  val monthlyRows = remember(allLines) {
    allLines.groupBy { it.monthKey }
      .map { (m, list) ->
        val r = list.sumOf { it.netRevenue }
        val c = list.sumOf { it.totalCost }
        GroupProfitRow(m, r, c, r - c)
      }
      .sortedByDescending { it.label }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Profit & Margin Reports", style = MaterialTheme.typography.headlineSmall)
        Text(
          "Confidential COGS vs Revenue (excluding tax)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            val rangeLabel = if (activePreset == DateRangePreset.ALL) "All Time" else "$startDateInput to $endDateInput"
            val file = InvoicePdfAndPrintHelper.exportSalesReportPdf(
              context = context,
              store = storeConfig,
              reportTitle = "Profit Report",
              dateRangeLabel = rangeLabel,
              summaryLines = listOf(
                "Net Sales Revenue" to IstTime.formatInrExact(totalNetRevenue, currency),
                "Total Cost (COGS)" to IstTime.formatInrExact(totalCogs, currency),
                "Net Profit" to IstTime.formatInrExact(netProfit, currency),
                "Margin" to "${String.format(Locale.US, "%.1f", marginPct)}%"
              ),
              tableHeaders = listOf("Product", "Qty", "COGS", "Profit"),
              tableRows = productProfitRows.map {
                listOf(it.name, "${it.qty}", IstTime.formatInrExact(it.cost, currency), IstTime.formatInrExact(it.profit, currency))
              }
            )
            InvoicePdfAndPrintHelper.shareOrSaveFile(context, file, "application/pdf", "TURKISHQ Profit Report PDF")
          },
          modifier = Modifier.testTag("export_profit_pdf_button")
        ) {
          Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("PDF")
        }
        OutlinedButton(
          onClick = {
            val rows = mutableListOf(listOf("Product", "Sell Price", "Cost Price", "Qty", "Revenue", "COGS", "Net Profit"))
            productProfitRows.forEach {
              rows.add(
                listOf(
                  it.name,
                  String.format(Locale.US, "%.2f", it.sellPrice),
                  String.format(Locale.US, "%.2f", it.costPrice),
                  "${it.qty}",
                  String.format(Locale.US, "%.2f", it.revenue),
                  String.format(Locale.US, "%.2f", it.cost),
                  String.format(Locale.US, "%.2f", it.profit)
                )
              )
            }
            val file = InvoicePdfAndPrintHelper.exportSalesReportCsv(context, "turkishq_profit_report", rows)
            InvoicePdfAndPrintHelper.shareOrSaveFile(context, file, "text/csv", "TURKISHQ Profit Report CSV")
          },
          modifier = Modifier.testTag("export_profit_csv_button")
        ) {
          Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("CSV")
        }
      }
    }

    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      DateRangePreset.entries.forEach { preset ->
        FilterChip(
          selected = activePreset == preset,
          onClick = {
            activePreset = preset
            val cur = System.currentTimeMillis()
            when (preset) {
              DateRangePreset.TODAY -> {
                val t = IstTime.formatDate(cur)
                startDateInput = t; endDateInput = t
              }
              DateRangePreset.YESTERDAY -> {
                val (ys, _) = IstTime.yesterdayStartEnd(cur)
                val y = IstTime.formatDate(ys)
                startDateInput = y; endDateInput = y
              }
              DateRangePreset.WEEK -> {
                startDateInput = IstTime.formatDate(IstTime.weekStartMillis(cur))
                endDateInput = IstTime.formatDate(cur)
              }
              DateRangePreset.MONTH -> {
                startDateInput = IstTime.formatDate(IstTime.monthStartMillis(cur))
                endDateInput = IstTime.formatDate(cur)
              }
              DateRangePreset.ALL -> Unit
            }
          },
          label = { Text(preset.label) }
        )
      }
    }

    if (activePreset != DateRangePreset.ALL) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = startDateInput,
          onValueChange = { startDateInput = it },
          label = { Text("From (DD-MM-YYYY)") },
          singleLine = true,
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = endDateInput,
          onValueChange = { endDateInput = it },
          label = { Text("To (DD-MM-YYYY)") },
          singleLine = true,
          modifier = Modifier.weight(1f)
        )
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      KpiStatCard(
        title = "Net Profit",
        value = IstTime.formatInr(netProfit, currency),
        subtitle = "Margin ${String.format(Locale.US, "%.1f", marginPct)}%",
        icon = Icons.Filled.TrendingUp,
        accentColor = if (netProfit >= 0) SuccessGreen else CrimsonError,
        modifier = Modifier.weight(1f)
      )
      KpiStatCard(
        title = "Revenue / COGS",
        value = IstTime.formatInr(totalNetRevenue, currency),
        subtitle = "COGS: ${IstTime.formatInr(totalCogs, currency)}",
        icon = Icons.Filled.CurrencyRupee,
        accentColor = SaffronGold,
        modifier = Modifier.weight(1f)
      )
    }

    ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
      listOf("Product Profit", "Category Profit", "Daily Profit", "Monthly Profit").forEachIndexed { i, t ->
        Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) })
      }
    }

    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      when (selectedTab) {
        0 -> {
          if (productProfitRows.isEmpty()) {
            item { EmptyReportPlaceholder("No product profit entries for this period.") }
          } else {
            items(productProfitRows) { r ->
              val unitProfit = r.sellPrice - r.costPrice
              ReportMetricCard(
                title = r.name,
                subtitle = "Sell ${IstTime.formatInr(r.sellPrice, currency)} • Cost ${IstTime.formatInr(r.costPrice, currency)} • Unit +${IstTime.formatInr(unitProfit, currency)} • Qty ${r.qty}",
                trailingValue = IstTime.formatInrExact(r.profit, currency),
                valueColor = if (r.profit >= 0) SuccessGreen else CrimsonError
              )
            }
          }
        }
        1 -> {
          if (categoryRows.isEmpty()) {
            item { EmptyReportPlaceholder("No category profit data for this period.") }
          } else {
            items(categoryRows) { r ->
              ReportMetricCard(
                title = r.label,
                subtitle = "Rev ${IstTime.formatInr(r.revenue, currency)} • COGS ${IstTime.formatInr(r.cost, currency)}",
                trailingValue = IstTime.formatInrExact(r.profit, currency),
                valueColor = if (r.profit >= 0) SuccessGreen else CrimsonError
              )
            }
          }
        }
        2 -> {
          if (dailyRows.isEmpty()) {
            item { EmptyReportPlaceholder("No daily profit data for this period.") }
          } else {
            items(dailyRows) { r ->
              ReportMetricCard(
                title = r.label,
                subtitle = "Rev ${IstTime.formatInr(r.revenue, currency)} • COGS ${IstTime.formatInr(r.cost, currency)}",
                trailingValue = IstTime.formatInrExact(r.profit, currency),
                valueColor = if (r.profit >= 0) SuccessGreen else CrimsonError
              )
            }
          }
        }
        3 -> {
          if (monthlyRows.isEmpty()) {
            item { EmptyReportPlaceholder("No monthly profit data for this period.") }
          } else {
            items(monthlyRows) { r ->
              ReportMetricCard(
                title = "Month ${r.label}",
                subtitle = "Rev ${IstTime.formatInr(r.revenue, currency)} • COGS ${IstTime.formatInr(r.cost, currency)}",
                trailingValue = IstTime.formatInrExact(r.profit, currency),
                valueColor = if (r.profit >= 0) SuccessGreen else CrimsonError
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ReportMetricCard(
  title: String,
  subtitle: String,
  trailingValue: String,
  valueColor: androidx.compose.ui.graphics.Color = EmeraldPrimary
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(Modifier.width(8.dp))
      Text(
        text = trailingValue,
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
        fontWeight = FontWeight.Bold,
        color = valueColor
      )
    }
  }
}

@Composable
private fun EmptyReportPlaceholder(message: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 40.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
