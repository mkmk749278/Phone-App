package com.dualshield.phone.data.repository

import com.dualshield.phone.data.db.dao.BlockedCallDao
import com.dualshield.phone.data.db.dao.BlockedMessageDao
import com.dualshield.phone.data.db.entity.BlockedCallEntity
import com.dualshield.phone.data.db.entity.BlockedMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * The Shield Vault: the private record of everything Shield kept away from the user.
 *
 * Clearing the Vault is explicitly a history operation. It deletes records and nothing
 * else — rules, allowlist entries and SIM settings are untouched.
 */
class VaultRepository(
    private val blockedCallDao: BlockedCallDao,
    private val blockedMessageDao: BlockedMessageDao,
) {

    fun observeBlockedCalls(): Flow<List<BlockedCallEntity>> = blockedCallDao.observeRecent()

    fun observeBlockedCall(id: Long): Flow<BlockedCallEntity?> = blockedCallDao.observeById(id)

    fun observeBlockedCallCount(): Flow<Int> = blockedCallDao.observeCount()

    fun observeBlockedCallCountSince(since: Long): Flow<Int> =
        blockedCallDao.observeCountSince(since)

    fun observeBlockedMessages(): Flow<List<BlockedMessageEntity>> =
        blockedMessageDao.observeRecent()

    fun observeBlockedMessageCount(): Flow<Int> = blockedMessageDao.observeCount()

    suspend fun record(entity: BlockedCallEntity): Long = blockedCallDao.insert(entity)

    suspend fun recordMessage(entity: BlockedMessageEntity): Long =
        blockedMessageDao.insert(entity)

    suspend fun deleteBlockedCall(id: Long) = blockedCallDao.deleteById(id)

    suspend fun deleteBlockedCalls(ids: List<Long>) = blockedCallDao.deleteByIds(ids)

    suspend fun clearBlockedCalls() = blockedCallDao.clear()

    suspend fun clearBlockedMessages() = blockedMessageDao.clear()
}
