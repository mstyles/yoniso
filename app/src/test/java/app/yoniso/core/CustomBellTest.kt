package app.yoniso.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomBellTest {

    private val uuid = "0f8fad5b-d9cb-469f-a165-70867728950e"

    @Test
    fun `id round-trips to the file name`() {
        val bell = CustomBell(uuid, "Gong")
        assertEquals("custom_$uuid", bell.id)
        assertEquals(uuid, CustomBell.fileNameOf(bell.id))
    }

    @Test
    fun `ids that could escape the bells folder are refused`() {
        assertNull(CustomBell.fileNameOf("custom_../settings"))
        assertNull(CustomBell.fileNameOf("custom_"))
        assertNull(CustomBell.fileNameOf("plum_village"))
        assertNull(CustomBell.fileNameOf(uuid))
        assertNull(CustomBell.fileNameOf("custom_$uuid/x"))
    }

    @Test
    fun `a short small file is fine`() {
        assertNull(CustomBellRules.check(sizeBytes = 300_000, durationMillis = 20_000))
        assertNull(CustomBellRules.check(CustomBellRules.MAX_BYTES, CustomBellRules.MAX_DURATION_MILLIS))
    }

    @Test
    fun `too large is reported before anything else`() {
        assertEquals(CustomBellProblem.TOO_LARGE, CustomBellRules.check(CustomBellRules.MAX_BYTES + 1, null))
    }

    @Test
    fun `too long`() {
        assertEquals(CustomBellProblem.TOO_LONG, CustomBellRules.check(1_000, CustomBellRules.MAX_DURATION_MILLIS + 1))
    }

    @Test
    fun `unreadable when empty or without a duration`() {
        assertEquals(CustomBellProblem.UNREADABLE, CustomBellRules.check(0, 5_000))
        assertEquals(CustomBellProblem.UNREADABLE, CustomBellRules.check(1_000, null))
        assertEquals(CustomBellProblem.UNREADABLE, CustomBellRules.check(1_000, 0))
    }

    @Test
    fun `default name drops the extension and underscores`() {
        assertEquals("temple gong", CustomBellRules.defaultName("temple_gong.mp3"))
        assertEquals("bowl.v2", CustomBellRules.defaultName("bowl.v2.ogg"))
        assertEquals("bell", CustomBellRules.defaultName("bell"))
    }

    @Test
    fun `default name falls back when there is nothing to use`() {
        assertEquals(CustomBellRules.FALLBACK_NAME, CustomBellRules.defaultName(null))
        assertEquals(CustomBellRules.FALLBACK_NAME, CustomBellRules.defaultName(".mp3"))
        assertEquals(CustomBellRules.FALLBACK_NAME, CustomBellRules.defaultName("  .m4a"))
    }

    @Test
    fun `names are put on one line, trimmed and cut`() {
        assertEquals("a b c", CustomBellRules.cleanName("  a\tb\n\nc "))
        assertNull(CustomBellRules.cleanName(" \n "))
        assertEquals(CustomBellRules.MAX_NAME_LENGTH, CustomBellRules.cleanName("x".repeat(100))!!.length)
    }

    @Test
    fun `the list round-trips in order`() {
        val other = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
        val bells = listOf(CustomBell(uuid, "Gong"), CustomBell(other, "Rain\tstick"))
        assertEquals(
            listOf(CustomBell(uuid, "Gong"), CustomBell(other, "Rain stick")),
            CustomBellRules.decode(CustomBellRules.encode(bells)),
        )
    }

    @Test
    fun `malformed and repeated entries are dropped`() {
        val stored = "$uuid\tGong\nnot-a-uuid\tBad\n$uuid\tAgain\n\n7c9e6679-7425-40de-944b-e07fc1f90ae7\t "
        assertEquals(
            listOf(
                CustomBell(uuid, "Gong"),
                CustomBell("7c9e6679-7425-40de-944b-e07fc1f90ae7", CustomBellRules.FALLBACK_NAME),
            ),
            CustomBellRules.decode(stored),
        )
        assertEquals(emptyList<CustomBell>(), CustomBellRules.decode(null))
        assertEquals(emptyList<CustomBell>(), CustomBellRules.decode(""))
    }
}
