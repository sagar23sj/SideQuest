package com.sidequest.ui.bucket;

import android.content.Context;
import androidx.lifecycle.SavedStateHandle;
import com.sidequest.data.local.dao.BucketDao;
import com.sidequest.data.repository.BucketRepository;
import com.sidequest.ui.capture.CurrentAccountProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class CreateBucketViewModel_Factory implements Factory<CreateBucketViewModel> {
  private final Provider<BucketRepository> bucketRepositoryProvider;

  private final Provider<CurrentAccountProvider> accountProvider;

  private final Provider<Context> appContextProvider;

  private final Provider<BucketDao> bucketDaoProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public CreateBucketViewModel_Factory(Provider<BucketRepository> bucketRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider, Provider<Context> appContextProvider,
      Provider<BucketDao> bucketDaoProvider, Provider<SavedStateHandle> savedStateHandleProvider) {
    this.bucketRepositoryProvider = bucketRepositoryProvider;
    this.accountProvider = accountProvider;
    this.appContextProvider = appContextProvider;
    this.bucketDaoProvider = bucketDaoProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public CreateBucketViewModel get() {
    return newInstance(bucketRepositoryProvider.get(), accountProvider.get(), appContextProvider.get(), bucketDaoProvider.get(), savedStateHandleProvider.get());
  }

  public static CreateBucketViewModel_Factory create(
      Provider<BucketRepository> bucketRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider, Provider<Context> appContextProvider,
      Provider<BucketDao> bucketDaoProvider, Provider<SavedStateHandle> savedStateHandleProvider) {
    return new CreateBucketViewModel_Factory(bucketRepositoryProvider, accountProvider, appContextProvider, bucketDaoProvider, savedStateHandleProvider);
  }

  public static CreateBucketViewModel newInstance(BucketRepository bucketRepository,
      CurrentAccountProvider accountProvider, Context appContext, BucketDao bucketDao,
      SavedStateHandle savedStateHandle) {
    return new CreateBucketViewModel(bucketRepository, accountProvider, appContext, bucketDao, savedStateHandle);
  }
}
