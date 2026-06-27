package com.sidequest.data.preview;

import androidx.work.WorkManager;
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
public final class WorkManagerPreviewEnqueuer_Factory implements Factory<WorkManagerPreviewEnqueuer> {
  private final Provider<WorkManager> workManagerProvider;

  public WorkManagerPreviewEnqueuer_Factory(Provider<WorkManager> workManagerProvider) {
    this.workManagerProvider = workManagerProvider;
  }

  @Override
  public WorkManagerPreviewEnqueuer get() {
    return newInstance(workManagerProvider.get());
  }

  public static WorkManagerPreviewEnqueuer_Factory create(
      Provider<WorkManager> workManagerProvider) {
    return new WorkManagerPreviewEnqueuer_Factory(workManagerProvider);
  }

  public static WorkManagerPreviewEnqueuer newInstance(WorkManager workManager) {
    return new WorkManagerPreviewEnqueuer(workManager);
  }
}
