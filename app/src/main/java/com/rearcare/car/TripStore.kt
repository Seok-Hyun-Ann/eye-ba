package com.rearcare.car

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * 다닌 곳 저장소. 앱 전용 폴더의 파일과 설정에만 둔다. 인터넷으로 보내지 않고 백업에서도 뺀다(res/xml의 백업 규칙).
 * 시연용 예시 기록은 시연 폴더의 파일에서 따로 읽어 "예시"로 표시한다. 예시를 사용자 기록에 섞어 저장하지 않는다.
 */
object TripStore {
    private const val TAG = "RearCareTrips"
    private const val DIR = "diary" // 백업에서 뺄 폴더. 쓰는 동안 생기는 임시 파일까지 함께 빠진다
    private const val FILE = "trips.tsv"
    private const val PREFS = "trip_diary"
    private const val KEY_BIRTH = "birth"
    private const val KEY_ASK = "ask"
    private const val KEY_YES_DAY = "yes_day"

    /** 이번에 앱을 켠 뒤 이미 물었는가. 파일에 두지 않는다. 앱이 꺼졌다 켜지면(시동을 다시 걸면) 다시 묻기 위해서다 */
    var askedThisRun = false

    private fun file(context: Context) = AtomicFile(File(File(context.filesDir, DIR).apply { mkdirs() }, FILE))

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 부모가 남긴 기록 */
    fun own(context: Context): List<Trip> = try {
        TripDiary.decode(String(file(context).readFully(), Charsets.UTF_8))
    } catch (e: IOException) {
        emptyList() // 아직 파일이 없다
    }

    private fun samples(context: Context): List<Trip> {
        val f = File(context.filesDir, DemoSetup.SAMPLE_TRIPS)
        if (!f.exists()) return emptyList()
        return TripDiary.decode(f.readText()).map { it.copy(sample = true) }
    }

    fun trips(context: Context): List<Trip> = samples(context) + own(context)

    /** 쓰고 다시 읽어 확인한다. 쓴 글이 그대로 다시 읽힐 때만 true(잠 칸의 소수 때문에 메모리 값과는 비교하지 않는다, TripDiary.sameAsWritten) */
    private fun write(context: Context, list: List<Trip>): Boolean {
        val target = file(context)
        val out = try {
            target.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "저장 시작 실패", e)
            return false
        }
        return try {
            val text = TripDiary.encode(list)
            out.write(text.toByteArray(Charsets.UTF_8))
            target.finishWrite(out)
            TripDiary.sameAsWritten(text, own(context))
        } catch (e: IOException) {
            target.failWrite(out)
            Log.e(TAG, "저장 실패", e)
            false
        }
    }

    /** 기록을 남긴다. 같은 번호가 있으면 부모가 쓴 한 줄은 살리고 나머지를 고친다 */
    fun save(context: Context, trip: Trip): Boolean {
        val list = own(context)
        val keptNote = list.firstOrNull { it.id == trip.id }?.note
        return write(context, TripDiary.upsert(list, if (keptNote != null) trip.copy(note = keptNote) else trip))
    }

    /** 기록을 지운다. 그 기록에 넣은 사진도 함께 지운다 */
    fun delete(context: Context, id: String): Boolean =
        write(context, own(context).filterNot { it.id == id }).also { if (it) TripPhotos.delete(context, id) }

    fun birth(context: Context): LocalDate? =
        prefs(context).getString(KEY_BIRTH, null)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    fun setBirth(context: Context, date: LocalDate?) {
        prefs(context).edit().apply {
            if (date == null) remove(KEY_BIRTH) else putString(KEY_BIRTH, date.toString())
        }.apply()
    }

    /** 그날 기록하겠다고 답했는가. 앱이 꺼져도 남는다. 같은 날 다시 타면 묻지 않고 이어서 기록한다 */
    fun saidYesOn(context: Context, day: LocalDate): Boolean =
        prefs(context).getString(KEY_YES_DAY, null) == day.toString()

    fun setSaidYes(context: Context, day: LocalDate?) {
        prefs(context).edit().apply {
            if (day == null) remove(KEY_YES_DAY) else putString(KEY_YES_DAY, day.toString())
        }.apply()
    }

    /** 차를 탈 때 물을 것인가. 정하지 않았으면 묻는다. 묻기만 하고 "네" 전에는 남기지 않기 때문이다 */
    fun ask(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ASK, true)

    fun setAsk(context: Context, ask: Boolean) {
        prefs(context).edit().putBoolean(KEY_ASK, ask).apply()
    }
}
