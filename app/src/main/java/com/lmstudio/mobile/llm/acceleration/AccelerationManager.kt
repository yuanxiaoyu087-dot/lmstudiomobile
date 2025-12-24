package com.lmstudio.mobile.llm.acceleration

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccelerationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isVulkanAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
    }

    fun isOpenGLESAvailable(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configInfo = activityManager.deviceConfigurationInfo
        return configInfo.reqGlEsVersion >= 0x30000 // Check for OpenGL ES 3.0 or higher
    }
}
