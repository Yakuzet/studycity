package com.derscalisma.app

import com.derscalisma.app.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeadlineTest {
    private val start = LocalDate.of(2026, 12, 1)

    private fun goal(total: Int = 100, days: Long = 10, linked: String? = "g_pages", manual: Int = 0) =
        DeadlineGoal("d1", "Fizik", "sayfa", total, start.toString(), start.plusDays(days - 1).toString(), linked, manual)

    private fun data(g: DeadlineGoal, vararg pages: Pair<LocalDate, Int>) = AppData(
        deadlines = listOf(g),
        days = pages.associate { (d, n) -> d.toString() to DayRecord(progress = mapOf("g_pages" to n)) },
    )

    @Test
    fun linkedProgressCountsOnlyInsideDateRange() {
        val g = goal()
        val d = data(g, start.minusDays(1) to 50, start to 10, start.plusDays(1) to 15)
        assertEquals(25, Deadlines.progress(d, g))
    }

    @Test
    fun perDayAndPace() {
        val g = goal(total = 100, days = 10)
        // 3. günün başındayız, 2 günde 15 sayfa yapılmış: plan 20 olmalıydı -> geride
        val d = data(g, start to 10, start.plusDays(1) to 5)
        val st = Deadlines.status(d, g, start.plusDays(2))
        assertEquals(8, st.daysLeft)
        assertEquals(20, st.expected)
        assertEquals(Pace.BEHIND, st.pace)
        assertEquals(11, st.perDay) // ceil(85 / 8)
    }

    @Test
    fun aheadAndDone() {
        val g = goal(total = 100, days = 10)
        val ahead = Deadlines.status(data(g, start to 40), g, start.plusDays(1))
        assertEquals(Pace.AHEAD, ahead.pace)
        val done = Deadlines.status(data(g.copy(manual = 100)), g.copy(manual = 100), start.plusDays(3))
        assertEquals(Pace.DONE, done.pace)
        assertTrue(Deadlines.reward(done) > 200)
    }
}
