package com.sidequest.data.preview;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = PreviewFetchWorker.class
)
public interface PreviewFetchWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.sidequest.data.preview.PreviewFetchWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(
      PreviewFetchWorker_AssistedFactory factory);
}
