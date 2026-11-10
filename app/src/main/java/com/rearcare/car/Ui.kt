package com.rearcare.car

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources

/**
 * 앱 화면이 함께 쓰는 색과 크기.
 *
 * - 글자(글꼴, 크기, 굵기)는 에뮬레이터의 Pleos 화면을 재서 맞춘 값이다.
 * - 앱 전체의 바탕은 베이지다. 아이 상태 카드만 상태를 알아보도록 짙은 색을 쓴다.
 *   밤낮으로 색을 바꾸지 않는다. 글자 대비는 4.5:1 이상으로 계산해 골랐다.
 * - 터치 크기는 Google Car App Quality의 UX-1(64dp 이상)을 참고한다.
 */
object Ui {
    const val PANEL = 0xFF222A5A.toInt() // 카드
    const val INK = Color.WHITE // 제목
    const val BODY = 0xFFE3E7FF.toInt() // 칸 글자
    const val SUBTLE = 0xFFB9C2EC.toInt() // 설명 글
    const val TRACK = 0xFF161D44.toInt() // 사진 위 글자 뒤에 까는 어두운 띠(다닌 곳 카드)
    const val PHOTO = 0xFF1A2148.toInt() // 사진이 다 채우지 못한 곳의 바탕(작은 창, 다닌 곳 카드와 사진 창)
    const val PRIMARY = 0xFFFFE9B0.toInt() // 금색. 지금은 ACCENT만 이 값을 쓴다
    const val ON_PRIMARY = 0xFF141B3A.toInt() // 밝은 바탕(로고, 그림 위 글자 칸, 내비 버튼) 위의 남색 글자·아이콘
    const val ACCENT = PRIMARY // 로고와 앱 아이콘
    const val ACCENT_DARK = 0xFFFFD98A.toInt() // 로터리 초점 테두리. 글자를 얹지 않는다
    // 잠 그래프(흰 카드 위). 잔 시간과 깨어 있었거나 못 본 시간. 색각 이상에서도 구분되고 카드와 대비 3:1 이상이다(색각 이상 모의 검사로 확인)
    const val CHART_SLEEP = 0xFFC98212.toInt()
    const val REST = 0xFF6F80D2.toInt()
    private const val SHADOW = 0xFF000000.toInt()
    private const val RIPPLE = 0x3CFFFFFF // 누를 때 잔물결
    private const val RIPPLE_ON_LIGHT = 0x1F000000 // 밝은 면을 누를 때 잔물결
    const val CLEAR = Color.TRANSPARENT

    // 앱 전체의 베이지 바탕
    // 베이지 위 글자 대비: CREAM_INK 13.6:1, CREAM_SUBTLE 5.5:1
    const val CREAM = 0xFFF6F1E8.toInt()
    const val CREAM_INK = 0xFF2B2420.toInt()
    const val CREAM_SUBTLE = 0xFF6B5F56.toInt()
    const val CREAM_LINE = 0xFFE3DACB.toInt() // 작은 창의 테두리
    const val CREAM_CARD = Color.WHITE // 베이지 위의 칸
    const val CREAM_PRIMARY = 0xFF3A2E28.toInt() // 베이지 위의 주요 버튼. 흰 글자를 얹는다
    const val ON_ART = 0xEBFFFFFF.toInt() // 그림과 사진 위의 흰 칸
    const val GLASS = 0xC9FFFFFF.toInt() // 장면 위에 띄우는 칸. 뒤 그림이 비친다. 옅은 글자도 어느 그림 위에서나 대비 5:1 이상이다
    const val GLASS_EDGE = 0xBFFFFFFF.toInt()

    /** 같은 색에 투명도만 바꾼다(alpha 0~255). 사진 위에 금색·남색을 옅게 얹을 때 쓴다 */
    fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    /**
     * 상태 카드 색 한 벌. 상태가 바뀔 때만 바뀌고 깜빡이지 않는다.
     * - fill, circle, detail: 색 면으로 보여 줄 때. 평소는 어두운 색으로 차분하게, 우는 때만 빨갛게 둔다. 제목 글자는 늘 INK다.
     */
    class CardLook(val fill: Int, val circle: Int, val detail: Int = SUBTLE)

    val LOOK_CHECK = CardLook(PANEL, 0xFF5A6390.toInt()) // 확인 중, 얼굴 안 보임
    val LOOK_AWAKE = CardLook(0xFF1D3A44.toInt(), 0xFF3F8A7E.toInt()) // 깨어 있음, 옆 보기
    val LOOK_ASLEEP = CardLook(0xFF262F72.toInt(), 0xFF5B6FC0.toInt())
    val LOOK_CRYING = CardLook(0xFFA8342B.toInt(), 0xFF7F241D.toInt(), detail = Color.WHITE)
    val LOOK_CRY_SOUND = CardLook(0xFF40351F.toInt(), 0xFFA67A33.toInt())

