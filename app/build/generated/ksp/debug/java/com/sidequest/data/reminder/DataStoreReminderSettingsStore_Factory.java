package com.sidequest.data.reminder;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
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
public final class DataStoreReminderSettingsStore_Factory implements Factory<DataStoreReminderSettingsStore> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public DataStoreReminderSettingsStore_Factory(
      Provider<DataStore<Preferences>> dataStoreProvider) {
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public DataStoreReminderSettingsStore get() {
    return newInstance(dataStoreProvider.get());
  }

  public static DataStoreReminderSettingsStore_Factory create(
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new DataStoreReminderSettingsStore_Factory(dataStoreProvider);
  }

  public static DataStoreReminderSettingsStore newInstance(DataStore<Preferences> dataStore) {
    return new DataStoreReminderSettingsStore(dataStore);
  }
}
