package com.pcexplorer.core.domain.model

/**
 * Sorting options for file browser.
 */
enum class SortBy {
    NAME,
    DATE,
    SIZE,
    TYPE
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

data class SortOrder(
    val sortBy: SortBy = SortBy.NAME,
    val direction: SortDirection = SortDirection.ASCENDING
) {
    fun toggle(): SortOrder = copy(
        direction = when (direction) {
            SortDirection.ASCENDING -> SortDirection.DESCENDING
            SortDirection.DESCENDING -> SortDirection.ASCENDING
        }
    )
}
