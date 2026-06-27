package com.sidequest.data.local.di;

import com.sidequest.data.local.SideQuestDatabase;
import com.sidequest.data.local.dao.BucketDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideBucketDaoFactory implements Factory<BucketDao> {
  private final Provider<SideQuestDatabase> databaseProvider;

  public DatabaseModule_ProvideBucketDaoFactory(Provider<SideQuestDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public BucketDao get() {
    return provideBucketDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideBucketDaoFactory create(
      Provider<SideQuestDatabase> databaseProvider) {
    return new DatabaseModule_ProvideBucketDaoFactory(databaseProvider);
  }

  public static BucketDao provideBucketDao(SideQuestDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideBucketDao(database));
  }
}
