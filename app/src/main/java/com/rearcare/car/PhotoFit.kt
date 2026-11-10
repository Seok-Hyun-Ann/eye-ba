package com.rearcare.car

import kotlin.math.max
import kotlin.math.min

/**
 * 아이 사진을 카드에 어떻게 놓을지 정한다. 순수 Kotlin.
 *
 * 가로 사진은 카드 너비가 한계라 카드만 키우면 위아래 빈 곳만 늘고 아이는 커지지 않는다.
 * 그래서 얼굴을 찾았으면 얼굴을 기준으로 확대해 카드를 채운다.
 *
 * - 얼굴을 못 찾았으면 사진 전체를 보여 준다. 무엇이 있는지 모르는데 잘라 내면 안 된다.
 * - 너무 많이 확대하지 않는다(MAX_ZOOM). 모자라 남는 곳은 화면 쪽에서 흐린 사진으로 채운다.
 * - 사진 끝이 카드 안으로 들어와 빈틈이 생기지 않게 움직임을 막는다.
 */
object PhotoFit {

    /** 얼굴 가운데 위치. 사진 크기를 1로 본 비율 */
    data class Focus(val x: Float, val y: Float)

    /** 사진을 scale배 하고 (dx, dy)만큼 옮겨 놓는다 */
    data class Placement(val scale: Float, val dx: Float, val dy: Float)

    /** 사진 전체가 들어가는 크기보다 이만큼까지만 확대한다 */
    const val MAX_ZOOM = 1.7f

    /** 얼굴 가운데를 카드 높이의 이 자리에 둔다. 얼굴 아래로 몸이 보이도록 가운데보다 조금 위다 */
    private const val FACE_HEIGHT = 0.42f

    fun place(imageW: Int, imageH: Int, viewW: Int, viewH: Int, focus: Focus?): Placement {
        val fit = min(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
        val fill = max(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
        val scale = if (focus == null) fit else min(fill, fit * MAX_ZOOM)
        return Placement(
            scale,
            offset(viewW.toFloat(), imageW * scale, viewW / 2f, focus?.x ?: 0.5f),
            offset(viewH.toFloat(), imageH * scale, viewH * FACE_HEIGHT, focus?.y ?: 0.5f),
        )
    }

    // 사진이 카드보다 작으면 가운데에 두고, 크면 얼굴이 anchor에 오도록 옮기되 빈틈이 생기기 전에 멈춘다
    private fun offset(view: Float, size: Float, anchor: Float, focus: Float): Float =
        if (size <= view) (view - size) / 2 else (anchor - focus * size).coerceIn(view - size, 0f)
}
