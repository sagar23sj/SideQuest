package com.sidequest.data.preview;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class OkHttpPreviewService_Factory implements Factory<OkHttpPreviewService> {
  private final Provider<OkHttpClient> clientProvider;

  public OkHttpPreviewService_Factory(Provider<OkHttpClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public OkHttpPreviewService get() {
    return newInstance(clientProvider.get());
  }

  public static OkHttpPreviewService_Factory create(Provider<OkHttpClient> clientProvider) {
    return new OkHttpPreviewService_Factory(clientProvider);
  }

  public static OkHttpPreviewService newInstance(OkHttpClient client) {
    return new OkHttpPreviewService(client);
  }
}
