package com.sidequest.data.local.di;

import com.sidequest.data.local.SideQuestDatabase;
import com.sidequest.data.local.dao.ActionItemDao;
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
public final class DatabaseModule_ProvideActionItemDaoFactory implements Factory<ActionItemDao> {
  private final Provider<SideQuestDatabase> databaseProvider;

  public DatabaseModule_ProvideActionItemDaoFactory(Provider<SideQuestDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ActionItemDao get() {
    return provideActionItemDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideActionItemDaoFactory create(
      Provider<SideQuestDatabase> databaseProvider) {
    return new DatabaseModule_ProvideActionItemDaoFactory(databaseProvider);
  }

  public static ActionItemDao provideActionItemDao(SideQuestDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideActionItemDao(database));
  }
}
