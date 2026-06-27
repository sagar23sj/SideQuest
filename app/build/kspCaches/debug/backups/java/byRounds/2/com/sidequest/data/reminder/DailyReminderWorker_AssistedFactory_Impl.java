package com.sidequest.data.reminder;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class DailyReminderWorker_AssistedFactory_Impl implements DailyReminderWorker_AssistedFactory {
  private final DailyReminderWorker_Factory delegateFactory;

  DailyReminderWorker_AssistedFactory_Impl(DailyReminderWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public DailyReminderWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<DailyReminderWorker_AssistedFactory> create(
      DailyReminderWorker_Factory delegateFactory) {
    return InstanceFactory.create(new DailyReminderWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<DailyReminderWorker_AssistedFactory> createFactoryProvider(
      DailyReminderWorker_Factory delegateFactory) {
    return InstanceFactory.create(new DailyReminderWorker_AssistedFactory_Impl(delegateFactory));
  }
}
