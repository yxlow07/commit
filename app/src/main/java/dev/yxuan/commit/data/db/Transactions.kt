package dev.yxuan.commit.data.db

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection

/**
 * Runs [block] in one write transaction (replacement for Room 2's `withTransaction`).
 * DAO calls made inside the block use the same writer connection, so they are part of it.
 */
suspend fun <T> AppDatabase.withTransaction(block: suspend () -> T): T =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
