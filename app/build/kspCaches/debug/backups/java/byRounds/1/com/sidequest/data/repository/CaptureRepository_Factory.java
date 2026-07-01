package com.sidequest.data.repository;

import android.content.Context;
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
public final class CaptureRepository_Factory implements Factory<CaptureRepository> {
  private final Provider<ActionItemDao> actionItemDaoProvider;

  private final Provider<PreviewEnqueuer> previewEnqueuerProvider;

  private final Provider<TaskReminderScheduler> taskReminderSchedulerProvider;

  private final Provider<Context> contextProvider;

  public CaptureRepository_Factory(Provider<ActionItemDao> actionItemDaoProvider,
      Provider<PreviewEnqueuer> previewEnqueuerProvider,
      Provider<TaskReminderScheduler> taskReminderSchedulerProvider,
      Provider<Context> contextProvider) {
    this.actionItemDaoProvider = actionItemDaoProvider;
    this.previewEnqueuerProvider = previewEnqueuerProvider;
    this.taskReminderSchedulerProvider = taskReminderSchedulerProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public CaptureRepository get() {
    return newInstance(actionItemDaoProvider.get(), previewEnqueuerProvider.get(), taskReminderSchedulerProvider.get(), contextProvider.get());
  }

  public static CaptureRepository_Factory create(Provider<ActionItemDao> actionItemDaoProvider,
      Provider<PreviewEnqueuer> previewEnqueuerProvider,
      Provider<TaskReminderScheduler> taskReminderSchedulerProvider,
      Provider<Context> contextProvider) {
    return new CaptureRepository_Factory(actionItemDaoProvider, previewEnqueuerProvider, taskReminderSchedulerProvider, contextProvider);
  }

  public static CaptureRepository newInstance(ActionItemDao actionItemDao,
      PreviewEnqueuer previewEnqueuer, TaskReminderScheduler taskReminderScheduler,
      Context context) {
    return new CaptureRepository(actionItemDao, previewEnqueuer, taskReminderScheduler, context);
  }
}
