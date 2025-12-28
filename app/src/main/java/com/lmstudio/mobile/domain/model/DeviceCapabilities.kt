package com.lmstudio.mobile.domain.model

data class DeviceCapabilities(
    val totalRam: Long,
    val availableRam: Long,
    val totalStorage: Long,
    val availableStorage: Long,
    val cpuCores: Int,
    val recommendedThreads: Int,
    val hasVulkan: Boolean,
    val gpuName: String?,
    val gpuVendor: String?,
    val deviceModel: String,
    val socName: String
)

