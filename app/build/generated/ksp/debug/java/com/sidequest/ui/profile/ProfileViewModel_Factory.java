package com.sidequest.ui.profile;

import android.content.Context;
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
public final class ProfileViewModel_Factory implements Factory<ProfileViewModel> {
  private final Provider<UserPreferences> userPreferencesProvider;

  private final Provider<Context> appContextProvider;

  public ProfileViewModel_Factory(Provider<UserPreferences> userPreferencesProvider,
      Provider<Context> appContextProvider) {
    this.userPreferencesProvider = userPreferencesProvider;
    this.appContextProvider = appContextProvider;
  }

  @Override
  public ProfileViewModel get() {
    return newInstance(userPreferencesProvider.get(), appContextProvider.get());
  }

  public static ProfileViewModel_Factory create(Provider<UserPreferences> userPreferencesProvider,
      Provider<Context> appContextProvider) {
    return new ProfileViewModel_Factory(userPreferencesProvider, appContextProvider);
  }

  public static ProfileViewModel newInstance(UserPreferences userPreferences, Context appContext) {
    return new ProfileViewModel(userPreferences, appContext);
  }
}
