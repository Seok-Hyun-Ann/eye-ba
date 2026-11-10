package com.rearcare.car

/**
 * 얼굴 표정 값으로 "우는 얼굴"인지 본다. 순수 Kotlin.
 *
 * 왜: 울음소리만으로 판단하면 다른 아이 울음·영상 소리에도 "아이가 울고 있어요"라고 한다.
 *     얼굴이 보일 때 우는 얼굴이 아니면 "울음소리가 들려요"로 낮춘다(ChildStateTracker).
 * 어떻게: 얼굴 모델(MediaPipe Face Landmarker)이 주는 표정 값 52개 중, 아기 사진 시험에서
 *     우는 얼굴과 울지 않는 얼굴을 가장 잘 가른 몇 개의 평균을 점수로 쓴다. 고른 표정 값과 문턱은
 *     공개 사진 시험 결과로 정한다(짝수 번호로 고르고 홀수 번호로 잼).
 */
object CryFace {

    /**
     * 점수에 쓰는 표정 값. 좌우가 있는 것은 둘의 평균.
     * 조정용(짝수) 사진에서 우는 얼굴과 울지 않는 얼굴을 가장 잘 가른 4개(조정용 AUC 0.86)
     */
    val FEATURES: List<List<String>> = listOf(
        listOf("mouthStretchLeft", "mouthStretchRight"), // 입을 옆으로 당김
        listOf("mouthFrownLeft", "mouthFrownRight"), // 입꼬리 내림
        listOf("mouthUpperUpLeft", "mouthUpperUpRight"), // 윗입술 올림
        listOf("jawOpen"), // 입 벌림
    )

    /**
     * 이 점수 이상이면 "우는 얼굴"로 단정한다. 조정용 사진에서 울지 않는 얼굴을 90% 이상 맞히는 가장 낮은 문턱.
     * 잘못 단정하지 않는 쪽으로 정했다.
     * 대신 우는 얼굴을 놓치는 일이 늘지만, 그때도 울음소리는 "울음소리가 들려요 · 아이가 우는지는 잘 모르겠어요"로 보여 준다.
     */
    const val THRESHOLD = 0.115f

    /**
     * 이 점수보다 낮아야 "차분한 얼굴"이다. 조정용 사진에서 우는 얼굴을 90% 이상 맞히는 가장 높은 문턱.
     * 잠으로 세거나 "아이는 눈을 감고 있어요"라고 말할 때 쓴다. 우는 아이를 잔다고 하면 안 되므로 여기는 낮게 둔다.
     * 이 값과 THRESHOLD 사이는 우는지 알 수 없는 얼굴이다.
     */
    const val CALM_BELOW = 0.021f

    /** 표정 값으로 점수를 낸다. 표정 값이 없으면 null */
    fun score(blend: Map<String, Float>): Float? {
        if (blend.isEmpty()) return null
        val parts = FEATURES.map { names -> names.map { blend[it] ?: return null }.average().toFloat() }
        return parts.average().toFloat()
    }

    /** 우는 얼굴이라고 단정할 수 있는가. 표정 값이 없으면 null(모름) */
    fun isCrying(blend: Map<String, Float>): Boolean? = score(blend)?.let { it >= THRESHOLD }

    /** 울고 있을 수도 있는 얼굴인가(차분한 얼굴이 아닌가). 표정 값이 없으면 null(모름) */
    fun isMaybeCrying(blend: Map<String, Float>): Boolean? = score(blend)?.let { it >= CALM_BELOW }
}
