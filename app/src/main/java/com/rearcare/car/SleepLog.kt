package com.rearcare.car

import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 기록하는 주행 하나에서 아이가 잔 시간과, 자는 동안·깨어 있는 동안의 차 조건을 모은다. 순수 Kotlin.
 * 자는 동안과 깨어 있는 동안을 통계로만 비교한다. 추천은 하지 않는다.
 * 조명, 몇 시부터 몇 시까지 잤는지, 잠들 때의 평균 조건도 모은다.
 *
 * - 잠은 ChildStateTracker가 정한 시각을 쓴다. 잠든 때는 눈을 감기 시작한 때, 깬 때는 눈을 뜨기 시작한 때나 우는 얼굴을 본 때다.
 *   출발 전에 이미 자고 있었으면 출발부터 센다. 주행을 기록하는 것은 출발부터라서다.
 * - 조건(속도, 설정 온도, 바람, 실내 온도, 실내등·독서등, 무드등 밝기)은 달리는 동안 SAMPLE_MS마다 받는다. 값 하나가 SAMPLE_MS만큼의 시간을 대표한다.
 * - 그 시각이 잠 안이면 "자는 동안", 깨어 있는 아이를 본 때면 "깨어 있는 동안"이다.
 *   얼굴이 안 보이거나 확인 중이거나 고개가 너무 돌아가 눈을 못 읽는 때는 어느 쪽에도 넣지 않는다.
 * - 잠 안이어도 얼굴이 안 보이거나 눈을 못 읽는 때(eyesUnseenChanged)는 잔 시간과 "자는 동안"에 넣지 않는다. 본 것만 센다.
 *   그 앞뒤로 잔 구간은 나뉘지만 새로 잠든 것은 아니다. 잠든 횟수와 잠들 때의 값은 늘지 않는다.
 * - 잠든 뒤 앱이 온도, 바람, 조명을 바꾸면, 그 값은 바꾼 때부터 그 잠이 끝날 때까지 "자는 동안"에 넣지 않는다.
 *   앱이 줄인 바람이나 끈 불을 "잘 때 그랬다"로 세지 않기 위해서다. 속도와 실내 온도는 앱이 바꾸지 않으므로 그대로 센다.
 * - 잠들 때의 값은 잠든 때에 가장 가까운 값이다. 앱은 잠든 뒤에야 실내를 바꾸자고 물으므로 앱이 바꾼 것이 섞이지 않는다.
 * - 시각은 모두 분석 입력과 같은 시계(SystemClock.elapsedRealtime)로 받는다. 출발한 시각(시계)은 화면에 "오전 9시 40분"처럼 보이려고 따로 받는다.
 */
class SleepLog {

    /**
     * 조건 값 하나. 모르면 null. 공조가 꺼져 있으면 설정 온도는 null, 바람은 0으로 넘긴다.
     * lightOn은 실내등이나 뒷좌석 독서등이 켜져 있는가, ambient는 무드등 밝기(0~100)다.
     */
    data class Conditions(
        val kph: Float?,
        val setC: Float?,
        val fan: Float?,
        val cabinC: Float?,
        val lightOn: Boolean? = null,
        val ambient: Float? = null,
    )

    private class Sample(val atMs: Long, val awakeSeen: Boolean, val c: Conditions)
    private class Period(val startMs: Long, var endMs: Long? = null)

    /** 이번 주행의 출발 시각. 아직 시작하지 않았으면 null */
    var startMs: Long? = null
        private set
    private var departMinute: Int? = null

    private val sleeps = mutableListOf<Period>()
    private val unseen = mutableListOf<Period>() // 얼굴이 안 보이거나 눈을 못 읽던 때
    private val samples = mutableListOf<Sample>()
    private val tempChanges = mutableListOf<Long>()
    private val fanChanges = mutableListOf<Long>()
    private val lightChanges = mutableListOf<Long>()

    val started: Boolean get() = startMs != null

    /**
     * 새 주행을 시작한다. 앞 주행의 기록은 지운다. 아이가 이미 자고 있으면 출발부터 잠으로 센다.
     * departMinute는 출발한 시각(자정부터 분)이다. 모르면 화면에 "출발 후 30분"처럼 보인다.
     * eyesUnseenNow는 출발할 때 얼굴이 안 보이거나 눈을 못 읽고 있는가다.
     */
    fun start(nowMs: Long, asleepNow: Boolean, departMinute: Int? = null, eyesUnseenNow: Boolean = false) {
        reset()
        startMs = nowMs
        this.departMinute = departMinute
        if (asleepNow) sleeps += Period(nowMs)
        if (eyesUnseenNow) unseen += Period(nowMs)
    }

