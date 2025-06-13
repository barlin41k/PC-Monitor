package com.bxrlya.pcmonitor

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import android.widget.Toast
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.graphics.Color

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private var updateJob: Job? = null
    private val serverIp = "192.168.1.33"

    private lateinit var cpuLoadLabel: TextView
    private lateinit var totalMemLabel: TextView
    private lateinit var freeMemLabel: TextView
    private lateinit var isChargingLabel: TextView
    private lateinit var percentChargingLabel: TextView
    private lateinit var timeRemainingBatteryLabel: TextView
    private lateinit var totalDiskLabel: TextView
    private lateinit var freeDiskLabel: TextView
    private lateinit var osUptimeTimeLabel: TextView
    private lateinit var spinner: Spinner

    data class DiskInfo(
        val fs: String,
        val size: Double,
        val used: Double,
        val free: Double
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cpuLoadLabel = findViewById(R.id.cpu_load)

        totalMemLabel = findViewById(R.id.total_mem)
        freeMemLabel = findViewById(R.id.free_mem)

        isChargingLabel = findViewById(R.id.is_charging)
        percentChargingLabel = findViewById(R.id.percent_charging)
        timeRemainingBatteryLabel = findViewById(R.id.time_remaining_battery)

        totalDiskLabel = findViewById(R.id.total_disk)
        freeDiskLabel = findViewById(R.id.free_disk)
        spinner = findViewById(R.id.disk_spinner)

        osUptimeTimeLabel = findViewById(R.id.os_uptime_hours)

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
                    val batteryTopic = json["battery"]?.jsonObject
                    val osTopic = json["os"]?.jsonObject
                    val diskArray = json["disk"]?.jsonArray ?: continue

                    val diskList = diskArray.map { el ->
                        val obj = el.jsonObject
                        DiskInfo(
                            fs = obj["fs"]?.jsonPrimitive?.content ?: "unknown",
                            size = obj["size"]?.jsonPrimitive?.double ?: 0.0,
                            used = obj["used"]?.jsonPrimitive?.double ?: 0.0,
                            free = obj["free"]?.jsonPrimitive?.double ?: 0.0
                        )
                    }

                    val cpuLoad = cpuTopic?.get("load")?.jsonPrimitive?.double ?: 0.0

                    val totalMemory = memTopic?.get("total")?.jsonPrimitive?.double ?: 0.0
                    val usedMemory = memTopic?.get("used")?.jsonPrimitive?.double ?: 0.0
                    val freeMemory = memTopic?.get("free")?.jsonPrimitive?.double ?: 0.0

                    val isCharging = batteryTopic?.get("is")?.jsonPrimitive?.boolean ?: false
                    val percentCharging = batteryTopic?.get("percent")?.jsonPrimitive?.double ?: 0.0
                    val remainingTime = batteryTopic?.get("remaining")?.jsonPrimitive?.doubleOrNull

                    val osUptimeHours = osTopic?.get("up")?.jsonPrimitive?.int ?: 0

                    withContext(Dispatchers.Main) {
                        val memPercent1 = if (totalMemory != 0.0) usedMemory / totalMemory * 100 else 0.0
                        val memPercent2 = if (totalMemory != 0.0) freeMemory / totalMemory * 100 else 0.0

                        cpuLoadLabel.text = "Загрузка процессора: ${"%.2f".format(cpuLoad)}%".coloredSpan("#f7f2f2", 0, 20)

                        totalMemLabel.text = "Загрузка ОЗУ: ${"%.2f".format(usedMemory)}/${"%.2f".format(totalMemory)} ГБ (${ "%.2f".format(memPercent1)}%)".coloredSpan("#f7f2f2", 0, 13)
                        freeMemLabel.text = "Свободно ОЗУ: ${"%.2f".format(freeMemory)} ГБ (${ "%.2f".format(memPercent2)}%)".coloredSpan("#f7f2f2", 0, 13)

                        isChargingLabel.text = when {
                            isCharging -> "Батарея: заряжается".coloredSpan("#f7f2f2", 0, 8)
                            !isCharging && percentCharging.toInt() != 100 -> "Батарея: не заряжается".coloredSpan("#f7f2f2", 0, 8)
                            percentCharging.toInt() == 100 -> "Батарея: заряжена полностью".coloredSpan("#f7f2f2", 0, 8)
                            else -> "Батарея: не известно".coloredSpan("#f7f2f2", 0, 8)
                        }
                        percentChargingLabel.text = "Процент зарядки: ${percentCharging}%".coloredSpan("#f7f2f2", 0, 16)
                        timeRemainingBatteryLabel.text = when {
                            remainingTime != null -> "Оставшееся время работы: ${"%.1f".format(remainingTime / 60)} мин".coloredSpan("#f7f2f2", 0, 26)
                            remainingTime != null && percentCharging != null && percentCharging.toInt() == 100 -> "Оставшееся время работы: ∞ мин".coloredSpan("#f7f2f2", 0, 24)
                            else -> "Оставшееся время работы: не определено".coloredSpan("#f7f2f2", 0, 24)
                        }

                        val adapter = ArrayAdapter(
                            this@MainActivity,
                            R.layout.spinner_item,
                            diskList.map { it.fs }
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinner.adapter = adapter

                        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                                val disk = diskList[position]
                                val percentUsed = if (disk.size != 0.0) disk.used / disk.size * 100 else 0.0
                                val percentFree = if (disk.size != 0.0) disk.free / disk.size * 100 else 0.0

                                totalDiskLabel.text = "Занято: ${"%.2f".format(disk.used)}/${"%.2f".format(disk.size)} ГБ (${ "%.2f".format(percentUsed)}%)".coloredSpan("#f7f2f2", 0, 7)
                                freeDiskLabel.text = "Свободно: ${"%.2f".format(disk.free)} ГБ (${ "%.2f".format(percentFree)}%)".coloredSpan("#f7f2f2", 0, 9)
                            }

                            override fun onNothingSelected(parent: AdapterView<*>) {}
                        }

                        spinner.setSelection(0)

                        osUptimeTimeLabel.text = "Время работы: ${getHourString(osUptimeHours/3600)}".coloredSpan("#f7f2f2", 0, 12)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                delay(5000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
    }

    private fun String.coloredSpan(color: String, start: Int, end: Int): Spannable {
        val spannable = SpannableString(this)
        spannable.setSpan(ForegroundColorSpan(Color.parseColor(color)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }
    private fun getHourString(number: Int): String {
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
}
