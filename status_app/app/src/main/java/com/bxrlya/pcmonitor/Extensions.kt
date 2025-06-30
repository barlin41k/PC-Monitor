package com.bxrlya.pcmonitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

// --- ОКРАСКА ЧАСТИ ТЕКСТА В ВЫДЕЛЯЮЩИЙ ЦВЕТ
fun String.coloredSpan(delimiter: Char = ':'): Spannable {
    val spannable = SpannableString(this)
    val prefixEnd = indexOf(delimiter) + 1

    if (prefixEnd > 0) {
        val color = "#f7f2f2"
        spannable.setSpan(
            ForegroundColorSpan(color.toColorInt()),
            0,
            prefixEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

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
    message: String,
    isUpdateChecker: Boolean = false
) {
    if (alertDialogErrorIsShowing?.isShowing == true) {
        return
    }
    if (!isUpdateChecker) {
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
    } else {
        alertDialogErrorIsShowing = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("ОК") { dialog, _ ->
                dialog.dismiss()
                alertDialogErrorIsShowing = null
                val intent = Intent(Intent.ACTION_VIEW,
                    "https://github.com/barlin41k/PC-Monitor/releases".toUri())
                context.startActivity(intent)
            }
            .setNegativeButton("Потом") { dialog, _ ->
                dialog.dismiss()
                alertDialogErrorIsShowing = null
            }
            .show()
    }
}

// --- ПРОВЕРКА ВЕРСИИ
fun checkForUpdate(context: Context): Pair<Boolean, String> {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://api.github.com/repos/barlin41k/PC-Monitor/releases/latest")
        .build()

    val response = client.newCall(request).execute()
    val body = response.body?.string() ?: return Pair(false, "?.?.?")

    val latestVersion = JSONObject(body).getString("tag_name")

    val currentVersion = context.packageManager
        .getPackageInfo(context.packageName, 0).versionName!! // принудительно (!!)

    return Pair(isNewerVersion(latestVersion, currentVersion), latestVersion)
}
fun isNewerVersion(latest: String, current: String): Boolean {
    return latest != current
}

fun checkUpdate(context: Context, serverIp: String, isAppVisible: Boolean) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val (updateAvailable, newVersion) = checkForUpdate(context)

            if (updateAvailable) {
                withContext(Dispatchers.Main) {
                    nonSuspendShowErrorDialog(
                        context,
                        context.getString(R.string.update_available, newVersion),
                        context.getString(R.string.update_available_text),
                        true
                    )
                }
            }
        } catch (_: IOException) {
            if (isAppVisible) {
                showErrorDialog(
                    context,
                    context.getString(R.string.no_internet_connection),
                    context.getString(R.string.no_internet_connection_text, serverIp)
                )
            }
        } catch (e: Exception) {
            if (isAppVisible) {
                showErrorDialog(
                    context,
                    context.getString(R.string.unknown_error),
                    e.message ?: context.getString(R.string.unknown_error_text)
                )
            }
        }
    }
}

// --- ПРОЦЕНТЫ
fun Double.percentOf(total: Double): Double = if (total != 0.0) this / total * 100 else 0.0