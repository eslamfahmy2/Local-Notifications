package com.example.localnotifications.di

import com.example.localnotifications.core.data.remote.parser.NotificationParser
import com.example.localnotifications.core.data.remote.parser.SaxNotificationParser
import com.example.localnotifications.core.data.repository.NotificationRepositoryImpl
import com.example.localnotifications.core.domain.repository.NotificationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    // Singleton: the repository keeps the loaded list in memory for detail lookups.
    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    abstract fun bindNotificationParser(impl: SaxNotificationParser): NotificationParser

    companion object {
        // The only CoroutineDispatcher binding in the app, so no qualifier is needed yet.
        @Provides
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
