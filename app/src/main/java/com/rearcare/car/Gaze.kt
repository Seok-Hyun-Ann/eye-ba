package com.rearcare.car

/**
 * 아이가 어디를 보는지 어림한다. 고개 각도만 보면 "고개만 돌리고 눈은 이쪽을 보는" 아이를
 * 옆을 본다고 잘못 말한다.
 *
 * 시선(사진 기준, 음수 = 사진 왼쪽) = 고개 각도 + 눈동자 쏠림 x 눈 움직임 폭
 * 눈동자 쏠림은 얼굴 모델의 표정 값 네 개로 잰다. 왼눈 바깥쪽과 오른눈 안쪽이 같은 방향이다.
 *
 * - 눈 움직임 폭 30도는 측정한 값이 아니라 가정값이다. 20~40도로 바꿔도 시연 사진의 판단은 같다(GazeTest).
 * - 더하는 방향은 공개 사진으로 맞췄다(눈이 카메라를 보는 사진 c0329에서 0도 가까이 나온다).
 * - 시선 정답이 있는 자료가 없어 정확도는 재지 못했다. 사진을 눈으로 대조했다.
 */
object Gaze {
    const val EYE_RANGE_DEG = 30f

    fun degrees(yawDeg: Float, blend: Map<String, Float>, eyeRangeDeg: Float = EYE_RANGE_DEG): Float? {
        val outLeft = blend["eyeLookOutLeft"] ?: return null
        val inRight = blend["eyeLookInRight"] ?: return null
        val inLeft = blend["eyeLookInLeft"] ?: return null
        val outRight = blend["eyeLookOutRight"] ?: return null
        val eyeShift = ((outLeft + inRight) - (inLeft + outRight)) / 2f
        return yawDeg + eyeShift * eyeRangeDeg
    }
}
