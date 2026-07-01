package com.sidequest;

import com.sidequest.data.local.UserPreferences;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<UserPreferences> userPreferencesProvider;

  public MainActivity_MembersInjector(Provider<UserPreferences> userPreferencesProvider) {
    this.userPreferencesProvider = userPreferencesProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<UserPreferences> userPreferencesProvider) {
    return new MainActivity_MembersInjector(userPreferencesProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectUserPreferences(instance, userPreferencesProvider.get());
  }

  @InjectedFieldSignature("com.sidequest.MainActivity.userPreferences")
  public static void injectUserPreferences(MainActivity instance, UserPreferences userPreferences) {
    instance.userPreferences = userPreferences;
  }
}
