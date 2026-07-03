package takagi.ru.monica.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun compareVersionTags_detectsNewerRelease() {
        assertTrue(UpdateChecker.compareVersionTags("v1.0.293", "1.0.292") > 0)
    }

    @Test
    fun compareVersionTags_treatsMatchingPrefixAsSameVersion() {
        assertEquals(0, UpdateChecker.compareVersionTags("v1.0.292", "1.0.292-preview"))
    }

    @Test
    fun compareVersionTags_ignoresReleaseLetterSuffix() {
        assertEquals(0, UpdateChecker.compareVersionTags("V1.0.292c", "1.0.292"))
        assertEquals(0, UpdateChecker.compareVersionTags("V1.0.292c", "V1.0.292b"))
    }

    @Test
    fun compareVersionTags_detectsOlderRelease() {
        assertTrue(UpdateChecker.compareVersionTags("1.0.287", "1.0.292") < 0)
    }
}
