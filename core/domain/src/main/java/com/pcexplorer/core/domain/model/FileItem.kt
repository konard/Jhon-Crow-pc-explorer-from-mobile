package com.pcexplorer.core.domain.model

/**
 * Represents a file or directory on the connected PC.
 */
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String = name.substringAfterLast('.', ""),
    val mimeType: String = ""
) {
    val isFile: Boolean get() = !isDirectory

    val formattedSize: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }

    companion object {
        fun directory(name: String, path: String, lastModified: Long = 0L): FileItem =
            FileItem(
                name = name,
                path = path,
                isDirectory = true,
                size = 0,
                lastModified = lastModified
            )

        fun file(
            name: String,
            path: String,
            size: Long,
            lastModified: Long = 0L
        ): FileItem = FileItem(
            name = name,
            path = path,
            isDirectory = false,
            size = size,
            lastModified = lastModified
        )
    }
}
