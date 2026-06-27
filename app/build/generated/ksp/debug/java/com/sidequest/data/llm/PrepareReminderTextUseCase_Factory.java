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
public final class PrepareReminderTextUseCase_Factory implements Factory<PrepareReminderTextUseCase> {
  private final Provider<LlmService> llmServiceProvider;

  public PrepareReminderTextUseCase_Factory(Provider<LlmService> llmServiceProvider) {
    this.llmServiceProvider = llmServiceProvider;
  }

  @Override
  public PrepareReminderTextUseCase get() {
    return newInstance(llmServiceProvider.get());
  }

  public static PrepareReminderTextUseCase_Factory create(Provider<LlmService> llmServiceProvider) {
    return new PrepareReminderTextUseCase_Factory(llmServiceProvider);
  }

  public static PrepareReminderTextUseCase newInstance(LlmService llmService) {
    return new PrepareReminderTextUseCase(llmService);
  }
}
