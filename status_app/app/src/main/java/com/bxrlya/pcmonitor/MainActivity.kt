package com.bxrlya.pcmonitor

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
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
import okio.IOException

class MainActivity : AppCompatActivity() {

    // --- ПЕРЕМЕННЫЕ
    private lateinit var sharedPrefs: SharedPreferences
    private val client = OkHttpClient()
    private var updateJob: Job? = null
    private var isAppVisible = true

    private val defaultServerIp = "192.168.1.33"
    private val defaultDelayGetReq = 5L

    private lateinit var cpuLoadLabel: TextView

    private lateinit var totalMemLabel: TextView
    private lateinit var freeMemLabel: TextView
    private lateinit var totalSwapLabel: TextView
    private lateinit var freeSwapLabel: TextView

    private lateinit var mainBatteryLabel: TextView
    private lateinit var isChargingLabel: TextView
    private lateinit var percentChargingLabel: TextView
    private lateinit var timeRemainingBatteryLabel: TextView

    private lateinit var totalDiskLabel: TextView
    private lateinit var freeDiskLabel: TextView

    private lateinit var osUptimeTimeLabel: TextView

    private lateinit var spinner: Spinner
    private lateinit var ipInputEditText: EditText
    private lateinit var applyIpButton: Button

    private var diskList: List<DiskInfo> = emptyList()
    private lateinit var diskAdapter: ArrayAdapter<String>

    data class DiskInfo(
        val fs: String,
        val size: Double,
        val used: Double,
        val free: Double
    )
    // --- КОНЕЦ

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences("settings", MODE_PRIVATE)

        var serverIp = sharedPrefs.getString("server_ip", defaultServerIp)
        var getDelay = sharedPrefs.getLong("delay", defaultDelayGetReq)

        // --- ЧЕК ОБНОВЛЕНИЯ
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (updateAvailable, newVersion) = checkForUpdate(this@MainActivity)

