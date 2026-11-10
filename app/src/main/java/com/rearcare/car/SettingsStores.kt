package com.rearcare.car

import android.content.Context

/*
 * 부모가 설정 화면에서 고르는 값의 저장소 3개. RearStatusActivity도 읽기 때문에 설정 화면 파일에서 떼어 두었다.
 * 저장 파일 이름과 키 글자는 바꾸지 않는다. 바꾸면 이미 저장된 설정을 못 읽는다.
 */

/** 잠들었을 때 실내등·독서등·무드등을 줄일지 물을 것인가. 정하지 않았으면 묻는다. 묻기만 하고 대답 전에는 바꾸지 않기 때문이다 */
object SleepLightStore {
    private const val FILE = "sleep_light"
    private const val KEY = "ask"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun ask(context: Context): Boolean =
        prefs(context).getBoolean(KEY, true)

    fun set(context: Context, ask: Boolean) {
        prefs(context).edit().putBoolean(KEY, ask).apply()
    }
}

/** 주행 중 사진 간격 저장소. 0이면 끔. 정하지 않았으면 고를 수 있는 값 중 가장 드문 5분 */
object DrivePhotoStore {
    private const val FILE = "drive_photo"
    private const val KEY = "minutes"
    val CHOICES = listOf(1, 3, 5)
    const val DEFAULT_MINUTES = 5

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun minutes(context: Context): Int =
        prefs(context).getInt(KEY, DEFAULT_MINUTES)

    fun set(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY, minutes).apply()
    }

    fun describe(minutes: Int) = if (minutes == 0) "꺼짐" else "${minutes}분마다"
}

/** 잠잘 때 온도 저장소. 앱 안 설정 파일(SharedPreferences)에 둔다. 정하기 전에는 비어 있다 */
object SleepRangeStore {
    private const val FILE = "sleep_range"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(context: Context): ComfortAdvisor.SleepRange? {
        val p = prefs(context)
        if (!p.contains("min")) return null
        return ComfortAdvisor.SleepRange(p.getFloat("min", 0f), p.getFloat("max", 0f))
    }

    fun set(context: Context, range: ComfortAdvisor.SleepRange) {
        prefs(context).edit().putFloat("min", range.minC).putFloat("max", range.maxC).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** 화면에 쓰는 글자: "24도", 정하지 않았으면 "정하지 않음" */
    fun describe(targetC: Float?) = targetC?.let { ComfortAdvisor.deg(it) } ?: StatusWords.NOT_SET
}
