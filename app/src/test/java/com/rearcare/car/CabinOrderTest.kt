package com.rearcare.car

import org.junit.Assert.assertEquals
import org.junit.Test

/** 공조 값을 하나씩 차례로 바꾸는 순서 규칙(CabinController.runInOrder) */
class CabinOrderTest {

    @Test
    fun 앞의_일이_끝났다고_알려야_다음_일을_시작한다() {
        val started = mutableListOf<String>()
        val doneOf = mutableMapOf<String, () -> Unit>()
        fun step(name: String): (() -> Unit) -> Unit = { done -> started += name; doneOf[name] = done }

        CabinController.runInOrder(listOf(step("운전석 온도"), step("조수석 온도"), step("바람")))
        assertEquals(listOf("운전석 온도"), started)

        doneOf.getValue("운전석 온도")()
        assertEquals(listOf("운전석 온도", "조수석 온도"), started)

        doneOf.getValue("조수석 온도")()
        assertEquals(listOf("운전석 온도", "조수석 온도", "바람"), started)
    }

    @Test
    fun 끝났다고_두_번_알려도_다음_일은_한_번만_한다() {
        val started = mutableListOf<String>()
        var firstDone: (() -> Unit)? = null
        CabinController.runInOrder(listOf(
            { done -> started += "온도"; firstDone = done },
            { _ -> started += "바람" },
        ))
        firstDone!!()
        firstDone!!()
        assertEquals(listOf("온도", "바람"), started)
    }

    @Test
    fun 바로_끝나는_일도_차례대로_모두_한다() {
        val started = mutableListOf<Int>()
        CabinController.runInOrder((1..3).map { n -> { done: () -> Unit -> started += n; done() } })
        assertEquals(listOf(1, 2, 3), started)
    }

    @Test
    fun 할_일이_없으면_아무것도_하지_않는다() {
        CabinController.runInOrder(emptyList())
    }
}
