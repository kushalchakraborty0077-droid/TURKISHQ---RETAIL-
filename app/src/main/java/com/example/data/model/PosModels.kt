package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class StoreConfig(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val ownerEmail: String = "",
  val businessName: String = "TURKISHQ RETAIL",
  val businessAddress: String = "",
  val contactNumber: String = "",
  val invoiceFooter: String = "Thank you for shopping with TURKISHQ RETAIL!",
  val currencySymbol: String = "₹",
  val invoicePrefix: String = "TQR",
  val defaultTaxPercent: Double = 5.0,
  val defaultDiscountPercent: Double = 0.0,
  val stockTrackingEnabled: Boolean = true,
  val allowStaffViewSalesReportsDefault: Boolean = true,
  val allowStaffViewProfitReportsDefault: Boolean = false,
  val adminUsername: String = "",
  val adminPasswordHash: String = "",
  val adminPasswordSalt: String = "",
  val memberEmails: List<String> = emptyList(),
  val salesViewerEmails: List<String> = emptyList(),
  val profitViewerEmails: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StaffAccount(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val ownerEmail: String = "",
  val username: String = "",
  val displayName: String = "",
  val passwordHash: String = "",
  val passwordSalt: String = "",
  val linkedEmail: String = "",
  val active: Boolean = true,
  val canViewSalesReports: Boolean = true,
  val canViewProfitReports: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StaffAccess(
  @DocumentId val id: String = "",
  val staffId: String = "",
  val email: String = "",
  val username: String = "",
  val displayName: String = "",
  val active: Boolean = true,
  val canViewSalesReports: Boolean = true,
  val canViewProfitReports: Boolean = false,
  val ownerUid: String = "",
  val ownerEmail: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Category(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val ownerEmail: String = "",
  val memberEmails: List<String> = emptyList(),
  val name: String = "",
  val sortOrder: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Product(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val ownerEmail: String = "",
  val memberEmails: List<String> = emptyList(),
  val name: String = "",
  val categoryId: String = "",
  val categoryName: String = "",
  val sellingPrice: Double = 0.0,
  val description: String = "",
  val imageKey: String = "default",
  val imageBase64: String = "",
  val active: Boolean = true,
  val trackStock: Boolean = false,
  val stockQty: Long = 0L,
  val lowStockThreshold: Long = 5L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ProductCost(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val ownerEmail: String = "",
  val profitViewerEmails: List<String> = emptyList(),
  val productId: String = "",
  val purchaseCost: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InvoiceLineItem(
  val productId: String = "",
  val productName: String = "",
  val categoryId: String = "",
  val categoryName: String = "",
  val quantity: Long = 1L,
  val unitPrice: Double = 0.0,
  val lineTotal: Double = 0.0,
  val trackStock: Boolean = false
)

@IgnoreExtraProperties
data class Invoice(
  @DocumentId val id: String = "",
  val storeOwnerUid: String = "",
  val storeOwnerEmail: String = "",
  val reportViewerEmails: List<String> = emptyList(),
  val staffAuthEmail: String = "",
  val invoiceNumber: String = "",
  val sequenceNumber: Long = 0L,
  val invoiceYear: Long = 2026L,
  val dateKey: String = "",
  val epochMillis: Long = 0L,
  val staffId: String = "",
  val staffName: String = "",
  val customerName: String = "",
  val customerPhone: String = "",
  val items: List<InvoiceLineItem> = emptyList(),
  val subtotal: Double = 0.0,
  val discountAmount: Double = 0.0,
  val taxPercent: Double = 0.0,
  val taxAmount: Double = 0.0,
  val grandTotal: Double = 0.0,
  val paymentMethod: String = "Cash",
  val status: String = "completed",
  val cancelReason: String = "",
  val cancelledBy: String = "",
  val editCount: Long = 0L,
  val lastEditedBy: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InvoiceItemCost(
  val productId: String = "",
  val productName: String = "",
  val categoryId: String = "",
  val categoryName: String = "",
  val quantity: Long = 0L,
  val unitCost: Double = 0.0,
  val lineCost: Double = 0.0,
  val lineRevenue: Double = 0.0
)

@IgnoreExtraProperties
data class InvoiceCost(
  @DocumentId val id: String = "",
  val storeOwnerUid: String = "",
  val storeOwnerEmail: String = "",
  val profitViewerEmails: List<String> = emptyList(),
  val invoiceNumber: String = "",
  val dateKey: String = "",
  val epochMillis: Long = 0L,
  val status: String = "completed",
  val totalCost: Double = 0.0,
  val totalRevenue: Double = 0.0,
  val itemCosts: List<InvoiceItemCost> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InvoiceRevision(
  @DocumentId val id: String = "",
  val editedBy: String = "",
  val editedByRole: String = "",
  val changeSummary: String = "",
  val previousGrandTotal: Double = 0.0,
  val newGrandTotal: Double = 0.0,
  val previousStatus: String = "",
  val newStatus: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StockMovement(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val ownerEmail: String = "",
  val productId: String = "",
  val productName: String = "",
  val type: String = "adjustment",
  val deltaQty: Long = 0L,
  val resultingQty: Long = 0L,
  val referenceId: String = "manual",
  val performedBy: String = "",
  val note: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ActivityLog(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val ownerEmail: String = "",
  val actorId: String = "",
  val actorName: String = "",
  val actorRole: String = "staff",
  val action: String = "",
  val details: String = "",
  val dateKey: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null
)

enum class PaymentMethod(val label: String) {
  CASH("Cash"),
  UPI("UPI"),
  CARD("Card"),
  OTHER("Other")
}

sealed class OperatorSession {
  data object LoggedOut : OperatorSession()
  data class MasterAdmin(val username: String, val displayName: String) : OperatorSession()
  data class Staff(val account: StaffAccount) : OperatorSession()

  val isAdmin: Boolean get() = this is MasterAdmin
  val actorId: String
    get() = when (this) {
      LoggedOut -> ""
      is MasterAdmin -> "master_admin"
      is Staff -> account.id
    }
  val actorName: String
    get() = when (this) {
      LoggedOut -> ""
      is MasterAdmin -> displayName
      is Staff -> account.displayName
    }
  val roleKey: String
    get() = when (this) {
      LoggedOut -> ""
      is MasterAdmin -> "master_admin"
      is Staff -> "staff"
    }
  val canViewSalesReports: Boolean
    get() = when (this) {
      LoggedOut -> false
      is MasterAdmin -> true
      is Staff -> account.canViewSalesReports
    }
  val canViewProfitReports: Boolean
    get() = when (this) {
      LoggedOut -> false
      is MasterAdmin -> true
      is Staff -> account.canViewProfitReports
    }
}

enum class PosDestination(val title: String) {
  DASHBOARD("Dashboard"),
  NEW_BILL("New Bill"),
  BILL_HISTORY("Bill History"),
  MY_SALES("My Sales"),
  CATEGORIES("Categories"),
  PRODUCTS("Products"),
  INVENTORY("Inventory"),
  SALES_REPORTS("Sales Reports"),
  PROFIT_REPORTS("Profit Reports"),
  STAFF_MANAGEMENT("Staff Management"),
  ACTIVITY_LOG("Activity Log"),
  SETTINGS("Settings")
}

data class CartItem(
  val product: Product,
  val quantity: Long
) {
  val lineTotal: Double get() = product.sellingPrice * quantity
}
