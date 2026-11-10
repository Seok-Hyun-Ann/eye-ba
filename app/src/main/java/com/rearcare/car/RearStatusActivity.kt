package com.rearcare.car

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.rearcare.car.ChildStateTracker.State
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.time.LocalDate

/**
 * 아이 상태 화면. 입력, 분석, 상태 추적, 차 실내 제안, 다닌 곳 기록을 하나로 잇는다.
 *
 *   뒷좌석 입력(RearFeedSource) → 분석(RearAnalyzer: 얼굴+소리) → 상태 추적(ChildStateTracker)
 *   → 상태 카드 + 차의 음성 비서로 물으면 대답(GleoReceiver, StatusWords, VoiceIO)
 *   앱은 먼저 말하지 않는다. 주행 중에는 내비 음성이 먼저다
 *   예외는 뒷창문이 열릴 때의 말 없는 짧은 알림음 하나다
 *   정차/주행(DrivingStateMonitor) → 설정 줄과 실내등·독서등 질문은 차가 서 있을 때(P단이고 플랫폼이 주행으로 보지 않을 때)만 띄운다
 *
 * 이 화면은 주행 중에도 쓰는 화면이라 DO(distractionOptimized)로 선언했다(매니페스트).
 * 그래서 주행 중에는 설정 줄을 숨기고 상태 카드(고정 아이콘 + 짧은 글)나 작은 창의 사진만 남긴다. 확인용 숫자는 로그에만 남긴다.
 * 말로 물을 때 누를 버튼은 두지 않는다.
 * 움직이는 영상은 내보내지 않는다. 내비 위 작은 창에만 아이 사진 한 장을 두고, 고른 간격(처음 설치 때 5분, 끌 수 있음)마다 바꾼다(DrivePhotoPolicy).
 */
class RearStatusActivity : AppCompatActivity() {

    // ---------- 화면 ----------
    // 뷰를 만들고 놓는 일은 StatusScreen이 한다. 이 클래스는 무엇을 보여 줄지 정한다
    private lateinit var screen: StatusScreen

    // ---------- 부품 ----------
    private lateinit var driving: DrivingStateMonitor
    private lateinit var voice: VoiceIO
    private var feed: RearFeedSource? = null
    private val analysisThread = Executors.newSingleThreadExecutor()
    private var analyzer: RearAnalyzer? = null // analysisThread 안에서만 쓴다
    private val analyzing = AtomicBoolean(false)

    private var tracker = ChildStateTracker(DemoSetup.trackerConfig)

    // 잠들면 차 실내를 맞추자고 제안하는 부분
    private lateinit var cabin: CabinController
    private var advisor = ComfortAdvisor()
    private var proposing = false // 지금 실내 제안을 묻는 중이다. 질문이 겹치지 않게 한다
    private var pendingConsent: ((Boolean?) -> Unit)? = null // 화면에 띄운 질문의 대답을 받을 곳
    private var restoreOnWake = false // 잠들어서 바꾼 것이 있어, 깨면 불을 되돌릴지 물어야 한다
    private var restoreWhenParked = false // 주행 중에 깨서 실내등·독서등을 못 켰다. 차를 세우면 묻는다

    // 뒷창문이 열리면 알리고 잠글지 묻는 부분
    private lateinit var alertTone: AlertTone
    private val windowWatch = RearWindowWatch()
    private var windowOfferUp = false // 뒷창문을 잠글지 묻는 중이다

    // 아이와 다닌 곳과 주행 중 수면. 차를 탈 때 묻는 질문은 이 화면이, 대답 뒤의 기록은 TripSession이 맡는다
    private lateinit var navi: NaviLink
    private lateinit var speed: CarSpeed
    private lateinit var tripSession: TripSession
    private var tripOfferUp = false // 기록할지 묻는 중이다
    private var gearMoving = false // 기어가 P가 아니다. parked와 달리 주행 규제 값을 기다리지 않는다
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // 내비 위 작은 창의 사진. 정차·주행 모두 부모가 고른 간격마다 한 장씩 바꾼다
    private var drivePhotoMinutes = DrivePhotoStore.DEFAULT_MINUTES
    private var drivePhoto: DrivePhotoPolicy? = null
    private var driveMode = false

    // 내비 위 작은 창. 분할하면 내비 가로가 절반이 되어 지도가 가리므로, 내비를 넓게 두고 이 화면만 작게 겹친다
    private var floating = false
    private var openingSettings = false
    private val overlayManager by lazy { applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager }

    @Volatile private var latestFrame: Bitmap? = null
    private var parked = false // 차가 서 있는가: P단이고 플랫폼이 주행으로 보지 않는다. 모르면 false
    private var sleepOfferUp = false // 잠들어서 묻는 제안이 화면에 떠 있는가

