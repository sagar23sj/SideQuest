package com.sidequest.data.preview;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
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
public final class PreviewModule_Companion_ProvideOkHttpClientFactory implements Factory<OkHttpClient> {
  @Override
  public OkHttpClient get() {
    return provideOkHttpClient();
  }

  public static PreviewModule_Companion_ProvideOkHttpClientFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static OkHttpClient provideOkHttpClient() {
    return Preconditions.checkNotNullFromProvides(PreviewModule.Companion.provideOkHttpClient());
  }

  private static final class InstanceHolder {
    private static final PreviewModule_Companion_ProvideOkHttpClientFactory INSTANCE = new PreviewModule_Companion_ProvideOkHttpClientFactory();
  }
}
