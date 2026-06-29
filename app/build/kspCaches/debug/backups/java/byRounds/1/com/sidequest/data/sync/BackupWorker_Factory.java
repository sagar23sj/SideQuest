package com.sidequest.data.sync;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class BackupWorker_Factory {
  private final Provider<AccountBootstrap> accountBootstrapProvider;

  private final Provider<BackupRepository> backupRepositoryProvider;

  public BackupWorker_Factory(Provider<AccountBootstrap> accountBootstrapProvider,
      Provider<BackupRepository> backupRepositoryProvider) {
    this.accountBootstrapProvider = accountBootstrapProvider;
    this.backupRepositoryProvider = backupRepositoryProvider;
  }

  public BackupWorker get(Context appContext, WorkerParameters params) {
    return newInstance(appContext, params, accountBootstrapProvider.get(), backupRepositoryProvider.get());
  }

  public static BackupWorker_Factory create(Provider<AccountBootstrap> accountBootstrapProvider,
      Provider<BackupRepository> backupRepositoryProvider) {
    return new BackupWorker_Factory(accountBootstrapProvider, backupRepositoryProvider);
  }

  public static BackupWorker newInstance(Context appContext, WorkerParameters params,
      AccountBootstrap accountBootstrap, BackupRepository backupRepository) {
    return new BackupWorker(appContext, params, accountBootstrap, backupRepository);
  }
}
