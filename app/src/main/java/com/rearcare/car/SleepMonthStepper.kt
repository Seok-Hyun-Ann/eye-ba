package com.rearcare.car

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout

/**
 * 막대가 한 화면을 넘을 때 "‹ 2025년 8월 ›"로 달을 넘기는 칸. 기록이 있는 달만 넘어간다.
 * 손으로 옆으로 밀어도 글자가 지금 보는 달로 바뀐다. 지금 달의 구간은 길 위에 표시한다(SleepBarsView.highlighted).
 */
class SleepMonthStepper(
    context: Context,
    private val chart: SleepBarsView,
    private val scroller: HorizontalScrollView,
) : LinearLayout(context) {

    private var starts = emptyList<Int>()
    private var month = 0

    // 화살표로 고른 달. 끝쪽 달은 왼쪽 끝까지 밀리지 않아 스크롤 위치로는 알 수 없으므로 손으로 밀기 전까지 유지한다
    private var pinned = false

    private val label = Ui.text(context, "", LABEL_SP, Ui.CREAM_INK, Ui.SEMIBOLD).apply {
        gravity = Gravity.CENTER
        maxLines = 1
    }
    private val prev = step(R.drawable.ic_set_prev, "이전 달") { go(month - 1) }
    private val next = step(R.drawable.ic_set_next, "다음 달") { go(month + 1) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = Ui.dp(context, 6)
        setPadding(pad, 0, pad, 0)
        background = Ui.glass(context)
        val width = Ui.dp(context, STEP_WIDTH_DP)
        val height = Ui.dp(context, Ui.TOUCH_DP)
        addView(prev, LayoutParams(width, height))
        addView(label, LayoutParams(Ui.dp(context, LABEL_WIDTH_DP), LayoutParams.WRAP_CONTENT))
        addView(next, LayoutParams(width, height))
        scroller.setOnScrollChangeListener { _, _, _, _, _ -> follow() }
        scroller.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) pinned = false
            false
        }
    }

    private fun step(iconRes: Int, description: String, onClick: () -> Unit) =
        Ui.iconButton(context, iconRes, description, STEP_FILL, Ui.BUTTON_RADIUS_DP, STEP_PAD_DP, null, onClick)

    /** 막대를 바꾼 뒤 부른다. 끝으로 밀어 둔 상태이므로 마지막 달부터 시작한다 */
    fun reset() {
        starts = SleepChart.monthStarts(chart.dates)
        month = starts.lastIndex.coerceAtLeast(0)
        pinned = false
        show()
    }

    private fun go(target: Int) {
        val index = starts.getOrNull(target) ?: return
        month = target
        pinned = true
        show()
        scroller.smoothScrollTo(chart.xOf(index), 0)
    }

    private fun follow() {
        if (pinned || starts.isEmpty()) return
        val latest = if (scroller.canScrollHorizontally(1)) SleepChart.monthAt(starts, chart.indexAt(scroller.scrollX)) else starts.lastIndex
        if (latest != month) {
            month = latest
            show()
        }
    }

    private fun show() {
        val dates = chart.dates
        val first = starts.getOrNull(month) ?: return
        label.text = SleepChart.monthLabel(dates[first])
        paint(prev, month > 0)
        paint(next, month < starts.lastIndex)
        chart.highlighted = first..((starts.getOrNull(month + 1) ?: dates.size) - 1)
    }

    private fun paint(button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else DIM
    }

    private companion object {
        const val LABEL_SP = 30f
        const val LABEL_WIDTH_DP = 230 // "2025년 12월"이 들어가는 폭. 달이 바뀌어도 화살표가 움직이지 않게 고정한다
        const val STEP_WIDTH_DP = 84
        const val STEP_PAD_DP = 18
        val STEP_FILL = Ui.withAlpha(Ui.CREAM, 0xEB)
        const val DIM = 0.35f
    }
}
