package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object IstTime {
  val timeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
  private val indianLocale = Locale("en", "IN")

  fun formatDate(epochMillis: Long): String {
    val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    sdf.timeZone = timeZone
    return sdf.format(Date(epochMillis))
  }

  fun formatDateTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US)
    sdf.timeZone = timeZone
    return "${sdf.format(Date(epochMillis))} IST"
  }

  fun formatTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.US)
    sdf.timeZone = timeZone
    return sdf.format(Date(epochMillis))
  }

  fun parseDateStartMillis(ddMmYyyy: String): Long? {
    return try {
      val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = this@IstTime.timeZone
        isLenient = false
      }
      val parsed = sdf.parse(ddMmYyyy.trim()) ?: return null
      startOfDayMillis(parsed.time)
    } catch (_: Exception) {
      null
    }
  }

  fun parseDateEndMillis(ddMmYyyy: String): Long? {
    val start = parseDateStartMillis(ddMmYyyy) ?: return null
    return endOfDayMillis(start)
  }

  fun startOfDayMillis(epochMillis: Long): Long {
    val cal = Calendar.getInstance(timeZone).apply {
      timeInMillis = epochMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }

  fun endOfDayMillis(epochMillis: Long): Long {
    val cal = Calendar.getInstance(timeZone).apply {
      timeInMillis = epochMillis
      set(Calendar.HOUR_OF_DAY, 23)
      set(Calendar.MINUTE, 59)
      set(Calendar.SECOND, 59)
      set(Calendar.MILLISECOND, 999)
    }
    return cal.timeInMillis
  }

  fun todayDateKey(nowMillis: Long = System.currentTimeMillis()): String = formatDate(nowMillis)

  fun yesterdayStartEnd(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val cal = Calendar.getInstance(timeZone).apply {
      timeInMillis = nowMillis
      add(Calendar.DAY_OF_YEAR, -1)
    }
    val start = startOfDayMillis(cal.timeInMillis)
    val end = endOfDayMillis(cal.timeInMillis)
    return start to end
  }

  fun weekStartMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance(timeZone).apply {
      timeInMillis = nowMillis
      firstDayOfWeek = Calendar.MONDAY
      set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    return startOfDayMillis(cal.timeInMillis)
  }

  fun monthStartMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance(timeZone).apply {
      timeInMillis = nowMillis
      set(Calendar.DAY_OF_MONTH, 1)
    }
    return startOfDayMillis(cal.timeInMillis)
  }

  fun currentYear(nowMillis: Long = System.currentTimeMillis()): Int {
    val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
    return cal.get(Calendar.YEAR)
  }

  fun lastNDaysKeys(days: Int, nowMillis: Long = System.currentTimeMillis()): List<String> {
    val cal = Calendar.getInstance(timeZone).apply {
      timeInMillis = nowMillis
      add(Calendar.DAY_OF_YEAR, -(days - 1))
    }
    return List(days) { idx ->
      val key = formatDate(cal.timeInMillis)
      cal.add(Calendar.DAY_OF_YEAR, 1)
      key
    }
  }

  fun formatInr(amount: Double, symbol: String = "₹"): String {
    val formatter = NumberFormat.getNumberInstance(indianLocale).apply {
      minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
      maximumFractionDigits = 2
    }
    val prefix = if (amount < 0) "-" else ""
    return "$prefix$symbol${formatter.format(kotlin.math.abs(amount))}"
  }

  fun formatInrExact(amount: Double, symbol: String = "₹"): String {
    val formatter = NumberFormat.getNumberInstance(indianLocale).apply {
      minimumFractionDigits = 2
      maximumFractionDigits = 2
    }
    val prefix = if (amount < 0) "-" else ""
    return "$prefix$symbol${formatter.format(kotlin.math.abs(amount))}"
  }
}
