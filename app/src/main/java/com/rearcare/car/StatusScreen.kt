package com.rearcare.car

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.rearcare.car.ChildStateTracker.State

/**
 * 본 화면의 모양. 앱 칸(넓은 창, 분할 창)과 내비 위 작은 창이 같은 뷰를 쓴다.
 * 무엇을 보여 줄지(아이 상태, 질문, 안내 글, 작은 창인지, 주행 중인지)는 RearStatusActivity가 정하고,
 * 이 클래스는 뷰를 만들어 그 값대로 놓기만 한다. 누르면 일어나는 일은 생성자로 받은 함수를 부른다.
 *
 * handler는 본 화면의 mainHandler다. 화면이 닫힐 때 거기 걸린 타이머(안내 글 지우기)도 함께 지워진다.
 */
class StatusScreen(
    private val context: Context,
    private val handler: Handler,
    onAnswer: (Boolean) -> Unit,
    onOpenDiary: () -> Unit,
    onOpenSettings: () -> Unit,
    onRestartDemo: () -> Unit,
    onPhotoTap: () -> Unit,
) {
    /** 앱 칸에서는 화면 전체, 작은 창에서는 겹쳐 띄우는 창이 된다 */
    val root: LinearLayout
    val photo: PhotoCardView

    private val body: LinearLayout
    private val cardPane: LinearLayout
    private val card: LinearLayout
    private val cardTexts: LinearLayout
    private val stateIcon: ImageView
    private val titleText: TextView
    private val detailText: TextView
    private val askHint: TextView
    private val consentPanel: LinearLayout
    private val consentText: TextView
    private val voiceText: TextView
    private val parkedPanel: View
    private val headerView: View
    private val messageSlot: View
    private val actionArea: View
    private val rangeValue: TextView
    private val photoValue: TextView
    private val diaryValue: TextView

    // 마지막으로 놓은 배치(applyLayout). 질문과 안내 글이 바뀔 때 이 배치에 맞춰 바탕과 자리를 다시 정한다
    private var floating = false
    private var driveMode = false
    // 상태 카드에 마지막으로 칠한 상태와 배치(render). null이면 다음 render에서 다시 칠한다
    private var paintedState: State? = null

    init {
        val gap = Ui.dp(context, Ui.GAP_DP)

        // 앱 이름 줄: 로고 + 이름. 앱의 얼굴이다
        val logoView = ImageView(context).apply {
            setImageResource(R.drawable.ic_logo)
            imageTintList = android.content.res.ColorStateList.valueOf(Ui.ON_PRIMARY)
            background = Ui.rounded(context, Ui.ACCENT, LOGO_RADIUS_DP)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val nameText = Ui.text(context, context.getString(R.string.header_name), Ui.TEXT_SECTION, Ui.CREAM_INK, Ui.SEMIBOLD)
        val logoSize = Ui.dp(context, LOGO_DP)
        (logoSize / 5).let { logoView.setPadding(it, it, it, it) }
        headerView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(logoView, LinearLayout.LayoutParams(logoSize, logoSize))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(nameText)
            }, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = gap * 2 / 3 })
        }

        // 상태 카드: 고정 아이콘 + 큰 글자 + 작은 글자. 상태가 바뀔 때만 바뀐다
        stateIcon = ImageView(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        titleText = Ui.text(context, weight = Ui.MEDIUM)
        detailText = Ui.text(context)
        cardTexts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleText)
            addView(detailText)
        }
        card = LinearLayout(context).apply {
            background = Ui.rounded(context, Ui.PANEL)
            Ui.lift(this)
            addView(stateIcon)
            addView(cardTexts)
        }

        // 말로 묻는 법 안내. 누르는 곳이 아니어서 칸 없이 글자로만 둔다. 운전자는 차의 음성 비서(Gleo)에게 묻는다
        askHint = Ui.text(context, ASK_HINT, Ui.TEXT_BODY, Ui.CREAM_SUBTLE).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 1
            val size = Ui.dp(context, HINT_ICON_DP)
            val icon = Ui.icon(context, R.drawable.ic_mic, Ui.CREAM_SUBTLE)?.apply { setBounds(0, 0, size, size) }
            setCompoundDrawablesRelative(icon, null, null, null)
            compoundDrawablePadding = gap / 2
        }

        // 제안에 대답하기: 묻는 동안 안내 글 자리에 나온다
        val yesButton = Ui.button(context, "네", fill = Ui.CREAM_PRIMARY, textColor = Color.WHITE, radiusDp = ACTION_AREA_DP / 2) { onAnswer(true) }
        val noButton = Ui.button(context, "아니요", fill = Ui.CREAM_CARD, textColor = Ui.CREAM_INK, radiusDp = ACTION_AREA_DP / 2) { onAnswer(false) }
        consentPanel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            addView(yesButton, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
            addView(noButton, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { leftMargin = gap / 2 })
        }

        // 네·아니요 자리. 주행 중에는 높이를 비워 두어 질문이 떠도 상태 카드 크기가 변하지 않게 한다
        actionArea = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            addView(consentPanel, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        }

        // 안내 글과 묻는 말이 번갈아 나오는 한 줄. 글이 나타나도 상태 카드 크기가 변하지 않게 자리를 비워 둔다
        voiceText = Ui.text(context, color = Ui.CREAM_SUBTLE).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        consentText = Ui.text(context, size = Ui.TEXT_BUTTON, color = Ui.CREAM_INK, weight = Ui.SEMIBOLD).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visibility = View.GONE
            // 두 줄이 될 때 "드릴까/요?"처럼 말 가운데에서 끊기지 않게 한다
            Ui.keepWords(this)
        }
        // 평소에는 한 줄 높이로 비워 둔다. 긴 질문일 때만 늘어나고, 그만큼 상태 카드가 조금 줄어든다
        messageSlot = FrameLayout(context).apply {
            minimumHeight = Ui.dp(context, MESSAGE_SLOT_DP)
            addView(askHint, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(voiceText, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(consentText, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER_VERTICAL))
        }

        // 정차 중에만 보이는 줄: 지금 설정과 시연 버튼(주행 중 설정 금지)
        rangeValue = Ui.text(context, color = Ui.CREAM_SUBTLE).apply { maxLines = 1 }
        photoValue = Ui.text(context, color = Ui.CREAM_SUBTLE).apply { maxLines = 1 }
        diaryValue = Ui.text(context, color = Ui.CREAM_SUBTLE).apply { maxLines = 1 }
        parkedPanel = Ui.tileRow(
            context,
            listOf(
                Ui.tile(context, "다닌 곳", diaryValue, onClick = onOpenDiary),
                Ui.tile(context, "잠잘 때 온도", rangeValue, onClick = onOpenSettings),
                Ui.tile(context, "주행 중 사진", photoValue, onClick = onOpenSettings),
                // 시연 때만 쓰는 칸이라 제품 기능처럼 보이지 않게 아이콘으로 작게 둔다
                Ui.iconTile(context, R.drawable.ic_replay, "시연 처음부터", onRestartDemo),
            ),
            lastWidthDp = PARKED_ROW_DP,
        ).apply { visibility = View.GONE }

        cardPane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(messageSlot, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = gap / 4 })
            addView(actionArea, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(context, ACTION_AREA_DP)).apply { topMargin = gap / 4 })
            addView(parkedPanel, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(context, PARKED_ROW_DP)).apply { topMargin = gap / 2 })
        }

        // 둥근 사진 카드. 작은 창에서만 쓴다. 누르면 설정으로 들어간다
        photo = PhotoCardView(context).apply {
            contentDescription = "설정 열기"
            setOnClickListener { onPhotoTap() }
        }
        body = LinearLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Ui.CREAM)
            addView(headerView)
            addView(body)
        }
    }

    /** 정차 중 줄의 지금 설정 값 */
    fun showParkedValues(range: String, photoInterval: String, diary: String) {
        rangeValue.text = range
        photoValue.text = photoInterval
        diaryValue.text = diary
    }

    fun showParkedPanel(visible: Boolean) {
        parkedPanel.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * 상태 카드를 그린다. 분석이 끝날 때마다(1초마다) 불린다.
     * 글자는 바뀌었을 때만 넣고, 아이콘과 색은 상태나 배치(사진 위에 얹는가)가 바뀔 때만 다시 칠한다.
     * 배치를 바꾸는 applyLayout은 paintedState를 비워 늘 다시 칠하게 한다.
     */
    fun render(status: ChildStateTracker.Status) {
        val now = SystemClock.elapsedRealtime()
        setIfChanged(titleText, StatusWords.title(status, DemoSetup.windowSide))
        setIfChanged(detailText, StatusWords.detail(status, now))
        if (status.state == paintedState) return
        paintedState = status.state
        stateIcon.setImageResource(iconFor(status.state))
        val look = lookOf(status.state)
        (card.background as GradientDrawable).setColor(look.fill)
        (stateIcon.background as GradientDrawable).setColor(look.circle)
        titleText.setTextColor(Ui.INK)
        detailText.setTextColor(look.detail)
    }

    /** 글자가 바뀌면 칸의 폭도 다시 잰다. 앞 글자보다 길어질 때 뒤가 잘리지 않게 한다("자는 것 같아요"가 "자는 것 같"으로 보였다) */
    private fun setIfChanged(view: TextView, text: String) {
        if (view.text.toString() == text) return
        view.text = text
        view.requestLayout()
    }

    fun showConsent(shortQuestion: String) {
        consentText.text = shortQuestion
        // 한 줄에 들어가면 칸 글자 크기로, 길면 설명 글 크기로 줄여 여러 줄로 보여 준다. 잘리면 무엇을 바꾸는지 모른 채 답하게 된다
        consentText.textSize = Ui.TEXT_BUTTON
        // 작은 창에서는 묻기 전까지 이 자리를 접어 두어 폭이 0이다. 그때는 창 안쪽 폭으로 잰다
        val width = (consentText.parent as View).width.takeIf { it > 0 } ?: (root.width - root.paddingLeft - root.paddingRight)
        if (width > 0 && consentText.paint.measureText(shortQuestion) > width) consentText.textSize = Ui.TEXT_BODY
        consentText.visibility = View.VISIBLE
        voiceText.visibility = View.INVISIBLE
        consentPanel.visibility = View.VISIBLE
        refreshChrome()
    }

    fun hideConsent() {
        consentPanel.visibility = View.GONE
        consentText.visibility = View.GONE
        voiceText.visibility = View.VISIBLE
        refreshChrome()
    }

    /** 안내 글을 잠깐 보여 준다. 자리는 늘 비워 두어 글자가 나타나도 상태 카드 크기가 변하지 않는다 */
    fun setVoiceStatus(message: String) {
        voiceText.text = message
        handler.removeCallbacks(clearVoiceStatus)
        handler.postDelayed(clearVoiceStatus, VOICE_STATUS_MS)
        refreshChrome()
    }

    private val clearVoiceStatus = Runnable {
        voiceText.text = ""
        refreshChrome()
    }

    /**
     * 창에 맞춰 배치한다.
     * - 앱 칸(넓은 창, 분할 창): 사진 없이 상태 카드와 버튼만 둔다. 사진은 내비 위 작은 창에서만 본다.
     * - 작은 창: 아이 사진만 보인다. 사진을 끈 사람에게는 상태 띠만 보인다. 누르면 설정으로 들어간다.
     */
    fun applyLayout(floating: Boolean, driveMode: Boolean, photoOff: Boolean, status: ChildStateTracker.Status) {
        this.floating = floating
        this.driveMode = driveMode
        val gap = Ui.dp(context, Ui.GAP_DP)
        val pad = if (floating) gap / 2 else gap
        val slim = floating || driveMode
        // 작은 창에는 아이 사진만 둔다. 사진을 끈 사람에게만 상태 띠를 보여 준다
        card.visibility = if (floating && !photoOff) View.GONE else View.VISIBLE
        root.setPadding(pad, pad, pad, pad)

        // 작은 창은 내용만큼, 그 밖에는 화면을 채운다. 이름 줄이 없으면 위 간격도 뺀다
        body.layoutParams = if (floating) {
            LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP)
        } else {
            LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = if (slim) 0 else pad }
        }

        // 상태 카드는 배치마다 다른 곳에 들어가므로 먼저 떼어 낸다
        (card.parent as? ViewGroup)?.removeView(card)
        body.removeAllViews()
        body.orientation = LinearLayout.VERTICAL
        val cardFace = card.background as GradientDrawable
        cardFace.cornerRadius = Ui.dp(context, Ui.CARD_RADIUS_DP).toFloat()
        Ui.lift(card)

        var photoBottom = 0
        if (floating) {
            val band = Ui.dp(context, FLOAT_BAND_DP)
            photo.addView(card, FrameLayout.LayoutParams(Ui.MATCH, band, Gravity.BOTTOM))
            cardFace.cornerRadius = 0f
            card.elevation = 0f
            if (photoOff) photoBottom = band
            body.addView(photo, LinearLayout.LayoutParams(Ui.MATCH, if (photoOff) band else Ui.dp(context, FLOAT_PHOTO_DP)))
            body.addView(cardPane, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        } else {
            body.addView(card, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
            body.addView(cardPane, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = gap / 4 })
        }
        // 작은 창에서는 상태 띠가 사진을 가리지 않게 사진을 띠 위까지만 놓는다
        photo.setPhotoBottom(photoBottom)

        // 앱 칸에서는 가운데에 크게, 작은 창에서는 아이콘 옆에 글자를 둔다
        card.orientation = if (floating) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        card.gravity = if (floating) Gravity.CENTER_VERTICAL else Gravity.CENTER
        cardTexts.layoutParams = if (floating) {
            LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = gap * 2 / 3 }
        } else {
            LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { topMargin = gap }
        }
        if (floating) card.setPadding(gap * 2 / 3, gap / 4, gap * 2 / 3, gap / 2) else card.setPadding(gap + 8, gap / 2, gap + 8, gap / 2)
        val textGravity = if (floating) Gravity.START else Gravity.CENTER
        titleText.gravity = textGravity
        detailText.gravity = textGravity
        val iconSize = Ui.dp(context, if (floating) FLOAT_ICON_DP else STATE_ICON_WIDE_DP)
        stateIcon.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        (iconSize / 5).let { stateIcon.setPadding(it, it, it, it) }
        titleText.textSize = if (floating) FLOAT_TITLE else Ui.TEXT_TITLE
        detailText.textSize = if (floating) Ui.TEXT_BODY else Ui.TEXT_BUTTON
        refreshChrome()
        photo.place()
        paintedState = null // 배치를 바꿨으니 색도 다시 칠한다
        render(status)
    }

    /**
     * 주행 중과 작은 창에서는 핵심만 남긴다. 공식 지침: "핵심 정보만 노출하고 불필요한 요소는 배제".
     * - 앱 이름 줄과 글레오 안내는 정차 중에만 둔다. 안내는 누르는 곳이 아니어서 다른 글이 있으면 비켜 준다.
     * - 주행 중에는 네·아니요 자리를 비워 둔다. 질문이 떠도 상태 카드가 움직이지 않게 하기 위해서다. 정차 중에는 물을 때만 자리를 쓴다.
     * - 작은 창은 묻거나 알릴 말이 있을 때만 바탕과 글 자리를 붙인다. 평소에는 한 장 카드만 보인다.
     */
    private fun refreshChrome() {
        val slim = floating || driveMode
        val asking = consentPanel.visibility == View.VISIBLE
        headerView.visibility = if (slim) View.GONE else View.VISIBLE
        askHint.visibility = if (asking || slim || voiceText.text.isNotEmpty()) View.GONE else View.VISIBLE
        if (floating) {
            val talking = asking || voiceText.text.isNotEmpty()
            messageSlot.visibility = if (talking) View.VISIBLE else View.GONE
            actionArea.visibility = if (asking) View.VISIBLE else View.GONE
            root.background = if (talking) Ui.rounded(context, Ui.CREAM).apply { setStroke(Ui.dp(context, 1), Ui.CREAM_LINE) } else null
        } else {
            messageSlot.visibility = View.VISIBLE
            actionArea.visibility = if (driveMode || asking) View.VISIBLE else View.GONE
            root.setBackgroundColor(Ui.CREAM)
        }
    }

    /** 상태별 아이콘. 차 화면과 어울리게 이모지 대신 흰 선 아이콘을 쓴다 */
    private fun iconFor(state: State): Int = when (state) {
        State.CHECKING -> R.drawable.ic_state_search
        State.AWAKE -> R.drawable.ic_state_sentiment_satisfied
        State.LOOKING_SIDE -> R.drawable.ic_state_visibility
        State.ASLEEP -> R.drawable.ic_state_bedtime
        State.CRYING -> R.drawable.ic_state_sentiment_very_dissatisfied
        State.CRY_SOUND -> R.drawable.ic_state_hearing
        State.FACE_HIDDEN -> R.drawable.ic_state_visibility_off
    }

    /** 상태별 색. 색 값은 Ui.kt에 있다(상태 카드 색 한 벌, Ui.CardLook) */
    private fun lookOf(state: State) = when (state) {
        State.CHECKING, State.FACE_HIDDEN -> Ui.LOOK_CHECK
        State.AWAKE, State.LOOKING_SIDE -> Ui.LOOK_AWAKE
        State.ASLEEP -> Ui.LOOK_ASLEEP
        State.CRYING -> Ui.LOOK_CRYING
        State.CRY_SOUND -> Ui.LOOK_CRY_SOUND
    }

    companion object {
        private const val ASK_HINT = "\"아기 뭐 해?\"라고 글레오에게 물어보세요"
        private const val LOGO_DP = 72
        private const val LOGO_RADIUS_DP = 18
        private const val VOICE_STATUS_MS = 6_000L
        private const val ACTION_AREA_DP = 88 // 내비 검색 막대와 같은 높이
        private const val PARKED_ROW_DP = 104 // 내비 검색 막대 아래 줄과 같은 높이
        private const val MESSAGE_SLOT_DP = 44
        private const val HINT_ICON_DP = 28
        private const val STATE_ICON_WIDE_DP = 128
        private const val FLOAT_PHOTO_DP = 344 // 작은 창의 사진 높이. 가로 516에 3:2 사진이 꼭 맞는다
        private const val FLOAT_BAND_DP = 92 // 작은 창의 상태 띠 높이
        private const val FLOAT_ICON_DP = 60
        private const val FLOAT_TITLE = 30f
    }
}
