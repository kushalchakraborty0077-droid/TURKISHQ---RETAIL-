const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_1";
const ADMIN_EMAIL = "owner@turkishqretail.com";
const STAFF_UID = "staff_uid_1";
const STAFF_EMAIL = "cashier@turkishqretail.com";
const PROFIT_STAFF_UID = "staff_uid_2";
const PROFIT_STAFF_EMAIL = "manager@turkishqretail.com";
const OUTSIDER_UID = "outsider_uid_1";
const OUTSIDER_EMAIL = "outsider@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTs() {
  return new Date(Date.now() - 5000);
}

function adminCtx() {
  return testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true });
}

function staffCtx() {
  return testEnv.authenticatedContext(STAFF_UID, { email: STAFF_EMAIL, email_verified: true });
}

function profitStaffCtx() {
  return testEnv.authenticatedContext(PROFIT_STAFF_UID, { email: PROFIT_STAFF_EMAIL, email_verified: true });
}

function outsiderCtx() {
  return testEnv.authenticatedContext(OUTSIDER_UID, { email: OUTSIDER_EMAIL, email_verified: true });
}

async function seedStoreBaseline() {
  const ts = pastTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("admins").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: ADMIN_EMAIL,
      createdAt: ts,
    });
    await db.collection("storeConfig").doc("main").set({
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      businessName: "TURKISHQ RETAIL",
      businessAddress: "MG Road, Bengaluru",
      contactNumber: "+91 9876543210",
      invoiceFooter: "Thank you for visiting TURKISHQ RETAIL",
      currencySymbol: "₹",
      invoicePrefix: "TQR",
      defaultTaxPercent: 5.0,
      defaultDiscountPercent: 0.0,
      stockTrackingEnabled: true,
      allowStaffViewSalesReportsDefault: true,
      allowStaffViewProfitReportsDefault: false,
      adminUsername: "masteradmin",
      adminPasswordHash: "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
      adminPasswordSalt: "salt12345678",
      memberEmails: [ADMIN_EMAIL, STAFF_EMAIL, PROFIT_STAFF_EMAIL],
      salesViewerEmails: [ADMIN_EMAIL, STAFF_EMAIL, PROFIT_STAFF_EMAIL],
      profitViewerEmails: [ADMIN_EMAIL, PROFIT_STAFF_EMAIL],
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("staffAccess").doc(STAFF_EMAIL).set({
      staffId: "staff_1",
      email: STAFF_EMAIL,
      username: "cashier1",
      displayName: "Ayesha",
      active: true,
      canViewSalesReports: true,
      canViewProfitReports: false,
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("staffAccess").doc(PROFIT_STAFF_EMAIL).set({
      staffId: "staff_2",
      email: PROFIT_STAFF_EMAIL,
      username: "manager1",
      displayName: "Zayd",
      active: true,
      canViewSalesReports: true,
      canViewProfitReports: true,
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("categories").doc("cat_baklava").set({
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      memberEmails: [ADMIN_EMAIL, STAFF_EMAIL, PROFIT_STAFF_EMAIL],
      name: "Baklava",
      sortOrder: 1,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("products").doc("prod_pistachio").set({
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      memberEmails: [ADMIN_EMAIL, STAFF_EMAIL, PROFIT_STAFF_EMAIL],
      name: "Pistachio Baklava",
      categoryId: "cat_baklava",
      categoryName: "Baklava",
      sellingPrice: 120.0,
      description: "Crispy pistachio baklava",
      imageKey: "baklava",
      active: true,
      trackStock: true,
      stockQty: 50,
      lowStockThreshold: 5,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("productCosts").doc("prod_pistachio").set({
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      profitViewerEmails: [ADMIN_EMAIL, PROFIT_STAFF_EMAIL],
      productId: "prod_pistachio",
      purchaseCost: 65.0,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("counters").doc("invoices").set({
      ownerEmail: ADMIN_EMAIL,
      nextNumber: 1,
      year: 2026,
      updatedAt: ts,
    });
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read products, categories, or invoices", async () => {
  await seedStoreBaseline();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("products").get());
  await assertFails(unauthDb.collection("categories").get());
  await assertFails(unauthDb.collection("invoices").get());
  await assertFails(unauthDb.collection("storeConfig").doc("main").get());
});

test("Email spoofing with email_verified=false is rejected even with admin email", async () => {
  await seedStoreBaseline();
  const spoofDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofDb.collection("products").where("ownerEmail", "==", ADMIN_EMAIL).get());
  await assertFails(spoofDb.collection("storeConfig").doc("main").get());
});

test("Outsider cannot read catalog or invoices", async () => {
  await seedStoreBaseline();
  const db = outsiderCtx().firestore();
  await assertFails(db.collection("products").get());
  await assertFails(db.collection("products").where("memberEmails", "array-contains", ADMIN_EMAIL).get());
  await assertFails(db.collection("products").doc("prod_pistachio").get());
  await assertFails(db.collection("storeConfig").doc("main").get());
});

test("Staff can read catalog & categories, and decrement stock on sale, but CANNOT create/edit/delete products or prices", async () => {
  await seedStoreBaseline();
  const db = staffCtx().firestore();
  const ts = pastTs();

  await assertSucceeds(db.collection("categories").where("memberEmails", "array-contains", STAFF_EMAIL).get());
  await assertSucceeds(db.collection("products").where("memberEmails", "array-contains", STAFF_EMAIL).get());

  // Staff can decrement stockQty
  await assertSucceeds(
    db.collection("products").doc("prod_pistachio").update({
      stockQty: 48,
      updatedAt: ts,
    })
  );

  // Staff CANNOT change sellingPrice
  await assertFails(
    db.collection("products").doc("prod_pistachio").update({
      sellingPrice: 1.0,
      updatedAt: ts,
    })
  );

  // Staff CANNOT increase stock arbitrarily
  await assertFails(
    db.collection("products").doc("prod_pistachio").update({
      stockQty: 100,
      updatedAt: ts,
    })
  );

  // Staff CANNOT create or delete products or categories
  await assertFails(
    db.collection("categories").doc("cat_new").set({
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      memberEmails: [ADMIN_EMAIL, STAFF_EMAIL],
      name: "Unauthorized",
      sortOrder: 2,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(db.collection("products").doc("prod_pistachio").delete());
});

test("Confidential productCosts blocked for regular staff, allowed for Master Admin & permitted profit staff", async () => {
  await seedStoreBaseline();
  const ts = pastTs();

  // Regular staff rejected
  await assertFails(staffCtx().firestore().collection("productCosts").doc("prod_pistachio").get());
  await assertFails(staffCtx().firestore().collection("productCosts").get());
  await assertFails(
    staffCtx().firestore().collection("productCosts").where("profitViewerEmails", "array-contains", ADMIN_EMAIL).get()
  );

  // Permitted profit staff allowed to read
  await assertSucceeds(profitStaffCtx().firestore().collection("productCosts").doc("prod_pistachio").get());
  // Permitted profit staff still CANNOT modify cost
  await assertFails(
    profitStaffCtx().firestore().collection("productCosts").doc("prod_pistachio").update({
      purchaseCost: 10.0,
      updatedAt: ts,
    })
  );

  // Master Admin can read and update
  await assertSucceeds(
    adminCtx().firestore().collection("productCosts").doc("prod_pistachio").update({
      purchaseCost: 70.0,
      updatedAt: ts,
    })
  );
});

test("Atomic invoice counter allows +1 increment only and blocks decrements or jumps", async () => {
  await seedStoreBaseline();
  const staffDb = staffCtx().firestore();
  const ts = pastTs();

  await assertSucceeds(
    staffDb.collection("counters").doc("invoices").update({
      nextNumber: 2,
      updatedAt: ts,
    })
  );
  // Jumping by +2 fails
  await assertFails(
    staffDb.collection("counters").doc("invoices").update({
      nextNumber: 4,
      updatedAt: ts,
    })
  );
  // Resetting backwards fails
  await assertFails(
    adminCtx().firestore().collection("counters").doc("invoices").update({
      nextNumber: 1,
      updatedAt: ts,
    })
  );
});

test("Staff can create invoice, cannot edit or cancel or delete invoice; Master Admin can edit/cancel but cannot change invoiceNumber or delete", async () => {
  await seedStoreBaseline();
  const staffDb = staffCtx().firestore();
  const adminDb = adminCtx().firestore();
  const ts = pastTs();

  const invoicePayload = {
    storeOwnerUid: ADMIN_UID,
    storeOwnerEmail: ADMIN_EMAIL,
    reportViewerEmails: [ADMIN_EMAIL, STAFF_EMAIL],
    staffAuthEmail: STAFF_EMAIL,
    invoiceNumber: "TQR-2026-000001",
    sequenceNumber: 1,
    invoiceYear: 2026,
    dateKey: "13-11-2026",
    epochMillis: 1794582186000,
    staffId: "staff_1",
    staffName: "Ayesha",
    customerName: "Rohan",
    customerPhone: "9876543210",
    items: [
      {
        productId: "prod_pistachio",
        productName: "Pistachio Baklava",
        categoryId: "cat_baklava",
        categoryName: "Baklava",
        quantity: 2,
        unitPrice: 120.0,
        lineTotal: 240.0,
        trackStock: true,
      },
    ],
    subtotal: 240.0,
    discountAmount: 0.0,
    taxPercent: 5.0,
    taxAmount: 12.0,
    grandTotal: 252.0,
    paymentMethod: "UPI",
    status: "completed",
    editCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };

  await assertSucceeds(staffDb.collection("invoices").doc("inv_1").set(invoicePayload));

  // Staff cannot edit or delete existing invoice
  await assertFails(
    staffDb.collection("invoices").doc("inv_1").update({
      grandTotal: 100.0,
      editCount: 1,
      updatedAt: ts,
    })
  );
  await assertFails(staffDb.collection("invoices").doc("inv_1").delete());

  // Master Admin cannot change invoiceNumber
  await assertFails(
    adminDb.collection("invoices").doc("inv_1").update({
      invoiceNumber: "TQR-2026-999999",
      updatedAt: ts,
    })
  );

  // Master Admin can edit totals & cancel, and cannot hard-delete
  await assertSucceeds(
    adminDb.collection("invoices").doc("inv_1").update({
      status: "cancelled",
      cancelReason: "Customer returned order",
      cancelledBy: "Master Admin",
      editCount: 1,
      lastEditedBy: "Master Admin",
      updatedAt: ts,
    })
  );
  await assertFails(adminDb.collection("invoices").doc("inv_1").delete());
});

test("Shadow update with ghost field is rejected", async () => {
  await seedStoreBaseline();
  const ts = pastTs();
  await assertFails(
    adminCtx().firestore().collection("categories").doc("cat_shadow").set({
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      memberEmails: [ADMIN_EMAIL],
      name: "Beverages",
      sortOrder: 2,
      ghostAdminOverride: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
