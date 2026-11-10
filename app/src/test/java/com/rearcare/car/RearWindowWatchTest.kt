package com.rearcare.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 뒷창문이 열리는 순간만 알리는지 본다 */
class RearWindowWatchTest {

    private val left = "ROW_2_LEFT"
    private val right = "ROW_2_RIGHT"

    @Test
    fun 처음_읽은_값은_알리지_않음() {
        val w = RearWindowWatch()
        assertFalse(w.update(left, 0, driving = true))
        assertFalse(RearWindowWatch().update(left, 5, driving = true)) // 켰을 때 이미 열려 있음
    }

    @Test
    fun 주행_중에_닫힘에서_열림으로_바뀌면_알림() {
        val w = RearWindowWatch()
        w.update(left, 0, driving = true)
        assertTrue(w.update(left, 3, driving = true))
    }

    @Test
    fun 켰을_때_열려_있던_창문은_닫혔다가_다시_열리면_알림() {
        val w = RearWindowWatch()
        w.update(left, 5, driving = true)
        assertFalse(w.update(left, 5, driving = true))
        assertFalse(w.update(left, 0, driving = true))
        assertTrue(w.update(left, 2, driving = true))
    }

    @Test
    fun 같은_값이_다시_오거나_더_열려도_한_번만_알림() {
        val w = RearWindowWatch()
        w.update(left, 0, driving = true)
        assertFalse(w.update(left, 0, driving = true)) // 등록 직후 같은 값이 두 번 온다
        assertTrue(w.update(left, 1, driving = true))
        assertFalse(w.update(left, 1, driving = true))
        assertFalse(w.update(left, 5, driving = true))
    }

    @Test
    fun 닫혔다가_다시_열리면_또_알림() {
        val w = RearWindowWatch()
        w.update(left, 0, driving = true)
        assertTrue(w.update(left, 4, driving = true))
        assertFalse(w.update(left, 0, driving = true))
        assertTrue(w.update(left, 4, driving = true))
    }

    @Test
    fun 세워_둔_차에서_열면_알리지_않음() {
        val w = RearWindowWatch()
        w.update(left, 0, driving = false)
        assertFalse(w.update(left, 5, driving = false))
        assertFalse(w.update(left, 7, driving = true)) // 세워서 연 채로 출발해도 알리지 않는다
    }

    @Test
    fun 왼쪽과_오른쪽은_따로_본다() {
        val w = RearWindowWatch()
        w.update(left, 0, driving = true)
        w.update(right, 0, driving = true)
        assertTrue(w.update(left, 3, driving = true))
        assertTrue(w.update(right, 3, driving = true))
    }
}
