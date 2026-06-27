package com.sidequest.data.reminder;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.time.Clock;
import javax.annotation.processing.Generated;

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
public final class ReminderModule_Companion_ProvideClockFactory implements Factory<Clock> {
  @Override
  public Clock get() {
    return provideClock();
  }

  public static ReminderModule_Companion_ProvideClockFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static Clock provideClock() {
    return Preconditions.checkNotNullFromProvides(ReminderModule.Companion.provideClock());
  }

  private static final class InstanceHolder {
    private static final ReminderModule_Companion_ProvideClockFactory INSTANCE = new ReminderModule_Companion_ProvideClockFactory();
  }
}
