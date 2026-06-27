package com.sidequest.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata
@QualifierMetadata("com.sidequest.di.IoDispatcher")
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
public final class DispatchersModule_ProvideIoDispatcherFactory implements Factory<CoroutineDispatcher> {
  @Override
  public CoroutineDispatcher get() {
    return provideIoDispatcher();
  }

  public static DispatchersModule_ProvideIoDispatcherFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CoroutineDispatcher provideIoDispatcher() {
    return Preconditions.checkNotNullFromProvides(DispatchersModule.INSTANCE.provideIoDispatcher());
  }

  private static final class InstanceHolder {
    private static final DispatchersModule_ProvideIoDispatcherFactory INSTANCE = new DispatchersModule_ProvideIoDispatcherFactory();
  }
}
