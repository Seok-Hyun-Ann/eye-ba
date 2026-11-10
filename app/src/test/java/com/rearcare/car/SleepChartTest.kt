package com.rearcare.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 주행 중 수면 막대의 표지판과 날짜 글자 시험 */
class SleepChartTest {

    private val birth = LocalDate.of(2021, 3, 10)
    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun `비어 있는 나이도 자리를 두고 누르지 못하게 한다`() {
        val tabs = SleepChart.tabs(listOf(d("2022-04-01"), d("2024-04-01"), d("2028-04-01")), birth)
        assertEquals(listOf("1살", "2살", "3살", "4살", "5살", "6살", "7살"), tabs.map { it.label })
        assertEquals(listOf(1, 3, 7), tabs.filter { it.hasRecords }.map { it.key })
    }

    @Test
    fun `생일 전과 돌 전은 기록이 있을 때만 둔다`() {
        assertEquals(listOf("3살", "4살"), SleepChart.tabs(listOf(d("2024-04-01"), d("2025-04-01")), birth).map { it.label })
        val tabs = SleepChart.tabs(listOf(d("2021-01-01"), d("2021-06-01"), d("2024-04-01")), birth)
        assertEquals(listOf("생일 전", "돌 전", "3살"), tabs.map { it.label })
    }

    @Test
    fun `생일을 모르면 연도로 묶는다`() {
        val tabs = SleepChart.tabs(listOf(d("2024-04-01"), d("2026-04-01")), null)
        assertEquals(listOf("2024년", "2025년", "2026년"), tabs.map { it.label })
        assertEquals(listOf(true, false, true), tabs.map { it.hasRecords })
    }

    @Test
    fun `기록이 없으면 표지판도 없다`() {
        assertTrue(SleepChart.tabs(emptyList(), birth).isEmpty())
    }

    @Test
    fun `나이가 바뀌는 날은 생일이다`() {
        assertEquals(3, SleepChart.keyOf(d("2025-03-09"), birth))
        assertEquals(4, SleepChart.keyOf(d("2025-03-10"), birth))
    }

    @Test
    fun `첫 막대는 달만 쓰고 같은 달은 다시 쓰지 않는다`() {
        assertEquals(listOf(SleepChart.Mark("11월", null), null, SleepChart.Mark("12월", null)), SleepChart.marks(listOf(d("2028-11-04"), d("2028-11-11"), d("2028-12-02"))))
    }

    @Test
    fun `해가 바뀌는 막대에는 달 아래에 해를 쓴다`() {
        assertEquals(
            listOf(SleepChart.Mark("12월", null), SleepChart.Mark("1월", "2029년"), SleepChart.Mark("2월", null)),
            SleepChart.marks(listOf(d("2028-12-30"), d("2029-01-01"), d("2029-02-03"))),
        )
    }

    @Test
    fun `기록이 없는 달을 건너뛰어도 달이 바뀌면 쓴다`() {
        assertEquals(listOf(SleepChart.Mark("3월", null), SleepChart.Mark("7월", null)), SleepChart.marks(listOf(d("2025-03-20"), d("2025-07-02"))))
    }

    @Test
    fun `달 화살표는 기록이 있는 달의 첫 막대로만 간다`() {
        val dates = listOf(d("2025-03-20"), d("2025-03-25"), d("2025-07-02"), d("2025-07-09"), d("2026-01-03"))
        assertEquals(listOf(0, 2, 4), SleepChart.monthStarts(dates))
    }

    @Test
    fun `막대가 어느 달에 드는지 찾는다`() {
        val starts = listOf(0, 2, 4)
        assertEquals(0, SleepChart.monthAt(starts, 1))
        assertEquals(1, SleepChart.monthAt(starts, 2))
        assertEquals(2, SleepChart.monthAt(starts, 9))
    }

    @Test
    fun `달 글자에는 해가 함께 들어간다`() {
        assertEquals("2025년 8월", SleepChart.monthLabel(d("2025-08-03")))
    }

    @Test
    fun `말풍선이 겹치지 않으면 한 줄에 놓는다`() {
        assertEquals(listOf(0, 0), SleepChart.bubbleRows(listOf(0f to 100f, 120f to 200f), 10f))
    }

    @Test
    fun `말풍선이 겹치면 위 줄로 올리고 빈 줄이 있으면 다시 쓴다`() {
        assertEquals(listOf(0, 1, 0), SleepChart.bubbleRows(listOf(0f to 100f, 50f to 150f, 300f to 400f), 10f))
    }
}
