package com.sidequest.data.sync;

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
public final class BackupWorker_AssistedFactory_Impl implements BackupWorker_AssistedFactory {
  private final BackupWorker_Factory delegateFactory;

  BackupWorker_AssistedFactory_Impl(BackupWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public BackupWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<BackupWorker_AssistedFactory> create(
      BackupWorker_Factory delegateFactory) {
    return InstanceFactory.create(new BackupWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<BackupWorker_AssistedFactory> createFactoryProvider(
      BackupWorker_Factory delegateFactory) {
    return InstanceFactory.create(new BackupWorker_AssistedFactory_Impl(delegateFactory));
  }
}
