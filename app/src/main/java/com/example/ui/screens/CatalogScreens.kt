package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.ProductCost
import com.example.data.model.StoreConfig
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.encodeUriToCompressedBase64
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.util.IstTime

@Composable
fun CategoriesScreen(
  categories: List<Category>,
  products: List<Product>,
  onSaveCategory: (id: String?, name: String, sortOrder: Long) -> Unit,
  onDeleteCategory: (Category) -> Unit,
  onLoadStarterCatalog: () -> Unit
) {
  var editingCategory by remember { mutableStateOf<Category?>(null) }
  var showAddDialog by remember { mutableStateOf(false) }
  var deletingCategory by remember { mutableStateOf<Category?>(null) }
  var viewingProductsCategory by remember { mutableStateOf<Category?>(null) }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        modifier = Modifier.testTag("add_category_fab")
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Add Category")
      }
    }
  ) { pad ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(pad)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      if (categories.isEmpty()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
          shape = RoundedCornerShape(16.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No Categories Created Yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
              "Create unlimited retail categories like Beverages, Baklava, Turkish Delight, or load our starter TurkishQ menu catalog.",
              style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onLoadStarterCatalog, modifier = Modifier.testTag("load_starter_catalog_button")) {
              Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Load Starter TurkishQ Menu")
            }
          }
        }
      }

      LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        items(categories, key = { it.id }) { cat ->
          val count = products.count { it.categoryId == cat.id }
          Card(
            onClick = { viewingProductsCategory = cat },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.size(42.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Category, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                  }
                }
                Column {
                  Text(cat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Text("$count product(s) • Order #${cat.sortOrder}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              Row {
                IconButton(onClick = { editingCategory = cat }) {
                  Icon(Icons.Filled.Edit, contentDescription = "Edit category")
                }
                IconButton(onClick = { deletingCategory = cat }) {
                  Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete category", tint = CrimsonError)
                }
              }
            }
          }
        }
      }
    }
  }

  if (showAddDialog || editingCategory != null) {
    val target = editingCategory
    var name by remember(target) { mutableStateOf(target?.name ?: "") }
    var sortInput by remember(target) { mutableStateOf((target?.sortOrder ?: (categories.size + 1L)).toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showAddDialog = false; editingCategory = null },
      title = { Text(if (target == null) "New Category" else "Edit Category") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Category Name *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("category_name_input")
          )
          OutlinedTextField(
            value = sortInput,
            onValueChange = { sortInput = it },
            label = { Text("Sort Order") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          if (error != null) Text(error!!, color = CrimsonError, style = MaterialTheme.typography.bodySmall)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (name.isBlank()) {
              error = "Category name is required."
            } else {
              onSaveCategory(target?.id, name.trim(), sortInput.toLongOrNull() ?: 1L)
              showAddDialog = false
              editingCategory = null
            }
          },
          modifier = Modifier.testTag("save_category_button")
        ) { Text("Save") }
      },
      dismissButton = {
        OutlinedButton(onClick = { showAddDialog = false; editingCategory = null }) { Text("Cancel") }
      }
    )
  }

  deletingCategory?.let { cat ->
    ConfirmActionDialog(
      title = "Delete '${cat.name}'?",
      message = "Categories with existing products cannot be deleted until products are moved or removed.",
      confirmLabel = "Delete",
      onConfirm = {
        onDeleteCategory(cat)
        deletingCategory = null
      },
      onDismiss = { deletingCategory = null }
    )
  }

  viewingProductsCategory?.let { cat ->
    val prodsInCat = products.filter { it.categoryId == cat.id }
    AlertDialog(
      onDismissRequest = { viewingProductsCategory = null },
      title = { Text("${cat.name} (${prodsInCat.size})") },
      text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          if (prodsInCat.isEmpty()) {
            Text("No products inside this category yet.")
          } else {
            prodsInCat.forEach { p ->
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(p.name, style = MaterialTheme.typography.bodyMedium)
                Text(IstTime.formatInr(p.sellingPrice), style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily), color = EmeraldPrimary)
              }
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { viewingProductsCategory = null }) { Text("Close") }
      }
    )
  }
}

