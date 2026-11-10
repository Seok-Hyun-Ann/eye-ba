package com.rearcare.car

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 부모가 정차 중에 정하는 설정: 잠잘 때 온도, 잠잘 때 조명, 주행 중 사진 간격. 기록 화면으로 가는 칸과 시연 처음부터도 여기 있다.
 * 그림 한 장(SceneView) 위에 유리 카드를 띄운다. 창이 좁으면 카드를 위아래로 쌓는다.
 *
 * 온도는 부모가 원하는 숫자 하나로 정한다. 정하기 전에는 앱이 온도를 묻지 않는다. 앱이 어떤 온도를 권하지는 않는다.
 * 고르면 바로 저장한다.
 *
 * 주행 중에는 설정을 쓰면 안 된다(플랫폼 NO_SETUP 규제). 그래서 주행으로 바뀌면 화면을 닫는다.
 */
class SleepSettingsActivity : AppCompatActivity() {

    private lateinit var tempValue: TextView
    private lateinit var driving: DrivingStateMonitor
    private lateinit var noneButton: Button
    private val lightButtons = mutableMapOf<Boolean, Button>()
    private val photoButtons = mutableMapOf<Int, Button>()

    // 지금 온도. 정하기 전에는 비어 있다
    private var target: Float? = null

    private fun dp(value: Int) = Ui.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = SleepRangeStore.get(this)?.targetC
        val wide = resources.configuration.screenWidthDp >= WIDE_WINDOW_DP

