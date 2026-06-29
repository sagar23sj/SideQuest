package com.sidequest.data.sync;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("com.sidequest.data.auth.AuthenticatedClient")
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
public final class SyncModule_ProvideBackupApiFactory implements Factory<BackupApi> {
  private final Provider<OkHttpClient> clientProvider;

  public SyncModule_ProvideBackupApiFactory(Provider<OkHttpClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public BackupApi get() {
    return provideBackupApi(clientProvider.get());
  }

  public static SyncModule_ProvideBackupApiFactory create(Provider<OkHttpClient> clientProvider) {
    return new SyncModule_ProvideBackupApiFactory(clientProvider);
  }

  public static BackupApi provideBackupApi(OkHttpClient client) {
    return Preconditions.checkNotNullFromProvides(SyncModule.INSTANCE.provideBackupApi(client));
  }
}
