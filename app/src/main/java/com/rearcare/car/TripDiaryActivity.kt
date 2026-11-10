package com.rearcare.car

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.time.LocalDate
import kotlin.concurrent.thread
import kotlin.math.min

/**
 * 아이와 다닌 곳. 나이별 선반으로 보여 준다(TripShelfView).
 * 카드의 내비 버튼을 누르면 그 장소를 차 내비에 연다.
 *
 * 기록은 차 안의 앱 저장소에만 둔다. 사진은 부모가 고른 것만 앱 폴더에 복사한다(TripPhotos).
 * 앱이 찍어서 남기지 않는다.
 * 설정 화면처럼 주행 중에는 쓰지 않는다. 주행으로 바뀌면 화면을 닫는다.
 */
class TripDiaryActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var shelf: TripShelfView
    private lateinit var toast: TextView
    private lateinit var panelLayer: FrameLayout
    private lateinit var overlayScroll: ScrollView
    private lateinit var overlayBody: LinearLayout
    private lateinit var driving: DrivingStateMonitor
    private lateinit var navi: NaviLink

    private var builtWidth = 0
    private var confirmingDelete = false // 창에서 지울지 묻는 중인가
    private var editing: Trip? = null // 창에서 고치는 중인 기록
    private var settingsOpen = false
    private var message = ""
    private var shelfStale = true // 기록이나 사진이 바뀌어 선반을 다시 그려야 하는가
    private var pickingFor: String? = null // 사진 선택기를 연 기록의 번호
    private var photoPage = 0 // 창에서 보고 있는 사진의 순서

    // 기록과 생일. 화면을 다시 그릴 때마다 파일을 읽지 않고, 지우기·생일을 바꾼 뒤에만 다시 읽는다
    private var trips = emptyList<Trip>()
    private var birth: LocalDate? = null

    private fun reload() {
        trips = TripStore.trips(this)
        birth = TripStore.birth(this)
        shelfStale = true
    }

    private val hideToast = Runnable { toast.visibility = View.GONE }

    private fun dp(value: Int) = Ui.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickingFor = savedInstanceState?.getString(STATE_PICKING)
        navi = NaviLink(this).also { it.start() }
        reload()

        buildScreen()
        setContentView(root)
        // 창 가로가 바뀌면(내비 옆 분할 ↔ 넓은 화면) 위에 뜨는 창의 폭을 다시 정한다
        root.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val width = right - left
            if (width > 0 && width != builtWidth) {
                builtWidth = width
                root.post { show() }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (panelOpen()) closePanel() else finish()
            }
        })

        driving = DrivingStateMonitor.closeWhenDriving(this) { closePicker() }
    }

    /** 사진을 고르는 중인가. 고르는 동안에는 이 화면이 멈춰도 닫힌 것이 아니다 */
    val picking: Boolean get() = pickingFor != null

    /**
     * 주행으로 바뀌면 열려 있는 사진 선택기도 닫는다. 선택기는 다른 앱의 화면이라 이 화면과 함께 닫히지 않고,
     * 차도 막아 주지 않아 아이 사진 목록이 주행 중에 남는다(2026-11-09 에뮬레이터에서 확인).
     */
    private fun closePicker() {
        if (pickingFor == null) return
        pickingFor = null
        finishActivity(REQUEST_PHOTO)
    }

    /** 요청 번호로 선택기를 닫아야 해서(closePicker) 번호를 주지 않는 ActivityResult API 대신 이 방식을 쓴다 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PHOTO) return
        val id = pickingFor ?: return
        pickingFor = null
        // 고르지 않고 돌아오면 창을 그대로 둔다
        val uris = if (resultCode == Activity.RESULT_OK && data != null) pickedUris(data) else emptyList()
        if (uris.isNotEmpty()) savePhotos(id, uris)
    }

    /** 여러 장을 고르면 clipData로, 한 장을 고르면 data로 온다 */
    private fun pickedUris(data: Intent): List<Uri> {
        val clip = data.clipData ?: return listOfNotNull(data.data)
        return (0 until clip.itemCount).map { clip.getItemAt(it).uri }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PICKING, pickingFor)
    }

    private fun buildScreen() {
        shelf = TripShelfView(this, onOpen = { trip -> openRecord(trip) }, onNavi = { trip -> openNavi(trip) })

        toast = Ui.text(this, color = Ui.INK).apply {
            val side = dp(TOAST_SIDE_DP)
            setPadding(side, dp(TOAST_TOP_DP), side, dp(TOAST_TOP_DP))
            background = Ui.rounded(context, TOAST_FILL, Ui.BUTTON_RADIUS_DP)
            visibility = View.GONE
        }

        overlayBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(PANEL_BOTTOM_DP))
        }
        overlayScroll = ScrollView(this).apply {
            clipToPadding = false
            addView(overlayBody)
        }
        panelLayer = FrameLayout(this).apply {
            setBackgroundColor(PANEL_SCRIM)
            visibility = View.GONE
            isClickable = true
            setOnClickListener { closePanel() } // 창 바깥을 누르면 닫는다
            addView(overlayScroll, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(PAGE_SIDE_DP) })
        }

        val bar = dp(BAR_TOP_DP)
        val side = dp(PAGE_SIDE_DP)
        val touch = dp(Ui.TOUCH_DP)
        val gap = dp(BAR_GAP_DP)
        root = FrameLayout(this).apply {
            setBackgroundColor(Ui.CREAM)
            addView(shelf, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH).apply { topMargin = bar + touch + gap })
            addView(Ui.roundIcon(context, R.drawable.ic_arrow_back, "닫기") { onBackPressedDispatcher.onBackPressed() }, FrameLayout.LayoutParams(touch, touch, Gravity.TOP or Gravity.START).apply {
                leftMargin = side
                topMargin = bar
            })
            addView(Ui.text(context, "아이와 다닌 곳", Ui.TEXT_SECTION, Ui.CREAM_INK, Ui.SEMIBOLD).apply {
                maxLines = 1
                gravity = Gravity.CENTER_VERTICAL
            }, FrameLayout.LayoutParams(Ui.WRAP, touch, Gravity.TOP or Gravity.START).apply {
                leftMargin = side + touch + gap * 2
                topMargin = bar
            })
            addView(Ui.roundIcon(context, R.drawable.ic_settings, "아이 생일과 묻기 설정") { openSettings() }, FrameLayout.LayoutParams(touch, touch, Gravity.TOP or Gravity.END).apply {
                rightMargin = side
                topMargin = bar
            })
            // 주행 중 수면. 기록한 주행의 잠을 모아 보는 화면이라 다닌 곳에서도 연다. 달 아이콘만 두면 통계인 줄 몰라 글자를 함께 둔다
            addView(sleepReportButton(), FrameLayout.LayoutParams(Ui.WRAP, touch, Gravity.TOP or Gravity.END).apply {
                rightMargin = side + touch + gap
                topMargin = bar
            })
            addView(toast, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(TOAST_BOTTOM_DP) })
            addView(panelLayer, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        }
    }

    private fun sleepReportButton() = Ui.button(this, "주행 중 수면", fill = Ui.CREAM_CARD, textColor = Ui.CREAM_INK, size = Ui.TEXT_BODY, radiusDp = Ui.TOUCH_DP / 2) { openSleepReport() }.apply {
        minWidth = 0
        minimumWidth = 0
        val inset = dp(STATS_BUTTON_SIDE_DP)
        setPadding(inset, 0, inset, 0)
        Ui.lift(this)
        val icon = Ui.icon(context, R.drawable.ic_state_bedtime, Ui.CREAM_INK)?.apply {
            setBounds(0, 0, dp(STATS_ICON_DP), dp(STATS_ICON_DP))
        }
        setCompoundDrawablesRelative(icon, null, null, null)
        compoundDrawablePadding = dp(BUTTON_GAP_DP)
    }

    /** 지금 상태대로 화면을 다시 그린다. 선반은 기록이나 사진이 바뀌었을 때만 다시 그린다 */
    private fun show() {
        if (shelfStale) {
            shelfStale = false
            shelf.render(TripDiary.groups(trips, birth), birth, emptyCard())
        }
        showMessage()
        showPanel()
    }

    private fun openNavi(trip: Trip) {
        if (!navi.open(trip)) {
            message = "내비에서 열지 못했어요."
            showMessage()
        }
    }

    /** 날짜와 탄 시간. 잔 시간은 여기 두지 않고 "주행 중 수면" 화면에서 본다 */
    private fun dateLine(trip: Trip) = buildList {
        add(TripDiary.dateLabel(trip.date))
        if (trip.minutes > 0) add(SleepStats.duration(trip.minutes * 60L))
    }.joinToString(" · ")

    private fun emptyCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val side = dp(EMPTY_SIDE_DP)
        val top = dp(EMPTY_TOP_DP)
        setPadding(side, top, side, top)
        gravity = Gravity.CENTER_VERTICAL
        background = Ui.rounded(context, Ui.ON_ART)
        elevation = dp(EMPTY_ELEVATION_DP).toFloat()
        addView(Ui.text(context, "아직 기록이 없어요", Ui.TEXT_SECTION, Ui.CREAM_INK, Ui.SEMIBOLD))
        addView(Ui.text(context, "차를 탈 때 물으면 \"네\"를 눌러 주세요. 차를 세우면 여기에 남아요.", color = Ui.CREAM_SUBTLE), Ui.below(context, 8))
    }

    private fun showMessage() {
        toast.removeCallbacks(hideToast)
        if (message.isEmpty()) {
            toast.visibility = View.GONE
            return
        }
        toast.text = message
        toast.visibility = View.VISIBLE
        message = "" // 한 번만 보여 준다
        toast.postDelayed(hideToast, TOAST_MS)
    }

    // ---------- 위에 뜨는 창: 설정과 기록 하나(사진 넣기, 지우기) ----------

    private fun panelOpen() = settingsOpen || editing != null

    private fun openSettings() {
        settingsOpen = true
        show()
    }

    private fun openSleepReport() {
        startActivity(Intent(this, SleepReportActivity::class.java))
    }

    private fun openRecord(trip: Trip) {
        editing = trip
        photoPage = 0
        confirmingDelete = false
        show()
    }

    private fun closePanel() {
        settingsOpen = false
        editing = null
        confirmingDelete = false
        show()
    }

    private fun showPanel() {
        overlayBody.removeAllViews()
        panelLayer.visibility = if (panelOpen()) View.VISIBLE else View.GONE
        if (!panelOpen()) return
        overlayScroll.layoutParams = (overlayScroll.layoutParams as FrameLayout.LayoutParams).apply { width = min(dp(PANEL_WIDTH_DP), builtWidth - 2 * dp(PAGE_SIDE_DP)) }
        val record = editing
        if (record != null) {
            overlayBody.addView(recordCard(record))
        } else {
            overlayBody.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.text(context, "다닌 곳 설정", Ui.TEXT_SECTION, Ui.INK, Ui.SEMIBOLD), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
                addView(quietButton("닫기") { closePanel() }, LinearLayout.LayoutParams(dp(SMALL_BUTTON_DP), dp(Ui.TOUCH_DP)))
            })
            overlayBody.addView(birthCard(birth), Ui.below(this, SECTION_GAP_DP))
            overlayBody.addView(askCard(), Ui.below(this, SECTION_GAP_DP))
        }
    }

    private fun card() = Ui.column(this, CARD_INSET_DP, Ui.rounded(this, Ui.CREAM_CARD))

    /** 옅은 버튼(닫기, 아니요) */
    private fun quietButton(label: String, onClick: () -> Unit) =
        Ui.button(this, label, fill = Ui.CREAM, textColor = Ui.CREAM_INK, onClick = onClick)

    /** 글자만 있는 작은 버튼(지우기) */
    private fun linkButton(label: String, onClick: () -> Unit) =
        Ui.button(this, label, fill = Ui.CLEAR, textColor = Ui.CREAM_SUBTLE, size = Ui.TEXT_BODY, onClick = onClick)

    private fun birthCard(birth: LocalDate?) = card().apply {
        addView(Ui.text(context, "아이 생일", Ui.TEXT_SECTION, weight = Ui.SEMIBOLD))
        addView(Ui.text(context, birth?.let { TripDiary.dateLabel(it) } ?: "생일을 넣으면 나이별로 모아 보여 드려요.", color = Ui.CREAM_SUBTLE))
        val date = birth ?: startDate()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val gap = dp(STEPPER_GAP_DP)
            addView(stepper("${date.year}년", { setBirth(date.plusYears(-1)) }, { setBirth(date.plusYears(1)) }), LinearLayout.LayoutParams(0, dp(Ui.SEGMENT_DP), YEAR_WEIGHT))
            addView(stepper("${date.monthValue}월", { setBirth(date.plusMonths(-1)) }, { setBirth(date.plusMonths(1)) }), LinearLayout.LayoutParams(0, dp(Ui.SEGMENT_DP), 1f).apply { leftMargin = gap })
            addView(stepper("${date.dayOfMonth}일", { setBirth(date.plusDays(-1)) }, { setBirth(date.plusDays(1)) }), LinearLayout.LayoutParams(0, dp(Ui.SEGMENT_DP), 1f).apply { leftMargin = gap })
        }
        addView(row, Ui.below(context, SECTION_GAP_DP))
        if (birth != null) {
            addView(
                linkButton("생일 지우기") {
                    TripStore.setBirth(this@TripDiaryActivity, null)
                    reload()
                    show()
                },
                Ui.below(context, 8),
            )
        }
    }

    // 생일을 처음 넣을 때 시작하는 숫자. 권하는 값이 아니고 누르기 전에는 저장하지 않는다
    private fun startDate(): LocalDate = LocalDate.now().minusYears(START_AGE).withDayOfMonth(1)

    private fun setBirth(date: LocalDate) {
        val today = LocalDate.now()
        TripStore.setBirth(this, if (date.isAfter(today)) today else date)
        reload()
        show()
    }

    private fun stepper(value: String, minus: () -> Unit, plus: () -> Unit): View {
        fun step(symbol: String, label: String, action: () -> Unit) =
            Ui.button(this, symbol, fill = Ui.CLEAR, textColor = Ui.CREAM_INK, size = 32f) { action() }.apply {
                contentDescription = "$value $label"
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
            }
        val text = Ui.text(this, value, Ui.TEXT_BUTTON, Ui.CREAM_INK, Ui.SEMIBOLD).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        }
        return Ui.segments(this, listOf(step("−", "줄이기", minus), text, step("+", "늘리기", plus))).apply {
            (text.layoutParams as LinearLayout.LayoutParams).weight = VALUE_WEIGHT
        }
    }

    private fun askCard() = card().apply {
        addView(Ui.text(context, "주행 기록 묻기", Ui.TEXT_SECTION, weight = Ui.SEMIBOLD))
        addView(Ui.text(context, "앱을 켜면 오늘 주행을 기록할지 물어봐요.", color = Ui.CREAM_SUBTLE), Ui.below(context, 4))
        val ask = TripStore.ask(context)
        val cells = listOf(true to "물어보기", false to "묻지 않기").map { (value, label) ->
            Ui.button(context, label) {
                TripStore.setAsk(this@TripDiaryActivity, value)
                show()
            }.also { Ui.paintChoice(it, value == ask) }
        }
        addView(Ui.segments(context, cells), LinearLayout.LayoutParams(Ui.MATCH, dp(Ui.SEGMENT_DP)).apply { topMargin = dp(SECTION_GAP_DP) })
    }

    /** 기록 하나의 창. 사진을 넣고, 옆으로 밀어 보고, 뺀다. 내가 남긴 기록은 지울 수도 있고, 되돌릴 수 없어 한 번 더 묻는다. 예시 기록은 지우지 못한다 */
    private fun recordCard(trip: Trip) = card().apply {
        addView(Ui.text(context, trip.name, Ui.TEXT_SECTION, weight = Ui.SEMIBOLD).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addView(Ui.text(context, dateLine(trip), color = Ui.CREAM_SUBTLE))
        if (confirmingDelete) {
            addView(Ui.text(context, "이 기록을 지울까요? 되돌릴 수 없어요.", Ui.TEXT_BUTTON, Ui.CREAM_INK, Ui.SEMIBOLD), Ui.below(context, SECTION_GAP_DP))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val height = dp(Ui.TOUCH_DP)
                addView(Ui.button(context, "네, 지울게요") {
                    message = if (TripStore.delete(this@TripDiaryActivity, trip.id)) "지웠어요." else "지우지 못했어요."
                    reload()
                    closePanel()
                }, LinearLayout.LayoutParams(0, height, 1f))
                addView(quietButton("아니요") {
                    confirmingDelete = false
                    show()
                }, LinearLayout.LayoutParams(0, height, 1f).apply { leftMargin = dp(BUTTON_GAP_DP) })
            }, Ui.below(context, SECTION_GAP_DP))
            return@apply
        }
        val photos = TripPhotos.count(context, trip.id)
        if (photos > 0) addView(photoPager(trip, photos), LinearLayout.LayoutParams(Ui.MATCH, dp(PREVIEW_DP)).apply { topMargin = dp(SECTION_GAP_DP) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val height = dp(Ui.TOUCH_DP)
            val buttons = buildList {
                if (photos < TripShelf.MAX_PHOTOS) add(Ui.button(context, if (photos > 0) "사진 더 넣기" else "사진 넣기") { pickPhoto(trip) })
                if (photos > 0) add(quietButton("이 사진 빼기") { removeShownPhoto(trip) })
                add(quietButton("닫기") { closePanel() })
            }
            buttons.forEachIndexed { index, button ->
                addView(button, LinearLayout.LayoutParams(0, height, 1f).apply { if (index > 0) leftMargin = dp(BUTTON_GAP_DP) })
            }
        }, Ui.below(context, SECTION_GAP_DP))
        if (!trip.sample) {
            addView(linkButton("이 기록 지우기") {
                confirmingDelete = true
                show()
            }, Ui.below(context, 8))
        }
    }

    /** 넣어 둔 사진을 옆으로 밀어 넘겨 본다. 두 장 이상일 때만 몇 번째인지 쓴다 */
    private fun photoPager(trip: Trip, count: Int) = FrameLayout(this).apply {
        background = Ui.rounded(context, Ui.PHOTO, Ui.BUTTON_RADIUS_DP)
        clipToOutline = true
        photoPage = photoPage.coerceIn(0, count - 1)
        val position = Ui.chip(context, "${photoPage + 1} / $count", POSITION_SIDE_DP)
        addView(ViewPager2(context).apply {
            offscreenPageLimit = 1 // 옆 사진을 미리 읽어 둔다
            adapter = PhotoPages(trip, count)
            setCurrentItem(photoPage, false)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(page: Int) {
                    photoPage = page
                    position.text = "${page + 1} / $count"
                }
            })
        }, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        if (count > 1) {
            addView(position, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(POSITION_MARGIN_DP)
                rightMargin = dp(POSITION_MARGIN_DP)
            })
        }
    }

    /** 사진 한 장이 한 쪽이다. 사진 전체가 보이게 자르지 않고 넣는다 */
    private inner class PhotoPages(private val trip: Trip, private val count: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = count

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(Ui.MATCH, Ui.MATCH)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val view = holder.itemView as ImageView
            view.setImageDrawable(null)
            view.tag = position
            view.contentDescription = "${trip.name}에 넣은 사진 ${position + 1}번째"
            val app = applicationContext
            val width = dp(PANEL_WIDTH_DP)
            val height = dp(PREVIEW_DP)
            thread {
                val bitmap = TripPhotos.load(app, trip.id, width, height, position) ?: return@thread
                view.post { if (view.tag == position) view.setImageBitmap(bitmap) }
            }
        }
    }

    /** 보고 있는 사진만 뺀다. 사진이 남아 있으면 창을 그대로 두어 이어서 볼 수 있게 한다. 빠졌는지 확인한 뒤에 말한다 */
    private fun removeShownPhoto(trip: Trip) {
        val removed = TripPhotos.removeAt(this, trip.id, photoPage)
        shelfStale = true
        if (removed && TripPhotos.has(this, trip.id)) {
            show()
            return
        }
        message = if (removed) "사진을 뺐어요." else "사진을 빼지 못했어요."
        closePanel()
    }

    /** 시스템 사진 선택기를 연다. 부모가 고른 사진만 앱에 오므로 저장소 권한을 묻지 않는다. 이 차의 GET_CONTENT는 빈 대체 화면이 먼저 잡아 쓰지 않는다 */
    private fun pickPhoto(trip: Trip) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            message = "이 차에서는 사진을 고를 수 없어요."
            closePanel()
            return
        }
        pickingFor = trip.id
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
                // 최대 장수를 1로 주면 선택기가 취소로 끝난다. 한 장만 더 넣을 수 있으면 주지 않고 한 장 고르기로 연다
                val room = TripShelf.MAX_PHOTOS - TripPhotos.count(this@TripDiaryActivity, trip.id)
                if (room > 1) putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, room)
            }, REQUEST_PHOTO)
        } catch (e: ActivityNotFoundException) {
            pickingFor = null
            message = "사진을 고르는 화면을 열지 못했어요."
            closePanel()
        }
    }

    /** 고른 사진을 앱 폴더에 넣는다. 다 넣고 다시 읽어 본 뒤에 결과를 말한다 */
    private fun savePhotos(id: String, uris: List<Uri>) {
        val app = applicationContext
        thread {
            val saved = TripPhotos.addAll(app, id, uris)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                message = TripShelf.photoResultLine(saved, uris.size)
                shelfStale = true
                closePanel()
            }
        }
    }

    override fun onDestroy() {
        toast.removeCallbacks(hideToast)
        shelf.release()
        driving.stop()
        navi.release()
        super.onDestroy()
    }

    private companion object {
        const val PAGE_SIDE_DP = 24
        const val BAR_TOP_DP = 20
        const val BAR_GAP_DP = 8
        val TOAST_FILL = Ui.withAlpha(Ui.ON_PRIMARY, 0xF0)
        const val PANEL_SCRIM = 0xB3080C20.toInt()

        const val EMPTY_SIDE_DP = 26
        const val EMPTY_TOP_DP = 16
        const val EMPTY_ELEVATION_DP = 6
        const val BUTTON_GAP_DP = 10
        const val STATS_BUTTON_SIDE_DP = 22
        const val STATS_ICON_DP = 30

        const val TOAST_SIDE_DP = 24
        const val TOAST_TOP_DP = 10
        const val TOAST_BOTTOM_DP = 324
        const val TOAST_MS = 4_000L

        const val PANEL_WIDTH_DP = 793
        const val PANEL_BOTTOM_DP = 24
        const val CARD_INSET_DP = 24
        const val SECTION_GAP_DP = 16
        const val SMALL_BUTTON_DP = 140
        const val STEPPER_GAP_DP = 8
        const val YEAR_WEIGHT = 1.25f
        const val VALUE_WEIGHT = 2.2f
        const val START_AGE = 3L
        const val PREVIEW_DP = 560
        const val POSITION_SIDE_DP = 14
        const val POSITION_MARGIN_DP = 12
        const val STATE_PICKING = "pickingFor"
        const val REQUEST_PHOTO = 41
    }
}
