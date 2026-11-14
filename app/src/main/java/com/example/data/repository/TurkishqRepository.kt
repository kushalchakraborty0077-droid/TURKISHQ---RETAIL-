package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ActivityLog
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.Invoice
import com.example.data.model.InvoiceCost
import com.example.data.model.InvoiceItemCost
import com.example.data.model.InvoiceLineItem
import com.example.data.model.InvoiceRevision
import com.example.data.model.Product
import com.example.data.model.ProductCost
import com.example.data.model.StaffAccount
import com.example.data.model.StockMovement
import com.example.data.model.StoreConfig
import com.example.data.security.PasswordHasher
import com.example.util.IstTime
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TurkishqRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    FirebaseAuth.getInstance()
  )

  private fun requireUser(): Pair<String, String> {
    val user = auth.currentUser
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    val email = user.email?.lowercase()?.trim()
      ?: throw IllegalStateException("Authenticated Google user has no email.")
    return user.uid to email
  }

  private fun <T> safeSnapshotError(
    flow: Flow<T>,
    operationType: OperationType,
    path: String,
    expectedUid: String
  ): Flow<T> = flow.catch { error ->
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val toLog = firestoreError ?: (error as? Exception)
    if (toLog != null && auth.currentUser?.uid == expectedUid) {
      handleFirestoreError(toLog, operationType, path)
    }
    throw error
  }

  suspend fun getStoreConfig(): StoreConfig? {
    requireUser()
    val ref = db.collection("storeConfig").document("main")
    return try {
      val snap = ref.get().await()
      if (snap.exists()) {
        snap.toObject(StoreConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } else null
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, ref.path)
      throw e
    }
  }

  fun observeStoreConfig(): Flow<StoreConfig?> = flow {
    val (uid, _) = requireUser()
    val ref = db.collection("storeConfig").document("main")
    emitAll(
      safeSnapshotError(
        ref.snapshots().map { snap ->
          if (snap.exists()) snap.toObject(StoreConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          else null
        },
        OperationType.GET,
        ref.path,
        uid
      )
    )
  }

  suspend fun initializeStore(
    businessName: String,
    businessAddress: String,
    contactNumber: String,
    adminUsername: String,
    adminPassword: String,
    invoicePrefix: String = "TQR",
    defaultTaxPercent: Double = 5.0
  ): Result<StoreConfig> = runCatching {
    val (uid, email) = requireUser()
    val hashed = PasswordHasher.hashPassword(adminPassword)
    val nowTs = FieldValue.serverTimestamp()

    val adminRef = db.collection("admins").document(uid)
    if (!adminRef.get().await().exists()) {
      val adminDoc = mapOf(
        "uid" to uid,
        "email" to email,
        "createdAt" to nowTs
      )
      adminRef.set(adminDoc).await()
    }

    val configPayload = mapOf(
      "ownerUid" to uid,
      "ownerEmail" to email,
      "businessName" to businessName.trim().ifEmpty { "TURKISHQ RETAIL" },
      "businessAddress" to businessAddress.trim(),
      "contactNumber" to contactNumber.trim(),
      "invoiceFooter" to "Thank you for visiting ${businessName.trim().ifEmpty { "TURKISHQ RETAIL" }}!",
      "currencySymbol" to "₹",
      "invoicePrefix" to invoicePrefix.trim().uppercase().ifEmpty { "TQR" },
      "defaultTaxPercent" to defaultTaxPercent.coerceIn(0.0, 100.0),
      "defaultDiscountPercent" to 0.0,
      "stockTrackingEnabled" to true,
      "allowStaffViewSalesReportsDefault" to true,
      "allowStaffViewProfitReportsDefault" to false,
      "adminUsername" to adminUsername.trim(),
      "adminPasswordHash" to hashed.hash,
      "adminPasswordSalt" to hashed.salt,
      "memberEmails" to listOf(email),
      "salesViewerEmails" to listOf(email),
      "profitViewerEmails" to listOf(email),
      "createdAt" to nowTs,
      "updatedAt" to nowTs
    )
    db.collection("storeConfig").document("main").set(configPayload).await()

    val counterRef = db.collection("counters").document("invoices")
    if (!counterRef.get().await().exists()) {
      counterRef.set(
        mapOf(
          "ownerEmail" to email,
          "nextNumber" to 1L,
          "year" to IstTime.currentYear().toLong(),
          "updatedAt" to nowTs
        )
      ).await()
    }

    logActivityInternal(
      ownerUid = uid,
      ownerEmail = email,
      actorId = "master_admin",
      actorName = adminUsername.trim(),
      actorRole = "master_admin",
      action = "STORE_INITIALIZED",
      details = "Initialized TURKISHQ RETAIL store & Master Admin account"
    )

    getStoreConfig() ?: throw IllegalStateException("Failed to read initialized store config")
  }.onFailure { e ->
    if (e !is CancellationException) {
      handleFirestoreError(e, OperationType.WRITE, "storeConfig/main")
    }
  }

  suspend fun checkStaffLinkedRole(email: String): StaffAccount? {
    val cleanEmail = email.lowercase().trim()
    val snap = db.collection("staffAccounts")
      .whereEqualTo("linkedEmail", cleanEmail)
      .whereEqualTo("active", true)
      .get()
      .await()
    return snap.documents.firstOrNull()?.toObject(
      StaffAccount::class.java,
      DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
    )
  }

  // --- REALTIME OBSERVATION STREAMS ---

  fun observeCategories(storeConfig: StoreConfig, isAdminView: Boolean): Flow<List<Category>> = flow {
    val (uid, email) = requireUser()
    val query = if (isAdminView && email == storeConfig.ownerEmail) {
      db.collection("categories").whereEqualTo("ownerEmail", storeConfig.ownerEmail)
    } else {
      db.collection("categories").whereArrayContains("memberEmails", email)
    }
    emitAll(
      safeSnapshotError(
        query.snapshots().map { snap ->
          snap.toObjects(Category::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.name.lowercase() })
        },
        OperationType.LIST,
        "categories",
        uid
      )
    )
  }

  fun observeProducts(storeConfig: StoreConfig, isAdminView: Boolean): Flow<List<Product>> = flow {
    val (uid, email) = requireUser()
    val query = if (isAdminView && email == storeConfig.ownerEmail) {
      db.collection("products").whereEqualTo("ownerEmail", storeConfig.ownerEmail)
    } else {
      db.collection("products").whereArrayContains("memberEmails", email)
    }
    emitAll(
      safeSnapshotError(
        query.snapshots().map { snap ->
          snap.toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.name.lowercase() }
        },
        OperationType.LIST,
        "products",
        uid
      )
    )
  }

  fun observeProductCosts(storeConfig: StoreConfig, isOwnerEmail: Boolean): Flow<List<ProductCost>> = flow {
    val (uid, email) = requireUser()
    val query = if (isOwnerEmail && email == storeConfig.ownerEmail) {
      db.collection("productCosts").whereEqualTo("ownerEmail", storeConfig.ownerEmail)
    } else {
      db.collection("productCosts").whereArrayContains("profitViewerEmails", email)
    }
    emitAll(
      safeSnapshotError(
        query.snapshots().map { snap ->
          snap.toObjects(ProductCost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        },
        OperationType.LIST,
        "productCosts",
        uid
      )
    )
  }

  fun observeInvoices(
    storeConfig: StoreConfig,
    canViewAll: Boolean,
    onlyOwnAuth: Boolean
  ): Flow<List<Invoice>> = flow {
    val (uid, email) = requireUser()
    val query = when {
      canViewAll && email == storeConfig.ownerEmail ->
        db.collection("invoices").whereEqualTo("storeOwnerEmail", storeConfig.ownerEmail)
      onlyOwnAuth ->
        db.collection("invoices").whereEqualTo("staffAuthEmail", email)
      else ->
        db.collection("invoices").whereArrayContains("reportViewerEmails", email)
    }
    emitAll(
      safeSnapshotError(
        query.snapshots().map { snap ->
          snap.toObjects(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.epochMillis }
        },
        OperationType.LIST,
        "invoices",
        uid
      )
    )
  }

  fun observeStaffAccounts(storeConfig: StoreConfig, isOwnerView: Boolean): Flow<List<StaffAccount>> = flow {
    val (uid, email) = requireUser()
    val query = if (isOwnerView && email == storeConfig.ownerEmail) {
      db.collection("staffAccounts").whereEqualTo("ownerEmail", storeConfig.ownerEmail)
    } else {
      db.collection("staffAccounts").whereEqualTo("linkedEmail", email)
    }
    emitAll(
      safeSnapshotError(
        query.snapshots().map { snap ->
          snap.toObjects(StaffAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.displayName.lowercase() }
        },
        OperationType.LIST,
        "staffAccounts",
        uid
      )
    )
  }

  suspend fun getStaffAccountsOnce(storeConfig: StoreConfig): List<StaffAccount> {
    requireUser()
    val snap = db.collection("staffAccounts")
      .whereEqualTo("ownerEmail", storeConfig.ownerEmail)
      .get()
      .await()
    return snap.toObjects(StaffAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      .sortedBy { it.displayName.lowercase() }
  }

  fun observeStockMovements(storeConfig: StoreConfig): Flow<List<StockMovement>> = flow {
    val (uid, _) = requireUser()
    val query = db.collection("stockMovements")
      .whereEqualTo("ownerEmail", storeConfig.ownerEmail)
    emitAll(
      safeSnapshotError(
        query.snapshots().map { snap ->
          snap.toObjects(StockMovement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        },
        OperationType.LIST,
        "stockMovements",
        uid
      )
    )
  }

  fun observeActivityLogs(storeConfig: StoreConfig): Flow<List<ActivityLog>> = flow {
    val (uid, _) = requireUser()
    val query = db.collection("activityLogs")
      .whereEqualTo("ownerEmail", storeConfig.ownerEmail)
    emitAll(
      safeSnapshotError(
        query.snapshots().map { snap ->
          snap.toObjects(ActivityLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        },
        OperationType.LIST,
        "activityLogs",
        uid
      )
    )
  }

  // --- CATEGORY CRUD (Master Admin) ---

  suspend fun saveCategory(
    storeConfig: StoreConfig,
    categoryId: String?,
    name: String,
    sortOrder: Long,
    actorName: String
  ): Result<String> = runCatching {
    val (uid, email) = requireUser()
    val id = categoryId?.takeIf { it.isNotBlank() } ?: "cat_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = db.collection("categories").document(id)
    val nowTs = FieldValue.serverTimestamp()

    if (categoryId.isNullOrBlank()) {
      docRef.set(
        mapOf(
          "ownerUid" to uid,
          "ownerEmail" to email,
          "memberEmails" to storeConfig.memberEmails.ifEmpty { listOf(email) },
          "name" to name.trim(),
          "sortOrder" to sortOrder,
          "createdAt" to nowTs,
          "updatedAt" to nowTs
        )
      ).await()
      logActivityInternal(uid, email, "master_admin", actorName, "master_admin", "CREATE_CATEGORY", "Created category '${name.trim()}'")
    } else {
      val existing = docRef.get().await()
      val oldName = existing.getString("name") ?: ""
      docRef.update(
        mapOf(
          "name" to name.trim(),
          "sortOrder" to sortOrder,
          "updatedAt" to nowTs
        )
      ).await()
      // Update denormalized categoryName on products in this category
      val prods = db.collection("products")
        .whereEqualTo("ownerEmail", storeConfig.ownerEmail)
        .whereEqualTo("categoryId", id)
        .get()
        .await()
      if (!prods.isEmpty) {
        val batch = db.batch()
        prods.documents.forEach { p ->
          batch.update(p.reference, mapOf("categoryName" to name.trim(), "updatedAt" to nowTs))
        }
        batch.commit().await()
      }
      logActivityInternal(uid, email, "master_admin", actorName, "master_admin", "UPDATE_CATEGORY", "Renamed category '$oldName' to '${name.trim()}'")
    }
    id
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.WRITE, "categories")
  }

  suspend fun deleteCategory(
    storeConfig: StoreConfig,
    category: Category,
    actorName: String
  ): Result<Unit> = runCatching {
    val (uid, email) = requireUser()
    val prods = db.collection("products")
      .whereEqualTo("ownerEmail", storeConfig.ownerEmail)
      .whereEqualTo("categoryId", category.id)
      .get()
      .await()
    require(prods.isEmpty) { "Cannot delete category '${category.name}' while it still has ${prods.size()} product(s)." }
    db.collection("categories").document(category.id).delete().await()
    logActivityInternal(uid, email, "master_admin", actorName, "master_admin", "DELETE_CATEGORY", "Deleted category '${category.name}'")
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.DELETE, "categories/${category.id}")
  }

  // --- PRODUCT CRUD & PRICE CONTROL (Master Admin) ---

  suspend fun saveProduct(
    storeConfig: StoreConfig,
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
    lowStockThreshold: Long,
    actorName: String
  ): Result<String> = runCatching {
    val (uid, email) = requireUser()
    val id = existingProduct?.id?.takeIf { it.isNotBlank() }
      ?: "prod_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = db.collection("products").document(id)
    val costRef = db.collection("productCosts").document(id)
    val nowTs = FieldValue.serverTimestamp()

    if (existingProduct == null) {
      docRef.set(
        mapOf(
          "ownerUid" to uid,
          "ownerEmail" to email,
          "memberEmails" to storeConfig.memberEmails.ifEmpty { listOf(email) },
          "name" to name.trim(),
          "categoryId" to category.id,
          "categoryName" to category.name,
          "sellingPrice" to sellingPrice,
          "description" to description.trim(),
          "imageKey" to imageKey,
          "imageBase64" to imageBase64,
          "active" to active,
          "trackStock" to trackStock,
          "stockQty" to stockQty.coerceAtLeast(0L),
          "lowStockThreshold" to lowStockThreshold.coerceAtLeast(0L),
          "createdAt" to nowTs,
          "updatedAt" to nowTs
        )
      ).await()

      if (purchaseCost != null) {
        costRef.set(
          mapOf(
            "ownerUid" to uid,
            "ownerEmail" to email,
            "profitViewerEmails" to storeConfig.profitViewerEmails.ifEmpty { listOf(email) },
            "productId" to id,
            "purchaseCost" to purchaseCost.coerceAtLeast(0.0),
            "createdAt" to nowTs,
            "updatedAt" to nowTs
          )
        ).await()
      }

      if (trackStock && stockQty > 0L) {
        writeStockMovement(
          ownerUid = uid,
          ownerEmail = email,
          productId = id,
          productName = name.trim(),
          type = "initial",
          deltaQty = stockQty,
          resultingQty = stockQty,
          referenceId = "initial",
          performedBy = actorName,
          note = "Initial stock on product creation"
        )
      }
      logActivityInternal(uid, email, "master_admin", actorName, "master_admin", "CREATE_PRODUCT", "Added '${name.trim()}' at ${IstTime.formatInr(sellingPrice)}")
    } else {
      val updates = mapOf(
        "name" to name.trim(),
        "categoryId" to category.id,
        "categoryName" to category.name,
        "sellingPrice" to sellingPrice,
        "description" to description.trim(),
        "imageKey" to imageKey,
        "imageBase64" to imageBase64,
        "active" to active,
        "trackStock" to trackStock,
        "stockQty" to stockQty.coerceAtLeast(0L),
        "lowStockThreshold" to lowStockThreshold.coerceAtLeast(0L),
        "updatedAt" to nowTs
      )
      docRef.update(updates).await()

      if (purchaseCost != null) {
        val costSnap = costRef.get().await()
        if (costSnap.exists()) {
          costRef.update(
            mapOf(
              "purchaseCost" to purchaseCost.coerceAtLeast(0.0),
              "updatedAt" to nowTs
            )
          ).await()
        } else {
          costRef.set(
            mapOf(
              "ownerUid" to uid,
              "ownerEmail" to email,
              "profitViewerEmails" to storeConfig.profitViewerEmails.ifEmpty { listOf(email) },
              "productId" to id,
              "purchaseCost" to purchaseCost.coerceAtLeast(0.0),
              "createdAt" to nowTs,
              "updatedAt" to nowTs
            )
          ).await()
        }
      }

      val details = buildString {
        append("Updated '${name.trim()}'")
        if (existingProduct.sellingPrice != sellingPrice) {
          append(" (Price: ${IstTime.formatInr(existingProduct.sellingPrice)} -> ${IstTime.formatInr(sellingPrice)})")
        }
      }
      logActivityInternal(
        uid, email, "master_admin", actorName, "master_admin",
        if (existingProduct.sellingPrice != sellingPrice) "UPDATE_PRICE" else "UPDATE_PRODUCT",
        details
      )
    }
    id
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.WRITE, "products")
  }

  suspend fun deleteProduct(product: Product, actorName: String): Result<Unit> = runCatching {
    val (uid, email) = requireUser()
    db.collection("products").document(product.id).delete().await()
    runCatching { db.collection("productCosts").document(product.id).delete().await() }
    logActivityInternal(uid, email, "master_admin", actorName, "master_admin", "DELETE_PRODUCT", "Deleted product '${product.name}'")
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.DELETE, "products/${product.id}")
  }

  suspend fun loadStarterCatalog(storeConfig: StoreConfig, actorName: String): Result<Unit> = runCatching {
    val (uid, email) = requireUser()
    val nowTs = FieldValue.serverTimestamp()
    val members = storeConfig.memberEmails.ifEmpty { listOf(email) }
    val profitViewers = storeConfig.profitViewerEmails.ifEmpty { listOf(email) }

    val starterCategories = listOf(
      "cat_beverages" to ("Beverages" to 1L),
      "cat_baklava" to ("Baklava" to 2L),
      "cat_delight" to ("Turkish Delight" to 3L),
      "cat_desserts" to ("Desserts" to 4L)
    )
    val batch = db.batch()
    starterCategories.forEach { (id, info) ->
      batch.set(
        db.collection("categories").document(id),
        mapOf(
          "ownerUid" to uid,
          "ownerEmail" to email,
          "memberEmails" to members,
          "name" to info.first,
          "sortOrder" to info.second,
          "createdAt" to nowTs,
          "updatedAt" to nowTs
        )
      )
    }

    data class StarterProd(
      val id: String,
      val name: String,
      val catId: String,
      val catName: String,
      val price: Double,
      val cost: Double,
      val imageKey: String,
      val desc: String
    )

    val products = listOf(
      StarterProd("prod_turkish_tea", "Turkish Tea", "cat_beverages", "Beverages", 30.0, 10.0, "tea", "Traditional brewed ruby black tea in tulip glass"),
      StarterProd("prod_cold_coffee", "Cold Coffee", "cat_beverages", "Beverages", 80.0, 32.0, "coffee", "Chilled signature Turkish espresso blend"),
      StarterProd("prod_mango_juice", "Mango Juice", "cat_beverages", "Beverages", 50.0, 20.0, "juice", "Fresh Alphonso mango nectar"),
      StarterProd("prod_orange_juice", "Orange Juice", "cat_beverages", "Beverages", 60.0, 25.0, "juice", "Cold-pressed Valencia orange juice"),
      StarterProd("prod_pistachio_baklava", "Pistachio Baklava", "cat_baklava", "Baklava", 120.0, 62.0, "baklava", "Hand-layered filo pastry with Gaziantep pistachios"),
      StarterProd("prod_chocolate_baklava", "Chocolate Baklava", "cat_baklava", "Baklava", 150.0, 78.0, "baklava", "Rich Belgian cocoa & roasted pistachio baklava"),
      StarterProd("prod_walnut_baklava", "Walnut Baklava", "cat_baklava", "Baklava", 100.0, 50.0, "baklava", "Classic honey-glazed walnut filo baklava"),
      StarterProd("prod_rose_lokum", "Rose Pistachio Lokum", "cat_delight", "Turkish Delight", 140.0, 68.0, "delight", "Authentic rosewater Turkish delight rolled in pistachio"),
      StarterProd("prod_kunafa", "Classic Cheese Kunafa", "cat_desserts", "Desserts", 220.0, 105.0, "dessert", "Warm golden kataifi pastry with sweet cheese & syrup")
    )

    products.forEach { p ->
      batch.set(
        db.collection("products").document(p.id),
        mapOf(
          "ownerUid" to uid,
          "ownerEmail" to email,
          "memberEmails" to members,
          "name" to p.name,
          "categoryId" to p.catId,
          "categoryName" to p.catName,
          "sellingPrice" to p.price,
          "description" to p.desc,
          "imageKey" to p.imageKey,
          "imageBase64" to "",
          "active" to true,
          "trackStock" to true,
          "stockQty" to 40L,
          "lowStockThreshold" to 5L,
          "createdAt" to nowTs,
          "updatedAt" to nowTs
        )
      )
      batch.set(
        db.collection("productCosts").document(p.id),
        mapOf(
          "ownerUid" to uid,
          "ownerEmail" to email,
          "profitViewerEmails" to profitViewers,
          "productId" to p.id,
          "purchaseCost" to p.cost,
          "createdAt" to nowTs,
          "updatedAt" to nowTs
        )
      )
    }
    batch.commit().await()
    logActivityInternal(uid, email, "master_admin", actorName, "master_admin", "LOAD_STARTER_CATALOG", "Loaded starter categories and menu items")
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.WRITE, "products")
  }

  // --- INVENTORY STOCK MANAGEMENT ---

  suspend fun adjustProductStock(
    storeConfig: StoreConfig,
    product: Product,
    movementType: String, // "purchase", "adjustment", "initial"
    deltaOrNewQty: Long,
    isAbsoluteSet: Boolean,
    note: String,
    actorName: String
  ): Result<Unit> = runCatching {
    val (uid, email) = requireUser()
    val prodRef = db.collection("products").document(product.id)
    val nowTs = FieldValue.serverTimestamp()

    val (delta, newQty) = db.runTransaction { tx ->
      val snap = tx.get(prodRef)
      val currentQty = snap.getLong("stockQty") ?: 0L
      val computedNew = if (isAbsoluteSet) deltaOrNewQty.coerceAtLeast(0L) else (currentQty + deltaOrNewQty).coerceAtLeast(0L)
      val computedDelta = computedNew - currentQty
      tx.update(prodRef, mapOf("stockQty" to computedNew, "trackStock" to true, "updatedAt" to nowTs))
      computedDelta to computedNew
    }.await()

    writeStockMovement(
      ownerUid = storeConfig.ownerUid,
      ownerEmail = storeConfig.ownerEmail,
      productId = product.id,
      productName = product.name,
      type = movementType,
      deltaQty = delta,
      resultingQty = newQty,
      referenceId = "manual",
      performedBy = actorName,
      note = note.trim()
    )
    logActivityInternal(
      storeConfig.ownerUid, storeConfig.ownerEmail, "master_admin", actorName, "master_admin",
      "ADD_STOCK", "${product.name}: stock ${if (delta >= 0) "+$delta" else "$delta"} -> $newQty ($note)"
    )
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.UPDATE, "products/${product.id}")
  }

  // --- ATOMIC INVOICE GENERATION ---

  suspend fun createInvoiceAtomic(
    storeConfig: StoreConfig,
    cartItems: List<CartItem>,
    discountAmount: Double,
    taxPercent: Double,
    customerName: String,
    customerPhone: String,
    paymentMethod: String,
    staffId: String,
    staffName: String,
    actorRole: String
  ): Result<Invoice> = runCatching {
    require(cartItems.isNotEmpty()) { "Cannot generate an empty bill." }
    val (_, authEmail) = requireUser()
    val nowMillis = System.currentTimeMillis()
    val year = IstTime.currentYear(nowMillis).toLong()
    val dateKey = IstTime.formatDate(nowMillis)
    val invoiceId = "inv_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    val counterRef = db.collection("counters").document("invoices")
    val invoiceRef = db.collection("invoices").document(invoiceId)
    val nowTs = FieldValue.serverTimestamp()

    val createdInvoice = db.runTransaction { tx ->
      val counterSnap = tx.get(counterRef)
      val currentSeq = if (counterSnap.exists()) counterSnap.getLong("nextNumber") ?: 1L else 1L

      // Fetch live authoritative product prices & stock inside the transaction
      val productSnaps = cartItems.map { item ->
        item to tx.get(db.collection("products").document(item.product.id))
      }

      val lineItems = mutableListOf<InvoiceLineItem>()
      val stockUpdates = mutableListOf<Triple<String, String, Pair<Long, Long>>>()

      for ((cartItem, snap) in productSnaps) {
        if (!snap.exists()) {
          throw IllegalStateException("Product '${cartItem.product.name}' no longer exists.")
        }
        val active = snap.getBoolean("active") ?: false
        if (!active) {
          throw IllegalStateException("Product '${cartItem.product.name}' is currently inactive.")
        }
        val livePrice = snap.getDouble("sellingPrice") ?: cartItem.product.sellingPrice
        val liveName = snap.getString("name") ?: cartItem.product.name
        val catId = snap.getString("categoryId") ?: cartItem.product.categoryId
        val catName = snap.getString("categoryName") ?: cartItem.product.categoryName
        val trackStock = (snap.getBoolean("trackStock") ?: false) && storeConfig.stockTrackingEnabled
        val currentStock = snap.getLong("stockQty") ?: 0L

        if (trackStock && currentStock < cartItem.quantity) {
          throw IllegalStateException("Insufficient stock for '$liveName' (Available: $currentStock, Requested: ${cartItem.quantity}).")
        }

        val lineTotal = livePrice * cartItem.quantity
        lineItems.add(
          InvoiceLineItem(
            productId = snap.id,
            productName = liveName,
            categoryId = catId,
            categoryName = catName,
            quantity = cartItem.quantity,
            unitPrice = livePrice,
            lineTotal = lineTotal,
            trackStock = trackStock
          )
        )

        if (trackStock) {
          val newQty = (currentStock - cartItem.quantity).coerceAtLeast(0L)
          stockUpdates.add(Triple(snap.id, liveName, (-cartItem.quantity) to newQty))
        }
      }

      val subtotal = lineItems.sumOf { it.lineTotal }
      val safeDiscount = discountAmount.coerceIn(0.0, subtotal)
      val taxable = (subtotal - safeDiscount).coerceAtLeast(0.0)
      val safeTaxPct = taxPercent.coerceIn(0.0, 100.0)
      val taxAmount = taxable * (safeTaxPct / 100.0)
      val grandTotal = taxable + taxAmount

      val prefix = storeConfig.invoicePrefix.ifBlank { "TQR" }
      val invoiceNumber = "$prefix-$year-${currentSeq.toString().padStart(6, '0')}"

      // Advance atomic counter
      if (counterSnap.exists()) {
        tx.update(
          counterRef,
          mapOf(
            "nextNumber" to (currentSeq + 1L),
            "year" to year,
            "updatedAt" to nowTs
          )
        )
      } else {
        tx.set(
          counterRef,
          mapOf(
            "ownerEmail" to storeConfig.ownerEmail,
            "nextNumber" to (currentSeq + 1L),
            "year" to year,
            "updatedAt" to nowTs
          )
        )
      }

      // Deduct stock quantities atomically
      stockUpdates.forEach { (prodId, _, qtyPair) ->
        tx.update(
          db.collection("products").document(prodId),
          mapOf("stockQty" to qtyPair.second, "updatedAt" to nowTs)
        )
      }

      val itemMaps = lineItems.map { li ->
        mapOf(
          "productId" to li.productId,
          "productName" to li.productName,
          "categoryId" to li.categoryId,
          "categoryName" to li.categoryName,
          "quantity" to li.quantity,
          "unitPrice" to li.unitPrice,
          "lineTotal" to li.lineTotal,
          "trackStock" to li.trackStock
        )
      }

      val invoiceMap = mapOf(
        "storeOwnerUid" to storeConfig.ownerUid,
        "storeOwnerEmail" to storeConfig.ownerEmail,
        "reportViewerEmails" to storeConfig.salesViewerEmails.ifEmpty { listOf(storeConfig.ownerEmail) },
        "staffAuthEmail" to authEmail,
        "invoiceNumber" to invoiceNumber,
        "sequenceNumber" to currentSeq,
        "invoiceYear" to year,
        "dateKey" to dateKey,
        "epochMillis" to nowMillis,
        "staffId" to staffId,
        "staffName" to staffName,
        "customerName" to customerName.trim(),
        "customerPhone" to customerPhone.trim(),
        "items" to itemMaps,
        "subtotal" to subtotal,
        "discountAmount" to safeDiscount,
        "taxPercent" to safeTaxPct,
        "taxAmount" to taxAmount,
        "grandTotal" to grandTotal,
        "paymentMethod" to paymentMethod,
        "status" to "completed",
        "cancelReason" to "",
        "cancelledBy" to "",
        "editCount" to 0L,
        "lastEditedBy" to "",
        "createdAt" to nowTs,
        "updatedAt" to nowTs
      )
      tx.set(invoiceRef, invoiceMap)

      Triple(
        Invoice(
          id = invoiceId,
          storeOwnerUid = storeConfig.ownerUid,
          storeOwnerEmail = storeConfig.ownerEmail,
          reportViewerEmails = storeConfig.salesViewerEmails,
          staffAuthEmail = authEmail,
          invoiceNumber = invoiceNumber,
          sequenceNumber = currentSeq,
          invoiceYear = year,
          dateKey = dateKey,
          epochMillis = nowMillis,
          staffId = staffId,
          staffName = staffName,
          customerName = customerName.trim(),
          customerPhone = customerPhone.trim(),
          items = lineItems,
          subtotal = subtotal,
          discountAmount = safeDiscount,
          taxPercent = safeTaxPct,
          taxAmount = taxAmount,
          grandTotal = grandTotal,
          paymentMethod = paymentMethod,
          status = "completed",
          editCount = 0L
        ),
        stockUpdates,
        invoiceNumber
      )
    }.await()

    val (invoice, stockUpdates, invoiceNumber) = createdInvoice

    // Write immutable stock movement entries
    stockUpdates.forEach { (prodId, prodName, qtyPair) ->
      runCatching {
        writeStockMovement(
          ownerUid = storeConfig.ownerUid,
          ownerEmail = storeConfig.ownerEmail,
          productId = prodId,
          productName = prodName,
          type = "sale",
          deltaQty = qtyPair.first,
          resultingQty = qtyPair.second,
          referenceId = invoiceNumber,
          performedBy = staffName,
          note = "Sold on $invoiceNumber"
        )
      }
    }

    runCatching {
      logActivityInternal(
        ownerUid = storeConfig.ownerUid,
        ownerEmail = storeConfig.ownerEmail,
        actorId = staffId,
        actorName = staffName,
        actorRole = actorRole,
        action = "CREATE_INVOICE",
        details = "Generated $invoiceNumber (${IstTime.formatInr(invoice.grandTotal)}, ${invoice.paymentMethod})"
      )
    }

    invoice
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.WRITE, "invoices")
  }

  // --- MASTER ADMIN INVOICE EDIT & SOFT CANCELLATION ---

  suspend fun editInvoice(
    storeConfig: StoreConfig,
    originalInvoice: Invoice,
    updatedItems: List<InvoiceLineItem>,
    newDiscountAmount: Double,
    newTaxPercent: Double,
    newCustomerName: String,
    newCustomerPhone: String,
    newPaymentMethod: String,
    editorName: String
  ): Result<Invoice> = runCatching {
    require(updatedItems.isNotEmpty()) { "Invoice must contain at least one item." }
    require(originalInvoice.status == "completed") { "Cancelled invoices cannot be edited." }
    val (_, _) = requireUser()
    val nowTs = FieldValue.serverTimestamp()
    val invoiceRef = db.collection("invoices").document(originalInvoice.id)

    val subtotal = updatedItems.sumOf { it.unitPrice * it.quantity }
    val safeDiscount = newDiscountAmount.coerceIn(0.0, subtotal)
    val taxable = (subtotal - safeDiscount).coerceAtLeast(0.0)
    val safeTaxPct = newTaxPercent.coerceIn(0.0, 100.0)
    val taxAmount = taxable * (safeTaxPct / 100.0)
    val newGrandTotal = taxable + taxAmount

    val normalizedItems = updatedItems.map {
      it.copy(lineTotal = it.unitPrice * it.quantity)
    }

    // Calculate net stock deltas by productId (oldQty - newQty)
    val oldQtyMap = originalInvoice.items.groupBy { it.productId }.mapValues { (_, list) -> list.sumOf { it.quantity } }
    val newQtyMap = normalizedItems.groupBy { it.productId }.mapValues { (_, list) -> list.sumOf { it.quantity } }
    val allProductIds = (oldQtyMap.keys + newQtyMap.keys).distinct()

    val stockChanges = mutableListOf<Triple<String, String, Pair<Long, Long>>>()

    db.runTransaction { tx ->
      val prodSnaps = allProductIds.associateWith { pid -> tx.get(db.collection("products").document(pid)) }

      for (pid in allProductIds) {
        val snap = prodSnaps[pid] ?: continue
        if (!snap.exists()) continue
        val track = (snap.getBoolean("trackStock") ?: false) && storeConfig.stockTrackingEnabled
        if (!track) continue
        val oldQ = oldQtyMap[pid] ?: 0L
        val newQ = newQtyMap[pid] ?: 0L
        val stockDelta = oldQ - newQ // positive restores stock, negative deducts more stock
        if (stockDelta != 0L) {
          val curStock = snap.getLong("stockQty") ?: 0L
          val nextStock = (curStock + stockDelta).coerceAtLeast(0L)
          val prodName = snap.getString("name") ?: pid
          tx.update(snap.reference, mapOf("stockQty" to nextStock, "updatedAt" to nowTs))
          stockChanges.add(Triple(pid, prodName, stockDelta to nextStock))
        }
      }

      val itemMaps = normalizedItems.map { li ->
        mapOf(
          "productId" to li.productId,
          "productName" to li.productName,
          "categoryId" to li.categoryId,
          "categoryName" to li.categoryName,
          "quantity" to li.quantity,
          "unitPrice" to li.unitPrice,
          "lineTotal" to li.lineTotal,
          "trackStock" to li.trackStock
        )
      }

      // Preserve invoiceNumber, sequenceNumber, invoiceYear, createdAt!
      tx.update(
        invoiceRef,
        mapOf(
          "customerName" to newCustomerName.trim(),
          "customerPhone" to newCustomerPhone.trim(),
          "items" to itemMaps,
          "subtotal" to subtotal,
          "discountAmount" to safeDiscount,
          "taxPercent" to safeTaxPct,
          "taxAmount" to taxAmount,
          "grandTotal" to newGrandTotal,
          "paymentMethod" to newPaymentMethod,
          "editCount" to (originalInvoice.editCount + 1L),
          "lastEditedBy" to editorName,
          "updatedAt" to nowTs
        )
      )
    }.await()

    val revId = "rev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val summary = buildString {
      append("Edited items/totals: ${IstTime.formatInr(originalInvoice.grandTotal)} -> ${IstTime.formatInr(newGrandTotal)}")
      if (originalInvoice.paymentMethod != newPaymentMethod) {
        append(", Payment: ${originalInvoice.paymentMethod} -> $newPaymentMethod")
      }
    }
    invoiceRef.collection("revisions").document(revId).set(
      mapOf(
        "editedBy" to editorName,
        "editedByRole" to "master_admin",
        "changeSummary" to summary,
        "previousGrandTotal" to originalInvoice.grandTotal,
        "newGrandTotal" to newGrandTotal,
        "previousStatus" to originalInvoice.status,
        "newStatus" to originalInvoice.status,
        "createdAt" to nowTs
      )
    ).await()

    stockChanges.forEach { (pid, prodName, qtyPair) ->
      runCatching {
        writeStockMovement(
          ownerUid = storeConfig.ownerUid,
          ownerEmail = storeConfig.ownerEmail,
          productId = pid,
          productName = prodName,
          type = "invoice_edit",
          deltaQty = qtyPair.first,
          resultingQty = qtyPair.second,
          referenceId = originalInvoice.invoiceNumber,
          performedBy = editorName,
          note = "Stock adjusted from editing ${originalInvoice.invoiceNumber}"
        )
      }
    }

    logActivityInternal(
      storeConfig.ownerUid, storeConfig.ownerEmail, "master_admin", editorName, "master_admin",
      "EDIT_INVOICE", "Edited ${originalInvoice.invoiceNumber}: $summary"
    )

    originalInvoice.copy(
      customerName = newCustomerName.trim(),
      customerPhone = newCustomerPhone.trim(),
      items = normalizedItems,
      subtotal = subtotal,
      discountAmount = safeDiscount,
      taxPercent = safeTaxPct,
      taxAmount = taxAmount,
      grandTotal = newGrandTotal,
      paymentMethod = newPaymentMethod,
      editCount = originalInvoice.editCount + 1L,
      lastEditedBy = editorName
    )
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.UPDATE, "invoices/${originalInvoice.id}")
  }

  suspend fun cancelInvoice(
    storeConfig: StoreConfig,
    invoice: Invoice,
    reason: String,
    editorName: String
  ): Result<Unit> = runCatching {
    require(invoice.status != "cancelled") { "Invoice is already cancelled." }
    requireUser()
    val nowTs = FieldValue.serverTimestamp()
    val invoiceRef = db.collection("invoices").document(invoice.id)
    val stockRestores = mutableListOf<Triple<String, String, Pair<Long, Long>>>()

    db.runTransaction { tx ->
      val itemSnaps = invoice.items.map { it to tx.get(db.collection("products").document(it.productId)) }
      for ((item, snap) in itemSnaps) {
        if (!snap.exists()) continue
        val track = (snap.getBoolean("trackStock") ?: false) && storeConfig.stockTrackingEnabled
        if (track) {
          val curStock = snap.getLong("stockQty") ?: 0L
          val nextStock = curStock + item.quantity
          tx.update(snap.reference, mapOf("stockQty" to nextStock, "updatedAt" to nowTs))
          stockRestores.add(Triple(item.productId, item.productName, item.quantity to nextStock))
        }
      }
      tx.update(
        invoiceRef,
        mapOf(
          "status" to "cancelled",
          "cancelReason" to reason.trim().ifEmpty { "Cancelled by Master Admin" },
          "cancelledBy" to editorName,
          "editCount" to (invoice.editCount + 1L),
          "lastEditedBy" to editorName,
          "updatedAt" to nowTs
        )
      )
    }.await()

    val revId = "rev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    invoiceRef.collection("revisions").document(revId).set(
      mapOf(
        "editedBy" to editorName,
        "editedByRole" to "master_admin",
        "changeSummary" to "Cancelled invoice. Reason: ${reason.trim().ifEmpty { "Cancelled by Master Admin" }}",
        "previousGrandTotal" to invoice.grandTotal,
        "newGrandTotal" to invoice.grandTotal,
        "previousStatus" to invoice.status,
        "newStatus" to "cancelled",
        "createdAt" to nowTs
      )
    ).await()

    stockRestores.forEach { (pid, prodName, qtyPair) ->
      runCatching {
        writeStockMovement(
          ownerUid = storeConfig.ownerUid,
          ownerEmail = storeConfig.ownerEmail,
          productId = pid,
          productName = prodName,
          type = "invoice_cancel",
          deltaQty = qtyPair.first,
          resultingQty = qtyPair.second,
          referenceId = invoice.invoiceNumber,
          performedBy = editorName,
          note = "Restored stock from cancelled ${invoice.invoiceNumber}"
        )
      }
    }

    logActivityInternal(
      storeConfig.ownerUid, storeConfig.ownerEmail, "master_admin", editorName, "master_admin",
      "CANCEL_INVOICE", "Cancelled ${invoice.invoiceNumber} (${IstTime.formatInr(invoice.grandTotal)}). Reason: ${reason.trim()}"
    )
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.UPDATE, "invoices/${invoice.id}")
  }

  suspend fun getInvoiceRevisions(invoiceId: String): List<InvoiceRevision> {
    requireUser()
    val snap = db.collection("invoices").document(invoiceId)
      .collection("revisions")
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .get()
      .await()
    return snap.toObjects(InvoiceRevision::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
  }

  // --- STAFF MANAGEMENT (Master Admin) ---

  suspend fun saveStaffAccount(
    storeConfig: StoreConfig,
    existingStaff: StaffAccount?,
    username: String,
    displayName: String,
    newPassword: String?,
    linkedEmail: String,
    active: Boolean,
    canViewSalesReports: Boolean,
    canViewProfitReports: Boolean,
    actorName: String
  ): Result<Unit> = runCatching {
    val (uid, email) = requireUser()
    val cleanUsername = username.trim()
    val cleanLinkedEmail = linkedEmail.lowercase().trim()
    val id = existingStaff?.id?.takeIf { it.isNotBlank() }
      ?: "staff_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val nowTs = FieldValue.serverTimestamp()

    if (existingStaff == null) {
      require(!newPassword.isNullOrBlank() && newPassword.length >= 4) { "Password must be at least 4 characters." }
      val hashed = PasswordHasher.hashPassword(newPassword)
      db.collection("staffAccounts").document(id).set(
        mapOf(
          "ownerUid" to uid,
          "ownerEmail" to email,
          "username" to cleanUsername,
          "displayName" to displayName.trim(),
          "passwordHash" to hashed.hash,
          "passwordSalt" to hashed.salt,
          "linkedEmail" to cleanLinkedEmail,
          "active" to active,
          "canViewSalesReports" to canViewSalesReports,
          "canViewProfitReports" to canViewProfitReports,
          "createdAt" to nowTs,
          "updatedAt" to nowTs
        )
      ).await()
      logActivityInternal(uid, email, "master_admin", actorName, "master_admin", "CREATE_STAFF", "Created staff account '${displayName.trim()}' (@$cleanUsername)")
    } else {
      val updates = mutableMapOf<String, Any>(
        "username" to cleanUsername,
        "displayName" to displayName.trim(),
        "linkedEmail" to cleanLinkedEmail,
        "active" to active,
        "canViewSalesReports" to canViewSalesReports,
        "canViewProfitReports" to canViewProfitReports,
        "updatedAt" to nowTs
      )
      if (!newPassword.isNullOrBlank()) {
        require(newPassword.length >= 4) { "Password must be at least 4 characters." }
        val hashed = PasswordHasher.hashPassword(newPassword)
        updates["passwordHash"] = hashed.hash
        updates["passwordSalt"] = hashed.salt
      }
      db.collection("staffAccounts").document(id).update(updates).await()

      // Remove old staffAccess email doc if email changed
      val oldEmail = existingStaff.linkedEmail.lowercase().trim()
      if (oldEmail.isNotEmpty() && oldEmail != cleanLinkedEmail) {
        runCatching { db.collection("staffAccess").document(oldEmail).delete().await() }
      }
      logActivityInternal(uid, email, "master_admin", actorName, "master_admin", "UPDATE_STAFF", "Updated staff '${displayName.trim()}' (Active: $active)")
    }

    if (cleanLinkedEmail.isNotEmpty()) {
      val accessRef = db.collection("staffAccess").document(cleanLinkedEmail)
      val existingAccess = accessRef.get().await()
      if (existingAccess.exists()) {
        accessRef.update(
          mapOf(
            "staffId" to id,
            "username" to cleanUsername,
            "displayName" to displayName.trim(),
            "active" to active,
            "canViewSalesReports" to canViewSalesReports,
            "canViewProfitReports" to canViewProfitReports,
            "updatedAt" to nowTs
          )
        ).await()
      } else {
        accessRef.set(
          mapOf(
            "staffId" to id,
            "email" to cleanLinkedEmail,
            "username" to cleanUsername,
            "displayName" to displayName.trim(),
            "active" to active,
            "canViewSalesReports" to canViewSalesReports,
            "canViewProfitReports" to canViewProfitReports,
            "ownerUid" to uid,
            "ownerEmail" to email,
            "createdAt" to nowTs,
            "updatedAt" to nowTs
          )
        ).await()
      }
    }

    syncStoreMemberEmails(storeConfig)
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.WRITE, "staffAccounts")
  }

  private suspend fun syncStoreMemberEmails(storeConfig: StoreConfig) {
    val staffDocs = db.collection("staffAccounts")
      .whereEqualTo("ownerEmail", storeConfig.ownerEmail)
      .get()
      .await()
      .toObjects(StaffAccount::class.java)

    val activeLinked = staffDocs.filter { it.active && it.linkedEmail.isNotBlank() }
    val memberEmails = (listOf(storeConfig.ownerEmail) + activeLinked.map { it.linkedEmail.lowercase().trim() }).distinct().take(50)
    val salesEmails = (listOf(storeConfig.ownerEmail) + activeLinked.filter { it.canViewSalesReports }.map { it.linkedEmail.lowercase().trim() }).distinct().take(50)
    val profitEmails = (listOf(storeConfig.ownerEmail) + activeLinked.filter { it.canViewProfitReports }.map { it.linkedEmail.lowercase().trim() }).distinct().take(50)

    val nowTs = FieldValue.serverTimestamp()
    db.collection("storeConfig").document("main").update(
      mapOf(
        "memberEmails" to memberEmails,
        "salesViewerEmails" to salesEmails,
        "profitViewerEmails" to profitEmails,
        "updatedAt" to nowTs
      )
    ).await()

    val batch = db.batch()
    db.collection("categories").whereEqualTo("ownerEmail", storeConfig.ownerEmail).get().await().documents.forEach {
      batch.update(it.reference, mapOf("memberEmails" to memberEmails, "updatedAt" to nowTs))
    }
    db.collection("products").whereEqualTo("ownerEmail", storeConfig.ownerEmail).get().await().documents.forEach {
      batch.update(it.reference, mapOf("memberEmails" to memberEmails, "updatedAt" to nowTs))
    }
    db.collection("productCosts").whereEqualTo("ownerEmail", storeConfig.ownerEmail).get().await().documents.forEach {
      batch.update(it.reference, mapOf("profitViewerEmails" to profitEmails, "updatedAt" to nowTs))
    }
    batch.commit().await()
  }

  // --- APPLICATION SETTINGS (Master Admin) ---

  suspend fun updateStoreSettings(
    storeConfig: StoreConfig,
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
    newAdminPassword: String?,
    actorName: String
  ): Result<Unit> = runCatching {
    val (uid, email) = requireUser()
    val nowTs = FieldValue.serverTimestamp()
    val updates = mutableMapOf<String, Any>(
      "businessName" to businessName.trim().ifEmpty { "TURKISHQ RETAIL" },
      "businessAddress" to businessAddress.trim(),
      "contactNumber" to contactNumber.trim(),
      "invoiceFooter" to invoiceFooter.trim(),
      "currencySymbol" to currencySymbol.trim().ifEmpty { "₹" },
      "invoicePrefix" to invoicePrefix.trim().uppercase().ifEmpty { "TQR" },
      "defaultTaxPercent" to defaultTaxPercent.coerceIn(0.0, 100.0),
      "defaultDiscountPercent" to defaultDiscountPercent.coerceIn(0.0, 100.0),
      "stockTrackingEnabled" to stockTrackingEnabled,
      "allowStaffViewSalesReportsDefault" to allowStaffSalesDefault,
      "allowStaffViewProfitReportsDefault" to allowStaffProfitDefault,
      "updatedAt" to nowTs
    )
    if (!newAdminPassword.isNullOrBlank()) {
      require(newAdminPassword.length >= 4) { "Admin password must be at least 4 characters." }
      val hashed = PasswordHasher.hashPassword(newAdminPassword)
      updates["adminPasswordHash"] = hashed.hash
      updates["adminPasswordSalt"] = hashed.salt
    }
    db.collection("storeConfig").document("main").update(updates).await()
    logActivityInternal(uid, email, "master_admin", actorName, "master_admin", "UPDATE_SETTINGS", "Updated application settings")
  }.onFailure { e ->
    if (e !is CancellationException) handleFirestoreError(e, OperationType.UPDATE, "storeConfig/main")
  }

  suspend fun logOperatorAction(
    storeConfig: StoreConfig,
    actorId: String,
    actorName: String,
    actorRole: String,
    action: String,
    details: String
  ) {
    runCatching {
      logActivityInternal(
        ownerUid = storeConfig.ownerUid,
        ownerEmail = storeConfig.ownerEmail,
        actorId = actorId,
        actorName = actorName,
        actorRole = actorRole,
        action = action,
        details = details
      )
    }
  }

  private suspend fun writeStockMovement(
    ownerUid: String,
    ownerEmail: String,
    productId: String,
    productName: String,
    type: String,
    deltaQty: Long,
    resultingQty: Long,
    referenceId: String,
    performedBy: String,
    note: String
  ) {
    val id = "stk_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    db.collection("stockMovements").document(id).set(
      mapOf(
        "ownerUid" to ownerUid,
        "ownerEmail" to ownerEmail,
        "productId" to productId,
        "productName" to productName,
        "type" to type,
        "deltaQty" to deltaQty,
        "resultingQty" to resultingQty,
        "referenceId" to referenceId,
        "performedBy" to performedBy,
        "note" to note,
        "createdAt" to FieldValue.serverTimestamp()
      )
    ).await()
  }

  private suspend fun logActivityInternal(
    ownerUid: String,
    ownerEmail: String,
    actorId: String,
    actorName: String,
    actorRole: String,
    action: String,
    details: String
  ) {
    val id = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    db.collection("activityLogs").document(id).set(
      mapOf(
        "ownerUid" to ownerUid,
        "ownerEmail" to ownerEmail,
        "actorId" to actorId,
        "actorName" to actorName,
        "actorRole" to actorRole,
        "action" to action,
        "details" to details.take(390),
        "dateKey" to IstTime.todayDateKey(),
        "createdAt" to FieldValue.serverTimestamp()
      )
    ).await()
  }
}
