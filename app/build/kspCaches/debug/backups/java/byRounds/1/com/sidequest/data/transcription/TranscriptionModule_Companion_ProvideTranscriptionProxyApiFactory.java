package com.sidequest.data.transcription;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class TranscriptionModule_Companion_ProvideTranscriptionProxyApiFactory implements Factory<TranscriptionProxyApi> {
  private final Provider<OkHttpClient> clientProvider;

  public TranscriptionModule_Companion_ProvideTranscriptionProxyApiFactory(
      Provider<OkHttpClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public TranscriptionProxyApi get() {
    return provideTranscriptionProxyApi(clientProvider.get());
  }

  public static TranscriptionModule_Companion_ProvideTranscriptionProxyApiFactory create(
      Provider<OkHttpClient> clientProvider) {
    return new TranscriptionModule_Companion_ProvideTranscriptionProxyApiFactory(clientProvider);
  }

  public static TranscriptionProxyApi provideTranscriptionProxyApi(OkHttpClient client) {
    return Preconditions.checkNotNullFromProvides(TranscriptionModule.Companion.provideTranscriptionProxyApi(client));
  }
}
