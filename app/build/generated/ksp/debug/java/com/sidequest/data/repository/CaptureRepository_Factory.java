package com.sidequest.data.repository;

import com.sidequest.data.local.dao.ActionItemDao;
import com.sidequest.data.preview.PreviewEnqueuer;
import com.sidequest.data.reminder.TaskReminderScheduler;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class CaptureRepository_Factory implements Factory<CaptureRepository> {
  private final Provider<ActionItemDao> actionItemDaoProvider;

  private final Provider<PreviewEnqueuer> previewEnqueuerProvider;

  private final Provider<TaskReminderScheduler> taskReminderSchedulerProvider;

  public CaptureRepository_Factory(Provider<ActionItemDao> actionItemDaoProvider,
      Provider<PreviewEnqueuer> previewEnqueuerProvider,
      Provider<TaskReminderScheduler> taskReminderSchedulerProvider) {
    this.actionItemDaoProvider = actionItemDaoProvider;
    this.previewEnqueuerProvider = previewEnqueuerProvider;
    this.taskReminderSchedulerProvider = taskReminderSchedulerProvider;
  }

  @Override
  public CaptureRepository get() {
    return newInstance(actionItemDaoProvider.get(), previewEnqueuerProvider.get(), taskReminderSchedulerProvider.get());
  }

  public static CaptureRepository_Factory create(Provider<ActionItemDao> actionItemDaoProvider,
      Provider<PreviewEnqueuer> previewEnqueuerProvider,
      Provider<TaskReminderScheduler> taskReminderSchedulerProvider) {
    return new CaptureRepository_Factory(actionItemDaoProvider, previewEnqueuerProvider, taskReminderSchedulerProvider);
  }

  public static CaptureRepository newInstance(ActionItemDao actionItemDao,
      PreviewEnqueuer previewEnqueuer, TaskReminderScheduler taskReminderScheduler) {
    return new CaptureRepository(actionItemDao, previewEnqueuer, taskReminderScheduler);
  }
}
