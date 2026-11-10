package com.rearcare.car

/**
 * 앱을 켤 때 오늘 운행을 기록할지 묻고, 차를 세울 때마다 그 주행을 남긴다. 순수 Kotlin.
 *
 * - 앱은 시동 값을 읽지 못한다(Pleos SDK 2.3.2에 읽는 함수가 없다). 그래서 앱을 켠 뒤 차가 서 있을 때 한 번 묻는다. 아이가 보이는지와는 상관없다.
 * - "네"라고 답해야 남긴다. "아니요"와 무응답은 남기지 않는다.
 * - 오늘 이미 "네"라고 했으면 앱을 다시 켜도 묻지 않고 이어서 기록한다(resumeToday). "아니요"는 다음에 앱을 켤 때 다시 묻는다.
 * - 묻는 중에 출발하면 그 질문을 거둔다. 운전 중에 답하게 하지 않는다.
 * - 차를 세울 때마다(D에서 P로) 그 주행이 끝난다. 세워 둔 시간은 주행에 넣지 않는다. 같은 날의 주행을 한 기록으로 합치는 일은 TripDiary.mergeSameDay가 한다.
 * - 차를 조금 옮긴 것은 남기지 않는다.
 */
class TripRecorder {

    /** 지금 남길 주행의 시작과 끝 */
    data class Save(val startMs: Long, val endMs: Long) {
        val minutes: Int get() = ((endMs - startMs) / 60_000L).toInt()
    }

    private enum class Phase { WAITING, ASKING, RECORDING, SKIPPED }

    private var phase = Phase.WAITING
    private var startMs: Long? = null // 이번 주행의 출발 시각. 세우면 비운다
    private var moving = false
    private var saved = false

    val asking: Boolean get() = phase == Phase.ASKING
    val recording: Boolean get() = phase == Phase.RECORDING

    /** 기록하고 있는데 아직 저장하지 않은 주행이 있거나, 하나도 저장하지 않았다. 화면에 "기록 중"을 보여 줄 때 쓴다 */
    val waitingToSave: Boolean get() = recording && (!saved || startMs != null)

    /** 이번 주행의 출발 시각. 새 주행이 시작됐는지 알 때 쓴다(잠 기록). 달리고 있지 않으면 null */
    val startedAtMs: Long? get() = startMs

    /** 앱을 켠 뒤 부른다. 지금 물어야 하면 true */
    fun onCarStarted(parked: Boolean): Boolean {
        if (!parked || phase != Phase.WAITING) return false
        phase = Phase.ASKING
        return true
    }

    /** 오늘 이미 "네"라고 했다. 묻지 않고 기록을 이어 간다. 이어 가기 시작했으면 true */
    fun resumeToday(): Boolean {
        if (phase != Phase.WAITING) return false
        phase = Phase.RECORDING
        return true
    }

    /** 질문의 대답. null은 대답 없음이다 */
    fun onAnswer(yes: Boolean?) {
        if (phase != Phase.ASKING) return
        phase = if (yes == true) Phase.RECORDING else Phase.SKIPPED
    }

    /** 차가 움직이기 시작할 때 부른다 */
    fun onDrive(nowMs: Long) {
        when (phase) {
            // 묻기 전에, 또는 묻는 중에 출발한 주행은 남기지 않는다
            Phase.WAITING, Phase.ASKING -> phase = Phase.SKIPPED
            Phase.RECORDING -> {
                moving = true
                if (startMs == null) startMs = nowMs
            }
            Phase.SKIPPED -> Unit
        }
    }

    /** 차를 세웠을 때 부른다. 지금 남겨야 하면 시작과 끝을 돌려준다. 다음에 출발하면 새 주행이다 */
    fun onPark(nowMs: Long): Save? {
        val wasMoving = moving
        moving = false
        if (phase != Phase.RECORDING || !wasMoving) return null
        val start = startMs ?: return null
        startMs = null
        if (nowMs - start < MIN_TRIP_MS) return null
        saved = true
        return Save(start, nowMs)
    }

    /** 처음으로 돌린다 */
    fun reset() {
        phase = Phase.WAITING
        startMs = null
        moving = false
        saved = false
    }

    companion object {
        const val MIN_TRIP_MS = 30_000L // 이보다 짧으면 차를 조금 옮긴 것으로 본다
    }
}
