package com.rearcare.car

import kotlin.math.abs
import kotlin.math.sign

/**
 * 1초마다 들어오는 관찰(얼굴·소리)을 모아 "아이가 지금 무엇을 하는지"를 정하는 부품.
 *
 * - 한 번 본 것으로 바로 바꾸지 않는다. 같은 것이 정해진 시간 이상 이어질 때만 상태를 바꾼다.
 *   (눈 한 번 깜빡였다고 "잠들었다"고 하지 않고, 잠깐 눈을 떴다고 "깼다"고 하지 않기 위해서다)
 * - 얼굴을 못 찾은 순간은 "모름"으로 두고, 이어지던 흐름을 끊지 않는다. 잠든 아기는 얼굴을 자주 놓친다.
 * - 안드로이드 기능을 쓰지 않는 순수 Kotlin이다. 그래서 PC에서 단위 시험(app/src/test)으로 규칙을 확인할 수 있다.
 *
 * 사용법: update(관찰)을 1초마다 부르면, 그 순간 생긴 일(잠듦·깸·울음 시작 등)을 돌려준다. 지금 상태는 status.
 */
class ChildStateTracker(private val config: Config = Config()) {

    /** 1초 동안의 관찰 하나. face가 null이면 그 순간 얼굴을 못 찾은 것 */
    data class Observation(
        val timeMs: Long,
        val face: FaceReading?,
        val crying: Boolean,
    )

    /**
     * 얼굴에서 읽은 숫자(FaceAnalyzer.Face에서 필요한 것만).
     * cryingFace: 표정 값으로 볼 때 "우는 얼굴"이라고 단정할 수 있는가. null이면 판단 정보 없음(소리로만 판단한다)
     * maybeCrying: 울고 있을 수도 있는 얼굴인가. false면 차분한 얼굴이다. 잠으로 셀지와 "자는 것 같아요"라고 말할지를 가른다
     */
    data class FaceReading(
        val eyesClosed: Float,
        val yawDeg: Float,
        val noseOffset: Float,
        val cryingFace: Boolean? = null,
        val maybeCrying: Boolean? = cryingFace,
        val gazeDeg: Float? = null, // 시선 어림(Gaze). 없으면 고개 각도만으로 판단한다
    )

    /**
     * 규칙에 쓰는 문턱과 시간. 기본값은 실제 주행용 가정값이고, 시연용은 DEMO다. 앱이 어느 쪽을 쓸지는 DemoSetup.trackerConfig가 정한다.
     * 문턱(눈 0.35, 고개 20도·0.2)은 아기 사진 시험에서 고른 값이다.
     */
    data class Config(
        val eyesClosedThreshold: Float = 0.35f,
        val turnYawDeg: Float = 20f,
        val turnNoseOffset: Float = 0.2f,
        val sleepConfirmMs: Long = 60_000, // 울지 않고 이만큼 계속 눈을 감고 있으면 "자는 것 같아요"
        val wakeConfirmMs: Long = 5_000, // 자다가 이만큼 계속 눈을 뜨고 있으면 깬 것으로 본다
        val lookConfirmMs: Long = 3_000, // 이만큼 계속 고개를 돌리고 있으면 "고개를 옆으로 돌렸어요"
        val sideGazeDeg: Float = 15f, // 시선이 고개와 같은 쪽으로 이만큼 벗어나야 옆을 보는 것이다(눈으로 대조한 사진에서 고른 값)
        val unreadableYawDeg: Float = 40f, // 고개가 이만큼 돌아가면 눈 값을 믿지 않는다. 시험 사진에 이 각도의 뜬 눈이 없고 잠든 아기를 뜬 눈으로 읽었다
        val cryStartCount: Int = 2, // 최근 cryStartWindowMs 안에 울음이 이만큼 들리면 "울기 시작"
        val cryStartWindowMs: Long = 3_000,
        val cryEndMs: Long = 10_000, // 울음이 이만큼 안 들리면 "그침"
        val cryEndCalmMs: Long = 3_000, // 우는 얼굴을 봤던 울음은, 소리가 이만큼 없고 얼굴이 차분해지면 바로 "그침"
        val faceLostMs: Long = 10_000, // 얼굴을 이만큼 못 찾으면 "얼굴이 잘 안 보여요"
        val cryRepeatMs: Long = 180_000, // 울음이 이어지면 이 간격마다 CRY_CONTINUES를 낸다(로그에만 남는다)
        val cryFaceWindowMs: Long = 5_000, // 울음소리가 날 때 이 시간 안에 본 얼굴로 "아이가 우는지"를 가린다
    ) {
        companion object {
            /** 시연용: 잠듦 판단만 짧게(10초). 실제로는 1분 이상 지켜본다 */
            val DEMO = Config(sleepConfirmMs = 10_000)
        }
    }

