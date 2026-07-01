package com.sidequest.ui.capture;

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
public final class ShareTargetActivity_MembersInjector implements MembersInjector<ShareTargetActivity> {
  private final Provider<UserPreferences> userPreferencesProvider;

  public ShareTargetActivity_MembersInjector(Provider<UserPreferences> userPreferencesProvider) {
    this.userPreferencesProvider = userPreferencesProvider;
  }

  public static MembersInjector<ShareTargetActivity> create(
      Provider<UserPreferences> userPreferencesProvider) {
    return new ShareTargetActivity_MembersInjector(userPreferencesProvider);
  }

  @Override
  public void injectMembers(ShareTargetActivity instance) {
    injectUserPreferences(instance, userPreferencesProvider.get());
  }

  @InjectedFieldSignature("com.sidequest.ui.capture.ShareTargetActivity.userPreferences")
  public static void injectUserPreferences(ShareTargetActivity instance,
      UserPreferences userPreferences) {
    instance.userPreferences = userPreferences;
  }
}
