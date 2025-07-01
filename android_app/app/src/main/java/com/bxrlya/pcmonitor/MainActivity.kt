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
import okhttp3.OkHttpClient
import okio.IOException

class MainActivity : AppCompatActivity() {

    // --- ПЕРЕМЕННЫЕ
    private lateinit var sharedPrefs: SharedPreferences
    private val client = OkHttpClient()
    private var updateJob: Job? = null
    private var isAppVisible = true
    companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_DELAY = "delay"
        const val DEFAULT_SERVER_IP = "192.168.1.33"
        const val DEFAULT_DELAY_GET_REQ = 5L
    }

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
    private lateinit var diskAdapter: ArrayAdapter<DiskItem>

    private var notificationsPrefs: List<Boolean> = emptyList()

    data class DiskInfo(
        val fs: String,
        val size: Double,
        val used: Double,
        val free: Double
    )
    data class DiskItem(val fs: String, val info: DiskInfo) {
        override fun toString(): String = "Диск $fs"
    }
    data class StatusData(
        val cpuLoad: Double,
        val totalMemory: Double,
        val usedMemory: Double,
        val freeMemory: Double,
        val totalSwap: Double,
        val usedSwap: Double,
        val freeSwap: Double,
        val hasBattery: Boolean,
        val isCharging: Boolean,
        val percentCharging: Double,
        val remainingTime: Double?,
        val osUptimeSeconds: Int,
        val diskList: List<DiskInfo>
    )
    data class Notifications(
        var notifiedHighCpu: Boolean = false,
        var notifiedHighMem: Boolean = false,
        var notifiedHighDisk: Boolean = false,
        var notifyString: String = ""
    )
    val notifications = Notifications()
    // --- КОНЕЦ

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences(KEY_SETTINGS, MODE_PRIVATE)

        var serverIp = sharedPrefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP)

        if (serverIp != null) {
            checkUpdate(this, serverIp, isAppVisible)
        } else {
            nonSuspendShowErrorDialog(
                this,
                getString(R.string.ip_error),
                getString(R.string.ip_error_text)
            )
            serverIp = DEFAULT_SERVER_IP
        }

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
            if (newIp.isNotEmpty() && isValidIPv4(newIp)) {
                serverIp = newIp
                sharedPrefs.edit { putString(KEY_SERVER_IP, serverIp) }
                Toast.makeText(this, getString(R.string.ip_updated_successful, serverIp), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.ip_not_updated), Toast.LENGTH_SHORT).show()
            }
        }
        // --- КОНЕЦ

        // --- SPINNER ДИСКОВ
        diskAdapter = ArrayAdapter(
            this,
            R.layout.style_spinner_item,
            mutableListOf<DiskItem>()
        )
        diskAdapter.setDropDownViewResource(R.layout.style_spinner_dropdown_item)
        spinner.adapter = diskAdapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val disk = diskList.getOrNull(position) ?: return
                val percentUsed = disk.used.percentOf(disk.size)
                val percentFree = disk.free.percentOf(disk.size)

                totalDiskLabel.text = cs(R.string.disk_used, disk.used, disk.size, percentUsed)
                freeDiskLabel.text = cs(R.string.disk_free, disk.free, percentFree)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        // --- КОНЕЦ

        updateJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    // --- ПОЛУЧЕНИЕ ДАННЫХ
                    val json = fetchStatus(serverIp.toString(), client)!! // принудительно (!!)
                    notificationsPrefs = sharedPrefsThresholdNotifications(sharedPrefs)

                    val jsonData = parseStatusJson(json)
                    diskList = jsonData.diskList
                    // --- КОНЕЦ

                    withContext(Dispatchers.Main) {
                        // --- ПРОВЕРКА БАТАРЕИ
                        if (!jsonData.hasBattery && isChargingLabel.isVisible) {
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
                        val oldIndex = spinner.selectedItemPosition.coerceAtLeast(0)

                        diskAdapter.clear()
                        diskAdapter.addAll(diskList.map { DiskItem(it.fs, it) })
                        diskAdapter.notifyDataSetChanged()

                        val indexToSelect = oldIndex.coerceAtMost(diskAdapter.count - 1)
                        spinner.setSelection(indexToSelect)

                        val selectedDisk = diskList.getOrNull(indexToSelect)
                        if (selectedDisk != null) {
                            val percentUsed = selectedDisk.used.percentOf(selectedDisk.size)
                            val percentFree = selectedDisk.free.percentOf(selectedDisk.size)

                            totalDiskLabel.text = cs(R.string.disk_used, selectedDisk.used, selectedDisk.size, percentUsed)
                            freeDiskLabel.text = cs(R.string.disk_free, selectedDisk.free, percentFree)
                        }
                        // --- КОНЕЦ

                        // --- РАСЧЕТ ПРОЦЕНТОВ
                        val usedMemPercent = jsonData.usedMemory.percentOf(jsonData.totalMemory)
                        val freeMemPercent = jsonData.freeMemory.percentOf(jsonData.totalMemory)
                        val usedSwapPercent = jsonData.usedSwap.percentOf(jsonData.totalSwap)
                        val freeSwapPercent = jsonData.freeSwap.percentOf(jsonData.totalSwap)
                        // --- КОНЕЦ

                        // --- ПРОВЕРКА ЗАГРУЖЕННОСТИ
                        val isNotifications = listOf(notificationsPrefs[0], notificationsPrefs[1], notificationsPrefs[2])
                        thresholdNotifications(this@MainActivity, jsonData, isNotifications, selectedDisk, notifications)
                        // --- КОНЕЦ

                        // --- ОБНОВЛЕНИЕ ДАННЫХ
                        cpuLoadLabel.text = cs(R.string.cpu_load, jsonData.cpuLoad)
                        totalMemLabel.text = cs(R.string.memory_usage, jsonData.usedMemory, jsonData.totalMemory, usedMemPercent)
                        freeMemLabel.text = cs(R.string.free_memory, jsonData.freeMemory, freeMemPercent)
                        totalSwapLabel.text = cs(R.string.swap_usage, jsonData.usedSwap, jsonData.totalSwap, usedSwapPercent)
                        freeSwapLabel.text = cs(R.string.swap_free, jsonData.freeSwap, freeSwapPercent)

                        isChargingLabel.text = when {
                            jsonData.isCharging -> cs(R.string.battery_status_charging)
                            !jsonData.isCharging && jsonData.percentCharging.toInt() != 100 -> cs(R.string.battery_status_not_charging)
                            jsonData.percentCharging.toInt() == 100 -> cs(R.string.battery_status_full)
                            else -> cs(R.string.battery_status_unknown)
                        }
                        percentChargingLabel.text = cs(R.string.percent_charging, jsonData.percentCharging)
                        timeRemainingBatteryLabel.text = when {
                            jsonData.remainingTime != null && jsonData.percentCharging.toInt() != 100 ->
                                cs(R.string.time_remaining_battery, jsonData.remainingTime/60)
                            jsonData.remainingTime != null && jsonData.percentCharging.toInt() == 100 ->
                                cs(R.string.time_remaining_battery_infinite)
                            else -> cs(R.string.time_remaining_battery_unknown)
                        }

                        val uptimeHours = jsonData.osUptimeSeconds / 3600
                        osUptimeTimeLabel.text = cs(R.string.uptime, getTimeString(uptimeHours.toLong(), 1))
                        // --- КОНЕЦ
                    }
                } catch (_: IOException) { // Нет подключения к серверу / интернету
                    IOExceptionCatch(this@MainActivity, isAppVisible, serverIp.toString())
                } catch (e: Exception) {
                    val string = getString(R.string.unknown_error_text)
                    unknownExceptionCatch(this@MainActivity, isAppVisible, e.message ?: string)
                }

                delay(sharedPrefs.getLong(KEY_DELAY, DEFAULT_DELAY_GET_REQ).toMiliSec())
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
                val issuesURL = "https://github.com/barlin41k/PC-Monitor/issues".toUri()
                val intent = Intent(Intent.ACTION_VIEW, issuesURL)
                Toast.makeText(this, getString(R.string.url_opened), Toast.LENGTH_SHORT).show()
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
