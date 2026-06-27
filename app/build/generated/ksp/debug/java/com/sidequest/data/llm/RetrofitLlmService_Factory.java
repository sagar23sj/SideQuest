package com.sidequest.data.llm;

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
public final class RetrofitLlmService_Factory implements Factory<RetrofitLlmService> {
  private final Provider<LlmProxyApi> apiProvider;

  public RetrofitLlmService_Factory(Provider<LlmProxyApi> apiProvider) {
    this.apiProvider = apiProvider;
  }

  @Override
  public RetrofitLlmService get() {
    return newInstance(apiProvider.get());
  }

  public static RetrofitLlmService_Factory create(Provider<LlmProxyApi> apiProvider) {
    return new RetrofitLlmService_Factory(apiProvider);
  }

  public static RetrofitLlmService newInstance(LlmProxyApi api) {
    return new RetrofitLlmService(api);
  }
}
