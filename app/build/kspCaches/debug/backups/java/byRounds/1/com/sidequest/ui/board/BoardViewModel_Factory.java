package com.sidequest.ui.board;

import com.sidequest.data.repository.BoardRepository;
import com.sidequest.ui.capture.CurrentAccountProvider;
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
public final class BoardViewModel_Factory implements Factory<BoardViewModel> {
  private final Provider<BoardRepository> boardRepositoryProvider;

  private final Provider<CurrentAccountProvider> accountProvider;

  public BoardViewModel_Factory(Provider<BoardRepository> boardRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider) {
    this.boardRepositoryProvider = boardRepositoryProvider;
    this.accountProvider = accountProvider;
  }

  @Override
  public BoardViewModel get() {
    return newInstance(boardRepositoryProvider.get(), accountProvider.get());
  }

  public static BoardViewModel_Factory create(Provider<BoardRepository> boardRepositoryProvider,
      Provider<CurrentAccountProvider> accountProvider) {
    return new BoardViewModel_Factory(boardRepositoryProvider, accountProvider);
  }

  public static BoardViewModel newInstance(BoardRepository boardRepository,
      CurrentAccountProvider accountProvider) {
    return new BoardViewModel(boardRepository, accountProvider);
  }
}
