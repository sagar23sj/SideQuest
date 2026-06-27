package com.sidequest.ui.bucket;

import com.sidequest.data.repository.BucketRepository;
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
public final class BucketManagementViewModel_Factory implements Factory<BucketManagementViewModel> {
  private final Provider<BucketRepository> bucketRepositoryProvider;

  private final Provider<CurrentAccountProvider> accountProvider;

  public BucketManagementViewModel_Factory(Provider<BucketRepository> bucketRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider) {
    this.bucketRepositoryProvider = bucketRepositoryProvider;
    this.accountProvider = accountProvider;
  }

  @Override
  public BucketManagementViewModel get() {
    return newInstance(bucketRepositoryProvider.get(), accountProvider.get());
  }

  public static BucketManagementViewModel_Factory create(
      Provider<BucketRepository> bucketRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider) {
    return new BucketManagementViewModel_Factory(bucketRepositoryProvider, accountProvider);
  }

  public static BucketManagementViewModel newInstance(BucketRepository bucketRepository,
      CurrentAccountProvider accountProvider) {
    return new BucketManagementViewModel(bucketRepository, accountProvider);
  }
}
