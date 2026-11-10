package com.rearcare.car

import com.rearcare.car.ChildStateTracker.Side
import com.rearcare.car.ChildStateTracker.State
import com.rearcare.car.ChildStateTracker.Status

/**
 * 상태를 사람이 듣고 읽을 문장으로 바꾼다. 상태 카드 글자, "아기 뭐 해?" 대답.
 *
 * - 아는 것만 말한다. 얼굴이 안 보이면 추측하지 않고 "얼굴이 잘 안 보여요"라고 한다.
 * - "창문 쪽을 보고 있어요"는 창문이 어느 쪽인지(windowSide) 정해 두었고 고개와 시선이 모두 그쪽일 때만 말한다.
 *   모르면 "고개를 옆으로 돌렸어요"로 낮춘다.
 * - 고개가 너무 돌아가거나 옆을 보다 얼굴을 놓치면 자는지 깨어 있는지 단정하지 않고 확인이 안 된다고 말한다.
 * - 주행 중 화면 글자 제한(120자)보다 훨씬 짧게 쓴다.
 */
object StatusWords {

    /** 상태 카드 큰 글자 */
    fun title(status: Status, windowSide: Side?): String = when (status.state) {
        State.CHECKING -> "확인하는 중이에요"
        State.AWAKE -> "깨어 있어요"
        State.LOOKING_SIDE -> if (looksAtWindow(status, windowSide)) "창문 쪽을 보고 있어요" else "고개를 옆으로 돌렸어요"
        State.ASLEEP -> "자는 것 같아요"
        State.CRYING -> "울고 있어요"
        State.CRY_SOUND -> "울음소리가 들려요"
        State.FACE_HIDDEN -> "얼굴이 잘 안 보여요"
    }

    /** 상태 카드 작은 글자: 얼마나 됐는지, 조용한지 */
    fun detail(status: Status, nowMs: Long): String {
        val parts = mutableListOf<String>()
        if (status.state != State.CHECKING) parts += since(nowMs - status.sinceMs)
        if (status.state == State.FACE_HIDDEN && status.lastSeenAsleep) parts += "마지막으로 봤을 때 자고 있었어요"
        if (status.state == State.CRY_SOUND) parts += cryCheck(status)
        if (status.eyesUnknown || lostWhileTurned(status)) parts += "자는지 깨어 있는지는 확인이 안 돼요"
        val quiet = status.quietSinceMs
        if (status.state in setOf(State.AWAKE, State.LOOKING_SIDE, State.FACE_HIDDEN) && quiet != null && nowMs - quiet >= MINUTE) {
            parts += "${minutes(nowMs - quiet)}분째 조용해요"
        }
        return parts.joinToString(" · ")
    }

    /** "아기 뭐 해?"에 대한 대답 */
    fun answer(status: Status, nowMs: Long, windowSide: Side?): String {
        val elapsed = nowMs - status.sinceMs
        val quiet = status.quietSinceMs?.let { nowMs - it }?.takeIf { it >= MINUTE }?.let { " ${minutes(it)}분째 조용해요." }.orEmpty()
        return when (status.state) {
            State.CHECKING -> "아직 살펴보는 중이에요. 조금만 기다려 주세요."
            State.AWAKE -> "깨어 있어요.$quiet"
            State.LOOKING_SIDE -> when {
                status.eyesUnknown -> cannotTell(status, windowSide) + quiet
                looksAtWindow(status, windowSide) -> "깨어서 창문 쪽을 보고 있어요."
                else -> "깨어서 옆쪽을 보고 있어요."
            }
            State.ASLEEP -> if (elapsed < MINUTE) "방금 잠든 것 같아요." else "${minutes(elapsed)}분째 자고 있어요."
            State.CRYING -> if (elapsed < MINUTE) "방금 울기 시작했어요." else "${minutes(elapsed)}분째 울고 있어요."
            State.CRY_SOUND -> when {
                // 잔다고 단정하지 않고 본 것만 말한다. 울음소리가 들리는데 잔다고 하면 앞뒤가 안 맞고, 우는 아이를 잘못 안심시킬 수 있다
                status.childAsleep -> "울음소리가 들리는데, 아이는 눈을 감고 있어요. 다른 곳에서 나는 소리일 수도 있어요."
                status.calmFaceSeen -> "울음소리가 들리는데, 아이가 우는지는 잘 모르겠어요."
                else -> "울음소리가 들리는데, 얼굴이 잘 안 보여서 아이가 우는 건지 모르겠어요."
            }
            State.FACE_HIDDEN ->
                if (lostWhileTurned(status)) {
                    cannotTell(status, windowSide) + quiet
                } else {
                    "얼굴이 잘 안 보여요." + (if (status.lastSeenAsleep) " 아까 봤을 때는 자고 있었어요." else "") + quiet
                }
        }
    }

