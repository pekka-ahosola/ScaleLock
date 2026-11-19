package com.ahosola.scalelock

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object HealthConnectHelper {
    val WEIGHT_PERMISSION = HealthPermission.getReadPermission(WeightRecord::class)

    fun isAvailable(context: Context): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasWeightPermission(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            WEIGHT_PERMISSION in granted
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getTodayWeightRecord(context: Context): WeightRecord? {
        if (!isAvailable(context)) return null
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (WEIGHT_PERMISSION !in granted) return null

            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val todayFourAm = LocalDate.now().atTime(LocalTime.of(4, 0)).atZone(zone).toInstant()
            val start = if (now.isAfter(todayFourAm)) {
                todayFourAm
            } else {
                LocalDate.now().atStartOfDay(zone).toInstant()
            }

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, now)
                )
            )
            response.records.maxByOrNull { it.time }
        } catch (_: Exception) {
            null
        }
    }
}
