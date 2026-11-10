package com.rearcare.car

import java.util.Locale
import kotlin.math.roundToInt

/**
 * 여러 주행(또는 주행 하나)의 잠 기록을 합쳐 "주행 중 수면" 화면에 보일 숫자와 문장을 만든다. 순수 Kotlin.
 * 추천은 하지 않고 기록된 사실만 낸다.
 * 앱의 잠은 "자는 것 같아요"로 본 것이라, 문장도 "잠든 것 같습니다"로 쓴다.
 */
object SleepStats {

    /**
     * 모든 주행 화면에서 비교를 보여 주려면 자는 동안과 깨어 있는 동안이 각각 이만큼 모여야 한다.
     * 근거가 있는 기준이 아니라, 몇 분으로 낸 평균을 비교처럼 보이지 않으려고 정한 값이다. 주행 하나 화면에는 걸지 않는다.
     */
    const val ENOUGH_SEC = 30 * 60

    data class Both(val asleep: Float?, val awake: Float?)

    /** 잠들 때의 값을 모은 것. 값은 평균, lightOff·lightOn은 실내등·독서등이 꺼져/켜져 있을 때 잠든 횟수 */
    data class Onsets(
        val count: Int,
        val kph: Float?,
        val setC: Float?,
        val fan: Float?,
        val cabinC: Float?,
        val ambient: Float?,
        val lightOff: Int,
        val lightOn: Int,
    )

    data class Report(
        val trips: Int, // 잠 기록이 있는 길
        val sleptTrips: Int, // 그중 잔 것 같은 길
        val sleptSec: Long, // 모두 잔 시간
        val avgSleepSec: Long?, // 잔 길에서 한 번 탈 때 잔 시간의 평균
        val avgFirstSleepSec: Long?, // 잔 길에서 출발부터 잠들 때까지의 평균
        val asleepSec: Long, // 비교에 쓴 자는 동안
        val awakeSec: Long, // 비교에 쓴 깨어 있는 동안
        val kph: Both,
        val setC: Both,
        val fan: Both,
        val cabinC: Both,
        val light: Both, // 실내등·독서등이 켜져 있던 몫(%)
        val ambient: Both, // 무드등 밝기 평균(%)
        val onsets: Onsets,
        val hasSample: Boolean, // 예시 기록이 섞여 있는가
    ) {
        val enough: Boolean get() = asleepSec >= ENOUGH_SEC && awakeSec >= ENOUGH_SEC
    }

    fun report(trips: List<Trip>): Report {
        val logged = trips.filter { it.sleep != null }
        val sums = logged.mapNotNull { it.sleep }
        val slept = sums.filter { it.sleptSec > 0 }
        val firsts = slept.mapNotNull { it.firstSleepSec?.toLong() }

        // 주행마다 값을 낸 시간이 다르므로 시간으로 가중 평균을 낸다
        fun weighted(pick: (SleepSummary) -> SleepSummary.Value?): Float? {
            val values = sums.mapNotNull(pick).filter { it.sec > 0 }
            val total = values.sumOf { it.sec.toLong() }
            return if (total == 0L) null else (values.sumOf { it.avg.toDouble() * it.sec } / total).toFloat()
        }

        // 잠들 때의 값은 잠든 번마다 하나라 그냥 평균을 낸다
        val known = sums.flatMap { it.onsets }.filter { it.known }
        fun mean(pick: (SleepSummary.Onset) -> Float?): Float? = known.mapNotNull(pick).takeIf { it.isNotEmpty() }?.average()?.toFloat()

        return Report(
            trips = logged.size,
            sleptTrips = slept.size,
            sleptSec = slept.sumOf { it.sleptSec.toLong() },
            avgSleepSec = if (slept.isEmpty()) null else slept.sumOf { it.sleptSec.toLong() } / slept.size,
            avgFirstSleepSec = if (firsts.isEmpty()) null else firsts.sum() / firsts.size,
            asleepSec = sums.sumOf { it.asleep.sec.toLong() },
            awakeSec = sums.sumOf { it.awake.sec.toLong() },
            kph = Both(weighted { it.asleep.kph }, weighted { it.awake.kph }),
            setC = Both(weighted { it.asleep.setC }, weighted { it.awake.setC }),
            fan = Both(weighted { it.asleep.fan }, weighted { it.awake.fan }),
            cabinC = Both(weighted { it.asleep.cabinC }, weighted { it.awake.cabinC }),
            light = Both(weighted { it.asleep.light }, weighted { it.awake.light }),
            ambient = Both(weighted { it.asleep.ambient }, weighted { it.awake.ambient }),
            onsets = Onsets(
                count = known.size,
                kph = mean { it.kph },
                setC = mean { it.setC },
                fan = mean { it.fan },
                cabinC = mean { it.cabinC },
                ambient = mean { it.ambient },
                lightOff = known.count { it.lightOn == false },
                lightOn = known.count { it.lightOn == true },
            ),
            hasSample = logged.any { it.sample },
        )
    }

    /** 35초, 38분, 1시간, 1시간 5분 */
    fun duration(sec: Long): String = when {
        sec < 60 -> "${sec}초"
        sec < 3600 -> "${sec / 60}분"
        else -> {
            val hours = sec / 3600
            val minutes = sec % 3600 / 60
            if (minutes == 0L) "${hours}시간" else "${hours}시간 ${minutes}분"
        }
    }

