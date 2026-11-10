package com.rearcare.car

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import java.time.LocalTime

/**
 * 아이와 다닌 곳 주행 하나와 그 주행에서 잔 잠을 기록한다.
 *
 * - 앱을 켤 때 묻는 화면 질문("오늘 아이와의 운행을 기록할까요?")과 속도 권한 요청은 RearStatusActivity가 맡는다. 이 클래스는 대답을 받은 뒤의 일을 한다.
 * - 출발(onDrive): 기록하는 길이면 내비 목적지를 읽어 두고, 새 길이면 잠 기록을 시작하고, 달리는 동안 5초마다 조건을 넣는다.
 * - 정차(onPark): 주행을 셈하고, 내비 값이 오기를 1초 기다렸다가 저장한다. 저장을 확인한 뒤 결과 문장을 onSaved로 알린다.
 * - 내비·공조·속도(차 SDK)와 Android를 쓰므로 단위 시험 대상이 아니다. 판단 규칙은 TripRecorder, SleepLog, TripDiary에 있고 거기서 시험한다.
 */
class TripSession(
    private val context: Context,
    private val navi: NaviLink,
    private val cabin: CabinController,
    private val speed: CarSpeed,
    private val handler: Handler,
    private val tracker: () -> ChildStateTracker, // 지금 상태 추적기. 시연을 처음부터 다시 시작하면 새것으로 바뀐다
    private val moving: () -> Boolean, // 기어가 P가 아닌가(RearStatusActivity가 기어를 받아 정한다)
    private val onSaved: (String) -> Unit, // 저장 결과 문장. RearStatusActivity가 안내 글에 쓴다
) {
    val recorder = TripRecorder()
    val sleepLog = SleepLog()
    private var sleepTripStart: Long? = null // sleepLog가 셈하는 주행. TripRecorder.startedAtMs와 다르면 새 주행이다
    private var lastAsleep = false
    private var lastEyesUnseen = false

    private val sampler = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, SleepLog.SAMPLE_MS)
        }
    }

    /** 시연을 처음부터 다시 시작할 때. 앞 주행의 기록과 예약해 둔 표본을 지운다 */
    fun reset() {
        recorder.reset()
        sleepLog.reset()
        sleepTripStart = null
        lastAsleep = false
        lastEyesUnseen = false
        handler.removeCallbacks(sampler)
    }

    /** 분석이 끝날 때마다 부른다. 잠든 때와 깬 때, 얼굴을 못 보게 된 때와 다시 본 때를 추적기가 정한 시각으로 남긴다 */
    fun childAsleepChanged() {
        val t = tracker()
        if (t.asleep != lastAsleep) {
            lastAsleep = t.asleep
            sleepLog.asleepChanged(lastAsleep, t.asleepChangedAtMs)
        }
        if (t.eyesUnseen != lastEyesUnseen) {
            lastEyesUnseen = t.eyesUnseen
            sleepLog.eyesUnseenChanged(lastEyesUnseen, t.eyesUnseenChangedAtMs)
        }
    }

    fun onDrive() {
        recorder.onDrive(System.currentTimeMillis())
        if (recorder.recording) {
            // 도착하면 안내가 끝나 목적지 값이 비므로 출발할 때도 읽어 둔다
            navi.refresh()
            // 잠 기록: 출발할 때마다 새로 센다. 같은 날의 주행은 저장할 때 합친다(TripDiary.mergeSameDay)
            val started = recorder.startedAtMs
            if (started != null && started != sleepTripStart) {
                sleepTripStart = started
                val asleep = tracker().asleep
                val eyesUnseen = tracker().eyesUnseen
                sleepLog.start(SystemClock.elapsedRealtime(), asleep, departMinute = SleepLog.minuteOfDay(LocalTime.now()), eyesUnseenNow = eyesUnseen)
                lastAsleep = asleep
                lastEyesUnseen = eyesUnseen
            }
            handler.removeCallbacks(sampler)
            handler.post(sampler)
        }
    }

    fun onPark() {
        handler.removeCallbacks(sampler)
        val save = recorder.onPark(System.currentTimeMillis()) ?: return
        val sleep = if (sleepLog.started) sleepLog.summary(SystemClock.elapsedRealtime()) else null
        navi.refresh()
        // 내비 값은 조금 뒤에 온다
        handler.postDelayed({ save(save, sleep) }, NAVI_REPLY_MS)
    }

    /**
     * 달리는 동안 몇 초마다 속도, 설정 온도, 바람, 실내 온도, 실내등·독서등, 무드등 밝기를 잠 기록에 넣는다.
     * 공조가 꺼져 있을 때의 처리는 CabinController.Climate.toConditions, 깨어 있는 때의 판정은 SleepLog.awakeSeen에 있다.
     */
    private fun sample() {
        if (!sleepLog.started || !moving()) return
        val at = SystemClock.elapsedRealtime()
        val awake = SleepLog.awakeSeen(tracker().status)
        val kph = speed.kph
        cabin.readClimate { c -> sleepLog.sample(at, awake, c.toConditions(kph)) }
    }

    /** 내비 목적지를 이름으로 남긴다. 안내를 켜지 않은 주행은 지금 주소를 쓴다. 저장을 확인한 뒤에 말한다 */
    private fun save(save: TripRecorder.Save, sleep: SleepSummary?) {
        val destination = navi.destination
        val drive = TripDiary.record(save, destination, navi.here, sleep)
        // 같은 날의 주행은 한 기록이다. 그날 기록이 있으면 거기에 더한다
        val today = TripStore.own(context).lastOrNull { it.date == drive.date }
        val record = today?.let { TripDiary.mergeSameDay(it, drive) } ?: drive
        val saved = TripStore.save(context, record)
        Log.i(TAG, "다닌 곳 저장 ${if (saved) "성공" else "실패"}: ${record.id} (목적지 ${if (destination != null) "있음" else "없음"}, 잠 ${sleep?.let(SleepSummary::encode) ?: "없음"})")
        val text = if (saved) {
            listOfNotNull(
                if (today == null) "${TripDiary.withObject(record.name)} 기록했어요." else "오늘 주행 기록에 더했어요.",
                SleepStats.savedLine(record.sleep),
            ).joinToString(" ")
        } else {
            "기록하지 못했어요."
        }
        onSaved(text)
    }

    private companion object {
        const val TAG = "RearCareStatus" // RearStatusActivity와 같은 태그로 남긴다
        const val NAVI_REPLY_MS = 1_000L // 내비에 값을 요청하고 답을 기다리는 시간
    }
}
