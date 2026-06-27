package com.sidequest.data.auth;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("com.sidequest.data.auth.AuthenticatedClient")
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
public final class AuthModule_Companion_ProvideAuthenticatedClientFactory implements Factory<OkHttpClient> {
  private final Provider<AuthHeaderInterceptor> authHeaderInterceptorProvider;

  private final Provider<TokenAuthenticator> tokenAuthenticatorProvider;

  public AuthModule_Companion_ProvideAuthenticatedClientFactory(
      Provider<AuthHeaderInterceptor> authHeaderInterceptorProvider,
      Provider<TokenAuthenticator> tokenAuthenticatorProvider) {
    this.authHeaderInterceptorProvider = authHeaderInterceptorProvider;
    this.tokenAuthenticatorProvider = tokenAuthenticatorProvider;
  }

  @Override
  public OkHttpClient get() {
    return provideAuthenticatedClient(authHeaderInterceptorProvider.get(), tokenAuthenticatorProvider.get());
  }

  public static AuthModule_Companion_ProvideAuthenticatedClientFactory create(
      Provider<AuthHeaderInterceptor> authHeaderInterceptorProvider,
      Provider<TokenAuthenticator> tokenAuthenticatorProvider) {
    return new AuthModule_Companion_ProvideAuthenticatedClientFactory(authHeaderInterceptorProvider, tokenAuthenticatorProvider);
  }

  public static OkHttpClient provideAuthenticatedClient(AuthHeaderInterceptor authHeaderInterceptor,
      TokenAuthenticator tokenAuthenticator) {
    return Preconditions.checkNotNullFromProvides(AuthModule.Companion.provideAuthenticatedClient(authHeaderInterceptor, tokenAuthenticator));
  }
}
