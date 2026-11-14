package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Invoice
import com.example.data.model.OperatorSession
import com.example.data.model.PosDestination
import com.example.data.model.Product
import com.example.data.model.StoreConfig
import com.example.ui.components.KpiStatCard
import com.example.ui.components.PaymentMethodDonutChart
import com.example.ui.components.RevenueBarChart
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SaffronGold
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.util.IstTime
import com.example.viewmodel.DashboardStats

@Composable
fun DashboardScreen(
  storeConfig: StoreConfig,
  session: OperatorSession,
  stats: DashboardStats,
  products: List<Product>,
  recentInvoices: List<Invoice>,
  onNavigate: (PosDestination) -> Unit,
  onOpenInvoice: (Invoice) -> Unit
) {
  val currency = storeConfig.currencySymbol
  val lowStockProducts = remember(products) {
    products.filter { it.active && it.trackStock && it.stockQty <= it.lowStockThreshold }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Quick Header Banner
    Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = EmeraldDeep)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            "Welcome, ${session.actorName}",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
          )
          Text(
            "Date: ${IstTime.todayDateKey()} (Asia/Kolkata) • ${if (session.isAdmin) "Master Admin" else "Staff POS"}",
            style = MaterialTheme.typography.bodySmall,
            color = SaffronGold
          )
        }
        Button(
          onClick = { onNavigate(PosDestination.NEW_BILL) },
          colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SaffronGold),
          modifier = Modifier.testTag("dashboard_new_bill_button")
        ) {
          Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("New Bill")
        }
      }
    }

    // Responsive KPI Grid
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val cols = if (maxWidth >= 720.dp) 4 else 2
      val kpis = listOf(
        Triple("Total Sales", IstTime.formatInr(stats.totalSalesAmount, currency), "${stats.totalBillsCount} completed bills") to Pair(Icons.Filled.CurrencyRupee, EmeraldPrimary),
        Triple("Today's Sales", IstTime.formatInr(stats.todaySales, currency), "Yesterday: ${IstTime.formatInr(stats.yesterdaySales, currency)}") to Pair(Icons.Filled.TrendingUp, SuccessGreen),
        Triple("This Week", IstTime.formatInr(stats.thisWeekSales, currency), "This Month: ${IstTime.formatInr(stats.thisMonthSales, currency)}") to Pair(Icons.Filled.Assessment, SaffronGold),
        Triple("Avg Invoice", IstTime.formatInr(stats.averageInvoiceValue, currency), "${stats.totalQuantitySold} units (${stats.totalDistinctProductsSold} SKUs)") to Pair(Icons.Filled.ReceiptLong, Color(0xFF2B6CB0))
      )
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        kpis.chunked(cols).forEach { rowItems ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowItems.forEach { (info, iconPair) ->
              KpiStatCard(
                title = info.first,
                value = info.second,
                subtitle = info.third,
                icon = iconPair.first,
                accentColor = iconPair.second,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }
    }

    // Secondary summary strip
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      KpiStatCard(
        title = "Products Sold Qty",
        value = "${stats.totalQuantitySold}",
        subtitle = "${stats.totalDistinctProductsSold} distinct products",
        icon = Icons.Filled.ShoppingBag,
        accentColor = EmeraldPrimary,
        modifier = Modifier.weight(1f)
      )
      KpiStatCard(
        title = "Cancelled Invoices",
        value = "${stats.cancelledInvoicesCount}",
        subtitle = "Excluded from sales totals",
        icon = Icons.Filled.Cancel,
        accentColor = CrimsonError,
        modifier = Modifier.weight(1f)
      )
    }

    // Low stock alert for Master Admin
    if (session.isAdmin && lowStockProducts.isNotEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(14.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber)
            Column {
              Text("${lowStockProducts.size} Low-Stock Alert(s)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
              Text(
                lowStockProducts.take(3).joinToString { "${it.name} (${it.stockQty})" },
                style = MaterialTheme.typography.bodySmall
              )
            }
          }
          FilledTonalButton(onClick = { onNavigate(PosDestination.INVENTORY) }) {
            Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Stock")
          }
        }
      }
    }

    // Charts Section (responsive row on wide, column on narrow)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val wide = maxWidth >= 780.dp
      if (wide) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          RevenueChartCard(stats, currency, Modifier.weight(1.3f))
          PaymentChartCard(stats, currency, Modifier.weight(1f))
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          RevenueChartCard(stats, currency, Modifier.fillMaxWidth())
          PaymentChartCard(stats, currency, Modifier.fillMaxWidth())
        }
      }
    }

    // Top Selling Products
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Top Performing Products", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (stats.topProducts.isEmpty()) {
          Text(
            "No completed sales recorded yet. Create your first invoice from New Bill.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          val maxRev = stats.topProducts.maxOf { it.second }.coerceAtLeast(1.0)
          stats.topProducts.forEach { (prodName, rev) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(prodName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                  IstTime.formatInr(rev, currency),
                  style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                  color = EmeraldPrimary
                )
              }
              LinearProgressIndicator(
                progress = { (rev / maxRev).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(7.dp)
                  .clip(RoundedCornerShape(4.dp)),
                color = EmeraldPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
              )
            }
          }
        }
      }
    }

    // Recent Bills
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Recent Invoices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          FilledTonalButton(
            onClick = {
              onNavigate(if (session.isAdmin) PosDestination.BILL_HISTORY else PosDestination.MY_SALES)
            }
          ) {
            Text("View All")
          }
        }
        if (recentInvoices.isEmpty()) {
          Text(
            "No invoices generated yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          recentInvoices.take(5).forEachIndexed { index, inv ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Surface(
              onClick = { onOpenInvoice(inv) },
              color = Color.Transparent,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(inv.invoiceNumber, style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily), color = EmeraldPrimary)
                  Text(
                    "${IstTime.formatDateTime(inv.epochMillis)} • ${inv.staffName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    IstTime.formatInrExact(inv.grandTotal, currency),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = if (inv.status == "cancelled") CrimsonError else MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    if (inv.status == "cancelled") "CANCELLED" else inv.paymentMethod,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inv.status == "cancelled") CrimsonError else MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun RevenueChartCard(stats: DashboardStats, currency: String, modifier: Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("7-Day Sales Trend (IST)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      RevenueBarChart(series = stats.last7DaysSeries, currencySymbol = currency)
    }
  }
}

@Composable
private fun PaymentChartCard(stats: DashboardStats, currency: String, modifier: Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Payment Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      PaymentMethodDonutChart(breakdown = stats.paymentBreakdown, currencySymbol = currency)
    }
  }
}
