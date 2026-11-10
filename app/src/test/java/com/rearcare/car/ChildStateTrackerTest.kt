package com.rearcare.car

import com.rearcare.car.ChildStateTracker.Config
import com.rearcare.car.ChildStateTracker.EventType
import com.rearcare.car.ChildStateTracker.FaceReading
import com.rearcare.car.ChildStateTracker.Observation
import com.rearcare.car.ChildStateTracker.Side
import com.rearcare.car.ChildStateTracker.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 상태 규칙 시험. PC에서 바로 돈다(에뮬레이터 필요 없음): gradlew :app:testDebugUnitTest
 * 1초에 관찰 하나씩 넣는 것처럼 시간을 1000ms씩 늘린다.
 */
class ChildStateTrackerTest {

    private val open = FaceReading(eyesClosed = 0.05f, yawDeg = 2f, noseOffset = 0.02f)
    private val closed = FaceReading(eyesClosed = 0.6f, yawDeg = 2f, noseOffset = 0.02f)
    private val turnedLeft = FaceReading(eyesClosed = 0.05f, yawDeg = -30f, noseOffset = -0.4f)

    private var now = 0L

    /** seconds초 동안 같은 관찰을 1초마다 넣고, 그동안 생긴 일을 모아 돌려준다 */
    private fun ChildStateTracker.feed(seconds: Int, face: FaceReading?, crying: Boolean = false): List<EventType> {
        val events = mutableListOf<EventType>()
        repeat(seconds) {
            events += update(Observation(now, face, crying)).map { it.type }
            now += 1000
        }
        return events
    }

    @Test
    fun 눈을_뜬_얼굴을_보면_깨어_있음() {
        val t = ChildStateTracker(Config.DEMO)
        assertEquals(State.CHECKING, t.status.state)
        t.feed(2, open)
        assertEquals(State.AWAKE, t.status.state)
    }

    @Test
    fun 정해진_시간_이상_눈을_감아야_잠듦() {
        val t = ChildStateTracker(Config.DEMO) // 시연용: 10초
        t.feed(3, open)
        assertTrue(t.feed(10, closed).isEmpty()) // 0~9초: 아직 10초가 안 됨
        assertEquals(State.AWAKE, t.status.state)
        assertEquals(listOf(EventType.FELL_ASLEEP), t.feed(1, closed)) // 10초째
        assertEquals(State.ASLEEP, t.status.state)
    }

    @Test
    fun 눈을_깜빡인_것은_잠으로_세지_않음() {
        val t = ChildStateTracker(Config.DEMO)
        repeat(5) {
            t.feed(6, closed)
            t.feed(1, open) // 6초마다 한 번 눈을 뜸: 감은 흐름이 끊긴다
        }
        assertEquals(State.AWAKE, t.status.state)
    }

