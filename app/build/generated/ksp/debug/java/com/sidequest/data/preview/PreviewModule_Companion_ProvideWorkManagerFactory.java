package com.sidequest.data.preview;

import android.content.Context;
import androidx.work.WorkManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class PreviewModule_Companion_ProvideWorkManagerFactory implements Factory<WorkManager> {
  private final Provider<Context> contextProvider;

  public PreviewModule_Companion_ProvideWorkManagerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public WorkManager get() {
    return provideWorkManager(contextProvider.get());
  }

  public static PreviewModule_Companion_ProvideWorkManagerFactory create(
      Provider<Context> contextProvider) {
    return new PreviewModule_Companion_ProvideWorkManagerFactory(contextProvider);
  }

  public static WorkManager provideWorkManager(Context context) {
    return Preconditions.checkNotNullFromProvides(PreviewModule.Companion.provideWorkManager(context));
  }
}
