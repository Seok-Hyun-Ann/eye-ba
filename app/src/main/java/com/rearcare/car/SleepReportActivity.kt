package com.rearcare.car

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 주행 중 수면. 두 가지로 연다.
 * - 모든 주행: 그림 한 장 위에 요약과 잠들 때의 평균을 띄우고, 고른 나이의 날짜마다 잔 시간을 길 위의 막대로 세운다.
 *   아래로 내리면 자는 동안과 깨어 있는 동안이 이어진다. 설정과 다닌 곳에서 연다.
 * - 그날 하나(EXTRA_TRIP): 막대의 기둥을 누르면 연다. 길 위에 그날의 주행과 잔 구간을 그리고, 잠들 때의 값, 자는 동안과 깨어 있는 동안을 그날의 값으로 보인다.
 *   모든 주행 화면과 같은 카드를 쓰고 값만 다르다.
 *
 * 숫자만 보여 준다. "이렇게 하세요" 같은 권하는 말은 없다.
 * "이럴 때 잠들었다"는 기록된 사실은 보여 준다.
 * 글자에는 자료 색을 입히지 않고, 금색은 점과 막대에만 쓴다.
 * 다닌 곳 화면처럼 주행 중에는 쓰지 않는다. 주행으로 바뀌면 닫는다.
 */
class SleepReportActivity : AppCompatActivity() {

    private lateinit var driving: DrivingStateMonitor
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView

    private fun dp(value: Int) = Ui.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        scroll = ScrollView(this).apply {
            setBackgroundColor(SceneView.GROUND_END)
            clipToPadding = false
            addView(body)
        }
        setContentView(scroll)
        show()

