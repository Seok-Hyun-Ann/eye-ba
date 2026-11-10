package com.rearcare.car

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 아이와 다닌 곳 기록 한 줄.
 * 장소 번호(poiId)가 있으면 내비에서 그 장소를 바로 연다. 없으면 이름으로 내비 검색을 연다.
 * sample은 시연용 예시 파일에서 읽은 기록이다. 화면에 "예시"라고 표시한다.
 */
data class Trip(
    val id: String,
    val date: LocalDate,
    val minutes: Int,
    val name: String,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val poiId: String = "",
    val poiProvider: String = "",
    val note: String = "",
    val sample: Boolean = false,
    val sleep: SleepSummary? = null, // 그 주행에서 잔 시간과 조건. 잠 기록 칸이 생기기 전에 남긴 기록에는 없다
)

/** 나이 계산, 나이별 묶기, 저장 형식. 순수 Kotlin이라 단위 시험으로 본다 */
object TripDiary {
    const val BEFORE_BIRTH = -1 // 생일보다 앞선 날짜의 기록을 묶는 자리

    /** 나이 묶음. age가 null이면 생일을 모르는 한 묶음이다 */
    data class Group(val age: Int?, val trips: List<Trip>)

    /** 생일 기준 만 나이 */
    fun age(birth: LocalDate, on: LocalDate): Int =
        if (on.isBefore(birth)) BEFORE_BIRTH else Period.between(birth, on).years

    fun groups(trips: List<Trip>, birth: LocalDate?): List<Group> {
        if (trips.isEmpty()) return emptyList()
        val sorted = trips.sortedWith(compareBy({ it.date }, { it.id }))
        if (birth == null) return listOf(Group(null, sorted))
        return sorted.groupBy { age(birth, it.date) }.toSortedMap().map { (age, list) -> Group(age, list) }
    }

    /** 몇 곳을 다녔나. 같은 곳을 두 번 가도 한 곳이다 */
    fun placeCount(trips: List<Trip>): Int = trips.map { it.name }.distinct().size

    fun ageLabel(age: Int): String = when (age) {
        BEFORE_BIRTH -> "생일 전"
        0 -> "돌 전"
        else -> "${age}살"
    }

    /** 그 나이였던 기간 */
    fun ageRange(birth: LocalDate, age: Int): String {
        val from = birth.plusYears(age.toLong())
        val to = birth.plusYears(age + 1L)
        return "${from.year}년 ${from.monthValue}월 ~ ${to.year}년 ${to.monthValue}월"
    }

    fun dateLabel(date: LocalDate) = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"

    /** 받침을 보고 을/를을 붙인다. 한글이 아니면 를을 붙인다 */
    fun withObject(name: String): String {
        val last = name.lastOrNull() ?: return name
        val hasFinal = last in '가'..'힣' && (last - '가') % 28 != 0
        return name + if (hasFinal) "을" else "를"
    }

    /** 기록 이름. 내비 목적지가 있으면 그것을, 없으면 지금 주소를 쓴다 */
    fun chooseName(destination: String?, address: String?): String? =
        destination?.takeIf { it.isNotBlank() } ?: address?.takeIf { it.isNotBlank() }

    /** 목적지 이름도 지금 주소도 읽지 못했을 때의 기록 이름 */
    const val UNKNOWN_PLACE = "어디인지 읽지 못한 곳"

    /**
     * 세운 주행 하나를 기록 한 줄로 만든다.
     * - 이름: 내비 목적지가 있으면 그것을, 없으면 지금 주소를 쓴다(chooseName). 둘 다 없으면 UNKNOWN_PLACE.
     * - 주소와 좌표: 목적지가 있으면 목적지의 것을, 없으면 지금 위치의 것을 쓴다.
     *   목적지 좌표가 없거나 0.0이면 지금 위치의 좌표를 쓴다. 도착 알림에는 좌표가 없는데,
     *   그때는 지금 위치가 곧 그 장소다. 0.0은 모르는 값으로 본다.
     * - 장소 번호(poiId)는 내비 목적지에만 있다.
     * - 번호와 날짜는 출발 시각에서 정한다. 날짜는 zone의 시각으로 본다.
     */
    fun record(save: TripRecorder.Save, destination: NaviLink.Place?, here: NaviLink.Place?, sleep: SleepSummary?, zone: ZoneId = ZoneId.systemDefault()): Trip {
        val place = destination ?: here
        return Trip(
            id = save.startMs.toString(),
            date = Instant.ofEpochMilli(save.startMs).atZone(zone).toLocalDate(),
            minutes = save.minutes,
            name = chooseName(destination?.name, here?.address) ?: UNKNOWN_PLACE,
            address = place?.address.orEmpty(),
            latitude = place?.latitude?.takeIf { it != 0.0 } ?: here?.latitude?.takeIf { it != 0.0 },
            longitude = place?.longitude?.takeIf { it != 0.0 } ?: here?.longitude?.takeIf { it != 0.0 },
            poiId = destination?.poiId.orEmpty(),
            poiProvider = destination?.poiProvider.orEmpty(),
            sleep = sleep,
        )
    }

