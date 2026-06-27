package com.sidequest.data.transcription;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class RetrofitTranscriptionService_Factory implements Factory<RetrofitTranscriptionService> {
  private final Provider<TranscriptionProxyApi> apiProvider;

  public RetrofitTranscriptionService_Factory(Provider<TranscriptionProxyApi> apiProvider) {
    this.apiProvider = apiProvider;
  }

  @Override
  public RetrofitTranscriptionService get() {
    return newInstance(apiProvider.get());
  }

  public static RetrofitTranscriptionService_Factory create(
      Provider<TranscriptionProxyApi> apiProvider) {
    return new RetrofitTranscriptionService_Factory(apiProvider);
  }

  public static RetrofitTranscriptionService newInstance(TranscriptionProxyApi api) {
    return new RetrofitTranscriptionService(api);
  }
}
