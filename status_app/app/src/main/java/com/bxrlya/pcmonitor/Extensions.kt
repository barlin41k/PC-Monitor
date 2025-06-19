package com.bxrlya.pcmonitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.graphics.toColorInt
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// --- ОКРАСКА ЧАСТИ ТЕКСТА В ВЫДЕЛЯЮЩИЙ ЦВЕТ
fun String.coloredSpan(start: Int, end: Int, context: Context): Spannable {
    val spannable = SpannableString(this)

    val isDarkTheme = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val color = if (isDarkTheme) "#f7f2f2" else "#050505"

    spannable.setSpan(
        ForegroundColorSpan(color.toColorInt()),
        start,
        end,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return spannable
}

// --- ПРАВИЛЬНОЕ СКЛОНЕНИЕ СЛОВА
fun getHourString(number: Int): String {
    val lastTwoDigits = number % 100
    val lastDigit = number % 10

    val word = when {
        lastTwoDigits in 11..14 -> "часов"
        lastDigit == 1 -> "час"
        lastDigit in 2..4 -> "часа"
        else -> "часов"
    }

    return "$number $word"
}

// --- ОТПРАВЛЕНИЕ УВЕДОМЛЕНИЯ
fun Context.sendNotification(title: String, message: String, channelId: String = "pc_monitor_channel") {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
    }


    val channel = NotificationChannel(
        channelId,
        "PC Monitor Alerts",
        NotificationManager.IMPORTANCE_HIGH
    )
    manager.createNotificationChannel(channel)


    val notification = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.ic_launcher_notify)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

    NotificationManagerCompat.from(this).notify(channelId.hashCode(), notification)
}

// --- ПРОВЕРИТЬ ПОРОГ -> (НЕ) ОТОСЛАТЬ УВЕДОМЛЕНИЕ
fun checkThreshold(
    currentValue: Double,
    threshold: Double,
    notifiedFlag: Boolean,
    onNotify: () -> Unit
): Boolean {
    return if (currentValue > threshold && !notifiedFlag) {
        onNotify()
        true
    } else if (currentValue <= threshold) {
        false
    } else {
        notifiedFlag
    }
}