package com.sidequest.data.local.entity

import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionPlan
import com.sidequest.domain.model.Bucket
import com.sidequest.domain.model.VoiceJournalEntry

/**
 * Pure mapping functions between the Room entities (app module) and the pure
 * domain models (`:domain` module).
 *
 * Keeping the `:domain` types free of Android/Room annotations means the entity
 * classes carry the persistence concerns while these mappers translate at the
 * boundary. The functions are total and side-effect free so they are trivially
 * testable (e.g. in a write→reload round-trip test).
 */

// --- ActionItem ----------------------------------------------------------

fun ActionItem.toEntity(): ActionItemEntity = ActionItemEntity(
    id = id,
    accountId = accountId,
    bucketId = bucketId,
    title = title,
    description = description,
    contentType = contentType,
    sourceContent = sourceContent,
    preview = preview,
    timeframe = timeframe,
    status = status,
    createdAt = createdAt,
    reminder = reminder,
    sync = sync,
)

fun ActionItemEntity.toDomain(): ActionItem = ActionItem(
    id = id,
    accountId = accountId,
    bucketId = bucketId,
    title = title,
    description = description,
    contentType = contentType,
    sourceContent = sourceContent,
    preview = preview,
    timeframe = timeframe,
    status = status,
    createdAt = createdAt,
    reminder = reminder,
    sync = sync,
)

// --- Bucket --------------------------------------------------------------

fun Bucket.toEntity(): BucketEntity = BucketEntity(
    id = id,
    accountId = accountId,
    name = name,
    notStartedColor = notStartedColor,
    inProgressColor = inProgressColor,
    completedColor = completedColor,
    imageRef = imageRef,
    position = position,
    sync = sync,
)

fun BucketEntity.toDomain(): Bucket = Bucket(
    id = id,
    accountId = accountId,
    name = name,
    notStartedColor = notStartedColor,
    inProgressColor = inProgressColor,
    completedColor = completedColor,
    imageRef = imageRef,
    position = position,
    sync = sync,
)

// --- ActionPlan ----------------------------------------------------------

fun ActionPlan.toEntity(): ActionPlanEntity = ActionPlanEntity(
    id = id,
    actionItemId = actionItemId,
    subActions = subActions,
    sync = sync,
)

fun ActionPlanEntity.toDomain(): ActionPlan = ActionPlan(
    id = id,
    actionItemId = actionItemId,
    subActions = subActions,
    sync = sync,
)

// --- VoiceJournalEntry ---------------------------------------------------

fun VoiceJournalEntry.toEntity(): VoiceJournalEntryEntity = VoiceJournalEntryEntity(
    id = id,
    accountId = accountId,
    audioRef = audioRef,
    transcript = transcript,
    transcriptionFailed = transcriptionFailed,
    createdAt = createdAt,
    extractedActionItemIds = extractedActionItemIds,
    sync = sync,
)

fun VoiceJournalEntryEntity.toDomain(): VoiceJournalEntry = VoiceJournalEntry(
    id = id,
    accountId = accountId,
    audioRef = audioRef,
    transcript = transcript,
    transcriptionFailed = transcriptionFailed,
    createdAt = createdAt,
    extractedActionItemIds = extractedActionItemIds,
    sync = sync,
)

// --- Collection helpers --------------------------------------------------

fun List<ActionItemEntity>.toActionItems(): List<ActionItem> = map { it.toDomain() }

fun List<BucketEntity>.toBuckets(): List<Bucket> = map { it.toDomain() }

fun List<ActionPlanEntity>.toActionPlans(): List<ActionPlan> = map { it.toDomain() }

fun List<VoiceJournalEntryEntity>.toVoiceJournalEntries(): List<VoiceJournalEntry> =
    map { it.toDomain() }
