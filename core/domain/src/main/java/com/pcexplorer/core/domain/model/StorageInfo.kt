package com.pcexplorer.core.domain.model

/**
 * Information about storage on the connected PC.
 */
data class StorageInfo(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long = totalSpace - freeSpace,
    val driveLetter: String = "",
    val volumeName: String = ""
) {
    val usagePercent: Float
        get() = if (totalSpace > 0) usedSpace.toFloat() / totalSpace else 0f

    val formattedTotal: String get() = formatBytes(totalSpace)
    val formattedFree: String get() = formatBytes(freeSpace)
    val formattedUsed: String get() = formatBytes(usedSpace)

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