                if (updateAvailable) {
                    withContext(Dispatchers.Main) {
                        nonSuspendShowErrorDialog(
                            this@MainActivity,
                            "Новое обновление $newVersion",
                            "Доступна новая версия. Пожалуйста, обновите приложение.",
                            true
                        )
                    }
                }
            } catch (e: IOException) {
                if (isAppVisible) {
                    showErrorDialog(
                        this@MainActivity,
                        "Ошибка подключения",
                        "Ошибка подключения к серверу $serverIp порт 8080. Проверьте подключение к интернету и состояние сервера."
                    )
                }
            } catch (e: Exception) {
                if (isAppVisible) {
                    showErrorDialog(
                        this@MainActivity,
                        "Неизвестная ошибка",
                        e.message ?: "Ошибка без сообщения"
                    )
                }
            }
        }
        // --- КОНЕЦ

        var notificationCpu: Boolean
        var notificationMem: Boolean
        var notificationDisk: Boolean

        val toolbar = findViewById<Toolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        // --- ПЕРЕМЕННЫЕ ЭЛЕМЕНТОВ
        cpuLoadLabel = findViewById(R.id.cpu_load)

        totalMemLabel = findViewById(R.id.total_mem)
        freeMemLabel = findViewById(R.id.free_mem)
        totalSwapLabel = findViewById(R.id.total_swap)
        freeSwapLabel = findViewById(R.id.free_swap)

        mainBatteryLabel = findViewById(R.id.battery_mainlabel)
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
                sharedPrefs.edit { putString("server_ip", serverIp) }
                Toast.makeText(this, "IP-адрес обновлён: $serverIp", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Введите корректный IP", Toast.LENGTH_SHORT).show()
            }
        }
        // --- КОНЕЦ

        var notifiedHighCpu = false
        var notifiedHighMem = false
        var notifiedHighDisk = false


        // --- SPINNER ДИСКОВ
        diskAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            mutableListOf<String>()
        )
        diskAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = diskAdapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val disk = diskList.getOrNull(position) ?: return
                val percentUsed = if (disk.size != 0.0) disk.used / disk.size * 100 else 0.0
                val percentFree = if (disk.size != 0.0) disk.free / disk.size * 100 else 0.0

                totalDiskLabel.text = getString(R.string.disk_used, disk.used, disk.size, percentUsed).coloredSpan(0, 7)
                freeDiskLabel.text = getString(R.string.disk_free, disk.free, percentFree).coloredSpan(0, 9)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        // --- КОНЕЦ

        updateJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    // --- ПОЛУЧЕНИЕ ДАННЫХ
                    val url = "http://$serverIp:8080/status"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""

                    getDelay = sharedPrefs.getLong("delay", defaultDelayGetReq)
                    notificationCpu = sharedPrefs.getBoolean("cpu_notify", true)
                    notificationMem = sharedPrefs.getBoolean("mem_notify", true)
                    notificationDisk = sharedPrefs.getBoolean("disk_notify", true)

                    val json = Json.parseToJsonElement(body).jsonObject

                    val cpuTopic = json["cpu"]?.jsonObject
                    val memTopic = json["mem"]?.jsonObject
                    val batteryTopic = json["battery"]?.jsonObject
                    val osTopic = json["os"]?.jsonObject
                    val diskArray = json["disk"]?.jsonArray ?: continue

                    diskList = diskArray.map { el ->
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
                    // --- КОНЕЦ

                    withContext(Dispatchers.Main) {
                        // --- ПРОВЕРКА БАТАРЕИ
                        if (!hasBattery && isChargingLabel.isVisible) {
                            batteryElements.forEach { el ->
                                el.visibility = View.GONE
                            }
                            if (mainBatteryLabel.text != getString(R.string.battery_is_not))
                                mainBatteryLabel.text = getString(R.string.battery_is_not)
                        } else {
                            batteryElements.forEach { el ->
                                el.visibility = View.VISIBLE
                            }
                        }
                        // --- КОНЕЦ

                        // --- SPINNER СОХРАНЕНИЕ ВЫБОРА ДИСКА
                        diskAdapter.clear()
                        diskAdapter.addAll(diskList.map { "Диск ${it.fs}" })
                        diskAdapter.notifyDataSetChanged()

                        val selectedFs = spinner.selectedItem?.toString()
                        val indexToSelect = diskList.indexOfFirst { "Диск ${it.fs}" == selectedFs }.takeIf { it >= 0 } ?: 0
                        spinner.setSelection(indexToSelect)

                        val selectedDisk = diskList.getOrNull(indexToSelect)
                        if (selectedDisk != null) {
                            val percentUsed = if (selectedDisk.size != 0.0) selectedDisk.used / selectedDisk.size * 100 else 0.0
                            val percentFree = if (selectedDisk.size != 0.0) selectedDisk.free / selectedDisk.size * 100 else 0.0

                            totalDiskLabel.text = getString(R.string.disk_used, selectedDisk.used, selectedDisk.size, percentUsed).coloredSpan(0, 7)
                            freeDiskLabel.text = getString(R.string.disk_free, selectedDisk.free, percentFree).coloredSpan(0, 9)
                        }
                        // --- КОНЕЦ

                        // --- РАСЧЕТ ПРОЦЕНТОВ
                        val memPercent1 = if (totalMemory != 0.0) usedMemory / totalMemory * 100 else 0.0
                        val memPercent2 = if (totalMemory != 0.0) freeMemory / totalMemory * 100 else 0.0
                        val swapPercent1 = if (totalSwap != 0.0) usedSwap / totalSwap * 100 else 0.0
                        val swapPercent2 = if (totalSwap != 0.0) freeSwap / totalSwap * 100 else 0.0
                        // --- КОНЕЦ

                        // --- ПРОВЕРКА ЗАГРУЖЕННОСТИ
                        if (notificationCpu) {
                            notifiedHighCpu = checkThreshold(cpuLoad, 90.0, notifiedHighCpu) {
                                sendNotification("Высокая загрузка CPU", "Загрузка CPU достигла ${cpuLoad.toInt()}%!")
                            }
                        }
                        if (notificationMem) {
                            notifiedHighMem = checkThreshold(memPercent1, 90.0, notifiedHighMem) {
                                sendNotification("Высокое использование ОЗУ", "Используется ${memPercent1.toInt()}% ОЗУ!")
                            }
                        }

                        val diskPercentUsed = selectedDisk?.let { disk ->
                            if (disk.size != 0.0) disk.used / disk.size * 100 else 0.0
                        } ?: 0.0
                        if (notificationDisk) {
                            notifiedHighDisk = checkThreshold(diskPercentUsed, 90.0, notifiedHighDisk) {
                                sendNotification("Мало места на диске", "Диск ${selectedDisk?.fs ?: "неизвестно"} заполнен на ${diskPercentUsed.toInt()}%")
                            }
                        }
                        // --- КОНЕЦ

                        // --- ОБНОВЛЕНИЕ ДАННЫХ
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
                                getString(R.string.time_remaining_battery, remainingTime/60).coloredSpan(0, 26)
                            remainingTime != null && percentCharging.toInt() == 100 ->
                                getString(R.string.time_remaining_battery_infinite).coloredSpan(0, 24)
                            else -> getString(R.string.time_remaining_battery_unknown).coloredSpan(0, 24)
                        }

                        val uptimeHours = osUptimeHours / 3600
                        osUptimeTimeLabel.text = getString(R.string.uptime, getTimeString(uptimeHours.toLong(), 1)).coloredSpan(0, 12)
                        // --- КОНЕЦ
                    }
                } catch (e: IOException) { // Нет подключения к серверу / интернету
                    if (isAppVisible) {
                        showErrorDialog(
                            this@MainActivity,
                            "Ошибка подключения",
                            "Ошибка подключения к серверу $serverIp порт 8080. Проверьте подключение к интернету и состояние сервера."
                        )
                    }
                } catch (e: Exception) {
                    if (isAppVisible) {
                        showErrorDialog(
                            this@MainActivity,
                            "Неизвестная ошибка",
                            e.message ?: "Ошибка без сообщения"
                        )
                    }
                }

                delay(getDelay*1000L)
            }
        }
    }

    // --- МЕНЮ
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.settings_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.help -> {
                val issuesURL = "https://github.com/barlin41k/PC-Monitor/issues"
                val intent = Intent(Intent.ACTION_VIEW, issuesURL.toUri())
                Toast.makeText(this, "Открыта ссылка", Toast.LENGTH_SHORT).show()
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    // --- КОНЕЦ

    override fun onResume() {
        super.onResume()
        isAppVisible = true
    }
    override fun onPause() {
        super.onPause()
        isAppVisible = false
    }
    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
    }
}
