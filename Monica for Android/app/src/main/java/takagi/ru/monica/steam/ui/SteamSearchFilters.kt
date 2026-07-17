package takagi.ru.monica.steam.ui

import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamConfirmation

internal fun filterSteamAccounts(
    accounts: List<SteamAccount>,
    query: String
): List<SteamAccount> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return accounts

    return accounts.filter { account ->
        account.accountName.contains(normalizedQuery, ignoreCase = true) ||
            account.displayName.contains(normalizedQuery, ignoreCase = true) ||
            account.steamId.contains(normalizedQuery, ignoreCase = true)
    }
}

internal fun filterSteamConfirmations(
    confirmations: List<SteamConfirmation>,
    query: String
): List<SteamConfirmation> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return confirmations

    return confirmations.filter { confirmation ->
        confirmation.headline.contains(normalizedQuery, ignoreCase = true) ||
            confirmation.summary.contains(normalizedQuery, ignoreCase = true) ||
            confirmation.type.contains(normalizedQuery, ignoreCase = true)
    }
}
