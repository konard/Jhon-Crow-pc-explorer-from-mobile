package com.pcexplorer.core.domain.usecase

import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.StorageInfo
import com.pcexplorer.core.domain.repository.FileRepository
import javax.inject.Inject

/**
 * Use case for file operations (create, rename, delete).
 */
class FileOperationsUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    /**
     * Create a new folder.
     */
    suspend fun createFolder(path: String, name: String): Result<FileItem> {
        return fileRepository.createFolder(path, name)
    }

    /**
     * Rename a file or folder.
     */
    suspend fun rename(path: String, newName: String): Result<FileItem> {
        return fileRepository.rename(path, newName)
    }

    /**
     * Delete files or folders.
     */
    suspend fun delete(paths: List<String>): Result<Unit> {
        return fileRepository.delete(paths)
    }

    /**
     * Get file information.
     */
    suspend fun getFileInfo(path: String): Result<FileItem> {
        return fileRepository.getFileInfo(path)
    }

    /**
     * Get storage information.
     */
    suspend fun getStorageInfo(drivePath: String = ""): Result<StorageInfo> {
        return fileRepository.getStorageInfo(drivePath)
    }

    /**
     * Get available drives.
     */
    suspend fun getDrives(): Result<List<String>> {
        return fileRepository.getDrives()
    }
}
