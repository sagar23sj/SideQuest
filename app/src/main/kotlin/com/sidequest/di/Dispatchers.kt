package com.sidequest.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Qualifier for the IO [CoroutineDispatcher] used for disk/network/audio work.
 *
 * Injecting the dispatcher (rather than referencing [Dispatchers.IO] directly)
 * keeps IO-bound components testable — tests substitute a test dispatcher — per
 * the Android Kotlin skill's "inject dispatchers for testability" guidance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Hilt bindings for the app's [CoroutineDispatcher]s.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
