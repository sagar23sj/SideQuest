package com.sidequest.data.audio;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata({
    "dagger.hilt.android.qualifiers.ApplicationContext",
    "com.sidequest.di.IoDispatcher"
})
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class MediaRecorderAudioRecorder_Factory implements Factory<MediaRecorderAudioRecorder> {
  private final Provider<Context> contextProvider;

  private final Provider<CoroutineDispatcher> ioDispatcherProvider;

  public MediaRecorderAudioRecorder_Factory(Provider<Context> contextProvider,
      Provider<CoroutineDispatcher> ioDispatcherProvider) {
    this.contextProvider = contextProvider;
    this.ioDispatcherProvider = ioDispatcherProvider;
  }

  @Override
  public MediaRecorderAudioRecorder get() {
    return newInstance(contextProvider.get(), ioDispatcherProvider.get());
  }

  public static MediaRecorderAudioRecorder_Factory create(Provider<Context> contextProvider,
      Provider<CoroutineDispatcher> ioDispatcherProvider) {
    return new MediaRecorderAudioRecorder_Factory(contextProvider, ioDispatcherProvider);
  }

  public static MediaRecorderAudioRecorder newInstance(Context context,
      CoroutineDispatcher ioDispatcher) {
    return new MediaRecorderAudioRecorder(context, ioDispatcher);
  }
}
