package com.rearcare.car

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlin.math.asin

/**
 * 사진 한 장에서 얼굴을 찾고, 눈을 감았는지와 고개를 어느 쪽으로 돌렸는지를 숫자로 낸다.
 *
 * - 구글의 공개 모델 MediaPipe Face Landmarker를 쓴다. 얼굴 위 478개 점과,
 *   표정 값 52개(예: eyeBlinkLeft = 왼쪽 눈 감김 정도 0~1), 머리 회전값(4x4 행렬)을 준다.
 * - 이 부품은 숫자만 낸다. "잠들었다"처럼 확정하는 규칙(몇 초 이상 감고 있으면 등)은 따로 둔다.
 * - 모델은 주로 어른 얼굴로 학습됐다. 아기 얼굴에서 얼마나 맞는지는 공개 사진으로 따로 확인했다.
 */
class FaceAnalyzer(context: Context) : AutoCloseable {

    /**
     * 얼굴 하나의 결과. 위치(left~bottom)는 사진 크기를 1로 본 비율.
     * 왼쪽/오른쪽은 "사진에 보이는 쪽" 기준이다(사람 기준과 반대일 수 있다).
     */
    data class Face(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val blinkLeft: Float, val blinkRight: Float,
        val yawDeg: Float,
        val noseOffset: Float,
        /** 표정 값 52개 전부(이름 → 0~1). 예: jawOpen = 입 벌림, browInnerUp = 미간 올림. 우는 얼굴 판단(CryFace)에 쓴다 */
        val blend: Map<String, Float> = emptyMap(),
    ) {
        val area get() = (right - left) * (bottom - top)
        /** 두 눈 감김 정도의 평균. 1에 가까울수록 감음 */
        val eyesClosed get() = (blinkLeft + blinkRight) / 2
    }

    private val landmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_FILE).build())
            .setRunningMode(RunningMode.IMAGE) // 사진 한 장씩
            .setNumFaces(MAX_FACES)
            .setOutputFaceBlendshapes(true) // 표정 값(눈 감김 등)
            .setOutputFacialTransformationMatrixes(true) // 머리 회전값
            .build(),
    )

    /** 얼굴을 큰 것부터 돌려준다. 사진 속 아기 얼굴이 보통 가장 크다 */
    fun analyze(bitmap: Bitmap): List<Face> {
        val result = landmarker.detect(BitmapImageBuilder(bitmap).build())
        val shapes = result.faceBlendshapes().orElse(emptyList())
        val matrices = result.facialTransformationMatrixes().orElse(emptyList())
        return result.faceLandmarks().mapIndexed { i, points ->
            val blend = shapes.getOrNull(i).orEmpty().associate { it.categoryName() to it.score() }
            val yaw = matrices.getOrNull(i)?.let { yawOf(it) } ?: Float.NaN
            // 코끝(1번 점)이 얼굴 양옆 끝(234번 → 454번 점)을 잇는 선 위 어디쯤 있는지: 가운데면 0, 234번 쪽 끝이면 -0.5.
            // 사진의 가로축이 아니라 "얼굴의 가로축" 기준이라 고개를 갸웃하거나 누워 있어도 같은 값이 나온다
            val ax = points[EDGE_B].x() - points[EDGE_A].x()
            val ay = points[EDGE_B].y() - points[EDGE_A].y()
            val nx = points[NOSE].x() - points[EDGE_A].x()
            val ny = points[NOSE].y() - points[EDGE_A].y()
            val noseOffset = (nx * ax + ny * ay) / (ax * ax + ay * ay) - 0.5f
            Face(
                left = points.minOf { it.x() }, top = points.minOf { it.y() },
                right = points.maxOf { it.x() }, bottom = points.maxOf { it.y() },
                blinkLeft = blend["eyeBlinkLeft"] ?: Float.NaN,
                blinkRight = blend["eyeBlinkRight"] ?: Float.NaN,
                yawDeg = yaw,
                noseOffset = noseOffset,
                blend = blend,
            )
        }.sortedByDescending { it.area }
    }

    /**
     * 4x4 행렬(열 우선 저장)의 회전 부분에서 고개를 좌우로 돌린 각도(yaw)를 꺼낸다.
     * 부호가 어느 쪽인지는 시험 사진으로 확인했다.
     */
    private fun yawOf(m: FloatArray): Float {
        fun r(row: Int, col: Int) = m[col * 4 + row]
        return Math.toDegrees(asin((-r(2, 0)).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    override fun close() = landmarker.close()

    companion object {
        private const val MODEL_FILE = "face_landmarker.task"
        private const val MAX_FACES = 3 // 여러 얼굴 중 가장 큰 얼굴을 고르려고 3개까지 찾는다
        private const val NOSE = 1
        private const val EDGE_A = 234
        private const val EDGE_B = 454
    }
}
