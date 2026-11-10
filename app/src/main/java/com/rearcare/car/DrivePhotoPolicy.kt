package com.rearcare.car

import com.rearcare.car.ChildStateTracker.State

/**
 * 내비 위 작은 창의 사진을 언제 바꿀지 정한다. 순수 Kotlin.
 *
 * - 작은 창을 열면 바로 한 장 보여 주고, 그 뒤로는 고른 간격마다 한 장씩 바꾼다. 그사이에는 멈춰 있다.
 * - 아이 상태가 크게 바뀌면(잠듦·울음·얼굴 안 보임 등) 간격을 기다리지 않고 바꾼다. 아이는 자는데
 *   사진은 깨어 있는 모습인 일을 막기 위해서다.
 * - 다만 사진이 너무 자주 바뀌지 않도록 최소 간격을 둔다. 그 안에 상태가 바뀌면 버리지 않고 최소 간격이 지날 때 바꾼다.
 * - 법과 Pleos 규칙에서 허용된다고 확인한 기능이 아니다.
 *   그래서 부모가 설정에서 끌 수 있고, 소리·움직임·알림 팝업 없이 자리에서 조용히 바뀌기만 한다.
 */
class DrivePhotoPolicy(
    private val intervalMs: Long,
    private val minGapMs: Long = DEFAULT_MIN_GAP_MS,
) {

    private var lastPhotoMs: Long? = null
    private var changeWaiting = false

    /** 아이 상태가 크게 바뀌었다. 사진도 그에 맞게 바꿔야 한다 */
    fun stateChanged() {
        changeWaiting = true
    }

    /** 지금 새 사진으로 바꿀 때인가. 그렇다고 답하면 그 시각을 기억하고, 간격을 처음부터 다시 센다 */
    fun shouldTakeNew(nowMs: Long): Boolean {
        val last = lastPhotoMs
        val intervalDue = last == null || nowMs - last >= intervalMs
        val changeDue = changeWaiting && (last == null || nowMs - last >= minGapMs)
        if (!intervalDue && !changeDue) return false
        lastPhotoMs = nowMs
        changeWaiting = false
        return true
    }

    companion object {
        /** 상태가 바뀌어도 사진은 이 시간에 한 번까지만 바꾼다. 카드와 어긋나는 시간도 이 안으로 줄어든다 */
        const val DEFAULT_MIN_GAP_MS = 20_000L

        /**
         * 사진을 바꿀 만큼 장면이 달라졌는가. 깨어 있음과 옆을 봄은 같은 장면으로 본다.
         * 아이가 뒤척일 때마다 사진이 바뀌면 안 되고, 둘 다 깨어 있는 아이라 사진이 크게 틀리지 않기 때문이다.
         * 처음 확인하는 중(CHECKING)에서 벗어나는 것은 변화로 치지 않는다.
         */
        fun sceneChanged(before: State, after: State): Boolean {
            if (before == State.CHECKING) return false
            return scene(before) != scene(after)
        }

        private fun scene(state: State) = if (state == State.LOOKING_SIDE) State.AWAKE else state
    }
}
