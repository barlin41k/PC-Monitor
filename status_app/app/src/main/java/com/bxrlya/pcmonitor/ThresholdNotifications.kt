package com.bxrlya.pcmonitor

import android.content.Context
import android.content.SharedPreferences
import com.bxrlya.pcmonitor.MainActivity.DiskInfo
import com.bxrlya.pcmonitor.MainActivity.Notifications

private var notificationCpu: Boolean = true
private var notificationMem: Boolean = true
private var notificationDisk: Boolean = true

fun thresholdNotifications(
    context: Context,
    jsonData: MainActivity.StatusData,
    isNotifications: List<Boolean>,
    selectedDisk: DiskInfo?,
    notifications: Notifications
) {


    if (isNotifications[0]) {
        notifications.notifiedHighCpu = checkThreshold(jsonData.cpuLoad, 90.0, notifications.notifiedHighCpu) {
            context.sendNotification(
                context.getString(R.string.cpu_overload),
                context.getString(R.string.cpu_overload_text, jsonData.cpuLoad)
            )
        }
    }

    if (isNotifications[1]) {
        val usedMemPercent = jsonData.usedMemory.percentOf(jsonData.totalMemory)
        notifications.notifiedHighMem = checkThreshold(usedMemPercent, 90.0, notifications.notifiedHighMem) {
            context.sendNotification(
                context.getString(R.string.mem_overload),
                context.getString(R.string.mem_overload_text, usedMemPercent)
            )
        }
    }

    if (isNotifications[2]) {
        val diskPercentUsed = selectedDisk?.let { disk ->
            if (disk.size != 0.0) disk.used / disk.size * 100 else 0.0
        } ?: 0.0

        notifications.notifiedHighDisk = checkThreshold(diskPercentUsed, 90.0, notifications.notifiedHighDisk) {
            notifications.notifyString = if (!selectedDisk?.fs.isNullOrEmpty()) {
                context.getString(R.string.disk_overload_text, selectedDisk.fs, diskPercentUsed.toInt())
            } else {
                context.getString(R.string.disk_unknown_overload_text, diskPercentUsed.toInt())
            }
            context.sendNotification(context.getString(R.string.disk_overload), notifications.notifyString)
        }
    }
}

fun sharedPrefsThresholdNotifications(sharedPrefs: SharedPreferences): List<Boolean> {
    notificationCpu = sharedPrefs.getBoolean("cpu_notify", true)
    notificationMem = sharedPrefs.getBoolean("mem_notify", true)
    notificationDisk = sharedPrefs.getBoolean("disk_notify", true)
    return listOf(notificationCpu, notificationMem, notificationDisk)
}
