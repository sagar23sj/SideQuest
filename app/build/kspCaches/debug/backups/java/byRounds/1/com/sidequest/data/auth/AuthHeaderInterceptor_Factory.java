package com.sidequest.data.auth;

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
public final class AuthHeaderInterceptor_Factory implements Factory<AuthHeaderInterceptor> {
  private final Provider<TokenStore> tokenStoreProvider;

  public AuthHeaderInterceptor_Factory(Provider<TokenStore> tokenStoreProvider) {
    this.tokenStoreProvider = tokenStoreProvider;
  }

  @Override
  public AuthHeaderInterceptor get() {
    return newInstance(tokenStoreProvider.get());
  }

  public static AuthHeaderInterceptor_Factory create(Provider<TokenStore> tokenStoreProvider) {
    return new AuthHeaderInterceptor_Factory(tokenStoreProvider);
  }

  public static AuthHeaderInterceptor newInstance(TokenStore tokenStore) {
    return new AuthHeaderInterceptor(tokenStore);
  }
}
