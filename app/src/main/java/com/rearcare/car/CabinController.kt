package com.rearcare.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs

/**
 * 차 실내(공조·조명)를 읽고, 승낙받은 제안을 실행하고, 불을 되돌리고, 뒷창문을 지켜보고 잠그는 부품.
 *
 * - start(): 어느 구역을 조절할 수 있는지(가능 여부)를 먼저 확인하고, 뒷창문 위치 알림을 받기 시작한다.
 * - read(): 지금 공조 전원, 실내 온도(센서), 바람 세기, 실내등, 뒷좌석 독서등, 무드등 밝기를 한꺼번에 읽는다.
 * - apply(): 제안 중 승낙받은 것을 바꾼다. 불은 바꾸기 전 값을 기억한다. 공조가 꺼져 있으면 먼저 켠다.
 * - restoreLights(): 기억해 둔 값으로 불만 되돌린다. 무드등은 천천히 밝힌다. 온도·바람·공조 전원은 되돌리지 않는다.
 * - lockRearWindows(): 뒷창문을 잠근다. 닫지 않는다. 앱은 창문 근처에 손이나 머리가 있는지 모른다.
 * - apply·restoreLights·lockRearWindows는 끝나면 바꾼 수와 실패한 수를 알린다. 실제 결과를 보고 말하기 위해서다.
 *
 * SDK의 "성공"은 요청을 받았다는 뜻일 뿐, 차가 바뀌었다는 뜻이 아니다. 2026-11-03 시험에서 SDK가 성공이라고 답한 뒤에도
 * 값이 그대로인 경우가 있었다. 그래서 바꾼 뒤 잠시 기다렸다가 다시 읽어, 정말 바뀐 것만 바뀌었다고 센다(confirmLater).
 *
 * SDK 결과는 다른 스레드에서 올 수 있어서, 값 저장과 결과 전달은 모두 화면 스레드(handler)에서 한다.
 */
