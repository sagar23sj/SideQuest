package com.sidequest.domain

/**
 * The :domain module holds the client's pure, portable logic (classification,
 * board aggregation, validation, completion counting, due-set computation,
 * games scoring/generation, leaderboard aggregation, sync conflict resolution).
 *
 * It has no Android dependencies so the same logic can be reused and validated
 * with the shared Correctness Properties. Concrete types and logic are added in
 * subsequent tasks (domain models, classification, etc.).
 */
internal object Domain
