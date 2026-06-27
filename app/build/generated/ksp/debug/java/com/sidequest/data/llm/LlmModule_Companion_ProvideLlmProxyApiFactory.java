package com.sidequest.data.llm;

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
public final class LlmModule_Companion_ProvideLlmProxyApiFactory implements Factory<LlmProxyApi> {
  private final Provider<OkHttpClient> clientProvider;

  public LlmModule_Companion_ProvideLlmProxyApiFactory(Provider<OkHttpClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public LlmProxyApi get() {
    return provideLlmProxyApi(clientProvider.get());
  }

  public static LlmModule_Companion_ProvideLlmProxyApiFactory create(
      Provider<OkHttpClient> clientProvider) {
    return new LlmModule_Companion_ProvideLlmProxyApiFactory(clientProvider);
  }

  public static LlmProxyApi provideLlmProxyApi(OkHttpClient client) {
    return Preconditions.checkNotNullFromProvides(LlmModule.Companion.provideLlmProxyApi(client));
  }
}
