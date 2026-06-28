package com.sidequest.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.sidequest.data.local.dao.ActionItemDao;
import com.sidequest.data.local.dao.ActionItemDao_Impl;
import com.sidequest.data.local.dao.ActionPlanDao;
import com.sidequest.data.local.dao.ActionPlanDao_Impl;
import com.sidequest.data.local.dao.BucketDao;
import com.sidequest.data.local.dao.BucketDao_Impl;
import com.sidequest.data.local.dao.VoiceJournalDao;
import com.sidequest.data.local.dao.VoiceJournalDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SideQuestDatabase_Impl extends SideQuestDatabase {
  private volatile ActionItemDao _actionItemDao;

  private volatile BucketDao _bucketDao;

  private volatile ActionPlanDao _actionPlanDao;

  private volatile VoiceJournalDao _voiceJournalDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(5) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `action_items` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `bucketId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `contentType` TEXT NOT NULL, `sourceContent` TEXT, `preview` TEXT, `timeframe` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `reminder` TEXT, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_action_items_accountId` ON `action_items` (`accountId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_action_items_bucketId` ON `action_items` (`bucketId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_action_items_createdAt` ON `action_items` (`createdAt`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `buckets` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `name` TEXT NOT NULL, `notStartedColor` TEXT NOT NULL, `inProgressColor` TEXT NOT NULL, `completedColor` TEXT NOT NULL, `imageRef` TEXT, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_buckets_accountId` ON `buckets` (`accountId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `action_plans` (`id` TEXT NOT NULL, `actionItemId` TEXT NOT NULL, `subActions` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_action_plans_actionItemId` ON `action_plans` (`actionItemId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `voice_journal_entries` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `audioRef` TEXT NOT NULL, `transcript` TEXT, `transcriptionFailed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `extractedActionItemIds` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_journal_entries_accountId` ON `voice_journal_entries` (`accountId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_journal_entries_createdAt` ON `voice_journal_entries` (`createdAt`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '43002a8290195fdbeea5389f39c3a27b')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `action_items`");
        db.execSQL("DROP TABLE IF EXISTS `buckets`");
        db.execSQL("DROP TABLE IF EXISTS `action_plans`");
        db.execSQL("DROP TABLE IF EXISTS `voice_journal_entries`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsActionItems = new HashMap<String, TableInfo.Column>(16);
        _columnsActionItems.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("accountId", new TableInfo.Column("accountId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("bucketId", new TableInfo.Column("bucketId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("description", new TableInfo.Column("description", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("contentType", new TableInfo.Column("contentType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("sourceContent", new TableInfo.Column("sourceContent", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("preview", new TableInfo.Column("preview", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("timeframe", new TableInfo.Column("timeframe", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("reminder", new TableInfo.Column("reminder", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("version", new TableInfo.Column("version", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("deleted", new TableInfo.Column("deleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionItems.put("dirty", new TableInfo.Column("dirty", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysActionItems = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesActionItems = new HashSet<TableInfo.Index>(3);
        _indicesActionItems.add(new TableInfo.Index("index_action_items_accountId", false, Arrays.asList("accountId"), Arrays.asList("ASC")));
        _indicesActionItems.add(new TableInfo.Index("index_action_items_bucketId", false, Arrays.asList("bucketId"), Arrays.asList("ASC")));
        _indicesActionItems.add(new TableInfo.Index("index_action_items_createdAt", false, Arrays.asList("createdAt"), Arrays.asList("ASC")));
        final TableInfo _infoActionItems = new TableInfo("action_items", _columnsActionItems, _foreignKeysActionItems, _indicesActionItems);
        final TableInfo _existingActionItems = TableInfo.read(db, "action_items");
        if (!_infoActionItems.equals(_existingActionItems)) {
          return new RoomOpenHelper.ValidationResult(false, "action_items(com.sidequest.data.local.entity.ActionItemEntity).\n"
                  + " Expected:\n" + _infoActionItems + "\n"
                  + " Found:\n" + _existingActionItems);
        }
        final HashMap<String, TableInfo.Column> _columnsBuckets = new HashMap<String, TableInfo.Column>(11);
        _columnsBuckets.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBuckets.put("accountId", new TableInfo.Column("accountId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBuckets.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBuckets.put("notStartedColor", new TableInfo.Column("notStartedColor", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBuckets.put("inProgressColor", new TableInfo.Column("inProgressColor", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBuckets.put("completedColor", new TableInfo.Column("completedColor", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBuckets.put("imageRef", new TableInfo.Column("imageRef", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBuckets.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBuckets.put("version", new TableInfo.Column("version", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBuckets.put("deleted", new TableInfo.Column("deleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBuckets.put("dirty", new TableInfo.Column("dirty", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBuckets = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBuckets = new HashSet<TableInfo.Index>(1);
        _indicesBuckets.add(new TableInfo.Index("index_buckets_accountId", false, Arrays.asList("accountId"), Arrays.asList("ASC")));
        final TableInfo _infoBuckets = new TableInfo("buckets", _columnsBuckets, _foreignKeysBuckets, _indicesBuckets);
        final TableInfo _existingBuckets = TableInfo.read(db, "buckets");
        if (!_infoBuckets.equals(_existingBuckets)) {
          return new RoomOpenHelper.ValidationResult(false, "buckets(com.sidequest.data.local.entity.BucketEntity).\n"
                  + " Expected:\n" + _infoBuckets + "\n"
                  + " Found:\n" + _existingBuckets);
        }
        final HashMap<String, TableInfo.Column> _columnsActionPlans = new HashMap<String, TableInfo.Column>(7);
        _columnsActionPlans.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionPlans.put("actionItemId", new TableInfo.Column("actionItemId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionPlans.put("subActions", new TableInfo.Column("subActions", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionPlans.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionPlans.put("version", new TableInfo.Column("version", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionPlans.put("deleted", new TableInfo.Column("deleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActionPlans.put("dirty", new TableInfo.Column("dirty", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysActionPlans = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesActionPlans = new HashSet<TableInfo.Index>(1);
        _indicesActionPlans.add(new TableInfo.Index("index_action_plans_actionItemId", false, Arrays.asList("actionItemId"), Arrays.asList("ASC")));
        final TableInfo _infoActionPlans = new TableInfo("action_plans", _columnsActionPlans, _foreignKeysActionPlans, _indicesActionPlans);
        final TableInfo _existingActionPlans = TableInfo.read(db, "action_plans");
        if (!_infoActionPlans.equals(_existingActionPlans)) {
          return new RoomOpenHelper.ValidationResult(false, "action_plans(com.sidequest.data.local.entity.ActionPlanEntity).\n"
                  + " Expected:\n" + _infoActionPlans + "\n"
                  + " Found:\n" + _existingActionPlans);
        }
        final HashMap<String, TableInfo.Column> _columnsVoiceJournalEntries = new HashMap<String, TableInfo.Column>(11);
        _columnsVoiceJournalEntries.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVoiceJournalEntries.put("accountId", new TableInfo.Column("accountId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVoiceJournalEntries.put("audioRef", new TableInfo.Column("audioRef", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVoiceJournalEntries.put("transcript", new TableInfo.Column("transcript", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVoiceJournalEntries.put("transcriptionFailed", new TableInfo.Column("transcriptionFailed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVoiceJournalEntries.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVoiceJournalEntries.put("extractedActionItemIds", new TableInfo.Column("extractedActionItemIds", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVoiceJournalEntries.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVoiceJournalEntries.put("version", new TableInfo.Column("version", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVoiceJournalEntries.put("deleted", new TableInfo.Column("deleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVoiceJournalEntries.put("dirty", new TableInfo.Column("dirty", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysVoiceJournalEntries = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesVoiceJournalEntries = new HashSet<TableInfo.Index>(2);
        _indicesVoiceJournalEntries.add(new TableInfo.Index("index_voice_journal_entries_accountId", false, Arrays.asList("accountId"), Arrays.asList("ASC")));
        _indicesVoiceJournalEntries.add(new TableInfo.Index("index_voice_journal_entries_createdAt", false, Arrays.asList("createdAt"), Arrays.asList("ASC")));
        final TableInfo _infoVoiceJournalEntries = new TableInfo("voice_journal_entries", _columnsVoiceJournalEntries, _foreignKeysVoiceJournalEntries, _indicesVoiceJournalEntries);
        final TableInfo _existingVoiceJournalEntries = TableInfo.read(db, "voice_journal_entries");
        if (!_infoVoiceJournalEntries.equals(_existingVoiceJournalEntries)) {
          return new RoomOpenHelper.ValidationResult(false, "voice_journal_entries(com.sidequest.data.local.entity.VoiceJournalEntryEntity).\n"
                  + " Expected:\n" + _infoVoiceJournalEntries + "\n"
                  + " Found:\n" + _existingVoiceJournalEntries);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "43002a8290195fdbeea5389f39c3a27b", "ffeebeaa08b7c98cca76dd41002c13ae");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "action_items","buckets","action_plans","voice_journal_entries");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `action_items`");
      _db.execSQL("DELETE FROM `buckets`");
      _db.execSQL("DELETE FROM `action_plans`");
      _db.execSQL("DELETE FROM `voice_journal_entries`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ActionItemDao.class, ActionItemDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(BucketDao.class, BucketDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ActionPlanDao.class, ActionPlanDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(VoiceJournalDao.class, VoiceJournalDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public ActionItemDao actionItemDao() {
    if (_actionItemDao != null) {
      return _actionItemDao;
    } else {
      synchronized(this) {
        if(_actionItemDao == null) {
          _actionItemDao = new ActionItemDao_Impl(this);
        }
        return _actionItemDao;
      }
    }
  }

  @Override
  public BucketDao bucketDao() {
    if (_bucketDao != null) {
      return _bucketDao;
    } else {
      synchronized(this) {
        if(_bucketDao == null) {
          _bucketDao = new BucketDao_Impl(this);
        }
        return _bucketDao;
      }
    }
  }

  @Override
  public ActionPlanDao actionPlanDao() {
    if (_actionPlanDao != null) {
      return _actionPlanDao;
    } else {
      synchronized(this) {
        if(_actionPlanDao == null) {
          _actionPlanDao = new ActionPlanDao_Impl(this);
        }
        return _actionPlanDao;
      }
    }
  }

  @Override
  public VoiceJournalDao voiceJournalDao() {
    if (_voiceJournalDao != null) {
      return _voiceJournalDao;
    } else {
      synchronized(this) {
        if(_voiceJournalDao == null) {
          _voiceJournalDao = new VoiceJournalDao_Impl(this);
        }
        return _voiceJournalDao;
      }
    }
  }
}