@Composable
fun ProductsScreen(
  storeConfig: StoreConfig,
  categories: List<Category>,
  products: List<Product>,
  productCosts: Map<String, ProductCost>,
  onSaveProduct: (
    existing: Product?,
    name: String,
    category: Category,
    sellingPrice: Double,
    purchaseCost: Double?,
    description: String,
    imageKey: String,
    imageBase64: String,
    active: Boolean,
    trackStock: Boolean,
    stockQty: Long,
    lowStockThreshold: Long
  ) -> Unit,
  onDeleteProduct: (Product) -> Unit,
  onLoadStarterCatalog: () -> Unit
) {
  var search by remember { mutableStateOf("") }
  var selectedCatId by remember { mutableStateOf("ALL") }
  var showDialog by remember { mutableStateOf(false) }
  var editingProduct by remember { mutableStateOf<Product?>(null) }
  var deletingProduct by remember { mutableStateOf<Product?>(null) }

  val filtered = remember(products, search, selectedCatId) {
    products.filter { p ->
      (selectedCatId == "ALL" || p.categoryId == selectedCatId) &&
        (search.isBlank() || p.name.contains(search, ignoreCase = true))
    }
  }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = { editingProduct = null; showDialog = true },
        modifier = Modifier.testTag("add_product_fab")
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Add Product")
      }
    }
  ) { pad ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(pad)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      OutlinedTextField(
        value = search,
        onValueChange = { search = it },
        placeholder = { Text("Search products...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(selected = selectedCatId == "ALL", onClick = { selectedCatId = "ALL" }, label = { Text("All") })
        categories.forEach { cat ->
          FilterChip(selected = selectedCatId == cat.id, onClick = { selectedCatId = cat.id }, label = { Text(cat.name) })
        }
      }

      if (products.isEmpty() && categories.isEmpty()) {
        Button(onClick = onLoadStarterCatalog) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Load Starter TurkishQ Menu")
        }
      }

      LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        items(filtered, key = { it.id }) { prod ->
          val cost = productCosts[prod.id]?.purchaseCost
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
              ProductThumbnail(product = prod, size = 58.dp)
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(prod.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Surface(
                    color = if (prod.active) SuccessGreen.copy(alpha = 0.14f) else CrimsonError.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      if (prod.active) "ACTIVE" else "INACTIVE",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (prod.active) SuccessGreen else CrimsonError,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
                Text(prod.categoryName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  Text(
                    "Sell: ${IstTime.formatInr(prod.sellingPrice, storeConfig.currencySymbol)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                    color = EmeraldPrimary
                  )
                  if (cost != null) {
                    Text(
                      "Cost: ${IstTime.formatInr(cost, storeConfig.currencySymbol)}",
                      style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                if (prod.trackStock) {
                  Text("Stock: ${prod.stockQty} (Low alert ≤ ${prod.lowStockThreshold})", style = MaterialTheme.typography.bodySmall)
                }
              }
              Column {
                IconButton(onClick = { editingProduct = prod; showDialog = true }) {
                  Icon(Icons.Filled.Edit, contentDescription = "Edit product")
                }
                IconButton(onClick = { deletingProduct = prod }) {
                  Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete product", tint = CrimsonError)
                }
              }
            }
          }
        }
      }
    }
  }

  if (showDialog) {
    ProductEditorDialog(
      categories = categories,
      existing = editingProduct,
      existingCost = editingProduct?.let { productCosts[it.id]?.purchaseCost },
      onSave = { name, cat, price, cost, desc, imgKey, imgBase64, active, track, qty, low ->
        onSaveProduct(editingProduct, name, cat, price, cost, desc, imgKey, imgBase64, active, track, qty, low)
        showDialog = false
        editingProduct = null
      },
      onDismiss = { showDialog = false; editingProduct = null }
    )
  }

  deletingProduct?.let { prod ->
    ConfirmActionDialog(
      title = "Delete '${prod.name}'?",
      message = "Past invoices will keep their snapshotted product name and price.",
      confirmLabel = "Delete",
      onConfirm = {
        onDeleteProduct(prod)
        deletingProduct = null
      },
      onDismiss = { deletingProduct = null }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorDialog(
  categories: List<Category>,
  existing: Product?,
  existingCost: Double?,
  onSave: (
    name: String,
    category: Category,
    sellingPrice: Double,
    purchaseCost: Double?,
    description: String,
    imageKey: String,
    imageBase64: String,
    active: Boolean,
    trackStock: Boolean,
    stockQty: Long,
    lowStockThreshold: Long
  ) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
  var selectedCategory by remember(existing, categories) {
    mutableStateOf(categories.firstOrNull { it.id == existing?.categoryId } ?: categories.firstOrNull())
  }
  var catDropdownExpanded by remember { mutableStateOf(false) }
  var priceInput by remember(existing) { mutableStateOf(existing?.sellingPrice?.toString() ?: "") }
  var costInput by remember(existingCost) { mutableStateOf(existingCost?.toString() ?: "") }
  var description by remember(existing) { mutableStateOf(existing?.description ?: "") }
  var imageKey by remember(existing) { mutableStateOf(existing?.imageKey ?: "baklava") }
  var imageBase64 by remember(existing) { mutableStateOf(existing?.imageBase64 ?: "") }
  var active by remember(existing) { mutableStateOf(existing?.active ?: true) }
  var trackStock by remember(existing) { mutableStateOf(existing?.trackStock ?: true) }
  var stockQtyInput by remember(existing) { mutableStateOf((existing?.stockQty ?: 25L).toString()) }
  var lowThresholdInput by remember(existing) { mutableStateOf((existing?.lowStockThreshold ?: 5L).toString()) }
  var error by remember { mutableStateOf<String?>(null) }

  // Zero-permission Android Photo Picker (Play Policy compliant)
  val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      val b64 = encodeUriToCompressedBase64(context, uri)
      if (b64.isNotEmpty()) {
        imageBase64 = b64
        imageKey = "custom_photo"
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "Add Product" else "Edit Product") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (categories.isEmpty()) {
          Text("Create at least one category first before adding products.", color = CrimsonError)
        } else {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Product Name *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("product_name_input")
          )
          ExposedDropdownMenuBox(expanded = catDropdownExpanded, onExpandedChange = { catDropdownExpanded = it }) {
            OutlinedTextField(
              value = selectedCategory?.name ?: "Select Category",
              onValueChange = {},
              readOnly = true,
              label = { Text("Category *") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catDropdownExpanded) },
              modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = catDropdownExpanded, onDismissRequest = { catDropdownExpanded = false }) {
              categories.forEach { cat ->
                DropdownMenuItem(
                  text = { Text(cat.name) },
                  onClick = { selectedCategory = cat; catDropdownExpanded = false }
                )
              }
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = priceInput,
              onValueChange = { priceInput = it; error = null },
              label = { Text("Selling Price (₹) *") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("product_price_input")
            )
            OutlinedTextField(
              value = costInput,
              onValueChange = { costInput = it },
              label = { Text("Purchase Cost (opt)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }
          OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth()
          )

          Text("Product Icon / Photo", style = MaterialTheme.typography.labelMedium)
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf("baklava", "tea", "coffee", "juice", "delight", "dessert", "icecream", "snack").forEach { key ->
              FilterChip(
                selected = imageKey == key && imageBase64.isEmpty(),
                onClick = { imageKey = key; imageBase64 = "" },
                label = { Text(key.replaceFirstChar { it.uppercase() }) }
              )
            }
          }
          OutlinedButton(
            onClick = {
              photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (imageBase64.isNotEmpty()) "Photo Attached (Tap to Replace)" else "Pick Photo from Gallery")
          }

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Product Active in POS", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = active, onCheckedChange = { active = it })
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Track Inventory Stock", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = trackStock, onCheckedChange = { trackStock = it })
          }
          if (trackStock) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = stockQtyInput,
                onValueChange = { stockQtyInput = it },
                label = { Text("Stock Qty") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = lowThresholdInput,
                onValueChange = { lowThresholdInput = it },
                label = { Text("Low Stock Alert") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
              )
            }
          }
          if (error != null) Text(error!!, color = CrimsonError, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      Button(
        enabled = categories.isNotEmpty(),
        onClick = {
          val cat = selectedCategory
          val price = priceInput.toDoubleOrNull()
          val cost = costInput.toDoubleOrNull()
          when {
            cat == null -> error = "Please select a category."
            name.isBlank() -> error = "Product name is required."
            price == null || price < 0.0 -> error = "Enter a valid selling price."
            cost != null && cost < 0.0 -> error = "Purchase cost cannot be negative."
            else -> {
              onSave(
                name.trim(),
                cat,
                price,
                cost,
                description.trim(),
                imageKey,
                imageBase64,
                active,
                trackStock,
                (stockQtyInput.toLongOrNull() ?: 0L).coerceAtLeast(0L),
                (lowThresholdInput.toLongOrNull() ?: 5L).coerceAtLeast(0L)
              )
            }
          }
        },
        modifier = Modifier.testTag("save_product_button")
      ) { Text("Save") }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