    @Test
    fun 자다가_잠깐_눈을_뜬_것은_깸이_아님() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, closed)
        assertEquals(State.ASLEEP, t.status.state)
        assertTrue(t.feed(3, open).isEmpty()) // 5초 미만
        t.feed(1, closed)
        assertEquals(State.ASLEEP, t.status.state)
        assertEquals(listOf(EventType.WOKE_UP), t.feed(6, open)) // 5초 이상 뜨면 깸
        assertEquals(State.AWAKE, t.status.state)
    }

    @Test
    fun 울면서_눈을_감은_것은_잠이_아님() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        val events = t.feed(20, closed, crying = true)
        assertEquals(listOf(EventType.CRY_STARTED), events)
        assertEquals(State.CRYING, t.status.state)
        assertFalse(EventType.FELL_ASLEEP in events)
    }

    @Test
    fun 울음은_두_번_들려야_시작하고_10초_조용하면_그침() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        assertTrue(t.feed(1, open, crying = true).isEmpty()) // 한 번은 잘못 들은 것일 수 있다
        t.feed(5, open)
        assertTrue(t.feed(1, open, crying = true).isEmpty())
        assertEquals(listOf(EventType.CRY_STARTED), t.feed(1, open, crying = true))
        assertTrue(t.feed(9, open).isEmpty())
        assertEquals(listOf(EventType.CRY_STOPPED), t.feed(1, open))
        assertEquals(State.AWAKE, t.status.state)
    }

    @Test
    fun 울음이_이어지면_3분마다_알림() {
        val t = ChildStateTracker(Config.DEMO)
        val events = t.feed(361, open, crying = true)
        assertEquals(listOf(EventType.CRY_STARTED, EventType.CRY_CONTINUES, EventType.CRY_CONTINUES), events)
    }

    @Test
    fun 자는_중_울면_깬_것으로_보고_그친_뒤_다시_잠들_수_있음() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, closed)
        assertEquals(State.ASLEEP, t.status.state)
        t.feed(5, closed, crying = true)
        assertEquals(State.CRYING, t.status.state)
        t.feed(10, closed) // 10초째에 울음 그침. 그때부터 감은 눈을 다시 센다
        assertEquals(State.AWAKE, t.status.state)
        assertTrue(t.feed(9, closed).isEmpty())
        assertEquals(listOf(EventType.FELL_ASLEEP), t.feed(1, closed))
    }

    @Test
    fun 얼굴을_오래_못_찾으면_추측하지_않음() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, closed)
        t.feed(9, null)
        assertEquals(State.ASLEEP, t.status.state) // 잠깐 놓친 것은 그대로 둔다
        t.feed(2, null)
        assertEquals(State.FACE_HIDDEN, t.status.state)
        assertTrue(t.status.lastSeenAsleep)
    }

    @Test
    fun 고개는_두_방법이_같은_쪽을_3초_가리켜야_돌림() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(2, open)
        t.feed(3, FaceReading(0.05f, yawDeg = 5f, noseOffset = -0.4f)) // 코 위치만 돌림: 정면으로 본다
        assertEquals(State.AWAKE, t.status.state)
        t.feed(3, turnedLeft)
        assertEquals(State.AWAKE, t.status.state) // 0~2초
        t.feed(1, turnedLeft)
        assertEquals(State.LOOKING_SIDE, t.status.state)
        assertEquals(Side.LEFT, t.status.lookSide)
    }

    // ---------- 시선: 고개만 돌린 것과 정말 옆을 보는 것을 나눈다 ----------

    // 시연 사진 c0329: 고개는 돌렸지만 눈동자가 돌아와 카메라를 본다
    private val turnedLookingBack = FaceReading(0.05f, yawDeg = -27f, noseOffset = -0.36f, gazeDeg = 2f)
    private val turnedLookingAside = FaceReading(0.05f, yawDeg = -30f, noseOffset = -0.4f, gazeDeg = -32f)
    private val turnedTooFar = FaceReading(0.05f, yawDeg = -50f, noseOffset = -0.5f, gazeDeg = -50f)

    @Test
    fun 고개만_돌리고_눈이_이쪽을_보면_옆을_보는_것이_아니다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(2, open)
        t.feed(6, turnedLookingBack)
        assertEquals(State.AWAKE, t.status.state)
        assertEquals("깨어 있어요.", StatusWords.answer(t.status, now, Side.LEFT))
    }

    @Test
    fun 시선까지_돌아가면_창문_쪽을_본다고_말한다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(2, open)
        t.feed(4, turnedLookingAside)
        assertEquals(State.LOOKING_SIDE, t.status.state)
        assertEquals(Side.LEFT, t.status.lookSide)
        assertFalse(t.status.eyesUnknown)
        assertEquals("창문 쪽을 보고 있어요", StatusWords.title(t.status, Side.LEFT))
        assertEquals("깨어서 창문 쪽을 보고 있어요.", StatusWords.answer(t.status, now, Side.LEFT))
        // 창문 반대쪽이면 창문이라고 하지 않는다
        assertEquals("고개를 옆으로 돌렸어요", StatusWords.title(t.status, Side.RIGHT))
        assertEquals("깨어서 옆쪽을 보고 있어요.", StatusWords.answer(t.status, now, Side.RIGHT))
    }

    @Test
    fun 고개가_너무_돌아가면_자는지_깨어_있는지_모른다고_말한다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(2, open)
        t.feed(4, turnedTooFar)
        assertEquals(State.LOOKING_SIDE, t.status.state)
        assertTrue(t.status.eyesUnknown)
        assertEquals("창문 쪽을 보고 있어요", StatusWords.title(t.status, Side.LEFT))
        assertTrue(StatusWords.detail(t.status, now).contains("자는지 깨어 있는지는 확인이 안 돼요"))
        assertEquals("창문 쪽을 보고 있어서 자는지 깨어 있는지 확인이 안 돼요.", StatusWords.answer(t.status, now, Side.LEFT))
        assertEquals("고개를 옆으로 돌리고 있어서 자는지 깨어 있는지 확인이 안 돼요.", StatusWords.answer(t.status, now, Side.RIGHT))
    }

    @Test
    fun 고개가_너무_돌아간_동안에는_감은_눈을_잠으로_세지_않는다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(2, open)
        val events = t.feed(30, turnedTooFar.copy(eyesClosed = 0.6f))
        assertFalse(events.contains(EventType.FELL_ASLEEP))
        assertTrue(t.status.eyesUnknown)
    }

    @Test
    fun 자다가_고개가_너무_돌아가도_깼다고_하지_않는다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, closed)
        assertEquals(State.ASLEEP, t.status.state)
        val events = t.feed(10, turnedTooFar)
        assertFalse(events.contains(EventType.WOKE_UP))
        assertEquals(State.ASLEEP, t.status.state)
    }

    @Test
    fun 옆을_보다가_얼굴을_놓치면_자는지_깨어_있는지_모른다고_말한다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(2, open)
        t.feed(4, turnedLookingAside)
        t.feed(11, null)
        assertEquals(State.FACE_HIDDEN, t.status.state)
        assertEquals(Side.LEFT, t.status.lookSide)
        assertEquals("창문 쪽을 보고 있어서 자는지 깨어 있는지 확인이 안 돼요.", StatusWords.answer(t.status, now, Side.LEFT))
        assertTrue(StatusWords.detail(t.status, now).contains("자는지 깨어 있는지는 확인이 안 돼요"))
    }

    @Test
    fun 정면을_보다가_얼굴을_놓치면_그냥_안_보인다고_말한다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(2, open)
        t.feed(11, null)
        assertEquals(State.FACE_HIDDEN, t.status.state)
        assertEquals(null, t.status.lookSide)
        assertEquals("얼굴이 잘 안 보여요.", StatusWords.answer(t.status, now, Side.LEFT))
    }

    @Test
    fun Gleo에_주는_설명은_상태마다_있다() {
        val t = ChildStateTracker(Config.DEMO)
        assertEquals("The app is still checking on the child.", StatusWords.gleoMessage(t.status))
        t.feed(2, open)
        assertEquals("The child is awake.", StatusWords.gleoMessage(t.status))
        t.feed(11, closed)
        assertEquals("The child seems to be asleep.", StatusWords.gleoMessage(t.status))
    }

    @Test
    fun 대답_문장() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, closed)
        t.feed(12 * 60, closed)
        assertEquals("12분째 자고 있어요.", StatusWords.answer(t.status, now, Side.LEFT))

        val u = ChildStateTracker(Config.DEMO)
        now = 0
        u.feed(2, open)
        u.feed(4, turnedLeft)
        assertEquals("깨어서 창문 쪽을 보고 있어요.", StatusWords.answer(u.status, now, Side.LEFT))
        assertEquals("깨어서 옆쪽을 보고 있어요.", StatusWords.answer(u.status, now, Side.RIGHT))
        assertEquals("깨어서 옆쪽을 보고 있어요.", StatusWords.answer(u.status, now, null))

        val v = ChildStateTracker(Config.DEMO)
        now = 0
        v.feed(1, open)
        v.feed(11, closed)
        v.feed(5 * 60, null)
        assertEquals("얼굴이 잘 안 보여요. 아까 봤을 때는 자고 있었어요. 5분째 조용해요.", StatusWords.answer(v.status, now, null))
    }

    // ---------- 울음소리와 우는 얼굴 ----------

    private val calmAsleep = FaceReading(eyesClosed = 0.6f, yawDeg = 2f, noseOffset = 0.02f, cryingFace = false)
    private val cryingFace = FaceReading(eyesClosed = 0.7f, yawDeg = 2f, noseOffset = 0.02f, cryingFace = true)

    @Test
    fun 자는_얼굴인데_울음소리만_나면_울음소리가_들린다고만_하고_잠은_그대로() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, calmAsleep)
        assertEquals(State.ASLEEP, t.status.state)
        assertEquals(listOf(EventType.CRY_SOUND_STARTED), t.feed(5, calmAsleep, crying = true))
        assertEquals(State.CRY_SOUND, t.status.state)
        assertTrue(t.status.childAsleep)
        assertEquals("울음소리가 들리는데, 아이는 눈을 감고 있어요. 다른 곳에서 나는 소리일 수도 있어요.", StatusWords.answer(t.status, now, null))
        t.feed(10, calmAsleep) // 소리가 그침
        assertEquals(State.ASLEEP, t.status.state) // 깼다고 하지 않는다
    }

    @Test
    fun 우는_얼굴과_울음소리가_함께면_아이가_운다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        assertEquals(listOf(EventType.CRY_STARTED), t.feed(5, cryingFace, crying = true))
        assertEquals(State.CRYING, t.status.state)
    }

    @Test
    fun 소리만_들리다가_우는_얼굴이_보이면_그때_울기_시작했다고_함() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        assertEquals(listOf(EventType.CRY_SOUND_STARTED), t.feed(3, open.copy(cryingFace = false), crying = true))
        assertEquals(State.CRY_SOUND, t.status.state)
        assertEquals(listOf(EventType.CRY_STARTED), t.feed(2, cryingFace, crying = true))
        assertEquals(State.CRYING, t.status.state)
    }

    @Test
    fun 우는_얼굴을_한_번_봤으면_얼굴을_놓쳐도_10초까지는_아이가_우는_것으로_유지() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(4, cryingFace, crying = true)
        assertEquals(State.CRYING, t.status.state)
        t.feed(5, null, crying = true) // 얼굴을 놓쳤지만 소리는 이어짐
        assertEquals(State.CRYING, t.status.state)
        assertTrue(t.feed(9, null).isEmpty()) // 소리도 끊겼지만 얼굴을 못 봐서 아직 모른다
        assertEquals(State.CRYING, t.status.state)
        assertEquals(listOf(EventType.CRY_STOPPED), t.feed(1, null))
    }

    @Test
    fun 우는_얼굴을_봤어도_소리가_없고_얼굴이_차분해지면_3초_만에_그침() {
        val t = ChildStateTracker(Config.DEMO)
        val calm = open.copy(cryingFace = false)
        t.feed(1, open)
        t.feed(4, cryingFace, crying = true)
        assertEquals(State.CRYING, t.status.state)
        assertTrue(t.feed(2, calm).isEmpty())
        assertEquals(State.CRYING, t.status.state)
        assertEquals(listOf(EventType.CRY_STOPPED), t.feed(1, calm))
        assertEquals(State.AWAKE, t.status.state)
    }

    @Test
    fun 얼굴이_차분해져도_울음소리가_이어지면_그치지_않음() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(4, cryingFace, crying = true)
        assertTrue(t.feed(8, open.copy(cryingFace = false), crying = true).isEmpty())
        assertEquals(State.CRYING, t.status.state)
    }

    @Test
    fun 우는지_알_수_없는_얼굴이면_3초_만에_끝내지_않음() {
        val t = ChildStateTracker(Config.DEMO)
        val unsure = open.copy(cryingFace = false, maybeCrying = true)
        t.feed(1, open)
        t.feed(4, cryingFace, crying = true)
        assertTrue(t.feed(9, unsure).isEmpty())
        assertEquals(State.CRYING, t.status.state)
        assertEquals(listOf(EventType.CRY_STOPPED), t.feed(1, unsure))
    }

    @Test
    fun 우는지_알_수_없는_얼굴이면_운다고도_잔다고도_하지_않음() {
        val t = ChildStateTracker(Config.DEMO)
        val unsureClosed = closed.copy(cryingFace = false, maybeCrying = true)
        t.feed(1, open)
        t.feed(11, calmAsleep)
        assertEquals(State.ASLEEP, t.status.state)
        assertEquals(listOf(EventType.CRY_SOUND_STARTED), t.feed(5, unsureClosed, crying = true))
        assertEquals(State.CRY_SOUND, t.status.state)
        assertFalse(t.status.childAsleep)
        assertEquals("울음소리가 들리는데, 아이가 우는지는 잘 모르겠어요.", StatusWords.answer(t.status, now, null))
    }

    @Test
    fun 잠든_뒤_얼굴을_못_보면_울음소리가_나도_잔다고_단정하지_않음() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, calmAsleep)
        t.feed(6, null)
        t.feed(3, null, crying = true)
        assertEquals(State.CRY_SOUND, t.status.state)
        assertEquals("울음소리가 들리는데, 얼굴이 잘 안 보여서 아이가 우는 건지 모르겠어요.", StatusWords.answer(t.status, now, null))
    }

    @Test
    fun 얼굴이_안_보이면_울음소리만_들린다고_함() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(6, null)
        assertEquals(listOf(EventType.CRY_SOUND_STARTED), t.feed(3, null, crying = true))
        assertEquals(State.CRY_SOUND, t.status.state)
        assertEquals("울음소리가 들리는데, 얼굴이 잘 안 보여서 아이가 우는 건지 모르겠어요.", StatusWords.answer(t.status, now, null))
    }

    // ---------- 잠 기록에서 못 본 때를 가른다 ----------

    @Test
    fun 얼굴을_10초_못_찾으면_마지막으로_본_때부터_못_본_때로_잡고_잠은_그대로_둔다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, closed) // 마지막으로 본 때 11초
        t.feed(9, null)
        assertFalse(t.eyesUnseen) // 잠깐 놓친 것은 못 본 때가 아니다
        t.feed(1, null)
        assertTrue(t.eyesUnseen)
        assertEquals(11_000L, t.eyesUnseenChangedAtMs)
        assertTrue(t.asleep)
    }

    @Test
    fun 얼굴을_다시_보면_그때부터_본_때로_돌아온다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, closed)
        t.feed(10, null)
        assertTrue(t.eyesUnseen)
        val backAt = now
        t.feed(1, closed)
        assertFalse(t.eyesUnseen)
        assertEquals(backAt, t.eyesUnseenChangedAtMs)
    }

    @Test
    fun 고개가_너무_돌아가_눈을_못_읽는_때도_못_본_때로_잡는다() {
        val far = FaceReading(eyesClosed = 0.6f, yawDeg = -50f, noseOffset = -0.4f)
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, closed)
        val turnedAt = now
        t.feed(3, far)
        assertFalse(t.eyesUnseen) // 잠깐 돌린 것은 못 본 때가 아니다
        t.feed(1, far)
        assertTrue(t.eyesUnseen)
        assertEquals(turnedAt, t.eyesUnseenChangedAtMs)
        assertTrue(t.asleep)
        t.feed(1, closed)
        assertFalse(t.eyesUnseen)
    }

    @Test
    fun 울음소리가_나도_얼굴을_못_찾으면_못_본_때다() {
        val t = ChildStateTracker(Config.DEMO)
        t.feed(1, open)
        t.feed(11, closed)
        t.feed(8, null)
        t.feed(3, null, crying = true)
        assertEquals(State.CRY_SOUND, t.status.state)
        assertTrue(t.eyesUnseen)
    }
}
