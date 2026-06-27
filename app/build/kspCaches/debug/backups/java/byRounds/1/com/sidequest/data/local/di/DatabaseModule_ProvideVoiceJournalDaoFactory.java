package com.sidequest.data.local.di;

import com.sidequest.data.local.SideQuestDatabase;
import com.sidequest.data.local.dao.VoiceJournalDao;
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
public final class DatabaseModule_ProvideVoiceJournalDaoFactory implements Factory<VoiceJournalDao> {
  private final Provider<SideQuestDatabase> databaseProvider;

  public DatabaseModule_ProvideVoiceJournalDaoFactory(
      Provider<SideQuestDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public VoiceJournalDao get() {
    return provideVoiceJournalDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideVoiceJournalDaoFactory create(
      Provider<SideQuestDatabase> databaseProvider) {
    return new DatabaseModule_ProvideVoiceJournalDaoFactory(databaseProvider);
  }

  public static VoiceJournalDao provideVoiceJournalDao(SideQuestDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideVoiceJournalDao(database));
  }
}
