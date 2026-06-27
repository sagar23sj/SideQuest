package com.sidequest;

import androidx.hilt.work.HiltWorkerFactory;
import com.sidequest.data.seed.PreviewSeeder;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class SideQuestApp_MembersInjector implements MembersInjector<SideQuestApp> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  private final Provider<PreviewSeeder> previewSeederProvider;

  public SideQuestApp_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider,
      Provider<PreviewSeeder> previewSeederProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
    this.previewSeederProvider = previewSeederProvider;
  }

  public static MembersInjector<SideQuestApp> create(
      Provider<HiltWorkerFactory> workerFactoryProvider,
      Provider<PreviewSeeder> previewSeederProvider) {
    return new SideQuestApp_MembersInjector(workerFactoryProvider, previewSeederProvider);
  }

  @Override
  public void injectMembers(SideQuestApp instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
    injectPreviewSeeder(instance, previewSeederProvider.get());
  }

  @InjectedFieldSignature("com.sidequest.SideQuestApp.workerFactory")
  public static void injectWorkerFactory(SideQuestApp instance, HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }

  @InjectedFieldSignature("com.sidequest.SideQuestApp.previewSeeder")
  public static void injectPreviewSeeder(SideQuestApp instance, PreviewSeeder previewSeeder) {
    instance.previewSeeder = previewSeeder;
  }
}
