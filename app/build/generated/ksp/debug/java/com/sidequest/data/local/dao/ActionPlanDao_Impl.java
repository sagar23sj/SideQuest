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
import com.sidequest.data.local.entity.ActionPlanEntity;
import com.sidequest.domain.model.SubAction;
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
public final class ActionPlanDao_Impl implements ActionPlanDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ActionPlanEntity> __insertionAdapterOfActionPlanEntity;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<ActionPlanEntity> __deletionAdapterOfActionPlanEntity;

  private final EntityDeletionOrUpdateAdapter<ActionPlanEntity> __updateAdapterOfActionPlanEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfClear;

  private final EntityUpsertionAdapter<ActionPlanEntity> __upsertionAdapterOfActionPlanEntity;

  public ActionPlanDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfActionPlanEntity = new EntityInsertionAdapter<ActionPlanEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `action_plans` (`id`,`actionItemId`,`subActions`,`updatedAt`,`version`,`deleted`,`dirty`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionPlanEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getActionItemId());
        final String _tmp = __converters.fromSubActions(entity.getSubActions());
        statement.bindString(3, _tmp);
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(4, _tmpSync.getUpdatedAt());
        statement.bindLong(5, _tmpSync.getVersion());
        final int _tmp_1 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(6, _tmp_1);
        final int _tmp_2 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(7, _tmp_2);
      }
    };
    this.__deletionAdapterOfActionPlanEntity = new EntityDeletionOrUpdateAdapter<ActionPlanEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `action_plans` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionPlanEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__updateAdapterOfActionPlanEntity = new EntityDeletionOrUpdateAdapter<ActionPlanEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `action_plans` SET `id` = ?,`actionItemId` = ?,`subActions` = ?,`updatedAt` = ?,`version` = ?,`deleted` = ?,`dirty` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionPlanEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getActionItemId());
        final String _tmp = __converters.fromSubActions(entity.getSubActions());
        statement.bindString(3, _tmp);
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(4, _tmpSync.getUpdatedAt());
        statement.bindLong(5, _tmpSync.getVersion());
        final int _tmp_1 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(6, _tmp_1);
        final int _tmp_2 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(7, _tmp_2);
        statement.bindString(8, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM action_plans WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClear = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM action_plans";
        return _query;
      }
    };
    this.__upsertionAdapterOfActionPlanEntity = new EntityUpsertionAdapter<ActionPlanEntity>(new EntityInsertionAdapter<ActionPlanEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `action_plans` (`id`,`actionItemId`,`subActions`,`updatedAt`,`version`,`deleted`,`dirty`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionPlanEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getActionItemId());
        final String _tmp = __converters.fromSubActions(entity.getSubActions());
        statement.bindString(3, _tmp);
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(4, _tmpSync.getUpdatedAt());
        statement.bindLong(5, _tmpSync.getVersion());
        final int _tmp_1 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(6, _tmp_1);
        final int _tmp_2 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(7, _tmp_2);
      }
    }, new EntityDeletionOrUpdateAdapter<ActionPlanEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `action_plans` SET `id` = ?,`actionItemId` = ?,`subActions` = ?,`updatedAt` = ?,`version` = ?,`deleted` = ?,`dirty` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionPlanEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getActionItemId());
        final String _tmp = __converters.fromSubActions(entity.getSubActions());
        statement.bindString(3, _tmp);
        final SyncMeta _tmpSync = entity.getSync();
        statement.bindLong(4, _tmpSync.getUpdatedAt());
        statement.bindLong(5, _tmpSync.getVersion());
        final int _tmp_1 = _tmpSync.getDeleted() ? 1 : 0;
        statement.bindLong(6, _tmp_1);
        final int _tmp_2 = _tmpSync.getDirty() ? 1 : 0;
        statement.bindLong(7, _tmp_2);
        statement.bindString(8, entity.getId());
      }
    });
  }

  @Override
  public Object insert(final ActionPlanEntity plan, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfActionPlanEntity.insert(plan);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<ActionPlanEntity> plans,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfActionPlanEntity.insert(plans);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final ActionPlanEntity plan, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfActionPlanEntity.handle(plan);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final ActionPlanEntity plan, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfActionPlanEntity.handle(plan);
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
  public Object upsert(final ActionPlanEntity plan, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfActionPlanEntity.upsert(plan);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertAll(final List<ActionPlanEntity> plans,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfActionPlanEntity.upsert(plans);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ActionPlanEntity>> observeAll() {
    final String _sql = "SELECT * FROM action_plans WHERE deleted = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"action_plans"}, new Callable<List<ActionPlanEntity>>() {
      @Override
      @NonNull
      public List<ActionPlanEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActionItemId = CursorUtil.getColumnIndexOrThrow(_cursor, "actionItemId");
          final int _cursorIndexOfSubActions = CursorUtil.getColumnIndexOrThrow(_cursor, "subActions");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<ActionPlanEntity> _result = new ArrayList<ActionPlanEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActionPlanEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpActionItemId;
            _tmpActionItemId = _cursor.getString(_cursorIndexOfActionItemId);
            final List<SubAction> _tmpSubActions;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfSubActions);
            _tmpSubActions = __converters.toSubActions(_tmp);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_1 != 0;
            final boolean _tmpDirty;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_2 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new ActionPlanEntity(_tmpId,_tmpActionItemId,_tmpSubActions,_tmpSync);
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
  public Flow<ActionPlanEntity> observeByActionItem(final String actionItemId) {
    final String _sql = "SELECT * FROM action_plans WHERE actionItemId = ? AND deleted = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, actionItemId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"action_plans"}, new Callable<ActionPlanEntity>() {
      @Override
      @Nullable
      public ActionPlanEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActionItemId = CursorUtil.getColumnIndexOrThrow(_cursor, "actionItemId");
          final int _cursorIndexOfSubActions = CursorUtil.getColumnIndexOrThrow(_cursor, "subActions");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final ActionPlanEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpActionItemId;
            _tmpActionItemId = _cursor.getString(_cursorIndexOfActionItemId);
            final List<SubAction> _tmpSubActions;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfSubActions);
            _tmpSubActions = __converters.toSubActions(_tmp);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_1 != 0;
            final boolean _tmpDirty;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_2 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _result = new ActionPlanEntity(_tmpId,_tmpActionItemId,_tmpSubActions,_tmpSync);
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
  public Object getById(final String id, final Continuation<? super ActionPlanEntity> $completion) {
    final String _sql = "SELECT * FROM action_plans WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ActionPlanEntity>() {
      @Override
      @Nullable
      public ActionPlanEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActionItemId = CursorUtil.getColumnIndexOrThrow(_cursor, "actionItemId");
          final int _cursorIndexOfSubActions = CursorUtil.getColumnIndexOrThrow(_cursor, "subActions");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final ActionPlanEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpActionItemId;
            _tmpActionItemId = _cursor.getString(_cursorIndexOfActionItemId);
            final List<SubAction> _tmpSubActions;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfSubActions);
            _tmpSubActions = __converters.toSubActions(_tmp);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_1 != 0;
            final boolean _tmpDirty;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_2 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _result = new ActionPlanEntity(_tmpId,_tmpActionItemId,_tmpSubActions,_tmpSync);
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
  public Object getByActionItem(final String actionItemId,
      final Continuation<? super ActionPlanEntity> $completion) {
    final String _sql = "SELECT * FROM action_plans WHERE actionItemId = ? AND deleted = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, actionItemId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ActionPlanEntity>() {
      @Override
      @Nullable
      public ActionPlanEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActionItemId = CursorUtil.getColumnIndexOrThrow(_cursor, "actionItemId");
          final int _cursorIndexOfSubActions = CursorUtil.getColumnIndexOrThrow(_cursor, "subActions");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final ActionPlanEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpActionItemId;
            _tmpActionItemId = _cursor.getString(_cursorIndexOfActionItemId);
            final List<SubAction> _tmpSubActions;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfSubActions);
            _tmpSubActions = __converters.toSubActions(_tmp);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_1 != 0;
            final boolean _tmpDirty;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_2 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _result = new ActionPlanEntity(_tmpId,_tmpActionItemId,_tmpSubActions,_tmpSync);
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
  public Object getAll(final Continuation<? super List<ActionPlanEntity>> $completion) {
    final String _sql = "SELECT * FROM action_plans";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ActionPlanEntity>>() {
      @Override
      @NonNull
      public List<ActionPlanEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActionItemId = CursorUtil.getColumnIndexOrThrow(_cursor, "actionItemId");
          final int _cursorIndexOfSubActions = CursorUtil.getColumnIndexOrThrow(_cursor, "subActions");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "version");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final int _cursorIndexOfDirty = CursorUtil.getColumnIndexOrThrow(_cursor, "dirty");
          final List<ActionPlanEntity> _result = new ArrayList<ActionPlanEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActionPlanEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpActionItemId;
            _tmpActionItemId = _cursor.getString(_cursorIndexOfActionItemId);
            final List<SubAction> _tmpSubActions;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfSubActions);
            _tmpSubActions = __converters.toSubActions(_tmp);
            final SyncMeta _tmpSync;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final long _tmpVersion;
            _tmpVersion = _cursor.getLong(_cursorIndexOfVersion);
            final boolean _tmpDeleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDeleted);
            _tmpDeleted = _tmp_1 != 0;
            final boolean _tmpDirty;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfDirty);
            _tmpDirty = _tmp_2 != 0;
            _tmpSync = new SyncMeta(_tmpUpdatedAt,_tmpVersion,_tmpDeleted,_tmpDirty);
            _item = new ActionPlanEntity(_tmpId,_tmpActionItemId,_tmpSubActions,_tmpSync);
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
