package com.sidequest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.sidequest.data.local.entity.VoiceJournalEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for [VoiceJournalEntryEntity] (Req 10.4).
 *
 * Reactive reads are exposed as [Flow] so the UI updates whenever entries
 * change; tombstoned rows are excluded. Per-account reads are ordered
 * newest-first (descending [VoiceJournalEntryEntity.createdAt]) so the most
 * recent recordings surface first. Writes cover insert, update, upsert and
 * delete so a captured entry persists and later transcription/extraction
 * updates (Req 10.4) are reflected.
 */
@Dao
interface VoiceJournalDao {

    @Query("SELECT * FROM voice_journal_entries WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VoiceJournalEntryEntity>>

    @Query(
        "SELECT * FROM voice_journal_entries WHERE accountId = :accountId AND deleted = 0 " +
            "ORDER BY createdAt DESC",
    )
    fun observeByAccount(accountId: String): Flow<List<VoiceJournalEntryEntity>>

    @Query("SELECT * FROM voice_journal_entries WHERE id = :id")
    fun observeById(id: String): Flow<VoiceJournalEntryEntity?>

    @Query("SELECT * FROM voice_journal_entries WHERE id = :id")
    suspend fun getById(id: String): VoiceJournalEntryEntity?

    @Query(
        "SELECT * FROM voice_journal_entries WHERE accountId = :accountId AND deleted = 0 " +
            "ORDER BY createdAt DESC",
    )
    suspend fun getByAccount(accountId: String): List<VoiceJournalEntryEntity>

    @Query("SELECT * FROM voice_journal_entries ORDER BY createdAt DESC")
    suspend fun getAll(): List<VoiceJournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VoiceJournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<VoiceJournalEntryEntity>)

    @Update
    suspend fun update(entry: VoiceJournalEntryEntity)

    @Upsert
    suspend fun upsert(entry: VoiceJournalEntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<VoiceJournalEntryEntity>)

    @Delete
    suspend fun delete(entry: VoiceJournalEntryEntity)

    @Query("DELETE FROM voice_journal_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM voice_journal_entries")
    suspend fun clear()
}