    /** 자정부터 분 → "오전 9시 10분", "오후 12시", 오전·오후를 빼려면 withPeriod = false */
    fun clock(minuteOfDay: Int, withPeriod: Boolean = true): String {
        val m = inDay(minuteOfDay)
        val minute = m % 60
        val period = if (m < 12 * 60) "오전" else "오후"
        val time = if (minute == 0) "${hour12(m)}시" else "${hour12(m)}시 ${minute}분"
        return if (withPeriod) "$period $time" else time
    }

    private fun isAm(minuteOfDay: Int) = inDay(minuteOfDay) < 12 * 60

    /** 하루(0~1439분) 안으로 감는다. 자정을 넘긴 값과 음수도 받는다 */
    private fun inDay(minuteOfDay: Int) = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)

    /** 하루 안의 분 → 12시간제 시(1~12) */
    private fun hour12(m: Int) = if ((m / 60) % 12 == 0) 12 else (m / 60) % 12

    /** 짧은 시계: "9:10", "12:50" */
    fun shortClock(minuteOfDay: Int): String {
        val m = inDay(minuteOfDay)
        return String.format(Locale.ROOT, "%d:%02d", hour12(m), m % 60)
    }

    /** 잔 구간 한 줄: "오전 9시 40분 ~ 10시 30분", 출발 시각을 모르면 "출발 후 30분 ~ 1시간 20분" */
    fun spanLine(s: SleepSummary, span: SleepSummary.Span): String {
        val depart = s.departMinute ?: return "출발 후 ${duration(span.fromSec.toLong())} ~ ${duration(span.toSec.toLong())}"
        val from = minuteAt(s, depart, span.fromSec, afterStop = true)
        val to = minuteAt(s, depart, span.toSec, afterStop = false)
        return "${clock(from)} ~ ${clock(to, withPeriod = isAm(from) != isAm(to))}"
    }

    /**
     * 달린 시간 sec의 실제 시각(자정부터 분). 그 자리까지 세워 둔 시간을 더한다.
     * 세운 자리에서 다시 출발하는 시각은 afterStop = true, 세운 자리에 도착한 시각은 false다.
     */
    private fun minuteAt(s: SleepSummary, depart: Int, sec: Int, afterStop: Boolean): Int =
        depart + sec / 60 + s.stops.filter { if (afterStop) it.atSec <= sec else it.atSec < sec }.sumOf { it.minutes }

    /** 그날 마지막으로 차를 세운 시각(자정부터 분) */
    fun arrivalMinute(s: SleepSummary, depart: Int, rideSec: Long): Int = minuteAt(s, depart, rideSec.toInt(), afterStop = false)

    /** 탄 시간 한 줄: "오전 9시 10분 ~ 오후 12시 50분 · 3시간 40분 주행". 다시 탔으면 "오전 9시 ~ 오후 3시, 오후 5시 ~ 7시 · 8시간 주행" */
    fun rideLine(trip: Trip): String {
        val ride = rideSec(trip)
        val s = trip.sleep
        val depart = s?.departMinute
        val time = "${duration(ride)} 주행"
        if (s == null || depart == null) return time
        val stopsAt = s.stops.map { it.atSec }
        val drives = (listOf(0) + stopsAt).zip(stopsAt + ride.toInt()).joinToString(", ") { (from, to) ->
            val a = minuteAt(s, depart, from, afterStop = true)
            val b = minuteAt(s, depart, to, afterStop = false)
            "${clock(a)} ~ ${clock(b, withPeriod = isAm(a) != isAm(b))}"
        }
        return "$drives · $time"
    }

    /**
     * 탄 시간(초). 잠 기록에 초 단위 탄 시간이 있으면 그것을 쓴다.
     * 없는 예전 기록은 다닌 곳 기록의 분(1분 아래는 버려져 있다)과 잔 시간, 마지막 잠의 끝 중 큰 값으로 어림한다
     */
    fun rideSec(trip: Trip): Long {
        val s = trip.sleep
        val fromSpans = s?.spans?.maxOfOrNull { it.toSec.toLong() } ?: 0L
        s?.rideSec?.let { return maxOf(it.toLong(), fromSpans) }
        return maxOf(trip.minutes * 60L, s?.sleptSec?.toLong() ?: 0L, fromSpans)
    }

    /** 기록을 남긴 뒤 "○○를 기록했어요." 뒤에 붙이는 말. 잔 것 같지 않으면 붙이지 않는다 */
    fun savedLine(s: SleepSummary?): String? =
        s?.takeIf { it.sleptSec > 0 }?.let { "오늘 약 ${duration(it.sleptSec.toLong())} 잠든 것 같아요." }

    /** 값을 모를 때 보이는 글. 화면은 이 글이면 옅은 색으로 쓴다 */
    const val UNKNOWN = "모름"

    fun kph(v: Float?) = v?.let { "${it.roundToInt()} km/h" } ?: UNKNOWN

    fun celsius(v: Float?) = v?.let { String.format(Locale.ROOT, "%.1f도", it) } ?: UNKNOWN

    fun fan(v: Float?) = v?.let { String.format(Locale.ROOT, "%.1f단", it) } ?: UNKNOWN

    fun percent(v: Float?) = v?.let { "${it.roundToInt()}%" } ?: UNKNOWN

    /** 잠들 때 실내등·독서등 한 줄. 모르면 null */
    fun onsetLightLine(o: Onsets): String? = when {
        o.lightOff == 0 && o.lightOn == 0 -> null
        o.lightOn == 0 -> "꺼져 있을 때 ${o.lightOff}번 잠든 것 같습니다"
        o.lightOff == 0 -> "켜져 있을 때 ${o.lightOn}번 잠든 것 같습니다"
        else -> "꺼져 있을 때 ${o.lightOff}번, 켜져 있을 때 ${o.lightOn}번 잠든 것 같습니다"
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
