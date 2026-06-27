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
public final class AuthModule_Companion_ProvideAuthApiFactory implements Factory<AuthApi> {
  private final Provider<OkHttpClient> clientProvider;

  public AuthModule_Companion_ProvideAuthApiFactory(Provider<OkHttpClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public AuthApi get() {
    return provideAuthApi(clientProvider.get());
  }

  public static AuthModule_Companion_ProvideAuthApiFactory create(
      Provider<OkHttpClient> clientProvider) {
    return new AuthModule_Companion_ProvideAuthApiFactory(clientProvider);
  }

  public static AuthApi provideAuthApi(OkHttpClient client) {
    return Preconditions.checkNotNullFromProvides(AuthModule.Companion.provideAuthApi(client));
  }
}
