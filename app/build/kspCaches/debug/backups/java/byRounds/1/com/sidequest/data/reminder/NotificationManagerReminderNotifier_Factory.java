package com.sidequest.data.reminder;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class NotificationManagerReminderNotifier_Factory implements Factory<NotificationManagerReminderNotifier> {
  private final Provider<Context> contextProvider;

  public NotificationManagerReminderNotifier_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public NotificationManagerReminderNotifier get() {
    return newInstance(contextProvider.get());
  }

  public static NotificationManagerReminderNotifier_Factory create(
      Provider<Context> contextProvider) {
    return new NotificationManagerReminderNotifier_Factory(contextProvider);
  }

  public static NotificationManagerReminderNotifier newInstance(Context context) {
    return new NotificationManagerReminderNotifier(context);
  }
}
