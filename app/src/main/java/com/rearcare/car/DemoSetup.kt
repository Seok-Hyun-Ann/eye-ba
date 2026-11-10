package com.rearcare.car

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * 시연(에뮬레이터)에서만 쓰는 값을 한곳에 모았다.
 * 차의 뒷좌석 카메라와 연결할 때 바꿀 곳이 이 파일이다. 판단 규칙과 화면은 그대로 쓴다.
 *
 * - 뒷좌석 입력(feed): 시연은 녹화해 둔 대본(DemoFeedSource). 실제 차에서는 카메라·마이크 입력으로
 *   RearFeedSource를 구현해 돌려준다(아직 없다).
 * - 잠듦 판단(trackerConfig): 시연은 10초. 실제 차에서는 ChildStateTracker.Config() 기본값(60초).
 * - 창문 쪽(windowSide): 시연 사진에 맞춘 값이다. 실제 차에서는 카메라 위치에 따라 다르다.
 * - 시연 자료(assets/demo): 처음 켤 때 앱 폴더로 옮긴다(installDemoFiles). 사진과 소리의 출처는 assets/demo/SOURCES.csv와 THIRD_PARTY_NOTICES.md에 있다.
 * - 예시 기록(SAMPLE_TRIPS): 다닌 곳 화면에 "예시"로 보인다. 파일이 없으면 보이지 않는다.
 * - 시연 처음부터: 설정 화면 맨 아래에 있고, 겹쳐 그리기를 못 쓸 때의 앱 화면에도 있다(StatusScreen). 실제 차에서는 뺀다.
 */
object DemoSetup {

    private const val TAG = "RearCareDemo"
    private const val DEMO_DIR = "demo"
    const val SCRIPT = "scenario.csv" // 시연 대본 파일 이름(DemoFeedSource도 읽는다)

    /** 예시 기록을 3살, 4살, 5살로 나눠 보여 주려고 시연에서만 쓰는 생일이다. 다닌 곳 설정에서 바꿀 수 있다 */
    private val DEMO_BIRTH: LocalDate = LocalDate.of(2021, 3, 10)

    /**
     * 앱에 넣어 둔 시연 자료를 앱 폴더로 옮긴다. 이미 있으면 건드리지 않는다.
     * 대본(scenario.csv)을 맨 마지막에 복사한다. 대본이 있으면 복사가 끝났다는 뜻이다.
     * 생일을 정하지 않았을 때만 시연용 생일을 넣는다.
     */
    fun installDemoFiles(context: Context) {
        val target = File(context.filesDir, DEMO_DIR)
        if (File(target, SCRIPT).exists()) return
        try {
            target.mkdirs()
            context.assets.list(DEMO_DIR).orEmpty().sortedBy { it == SCRIPT }.forEach { name ->
                context.assets.open("$DEMO_DIR/$name").use { input ->
                    File(target, name).outputStream().use { input.copyTo(it) }
                }
            }
            if (TripStore.birth(context) == null) TripStore.setBirth(context, DEMO_BIRTH)
            Log.i(TAG, "시연 자료를 앱 폴더로 옮김")
        } catch (e: IOException) {
            Log.e(TAG, "시연 자료를 옮기지 못함", e)
        }
    }

    /** 상태 판단 규칙. 시연은 잠듦 판단만 10초로 짧게 한다 */
    val trackerConfig = ChildStateTracker.Config.DEMO

    /** 뒷좌석 입력. 앱 폴더의 demo에 옮겨 둔 시연 대본을 읽는다 */
    fun feed(context: Context): RearFeedSource = DemoFeedSource(File(context.filesDir, DEMO_DIR), context.filesDir)

    /** 시연 대본이 끝나면 안내 글 자리에 잠깐 띄우는 말 */
    const val SCRIPT_ENDED = "시연 대본 끝"

    /**
     * 창문이 사진의 어느 쪽에 보이는지. 시연 대본에 맞춘 값이다(시연 사진의 아이는 사진 왼쪽으로 고개를 돌린다).
     * 창문 쪽 설정 화면은 만들지 않기로 했다. 실제 차에서는 맞다고 보장할 수 없다.
     */
    val windowSide: ChildStateTracker.Side? = ChildStateTracker.Side.LEFT

    /** 시연용 예시 기록 파일(앱 폴더 기준). 다닌 곳 화면에 "예시"로 보이고, 부모의 기록에 섞어 저장하지 않는다 */
    const val SAMPLE_TRIPS = "$DEMO_DIR/sample_trips.tsv"
}