    fun reset() {
        startMs = null
        departMinute = null
        sleeps.clear()
        unseen.clear()
        samples.clear()
        tempChanges.clear()
        fanChanges.clear()
        lightChanges.clear()
    }

    /** 아이가 잠들었거나 깼다. atMs는 ChildStateTracker가 정한 그 시각이다 */
    fun asleepChanged(asleep: Boolean, atMs: Long) {
        val start = startMs ?: return
        val open = sleeps.lastOrNull()?.takeIf { it.endMs == null }
        val at = max(atMs, start)
        if (asleep && open == null) sleeps += Period(at)
        if (!asleep && open != null) open.endMs = max(at, open.startMs)
    }

    /** 얼굴이 안 보이게 됐거나 다시 보인다. atMs는 ChildStateTracker가 정한 그 시각이다 */
    fun eyesUnseenChanged(eyesUnseen: Boolean, atMs: Long) {
        if (!started) return
        val open = unseen.lastOrNull()?.takeIf { it.endMs == null }
        if (eyesUnseen && open == null) unseen += Period(atMs)
        if (!eyesUnseen && open != null) open.endMs = max(atMs, open.startMs)
    }

    /** 앱이 실내를 바꿨다. 잠든 동안이면 그 값은 그 잠이 끝날 때까지 "자는 동안"에 넣지 않는다 */
    fun appChanged(atMs: Long, temp: Boolean, fan: Boolean, light: Boolean = false) {
        if (!started) return
        if (temp) tempChanges += atMs
        if (fan) fanChanges += atMs
        if (light) lightChanges += atMs
    }

    /** 달리는 동안 SAMPLE_MS마다 부른다. awakeSeen은 그때 깨어 있는 아이를 봤는가 */
    fun sample(atMs: Long, awakeSeen: Boolean, conditions: Conditions) {
        if (!started) return
        samples += Sample(atMs, awakeSeen, conditions)
    }

    /** 지금까지를 endMs에 끝난 길로 셈한다. 상태는 바꾸지 않는다(잠깐 세웠다 이어 가면 같은 기록을 다시 셈한다) */
    fun summary(endMs: Long): SleepSummary {
        val start = startMs ?: endMs
        // 추적기가 정한 잠. 앱이 바꾼 값을 빼는 범위는 이것으로 본다
        val sleepSpans = sleeps.mapNotNull { s ->
            val from = max(s.startMs, start)
            val to = min(s.endMs ?: endMs, endMs)
            if (to > from) LongArray(2).also { it[0] = from; it[1] = to } else null
        }
        // 잠마다 눈을 본 구간. 잔 시간과 화면의 잔 구간은 이것으로 센다
        val seenBySleep = sleepSpans.map { seenParts(it, endMs) }
        val seen = seenBySleep.flatten()
        val asleep = PhaseTally()
        val awake = PhaseTally()
        samples.forEach { s ->
            val span = sleepSpans.firstOrNull { s.atMs >= it[0] && s.atMs < it[1] }
            fun untouched(changes: List<Long>) = span == null || changes.none { it >= span[0] && it <= s.atMs }
            when {
                span != null -> if (seen.any { s.atMs >= it[0] && s.atMs < it[1] }) {
                    asleep.add(s.c, countTemp = untouched(tempChanges), countFan = untouched(fanChanges), countLight = untouched(lightChanges))
                }
                s.awakeSeen -> awake.add(s.c, countTemp = true, countFan = true, countLight = true)
            }
        }
        // 잠들 때의 값: 잠든 때에 가장 가까운 표본(앞뒤 SAMPLE_MS 안). 차가 서 있을 때 잠들었으면 표본이 없어 모른다
        fun onsetAt(ms: Long): SleepSummary.Onset {
            val near = samples.filter { abs(it.atMs - ms) <= SAMPLE_MS }.minByOrNull { abs(it.atMs - ms) }?.c
            return if (near == null) SleepSummary.Onset() else SleepSummary.Onset(near.kph, near.setC, near.fan, near.cabinC, near.lightOn, near.ambient)
        }
        // 잠든 때를 본 구간에만 값이 있다. 다시 보이기 시작한 구간은 새로 잠든 것이 아니다. 구간과 개수를 맞추려고 빈 값을 둔다
        val onsets = sleepSpans.zip(seenBySleep).flatMap { (span, parts) ->
            parts.map { part -> if (part[0] == span[0]) onsetAt(span[0]) else SleepSummary.Onset() }
        }
        val lastOpen = sleeps.lastOrNull()?.let { it.endMs == null || it.endMs!! >= endMs } == true
        return SleepSummary(
            sleptSec = (seen.sumOf { it[1] - it[0] } / 1000).toInt(),
            firstSleepSec = seen.firstOrNull()?.let { ((it[0] - start) / 1000).toInt() },
            sleeps = seenBySleep.count { it.isNotEmpty() },
            arrivedAsleep = lastOpen && seen.lastOrNull()?.let { it[1] >= endMs } == true,
            asleep = asleep.phase(),
            awake = awake.phase(),
            departMinute = departMinute,
            spans = seen.map { SleepSummary.Span(((it[0] - start) / 1000).toInt(), ((it[1] - start) / 1000).toInt()) },
            onsets = onsets,
            rideSec = ((endMs - start) / 1000).toInt(),
        )
    }

