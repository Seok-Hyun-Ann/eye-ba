package com.rearcare.car

import com.rearcare.car.PhotoFit.Focus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 사진을 키울 때 아이가 카드 안에 있고 빈틈이 생기지 않는지 본다 */
class PhotoFitTest {

    private val delta = 0.5f

    @Test
    fun 얼굴을_못_찾으면_사진_전체를_가운데에_둔다() {
        val p = PhotoFit.place(960, 640, 800, 800, null)
        assertEquals(800f / 960, p.scale, 0.001f)
        assertEquals(0f, p.dx, delta)
        assertEquals((800 - 640 * p.scale) / 2, p.dy, delta)
    }

    @Test
    fun 얼굴을_찾으면_확대해_카드를_채운다() {
        val p = PhotoFit.place(960, 640, 793, 844, Focus(0.45f, 0.4f))
        assertEquals(844f / 640, p.scale, 0.001f)
        // 사진이 카드의 위아래와 양옆을 모두 덮는다
        assertTrue(p.dx <= 0f && p.dx + 960 * p.scale >= 793f)
        assertTrue(p.dy <= 0f && p.dy + 640 * p.scale >= 844f)
        // 얼굴이 카드 가로 가운데에 온다
        assertEquals(793f / 2, p.dx + 0.45f * 960 * p.scale, delta)
    }

    @Test
    fun 얼굴이_가장자리에_있어도_빈틈이_생기지_않는다() {
        val left = PhotoFit.place(960, 640, 793, 844, Focus(0.02f, 0.5f))
        assertEquals(0f, left.dx, delta)
        val right = PhotoFit.place(960, 640, 793, 844, Focus(0.98f, 0.5f))
        assertEquals(793f, right.dx + 960 * right.scale, delta)
    }

    @Test
    fun 한도보다_많이_확대하지_않는다() {
        val fit = 793f / 1920
        val p = PhotoFit.place(1920, 1080, 793, 844, Focus(0.5f, 0.5f))
        assertEquals(fit * PhotoFit.MAX_ZOOM, p.scale, 0.001f)
        // 세로가 모자라면 가운데에 두고 남는 곳은 흐린 사진이 채운다
        assertEquals((844 - 1080 * p.scale) / 2, p.dy, delta)
    }

    @Test
    fun 세로_사진은_얼굴이_위쪽_자리에_온다() {
        val p = PhotoFit.place(960, 1440, 793, 844, Focus(0.5f, 0.55f))
        assertEquals(793f / 960, p.scale, 0.001f)
        assertEquals(844 * 0.42f, p.dy + 0.55f * 1440 * p.scale, delta)
    }
}
