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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Product
import com.example.data.model.StockMovement
import com.example.data.model.StoreConfig
import com.example.ui.components.ProductThumbnail
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SaffronGold
import com.example.ui.theme.SuccessGreen
import com.example.util.IstTime

@Composable
fun InventoryScreen(
  storeConfig: StoreConfig,
  products: List<Product>,
  stockMovements: List<StockMovement>,
  onAdjustStock: (Product, String, Long, Boolean, String) -> Unit
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var searchQuery by remember { mutableStateOf("") }
  var stockFilter by remember { mutableStateOf("ALL") } // ALL, LOW, TRACKED
  var adjustingProduct by remember { mutableStateOf<Product?>(null) }
  var isPurchaseMode by remember { mutableStateOf(true) }

  val filteredProducts = remember(products, searchQuery, stockFilter) {
    products.filter { p ->
      val matchSearch = searchQuery.isBlank() ||
        p.name.contains(searchQuery, ignoreCase = true) ||
        p.categoryName.contains(searchQuery, ignoreCase = true)
      val matchFilter = when (stockFilter) {
        "LOW" -> p.trackStock && p.stockQty <= p.lowStockThreshold
        "TRACKED" -> p.trackStock
        else -> true
      }
      matchSearch && matchFilter
    }
  }

  val lowStockCount = remember(products) {
    products.count { it.active && it.trackStock && it.stockQty <= it.lowStockThreshold }
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
      Column {
        Text("Inventory & Stock Control", style = MaterialTheme.typography.headlineSmall)
        Text(
          if (storeConfig.stockTrackingEnabled) {
            "${products.count { it.trackStock }} tracked items • $lowStockCount low-stock alert(s)"
          } else {
            "Global stock auto-deduction is paused in Settings"
          },
          style = MaterialTheme.typography.bodySmall,
          color = if (lowStockCount > 0) SaffronGold else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    TabRow(selectedTabIndex = selectedTab) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Stock Levels (${products.size})") },
        modifier = Modifier.testTag("inventory_tab_levels")
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Stock Ledger (${stockMovements.size})") },
        modifier = Modifier.testTag("inventory_tab_ledger")
      )
    }

    if (selectedTab == 0) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search product or category...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("inventory_search_input")
      )

      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = stockFilter == "ALL",
          onClick = { stockFilter = "ALL" },
          label = { Text("All (${products.size})") }
        )
        FilterChip(
          selected = stockFilter == "LOW",
          onClick = { stockFilter = "LOW" },
          label = { Text("Low Stock ($lowStockCount)") },
          leadingIcon = {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
          }
        )
        FilterChip(
          selected = stockFilter == "TRACKED",
          onClick = { stockFilter = "TRACKED" },
          label = { Text("Tracked Only") }
        )
      }

      if (filteredProducts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("No matching products in inventory.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          items(filteredProducts, key = { it.id }) { prod ->
            val isLow = prod.trackStock && prod.stockQty <= prod.lowStockThreshold
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                ProductThumbnail(product = prod, size = 50.dp)
                Column(modifier = Modifier.weight(1f)) {
                  Text(prod.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  Text(
                    "${prod.categoryName} • ${IstTime.formatInr(prod.sellingPrice, storeConfig.currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(Modifier.height(4.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when {
                      !prod.trackStock -> MaterialTheme.colorScheme.onSurfaceVariant
                      prod.stockQty <= 0L -> CrimsonError
                      isLow -> SaffronGold
                      else -> SuccessGreen
                    }
                    Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                      Text(
                        text = if (prod.trackStock) {
                          "Stock: ${prod.stockQty} (Alert <= ${prod.lowStockThreshold})"
                        } else {
                          "Untracked"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                      )
                    }
                  }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                  OutlinedButton(
                    onClick = {
                      adjustingProduct = prod
                      isPurchaseMode = true
                    },
                    modifier = Modifier.testTag("add_stock_${prod.id}")
                  ) {
                    Icon(Icons.Filled.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add +")
                  }
                  OutlinedButton(
                    onClick = {
                      adjustingProduct = prod
                      isPurchaseMode = false
                    },
                    modifier = Modifier.testTag("adjust_stock_${prod.id}")
                  ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Set")
                  }
                }
              }
            }
          }
        }
      }
    } else {
      if (stockMovements.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("No stock movements recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(stockMovements, key = { it.id }) { mov ->
            val positive = mov.deltaQty >= 0L
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(mov.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  Text(
                    "${mov.type.uppercase()} • Ref: ${mov.referenceId} • By ${mov.performedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  if (mov.note.isNotBlank()) {
                    Text(mov.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  mov.createdAt?.toDate()?.time?.let { ts ->
                    Text(IstTime.formatDateTime(ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = if (positive) "+${mov.deltaQty}" else "${mov.deltaQty}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = if (positive) SuccessGreen else CrimsonError
                  )
                  Text(
                    "Bal: ${mov.resultingQty}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = EmeraldPrimary
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  adjustingProduct?.let { prod ->
    StockAdjustDialog(
      product = prod,
      isPurchaseMode = isPurchaseMode,
      onConfirm = { qty, isAbsolute, note ->
        val type = if (isPurchaseMode) "purchase" else "adjustment"
        onAdjustStock(prod, type, qty, isAbsolute, note)
        adjustingProduct = null
      },
      onDismiss = { adjustingProduct = null }
    )
  }
}

@Composable
private fun StockAdjustDialog(
  product: Product,
  isPurchaseMode: Boolean,
  onConfirm: (qty: Long, isAbsolute: Boolean, note: String) -> Unit,
  onDismiss: () -> Unit
) {
  var qtyInput by remember { mutableStateOf(if (isPurchaseMode) "10" else product.stockQty.toString()) }
  var note by remember { mutableStateOf(if (isPurchaseMode) "Restock purchase" else "Manual stock count update") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(if (isPurchaseMode) "Add Purchase Stock" else "Set Exact Stock Count")
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "${product.name} (Current Stock: ${product.stockQty})",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
          value = qtyInput,
          onValueChange = { qtyInput = it; error = null },
          label = { Text(if (isPurchaseMode) "Quantity to Add *" else "New Exact Stock Quantity *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("stock_qty_modal_input")
        )
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("Note / Reason") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
          Text(error!!, color = CrimsonError, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsed = qtyInput.trim().toLongOrNull()
          if (parsed == null || (isPurchaseMode && parsed <= 0L) || (!isPurchaseMode && parsed < 0L)) {
            error = if (isPurchaseMode) "Enter a positive quantity to add." else "Enter 0 or higher."
            return@Button
          }
          onConfirm(parsed, !isPurchaseMode, note.ifBlank { "Updated" })
        },
        modifier = Modifier.testTag("confirm_stock_adjust_button")
      ) {
        Text("Update Stock")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