    /** 사진 기준 방향. 카메라가 아이를 마주 보므로 아이 기준과는 반대다 */
    enum class Side { LEFT, RIGHT }

    /**
     * 상태 카드에 보이는 상태 하나.
     * CRYING = 울음소리 + 우는 얼굴(아이가 운다). CRY_SOUND = 울음소리는 나는데 우는 얼굴은 못 봄(다른 아이·영상 소리일 수 있음)
     */
    enum class State { CHECKING, AWAKE, LOOKING_SIDE, ASLEEP, CRYING, CRY_SOUND, FACE_HIDDEN }

    data class Status(
        val state: State,
        val sinceMs: Long, // 이 상태가 시작된 때
        val lookSide: Side? = null, // LOOKING_SIDE일 때 돌린 방향. FACE_HIDDEN일 때는 놓치기 직전에 돌렸던 방향
        val eyesUnknown: Boolean = false, // LOOKING_SIDE일 때, 고개가 너무 돌아가 자는지 깨어 있는지 알 수 없는가
        val lastSeenAsleep: Boolean = false, // FACE_HIDDEN일 때, 마지막으로 봤을 때 자고 있었는가
        val quietSinceMs: Long? = null, // 울음 없이 조용했던 시작 시각. 울고 있으면 null
        val calmFaceSeen: Boolean = false, // CRY_SOUND일 때, 얼굴을 봤는데 울지 않는 얼굴이었는가(false면 얼굴을 못 봄)
        val childAsleep: Boolean = false, // CRY_SOUND일 때, 아이는 자는 것으로 보고 있었는가
    )

    /** CRY_STARTED = 아이가 울기 시작(우는 얼굴 확인), CRY_SOUND_STARTED = 울음소리만 들림 */
    enum class EventType { FELL_ASLEEP, WOKE_UP, CRY_STARTED, CRY_SOUND_STARTED, CRY_CONTINUES, CRY_STOPPED }

    /** 그 순간 생긴 일. CRY_CONTINUES일 때 minutes = 울음이 이어진 분, childCrying = 아이가 우는 것으로 보는가 */
    data class Event(val type: EventType, val minutes: Int = 0, val childCrying: Boolean = true)

    // 눈으로 본 상태. UNKNOWN = 아직 얼굴을 제대로 못 봄
    private enum class Eyes { UNKNOWN, AWAKE, ASLEEP }

    private var startMs: Long? = null

    // 울음
    private val recentCries = ArrayDeque<Long>()
    private var crying = false
    private var cryStartMs = 0L
    private var lastCryMs = 0L
    private var cryRepeatsAnnounced = 0
    private var quietSinceMs = 0L
    private var childCryAnnounced = false // 이번 울음에서 우는 얼굴을 확인해 CRY_STARTED를 냈는가
    private class FaceCry(val timeMs: Long, val crying: Boolean, val maybe: Boolean)
    private val recentFaceCry = ArrayDeque<FaceCry>() // 최근에 본 얼굴들

    // 눈
    private var eyes = Eyes.UNKNOWN
    private var eyesSinceMs = 0L
    private var closedRunStart: Long? = null // 눈을 감은 흐름이 시작된 때
    private var openRunStart: Long? = null // 눈을 뜬 흐름이 시작된 때

    // 얼굴·고개
    private var lastFaceMs: Long? = null
    private var turnSide: Side? = null
    private var turnRunStart = 0L
    private var eyesUnreadable = false // 지금 얼굴은 고개가 너무 돌아가 눈을 믿을 수 없다

