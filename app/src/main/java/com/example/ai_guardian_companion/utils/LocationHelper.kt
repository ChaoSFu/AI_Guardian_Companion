package com.example.ai_guardian_companion.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 位置服务工具类
 * 用于获取和监听用户位置
 */
class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        10000 // 10秒更新一次
    ).apply {
        setMinUpdateIntervalMillis(5000) // 最快5秒
        setWaitForAccurateLocation(false)
    }.build()

    /**
     * 获取当前位置（一次性）
     */
    suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            fusedLocationClient.lastLocation.result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 持续监听位置变化
     */
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            close()
            return@callbackFlow
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    /**
     * 计算两个位置之间的距离（米）
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * 判断是否在指定区域内
     */
    fun isInArea(
        currentLat: Double, currentLon: Double,
        centerLat: Double, centerLon: Double,
        radiusMeters: Float
    ): Boolean {
        val distance = calculateDistance(currentLat, currentLon, centerLat, centerLon)
        return distance <= radiusMeters
    }
}
