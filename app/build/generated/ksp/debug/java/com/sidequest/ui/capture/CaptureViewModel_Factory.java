package com.sidequest.ui.capture;

import com.sidequest.data.repository.BucketRepository;
import com.sidequest.data.repository.CaptureRepository;
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
public final class CaptureViewModel_Factory implements Factory<CaptureViewModel> {
  private final Provider<CaptureRepository> captureRepositoryProvider;

  private final Provider<BucketRepository> bucketRepositoryProvider;

  private final Provider<CurrentAccountProvider> accountProvider;

  public CaptureViewModel_Factory(Provider<CaptureRepository> captureRepositoryProvider,
      Provider<BucketRepository> bucketRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider) {
    this.captureRepositoryProvider = captureRepositoryProvider;
    this.bucketRepositoryProvider = bucketRepositoryProvider;
    this.accountProvider = accountProvider;
  }

  @Override
  public CaptureViewModel get() {
    return newInstance(captureRepositoryProvider.get(), bucketRepositoryProvider.get(), accountProvider.get());
  }

  public static CaptureViewModel_Factory create(
      Provider<CaptureRepository> captureRepositoryProvider,
      Provider<BucketRepository> bucketRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider) {
    return new CaptureViewModel_Factory(captureRepositoryProvider, bucketRepositoryProvider, accountProvider);
  }

  public static CaptureViewModel newInstance(CaptureRepository captureRepository,
      BucketRepository bucketRepository, CurrentAccountProvider accountProvider) {
    return new CaptureViewModel(captureRepository, bucketRepository, accountProvider);
  }
}
