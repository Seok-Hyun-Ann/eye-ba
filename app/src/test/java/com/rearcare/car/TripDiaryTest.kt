package com.rearcare.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** 나이 계산, 나이별 묶기, 저장 형식을 본다 */
class TripDiaryTest {

    @Test
    fun `같은 곳을 두 번 가도 한 곳으로 센다`() {
        fun trip(id: String, name: String) = Trip(id, LocalDate.of(2026, 1, 1), 0, name)
        assertEquals(2, TripDiary.placeCount(listOf(trip("1", "에버랜드"), trip("2", "전주"), trip("3", "에버랜드"))))
        assertEquals(0, TripDiary.placeCount(emptyList()))
    }

    private val birth = LocalDate.of(2022, 3, 10)

    private fun trip(id: String, date: LocalDate, name: String = id, sample: Boolean = false) =
        Trip(id = id, date = date, minutes = 30, name = name, sample = sample)

    @Test
    fun 만_나이는_생일_전날까지_그대로이고_생일에_오른다() {
        assertEquals(2, TripDiary.age(birth, LocalDate.of(2025, 3, 9)))
        assertEquals(3, TripDiary.age(birth, LocalDate.of(2025, 3, 10)))
    }

    @Test
    fun 윤년_2월_29일에_태어나면_평년에는_3월_1일에_오른다() {
        val leap = LocalDate.of(2020, 2, 29)
        assertEquals(0, TripDiary.age(leap, LocalDate.of(2021, 2, 28)))
        assertEquals(1, TripDiary.age(leap, LocalDate.of(2021, 3, 1)))
        assertEquals(4, TripDiary.age(leap, LocalDate.of(2024, 2, 29)))
    }

    @Test
    fun 나이별로_묶고_묶음_안은_날짜순이다() {
        val groups = TripDiary.groups(
            listOf(
                trip("부산", LocalDate.of(2025, 8, 3)),
                trip("전주", LocalDate.of(2026, 9, 26)),
                trip("경주", LocalDate.of(2025, 4, 12)),
                trip("잠실", LocalDate.of(2026, 4, 5)),
            ),
            birth,
        )
        assertEquals(listOf(3, 4), groups.map { it.age })
        assertEquals(listOf("경주", "부산"), groups[0].trips.map { it.name })
        assertEquals(listOf("잠실", "전주"), groups[1].trips.map { it.name })
    }

    @Test
    fun 생일이_없으면_한_묶음으로_날짜순이다() {
        val groups = TripDiary.groups(
            listOf(trip("부산", LocalDate.of(2025, 8, 3)), trip("경주", LocalDate.of(2025, 4, 12))),
            null,
        )
        assertEquals(1, groups.size)
        assertNull(groups[0].age)
        assertEquals(listOf("경주", "부산"), groups[0].trips.map { it.name })
    }

    @Test
    fun 기록이_없으면_묶음도_없다() {
        assertTrue(TripDiary.groups(emptyList(), birth).isEmpty())
        assertTrue(TripDiary.groups(emptyList(), null).isEmpty())
    }

    @Test
    fun 태어나기_전_날짜의_기록은_따로_묶어_맨_앞에_둔다() {
        val groups = TripDiary.groups(
            listOf(trip("경주", LocalDate.of(2025, 4, 12)), trip("옛날", LocalDate.of(2021, 1, 1))),
            birth,
        )
        assertEquals(listOf(TripDiary.BEFORE_BIRTH, 3), groups.map { it.age })
    }

    @Test
    fun 나이_이름() {
        assertEquals("돌 전", TripDiary.ageLabel(0))
        assertEquals("3살", TripDiary.ageLabel(3))
        assertEquals("생일 전", TripDiary.ageLabel(TripDiary.BEFORE_BIRTH))
    }

    @Test
    fun 나이의_기간을_월까지_보여_준다() {
        assertEquals("2025년 3월 ~ 2026년 3월", TripDiary.ageRange(birth, 3))
        assertEquals("2022년 3월 ~ 2023년 3월", TripDiary.ageRange(birth, 0))
    }

    @Test
    fun 조사는_받침을_보고_고른다() {
        assertEquals("더현대서울을", TripDiary.withObject("더현대서울"))
        assertEquals("부산을", TripDiary.withObject("부산"))
        assertEquals("에버랜드를", TripDiary.withObject("에버랜드"))
        assertEquals("COEX를", TripDiary.withObject("COEX"))
    }

