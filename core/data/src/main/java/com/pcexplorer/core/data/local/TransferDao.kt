package com.pcexplorer.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for transfer operations.
 */
@Dao
interface TransferDao {

    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun getAllTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE state IN ('Pending', 'InProgress') ORDER BY createdAt DESC")
    fun getActiveTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun getTransferById(id: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE id = :id")
    fun getTransferByIdFlow(id: String): Flow<TransferEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity)

    @Update
    suspend fun updateTransfer(transfer: TransferEntity)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteTransfer(id: String)

    @Query("DELETE FROM transfers WHERE state IN ('Completed', 'Failed', 'Cancelled')")
    suspend fun clearHistory()

    @Query("UPDATE transfers SET transferredBytes = :bytes, state = :state WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long, state: String)
}
