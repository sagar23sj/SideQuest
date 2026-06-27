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
import com.sidequest.data.local.entity.BucketEntity;
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
public final class BucketDao_Impl implements BucketDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BucketEntity> __insertionAdapterOfBucketEntity;

  private final EntityDeletionOrUpdateAdapter<BucketEntity> __deletionAdapterOfBucketEntity;

  private final EntityDeletionOrUpdateAdapter<BucketEntity> __updateAdapterOfBucketEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfClear;

  private final EntityUpsertionAdapter<BucketEntity> __upsertionAdapterOfBucketEntity;

  public BucketDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBucketEntity = new EntityInsertionAdapter<BucketEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `buckets` (`id`,`accountId`,`name`,`notStartedColor`,`inProgressColor`,`completedColor`,`updatedAt`,`version`,`deleted`,`dirty`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BucketEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getName());
        statement.bindString(4, entity.getNotStartedColor());
        statement.bindString(5, entity.getInProgressColor());
        statement.bindString(6, entity.getCompletedColor());
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(7, _tmpSync.getUpdatedAt());
        statement.bindLong(8, _tmpSync.getVersion());
        final int _tmp = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(9, _tmp);
        final int _tmp_1 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
      }
    };
    this.__deletionAdapterOfBucketEntity = new EntityDeletionOrUpdateAdapter<BucketEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `buckets` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BucketEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__updateAdapterOfBucketEntity = new EntityDeletionOrUpdateAdapter<BucketEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `buckets` SET `id` = ?,`accountId` = ?,`name` = ?,`notStartedColor` = ?,`inProgressColor` = ?,`completedColor` = ?,`updatedAt` = ?,`version` = ?,`deleted` = ?,`dirty` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BucketEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getName());
        statement.bindString(4, entity.getNotStartedColor());
        statement.bindString(5, entity.getInProgressColor());
        statement.bindString(6, entity.getCompletedColor());
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(7, _tmpSync.getUpdatedAt());
        statement.bindLong(8, _tmpSync.getVersion());
        final int _tmp = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(9, _tmp);
        final int _tmp_1 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
        statement.bindString(11, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM buckets WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClear = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM buckets";
        return _query;
      }
    };
    this.__upsertionAdapterOfBucketEntity = new EntityUpsertionAdapter<BucketEntity>(new EntityInsertionAdapter<BucketEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `buckets` (`id`,`accountId`,`name`,`notStartedColor`,`inProgressColor`,`completedColor`,`updatedAt`,`version`,`deleted`,`dirty`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BucketEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getName());
        statement.bindString(4, entity.getNotStartedColor());
        statement.bindString(5, entity.getInProgressColor());
        statement.bindString(6, entity.getCompletedColor());
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(7, _tmpSync.getUpdatedAt());
        statement.bindLong(8, _tmpSync.getVersion());
        final int _tmp = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(9, _tmp);
        final int _tmp_1 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
      }
    }, new EntityDeletionOrUpdateAdapter<BucketEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `buckets` SET `id` = ?,`accountId` = ?,`name` = ?,`notStartedColor` = ?,`inProgressColor` = ?,`completedColor` = ?,`updatedAt` = ?,`version` = ?,`deleted` = ?,`dirty` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BucketEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getAccountId());
        statement.bindString(3, entity.getName());
        statement.bindString(4, entity.getNotStartedColor());
        statement.bindString(5, entity.getInProgressColor());
        statement.bindString(6, entity.getCompletedColor());
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(7, _tmpSync.getUpdatedAt());
        statement.bindLong(8, _tmpSync.getVersion());
        final int _tmp = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(9, _tmp);
        final int _tmp_1 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
        statement.bindString(11, entity.getId());
      }
    });
  }

  @Override
  public Object insert(final BucketEntity bucket, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBucketEntity.insert(bucket);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<BucketEntity> buckets,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBucketEntity.insert(buckets);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final BucketEntity bucket, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfBucketEntity.handle(bucket);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final BucketEntity bucket, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfBucketEntity.handle(bucket);
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
  public Object upsert(final BucketEntity bucket, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfBucketEntity.upsert(bucket);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertAll(final List<BucketEntity> buckets,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfBucketEntity.upsert(buckets);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BucketEntity>> observeAll() {
    final String _sql = "SELECT * FROM buckets WHERE deleted = 0 ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"buckets"}, new Callable<List<BucketEntity>>() {
      @Override
      @NonNull
      public List<BucketEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfNotStartedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "notStartedColor");
          final int _cursorIndexOfInProgressColor = CursorUtil.getColumnIndexOrThrow(_cursor, "inProgressColor");
          final int _cursorIndexOfCompletedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "completedColor");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<BucketEntity> _result = new ArrayList<BucketEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BucketEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpNotStartedColor;
            _tmpNotStartedColor = _cursor.getString(_cursorIndexOfNotStartedColor);
            final String _tmpInProgressColor;
            _tmpInProgressColor = _cursor.getString(_cursorIndexOfInProgressColor);
            final String _tmpCompletedColor;
            _tmpCompletedColor = _cursor.getString(_cursorIndexOfCompletedColor);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp != 0;
            final boolean _tmpDirty;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_1 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new BucketEntity(_tmpId,_tmpAccountId,_tmpName,_tmpNotStartedColor,_tmpInProgressColor,_tmpCompletedColor,_tmpSync);
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
  public Flow<List<BucketEntity>> observeByAccount(final String accountId) {
    final String _sql = "SELECT * FROM buckets WHERE accountId = ? AND deleted = 0 ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, accountId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"buckets"}, new Callable<List<BucketEntity>>() {
      @Override
      @NonNull
      public List<BucketEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfNotStartedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "notStartedColor");
          final int _cursorIndexOfInProgressColor = CursorUtil.getColumnIndexOrThrow(_cursor, "inProgressColor");
          final int _cursorIndexOfCompletedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "completedColor");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<BucketEntity> _result = new ArrayList<BucketEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BucketEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpNotStartedColor;
            _tmpNotStartedColor = _cursor.getString(_cursorIndexOfNotStartedColor);
            final String _tmpInProgressColor;
            _tmpInProgressColor = _cursor.getString(_cursorIndexOfInProgressColor);
            final String _tmpCompletedColor;
            _tmpCompletedColor = _cursor.getString(_cursorIndexOfCompletedColor);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp != 0;
            final boolean _tmpDirty;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_1 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new BucketEntity(_tmpId,_tmpAccountId,_tmpName,_tmpNotStartedColor,_tmpInProgressColor,_tmpCompletedColor,_tmpSync);
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
  public Flow<BucketEntity> observeById(final String id) {
    final String _sql = "SELECT * FROM buckets WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"buckets"}, new Callable<BucketEntity>() {
      @Override
      @Nullable
      public BucketEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfNotStartedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "notStartedColor");
          final int _cursorIndexOfInProgressColor = CursorUtil.getColumnIndexOrThrow(_cursor, "inProgressColor");
          final int _cursorIndexOfCompletedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "completedColor");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final BucketEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpNotStartedColor;
            _tmpNotStartedColor = _cursor.getString(_cursorIndexOfNotStartedColor);
            final String _tmpInProgressColor;
            _tmpInProgressColor = _cursor.getString(_cursorIndexOfInProgressColor);
            final String _tmpCompletedColor;
            _tmpCompletedColor = _cursor.getString(_cursorIndexOfCompletedColor);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp != 0;
            final boolean _tmpDirty;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_1 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _result = new BucketEntity(_tmpId,_tmpAccountId,_tmpName,_tmpNotStartedColor,_tmpInProgressColor,_tmpCompletedColor,_tmpSync);
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
  public Object getById(final String id, final Continuation<? super BucketEntity> $completion) {
    final String _sql = "SELECT * FROM buckets WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BucketEntity>() {
      @Override
      @Nullable
      public BucketEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfNotStartedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "notStartedColor");
          final int _cursorIndexOfInProgressColor = CursorUtil.getColumnIndexOrThrow(_cursor, "inProgressColor");
          final int _cursorIndexOfCompletedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "completedColor");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final BucketEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpNotStartedColor;
            _tmpNotStartedColor = _cursor.getString(_cursorIndexOfNotStartedColor);
            final String _tmpInProgressColor;
            _tmpInProgressColor = _cursor.getString(_cursorIndexOfInProgressColor);
            final String _tmpCompletedColor;
            _tmpCompletedColor = _cursor.getString(_cursorIndexOfCompletedColor);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp != 0;
            final boolean _tmpDirty;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_1 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _result = new BucketEntity(_tmpId,_tmpAccountId,_tmpName,_tmpNotStartedColor,_tmpInProgressColor,_tmpCompletedColor,_tmpSync);
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
      final Continuation<? super List<BucketEntity>> $completion) {
    final String _sql = "SELECT * FROM buckets WHERE accountId = ? AND deleted = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, accountId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<BucketEntity>>() {
      @Override
      @NonNull
      public List<BucketEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfNotStartedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "notStartedColor");
          final int _cursorIndexOfInProgressColor = CursorUtil.getColumnIndexOrThrow(_cursor, "inProgressColor");
          final int _cursorIndexOfCompletedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "completedColor");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<BucketEntity> _result = new ArrayList<BucketEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BucketEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpNotStartedColor;
            _tmpNotStartedColor = _cursor.getString(_cursorIndexOfNotStartedColor);
            final String _tmpInProgressColor;
            _tmpInProgressColor = _cursor.getString(_cursorIndexOfInProgressColor);
            final String _tmpCompletedColor;
            _tmpCompletedColor = _cursor.getString(_cursorIndexOfCompletedColor);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp != 0;
            final boolean _tmpDirty;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_1 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new BucketEntity(_tmpId,_tmpAccountId,_tmpName,_tmpNotStartedColor,_tmpInProgressColor,_tmpCompletedColor,_tmpSync);
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
  public Object getAll(final Continuation<? super List<BucketEntity>> $completion) {
    final String _sql = "SELECT * FROM buckets ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<BucketEntity>>() {
      @Override
      @NonNull
      public List<BucketEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "accountId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfNotStartedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "notStartedColor");
          final int _cursorIndexOfInProgressColor = CursorUtil.getColumnIndexOrThrow(_cursor, "inProgressColor");
          final int _cursorIndexOfCompletedColor = CursorUtil.getColumnIndexOrThrow(_cursor, "completedColor");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<BucketEntity> _result = new ArrayList<BucketEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BucketEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpAccountId;
            _tmpAccountId = _cursor.getString(_cursorIndexOfAccountId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpNotStartedColor;
            _tmpNotStartedColor = _cursor.getString(_cursorIndexOfNotStartedColor);
            final String _tmpInProgressColor;
            _tmpInProgressColor = _cursor.getString(_cursorIndexOfInProgressColor);
            final String _tmpCompletedColor;
            _tmpCompletedColor = _cursor.getString(_cursorIndexOfCompletedColor);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp != 0;
            final boolean _tmpDirty;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_1 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new BucketEntity(_tmpId,_tmpAccountId,_tmpName,_tmpNotStartedColor,_tmpInProgressColor,_tmpCompletedColor,_tmpSync);
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
