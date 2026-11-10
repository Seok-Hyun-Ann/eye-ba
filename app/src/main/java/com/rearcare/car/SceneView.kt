package com.rearcare.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import androidx.appcompat.content.res.AppCompatResources

/**
 * 설정과 주행 중 수면 화면의 배경 그림. 낮 하늘, 언덕, 길을 그린다. 다닌 곳 카드의 계절 그림과 같은 그림체다.
 * 길의 위치는 화면 아래에서 잰다. 막대 그래프가 길 위에 서야 해서 창 크기가 달라도 길과 땅의 높이는 그대로다.
 * carScale이 0이면 차를 그리지 않는다. 막대 그래프는 막대 끝에 자기 차를 그린다.
 */
class SceneView(
    context: Context,
    private val roadFromBottomDp: Int,
    private val sunXRatio: Float,
    private val sunYDp: Int,
    private val carScale: Float,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DASH
        style = Paint.Style.STROKE
        strokeWidth = 7 * density
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(52 * density, 46 * density), 0f)
    }
    private val dashLine = Path()
    private val farHill = Path()
    private val nearHill = Path()
    private val car = if (carScale > 0f) AppCompatResources.getDrawable(context, R.drawable.art_car) else null
    private var skyShader: Shader? = null
    private var groundShader: Shader? = null

    private fun road() = height - roadFromBottomDp * density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val r = road()
        val roadBottom = r + ROAD_DP * density
        skyShader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(SKY_TOP, SKY_MIDDLE, SKY_BOTTOM), floatArrayOf(0f, .48f, .78f), Shader.TileMode.CLAMP)
        groundShader = LinearGradient(0f, roadBottom, 0f, h.toFloat(), GROUND_TOP, GROUND_END, Shader.TileMode.CLAMP)
        val x = w / DESIGN_WIDTH
        fun y(up: Int) = r - up * density
        farHill.reset()
        farHill.moveTo(0f, y(170))
        farHill.cubicTo(220 * x, y(330), 430 * x, y(300), 640 * x, y(190))
        farHill.cubicTo(850 * x, y(80), 1050 * x, y(140), 1240 * x, y(250))
        farHill.cubicTo(1430 * x, y(360), 1600 * x, y(300), w.toFloat(), y(190))
        farHill.lineTo(w.toFloat(), y(-10))
        farHill.lineTo(0f, y(-10))
        farHill.close()
        nearHill.reset()
        nearHill.moveTo(0f, y(70))
        nearHill.cubicTo(260 * x, y(190), 520 * x, y(150), 760 * x, y(80))
        nearHill.cubicTo(1000 * x, y(10), 1250 * x, y(40), w.toFloat(), y(130))
        nearHill.lineTo(w.toFloat(), y(-10))
        nearHill.lineTo(0f, y(-10))
        nearHill.close()
        dashLine.reset()
        dashLine.moveTo(-20 * density, r + ROAD_DP * density / 2)
        dashLine.lineTo(w + 20 * density, r + ROAD_DP * density / 2)
    }

    override fun onDraw(canvas: Canvas) {
        val r = road()
        val w = width.toFloat()
        val x = w / DESIGN_WIDTH
        paint.shader = skyShader
        canvas.drawRect(0f, 0f, w, height.toFloat(), paint)
        paint.shader = null
        CLOUDS.forEach { (cx, cy, size) -> cloud(canvas, cx * x, cy * density, size * density) }
        val sunX = w * sunXRatio
        val sunY = sunYDp * density
        paint.color = SUN_HALO
        canvas.drawCircle(sunX, sunY, 150 * density, paint)
        paint.color = SUN
        canvas.drawCircle(sunX, sunY, 96 * density, paint)
        paint.color = FAR_HILL
        canvas.drawPath(farHill, paint)
        paint.color = NEAR_HILL
        canvas.drawPath(nearHill, paint)
        paint.color = ROAD
        canvas.drawRect(0f, r, w, r + ROAD_DP * density, paint)
        canvas.drawPath(dashLine, dashes)
        paint.shader = groundShader
        canvas.drawRect(0f, r + ROAD_DP * density, w, height.toFloat(), paint)
        paint.shader = null
        car?.let { drawCar(canvas, it, w / 2, r + CAR_WHEEL_DP * density, carScale * density) }
    }

    private fun cloud(canvas: Canvas, x: Float, y: Float, size: Float) {
        paint.color = CLOUD
        canvas.drawOval(x - size * 1.7f, y - size * .62f, x + size * 1.7f, y + size * .62f, paint)
        canvas.drawCircle(x - size * .45f, y - size * .3f, size * .55f, paint)
        canvas.drawCircle(x + size * .4f, y - size * .42f, size * .72f, paint)
    }

    companion object {
        const val ROAD_DP = 72 // 길의 높이
        const val ROAD = 0xFFF7E4BE.toInt()
        const val GROUND_END = 0xFFA3BC7E.toInt() // 땅의 맨 아래 색. 장면 아래로 이어지는 칸의 바탕이다
        const val CAR_WHEEL_DP = 30 // 차 바퀴 아래가 길 위 어디에 닿는가
        const val LANE_COVER_DP = 8 // 길 위에 그릴 때 차선을 가리는 띠를 길 위아래 끝에서 이만큼 안쪽에 둔다
        const val SIGN_FILL = 0xFFFFF8E6.toInt() // 길가 표지판
        const val SIGN_EDGE = 0xFF8A6E4B.toInt()
        private const val DESIGN_WIDTH = 1707f // 언덕과 구름의 가로 위치를 잰 화면 폭
        private const val SKY_TOP = 0xFFA8DCF7.toInt()
        private const val SKY_MIDDLE = 0xFFD9F0F6.toInt()
        private const val SKY_BOTTOM = 0xFFFFF0D6.toInt()
        private const val SUN = 0xFFFFD76A.toInt()
        private const val SUN_HALO = 0x8CFFF3C4.toInt()
        private const val CLOUD = 0xEBFFFFFF.toInt()
        private const val FAR_HILL = 0xFFB9DDAA.toInt()
        private const val NEAR_HILL = 0xFF93C988.toInt()
        private const val GROUND_TOP = 0xFFBFD79A.toInt()
        private const val DASH = 0xFFFFFFFF.toInt()
        private const val CAR_VIEW_WIDTH = 100f
        private const val CAR_VIEW_HEIGHT = 54f
        private const val CAR_WHEEL_VIEW_Y = 50f // art_car에서 바퀴 아래의 높이
        private val CLOUDS = listOf(Triple(250f, 300f, 70f), Triple(1420f, 470f, 84f), Triple(640f, 560f, 52f), Triple(1130f, 180f, 46f))

        /** 작은 차를 바퀴 아래가 wheelY에 닿게 그린다. scale은 그림 단위 하나의 픽셀 수다 */
        fun drawCar(canvas: Canvas, car: android.graphics.drawable.Drawable, centerX: Float, wheelY: Float, scale: Float) {
            val left = centerX - CAR_VIEW_WIDTH / 2 * scale
            val top = wheelY - CAR_WHEEL_VIEW_Y * scale
            car.setBounds(left.toInt(), top.toInt(), (left + CAR_VIEW_WIDTH * scale).toInt(), (top + CAR_VIEW_HEIGHT * scale).toInt())
            car.draw(canvas)
        }
    }
}
