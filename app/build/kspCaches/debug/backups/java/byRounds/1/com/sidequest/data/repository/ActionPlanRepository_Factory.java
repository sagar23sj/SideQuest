package com.sidequest.data.repository;

import com.sidequest.data.local.dao.ActionPlanDao;
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
public final class ActionPlanRepository_Factory implements Factory<ActionPlanRepository> {
  private final Provider<ActionPlanDao> actionPlanDaoProvider;

  private final Provider<BoardRepository> boardRepositoryProvider;

  public ActionPlanRepository_Factory(Provider<ActionPlanDao> actionPlanDaoProvider,
      Provider<BoardRepository> boardRepositoryProvider) {
    this.actionPlanDaoProvider = actionPlanDaoProvider;
    this.boardRepositoryProvider = boardRepositoryProvider;
  }

  @Override
  public ActionPlanRepository get() {
    return newInstance(actionPlanDaoProvider.get(), boardRepositoryProvider.get());
  }

  public static ActionPlanRepository_Factory create(Provider<ActionPlanDao> actionPlanDaoProvider,
      Provider<BoardRepository> boardRepositoryProvider) {
    return new ActionPlanRepository_Factory(actionPlanDaoProvider, boardRepositoryProvider);
  }

  public static ActionPlanRepository newInstance(ActionPlanDao actionPlanDao,
      BoardRepository boardRepository) {
    return new ActionPlanRepository(actionPlanDao, boardRepository);
  }
}