    @Test
    fun 이름은_내비_목적지를_먼저_쓰고_없으면_지금_주소를_쓴다() {
        assertEquals("더현대서울", TripDiary.chooseName("더현대서울", "수정구 시흥동"))
        assertEquals("수정구 시흥동", TripDiary.chooseName("", "수정구 시흥동"))
        assertEquals("수정구 시흥동", TripDiary.chooseName(null, "수정구 시흥동"))
        assertNull(TripDiary.chooseName(" ", ""))
    }

    @Test
    fun 저장_형식은_특수_문자가_있어도_그대로_돌아온다() {
        val t = Trip(
            id = "1730000000000",
            date = LocalDate.of(2026, 11, 5),
            minutes = 72,
            name = "더현대\t서울",
            address = "서울특별시 영등포구\n여의대로 108",
            latitude = 37.52596666,
            longitude = 126.92851111,
            poiId = "36khqkj6n9rfcm5nqhe3",
            poiProvider = "",
            note = "역슬래시 \\ 와\t탭",
        )
        assertEquals(listOf(t), TripDiary.decode(TripDiary.encode(listOf(t))))
    }

    @Test
    fun 좌표가_없어도_저장된다() {
        val t = trip("1", LocalDate.of(2026, 11, 5), name = "수정구 시흥동")
        val back = TripDiary.decode(TripDiary.encode(listOf(t))).single()
        assertNull(back.latitude)
        assertNull(back.longitude)
        assertEquals(t, back)
    }

    @Test
    fun 예시_기록_표시는_저장하지_않는다() {
        // 예시인지는 어느 파일에서 읽었는지로 정한다. 사용자의 기록 파일에 예시 표시가 섞이지 않게 한다
        val t = trip("1", LocalDate.of(2026, 11, 5), sample = true)
        assertEquals(false, TripDiary.decode(TripDiary.encode(listOf(t))).single().sample)
    }

    @Test
    fun 망가진_줄은_건너뛴다() {
        val good = trip("1", LocalDate.of(2026, 11, 5), name = "부산")
        val text = "이상한 줄\n" + TripDiary.encode(listOf(good)) + "1\t날짜아님\t3\t이름\t\t\t\t\t\t\n\n"
        assertEquals(listOf(good), TripDiary.decode(text))
    }

    @Test
    fun 뒤쪽_빈_칸이_없어도_읽는다() {
        val back = TripDiary.decode("s1\t2025-10-19\t0\t에버랜드\t용인시\t37.29\t127.2\n").single()
        assertEquals("에버랜드", back.name)
        assertEquals(37.29, back.latitude!!, 0.0001)
        assertEquals("", back.note)
    }

    @Test
    fun 칸이_너무_많거나_적으면_건너뛴다() {
        assertTrue(TripDiary.decode("s1\t2025-10-19\t0\n").isEmpty())
        assertTrue(TripDiary.decode("s1\t2025-10-19\t0\t이름\t\t\t\t\t\t\t\t더\n").isEmpty()) // 12칸
    }

    @Test
    fun 잠_기록도_저장되고_예전_줄은_잠_없이_읽는다() {
        val sleep = SleepSummary(
            sleptSec = 2280,
            firstSleepSec = 720,
            sleeps = 1,
            arrivedAsleep = false,
            asleep = SleepSummary.Phase(2250, SleepSummary.Value(72.3f, 2250), SleepSummary.Value(22.5f, 1200), SleepSummary.Value(2f, 1200), SleepSummary.Value(19.1f, 2250)),
            awake = SleepSummary.Phase(1320, SleepSummary.Value(31f, 1320), null, SleepSummary.Value(4f, 1320), null),
        )
        val t = trip("1", LocalDate.of(2026, 11, 6), name = "에버랜드").copy(minutes = 60, sleep = sleep)
        assertEquals(listOf(t), TripDiary.decode(TripDiary.encode(listOf(t))))
        // 잠 기록 칸이 생기기 전의 10칸 줄
        assertNull(TripDiary.decode("s1\t2025-10-19\t0\t에버랜드\t\t\t\t\t\t메모\n").single().sleep)
    }

