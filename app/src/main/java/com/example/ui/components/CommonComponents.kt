package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.Invoice
import com.example.data.model.Product
import com.example.data.model.StoreConfig
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SaffronGold
import com.example.ui.theme.SuccessGreen
import com.example.util.InvoicePdfAndPrintHelper
import com.example.util.InvoicePrintFormat
import com.example.util.IstTime
import java.io.ByteArrayOutputStream

@Composable
fun TurkishqLogoBadge(size: Dp = 44.dp, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(id = R.drawable.img_turkishq_logo),
    contentDescription = "TURKISHQ RETAIL Logo",
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(12.dp))
      .border(1.dp, SaffronGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
    contentScale = ContentScale.Crop
  )
}

@Composable
fun ProductThumbnail(
  product: Product,
  size: Dp = 56.dp,
  modifier: Modifier = Modifier
) {
  val customBitmap = remember(product.imageBase64) {
    if (product.imageBase64.isNotBlank()) {
      runCatching {
        val bytes = Base64.decode(product.imageBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
      }.getOrNull()
    } else null
  }

  if (customBitmap != null) {
    Image(
      bitmap = customBitmap,
      contentDescription = product.name,
      modifier = modifier
        .size(size)
        .clip(RoundedCornerShape(12.dp)),
      contentScale = ContentScale.Crop
    )
  } else {
    val drawableId = when (product.imageKey.lowercase()) {
      "baklava" -> R.drawable.img_prod_baklava
      "tea", "coffee" -> R.drawable.img_prod_turkish_tea
      else -> null
    }
    if (drawableId != null) {
      Image(
        painter = painterResource(id = drawableId),
        contentDescription = product.name,
        modifier = modifier
          .size(size)
          .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
      )
    } else {
      val icon: ImageVector = when (product.imageKey.lowercase()) {
        "tea" -> Icons.Filled.LocalCafe
        "coffee" -> Icons.Filled.Coffee
        "juice" -> Icons.Filled.LocalDrink
        "baklava", "delight" -> Icons.Filled.BakeryDining
        "dessert" -> Icons.Filled.Cake
        "icecream" -> Icons.Filled.Icecream
        "snack", "chocolate" -> Icons.Filled.Cookie
        else -> Icons.Filled.ShoppingBag
      }
      Box(
        modifier = modifier
          .size(size)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = product.name,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(size * 0.52f)
        )
      }
    }
  }
}

fun encodeUriToCompressedBase64(context: android.content.Context, uri: Uri): String {
  return runCatching {
    val input = context.contentResolver.openInputStream(uri) ?: return ""
    val original = BitmapFactory.decodeStream(input)
    input.close()
    if (original == null) return ""
    val maxDim = 320
    val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
    val scaled = Bitmap.createScaledBitmap(
      original,
      (original.width * scale).toInt().coerceAtLeast(1),
      (original.height * scale).toInt().coerceAtLeast(1),
      true
    )
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 72, baos)
    Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
  }.getOrDefault("")
}

@Composable
fun KpiStatCard(
  title: String,
  value: String,
  subtitle: String,
  icon: ImageVector,
  accentColor: Color = EmeraldPrimary,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(18.dp))
        }
      }
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun RevenueBarChart(
  series: List<Pair<String, Double>>,
  currencySymbol: String,
  modifier: Modifier = Modifier
) {
  val barColor = EmeraldPrimary
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
  val maxVal = remember(series) { (series.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(100.0) }

  Column(modifier = modifier.fillMaxWidth()) {
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(160.dp)
        .padding(top = 8.dp, bottom = 6.dp)
    ) {
      val count = series.size.coerceAtLeast(1)
      val slotW = size.width / count
      val barW = (slotW * 0.54f).coerceAtMost(42.dp.toPx())

      for (step in 1..3) {
        val y = size.height * (step / 4f)
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
      }

      series.forEachIndexed { idx, (_, amount) ->
        val ratio = (amount / maxVal).toFloat().coerceIn(0f, 1f)
        val barH = (size.height * ratio).coerceAtLeast(if (amount > 0) 6.dp.toPx() else 2.dp.toPx())
        val left = idx * slotW + (slotW - barW) / 2f
        val top = size.height - barH
        drawRoundRect(
          color = if (amount > 0) barColor else gridColor,
          topLeft = Offset(left, top),
          size = Size(barW, barH),
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
      series.forEach { (dayLabel, amount) ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
          Text(
            text = dayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
          )
          Text(
            text = if (amount > 0) IstTime.formatInr(amount, currencySymbol) else "-",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.82f),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
          )
        }
      }
    }
  }
}

