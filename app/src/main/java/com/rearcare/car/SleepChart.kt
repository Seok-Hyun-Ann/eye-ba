package com.rearcare.car

import java.time.LocalDate

/**
 * 주행 중 수면 막대의 표지판과 날짜 글자를 정한다. 순수 Kotlin이라 단위 시험으로 본다.
 * 표지판 하나를 고르면 그 나이의 기록만 보인다. 생일을 모르면 나이 대신 연도로 묶는다.
 */
object SleepChart {

    data class Tab(val key: Int, val label: String, val hasRecords: Boolean)

    /** 막대 아래에 쓸 글자. 달은 윗줄, 해가 바뀌면 아랫줄에 해를 쓴다 */
    data class Mark(val month: String, val year: String?)

    /** 이 날짜가 속한 표지판. 생일을 알면 나이, 모르면 연도다 */
    fun keyOf(date: LocalDate, birth: LocalDate?): Int = if (birth == null) date.year else TripDiary.age(birth, date)

    /**
     * 표지판 목록. 처음 기록부터 마지막 기록까지 사이가 비어도 자리를 두고, 비어 있는 자리는 누르지 못하게 한다.
     * "생일 전"과 "돌 전"은 기록이 있을 때만 둔다.
     */
    fun tabs(dates: List<LocalDate>, birth: LocalDate?): List<Tab> {
        val keys = dates.map { keyOf(it, birth) }.toSet()
        if (keys.isEmpty()) return emptyList()
        if (birth == null) return (keys.min()..keys.max()).map { Tab(it, "${it}년", it in keys) }
        val early = keys.filter { it < 1 }.sorted().map { Tab(it, TripDiary.ageLabel(it), true) }
        val grown = keys.filter { it >= 1 }
        val ages = if (grown.isEmpty()) emptyList() else (grown.min()..grown.max()).map { Tab(it, TripDiary.ageLabel(it), it in keys) }
        return early + ages
    }

    /** 막대마다 길 아래에 쓸 글자. 달이 바뀌는 막대에 달을, 해가 바뀌는 막대에는 해도 함께 쓴다. 나머지는 null */
    fun marks(dates: List<LocalDate>): List<Mark?> = dates.mapIndexed { i, date ->
        val before = dates.getOrNull(i - 1)
        when {
            before == null -> Mark("${date.monthValue}월", null)
            before.year != date.year -> Mark("${date.monthValue}월", "${date.year}년")
            before.monthValue != date.monthValue -> Mark("${date.monthValue}월", null)
            else -> null
        }
    }

    /** 달이 바뀌는 막대의 순서. 달 화살표가 넘어갈 자리다. 기록이 없는 달은 들어가지 않는다 */
    fun monthStarts(dates: List<LocalDate>): List<Int> =
        marks(dates).withIndex().filter { it.value != null }.map { it.index }

    /** index번째 막대가 monthStarts의 몇 번째 달에 드는가 */
    fun monthAt(starts: List<Int>, index: Int): Int = starts.indexOfLast { it <= index }.coerceAtLeast(0)

    /** 왼쪽부터 놓은 말풍선의 (왼쪽, 오른쪽)을 받아 줄 번호를 돌려준다. 앞 말풍선과 겹치면 위 줄로 올린다. 0번이 길에 가장 가깝다 */
    fun bubbleRows(edges: List<Pair<Float, Float>>, gap: Float): List<Int> {
        val rowEnds = mutableListOf<Float>()
        return edges.map { (left, right) ->
            val found = rowEnds.indexOfFirst { left >= it + gap }
            val row = if (found >= 0) found else rowEnds.size
            if (row == rowEnds.size) rowEnds.add(right) else rowEnds[row] = right
            row
        }
    }

    fun monthLabel(date: LocalDate) = "${date.year}년 ${date.monthValue}월"
}
