package com.pcexplorer.shared.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Displays an appropriate icon for a file based on its extension.
 */
@Composable
fun FileIcon(
    extension: String,
    isDirectory: Boolean,
    isOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = getFileIconAndTint(extension, isDirectory, isOpen)

    Icon(
        imageVector = icon,
        contentDescription = if (isDirectory) "Folder" else "File",
        modifier = modifier,
        tint = tint
    )
}

@Composable
private fun getFileIconAndTint(
    extension: String,
    isDirectory: Boolean,
    isOpen: Boolean
): Pair<ImageVector, Color> {
    if (isDirectory) {
        return if (isOpen) {
            Icons.Filled.FolderOpen to Color(0xFFFFA000)
        } else {
            Icons.Filled.Folder to Color(0xFFFFA000)
        }
    }

    return when (extension.lowercase()) {
        // Images
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico" ->
            Icons.Filled.Image to Color(0xFF43A047)

        // Videos
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v" ->
            Icons.Filled.VideoFile to Color(0xFFE53935)

        // Audio
        "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a" ->
            Icons.Filled.AudioFile to Color(0xFF8E24AA)

        // Documents
        "pdf" ->
            Icons.Filled.PictureAsPdf to Color(0xFFD32F2F)
        "doc", "docx", "odt", "rtf" ->
            Icons.Filled.Article to Color(0xFF1565C0)
        "xls", "xlsx", "ods", "csv" ->
            Icons.Filled.Description to Color(0xFF2E7D32)
        "ppt", "pptx", "odp" ->
            Icons.Filled.Description to Color(0xFFD84315)
        "txt", "md", "log" ->
            Icons.Filled.Description to MaterialTheme.colorScheme.onSurface

        // Code
        "kt", "java", "py", "js", "ts", "html", "css", "xml", "json",
        "c", "cpp", "h", "hpp", "cs", "go", "rs", "rb", "php", "swift" ->
            Icons.Filled.Code to Color(0xFF00897B)

        // Archives
        "zip", "rar", "7z", "tar", "gz", "bz2" ->
            Icons.Filled.InsertDriveFile to Color(0xFF795548)

        // Android
        "apk", "aab" ->
            Icons.Filled.Android to Color(0xFF689F38)

        // Executables
        "exe", "msi", "bat", "sh", "cmd" ->
            Icons.Filled.InsertDriveFile to Color(0xFF546E7A)

        // Default
        else ->
            Icons.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }
}
