package com.sidequest.data.local.di;

import com.sidequest.data.local.SideQuestDatabase;
import com.sidequest.data.local.dao.ActionPlanDao;
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
public final class DatabaseModule_ProvideActionPlanDaoFactory implements Factory<ActionPlanDao> {
  private final Provider<SideQuestDatabase> databaseProvider;

  public DatabaseModule_ProvideActionPlanDaoFactory(Provider<SideQuestDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ActionPlanDao get() {
    return provideActionPlanDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideActionPlanDaoFactory create(
      Provider<SideQuestDatabase> databaseProvider) {
    return new DatabaseModule_ProvideActionPlanDaoFactory(databaseProvider);
  }

  public static ActionPlanDao provideActionPlanDao(SideQuestDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideActionPlanDao(database));
  }
}
