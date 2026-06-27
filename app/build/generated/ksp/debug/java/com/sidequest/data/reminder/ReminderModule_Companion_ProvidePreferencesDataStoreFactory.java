package com.sidequest.data.reminder;

import android.content.Context;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class ReminderModule_Companion_ProvidePreferencesDataStoreFactory implements Factory<DataStore<Preferences>> {
  private final Provider<Context> contextProvider;

  public ReminderModule_Companion_ProvidePreferencesDataStoreFactory(
      Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DataStore<Preferences> get() {
    return providePreferencesDataStore(contextProvider.get());
  }

  public static ReminderModule_Companion_ProvidePreferencesDataStoreFactory create(
      Provider<Context> contextProvider) {
    return new ReminderModule_Companion_ProvidePreferencesDataStoreFactory(contextProvider);
  }

  public static DataStore<Preferences> providePreferencesDataStore(Context context) {
    return Preconditions.checkNotNullFromProvides(ReminderModule.Companion.providePreferencesDataStore(context));
  }
}
