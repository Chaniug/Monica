package takagi.ru.monica.steam.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.importer.SteamMaFilePayload

class SteamAccountRepository(
    private val dao: SteamAccountDao,
    private val securityManager: SecurityManager
) {
    fun observeAccounts(): Flow<List<SteamAccount>> {
        return dao.observeAccounts().map { accounts -> accounts.map(::decryptEntity) }
    }

    suspend fun getAccounts(): List<SteamAccount> = dao.getAccounts().map(::decryptEntity)

    suspend fun getAccount(id: Long): SteamAccount? = dao.getById(id)?.let(::decryptEntity)

    suspend fun getSelectedAccount(): SteamAccount? {
        return dao.getSelected()?.let(::decryptEntity)
            ?: dao.getAccounts().firstOrNull()?.let(::decryptEntity)
    }

    suspend fun upsertFromMaFile(payload: SteamMaFilePayload): Long {
        val now = System.currentTimeMillis()
        val existing = dao.getBySteamId(payload.steamId)
        val shouldSelect = existing?.selected ?: (dao.count() == 0)
        val entity = SteamAccountEntity(
            id = existing?.id ?: 0L,
            steamId = payload.steamId,
            accountName = payload.accountName,
            displayName = payload.displayName,
            deviceId = payload.deviceId,
            sharedSecret = encrypt(payload.sharedSecret),
            identitySecret = payload.identitySecret?.let(::encrypt),
            revocationCode = payload.revocationCode?.let(::encrypt),
            tokenGid = payload.tokenGid?.let(::encrypt),
            accessToken = payload.accessToken?.let(::encrypt),
            refreshToken = payload.refreshToken?.let(::encrypt),
            steamLoginSecure = payload.steamLoginSecure?.let(::encrypt),
            rawSteamGuardJson = encrypt(payload.rawJson),
            selected = shouldSelect,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        val id = if (existing == null) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            existing.id
        }
        if (shouldSelect) dao.selectAccount(id)
        return id
    }

    suspend fun updateDisplayName(id: Long, displayName: String) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                displayName = displayName.trim().ifBlank { existing.accountName },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(id: Long) {
        val wasSelected = dao.getById(id)?.selected == true
        dao.deleteById(id)
        if (wasSelected) {
            dao.getAccounts().firstOrNull()?.let { dao.selectAccount(it.id) }
        }
    }

    suspend fun select(id: Long) {
        dao.selectAccount(id)
    }

    private fun encrypt(value: String): String {
        return securityManager.encryptDataLegacyCompat(value)
    }

    private fun decrypt(value: String?): String? {
        return value?.let { securityManager.decryptDataIfMonicaCiphertext(it) }
    }

    private fun decryptEntity(entity: SteamAccountEntity): SteamAccount {
        return SteamAccount(
            id = entity.id,
            steamId = entity.steamId,
            accountName = entity.accountName,
            displayName = entity.displayName,
            deviceId = entity.deviceId,
            sharedSecret = decrypt(entity.sharedSecret).orEmpty(),
            identitySecret = decrypt(entity.identitySecret),
            revocationCode = decrypt(entity.revocationCode),
            tokenGid = decrypt(entity.tokenGid),
            accessToken = decrypt(entity.accessToken),
            refreshToken = decrypt(entity.refreshToken),
            steamLoginSecure = decrypt(entity.steamLoginSecure),
            rawSteamGuardJson = decrypt(entity.rawSteamGuardJson).orEmpty(),
            selected = entity.selected,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