    var status = Status(State.CHECKING, 0L)
        private set

    /** 자는 것으로 보는가. 얼굴을 잠깐 놓치거나 다른 아이의 울음소리가 나도 바뀌지 않는다. 잠 기록(SleepLog)에 쓴다 */
    val asleep: Boolean get() = eyes == Eyes.ASLEEP

    /** asleep이 마지막으로 바뀐 때. 잠들었으면 눈을 감기 시작한 때, 깼으면 눈을 뜨기 시작한 때나 우는 얼굴을 본 때다 */
    val asleepChangedAtMs: Long get() = eyesSinceMs

    /**
     * 얼굴이 faceLostMs 넘게 안 보이거나, 고개가 너무 돌아가 눈을 못 읽고 있는가. 이때는 자는지 깨어 있는지 본 것이 아니다.
     * 울음소리가 나도 바뀌지 않는다. 잠 기록(SleepLog)에서 잔 시간에 넣지 않으려고 쓴다.
     */
    var eyesUnseen = false
        private set

    /** eyesUnseen이 마지막으로 바뀐 때. 못 보게 됐으면 얼굴을 마지막으로 본 때나 고개를 돌리기 시작한 때, 다시 봤으면 다시 본 때다 */
    var eyesUnseenChangedAtMs = 0L
        private set

    fun update(obs: Observation): List<Event> {
        val t = obs.timeMs
        val start = startMs ?: t.also { startMs = it; quietSinceMs = it; status = Status(State.CHECKING, it, quietSinceMs = it) }
        val events = mutableListOf<Event>()

        // 얼굴을 봤으면 먼저 기록한다. 울음이 시작된 바로 그 순간의 얼굴도 "아이가 우는지" 판단에 쓰기 위해서다
        obs.face?.let { f ->
            lastFaceMs = t
            f.cryingFace?.let { recentFaceCry.addLast(FaceCry(t, it, f.maybeCrying ?: it)) }
        }
        while (recentFaceCry.isNotEmpty() && t - recentFaceCry.first().timeMs >= config.cryFaceWindowMs) recentFaceCry.removeFirst()

        updateCrying(obs, t, events)
        obs.face?.let { updateFace(it, t, obs.crying, events) }

        // 우는 얼굴까지 확인되면 깨어 있는 것이다. WOKE_UP은 따로 내지 않는다(우는 상태가 대신한다)
        // 이번 울음에서 한 번 우는 얼굴을 확인했으면, 울음이 끝날 때까지 아이 울음으로 본다(얼굴을 잠깐 놓쳐도 바뀌지 않게)
        val childCrying = crying && (childCryAnnounced || childCryingNow(t))
        if (childCrying && eyes == Eyes.ASLEEP) {
            eyes = Eyes.AWAKE
            eyesSinceMs = t
        }

        // 상태 카드에 보일 상태 하나를 고른다. 위에 있는 것이 먼저다
        val faceHidden = t - (lastFaceMs ?: start) >= config.faceLostMs
        val unreadable = eyesUnreadable && turnSide != null && t - turnRunStart >= config.lookConfirmMs
        if ((faceHidden || unreadable) != eyesUnseen) {
            eyesUnseen = faceHidden || unreadable
            eyesUnseenChangedAtMs = when {
                faceHidden -> lastFaceMs ?: start
                unreadable -> turnRunStart
                else -> t
            }
        }
        val quiet = if (crying) null else quietSinceMs
        status = when {
            childCrying -> Status(State.CRYING, cryStartMs)
            crying -> Status(State.CRY_SOUND, cryStartMs, calmFaceSeen = calmFaceSinceCry(), childAsleep = calmlyAsleepSinceCry())
            faceHidden -> Status(State.FACE_HIDDEN, lastFaceMs ?: start, lookSide = turnSide, lastSeenAsleep = eyes == Eyes.ASLEEP, quietSinceMs = quiet)
            eyes == Eyes.ASLEEP -> Status(State.ASLEEP, eyesSinceMs, quietSinceMs = quiet)
            unreadable -> Status(State.LOOKING_SIDE, turnRunStart, lookSide = turnSide, eyesUnknown = true, quietSinceMs = quiet)
            eyes == Eyes.AWAKE && turnSide != null && t - turnRunStart >= config.lookConfirmMs ->
                Status(State.LOOKING_SIDE, turnRunStart, lookSide = turnSide, quietSinceMs = quiet)
            eyes == Eyes.AWAKE -> Status(State.AWAKE, eyesSinceMs, quietSinceMs = quiet)
            else -> Status(State.CHECKING, start, quietSinceMs = quiet)
        }
        return events
    }

