package com.rearcare.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.content.res.AppCompatResources
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * 고른 나이의 날짜마다 잔 시간을 길 위에 세운 막대로 그린다. 배경의 SceneView와 길의 높이를 맞춘다.
 * 가로는 다닌 날짜, 세로는 시간. 기둥 전체가 탄 시간이고 아래 금색이 잔 시간, 위 회청색이 깨어 있었거나 못 본 시간이다.
 * 일은 길 위에, 달과 해는 길 아래 두 줄에 쓴다(SleepChart.marks). 맨 끝에 작은 차를 그린다.
 * 날짜가 적으면 칸을 넓혀 화면에 고르게 펴고, 많으면 옆으로 민다(HorizontalScrollView 안에 둔다). 시간 눈금은 밀어도 움직이지 않게 SleepBarsAxis가 따로 그린다.
 * 기둥을 누르면 onPick으로 그날의 번호를 알린다.
 */
class SleepBarsView(context: Context) : View(context) {

    data class Bar(val tripId: String, val date: LocalDate, val rideSec: Long, val sleptSec: Long)

    var onPick: ((String) -> Unit)? = null

    /** 다음 배치가 끝나면 한 번 부른다. 막대를 바꾼 뒤 끝으로 밀 때 쓴다 */
    var afterLayout: (() -> Unit)? = null

