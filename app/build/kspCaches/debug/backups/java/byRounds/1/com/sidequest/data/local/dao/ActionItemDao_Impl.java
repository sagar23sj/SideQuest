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
import com.sidequest.data.local.entity.ActionItemEntity;
import com.sidequest.domain.model.ActionStatus;
import com.sidequest.domain.model.ContentType;
import com.sidequest.domain.model.LinkPreview;
import com.sidequest.domain.model.SyncMeta;
import com.sidequest.domain.model.TaskReminder;
import com.sidequest.domain.model.Timeframe;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class ActionItemDao_Impl implements ActionItemDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ActionItemEntity> __insertionAdapterOfActionItemEntity;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<ActionItemEntity> __deletionAdapterOfActionItemEntity;

  private final EntityDeletionOrUpdateAdapter<ActionItemEntity> __updateAdapterOfActionItemEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfClear;

  private final EntityUpsertionAdapter<ActionItemEntity> __upsertionAdapterOfActionItemEntity;

  public ActionItemDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfActionItemEntity = new EntityInsertionAdapter<ActionItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `action_items` (`id`,`accountId`,`bucketId`,`title`,`description`,`contentType`,`sourceContent`,`preview`,`timeframe`,`status`,`createdAt`,`reminder`,`updatedAt`,`version`,`deleted`,`dirty`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionItemEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getBucketId());
        statement.bindString(4, entity.getTitle());
        if (entity.getDescription() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getDescription());
        }
        final String _tmp = __converters.fromContentType(entity.getContentType());
        statement.bindString(6, _tmp);
        if (entity.getSourceContent() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getSourceContent());
        }
        final String _tmp_1 = __converters.fromLinkPreview(entity.getPreview());
        if (_tmp_1 == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, _tmp_1);
        }
        final String _tmp_2 = __converters.fromTimeframe(entity.getTimeframe());
        statement.bindString(9, _tmp_2);
        final String _tmp_3 = __converters.fromActionStatus(entity.getStatus());
        statement.bindString(10, _tmp_3);
        statement.bindLong(11, entity.getCreatedAt());
        final String _tmp_4 = __converters.fromTaskReminder(entity.getReminder());
        if (_tmp_4 == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp_4);
        }
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(13, _tmpSync.getUpdatedAt());
        statement.bindLong(14, _tmpSync.getVersion());
        final int _tmp_5 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(15, _tmp_5);
        final int _tmp_6 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(16, _tmp_6);
      }
    };
    this.__deletionAdapterOfActionItemEntity = new EntityDeletionOrUpdateAdapter<ActionItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `action_items` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionItemEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__updateAdapterOfActionItemEntity = new EntityDeletionOrUpdateAdapter<ActionItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `action_items` SET `id` = ?,`accountId` = ?,`bucketId` = ?,`title` = ?,`description` = ?,`contentType` = ?,`sourceContent` = ?,`preview` = ?,`timeframe` = ?,`status` = ?,`createdAt` = ?,`reminder` = ?,`updatedAt` = ?,`version` = ?,`deleted` = ?,`dirty` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionItemEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getBucketId());
        statement.bindString(4, entity.getTitle());
        if (entity.getDescription() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getDescription());
        }
        final String _tmp = __converters.fromContentType(entity.getContentType());
        statement.bindString(6, _tmp);
        if (entity.getSourceContent() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getSourceContent());
        }
        final String _tmp_1 = __converters.fromLinkPreview(entity.getPreview());
        if (_tmp_1 == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, _tmp_1);
        }
        final String _tmp_2 = __converters.fromTimeframe(entity.getTimeframe());
        statement.bindString(9, _tmp_2);
        final String _tmp_3 = __converters.fromActionStatus(entity.getStatus());
        statement.bindString(10, _tmp_3);
        statement.bindLong(11, entity.getCreatedAt());
        final String _tmp_4 = __converters.fromTaskReminder(entity.getReminder());
        if (_tmp_4 == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp_4);
        }
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(13, _tmpSync.getUpdatedAt());
        statement.bindLong(14, _tmpSync.getVersion());
        final int _tmp_5 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(15, _tmp_5);
        final int _tmp_6 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(16, _tmp_6);
        statement.bindString(17, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM action_items WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClear = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM action_items";
        return _query;
      }
    };
    this.__upsertionAdapterOfActionItemEntity = new EntityUpsertionAdapter<ActionItemEntity>(new EntityInsertionAdapter<ActionItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `action_items` (`id`,`accountId`,`bucketId`,`title`,`description`,`contentType`,`sourceContent`,`preview`,`timeframe`,`status`,`createdAt`,`reminder`,`updatedAt`,`version`,`deleted`,`dirty`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionItemEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getBucketId());
        statement.bindString(4, entity.getTitle());
        if (entity.getDescription() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getDescription());
        }
        final String _tmp = __converters.fromContentType(entity.getContentType());
        statement.bindString(6, _tmp);
        if (entity.getSourceContent() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getSourceContent());
        }
        final String _tmp_1 = __converters.fromLinkPreview(entity.getPreview());
        if (_tmp_1 == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, _tmp_1);
        }
        final String _tmp_2 = __converters.fromTimeframe(entity.getTimeframe());
        statement.bindString(9, _tmp_2);
        final String _tmp_3 = __converters.fromActionStatus(entity.getStatus());
        statement.bindString(10, _tmp_3);
        statement.bindLong(11, entity.getCreatedAt());
        final String _tmp_4 = __converters.fromTaskReminder(entity.getReminder());
        if (_tmp_4 == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp_4);
        }
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(13, _tmpSync.getUpdatedAt());
        statement.bindLong(14, _tmpSync.getVersion());
        final int _tmp_5 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(15, _tmp_5);
        final int _tmp_6 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(16, _tmp_6);
      }
    }, new EntityDeletionOrUpdateAdapter<ActionItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `action_items` SET `id` = ?,`accountId` = ?,`bucketId` = ?,`title` = ?,`description` = ?,`contentType` = ?,`sourceContent` = ?,`preview` = ?,`timeframe` = ?,`status` = ?,`createdAt` = ?,`reminder` = ?,`updatedAt` = ?,`version` = ?,`deleted` = ?,`dirty` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionItemEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getBucketId());
        statement.bindString(4, entity.getTitle());
        if (entity.getDescription() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getDescription());
        }
        final String _tmp = __converters.fromContentType(entity.getContentType());
        statement.bindString(6, _tmp);
        if (entity.getSourceContent() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getSourceContent());
        }
        final String _tmp_1 = __converters.fromLinkPreview(entity.getPreview());
        if (_tmp_1 == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, _tmp_1);
        }
        final String _tmp_2 = __converters.fromTimeframe(entity.getTimeframe());
        statement.bindString(9, _tmp_2);
        final String _tmp_3 = __converters.fromActionStatus(entity.getStatus());
        statement.bindString(10, _tmp_3);
        statement.bindLong(11, entity.getCreatedAt());
        final String _tmp_4 = __converters.fromTaskReminder(entity.getReminder());
        if (_tmp_4 == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp_4);
        }
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(13, _tmpSync.getUpdatedAt());
        statement.bindLong(14, _tmpSync.getVersion());
        final int _tmp_5 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(15, _tmp_5);
        final int _tmp_6 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(16, _tmp_6);
        statement.bindString(17, entity.getId());
      }
    });
  }

  @Override
  public Object insert(final ActionItemEntity item, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfActionItemEntity.insert(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<ActionItemEntity> items,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfActionItemEntity.insert(items);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final ActionItemEntity item, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfActionItemEntity.handle(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final ActionItemEntity item, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfActionItemEntity.handle(item);
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
  public Object upsert(final ActionItemEntity item, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfActionItemEntity.upsert(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertAll(final List<ActionItemEntity> items,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfActionItemEntity.upsert(items);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ActionItemEntity>> observeAll() {
    final String _sql = "SELECT * FROM action_items WHERE deleted = 0 ORDER BY createdAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"action_items"}, new Callable<List<ActionItemEntity>>() {
      @Override
      @NonNull
      public List<ActionItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfBucketId = CursorUtil.getColumnIndexOrThrow(_cursor, "bucketId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "contentType");
          final int _cursorIndexOfSourceContent = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceContent");
          final int _cursorIndexOfPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "preview");
          final int _cursorIndexOfTimeframe = CursorUtil.getColumnIndexOrThrow(_cursor, "timeframe");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfReminder = CursorUtil.getColumnIndexOrThrow(_cursor, "reminder");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<ActionItemEntity> _result = new ArrayList<ActionItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActionItemEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpBucketId;
            _tmpBucketId = _cursor.getString(_cursorIndexOfBucketId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final ContentType _tmpContentType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContentType);
            _tmpContentType = __converters.toContentType(_tmp);
            final String _tmpSourceContent;
            if (_cursor.isNull(_cursorIndexOfSourceContent)) {
              _tmpSourceContent = null;
            } else {
              _tmpSourceContent = _cursor.getString(_cursorIndexOfSourceContent);
            }
            final LinkPreview _tmpPreview;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfPreview)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfPreview);
            }
            _tmpPreview = __converters.toLinkPreview(_tmp_1);
            final Timeframe _tmpTimeframe;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfTimeframe);
            _tmpTimeframe = __converters.toTimeframe(_tmp_2);
            final ActionStatus _tmpStatus;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toActionStatus(_tmp_3);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final TaskReminder _tmpReminder;
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfReminder)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfReminder);
            }
            _tmpReminder = __converters.toTaskReminder(_tmp_4);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_5 != 0;
            final boolean _tmpDirty;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_6 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new ActionItemEntity(_tmpId,_tmpAccountId,_tmpBucketId,_tmpTitle,_tmpDescription,_tmpContentType,_tmpSourceContent,_tmpPreview,_tmpTimeframe,_tmpStatus,_tmpCreatedAt,_tmpReminder,_tmpSync);
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
  public Flow<List<ActionItemEntity>> observeByAccount(final String accountId) {
    final String _sql = "SELECT * FROM action_items WHERE accountId = ? AND deleted = 0 ORDER BY createdAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, accountId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"action_items"}, new Callable<List<ActionItemEntity>>() {
      @Override
      @NonNull
      public List<ActionItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfBucketId = CursorUtil.getColumnIndexOrThrow(_cursor, "bucketId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "contentType");
          final int _cursorIndexOfSourceContent = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceContent");
          final int _cursorIndexOfPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "preview");
          final int _cursorIndexOfTimeframe = CursorUtil.getColumnIndexOrThrow(_cursor, "timeframe");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfReminder = CursorUtil.getColumnIndexOrThrow(_cursor, "reminder");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<ActionItemEntity> _result = new ArrayList<ActionItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActionItemEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpBucketId;
            _tmpBucketId = _cursor.getString(_cursorIndexOfBucketId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final ContentType _tmpContentType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContentType);
            _tmpContentType = __converters.toContentType(_tmp);
            final String _tmpSourceContent;
            if (_cursor.isNull(_cursorIndexOfSourceContent)) {
              _tmpSourceContent = null;
            } else {
              _tmpSourceContent = _cursor.getString(_cursorIndexOfSourceContent);
            }
            final LinkPreview _tmpPreview;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfPreview)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfPreview);
            }
            _tmpPreview = __converters.toLinkPreview(_tmp_1);
            final Timeframe _tmpTimeframe;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfTimeframe);
            _tmpTimeframe = __converters.toTimeframe(_tmp_2);
            final ActionStatus _tmpStatus;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toActionStatus(_tmp_3);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final TaskReminder _tmpReminder;
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfReminder)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfReminder);
            }
            _tmpReminder = __converters.toTaskReminder(_tmp_4);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_5 != 0;
            final boolean _tmpDirty;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_6 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new ActionItemEntity(_tmpId,_tmpAccountId,_tmpBucketId,_tmpTitle,_tmpDescription,_tmpContentType,_tmpSourceContent,_tmpPreview,_tmpTimeframe,_tmpStatus,_tmpCreatedAt,_tmpReminder,_tmpSync);
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
  public Flow<List<ActionItemEntity>> observeByBucket(final String bucketId) {
    final String _sql = "SELECT * FROM action_items WHERE bucketId = ? AND deleted = 0 ORDER BY createdAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, bucketId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"action_items"}, new Callable<List<ActionItemEntity>>() {
      @Override
      @NonNull
      public List<ActionItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfBucketId = CursorUtil.getColumnIndexOrThrow(_cursor, "bucketId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "contentType");
          final int _cursorIndexOfSourceContent = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceContent");
          final int _cursorIndexOfPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "preview");
          final int _cursorIndexOfTimeframe = CursorUtil.getColumnIndexOrThrow(_cursor, "timeframe");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfReminder = CursorUtil.getColumnIndexOrThrow(_cursor, "reminder");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<ActionItemEntity> _result = new ArrayList<ActionItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActionItemEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpBucketId;
            _tmpBucketId = _cursor.getString(_cursorIndexOfBucketId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final ContentType _tmpContentType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContentType);
            _tmpContentType = __converters.toContentType(_tmp);
            final String _tmpSourceContent;
            if (_cursor.isNull(_cursorIndexOfSourceContent)) {
              _tmpSourceContent = null;
            } else {
              _tmpSourceContent = _cursor.getString(_cursorIndexOfSourceContent);
            }
            final LinkPreview _tmpPreview;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfPreview)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfPreview);
            }
            _tmpPreview = __converters.toLinkPreview(_tmp_1);
            final Timeframe _tmpTimeframe;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfTimeframe);
            _tmpTimeframe = __converters.toTimeframe(_tmp_2);
            final ActionStatus _tmpStatus;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toActionStatus(_tmp_3);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final TaskReminder _tmpReminder;
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfReminder)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfReminder);
            }
            _tmpReminder = __converters.toTaskReminder(_tmp_4);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_5 != 0;
            final boolean _tmpDirty;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_6 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new ActionItemEntity(_tmpId,_tmpAccountId,_tmpBucketId,_tmpTitle,_tmpDescription,_tmpContentType,_tmpSourceContent,_tmpPreview,_tmpTimeframe,_tmpStatus,_tmpCreatedAt,_tmpReminder,_tmpSync);
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
  public Flow<ActionItemEntity> observeById(final String id) {
    final String _sql = "SELECT * FROM action_items WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"action_items"}, new Callable<ActionItemEntity>() {
      @Override
      @Nullable
      public ActionItemEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfBucketId = CursorUtil.getColumnIndexOrThrow(_cursor, "bucketId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "contentType");
          final int _cursorIndexOfSourceContent = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceContent");
          final int _cursorIndexOfPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "preview");
          final int _cursorIndexOfTimeframe = CursorUtil.getColumnIndexOrThrow(_cursor, "timeframe");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfReminder = CursorUtil.getColumnIndexOrThrow(_cursor, "reminder");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final ActionItemEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpBucketId;
            _tmpBucketId = _cursor.getString(_cursorIndexOfBucketId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final ContentType _tmpContentType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContentType);
            _tmpContentType = __converters.toContentType(_tmp);
            final String _tmpSourceContent;
            if (_cursor.isNull(_cursorIndexOfSourceContent)) {
              _tmpSourceContent = null;
            } else {
              _tmpSourceContent = _cursor.getString(_cursorIndexOfSourceContent);
            }
            final LinkPreview _tmpPreview;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfPreview)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfPreview);
            }
            _tmpPreview = __converters.toLinkPreview(_tmp_1);
            final Timeframe _tmpTimeframe;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfTimeframe);
            _tmpTimeframe = __converters.toTimeframe(_tmp_2);
            final ActionStatus _tmpStatus;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toActionStatus(_tmp_3);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final TaskReminder _tmpReminder;
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfReminder)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfReminder);
            }
            _tmpReminder = __converters.toTaskReminder(_tmp_4);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_5 != 0;
            final boolean _tmpDirty;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_6 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _result = new ActionItemEntity(_tmpId,_tmpAccountId,_tmpBucketId,_tmpTitle,_tmpDescription,_tmpContentType,_tmpSourceContent,_tmpPreview,_tmpTimeframe,_tmpStatus,_tmpCreatedAt,_tmpReminder,_tmpSync);
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
  public Object getById(final String id, final Continuation<? super ActionItemEntity> $completion) {
    final String _sql = "SELECT * FROM action_items WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ActionItemEntity>() {
      @Override
      @Nullable
      public ActionItemEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfBucketId = CursorUtil.getColumnIndexOrThrow(_cursor, "bucketId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "contentType");
          final int _cursorIndexOfSourceContent = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceContent");
          final int _cursorIndexOfPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "preview");
          final int _cursorIndexOfTimeframe = CursorUtil.getColumnIndexOrThrow(_cursor, "timeframe");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfReminder = CursorUtil.getColumnIndexOrThrow(_cursor, "reminder");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final ActionItemEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpBucketId;
            _tmpBucketId = _cursor.getString(_cursorIndexOfBucketId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final ContentType _tmpContentType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContentType);
            _tmpContentType = __converters.toContentType(_tmp);
            final String _tmpSourceContent;
            if (_cursor.isNull(_cursorIndexOfSourceContent)) {
              _tmpSourceContent = null;
            } else {
              _tmpSourceContent = _cursor.getString(_cursorIndexOfSourceContent);
            }
            final LinkPreview _tmpPreview;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfPreview)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfPreview);
            }
            _tmpPreview = __converters.toLinkPreview(_tmp_1);
            final Timeframe _tmpTimeframe;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfTimeframe);
            _tmpTimeframe = __converters.toTimeframe(_tmp_2);
            final ActionStatus _tmpStatus;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toActionStatus(_tmp_3);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final TaskReminder _tmpReminder;
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfReminder)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfReminder);
            }
            _tmpReminder = __converters.toTaskReminder(_tmp_4);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_5 != 0;
            final boolean _tmpDirty;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_6 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _result = new ActionItemEntity(_tmpId,_tmpAccountId,_tmpBucketId,_tmpTitle,_tmpDescription,_tmpContentType,_tmpSourceContent,_tmpPreview,_tmpTimeframe,_tmpStatus,_tmpCreatedAt,_tmpReminder,_tmpSync);
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
  public Object getAll(final Continuation<? super List<ActionItemEntity>> $completion) {
    final String _sql = "SELECT * FROM action_items ORDER BY createdAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ActionItemEntity>>() {
      @Override
      @NonNull
      public List<ActionItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfBucketId = CursorUtil.getColumnIndexOrThrow(_cursor, "bucketId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "contentType");
          final int _cursorIndexOfSourceContent = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceContent");
          final int _cursorIndexOfPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "preview");
          final int _cursorIndexOfTimeframe = CursorUtil.getColumnIndexOrThrow(_cursor, "timeframe");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfReminder = CursorUtil.getColumnIndexOrThrow(_cursor, "reminder");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<ActionItemEntity> _result = new ArrayList<ActionItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActionItemEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpBucketId;
            _tmpBucketId = _cursor.getString(_cursorIndexOfBucketId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final ContentType _tmpContentType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContentType);
            _tmpContentType = __converters.toContentType(_tmp);
            final String _tmpSourceContent;
            if (_cursor.isNull(_cursorIndexOfSourceContent)) {
              _tmpSourceContent = null;
            } else {
              _tmpSourceContent = _cursor.getString(_cursorIndexOfSourceContent);
            }
            final LinkPreview _tmpPreview;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfPreview)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfPreview);
            }
            _tmpPreview = __converters.toLinkPreview(_tmp_1);
            final Timeframe _tmpTimeframe;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfTimeframe);
            _tmpTimeframe = __converters.toTimeframe(_tmp_2);
            final ActionStatus _tmpStatus;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toActionStatus(_tmp_3);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final TaskReminder _tmpReminder;
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfReminder)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfReminder);
            }
            _tmpReminder = __converters.toTaskReminder(_tmp_4);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_5 != 0;
            final boolean _tmpDirty;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_6 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new ActionItemEntity(_tmpId,_tmpAccountId,_tmpBucketId,_tmpTitle,_tmpDescription,_tmpContentType,_tmpSourceContent,_tmpPreview,_tmpTimeframe,_tmpStatus,_tmpCreatedAt,_tmpReminder,_tmpSync);
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
  public Object getByBucket(final String bucketId,
      final Continuation<? super List<ActionItemEntity>> $completion) {
    final String _sql = "SELECT * FROM action_items WHERE bucketId = ? AND deleted = 0 ORDER BY createdAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, bucketId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ActionItemEntity>>() {
      @Override
      @NonNull
      public List<ActionItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfBucketId = CursorUtil.getColumnIndexOrThrow(_cursor, "bucketId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "contentType");
          final int _cursorIndexOfSourceContent = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceContent");
          final int _cursorIndexOfPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "preview");
          final int _cursorIndexOfTimeframe = CursorUtil.getColumnIndexOrThrow(_cursor, "timeframe");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfReminder = CursorUtil.getColumnIndexOrThrow(_cursor, "reminder");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<ActionItemEntity> _result = new ArrayList<ActionItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActionItemEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpBucketId;
            _tmpBucketId = _cursor.getString(_cursorIndexOfBucketId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final ContentType _tmpContentType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContentType);
            _tmpContentType = __converters.toContentType(_tmp);
            final String _tmpSourceContent;
            if (_cursor.isNull(_cursorIndexOfSourceContent)) {
              _tmpSourceContent = null;
            } else {
              _tmpSourceContent = _cursor.getString(_cursorIndexOfSourceContent);
            }
            final LinkPreview _tmpPreview;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfPreview)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfPreview);
            }
            _tmpPreview = __converters.toLinkPreview(_tmp_1);
            final Timeframe _tmpTimeframe;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfTimeframe);
            _tmpTimeframe = __converters.toTimeframe(_tmp_2);
            final ActionStatus _tmpStatus;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toActionStatus(_tmp_3);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final TaskReminder _tmpReminder;
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfReminder)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfReminder);
            }
            _tmpReminder = __converters.toTaskReminder(_tmp_4);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_5 != 0;
            final boolean _tmpDirty;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_6 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new ActionItemEntity(_tmpId,_tmpAccountId,_tmpBucketId,_tmpTitle,_tmpDescription,_tmpContentType,_tmpSourceContent,_tmpPreview,_tmpTimeframe,_tmpStatus,_tmpCreatedAt,_tmpReminder,_tmpSync);
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
  public Object getByAccount(final String accountId,
      final Continuation<? super List<ActionItemEntity>> $completion) {
    final String _sql = "SELECT * FROM action_items WHERE accountId = ? AND deleted = 0 ORDER BY createdAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, accountId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ActionItemEntity>>() {
      @Override
      @NonNull
      public List<ActionItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfBucketId = CursorUtil.getColumnIndexOrThrow(_cursor, "bucketId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "contentType");
          final int _cursorIndexOfSourceContent = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceContent");
          final int _cursorIndexOfPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "preview");
          final int _cursorIndexOfTimeframe = CursorUtil.getColumnIndexOrThrow(_cursor, "timeframe");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfReminder = CursorUtil.getColumnIndexOrThrow(_cursor, "reminder");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<ActionItemEntity> _result = new ArrayList<ActionItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActionItemEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpBucketId;
            _tmpBucketId = _cursor.getString(_cursorIndexOfBucketId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final ContentType _tmpContentType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContentType);
            _tmpContentType = __converters.toContentType(_tmp);
            final String _tmpSourceContent;
            if (_cursor.isNull(_cursorIndexOfSourceContent)) {
              _tmpSourceContent = null;
            } else {
              _tmpSourceContent = _cursor.getString(_cursorIndexOfSourceContent);
            }
            final LinkPreview _tmpPreview;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfPreview)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfPreview);
            }
            _tmpPreview = __converters.toLinkPreview(_tmp_1);
            final Timeframe _tmpTimeframe;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfTimeframe);
            _tmpTimeframe = __converters.toTimeframe(_tmp_2);
            final ActionStatus _tmpStatus;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toActionStatus(_tmp_3);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final TaskReminder _tmpReminder;
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfReminder)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfReminder);
            }
            _tmpReminder = __converters.toTaskReminder(_tmp_4);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_5 != 0;
            final boolean _tmpDirty;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_6 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new ActionItemEntity(_tmpId,_tmpAccountId,_tmpBucketId,_tmpTitle,_tmpDescription,_tmpContentType,_tmpSourceContent,_tmpPreview,_tmpTimeframe,_tmpStatus,_tmpCreatedAt,_tmpReminder,_tmpSync);
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
  public Object countByBucket(final String bucketId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM action_items WHERE bucketId = ? AND deleted = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, bucketId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