    // 분석을 마친 사진과 얼굴 위치. 얼굴을 찾은 바로 그 사진을 작은 창에 보여 준다
    private var lastFrame: Bitmap? = null
    private var lastFocus: PhotoFit.Focus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = StatusScreen(
            this,
            mainHandler,
            onAnswer = { yes -> answerConsent(yes) },
            onOpenDiary = {
                openingSettings = true
                startActivity(Intent(this, TripDiaryActivity::class.java))
            },
            onOpenSettings = {
                openingSettings = true
                startActivity(Intent(this, SleepSettingsActivity::class.java))
            },
            onRestartDemo = { restartDemo() },
            onPhotoTap = { openSettingsFromPhoto() },
        )
        setContentView(screen.root)
        applyLayout()

        voice = VoiceIO(
            this,
            onStatus = { message -> runOnUiThread { screen.setVoiceStatus(message) } },
        )
        voice.start()

        driving = DrivingStateMonitor(this) { state -> applyDriving(state) }
        driving.start()

        alertTone = AlertTone(this)
        cabin = CabinController(this)
        cabin.onRearWindowMoved = ::onRearWindowMoved
        cabin.start()
        navi = NaviLink(this).also { it.start() }
        speed = CarSpeed(this).also { it.start() }
        tripSession = TripSession(this, navi, cabin, speed, mainHandler, tracker = { tracker }, moving = { gearMoving }, onSaved = ::onTripSaved)

        // 차의 음성 비서(Gleo)로 물어보면 여기서 답한다
        GleoBridge.answer = {
            val status = tracker.status
            val spoken = StatusWords.answer(status, SystemClock.elapsedRealtime(), DemoSetup.windowSide)
            voice.speak(spoken)
            GleoBridge.Answer(StatusWords.gleoMessage(status), spoken)
        }