    const val TEXT_TITLE = 54f // 큰 제목
    const val TEXT_SECTION = 32f // 항목 제목
    const val TEXT_BUTTON = 28f // 칸 글자
    const val TEXT_BODY = 24f // 설명 글

    // Pleos의 굵기(화면을 글꼴별로 픽셀 대조해 찾았다): 설명 글 Regular, 큰 제목 Medium, 항목 제목과 칸 글자 SemiBold
    const val REGULAR = 400
    const val MEDIUM = 500
    const val SEMIBOLD = 600

    const val TOUCH_DP = 72 // 터치 높이. 64dp 기준보다 조금 넉넉하게
    private const val ROUND_ICON_DP = 36
    const val GAP_DP = 24
    const val SEGMENT_DP = 100 // 고르는 칸 높이
    const val PILL_DP = 84 // 장면 위에 띄우는 누르는 칸의 높이
    const val CARD_RADIUS_DP = 28
    const val BUTTON_RADIUS_DP = 20
    private const val TRACK_INSET_DP = 6
    private const val FOCUS_DP = 6
    private const val EDGE_DP = 2
    private const val SHADOW_DP = 4
    private const val LIGHT_FILL = 0.5f

    const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    /**
     * Pleos 글꼴. 글꼴 파일을 앱에 넣지 않고 차에 있는 글꼴을 이름으로 부른다. 없는 차에서는 시스템 기본 글꼴이 나온다.
     * 이 차의 글꼴 표(fonts_customization.xml)에서 "pretendard"라는 이름은 실제로는 Asta Sans를 가리킨다.
     * 기본 "sans-serif"로는 500을 달라고 해도 Regular가 나와 Pleos의 Medium과 같아지지 않는다.
     */
    private const val FAMILY = "pretendard"

    /**
     * 글꼴 표에는 SemiBold로 가는 이름이 없어서(600이 Bold로 간다) 차에 있는 파일을 직접 읽는다. 앱에 복사하지 않는다.
     * 파일이 없는 차에서는 FAMILY의 600으로 넘어간다.
     */
    private const val SEMIBOLD_FILE = "/product/fonts/AstaSansOTF-SemiBold.otf"

    private fun named(weight: Int) = Typeface.create(Typeface.create(FAMILY, Typeface.NORMAL), weight, false)

    private val semiBold: Typeface by lazy {
        Typeface.Builder(SEMIBOLD_FILE).setFallback(FAMILY).setWeight(SEMIBOLD).build() ?: named(SEMIBOLD)
    }

