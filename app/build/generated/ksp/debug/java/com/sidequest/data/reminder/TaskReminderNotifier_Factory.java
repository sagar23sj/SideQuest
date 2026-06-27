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
public final class TaskReminderNotifier_Factory implements Factory<TaskReminderNotifier> {
  private final Provider<Context> contextProvider;

  public TaskReminderNotifier_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public TaskReminderNotifier get() {
    return newInstance(contextProvider.get());
  }

  public static TaskReminderNotifier_Factory create(Provider<Context> contextProvider) {
    return new TaskReminderNotifier_Factory(contextProvider);
  }

  public static TaskReminderNotifier newInstance(Context context) {
    return new TaskReminderNotifier(context);
  }
}