    /**
     * 같은 날 다시 탄 주행을 그날 기록에 더한다. 하루가 한 기록이다.
     * 번호, 날짜, 장소, 메모는 그날 처음 것을 둔다. 사진이 번호에 묶여 있어서다. 처음 장소를 못 읽었으면 이번 장소로 채운다.
     * 탄 시간은 달린 시간만 더한다. 세워 둔 시간은 잠 기록의 stops에 남는다.
     */
    fun mergeSameDay(day: Trip, next: Trip): Trip {
        val base = if (day.name == UNKNOWN_PLACE) next.copy(id = day.id, date = day.date, note = day.note) else day
        val dayRide = day.sleep?.rideSec ?: (day.minutes * 60)
        val nextRide = next.sleep?.rideSec ?: (next.minutes * 60)
        val sleep = when {
            day.sleep == null -> next.sleep
            next.sleep == null -> day.sleep
            else -> SleepSummary.merge(day.sleep, dayRide, next.sleep, nextRide)
        }
        return base.copy(minutes = (dayRide + nextRide) / 60, sleep = sleep)
    }

    /** 같은 번호면 고치고 없으면 더한다 */
    fun upsert(list: List<Trip>, trip: Trip): List<Trip> =
        if (list.any { it.id == trip.id }) list.map { if (it.id == trip.id) trip else it } else list + trip

    // ---------- 저장 형식: 한 줄에 기록 하나, 칸은 탭으로 나눈다 ----------

    private const val FIELDS = 11 // 11번째 칸은 잠 기록이다. 10칸인 예전 줄도 그대로 읽는다
    private const val REQUIRED_FIELDS = 4

    fun encode(trips: List<Trip>): String = buildString {
        trips.forEach { t ->
            append(
                listOf(
                    t.id, t.date.toString(), t.minutes.toString(), t.name, t.address,
                    t.latitude?.toString().orEmpty(), t.longitude?.toString().orEmpty(),
                    t.poiId, t.poiProvider, t.note,
                    t.sleep?.let(SleepSummary::encode).orEmpty(),
                ).joinToString("\t", transform = ::escape).trimEnd('\t'),
            )
            append('\n')
        }
    }

    /** 망가진 줄은 건너뛴다. 한 줄 때문에 기록 전체를 잃지 않게 한다 */
    fun decode(text: String): List<Trip> = text.lineSequence().mapNotNull(::decodeLine).toList()

    /**
     * 다시 읽은 기록이 방금 쓴 글과 같은가(저장 확인용).
     * 잠 칸의 평균은 소수 한 자리로 저장되므로, 메모리의 기록과 그대로 비교하면 109.1234와 109.1처럼 늘 달라진다
     * (2026-11-06 1시간 40분 주행 시험에서 저장은 됐는데 "기록하지 못했어요."가 떴다). 그래서 다시 읽은 기록을 다시 글로 바꿔 비교한다.
     * 줄이 빠지거나 칸이 망가져 못 읽은 것이 있으면 글이 달라져 그대로 잡힌다.
     */
    fun sameAsWritten(written: String, readBack: List<Trip>): Boolean = encode(readBack) == written

    private fun decodeLine(line: String): Trip? {
        val parts = line.split('\t').map(::unescape)
        // 번호, 날짜, 분, 이름은 꼭 있어야 한다. 뒤쪽 빈 칸은 편집기가 지울 수 있어 없어도 된다(2026-11-05 예시 파일에서 겪음)
        if (parts.size !in REQUIRED_FIELDS..FIELDS) return null
        val f = parts + List(FIELDS - parts.size) { "" }
        if (f[0].isBlank() || f[3].isBlank()) return null
        val date = try {
            LocalDate.parse(f[1])
        } catch (e: DateTimeParseException) {
            return null
        }
        return Trip(
            id = f[0],
            date = date,
            minutes = f[2].toIntOrNull() ?: 0,
            name = f[3],
            address = f[4],
            latitude = f[5].toDoubleOrNull(),
            longitude = f[6].toDoubleOrNull(),
            poiId = f[7],
            poiProvider = f[8],
            note = f[9],
            sleep = SleepSummary.decode(f[10]), // 잠 칸이 망가졌으면 잠만 버리고 기록은 남긴다
        )
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                i++
                append(
                    when (s[i]) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        else -> s[i]
                    },
                )
            } else {
                append(c)
            }
            i++
        }
    }
}
