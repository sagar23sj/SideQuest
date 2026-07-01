package com.sidequest.ui.auth;

import android.content.Context;
import com.sidequest.data.auth.AuthApi;
import com.sidequest.data.auth.TokenStore;
import com.sidequest.data.local.UserPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class LoginViewModel_Factory implements Factory<LoginViewModel> {
  private final Provider<AuthApi> authApiProvider;

  private final Provider<TokenStore> tokenStoreProvider;

  private final Provider<UserPreferences> userPreferencesProvider;

  private final Provider<Context> appContextProvider;

  public LoginViewModel_Factory(Provider<AuthApi> authApiProvider,
      Provider<TokenStore> tokenStoreProvider, Provider<UserPreferences> userPreferencesProvider,
      Provider<Context> appContextProvider) {
    this.authApiProvider = authApiProvider;
    this.tokenStoreProvider = tokenStoreProvider;
    this.userPreferencesProvider = userPreferencesProvider;
    this.appContextProvider = appContextProvider;
  }

  @Override
  public LoginViewModel get() {
    return newInstance(authApiProvider.get(), tokenStoreProvider.get(), userPreferencesProvider.get(), appContextProvider.get());
  }

  public static LoginViewModel_Factory create(Provider<AuthApi> authApiProvider,
      Provider<TokenStore> tokenStoreProvider, Provider<UserPreferences> userPreferencesProvider,
      Provider<Context> appContextProvider) {
    return new LoginViewModel_Factory(authApiProvider, tokenStoreProvider, userPreferencesProvider, appContextProvider);
  }

  public static LoginViewModel newInstance(AuthApi authApi, TokenStore tokenStore,
      UserPreferences userPreferences, Context appContext) {
    return new LoginViewModel(authApi, tokenStore, userPreferences, appContext);
  }
}
