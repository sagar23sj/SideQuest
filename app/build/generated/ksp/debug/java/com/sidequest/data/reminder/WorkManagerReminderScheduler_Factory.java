package com.sidequest.data.reminder;

import androidx.work.WorkManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.time.Clock;
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
public final class WorkManagerReminderScheduler_Factory implements Factory<WorkManagerReminderScheduler> {
  private final Provider<WorkManager> workManagerProvider;

  private final Provider<Clock> clockProvider;

  public WorkManagerReminderScheduler_Factory(Provider<WorkManager> workManagerProvider,
      Provider<Clock> clockProvider) {
    this.workManagerProvider = workManagerProvider;
    this.clockProvider = clockProvider;
  }

  @Override
  public WorkManagerReminderScheduler get() {
    return newInstance(workManagerProvider.get(), clockProvider.get());
  }

  public static WorkManagerReminderScheduler_Factory create(
      Provider<WorkManager> workManagerProvider, Provider<Clock> clockProvider) {
    return new WorkManagerReminderScheduler_Factory(workManagerProvider, clockProvider);
  }

  public static WorkManagerReminderScheduler newInstance(WorkManager workManager, Clock clock) {
    return new WorkManagerReminderScheduler(workManager, clock);
  }
}