    /** 잠 하나에서 눈을 못 본 때를 뺀 구간들 */
    private fun seenParts(span: LongArray, endMs: Long): List<LongArray> {
        val parts = mutableListOf<LongArray>()
        var from = span[0]
        unseen.forEach { u ->
            val gapFrom = max(u.startMs, from)
            val gapTo = min(u.endMs ?: endMs, span[1])
            if (gapFrom < gapTo) {
                if (gapFrom > from) parts += longArrayOf(from, gapFrom)
                from = gapTo
            }
        }
        if (span[1] > from) parts += longArrayOf(from, span[1])
        return parts
    }

    /** 한 상태(자는 동안 또는 깨어 있는 동안)의 값을 더해 둔다. 실내등·독서등은 켜져 있으면 100, 꺼져 있으면 0으로 더해 켜진 몫(%)을 낸다 */
    private class PhaseTally {
        private var count = 0
        private val sums = DoubleArray(6)
        private val counts = IntArray(6)

        fun add(c: Conditions, countTemp: Boolean, countFan: Boolean, countLight: Boolean) {
            count++
            put(0, c.kph)
            if (countTemp) put(1, c.setC)
            if (countFan) put(2, c.fan)
            put(3, c.cabinC)
            if (countLight) {
                put(4, c.lightOn?.let { if (it) 100f else 0f })
                put(5, c.ambient)
            }
        }

        private fun put(i: Int, v: Float?) {
            if (v == null) return
            sums[i] += v
            counts[i]++
        }

        private fun value(i: Int) = if (counts[i] == 0) null else SleepSummary.Value((sums[i] / counts[i]).toFloat(), counts[i] * SAMPLE_SEC)

        fun phase() = SleepSummary.Phase(count * SAMPLE_SEC, value(0), value(1), value(2), value(3), value(4), value(5))
    }

    companion object {
        const val SAMPLE_MS = 5_000L
        private const val SAMPLE_SEC = (SAMPLE_MS / 1000).toInt()

        /**
         * 깨어 있는 아이를 봤는가("깨어 있는 동안"에 넣을지).
         * 얼굴이 안 보이거나, 확인 중이거나, 울음소리만 들리거나, 고개가 너무 돌아가 눈을 못 읽으면 모르는 것이다.
         */
        fun awakeSeen(s: ChildStateTracker.Status): Boolean = when (s.state) {
            ChildStateTracker.State.AWAKE, ChildStateTracker.State.CRYING -> true
            ChildStateTracker.State.LOOKING_SIDE -> !s.eyesUnknown
            else -> false
        }

        /** 출발한 시각을 자정부터 분으로 센다. 초는 버린다(화면에 "오전 9시 40분"처럼 보인다) */
        fun minuteOfDay(t: LocalTime): Int = t.hour * 60 + t.minute
    }
}

/**
 * 주행 하나의 잠 기록. 시간은 초다. 다닌 곳 기록(Trip)에 함께 저장한다(TripDiary 11번째 칸).
 * asleep·awake의 값은 평균과 그 평균을 낸 시간이다. 여러 주행을 합칠 때 시간으로 가중 평균을 내려고 시간을 함께 둔다.
 * departMinute·rideSec·spans·stops·onsets와 조명 값은 나중에 더한 칸이라 예전 기록에는 없을 수 있다.
 */
