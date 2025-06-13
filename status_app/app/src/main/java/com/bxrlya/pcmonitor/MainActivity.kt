package com.bxrlya.pcmonitor

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private var serverIp = "192.168.1.33"
    private var updateJob: Job? = null

    fun String.coloredSpan(colorHex: String, start: Int, end: Int): SpannableString {
        val spannable = SpannableString(this)
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor(colorHex)),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val cpuLoadLabel = findViewById<TextView>(R.id.cpu_load)
        //val cpuTempLabel = findViewById<TextView>(R.id.cpu_temp)

        val totalMemLabel = findViewById<TextView>(R.id.total_mem)
        val freeMemLabel = findViewById<TextView>(R.id.free_mem)

        val totalDiskLabel = findViewById<TextView>(R.id.total_disk)
        val freeDiskLabel = findViewById<TextView>(R.id.free_disk)

        val isChargingLabel = findViewById<TextView>(R.id.is_charging)
        val percentChargingLabel = findViewById<TextView>(R.id.percent_charging)
        val timeRemainingBatteryLabel = findViewById<TextView>(R.id.time_remaining_battery)

        val osUptimeHoursLabel = findViewById<TextView>(R.id.os_uptime_hours)

        val ipInput = findViewById<EditText>(R.id.ip_input)
        val applyIpButton = findViewById<Button>(R.id.apply_ip)

        ipInput.setText(serverIp)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fun startUpdating() {
            updateJob?.cancel()
            updateJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    try {
                        val url = "http://$serverIp:8080/status"
                        val request = Request.Builder().url(url).build()
                        val response = client.newCall(request).execute()
                        val body = response.body?.string() ?: ""

                        val json = Json.parseToJsonElement(body).jsonObject

                        val cpuTopic = json["cpu"]?.jsonObject
                        val memTopic = json["mem"]?.jsonObject
                        val diskTopic = json["disk"]?.jsonObject
                        val batteryTopic = json["battery"]?.jsonObject
                        val osTopic = json["os"]?.jsonObject

                        val cpuLoad = cpuTopic?.get("load")?.jsonPrimitive?.double ?: 0.0
                        //val cpuTemp = cpuTopic?.get("temp")?.jsonPrimitive?.double ?: 0.0

                        val totalMemory = memTopic?.get("total")?.jsonPrimitive?.double ?: 0.0
                        val usedMemory = memTopic?.get("used")?.jsonPrimitive?.double ?: 0.0
                        val freeMemory = memTopic?.get("free")?.jsonPrimitive?.double ?: 0.0

                        val totalDisk = diskTopic?.get("total")?.jsonPrimitive?.double ?: 0.0
                        val usedDisk = diskTopic?.get("used")?.jsonPrimitive?.double ?: 0.0
                        val freeDisk = diskTopic?.get("free")?.jsonPrimitive?.double ?: 0.0

                        val isCharging = batteryTopic?.get("is")?.jsonPrimitive?.boolean ?: false
                        val percentCharging = batteryTopic?.get("percent")?.jsonPrimitive?.double ?: 0.0
                        val remainingTime = batteryTopic?.get("remaining")?.jsonPrimitive?.doubleOrNull

                        val osUptimeHours = osTopic?.get("up")?.jsonPrimitive?.int ?: 0

                        withContext(Dispatchers.Main) {
                            val memPercent1 = if (totalMemory != 0.0) usedMemory / totalMemory * 100 else "Ошибка вычисления"
                            val memPercent2 = if (totalMemory != 0.0) freeMemory / totalMemory * 100 else "Ошибка вычисления"
                            val diskPercent1 = if (totalDisk != 0.0) usedDisk / totalDisk * 100 else "Ошибка вычисления"
                            val diskPercent2 = if (totalDisk != 0.0) freeDisk / totalDisk * 100 else "Ошибка вычисления"

                            cpuLoadLabel.text = "Загрузка процессора: ${"%.2f".format(cpuLoad)}%".coloredSpan("#f7f2f2", 0, 20)
                            //cpuTempLabel.text = "Температура процессора: ${"%.2f".format(cpuTemp)}°C".coloredSpan("#f7f2f2", 0, 23)

                            totalMemLabel.text = "Загрузка ОЗУ: ${"%.2f".format(usedMemory)}/${"%.2f".format(totalMemory)} ГБ (${ "%.2f".format(memPercent1)}%)".coloredSpan("#f7f2f2", 0, 13)
                            freeMemLabel.text = "Свободно ОЗУ: ${"%.2f".format(freeMemory)} ГБ (${ "%.2f".format(memPercent2)}%)".coloredSpan("#f7f2f2", 0, 13)

                            totalDiskLabel.text = "Занято на всех дисках: ${"%.2f".format(usedDisk)}/${"%.2f".format(totalDisk)} ГБ (${ "%.2f".format(diskPercent1)}%)".coloredSpan("#f7f2f2", 0, 22)
                            freeDiskLabel.text = "Свободно на всех дисках: ${"%.2f".format(freeDisk)} ГБ (${ "%.2f".format(diskPercent2)}%)".coloredSpan("#f7f2f2", 0, 24)

                            isChargingLabel.text = when {
                                isCharging == true -> "Батарея: заряжается".coloredSpan("#f7f2f2", 0, 8)
                                isCharging == false && percentCharging.toInt() != 100 -> "Батарея: не заряжается".coloredSpan("#f7f2f2", 0, 8)
                                percentCharging.toInt() == 100 -> "Батарея: заряжена полностью".coloredSpan("#f7f2f2", 0, 8)
                                else -> "Батарея: не известно".coloredSpan("#f7f2f2", 0, 8)
                            }
                            percentChargingLabel.text = "Процент зарядки: ${percentCharging}%".coloredSpan("#f7f2f2", 0, 16)
                            timeRemainingBatteryLabel.text = when {
                                remainingTime != null -> "Оставшееся время работы: ${"%.1f".format(remainingTime / 60)} мин".coloredSpan("#f7f2f2", 0, 26)
                                remainingTime != null && percentCharging != null && percentCharging.toInt() == 100 -> "Оставшееся время работы: ∞ мин".coloredSpan("#f7f2f2", 0, 24)
                                else -> "Оставшееся время работы: не определено".coloredSpan("#f7f2f2", 0, 24)
                            }

                            osUptimeHoursLabel.text = "Время работы: ${getHourString(osUptimeHours / 3600)}".coloredSpan("#f7f2f2", 0, 13)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    delay(2500)
                }
            }
        }

        startUpdating()
        applyIpButton.setOnClickListener {
            val newIp = ipInput.text.toString().trim()
            if (newIp.isNotEmpty()) {
                serverIp = newIp
                Toast.makeText(this, "IP сервера изменён на $serverIp", Toast.LENGTH_SHORT).show()
                startUpdating()
            } else {
                Toast.makeText(this, "Введите корректный IP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
    }
}