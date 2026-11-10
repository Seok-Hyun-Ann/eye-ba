package com.rearcare.car

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * 차가 말하기(TTS). 운전자가 차의 음성 비서로 물었을 때 대답하는 데만 쓴다.
 * 앱은 먼저 말하지 않는다. 주행 중에는 내비 음성이 먼저다.
 * 앱이 직접 듣는 일도 없다. 운전자 말은 Gleo가 듣는다.
 *
 * - speak(문장): 줄을 세워 하나씩 말한다. 말하는 도중에 다음 문장이 와도 겹치지 않는다.
 * - 상태(current, queue)는 화면 스레드에서만 바꾼다. SDK 콜백은 다른 스레드에서 올 수 있어서 handler로 옮긴다.
 */
class VoiceIO(
    context: Context,
    private val onStatus: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context, TtsMode.ON_DEVICE)

    private var ttsReady = false
    private val queue = ArrayDeque<String>()
    private var current: String? = null // 지금 말하는 문장

    fun start() {
        tts.initialize(object : TtsInitListener {
            override fun onSuccess() {
                handler.post {
                    // 리스너는 초기화가 끝난 뒤에 붙인다(먼저 붙이면 앱이 멈춘다)
                    tts.addEventListener(ttsEvents)
                    tts.setLocale(Locale.KOREA)
                    ttsReady = true
                    speakNext()
                }
            }

            override fun onFailure(errorType: TtsErrorType) {
                onStatus("음성 안내 준비 실패: $errorType")
            }
        })
    }

    /** 문장을 줄에 세운다. 지금 말하는 중이 아니면 바로 말한다 */
    fun speak(text: String) {
        handler.post {
            queue.addLast(text)
            speakNext()
        }
    }

    private fun speakNext() {
        if (!ttsReady || current != null) return
        val next = queue.removeFirstOrNull() ?: return
        current = next
        Log.i(TAG, "말하기: $next")
        tts.speak(next)
        // 끝났다는 알림이 오지 않는 경우에 대비해, 일정 시간이 지나면 다 말한 것으로 본다
        handler.postDelayed(speakTimeout, SPEAK_TIMEOUT_MS)
    }

    private val speakTimeout = Runnable {
        Log.w(TAG, "말하기 끝 알림이 오지 않아 다음으로 넘어감")
        doneSpeaking()
    }

    private val ttsEvents = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onDone() = finishedSpeaking()
        override fun onStop() = finishedSpeaking()
        override fun onError(errorType: TtsErrorType) {
            Log.e(TAG, "말하기 실패: $errorType")
            finishedSpeaking()
        }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private fun finishedSpeaking() {
        handler.post { doneSpeaking() }
    }

    private fun doneSpeaking() {
        handler.removeCallbacks(speakTimeout)
        current = null
        speakNext()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        if (ttsReady) tts.removeEventListener(ttsEvents)
        tts.release()
    }

    companion object {
        private const val TAG = "RearCareVoiceIO"
        private const val SPEAK_TIMEOUT_MS = 15_000L
    }
}