    @Test
    fun 잠_평균이_긴_소수여도_저장_확인은_맞다고_본다() {
        // 2026-11-06 1시간 40분 주행 시험: 파일에는 남았는데 "기록하지 못했어요."가 떴다. 평균이 소수 한 자리로 저장되기 때문이다
        val sleep = SleepSummary(
            sleptSec = 3004,
            firstSleepSec = 750,
            sleeps = 1,
            arrivedAsleep = false,
            asleep = SleepSummary.Phase(3005, SleepSummary.Value(96.93178f, 3005), SleepSummary.Value(24f, 25), SleepSummary.Value(4f, 25), SleepSummary.Value(19.1f, 3005)),
            awake = SleepSummary.Phase(1055, SleepSummary.Value(58.0331f, 1055), SleepSummary.Value(23.71327f, 1055), SleepSummary.Value(3.1308f, 1055), SleepSummary.Value(19.1f, 1055)),
        )
        val list = listOf(trip("1", LocalDate.of(2026, 11, 6), name = "국립중앙과학관").copy(minutes = 67, sleep = sleep))
        val text = TripDiary.encode(list)
        val back = TripDiary.decode(text)
        assertNotEquals(list, back) // 메모리 값과 그대로 비교하면 다르다(고치기 전의 확인 방식)
        assertTrue(TripDiary.sameAsWritten(text, back))
    }

    @Test
    fun 다시_읽은_기록이_빠지면_저장_확인이_틀렸다고_본다() {
        val list = listOf(trip("1", LocalDate.of(2026, 11, 5), name = "부산"), trip("2", LocalDate.of(2026, 11, 6), name = "경주"))
        val text = TripDiary.encode(list)
        assertFalse(TripDiary.sameAsWritten(text, TripDiary.decode(text).take(1)))
        assertFalse(TripDiary.sameAsWritten(text, emptyList()))
    }

    @Test
    fun 잠_칸이_망가지면_잠만_버리고_기록은_남긴다() {
        val back = TripDiary.decode("s1\t2025-10-19\t0\t에버랜드\t\t\t\t\t\t메모\t망가진값\n").single()
        assertEquals("메모", back.note)
        assertNull(back.sleep)
    }

    @Test
    fun 같은_번호면_고치고_없으면_더한다() {
        val a = trip("1", LocalDate.of(2026, 11, 5), name = "부산")
        val b = trip("2", LocalDate.of(2026, 11, 6), name = "경주")
        val list = TripDiary.upsert(listOf(a, b), a.copy(name = "해운대"))
        assertEquals(listOf("해운대", "경주"), list.map { it.name })
        assertEquals(3, TripDiary.upsert(list, trip("3", LocalDate.of(2026, 11, 7))).size)
    }

    // ---------- 세운 주행을 기록 한 줄로 만들기 ----------

    private val seoul = ZoneId.of("Asia/Seoul")

