package com.pcexplorer.core.domain.repository

import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.SortOrder
import com.pcexplorer.core.domain.model.StorageInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository for file operations on the connected PC.
 */
interface FileRepository {
    /**
     * List files in a directory on the PC.
     */
    suspend fun listFiles(path: String, sortOrder: SortOrder = SortOrder()): Result<List<FileItem>>

    /**
     * Search for files matching a query.
     */
    suspend fun searchFiles(query: String, path: String = ""): Result<List<FileItem>>

    /**
     * Get file information.
     */
    suspend fun getFileInfo(path: String): Result<FileItem>

    /**
     * Create a new folder.
     */
    suspend fun createFolder(path: String, name: String): Result<FileItem>

    /**
     * Rename a file or folder.
     */
    suspend fun rename(path: String, newName: String): Result<FileItem>

    /**
     * Delete files or folders.
     */
    suspend fun delete(paths: List<String>): Result<Unit>

    /**
     * Get storage information.
     */
    suspend fun getStorageInfo(drivePath: String = ""): Result<StorageInfo>

    /**
     * Get available drives on PC.
     */
    suspend fun getDrives(): Result<List<String>>
}
