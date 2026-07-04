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
    }
}
