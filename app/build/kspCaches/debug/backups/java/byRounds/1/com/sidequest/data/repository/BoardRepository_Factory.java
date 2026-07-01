package com.sidequest.data.repository;

import com.sidequest.data.local.dao.ActionItemDao;
import com.sidequest.data.local.dao.BucketDao;
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
public final class BoardRepository_Factory implements Factory<BoardRepository> {
  private final Provider<ActionItemDao> actionItemDaoProvider;

  private final Provider<BucketDao> bucketDaoProvider;

  private final Provider<TaskReminderScheduler> taskReminderSchedulerProvider;

  private final Provider<PreviewEnqueuer> previewEnqueuerProvider;

  public BoardRepository_Factory(Provider<ActionItemDao> actionItemDaoProvider,
      Provider<BucketDao> bucketDaoProvider,
      Provider<TaskReminderScheduler> taskReminderSchedulerProvider,
      Provider<PreviewEnqueuer> previewEnqueuerProvider) {
    this.actionItemDaoProvider = actionItemDaoProvider;
    this.bucketDaoProvider = bucketDaoProvider;
    this.taskReminderSchedulerProvider = taskReminderSchedulerProvider;
    this.previewEnqueuerProvider = previewEnqueuerProvider;
  }

  @Override
  public BoardRepository get() {
    return newInstance(actionItemDaoProvider.get(), bucketDaoProvider.get(), taskReminderSchedulerProvider.get(), previewEnqueuerProvider.get());
  }

  public static BoardRepository_Factory create(Provider<ActionItemDao> actionItemDaoProvider,
      Provider<BucketDao> bucketDaoProvider,
      Provider<TaskReminderScheduler> taskReminderSchedulerProvider,
      Provider<PreviewEnqueuer> previewEnqueuerProvider) {
    return new BoardRepository_Factory(actionItemDaoProvider, bucketDaoProvider, taskReminderSchedulerProvider, previewEnqueuerProvider);
  }

  public static BoardRepository newInstance(ActionItemDao actionItemDao, BucketDao bucketDao,
      TaskReminderScheduler taskReminderScheduler, PreviewEnqueuer previewEnqueuer) {
    return new BoardRepository(actionItemDao, bucketDao, taskReminderScheduler, previewEnqueuer);
  }
}
