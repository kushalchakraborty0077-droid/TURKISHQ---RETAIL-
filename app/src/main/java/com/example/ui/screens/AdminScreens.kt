package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLog
import com.example.data.model.Invoice
import com.example.data.model.StaffAccount
import com.example.data.model.StoreConfig
import com.example.ui.components.TurkishqLogoBadge
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SaffronGold
import com.example.ui.theme.SuccessGreen
import com.example.util.InvoicePdfAndPrintHelper
import com.example.util.IstTime
import java.io.File

@Composable
fun StaffManagementScreen(
  storeConfig: StoreConfig,
  staffAccounts: List<StaffAccount>,
  invoices: List<Invoice>,
  onSaveStaff: (
    existing: StaffAccount?,
    username: String,
    displayName: String,
    newPassword: String?,
    linkedEmail: String,
    active: Boolean,
    canViewSalesReports: Boolean,
    canViewProfitReports: Boolean
  ) -> Unit,
  onOpenInvoice: (Invoice) -> Unit
) {
  var showEditor by remember { mutableStateOf(false) }
  var editingStaff by remember { mutableStateOf<StaffAccount?>(null) }
  var inspectingStaff by remember { mutableStateOf<StaffAccount?>(null) }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editingStaff = null
          showEditor = true
        },
        modifier = Modifier.testTag("add_staff_fab")
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Add Staff Account")
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
      Text("Staff Management", style = MaterialTheme.typography.headlineSmall)
      Text(
        "Create unlimited staff POS accounts, assign report permissions, reset passwords, or link Google emails for multi-device counters.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (staffAccounts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.size(48.dp), tint = EmeraldPrimary)
            Text("No staff accounts created yet.", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { editingStaff = null; showEditor = true }) {
              Icon(Icons.Filled.Add, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text("Add First Staff Member")
            }
          }
        }
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          items(staffAccounts, key = { it.id }) { staff ->
            val staffBills = invoices.filter { it.staffId == staff.id && it.status == "completed" }
            val staffTotal = staffBills.sumOf { it.grandTotal }
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(staff.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                      "@${staff.username}" + if (staff.linkedEmail.isNotBlank()) " • ${staff.linkedEmail}" else "",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Surface(
                    color = (if (staff.active) SuccessGreen else CrimsonError).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text(
                      if (staff.active) "ACTIVE" else "DISABLED",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (staff.active) SuccessGreen else CrimsonError,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                  }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Surface(color = EmeraldPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                      "Sales Reports: ${if (staff.canViewSalesReports) "Yes" else "No"}",
                      style = MaterialTheme.typography.labelSmall,
                      color = EmeraldPrimary,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                  }
                  Surface(color = SaffronGold.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                      "Profit Reports: ${if (staff.canViewProfitReports) "Yes" else "No"}",
                      style = MaterialTheme.typography.labelSmall,
                      color = SaffronGold,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                  }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    "${staffBills.size} bills • ${IstTime.formatInr(staffTotal, storeConfig.currencySymbol)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                    color = EmeraldPrimary
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { inspectingStaff = staff }) {
                      Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Sales")
                    }
                    OutlinedButton(
                      onClick = {
                        editingStaff = staff
                        showEditor = true
                      },
                      modifier = Modifier.testTag("edit_staff_${staff.id}")
                    ) {
                      Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Edit")
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

  if (showEditor) {
    StaffEditorDialog(
      storeConfig = storeConfig,
      existing = editingStaff,
      onSave = { username, displayName, password, linkedEmail, active, salesPerm, profitPerm ->
        onSaveStaff(editingStaff, username, displayName, password, linkedEmail, active, salesPerm, profitPerm)
        showEditor = false
      },
      onDismiss = { showEditor = false }
    )
  }

  inspectingStaff?.let { staff ->
    val staffInvoices = invoices.filter { it.staffId == staff.id }
    AlertDialog(
      onDismissRequest = { inspectingStaff = null },
      title = { Text("${staff.displayName} — Invoice History") },
      text = {
        if (staffInvoices.isEmpty()) {
          Text("No invoices generated by this staff account yet.")
        } else {
          LazyColumn(modifier = Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(staffInvoices, key = { it.id }) { inv ->
              Card(
                modifier = Modifier.fillMaxWidth().clickable {
                  inspectingStaff = null
                  onOpenInvoice(inv)
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column {
                    Text(inv.invoiceNumber, style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily))
                    Text(IstTime.formatDateTime(inv.epochMillis), style = MaterialTheme.typography.bodySmall)
                  }
                  Text(
                    IstTime.formatInr(inv.grandTotal, storeConfig.currencySymbol),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { inspectingStaff = null }) { Text("Close") }
      }
    )
  }
}

@Composable
private fun StaffEditorDialog(
  storeConfig: StoreConfig,
  existing: StaffAccount?,
  onSave: (
    username: String,
    displayName: String,
    newPassword: String?,
    linkedEmail: String,
    active: Boolean,
    canViewSalesReports: Boolean,
    canViewProfitReports: Boolean
  ) -> Unit,
  onDismiss: () -> Unit
) {
  var username by remember { mutableStateOf(existing?.username ?: "") }
  var displayName by remember { mutableStateOf(existing?.displayName ?: "") }
  var password by remember { mutableStateOf("") }
  var linkedEmail by remember { mutableStateOf(existing?.linkedEmail ?: "") }
  var active by remember { mutableStateOf(existing?.active ?: true) }
  var canViewSales by remember { mutableStateOf(existing?.canViewSalesReports ?: storeConfig.allowStaffViewSalesReportsDefault) }
  var canViewProfit by remember { mutableStateOf(existing?.canViewProfitReports ?: storeConfig.allowStaffViewProfitReportsDefault) }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "Add Staff Account" else "Edit Staff Account") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it; error = null },
          label = { Text("Full Display Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("staff_display_name_input")
        )
        OutlinedTextField(
          value = username,
          onValueChange = { username = it.replace(" ", ""); error = null },
          label = { Text("Login Username *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("staff_username_input")
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it; error = null },
          label = { Text(if (existing == null) "Password * (min 4 chars)" else "Reset Password (leave blank to keep)") },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("staff_password_input")
        )
        OutlinedTextField(
          value = linkedEmail,
          onValueChange = { linkedEmail = it },
          label = { Text("Linked Google Email (for remote terminal)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("staff_email_input")
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Account Active")
          Switch(checked = active, onCheckedChange = { active = it })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Can View Sales Reports")
          Switch(checked = canViewSales, onCheckedChange = { canViewSales = it })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Can View Profit Reports")
          Switch(checked = canViewProfit, onCheckedChange = { canViewProfit = it })
        }
        if (error != null) {
          Text(error!!, color = CrimsonError, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when {
            displayName.isBlank() -> error = "Display name is required."
            username.trim().length < 3 -> error = "Username must be at least 3 characters."
            existing == null && password.length < 4 -> error = "Password must be at least 4 characters."
            password.isNotEmpty() && password.length < 4 -> error = "New password must be at least 4 characters."
            else -> onSave(
              username.trim(),
              displayName.trim(),
              password.takeIf { it.isNotBlank() },
              linkedEmail.trim(),
              active,
              canViewSales,
              canViewProfit
            )
          }
        },
        modifier = Modifier.testTag("save_staff_button")
      ) {
        Text("Save Staff")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
fun ActivityLogScreen(logs: List<ActivityLog>) {
  var search by remember { mutableStateOf("") }
  var roleFilter by remember { mutableStateOf("ALL") }

  val filtered = remember(logs, search, roleFilter) {
    logs.filter { log ->
      val matchRole = roleFilter == "ALL" || log.actorRole == roleFilter
      val matchQuery = search.isBlank() ||
        log.actorName.contains(search, ignoreCase = true) ||
        log.action.contains(search, ignoreCase = true) ||
        log.details.contains(search, ignoreCase = true)
      matchRole && matchQuery
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Staff & Admin Activity Log", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
      value = search,
      onValueChange = { search = it },
      placeholder = { Text("Search action, actor, or details...") },
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth()
    )
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChip(selected = roleFilter == "ALL", onClick = { roleFilter = "ALL" }, label = { Text("All (${logs.size})") })
      FilterChip(selected = roleFilter == "master_admin", onClick = { roleFilter = "master_admin" }, label = { Text("Master Admin") })
      FilterChip(selected = roleFilter == "staff", onClick = { roleFilter = "staff" }, label = { Text("Staff") })
    }

    if (filtered.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("No activity logs found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filtered, key = { it.id }) { log ->
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(
                  color = (if (log.actorRole == "master_admin") SaffronGold else EmeraldPrimary).copy(alpha = 0.14f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    log.action,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = if (log.actorRole == "master_admin") SaffronGold else EmeraldPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  )
                }
                log.createdAt?.toDate()?.time?.let { ts ->
                  Text(IstTime.formatDateTime(ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              Text(log.details, style = MaterialTheme.typography.bodyMedium)
              Text(
                "Actor: ${log.actorName} (${log.actorRole})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun SettingsScreen(
  storeConfig: StoreConfig,
  onSaveSettings: (
    businessName: String,
    businessAddress: String,
    contactNumber: String,
    invoiceFooter: String,
    currencySymbol: String,
    invoicePrefix: String,
    defaultTaxPercent: Double,
    defaultDiscountPercent: Double,
    stockTrackingEnabled: Boolean,
    allowStaffSalesDefault: Boolean,
    allowStaffProfitDefault: Boolean,
    newAdminPassword: String?
  ) -> Unit,
  onExportBackupJson: () -> String
) {
  val context = LocalContext.current
  var businessName by remember(storeConfig) { mutableStateOf(storeConfig.businessName) }
  var businessAddress by remember(storeConfig) { mutableStateOf(storeConfig.businessAddress) }
  var contactNumber by remember(storeConfig) { mutableStateOf(storeConfig.contactNumber) }
  var invoiceFooter by remember(storeConfig) { mutableStateOf(storeConfig.invoiceFooter) }
  var currencySymbol by remember(storeConfig) { mutableStateOf(storeConfig.currencySymbol) }
  var invoicePrefix by remember(storeConfig) { mutableStateOf(storeConfig.invoicePrefix) }
  var defaultTax by remember(storeConfig) { mutableStateOf(storeConfig.defaultTaxPercent.toString()) }
  var defaultDiscount by remember(storeConfig) { mutableStateOf(storeConfig.defaultDiscountPercent.toString()) }
  var stockTracking by remember(storeConfig) { mutableStateOf(storeConfig.stockTrackingEnabled) }
  var staffSalesDefault by remember(storeConfig) { mutableStateOf(storeConfig.allowStaffViewSalesReportsDefault) }
  var staffProfitDefault by remember(storeConfig) { mutableStateOf(storeConfig.allowStaffViewProfitReportsDefault) }
  var newAdminPassword by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      TurkishqLogoBadge(size = 50.dp)
      Column {
        Text("Application Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Master Admin Store Configuration • Asia/Kolkata", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Business & Invoice Profile", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = businessName, onValueChange = { businessName = it }, label = { Text("Business Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = businessAddress, onValueChange = { businessAddress = it }, label = { Text("Business Address") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = contactNumber, onValueChange = { contactNumber = it }, label = { Text("Contact Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = invoiceFooter, onValueChange = { invoiceFooter = it }, label = { Text("Invoice Footer Note") }, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(value = currencySymbol, onValueChange = { currencySymbol = it.take(3) }, label = { Text("Currency") }, modifier = Modifier.weight(1f))
          OutlinedTextField(value = invoicePrefix, onValueChange = { invoicePrefix = it.uppercase().take(8) }, label = { Text("Invoice Prefix") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(value = defaultTax, onValueChange = { defaultTax = it }, label = { Text("Default Tax %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
          OutlinedTextField(value = defaultDiscount, onValueChange = { defaultDiscount = it }, label = { Text("Default Disc %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
      }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Operations & Permissions", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Auto Inventory Stock Tracking")
          Switch(checked = stockTracking, onCheckedChange = { stockTracking = it })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Default: Staff View Sales Reports")
          Switch(checked = staffSalesDefault, onCheckedChange = { staffSalesDefault = it })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Default: Staff View Profit Reports")
          Switch(checked = staffProfitDefault, onCheckedChange = { staffProfitDefault = it })
        }
        OutlinedTextField(
          value = newAdminPassword,
          onValueChange = { newAdminPassword = it },
          label = { Text("Change Master Admin Password (optional)") },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }

    Button(
      onClick = {
        onSaveSettings(
          businessName,
          businessAddress,
          contactNumber,
          invoiceFooter,
          currencySymbol,
          invoicePrefix,
          defaultTax.toDoubleOrNull() ?: 5.0,
          defaultDiscount.toDoubleOrNull() ?: 0.0,
          stockTracking,
          staffSalesDefault,
          staffProfitDefault,
          newAdminPassword.takeIf { it.isNotBlank() }
        )
        newAdminPassword = ""
      },
      modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_settings_button")
    ) {
      Icon(Icons.Filled.Save, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text("Save Settings")
    }

    OutlinedButton(
      onClick = {
        val json = onExportBackupJson()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "turkishq_backup_${IstTime.todayDateKey()}.json")
        file.writeText(json)
        InvoicePdfAndPrintHelper.shareOrSaveFile(context, file, "application/json", "TURKISHQ RETAIL Backup JSON")
      },
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_backup_button")
    ) {
      Icon(Icons.Filled.Download, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text("Export Full Store Backup (JSON)")
    }
  }
}
