package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.Invoice
import com.example.data.model.StoreConfig
import java.io.File
import java.io.FileOutputStream

enum class InvoicePrintFormat(val label: String) {
  A4("A4 Invoice"),
  THERMAL_80MM("80mm Thermal Receipt")
}

object InvoicePdfAndPrintHelper {

  fun generateInvoicePdfFile(
    context: Context,
    storeConfig: StoreConfig,
    invoice: Invoice,
    format: InvoicePrintFormat
  ): File {
    val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val suffix = if (format == InvoicePrintFormat.A4) "A4" else "80mm"
    val file = File(exportsDir, "${invoice.invoiceNumber}_$suffix.pdf")

    val doc = PdfDocument()
    try {
      if (format == InvoicePrintFormat.A4) {
        drawA4Invoice(context, doc, storeConfig, invoice)
      } else {
        draw80mmThermalReceipt(context, doc, storeConfig, invoice)
      }
      FileOutputStream(file).use { out -> doc.writeTo(out) }
    } finally {
      doc.close()
    }
    return file
  }

  fun shareOrSaveFile(context: Context, file: File, mimeType: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, title)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, title).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }

  fun printInvoiceDirect(
    context: Context,
    storeConfig: StoreConfig,
    invoice: Invoice,
    format: InvoicePrintFormat
  ) {
    val pdfFile = generateInvoicePdfFile(context, storeConfig, invoice, format)
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = "${storeConfig.businessName} - ${invoice.invoiceNumber}"

    val adapter = object : PrintDocumentAdapter() {
      override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
      ) {
        if (cancellationSignal?.isCanceled == true) {
          callback.onLayoutCancelled()
          return
        }
        val info = PrintDocumentInfo.Builder("${invoice.invoiceNumber}.pdf")
          .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
          .setPageCount(1)
          .build()
        callback.onLayoutFinished(info, true)
      }

      override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
      ) {
        try {
          pdfFile.inputStream().use { input ->
            FileOutputStream(destination.fileDescriptor).use { output ->
              input.copyTo(output)
            }
          }
          callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
          callback.onWriteFailed(e.message)
        }
      }
    }

    val mediaSize = if (format == InvoicePrintFormat.A4) {
      PrintAttributes.MediaSize.ISO_A4
    } else {
      PrintAttributes.MediaSize.UNKNOWN_PORTRAIT
    }
    val attrs = PrintAttributes.Builder()
      .setMediaSize(mediaSize)
      .build()
    printManager.print(jobName, adapter, attrs)
  }

  private fun drawA4Invoice(
    context: Context,
    doc: PdfDocument,
    store: StoreConfig,
    invoice: Invoice
  ) {
    val pageWidth = 595
    val pageHeight = 842
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = doc.startPage(pageInfo)
    val c: Canvas = page.canvas

    val emerald = Color.rgb(15, 107, 91)
    val gold = Color.rgb(198, 139, 44)
    val ink = Color.rgb(26, 21, 18)
    val muted = Color.rgb(95, 87, 80)
    val lightBg = Color.rgb(250, 246, 240)

    val bgPaint = Paint().apply { color = Color.WHITE }
    c.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

    // Top brand header band
    val headerPaint = Paint().apply { color = Color.rgb(15, 61, 53) }
    c.drawRect(0f, 0f, pageWidth.toFloat(), 110f, headerPaint)

    // Brand logo bitmap
    runCatching {
      val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_turkishq_logo)
      if (bmp != null) {
        val scaled = Bitmap.createScaledBitmap(bmp, 66, 66, true)
        c.drawBitmap(scaled, 36f, 22f, null)
      }
    }

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 20f
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    val subWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(225, 238, 234)
      textSize = 10f
    }
    c.drawText(store.businessName.ifBlank { "TURKISHQ RETAIL" }, 116f, 44f, titlePaint)
    if (store.businessAddress.isNotBlank()) {
      c.drawText(store.businessAddress, 116f, 62f, subWhitePaint)
    }
    if (store.contactNumber.isNotBlank()) {
      c.drawText("Tel: ${store.contactNumber}", 116f, 78f, subWhitePaint)
    }

    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = gold
      textSize = 14f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.RIGHT
    }
    c.drawText("TAX INVOICE", pageWidth - 36f, 42f, badgePaint)
    val invNumWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 12f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      textAlign = Paint.Align.RIGHT
    }
    c.drawText(invoice.invoiceNumber, pageWidth - 36f, 62f, invNumWhite)
    if (invoice.status == "cancelled") {
      val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 180, 171)
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
      }
      c.drawText("STATUS: CANCELLED", pageWidth - 36f, 82f, cancelPaint)
    }

    // Metadata box
    var y = 132f
    val boxPaint = Paint().apply { color = lightBg }
    c.drawRect(36f, y, pageWidth - 36f, y + 66f, boxPaint)

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = muted
      textSize = 9.5f
      typeface = Typeface.DEFAULT_BOLD
    }
    val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ink
      textSize = 10.5f
    }
    c.drawText("INVOICE DATE & TIME", 48f, y + 20f, labelPaint)
    c.drawText(IstTime.formatDateTime(invoice.epochMillis), 48f, y + 36f, valPaint)
    c.drawText("CASHIER / STAFF: ${invoice.staffName}", 48f, y + 54f, valPaint)

    c.drawText("CUSTOMER DETAILS", 330f, y + 20f, labelPaint)
    val custName = invoice.customerName.ifBlank { "Walk-in Customer" }
    c.drawText(custName, 330f, y + 36f, valPaint)
    val custPhone = if (invoice.customerPhone.isNotBlank()) "Ph: ${invoice.customerPhone} • ${invoice.paymentMethod}" else "Payment: ${invoice.paymentMethod}"
    c.drawText(custPhone, 330f, y + 54f, valPaint)

    // Table header
    y += 88f
    val tableHeaderPaint = Paint().apply { color = emerald }
    c.drawRect(36f, y, pageWidth - 36f, y + 24f, tableHeaderPaint)
    val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 10f
      typeface = Typeface.DEFAULT_BOLD
    }
    val thRight = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }
    c.drawText("#", 44f, y + 16f, thPaint)
    c.drawText("PRODUCT", 68f, y + 16f, thPaint)
    c.drawText("QTY", 355f, y + 16f, thRight)
    c.drawText("UNIT PRICE", 445f, y + 16f, thRight)
    c.drawText("LINE TOTAL", pageWidth - 44f, y + 16f, thRight)

    y += 38f
    val rowRight = Paint(valPaint).apply { textAlign = Paint.Align.RIGHT }
    val dividerPaint = Paint().apply { color = Color.rgb(230, 224, 214); strokeWidth = 1f }

    invoice.items.forEachIndexed { idx, item ->
      if (y > 700f) return@forEachIndexed
      c.drawText("${idx + 1}", 44f, y, valPaint)
      c.drawText(item.productName.take(36), 68f, y, valPaint)
      c.drawText("${item.quantity}", 355f, y, rowRight)
      c.drawText(IstTime.formatInrExact(item.unitPrice, store.currencySymbol), 445f, y, rowRight)
      c.drawText(IstTime.formatInrExact(item.lineTotal, store.currencySymbol), pageWidth - 44f, y, rowRight)
      c.drawLine(36f, y + 8f, pageWidth - 36f, y + 8f, dividerPaint)
      y += 24f
    }

    // Totals summary on right
    y += 16f
    val totalLabel = Paint(valPaint).apply { textAlign = Paint.Align.RIGHT; color = muted }
    val totalVal = Paint(valPaint).apply { textAlign = Paint.Align.RIGHT; typeface = Typeface.DEFAULT_BOLD }

    c.drawText("Subtotal:", 445f, y, totalLabel)
    c.drawText(IstTime.formatInrExact(invoice.subtotal, store.currencySymbol), pageWidth - 44f, y, totalVal)
    y += 20f
    if (invoice.discountAmount > 0.0) {
      c.drawText("Discount:", 445f, y, totalLabel)
      c.drawText("-${IstTime.formatInrExact(invoice.discountAmount, store.currencySymbol)}", pageWidth - 44f, y, totalVal)
      y += 20f
    }
    if (invoice.taxAmount > 0.0) {
      c.drawText("Tax (${invoice.taxPercent}%):", 445f, y, totalLabel)
      c.drawText(IstTime.formatInrExact(invoice.taxAmount, store.currencySymbol), pageWidth - 44f, y, totalVal)
      y += 20f
    }

    c.drawRect(330f, y - 4f, pageWidth - 36f, y + 28f, tableHeaderPaint)
    val grandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 13f
      typeface = Typeface.DEFAULT_BOLD
      textAlign = Paint.Align.RIGHT
    }
    c.drawText("GRAND TOTAL", 445f, y + 17f, grandPaint)
    c.drawText(IstTime.formatInrExact(invoice.grandTotal, store.currencySymbol), pageWidth - 44f, y + 17f, grandPaint)

    // Footer
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = muted
      textSize = 10f
      textAlign = Paint.Align.CENTER
    }
    c.drawText(store.invoiceFooter.ifBlank { "Thank you for visiting TURKISHQ RETAIL!" }, pageWidth / 2f, pageHeight - 48f, footerPaint)
    c.drawText("Powered by TURKISHQ RETAIL POS • All amounts in INR (${store.currencySymbol})", pageWidth / 2f, pageHeight - 32f, footerPaint)

    doc.finishPage(page)
  }

  private fun draw80mmThermalReceipt(
    context: Context,
    doc: PdfDocument,
    store: StoreConfig,
    invoice: Invoice
  ) {
    val pageWidth = 226 // 80mm at 72dpi
    val estimatedHeight = 310 + (invoice.items.size * 28)
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, estimatedHeight.coerceAtLeast(360), 1).create()
    val page = doc.startPage(pageInfo)
    val c = page.canvas

    c.drawColor(Color.WHITE)
    val centerBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = 11f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
    }
    val centerSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.DKGRAY
      textSize = 7.5f
      typeface = Typeface.MONOSPACE
      textAlign = Paint.Align.CENTER
    }
    val leftText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = 7.8f
      typeface = Typeface.MONOSPACE
    }
    val rightText = Paint(leftText).apply { textAlign = Paint.Align.RIGHT }
    val dashPaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 0.8f }

    var y = 20f
    runCatching {
      val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_turkishq_logo)
      if (bmp != null) {
        val scaled = Bitmap.createScaledBitmap(bmp, 36, 36, true)
        c.drawBitmap(scaled, (pageWidth - 36) / 2f, y - 10f, null)
        y += 34f
      }
    }
    c.drawText(store.businessName.ifBlank { "TURKISHQ RETAIL" }, pageWidth / 2f, y, centerBold)
    y += 12f
    if (store.businessAddress.isNotBlank()) {
      c.drawText(store.businessAddress.take(36), pageWidth / 2f, y, centerSmall)
      y += 10f
    }
    if (store.contactNumber.isNotBlank()) {
      c.drawText("Tel: ${store.contactNumber}", pageWidth / 2f, y, centerSmall)
      y += 10f
    }
    c.drawLine(8f, y, pageWidth - 8f, y, dashPaint)
    y += 12f

    c.drawText("INV: ${invoice.invoiceNumber}", 10f, y, leftText)
    y += 11f
    c.drawText(IstTime.formatDateTime(invoice.epochMillis), 10f, y, leftText)
    y += 11f
    c.drawText("Staff: ${invoice.staffName}", 10f, y, leftText)
    y += 11f
    if (invoice.customerName.isNotBlank()) {
      c.drawText("Cust: ${invoice.customerName}", 10f, y, leftText)
      y += 11f
    }
    if (invoice.status == "cancelled") {
      c.drawText("*** CANCELLED INVOICE ***", pageWidth / 2f, y, centerBold)
      y += 12f
    }
    c.drawLine(8f, y, pageWidth - 8f, y, dashPaint)
    y += 12f

    c.drawText("ITEM", 10f, y, leftText)
    c.drawText("QTY  TOTAL", pageWidth - 10f, y, rightText)
    y += 10f
    c.drawLine(8f, y, pageWidth - 8f, y, dashPaint)
    y += 12f

    invoice.items.forEach { item ->
      c.drawText(item.productName.take(26), 10f, y, leftText)
      y += 10f
      c.drawText("  ${item.quantity} x ${IstTime.formatInr(item.unitPrice, store.currencySymbol)}", 10f, y, leftText)
      c.drawText(IstTime.formatInrExact(item.lineTotal, store.currencySymbol), pageWidth - 10f, y, rightText)
      y += 13f
    }

    c.drawLine(8f, y, pageWidth - 8f, y, dashPaint)
    y += 12f
    c.drawText("Subtotal", 10f, y, leftText)
    c.drawText(IstTime.formatInrExact(invoice.subtotal, store.currencySymbol), pageWidth - 10f, y, rightText)
    y += 11f
    if (invoice.discountAmount > 0.0) {
      c.drawText("Discount", 10f, y, leftText)
      c.drawText("-${IstTime.formatInrExact(invoice.discountAmount, store.currencySymbol)}", pageWidth - 10f, y, rightText)
      y += 11f
    }
    if (invoice.taxAmount > 0.0) {
      c.drawText("Tax (${invoice.taxPercent}%)", 10f, y, leftText)
      c.drawText(IstTime.formatInrExact(invoice.taxAmount, store.currencySymbol), pageWidth - 10f, y, rightText)
      y += 11f
    }
    c.drawLine(8f, y, pageWidth - 8f, y, dashPaint)
    y += 14f

    val grandLeft = Paint(leftText).apply { textSize = 9.5f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    val grandRight = Paint(grandLeft).apply { textAlign = Paint.Align.RIGHT }
    c.drawText("GRAND TOTAL", 10f, y, grandLeft)
    c.drawText(IstTime.formatInrExact(invoice.grandTotal, store.currencySymbol), pageWidth - 10f, y, grandRight)
    y += 13f
    c.drawText("Paid via: ${invoice.paymentMethod}", 10f, y, leftText)
    y += 16f

    c.drawLine(8f, y, pageWidth - 8f, y, dashPaint)
    y += 12f
    c.drawText(store.invoiceFooter.take(36), pageWidth / 2f, y, centerSmall)
    doc.finishPage(page)
  }

  fun exportSalesReportCsv(
    context: Context,
    title: String,
    rows: List<List<String>>
  ): File {
    val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val file = File(exportsDir, "$safeName.csv")
    file.writeText(
      rows.joinToString("\n") { cols ->
        cols.joinToString(",") { cell -> "\"${cell.replace("\"", "\"\"")}\"" }
      }
    )
    return file
  }

  fun exportSalesReportPdf(
    context: Context,
    store: StoreConfig,
    reportTitle: String,
    dateRangeLabel: String,
    summaryLines: List<Pair<String, String>>,
    tableHeaders: List<String>,
    tableRows: List<List<String>>
  ): File {
    val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val safeName = reportTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val file = File(exportsDir, "$safeName.pdf")
    val doc = PdfDocument()
    try {
      val pageWidth = 595
      val pageHeight = 842
      var pageNum = 1
      var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
      var c = page.canvas

      val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(15, 61, 53)
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
      }
      val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 10f
      }
      val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
      }
      val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 9.5f
      }

      var y = 40f
      c.drawText("${store.businessName} — $reportTitle", 36f, y, titlePaint)
      y += 18f
      c.drawText("Period: $dateRangeLabel • Generated: ${IstTime.formatDateTime(System.currentTimeMillis())}", 36f, y, subPaint)
      y += 22f

      summaryLines.forEach { (k, v) ->
        c.drawText("$k: $v", 36f, y, boldPaint)
        y += 16f
      }
      y += 12f

      // Table header
      val colWidth = (pageWidth - 72f) / tableHeaders.size.coerceAtLeast(1)
      val headerBg = Paint().apply { color = Color.rgb(15, 107, 91) }
      c.drawRect(36f, y, pageWidth - 36f, y + 22f, headerBg)
      val whiteBold = Paint(boldPaint).apply { color = Color.WHITE }
      tableHeaders.forEachIndexed { i, h ->
        c.drawText(h.take(20), 42f + (i * colWidth), y + 15f, whiteBold)
      }
      y += 32f

      tableRows.forEach { row ->
        if (y > pageHeight - 48f) {
          doc.finishPage(page)
          pageNum++
          page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
          c = page.canvas
          y = 44f
        }
        row.forEachIndexed { i, cell ->
          c.drawText(cell.take(24), 42f + (i * colWidth), y, bodyPaint)
        }
        y += 18f
      }
      doc.finishPage(page)
      FileOutputStream(file).use { out -> doc.writeTo(out) }
    } finally {
      doc.close()
    }
    return file
  }
}
