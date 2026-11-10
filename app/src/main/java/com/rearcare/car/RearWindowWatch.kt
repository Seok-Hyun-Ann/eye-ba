package com.rearcare.car

/**
 * 뒷창문이 "닫힘 → 열림"으로 바뀌는 순간을 찾는다. 순수 Kotlin.
 *
 * - 처음 읽은 값은 기준으로만 쓴다. 앱을 켰을 때 이미 열려 있던 창문은 알리지 않는다.
 * - 같은 값이 다시 와도 알리지 않는다. 2026-11-05 시험에서 등록 직후 같은 값이 두 번 왔다.
 * - 더 열리는 것(3 → 5)은 알리지 않는다. 한 번 열릴 때 소리가 한 번만 나게 한다.
 * - 주행 중일 때만 알린다. 세워 둔 차에서 창문을 여는 것은 흔한 일이다.
 * - 거절한 뒤에도 그 창문이 닫혔다가 다시 열리면 또 알린다. 값만 보고 정하므로 따로 기억하지 않는다.
 */
class RearWindowWatch {
    private val last = mutableMapOf<String, Int>()

    /** 위치를 넣고, 지금 알려야 하면 true를 돌려준다 */
    fun update(window: String, position: Int, driving: Boolean): Boolean {
        val before = last.put(window, position) ?: return false
        return driving && before <= CLOSED && position > CLOSED
    }

    private companion object {
        const val CLOSED = 0 // 창문 위치 0 = 닫힘(에뮬레이터 범위 0~10, 콘솔 0%와 같음을 확인)
    }
}
