package com.rearcare.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.appcompat.content.res.AppCompatResources

/**
 * 그날 하나의 주행을 길 위에 그린다. 배경의 SceneView와 길의 높이를 맞춘다.
 * 출발부터 도착까지 가는 선 위에 잔 구간을 금색으로 칠하고, 구간마다 말풍선에 시각을 쓴다. 말풍선이 겹치면 위 줄로 올린다.
 * 출발 시각을 모르는 예전 기록은 "출발", "도착"만 적는다. 세워 둔 자리는 선을 끊어 보인다. 선의 길이는 달린 시간만 나타낸다.
 */
class SleepDayRoadView(context: Context) : View(context) {

    private class Bubble(val text: String, val center: Float, val left: Float, val right: Float, val row: Int)

    private var rideSec = 1L
    private var spans = emptyList<SleepSummary.Span>()
    private var stops = emptyList<SleepSummary.Stop>()
    private var texts = emptyList<String>()
    private var startLabel = "출발"
    private var endLabel = "도착"
    private var bubbles = emptyList<Bubble>()
    private var rows = 1

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.CREAM_PRIMARY
        textSize = sp(Ui.TEXT_BUTTON)
        typeface = Ui.face(Ui.SEMIBOLD)
        textAlign = Paint.Align.CENTER
    }
    private val car = AppCompatResources.getDrawable(context, R.drawable.art_car)
    private val rect = RectF()

    fun show(trip: Trip) {
        val s = trip.sleep
        rideSec = SleepStats.rideSec(trip).coerceAtLeast(1)
        spans = s?.spans.orEmpty()
        stops = s?.stops.orEmpty()
        texts = if (s == null) emptyList() else spans.map { "${SleepStats.spanLine(s, it)} · ${SleepStats.duration((it.toSec - it.fromSec).toLong())}" }
        val depart = s?.departMinute
        startLabel = depart?.let { "출발 ${SleepStats.shortClock(it)}" } ?: "출발"
        endLabel = if (s != null && depart != null) "도착 ${SleepStats.shortClock(SleepStats.arrivalMinute(s, depart, rideSec))}" else "도착"
        contentDescription = "수면 시간대. $startLabel, $endLabel. " + texts.joinToString(", ")
        requestLayout()
        invalidate()
    }

    private fun startX() = dp(LEFT_DP)
    private fun endX(viewWidth: Int) = viewWidth - dp(RIGHT_DP)
    private fun xOf(sec: Int, viewWidth: Int) = startX() + (endX(viewWidth) - startX()) * sec / rideSec
    private fun road() = height - dp(ROAD_FROM_BOTTOM_DP.toFloat())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val margin = dp(EDGE_DP)
        val placed = spans.zip(texts).map { (span, text) ->
            val center = xOf((span.fromSec + span.toSec) / 2, w)
            val width = label.measureText(text) + dp(BUBBLE_SIDE_DP) * 2
            val left = (center - width / 2).coerceIn(margin, (w - width - margin).coerceAtLeast(margin))
            Triple(text, center, left to left + width)
        }
        val assigned = SleepChart.bubbleRows(placed.map { it.third }, dp(BUBBLE_GAP_DP))
        bubbles = placed.mapIndexed { i, (text, center, edges) -> Bubble(text, center, edges.first, edges.second, assigned[i]) }
        rows = ((assigned.maxOrNull() ?: 0) + 1)
        val height = dp(TOP_GAP_DP) + rows * dp(BUBBLE_DP) + (rows - 1) * dp(ROW_GAP_DP) + dp(CONNECTOR_DP) + dp(ROAD_FROM_BOTTOM_DP.toFloat())
        setMeasuredDimension(w, height.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val road = road()
        val x0 = startX()
        val x1 = endX(width)
        val lineY = road + dp(SceneView.ROAD_DP / 2f)
        val half = dp(LINE_DP) / 2
        // 선과 겹치는 차선을 가린다
        fill.color = SceneView.ROAD
        canvas.drawRect(x0 - dp(24f), road + dp(SceneView.LANE_COVER_DP.toFloat()), x1 + dp(24f), road + dp((SceneView.ROAD_DP - SceneView.LANE_COVER_DP).toFloat()), fill)
        fill.color = Ui.REST
        rect.set(x0, lineY - half, x1, lineY + half)
        canvas.drawRoundRect(rect, half, half, fill)
        fill.color = Ui.CHART_SLEEP
        spans.forEach { span ->
            val left = xOf(span.fromSec, width)
            val right = maxOf(left + dp(MIN_SEGMENT_DP), xOf(span.toSec, width)).coerceAtMost(x1)
            rect.set(left, lineY - half, right, lineY + half)
            canvas.drawRoundRect(rect, half, half, fill)
        }
        fill.color = SceneView.ROAD
        stops.forEach { stop ->
            val x = xOf(stop.atSec, width)
            canvas.drawRect(x - dp(STOP_DP) / 2, lineY - half, x + dp(STOP_DP) / 2, lineY + half, fill)
        }

        // 구간과 말풍선을 잇는 선을 먼저 그려 말풍선이 그 위에 오게 한다
        stroke.color = Ui.CHART_SLEEP
        stroke.strokeWidth = dp(5f)
        bubbles.forEach { canvas.drawLine(it.center, bubbleTop(it.row) + dp(BUBBLE_DP), it.center, lineY, stroke) }
        bubbles.forEach { bubble ->
            val top = bubbleTop(bubble.row)
            rect.set(bubble.left, top, bubble.right, top + dp(BUBBLE_DP))
            fill.color = BUBBLE_FILL
            canvas.drawRoundRect(rect, dp(BUBBLE_RADIUS_DP), dp(BUBBLE_RADIUS_DP), fill)
            stroke.color = Ui.GLASS_EDGE
            stroke.strokeWidth = dp(2f)
            canvas.drawRoundRect(rect, dp(BUBBLE_RADIUS_DP), dp(BUBBLE_RADIUS_DP), stroke)
            canvas.drawText(bubble.text, (bubble.left + bubble.right) / 2, top + dp(BUBBLE_DP) / 2 + sp(Ui.TEXT_BUTTON) / 3, label)
        }

        sign(canvas, x0, road, startLabel)
        sign(canvas, x1, road, endLabel)
        car?.let { SceneView.drawCar(canvas, it, x1 + dp(CAR_OFFSET_DP), road + dp(SceneView.CAR_WHEEL_DP.toFloat()), dp(CAR_SCALE)) }
    }

    /** 위 칸부터 번호가 큰 줄이다. 0번 줄이 길에 가장 가깝다 */
    private fun bubbleTop(row: Int) = dp(TOP_GAP_DP) + (rows - 1 - row) * (dp(BUBBLE_DP) + dp(ROW_GAP_DP))

    private fun sign(canvas: Canvas, x: Float, road: Float, text: String) {
        val roadBottom = road + dp(SceneView.ROAD_DP.toFloat())
        val top = roadBottom + dp(POLE_DP)
        stroke.color = SceneView.SIGN_EDGE
        stroke.strokeWidth = dp(7f)
        canvas.drawLine(x, roadBottom, x, top, stroke)
        val w = label.measureText(text) + dp(SIGN_SIDE_DP) * 2
        rect.set(x - w / 2, top, x + w / 2, top + dp(SIGN_DP))
        fill.color = SceneView.SIGN_FILL
        canvas.drawRoundRect(rect, dp(SIGN_RADIUS_DP), dp(SIGN_RADIUS_DP), fill)
        stroke.strokeWidth = dp(3f)
        canvas.drawRoundRect(rect, dp(SIGN_RADIUS_DP), dp(SIGN_RADIUS_DP), stroke)
        canvas.drawText(text, x, top + dp(SIGN_DP) / 2 + sp(Ui.TEXT_BUTTON) / 3, label)
    }

    companion object {
        const val ROAD_FROM_BOTTOM_DP = 194 // 길 위부터 이 뷰 아래까지. 길, 표지판, 땅의 높이다
        private const val LEFT_DP = 150f
        private const val RIGHT_DP = 267f // 도착 뒤에 작은 차가 설 자리
        private const val EDGE_DP = 16f
        private const val TOP_GAP_DP = 26f
        private const val BUBBLE_DP = 84f
        private const val BUBBLE_SIDE_DP = 48f
        private const val BUBBLE_GAP_DP = 14f
        private const val BUBBLE_RADIUS_DP = 28f
        private const val ROW_GAP_DP = 12f
        private const val CONNECTOR_DP = 54f
        private const val LINE_DP = 20f
        private const val MIN_SEGMENT_DP = 12f
        private const val STOP_DP = 6f
        private const val POLE_DP = 32f
        private const val SIGN_DP = 44f
        private const val SIGN_SIDE_DP = 22f
        private const val SIGN_RADIUS_DP = 12f
        private const val CAR_OFFSET_DP = 130f
        private const val CAR_SCALE = 2.8f
        private const val BUBBLE_FILL = Ui.GLASS
    }
}
