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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.bxrlya.pcmonitor.SettingsActivity.Companion.KEY_DELAY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress


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
        .setSmallIcon(R.drawable.ic_launcher_foreground)
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

const val KEY_SERVER_IP = "server_ip"
const val DEFAULT_SERVER_IP = "192.168.1.33"
const val DEFAULT_DELAY_GET_REQ = 5L
fun nonSuspendShowErrorDialog(
    context: Context,
    title: String,
    message: String,
    isUpdateChecker: Boolean = false,
    isPrefsButton: Boolean = false
) {
    if (alertDialogErrorIsShowing?.isShowing == true) {
        return
    }

    val builder = AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("ОК") { dialog, _ ->
            dialog.dismiss()
            alertDialogErrorIsShowing = null
        }
        .setOnDismissListener {
            alertDialogErrorIsShowing = null
        }

    if (isUpdateChecker) {
        builder.setPositiveButton("ОК") { dialog, _ ->
            dialog.dismiss()
            alertDialogErrorIsShowing = null
            val intent = Intent(Intent.ACTION_VIEW,
                "https://github.com/barlin41k/PC-Monitor/releases".toUri())
            context.startActivity(intent)
        }
        builder.setNegativeButton("Потом") { dialog, _ ->
            dialog.dismiss()
            alertDialogErrorIsShowing = null
        }
    }

    if (isPrefsButton) {
        builder.setNeutralButton("Сбросить") { dialog, _ ->
            dialog.dismiss()
            alertDialogErrorIsShowing = null
            val sharedPrefs = context.getSharedPreferences(MainActivity.KEY_SETTINGS, Context.MODE_PRIVATE)
            sharedPrefs.edit {
                putLong(KEY_DELAY, DEFAULT_DELAY_GET_REQ)
                putString(KEY_SERVER_IP, DEFAULT_SERVER_IP)
            }
            Toast.makeText(context, "Настройки сброшены!", Toast.LENGTH_SHORT).show()
        }
    }

    alertDialogErrorIsShowing = builder.show()
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
            IOExceptionCatch(context, isAppVisible, serverIp.toString())
        } catch (e: Exception) {
            val string = context.getString(R.string.unknown_error_text)
            unknownExceptionCatch(context, isAppVisible, e.message ?: string)
        }
    }
}

// --- ЗАГРУЗКА JSON
fun fetchStatus(serverIp: String, client: OkHttpClient): JsonObject? {
    return try {
        val url = "http://$serverIp:8080/status"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null
        Json.parseToJsonElement(body).jsonObject
    } catch (_: IOException) {
        null
    }
}

// --- ПРОВЕРИТЬ КОРРЕКТНОСТЬ АЙПИ
fun isValidIPv4(ip: String): Boolean {
    return try {
        val inet = InetAddress.getByName(ip)
        inet.hostAddress == ip && inet.address.size == 4
    } catch (_: Exception) {
        false
    }
}

// --- ПРОЦЕНТЫ
fun Double.percentOf(total: Double): Double = if (total != 0.0) this / total * 100 else 0.0

// --- getString+coloredSpan ПОВТОРЫ
fun Context.cs(id: Int, vararg args: Any): CharSequence = getString(id, *args).coloredSpan()

// --- ЗАДЕРЖКА В МС
fun Long.toMiliSec(): Long = this*1000L