package app.yoniso.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BellProblemsTest {

    private val on = BellSettings(enabled = true)

    private fun problems(
        settings: BellSettings = on,
        exact: Boolean = true,
        accessibility: Boolean = true,
        dnd: Boolean = false,
        silent: Boolean = false,
    ) = BellProblems.of(settings, exact, accessibility, dnd, silent)

    @Test
    fun `none when all is well, or when bells are off`() {
        assertEquals(emptyList<BellProblem>(), problems())
        assertEquals(emptyList<BellProblem>(), problems(BellSettings(enabled = false), exact = false, dnd = true))
    }

    @Test
    fun `no days`() {
        val noDays = on.copy(activeHours = ActiveHours(days = emptySet()))
        assertEquals(listOf(BellProblem.NO_DAYS), problems(noDays))
    }

    @Test
    fun `do not disturb outranks silent`() {
        assertEquals(listOf(BellProblem.DO_NOT_DISTURB), problems(dnd = true, silent = true))
        assertEquals(listOf(BellProblem.SILENT), problems(silent = true))
    }

    @Test
    fun `without exact alarms a bell may be late, or only vibrate without accessibility`() {
        assertEquals(listOf(BellProblem.MAY_BE_LATE), problems(exact = false))
        assertEquals(listOf(BellProblem.VIBRATE_ONLY), problems(exact = false, accessibility = false))
    }

    @Test
    fun `vibration alone needs no accessibility`() {
        val vibrate = on.copy(output = BellOutput.VIBRATE)
        assertEquals(listOf(BellProblem.MAY_BE_LATE), problems(vibrate, exact = false, accessibility = false))
    }
}
