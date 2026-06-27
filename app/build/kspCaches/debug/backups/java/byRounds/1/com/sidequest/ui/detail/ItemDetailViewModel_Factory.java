package com.sidequest.ui.detail;

import androidx.lifecycle.SavedStateHandle;
import com.sidequest.data.local.dao.ActionItemDao;
import com.sidequest.data.repository.ActionPlanRepository;
import com.sidequest.data.repository.BoardRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ItemDetailViewModel_Factory implements Factory<ItemDetailViewModel> {
  private final Provider<ActionPlanRepository> planRepositoryProvider;

  private final Provider<BoardRepository> boardRepositoryProvider;

  private final Provider<ActionItemDao> actionItemDaoProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public ItemDetailViewModel_Factory(Provider<ActionPlanRepository> planRepositoryProvider,
      Provider<BoardRepository> boardRepositoryProvider,
      Provider<ActionItemDao> actionItemDaoProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.planRepositoryProvider = planRepositoryProvider;
    this.boardRepositoryProvider = boardRepositoryProvider;
    this.actionItemDaoProvider = actionItemDaoProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public ItemDetailViewModel get() {
    return newInstance(planRepositoryProvider.get(), boardRepositoryProvider.get(), actionItemDaoProvider.get(), savedStateHandleProvider.get());
  }

  public static ItemDetailViewModel_Factory create(
      Provider<ActionPlanRepository> planRepositoryProvider,
      Provider<BoardRepository> boardRepositoryProvider,
      Provider<ActionItemDao> actionItemDaoProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new ItemDetailViewModel_Factory(planRepositoryProvider, boardRepositoryProvider, actionItemDaoProvider, savedStateHandleProvider);
  }

  public static ItemDetailViewModel newInstance(ActionPlanRepository planRepository,
      BoardRepository boardRepository, ActionItemDao actionItemDao,
      SavedStateHandle savedStateHandle) {
    return new ItemDetailViewModel(planRepository, boardRepository, actionItemDao, savedStateHandle);
  }
}
