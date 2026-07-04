package takagi.ru.monica.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.network.SteamQrChallenge

class SteamQrChallengeParserTest {
    @Test
    fun parsesStandardSteamQrLink() {
        val challenge = SteamQrChallenge.parse("https://s.team/q/1/123456789")

        assertEquals(1, challenge?.version)
        assertEquals(123456789L, challenge?.clientId)
    }

    @Test
    fun parsesSteamQrLinkInsideScannedText() {
        val challenge = SteamQrChallenge.parse("Login request: <https://s.team/q/2/987654321>, approve?")

        assertEquals(2, challenge?.version)
        assertEquals(987654321L, challenge?.clientId)
    }

    @Test
    fun parsesSteamCommunityHostVariants() {
        val community = SteamQrChallenge.parse("https://steamcommunity.com/q/3/42?foo=bar")
        val wwwCommunity = SteamQrChallenge.parse("https://www.steamcommunity.com/q/4/43")

        assertEquals(3, community?.version)
        assertEquals(42L, community?.clientId)
        assertEquals(4, wwwCommunity?.version)
        assertEquals(43L, wwwCommunity?.clientId)
    }

    @Test
    fun parsesSteamOpenUrlWrappedQrLink() {
        val challenge = SteamQrChallenge.parse(
            "steam://openurl/https%3A%2F%2Fs.team%2Fq%2F3%2F111222333%3Futm%3Dscan"
        )

        assertEquals(3, challenge?.version)
        assertEquals(111222333L, challenge?.clientId)
    }

    @Test
    fun rejectsNonSteamQrPayloads() {
        assertNull(SteamQrChallenge.parse("1234567890"))
        assertNull(SteamQrChallenge.parse("otpauth://totp/Steam:user?secret=ABC"))
        assertNull(SteamQrChallenge.parse("https://example.com/not-steam"))
        assertNull(SteamQrChallenge.parse("https://evil.example/q/1/123"))
        assertNull(SteamQrChallenge.parse("http://s.team/q/1/123"))
        assertNull(SteamQrChallenge.parse("https://s.team/q/abc/123"))
    }
}