    private var bars = emptyList<Bar>()
    private var marks = emptyList<SleepChart.Mark?>()
    private var slot = 0f // 막대 한 칸의 폭. onMeasure에서 정한다
    private var start = 0f // 막대가 적어 칸이 최대일 때 가운데로 모으는 왼쪽 여백
    private var topSec = 3600L
    var ticks = emptyList<Long>()
        private set

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BAR_EDGE
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GRID
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(9f)), 0f)
    }
    private val tickLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MARK_LINE
        strokeWidth = dp(3f)
    }
    private val dayText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.CREAM_PRIMARY
        textSize = sp(Ui.TEXT_BODY)
        typeface = Ui.face(Ui.SEMIBOLD)
        textAlign = Paint.Align.CENTER
    }
    private val markText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.CREAM_INK
        textSize = sp(Ui.TEXT_BUTTON)
        typeface = Ui.face(Ui.SEMIBOLD)
    }
    private val car = AppCompatResources.getDrawable(context, R.drawable.art_car)
    private val path = Path()
    private val rect = RectF()

    /** 눈금은 모든 나이를 합쳐 정한다. 나이를 바꿔도 막대 높이를 서로 비교할 수 있다 */
    fun setScale(all: List<Bar>) {
        val maxSec = all.maxOfOrNull { maxOf(it.rideSec, it.sleptSec) } ?: 0L
        val maxMin = ceil(maxSec / 60.0).toLong().coerceAtLeast(1)
        val step = STEPS_MIN.firstOrNull { maxMin <= it * MAX_TICKS } ?: (ceil(maxMin / MAX_TICKS.toDouble() / 60.0).toLong() * 60)
        val top = ceil(maxMin / step.toDouble()).toLong().coerceAtLeast(1) * step
        topSec = top * 60
        ticks = (1..(top / step)).map { it * step * 60 }
    }

    fun show(list: List<Bar>) {
        bars = list
        marks = SleepChart.marks(list.map { it.date })
        contentDescription = "날짜별 수면 시간 막대. " + list.joinToString(", ") {
            "${TripDiary.dateLabel(it.date)} ${SleepStats.duration(it.rideSec)} 중 ${SleepStats.duration(it.sleptSec)}"
        }
        requestLayout()
        invalidate()
    }

    val dates: List<LocalDate> get() = bars.map { it.date }

    /** 길 위에 표시할 막대 구간. 달 화살표로 고른 달이다 */
    var highlighted: IntRange? = null
        set(value) {
            field = value
            invalidate()
        }

    /** 화면 왼쪽 끝에 있는 막대의 순서 */
    fun indexAt(scrollX: Int): Int = if (bars.isEmpty()) 0 else ((scrollX - start) / slot).toInt().coerceIn(0, bars.lastIndex)

    /** index번째 막대가 시작하는 곳 */
    fun xOf(index: Int): Int = (start + slot * index).toInt()

    private fun slotsWidth() = bars.size * slot

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewport = MeasureSpec.getSize(widthMeasureSpec)
        val room = (viewport - dp(CAR_ROOM_DP)).coerceAtLeast(0f)
        slot = if (bars.isEmpty()) dp(MIN_SLOT_DP) else (room / bars.size).coerceIn(dp(MIN_SLOT_DP), dp(MAX_SLOT_DP))
        start = ((room - slotsWidth()) / 2).coerceAtLeast(0f)
        val wanted = (start + slotsWidth() + dp(CAR_ROOM_DP)).toInt()
        setMeasuredDimension(maxOf(wanted, viewport), dp(HEIGHT_DP.toFloat()).toInt())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        afterLayout?.let {
            afterLayout = null
            it()
        }
    }

    private fun baseline() = dp((TOP_DP + PLOT_DP).toFloat())
    fun yOf(sec: Long) = baseline() - dp(PLOT_DP.toFloat()) * sec / topSec.toFloat()
    private fun centerOf(index: Int) = start + slot * index + slot / 2

    override fun onDraw(canvas: Canvas) {
        val base = baseline()
        val end = start + slotsWidth()
        ticks.forEach { sec ->
            val y = yOf(sec)
            canvas.drawLine(0f, y, end, y, grid)
        }
        // 일 글자와 겹치는 차선을 가린다
        fill.color = SceneView.ROAD
        canvas.drawRect(start, base + dp(SceneView.LANE_COVER_DP.toFloat()), end, base + dp((SceneView.ROAD_DP - SceneView.LANE_COVER_DP).toFloat()), fill)
        highlighted?.let { range ->
            fill.color = ROAD_HIGHLIGHT
            canvas.drawRect(start + slot * range.first, base + dp(SceneView.LANE_COVER_DP.toFloat()), start + slot * (range.last + 1), base + dp((SceneView.ROAD_DP - SceneView.LANE_COVER_DP).toFloat()), fill)
        }
        val roadBottom = base + dp(SceneView.ROAD_DP.toFloat())
        bars.forEachIndexed { i, bar ->
            val cx = centerOf(i)
            drawBar(canvas, bar, cx)
            canvas.drawText(bar.date.dayOfMonth.toString(), cx, base + dp(SceneView.ROAD_DP / 2f + 9f), dayText)
            marks[i]?.let { mark ->
                val x = start + slot * i + dp(8f)
                val textX = x + dp(10f)
                val lastRow = roadBottom + dp(if (mark.year != null) YEAR_ROW_DP else MONTH_ROW_DP)
                canvas.drawLine(x, roadBottom, x, lastRow + dp(6f), tickLine)
                canvas.drawText(mark.month, textX, roadBottom + dp(MONTH_ROW_DP), markText)
                mark.year?.let { canvas.drawText(it, textX, roadBottom + dp(YEAR_ROW_DP), markText) }
            }
        }
        car?.let { SceneView.drawCar(canvas, it, width - dp(CAR_ROOM_DP) / 2, base + dp(SceneView.CAR_WHEEL_DP.toFloat()), dp(CAR_SCALE)) }
    }

    private fun drawBar(canvas: Canvas, bar: Bar, cx: Float) {
        val half = (slot * BAR_RATIO).coerceIn(dp(MIN_BAR_DP), dp(MAX_BAR_DP)) / 2
        val ride = maxOf(bar.rideSec, bar.sleptSec)
        val base = baseline()
        val sleepTop = yOf(bar.sleptSec)
        val rideTop = yOf(ride)
        val r = dp(END_RADIUS_DP)
        if (bar.sleptSec > 0) {
            segment(canvas, cx - half, sleepTop, cx + half, base, if (bar.sleptSec >= ride) r else 0f, SLEEP)
        }
        val restBottom = if (bar.sleptSec > 0) sleepTop else base
        if (ride > bar.sleptSec && restBottom - rideTop > 0f) {
            segment(canvas, cx - half, rideTop, cx + half, restBottom, r, REST)
        }
    }

    /** 위 끝만 둥근 칸. 바닥은 각지게 두어 길에서 자란 것처럼 보이게 한다 */
    private fun segment(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        path.reset()
        val r = minOf(radius, (bottom - top) / 2, (right - left) / 2)
        rect.set(left, top, right, bottom)
        path.addRoundRect(rect, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
        fill.color = color
        canvas.drawPath(path, fill)
        canvas.drawPath(path, edge)
    }

    // ---------- 누르기 ----------

    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (hypot(event.x - downX, event.y - downY) <= slop) {
                    performClick()
                    // 기둥 칸 안을 누르면 그 기둥이다. 기둥이 짧아도 칸 어디를 눌러도 된다
                    if (event.x >= start) bars.getOrNull(((event.x - start) / slot).toInt())?.let { onPick?.invoke(it.tripId) }
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        private const val GROUND_DP = 98 // 길 아래 땅의 높이. 달과 해 글자를 쓴다
        const val ROAD_FROM_BOTTOM_DP = SceneView.ROAD_DP + GROUND_DP
        const val AXIS_WIDTH_DP = 150
        private const val TOP_DP = 8
        private const val PLOT_DP = 300
        const val HEIGHT_DP = TOP_DP + PLOT_DP + ROAD_FROM_BOTTOM_DP
        private const val MIN_SLOT_DP = 70f // 누를 수 있는 가장 좁은 칸
        private const val MAX_SLOT_DP = 200f // 막대가 적어도 칸이 이보다 넓어지지 않게 한다
        private const val MIN_BAR_DP = 40f
        private const val MAX_BAR_DP = 64f
        private const val BAR_RATIO = 0.36f
        private const val CAR_ROOM_DP = 280f
        private const val CAR_SCALE = 2.6f
        private const val END_RADIUS_DP = 8f
        private const val MONTH_ROW_DP = 40f
        private const val YEAR_ROW_DP = 80f
        private const val MAX_TICKS = 5
        private val STEPS_MIN = listOf(1L, 2L, 5L, 10L, 15L, 30L, 60L)
        private const val SLEEP = Ui.CHART_SLEEP
        private const val REST = Ui.REST
        private const val BAR_EDGE = 0xCCFFFFFF.toInt()
        private const val ROAD_HIGHLIGHT = 0xFFF0D088.toInt()
        private const val GRID = 0xD9FFFFFF.toInt()
        private const val MARK_LINE = 0xFF5B4A36.toInt()
    }
}

/** 시간 눈금 글자. 막대를 옆으로 밀어도 움직이지 않게 그래프 왼쪽에 따로 둔다 */
class SleepBarsAxis(context: Context, private val chart: SleepBarsView) : View(context) {

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.CREAM_INK
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics)
        typeface = Ui.face(Ui.MEDIUM)
        textAlign = Paint.Align.RIGHT
    }

    override fun onDraw(canvas: Canvas) {
        val right = width - Ui.dp(context, 16).toFloat()
        chart.ticks.forEach { sec -> canvas.drawText(SleepStats.duration(sec), right, chart.yOf(sec) + Ui.dp(context, 8), text) }
    }
}
