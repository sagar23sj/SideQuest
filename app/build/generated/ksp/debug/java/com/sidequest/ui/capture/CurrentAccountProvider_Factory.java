package com.sidequest.ui.capture;

import com.sidequest.data.auth.TokenStore;
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
public final class CurrentAccountProvider_Factory implements Factory<CurrentAccountProvider> {
  private final Provider<TokenStore> tokenStoreProvider;

  public CurrentAccountProvider_Factory(Provider<TokenStore> tokenStoreProvider) {
    this.tokenStoreProvider = tokenStoreProvider;
  }

  @Override
  public CurrentAccountProvider get() {
    return newInstance(tokenStoreProvider.get());
  }

  public static CurrentAccountProvider_Factory create(Provider<TokenStore> tokenStoreProvider) {
    return new CurrentAccountProvider_Factory(tokenStoreProvider);
  }

  public static CurrentAccountProvider newInstance(TokenStore tokenStore) {
    return new CurrentAccountProvider(tokenStore);
  }
}
