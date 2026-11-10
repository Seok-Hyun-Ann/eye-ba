package com.rearcare.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 차의 음성 비서(Gleo)가 앱을 부르는 입구(Pleos "Gleo Interface").
 *
 *   운전자가 차에 "아기 뭐 해?"라고 말함 → Gleo가 assets/schema.json을 보고 CHECK_CHILD_STATUS를 보냄
 *   → 여기서 받아 지금 상태를 Gleo에 돌려줌(7초 안에 답해야 한다)
 *
 * mode는 silent다. Gleo가 영어 설명을 다시 지어 말하지 않게 하고, 확정한 문장(StatusWords.answer)을 앱이 직접 말한다.
 * 앱이 꺼져 있으면 아이를 볼 수 없으므로 실패로 답한다. 이때는 Gleo가 말한다.
 */
class GleoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 문서 예시는 function 이름 그대로이고, 차에 있는 내비 앱은 "패키지명.이름"으로 받는다. 어느 쪽으로 와도 받는다
        val action = intent.action ?: return
        if (action != ACTION_CHECK && action != "${context.packageName}.$ACTION_CHECK") return
        Log.i(TAG, "Gleo가 불렀다: $action")
        val answer = GleoBridge.answer?.invoke()
        val response = JSONObject().apply {
            put("functionName", ACTION_CHECK)
            if (answer != null) {
                put("status", "success")
                put("message", answer.message)
                put("mode", "silent")
                // 대화 맥락용. silent일 때 Gleo가 볼 수 있는 것은 이 응답뿐이다
                put("data", JSONArray().put(JSONObject().put("spokenAnswer", answer.spoken)).toString())
            } else {
                put("status", "fail")
                put("message", "The rear seat care app is not running, so the child cannot be checked.")
            }
        }
        context.sendBroadcast(
            Intent(ACTION_RESPONSE).apply {
                putExtra("packageName", context.packageName)
                putExtra("response", response.toString())
            },
        )
        Log.i(TAG, "Gleo에 답함: ${response.getString("status")}")
    }

    companion object {
        private const val TAG = "RearCareGleo"
        const val ACTION_CHECK = "CHECK_CHILD_STATUS" // assets/schema.json의 function 이름과 같아야 한다
        private const val ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    }
}

/** Gleo의 호출을 지금 떠 있는 상태 화면으로 잇는다. 화면이 없으면 answer는 null이다. 화면 스레드에서만 쓴다 */
object GleoBridge {
    /** message: Gleo에 주는 영어 설명, spoken: 앱이 운전자에게 말한 문장 */
    class Answer(val message: String, val spoken: String)

    var answer: (() -> Answer)? = null
}