    /** 서울 시각으로 출발해 minutes분 달린 주행 */
    private fun drive(year: Int, month: Int, day: Int, hour: Int, minute: Int, minutes: Int): TripRecorder.Save {
        val start = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, seoul).toInstant().toEpochMilli()
        return TripRecorder.Save(start, start + minutes * 60_000L)
    }

    @Test
    fun 내비_목적지가_있으면_목적지의_이름과_주소와_좌표와_장소_번호로_남긴다() {
        val save = drive(2026, 11, 5, 12, 0, minutes = 35)
        val destination = NaviLink.Place("에버랜드", "경기 용인시 처인구 포곡읍", 37.29, 127.2, poiId = "poi-1", poiProvider = "T")
        val here = NaviLink.Place("", "경기 용인시 처인구", 37.28, 127.21)

        val t = TripDiary.record(save, destination, here, sleep = null, zone = seoul)

        assertEquals(save.startMs.toString(), t.id)
        assertEquals(LocalDate.of(2026, 11, 5), t.date)
        assertEquals(35, t.minutes)
        assertEquals("에버랜드", t.name)
        assertEquals("경기 용인시 처인구 포곡읍", t.address)
        assertEquals(37.29, t.latitude!!, 0.0)
        assertEquals(127.2, t.longitude!!, 0.0)
        assertEquals("poi-1", t.poiId)
        assertEquals("T", t.poiProvider)
        assertNull(t.sleep)
    }

    @Test
    fun 안내를_켜지_않은_길은_지금_주소와_좌표로_남기고_잠_기록을_붙인다() {
        val sleep = SleepSummary(
            sleptSec = 600,
            firstSleepSec = 120,
            sleeps = 1,
            arrivedAsleep = false,
            asleep = SleepSummary.Phase(600, null, null, null, null),
            awake = SleepSummary.Phase(0, null, null, null, null),
        )
        val here = NaviLink.Place("", "경기 성남시 수정구 시흥동", 37.43, 127.07)

        val t = TripDiary.record(drive(2026, 11, 5, 9, 40, minutes = 12), destination = null, here = here, sleep = sleep, zone = seoul)

        assertEquals("경기 성남시 수정구 시흥동", t.name)
        assertEquals("경기 성남시 수정구 시흥동", t.address)
        assertEquals(37.43, t.latitude!!, 0.0)
        assertEquals(127.07, t.longitude!!, 0.0)
        assertEquals("", t.poiId)
        assertEquals("", t.poiProvider)
        assertEquals(sleep, t.sleep)
    }

    @Test
    fun 목적지_좌표가_없거나_0이면_지금_위치_좌표를_쓴다() {
        // 도착 알림으로 받은 목적지에는 좌표가 없다. 그때는 지금 위치가 곧 그 장소다
        val here = NaviLink.Place("", "서울 영등포구 여의도동", 37.526, 126.928)
        val save = drive(2026, 11, 5, 12, 0, minutes = 40)
        for (destination in listOf(
            NaviLink.Place("더현대서울", "서울 영등포구 여의대로 108", null, null),
            NaviLink.Place("더현대서울", "서울 영등포구 여의대로 108", 0.0, 0.0),
        )) {
            val t = TripDiary.record(save, destination, here, sleep = null, zone = seoul)
            assertEquals("더현대서울", t.name)
            assertEquals("서울 영등포구 여의대로 108", t.address)
            assertEquals(37.526, t.latitude!!, 0.0)
            assertEquals(126.928, t.longitude!!, 0.0)
        }
    }

    @Test
    fun 좌표를_어디서도_모르면_비워_둔다() {
        val save = drive(2026, 11, 5, 12, 0, minutes = 40)
        val unknown = TripDiary.record(save, NaviLink.Place("에버랜드", "", 0.0, 0.0), NaviLink.Place("", "", 0.0, 0.0), sleep = null, zone = seoul)
        assertNull(unknown.latitude)
        assertNull(unknown.longitude)
        val noHere = TripDiary.record(save, NaviLink.Place("에버랜드", "", null, null), here = null, sleep = null, zone = seoul)
        assertNull(noHere.latitude)
        assertNull(noHere.longitude)
    }

    @Test
    fun 목적지도_지금_주소도_못_읽으면_정해진_이름으로_남긴다() {
        val t = TripDiary.record(drive(2026, 11, 5, 12, 0, minutes = 5), destination = null, here = null, sleep = null, zone = seoul)
        assertEquals("어디인지 읽지 못한 곳", t.name)
        assertEquals("", t.address)
        assertNull(t.latitude)
    }

    @Test
    fun 날짜는_출발한_때의_현지_날짜다() {
        // 서울 0시 10분은 세계 표준시로 전날 15시 10분이다. 기록 날짜는 서울 날짜로 남는다
        val t = TripDiary.record(drive(2026, 11, 6, 0, 10, minutes = 20), destination = null, here = null, sleep = null, zone = seoul)
        assertEquals(LocalDate.of(2026, 11, 6), t.date)
        assertEquals(20, t.minutes)
    }

    // ---------- 같은 날 다시 탄 주행 ----------

    private val noPhase = SleepSummary.Phase(0)

    private fun day(sleep: SleepSummary?, name: String = "에버랜드", minutes: Int = 360) =
        Trip(id = "100", date = LocalDate.of(2026, 11, 9), minutes = minutes, name = name, note = "처음", sleep = sleep)

    private fun morning(arrivedAsleep: Boolean = false) = SleepSummary(
        sleptSec = 3600, firstSleepSec = 1800, sleeps = 1, arrivedAsleep = arrivedAsleep,
        asleep = SleepSummary.Phase(3600, kph = SleepSummary.Value(60f, 3600)), awake = noPhase,
        departMinute = 9 * 60, spans = listOf(SleepSummary.Span(1800, 5400)),
        onsets = listOf(SleepSummary.Onset(kph = 50f)), rideSec = 6 * 3600,
    )

    private fun evening(firstFrom: Int = 600) = SleepSummary(
        sleptSec = 1800, firstSleepSec = firstFrom, sleeps = 1, arrivedAsleep = false,
        asleep = SleepSummary.Phase(1800, kph = SleepSummary.Value(90f, 1800)), awake = noPhase,
        departMinute = 17 * 60, spans = listOf(SleepSummary.Span(firstFrom, firstFrom + 1800)),
        onsets = listOf(SleepSummary.Onset(kph = 80f)), rideSec = 2 * 3600,
    )

    private fun next(sleep: SleepSummary?, name: String = "집", minutes: Int = 120) =
        Trip(id = "200", date = LocalDate.of(2026, 11, 9), minutes = minutes, name = name, sleep = sleep)

    @Test
    fun 같은_날_다시_타면_번호와_장소와_메모는_처음_것을_둔다() {
        val m = TripDiary.mergeSameDay(day(morning()), next(evening()))
        assertEquals("100", m.id)
        assertEquals("에버랜드", m.name)
        assertEquals("처음", m.note)
    }

    @Test
    fun 처음_장소를_못_읽었으면_이번_장소로_채운다() {
        val m = TripDiary.mergeSameDay(day(morning(), name = TripDiary.UNKNOWN_PLACE), next(evening()))
        assertEquals("100", m.id)
        assertEquals("집", m.name)
        assertEquals("처음", m.note)
    }

    @Test
    fun 음식점에_있던_두_시간은_탄_시간에_넣지_않는다() {
        val m = TripDiary.mergeSameDay(day(morning()), next(evening()))
        assertEquals(480, m.minutes)
        assertEquals(8 * 3600, m.sleep!!.rideSec)
        assertEquals(listOf(SleepSummary.Stop(6 * 3600, 120)), m.sleep!!.stops)
    }

    @Test
    fun 잠은_더하고_다시_탄_뒤의_구간은_달린_시간_뒤에_잇는다() {
        val s = TripDiary.mergeSameDay(day(morning()), next(evening())).sleep!!
        assertEquals(5400, s.sleptSec)
        assertEquals(2, s.sleeps)
        assertEquals(1800, s.firstSleepSec)
        assertEquals(SleepSummary.Span(6 * 3600 + 600, 6 * 3600 + 2400), s.spans[1])
        assertEquals(2, s.onsets.size)
    }

    @Test
    fun 평균은_시간으로_가중해서_합친다() {
        val s = TripDiary.mergeSameDay(day(morning()), next(evening())).sleep!!
        assertEquals(5400, s.asleep.sec)
        assertEquals(70f, s.asleep.kph!!.avg, 0.01f)
        assertEquals(5400, s.asleep.kph!!.sec)
    }

    @Test
    fun 아침에_안_자고_저녁에_처음_잤으면_달린_시간을_더해_첫_잠을_센다() {
        val awakeMorning = morning().copy(sleptSec = 0, firstSleepSec = null, sleeps = 0, spans = emptyList(), onsets = emptyList())
        val s = TripDiary.mergeSameDay(day(awakeMorning), next(evening())).sleep!!
        assertEquals(6 * 3600 + 600, s.firstSleepSec)
    }

    @Test
    fun 세울_때도_출발할_때도_자고_있었으면_새로_잠든_것이_아니다() {
        val s = TripDiary.mergeSameDay(day(morning(arrivedAsleep = true)), next(evening(firstFrom = 0))).sleep!!
        assertEquals(1, s.sleeps)
        assertEquals(2, s.spans.size)
        assertFalse(s.onsets[1].known)
    }

    @Test
    fun 시각에는_세워_둔_시간을_더한다() {
        val m = TripDiary.mergeSameDay(day(morning()), next(evening()))
        val s = m.sleep!!
        assertEquals("오전 9시 ~ 오후 3시, 오후 5시 ~ 7시 · 8시간 주행", SleepStats.rideLine(m))
        assertEquals("오전 9시 30분 ~ 10시 30분", SleepStats.spanLine(s, s.spans[0]))
        assertEquals("오후 5시 10분 ~ 5시 40분", SleepStats.spanLine(s, s.spans[1]))
    }

    @Test
    fun 세운_시간은_저장했다_읽어도_남는다() {
        val m = TripDiary.mergeSameDay(day(morning()), next(evening()))
        val back = TripDiary.decode(TripDiary.encode(listOf(m))).single()
        assertEquals(listOf(SleepSummary.Stop(6 * 3600, 120)), back.sleep!!.stops)
    }

    @Test
    fun 세_번_타도_시각이_맞는다() {
        val two = TripDiary.mergeSameDay(day(morning()), next(evening()))
        val third = next(evening().copy(departMinute = 20 * 60, rideSec = 3600), minutes = 60)
        val m = TripDiary.mergeSameDay(two, third)
        assertEquals(listOf(SleepSummary.Stop(6 * 3600, 120), SleepSummary.Stop(8 * 3600, 60)), m.sleep!!.stops)
        assertEquals("오전 9시 ~ 오후 3시, 오후 5시 ~ 7시, 오후 8시 ~ 9시 · 9시간 주행", SleepStats.rideLine(m))
    }

    @Test
    fun 잠_기록이_없는_예전_기록에도_더할_수_있다() {
        val m = TripDiary.mergeSameDay(day(null, minutes = 60), next(evening()))
        assertEquals(180, m.minutes)
        assertEquals(1800, m.sleep!!.sleptSec)
    }
}
