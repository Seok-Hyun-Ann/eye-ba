package com.rearcare.car

import android.app.Activity
import android.os.Bundle

/**
 * 속도 권한만 묻고 닫히는 투명한 화면. 작은 창에는 권한 창을 띄울 수 없어 "네"라고 답한 뒤 잠깐 연다.
 * 닫히면 RearStatusActivity가 속도 읽기를 다시 시작한다(RearStatusActivity.screenWatcher).
 */
class SpeedPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestPermissions(arrayOf(CarSpeed.PERMISSION), REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }

    private companion object {
        const val REQUEST = 27
    }
}