    private fun updateCrying(obs: Observation, t: Long, events: MutableList<Event>) {
        if (obs.crying) {
            recentCries.addLast(t)
            lastCryMs = t
        }
        while (recentCries.isNotEmpty() && t - recentCries.first() >= config.cryStartWindowMs) recentCries.removeFirst()

        if (!crying && recentCries.size >= config.cryStartCount) {
            crying = true
            cryStartMs = recentCries.first()
            cryRepeatsAnnounced = 0
            childCryAnnounced = childCryingNow(t)
            if (childCryAnnounced) {
                closedRunStart = null // 우는 아이의 감은 눈은 잠으로 세지 않는다
                events += Event(EventType.CRY_STARTED)
            } else {
                events += Event(EventType.CRY_SOUND_STARTED, childCrying = false)
            }
        } else if (crying && (t - lastCryMs >= config.cryEndMs || calmedDown(t))) {
            crying = false
            quietSinceMs = lastCryMs
            events += Event(EventType.CRY_STOPPED)
        }

        if (crying) {
            val child = childCryingNow(t)
            if (child && !childCryAnnounced) {
                // 소리만 들리다가 우는 얼굴이 보이면, 그때 CRY_STARTED
                childCryAnnounced = true
                events += Event(EventType.CRY_STARTED)
            }
            val repeats = ((t - cryStartMs) / config.cryRepeatMs).toInt()
            if (repeats > cryRepeatsAnnounced) {
                cryRepeatsAnnounced = repeats
                events += Event(EventType.CRY_CONTINUES, minutes =(repeats * config.cryRepeatMs / 60_000).toInt(), childCrying = child)
            }
        }
    }

    /**
     * 지금 나는 울음소리가 이 아이의 것인가.
     * - 최근 5초 안에 우는 얼굴을 봤으면 그렇다.
     * - 얼굴을 봤는데 모두 울지 않는 얼굴이면 아니다(다른 아이, 영상·라디오 소리일 수 있다).
     * - 얼굴은 봤지만 우는 얼굴 판단 정보가 없으면 그렇다고 본다.
     * - 얼굴을 못 봤으면 확인할 수 없으므로 아니라고 본다("울음소리가 들려요"로 말한다).
     */
    private fun childCryingNow(t: Long): Boolean {
        // 울음소리가 나기 전에 본 차분한 얼굴은 근거로 쓰지 않는다. 울기 시작하면서 얼굴을 놓치는 경우가 있어서다
        val since = if (crying) cryStartMs else t
        val seen = recentFaceCry.filter { it.timeMs >= since }
        if (seen.isNotEmpty()) return seen.any { it.crying }
        val lastFace = lastFaceMs ?: return false
        return lastFace >= since && t - lastFace < config.cryFaceWindowMs
    }

    /**
     * 우는 얼굴을 확인했던 울음이 끝났는가: 소리가 3초 끊겼고, 그 뒤로 본 얼굴(2장 이상)이 모두 차분하다.
     * 얼굴을 못 봤거나 우는지 알 수 없는 얼굴이면 여기서 끝내지 않고 10초 규칙을 따른다(울다가 잠깐 멈춘 순간에 끝내지 않기 위해서다).
     */
    private fun calmedDown(t: Long): Boolean {
        if (!childCryAnnounced || t - lastCryMs < config.cryEndCalmMs) return false
        val after = recentFaceCry.filter { it.timeMs > lastCryMs }
        return after.size >= 2 && after.none { it.maybe }
    }