        application.registerActivityLifecycleCallbacks(screenWatcher)
        demoRestart = { restartDemo() }
        DemoSetup.installDemoFiles(this)
        startFeed()
    }

    override fun onStart() {
        super.onStart()
        openingSettings = false
        // 앱 화면은 겹쳐 그리기를 못 쓸 때만 쓴다. 쓸 수 있으면 내비를 앞으로 가져와 작은 창만 남긴다
        if (Settings.canDrawOverlays(this)) {
            if (!floating) screen.root.visibility = View.INVISIBLE
            return
        }
        hideFloating()
    }

    /** 차의 지도 앱을 앞으로 가져온다. 열지 못하면 false */
    private fun openNavi(): Boolean = try {
        startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "지도 앱을 열지 못해 앱 화면으로 보여 준다", e)
        false
    }

    // 내비를 눌러 앱 화면이 내려가면 같은 화면을 작은 창으로 겹쳐 띄운다. 설정 화면으로 갈 때는 띄우지 않는다
    override fun onStop() {
        super.onStop()
        mainHandler.removeCallbacks(bringNavi)
        callingNavi = false
        if (!openingSettings && !isFinishing && !isChangingConfigurations) showFloating()
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면에서 돌아오면 바뀐 값을 보여 준다
        refreshParkedValues()
        if (Settings.canDrawOverlays(this)) callNavi()
    }

    /**
     * 이 차 화면은 여러 칸의 앱이 함께 실행 중이라, 내비가 앞에 와도 이 화면에는 onPause가 오지 않는다.
     * 그래서 설정에서 돌아와 이 화면이 다시 맨 앞이 될 때 onResume도 오지 않아, 내비 대신 빈 앱 창이 남았다(2026-11-10 시험).
     * 맨 앞이 될 때도 내비를 부른다. 이미 부르는 중이면(앱을 막 켰을 때 차 화면이 앱을 한 번 더 올린다) 그 시도에 맡긴다.
     */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (isTopResumedActivity && !callingNavi && Settings.canDrawOverlays(this)) callNavi()
    }

    private fun callNavi() {
        naviTries = 0
        callingNavi = true
        mainHandler.removeCallbacks(bringNavi)
        mainHandler.postDelayed(bringNavi, NAVI_RETRY_MS)
    }

    // 앱을 막 켜면 내비를 불러도 차 화면이 0.3초쯤 뒤에 앱을 다시 위로 올린다(2026-11-09 시험). 앱이 내려갈 때까지 몇 번 더 부른다
    private var naviTries = 0
    private var callingNavi = false // 내비를 부르는 중이다(bringNavi가 걸려 있다)
    private val bringNavi = object : Runnable {
        override fun run() {
            callingNavi = false
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            if (!openNavi()) {
                screen.root.visibility = View.VISIBLE
                return
            }
            // 차 화면의 상태에 따라 내비가 앞에 와도 앱이 멈추지 않아 onStop이 오지 않을 때가 있다. 그래도 작은 창은 떠야 한다
            showFloating()
            if (++naviTries < NAVI_TRIES) {
                callingNavi = true
                mainHandler.postDelayed(this, NAVI_RETRY_MS)
            }
        }
    }

    private fun refreshParkedValues() {
        drivePhotoMinutes = DrivePhotoStore.minutes(this)
        screen.showParkedValues(
            range = SleepRangeStore.describe(SleepRangeStore.get(this)?.targetC),
            photoInterval = DrivePhotoStore.describe(drivePhotoMinutes),
            diary = if (tripSession.recorder.waitingToSave) "기록 중" else TripDiary.placeCount(TripStore.trips(this)).let { if (it == 0) "없음" else "${it}곳" },
        )
    }

    // ---------- 1. 입력 → 분석 ----------

    private fun startFeed() {
        feed?.stop()
        tracker = ChildStateTracker(DemoSetup.trackerConfig)
        // "거절 뒤 30분 묻지 않기" 기억도 지운다. 안 지우면 다시 시작해도 제안이 나오지 않는다
        advisor = ComfortAdvisor()
        restoreOnWake = false
        restoreWhenParked = false
        // 떠 있던 질문을 모두 내린다. 새 시연이 앞 시연의 질문에 막히지 않게 한다
        tripOfferUp = false
        sleepOfferUp = false
        proposing = false
        windowOfferUp = false
        dismissConsent()
        tripSession.reset()
        resumeTodayIfSaidYes()
        refreshParkedValues() // 다닌 곳 칸의 "기록 중"을 지운다
        lastFrame = null
        lastFocus = null
        screen.render(tracker.status)
        // 입력은 DemoSetup이 정한다(지금은 시연 대본). 카메라와 연결할 때 바꿀 값은 모두 DemoSetup에 있다
        feed = DemoSetup.feed(this).also { it.start(feedListener) }
    }

    private val feedListener = object : RearFeedSource.Listener {
        override fun onFrame(frame: Bitmap, timeMs: Long) {
            latestFrame = frame // 분석에 쓴다. 화면에 넣는 사진은 고른 간격마다 updatePhoto가 정한다
        }

        override fun onAudio(samples: FloatArray, sampleRate: Int, timeMs: Long) {
            // 분석이 1초보다 오래 걸리면 밀린 것을 쌓지 않고 이번 것을 건너뛴다(늘 최신 상태를 본다)
            if (!analyzing.compareAndSet(false, true)) {
                Log.w(TAG, "분석이 밀려 1초 건너뜀")
                return
            }
            val frame = latestFrame
            analysisThread.execute {
                try {
                    val a = analyzer ?: RearAnalyzer(this@RearStatusActivity).also { analyzer = it }
                    val result = a.analyze(timeMs, frame, samples, sampleRate)
                    runOnUiThread { onAnalyzed(result) }
                } catch (e: Exception) {
                    Log.e(TAG, "분석 실패", e)
                } finally {
                    analyzing.set(false)
                }
            }
        }

        override fun onEnded() = runOnUiThread { screen.setVoiceStatus(DemoSetup.SCRIPT_ENDED) }
        override fun onError(message: String) = runOnUiThread { screen.setVoiceStatus(message) }
    }

    // ---------- 2. 분석 결과 → 상태·알림 ----------

    private fun onAnalyzed(result: RearAnalyzer.Result) {
        lastFrame = result.frame ?: lastFrame
        lastFocus = result.focus
        val before = tracker.status.state
        val events = tracker.update(result.observation)
        if (tracker.status.state != before) Log.i(TAG, "상태: $before → ${tracker.status.state}")
        // 잠 기록: 잠든 때와 깬 때는 추적기가 정한 시각(눈을 감거나 뜨기 시작한 때)으로 남긴다
        tripSession.childAsleepChanged()
        // 카드와 사진이 어긋나지 않게, 장면이 달라지면 사진도 바꾸게 한다(바꾸는 것은 아래 updatePhoto)
        if (DrivePhotoPolicy.sceneChanged(before, tracker.status.state)) drivePhoto?.stateChanged()
        // 잠듦 제안이 떠 있는데 아이가 깼거나 울기 시작하면 내린다. 우는 아이를 두고 "불을 꺼 드릴까요?"라고 묻지 않는다.
        // 얼굴이 잠깐 안 보이는 것은 깬 것이 아니므로 그대로 둔다
        val now = tracker.status.state
        if (sleepOfferUp && now != State.ASLEEP && now != State.FACE_HIDDEN) cancelSleepOffer()
        for (e in events) {
            Log.i(TAG, "일: ${e.type} → 상태 ${tracker.status.state}")
            when {
                // 잠듦: 실내가 정해 둔 범위를 벗어났으면 맞출지 화면에 묻는다
                e.type == ChildStateTracker.EventType.FELL_ASLEEP -> suggestOnSleep()
                // 깸·울음 그침: 잠들어서 끄거나 어둡게 한 불이 있으면 되돌릴지 한 번 묻는다
                (e.type == ChildStateTracker.EventType.WOKE_UP || e.type == ChildStateTracker.EventType.CRY_STOPPED) &&
                    restoreOnWake && pendingConsent == null -> offerRestore()
            }
        }
        screen.render(tracker.status)
        updatePhoto()
        watchBoarding()

        // 확인용 숫자는 화면에 두지 않고 로그로만 남긴다
        val f = result.observation.face
        Log.d(TAG, buildString {
            append("얼굴 ${result.facesFound}개")
            if (f != null) append(" · 눈 감김 %.2f · 고개 %.0f도 / 코 %.2f · 시선 %.0f도".format(f.eyesClosed, f.yawDeg, f.noseOffset, f.gazeDeg ?: Float.NaN))
            result.cryFaceScore?.let {
                val word = if (it >= CryFace.THRESHOLD) "예" else if (it >= CryFace.CALM_BELOW) "모름" else "아니오"
                append(" · 우는 얼굴 %.2f(%s)".format(it, word))
            }
            append(" · 울음 %.2f · 개 %.2f · 분석 %dms".format(result.cryScore, result.dogScore, result.elapsedMs))
        })
    }

    // ---------- 2-1. 차 실내 제안: 아이가 잠들었을 때 ----------

    /**
     * 실내를 읽어 부모가 정한 범위와 비교하고, 바꿀 것이 있으면 한 번에 묻는다. "네"면 실행한다.
     * 앱은 "서늘해요/더워요"를 판정하지 않고 부모가 정한 온도와 비교한 사실만 말한다.
     */
    private fun suggestOnSleep() {
        if (proposing || pendingConsent != null) return
        // 묻는 도중 시연을 다시 시작해도 이전 대답이 새 시연의 기억에 섞이지 않게, 이 제안을 낸 advisor를 잡아 둔다
        val advisor = advisor
        proposing = true
        cabin.read { c ->
            val range = SleepRangeStore.get(this)
            val p = advisor.propose(c, range, SystemClock.elapsedRealtime(), lightsAllowed = parked, askLights = SleepLightStore.ask(this))
            Log.i(TAG, "잠듦 제안: 실내 $c, 범위 $range → ${p?.question ?: "제안 없음"}")
            if (p == null) {
                proposing = false
                return@read
            }
            askToChange(p, c, range, advisor)
        }
    }

    /** 제안을 화면에 묻고 대답에 따라 실행하거나 그대로 둔다 */
    private fun askToChange(
        p: ComfortAdvisor.Proposal,
        c: ComfortAdvisor.Cabin,
        range: ComfortAdvisor.SleepRange?,
        advisor: ComfortAdvisor,
    ) {
        sleepOfferUp = true
        askConsent(p.shortQuestion) { yes ->
            proposing = false
            sleepOfferUp = false
            Log.i(TAG, "제안: ${p.question} → ${answerName(yes)}")
            when (yes) {
                true -> {
                    advisor.accepted(SystemClock.elapsedRealtime())
                    restoreOnWake = true
                    // 앱이 바꾼 온도·바람·조명은 이 잠의 "자는 동안" 값으로 세지 않는다
                    tripSession.sleepLog.appChanged(SystemClock.elapsedRealtime(), temp = p.changesTemp, fan = p.changesFan, light = p.changesLight)
                    // 실제로 바뀌었는지 보고 나서 결과를 보여 준다
                    cabin.apply(p, ::logCabin) { changed, failed -> reportResult(StatusWords.applyResult(changed, failed)) }
                }
                false, null -> {
                    // 대답이 없으면 바꾸지 않는다. 거절과 같이 30분 동안 다시 묻지 않는다
                    advisor.declined(c, range, SystemClock.elapsedRealtime())
                    screen.setVoiceStatus(StatusWords.keep(yes))
                }
            }
        }
    }

    /** 떠 있는 잠듦 제안을 내린다. 운전자가 거절한 것이 아니므로 거절로 기억하지 않는다 */
    private fun cancelSleepOffer() {
        Log.i(TAG, "아이가 잠에서 깨어 제안을 내림")
        sleepOfferUp = false
        proposing = false
        dismissConsent()
    }

    /** 차 실내를 바꾼 결과 한 줄을 기록한다 */
    private fun logCabin(line: String) {
        Log.i(TAG, "실내: $line")
    }

    /**
     * 끄거나 어둡게 한 불을 되돌릴지 묻는다. 온도·바람·공조 전원은 그대로 둔다.
     * 주행 중에는 무드등만 되돌릴 수 있다. 실내등·독서등이 남으면 차를 세웠을 때 묻는다.
     */
    private fun offerRestore() {
        restoreOnWake = false // 깰 때마다 묻지 않는다
        restoreWhenParked = false
        val question = ComfortAdvisor.restoreQuestion(cabin.hasAmbientToRestore, cabin.hasSwitchLightsToRestore, parked)
        if (question == null) {
            restoreWhenParked = cabin.hasSwitchLightsToRestore
            return
        }
        askConsent(question) { yes ->
            Log.i(TAG, "되돌리기 제안 → ${answerName(yes)}")
            if (yes == true) {
                // 무드등은 천천히 밝혀서 결과를 알기까지 시간이 걸린다. 그동안 눌린 것이 맞는지 알 수 있게 한다
                if (cabin.hasAmbientToRestore) screen.setVoiceStatus("천천히 밝히는 중이에요.")
                cabin.restoreLights(parked, ::logCabin) { changed, failed ->
                    restoreWhenParked = cabin.hasSwitchLightsToRestore
                    StatusWords.restoreResult(changed, failed)?.let { reportResult(it) }
                }
            } else {
                screen.setVoiceStatus(StatusWords.keep(yes))
            }
        }
    }

    // ---------- 2-2. 뒷창문이 열렸을 때 ----------

    /**
     * 주행 중 뒷창문이 닫힘에서 열림으로 바뀌면 짧은 알림음을 내고 잠글지 묻는다.
     * 앱은 누가 열었는지 모르므로 "아이가 열었어요"라고 말하지 않는다. 운전자가 직접 열어도 알린다.
     * 이미 잠겨 있으면 뒷좌석에서 열 수 없으니 잠글지 묻지 않고 열렸다는 것만 보여 준다.
     */
    private fun onRearWindowMoved(window: String, position: Int) {
        if (!windowWatch.update(window, position, driveMode)) return
        Log.i(TAG, "뒷창문 열림: $window → $position")
        if (windowOfferUp) return // 이미 묻는 중이다. 잠그면 양쪽 다 잠긴다
        alertTone.play()
        cabin.isRearWindowLocked(window) { locked ->
            if (locked) screen.setVoiceStatus("뒷창문이 열렸어요.") else offerWindowLock()
        }
    }

    private fun offerWindowLock() {
        if (windowOfferUp) return
        dropOtherQuestion()
        windowOfferUp = true
        askConsent("뒷창문이 열렸어요. 잠글까요?") { yes ->
            windowOfferUp = false
            Log.i(TAG, "뒷창문 잠금 제안 → ${answerName(yes)}")
            when (yes) {
                true -> cabin.lockRearWindows(::logCabin) { changed, failed -> reportResult(StatusWords.lockResult(changed, failed)) }
                false, null -> screen.setVoiceStatus(StatusWords.keep(yes))
            }
        }
    }

    /**
     * 뒷창문은 지금 일어난 일이라 떠 있는 다른 질문을 내리고 먼저 묻는다.
     * 운전자가 거절한 것이 아니므로 거절로 기억하지 않는다. 내린 것이 불 되돌리기 질문이면 다음에 깰 때 다시 묻는다.
     */
    private fun dropOtherQuestion() {
        if (pendingConsent == null) return
        if (!sleepOfferUp) restoreOnWake = true
        Log.i(TAG, "뒷창문을 먼저 묻기 위해 떠 있던 질문을 내림")
        sleepOfferUp = false
        proposing = false
        dismissConsent()
    }

    // ---------- 2-3. 아이와 다닌 곳: 차를 탈 때 묻고, 세우면 남긴다 ----------

    /**
     * 앱을 켠 뒤 차가 서 있으면 오늘 운행을 기록할지 한 번 묻는다. 아이가 보이는지와는 상관없다.
     * 아이가 울 때는 기다린다. 다른 질문이 떠 있으면 다음 분석 때 다시 본다.
     * 설정이나 다닌 곳 화면을 보는 동안에는 묻지 않는다. 안 보이는 질문이 "대답 없음"으로 끝나면 안 된다.
     */
    private fun watchBoarding() {
        if (!TripStore.ask(this)) return
        val recorder = tripSession.recorder
        if (recorder.recording && parked && !TripStore.saidYesOn(this, LocalDate.now())) {
            // 어제 "네"라고 한 뒤 앱이 꺼지지 않은 채 날이 바뀌었다. 오늘은 새로 묻는다
            recorder.reset()
            TripStore.askedThisRun = false
        }
        val onScreen = (floating && openScreens.isEmpty()) || lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val crying = tracker.status.state == State.CRYING || tracker.status.state == State.CRY_SOUND
        if (!onScreen || crying || pendingConsent != null || proposing || TripStore.askedThisRun) return
        if (recorder.onCarStarted(parked)) {
            TripStore.askedThisRun = true
            offerTrip()
        }
    }

    /** 오늘 이미 기록하겠다고 답했으면 묻지 않고 이어서 기록한다. 같은 날 다시 탄 주행이다 */
    private fun resumeTodayIfSaidYes() {
        if (!TripStore.ask(this) || !TripStore.saidYesOn(this, LocalDate.now())) return
        if (tripSession.recorder.resumeToday()) screen.setVoiceStatus("오늘 주행을 이어서 기록할게요.")
    }

    /** 시연을 처음부터. 오늘 답한 것도 지워 질문부터 다시 보여 준다. 저장된 기록은 지우지 않는다 */
    private fun restartDemo() {
        TripStore.askedThisRun = false
        TripStore.setSaidYes(this, null)
        startFeed()
    }

    private fun offerTrip() {
        Log.i(TAG, "이번 길 기록 질문을 띄움")
        tripOfferUp = true
        askConsent(StatusWords.TRIP_QUESTION) { yes ->
            tripOfferUp = false
            tripSession.recorder.onAnswer(yes)
            Log.i(TAG, "이번 길 기록 대답: ${answerName(yes)}")
            when (yes) {
                true -> {
                    TripStore.setSaidYes(this, LocalDate.now())
                    navi.beginTrip()
                    screen.setVoiceStatus("네, 오늘 주행을 기록할게요.")
                    requestSpeedPermission()
                }
                false -> screen.setVoiceStatus("네, 기록하지 않을게요.")
                null -> screen.setVoiceStatus("그럼 오늘 주행은 기록하지 않을게요.")
            }
            refreshParkedValues()
        }
    }

    /** 묻는 중에 출발했거나 시연을 다시 시작했다. 운전 중에 답하게 하지 않으려고 질문을 거둔다 */
    private fun dropTripOffer() {
        if (!tripOfferUp) return
        Log.i(TAG, "이번 길 기록 질문을 거둠")
        tripOfferUp = false
        dismissConsent()
    }

    /** 출발했다. 떠 있는 기록 질문은 거두고(운전 중에 답하게 하지 않는다), 기록은 TripSession이 이어 간다 */
    private fun onTripDrive() {
        dropTripOffer()
        tripSession.onDrive()
    }

    private fun onTripPark() = tripSession.onPark()

    /** 속도는 사용자가 허락해야 읽힌다. 기록을 하겠다고 했을 때 한 번 묻는다 */
    private fun requestSpeedPermission() {
        if (speed.permitted) return
        // 작은 창에서는 권한 창을 띄울 수 없어 권한만 묻는 투명한 화면을 잠깐 연다
        if (floating) {
            startActivity(Intent(this, SpeedPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        requestPermissions(arrayOf(CarSpeed.PERMISSION), REQUEST_SPEED)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SPEED) speed.start()
    }

    /** 다닌 곳을 저장한 결과(TripSession이 저장을 확인한 뒤 알린다)를 안내 글에 보인다 */
    private fun onTripSaved(text: String) {
        screen.setVoiceStatus(text)
        refreshParkedValues()
    }

    private fun answerName(yes: Boolean?) = when (yes) {
        true -> "네"
        false -> "아니요"
        null -> "대답 없음"
    }

    /**
     * 화면에 묻고 네·아니요를 받는다. 앱이 먼저 말하지 않으므로 소리 없이 묻는다.
     * 대답이 없으면 일정 시간 뒤 스스로 내린다. 운전 중인 사람을 재촉하지 않는다.
     *
     * 타이머는 화면(View)이 아니라 mainHandler에 건다. 화면에 걸면 분할과 작은 창을 오갈 때 이전 창에 걸린 타이머를 지우지 못해,
     * 다음 질문이 30초를 기다리지 못하고 "대답 없음"으로 끝난다(2026-11-05 시험에서 찾음).
     */
    private fun askConsent(question: String, onResult: (Boolean?) -> Unit) {
        pendingConsent = onResult
        screen.showConsent(question)
        mainHandler.removeCallbacks(consentTimeout)
        mainHandler.postDelayed(consentTimeout, CONSENT_TIMEOUT_MS)
    }

    private fun answerConsent(yes: Boolean?) {
        val onResult = pendingConsent ?: return
        dismissConsent()
        onResult(yes)
    }

    /** 떠 있는 질문을 내린다. 대답을 받을 곳과 30초 타이머도 함께 지운다 */
    private fun dismissConsent() {
        pendingConsent = null
        mainHandler.removeCallbacks(consentTimeout)
        screen.hideConsent()
    }

    private val consentTimeout = Runnable { answerConsent(null) }

    /** 공조·조명을 바꾼 실제 결과를 화면에 보여 주고 기록한다 */
    private fun reportResult(text: String) {
        Log.i(TAG, "실내 변경 결과: $text")
        screen.setVoiceStatus(text)
    }

    // ---------- 3. 정차/주행 ----------

    private fun applyDriving(state: DrivingStateMonitor.DrivingState) {
        // 차를 세웠는데 아이가 자고 있으면 다시 본다. 주행 중에는 차가 실내등·독서등을 못 바꾸게 해 그때 묻지 못했다
        // 기어와 주행 규제를 함께 본 값을 쓴다. 차가 움직이는데 불 끄기를 묻는 일이 없어야 한다
        val wasParked = parked
        parked = state.videoAllowed
        if (parked && !wasParked) {
            when {
                tracker.status.state == State.ASLEEP -> suggestOnSleep()
                // 주행 중에 깨서 켜지 못한 실내등·독서등을 이제 켤 수 있다
                restoreWhenParked && pendingConsent == null -> offerRestore()
            }
        }
        Log.i(TAG, "기어 ${state.gear}, 규제 ${DrivingStateMonitor.describe(state.uxRestrictions)} → ${if (parked) "정차" else "주행"}")
        val wasDriving = driveMode
        driveMode = !parked && state.gear != null
        updateParkedPanel()
        if (driveMode != wasDriving) applyLayout()
        // 다닌 곳 기록. 앱을 켠 직후에는 기어만 알고 규제 값을 몰라 driveMode가 잠깐 true가 되므로 기어를 직접 본다
        val moving = state.gear != null && state.gear != VehicleGear.GEAR_PARK
        if (moving && !gearMoving) onTripDrive()
        gearMoving = moving
        if (parked && !wasParked) onTripPark()
    }

    private fun updateParkedPanel() = screen.showParkedPanel(parked && !floating)

    /**
     * 내비 위 작은 창의 사진을 고른 간격마다, 그리고 아이 상태가 크게 바뀔 때 한 장씩 바꾼다. 정차와 주행이 같다.
     * 바꾸는 사이에는 멈춰 있고, 소리·알림·움직임은 없다. 끄면(0) 사진을 보여 주지 않는다. 앱 칸에는 사진이 없다.
     */
    private fun updatePhoto() {
        if (!floating || drivePhotoMinutes == 0) return
        val photo = drivePhoto ?: DrivePhotoPolicy(drivePhotoMinutes * 60_000L).also { drivePhoto = it }
        val now = SystemClock.elapsedRealtime()
        // 얼굴을 찾은 바로 그 사진을 쓴다. 분석 전의 새 사진에 앞 사진의 얼굴 위치를 대면 엉뚱한 곳이 커진다
        val frame = lastFrame ?: latestFrame
        if (frame != null && photo.shouldTakeNew(now)) {
            screen.photo.show(frame, if (frame === lastFrame) lastFocus else null)
            Log.i(TAG, "사진 바꿈 (${drivePhotoMinutes}분마다, 지금 상태 ${tracker.status.state})")
        }
    }

    // ---------- 화면 배치 ----------

    /** 지금 상태(작은 창인지, 주행 중인지, 사진을 껐는지)대로 화면을 다시 놓는다 */
    private fun applyLayout() = screen.applyLayout(floating, driveMode, drivePhotoMinutes == 0, tracker.status)

    /**
     * 같은 화면을 내비 위 작은 창으로 옮긴다. 화면을 새로 만들지 않아 카드·사진·네아니요가 그대로 동작한다.
     * 내비의 오른쪽 버튼과 내 차 표시를 가리지 않는 자리에 두고, 위쪽 선은 내비 패널에 맞춘다. 값은 에뮬레이터 화면에서 쟀다.
     */
    private fun showFloating() {
        if (floating) return
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "겹쳐 그리기 권한이 없어 작은 창을 띄우지 않음")
            return
        }
        floating = true
        screen.root.visibility = View.VISIBLE
        (screen.root.parent as? ViewGroup)?.removeView(screen.root)
        applyLayout()
        updateParkedPanel()
        // 작은 창을 열면 새 사진으로 시작하고, 그 뒤로는 고른 간격마다 바꾼다
        drivePhoto = null
        screen.photo.clear()
        updatePhoto()
        val params = WindowManager.LayoutParams(
            Ui.dp(this, FLOAT_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = Ui.dp(this@RearStatusActivity, FLOAT_END_DP)
            y = Ui.dp(this@RearStatusActivity, FLOAT_TOP_DP)
        }
        overlayManager.addView(screen.root, params)
        Log.i(TAG, "내비 위 작은 창 띄움")
    }

    /** 작은 창의 사진을 누르면 설정으로 들어간다. 달리는 중이거나 질문에 답하는 중이면 열지 않는다 */
    private fun openSettingsFromPhoto() {
        if (!floating || !parked || pendingConsent != null) return
        startActivity(Intent(this, SleepSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // 설정, 다닌 곳, 주행 중 수면 화면이 떠 있는 동안에는 작은 창을 숨기고 모두 닫히면 다시 보인다.
    // 화면을 오갈 때 새 화면의 onStart가 앞 화면의 onStop보다 먼저 오고, 사진 고르는 동안 다닌 곳 화면이 멈춰도 닫힌 것으로 보지 않는다
    private val openScreens = HashSet<Activity>()
    private fun isAppScreen(a: Activity) = a is SleepSettingsActivity || a is TripDiaryActivity || a is SleepReportActivity

    private fun screenClosed(activity: Activity) {
        if (!openScreens.remove(activity) || openScreens.isNotEmpty() || !floating) return
        screen.root.visibility = View.VISIBLE
        refreshParkedValues()
        applyLayout()
        drivePhoto = null
        screen.photo.clear()
        updatePhoto()
    }

    private val screenWatcher = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            if (!isAppScreen(activity)) return
            openScreens += activity
            if (floating) screen.root.visibility = View.GONE
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity is TripDiaryActivity && activity.picking) return
            if (isAppScreen(activity)) screenClosed(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (isAppScreen(activity)) screenClosed(activity)
            if (activity is SpeedPermissionActivity) speed.start()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    private fun hideFloating() {
        if (!floating) return
        floating = false
        // 바로 닫아야 한다. removeView는 뒤늦게 처리되며 이미 앱 화면으로 옮긴 root의 연결을 끊어 앱이 죽는다
        overlayManager.removeViewImmediate(screen.root)
        // 크기를 다시 준다. 안 주면 작은 창의 가로가 남아 앱 화면이 좁게 나온다
        setContentView(screen.root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        drivePhoto = null
        screen.photo.clear()
        applyLayout()
        updateParkedPanel()
        Log.i(TAG, "내비 위 작은 창 닫음")
    }

    /**
     * 내비 옆으로 분할하면 창 크기가 바뀐다. 매니페스트의 configChanges 덕분에 화면을 다시 만들지 않고 여기로 온다.
     * 다시 만들면 분석과 듣기가 처음부터 시작된다(2026-11-04 시험에서 분할하자 시연이 처음으로 돌아갔다).
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    override fun onDestroy() {
        GleoBridge.answer = null
        application.unregisterActivityLifecycleCallbacks(screenWatcher)
        demoRestart = null
        mainHandler.removeCallbacksAndMessages(null)
        navi.release()
        if (floating) {
            floating = false
            overlayManager.removeViewImmediate(screen.root)
        }
        feed?.stop()
        driving.stop()
        voice.release()
        alertTone.release()
        cabin.release()
        speed.stop()
        analysisThread.execute { analyzer?.close() }
        analysisThread.shutdown()
        super.onDestroy()
    }

    companion object {
        /** 설정 화면의 "시연 처음부터" 버튼이 부른다 */
        var demoRestart: (() -> Unit)? = null

        private const val TAG = "RearCareStatus"
        private const val NAVI_RETRY_MS = 500L
        private const val NAVI_TRIES = 5
        private const val REQUEST_SPEED = 26 // 속도 권한 요청 번호
        private const val CONSENT_TIMEOUT_MS = 30_000L // 화면 질문에 대답이 없으면 이만큼 뒤에 내린다
        private const val FLOAT_WIDTH_DP = 540 // 내비 위 작은 창의 가로
        private const val FLOAT_END_DP = 160 // 화면 오른쪽 끝에서 띄우는 거리. 내비 오른쪽 버튼을 가리지 않는다
        private const val FLOAT_TOP_DP = 24 // 상태 표시줄 아래에서 띄우는 거리. 내비 패널 위쪽 선과 맞는다
    }
}
