package com.sidequest.data.seed;

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
public final class DefaultBucketSeeder_Factory implements Factory<DefaultBucketSeeder> {
  private final Provider<BucketDao> bucketDaoProvider;

  public DefaultBucketSeeder_Factory(Provider<BucketDao> bucketDaoProvider) {
    this.bucketDaoProvider = bucketDaoProvider;
  }

  @Override
  public DefaultBucketSeeder get() {
    return newInstance(bucketDaoProvider.get());
  }

  public static DefaultBucketSeeder_Factory create(Provider<BucketDao> bucketDaoProvider) {
    return new DefaultBucketSeeder_Factory(bucketDaoProvider);
  }

  public static DefaultBucketSeeder newInstance(BucketDao bucketDao) {
    return new DefaultBucketSeeder(bucketDao);
  }
}
