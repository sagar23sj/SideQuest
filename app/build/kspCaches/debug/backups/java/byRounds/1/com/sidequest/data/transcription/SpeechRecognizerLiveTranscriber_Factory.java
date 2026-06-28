package com.sidequest.data.transcription;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class SpeechRecognizerLiveTranscriber_Factory implements Factory<SpeechRecognizerLiveTranscriber> {
  private final Provider<Context> contextProvider;

  public SpeechRecognizerLiveTranscriber_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SpeechRecognizerLiveTranscriber get() {
    return newInstance(contextProvider.get());
  }

  public static SpeechRecognizerLiveTranscriber_Factory create(Provider<Context> contextProvider) {
    return new SpeechRecognizerLiveTranscriber_Factory(contextProvider);
  }

  public static SpeechRecognizerLiveTranscriber newInstance(Context context) {
    return new SpeechRecognizerLiveTranscriber(context);
  }
}
