package com.bxrlya.pcmonitor

import android.content.res.Configuration
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private var updateJob: Job? = null
    private var serverIp = "192.168.1.33"

    private val spanDark = "#f7f2f2"
    private val spanLight = "#141414"

    private val isDarkTheme: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private lateinit var cpuLoadLabel: TextView

    private lateinit var totalMemLabel: TextView
    private lateinit var freeMemLabel: TextView
    private lateinit var totalSwapLabel: TextView
    private lateinit var freeSwapLabel: TextView

    private lateinit var mainlabelBattery: TextView
    private lateinit var isChargingLabel: TextView
    private lateinit var percentChargingLabel: TextView
    private lateinit var timeRemainingBatteryLabel: TextView

    private lateinit var totalDiskLabel: TextView
    private lateinit var freeDiskLabel: TextView

    private lateinit var osUptimeTimeLabel: TextView

    private lateinit var spinner: Spinner
    private lateinit var ipInputEditText: EditText
    private lateinit var applyIpButton: Button

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
        totalSwapLabel = findViewById(R.id.total_swap)
        freeSwapLabel = findViewById(R.id.free_swap)

        mainlabelBattery = findViewById(R.id.battery_mainlabel)
        isChargingLabel = findViewById(R.id.is_charging)
        percentChargingLabel = findViewById(R.id.percent_charging)
        timeRemainingBatteryLabel = findViewById(R.id.time_remaining_battery)

        totalDiskLabel = findViewById(R.id.total_disk)
        freeDiskLabel = findViewById(R.id.free_disk)

        osUptimeTimeLabel = findViewById(R.id.os_uptime_hours)

        spinner = findViewById(R.id.disk_spinner)
        ipInputEditText = findViewById(R.id.ip_input)
        applyIpButton = findViewById(R.id.apply_ip)

        val batteryElements = listOf(isChargingLabel, percentChargingLabel, timeRemainingBatteryLabel)
        batteryElements.forEach { el ->
            el.visibility = View.VISIBLE
        }
        applyIpButton.setOnClickListener {
            val newIp = ipInputEditText.text.toString().trim()
            if (newIp.isNotEmpty()) {
                serverIp = newIp
                Toast.makeText(this, "IP-адрес обновлён: $serverIp", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Введите корректный IP", Toast.LENGTH_SHORT).show()
            }
        }

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
                    val totalSwap = memTopic?.get("swap_total")?.jsonPrimitive?.double ?: 0.0
                    val usedSwap = memTopic?.get("swap_used")?.jsonPrimitive?.double ?: 0.0
                    val freeSwap = memTopic?.get("swap_free")?.jsonPrimitive?.double ?: 0.0

                    val hasBattery = batteryTopic?.get("has")?.jsonPrimitive?.boolean ?: false
                    val isCharging = batteryTopic?.get("is")?.jsonPrimitive?.boolean ?: false
                    val percentCharging = batteryTopic?.get("percent")?.jsonPrimitive?.double ?: 0.0
                    val remainingTime = batteryTopic?.get("remaining")?.jsonPrimitive?.doubleOrNull

                    val osUptimeHours = osTopic?.get("up")?.jsonPrimitive?.int ?: 0

                    withContext(Dispatchers.Main) {
                        if (!hasBattery && isChargingLabel.isVisible) {
                            batteryElements.forEach { el ->
                                el.visibility = View.GONE
                            }
                            if (mainlabelBattery.text != getString(R.string.battery_is_not))
                                mainlabelBattery.text = getString(R.string.battery_is_not)
                        } else {
                            batteryElements.forEach { el ->
                                el.visibility = View.VISIBLE
                            }
                        }

                        val memPercent1 = if (totalMemory != 0.0) usedMemory / totalMemory * 100 else 0.0
                        val memPercent2 = if (totalMemory != 0.0) freeMemory / totalMemory * 100 else 0.0
                        val swapPercent1 = if (totalSwap != 0.0) usedSwap / totalSwap * 100 else 0.0
                        val swapPercent2 = if (totalSwap != 0.0) freeSwap / totalSwap * 100 else 0.0

                        cpuLoadLabel.text = getString(R.string.cpu_load, cpuLoad).coloredSpan(0, 20)

                        totalMemLabel.text = getString(R.string.memory_usage, usedMemory, totalMemory, memPercent1).coloredSpan(0, 13)

                        freeMemLabel.text = getString(R.string.free_memory, freeMemory, memPercent2).coloredSpan(0, 13)

                        totalSwapLabel.text = getString(R.string.swap_usage, usedSwap, totalSwap, swapPercent1).coloredSpan(0, 24)

                        freeSwapLabel.text = getString(R.string.swap_free, freeSwap, swapPercent2).coloredSpan(0, 26)

                        isChargingLabel.text = when {
                            isCharging -> getString(R.string.battery_status_charging).coloredSpan(0, 8)
                            !isCharging && percentCharging.toInt() != 100 -> getString(R.string.battery_status_not_charging).coloredSpan(0, 8)
                            percentCharging.toInt() == 100 -> getString(R.string.battery_status_full).coloredSpan(0, 8)
                            else -> getString(R.string.battery_status_unknown).coloredSpan(0, 8)
                        }

                        percentChargingLabel.text = getString(R.string.percent_charging, percentCharging).coloredSpan(0, 16)

                        timeRemainingBatteryLabel.text = when {
                            remainingTime != null && percentCharging.toInt() != 100 ->
                                getString(R.string.time_remaining_battery, remainingTime / 60).coloredSpan(0, 26)
                            remainingTime != null && percentCharging.toInt() == 100 ->
                                getString(R.string.time_remaining_battery_infinite).coloredSpan(0, 24)
                            else -> getString(R.string.time_remaining_battery_unknown).coloredSpan(0, 24)
                        }

                        val selectedFs = spinner.selectedItem?.toString()

                        val adapter = ArrayAdapter(
                            this@MainActivity,
                            R.layout.spinner_item,
                            diskList.map { "Диск ${it.fs}" }
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinner.adapter = adapter

                        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                                val disk = diskList[position]
                                val percentUsed = if (disk.size != 0.0) disk.used / disk.size * 100 else 0.0
                                val percentFree = if (disk.size != 0.0) disk.free / disk.size * 100 else 0.0

                                totalDiskLabel.text = getString(R.string.disk_used, disk.used, disk.size, percentUsed).coloredSpan(0, 7)
                                freeDiskLabel.text = getString(R.string.disk_free, disk.free, percentFree).coloredSpan(0, 9)
                            }

                            override fun onNothingSelected(parent: AdapterView<*>) {}
                        }

                        val indexToSelect = diskList.indexOfFirst { it.fs == selectedFs }.takeIf { it >= 0 } ?: 0
                        spinner.setSelection(indexToSelect)

                        osUptimeTimeLabel.text = getString(R.string.uptime, getHourString(osUptimeHours / 3600)).coloredSpan(0, 12)
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

    private fun String.coloredSpan(start: Int, end: Int): Spannable {
        val spannable = SpannableString(this)
        if (isDarkTheme) {
            spannable.setSpan(ForegroundColorSpan(spanDark.toColorInt()), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            spannable.setSpan(ForegroundColorSpan(spanLight.toColorInt()), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
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
