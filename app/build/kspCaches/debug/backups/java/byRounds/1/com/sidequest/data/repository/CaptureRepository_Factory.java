package com.sidequest.data.repository;

import com.sidequest.data.local.dao.ActionItemDao;
import com.sidequest.data.preview.PreviewEnqueuer;
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

  public CaptureRepository_Factory(Provider<ActionItemDao> actionItemDaoProvider,
      Provider<PreviewEnqueuer> previewEnqueuerProvider) {
    this.actionItemDaoProvider = actionItemDaoProvider;
    this.previewEnqueuerProvider = previewEnqueuerProvider;
  }

  @Override
  public CaptureRepository get() {
    return newInstance(actionItemDaoProvider.get(), previewEnqueuerProvider.get());
  }

  public static CaptureRepository_Factory create(Provider<ActionItemDao> actionItemDaoProvider,
      Provider<PreviewEnqueuer> previewEnqueuerProvider) {
    return new CaptureRepository_Factory(actionItemDaoProvider, previewEnqueuerProvider);
  }

  public static CaptureRepository newInstance(ActionItemDao actionItemDao,
      PreviewEnqueuer previewEnqueuer) {
    return new CaptureRepository(actionItemDao, previewEnqueuer);
  }
}
