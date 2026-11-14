package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Category
import com.example.data.model.PaymentMethod
import com.example.data.model.Product
import com.example.data.model.StoreConfig
import com.example.ui.components.ProductThumbnail
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SaffronGold
import com.example.ui.theme.WarningAmber
import com.example.util.IstTime
import com.example.viewmodel.PosBillingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingPosScreen(
  storeConfig: StoreConfig,
  categories: List<Category>,
  products: List<Product>,
  billingState: PosBillingState,
  onSelectCategory: (String) -> Unit,
  onSearchChange: (String) -> Unit,
  onAddToCart: (Product) -> Unit,
  onUpdateQty: (String, Long) -> Unit,
  onRemoveFromCart: (String) -> Unit,
  onClearCart: () -> Unit,
  onUpdateFields: (
    discountInput: String?,
    isDiscountPercent: Boolean?,
    taxPercentInput: String?,
    customerName: String?,
    customerPhone: String?,
    paymentMethod: String?
  ) -> Unit,
  onGenerateBill: () -> Unit
) {
  val activeProducts = remember(products, billingState.selectedCategoryId, billingState.searchQuery) {
    products.filter { p ->
      p.active &&
        (billingState.selectedCategoryId == "ALL" || p.categoryId == billingState.selectedCategoryId) &&
        (billingState.searchQuery.isBlank() || p.name.contains(billingState.searchQuery, ignoreCase = true))
    }
  }
  var showMobileCartSheet by remember { mutableStateOf(false) }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isSplitPane = maxWidth >= 820.dp

    if (isSplitPane) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        CatalogPane(
          storeConfig = storeConfig,
          categories = categories,
          products = activeProducts,
          billingState = billingState,
          onSelectCategory = onSelectCategory,
          onSearchChange = onSearchChange,
          onAddToCart = onAddToCart,
          modifier = Modifier
            .weight(1.45f)
            .fillMaxHeight()
        )
        Card(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          CartAndCheckoutPane(
            storeConfig = storeConfig,
            billingState = billingState,
            onUpdateQty = onUpdateQty,
            onRemoveFromCart = onRemoveFromCart,
            onClearCart = onClearCart,
            onUpdateFields = onUpdateFields,
            onGenerateBill = onGenerateBill,
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp)
          )
        }
      }
    } else {
      Column(modifier = Modifier.fillMaxSize()) {
        CatalogPane(
          storeConfig = storeConfig,
          categories = categories,
          products = activeProducts,
          billingState = billingState,
          onSelectCategory = onSelectCategory,
          onSearchChange = onSearchChange,
          onAddToCart = onAddToCart,
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 14.dp, vertical = 10.dp)
        )

        // Sticky Bottom Checkout Bar on Compact Phone
        Surface(
          tonalElevation = 6.dp,
          shadowElevation = 8.dp,
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              val totalUnits = billingState.cart.sumOf { it.quantity }
              Text(
                "$totalUnits item(s) in bill",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                IstTime.formatInrExact(billingState.grandTotal, storeConfig.currencySymbol),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
              )
            }
            Button(
              onClick = { showMobileCartSheet = true },
              enabled = billingState.cart.isNotEmpty(),
              modifier = Modifier.testTag("open_mobile_cart_button")
            ) {
              BadgedBox(
                badge = {
                  if (billingState.cart.isNotEmpty()) {
                    Badge { Text("${billingState.cart.sumOf { it.quantity }}") }
                  }
                }
              ) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null)
              }
              Spacer(Modifier.width(10.dp))
              Text("Review & Pay")
            }
          }
        }
      }

      if (showMobileCartSheet) {
        ModalBottomSheet(onDismissRequest = { showMobileCartSheet = false }) {
          CartAndCheckoutPane(
            storeConfig = storeConfig,
            billingState = billingState,
            onUpdateQty = onUpdateQty,
            onRemoveFromCart = onRemoveFromCart,
            onClearCart = {
              onClearCart()
              showMobileCartSheet = false
            },
            onUpdateFields = onUpdateFields,
            onGenerateBill = {
              showMobileCartSheet = false
              onGenerateBill()
            },
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun CatalogPane(
  storeConfig: StoreConfig,
  categories: List<Category>,
  products: List<Product>,
  billingState: PosBillingState,
  onSelectCategory: (String) -> Unit,
  onSearchChange: (String) -> Unit,
  onAddToCart: (Product) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(
      value = billingState.searchQuery,
      onValueChange = onSearchChange,
      placeholder = { Text("Search products by name...") },
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      trailingIcon = {
        if (billingState.searchQuery.isNotEmpty()) {
          IconButton(onClick = { onSearchChange("") }) {
            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("pos_search_input")
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChip(
        selected = billingState.selectedCategoryId == "ALL",
        onClick = { onSelectCategory("ALL") },
        label = { Text("All") }
      )
      categories.forEach { cat ->
        FilterChip(
          selected = billingState.selectedCategoryId == cat.id,
          onClick = { onSelectCategory(cat.id) },
          label = { Text(cat.name) }
        )
      }
    }

    if (products.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("No active products match your filter.", style = MaterialTheme.typography.bodyLarge)
          Text("Master Admin can add products under Products.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        modifier = Modifier.weight(1f)
      ) {
        items(products, key = { it.id }) { prod ->
          val qtyInCart = billingState.cart.firstOrNull { it.product.id == prod.id }?.quantity ?: 0L
          val track = prod.trackStock && storeConfig.stockTrackingEnabled
          val outOfStock = track && prod.stockQty <= 0L

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable(enabled = !outOfStock) { onAddToCart(prod) }
              .testTag("pos_product_card_${prod.id}"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (qtyInCart > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
              else MaterialTheme.colorScheme.surface
            ),
            border = if (qtyInCart > 0) BorderStroke(1.5.dp, EmeraldPrimary) else null,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
              ) {
                ProductThumbnail(product = prod, size = 54.dp)
                if (qtyInCart > 0) {
                  Surface(
                    color = EmeraldPrimary,
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text(
                      "x$qtyInCart",
                      color = Color.White,
                      style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                  }
                }
              }

              Text(
                prod.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                prod.categoryName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  IstTime.formatInr(prod.sellingPrice, storeConfig.currencySymbol),
                  style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                  fontWeight = FontWeight.Bold,
                  color = EmeraldPrimary
                )
                if (track) {
                  Text(
                    if (outOfStock) "Out" else "${prod.stockQty} left",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                      outOfStock -> CrimsonError
                      prod.stockQty <= prod.lowStockThreshold -> WarningAmber
                      else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
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
private fun CartAndCheckoutPane(
  storeConfig: StoreConfig,
  billingState: PosBillingState,
  onUpdateQty: (String, Long) -> Unit,
  onRemoveFromCart: (String) -> Unit,
  onClearCart: () -> Unit,
  onUpdateFields: (
    discountInput: String?,
    isDiscountPercent: Boolean?,
    taxPercentInput: String?,
    customerName: String?,
    customerPhone: String?,
    paymentMethod: String?
  ) -> Unit,
  onGenerateBill: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Current Bill", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      if (billingState.cart.isNotEmpty()) {
        OutlinedButton(onClick = onClearCart) { Text("Clear") }
      }
    }

    if (billingState.cart.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(140.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          "Tap products from the catalog to add them to this bill.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      billingState.cart.forEach { item ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(item.product.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
              Text(
                "${IstTime.formatInr(item.product.sellingPrice, storeConfig.currencySymbol)} × ${item.quantity} = ${IstTime.formatInrExact(item.lineTotal, storeConfig.currencySymbol)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                color = EmeraldPrimary
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { onUpdateQty(item.product.id, -1L) },
                modifier = Modifier.size(36.dp).testTag("cart_dec_${item.product.id}")
              ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
              }
              Text(
                "${item.quantity}",
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                modifier = Modifier.padding(horizontal = 4.dp)
              )
              IconButton(
                onClick = { onUpdateQty(item.product.id, 1L) },
                modifier = Modifier.size(36.dp).testTag("cart_inc_${item.product.id}")
              ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
              }
              IconButton(
                onClick = { onRemoveFromCart(item.product.id) },
                modifier = Modifier.size(36.dp).testTag("cart_remove_${item.product.id}")
              ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove item", tint = CrimsonError)
              }
            }
          }
        }
      }
    }

    HorizontalDivider()

    // Discount & Tax controls
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = billingState.discountInput,
        onValueChange = { onUpdateFields(it, null, null, null, null, null) },
        label = { Text(if (billingState.isDiscountPercent) "Discount %" else "Discount (${storeConfig.currencySymbol})") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("pos_discount_input")
      )
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("% Mode", style = MaterialTheme.typography.labelSmall)
        Switch(
          checked = billingState.isDiscountPercent,
          onCheckedChange = { onUpdateFields(null, it, null, null, null, null) }
        )
      }
      OutlinedTextField(
        value = billingState.taxPercentInput,
        onValueChange = { onUpdateFields(null, null, it, null, null, null) },
        label = { Text("Tax %") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.weight(0.75f).testTag("pos_tax_input")
      )
    }

    // Optional Customer Details
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = billingState.customerName,
        onValueChange = { onUpdateFields(null, null, null, it, null, null) },
        label = { Text("Customer Name (opt)") },
        singleLine = true,
        modifier = Modifier.weight(1f)
      )
      OutlinedTextField(
        value = billingState.customerPhone,
        onValueChange = { onUpdateFields(null, null, null, null, it, null) },
        label = { Text("Phone (opt)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.weight(1f)
      )
    }

    // Payment Methods
    Text("Payment Method", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      PaymentMethod.entries.forEach { method ->
        FilterChip(
          selected = billingState.paymentMethod == method.label,
          onClick = { onUpdateFields(null, null, null, null, null, method.label) },
          label = { Text(method.label) },
          modifier = Modifier.testTag("payment_chip_${method.label.lowercase()}")
        )
      }
    }

    // Totals Box
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BillTotalRow("Subtotal", IstTime.formatInrExact(billingState.subtotal, storeConfig.currencySymbol))
        if (billingState.discountAmount > 0.0) {
          BillTotalRow("Discount", "-${IstTime.formatInrExact(billingState.discountAmount, storeConfig.currencySymbol)}")
        }
        if (billingState.taxAmount > 0.0) {
          BillTotalRow("Tax (${billingState.taxPercent}%)", IstTime.formatInrExact(billingState.taxAmount, storeConfig.currencySymbol))
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Grand Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            IstTime.formatInrExact(billingState.grandTotal, storeConfig.currencySymbol),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary
          )
        }
      }
    }

    Button(
      onClick = onGenerateBill,
      enabled = billingState.cart.isNotEmpty() && !billingState.isSubmitting,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("generate_bill_button")
    ) {
      if (billingState.isSubmitting) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
      } else {
        Icon(Icons.Filled.ReceiptLong, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Generate Unique Invoice")
      }
    }
  }
}

@Composable
private fun BillTotalRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
  }
}
