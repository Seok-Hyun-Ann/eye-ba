package com.rearcare.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.app.Activity
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 차가 정차 중인지 주행 중인지 알려 주는 부품. 두 가지 정보를 함께 본다.
 *
 * 1. 기어 (Pleos Vehicle SDK): P/R/N/D. 기어가 바뀌면 바로 알려 준다.
 * 2. 주행 규제 (Android CarUxRestrictionsManager): 플랫폼이 지금 "영상 금지" 같은 규제를 거는지.
 *    속도·기어·브레이크를 보고 플랫폼이 정하며, 영상을 보여 줘도 되는지는 최종적으로 이걸 따라야 한다.
 *
 * 영상은 "P단이고, 플랫폼도 영상을 막지 않을 때"만 허용한다. 둘 중 하나라도 모르면 허용하지 않는다.
 *
 * 사용법: start()로 시작하면 상태가 바뀔 때마다 onChanged가 화면 스레드에서 불린다. 다 쓰면 stop().
 */
class DrivingStateMonitor(
    private val context: Context,
    /**
     * 기어 알림까지 받는가. 같은 앱의 두 화면이 기어 알림을 받다가 한쪽이 해제하면 다른 쪽 알림도 끊긴다
     * (2026-11-04 설정 화면을 다녀온 뒤 상태 화면이 D단을 모르는 것을 시험에서 찾음). 그래서 기어는 상태 화면만 받는다.
     */
    private val watchGear: Boolean = true,
    private val onChanged: (DrivingState) -> Unit,
) {

    /** 지금 차 상태. 아직 모르는 값은 null이다. */
    data class DrivingState(
        val gear: VehicleGear? = null,
        val uxRestrictions: CarUxRestrictions? = null,
    ) {
        /** 플랫폼이 영상을 막고 있는가. 규제 정보를 아직 못 받았으면 null */
        val videoBlockedByPlatform: Boolean?
            get() = uxRestrictions?.let { (it.activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) != 0 }

        /** 뒷좌석 영상을 보여 줘도 되는가 */
        val videoAllowed: Boolean
            get() = gear == VehicleGear.GEAR_PARK && videoBlockedByPlatform == false
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var state = DrivingState()

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null

    // 기어가 바뀔 때마다 Vehicle SDK가 불러 주는 리스너.
    // SDK가 값을 비워서(null) 보낼 수도 있다. 그러면 "모름"이 되어 영상은 허용되지 않는다
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = update { it.copy(gear = gear) }
        override fun onFailed(e: Exception) {
            Log.e(TAG, "기어 변화 알림 실패", e)
        }
    }

    // 주행 규제가 바뀔 때마다 플랫폼이 불러 주는 리스너
    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
        update { it.copy(uxRestrictions = restrictions) }
    }

    fun start() {
        if (watchGear) startGear()
        startUxRestrictions()
    }

    private fun startGear() {
        val v = Vehicle(context)
        try {
            v.initialize()
        } catch (e: Exception) {
            Log.e(TAG, "Vehicle 초기화 실패. 기어를 알 수 없다", e)
            return
        }
        vehicle = v
        // 지금 기어를 한 번 읽고, 이후 바뀔 때마다 알림을 받는다
        v.getGear().getCurrentGearSelectionState(
            { gear -> update { it.copy(gear = gear) } },
            { error -> Log.e(TAG, "현재 기어 읽기 실패", error) },
        )
        v.getGear().registerGearSelection(gearListener)
    }

    private fun startUxRestrictions() {
        val c = Car.createCar(context)
        if (c == null) {
            Log.e(TAG, "Car 서비스 연결 실패. 주행 규제를 알 수 없다")
            return
        }
        car = c
        val manager = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        if (manager == null) {
            Log.e(TAG, "주행 규제 매니저를 얻지 못함")
            return
        }
        uxManager = manager
        // 지금 규제를 한 번 읽고, 이후 바뀔 때마다 알림을 받는다
        val current = manager.currentCarUxRestrictions
        update { it.copy(uxRestrictions = current) }
        manager.registerListener(uxListener)
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        vehicle?.let {
            it.getGear().unregisterGearSelection(gearListener)
            it.release()
        }
        vehicle = null
        uxManager?.unregisterListener()
        uxManager = null
        car?.disconnect()
        car = null
    }

    // 기어 알림과 규제 알림은 서로 다른 스레드에서 올 수 있다.
    // 둘 다 화면 스레드 하나로 모아 차례대로 처리해야 state가 꼬이지 않는다
    private fun update(change: (DrivingState) -> DrivingState) {
        mainHandler.post {
            val newState = change(state)
            if (newState != state) {
                state = newState
                onChanged(state)
            }
        }
    }

    companion object {
        private const val TAG = "RearCareDrive"

        /**
         * 주행 중에 쓰면 안 되는 화면(설정, 다닌 곳, 통계)을 주행으로 바뀌면 닫는다. 돌려준 감시는 화면이 닫힐 때 stop()한다.
         * 플랫폼은 P단이 아니면 영상을 막는다. 그래서 기어 알림을 받지 않아도 그것으로 주행을 안다.
         * 기어 알림을 받지 않는 까닭은 위 watchGear 설명에 있다.
         * beforeClose: 화면을 닫기 전에 할 일. 이 화면이 띄운 다른 앱의 화면(사진 선택기)은 함께 닫히지 않으므로 여기서 닫는다.
         */
        fun closeWhenDriving(activity: Activity, beforeClose: () -> Unit = {}): DrivingStateMonitor =
            DrivingStateMonitor(activity, watchGear = false) { state ->
                if (state.videoBlockedByPlatform == true) {
                    beforeClose()
                    activity.finish()
                }
            }.also { it.start() }

        /** 규제 숫자(비트 묶음)를 사람이 읽을 이름으로 바꾼다. 확인·로그용 */
        fun describe(restrictions: CarUxRestrictions?): String {
            if (restrictions == null) return "모름"
            val active = restrictions.activeRestrictions
            if (active == CarUxRestrictions.UX_RESTRICTIONS_BASELINE) return "없음"
            val names = listOf(
                CarUxRestrictions.UX_RESTRICTIONS_NO_DIALPAD to "NO_DIALPAD",
                CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING to "NO_FILTERING",
                CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH to "LIMIT_STRING_LENGTH",
                CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD to "NO_KEYBOARD",
                CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO to "NO_VIDEO",
                CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT to "LIMIT_CONTENT",
                CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP to "NO_SETUP",
                CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE to "NO_TEXT_MESSAGE",
                CarUxRestrictions.UX_RESTRICTIONS_NO_VOICE_TRANSCRIPTION to "NO_VOICE_TRANSCRIPTION",
            )
            val on = names.filter { (bit, _) -> (active and bit) != 0 }.joinToString(", ") { it.second }
            return "$on (글자 최대 ${restrictions.maxRestrictedStringLength}자, 항목 최대 ${restrictions.maxCumulativeContentItems}개)"
        }
    }
}
