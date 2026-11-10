package com.rearcare.car

import android.content.Context
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions

/**
 * 소리를 듣고 "아기 울음"과 "개 소리" 점수를 내는 부품.
 *
 * - 구글의 공개 모델 YAMNet을 MediaPipe로 돌린다. YAMNet은 소리 521종을 구분하도록 이미 학습돼 있다.
 *   우리가 직접 학습시키지 않는다.
 * - 모델은 소리를 약 1초(0.975초) 구간으로 잘라, 구간마다 "이 소리가 무엇일 확률"을 0~1 점수로 준다.
 * - 이 부품은 점수만 낸다. "울고 있다"고 확정하는 규칙(몇 초 이상 이어지면 등)은 따로 둔다.
 */
class SoundAnalyzer(context: Context) : AutoCloseable {

    /** 1초 구간 하나의 결과 */
    data class Window(
        val cryScore: Float,
        val dogScore: Float,
    )

    private val classifier: AudioClassifier = AudioClassifier.createFromOptions(
        context,
        AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_FILE).build())
            .setRunningMode(RunningMode.AUDIO_CLIPS) // 정해진 길이의 소리를 한 번에 넣는 방식
            .setMaxResults(TOP_N)
            .build(),
    )

    /**
     * samples: -1~1 사이 숫자로 된 소리(모노), sampleRate: 1초에 몇 개 숫자인지(예: 44100).
     * 모델은 16000Hz로 학습됐지만, MediaPipe가 알아서 바꿔 넣어 준다.
     */
    fun analyze(samples: FloatArray, sampleRate: Int): List<Window> {
        val format = AudioData.AudioDataFormat.builder()
            .setNumOfChannels(1)
            .setSampleRate(sampleRate.toFloat())
            .build()
        val audio = AudioData.create(format, samples.size)
        audio.load(samples)

        val result = classifier.classify(audio)
        return result.classificationResults().map { window ->
            val scores = window.classifications().first().categories().map { it.categoryName() to it.score() }
            Window(
                cryScore = scores.filter { it.first in CRY_LABELS }.maxOfOrNull { it.second } ?: 0f,
                dogScore = scores.filter { it.first in DOG_LABELS }.maxOfOrNull { it.second } ?: 0f,
            )
        }
    }

    override fun close() = classifier.close()

    /** 판정 결과: 울음, 개 소리, 둘 다 아님 */
    enum class Sound { CRY, DOG, NONE }

    companion object {
        /**
         * 울음 점수와 개 점수로 하나를 고른다.
         * - 둘 다 문턱보다 낮으면 "아님"
         * - 둘 다 높으면 더 높은 쪽. 아기 울음이 개 소리 점수도 조금 받는 경우가 있어서다(ESC-50 시험에서 확인)
         * 문턱 0.3은 공개 자료 200개 시험에서 고른 값이다.
         */
        fun decide(cryScore: Float, dogScore: Float): Sound = when {
            maxOf(cryScore, dogScore) < THRESHOLD -> Sound.NONE
            cryScore >= dogScore -> Sound.CRY
            else -> Sound.DOG
        }

        const val THRESHOLD = 0.3f

        private const val MODEL_FILE = "yamnet.tflite"

        // 구간마다 점수가 높은 10개만 받는다. 목표 소리가 10위 안에 없으면 0점으로 본다
        private const val TOP_N = 10

        // YAMNet이 쓰는 소리 이름(AudioSet 분류) 중 우리가 볼 것들
        val CRY_LABELS = setOf("Baby cry, infant cry", "Crying, sobbing")
        val DOG_LABELS = setOf("Dog", "Bark", "Yip", "Howl", "Bow-wow", "Growling", "Whimper (dog)")
    }
}