data class SleepSummary(
    val sleptSec: Int,
    val firstSleepSec: Int?, // 출발부터 처음 잠들 때까지. 자지 않았으면 null
    val sleeps: Int, // 잠든 횟수
    val arrivedAsleep: Boolean, // 차를 세울 때도 자고 있었는가
    val asleep: Phase,
    val awake: Phase,
    val departMinute: Int? = null, // 출발한 시각(자정부터 분)
    val spans: List<Span> = emptyList(), // 잠든 구간
    val onsets: List<Onset> = emptyList(), // 구간마다 잠들 때의 값. spans와 개수가 같다
    val rideSec: Int? = null, // 탄 시간(초). 다닌 곳 기록의 분은 1분 아래를 버려 짧은 주행의 시간 막대가 어긋나서 둔다
    val stops: List<Stop> = emptyList(), // 같은 날 세워 둔 때. 구간과 탄 시간은 달린 시간만 센다(merge)
) {
    data class Value(val avg: Float, val sec: Int)

    /** light는 실내등·독서등이 켜져 있던 몫(%), ambient는 무드등 밝기 평균(%) */
    data class Phase(
        val sec: Int,
        val kph: Value? = null,
        val setC: Value? = null,
        val fan: Value? = null,
        val cabinC: Value? = null,
        val light: Value? = null,
        val ambient: Value? = null,
    )

    /** 잠든 구간. 출발부터 달린 시간(초). 세워 둔 시간은 들어가지 않는다 */
    data class Span(val fromSec: Int, val toSec: Int)

    /** 달린 시간 atSec이 지난 자리에서 차를 minutes분 세워 두었다 */
    data class Stop(val atSec: Int, val minutes: Int)

    /** 잠들 때의 값. 모르면 null */
    data class Onset(
        val kph: Float? = null,
        val setC: Float? = null,
        val fan: Float? = null,
        val cabinC: Float? = null,
        val lightOn: Boolean? = null,
        val ambient: Float? = null,
    ) {
        val known: Boolean get() = listOf(kph, setC, fan, cabinC, ambient).any { it != null } || lightOn != null
    }

    companion object {
        /**
         * 한 칸에 넣는 글. 예: s=2280;f=720;n=1;a=0;zs=2250;zk=72.3/2250;...;d=550;p=1800-4800;o=88.0/22.5/3.0/23.0/0/30.0
         * z는 자는 동안, w는 깨어 있는 동안(k 속도, t 설정 온도, f 바람, c 실내 온도, l 불 켜진 몫, a 무드등).
         * d 출발 시각(분), r 탄 시간(초), p 잠든 구간(출발부터 초, 시작-끝), b 세워 둔 때(출발부터 초:분), o 잠들 때 값(속도/설정 온도/바람/실내 온도/불 켜짐 1·0/무드등, 모르면 빈칸)
         */
        fun encode(s: SleepSummary): String = buildList {
            add("s=${s.sleptSec}")
            s.firstSleepSec?.let { add("f=$it") }
            add("n=${s.sleeps}")
            add("a=${if (s.arrivedAsleep) 1 else 0}")
            listOf("z" to s.asleep, "w" to s.awake).forEach { (p, phase) ->
                add("${p}s=${phase.sec}")
                listOf("k" to phase.kph, "t" to phase.setC, "f" to phase.fan, "c" to phase.cabinC, "l" to phase.light, "a" to phase.ambient).forEach { (k, v) ->
                    if (v != null) add("$p$k=${num(v.avg)}/${v.sec}")
                }
            }
            s.departMinute?.let { add("d=$it") }
            s.rideSec?.let { add("r=$it") }
            if (s.spans.isNotEmpty()) add("p=" + s.spans.joinToString(",") { "${it.fromSec}-${it.toSec}" })
            if (s.stops.isNotEmpty()) add("b=" + s.stops.joinToString(",") { "${it.atSec}:${it.minutes}" })
            if (s.onsets.isNotEmpty()) {
                add("o=" + s.onsets.joinToString(",") { o ->
                    listOf(num(o.kph), num(o.setC), num(o.fan), num(o.cabinC), o.lightOn?.let { if (it) "1" else "0" }.orEmpty(), num(o.ambient)).joinToString("/")
                })
            }
        }.joinToString(";")

        private fun num(v: Float?) = v?.let { String.format(Locale.ROOT, "%.1f", it) }.orEmpty()

        /** 망가졌거나 비었으면 null. 모르는 칸은 건너뛴다(나중에 칸을 더해도 예전 앱이 읽게) */
        fun decode(text: String): SleepSummary? {
            if (text.isBlank()) return null
            val map = text.split(';').mapNotNull { part ->
                val at = part.indexOf('=')
                if (at <= 0) null else part.substring(0, at).trim() to part.substring(at + 1).trim()
            }.toMap()
            val slept = map["s"]?.toIntOrNull() ?: return null
            fun value(key: String): Value? {
                val raw = map[key] ?: return null
                val slash = raw.indexOf('/')
                if (slash <= 0) return null
                val avg = raw.substring(0, slash).toFloatOrNull() ?: return null
                val sec = raw.substring(slash + 1).toIntOrNull() ?: return null
                return Value(avg, sec)
            }
            fun phase(p: String) = Phase(
                map["${p}s"]?.toIntOrNull() ?: 0,
                value("${p}k"), value("${p}t"), value("${p}f"), value("${p}c"), value("${p}l"), value("${p}a"),
            )
            val spans = map["p"]?.split(',')?.mapNotNull { part ->
                val dash = part.indexOf('-')
                val from = if (dash > 0) part.substring(0, dash).toIntOrNull() else null
                val to = if (dash > 0) part.substring(dash + 1).toIntOrNull() else null
                if (from != null && to != null && to >= from) Span(from, to) else null
            }.orEmpty()
            val onsets = map["o"]?.split(',')?.map { part ->
                val f = part.split('/')
                Onset(
                    kph = f.getOrNull(0)?.toFloatOrNull(),
                    setC = f.getOrNull(1)?.toFloatOrNull(),
                    fan = f.getOrNull(2)?.toFloatOrNull(),
                    cabinC = f.getOrNull(3)?.toFloatOrNull(),
                    lightOn = when (f.getOrNull(4)) {
                        "1" -> true
                        "0" -> false
                        else -> null
                    },
                    ambient = f.getOrNull(5)?.toFloatOrNull(),
                )
            }.orEmpty()
            val stops = map["b"]?.split(',')?.mapNotNull { part ->
                val f = part.split(':')
                val at = f.getOrNull(0)?.toIntOrNull()
                val minutes = f.getOrNull(1)?.toIntOrNull()
                if (at != null && minutes != null) Stop(at, minutes) else null
            }.orEmpty()
            return SleepSummary(
                sleptSec = slept,
                firstSleepSec = map["f"]?.toIntOrNull(),
                sleeps = map["n"]?.toIntOrNull() ?: 0,
                arrivedAsleep = map["a"] == "1",
                asleep = phase("z"),
                awake = phase("w"),
                departMinute = map["d"]?.toIntOrNull(),
                spans = spans,
                onsets = onsets,
                rideSec = map["r"]?.toIntOrNull(),
                stops = stops,
            )
        }

        /**
         * 같은 날 다시 탄 주행 b를 그날 기록 a 뒤에 잇는다.
         * 시간은 달린 시간만 이어 붙이고, 세워 둔 시간은 stops에 따로 둔다. 화면의 시각은 SleepStats가 stops를 더해 낸다.
         * a를 세울 때도 자고 있었고 b를 출발할 때도 자고 있으면 새로 잠든 것이 아니다. 횟수는 늘리지 않고 잠들 때의 값은 빈 값으로 둔다.
         */
        fun merge(a: SleepSummary, aRideSec: Int, b: SleepSummary, bRideSec: Int): SleepSummary {
            val gap = if (a.departMinute != null && b.departMinute != null) {
                max(0, b.departMinute - (a.departMinute + a.stops.sumOf { it.minutes } + aRideSec / 60))
            } else {
                0
            }
            val stillAsleep = a.arrivedAsleep && b.spans.firstOrNull()?.fromSec == 0
            val nextOnsets = if (stillAsleep && b.onsets.isNotEmpty()) listOf(Onset()) + b.onsets.drop(1) else b.onsets
            return SleepSummary(
                sleptSec = a.sleptSec + b.sleptSec,
                firstSleepSec = a.firstSleepSec ?: b.firstSleepSec?.let { it + aRideSec },
                sleeps = max(0, a.sleeps + b.sleeps - if (stillAsleep) 1 else 0),
                arrivedAsleep = b.arrivedAsleep,
                asleep = merge(a.asleep, b.asleep),
                awake = merge(a.awake, b.awake),
                departMinute = a.departMinute,
                spans = a.spans + b.spans.map { Span(it.fromSec + aRideSec, it.toSec + aRideSec) },
                onsets = a.onsets + nextOnsets,
                rideSec = aRideSec + bRideSec,
                stops = if (gap > 0) a.stops + Stop(aRideSec, gap) else a.stops,
            )
        }

        private fun merge(a: Phase, b: Phase) = Phase(
            a.sec + b.sec,
            merge(a.kph, b.kph),
            merge(a.setC, b.setC),
            merge(a.fan, b.fan),
            merge(a.cabinC, b.cabinC),
            merge(a.light, b.light),
            merge(a.ambient, b.ambient),
        )

        private fun merge(a: Value?, b: Value?): Value? {
            if (a == null || b == null) return a ?: b
            val sec = a.sec + b.sec
            return if (sec == 0) a else Value((a.avg * a.sec + b.avg * b.sec) / sec, sec)
        }
    }
}
