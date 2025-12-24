package com.lmstudio.mobile.domain.model

data class SystemMetrics(
    val cpuUsagePercent: Float,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val gpuUsagePercent: Float,
    val timestamp: Long
)

data class ResourceMetrics(
    val cpuUsage: Float,
    val ramUsage: Float,
    val vramUsage: Float,
    val gpuUsage: Float
)

