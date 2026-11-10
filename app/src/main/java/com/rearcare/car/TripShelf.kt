package com.rearcare.car

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/** 카드 그림의 계절. 기록 날짜의 달로 정한다 */
enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

/**
 * 다닌 곳 선반의 규칙. 순수 Kotlin이라 단위 시험으로 본다.
 * 나이별로 한 줄씩 놓고 기록 하나를 카드 한 장으로 보여 준다. 카드에는 장소와 날짜를 쓰고, 사용자가 넣은 사진이 없으면 계절 그림을 깐다.
 */
object TripShelf {

    const val MAX_PHOTOS = 10 // 정함: 카드 하나에 넣는 사진 수. 저장한 사진이 한 장에 약 0.5MB라 카드 하나가 약 5MB를 넘지 않는다

    fun season(date: LocalDate): Season = when (date.monthValue) {
        in 3..5 -> Season.SPRING
        in 6..8 -> Season.SUMMER
        in 9..11 -> Season.AUTUMN
        else -> Season.WINTER
    }

    /** 같은 계절 그림이 이어져도 똑같아 보이지 않게 날짜에 따라 좌우를 뒤집는다 */
    fun mirrored(date: LocalDate): Boolean = date.dayOfYear % 2 == 1

    fun title(age: Int?): String = when (age) {
        null -> "다닌 곳"
        TripDiary.BEFORE_BIRTH, 0 -> TripDiary.ageLabel(age)
        else -> "${TripDiary.ageLabel(age)} 때"
    }

    fun meta(group: TripDiary.Group, birth: LocalDate?): String {
        val places = "${TripDiary.placeCount(group.trips)}곳"
        val age = group.age
        return when {
            age == null -> "$places · 설정에서 생일을 넣으면 나이별로 모여요"
            age >= 0 && birth != null -> "$places · ${TripDiary.ageRange(birth, age)}"
            else -> places
        }
    }

    /** 위치도 장소 번호도 없는 기록("어디인지 읽지 못한 곳")은 내비에 찾을 곳이 없다 */
    fun canNavigate(trip: Trip): Boolean = trip.latitude != null || trip.poiId.isNotBlank()

    private fun photoBase(id: String): String =
        id.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.ifEmpty { "trip" }

    /** 사진 파일 이름. 기록 번호로 만들되 글자와 숫자만 남겨 사진 폴더를 벗어나지 못하게 한다. 0번이 아니면 뒤에 번호를 붙인다 */
    fun photoFileName(id: String, number: Int = 0): String =
        photoBase(id) + (if (number == 0) "" else "_$number") + ".jpg"

    /** 이 기록의 사진 파일이면 그 번호, 아니면 null */
    fun photoNumber(id: String, fileName: String): Int? {
        val base = photoBase(id)
        if (fileName == "$base.jpg") return 0
        if (!fileName.startsWith("${base}_") || !fileName.endsWith(".jpg")) return null
        return fileName.substring(base.length + 1, fileName.length - ".jpg".length).toIntOrNull()?.takeIf { it > 0 }
    }

    /** 사진을 넣은 결과 문장. 실제로 넣은 장수로 말한다 */
    fun photoResultLine(saved: Int, picked: Int): String = when {
        saved == 0 -> "사진을 넣지 못했어요."
        saved < picked -> "${picked}장 중 ${saved}장만 넣었어요."
        picked == 1 -> "사진을 넣었어요."
        else -> "사진 ${saved}장을 넣었어요."
    }

    /** 저장할 크기. 긴 쪽이 maxSide를 넘으면 비율을 지켜 줄이고, 작은 사진은 키우지 않는다 */
    fun scaledSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val longer = max(width, height)
        if (longer <= maxSide) return width to height
        val scale = maxSide.toFloat() / longer
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /** 사진을 읽을 때 건너뛸 배수(2의 거듭제곱). 읽은 그림이 원하는 크기보다 작아지지 않는 가장 큰 값이다 */
    fun sampleSize(width: Int, height: Int, wantWidth: Int, wantHeight: Int): Int {
        var size = 1
        while (width / (size * 2) >= wantWidth && height / (size * 2) >= wantHeight) size *= 2
        return size
    }
}
