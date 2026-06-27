package com.sidequest.ui.reminder;

import com.sidequest.data.reminder.ReminderScheduler;
import com.sidequest.data.reminder.ReminderSettingsStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ReminderSettingsViewModel_Factory implements Factory<ReminderSettingsViewModel> {
  private final Provider<ReminderSettingsStore> settingsStoreProvider;

  private final Provider<ReminderScheduler> schedulerProvider;

  public ReminderSettingsViewModel_Factory(Provider<ReminderSettingsStore> settingsStoreProvider,
      Provider<ReminderScheduler> schedulerProvider) {
    this.settingsStoreProvider = settingsStoreProvider;
    this.schedulerProvider = schedulerProvider;
  }

  @Override
  public ReminderSettingsViewModel get() {
    return newInstance(settingsStoreProvider.get(), schedulerProvider.get());
  }

  public static ReminderSettingsViewModel_Factory create(
      Provider<ReminderSettingsStore> settingsStoreProvider,
      Provider<ReminderScheduler> schedulerProvider) {
    return new ReminderSettingsViewModel_Factory(settingsStoreProvider, schedulerProvider);
  }

  public static ReminderSettingsViewModel newInstance(ReminderSettingsStore settingsStore,
      ReminderScheduler scheduler) {
    return new ReminderSettingsViewModel(settingsStore, scheduler);
  }
}