@Composable
fun PaymentMethodDonutChart(
  breakdown: Map<String, Double>,
  currencySymbol: String,
  modifier: Modifier = Modifier
) {
  val palette = mapOf(
    "Cash" to EmeraldPrimary,
    "UPI" to SaffronGold,
    "Card" to Color(0xFF2B6CB0),
    "Other" to Color(0xFF7B5EA7)
  )
  val total = breakdown.values.sum()

  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(18.dp)
  ) {
    Box(
      modifier = Modifier.size(124.dp),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 20.dp.toPx()
        if (total <= 0.0) {
          drawArc(
            color = Color.LightGray.copy(alpha = 0.35f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
          )
        } else {
          var start = -90f
          breakdown.entries.forEach { (method, amount) ->
            if (amount > 0.0) {
              val sweep = ((amount / total) * 360.0).toFloat()
              drawArc(
                color = palette[method] ?: EmeraldDeep,
                startAngle = start,
                sweepAngle = sweep - 2f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
              )
              start += sweep
            }
          }
        }
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          IstTime.formatInr(total, currencySymbol),
          style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
          fontWeight = FontWeight.Bold
        )
      }
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      listOf("Cash", "UPI", "Card", "Other").forEach { method ->
        val amt = breakdown[method] ?: 0.0
        val pct = if (total > 0) ((amt / total) * 100).toInt() else 0
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(palette[method] ?: EmeraldDeep)
            )
            Text(method, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
          }
          Text(
            "${IstTime.formatInr(amt, currencySymbol)} ($pct%)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
fun InvoicePreviewDialog(
  storeConfig: StoreConfig,
  invoice: Invoice,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  var selectedFormat by remember { mutableStateOf(InvoicePrintFormat.A4) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .padding(vertical = 16.dp),
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 4.dp
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Invoice Preview", style = MaterialTheme.typography.titleLarge)
            Text(
              invoice.invoiceNumber,
              style = MaterialTheme.typography.labelSmall,
              color = EmeraldPrimary
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_invoice_preview_button")) {
            Icon(Icons.Filled.Close, contentDescription = "Close preview")
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(vertical = 8.dp)
        ) {
          InvoicePrintFormat.entries.forEach { fmt ->
            FilterChip(
              selected = selectedFormat == fmt,
              onClick = { selectedFormat = fmt },
              label = { Text(fmt.label) }
            )
          }
        }

        // Branded Receipt / Invoice Card
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          shape = RoundedCornerShape(14.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
          val isThermal = selectedFormat == InvoicePrintFormat.THERMAL_80MM
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(if (isThermal) 14.dp else 18.dp),
            horizontalAlignment = if (isThermal) Alignment.CenterHorizontally else Alignment.Start
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              TurkishqLogoBadge(size = 44.dp)
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  storeConfig.businessName.ifBlank { "TURKISHQ RETAIL" },
                  style = MaterialTheme.typography.titleMedium,
                  color = EmeraldDeep,
                  fontWeight = FontWeight.Bold
                )
                if (storeConfig.businessAddress.isNotBlank()) {
                  Text(storeConfig.businessAddress, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                }
                if (storeConfig.contactNumber.isNotBlank()) {
                  Text("Tel: ${storeConfig.contactNumber}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                }
              }
            }

            if (invoice.status == "cancelled") {
              Spacer(Modifier.height(8.dp))
              Surface(color = CrimsonError.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                Text(
                  "CANCELLED (${invoice.cancelReason})",
                  color = CrimsonError,
                  style = MaterialTheme.typography.labelMedium,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFFE5DFD5))

            val mono = if (isThermal) FontFamily.Monospace else MonoFontFamily
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Column {
                Text("Invoice #: ${invoice.invoiceNumber}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = mono), color = Color.Black)
                Text("Date: ${IstTime.formatDateTime(invoice.epochMillis)}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                Text("Staff: ${invoice.staffName}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
              }
              Column(horizontalAlignment = Alignment.End) {
                if (invoice.customerName.isNotBlank()) {
                  Text(invoice.customerName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }
                if (invoice.customerPhone.isNotBlank()) {
                  Text(invoice.customerPhone, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                }
                Text("Paid: ${invoice.paymentMethod}", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
              }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFFE5DFD5))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("ITEM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.DarkGray, modifier = Modifier.weight(1.8f))
              Text("QTY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.DarkGray, textAlign = TextAlign.End, modifier = Modifier.weight(0.5f))
              Text("RATE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.DarkGray, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
              Text("TOTAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.DarkGray, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))

            invoice.items.forEach { item ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(item.productName, style = MaterialTheme.typography.bodySmall, color = Color.Black, modifier = Modifier.weight(1.8f))
                Text("${item.quantity}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono), color = Color.Black, textAlign = TextAlign.End, modifier = Modifier.weight(0.5f))
                Text(IstTime.formatInr(item.unitPrice, storeConfig.currencySymbol), style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono), color = Color.DarkGray, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                Text(IstTime.formatInrExact(item.lineTotal, storeConfig.currencySymbol), style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono), fontWeight = FontWeight.SemiBold, color = Color.Black, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
              }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFFE5DFD5))

            SummaryLineRow("Subtotal", IstTime.formatInrExact(invoice.subtotal, storeConfig.currencySymbol))
            if (invoice.discountAmount > 0.0) {
              SummaryLineRow("Discount", "-${IstTime.formatInrExact(invoice.discountAmount, storeConfig.currencySymbol)}")
            }
            if (invoice.taxAmount > 0.0) {
              SummaryLineRow("Tax (${invoice.taxPercent}%)", IstTime.formatInrExact(invoice.taxAmount, storeConfig.currencySymbol))
            }
            Spacer(Modifier.height(6.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(EmeraldDeep.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("GRAND TOTAL", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = EmeraldDeep)
              Text(
                IstTime.formatInrExact(invoice.grandTotal, storeConfig.currencySymbol),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.Bold,
                color = EmeraldDeep
              )
            }

            Spacer(Modifier.height(12.dp))
            Text(
              storeConfig.invoiceFooter.ifBlank { "Thank you for visiting TURKISHQ RETAIL!" },
              style = MaterialTheme.typography.bodySmall,
              color = Color.DarkGray,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }

        Spacer(Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = {
              val file = InvoicePdfAndPrintHelper.generateInvoicePdfFile(context, storeConfig, invoice, selectedFormat)
              InvoicePdfAndPrintHelper.shareOrSaveFile(context, file, "application/pdf", "Invoice ${invoice.invoiceNumber}")
            },
            modifier = Modifier
              .weight(1f)
              .testTag("download_invoice_pdf_button")
          ) {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("PDF / Share")
          }
          Button(
            onClick = {
              InvoicePdfAndPrintHelper.printInvoiceDirect(context, storeConfig, invoice, selectedFormat)
            },
            modifier = Modifier
              .weight(1f)
              .testTag("print_invoice_button")
          ) {
            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Print (${if (selectedFormat == InvoicePrintFormat.A4) "A4" else "80mm"})")
          }
        }
      }
    }
  }
}

@Composable
private fun SummaryLineRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
    Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = Color.Black)
  }
}

@Composable
fun ConfirmActionDialog(
  title: String,
  message: String,
  confirmLabel: String,
  isDestructive: Boolean = true,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors = if (isDestructive) {
          androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CrimsonError)
        } else {
          androidx.compose.material3.ButtonDefaults.buttonColors()
        }
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
