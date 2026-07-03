package takagi.ru.monica.steam.ui

import android.content.Context

private const val STEAM_QR_PREFS_NAME = "steam_qr_preferences"
private const val KEY_LAST_ACCOUNT_ID = "last_account_id"

internal fun readLastSteamQrAccountId(context: Context): Long? {
    val accountId = context.applicationContext
        .getSharedPreferences(STEAM_QR_PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(KEY_LAST_ACCOUNT_ID, -1L)
    return accountId.takeIf { it > 0L }
}

internal fun saveLastSteamQrAccountId(context: Context, accountId: Long?) {
    context.applicationContext
        .getSharedPreferences(STEAM_QR_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .apply {
            if (accountId != null && accountId > 0L) {
                putLong(KEY_LAST_ACCOUNT_ID, accountId)
            } else {
                remove(KEY_LAST_ACCOUNT_ID)
            }
        }
        .apply()
}
