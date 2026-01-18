package com.pcexplorer.core.data.di

import android.content.Context
import androidx.room.Room
import com.pcexplorer.core.data.local.AppDatabase
import com.pcexplorer.core.data.local.TransferDao
import com.pcexplorer.core.data.repository.ConnectionProvider
import com.pcexplorer.core.data.repository.FileRepositoryImpl
import com.pcexplorer.core.data.repository.TcpConnectionRepositoryImpl
import com.pcexplorer.core.data.repository.TransferRepositoryImpl
import com.pcexplorer.core.data.repository.UsbConnectionRepositoryImpl
import com.pcexplorer.core.domain.repository.FileRepository
import com.pcexplorer.core.domain.repository.TransferRepository
import com.pcexplorer.core.domain.repository.UsbConnectionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(
        impl: ConnectionProvider
    ): UsbConnectionRepository

    @Binds
    @Singleton
    abstract fun bindFileRepository(
        impl: FileRepositoryImpl
    ): FileRepository

    @Binds
    @Singleton
    abstract fun bindTransferRepository(
        impl: TransferRepositoryImpl
    ): TransferRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ConnectionModule {

    @Provides
    @Singleton
    fun provideTcpConnectionRepository(): TcpConnectionRepositoryImpl {
        return TcpConnectionRepositoryImpl()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideTransferDao(database: AppDatabase): TransferDao {
        return database.transferDao()
    }
}
