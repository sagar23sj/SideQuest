package com.sidequest.data.preview;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.sidequest.data.local.dao.ActionItemDao;
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
public final class PreviewFetchWorker_Factory {
  private final Provider<PreviewService> previewServiceProvider;

  private final Provider<ActionItemDao> actionItemDaoProvider;

  public PreviewFetchWorker_Factory(Provider<PreviewService> previewServiceProvider,
      Provider<ActionItemDao> actionItemDaoProvider) {
    this.previewServiceProvider = previewServiceProvider;
    this.actionItemDaoProvider = actionItemDaoProvider;
  }

  public PreviewFetchWorker get(Context appContext, WorkerParameters params) {
    return newInstance(appContext, params, previewServiceProvider.get(), actionItemDaoProvider.get());
  }

  public static PreviewFetchWorker_Factory create(Provider<PreviewService> previewServiceProvider,
      Provider<ActionItemDao> actionItemDaoProvider) {
    return new PreviewFetchWorker_Factory(previewServiceProvider, actionItemDaoProvider);
  }

  public static PreviewFetchWorker newInstance(Context appContext, WorkerParameters params,
      PreviewService previewService, ActionItemDao actionItemDao) {
    return new PreviewFetchWorker(appContext, params, previewService, actionItemDao);
  }
}