    /** Gleo에 돌려주는 상태 설명. Gleo Interface 규칙상 영어 문장으로 쓴다. 운전자에게는 answer를 말한다 */
    fun gleoMessage(status: Status): String = when (status.state) {
        State.CHECKING -> "The app is still checking on the child."
        State.AWAKE -> "The child is awake."
        State.LOOKING_SIDE ->
            if (status.eyesUnknown) "The child's head is turned away, so it is unclear whether the child is asleep." else "The child is awake and looking to the side."
        State.ASLEEP -> "The child seems to be asleep."
        State.CRYING -> "The child is crying."
        State.CRY_SOUND -> "A crying sound is heard, but it is unclear whether it is this child."
        State.FACE_HIDDEN -> "The child's face cannot be seen."
    }

    // ---------- 화면 질문과 결과 ----------

    /** 차를 탈 때 묻는 기록 질문 */
    const val TRIP_QUESTION = "오늘 아이와의 운행을 기록할까요?"

    /** 잠잘 때 온도를 아직 고르지 않았을 때 */
    const val NOT_SET = "정하지 않음"

    /** 질문에 아니요(false)라고 했거나 대답이 없었을(null) 때. 대답이 없었는데 "네"라고 하지 않는다 */
    fun keep(answer: Boolean?): String = if (answer == false) "네, 그대로 둘게요." else "그럼 일단 그대로 둘게요."

    /** 잠듦 제안을 실행한 결과. 실제로 바뀐 수와 실패한 수를 보고 말한다 */
    fun applyResult(changed: Int, failed: Int): String = when {
        failed == 0 && changed == 0 -> "이미 그렇게 되어 있어요."
        failed == 0 -> "네, 맞춰 뒀어요."
        changed == 0 -> "죄송해요, 바꾸지 못했어요."
        else -> "일부만 바뀌었어요. 한번 확인해 주세요."
    }

    /** 불 되돌리기 결과. 바꾼 것도 실패한 것도 없으면(밝히는 중에 다시 어둡게 해서 멈췄다) 말하지 않는다 */
    fun restoreResult(changed: Int, failed: Int): String? = when {
        changed == 0 && failed == 0 -> null
        failed == 0 -> "네, 원래대로 돌려 뒀어요."
        changed == 0 -> "죄송해요, 되돌리지 못했어요."
        else -> "일부만 돌아갔어요. 한번 확인해 주세요."
    }

    /** 뒷창문 잠금 결과 */
    fun lockResult(changed: Int, failed: Int): String = when {
        failed == 0 -> "네, 뒷창문을 잠갔어요."
        changed == 0 -> "죄송해요, 잠그지 못했어요."
        else -> "일부만 잠겼어요. 한번 확인해 주세요."
    }

    // 울음소리만 들릴 때, 아이 쪽에서 본 것을 짧게
    private fun cryCheck(status: Status) = when {
        status.childAsleep -> "아이는 눈을 감고 있어요"
        status.calmFaceSeen -> "아이가 우는지는 잘 모르겠어요"
        else -> "얼굴이 잘 안 보여요"
    }

    private fun looksAtWindow(status: Status, windowSide: Side?) = windowSide != null && status.lookSide == windowSide

    // 옆을 보던 아이의 얼굴을 놓쳤다. 자고 있던 아이는 "아까 자고 있었어요"가 더 쓸모 있으므로 빼고 본다
    private fun lostWhileTurned(status: Status) = status.state == State.FACE_HIDDEN && status.lookSide != null && !status.lastSeenAsleep

    private fun cannotTell(status: Status, windowSide: Side?) =
        if (looksAtWindow(status, windowSide)) {
            "창문 쪽을 보고 있어서 자는지 깨어 있는지 확인이 안 돼요."
        } else {
            "고개를 옆으로 돌리고 있어서 자는지 깨어 있는지 확인이 안 돼요."
        }

    private fun since(ms: Long) = if (ms < MINUTE) "방금" else "${minutes(ms)}분째"

    private fun minutes(ms: Long) = (ms / MINUTE).toInt()

    private const val MINUTE = 60_000L
}