        driving = DrivingStateMonitor.closeWhenDriving(this)
    }

    override fun onDestroy() {
        driving.stop()
        super.onDestroy()
    }

    private fun show() {
        val all = TripStore.trips(this)
        val tripId = intent.getStringExtra(EXTRA_TRIP)
        body.removeAllViews()
        if (tripId == null) {
            showAll(all)
        } else {
            val trip = all.firstOrNull { it.id == tripId && it.sleep != null }
            if (trip == null) finish() else showTrip(trip)
        }
    }

    // ---------- 모든 주행 ----------

    private fun showAll(all: List<Trip>) {
        val r = SleepStats.report(all)
        val recorded = all.filter { it.sleep != null }.sortedWith(compareBy({ it.date }, { it.id }))
        val empty = r.trips == 0 || recorded.isEmpty()
        val page = FrameLayout(this).apply {
            addView(SceneView(context, SleepBarsView.ROAD_FROM_BOTTOM_DP, SUN_X, SUN_Y_DP, if (empty) EMPTY_CAR_SCALE else 0f), FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        }
        body.addView(page, LinearLayout.LayoutParams(Ui.MATCH, dp(PAGE_MIN_DP)))
        // 첫 장은 창 높이에 맞춘다. 길이 언제나 화면 아래쪽에 있게 하려는 것이다
        scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val height = maxOf(bottom - top, dp(PAGE_MIN_DP))
            if (page.layoutParams.height != height) {
                page.layoutParams.height = height
                scroll.post { page.requestLayout() }
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val side = dp(HERO_SIDE_DP)
            setPadding(side, dp(BAR_TOP_DP), side, 0)
            addView(topBar("주행 중 수면", if (r.hasSample) "예시 기록이 포함되어 있습니다" else null), LinearLayout.LayoutParams(Ui.MATCH, dp(Ui.PILL_DP)))
        }
        page.addView(column, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        if (empty) {
            column.addView(glassCard().apply {
                addView(Ui.text(context, "주행 중 아이의 수면 시간을 알려드려요", Ui.TEXT_SECTION, Ui.CREAM_INK, Ui.MEDIUM))
                addView(Ui.text(context, "아직 기록된 주행이 없습니다", color = Ui.CREAM_INK), Ui.below(context, LINE_GAP_DP))
                addView(Ui.text(context, "주행을 기록하면 아이의 수면 시간과 그때의 속도, 온도, 바람, 조명을 알려드려요.", color = Ui.CREAM_SUBTLE), Ui.below(context, 4))
            }, Ui.below(this, HERO_GAP_DP))
            return
        }
        column.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(summaryCard(r), LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
            addView(envCard(r, "수면 시작 시 평균 환경", "${r.onsets.count}번 잠든 때의 평균입니다"), LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { leftMargin = dp(HERO_CARD_GAP_DP) })
        }, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = dp(HERO_GAP_DP) })
        roadChart(page, column, recorded)
        body.addView(lowerPage(r, null, needEnough = true))
    }

    /** 장면 아래로 이어지는 칸. 자는 동안과 깨어 있는 동안을 둔다 */
    private fun lowerPage(r: SleepStats.Report, hint: String?, needEnough: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val side = dp(PAGE_SIDE_DP)
        setPadding(side, 0, side, dp(PAGE_SIDE_DP))
        addView(compareCard(r, hint, needEnough), Ui.below(context, 0))
        speedNote(r)?.let { addView(it, Ui.below(context, Ui.GAP_DP).apply { leftMargin = dp(TITLE_INDENT_DP) }) }
    }

    private fun topBar(title: String, note: String?) = Ui.backBar(this, title, { finish() }, listOfNotNull(note?.let { glassLabel(it) }))

    private fun glassLabel(text: String, size: Float = Ui.TEXT_BUTTON) = Ui.text(this, text, size, Ui.CREAM_INK, Ui.SEMIBOLD).apply {
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        val side = dp(LABEL_SIDE_DP)
        setPadding(side, 0, side, 0)
        background = Ui.glass(context)
    }

    private fun glassCard() = Ui.column(this, GLASS_INSET_DP, Ui.glass(this))

    /** 주행 중 평균 수면을 크게 쓰고, 나머지 요약을 아래에 둔다. 평균을 낼 잠이 없으면 큰 글자는 뺀다 */
    private fun summaryCard(r: SleepStats.Report) = glassCard().apply {
        gravity = Gravity.CENTER_VERTICAL
        r.avgSleepSec?.let { avg ->
            addView(Ui.text(context, "주행 중 평균", AVERAGE_LABEL_SP, Ui.CREAM_INK, Ui.MEDIUM))
            addView(bigSleep(SleepStats.duration(avg)), Ui.below(context, 4))
        }
        addView(Ui.text(context, "현재까지 ${r.trips}일 주행 중 ${r.sleptTrips}일은 아이가 잠든 것 같습니다", color = Ui.CREAM_SUBTLE), Ui.below(context, SECTION_GAP_DP))
        if (r.sleptTrips > 0) {
            addView(Ui.text(context, listOfNotNull(
                "총 ${SleepStats.duration(r.sleptSec)} 수면",
                r.avgFirstSleepSec?.let { "출발 후 평균 ${SleepStats.duration(it)} 뒤 수면 시작" },
            ).joinToString(" · "), color = Ui.CREAM_SUBTLE), Ui.below(context, 2))
        }
    }

    /** "58분 수면"처럼 잔 시간을 크게 쓴다 */
    private fun bigSleep(text: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(Ui.text(context, text, if (text.length <= SHORT_TIME_LENGTH) BIG_SP else BIG_LONG_SP, Ui.CREAM_INK, Ui.SEMIBOLD).apply { includeFontPadding = false })
        addView(Ui.text(context, "수면", SLEEP_WORD_SP, Ui.CREAM_INK, Ui.MEDIUM), LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = dp(BIG_WORD_GAP_DP) })
    }

    /** 잠든 때의 속도, 설정 온도, 바람, 실내 온도를 그림과 함께 네 칸으로 두고 조명은 글로 적는다 */
    private fun envCard(r: SleepStats.Report, heading: String, hint: String?) = glassCard().apply {
        addView(Ui.text(context, heading, Ui.TEXT_SECTION, Ui.CREAM_INK, Ui.MEDIUM))
        val o = r.onsets
        if (o.count == 0) {
            addView(Ui.text(context, "잠들 때의 환경은 앞으로 기록하는 주행부터 알려드려요.", color = Ui.CREAM_INK), Ui.below(context, LINE_GAP_DP))
            return@apply
        }
        hint?.let { addView(Ui.text(context, it, color = Ui.CREAM_SUBTLE)) }
        addView(envRow(R.drawable.ic_set_speed to ("속도" to SleepStats.kph(o.kph)), R.drawable.ic_set_dial to ("설정 온도" to SleepStats.celsius(o.setC))), Ui.below(context, ENV_GAP_DP))
        addView(envRow(R.drawable.ic_set_fan to ("바람" to SleepStats.fan(o.fan)), R.drawable.ic_set_cabin to ("실내 온도" to SleepStats.celsius(o.cabinC))), Ui.below(context, ENV_GAP_DP))
        val light = lightLines(r)
        if (light.isNotEmpty()) {
            addView(Ui.text(context, light.joinToString("\n"), color = Ui.CREAM_SUBTLE), Ui.below(context, ENV_GAP_DP))
        }
    }

    private fun envRow(a: Pair<Int, Pair<String, String>>, b: Pair<Int, Pair<String, String>>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(envTile(a.first, a.second.first, a.second.second), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        addView(envTile(b.first, b.second.first, b.second.second), LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = dp(ENV_GAP_DP) })
    }

    private fun envTile(iconRes: Int, label: String, text: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = dp(ENV_PAD_DP)
        setPadding(pad, dp(ENV_PAD_DP - 2), pad, dp(ENV_PAD_DP - 2))
        background = Ui.rounded(context, ENV_FILL, TILE_RADIUS_DP)
        addView(Ui.picture(context, iconRes), LinearLayout.LayoutParams(dp(ENV_ICON_DP), dp(ENV_ICON_DP)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(context, label, color = Ui.CREAM_SUBTLE))
            addView(value(text))
        }, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = dp(14) })
    }

    /**
     * 나이 표지판과 길 위의 막대. 표지판을 누르면 그 나이의 기록만 보인다. 처음에는 가장 최근 나이의 끝을 보여 준다.
     * 막대를 누르면 그날 하나 화면을 연다.
     */
    private fun roadChart(page: FrameLayout, column: LinearLayout, recorded: List<Trip>) {
        val birth = TripStore.birth(this)
        val byTab = barsByTab(recorded, birth)
        val chart = SleepBarsView(this).apply {
            setScale(byTab.values.flatten())
            onPick = { id -> startActivity(Intent(this@SleepReportActivity, SleepReportActivity::class.java).putExtra(EXTRA_TRIP, id)) }
        }
        val scroller = Ui.sideScroll(this).apply {
            isFillViewport = true
            addView(chart, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP))
        }
        val range = glassLabel("")
        val months = SleepMonthStepper(this, chart, scroller).apply { visibility = View.GONE }
        column.addView(chartHeader(range, months), LinearLayout.LayoutParams(Ui.MATCH, dp(Ui.PILL_DP)).apply { topMargin = dp(LEGEND_TOP_DP) })

        val signs = LinkedHashMap<SleepChart.Tab, Button>()
        fun select(key: Int) {
            chart.show(byTab[key].orEmpty())
            signs.forEach { (tab, button) -> paintSign(button, tab.key == key, tab.hasRecords) }
            showRange(range, birth, key)
            chart.afterLayout = {
                scroller.post {
                    scroller.scrollTo(chart.width, 0)
                    // 한 화면에 다 들어가면 넘길 달이 없으므로 그 나이의 기간을 보여 준다
                    val overflow = chart.width > scroller.width
                    months.visibility = if (overflow) View.VISIBLE else View.GONE
                    if (overflow) {
                        range.visibility = View.GONE
                        months.reset()
                    } else {
                        chart.highlighted = null
                    }
                }
            }
        }
        val tabs = SleepChart.tabs(recorded.map { it.date }, birth)
        tabs.forEach { tab ->
            signs[tab] = Ui.button(this, tab.label) { select(tab.key) }
        }
        column.addView(signStrip(signs.values), LinearLayout.LayoutParams(Ui.MATCH, dp(Ui.TOUCH_DP)).apply {
            topMargin = dp(SIGN_TOP_DP)
            leftMargin = dp(SleepBarsView.AXIS_WIDTH_DP - HERO_SIDE_DP)
        })
        column.addView(Space(this), LinearLayout.LayoutParams(Ui.MATCH, dp(SleepBarsView.HEIGHT_DP)))

        page.addView(chartFrame(chart, scroller), FrameLayout.LayoutParams(Ui.MATCH, dp(SleepBarsView.HEIGHT_DP), Gravity.BOTTOM))
        tabs.lastOrNull { it.hasRecords }?.let { select(it.key) }
    }

    /** 나이 표지판마다 그 나이 날짜들의 막대 */
    private fun barsByTab(recorded: List<Trip>, birth: java.time.LocalDate?) =
        recorded.groupBy { SleepChart.keyOf(it.date, birth) }.mapValues { (_, trips) ->
            trips.map { SleepBarsView.Bar(it.id, it.date, SleepStats.rideSec(it), it.sleep!!.sleptSec.toLong()) }
        }

    /** 그래프 머리. 왼쪽에 제목과 범례, 오른쪽 끝에 그 나이의 기간이나 달 넘기기를 둔다 */
    private fun chartHeader(range: TextView, months: SleepMonthStepper) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val side = dp(LABEL_SIDE_DP)
            setPadding(side, 0, side, 0)
            background = Ui.glass(context)
            addView(Ui.text(context, "날짜별 수면 시간", Ui.TEXT_SECTION, Ui.CREAM_INK, Ui.SEMIBOLD))
            addView(legend("수면 시간", "깨어 있거나 확인하지 못한 시간"), LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = dp(LEGEND_GAP_DP) })
        }, LinearLayout.LayoutParams(Ui.WRAP, Ui.MATCH))
        addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        addView(range, LinearLayout.LayoutParams(Ui.WRAP, Ui.MATCH))
        addView(months, LinearLayout.LayoutParams(Ui.WRAP, Ui.MATCH))
    }

    /** 나이 표지판 줄. 옆으로 민다 */
    private fun signStrip(signs: Collection<Button>) = Ui.sideScroll(this).apply {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            signs.forEachIndexed { i, button ->
                addView(button, LinearLayout.LayoutParams(dp(SIGN_WIDTH_DP), dp(Ui.TOUCH_DP)).apply { if (i > 0) leftMargin = dp(SIGN_GAP_DP) })
            }
        })
    }

    /** 왼쪽에 밀어도 움직이지 않는 시간 눈금, 그 오른쪽에 옆으로 미는 막대 */
    private fun chartFrame(chart: SleepBarsView, scroller: HorizontalScrollView) = FrameLayout(this).apply {
        addView(SleepBarsAxis(context, chart), FrameLayout.LayoutParams(dp(SleepBarsView.AXIS_WIDTH_DP), Ui.MATCH))
        addView(scroller, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH).apply { leftMargin = dp(SleepBarsView.AXIS_WIDTH_DP) })
    }

    private fun showRange(view: TextView, birth: java.time.LocalDate?, key: Int) {
        val known = birth != null && key >= 0
        view.visibility = if (known) View.VISIBLE else View.INVISIBLE
        if (known) view.text = TripDiary.ageRange(birth!!, key)
    }

    /** 표지판. 고른 나이는 진하게, 기록이 없는 나이는 흐리게 하고 누르지 못하게 한다 */
    private fun paintSign(button: Button, selected: Boolean, hasRecords: Boolean) {
        button.isEnabled = hasRecords
        button.alpha = if (hasRecords) 1f else SIGN_DIM
        if (selected) {
            Ui.paint(button, Ui.CREAM_PRIMARY, android.graphics.Color.WHITE, SIGN_RADIUS_DP)
        } else {
            button.background = Ui.pressable(this, SceneView.SIGN_FILL, SIGN_RADIUS_DP, SceneView.SIGN_EDGE)
            button.setTextColor(Ui.CREAM_PRIMARY)
        }
    }

    // ---------- 그날 하나 ----------

    private fun showTrip(trip: Trip) {
        val s = trip.sleep!!
        val r = SleepStats.report(listOf(trip))
        val hint = when (r.onsets.count) {
            0 -> null
            1 -> "이 주행에서 잠든 때의 환경입니다"
            else -> "이 주행에서 ${r.onsets.count}번 잠든 때의 평균입니다"
        }
        val side = dp(HERO_SIDE_DP)
        fun sides(params: LinearLayout.LayoutParams) = params.apply {
            leftMargin = side
            rightMargin = side
        }
        val page = FrameLayout(this).apply {
            addView(SceneView(context, SleepDayRoadView.ROAD_FROM_BOTTOM_DP, DAY_SUN_X, DAY_SUN_Y_DP, 0f), FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(BAR_TOP_DP), 0, 0)
                addView(topBar("${trip.date.monthValue}월 ${trip.date.dayOfMonth}일 ${trip.name}", if (trip.sample) "예시 기록이에요" else null), sides(LinearLayout.LayoutParams(Ui.MATCH, dp(Ui.PILL_DP))))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(daySummaryCard(trip, s), LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
                    addView(envCard(r, "수면 시작 시 환경", hint), LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { leftMargin = dp(HERO_CARD_GAP_DP) })
                }, sides(LinearLayout.LayoutParams(Ui.MATCH, dp(DAY_CARDS_DP))).apply { topMargin = dp(HERO_GAP_DP) })
                addView(glassLabel("수면 시간대", Ui.TEXT_SECTION), LinearLayout.LayoutParams(Ui.WRAP, dp(Ui.PILL_DP)).apply {
                    leftMargin = side
                    topMargin = dp(DAY_TITLE_TOP_DP)
                })
                addView(SleepDayRoadView(context).apply { show(trip) }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
            }, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        }
        body.addView(page, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        body.addView(lowerPage(r, "이 주행 기준", needEnough = false))
    }

    /** 그날의 주행과 잔 시간. 잠든 모습을 못 봤으면 큰 글자 대신 그렇게 적는다 */
    private fun daySummaryCard(trip: Trip, s: SleepSummary) = glassCard().apply {
        addView(Ui.text(context, "${trip.date.year}년 · ${SleepStats.rideLine(trip)}", color = Ui.CREAM_SUBTLE))
        if (s.sleptSec <= 0) {
            addView(Ui.text(context, "이 주행에서는 잠든 모습을 확인하지 못했습니다.", Ui.TEXT_SECTION, Ui.CREAM_INK, Ui.MEDIUM), Ui.below(context, DAY_SUMMARY_GAP_DP))
            return@apply
        }
        addView(Ui.text(context, "총 약", AVERAGE_LABEL_SP, Ui.CREAM_INK, Ui.MEDIUM), Ui.below(context, DAY_SUMMARY_GAP_DP))
        addView(bigSleep(SleepStats.duration(s.sleptSec.toLong())), Ui.below(context, 4))
        if (s.spans.isEmpty()) addView(Ui.text(context, "이 기록에는 수면 시각이 없습니다", color = Ui.CREAM_SUBTLE), Ui.below(context, 8))
    }

    private fun lightLines(r: SleepStats.Report) = listOfNotNull(
        SleepStats.onsetLightLine(r.onsets)?.let { "실내등·독서등: $it" },
        r.onsets.ambient?.let { "무드등 밝기: 평균 ${SleepStats.percent(it)}" },
    )

    /** 자는 동안과 깨어 있는 동안을 나란히 둔다. 판정이나 권하는 말은 넣지 않는다 */
    private fun compareCard(r: SleepStats.Report, hint: String?, needEnough: Boolean) = card().apply {
        addView(Ui.text(context, "자는 동안과 깨어 있는 동안", Ui.TEXT_SECTION, Ui.CREAM_INK, Ui.MEDIUM))
        hint?.let { addView(Ui.text(context, it, color = Ui.CREAM_SUBTLE), Ui.below(context, 4)) }
        if (needEnough && !r.enough) {
            addView(Ui.text(context, "아직 비교할 만큼 기록이 쌓이지 않았습니다.", color = Ui.CREAM_INK), Ui.below(context, LINE_GAP_DP))
            addView(
                Ui.text(context, "현재까지 기록된 시간은 자는 동안 ${SleepStats.duration(r.asleepSec)}, 깨어 있는 동안 ${SleepStats.duration(r.awakeSec)}입니다. 각각 ${SleepStats.ENOUGH_SEC / 60}분이 넘으면 알려드려요.", color = Ui.CREAM_SUBTLE),
                Ui.below(context, LINE_GAP_DP),
            )
            return@apply
        }
        addView(row("", key(SLEEP_DOT, "자는 동안", SleepStats.duration(r.asleepSec)), key(REST_DOT, "깨어 있는 동안", SleepStats.duration(r.awakeSec))), Ui.below(context, SECTION_GAP_DP))
        listOf(
            "속도" to (SleepStats.kph(r.kph.asleep) to SleepStats.kph(r.kph.awake)),
            "설정 온도" to (SleepStats.celsius(r.setC.asleep) to SleepStats.celsius(r.setC.awake)),
            "바람" to (SleepStats.fan(r.fan.asleep) to SleepStats.fan(r.fan.awake)),
            "실내 온도" to (SleepStats.celsius(r.cabinC.asleep) to SleepStats.celsius(r.cabinC.awake)),
            "실내등·독서등 켜짐" to (SleepStats.percent(r.light.asleep) to SleepStats.percent(r.light.awake)),
            "무드등 밝기" to (SleepStats.percent(r.ambient.asleep) to SleepStats.percent(r.ambient.awake)),
        ).forEach { (label, values) ->
            addView(divider(), LinearLayout.LayoutParams(Ui.MATCH, 1))
            addView(row(label, value(values.first), value(values.second)))
        }
    }

    private fun row(label: String, asleep: View, awake: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(ROW_DP)
        addView(Ui.text(context, label, color = Ui.CREAM_INK), LinearLayout.LayoutParams(0, Ui.WRAP, LABEL_WEIGHT))
        addView(asleep, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        addView(awake, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
    }

    /** 칸 머리: 색 점, 이름, 모인 시간. 색은 점에만 쓰고 글자는 기본 글자색이다 */
    private fun key(color: Int, name: String, time: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(View(context).apply { background = Ui.rounded(context, color, DOT_DP / 2) }, LinearLayout.LayoutParams(dp(DOT_DP), dp(DOT_DP)))
            addView(Ui.text(context, name, color = Ui.CREAM_INK, weight = Ui.MEDIUM), LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = dp(DOT_GAP_DP) })
        })
        addView(Ui.text(context, time, color = Ui.CREAM_SUBTLE))
    }

    private fun legend(sleep: String, rest: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        listOf(SLEEP_DOT to sleep, REST_DOT to rest).forEachIndexed { i, (color, name) ->
            addView(View(context).apply { background = Ui.rounded(context, color, SWATCH_RADIUS_DP) }, LinearLayout.LayoutParams(dp(DOT_DP), dp(DOT_DP)).apply { if (i > 0) leftMargin = dp(LEGEND_GAP_DP) })
            addView(Ui.text(context, name, color = Ui.CREAM_SUBTLE), LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = dp(DOT_GAP_DP) })
        }
    }

    private fun value(text: String) = Ui.text(this, text, Ui.TEXT_SECTION, if (text == SleepStats.UNKNOWN) Ui.CREAM_SUBTLE else Ui.CREAM_INK, Ui.SEMIBOLD)

    private fun divider() = View(this).apply { setBackgroundColor(Ui.CREAM_LINE) }

    private fun card() = Ui.column(this, CARD_INSET_DP, Ui.glass(this))

    /** 속도를 읽지 못했을 때만 까닭을 적는다 */
    private fun speedNote(r: SleepStats.Report) =
        if (r.kph.asleep == null && r.kph.awake == null) Ui.text(this, "속도는 속도 정보를 허용해야 알려드려요.", color = Ui.CREAM_INK) else null

    companion object {
        /** 그날 하나 화면으로 열 때 넣는 다닌 곳 기록의 번호 */
        const val EXTRA_TRIP = "trip"

        private const val PAGE_SIDE_DP = 24
        private const val BAR_TOP_DP = 20
        private const val TITLE_INDENT_DP = 8
        private const val CARD_INSET_DP = 28
        private const val LINE_GAP_DP = 12
        private const val SECTION_GAP_DP = 16
        private const val ROW_DP = 72
        private const val DOT_DP = 14
        private const val DOT_GAP_DP = 10
        private const val LEGEND_GAP_DP = 26
        private const val SWATCH_RADIUS_DP = 4
        private const val TILE_RADIUS_DP = 18
        private const val LABEL_WEIGHT = 0.9f
        private const val SLEEP_DOT = Ui.CHART_SLEEP
        private const val REST_DOT = Ui.REST
        private const val PAGE_MIN_DP = 1180 // 첫 장에 요약, 표지판, 막대가 모두 들어가는 높이
        private const val SUN_X = 0.656f
        private const val DAY_SUN_X = 0.375f
        private const val DAY_SUN_Y_DP = 590
        private const val DAY_CARDS_DP = 410 // 조명 두 줄까지 들어가는 높이
        private const val DAY_TITLE_TOP_DP = 18
        private const val DAY_SUMMARY_GAP_DP = 22
        private const val SUN_Y_DP = 640
        private const val EMPTY_CAR_SCALE = 3f
        private const val HERO_SIDE_DP = 26
        private const val HERO_GAP_DP = 20
        private const val HERO_CARD_GAP_DP = 28
        private const val LABEL_SIDE_DP = 24
        private const val GLASS_INSET_DP = 24
        private const val AVERAGE_LABEL_SP = 30f
        private const val SHORT_TIME_LENGTH = 4 // "58분"처럼 짧으면 더 크게 쓴다
        private const val BIG_SP = 128f
        private const val BIG_LONG_SP = 84f
        private const val SLEEP_WORD_SP = 44f
        private const val BIG_WORD_GAP_DP = 20
        private const val ENV_GAP_DP = 12
        private const val ENV_PAD_DP = 12
        private const val ENV_ICON_DP = 64
        private val ENV_FILL = Ui.withAlpha(Ui.CREAM, 0xD1)
        private const val LEGEND_TOP_DP = 14
        private const val SIGN_TOP_DP = 8
        private const val SIGN_WIDTH_DP = 112
        private const val SIGN_GAP_DP = 10
        private const val SIGN_RADIUS_DP = 16
        private const val SIGN_DIM = 0.42f
    }
}
