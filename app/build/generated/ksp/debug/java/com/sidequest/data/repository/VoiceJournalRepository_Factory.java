package com.sidequest.data.repository;

import com.sidequest.data.audio.AudioRecorder;
import com.sidequest.data.llm.LlmService;
import com.sidequest.data.local.dao.ActionItemDao;
import com.sidequest.data.local.dao.VoiceJournalDao;
import com.sidequest.data.transcription.LiveTranscriber;
import com.sidequest.data.transcription.TranscriptionService;
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
public final class VoiceJournalRepository_Factory implements Factory<VoiceJournalRepository> {
  private final Provider<AudioRecorder> audioRecorderProvider;

  private final Provider<VoiceJournalDao> voiceJournalDaoProvider;

  private final Provider<TranscriptionService> transcriptionServiceProvider;

  private final Provider<LiveTranscriber> liveTranscriberProvider;

  private final Provider<LlmService> llmServiceProvider;

  private final Provider<ActionItemDao> actionItemDaoProvider;

  public VoiceJournalRepository_Factory(Provider<AudioRecorder> audioRecorderProvider,
      Provider<VoiceJournalDao> voiceJournalDaoProvider,
      Provider<TranscriptionService> transcriptionServiceProvider,
      Provider<LiveTranscriber> liveTranscriberProvider, Provider<LlmService> llmServiceProvider,
      Provider<ActionItemDao> actionItemDaoProvider) {
    this.audioRecorderProvider = audioRecorderProvider;
    this.voiceJournalDaoProvider = voiceJournalDaoProvider;
    this.transcriptionServiceProvider = transcriptionServiceProvider;
    this.liveTranscriberProvider = liveTranscriberProvider;
    this.llmServiceProvider = llmServiceProvider;
    this.actionItemDaoProvider = actionItemDaoProvider;
  }

  @Override
  public VoiceJournalRepository get() {
    return newInstance(audioRecorderProvider.get(), voiceJournalDaoProvider.get(), transcriptionServiceProvider.get(), liveTranscriberProvider.get(), llmServiceProvider.get(), actionItemDaoProvider.get());
  }

  public static VoiceJournalRepository_Factory create(Provider<AudioRecorder> audioRecorderProvider,
      Provider<VoiceJournalDao> voiceJournalDaoProvider,
      Provider<TranscriptionService> transcriptionServiceProvider,
      Provider<LiveTranscriber> liveTranscriberProvider, Provider<LlmService> llmServiceProvider,
      Provider<ActionItemDao> actionItemDaoProvider) {
    return new VoiceJournalRepository_Factory(audioRecorderProvider, voiceJournalDaoProvider, transcriptionServiceProvider, liveTranscriberProvider, llmServiceProvider, actionItemDaoProvider);
  }

  public static VoiceJournalRepository newInstance(AudioRecorder audioRecorder,
      VoiceJournalDao voiceJournalDao, TranscriptionService transcriptionService,
      LiveTranscriber liveTranscriber, LlmService llmService, ActionItemDao actionItemDao) {
    return new VoiceJournalRepository(audioRecorder, voiceJournalDao, transcriptionService, liveTranscriber, llmService, actionItemDao);
  }
}