class CabinController(context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context)
    private var ready = false

    private var fanBounds: Map<SeatArea, AreaBounds<Int>> = emptyMap()
    private var tempBounds: Map<SeatArea, AreaBounds<Float>> = emptyMap()
    private var readingAreas: List<SeatArea> = emptyList()
    private var currentTempArea: SeatArea? = null
    private var powerAreas: List<SeatArea> = emptyList()
    private var ambientBounds: Map<SeatArea, AreaBounds<Int>> = emptyMap()
    private var rearLockAreas: List<WindowArea> = emptyList()
    private val windowListeners = mutableListOf<Pair<WindowArea, WindowPositionListener>>()

    private val savedReading = mutableMapOf<SeatArea, LightSwitch>()
    private var savedCabin: LightSwitch? = null
    private val savedAmbient = mutableMapOf<SeatArea, Int>()
    private var ambientFadeId = 0 // 무드등을 천천히 밝히는 중에 다시 어둡게 하면 밝히기를 멈추려고 번호를 둔다

    /** 뒷창문 위치가 바뀔 때 화면 스레드에서 부른다. 처음 읽은 값도 온다. 값은 0이 닫힘이다 */
    var onRearWindowMoved: ((window: String, position: Int) -> Unit)? = null

    /** 어둡게 한 무드등이 있어 되돌릴 수 있는가. 주행 중에도 바뀐다 */
    val hasAmbientToRestore: Boolean
        get() = savedAmbient.isNotEmpty()

    /** 끈 실내등·독서등이 있어 되돌릴 수 있는가. P단에서만 바뀐다 */
    val hasSwitchLightsToRestore: Boolean
        get() = savedCabin != null || savedReading.isNotEmpty()

    /** 여러 요청의 결과를 모아, 모두 끝나면 한 번 알린다. 화면 스레드에서만 쓴다 */
    private class Tally(private val onDone: (changed: Int, failed: Int) -> Unit) {
        private var pending = 1 // 요청을 다 보내기 전에 끝났다고 하지 않도록 1부터 시작한다
        private var changed = 0
        private var failed = 0

        fun begin() { pending++ }
        fun ok() { changed++; end() }
        fun fail() { failed++; end() }
        fun skip() = end() // 바꿀 필요가 없었던 것(이미 꺼져 있는 불 등)
        fun allSent() = end()

        private fun end() {
            pending--
            if (pending == 0) onDone(changed, failed)
        }
    }

    fun start() {
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            Log.e(TAG, "Vehicle 초기화 실패. 공조·조명을 쓸 수 없다", e)
            return
        }
        ready = true
        val hvac = vehicle.getHVAC()
        hvac.checkHvacPowerCapabilities({ areas: List<SeatArea> -> handler.post { powerAreas = areas } }, { e -> Log.e(TAG, "공조 전원 가능 여부 실패", e) })
        hvac.checkHvacFanSpeedCapabilities({ b -> handler.post { fanBounds = b } }, { e -> Log.e(TAG, "바람 가능 여부 실패", e) })
        hvac.checkHvacTemperatureCapabilities({ b -> handler.post { tempBounds = b } }, { e -> Log.e(TAG, "온도 가능 여부 실패", e) })
        // 실제 실내 온도 센서: 2열이 들어간 구역(실내 전체 등)을 고른다. 없으면 첫 구역
        hvac.checkHvacTemperatureCurrentCapabilities({ _, areas ->
            handler.post { currentTempArea = areas.firstOrNull { a -> a.values.any { it.name.startsWith("ROW_2") } } ?: areas.firstOrNull() }
        }, { e -> Log.e(TAG, "실내 온도 센서 가능 여부 실패", e) })
        vehicle.getLight().checkReadingLightSwitchCapabilities(
            { m: Map<SeatArea, List<LightSwitch>> -> handler.post { readingAreas = m.keys.filter { it.isRearOnly() } } },
            { e -> Log.e(TAG, "독서등 가능 여부 실패", e) },
        )
        vehicle.getLight().checkAmbientLightBrightnessCapabilities({ _, b -> handler.post { ambientBounds = b } }, { e -> Log.e(TAG, "무드등 가능 여부 실패", e) })
        vehicle.getWindow().checkWindowPositionCapabilities({ _, b ->
            handler.post { watchRearWindows(b.keys.filter { it.isRearOnly() }) }
        }, { e -> Log.e(TAG, "창문 가능 여부 실패", e) })
        // 잠금은 뒷창문 구역만 쓴다. 전체 구역은 앞창문까지 포함한다
        vehicle.getWindow().checkWindowLockCapabilities({ _, areas ->
            handler.post { rearLockAreas = areas.filter { it.isRearOnly() } }
        }, { e -> Log.e(TAG, "창문 잠금 가능 여부 실패", e) })
    }

    private fun WindowArea.isRearOnly() = values.isNotEmpty() && values.all { it.name.startsWith("ROW_") && !it.name.startsWith("ROW_1") }
    private fun WindowArea.name() = values.joinToString("+") { it.name }

    /** 뒷창문 위치 알림을 받는다. 이 부품은 상태 화면에만 있으므로 다른 화면이 같은 알림을 끊을 일이 없다 */
    private fun watchRearWindows(areas: List<WindowArea>) {
        areas.forEach { area ->
            val listener = object : WindowPositionListener {
                override fun onWindowPositionUpdated(position: Int?) {
                    if (position != null) handler.post { onRearWindowMoved?.invoke(area.name(), position) }
                }

                override fun onFailed(e: Exception) {
                    Log.e(TAG, "창문 위치 알림 실패 ${area.name()}", e)
                }
            }
            vehicle.getWindow().registerWindowPosition(area, listener)
            windowListeners += area to listener
        }
    }

    /** 그 뒷창문이 잠겨 있는지 읽는다. 모르면 false다. 잠겼다고 잘못 알면 잠글지 묻지 않게 되기 때문이다 */
    fun isRearWindowLocked(window: String, onResult: (Boolean) -> Unit) {
        val area = rearLockAreas.firstOrNull { it.name() == window } ?: return onResult(false)
        vehicle.getWindow().getCurrentWindowLockState(area, { locked -> handler.post { onResult(locked == true) } }, { e ->
            Log.e(TAG, "창문 잠금 읽기 실패", e)
            handler.post { onResult(false) }
        })
    }

    /**
     * 뒷창문을 잠근다. 닫지는 않고, 앱이 풀지도 않는다. 풀기는 차의 창문 잠금 버튼으로 한다.
     * 콘솔에서 잠금을 바꿔도 알림이 오지 않는 것을 시험에서 봤으므로 알림을 기다리지 않고 다시 읽어 확인한다.
     */
    fun lockRearWindows(onLog: (String) -> Unit, onDone: (changed: Int, failed: Int) -> Unit) {
        if (!ready) return onDone(0, 1)
        val tally = Tally(onDone)
        val window = vehicle.getWindow()
        if (rearLockAreas.isEmpty()) { tally.begin(); tally.fail() }
        rearLockAreas.forEach { area ->
            tally.begin()
            window.setWindowLock(area, true, { _, _ ->
                confirmLater<Boolean>(
                    { ok, fail -> window.getCurrentWindowLockState(area, ok, fail) },
                    { it == true },
                    { same ->
                        report(onLog, if (same) "뒷창문 잠금" else "뒷창문: 잠기지 않음", area.name())
                        if (same) tally.ok() else tally.fail()
                    },
                )
            }, { e -> Log.e(TAG, "창문 잠그기 실패", e); handler.post { tally.fail() } })
        }
        tally.allSent()
    }

    /** 지금 차 상태를 읽어 onResult(Cabin)을 화면 스레드에서 부른다. 못 읽은 값은 null */
    fun read(onResult: (ComfortAdvisor.Cabin) -> Unit) {
        if (!ready) return onResult(ComfortAdvisor.Cabin(null, null, null, null))
        var temp: Float? = null
        var fan: Int? = null
        var cabinOn: Boolean? = null
        val rearOn = mutableListOf<Boolean>()
        val powerOn = mutableListOf<Boolean>()
        var ambient: Int? = null
        var pending = 0
        fun done() {
            pending--
            if (pending == 0) {
                onResult(
                    ComfortAdvisor.Cabin(
                        temp,
                        fan,
                        cabinOn,
                        rearOn.takeIf { it.isNotEmpty() }?.any { it },
                        powerOn.takeIf { it.isNotEmpty() }?.any { it },
                        ambient,
                    ),
                )
            }
        }
        val hvac = vehicle.getHVAC()
        val light = vehicle.getLight()
        val tempArea = currentTempArea
        val fanArea = targetAreas(fanBounds).firstOrNull()
        val ambientArea = targetAreas(ambientBounds).firstOrNull()
        pending = 1 + (if (tempArea != null) 1 else 0) + (if (fanArea != null) 1 else 0) + (if (ambientArea != null) 1 else 0) +
            readingAreas.size + powerAreas.size
        // 무드등은 밝기만 본다. 에뮬레이터의 스위치 값은 콘솔과 따로 놀아 켜져 있는지 판단할 수 없다
        ambientArea?.let { a ->
            light.getAmbientLightBrightness(a, { v -> handler.post { ambient = v; done() } }, { handler.post { done() } })
        }
        powerAreas.forEach { a ->
            hvac.getHvacPowerState(a, { v -> handler.post { if (v != null) powerOn += v; done() } }, { handler.post { done() } })
        }
        tempArea?.let { a ->
            hvac.getHvacTemperatureCurrent(a, TemperatureUnit.CELSIUS, { v -> handler.post { temp = v; done() } }, { handler.post { done() } })
        }
        fanArea?.let { a ->
            hvac.getHvacFanSpeed(a, { v -> handler.post { fan = v; done() } }, { handler.post { done() } })
        }
        light.getCabinLightSwitch({ v -> handler.post { cabinOn = v == LightSwitch.ON; done() } }, { handler.post { done() } })
        readingAreas.forEach { a ->
            light.getReadingLightSwitch(a, { v -> handler.post { if (v != null) rearOn += v == LightSwitch.ON; done() } }, { handler.post { done() } })
        }
    }

    /**
     * 잠 기록에 쓰는 실내 값. 모르면 null.
     * lightOn은 실내등이나 뒷좌석 독서등이 켜져 있는가, ambient는 무드등 밝기(0~100)다.
     * 무드등은 밝기만 본다. 에뮬레이터의 무드등 스위치 값은 콘솔과 따로 논다.
     */
    data class Climate(val on: Boolean?, val setC: Float?, val fan: Int?, val cabinC: Float?, val lightOn: Boolean? = null, val ambient: Int? = null) {
        /** 잠 기록의 조건으로 바꾼다. 공조가 꺼져 있으면 설정 온도는 뜻이 없어 모름(null)으로, 바람은 0으로 넣는다 */
        fun toConditions(kph: Float?): SleepLog.Conditions {
            val off = on == false
            return SleepLog.Conditions(kph, if (off) null else setC, if (off) 0f else fan?.toFloat(), cabinC, lightOn, ambient?.toFloat())
        }
    }

    /**
     * 공조 전원, 설정 온도, 바람 세기, 실제 실내 온도, 실내등·독서등, 무드등 밝기를 읽어 onResult를 화면 스레드에서 부른다.
     * 설정 온도와 바람은 잠듦 제안이 바꾸는 구역(뒷좌석 구역이 있으면 그것, 없으면 첫 구역)의 값이다.
     */
    fun readClimate(onResult: (Climate) -> Unit) {
        if (!ready) return onResult(Climate(null, null, null, null))
        val hvac = vehicle.getHVAC()
        val light = vehicle.getLight()
        val setArea = targetAreas(tempBounds).firstOrNull()
        val fanArea = targetAreas(fanBounds).firstOrNull()
        val ambientArea = targetAreas(ambientBounds).firstOrNull()
        val cabinArea = currentTempArea
        val powers = mutableListOf<Boolean>()
        val lights = mutableListOf<Boolean>()
        var setC: Float? = null
        var fan: Int? = null
        var cabinC: Float? = null
        var ambient: Int? = null
        // 요청을 다 보내기 전에 끝났다고 하지 않도록 1을 더해 두고 마지막에 뺀다. 실내등 1개도 더한다
        var pending = 1 + 1 + powerAreas.size + readingAreas.size + listOfNotNull(setArea, fanArea, cabinArea, ambientArea).size
        fun done() {
            pending--
            if (pending == 0) {
                onResult(Climate(powers.takeIf { it.isNotEmpty() }?.any { it }, setC, fan, cabinC, lights.takeIf { it.isNotEmpty() }?.any { it }, ambient))
            }
        }
        light.getCabinLightSwitch({ v -> handler.post { if (v != null) lights += v == LightSwitch.ON; done() } }, { handler.post { done() } })
        readingAreas.forEach { a ->
            light.getReadingLightSwitch(a, { v -> handler.post { if (v != null) lights += v == LightSwitch.ON; done() } }, { handler.post { done() } })
        }
        ambientArea?.let { a ->
            light.getAmbientLightBrightness(a, { v -> handler.post { ambient = v; done() } }, { handler.post { done() } })
        }
        powerAreas.forEach { a ->
            hvac.getHvacPowerState(a, { v -> handler.post { if (v != null) powers += v; done() } }, { handler.post { done() } })
        }
        setArea?.let { a ->
            hvac.getHvacTemperature(a, TemperatureUnit.CELSIUS, { v -> handler.post { setC = v; done() } }, { handler.post { done() } })
        }
        fanArea?.let { a ->
            hvac.getHvacFanSpeed(a, { v -> handler.post { fan = v; done() } }, { handler.post { done() } })
        }
        cabinArea?.let { a ->
            hvac.getHvacTemperatureCurrent(a, TemperatureUnit.CELSIUS, { v -> handler.post { cabinC = v; done() } }, { handler.post { done() } })
        }
        handler.post { done() }
    }

    /**
     * 조금 뒤 값을 다시 읽어 정말 바뀌었는지 본다. done(바뀌었는가)을 화면 스레드에서 부른다.
     * read에는 SDK의 읽기 함수를 넘긴다.
     */
    private fun <T> confirmLater(
        read: (onValue: (T?) -> Unit, onFail: (Exception) -> Unit) -> Unit,
        isRight: (T?) -> Boolean,
        done: (Boolean) -> Unit,
        delayMs: Long = CONFIRM_DELAY_MS,
    ) {
        handler.postDelayed({
            read({ v -> handler.post { done(isRight(v)) } }, { e ->
                Log.e(TAG, "다시 읽기 실패", e)
                handler.post { done(false) }
            })
        }, delayMs)
    }

    private fun confirmTemp(area: SeatArea, target: Float, done: (Boolean) -> Unit) = confirmLater<Float>(
        { ok, fail -> vehicle.getHVAC().getHvacTemperature(area, TemperatureUnit.CELSIUS, ok, fail) },
        { it != null && abs(it - target) < 0.01f },
        done,
    )

    private fun confirmFan(area: SeatArea, target: Int, done: (Boolean) -> Unit) = confirmLater<Int>(
        { ok, fail -> vehicle.getHVAC().getHvacFanSpeed(area, ok, fail) },
        { it == target },
        done,
    )

    private fun confirmPower(area: SeatArea, target: Boolean, done: (Boolean) -> Unit) = confirmLater<Boolean>(
        { ok, fail -> vehicle.getHVAC().getHvacPowerState(area, ok, fail) },
        { it == target },
        done,
    )

    private fun confirmCabinLight(target: LightSwitch, done: (Boolean) -> Unit) = confirmLater<LightSwitch>(
        { ok, fail -> vehicle.getLight().getCabinLightSwitch(ok, fail) },
        { it == target },
        done,
    )

    private fun confirmReadingLight(area: SeatArea, target: LightSwitch, done: (Boolean) -> Unit) = confirmLater<LightSwitch>(
        { ok, fail -> vehicle.getLight().getReadingLightSwitch(area, ok, fail) },
        { it == target },
        done,
    )

    private fun confirmAmbient(area: SeatArea, target: Int, done: (Boolean) -> Unit) = confirmLater<Int>(
        { ok, fail -> vehicle.getLight().getAmbientLightBrightness(area, ok, fail) },
        { it == target },
        done,
    )

    /**
     * 승낙받은 제안을 실행한다. 바꾼 결과를 한 줄씩 onLog로 알리고, 모두 끝나면 onDone(바꾼 수, 실패한 수)을 부른다.
     * 공조를 켜야 하면 먼저 켜고, 켜진 것을 확인한 뒤 나머지를 바꾼다. 켜지지 않으면 온도·바람은 바꾸지 않는다.
     */
    fun apply(p: ComfortAdvisor.Proposal, onLog: (String) -> Unit, onDone: (changed: Int, failed: Int) -> Unit) {
        if (!ready) return onDone(0, 1)
        val tally = Tally(onDone)
        if (p.turnClimateOn) {
            tally.begin() // 공조 전원이 켜진 뒤에 나머지를 보내므로, 그때까지 끝나지 않게 잡아 둔다
            turnClimateOn(onLog) { on ->
                tally.begin()
                if (on) {
                    tally.ok()
                    applyClimate(p, tally, onLog)
                } else {
                    tally.fail()
                }
                tally.skip()
            }
        } else {
            applyClimate(p, tally, onLog)
        }
        if (p.lightsOff) applyLights(tally, onLog)
        p.setAmbientBrightness?.let { applyAmbient(it, tally, onLog) }
        tally.allSent()
    }

    private fun applyAmbient(level: Int, tally: Tally, onLog: (String) -> Unit) {
        ambientFadeId++ // 천천히 밝히는 중이었다면 멈춘다
        val light = vehicle.getLight()
        val areas = targetAreas(ambientBounds)
        if (areas.isEmpty()) { tally.begin(); tally.fail() }
        areas.forEach { area ->
            val v = level.coerceIn(ambientBounds.getValue(area).minValue, ambientBounds.getValue(area).maxValue)
            tally.begin()
            light.getAmbientLightBrightness(area, { before ->
                handler.post {
                    val first = area !in savedAmbient
                    if (before != null && first) savedAmbient[area] = before
                    light.setAmbientLightBrightness(area, v, { _, _ ->
                        confirmAmbient(area, v) { same ->
                            if (same) {
                                onLog("무드등 밝기: $before → $v")
                                tally.ok()
                            } else {
                                if (first) savedAmbient.remove(area)
                                onLog("무드등 밝기: 바뀌지 않음")
                                tally.fail()
                            }
                        }
                    }, { e -> Log.e(TAG, "무드등 바꾸기 실패", e); handler.post { if (first) savedAmbient.remove(area); tally.fail() } })
                }
            }, { e -> Log.e(TAG, "무드등 읽기 실패", e); handler.post { tally.fail() } })
        }
    }

    private fun turnClimateOn(onLog: (String) -> Unit, then: (Boolean) -> Unit) {
        val areas = powerAreas
        if (areas.isEmpty()) return then(false)
        var left = areas.size
        var allOn = true
        fun one(on: Boolean) {
            if (!on) allOn = false
            left--
            if (left == 0) then(allOn)
        }
        areas.forEach { area ->
            vehicle.getHVAC().setHvacPower(area, true, { _, _ ->
                confirmPower(area, true) { on ->
                    onLog(if (on) "공조 켬" else "공조가 켜지지 않음")
                    one(on)
                }
            }, { e ->
                Log.e(TAG, "공조 켜기 실패", e)
                handler.post { one(false) }
            })
        }
    }

    private fun applyClimate(p: ComfortAdvisor.Proposal, tally: Tally, onLog: (String) -> Unit) {
        val hvac = vehicle.getHVAC()
        p.setTempC?.let { target ->
            val areas = targetAreas(tempBounds)
            if (areas.isEmpty()) { tally.begin(); tally.fail() }
            areas.forEach { area ->
                val b = tempBounds.getValue(area)
                val t = target.coerceIn(b.minValue, b.maxValue)
                tally.begin()
                hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, { before ->
                    handler.post {
                        hvac.setHvacTemperature(area, t, TemperatureUnit.CELSIUS, { a, _ ->
                            confirmTemp(area, t) { same ->
                                if (same) {
                                    report(onLog, "설정 온도: $before → $t℃", a.label())
                                    tally.ok()
                                } else {
                                    report(onLog, "설정 온도: 바뀌지 않음", a.label())
                                    tally.fail()
                                }
                            }
                        }, { e -> Log.e(TAG, "온도 바꾸기 실패", e); handler.post { tally.fail() } })
                    }
                }, { e -> Log.e(TAG, "온도 읽기 실패", e); handler.post { tally.fail() } })
            }
        }
        p.setFanSpeed?.let { level ->
            val areas = targetAreas(fanBounds)
            if (areas.isEmpty()) { tally.begin(); tally.fail() }
            areas.forEach { area ->
                val v = level.coerceIn(fanBounds.getValue(area).minValue, fanBounds.getValue(area).maxValue)
                tally.begin()
                hvac.getHvacFanSpeed(area, { before ->
                    handler.post {
                        hvac.setHvacFanSpeed(area, v, { a, _ ->
                            confirmFan(area, v) { same ->
                                if (same) {
                                    report(onLog, "바람: $before → $v", a.label())
                                    tally.ok()
                                } else {
                                    report(onLog, "바람: 바뀌지 않음", a.label())
                                    tally.fail()
                                }
                            }
                        }, { e -> Log.e(TAG, "바람 바꾸기 실패", e); handler.post { tally.fail() } })
                    }
                }, { e -> Log.e(TAG, "바람 읽기 실패", e); handler.post { tally.fail() } })
            }
        }
    }

    private fun applyLights(tally: Tally, onLog: (String) -> Unit) {
        val light = vehicle.getLight()
        tally.begin()
        light.getCabinLightSwitch({ before ->
            handler.post {
                if (before == LightSwitch.ON) {
                    val first = savedCabin == null
                    if (first) savedCabin = before
                    light.setCabinLightSwitch(LightSwitch.OFF, { _ ->
                        confirmCabinLight(LightSwitch.OFF) { same ->
                            if (same) {
                                onLog("실내등 끔")
                                tally.ok()
                            } else {
                                if (first) savedCabin = null
                                onLog("실내등: 꺼지지 않음")
                                tally.fail()
                            }
                        }
                    }, { e -> Log.e(TAG, "실내등 끄기 실패", e); handler.post { if (first) savedCabin = null; tally.fail() } })
                } else {
                    tally.skip()
                }
            }
        }, { e -> Log.e(TAG, "실내등 읽기 실패", e); handler.post { tally.fail() } })
        readingAreas.forEach { area ->
            tally.begin()
            light.getReadingLightSwitch(area, { before ->
                handler.post {
                    if (before == LightSwitch.ON) {
                        val first = area !in savedReading
                        if (first) savedReading[area] = before
                        light.setReadingLightSwitch(area, LightSwitch.OFF, { _ ->
                            confirmReadingLight(area, LightSwitch.OFF) { same ->
                                if (same) {
                                    report(onLog, "독서등 끔", area.label())
                                    tally.ok()
                                } else {
                                    if (first) savedReading.remove(area)
                                    report(onLog, "독서등: 꺼지지 않음", area.label())
                                    tally.fail()
                                }
                            }
                        }, { e -> Log.e(TAG, "독서등 끄기 실패", e); handler.post { if (first) savedReading.remove(area); tally.fail() } })
                    } else {
                        tally.skip()
                    }
                }
            }, { e -> Log.e(TAG, "독서등 읽기 실패", e); handler.post { tally.fail() } })
        }
    }

    /**
     * 불만 바꾸기 전 값으로 되돌리고, 모두 끝나면 onDone(되돌린 수, 실패한 수)을 부른다.
     * 무드등은 밝기 값이 있어 천천히 밝힌다. 끝난 뒤 다시 읽어 확인하므로 onDone은 그때 불린다.
     * 실내등·독서등은 켜기·끄기만 있어 바로 켠다. parked일 때만 되돌린다. 주행 중에는 앱에서 켜면 거절되므로 기억해 두었다가 세웠을 때 되돌린다.
     */
    fun restoreLights(parked: Boolean, onLog: (String) -> Unit, onDone: (changed: Int, failed: Int) -> Unit) {
        if (!ready) return onDone(0, 1)
        val light = vehicle.getLight()
        val tally = Tally(onDone)
        val fadeId = ++ambientFadeId
        // 원래 밝기는 다 밝힌 뒤에 지운다. 밝히는 중에 다시 어둡게 하면 중간 밝기를 원래 값으로 잘못 기억하게 된다
        savedAmbient.toMap().forEach { (a, v) ->
            tally.begin()
            light.getAmbientLightBrightness(a, { now ->
                handler.post {
                    stepAmbient(a, ComfortAdvisor.fadeLevels(now ?: v, v, AMBIENT_FADE_STEPS), 0, v, fadeId) { same ->
                        when (same) {
                            true -> { savedAmbient.remove(a); onLog("무드등 밝기 → $v"); tally.ok() }
                            false -> { onLog("무드등 밝기: 돌아가지 않음"); tally.fail() }
                            null -> tally.skip() // 그사이 다시 어둡게 했다. 원래 밝기는 그대로 기억한다
                        }
                    }
                }
            }, { e -> Log.e(TAG, "무드등 읽기 실패", e); handler.post { tally.fail() } })
        }
        if (parked) {
            savedCabin?.let { v ->
                tally.begin()
                light.setCabinLightSwitch(v, { _ ->
                    confirmCabinLight(v) { same ->
                        onLog(if (same) "실내등 켬" else "실내등: 돌아가지 않음")
                        if (same) tally.ok() else tally.fail()
                    }
                }, { e -> Log.e(TAG, "되돌리기 실패", e); handler.post { tally.fail() } })
            }
            savedReading.forEach { (a, v) ->
                tally.begin()
                light.setReadingLightSwitch(a, v, { _ ->
                    confirmReadingLight(a, v) { same ->
                        report(onLog, if (same) "독서등 켬" else "독서등: 돌아가지 않음", a.label())
                        if (same) tally.ok() else tally.fail()
                    }
                }, { e -> Log.e(TAG, "되돌리기 실패", e); handler.post { tally.fail() } })
            }
            savedCabin = null
            savedReading.clear()
        }
        tally.allSent()
    }

    /** 무드등 밝기를 한 단계씩 바꾼다. 마지막 단계 뒤에 다시 읽어 확인한다. done(null)은 그사이 다시 어둡게 해서 멈췄다는 뜻이다 */
    private fun stepAmbient(area: SeatArea, levels: List<Int>, index: Int, target: Int, fadeId: Int, done: (Boolean?) -> Unit) {
        if (fadeId != ambientFadeId) return done(null)
        vehicle.getLight().setAmbientLightBrightness(area, levels[index], { _, _ ->
            handler.post {
                if (index == levels.lastIndex) {
                    confirmAmbient(area, target) { same -> done(same) }
                } else {
                    handler.postDelayed({ stepAmbient(area, levels, index + 1, target, fadeId, done) }, AMBIENT_FADE_STEP_MS)
                }
            }
        }, { e -> Log.e(TAG, "무드등 밝히기 실패", e); handler.post { done(false) } })
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        if (ready) {
            windowListeners.forEach { (area, listener) -> vehicle.getWindow().unregisterWindowPosition(area, listener) }
            windowListeners.clear()
            vehicle.release()
        }
    }

    /** 공조를 바꿀 구역: 뒷좌석만 따로 되면 그것만, 안 되면 전부(에뮬레이터는 실내 전체) */
    private fun <T> targetAreas(bounds: Map<SeatArea, T>): List<SeatArea> =
        bounds.keys.filter { it.isRearOnly() }.ifEmpty { bounds.keys.toList() }

    private fun SeatArea.isRearOnly() = values.isNotEmpty() && values.none { it.name.startsWith("ROW_1") }
    private fun SeatArea.label() = values.joinToString("+") { it.name }

    /**
     * 실내를 바꾼 결과 한 줄을 알린다. 화면으로 가는 글에는 SDK 구역 이름(예: ROW_2_LEFT)을 넣지 않고,
     * 구역은 로그에만 남긴다.
     */
    private fun report(onLog: (String) -> Unit, line: String, area: String) {
        Log.i(TAG, "$line [$area]")
        onLog(line)
    }

    companion object {
        private const val TAG = "RearCareCabin"

        /** 바꾼 뒤 다시 읽기까지 기다리는 시간. 에뮬레이터는 2ms 안에 바뀐다. 실제 차에서 얼마나 걸리는지는 모른다 */
        private const val CONFIRM_DELAY_MS = 1_000L

        /**
         * 무드등을 천천히 밝히는 단계와 간격. 합쳐서 약 10초다.
         * 시연에서 기다릴 수 있는 길이로 정한 값이고, 아이에게 좋다는 근거가 있는 숫자가 아니다.
         */
        private const val AMBIENT_FADE_STEPS = 10
        private const val AMBIENT_FADE_STEP_MS = 1_000L
    }
}