    fun face(weight: Int): Typeface = if (weight == SEMIBOLD) semiBold else named(weight)

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    /** 글자 한 줄. 차량 기준에 맞춰 기본 24sp */
    fun text(context: Context, value: String = "", size: Float = TEXT_BODY, color: Int = CREAM_INK, weight: Int = REGULAR) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = face(weight)
        }

    /** 한글 낱말 중간에서 줄을 바꾸지 않게 한다(Android 13부터) */
    fun keepWords(view: TextView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) view.lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
    }

    /** 둥근 면 */
    fun rounded(context: Context, fill: Int, radiusDp: Int = CARD_RADIUS_DP) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(context, radiusDp).toFloat()
    }

    /** 카드에 옅은 그림자를 준다. 움직이지 않는다 */
    fun lift(view: View) {
        view.elevation = dp(view.context, SHADOW_DP).toFloat()
        view.outlineAmbientShadowColor = SHADOW
        view.outlineSpotShadowColor = SHADOW
    }

    /** 세로 줄에서 위 칸과 topDp만큼 띄운 자리. 가로는 꽉 채운다. 부를 때마다 새로 만든다 */
    fun below(context: Context, topDp: Int) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(context, topDp) }

    /** 네 변을 insetDp만큼 띄운 세로 칸 */
    fun column(context: Context, insetDp: Int, background: Drawable) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val inset = dp(context, insetDp)
        setPadding(inset, inset, inset, inset)
        this.background = background
    }

    /** 그림 한 장. 꾸밈이라 화면 읽기에서 건너뛴다 */
    fun picture(context: Context, iconRes: Int) = ImageView(context).apply {
        setImageResource(iconRes)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** 아이콘 그림을 따로 떼어 tint로 칠한다. tint가 null이면 칠하지 않는다 */
    fun icon(context: Context, iconRes: Int, tint: Int?): Drawable? =
        AppCompatResources.getDrawable(context, iconRes)?.mutate()?.apply { tint?.let { setTint(it) } }

    /** 칠한 아이콘 한 개. 꾸밈이라 화면 읽기에서 건너뛴다 */
    fun iconView(context: Context, iconRes: Int, tint: Int?) = ImageView(context).apply {
        setImageDrawable(icon(context, iconRes, tint))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** 그림이나 사진 위에 얹는 작은 둥근 글자 칸 */
    fun chip(context: Context, label: String, sideDp: Int) = text(context, label, TEXT_BODY, ON_PRIMARY, REGULAR).apply {
        val side = dp(context, sideDp)
        setPadding(side, 0, side, 0)
        background = rounded(context, ON_ART, sideDp)
    }

    /** 옆으로 미는 칸. 스크롤 막대와 끝에서 늘어나는 효과를 끈다 */
    fun sideScroll(context: Context) = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    /** 색을 채운 버튼 */
    fun button(
        context: Context,
        label: CharSequence,
        fill: Int = CREAM_PRIMARY,
        textColor: Int = Color.WHITE,
        size: Float = TEXT_BUTTON,
        weight: Int = SEMIBOLD,
        radiusDp: Int = BUTTON_RADIUS_DP,
        onClick: () -> Unit,
    ) = Button(context).apply {
        text = label
        isAllCaps = false
        textSize = size
        typeface = face(weight)
        letterSpacing = 0f
        stateListAnimator = null
        minHeight = dp(context, TOUCH_DP)
        minimumHeight = dp(context, TOUCH_DP)
        setPadding(dp(context, 8), 0, dp(context, 8), 0)
        paint(this, fill, textColor, radiusDp)
        setOnClickListener { onClick() }
    }

    /** 누를 수 있는 면. 누르면 잔물결이 지고, 로터리 조작기로 옮겨 왔을 때 금색 테두리가 보인다 */
    fun pressable(context: Context, fill: Int, radiusDp: Int, edge: Int? = null): Drawable {
        val states = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                rounded(context, fill, radiusDp).apply { setStroke(dp(context, FOCUS_DP), ACCENT_DARK) },
            )
            addState(intArrayOf(), rounded(context, fill, radiusDp).apply { edge?.let { setStroke(dp(context, EDGE_DP), it) } })
        }
        val ripple = if (Color.luminance(fill) > LIGHT_FILL) RIPPLE_ON_LIGHT else RIPPLE
        return RippleDrawable(ColorStateList.valueOf(ripple), states, rounded(context, Color.WHITE, radiusDp))
    }

    /** 버튼의 색을 바꾼다(고른 값 표시) */
    fun paint(button: Button, fill: Int, textColor: Int, radiusDp: Int = BUTTON_RADIUS_DP) {
        button.background = pressable(button.context, fill, radiusDp)
        button.setTextColor(textColor)
    }

    /** 고르는 칸. 고른 칸만 진하게 채운다 */
    fun paintChoice(button: Button, selected: Boolean) {
        if (selected) paint(button, CREAM_PRIMARY, Color.WHITE) else paint(button, CLEAR, CREAM_INK)
    }

    /** 고르는 칸 묶음. 옅은 바탕 위에 칸을 나란히 놓는다. 높이는 부르는 쪽에서 정한다(보통 SEGMENT_DP) */
    fun segments(context: Context, cells: List<View>) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val inset = dp(context, TRACK_INSET_DP)
        setPadding(inset, inset, inset, inset)
        background = rounded(context, CREAM, BUTTON_RADIUS_DP + TRACK_INSET_DP)
        cells.forEach { cell ->
            addView(cell, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    }

    /** 칸을 나란히 놓은 줄. 마지막 칸만 lastWidthDp 너비로 두고 나머지 칸이 남는 곳을 나눠 쓴다 */
    fun tileRow(context: Context, tiles: List<View>, lastWidthDp: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
        val gap = dp(context, GAP_DP) / 2
        tiles.forEachIndexed { index, tile ->
            val params = if (index == tiles.lastIndex) {
                LinearLayout.LayoutParams(dp(context, lastWidthDp), MATCH)
            } else {
                LinearLayout.LayoutParams(0, MATCH, 1f)
            }
            addView(tile, params.apply { if (index > 0) leftMargin = gap })
        }
    }

    /**
     * 아이콘만 있는 버튼. 글자가 없으니 label은 화면 읽기용으로 넣는다.
     * insetDp는 아이콘 둘레의 여백, tint가 null이면 아이콘 색을 그대로 둔다.
     */
    fun iconButton(
        context: Context,
        iconRes: Int,
        label: String,
        fill: Int,
        radiusDp: Int,
        insetDp: Int,
        tint: Int?,
        onClick: () -> Unit,
    ) = ImageButton(context).apply {
        setImageResource(iconRes)
        tint?.let { imageTintList = ColorStateList.valueOf(it) }
        scaleType = ImageView.ScaleType.FIT_CENTER
        val inset = dp(context, insetDp)
        setPadding(inset, inset, inset, inset)
        contentDescription = label
        background = pressable(context, fill, radiusDp)
        setOnClickListener { onClick() }
    }

    /** 둥근 아이콘 버튼(뒤로, 설정). 글자가 없으니 label은 화면 읽기용으로 넣는다 */
    fun roundIcon(context: Context, iconRes: Int, label: String, onClick: () -> Unit) =
        iconButton(context, iconRes, label, CLEAR, TOUCH_DP / 2, (TOUCH_DP - ROUND_ICON_DP) / 2, CREAM_INK, onClick)

    /** 아이콘만 있는 칸. 글자가 없으니 label은 화면 읽기용으로 넣는다 */
    fun iconTile(context: Context, iconRes: Int, label: String, onClick: () -> Unit) = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = label
        background = pressable(context, CREAM_CARD, BUTTON_RADIUS_DP)
        lift(this)
        setOnClickListener { onClick() }
        addView(iconView(context, iconRes, CREAM_SUBTLE), LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)))
    }

    /**
     * 항목 이름 아래에 지금 값을 쓰는 칸("잠잘 때 온도 / 24도").
     * 누르면 다른 화면으로 넘어가므로 화살표를 붙인다.
     */
    fun tile(context: Context, title: String, value: TextView, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val inset = dp(context, 16)
        setPadding(inset, 0, inset, 0)
        isClickable = true
        isFocusable = true
        background = pressable(context, CREAM_CARD, BUTTON_RADIUS_DP)
        lift(this)
        setOnClickListener { onClick() }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(context, title, TEXT_BUTTON, CREAM_INK, SEMIBOLD).apply { maxLines = 1 })
            addView(value)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(iconView(context, R.drawable.ic_arrow_forward, CREAM_SUBTLE), LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)))
    }

    /** 장면 위에 띄우는 유리 면. 뒤를 흐리게 하지는 않는다 */
    fun glass(context: Context, radiusDp: Int = CARD_RADIUS_DP) = rounded(context, GLASS, radiusDp).apply { setStroke(dp(context, EDGE_DP), GLASS_EDGE) }

    /** 장면 위에 띄우는 누르는 칸. tintIcon은 글자색으로 칠할 선 아이콘일 때, arrow는 다른 화면으로 갈 때 쓴다 */
    fun pill(context: Context, label: String, iconRes: Int, tintIcon: Boolean = false, arrow: Boolean = false, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val side = dp(context, 22)
        setPadding(side, 0, side, 0)
        isClickable = true
        isFocusable = true
        background = pressable(context, GLASS, CARD_RADIUS_DP, GLASS_EDGE)
        setOnClickListener { onClick() }
        val iconSize = dp(context, if (tintIcon) 36 else 48)
        addView(iconView(context, iconRes, if (tintIcon) CREAM_INK else null), LinearLayout.LayoutParams(iconSize, iconSize))
        addView(
            text(context, label, TEXT_BUTTON, CREAM_INK, SEMIBOLD).apply { maxLines = 1 },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(context, 12) },
        )
        if (arrow) {
            addView(iconView(context, R.drawable.ic_arrow_forward, CREAM_INK), LinearLayout.LayoutParams(dp(context, 30), dp(context, 30)).apply { leftMargin = dp(context, 12) })
        }
    }

    /**
     * 장면 위 맨 윗줄. 왼쪽에 뒤로 가는 칸(title)을, 오른쪽 끝에 trailing을 차례로 둔다.
     * trailing 칸 사이만 gapDp만큼 띄운다.
     */
    fun backBar(context: Context, title: String, onBack: () -> Unit, trailing: List<View>, gapDp: Int = 0) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val height = dp(context, PILL_DP)
        addView(pill(context, title, R.drawable.ic_arrow_back, tintIcon = true, onClick = onBack), LinearLayout.LayoutParams(WRAP, height))
        addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        trailing.forEachIndexed { index, view ->
            addView(view, LinearLayout.LayoutParams(WRAP, height).apply { if (index > 0) leftMargin = dp(context, gapDp) })
        }
    }
}
