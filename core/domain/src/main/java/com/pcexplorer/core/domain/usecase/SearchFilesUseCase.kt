package com.pcexplorer.core.domain.usecase

import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.repository.FileRepository
import javax.inject.Inject

/**
 * Use case for searching files.
 */
class SearchFilesUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    /**
     * Search for files matching the query.
     * @param query Search query
     * @param path Optional path to limit search scope
     */
    suspend operator fun invoke(query: String, path: String = ""): Result<List<FileItem>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }
        return fileRepository.searchFiles(query.trim(), path)
    }
}