        setContentView(FrameLayout(this).apply {
            addView(SceneView(context, ROAD_FROM_BOTTOM_DP, SUN_X, SUN_Y_DP, CAR_SCALE), FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(ScrollView(context).apply {
                isFillViewport = true
                addView(if (wide) wideLayout() else narrowLayout())
            }, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        })
        show()

        driving = DrivingStateMonitor.closeWhenDriving(this)
    }

    /** 넓은 창: 차를 가운데 두고 양옆에 온도와 조명, 아래에 사진 간격 */
    private fun wideLayout() = page().apply {
        addView(topBar(), LinearLayout.LayoutParams(Ui.MATCH, dp(Ui.PILL_DP)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(tempCard(), LinearLayout.LayoutParams(dp(SIDE_CARD_DP), Ui.MATCH))
            addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(lightCard(), LinearLayout.LayoutParams(dp(SIDE_CARD_DP), Ui.MATCH))
        }, LinearLayout.LayoutParams(Ui.MATCH, dp(SIDE_CARD_HEIGHT_DP)).apply { topMargin = dp(Ui.GAP_DP) })
        addView(Space(context), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        addView(photoCard(wide = true), LinearLayout.LayoutParams(Ui.MATCH, dp(PHOTO_CARD_HEIGHT_DP)))
    }

    /** 좁은 창: 카드를 위아래로 쌓는다 */
    private fun narrowLayout() = page().apply {
        addView(topBar(), LinearLayout.LayoutParams(Ui.MATCH, dp(Ui.PILL_DP)))
        addView(tempCard(), Ui.below(context, Ui.GAP_DP))
        addView(lightCard(), Ui.below(context, Ui.GAP_DP))
        addView(photoCard(wide = false), Ui.below(context, Ui.GAP_DP))
    }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val side = dp(PAGE_SIDE_DP)
        setPadding(side, dp(PAGE_TOP_DP), side, dp(PAGE_TOP_DP))
    }

    private fun trackParams(top: Int) = LinearLayout.LayoutParams(Ui.MATCH, dp(TRACK_DP)).apply { topMargin = dp(top) }

    /** 뒤로 가기와 기록 화면으로 가는 칸. 서로 다른 화면으로 가므로 칸마다 떼어 놓는다 */
    private fun topBar() = Ui.backBar(this, "설정", { finish() }, listOf(
        Ui.pill(this, "다닌 곳", R.drawable.ic_set_place, arrow = true) {
            startActivity(Intent(this@SleepSettingsActivity, TripDiaryActivity::class.java))
        },
        Ui.pill(this, "주행 중 수면", R.drawable.ic_set_moon, arrow = true) {
            startActivity(Intent(this@SleepSettingsActivity, SleepReportActivity::class.java))
        },
    ), PILL_GAP_DP)

    /** 유리 카드 안에 그림 → 제목 → 설명 순서로 놓는다. 고르는 칸은 카드 아래쪽에 붙는다 */
    private fun sideCard(iconRes: Int, title: String, description: String) = Ui.column(this, CARD_INSET_DP, Ui.glass(this)).apply {
        addView(Ui.picture(context, iconRes), LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)))
        addView(Ui.text(context, title, Ui.TEXT_SECTION, Ui.CREAM_INK, Ui.SEMIBOLD), Ui.below(context, 12))
        addView(Ui.text(context, description, color = Ui.CREAM_SUBTLE).apply { Ui.keepWords(this) }, Ui.below(context, 6))
        addView(Space(context), LinearLayout.LayoutParams(Ui.MATCH, dp(TRACK_GAP_DP), 1f))
    }

    private fun tempCard() = sideCard(R.drawable.ic_set_temp, "잠잘 때 온도", "아이가 잠들면 이 온도로 맞출지 물어봐요.").apply {
        fun step(symbol: String, delta: Float) =
            Ui.button(context, symbol, fill = Ui.CLEAR, textColor = Ui.CREAM_INK, size = 36f) { adjust(delta) }
        tempValue = Ui.text(context, size = TEMP_SP, color = Ui.CREAM_INK, weight = Ui.SEMIBOLD).apply { gravity = Gravity.CENTER }
        noneButton = Ui.button(context, "묻지 않기") {
            SleepRangeStore.clear(this@SleepSettingsActivity)
            target = null
            show()
        }
        addView(Ui.segments(context, listOf(step("−", -TEMP_STEP_C), tempValue, step("+", TEMP_STEP_C))), trackParams(0))
        // "정하지 않음"도 한 줄에 들어가게 숫자 칸을 넓힌다
        (tempValue.layoutParams as LinearLayout.LayoutParams).weight = TEMP_VALUE_WEIGHT
        addView(Ui.segments(context, listOf(noneButton)), trackParams(TRACK_GAP_DP))
    }

    private fun lightCard() = sideCard(R.drawable.ic_set_light, "잠잘 때 조명", "아이가 잠들면 실내등과 무드등을 줄일지 물어봐요.").apply {
        listOf(true to "물어보기", false to "묻지 않기").forEachIndexed { index, (ask, label) ->
            val button = Ui.button(context, label) {
                SleepLightStore.set(this@SleepSettingsActivity, ask)
                show()
            }.also { lightButtons[ask] = it }
            addView(Ui.segments(context, listOf(button)), trackParams(if (index == 0) 0 else TRACK_GAP_DP))
        }
    }

    private fun photoCard(wide: Boolean) = LinearLayout(this).apply {
        orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val inset = dp(CARD_INSET_DP)
        if (wide) setPadding(inset, 0, inset, 0) else setPadding(inset, inset, inset, inset)
        background = Ui.glass(context)
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.picture(context, R.drawable.ic_set_photo), LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(context, "주행 중 사진", Ui.TEXT_SECTION, Ui.CREAM_INK, Ui.SEMIBOLD))
                addView(Ui.text(context, "사진을 몇 분마다 바꿀지 골라요.", color = Ui.CREAM_SUBTLE))
            }, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = dp(HEADING_GAP_DP) })
        }
        val cells = (listOf(0) + DrivePhotoStore.CHOICES).map { minutes ->
            val label = if (minutes == 0) "끄기" else "${minutes}분"
            Ui.button(context, label) {
                DrivePhotoStore.set(this@SleepSettingsActivity, minutes)
                show()
            }.also { photoButtons[minutes] = it }
        }
        val track = Ui.segments(context, cells)
        val demo = Ui.pill(context, "시연 처음부터", R.drawable.ic_replay, tintIcon = true) {
            RearStatusActivity.demoRestart?.invoke()
            finish()
        }
        if (wide) {
            addView(heading, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP))
            addView(track, LinearLayout.LayoutParams(0, dp(TRACK_DP), 1f).apply { leftMargin = dp(HEADING_GAP_DP * 2) })
            addView(demo, LinearLayout.LayoutParams(Ui.WRAP, dp(Ui.TOUCH_DP)).apply { leftMargin = dp(HEADING_GAP_DP) })
        } else {
            addView(heading, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
            addView(track, trackParams(TRACK_GAP_DP))
            addView(demo, LinearLayout.LayoutParams(Ui.WRAP, dp(Ui.TOUCH_DP)).apply { topMargin = dp(TRACK_GAP_DP) })
        }
    }

    // 온도를 바꾸고 바로 저장한다. 정하기 전이면 처음 누를 때 시작 숫자가 된다. 권하는 값이 아니고 누르기 전에는 온도를 묻지 않는다
    private fun adjust(delta: Float) {
        val next = (target?.plus(delta) ?: START_C).coerceIn(MIN_C, MAX_C)
        target = next
        SleepRangeStore.set(this, ComfortAdvisor.SleepRange.around(next))
        show()
    }

    private fun show() {
        tempValue.text = SleepRangeStore.describe(target)
        Ui.paintChoice(noneButton, target == null)
        val askLights = SleepLightStore.ask(this)
        lightButtons.forEach { (ask, button) -> Ui.paintChoice(button, ask == askLights) }
        val minutes = DrivePhotoStore.minutes(this)
        photoButtons.forEach { (m, button) -> Ui.paintChoice(button, m == minutes) }
    }

    override fun onDestroy() {
        driving.stop()
        super.onDestroy()
    }

    companion object {
        private const val WIDE_WINDOW_DP = 1200 // 이보다 좁으면 양옆 카드 사이에 차가 들어갈 자리가 없다
        private const val PAGE_SIDE_DP = 26
        private const val PAGE_TOP_DP = 20
        private const val PILL_GAP_DP = 14
        private const val SIDE_CARD_DP = 486
        private const val SIDE_CARD_HEIGHT_DP = 572
        private const val PHOTO_CARD_HEIGHT_DP = 194
        private const val CARD_INSET_DP = 24
        private const val ICON_DP = 104
        private const val TRACK_DP = 92
        private const val TRACK_GAP_DP = 12
        private const val HEADING_GAP_DP = 22
        private const val TEMP_SP = 40f
        private const val ROAD_FROM_BOTTOM_DP = 410
        private const val SUN_X = 0.5f
        private const val SUN_Y_DP = 300
        private const val CAR_SCALE = 4.4f
        private const val TEMP_STEP_C = 0.5f
        private const val START_C = 23f // 처음 누를 때의 시작 숫자. 권하는 온도가 아니다
        private const val MIN_C = 16f // 앞뒤 1도를 더해도 차 공조가 받는 15~30도 안에 있게 한다
        private const val MAX_C = 29f
        private const val TEMP_VALUE_WEIGHT = 2f
    }
}
