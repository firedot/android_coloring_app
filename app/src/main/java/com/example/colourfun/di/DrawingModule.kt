package com.example.colourfun.di

import android.app.ActivityManager
import android.content.Context
import com.example.colourfun.drawing.document.DocumentSize
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DrawingModule {

    @Provides
    @Singleton
    fun provideDocumentSize(@ApplicationContext context: Context): DocumentSize {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        return DocumentSize.forDevice(activityManager.isLowRamDevice)
    }
}
