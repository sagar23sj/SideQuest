package com.sidequest.data.seed;

import com.sidequest.data.local.dao.ActionItemDao;
import com.sidequest.data.local.dao.ActionPlanDao;
import com.sidequest.data.local.dao.BucketDao;
import com.sidequest.data.local.dao.VoiceJournalDao;
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
public final class PreviewSeeder_Factory implements Factory<PreviewSeeder> {
  private final Provider<BucketDao> bucketDaoProvider;

  private final Provider<ActionItemDao> actionItemDaoProvider;

  private final Provider<ActionPlanDao> actionPlanDaoProvider;

  private final Provider<VoiceJournalDao> voiceJournalDaoProvider;

  public PreviewSeeder_Factory(Provider<BucketDao> bucketDaoProvider,
      Provider<ActionItemDao> actionItemDaoProvider, Provider<ActionPlanDao> actionPlanDaoProvider,
      Provider<VoiceJournalDao> voiceJournalDaoProvider) {
    this.bucketDaoProvider = bucketDaoProvider;
    this.actionItemDaoProvider = actionItemDaoProvider;
    this.actionPlanDaoProvider = actionPlanDaoProvider;
    this.voiceJournalDaoProvider = voiceJournalDaoProvider;
  }

  @Override
  public PreviewSeeder get() {
    return newInstance(bucketDaoProvider.get(), actionItemDaoProvider.get(), actionPlanDaoProvider.get(), voiceJournalDaoProvider.get());
  }

  public static PreviewSeeder_Factory create(Provider<BucketDao> bucketDaoProvider,
      Provider<ActionItemDao> actionItemDaoProvider, Provider<ActionPlanDao> actionPlanDaoProvider,
      Provider<VoiceJournalDao> voiceJournalDaoProvider) {
    return new PreviewSeeder_Factory(bucketDaoProvider, actionItemDaoProvider, actionPlanDaoProvider, voiceJournalDaoProvider);
  }

  public static PreviewSeeder newInstance(BucketDao bucketDao, ActionItemDao actionItemDao,
      ActionPlanDao actionPlanDao, VoiceJournalDao voiceJournalDao) {
    return new PreviewSeeder(bucketDao, actionItemDao, actionPlanDao, voiceJournalDao);
  }
}
