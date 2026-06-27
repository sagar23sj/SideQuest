package com.sidequest.ui.reminder;

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
public final class NotificationPermissionViewModel_Factory implements Factory<NotificationPermissionViewModel> {
  private final Provider<ReminderSettingsStore> settingsStoreProvider;

  public NotificationPermissionViewModel_Factory(
      Provider<ReminderSettingsStore> settingsStoreProvider) {
    this.settingsStoreProvider = settingsStoreProvider;
  }

  @Override
  public NotificationPermissionViewModel get() {
    return newInstance(settingsStoreProvider.get());
  }

  public static NotificationPermissionViewModel_Factory create(
      Provider<ReminderSettingsStore> settingsStoreProvider) {
    return new NotificationPermissionViewModel_Factory(settingsStoreProvider);
  }

  public static NotificationPermissionViewModel newInstance(ReminderSettingsStore settingsStore) {
    return new NotificationPermissionViewModel(settingsStore);
  }
}
