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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Invoice
import com.example.data.model.InvoiceLineItem
import com.example.data.model.InvoiceRevision
import com.example.data.model.OperatorSession
import com.example.data.model.PaymentMethod
import com.example.data.model.Product
import com.example.data.model.StoreConfig
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.util.IstTime

@Composable
fun BillHistoryScreen(
  storeConfig: StoreConfig,
  session: OperatorSession,
  invoices: List<Invoice>,
  products: List<Product>,
  onlyMySales: Boolean,
  onOpenInvoice: (Invoice) -> Unit,
  onEditInvoice: (
    original: Invoice,
    items: List<InvoiceLineItem>,
    discountAmount: Double,
    taxPercent: Double,
    customerName: String,
    customerPhone: String,
    paymentMethod: String
  ) -> Unit,
  onCancelInvoice: (Invoice, String) -> Unit,
  onFetchRevisions: suspend (String) -> List<InvoiceRevision>
) {
  var searchQuery by remember { mutableStateOf("") }
  var dateFilter by remember { mutableStateOf("") }
  var staffFilter by remember { mutableStateOf("ALL") }
  var statusFilter by remember { mutableStateOf("ALL") }

  var editingInvoice by remember { mutableStateOf<Invoice?>(null) }
  var cancellingInvoice by remember { mutableStateOf<Invoice?>(null) }
  var revisionInvoice by remember { mutableStateOf<Invoice?>(null) }

  val scopedInvoices = remember(invoices, session, onlyMySales) {
    if (onlyMySales && session is OperatorSession.Staff) {
      invoices.filter { it.staffId == session.actorId }
    } else {
      invoices
    }
  }

  val staffNames = remember(scopedInvoices) {
    scopedInvoices.map { it.staffName }.distinct().sorted()
  }

  val filtered = remember(scopedInvoices, searchQuery, dateFilter, staffFilter, statusFilter) {
    scopedInvoices.filter { inv ->
      val matchesSearch = searchQuery.isBlank() ||
        inv.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
        inv.customerName.contains(searchQuery, ignoreCase = true) ||
        inv.staffName.contains(searchQuery, ignoreCase = true)
      val matchesDate = dateFilter.isBlank() || inv.dateKey.contains(dateFilter.trim())
      val matchesStaff = staffFilter == "ALL" || inv.staffName == staffFilter
      val matchesStatus = statusFilter == "ALL" || inv.status == statusFilter
      matchesSearch && matchesDate && matchesStaff && matchesStatus
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Search Invoice #, Staff, Customer") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.weight(1.3f).testTag("bill_history_search")
      )
      OutlinedTextField(
        value = dateFilter,
        onValueChange = { dateFilter = it },
        label = { Text("Date (DD-MM-YYYY)") },
        singleLine = true,
        modifier = Modifier.weight(0.9f)
      )
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      listOf("ALL" to "All", "completed" to "Completed", "cancelled" to "Cancelled").forEach { (key, label) ->
        FilterChip(
          selected = statusFilter == key,
          onClick = { statusFilter = key },
          label = { Text(label) }
        )
      }
      if (!onlyMySales && staffNames.size > 1) {
        FilterChip(
          selected = staffFilter == "ALL",
          onClick = { staffFilter = "ALL" },
          label = { Text("All Staff") }
        )
        staffNames.forEach { name ->
          FilterChip(
            selected = staffFilter == name,
            onClick = { staffFilter = name },
            label = { Text(name) }
          )
        }
      }
    }

    if (filtered.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No matching invoices found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        items(filtered, key = { it.id }) { inv ->
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                      inv.invoiceNumber,
                      style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                      fontWeight = FontWeight.Bold,
                      color = EmeraldPrimary
                    )
                    Surface(
                      color = if (inv.status == "completed") SuccessGreen.copy(alpha = 0.14f) else CrimsonError.copy(alpha = 0.14f),
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        inv.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (inv.status == "completed") SuccessGreen else CrimsonError,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                    if (inv.editCount > 0) {
                      Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                      ) {
                        Text(
                          "Edited x${inv.editCount}",
                          style = MaterialTheme.typography.labelSmall,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }
                  Text(
                    "${IstTime.formatDateTime(inv.epochMillis)} • Staff: ${inv.staffName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  if (inv.customerName.isNotBlank()) {
                    Text(
                      "Customer: ${inv.customerName}${if (inv.customerPhone.isNotBlank()) " (${inv.customerPhone})" else ""}",
                      style = MaterialTheme.typography.bodySmall
                    )
                  }
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    IstTime.formatInrExact(inv.grandTotal, storeConfig.currencySymbol),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.Bold
                  )
                  Text(inv.paymentMethod, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }

              Text(
                inv.items.joinToString { "${it.productName} ×${it.quantity}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedButton(
                  onClick = { onOpenInvoice(inv) },
                  modifier = Modifier.weight(1f).testTag("open_invoice_${inv.invoiceNumber}")
                ) {
                  Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("View / PDF")
                }

                // STRICT ROLE GUARD: Only Master Admin can edit, cancel, or inspect audit revisions
                if (session.isAdmin && !onlyMySales) {
                  if (inv.status == "completed") {
                    OutlinedButton(
                      onClick = { editingInvoice = inv },
                      modifier = Modifier.testTag("edit_invoice_${inv.invoiceNumber}")
                    ) {
                      Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Edit")
                    }
                    OutlinedButton(
                      onClick = { cancellingInvoice = inv },
                      colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonError),
                      modifier = Modifier.testTag("cancel_invoice_${inv.invoiceNumber}")
                    ) {
                      Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Cancel")
                    }
                  }
                  if (inv.editCount > 0) {
                    IconButton(onClick = { revisionInvoice = inv }) {
                      Icon(Icons.Filled.History, contentDescription = "Modification history")
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

  editingInvoice?.let { inv ->
    EditInvoiceDialog(
      storeConfig = storeConfig,
      invoice = inv,
      catalogProducts = products,
      onSave = { items, discount, taxPct, custName, custPhone, payment ->
        onEditInvoice(inv, items, discount, taxPct, custName, custPhone, payment)
        editingInvoice = null
      },
      onDismiss = { editingInvoice = null }
    )
  }

  cancellingInvoice?.let { inv ->
    var reason by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { cancellingInvoice = null },
      title = { Text("Cancel ${inv.invoiceNumber}?") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("This performs a soft cancellation with audit history, preserves the invoice number, excludes it from completed sales, and restores tracked stock.")
          OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Cancellation Reason *") },
            modifier = Modifier.fillMaxWidth().testTag("cancel_reason_input")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onCancelInvoice(inv, reason.ifBlank { "Cancelled by Master Admin" })
            cancellingInvoice = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
          modifier = Modifier.testTag("confirm_cancel_invoice_button")
        ) {
          Text("Confirm Cancel")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { cancellingInvoice = null }) { Text("Keep Invoice") }
      }
    )
  }

  revisionInvoice?.let { inv ->
    var revisions by remember { mutableStateOf<List<InvoiceRevision>>(emptyList()) }
    LaunchedEffect(inv.id) {
      revisions = onFetchRevisions(inv.id)
    }
    AlertDialog(
      onDismissRequest = { revisionInvoice = null },
      title = { Text("Audit History — ${inv.invoiceNumber}") },
      text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (revisions.isEmpty()) {
            Text("Loading revision records...")
          } else {
            revisions.forEach { rev ->
              Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text("By: ${rev.editedBy}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                  Text(rev.changeSummary, style = MaterialTheme.typography.bodySmall)
                  val ts = rev.createdAt?.toDate()?.time
                  if (ts != null) {
                    Text(IstTime.formatDateTime(ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { revisionInvoice = null }) { Text("Close") }
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditInvoiceDialog(
  storeConfig: StoreConfig,
  invoice: Invoice,
  catalogProducts: List<Product>,
  onSave: (
    items: List<InvoiceLineItem>,
    discountAmount: Double,
    taxPercent: Double,
    customerName: String,
    customerPhone: String,
    paymentMethod: String
  ) -> Unit,
  onDismiss: () -> Unit
) {
  val items = remember { mutableStateListOf<InvoiceLineItem>().apply { addAll(invoice.items) } }
  var discountInput by remember { mutableStateOf(invoice.discountAmount.toString()) }
  var taxInput by remember { mutableStateOf(invoice.taxPercent.toString()) }
  var customerName by remember { mutableStateOf(invoice.customerName) }
  var customerPhone by remember { mutableStateOf(invoice.customerPhone) }
  var paymentMethod by remember { mutableStateOf(invoice.paymentMethod) }
  var productPickerExpanded by remember { mutableStateOf(false) }

  val subtotal = items.sumOf { it.unitPrice * it.quantity }
  val discount = (discountInput.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal)
  val taxPct = (taxInput.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
  val taxable = (subtotal - discount).coerceAtLeast(0.0)
  val taxAmt = taxable * (taxPct / 100.0)
  val grandTotal = taxable + taxAmt

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .padding(vertical = 16.dp),
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface
    ) {
      Column(
        modifier = Modifier
          .padding(18.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text("Edit Invoice ${invoice.invoiceNumber}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Original invoice number will be preserved.", style = MaterialTheme.typography.bodySmall, color = EmeraldPrimary)

        items.forEachIndexed { idx, li ->
          Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(li.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (items.size > 1) items.removeAt(idx) }) {
                  Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove line item", tint = CrimsonError)
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                  value = li.unitPrice.toString(),
                  onValueChange = { raw ->
                    val p = raw.toDoubleOrNull() ?: li.unitPrice
                    items[idx] = li.copy(unitPrice = p.coerceAtLeast(0.0), lineTotal = p.coerceAtLeast(0.0) * li.quantity)
                  },
                  label = { Text("Unit Price") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(onClick = {
                    val nextQ = (li.quantity - 1L).coerceAtLeast(1L)
                    items[idx] = li.copy(quantity = nextQ, lineTotal = li.unitPrice * nextQ)
                  }) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }
                  Text("${li.quantity}", style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily))
                  IconButton(onClick = {
                    val nextQ = li.quantity + 1L
                    items[idx] = li.copy(quantity = nextQ, lineTotal = li.unitPrice * nextQ)
                  }) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
                }
              }
            }
          }
        }

        // Add product to invoice dropdown
        ExposedDropdownMenuBox(expanded = productPickerExpanded, onExpandedChange = { productPickerExpanded = it }) {
          OutlinedTextField(
            value = "Add Product to Invoice...",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productPickerExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
          )
          ExposedDropdownMenu(expanded = productPickerExpanded, onDismissRequest = { productPickerExpanded = false }) {
            catalogProducts.filter { it.active }.forEach { prod ->
              DropdownMenuItem(
                text = { Text("${prod.name} (${IstTime.formatInr(prod.sellingPrice, storeConfig.currencySymbol)})") },
                onClick = {
                  val existingIdx = items.indexOfFirst { it.productId == prod.id }
                  if (existingIdx >= 0) {
                    val cur = items[existingIdx]
                    items[existingIdx] = cur.copy(quantity = cur.quantity + 1L, lineTotal = cur.unitPrice * (cur.quantity + 1L))
                  } else {
                    items.add(
                      InvoiceLineItem(
                        productId = prod.id,
                        productName = prod.name,
                        categoryId = prod.categoryId,
                        categoryName = prod.categoryName,
                        quantity = 1L,
                        unitPrice = prod.sellingPrice,
                        lineTotal = prod.sellingPrice,
                        trackStock = prod.trackStock
                      )
                    )
                  }
                  productPickerExpanded = false
                }
              )
            }
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = discountInput,
            onValueChange = { discountInput = it },
            label = { Text("Discount (${storeConfig.currencySymbol})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = taxInput,
            onValueChange = { taxInput = it },
            label = { Text("Tax %") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("Customer Name") }, modifier = Modifier.weight(1f))
          OutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("Customer Phone") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
          PaymentMethod.entries.forEach { m ->
            FilterChip(selected = paymentMethod == m.label, onClick = { paymentMethod = m.label }, label = { Text(m.label) })
          }
        }

        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Updated Grand Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(IstTime.formatInrExact(grandTotal, storeConfig.currencySymbol), style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily), color = EmeraldPrimary, fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
          Button(
            onClick = { onSave(items.toList(), discount, taxPct, customerName, customerPhone, paymentMethod) },
            modifier = Modifier.weight(1f).testTag("save_edited_invoice_button")
          ) { Text("Save Changes") }
        }
      }
    }
  }
}
