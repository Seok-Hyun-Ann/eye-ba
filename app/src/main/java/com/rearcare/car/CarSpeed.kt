package com.rearcare.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlin.math.abs

/**
 * 차의 지금 속도(km/h)를 읽는 부품. 주행 중 수면의 조건 비교에 쓴다.
 *
 * Pleos Vehicle SDK에는 지금 속도를 읽는 기능이 없다(정속 주행 목표 속도만 있다, 2026-11-06 SDK 2.3.2 확인).
 * 그래서 주행 규제를 읽을 때 쓰는 Android 차 서비스(android.car)의 표준 속도 값(PERF_VEHICLE_SPEED)을 쓴다.
 * 이 값을 읽으려면 사용자가 허락하는 권한 android.car.permission.CAR_SPEED가 필요하다. 허락이 없으면 속도는 모름(null)이다.
 */
class CarSpeed(private val context: Context) {

    /** 마지막으로 받은 속도(km/h). 아직 모르거나 권한이 없으면 null */
    @Volatile var kph: Float? = null
        private set

    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var lastLoggedKph = -1

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            // 값은 초당 미터다. 후진이면 음수일 수 있어 크기만 쓴다
            val metersPerSecond = value.value as? Float ?: return
            val now = abs(metersPerSecond) * MPS_TO_KPH
            kph = now
            // 확인용 로그는 정수 km/h가 바뀔 때만 남긴다
            val rounded = Math.round(now)
            if (rounded != lastLoggedKph) {
                lastLoggedKph = rounded
                Log.i(TAG, "속도 $rounded km/h")
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "속도 값 오류")
        }
    }

    val permitted: Boolean
        get() = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** 권한이 있으면 속도 알림을 받기 시작한다. 이미 받고 있으면 아무것도 하지 않는다. 권한을 받은 뒤 다시 불러도 된다 */
    fun start() {
        if (manager != null) return
        if (!permitted) {
            Log.w(TAG, "속도 권한이 없어 속도를 모름으로 둔다")
            return
        }
        val c = Car.createCar(context)
        if (c == null) {
            Log.e(TAG, "Car 서비스 연결 실패. 속도를 알 수 없다")
            return
        }
        val m = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        if (m == null) {
            Log.e(TAG, "차 속성 매니저를 얻지 못함")
            c.disconnect()
            return
        }
        try {
            m.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        } catch (e: SecurityException) {
            Log.e(TAG, "속도 읽기 거절", e)
            c.disconnect()
            return
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "이 차는 속도 값을 주지 않는다", e)
            c.disconnect()
            return
        }
        car = c
        manager = m
    }

    fun stop() {
        manager?.unregisterCallback(callback)
        manager = null
        car?.disconnect()
        car = null
        kph = null
    }

    companion object {
        private const val TAG = "RearCareSpeed"
        const val PERMISSION = "android.car.permission.CAR_SPEED"
        private const val MPS_TO_KPH = 3.6f
    }
}
