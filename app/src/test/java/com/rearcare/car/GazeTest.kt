package com.rearcare.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 시선 어림 시험. 값은 공개 사진을 얼굴 모델에 넣어 나온 실제 값이다 */
class GazeTest {

    // c0329: 고개는 사진 왼쪽으로 돌렸지만 눈은 카메라를 본다(사람이 사진을 보고 확인)
    private val lookingBack = mapOf(
        "eyeLookOutLeft" to 0.9598f,
        "eyeLookInRight" to 0.9595f,
        "eyeLookInLeft" to 0.0002f,
        "eyeLookOutRight" to 0.0001f,
    )

    @Test
    fun 고개와_눈이_반대로_가면_이쪽을_보는_것으로_어림한다() {
        assertEquals(2.2f, Gaze.degrees(-26.57f, lookingBack)!!, 0.2f)
    }

    @Test
    fun 눈_움직임_폭을_20도나_40도로_바꿔도_이쪽을_보는_쪽으로_나온다() {
        // 30도는 가정값이다. 가정이 조금 틀려도 판단이 뒤집히지 않는지 본다
        for (range in listOf(20f, 30f, 40f)) {
            assertTrue(abs(Gaze.degrees(-26.57f, lookingBack, range)!!) < 15f)
        }
    }

    @Test
    fun 눈이_가운데면_고개_각도가_그대로_시선이다() {
        val center = lookingBack.mapValues { 0f }
        assertEquals(-30f, Gaze.degrees(-30f, center)!!, 0.01f)
    }

    @Test
    fun 눈_값이_없으면_어림하지_않는다() {
        assertNull(Gaze.degrees(-30f, emptyMap()))
    }
}
