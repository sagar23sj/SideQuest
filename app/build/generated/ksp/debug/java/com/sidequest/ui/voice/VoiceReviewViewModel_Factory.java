package com.sidequest.ui.voice;

import androidx.lifecycle.SavedStateHandle;
import com.sidequest.data.repository.BucketRepository;
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
public final class VoiceReviewViewModel_Factory implements Factory<VoiceReviewViewModel> {
  private final Provider<VoiceJournalRepository> voiceRepositoryProvider;

  private final Provider<BucketRepository> bucketRepositoryProvider;

  private final Provider<CurrentAccountProvider> accountProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public VoiceReviewViewModel_Factory(Provider<VoiceJournalRepository> voiceRepositoryProvider,
      Provider<BucketRepository> bucketRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.voiceRepositoryProvider = voiceRepositoryProvider;
    this.bucketRepositoryProvider = bucketRepositoryProvider;
    this.accountProvider = accountProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public VoiceReviewViewModel get() {
    return newInstance(voiceRepositoryProvider.get(), bucketRepositoryProvider.get(), accountProvider.get(), savedStateHandleProvider.get());
  }

  public static VoiceReviewViewModel_Factory create(
      Provider<VoiceJournalRepository> voiceRepositoryProvider,
      Provider<BucketRepository> bucketRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new VoiceReviewViewModel_Factory(voiceRepositoryProvider, bucketRepositoryProvider, accountProvider, savedStateHandleProvider);
  }

  public static VoiceReviewViewModel newInstance(VoiceJournalRepository voiceRepository,
      BucketRepository bucketRepository, CurrentAccountProvider accountProvider,
      SavedStateHandle savedStateHandle) {
    return new VoiceReviewViewModel(voiceRepository, bucketRepository, accountProvider, savedStateHandle);
  }
}
