package com.lmstudio.mobile.util

import android.app.ActivityManager
import android.content.Context
import com.lmstudio.mobile.domain.model.SystemMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class ResourceMonitor @Inject constructor(
    private val context: Context
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun startMonitoring(intervalMs: Long = 1000L): Flow<SystemMetrics> = flow {
        while (coroutineContext.isActive) {
            emit(getCurrentMetrics())
            kotlinx.coroutines.delay(intervalMs)
        }
    }

    fun getCurrentMetrics(): SystemMetrics {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val cpuUsage = getCpuUsage()
        val gpuUsage = 0f // GPU usage monitoring requires vendor-specific APIs

        return SystemMetrics(
            cpuUsagePercent = cpuUsage,
            ramUsedMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024),
            ramTotalMb = memInfo.totalMem / (1024 * 1024),
            gpuUsagePercent = gpuUsage,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun getCpuUsage(): Float {
        // Simplified CPU usage calculation
        // In production, read from /proc/stat
        return 0f
    }
}
