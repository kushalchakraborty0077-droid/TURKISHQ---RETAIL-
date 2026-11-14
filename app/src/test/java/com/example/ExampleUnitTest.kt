package com.example

import com.example.data.model.CartItem
import com.example.data.model.Product
import com.example.data.security.PasswordHasher
import com.example.util.IstTime
import com.example.viewmodel.PosBillingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun passwordHasher_saltedVerificationWorks() {
    val hashed = PasswordHasher.hashPassword("TurkishQ@2026")
    assertTrue(hashed.salt.isNotBlank())
    assertTrue(hashed.hash.isNotBlank())
    assertTrue(PasswordHasher.verifyPassword("TurkishQ@2026", hashed.hash, hashed.salt))
    assertFalse(PasswordHasher.verifyPassword("WrongPass", hashed.hash, hashed.salt))
  }

  @Test
  fun istTime_formatsDdMmYyyyAndInrCorrectly() {
    val startMs = IstTime.parseDateStartMillis("15-03-2026")
    assertNotNull(startMs)
    assertEquals("15-03-2026", IstTime.formatDate(startMs!!))
    assertNull(IstTime.parseDateStartMillis("2026-03-15"))
    assertEquals("₹1,250", IstTime.formatInr(1250.0))
    assertEquals("₹150.50", IstTime.formatInrExact(150.5))
  }

  @Test
  fun posBillingState_computesDiscountAndTaxAccurately() {
    val baklava = Product(id = "p1", name = "Pistachio Baklava", sellingPrice = 120.0)
    val tea = Product(id = "p2", name = "Turkish Tea", sellingPrice = 30.0)
    val state = PosBillingState(
      cart = listOf(CartItem(baklava, 2L), CartItem(tea, 2L)), // subtotal = 240 + 60 = 300
      discountInput = "10",
      isDiscountPercent = true, // 10% of 300 = 30 -> taxable = 270
      taxPercentInput = "5" // 5% of 270 = 13.5 -> grandTotal = 283.5
    )
    assertEquals(300.0, state.subtotal, 0.001)
    assertEquals(30.0, state.discountAmount, 0.001)
    assertEquals(270.0, state.taxableAmount, 0.001)
    assertEquals(13.5, state.taxAmount, 0.001)
    assertEquals(283.5, state.grandTotal, 0.001)
  }
}
