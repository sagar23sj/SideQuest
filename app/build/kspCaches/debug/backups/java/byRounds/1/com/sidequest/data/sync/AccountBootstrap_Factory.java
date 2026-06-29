package com.sidequest.data.sync;

import com.sidequest.data.auth.AuthApi;
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
public final class AccountBootstrap_Factory implements Factory<AccountBootstrap> {
  private final Provider<TokenStore> tokenStoreProvider;

  private final Provider<AuthApi> authApiProvider;

  private final Provider<DeviceIdentity> deviceIdentityProvider;

  public AccountBootstrap_Factory(Provider<TokenStore> tokenStoreProvider,
      Provider<AuthApi> authApiProvider, Provider<DeviceIdentity> deviceIdentityProvider) {
    this.tokenStoreProvider = tokenStoreProvider;
    this.authApiProvider = authApiProvider;
    this.deviceIdentityProvider = deviceIdentityProvider;
  }

  @Override
  public AccountBootstrap get() {
    return newInstance(tokenStoreProvider.get(), authApiProvider.get(), deviceIdentityProvider.get());
  }

  public static AccountBootstrap_Factory create(Provider<TokenStore> tokenStoreProvider,
      Provider<AuthApi> authApiProvider, Provider<DeviceIdentity> deviceIdentityProvider) {
    return new AccountBootstrap_Factory(tokenStoreProvider, authApiProvider, deviceIdentityProvider);
  }

  public static AccountBootstrap newInstance(TokenStore tokenStore, AuthApi authApi,
      DeviceIdentity deviceIdentity) {
    return new AccountBootstrap(tokenStore, authApi, deviceIdentity);
  }
}
