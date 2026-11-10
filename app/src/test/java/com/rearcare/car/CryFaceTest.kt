package com.rearcare.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 우는 얼굴 점수 계산 시험. 문턱 값 자체의 정확도는 여기서 재지 않는다 */
class CryFaceTest {

    private fun blend(v: Float) = CryFace.FEATURES.flatten().associateWith { v }

    @Test
    fun 표정_값이_없으면_모름() {
        assertNull(CryFace.isCrying(emptyMap()))
        assertNull(CryFace.score(mapOf("jawOpen" to 0.5f))) // 필요한 값이 하나라도 없으면 모름
    }

    @Test
    fun 점수는_고른_표정_값의_평균() {
        assertEquals(0.4f, CryFace.score(blend(0.4f))!!, 1e-6f)
        assertEquals(true, CryFace.isCrying(blend(CryFace.THRESHOLD + 0.01f)))
        assertEquals(false, CryFace.isCrying(blend(CryFace.THRESHOLD - 0.01f)))
    }

    @Test
    fun 두_문턱_사이는_우는지_알_수_없는_얼굴() {
        val between = blend((CryFace.CALM_BELOW + CryFace.THRESHOLD) / 2)
        assertEquals(false, CryFace.isCrying(between))
        assertEquals(true, CryFace.isMaybeCrying(between))
        assertEquals(false, CryFace.isMaybeCrying(blend(CryFace.CALM_BELOW - 0.01f)))
    }
}
