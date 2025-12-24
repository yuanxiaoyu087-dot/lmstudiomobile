package com.lmstudio.mobile.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.lmstudio.mobile.domain.model.DeviceCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getDeviceCapabilities(): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val availableRam = memInfo.availMem

        val storageStats = getStorageStats()
        val hasVulkan = checkVulkanSupport()
        val cpuCores = Runtime.getRuntime().availableProcessors()

        return DeviceCapabilities(
            totalRam = totalRam,
            availableRam = availableRam,
            totalStorage = storageStats.total,
            availableStorage = storageStats.available,
            cpuCores = cpuCores,
            hasVulkan = hasVulkan,
            gpuName = getGpuName(),
            gpuVendor = getGpuVendor()
        )
    }

    private fun getStorageStats(): StorageStats {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val available = stat.availableBlocksLong * stat.blockSizeLong
            StorageStats(total, available)
        } catch (e: Exception) {
            StorageStats(0L, 0L)
        }
    }

    private fun checkVulkanSupport(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.packageManager.hasSystemFeature("android.hardware.vulkan.version")
        } else {
            false
        }
    }

    private fun getGpuName(): String? {
        return Build.HARDWARE
    }

    private fun getGpuVendor(): String? {
        return Build.MANUFACTURER
    }

    private data class StorageStats(val total: Long, val available: Long)
}
