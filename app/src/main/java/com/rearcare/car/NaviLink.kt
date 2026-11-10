package com.rearcare.car

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 차 내비와 잇는 부품. 읽기와 장소 열기만 한다. 경로나 경유지는 바꾸지 않는다.
 *
 * - 목적지 이름과 지금 주소를 기억해 둔다. 값은 비동기로 오므로 refresh() 뒤 조금 기다렸다가 읽는다.
 * - 도착하면 안내가 끝나 목적지 값이 비므로, 빈 값이 와도 기억을 지우지 않는다. 안내를 취소했을 때만 지운다.
 * - 내비 지도에 여러 곳을 찍는 공개 메서드는 없다. 한 곳씩 연다.
 *
 * 알림은 화면 스레드로 옮겨서 넘긴다. 다 쓰면 release().
 */
class NaviLink(context: Context) {

    data class Place(
        val name: String,
        val address: String,
        val latitude: Double?,
        val longitude: Double?,
        val poiId: String = "",
        val poiProvider: String = "",
    )

    private val helper = NaviHelper(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var connected = false

    /** 기억해 둔 목적지. 안내를 켜지 않았으면 비어 있다 */
    var destination: Place? = null
        private set

    /** 지금 있는 곳. 이름은 비어 있고 주소는 동네까지 온다 */
    var here: Place? = null
        private set

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val d = destinationInfo
            if (d.name.isBlank()) return
            handler.post {
                destination = Place(d.name, d.address, d.latitude, d.longitude, d.poiId, d.poiProvider)
            }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            val a = destinationArrivedInfo
            Log.i(TAG, "도착 알림: $a")
            if (a.name.isBlank()) return
            handler.post {
                if (destination?.name != a.name) destination = Place(a.name, a.address, null, null)
            }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val c = currentLocationInfo
            handler.post { here = Place("", c.address, c.latitude, c.longitude) }
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.i(TAG, "경로 시작: ${routeStartInfo.name}")
            request { helper.getDestinationInfo() }
        }

        override fun onDestinationChanged() {
            Log.i(TAG, "목적지 바뀜")
            request { helper.getDestinationInfo() }
        }

        override fun onRouteCancelled() {
            Log.i(TAG, "경로 취소")
            handler.post { destination = null }
        }

        override fun onRouteEnded() {
            Log.i(TAG, "경로 끝")
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.e(TAG, "내비 오류: $errorCode")
        }
    }

    fun start() {
        helper.addListener(listener)
        helper.initialize({
            connected = true
            Log.i(TAG, "내비에 연결됨")
            refresh()
        }, { e -> Log.e(TAG, "내비 연결 실패", e) })
    }

    /** 새 기록을 시작한다. 전 기록의 목적지가 섞이지 않게 비우고 지금 값을 다시 읽는다 */
    fun beginTrip() {
        destination = null
        refresh()
    }

    /** 목적지와 지금 위치를 다시 읽어 달라고 한다. 값은 조금 뒤에 온다 */
    fun refresh() {
        request {
            helper.getDestinationInfo()
            helper.getCurrentLocationInfo()
        }
    }

    /** 내비 화면에 그 장소를 연다. 장소 번호가 있으면 장소 창을, 없으면 이름 검색을 띄운다. 요청을 못 보내면 false */
    fun open(trip: Trip): Boolean {
        val screen = if (trip.poiId.isNotBlank()) {
            NavigationScreenRequest.POIDetailPopup(trip.poiId, trip.poiProvider)
        } else {
            NavigationScreenRequest.SearchPlace(trip.name)
        }
        return request { helper.requestNavigationScreen(screen) }
    }

    private fun request(call: () -> Unit): Boolean {
        if (!connected) {
            Log.w(TAG, "내비에 연결되지 않아 요청을 보내지 못함")
            return false
        }
        return try {
            call()
            true
        } catch (e: Exception) {
            Log.e(TAG, "내비 요청 실패", e)
            false
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        try {
            helper.removeListener(listener)
            helper.release()
        } catch (e: Exception) {
            Log.e(TAG, "내비 연결 끊기 실패", e)
        }
    }

    private companion object {
        const val TAG = "RearCareNavi"
    }
}
