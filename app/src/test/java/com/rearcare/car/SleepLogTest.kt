package com.rearcare.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SleepLogTest {

    private fun cond(kph: Float? = 60f, setC: Float? = 23f, fan: Float? = 3f, cabinC: Float? = 19.1f) = SleepLog.Conditions(kph, setC, fan, cabinC)

    /** 0초에 출발해 from부터 to 전까지 5초마다 값을 넣는다 */
    private fun SleepLog.feed(from: Long, to: Long, awakeSeen: Boolean, c: SleepLog.Conditions) {
        var t = from
        while (t < to) {
            sample(t, awakeSeen, c)
            t += SleepLog.SAMPLE_MS
        }
    }

    // ---------- 화면에서 쓰는 규칙 ----------

    private fun status(state: ChildStateTracker.State, eyesUnknown: Boolean = false) =
        ChildStateTracker.Status(state, sinceMs = 0, eyesUnknown = eyesUnknown)

    @Test
    fun 깨어_있거나_우는_아이를_봤으면_깨어_있는_때로_센다() {
        assertTrue(SleepLog.awakeSeen(status(ChildStateTracker.State.AWAKE)))
        assertTrue(SleepLog.awakeSeen(status(ChildStateTracker.State.CRYING)))
        assertTrue(SleepLog.awakeSeen(status(ChildStateTracker.State.LOOKING_SIDE)))
    }

    @Test
    fun 눈을_못_읽거나_얼굴이_안_보이거나_소리만_들리면_깨어_있는지_모른다() {
        assertFalse(SleepLog.awakeSeen(status(ChildStateTracker.State.LOOKING_SIDE, eyesUnknown = true)))
        assertFalse(SleepLog.awakeSeen(status(ChildStateTracker.State.FACE_HIDDEN)))
        assertFalse(SleepLog.awakeSeen(status(ChildStateTracker.State.CHECKING)))
        assertFalse(SleepLog.awakeSeen(status(ChildStateTracker.State.CRY_SOUND)))
        assertFalse(SleepLog.awakeSeen(status(ChildStateTracker.State.ASLEEP)))
    }

    @Test
    fun 출발_시각은_자정부터_분으로_세고_초는_버린다() {
        assertEquals(0, SleepLog.minuteOfDay(LocalTime.MIDNIGHT))
        assertEquals(9 * 60 + 40, SleepLog.minuteOfDay(LocalTime.of(9, 40, 59)))
        assertEquals(23 * 60 + 59, SleepLog.minuteOfDay(LocalTime.of(23, 59)))
    }

    // ---------- 잔 시간 ----------

    @Test
    fun `잠든 때부터 깬 때까지를 잔 시간으로 센다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.asleepChanged(true, 50_000) // 눈을 감기 시작한 때
        log.asleepChanged(false, 200_000) // 눈을 뜨기 시작한 때
        val s = log.summary(300_000)
        assertEquals(150, s.sleptSec)
        assertEquals(50, s.firstSleepSec)
        assertEquals(1, s.sleeps)
        assertFalse(s.arrivedAsleep)
    }

    @Test
    fun `두 번 자면 더하고 도착할 때 자고 있으면 도착까지 센다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.asleepChanged(true, 60_000)
        log.asleepChanged(false, 120_000)
        log.asleepChanged(true, 200_000)
        val s = log.summary(260_000)
        assertEquals(60 + 60, s.sleptSec)
        assertEquals(2, s.sleeps)
        assertTrue(s.arrivedAsleep)
    }

    @Test
    fun `출발할 때 이미 자고 있었으면 출발부터 센다`() {
        val log = SleepLog()
        log.start(10_000, asleepNow = true)
        val s = log.summary(70_000)
        assertEquals(60, s.sleptSec)
        assertEquals(0, s.firstSleepSec)
    }

    @Test
    fun `자지 않았으면 잔 시간이 0이고 처음 잠든 때가 없다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        val s = log.summary(600_000)
        assertEquals(0, s.sleptSec)
        assertNull(s.firstSleepSec)
        assertEquals(0, s.sleeps)
    }

    @Test
    fun `셈해 봐도 상태는 그대로라 잠깐 세웠다 이어 가면 같은 잠이 이어진다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.asleepChanged(true, 30_000)
        assertEquals(30, log.summary(60_000).sleptSec)
        assertEquals(90, log.summary(120_000).sleptSec)
    }

    // ---------- 조건 ----------

    @Test
    fun `조건은 자는 동안과 깨어 있는 동안으로 나눠 평균을 낸다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.feed(0, 60_000, awakeSeen = true, cond(kph = 40f, fan = 4f))
        log.asleepChanged(true, 60_000)
        log.feed(60_000, 180_000, awakeSeen = false, cond(kph = 90f, fan = 4f))
        val s = log.summary(180_000)
        assertEquals(60, s.awake.sec)
        assertEquals(120, s.asleep.sec)
        assertEquals(40f, s.awake.kph!!.avg, 0.01f)
        assertEquals(90f, s.asleep.kph!!.avg, 0.01f)
        assertEquals(120, s.asleep.kph!!.sec)
    }

    @Test
    fun `잠든 때를 거슬러 정하면 그동안 깨어 있다고 넣은 값도 자는 동안으로 옮긴다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.feed(0, 60_000, awakeSeen = true, cond(kph = 50f))
        // 60초에 "자는 것 같아요"가 됐지만 눈은 40초부터 감고 있었다
        log.asleepChanged(true, 40_000)
        val s = log.summary(60_000)
        assertEquals(40, s.awake.sec)
        assertEquals(20, s.asleep.sec)
    }

    @Test
    fun `얼굴이 안 보이던 때는 어느 쪽에도 넣지 않는다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.feed(0, 60_000, awakeSeen = false, cond())
        val s = log.summary(60_000)
        assertEquals(0, s.awake.sec)
        assertEquals(0, s.asleep.sec)
        assertNull(s.awake.kph)
    }

    @Test
    fun `잠든 뒤 앱이 바람을 바꾸면 그 잠의 바람은 바꾼 때부터 빼고 속도는 그대로 센다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.asleepChanged(true, 0)
        log.feed(0, 60_000, awakeSeen = false, cond(kph = 80f, fan = 5f))
        log.appChanged(60_000, temp = false, fan = true)
        log.feed(60_000, 120_000, awakeSeen = false, cond(kph = 80f, fan = 2f))
        val s = log.summary(120_000)
        assertEquals(5f, s.asleep.fan!!.avg, 0.01f) // 앱이 줄인 2단은 세지 않는다
        assertEquals(60, s.asleep.fan!!.sec)
        assertEquals(120, s.asleep.kph!!.sec)
        assertEquals(23f, s.asleep.setC!!.avg, 0.01f) // 온도는 바꾸지 않았으니 그대로 센다
        assertEquals(120, s.asleep.setC!!.sec)
    }

    @Test
    fun `앱이 바꾼 것은 그 잠에만 해당하고 깬 동안과 다음 잠에는 다시 센다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.asleepChanged(true, 0)
        log.appChanged(10_000, temp = true, fan = false)
        log.feed(10_000, 60_000, awakeSeen = false, cond(setC = 22f))
        log.asleepChanged(false, 60_000)
        log.feed(60_000, 120_000, awakeSeen = true, cond(setC = 22f))
        log.asleepChanged(true, 120_000)
        log.feed(120_000, 180_000, awakeSeen = false, cond(setC = 22f))
        val s = log.summary(180_000)
        assertEquals(60, s.awake.setC!!.sec)
        assertEquals(60, s.asleep.setC!!.sec) // 첫 잠의 50초는 빼고 둘째 잠의 60초만
    }

    @Test
    fun `모르는 값은 평균에서 빼고 셀 수 있는 시간만 남긴다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.feed(0, 30_000, awakeSeen = true, cond(kph = null))
        log.feed(30_000, 60_000, awakeSeen = true, cond(kph = 30f))
        val s = log.summary(60_000)
        assertEquals(60, s.awake.sec)
        assertEquals(30, s.awake.kph!!.sec)
        assertEquals(30f, s.awake.kph!!.avg, 0.01f)
    }

    @Test
    fun `시작하기 전 값은 받지 않는다`() {
        val log = SleepLog()
        log.sample(0, true, cond())
        log.asleepChanged(true, 0)
        log.start(10_000, asleepNow = false)
        assertEquals(0, log.summary(20_000).awake.sec)
        assertEquals(0, log.summary(20_000).sleptSec)
    }

    // ---------- 저장 칸 ----------

    @Test
    fun `저장 칸은 그대로 돌아오고 모르는 칸은 건너뛴다`() {
        val s = SleepSummary(35, 27, 1, false, SleepSummary.Phase(35, SleepSummary.Value(90f, 35)), SleepSummary.Phase(60, SleepSummary.Value(40f, 60)))
        assertEquals(s, SleepSummary.decode(SleepSummary.encode(s)))
        assertEquals(s, SleepSummary.decode(SleepSummary.encode(s) + ";x=1"))
        assertNull(SleepSummary.decode(""))
        assertNull(SleepSummary.decode("n=1"))
    }

    // ---------- 여러 주행 합치기와 문장 ----------

    private fun trip(id: String, minutes: Int, sleep: SleepSummary?, sample: Boolean = false) =
        Trip(id, LocalDate.of(2026, 11, 6), minutes, "곳$id", sample = sample, sleep = sleep)

    private fun summary(slept: Int, first: Int?, asleepSec: Int, asleepKph: Float, awakeSec: Int, awakeKph: Float) = SleepSummary(
        slept, first, if (slept > 0) 1 else 0, false,
        SleepSummary.Phase(asleepSec, SleepSummary.Value(asleepKph, asleepSec)),
        SleepSummary.Phase(awakeSec, SleepSummary.Value(awakeKph, awakeSec)),
    )

    @Test
    fun `여러 길은 시간으로 가중 평균을 낸다`() {
        val r = SleepStats.report(
            listOf(
                trip("1", 60, summary(1800, 600, 1800, 90f, 1800, 40f)),
                trip("2", 60, summary(600, 1200, 600, 70f, 3000, 50f)),
                trip("3", 30, summary(0, null, 0, 0f, 1800, 30f)),
                trip("4", 30, null), // 잠 기록이 없는 예전 기록
            ),
        )
        assertEquals(3, r.trips)
        assertEquals(2, r.sleptTrips)
        assertEquals(2400L, r.sleptSec)
        assertEquals(1200L, r.avgSleepSec)
        assertEquals(900L, r.avgFirstSleepSec)
        assertEquals((90f * 1800 + 70f * 600) / 2400, r.kph.asleep!!, 0.01f)
        assertEquals((40f * 1800 + 50f * 3000 + 30f * 1800) / 6600, r.kph.awake!!, 0.01f)
        assertTrue(r.enough)
        assertFalse(r.hasSample)
    }

    @Test
    fun `자는 동안이나 깨어 있는 동안이 30분 안 되면 비교하지 않는다`() {
        val r = SleepStats.report(listOf(trip("1", 10, summary(35, 27, 35, 90f, 600, 40f), sample = false)))
        assertFalse(r.enough)
        assertTrue(SleepStats.report(listOf(trip("1", 10, summary(35, 27, 35, 90f, 600, 40f), sample = true))).hasSample)
    }

    @Test
    fun `기록을 남길 때는 잔 것 같을 때만 잔 시간을 덧붙인다`() {
        assertEquals("오늘 약 38분 잠든 것 같아요.", SleepStats.savedLine(summary(38 * 60, 600, 0, 0f, 0, 0f)))
        assertEquals("오늘 약 35초 잠든 것 같아요.", SleepStats.savedLine(summary(35, 27, 35, 90f, 60, 40f)))
        assertNull(SleepStats.savedLine(summary(0, null, 0, 0f, 0, 0f)))
    }

    @Test
    fun `모르는 값은 모름으로 쓴다`() {
        assertEquals("82 km/h", SleepStats.kph(81.6f))
        assertEquals("22.5도", SleepStats.celsius(22.46f))
        assertEquals("2.0단", SleepStats.fan(2f))
        assertEquals("6%", SleepStats.percent(6.4f))
        assertEquals("모름", SleepStats.kph(null))
        assertEquals("모름", SleepStats.percent(null))
    }

    // ---------- 몇 시부터 몇 시까지 ----------

    @Test
    fun `시각은 오전 오후로 쓴다`() {
        assertEquals("오전 9시 10분", SleepStats.clock(550))
        assertEquals("오후 12시", SleepStats.clock(720))
        assertEquals("오전 12시 5분", SleepStats.clock(5))
        assertEquals("오후 12시 50분", SleepStats.clock(770))
        assertEquals("오전 1시", SleepStats.clock(24 * 60 + 60)) // 자정을 넘긴 길
        assertEquals("9:10", SleepStats.shortClock(550))
        assertEquals("12:50", SleepStats.shortClock(770))
    }

    @Test
    fun `잔 구간과 탄 시간은 시각으로 쓰고 출발 시각을 모르면 출발부터 잰다`() {
        val s = SleepSummary(
            5100, 1800, 2, false, SleepSummary.Phase(0), SleepSummary.Phase(0),
            departMinute = 550, spans = listOf(SleepSummary.Span(1800, 4800), SleepSummary.Span(9000, 11100)),
        )
        assertEquals("오전 9시 40분 ~ 10시 30분", SleepStats.spanLine(s, s.spans[0]))
        assertEquals("오전 11시 40분 ~ 오후 12시 15분", SleepStats.spanLine(s, s.spans[1]))
        assertEquals("오전 9시 10분 ~ 오후 12시 50분 · 3시간 40분 주행", SleepStats.rideLine(trip("1", 220, s)))
        val noClock = s.copy(departMinute = null)
        assertEquals("출발 후 30분 ~ 1시간 20분", SleepStats.spanLine(noClock, noClock.spans[0]))
        assertEquals("3시간 40분 주행", SleepStats.rideLine(trip("1", 220, noClock)))
    }

    @Test
    fun `탄 시간은 초 단위 값을 쓰고 없으면 분과 잔 구간으로 어림한다`() {
        val s = SleepSummary(39, 21, 1, true, SleepSummary.Phase(0), SleepSummary.Phase(0), spans = listOf(SleepSummary.Span(21, 60)))
        assertEquals(60L, SleepStats.rideSec(trip("1", 0, s)))
        assertEquals(120L, SleepStats.rideSec(trip("1", 2, s)))
        assertEquals(85L, SleepStats.rideSec(trip("1", 1, s.copy(rideSec = 85)))) // 1분 25초 탄 주행이 "1분"으로 줄지 않는다
    }

    @Test
    fun `잠 기록은 탄 시간을 초로 남기고 저장했다가 그대로 읽는다`() {
        val log = SleepLog()
        log.start(5_000, asleepNow = false)
        val s = log.summary(90_000)
        assertEquals(85, s.rideSec)
        assertEquals(85, SleepSummary.decode(SleepSummary.encode(s))!!.rideSec)
    }

    // ---------- 잠들 때와 조명 ----------

    @Test
    fun `잠든 구간과 잠들 때의 값을 남긴다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false, departMinute = 550)
        log.feed(0, 40_000, awakeSeen = true, cond(kph = 60f).copy(lightOn = true, ambient = 70f))
        log.feed(40_000, 120_000, awakeSeen = true, cond(kph = 88f).copy(lightOn = false, ambient = 30f))
        log.asleepChanged(true, 50_000) // 50초에 잠들었다(그때 시속 88km, 불 꺼짐)
        log.asleepChanged(false, 100_000)
        val s = log.summary(120_000)
        assertEquals(550, s.departMinute)
        assertEquals(listOf(SleepSummary.Span(50, 100)), s.spans)
        assertEquals(SleepSummary.Onset(88f, 23f, 3f, 19.1f, false, 30f), s.onsets.single())
    }

    @Test
    fun `차가 서 있을 때 잠들면 잠들 때의 값은 모른다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.asleepChanged(true, 30_000) // 달리는 값을 받기 전이다
        val s = log.summary(60_000)
        assertEquals(1, s.onsets.size)
        assertFalse(s.onsets.single().known)
    }

    @Test
    fun `조명은 켜진 몫과 무드등 밝기로 남기고 앱이 끈 뒤로는 그 잠에서 뺀다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.feed(0, 60_000, awakeSeen = true, cond().copy(lightOn = true, ambient = 80f))
        log.asleepChanged(true, 60_000)
        log.feed(60_000, 120_000, awakeSeen = false, cond().copy(lightOn = true, ambient = 80f))
        log.appChanged(120_000, temp = false, fan = false, light = true) // 앱이 불을 끄고 무드등을 어둡게 했다
        log.feed(120_000, 180_000, awakeSeen = false, cond().copy(lightOn = false, ambient = 10f))
        val s = log.summary(180_000)
        assertEquals(100f, s.awake.light!!.avg, 0.01f)
        assertEquals(100f, s.asleep.light!!.avg, 0.01f) // 앱이 끈 뒤의 꺼진 불은 세지 않는다
        assertEquals(60, s.asleep.light!!.sec)
        assertEquals(80f, s.asleep.ambient!!.avg, 0.01f)
        assertEquals(120, s.asleep.kph!!.sec) // 속도는 그대로 센다
    }

    @Test
    fun `새 칸도 저장했다가 그대로 돌아오고 예전 칸만 있는 줄도 읽는다`() {
        val s = SleepSummary(
            sleptSec = 85 * 60, firstSleepSec = 1800, sleeps = 2, arrivedAsleep = false,
            asleep = SleepSummary.Phase(5100, SleepSummary.Value(92f, 5100), light = SleepSummary.Value(0f, 3060), ambient = SleepSummary.Value(15f, 3060)),
            awake = SleepSummary.Phase(7290, SleepSummary.Value(61f, 7290), light = SleepSummary.Value(15f, 7290)),
            departMinute = 550,
            spans = listOf(SleepSummary.Span(1800, 4800), SleepSummary.Span(9000, 11100)),
            onsets = listOf(SleepSummary.Onset(88f, 22.5f, 3f, 23f, false, 30f), SleepSummary.Onset(kph = 95f, lightOn = true)),
        )
        assertEquals(s, SleepSummary.decode(SleepSummary.encode(s)))
        val old = SleepSummary.decode("s=39;f=21;n=1;a=0;zs=40;zk=90.0/40;ws=50;wk=46.0/50")!!
        assertNull(old.departMinute)
        assertTrue(old.spans.isEmpty())
        assertTrue(old.onsets.isEmpty())
        assertNull(old.asleep.light)
    }

    @Test
    fun `잠들 때의 값은 잠든 번마다 평균 내고 조명은 횟수로 센다`() {
        fun withOnsets(vararg o: SleepSummary.Onset) = SleepSummary(600, 60, o.size, false, SleepSummary.Phase(600), SleepSummary.Phase(600), onsets = o.toList())
        val r = SleepStats.report(
            listOf(
                trip("1", 60, withOnsets(SleepSummary.Onset(80f, 23f, 2f, 22f, false, 20f), SleepSummary.Onset(90f, 24f, 3f, 23f, false, 40f))),
                trip("2", 60, withOnsets(SleepSummary.Onset(70f, 22f, 1f, 21f, true, null), SleepSummary.Onset())), // 모르는 값은 빼고 센다
                trip("3", 60, summary(0, null, 0, 0f, 600, 40f)), // 잠들 때의 값이 없는 예전 기록
            ),
        )
        assertEquals(3, r.onsets.count)
        assertEquals(80f, r.onsets.kph!!, 0.01f)
        assertEquals(23f, r.onsets.setC!!, 0.01f)
        assertEquals(30f, r.onsets.ambient!!, 0.01f)
        assertEquals(2, r.onsets.lightOff)
        assertEquals(1, r.onsets.lightOn)
        assertEquals("꺼져 있을 때 2번, 켜져 있을 때 1번 잠든 것 같습니다", SleepStats.onsetLightLine(r.onsets))
        assertEquals("꺼져 있을 때 2번 잠든 것 같습니다", SleepStats.onsetLightLine(r.onsets.copy(lightOn = 0)))
        assertNull(SleepStats.onsetLightLine(r.onsets.copy(lightOff = 0, lightOn = 0)))
    }

    // ---------- 얼굴이 안 보이면 안 보이는 것으로 둔다 ----------

    @Test
    fun `자다가 얼굴이 안 보이던 시간은 잔 시간과 자는 동안에 넣지 않는다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.asleepChanged(true, 60_000)
        log.eyesUnseenChanged(true, 120_000)
        log.eyesUnseenChanged(false, 180_000)
        log.asleepChanged(false, 240_000)
        log.feed(60_000, 120_000, awakeSeen = false, cond(kph = 80f))
        log.feed(120_000, 180_000, awakeSeen = false, cond(kph = 30f))
        log.feed(180_000, 240_000, awakeSeen = false, cond(kph = 100f))
        val s = log.summary(300_000)
        assertEquals(120, s.sleptSec)
        assertEquals(120, s.asleep.sec)
        assertEquals(0, s.awake.sec)
        assertEquals(90f, s.asleep.kph!!.avg, 0.01f)
    }

    @Test
    fun `안 보이던 때를 사이에 두면 잔 구간은 나뉘어도 잠든 횟수는 한 번이고 잠들 때의 값도 하나다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.asleepChanged(true, 60_000)
        log.eyesUnseenChanged(true, 120_000)
        log.eyesUnseenChanged(false, 180_000)
        log.asleepChanged(false, 240_000)
        log.feed(0, 240_000, awakeSeen = false, cond())
        val s = log.summary(300_000)
        assertEquals(listOf(SleepSummary.Span(60, 120), SleepSummary.Span(180, 240)), s.spans)
        assertEquals(1, s.sleeps)
        assertEquals(60, s.firstSleepSec)
        assertEquals(2, s.onsets.size)
        assertTrue(s.onsets[0].known)
        assertFalse(s.onsets[1].known)
        assertEquals(1, SleepStats.report(listOf(tripWith(s))).onsets.count)
    }

    @Test
    fun `안 보이다가 깬 모습으로 돌아오면 마지막으로 본 때까지만 잔 것으로 센다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.asleepChanged(true, 60_000)
        log.eyesUnseenChanged(true, 120_000)
        log.eyesUnseenChanged(false, 180_000)
        log.asleepChanged(false, 180_000)
        val s = log.summary(240_000)
        assertEquals(listOf(SleepSummary.Span(60, 120)), s.spans)
        assertEquals(60, s.sleptSec)
    }

    @Test
    fun `도착할 때 얼굴이 안 보이면 안 보이기 전까지만 세고 자면서 도착했다고 하지 않는다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.asleepChanged(true, 60_000)
        log.eyesUnseenChanged(true, 120_000)
        val s = log.summary(180_000)
        assertEquals(60, s.sleptSec)
        assertFalse(s.arrivedAsleep)
    }

    @Test
    fun `출발할 때 자고 있어도 얼굴이 안 보이면 다시 보일 때부터 센다`() {
        val log = SleepLog()
        log.start(0, asleepNow = true, eyesUnseenNow = true)
        log.eyesUnseenChanged(false, 30_000)
        log.feed(0, 60_000, awakeSeen = false, cond())
        val s = log.summary(60_000)
        assertEquals(listOf(SleepSummary.Span(30, 60)), s.spans)
        assertEquals(30, s.sleptSec)
        assertFalse(s.onsets.single().known)
    }

    @Test
    fun `앱이 바꾼 값은 얼굴이 안 보였다 돌아와도 그 잠이 끝날 때까지 뺀다`() {
        val log = SleepLog()
        log.start(0, asleepNow = true)
        log.appChanged(30_000, temp = false, fan = true)
        log.eyesUnseenChanged(true, 60_000)
        log.eyesUnseenChanged(false, 90_000)
        log.feed(0, 30_000, awakeSeen = false, cond(fan = 5f))
        log.feed(30_000, 120_000, awakeSeen = false, cond(fan = 1f))
        val s = log.summary(120_000)
        assertEquals(5f, s.asleep.fan!!.avg, 0.01f)
    }

    @Test
    fun `잠 밖에서는 얼굴을 놓쳐도 깨어 있는 동안 셈이 그대로다`() {
        val log = SleepLog()
        log.start(0, asleepNow = false)
        log.eyesUnseenChanged(true, 10_000)
        log.eyesUnseenChanged(false, 20_000)
        log.feed(0, 30_000, awakeSeen = true, cond())
        val s = log.summary(30_000)
        assertEquals(30, s.awake.sec)
        assertEquals(0, s.sleptSec)
    }

    private fun tripWith(s: SleepSummary) = Trip("1", LocalDate.of(2026, 11, 8), 5, "곳", sleep = s)
}
