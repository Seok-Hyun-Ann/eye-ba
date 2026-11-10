package com.rearcare.car

import com.rearcare.car.ChildStateTracker.State
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 주행 중 사진을 바꾸는 시점 시험. 시각은 밀리초 */
class DrivePhotoPolicyTest {

    private val minute = 60_000L

    @Test
    fun 주행을_시작하면_바로_한_장_보여_주고_간격이_지나야_바꾼다() {
        val p = DrivePhotoPolicy(3 * minute)
        assertTrue(p.shouldTakeNew(0))
        assertFalse(p.shouldTakeNew(minute))
        assertFalse(p.shouldTakeNew(3 * minute - 1))
        assertTrue(p.shouldTakeNew(3 * minute))
        assertFalse(p.shouldTakeNew(3 * minute + 1000))
    }

    @Test
    fun 상태가_바뀌면_간격을_기다리지_않고_바꾸고_간격을_다시_센다() {
        val p = DrivePhotoPolicy(5 * minute)
        assertTrue(p.shouldTakeNew(0))
        p.stateChanged()
        assertTrue(p.shouldTakeNew(minute))
        assertFalse(p.shouldTakeNew(minute + 1000)) // 한 번 바꿨으면 끝
        assertFalse(p.shouldTakeNew(5 * minute)) // 간격은 바꾼 때부터 다시 센다
        assertTrue(p.shouldTakeNew(6 * minute))
    }

    @Test
    fun 최소_간격_안에_상태가_바뀌면_버리지_않고_간격이_지날_때_한_번_바꾼다() {
        val p = DrivePhotoPolicy(5 * minute, minGapMs = 20_000)
        assertTrue(p.shouldTakeNew(0))
        p.stateChanged()
        assertFalse(p.shouldTakeNew(3_000)) // 3초 만에 또 바꾸지 않는다
        p.stateChanged()
        assertFalse(p.shouldTakeNew(19_999))
        assertTrue(p.shouldTakeNew(20_000))
        assertFalse(p.shouldTakeNew(40_000)) // 밀린 것은 한 번으로 끝
    }

    @Test
    fun 깨어_있음과_옆을_봄은_같은_장면이다() {
        assertFalse(DrivePhotoPolicy.sceneChanged(State.AWAKE, State.LOOKING_SIDE))
        assertFalse(DrivePhotoPolicy.sceneChanged(State.LOOKING_SIDE, State.AWAKE))
        assertFalse(DrivePhotoPolicy.sceneChanged(State.CHECKING, State.AWAKE))
        assertFalse(DrivePhotoPolicy.sceneChanged(State.ASLEEP, State.ASLEEP))
        assertTrue(DrivePhotoPolicy.sceneChanged(State.LOOKING_SIDE, State.ASLEEP))
        assertTrue(DrivePhotoPolicy.sceneChanged(State.AWAKE, State.ASLEEP))
        assertTrue(DrivePhotoPolicy.sceneChanged(State.ASLEEP, State.CRYING))
        assertTrue(DrivePhotoPolicy.sceneChanged(State.CRYING, State.AWAKE))
        assertTrue(DrivePhotoPolicy.sceneChanged(State.AWAKE, State.FACE_HIDDEN))
    }

}
