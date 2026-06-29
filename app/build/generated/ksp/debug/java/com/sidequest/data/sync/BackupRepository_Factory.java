package com.sidequest.data.sync;

import com.sidequest.data.local.dao.ActionItemDao;
import com.sidequest.data.local.dao.ActionPlanDao;
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
public final class BackupRepository_Factory implements Factory<BackupRepository> {
  private final Provider<BucketDao> bucketDaoProvider;

  private final Provider<ActionItemDao> actionItemDaoProvider;

  private final Provider<ActionPlanDao> actionPlanDaoProvider;

  private final Provider<BackupApi> backupApiProvider;

  private final Provider<DeviceIdentity> deviceIdentityProvider;

  public BackupRepository_Factory(Provider<BucketDao> bucketDaoProvider,
      Provider<ActionItemDao> actionItemDaoProvider, Provider<ActionPlanDao> actionPlanDaoProvider,
      Provider<BackupApi> backupApiProvider, Provider<DeviceIdentity> deviceIdentityProvider) {
    this.bucketDaoProvider = bucketDaoProvider;
    this.actionItemDaoProvider = actionItemDaoProvider;
    this.actionPlanDaoProvider = actionPlanDaoProvider;
    this.backupApiProvider = backupApiProvider;
    this.deviceIdentityProvider = deviceIdentityProvider;
  }

  @Override
  public BackupRepository get() {
    return newInstance(bucketDaoProvider.get(), actionItemDaoProvider.get(), actionPlanDaoProvider.get(), backupApiProvider.get(), deviceIdentityProvider.get());
  }

  public static BackupRepository_Factory create(Provider<BucketDao> bucketDaoProvider,
      Provider<ActionItemDao> actionItemDaoProvider, Provider<ActionPlanDao> actionPlanDaoProvider,
      Provider<BackupApi> backupApiProvider, Provider<DeviceIdentity> deviceIdentityProvider) {
    return new BackupRepository_Factory(bucketDaoProvider, actionItemDaoProvider, actionPlanDaoProvider, backupApiProvider, deviceIdentityProvider);
  }

  public static BackupRepository newInstance(BucketDao bucketDao, ActionItemDao actionItemDao,
      ActionPlanDao actionPlanDao, BackupApi backupApi, DeviceIdentity deviceIdentity) {
    return new BackupRepository(bucketDao, actionItemDao, actionPlanDao, backupApi, deviceIdentity);
  }
}
