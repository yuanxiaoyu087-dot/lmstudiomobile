package com.lmstudio.mobile.util

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.lmstudio.mobile.domain.model.SystemMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.RandomAccessFile
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

    private var lastCpuTotal: Long = 0
    private var lastCpuIdle: Long = 0

    private fun getCpuUsage(): Float {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()
            
            if (line == null) return 0f
            
            val parts = line.split("\\s+".toRegex())
            if (parts.size < 8) return 0f
            
            val user = parts[1].toLong()
            val nice = parts[2].toLong()
            val system = parts[3].toLong()
            val idle = parts[4].toLong()
            val iowait = parts[5].toLong()
            val irq = parts[6].toLong()
            val softirq = parts[7].toLong()
            
            val total = user + nice + system + idle + iowait + irq + softirq
            val diffTotal = total - lastCpuTotal
            val diffIdle = idle - lastCpuIdle
            
            lastCpuTotal = total
            lastCpuIdle = idle
            
            if (diffTotal == 0L) return 0f
            
            val usage = (diffTotal - diffIdle).toFloat() / diffTotal
            usage.coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.e("ResourceMonitor", "Error reading CPU usage", e)
            0f
        }
    }
}
