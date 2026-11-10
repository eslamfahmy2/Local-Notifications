package com.example.localnotifications.di

import android.content.Context
import androidx.work.WorkManager
import com.example.localnotifications.core.domain.scheduler.NotificationScheduler
import com.example.localnotifications.core.notifications.scheduler.WorkManagerNotificationScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {

    @Binds
    @Singleton
    abstract fun bindNotificationScheduler(impl: WorkManagerNotificationScheduler): NotificationScheduler

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
