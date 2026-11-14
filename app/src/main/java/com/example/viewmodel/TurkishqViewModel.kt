package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityLog
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.Invoice
import com.example.data.model.InvoiceLineItem
import com.example.data.model.InvoiceRevision
import com.example.data.model.OperatorSession
import com.example.data.model.PosDestination
import com.example.data.model.Product
import com.example.data.model.ProductCost
import com.example.data.model.StaffAccount
import com.example.data.model.StockMovement
import com.example.data.model.StoreConfig
import com.example.data.repository.TurkishqRepository
import com.example.data.security.PasswordHasher
import com.example.util.IstTime
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed interface StoreBootstrapState {
  data object Loading : StoreBootstrapState
  data object NeedsInitialSetup : StoreBootstrapState
  data class Ready(val storeConfig: StoreConfig, val linkedStaff: StaffAccount?) : StoreBootstrapState
  data class Error(val message: String) : StoreBootstrapState
}

data class PosBillingState(
  val cart: List<CartItem> = emptyList(),
  val selectedCategoryId: String = "ALL",
  val searchQuery: String = "",
  val discountInput: String = "0",
  val isDiscountPercent: Boolean = false,
  val taxPercentInput: String = "5",
  val customerName: String = "",
  val customerPhone: String = "",
  val paymentMethod: String = "Cash",
  val isSubmitting: Boolean = false
) {
  val subtotal: Double get() = cart.sumOf { it.lineTotal }
  val discountAmount: Double
    get() {
      val raw = discountInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
      val amt = if (isDiscountPercent) subtotal * (raw.coerceAtMost(100.0) / 100.0) else raw
      return amt.coerceIn(0.0, subtotal)
    }
  val taxPercent: Double get() = (taxPercentInput.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
  val taxableAmount: Double get() = (subtotal - discountAmount).coerceAtLeast(0.0)
  val taxAmount: Double get() = taxableAmount * (taxPercent / 100.0)
  val grandTotal: Double get() = taxableAmount + taxAmount
}

data class DashboardStats(
  val totalSalesAmount: Double = 0.0,
  val totalBillsCount: Int = 0,
  val totalDistinctProductsSold: Int = 0,
  val totalQuantitySold: Long = 0L,
  val todaySales: Double = 0.0,
  val yesterdaySales: Double = 0.0,
  val thisWeekSales: Double = 0.0,
  val thisMonthSales: Double = 0.0,
  val averageInvoiceValue: Double = 0.0,
  val cancelledInvoicesCount: Int = 0,
  val last7DaysSeries: List<Pair<String, Double>> = emptyList(),
  val paymentBreakdown: Map<String, Double> = emptyMap(),
  val topProducts: List<Pair<String, Double>> = emptyList()
)

class TurkishqViewModel(
  private val repository: TurkishqRepository,
  private val currentUserId: String,
  private val currentUserEmail: String
) : ViewModel() {

  private val _bootstrapState = MutableStateFlow<StoreBootstrapState>(StoreBootstrapState.Loading)
  val bootstrapState: StateFlow<StoreBootstrapState> = _bootstrapState.asStateFlow()

  private val _session = MutableStateFlow<OperatorSession>(OperatorSession.LoggedOut)
  val session: StateFlow<OperatorSession> = _session.asStateFlow()

  private val _currentDestination = MutableStateFlow(PosDestination.DASHBOARD)
  val currentDestination: StateFlow<PosDestination> = _currentDestination.asStateFlow()

  private val _categories = MutableStateFlow<List<Category>>(emptyList())
  val categories: StateFlow<List<Category>> = _categories.asStateFlow()

  private val _products = MutableStateFlow<List<Product>>(emptyList())
  val products: StateFlow<List<Product>> = _products.asStateFlow()

  private val _productCosts = MutableStateFlow<Map<String, ProductCost>>(emptyMap())
  val productCosts: StateFlow<Map<String, ProductCost>> = _productCosts.asStateFlow()

  private val _invoices = MutableStateFlow<List<Invoice>>(emptyList())
  val invoices: StateFlow<List<Invoice>> = _invoices.asStateFlow()

  private val _staffAccounts = MutableStateFlow<List<StaffAccount>>(emptyList())
  val staffAccounts: StateFlow<List<StaffAccount>> = _staffAccounts.asStateFlow()

  private val _stockMovements = MutableStateFlow<List<StockMovement>>(emptyList())
  val stockMovements: StateFlow<List<StockMovement>> = _stockMovements.asStateFlow()

  private val _activityLogs = MutableStateFlow<List<ActivityLog>>(emptyList())
  val activityLogs: StateFlow<List<ActivityLog>> = _activityLogs.asStateFlow()

  private val _billingState = MutableStateFlow(PosBillingState())
  val billingState: StateFlow<PosBillingState> = _billingState.asStateFlow()

  private val _lastGeneratedInvoice = MutableStateFlow<Invoice?>(null)
  val lastGeneratedInvoice: StateFlow<Invoice?> = _lastGeneratedInvoice.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private var storeObserverJob: Job? = null
  private var dataJobs = mutableListOf<Job>()

  init {
    loadBootstrap()
  }

  fun loadBootstrap() {
    storeObserverJob?.cancel()
    _bootstrapState.value = StoreBootstrapState.Loading
    storeObserverJob = viewModelScope.launch {
      try {
        val existing = repository.getStoreConfig()
        if (existing == null) {
          _bootstrapState.value = StoreBootstrapState.NeedsInitialSetup
          return@launch
        }
        val cleanEmail = currentUserEmail.lowercase().trim()
        val isOwnerOrBootstrapped = cleanEmail == existing.ownerEmail.lowercase() ||
          cleanEmail == "kushalchakraborty0077@gmail.com"
        val linkedStaff = if (!isOwnerOrBootstrapped) {
          runCatching { repository.checkStaffLinkedRole(cleanEmail) }.getOrNull()
        } else null

        if (!isOwnerOrBootstrapped && linkedStaff == null) {
          _bootstrapState.value = StoreBootstrapState.Error(
            "This Google account ($cleanEmail) is not registered as Master Admin or an active Staff member in TURKISHQ RETAIL."
          )
          return@launch
        }

        // Observe storeConfig live
        repository.observeStoreConfig()
          .catch { e ->
            val fs = unwrapFirestore(e)
            if (fs == null && e is CancellationException) throw e
            _bootstrapState.value = StoreBootstrapState.Error(fs?.message ?: e.message ?: "Store load error")
          }
          .collect { liveConfig ->
            if (liveConfig == null) {
              _bootstrapState.value = StoreBootstrapState.NeedsInitialSetup
            } else {
              _bootstrapState.value = StoreBootstrapState.Ready(liveConfig, linkedStaff)
              _billingState.update { bs ->
                if (bs.cart.isEmpty()) {
                  bs.copy(taxPercentInput = formatCleanNum(liveConfig.defaultTaxPercent))
                } else bs
              }
            }
          }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val fs = unwrapFirestore(e)
        Log.e(TAG, "Bootstrap failed", fs ?: e)
        _bootstrapState.value = StoreBootstrapState.Error(fs?.message ?: e.message ?: "Unable to connect to store database")
      }
    }
  }

  fun initializeMasterAdminStore(
    businessName: String,
    businessAddress: String,
    contactNumber: String,
    adminUsername: String,
    adminPassword: String,
    invoicePrefix: String,
    defaultTaxPercent: Double
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.initializeStore(
        businessName = businessName,
        businessAddress = businessAddress,
        contactNumber = contactNumber,
        adminUsername = adminUsername,
        adminPassword = adminPassword,
        invoicePrefix = invoicePrefix,
        defaultTaxPercent = defaultTaxPercent
      ).onSuccess { config ->
        _session.value = OperatorSession.MasterAdmin(
          username = config.adminUsername,
          displayName = "Master Admin (${config.adminUsername})"
        )
        _currentDestination.value = PosDestination.DASHBOARD
        startDataListeners(config, _session.value)
        loadBootstrap()
        _snackbarMessage.value = "TURKISHQ RETAIL store initialized!"
      }.onFailure { e ->
        _snackbarMessage.value = e.message ?: "Setup failed"
      }
      _isBusy.value = false
    }
  }

  fun loginAsMasterAdmin(username: String, password: String): String? {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready
      ?: return "Store is not ready yet."
    if (ready.linkedStaff != null) {
      return "Your Google account is scoped to Staff access only. Master Admin login is restricted."
    }
    val cfg = ready.storeConfig
    if (!cfg.adminUsername.equals(username.trim(), ignoreCase = true)) {
      return "Invalid Master Admin username."
    }
    if (!PasswordHasher.verifyPassword(password, cfg.adminPasswordHash, cfg.adminPasswordSalt)) {
      return "Incorrect Master Admin password."
    }
    val newSession = OperatorSession.MasterAdmin(
      username = cfg.adminUsername,
      displayName = "Master Admin"
    )
    _session.value = newSession
    _currentDestination.value = PosDestination.DASHBOARD
    startDataListeners(cfg, newSession)
    viewModelScope.launch {
      repository.logOperatorAction(cfg, "master_admin", "Master Admin", "master_admin", "LOGIN", "Master Admin logged in")
    }
    return null
  }

  suspend fun loginAsStaff(username: String, password: String): String? {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready
      ?: return "Store is not ready yet."
    val cfg = ready.storeConfig

    val candidateList = if (ready.linkedStaff != null) {
      listOf(ready.linkedStaff)
    } else {
      runCatching { repository.getStaffAccountsOnce(cfg) }.getOrDefault(emptyList())
    }

    val match = candidateList.firstOrNull {
      it.username.equals(username.trim(), ignoreCase = true)
    } ?: return "Staff account '$username' not found."

    if (!match.active) {
      return "This staff account is deactivated. Contact Master Admin."
    }
    if (!PasswordHasher.verifyPassword(password, match.passwordHash, match.passwordSalt)) {
      return "Incorrect staff password."
    }

    val staffSession = OperatorSession.Staff(match)
    _session.value = staffSession
    _currentDestination.value = PosDestination.DASHBOARD
    startDataListeners(cfg, staffSession)
    viewModelScope.launch {
      repository.logOperatorAction(cfg, match.id, match.displayName, "staff", "LOGIN", "Staff @${match.username} logged in")
    }
    return null
  }

  fun logoutOperator() {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready
    val active = _session.value
    if (ready != null && active !is OperatorSession.LoggedOut) {
      viewModelScope.launch {
        repository.logOperatorAction(
          ready.storeConfig,
          active.actorId,
          active.actorName,
          active.roleKey,
          "LOGOUT",
          "${active.actorName} logged out"
        )
      }
    }
    stopDataListeners()
    _session.value = OperatorSession.LoggedOut
    _billingState.value = PosBillingState()
  }

  private fun stopDataListeners() {
    dataJobs.forEach { it.cancel() }
    dataJobs.clear()
  }

  private fun startDataListeners(config: StoreConfig, activeSession: OperatorSession) {
    stopDataListeners()
    val isAdmin = activeSession.isAdmin
    val isOwnerEmail = currentUserEmail.equals(config.ownerEmail, ignoreCase = true)

    dataJobs += viewModelScope.launch {
      repository.observeCategories(config, isAdminView = isAdmin)
        .catch { e -> Log.w(TAG, "Categories stream error", unwrapFirestore(e) ?: e) }
        .collect { _categories.value = it }
    }
    dataJobs += viewModelScope.launch {
      repository.observeProducts(config, isAdminView = isAdmin)
        .catch { e -> Log.w(TAG, "Products stream error", unwrapFirestore(e) ?: e) }
        .collect { _products.value = it }
    }
    if (activeSession.canViewProfitReports) {
      dataJobs += viewModelScope.launch {
        repository.observeProductCosts(config, isOwnerEmail = isOwnerEmail)
          .catch { e -> Log.w(TAG, "ProductCosts stream error", unwrapFirestore(e) ?: e) }
          .collect { costs -> _productCosts.value = costs.associateBy { it.productId } }
      }
    } else {
      _productCosts.value = emptyMap()
    }
    dataJobs += viewModelScope.launch {
      repository.observeInvoices(
        storeConfig = config,
        canViewAll = isAdmin || (activeSession.canViewSalesReports && isOwnerEmail),
        onlyOwnAuth = !isAdmin && !activeSession.canViewSalesReports
      )
        .catch { e -> Log.w(TAG, "Invoices stream error", unwrapFirestore(e) ?: e) }
        .collect { _invoices.value = it }
    }
    if (isAdmin) {
      dataJobs += viewModelScope.launch {
        repository.observeStaffAccounts(config, isOwnerView = true)
          .catch { e -> Log.w(TAG, "StaffAccounts stream error", unwrapFirestore(e) ?: e) }
          .collect { _staffAccounts.value = it }
      }
      dataJobs += viewModelScope.launch {
        repository.observeStockMovements(config)
          .catch { e -> Log.w(TAG, "StockMovements stream error", unwrapFirestore(e) ?: e) }
          .collect { _stockMovements.value = it }
      }
      dataJobs += viewModelScope.launch {
        repository.observeActivityLogs(config)
          .catch { e -> Log.w(TAG, "ActivityLogs stream error", unwrapFirestore(e) ?: e) }
          .collect { _activityLogs.value = it }
      }
    }
  }

  fun navigateTo(dest: PosDestination) {
    val active = _session.value
    if (!active.isAdmin) {
      val allowed = when (dest) {
        PosDestination.DASHBOARD, PosDestination.NEW_BILL, PosDestination.MY_SALES -> true
        PosDestination.SALES_REPORTS -> active.canViewSalesReports
        PosDestination.PROFIT_REPORTS -> active.canViewProfitReports
        else -> false
      }
      if (!allowed) {
        _snackbarMessage.value = "Access denied: Master Admin permission required."
        return
      }
    }
    _currentDestination.value = dest
  }

  // --- POS BILLING ACTIONS ---

  fun selectPosCategory(categoryId: String) {
    _billingState.update { it.copy(selectedCategoryId = categoryId) }
  }

  fun updatePosSearch(query: String) {
    _billingState.update { it.copy(searchQuery = query) }
  }

  fun addToCart(product: Product) {
    val cfg = (_bootstrapState.value as? StoreBootstrapState.Ready)?.storeConfig
    val enforceStock = product.trackStock && (cfg?.stockTrackingEnabled ?: true)
    _billingState.update { state ->
      val existing = state.cart.firstOrNull { it.product.id == product.id }
      val nextQty = (existing?.quantity ?: 0L) + 1L
      if (enforceStock && nextQty > product.stockQty) {
        _snackbarMessage.value = "Only ${product.stockQty} unit(s) available in stock for ${product.name}"
        return@update state
      }
      val newCart = if (existing == null) {
        state.cart + CartItem(product, 1L)
      } else {
        state.cart.map { if (it.product.id == product.id) it.copy(quantity = nextQty, product = product) else it }
      }
      state.copy(cart = newCart)
    }
  }

  fun updateCartQty(productId: String, delta: Long) {
    val cfg = (_bootstrapState.value as? StoreBootstrapState.Ready)?.storeConfig
    _billingState.update { state ->
      val item = state.cart.firstOrNull { it.product.id == productId } ?: return@update state
      val nextQty = item.quantity + delta
      if (nextQty <= 0L) {
        return@update state.copy(cart = state.cart.filterNot { it.product.id == productId })
      }
      val enforceStock = item.product.trackStock && (cfg?.stockTrackingEnabled ?: true)
      if (enforceStock && nextQty > item.product.stockQty) {
        _snackbarMessage.value = "Stock limit reached (${item.product.stockQty}) for ${item.product.name}"
        return@update state
      }
      state.copy(cart = state.cart.map { if (it.product.id == productId) it.copy(quantity = nextQty) else it })
    }
  }

  fun removeFromCart(productId: String) {
    _billingState.update { state ->
      state.copy(cart = state.cart.filterNot { it.product.id == productId })
    }
  }

  fun clearCart() {
    val cfg = (_bootstrapState.value as? StoreBootstrapState.Ready)?.storeConfig
    _billingState.value = PosBillingState(
      taxPercentInput = formatCleanNum(cfg?.defaultTaxPercent ?: 5.0)
    )
  }

  fun updateBillingFields(
    discountInput: String? = null,
    isDiscountPercent: Boolean? = null,
    taxPercentInput: String? = null,
    customerName: String? = null,
    customerPhone: String? = null,
    paymentMethod: String? = null
  ) {
    _billingState.update { state ->
      state.copy(
        discountInput = discountInput ?: state.discountInput,
        isDiscountPercent = isDiscountPercent ?: state.isDiscountPercent,
        taxPercentInput = taxPercentInput ?: state.taxPercentInput,
        customerName = customerName ?: state.customerName,
        customerPhone = customerPhone ?: state.customerPhone,
        paymentMethod = paymentMethod ?: state.paymentMethod
      )
    }
  }

  fun submitBill() {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready ?: return
    val active = _session.value
    if (active is OperatorSession.LoggedOut) return
    val state = _billingState.value
    if (state.cart.isEmpty()) {
      _snackbarMessage.value = "Add at least one product to generate a bill."
      return
    }
    viewModelScope.launch {
      _billingState.update { it.copy(isSubmitting = true) }
      repository.createInvoiceAtomic(
        storeConfig = ready.storeConfig,
        cartItems = state.cart,
        discountAmount = state.discountAmount,
        taxPercent = state.taxPercent,
        customerName = state.customerName,
        customerPhone = state.customerPhone,
        paymentMethod = state.paymentMethod,
        staffId = active.actorId,
        staffName = active.actorName,
        actorRole = active.roleKey
      ).onSuccess { invoice ->
        _lastGeneratedInvoice.value = invoice
        clearCart()
        _snackbarMessage.value = "Invoice ${invoice.invoiceNumber} generated!"
      }.onFailure { e ->
        _snackbarMessage.value = e.message ?: "Failed to generate invoice"
      }
      _billingState.update { it.copy(isSubmitting = false) }
    }
  }

  fun dismissGeneratedInvoice() {
    _lastGeneratedInvoice.value = null
  }

  // --- ADMIN CRUD & ACTIONS ---

  fun saveCategory(categoryId: String?, name: String, sortOrder: Long) {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready ?: return
    val active = _session.value
    if (!active.isAdmin) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.saveCategory(ready.storeConfig, categoryId, name, sortOrder, active.actorName)
        .onSuccess { _snackbarMessage.value = "Category saved" }
        .onFailure { _snackbarMessage.value = it.message ?: "Failed to save category" }
      _isBusy.value = false
    }
  }

  fun deleteCategory(category: Category) {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready ?: return
    val active = _session.value
    if (!active.isAdmin) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.deleteCategory(ready.storeConfig, category, active.actorName)
        .onSuccess { _snackbarMessage.value = "Category deleted" }
        .onFailure { _snackbarMessage.value = it.message ?: "Failed to delete category" }
      _isBusy.value = false
    }
  }

  fun saveProduct(
    existingProduct: Product?,
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
  ) {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready ?: return
    val op = _session.value
    if (!op.isAdmin) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.saveProduct(
        storeConfig = ready.storeConfig,
        existingProduct = existingProduct,
        name = name,
        category = category,
        sellingPrice = sellingPrice,
        purchaseCost = purchaseCost,
        description = description,
        imageKey = imageKey,
        imageBase64 = imageBase64,
        active = active,
        trackStock = trackStock,
        stockQty = stockQty,
        lowStockThreshold = lowStockThreshold,
        actorName = op.actorName
      ).onSuccess { _snackbarMessage.value = "Product saved" }
        .onFailure { _snackbarMessage.value = it.message ?: "Failed to save product" }
      _isBusy.value = false
    }
  }

  fun deleteProduct(product: Product) {
    val op = _session.value
    if (!op.isAdmin) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.deleteProduct(product, op.actorName)
        .onSuccess { _snackbarMessage.value = "Product deleted" }
        .onFailure { _snackbarMessage.value = it.message ?: "Failed to delete product" }
      _isBusy.value = false
    }
  }

  fun loadStarterCatalog() {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready ?: return
    val op = _session.value
    if (!op.isAdmin) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.loadStarterCatalog(ready.storeConfig, op.actorName)
        .onSuccess { _snackbarMessage.value = "Starter categories & menu products added" }
        .onFailure { _snackbarMessage.value = it.message ?: "Failed to load starter menu" }
      _isBusy.value = false
    }
  }

  fun adjustStock(product: Product, type: String, qty: Long, isAbsolute: Boolean, note: String) {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready ?: return
    val op = _session.value
    if (!op.isAdmin) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.adjustProductStock(ready.storeConfig, product, type, qty, isAbsolute, note, op.actorName)
        .onSuccess { _snackbarMessage.value = "Stock updated for ${product.name}" }
        .onFailure { _snackbarMessage.value = it.message ?: "Failed to update stock" }
      _isBusy.value = false
    }
  }

  fun editInvoice(
    original: Invoice,
    updatedItems: List<InvoiceLineItem>,
    discountAmount: Double,
    taxPercent: Double,
    customerName: String,
    customerPhone: String,
    paymentMethod: String,
    onDone: (Invoice) -> Unit
  ) {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready ?: return
    val op = _session.value
    if (!op.isAdmin) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.editInvoice(
        storeConfig = ready.storeConfig,
        originalInvoice = original,
        updatedItems = updatedItems,
        newDiscountAmount = discountAmount,
        newTaxPercent = taxPercent,
        newCustomerName = customerName,
        newCustomerPhone = customerPhone,
        newPaymentMethod = paymentMethod,
        editorName = op.actorName
      ).onSuccess { updated ->
        _snackbarMessage.value = "${updated.invoiceNumber} updated"
        onDone(updated)
      }.onFailure {
        _snackbarMessage.value = it.message ?: "Failed to edit invoice"
      }
      _isBusy.value = false
    }
  }

  fun cancelInvoice(invoice: Invoice, reason: String) {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready ?: return
    val op = _session.value
    if (!op.isAdmin) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.cancelInvoice(ready.storeConfig, invoice, reason, op.actorName)
        .onSuccess { _snackbarMessage.value = "${invoice.invoiceNumber} cancelled" }
        .onFailure { _snackbarMessage.value = it.message ?: "Failed to cancel invoice" }
      _isBusy.value = false
    }
  }

  suspend fun fetchInvoiceRevisions(invoiceId: String): List<InvoiceRevision> {
    return runCatching { repository.getInvoiceRevisions(invoiceId) }.getOrDefault(emptyList())
  }

  fun saveStaff(
    existing: StaffAccount?,
    username: String,
    displayName: String,
    newPassword: String?,
    linkedEmail: String,
    active: Boolean,
    canViewSalesReports: Boolean,
    canViewProfitReports: Boolean
  ) {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready ?: return
    val op = _session.value
    if (!op.isAdmin) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.saveStaffAccount(
        storeConfig = ready.storeConfig,
        existingStaff = existing,
        username = username,
        displayName = displayName,
        newPassword = newPassword,
        linkedEmail = linkedEmail,
        active = active,
        canViewSalesReports = canViewSalesReports,
        canViewProfitReports = canViewProfitReports,
        actorName = op.actorName
      ).onSuccess { _snackbarMessage.value = "Staff account saved" }
        .onFailure { _snackbarMessage.value = it.message ?: "Failed to save staff" }
      _isBusy.value = false
    }
  }

  fun updateSettings(
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
  ) {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready ?: return
    val op = _session.value
    if (!op.isAdmin) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.updateStoreSettings(
        storeConfig = ready.storeConfig,
        businessName = businessName,
        businessAddress = businessAddress,
        contactNumber = contactNumber,
        invoiceFooter = invoiceFooter,
        currencySymbol = currencySymbol,
        invoicePrefix = invoicePrefix,
        defaultTaxPercent = defaultTaxPercent,
        defaultDiscountPercent = defaultDiscountPercent,
        stockTrackingEnabled = stockTrackingEnabled,
        allowStaffSalesDefault = allowStaffSalesDefault,
        allowStaffProfitDefault = allowStaffProfitDefault,
        newAdminPassword = newAdminPassword,
        actorName = op.actorName
      ).onSuccess { _snackbarMessage.value = "Application settings updated" }
        .onFailure { _snackbarMessage.value = it.message ?: "Failed to update settings" }
      _isBusy.value = false
    }
  }

  fun buildBackupJson(): String {
    val ready = _bootstrapState.value as? StoreBootstrapState.Ready
    val root = JSONObject().apply {
      put("exportedAtIst", IstTime.formatDateTime(System.currentTimeMillis()))
      put("businessName", ready?.storeConfig?.businessName ?: "TURKISHQ RETAIL")
      put("invoicePrefix", ready?.storeConfig?.invoicePrefix ?: "TQR")
      put("categoriesCount", _categories.value.size)
      put("productsCount", _products.value.size)
      put("invoicesCount", _invoices.value.size)
      put("categories", JSONArray(_categories.value.map { c -> JSONObject().put("id", c.id).put("name", c.name) }))
      put("products", JSONArray(_products.value.map { p ->
        JSONObject().put("id", p.id).put("name", p.name).put("category", p.categoryName)
          .put("sellingPrice", p.sellingPrice).put("stockQty", p.stockQty).put("active", p.active)
      }))
      put("invoices", JSONArray(_invoices.value.map { inv ->
        JSONObject().put("invoiceNumber", inv.invoiceNumber).put("dateKey", inv.dateKey)
          .put("staffName", inv.staffName).put("grandTotal", inv.grandTotal).put("status", inv.status)
      }))
    }
    return root.toString(2)
  }

  fun clearSnackbar() {
    _snackbarMessage.value = null
  }

  // --- REAL-TIME ANALYTICS HELPERS ---

  fun computeDashboardStats(allInvoices: List<Invoice>): DashboardStats {
    val activeSession = _session.value
    val scoped = if (activeSession is OperatorSession.Staff && !activeSession.canViewSalesReports) {
      allInvoices.filter { it.staffId == activeSession.actorId }
    } else {
      allInvoices
    }

    val completed = scoped.filter { it.status == "completed" }
    val cancelledCount = scoped.count { it.status == "cancelled" }

    val now = System.currentTimeMillis()
    val todayStart = IstTime.startOfDayMillis(now)
    val todayEnd = IstTime.endOfDayMillis(now)
    val (yestStart, yestEnd) = IstTime.yesterdayStartEnd(now)
    val weekStart = IstTime.weekStartMillis(now)
    val monthStart = IstTime.monthStartMillis(now)

    val totalSales = completed.sumOf { it.grandTotal }
    val totalBills = completed.size
    val allItems = completed.flatMap { it.items }
    val distinctProducts = allItems.map { it.productId }.distinct().size
    val totalQty = allItems.sumOf { it.quantity }

    val todaySales = completed.filter { it.epochMillis in todayStart..todayEnd }.sumOf { it.grandTotal }
    val yestSales = completed.filter { it.epochMillis in yestStart..yestEnd }.sumOf { it.grandTotal }
    val weekSales = completed.filter { it.epochMillis >= weekStart }.sumOf { it.grandTotal }
    val monthSales = completed.filter { it.epochMillis >= monthStart }.sumOf { it.grandTotal }
    val avg = if (totalBills > 0) totalSales / totalBills else 0.0

    val last7Keys = IstTime.lastNDaysKeys(7, now)
    val byDate = completed.groupBy { it.dateKey }.mapValues { (_, list) -> list.sumOf { it.grandTotal } }
    val series = last7Keys.map { key -> key.take(5) to (byDate[key] ?: 0.0) }

    val payments = completed.groupBy { it.paymentMethod }.mapValues { (_, list) -> list.sumOf { it.grandTotal } }
    val topProds = allItems.groupBy { it.productName }
      .map { (name, list) -> name to list.sumOf { it.lineTotal } }
      .sortedByDescending { it.second }
      .take(5)

    return DashboardStats(
      totalSalesAmount = totalSales,
      totalBillsCount = totalBills,
      totalDistinctProductsSold = distinctProducts,
      totalQuantitySold = totalQty,
      todaySales = todaySales,
      yesterdaySales = yestSales,
      thisWeekSales = weekSales,
      thisMonthSales = monthSales,
      averageInvoiceValue = avg,
      cancelledInvoicesCount = cancelledCount,
      last7DaysSeries = series,
      paymentBreakdown = payments,
      topProducts = topProds
    )
  }

  private fun unwrapFirestore(t: Throwable): FirebaseFirestoreException? {
    return generateSequence(t) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
  }

  private fun formatCleanNum(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
  }

  private companion object {
    const val TAG = "TurkishqVM"
  }
}
