package com.rearcare.car

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 뒷창문이 열렸을 때 내는 말 없는 짧은 알림음. 앱이 먼저 내는 소리는 이것뿐이다.
 *
 * - 파일 없이 코드로 만든다. 출처를 따로 기록할 소리가 없다.
 * - 내비 음성이 먼저다. 오디오 포커스를 잠깐만 받는 방식(MAY_DUCK)으로 요청하고, 차가 주지 않으면 소리를 내지 않는다. 화면 질문은 그대로 뜬다.
 * - 음량은 앱이 바꾸지 않는다. 차의 알림 음량을 따른다.
 * - 귀로 확인한 소리가 아니다. 에뮬레이터에서는 포커스 결과와 재생 위치를 로그로 본다.
 */
class AlertTone(context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private val samples = chime()
    private val track = AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(samples.size * 2)
        .build()
        .also { it.write(samples, 0, samples.size) }
    private var playing = false

    fun play() {
        if (playing) return
        val result = audio.requestAudioFocus(focus)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.i(TAG, "알림음 생략: 오디오 포커스를 받지 못함($result)")
            return
        }
        playing = true
        track.stop()
        track.reloadStaticData()
        track.play()
        Log.i(TAG, "알림음 시작: 포커스 받음, 재생 상태 ${track.playState}")
        handler.postDelayed({
            Log.i(TAG, "알림음 끝: 재생 위치 ${track.playbackHeadPosition}/${samples.size}")
            audio.abandonAudioFocusRequest(focus)
            playing = false
        }, samples.size * 1000L / SAMPLE_RATE + END_MARGIN_MS)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        audio.abandonAudioFocusRequest(focus)
        track.release()
    }

    private companion object {
        const val TAG = "RearCareAlert"
        const val SAMPLE_RATE = 44_100
        const val END_MARGIN_MS = 100L
        const val NOTE_MS = 180
        const val EDGE_MS = 15 // 소리의 앞뒤를 부드럽게 해서 틱 소리가 나지 않게 한다
        const val VOLUME = 0.5
        val NOTES = doubleArrayOf(880.0, 660.0) // 높은음 다음 낮은음. 경고음보다 부드럽게 들리도록 고른 값이고 근거가 있는 값은 아니다

        fun chime(): ShortArray {
            val perNote = SAMPLE_RATE * NOTE_MS / 1000
            val edge = SAMPLE_RATE * EDGE_MS / 1000
            val out = ShortArray(perNote * NOTES.size)
            NOTES.forEachIndexed { n, hz ->
                for (i in 0 until perNote) {
                    val envelope = min(1.0, min(i, perNote - 1 - i).toDouble() / edge)
                    val value = sin(2 * PI * hz * i / SAMPLE_RATE) * envelope * VOLUME
                    out[n * perNote + i] = (value * Short.MAX_VALUE).toInt().toShort()
                }
            }
            return out
        }
    }
}
