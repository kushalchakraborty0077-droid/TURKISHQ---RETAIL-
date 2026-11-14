package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Invoice
import com.example.data.model.OperatorSession
import com.example.data.model.PosDestination
import com.example.data.model.StoreConfig
import com.example.ui.components.InvoicePreviewDialog
import com.example.ui.components.TurkishqLogoBadge
import com.example.ui.screens.ActivityLogScreen
import com.example.ui.screens.BillHistoryScreen
import com.example.ui.screens.BillingPosScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.ProfitReportsScreen
import com.example.ui.screens.SalesReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StaffManagementScreen
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.SaffronGold
import com.example.viewmodel.TurkishqViewModel
import kotlinx.coroutines.launch

private data class DrawerNavItem(
  val destination: PosDestination,
  val label: String,
  val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosShell(
  viewModel: TurkishqViewModel,
  storeConfig: StoreConfig,
  session: OperatorSession,
  onSignOutCloud: () -> Unit
) {
  val currentDest by viewModel.currentDestination.collectAsStateWithLifecycle()
  val categories by viewModel.categories.collectAsStateWithLifecycle()
  val products by viewModel.products.collectAsStateWithLifecycle()
  val productCosts by viewModel.productCosts.collectAsStateWithLifecycle()
  val invoices by viewModel.invoices.collectAsStateWithLifecycle()
  val staffAccounts by viewModel.staffAccounts.collectAsStateWithLifecycle()
  val stockMovements by viewModel.stockMovements.collectAsStateWithLifecycle()
  val activityLogs by viewModel.activityLogs.collectAsStateWithLifecycle()
  val billingState by viewModel.billingState.collectAsStateWithLifecycle()
  val lastGeneratedInvoice by viewModel.lastGeneratedInvoice.collectAsStateWithLifecycle()
  val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

  var inspectingInvoice by remember { mutableStateOf<Invoice?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()

  LaunchedEffect(snackbarMessage) {
    snackbarMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearSnackbar()
    }
  }

  // Handle system back on non-dashboard views
  BackHandler(enabled = currentDest != PosDestination.DASHBOARD) {
    viewModel.navigateTo(PosDestination.DASHBOARD)
  }

  val navItems = remember(session) {
    if (session.isAdmin) {
      listOf(
        DrawerNavItem(PosDestination.DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
        DrawerNavItem(PosDestination.NEW_BILL, "New Bill", Icons.Filled.AddShoppingCart),
        DrawerNavItem(PosDestination.BILL_HISTORY, "Bill History", Icons.Filled.ReceiptLong),
        DrawerNavItem(PosDestination.CATEGORIES, "Categories", Icons.Filled.Category),
        DrawerNavItem(PosDestination.PRODUCTS, "Products", Icons.Filled.ShoppingBag),
        DrawerNavItem(PosDestination.INVENTORY, "Inventory", Icons.Filled.Inventory),
        DrawerNavItem(PosDestination.SALES_REPORTS, "Sales Reports", Icons.Filled.Assessment),
        DrawerNavItem(PosDestination.PROFIT_REPORTS, "Profit Reports", Icons.Filled.TrendingUp),
        DrawerNavItem(PosDestination.STAFF_MANAGEMENT, "Staff Management", Icons.Filled.Badge),
        DrawerNavItem(PosDestination.ACTIVITY_LOG, "Activity Log", Icons.Filled.History),
        DrawerNavItem(PosDestination.SETTINGS, "Settings", Icons.Filled.Settings)
      )
    } else {
      buildList {
        add(DrawerNavItem(PosDestination.DASHBOARD, "Dashboard", Icons.Filled.Dashboard))
        add(DrawerNavItem(PosDestination.NEW_BILL, "New Bill", Icons.Filled.AddShoppingCart))
        add(DrawerNavItem(PosDestination.MY_SALES, "My Sales", Icons.Filled.ReceiptLong))
        if (session.canViewSalesReports) {
          add(DrawerNavItem(PosDestination.SALES_REPORTS, "Sales Reports", Icons.Filled.Assessment))
        }
        if (session.canViewProfitReports) {
          add(DrawerNavItem(PosDestination.PROFIT_REPORTS, "Profit Reports", Icons.Filled.TrendingUp))
        }
      }
    }
  }

  val dashboardStats = remember(invoices, session) {
    viewModel.computeDashboardStats(invoices)
  }

  @Composable
  fun DrawerContent(closeOnSelect: Boolean) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(12.dp)
    ) {
      Surface(
        color = EmeraldDeep,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          TurkishqLogoBadge(size = 42.dp)
          Column {
            Text(
              storeConfig.businessName,
              style = MaterialTheme.typography.titleSmall,
              color = Color.White,
              fontWeight = FontWeight.Bold
            )
            Text(
              session.actorName,
              style = MaterialTheme.typography.bodySmall,
              color = SaffronGold
            )
            Text(
              if (session.isAdmin) "MASTER ADMIN" else "STAFF COUNTER",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.75f)
            )
          }
        }
      }

      Spacer(Modifier.height(12.dp))

      navItems.forEach { item ->
        NavigationDrawerItem(
          label = { Text(item.label) },
          selected = currentDest == item.destination,
          onClick = {
            viewModel.navigateTo(item.destination)
            if (closeOnSelect) scope.launch { drawerState.close() }
          },
          icon = { Icon(item.icon, contentDescription = null) },
          modifier = Modifier.testTag("nav_${item.destination.name.lowercase()}")
        )
      }

      HorizontalDivider(Modifier.padding(vertical = 8.dp))

      NavigationDrawerItem(
        label = { Text("Switch Operator / Logout", color = CrimsonError) },
        selected = false,
        onClick = {
          if (closeOnSelect) scope.launch { drawerState.close() }
          viewModel.logoutOperator()
        },
        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CrimsonError) },
        modifier = Modifier.testTag("nav_logout")
      )
      NavigationDrawerItem(
        label = { Text("Sign Out Google Cloud") },
        selected = false,
        onClick = {
          if (closeOnSelect) scope.launch { drawerState.close() }
          onSignOutCloud()
        },
        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) }
      )
    }
  }

  @Composable
  fun ActiveScreenContent(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
      when (currentDest) {
        PosDestination.DASHBOARD -> DashboardScreen(
          storeConfig = storeConfig,
          session = session,
          stats = dashboardStats,
          products = products,
          recentInvoices = invoices.let {
            if (session.isAdmin || session.canViewSalesReports) it else it.filter { inv -> inv.staffId == session.actorId }
          },
          onNavigate = { viewModel.navigateTo(it) },
          onOpenInvoice = { inspectingInvoice = it }
        )

        PosDestination.NEW_BILL -> BillingPosScreen(
          storeConfig = storeConfig,
          categories = categories,
          products = products,
          billingState = billingState,
          onSelectCategory = viewModel::selectPosCategory,
          onSearchChange = viewModel::updatePosSearch,
          onAddToCart = viewModel::addToCart,
          onUpdateQty = viewModel::updateCartQty,
          onRemoveFromCart = viewModel::removeFromCart,
          onClearCart = viewModel::clearCart,
          onUpdateFields = viewModel::updateBillingFields,
          onGenerateBill = viewModel::submitBill
        )

        PosDestination.BILL_HISTORY, PosDestination.MY_SALES -> BillHistoryScreen(
          storeConfig = storeConfig,
          session = session,
          invoices = invoices,
          products = products,
          onlyMySales = currentDest == PosDestination.MY_SALES || !session.isAdmin,
          onOpenInvoice = { inspectingInvoice = it },
          onEditInvoice = { orig, items, disc, tax, custName, custPhone, payMethod ->
            viewModel.editInvoice(orig, items, disc, tax, custName, custPhone, payMethod) { updated ->
              inspectingInvoice = updated
            }
          },
          onCancelInvoice = { inv, reason -> viewModel.cancelInvoice(inv, reason) },
          onFetchRevisions = { invId -> viewModel.fetchInvoiceRevisions(invId) }
        )

        PosDestination.CATEGORIES -> CategoriesScreen(
          categories = categories,
          products = products,
          onSaveCategory = viewModel::saveCategory,
          onDeleteCategory = viewModel::deleteCategory,
          onLoadStarterCatalog = viewModel::loadStarterCatalog
        )

        PosDestination.PRODUCTS -> ProductsScreen(
          storeConfig = storeConfig,
          categories = categories,
          products = products,
          productCosts = productCosts,
          onSaveProduct = viewModel::saveProduct,
          onDeleteProduct = viewModel::deleteProduct,
          onLoadStarterCatalog = viewModel::loadStarterCatalog
        )

        PosDestination.INVENTORY -> InventoryScreen(
          storeConfig = storeConfig,
          products = products,
          stockMovements = stockMovements,
          onAdjustStock = viewModel::adjustStock
        )

        PosDestination.SALES_REPORTS -> SalesReportsScreen(
          storeConfig = storeConfig,
          invoices = invoices,
          categories = categories,
          staffAccounts = staffAccounts,
          isAdmin = session.isAdmin
        )

        PosDestination.PROFIT_REPORTS -> ProfitReportsScreen(
          storeConfig = storeConfig,
          invoices = invoices,
          productCosts = productCosts,
          categories = categories
        )

        PosDestination.STAFF_MANAGEMENT -> StaffManagementScreen(
          storeConfig = storeConfig,
          staffAccounts = staffAccounts,
          invoices = invoices,
          onSaveStaff = viewModel::saveStaff,
          onOpenInvoice = { inspectingInvoice = it }
        )

        PosDestination.ACTIVITY_LOG -> ActivityLogScreen(logs = activityLogs)

        PosDestination.SETTINGS -> SettingsScreen(
          storeConfig = storeConfig,
          onSaveSettings = viewModel::updateSettings,
          onExportBackupJson = viewModel::buildBackupJson
        )
      }
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isExpanded = maxWidth >= 840.dp

    if (isExpanded) {
      PermanentNavigationDrawer(
        drawerContent = {
          PermanentDrawerSheet(modifier = Modifier.width(268.dp)) {
            DrawerContent(closeOnSelect = false)
          }
        }
      ) {
        Scaffold(
          contentWindowInsets = WindowInsets.safeDrawing,
          snackbarHost = { SnackbarHost(snackbarHostState) },
          topBar = {
            TopAppBar(
              title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text(currentDest.title, fontWeight = FontWeight.Bold)
                  Surface(
                    color = (if (session.isAdmin) SaffronGold else MaterialTheme.colorScheme.primary).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      if (session.isAdmin) "MASTER ADMIN" else session.actorName,
                      style = MaterialTheme.typography.labelSmall,
                      color = if (session.isAdmin) SaffronGold else MaterialTheme.colorScheme.primary,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                  }
                }
              },
              colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
          }
        ) { innerPadding ->
          ActiveScreenContent(Modifier.padding(innerPadding))
        }
      }
    } else {
      val bottomBarItems = remember(navItems) { navItems.take(4) }
      ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
          ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
            DrawerContent(closeOnSelect = true)
          }
        }
      ) {
        Scaffold(
          contentWindowInsets = WindowInsets.safeDrawing,
          snackbarHost = { SnackbarHost(snackbarHostState) },
          topBar = {
            TopAppBar(
              navigationIcon = {
                IconButton(
                  onClick = { scope.launch { drawerState.open() } },
                  modifier = Modifier.testTag("open_drawer_button")
                ) {
                  Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                }
              },
              title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  TurkishqLogoBadge(size = 28.dp)
                  Text(currentDest.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
              },
              colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
          },
          bottomBar = {
            NavigationBar {
              bottomBarItems.forEach { item ->
                NavigationBarItem(
                  selected = currentDest == item.destination,
                  onClick = { viewModel.navigateTo(item.destination) },
                  icon = { Icon(item.icon, contentDescription = item.label) },
                  label = { Text(item.label, maxLines = 1) }
                )
              }
            }
          }
        ) { innerPadding ->
          ActiveScreenContent(Modifier.padding(innerPadding))
        }
      }
    }
  }

  val activePreview = inspectingInvoice ?: lastGeneratedInvoice
  activePreview?.let { inv ->
    InvoicePreviewDialog(
      storeConfig = storeConfig,
      invoice = inv,
      onDismiss = {
        if (inspectingInvoice != null) inspectingInvoice = null
        else viewModel.dismissGeneratedInvoice()
      }
    )
  }
}
