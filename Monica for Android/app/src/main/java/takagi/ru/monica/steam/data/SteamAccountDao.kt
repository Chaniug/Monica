package takagi.ru.monica.steam.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SteamAccountDao {
    @Query("SELECT * FROM steam_accounts ORDER BY selected DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<SteamAccountEntity>>

    @Query("SELECT * FROM steam_accounts ORDER BY selected DESC, updatedAt DESC")
    suspend fun getAccounts(): List<SteamAccountEntity>

    @Query("SELECT * FROM steam_accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SteamAccountEntity?

    @Query("SELECT * FROM steam_accounts WHERE steam_id = :steamId LIMIT 1")
    suspend fun getBySteamId(steamId: String): SteamAccountEntity?

    @Query("SELECT * FROM steam_accounts WHERE selected = 1 LIMIT 1")
    suspend fun getSelected(): SteamAccountEntity?

    @Query("SELECT COUNT(*) FROM steam_accounts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: SteamAccountEntity): Long

    @Update
    suspend fun update(account: SteamAccountEntity)

    @Query("DELETE FROM steam_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE steam_accounts SET selected = 0")
    suspend fun clearSelected()

    @Query("UPDATE steam_accounts SET selected = 1 WHERE id = :id")
    suspend fun markSelected(id: Long)

    @Transaction
    suspend fun selectAccount(id: Long) {
        clearSelected()
        markSelected(id)
    }
}
