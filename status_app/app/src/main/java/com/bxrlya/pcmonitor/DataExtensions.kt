package com.bxrlya.pcmonitor

import com.bxrlya.pcmonitor.MainActivity.DiskInfo
import com.bxrlya.pcmonitor.MainActivity.StatusData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun parseStatusJson(json: JsonObject): StatusData {
    val cpuTopic = json["cpu"]?.jsonObject
    val memTopic = json["mem"]?.jsonObject
    val batteryTopic = json["battery"]?.jsonObject
    val osTopic = json["os"]?.jsonObject
    val diskArray = json["disk"]?.jsonArray ?: emptyList()

    val diskList = diskArray.map { el ->
        val obj = el.jsonObject
        DiskInfo(
            fs = obj["fs"]?.jsonPrimitive?.content ?: "unknown",
            size = obj["size"]?.jsonPrimitive?.double ?: 0.0,
            used = obj["used"]?.jsonPrimitive?.double ?: 0.0,
            free = obj["free"]?.jsonPrimitive?.double ?: 0.0
        )
    }

    return StatusData(
        cpuLoad = cpuTopic?.get("load")?.jsonPrimitive?.double ?: 0.0,
        totalMemory = memTopic?.get("total")?.jsonPrimitive?.double ?: 0.0,
        usedMemory = memTopic?.get("used")?.jsonPrimitive?.double ?: 0.0,
        freeMemory = memTopic?.get("free")?.jsonPrimitive?.double ?: 0.0,
        totalSwap = memTopic?.get("swap_total")?.jsonPrimitive?.double ?: 0.0,
        usedSwap = memTopic?.get("swap_used")?.jsonPrimitive?.double ?: 0.0,
        freeSwap = memTopic?.get("swap_free")?.jsonPrimitive?.double ?: 0.0,
        hasBattery = batteryTopic?.get("has")?.jsonPrimitive?.boolean == true,
        isCharging = batteryTopic?.get("is")?.jsonPrimitive?.boolean == true,
        percentCharging = batteryTopic?.get("percent")?.jsonPrimitive?.double ?: 0.0,
        remainingTime = batteryTopic?.get("remaining")?.jsonPrimitive?.doubleOrNull,
        osUptimeSeconds = osTopic?.get("up")?.jsonPrimitive?.int ?: 0,
        diskList = diskList
    )
}
