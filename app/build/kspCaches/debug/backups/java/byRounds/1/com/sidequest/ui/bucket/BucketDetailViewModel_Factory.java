package com.sidequest.ui.bucket;

import androidx.lifecycle.SavedStateHandle;
import com.sidequest.data.repository.BoardRepository;
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
public final class BucketDetailViewModel_Factory implements Factory<BucketDetailViewModel> {
  private final Provider<BoardRepository> boardRepositoryProvider;

  private final Provider<CurrentAccountProvider> accountProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public BucketDetailViewModel_Factory(Provider<BoardRepository> boardRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.boardRepositoryProvider = boardRepositoryProvider;
    this.accountProvider = accountProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public BucketDetailViewModel get() {
    return newInstance(boardRepositoryProvider.get(), accountProvider.get(), savedStateHandleProvider.get());
  }

  public static BucketDetailViewModel_Factory create(
      Provider<BoardRepository> boardRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new BucketDetailViewModel_Factory(boardRepositoryProvider, accountProvider, savedStateHandleProvider);
  }

  public static BucketDetailViewModel newInstance(BoardRepository boardRepository,
      CurrentAccountProvider accountProvider, SavedStateHandle savedStateHandle) {
    return new BucketDetailViewModel(boardRepository, accountProvider, savedStateHandle);
  }
}
