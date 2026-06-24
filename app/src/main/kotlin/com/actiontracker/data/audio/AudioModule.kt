package com.actiontracker.data.audio

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for audio capture (Req 10.2).
 *
 * Binds the [AudioRecorder] seam to its [MediaRecorderAudioRecorder]
 * implementation so the voice-journal repository can depend on the interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioRecorder(impl: MediaRecorderAudioRecorder): AudioRecorder
}
