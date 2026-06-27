package com.sidequest.data.repository;

import com.sidequest.data.local.dao.ActionItemDao;
import com.sidequest.data.local.dao.BucketDao;
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
public final class BucketRepository_Factory implements Factory<BucketRepository> {
  private final Provider<BucketDao> bucketDaoProvider;

  private final Provider<ActionItemDao> actionItemDaoProvider;

  public BucketRepository_Factory(Provider<BucketDao> bucketDaoProvider,
      Provider<ActionItemDao> actionItemDaoProvider) {
    this.bucketDaoProvider = bucketDaoProvider;
    this.actionItemDaoProvider = actionItemDaoProvider;
  }

  @Override
  public BucketRepository get() {
    return newInstance(bucketDaoProvider.get(), actionItemDaoProvider.get());
  }

  public static BucketRepository_Factory create(Provider<BucketDao> bucketDaoProvider,
      Provider<ActionItemDao> actionItemDaoProvider) {
    return new BucketRepository_Factory(bucketDaoProvider, actionItemDaoProvider);
  }

  public static BucketRepository newInstance(BucketDao bucketDao, ActionItemDao actionItemDao) {
    return new BucketRepository(bucketDao, actionItemDao);
  }
}
