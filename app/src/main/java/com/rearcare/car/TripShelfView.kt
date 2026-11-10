package com.rearcare.car

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * 다닌 곳 선반. 나이별로 한 줄씩 놓고, 기록 하나를 카드 한 장으로 옆으로 넘긴다.
 * 카드에는 장소와 날짜만 쓴다. 바탕은 부모가 넣은 사진이고, 사진이 없으면 계절 그림이다(res/drawable/art_*.xml).
 * 카드를 누르면 onOpen을 부른다. 예시 기록도 사진은 넣을 수 있다.
 */
class TripShelfView(
    context: Context,
    private val onOpen: (Trip) -> Unit,
    private val onNavi: (Trip) -> Unit,
) : ScrollView(context) {

    private val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(BOTTOM_DP))
    }
    private val rails = HashMap<Int?, HorizontalScrollView>() // 다시 그릴 때 넘겨 둔 자리를 지키려고 나이별로 둔다
    private val loader = Executors.newSingleThreadExecutor()

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(body)
    }

    private fun dp(value: Int) = Ui.dp(context, value)

    /** 기록이나 사진이 바뀌면 다시 그린다. 보고 있던 자리는 그대로 둔다 */
    fun render(groups: List<TripDiary.Group>, birth: LocalDate?, empty: View) {
        val keepY = scrollY
        val keepX = rails.mapValues { it.value.scrollX }
        body.removeAllViews()
        rails.clear()
        if (groups.isEmpty()) {
            body.addView(empty, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply {
                leftMargin = dp(SIDE_DP)
                rightMargin = dp(SIDE_DP)
                topMargin = dp(HEAD_TOP_DP)
            })
        }
        groups.forEach { group ->
            body.addView(head(group, birth), LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
            body.addView(rail(group), LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        post {
            scrollTo(0, keepY)
            rails.forEach { (age, rail) -> keepX[age]?.let { rail.scrollTo(it, 0) } }
        }
    }

    fun release() {
        loader.shutdownNow()
    }

    private fun head(group: TripDiary.Group, birth: LocalDate?) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(SIDE_DP), dp(HEAD_TOP_DP), dp(SIDE_DP), 0)
        addView(Ui.text(context, TripShelf.title(group.age), Ui.TEXT_TITLE, Ui.CREAM_INK, Ui.MEDIUM).apply { maxLines = 1 })
        addView(Ui.text(context, TripShelf.meta(group, birth), Ui.TEXT_BODY, Ui.CREAM_SUBTLE).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = dp(HEAD_GAP_DP) })
    }

    private fun rail(group: TripDiary.Group) = Ui.sideScroll(context).apply {
        clipToPadding = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipToPadding = false
            setPadding(dp(SIDE_DP), dp(RAIL_PAD_DP), dp(SIDE_DP), dp(RAIL_PAD_DP))
            group.trips.forEachIndexed { index, trip ->
                addView(card(trip), LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), dp(CARD_HEIGHT_DP)).apply {
                    if (index > 0) leftMargin = dp(CARD_GAP_DP)
                })
            }
        })
        rails[group.age] = this
    }

    private fun card(trip: Trip) = FrameLayout(context).apply {
        background = Ui.rounded(context, Ui.PHOTO, Ui.CARD_RADIUS_DP)
        clipToOutline = true
        Ui.lift(this)

        val hasPhoto = TripPhotos.has(context, trip.id)
        addCover(this, trip, hasPhoto)

        if (trip.sample) {
            addView(Ui.chip(context, "예시", TAG_SIDE_DP), FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(CARD_INSET_DP)
                topMargin = dp(CARD_INSET_DP)
            })
        }

        addView(words(trip, hasPhoto), FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM).apply {
            leftMargin = dp(WORDS_LEFT_DP)
            rightMargin = dp(WORDS_RIGHT_DP)
            bottomMargin = dp(WORDS_LEFT_DP)
        })

        isClickable = true
        isFocusable = true
        foreground = Ui.pressable(context, Ui.CLEAR, Ui.CARD_RADIUS_DP)
        contentDescription = "${trip.name} ${TripDiary.dateLabel(trip.date)}. ${if (hasPhoto) "사진 보기" else "사진 넣기"}"
        setOnClickListener { onOpen(trip) }

        // 위치를 모르는 기록은 내비에 찾을 곳이 없다
        if (TripShelf.canNavigate(trip)) {
            addView(naviButton(trip), FrameLayout.LayoutParams(dp(Ui.TOUCH_DP), dp(Ui.TOUCH_DP), Gravity.TOP or Gravity.END).apply {
                rightMargin = dp(CARD_INSET_DP)
                topMargin = dp(CARD_INSET_DP)
            })
        }
    }

    /** 카드 바탕. 넣은 사진이 있으면 사진과 글자 뒤의 어두운 띠를, 없으면 계절 그림을 깐다 */
    private fun addCover(card: FrameLayout, trip: Trip, hasPhoto: Boolean) {
        val picture = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        card.addView(picture, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        if (hasPhoto) {
            loadPhoto(picture, trip.id)
            // 사진은 밝기를 알 수 없어 글자 뒤를 어둡게 깐다. 계절 그림에는 그림 안에 같은 띠가 있다
            card.addView(View(context).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Ui.withAlpha(Ui.TRACK, 0), Ui.withAlpha(Ui.TRACK, PHOTO_SCRIM_MIDDLE), Ui.TRACK))
            }, FrameLayout.LayoutParams(Ui.MATCH, dp(SCRIM_DP), Gravity.BOTTOM))
        } else {
            picture.setImageResource(art(TripShelf.season(trip.date)))
            picture.scaleX = if (TripShelf.mirrored(trip.date)) -1f else 1f
        }
    }

    /** 카드 아래쪽 글자: 장소, 날짜. 내가 남긴 기록에 사진이 없으면 사진 넣는 법을 덧붙인다 */
    private fun words(trip: Trip, hasPhoto: Boolean) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(Ui.text(context, trip.name, CARD_NAME_SP, Ui.INK, Ui.MEDIUM).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addView(Ui.text(context, TripDiary.dateLabel(trip.date), Ui.TEXT_BODY, Ui.INK))
        if (!trip.sample && !hasPhoto) addView(Ui.text(context, "눌러서 사진 넣기", Ui.TEXT_BODY, Ui.INK))
    }

    private fun naviButton(trip: Trip) =
        Ui.iconButton(context, R.drawable.ic_near_me, "${trip.name} 내비", Ui.ON_ART, Ui.TOUCH_DP / 2, NAVI_INSET_DP, Ui.ON_PRIMARY) { onNavi(trip) }

    private fun art(season: Season) = when (season) {
        Season.SPRING -> R.drawable.art_spring
        Season.SUMMER -> R.drawable.art_summer
        Season.AUTUMN -> R.drawable.art_autumn
        Season.WINTER -> R.drawable.art_winter
    }

    private fun loadPhoto(view: ImageView, id: String) {
        val width = dp(CARD_WIDTH_DP)
        val height = dp(CARD_HEIGHT_DP)
        val appContext = context.applicationContext
        loader.execute {
            val bitmap = TripPhotos.load(appContext, id, width, height) ?: return@execute
            view.post { view.setImageBitmap(bitmap) }
        }
    }

    private companion object {
        const val SIDE_DP = 24
        const val BOTTOM_DP = 24
        const val HEAD_TOP_DP = 8
        const val HEAD_GAP_DP = 18
        const val RAIL_PAD_DP = 12
        const val CARD_WIDTH_DP = 372
        const val CARD_HEIGHT_DP = 440
        const val CARD_GAP_DP = 16
        const val CARD_INSET_DP = 16
        const val CARD_NAME_SP = 44f
        const val WORDS_LEFT_DP = 22
        const val WORDS_RIGHT_DP = 18
        const val TAG_SIDE_DP = 12
        const val NAVI_INSET_DP = 20
        const val SCRIM_DP = 176
        const val PHOTO_SCRIM_MIDDLE = 0xD1 // 흰 글자가 밝은 사진 위에서도 읽히게 한 어둡기
    }
}
