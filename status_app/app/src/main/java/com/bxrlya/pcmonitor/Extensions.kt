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
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- ОКРАСКА ЧАСТИ ТЕКСТА В ВЫДЕЛЯЮЩИЙ ЦВЕТ
fun String.coloredSpan(start: Int, end: Int, context: Context): Spannable {
    val spannable = SpannableString(this)
    val color = "#f7f2f2"

    spannable.setSpan(
        ForegroundColorSpan(color.toColorInt()),
        start,
        end,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return spannable
}

// --- ПРАВИЛЬНОЕ СКЛОНЕНИЕ СЛОВА
// 1 - час, 2 - секунда, 3 - минута
fun getTimeString(number: Long, key: Int): String {
    val lastTwoDigits = number % 100
    val lastDigit = number % 10

    val forms = when (key) {
        1 -> listOf("час", "часа", "часов")
        2 -> listOf("секунда", "секунды", "секунд")
        3 -> listOf("минута", "минуты", "минут")
        else -> listOf("единица", "единицы", "единиц")
    }

    val word = when {
        lastTwoDigits in 11..14 -> forms[2]
        lastDigit == 1L -> forms[0]
        lastDigit in 2..4 -> forms[1]
        else -> forms[2]
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

// --- АЛЕРТ НА ОШИБКИ
var alertDialogErrorIsShowing: AlertDialog? = null

suspend fun showErrorDialog(
    context: Context,
    title: String,
    message: String
) {
    withContext(Dispatchers.Main) {
        if (alertDialogErrorIsShowing?.isShowing == true) {
            return@withContext
        }

        alertDialogErrorIsShowing = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("ОК") { dialog, _ ->
                dialog.dismiss()
                alertDialogErrorIsShowing = null
            }
            .setOnDismissListener {
                alertDialogErrorIsShowing = null
            }
            .show()
    }
}

fun nonSuspendShowErrorDialog(
    context: Context,
    title: String,
    message: String
) {
    if (alertDialogErrorIsShowing?.isShowing == true) {
        return
    }

    alertDialogErrorIsShowing = AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("ОК") { dialog, _ ->
            dialog.dismiss()
            alertDialogErrorIsShowing = null
        }
        .setOnDismissListener {
            alertDialogErrorIsShowing = null
        }
        .show()
}