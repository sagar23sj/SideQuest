package com.sidequest.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.sidequest.data.local.converters.Converters;
import com.sidequest.data.local.entity.VoiceJournalEntryEntity;
import com.sidequest.domain.model.SyncMeta;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class VoiceJournalDao_Impl implements VoiceJournalDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<VoiceJournalEntryEntity> __insertionAdapterOfVoiceJournalEntryEntity;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<VoiceJournalEntryEntity> __deletionAdapterOfVoiceJournalEntryEntity;

  private final EntityDeletionOrUpdateAdapter<VoiceJournalEntryEntity> __updateAdapterOfVoiceJournalEntryEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfClear;

  private final EntityUpsertionAdapter<VoiceJournalEntryEntity> __upsertionAdapterOfVoiceJournalEntryEntity;

  public VoiceJournalDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfVoiceJournalEntryEntity = new EntityInsertionAdapter<VoiceJournalEntryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `voice_journal_entries` (`id`,`accountId`,`audioRef`,`transcript`,`transcriptionFailed`,`createdAt`,`extractedActionItemIds`,`updatedAt`,`version`,`deleted`,`dirty`) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final VoiceJournalEntryEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getAudioRef());
        if (entity.getTranscript() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getTranscript());
        }
        final int _tmp = entity.getTranscriptionFailed() ? 1 : 0;
        statement.bindLong(5, _tmp);
        statement.bindLong(6, entity.getCreatedAt());
        final String _tmp_1 = __converters.fromStringList(entity.getExtractedActionItemIds());
        statement.bindString(7, _tmp_1);
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(8, _tmpSync.getUpdatedAt());
        statement.bindLong(9, _tmpSync.getVersion());
        final int _tmp_2 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        final int _tmp_3 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(11, _tmp_3);
      }
    };
    this.__deletionAdapterOfVoiceJournalEntryEntity = new EntityDeletionOrUpdateAdapter<VoiceJournalEntryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `voice_journal_entries` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final VoiceJournalEntryEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__updateAdapterOfVoiceJournalEntryEntity = new EntityDeletionOrUpdateAdapter<VoiceJournalEntryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `voice_journal_entries` SET `id` = ?,`accountId` = ?,`audioRef` = ?,`transcript` = ?,`transcriptionFailed` = ?,`createdAt` = ?,`extractedActionItemIds` = ?,`updatedAt` = ?,`version` = ?,`deleted` = ?,`dirty` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final VoiceJournalEntryEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getAudioRef());
        if (entity.getTranscript() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getTranscript());
        }
        final int _tmp = entity.getTranscriptionFailed() ? 1 : 0;
        statement.bindLong(5, _tmp);
        statement.bindLong(6, entity.getCreatedAt());
        final String _tmp_1 = __converters.fromStringList(entity.getExtractedActionItemIds());
        statement.bindString(7, _tmp_1);
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(8, _tmpSync.getUpdatedAt());
        statement.bindLong(9, _tmpSync.getVersion());
        final int _tmp_2 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        final int _tmp_3 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(11, _tmp_3);
        statement.bindString(12, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM voice_journal_entries WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClear = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM voice_journal_entries";
        return _query;
      }
    };
    this.__upsertionAdapterOfVoiceJournalEntryEntity = new EntityUpsertionAdapter<VoiceJournalEntryEntity>(new EntityInsertionAdapter<VoiceJournalEntryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `voice_journal_entries` (`id`,`accountId`,`audioRef`,`transcript`,`transcriptionFailed`,`createdAt`,`extractedActionItemIds`,`updatedAt`,`version`,`deleted`,`dirty`) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final VoiceJournalEntryEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getAudioRef());
        if (entity.getTranscript() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getTranscript());
        }
        final int _tmp = entity.getTranscriptionFailed() ? 1 : 0;
        statement.bindLong(5, _tmp);
        statement.bindLong(6, entity.getCreatedAt());
        final String _tmp_1 = __converters.fromStringList(entity.getExtractedActionItemIds());
        statement.bindString(7, _tmp_1);
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(8, _tmpSync.getUpdatedAt());
        statement.bindLong(9, _tmpSync.getVersion());
        final int _tmp_2 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        final int _tmp_3 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(11, _tmp_3);
      }
    }, new EntityDeletionOrUpdateAdapter<VoiceJournalEntryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `voice_journal_entries` SET `id` = ?,`accountId` = ?,`audioRef` = ?,`transcript` = ?,`transcriptionFailed` = ?,`createdAt` = ?,`extractedActionItemIds` = ?,`updatedAt` = ?,`version` = ?,`deleted` = ?,`dirty` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final VoiceJournalEntryEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getAudioRef());
        if (entity.getTranscript() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getTranscript());
        }
        final int _tmp = entity.getTranscriptionFailed() ? 1 : 0;
        statement.bindLong(5, _tmp);
        statement.bindLong(6, entity.getCreatedAt());
        final String _tmp_1 = __converters.fromStringList(entity.getExtractedActionItemIds());
        statement.bindString(7, _tmp_1);
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(8, _tmpSync.getUpdatedAt());
        statement.bindLong(9, _tmpSync.getVersion());
        final int _tmp_2 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        final int _tmp_3 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(11, _tmp_3);
        statement.bindString(12, entity.getId());
      }
    });
  }

  @Override
  public Object insert(final VoiceJournalEntryEntity entry,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfVoiceJournalEntryEntity.insert(entry);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<VoiceJournalEntryEntity> entries,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfVoiceJournalEntryEntity.insert(entries);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final VoiceJournalEntryEntity entry,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfVoiceJournalEntryEntity.handle(entry);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final VoiceJournalEntryEntity entry,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfVoiceJournalEntryEntity.handle(entry);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clear(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClear.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClear.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final VoiceJournalEntryEntity entry,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfVoiceJournalEntryEntity.upsert(entry);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertAll(final List<VoiceJournalEntryEntity> entries,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfVoiceJournalEntryEntity.upsert(entries);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<VoiceJournalEntryEntity>> observeAll() {
    final String _sql = "SELECT * FROM voice_journal_entries WHERE deleted = 0 ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"voice_journal_entries"}, new Callable<List<VoiceJournalEntryEntity>>() {
      @Override
      @NonNull
      public List<VoiceJournalEntryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfAudioRef = CursorUtil.getColumnIndexOrThrow(_cursor, "audioRef");
          final int _cursorIndexOfTranscript = CursorUtil.getColumnIndexOrThrow(_cursor, "transcript");
          final int _cursorIndexOfTranscriptionFailed = CursorUtil.getColumnIndexOrThrow(_cursor, "transcriptionFailed");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfExtractedActionItemIds = CursorUtil.getColumnIndexOrThrow(_cursor, "extractedActionItemIds");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<VoiceJournalEntryEntity> _result = new ArrayList<VoiceJournalEntryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final VoiceJournalEntryEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpAudioRef;
            _tmpAudioRef = _cursor.getString(_cursorIndexOfAudioRef);
            final String _tmpTranscript;
            if (_cursor.isNull(_cursorIndexOfTranscript)) {
              _tmpTranscript = null;
            } else {
              _tmpTranscript = _cursor.getString(_cursorIndexOfTranscript);
            }
            final boolean _tmpTranscriptionFailed;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfTranscriptionFailed);
            _tmpTranscriptionFailed = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final List<String> _tmpExtractedActionItemIds;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfExtractedActionItemIds);
            _tmpExtractedActionItemIds = __converters.toStringList(_tmp_1);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_2 != 0;
            final boolean _tmpDirty;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_3 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new VoiceJournalEntryEntity(_tmpId,_tmpAccountId,_tmpAudioRef,_tmpTranscript,_tmpTranscriptionFailed,_tmpCreatedAt,_tmpExtractedActionItemIds,_tmpSync);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<VoiceJournalEntryEntity>> observeByAccount(final String accountId) {
    final String _sql = "SELECT * FROM voice_journal_entries WHERE accountId = ? AND deleted = 0 ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, accountId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"voice_journal_entries"}, new Callable<List<VoiceJournalEntryEntity>>() {
      @Override
      @NonNull
      public List<VoiceJournalEntryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfAudioRef = CursorUtil.getColumnIndexOrThrow(_cursor, "audioRef");
          final int _cursorIndexOfTranscript = CursorUtil.getColumnIndexOrThrow(_cursor, "transcript");
          final int _cursorIndexOfTranscriptionFailed = CursorUtil.getColumnIndexOrThrow(_cursor, "transcriptionFailed");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfExtractedActionItemIds = CursorUtil.getColumnIndexOrThrow(_cursor, "extractedActionItemIds");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<VoiceJournalEntryEntity> _result = new ArrayList<VoiceJournalEntryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final VoiceJournalEntryEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpAudioRef;
            _tmpAudioRef = _cursor.getString(_cursorIndexOfAudioRef);
            final String _tmpTranscript;
            if (_cursor.isNull(_cursorIndexOfTranscript)) {
              _tmpTranscript = null;
            } else {
              _tmpTranscript = _cursor.getString(_cursorIndexOfTranscript);
            }
            final boolean _tmpTranscriptionFailed;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfTranscriptionFailed);
            _tmpTranscriptionFailed = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final List<String> _tmpExtractedActionItemIds;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfExtractedActionItemIds);
            _tmpExtractedActionItemIds = __converters.toStringList(_tmp_1);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_2 != 0;
            final boolean _tmpDirty;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_3 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new VoiceJournalEntryEntity(_tmpId,_tmpAccountId,_tmpAudioRef,_tmpTranscript,_tmpTranscriptionFailed,_tmpCreatedAt,_tmpExtractedActionItemIds,_tmpSync);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<VoiceJournalEntryEntity> observeById(final String id) {
    final String _sql = "SELECT * FROM voice_journal_entries WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"voice_journal_entries"}, new Callable<VoiceJournalEntryEntity>() {
      @Override
      @Nullable
      public VoiceJournalEntryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfAudioRef = CursorUtil.getColumnIndexOrThrow(_cursor, "audioRef");
          final int _cursorIndexOfTranscript = CursorUtil.getColumnIndexOrThrow(_cursor, "transcript");
          final int _cursorIndexOfTranscriptionFailed = CursorUtil.getColumnIndexOrThrow(_cursor, "transcriptionFailed");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfExtractedActionItemIds = CursorUtil.getColumnIndexOrThrow(_cursor, "extractedActionItemIds");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final VoiceJournalEntryEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpAudioRef;
            _tmpAudioRef = _cursor.getString(_cursorIndexOfAudioRef);
            final String _tmpTranscript;
            if (_cursor.isNull(_cursorIndexOfTranscript)) {
              _tmpTranscript = null;
            } else {
              _tmpTranscript = _cursor.getString(_cursorIndexOfTranscript);
            }
            final boolean _tmpTranscriptionFailed;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfTranscriptionFailed);
            _tmpTranscriptionFailed = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final List<String> _tmpExtractedActionItemIds;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfExtractedActionItemIds);
            _tmpExtractedActionItemIds = __converters.toStringList(_tmp_1);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_2 != 0;
            final boolean _tmpDirty;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_3 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _result = new VoiceJournalEntryEntity(_tmpId,_tmpAccountId,_tmpAudioRef,_tmpTranscript,_tmpTranscriptionFailed,_tmpCreatedAt,_tmpExtractedActionItemIds,_tmpSync);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getById(final String id,
      final Continuation<? super VoiceJournalEntryEntity> $completion) {
    final String _sql = "SELECT * FROM voice_journal_entries WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<VoiceJournalEntryEntity>() {
      @Override
      @Nullable
      public VoiceJournalEntryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfAudioRef = CursorUtil.getColumnIndexOrThrow(_cursor, "audioRef");
          final int _cursorIndexOfTranscript = CursorUtil.getColumnIndexOrThrow(_cursor, "transcript");
          final int _cursorIndexOfTranscriptionFailed = CursorUtil.getColumnIndexOrThrow(_cursor, "transcriptionFailed");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfExtractedActionItemIds = CursorUtil.getColumnIndexOrThrow(_cursor, "extractedActionItemIds");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final VoiceJournalEntryEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpAudioRef;
            _tmpAudioRef = _cursor.getString(_cursorIndexOfAudioRef);
            final String _tmpTranscript;
            if (_cursor.isNull(_cursorIndexOfTranscript)) {
              _tmpTranscript = null;
            } else {
              _tmpTranscript = _cursor.getString(_cursorIndexOfTranscript);
            }
            final boolean _tmpTranscriptionFailed;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfTranscriptionFailed);
            _tmpTranscriptionFailed = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final List<String> _tmpExtractedActionItemIds;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfExtractedActionItemIds);
            _tmpExtractedActionItemIds = __converters.toStringList(_tmp_1);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_2 != 0;
            final boolean _tmpDirty;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_3 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _result = new VoiceJournalEntryEntity(_tmpId,_tmpAccountId,_tmpAudioRef,_tmpTranscript,_tmpTranscriptionFailed,_tmpCreatedAt,_tmpExtractedActionItemIds,_tmpSync);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getByAccount(final String accountId,
      final Continuation<? super List<VoiceJournalEntryEntity>> $completion) {
    final String _sql = "SELECT * FROM voice_journal_entries WHERE accountId = ? AND deleted = 0 ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, accountId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<VoiceJournalEntryEntity>>() {
      @Override
      @NonNull
      public List<VoiceJournalEntryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfAudioRef = CursorUtil.getColumnIndexOrThrow(_cursor, "audioRef");
          final int _cursorIndexOfTranscript = CursorUtil.getColumnIndexOrThrow(_cursor, "transcript");
          final int _cursorIndexOfTranscriptionFailed = CursorUtil.getColumnIndexOrThrow(_cursor, "transcriptionFailed");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfExtractedActionItemIds = CursorUtil.getColumnIndexOrThrow(_cursor, "extractedActionItemIds");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<VoiceJournalEntryEntity> _result = new ArrayList<VoiceJournalEntryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final VoiceJournalEntryEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpAudioRef;
            _tmpAudioRef = _cursor.getString(_cursorIndexOfAudioRef);
            final String _tmpTranscript;
            if (_cursor.isNull(_cursorIndexOfTranscript)) {
              _tmpTranscript = null;
            } else {
              _tmpTranscript = _cursor.getString(_cursorIndexOfTranscript);
            }
            final boolean _tmpTranscriptionFailed;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfTranscriptionFailed);
            _tmpTranscriptionFailed = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final List<String> _tmpExtractedActionItemIds;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfExtractedActionItemIds);
            _tmpExtractedActionItemIds = __converters.toStringList(_tmp_1);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_2 != 0;
            final boolean _tmpDirty;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_3 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new VoiceJournalEntryEntity(_tmpId,_tmpAccountId,_tmpAudioRef,_tmpTranscript,_tmpTranscriptionFailed,_tmpCreatedAt,_tmpExtractedActionItemIds,_tmpSync);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAll(final Continuation<? super List<VoiceJournalEntryEntity>> $completion) {
    final String _sql = "SELECT * FROM voice_journal_entries ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<VoiceJournalEntryEntity>>() {
      @Override
      @NonNull
      public List<VoiceJournalEntryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfAudioRef = CursorUtil.getColumnIndexOrThrow(_cursor, "audioRef");
          final int _cursorIndexOfTranscript = CursorUtil.getColumnIndexOrThrow(_cursor, "transcript");
          final int _cursorIndexOfTranscriptionFailed = CursorUtil.getColumnIndexOrThrow(_cursor, "transcriptionFailed");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfExtractedActionItemIds = CursorUtil.getColumnIndexOrThrow(_cursor, "extractedActionItemIds");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<VoiceJournalEntryEntity> _result = new ArrayList<VoiceJournalEntryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final VoiceJournalEntryEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpAudioRef;
            _tmpAudioRef = _cursor.getString(_cursorIndexOfAudioRef);
            final String _tmpTranscript;
            if (_cursor.isNull(_cursorIndexOfTranscript)) {
              _tmpTranscript = null;
            } else {
              _tmpTranscript = _cursor.getString(_cursorIndexOfTranscript);
            }
            final boolean _tmpTranscriptionFailed;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfTranscriptionFailed);
            _tmpTranscriptionFailed = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final List<String> _tmpExtractedActionItemIds;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfExtractedActionItemIds);
            _tmpExtractedActionItemIds = __converters.toStringList(_tmp_1);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_2 != 0;
            final boolean _tmpDirty;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_3 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new VoiceJournalEntryEntity(_tmpId,_tmpAccountId,_tmpAudioRef,_tmpTranscript,_tmpTranscriptionFailed,_tmpCreatedAt,_tmpExtractedActionItemIds,_tmpSync);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
