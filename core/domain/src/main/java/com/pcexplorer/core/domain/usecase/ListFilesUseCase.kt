package com.pcexplorer.core.domain.usecase

import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.SortBy
import com.pcexplorer.core.domain.model.SortDirection
import com.pcexplorer.core.domain.model.SortOrder
import com.pcexplorer.core.domain.repository.FileRepository
import javax.inject.Inject

/**
 * Use case for listing files in a directory.
 */
class ListFilesUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    /**
     * List files in the specified directory.
     */
    suspend operator fun invoke(
        path: String,
        sortOrder: SortOrder = SortOrder()
    ): Result<List<FileItem>> {
        return fileRepository.listFiles(path, sortOrder)
            .map { files -> sortFiles(files, sortOrder) }
    }

    private fun sortFiles(files: List<FileItem>, sortOrder: SortOrder): List<FileItem> {
        val (directories, regularFiles) = files.partition { it.isDirectory }

        val sortedDirs = sortList(directories, sortOrder)
        val sortedFiles = sortList(regularFiles, sortOrder)

        // Directories first, then files
        return sortedDirs + sortedFiles
    }

    private fun sortList(items: List<FileItem>, sortOrder: SortOrder): List<FileItem> {
        val comparator: Comparator<FileItem> = when (sortOrder.sortBy) {
            SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortBy.DATE -> compareBy { it.lastModified }
            SortBy.SIZE -> compareBy { it.size }
            SortBy.TYPE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.extension }
        }

        return when (sortOrder.direction) {
            SortDirection.ASCENDING -> items.sortedWith(comparator)
            SortDirection.DESCENDING -> items.sortedWith(comparator.reversed())
        }
    }
}
