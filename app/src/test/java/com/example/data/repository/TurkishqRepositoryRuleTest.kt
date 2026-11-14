package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishqRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun fullPosWorkflow_initializeCreateCatalogAtomicInvoicesEditAndCancel() = runBlocking {
    val ownerEmail = "kushalchakraborty0077@gmail.com"
    signInTestUser(ownerEmail, verifyEmail = true)
    val repo = TurkishqRepository(firestore, auth)

    // 1. Initialize store & Master Admin
    val storeConfig = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.initializeStore(
        businessName = "TURKISHQ RETAIL",
        businessAddress = "Indiranagar, Bengaluru",
        contactNumber = "+91 9876500001",
        adminUsername = "masteradmin",
        adminPassword = "AdminPass123",
        invoicePrefix = "TQR",
        defaultTaxPercent = 5.0
      ).getOrThrow()
    }
    assertEquals("TURKISHQ RETAIL", storeConfig.businessName)

    // 2. Create category & product
    val catId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveCategory(storeConfig, null, "Baklava", 1L, "Master Admin").getOrThrow()
    }
    val category = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeCategories(storeConfig, isAdminView = true).first { list -> list.any { it.id == catId } }
    }.first { it.id == catId }

    val prodId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveProduct(
        storeConfig = storeConfig,
        existingProduct = null,
        name = "Pistachio Baklava",
        category = category,
        sellingPrice = 120.0,
        purchaseCost = 60.0,
        description = "Fresh pistachio baklava",
        imageKey = "baklava",
        imageBase64 = "",
        active = true,
        trackStock = true,
        stockQty = 30L,
        lowStockThreshold = 5L,
        actorName = "Master Admin"
      ).getOrThrow()
    }
    val product = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeProducts(storeConfig, isAdminView = true).first { list -> list.any { it.id == prodId } }
    }.first { it.id == prodId }

    // 3. Create two sequential atomic invoices
    val inv1 = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createInvoiceAtomic(
        storeConfig = storeConfig,
        cartItems = listOf(CartItem(product, 3L)),
        discountAmount = 0.0,
        taxPercent = 5.0,
        customerName = "Aarav",
        customerPhone = "9876543210",
        paymentMethod = "UPI",
        staffId = "master_admin",
        staffName = "Master Admin",
        actorRole = "master_admin"
      ).getOrThrow()
    }
    val inv2 = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createInvoiceAtomic(
        storeConfig = storeConfig,
        cartItems = listOf(CartItem(product, 2L)),
        discountAmount = 20.0,
        taxPercent = 5.0,
        customerName = "Meera",
        customerPhone = "",
        paymentMethod = "Cash",
        staffId = "master_admin",
        staffName = "Master Admin",
        actorRole = "master_admin"
      ).getOrThrow()
    }
    assertNotEquals(inv1.invoiceNumber, inv2.invoiceNumber)
    assertEquals(inv1.sequenceNumber + 1L, inv2.sequenceNumber)

    // Verify stock deducted (30 - 3 - 2 = 25)
    val updatedProd = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeProducts(storeConfig, isAdminView = true).first { list ->
        list.firstOrNull { it.id == prodId }?.stockQty == 25L
      }
    }.first { it.id == prodId }
    assertEquals(25L, updatedProd.stockQty)

    // 4. Update product price and verify old invoice retains original price
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveProduct(
        storeConfig = storeConfig,
        existingProduct = updatedProd,
        name = updatedProd.name,
        category = category,
        sellingPrice = 150.0,
        purchaseCost = 65.0,
        description = updatedProd.description,
        imageKey = updatedProd.imageKey,
        imageBase64 = "",
        active = true,
        trackStock = true,
        stockQty = updatedProd.stockQty,
        lowStockThreshold = 5L,
        actorName = "Master Admin"
      ).getOrThrow()
    }
    val fetchedInv1 = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeInvoices(storeConfig, canViewAll = true, onlyOwnAuth = false)
        .first { list -> list.any { it.id == inv1.id } }
    }.first { it.id == inv1.id }
    assertEquals(120.0, fetchedInv1.items.first().unitPrice, 0.01)

    // 5. Edit inv1 quantity (3 -> 4) & verify invoiceNumber preserved + stock adjusted (25 -> 24)
    val editedInv1 = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.editInvoice(
        storeConfig = storeConfig,
        originalInvoice = fetchedInv1,
        updatedItems = listOf(fetchedInv1.items.first().copy(quantity = 4L)),
        newDiscountAmount = 0.0,
        newTaxPercent = 5.0,
        newCustomerName = "Aarav",
        newCustomerPhone = "9876543210",
        newPaymentMethod = "Card",
        editorName = "Master Admin"
      ).getOrThrow()
    }
    assertEquals(inv1.invoiceNumber, editedInv1.invoiceNumber)
    assertEquals(1L, editedInv1.editCount)

    // 6. Soft-cancel inv2 and verify stock restored (24 + 2 = 26)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.cancelInvoice(storeConfig, inv2, "Test cancellation", "Master Admin").getOrThrow()
    }
    val finalProd = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeProducts(storeConfig, isAdminView = true).first { list ->
        list.firstOrNull { it.id == prodId }?.stockQty == 26L
      }
    }.first { it.id == prodId }
    assertEquals(26L, finalProd.stockQty)
  }

  @Test
  fun staffSecurityBoundaries_andUnauthenticatedRejection() = runBlocking {
    val ownerEmail = "kushalchakraborty0077@gmail.com"
    signInTestUser(ownerEmail, verifyEmail = true)
    val adminRepo = TurkishqRepository(firestore, auth)
    val storeConfig = withTimeout(DEFAULT_TIMEOUT_MS) {
      adminRepo.initializeStore(
        businessName = "TURKISHQ RETAIL",
        businessAddress = "MG Road",
        contactNumber = "9999999999",
        adminUsername = "admin",
        adminPassword = "Password1"
      ).getOrThrow()
    }
    val staffEmail = "staff_cashier@turkishq.test"
    withTimeout(DEFAULT_TIMEOUT_MS) {
      adminRepo.saveStaffAccount(
        storeConfig = storeConfig,
        existingStaff = null,
        username = "cashier1",
        displayName = "Cashier One",
        newPassword = "StaffPass1",
        linkedEmail = staffEmail,
        active = true,
        canViewSalesReports = false,
        canViewProfitReports = false,
        actorName = "Master Admin"
      ).getOrThrow()
    }

    // Switch to staff user
    signInTestUser(staffEmail, verifyEmail = true)
    val staffRepo = TurkishqRepository(firestore, auth)

    // Staff CANNOT create category
    val catRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      staffRepo.saveCategory(storeConfig, null, "UnauthorizedCat", 1L, "Cashier One")
    }
    assertTrue(catRes.isFailure)
    val catEx = catRes.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, catEx?.code)

    // Staff's own profitViewerEmails-scoped query returns 0 documents (costs hidden)
    val scopedCosts = withTimeout(FLOW_TIMEOUT_MS) {
      staffRepo.observeProductCosts(storeConfig, isOwnerEmail = false).first()
    }
    assertTrue(scopedCosts.isEmpty())

    // Staff CANNOT bypass repository scoping to list owner-wide productCosts (must fail with PERMISSION_DENIED)
    val costException = runCatching {
      withTimeout(FLOW_TIMEOUT_MS) {
        firestore.collection("productCosts")
          .whereEqualTo("ownerEmail", storeConfig.ownerEmail)
          .get()
          .await()
      }
    }.exceptionOrNull()
    val costFsEx = costException?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, costFsEx?.code)

    // Staff CANNOT directly read a productCosts document even if they know its ID
    val directCostGetEx = runCatching {
      withTimeout(FLOW_TIMEOUT_MS) {
        firestore.collection("productCosts").document("prod_pistachio_baklava").get().await()
      }
    }.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, directCostGetEx?.code)

    // Unauthenticated caller rejected
    auth.signOut()
    val unauthException = runCatching {
      withTimeout(FLOW_TIMEOUT_MS) {
        staffRepo.observeProducts(storeConfig, isAdminView = false).first()
      }
    }.exceptionOrNull()
    assertTrue(unauthException != null)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 8000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}
