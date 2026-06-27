package com.sidequest.data.reminder;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.time.Clock;
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
public final class TaskReminderScheduler_Factory implements Factory<TaskReminderScheduler> {
  private final Provider<Context> contextProvider;

  private final Provider<Clock> clockProvider;

  public TaskReminderScheduler_Factory(Provider<Context> contextProvider,
      Provider<Clock> clockProvider) {
    this.contextProvider = contextProvider;
    this.clockProvider = clockProvider;
  }

  @Override
  public TaskReminderScheduler get() {
    return newInstance(contextProvider.get(), clockProvider.get());
  }

  public static TaskReminderScheduler_Factory create(Provider<Context> contextProvider,
      Provider<Clock> clockProvider) {
    return new TaskReminderScheduler_Factory(contextProvider, clockProvider);
  }

  public static TaskReminderScheduler newInstance(Context context, Clock clock) {
    return new TaskReminderScheduler(context, clock);
  }
}
