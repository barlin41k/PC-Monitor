package com.bxrlya.pcmonitor

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit

class SettingsActivity : AppCompatActivity() {
    // --- ПЕРЕМЕННЫЕ
    private lateinit var applyDelayButton: Button
    private lateinit var sharedPrefsButton: Button
    private lateinit var editTextDelay: EditText
    private lateinit var cpuNotifications: ToggleButton
    private lateinit var memNotifications: ToggleButton
    private lateinit var diskNotifications: ToggleButton
    private val defaultServerIp = "192.168.1.33"
    private val defaultDelayGetReq = 5L
    // --- КОНЕЦ

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<Toolbar>(R.id.second_toolbar)
        setSupportActionBar(toolbar)

        val sharedPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        var serverIp: String?
        var delay: Long

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        sharedPrefsButton = findViewById(R.id.sharedprefs_button)
        sharedPrefsButton.setOnClickListener {
            delay = sharedPrefs.getLong("delay", defaultDelayGetReq)
            serverIp = sharedPrefs.getString("server_ip", defaultServerIp)
            nonSuspendShowErrorDialog(
                this,
                getString(R.string.prefs_data),
                getString(R.string.prefs_data_text, delay, serverIp)
            )
        }

        // --- ЗАДЕРЖКА
        applyDelayButton = findViewById(R.id.apply_delay)
        editTextDelay = findViewById(R.id.delay_input)

        applyDelayButton.setOnClickListener {
            val newDelay = editTextDelay.text.toString().trim()
            val tryLong: Long? = newDelay.toLongOrNull()
            if (newDelay.isNotEmpty() && tryLong != null && tryLong >= 1) {
                sharedPrefs.edit { putLong("delay", tryLong) }
                Toast.makeText(this, getString(R.string.delay_updated_successful, getTimeString(tryLong, 2)), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.delay_not_updated), Toast.LENGTH_SHORT).show()
            }
        }
        // --- КОНЕЦ

        // --- УВЕДОМЛЕНИЯ
        cpuNotifications = findViewById(R.id.cpu_notifications)
        memNotifications = findViewById(R.id.mem_notifications)
        diskNotifications = findViewById(R.id.disk_notifications)

        val cpuNotificationsEnabled = sharedPrefs.getBoolean("cpu_notify", true)
        val memNotificationsEnabled = sharedPrefs.getBoolean("mem_notify", true)
        val diskNotificationsEnabled = sharedPrefs.getBoolean("disk_notify", true)
        cpuNotifications.isChecked = cpuNotificationsEnabled
        memNotifications.isChecked = memNotificationsEnabled
        diskNotifications.isChecked = diskNotificationsEnabled

        cpuNotifications.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit {
                putBoolean("cpu_notify", isChecked)
            }
            val message = if (isChecked) getString(R.string.notify_cpu_on_main) else getString(R.string.notify_cpu_off_main)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        memNotifications.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit {
                putBoolean("mem_notify", isChecked)
            }
            val message = if (isChecked) getString(R.string.notify_mem_on_main) else getString(R.string.notify_mem_off_main)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        diskNotifications.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit {
                putBoolean("disk_notify", isChecked)
            }
            val message = if (isChecked) getString(R.string.notify_disk_on_main) else getString(R.string.notify_disk_off_main)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}