    /** 울음소리가 난 뒤에 본 얼굴 중 "우는 얼굴"이라고 단정할 수 없는 얼굴이 있었는가(CRY_SOUND 설명용) */
    private fun calmFaceSinceCry(): Boolean = recentFaceCry.any { it.timeMs >= cryStartMs && !it.crying }

    /** 자고 있었고, 울음소리가 난 뒤에 본 얼굴이 모두 차분한가. 이때만 "아이는 눈을 감고 있어요"라고 말한다 */
    private fun calmlyAsleepSinceCry(): Boolean {
        val seen = recentFaceCry.filter { it.timeMs >= cryStartMs }
        return eyes == Eyes.ASLEEP && seen.isNotEmpty() && seen.none { it.maybe }
    }

    private fun updateFace(face: FaceReading, t: Long, cryHeardNow: Boolean, events: MutableList<Event>) {
        updateTurn(face, t)
        if (!eyesUnreadable) updateEyes(face, t, cryHeardNow, events)
    }

    private fun updateEyes(face: FaceReading, t: Long, cryHeardNow: Boolean, events: MutableList<Event>) {
        // 눈: 감은 흐름과 뜬 흐름이 얼마나 이어졌는지 센다
        val closed = face.eyesClosed >= config.eyesClosedThreshold
        // 울고 있을 수도 있는 얼굴인가: 표정 값 판단이 있으면 그것을, 없으면 울음소리로 대신한다
        val cryFace = face.maybeCrying ?: (crying || cryHeardNow)
        when {
            // 울면서 눈을 질끈 감는 것은 잠이 아니다. 차분하게 감은 눈이면 소리가 나도 잠으로 센다(다른 아이 소리일 수 있다)
            closed && cryFace -> closedRunStart = null
            closed -> {
                if (closedRunStart == null) closedRunStart = t
                openRunStart = null
            }
            else -> {
                if (openRunStart == null) openRunStart = t
                closedRunStart = null
            }
        }
        val closedStart = closedRunStart
        val openStart = openRunStart
        if (eyes != Eyes.ASLEEP && closedStart != null && t - closedStart >= config.sleepConfirmMs) {
            eyes = Eyes.ASLEEP
            eyesSinceMs = closedStart
            events += Event(EventType.FELL_ASLEEP)
        } else if (eyes == Eyes.ASLEEP && openStart != null && t - openStart >= config.wakeConfirmMs) {
            eyes = Eyes.AWAKE
            eyesSinceMs = openStart
            events += Event(EventType.WOKE_UP)
        } else if (eyes == Eyes.UNKNOWN && openStart != null) {
            // 처음 눈을 뜬 얼굴을 보면 바로 "깨어 있음"으로 둔다(알림은 하지 않는다)
            eyes = Eyes.AWAKE
            eyesSinceMs = openStart
        }
    }

    private fun updateTurn(face: FaceReading, t: Long) {
        // 고개: 두 방법(머리 회전각, 코 위치)이 같은 쪽을 가리킬 때만 "돌림"으로 본다.
        // 한 방법만 쓰면 정면을 돌렸다고 잘못 보는 일이 많아서다(코 위치만 쓰면 평가용 정면 사진의 71%만 정면으로 맞혔다)
        val yawSays = abs(face.yawDeg) >= config.turnYawDeg
        val noseSays = abs(face.noseOffset) >= config.turnNoseOffset
        val sameWay = sign(face.yawDeg) == sign(face.noseOffset)
        val headTurned = yawSays && noseSays && sameWay
        val tooFar = abs(face.yawDeg) >= config.unreadableYawDeg
        // 시선: 고개만 돌리고 눈이 이쪽을 보면 옆을 보는 것이 아니다. 고개가 너무 돌아가면 눈 값을 믿을 수 없어 고개만 본다
        val gaze = face.gazeDeg
        val gazeAway = tooFar || gaze == null || (abs(gaze) >= config.sideGazeDeg && sign(gaze) == sign(face.yawDeg))
        val side = if (headTurned && gazeAway) {
            if (face.noseOffset < 0) Side.LEFT else Side.RIGHT
        } else {
            null
        }
        eyesUnreadable = tooFar && headTurned
        if (side != turnSide) {
            turnSide = side
            turnRunStart = t
        }
    }
}
