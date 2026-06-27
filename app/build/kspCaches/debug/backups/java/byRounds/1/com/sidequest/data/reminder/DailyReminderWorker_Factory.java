package com.sidequest.data.reminder;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.sidequest.data.llm.PrepareReminderTextUseCase;
import com.sidequest.data.local.dao.ActionItemDao;
import com.sidequest.data.local.dao.BucketDao;
import com.sidequest.ui.capture.CurrentAccountProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.time.Clock;
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
public final class DailyReminderWorker_Factory {
  private final Provider<ActionItemDao> actionItemDaoProvider;

  private final Provider<BucketDao> bucketDaoProvider;

  private final Provider<CurrentAccountProvider> currentAccountProvider;

  private final Provider<PrepareReminderTextUseCase> prepareReminderTextUseCaseProvider;

  private final Provider<ReminderSettingsStore> settingsStoreProvider;

  private final Provider<ReminderNotifier> notifierProvider;

  private final Provider<Clock> clockProvider;

  public DailyReminderWorker_Factory(Provider<ActionItemDao> actionItemDaoProvider,
      Provider<BucketDao> bucketDaoProvider,
      Provider<CurrentAccountProvider> currentAccountProvider,
      Provider<PrepareReminderTextUseCase> prepareReminderTextUseCaseProvider,
      Provider<ReminderSettingsStore> settingsStoreProvider,
      Provider<ReminderNotifier> notifierProvider, Provider<Clock> clockProvider) {
    this.actionItemDaoProvider = actionItemDaoProvider;
    this.bucketDaoProvider = bucketDaoProvider;
    this.currentAccountProvider = currentAccountProvider;
    this.prepareReminderTextUseCaseProvider = prepareReminderTextUseCaseProvider;
    this.settingsStoreProvider = settingsStoreProvider;
    this.notifierProvider = notifierProvider;
    this.clockProvider = clockProvider;
  }

  public DailyReminderWorker get(Context appContext, WorkerParameters params) {
    return newInstance(appContext, params, actionItemDaoProvider.get(), bucketDaoProvider.get(), currentAccountProvider.get(), prepareReminderTextUseCaseProvider.get(), settingsStoreProvider.get(), notifierProvider.get(), clockProvider.get());
  }

  public static DailyReminderWorker_Factory create(Provider<ActionItemDao> actionItemDaoProvider,
      Provider<BucketDao> bucketDaoProvider,
      Provider<CurrentAccountProvider> currentAccountProvider,
      Provider<PrepareReminderTextUseCase> prepareReminderTextUseCaseProvider,
      Provider<ReminderSettingsStore> settingsStoreProvider,
      Provider<ReminderNotifier> notifierProvider, Provider<Clock> clockProvider) {
    return new DailyReminderWorker_Factory(actionItemDaoProvider, bucketDaoProvider, currentAccountProvider, prepareReminderTextUseCaseProvider, settingsStoreProvider, notifierProvider, clockProvider);
  }

  public static DailyReminderWorker newInstance(Context appContext, WorkerParameters params,
      ActionItemDao actionItemDao, BucketDao bucketDao,
      CurrentAccountProvider currentAccountProvider,
      PrepareReminderTextUseCase prepareReminderTextUseCase, ReminderSettingsStore settingsStore,
      ReminderNotifier notifier, Clock clock) {
    return new DailyReminderWorker(appContext, params, actionItemDao, bucketDao, currentAccountProvider, prepareReminderTextUseCase, settingsStore, notifier, clock);
  }
}
