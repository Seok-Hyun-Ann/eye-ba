package com.rearcare.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 앱을 켤 때 기록할지 묻고, 차를 세울 때마다 남기는 흐름을 본다 */
class TripRecorderTest {

    private val minute = 60_000L

    /** "네"라고 답한 상태까지 간다 */
    private fun recording() = TripRecorder().also {
        assertTrue(it.onCarStarted(parked = true))
        it.onAnswer(true)
    }

    @Test
    fun 앱을_켜고_차가_서_있으면_한_번만_묻는다() {
        val r = TripRecorder()
        assertTrue(r.onCarStarted(parked = true))
        assertFalse(r.onCarStarted(parked = true))
    }

    @Test
    fun 주행_중에는_묻지_않는다() {
        val r = TripRecorder()
        assertFalse(r.onCarStarted(parked = false))
    }

    @Test
    fun 네라고_하면_세웠을_때_기록을_남긴다() {
        val r = recording()
        r.onDrive(1_000L)
        val save = r.onPark(1_000L + 5 * minute)
        assertNotNull(save)
        assertEquals(1_000L, save!!.startMs)
        assertEquals(5, save.minutes)
    }

    @Test
    fun 아니요라고_하면_남기지_않고_다시_묻지_않는다() {
        val r = TripRecorder()
        r.onCarStarted(parked = true)
        r.onAnswer(false)
        r.onDrive(0L)
        assertNull(r.onPark(5 * minute))
        assertFalse(r.onCarStarted(parked = true))
    }

    @Test
    fun 대답이_없으면_아니요와_같다() {
        val r = TripRecorder()
        r.onCarStarted(parked = true)
        r.onAnswer(null)
        r.onDrive(0L)
        assertNull(r.onPark(5 * minute))
    }

    @Test
    fun 묻는_중에_출발하면_남기지_않고_다시_묻지_않는다() {
        val r = TripRecorder()
        r.onCarStarted(parked = true)
        assertTrue(r.asking)
        r.onDrive(0L)
        assertFalse(r.asking)
        assertNull(r.onPark(5 * minute))
        assertFalse(r.onCarStarted(parked = true))
    }

    @Test
    fun 묻기_전에_출발한_주행은_남기지_않는다() {
        val r = TripRecorder()
        r.onDrive(0L)
        assertNull(r.onPark(5 * minute))
        assertFalse(r.onCarStarted(parked = true))
    }

    @Test
    fun 오늘_네라고_했으면_묻지_않고_이어서_기록한다() {
        val r = TripRecorder()
        assertTrue(r.resumeToday())
        assertTrue(r.recording)
        assertFalse(r.onCarStarted(parked = true))
        r.onDrive(0L)
        assertNotNull(r.onPark(5 * minute))
    }

    @Test
    fun 거절한_뒤에는_이어서_기록하지_않는다() {
        val r = TripRecorder()
        r.onCarStarted(parked = true)
        r.onAnswer(false)
        assertFalse(r.resumeToday())
        assertFalse(r.recording)
    }

    @Test
    fun 세운_채로_다시_세워도_두_번_남기지_않는다() {
        val r = recording()
        r.onDrive(0L)
        assertNotNull(r.onPark(5 * minute))
        assertNull(r.onPark(6 * minute))
    }

    @Test
    fun 세웠다_다시_출발하면_새_주행이고_세워_둔_시간은_넣지_않는다() {
        val r = recording()
        r.onDrive(0L)
        val first = r.onPark(5 * minute)!!
        r.onDrive(125 * minute)
        val second = r.onPark(140 * minute)!!
        assertNotEquals(first.startMs, second.startMs)
        assertEquals(125 * minute, second.startMs)
        assertEquals(15, second.minutes)
    }

    @Test
    fun 잠깐_움직인_것은_남기지_않고_기록은_이어_간다() {
        val r = recording()
        r.onDrive(0L)
        assertNull(r.onPark(TripRecorder.MIN_TRIP_MS - 1))
        assertTrue(r.recording)
        r.onDrive(minute)
        val save = r.onPark(minute + TripRecorder.MIN_TRIP_MS)
        assertNotNull(save)
        assertEquals(minute, save!!.startMs)
    }

    @Test
    fun 처음부터_다시_시작하면_다시_묻는다() {
        val r = recording()
        r.onDrive(0L)
        r.reset()
        assertFalse(r.recording)
        assertNull(r.onPark(5 * minute))
        assertTrue(r.onCarStarted(parked = true))
    }

    @Test
    fun 저장하지_않은_주행이_있을_때만_기록_중이다() {
        val r = recording()
        assertTrue(r.waitingToSave)
        r.onDrive(0L)
        assertTrue(r.waitingToSave)
        r.onPark(5 * minute)
        assertFalse(r.waitingToSave)
        r.onDrive(10 * minute)
        assertTrue(r.waitingToSave)
    }

    @Test
    fun 남긴_뒤에도_오늘은_계속_기록한다() {
        val r = recording()
        r.onDrive(0L)
        r.onPark(5 * minute)
        assertTrue(r.recording)
    }

    @Test
    fun 기록_중인지_알려_준다() {
        val r = TripRecorder()
        assertFalse(r.recording)
        r.onCarStarted(parked = true)
        assertFalse(r.recording)
        r.onAnswer(true)
        assertTrue(r.recording)
    }
}
