package net.lumalyte.lumasg.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.transaction

/** Execute a database transaction on the IO dispatcher (never blocks main thread). */
suspend fun <T> dbQuery(block: () -> T): T =
    withContext(Dispatchers.IO) { transaction { block() } }
