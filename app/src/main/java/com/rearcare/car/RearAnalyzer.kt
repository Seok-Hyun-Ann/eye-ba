package com.rearcare.car

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock

/**
 * 1초 분량의 소리와 가장 최근 영상 한 장을 받아, 상태 추적기(ChildStateTracker)에 넣을 관찰 하나로 만든다.
 * 얼굴 모델(FaceAnalyzer)과 소리 모델(SoundAnalyzer)을 함께 돌린다. 화면 스레드가 아닌 곳에서 불러야 한다.
 */
class RearAnalyzer(context: Context) : AutoCloseable {

    /** 관찰 하나와, 확인용 숫자들(화면에는 두지 않고 로그로만 남긴다) */
    data class Result(
        val observation: ChildStateTracker.Observation,
        val cryScore: Float,
        val dogScore: Float,
        val facesFound: Int,
        val elapsedMs: Long,
        val cryFaceScore: Float? = null, // 우는 얼굴 점수(CryFace). 얼굴을 못 찾으면 null
        val frame: Bitmap? = null, // 분석한 사진
        val focus: PhotoFit.Focus? = null, // 그 사진에서 찾은 가장 큰 얼굴의 가운데. 못 찾으면 null
    )

    private val face = FaceAnalyzer(context)
    private val sound = SoundAnalyzer(context)

    fun analyze(timeMs: Long, frame: Bitmap?, samples: FloatArray, sampleRate: Int): Result {
        val started = SystemClock.elapsedRealtime()

        // 얼굴: 가장 큰 얼굴 하나만 본다. 눈 값이 비어 있으면(모델이 표정 값을 못 냄) 얼굴을 못 찾은 것으로 친다
        val faces = frame?.let { face.analyze(it) }.orEmpty()
        val first = faces.firstOrNull()
        val reading = first
            ?.takeIf { !it.eyesClosed.isNaN() }
            ?.let {
                ChildStateTracker.FaceReading(
                    it.eyesClosed, it.yawDeg, it.noseOffset,
                    cryingFace = CryFace.isCrying(it.blend),
                    maybeCrying = CryFace.isMaybeCrying(it.blend),
                    gazeDeg = Gaze.degrees(it.yawDeg, it.blend),
                )
            }

        // 소리: 1초 안의 구간들 중 가장 높은 점수를 쓴다. 판정은 소리 시험에서 고른 규칙 그대로
        val windows = sound.analyze(samples, sampleRate)
        val cry = windows.maxOfOrNull { it.cryScore } ?: 0f
        val dog = windows.maxOfOrNull { it.dogScore } ?: 0f
        val crying = SoundAnalyzer.decide(cry, dog) == SoundAnalyzer.Sound.CRY

        return Result(
            observation = ChildStateTracker.Observation(timeMs, reading, crying),
            cryScore = cry,
            dogScore = dog,
            facesFound = faces.size,
            elapsedMs = SystemClock.elapsedRealtime() - started,
            cryFaceScore = first?.let { CryFace.score(it.blend) },
            frame = frame,
            focus = first?.let { PhotoFit.Focus((it.left + it.right) / 2, (it.top + it.bottom) / 2) },
        )
    }

    override fun close() {
        face.close()
        sound.close()
    }
}
