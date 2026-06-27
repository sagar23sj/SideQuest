package com.sidequest.ui.voice;

import com.sidequest.data.repository.VoiceJournalRepository;
import com.sidequest.ui.capture.CurrentAccountProvider;
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
public final class VoiceJournalViewModel_Factory implements Factory<VoiceJournalViewModel> {
  private final Provider<VoiceJournalRepository> repositoryProvider;

  private final Provider<CurrentAccountProvider> accountProvider;

  public VoiceJournalViewModel_Factory(Provider<VoiceJournalRepository> repositoryProvider,
      Provider<CurrentAccountProvider> accountProvider) {
    this.repositoryProvider = repositoryProvider;
    this.accountProvider = accountProvider;
  }

  @Override
  public VoiceJournalViewModel get() {
    return newInstance(repositoryProvider.get(), accountProvider.get());
  }

  public static VoiceJournalViewModel_Factory create(
      Provider<VoiceJournalRepository> repositoryProvider,
      Provider<CurrentAccountProvider> accountProvider) {
    return new VoiceJournalViewModel_Factory(repositoryProvider, accountProvider);
  }

  public static VoiceJournalViewModel newInstance(VoiceJournalRepository repository,
      CurrentAccountProvider accountProvider) {
    return new VoiceJournalViewModel(repository, accountProvider);
  }
}
