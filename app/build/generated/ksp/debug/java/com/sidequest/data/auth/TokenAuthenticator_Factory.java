package com.sidequest.data.auth;

import dagger.Lazy;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
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
public final class TokenAuthenticator_Factory implements Factory<TokenAuthenticator> {
  private final Provider<TokenStore> tokenStoreProvider;

  private final Provider<AuthApi> authApiProvider;

  public TokenAuthenticator_Factory(Provider<TokenStore> tokenStoreProvider,
      Provider<AuthApi> authApiProvider) {
    this.tokenStoreProvider = tokenStoreProvider;
    this.authApiProvider = authApiProvider;
  }

  @Override
  public TokenAuthenticator get() {
    return newInstance(tokenStoreProvider.get(), DoubleCheck.lazy(authApiProvider));
  }

  public static TokenAuthenticator_Factory create(Provider<TokenStore> tokenStoreProvider,
      Provider<AuthApi> authApiProvider) {
    return new TokenAuthenticator_Factory(tokenStoreProvider, authApiProvider);
  }

  public static TokenAuthenticator newInstance(TokenStore tokenStore, Lazy<AuthApi> authApi) {
    return new TokenAuthenticator(tokenStore, authApi);
  }
}
