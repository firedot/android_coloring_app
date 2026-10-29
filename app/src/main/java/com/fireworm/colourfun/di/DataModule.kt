package com.fireworm.colourfun.di

import android.content.Context
import com.fireworm.colourfun.data.artwork.ArtworkRepository
import com.fireworm.colourfun.data.export.GalleryExporter
import com.fireworm.colourfun.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Lives as long as the process: for work that must outlive a screen, like saving. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideArtworkRepository(@ApplicationContext context: Context) = ArtworkRepository.create(context)

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context) = SettingsRepository.create(context)

    @Provides
    @Singleton
    fun provideGalleryExporter(@ApplicationContext context: Context) = GalleryExporter(context)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
