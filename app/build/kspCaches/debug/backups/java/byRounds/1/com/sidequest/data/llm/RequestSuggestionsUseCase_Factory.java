package com.sidequest.data.llm;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class RequestSuggestionsUseCase_Factory implements Factory<RequestSuggestionsUseCase> {
  private final Provider<LlmService> llmServiceProvider;

  public RequestSuggestionsUseCase_Factory(Provider<LlmService> llmServiceProvider) {
    this.llmServiceProvider = llmServiceProvider;
  }

  @Override
  public RequestSuggestionsUseCase get() {
    return newInstance(llmServiceProvider.get());
  }

  public static RequestSuggestionsUseCase_Factory create(Provider<LlmService> llmServiceProvider) {
    return new RequestSuggestionsUseCase_Factory(llmServiceProvider);
  }

  public static RequestSuggestionsUseCase newInstance(LlmService llmService) {
    return new RequestSuggestionsUseCase(llmService);
  }
}
