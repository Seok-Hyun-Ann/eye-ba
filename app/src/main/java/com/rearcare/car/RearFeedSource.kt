package com.rearcare.car

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * 뒷좌석 영상·소리를 차량 앱에 넣어 주는 곳.
 *
 * 실제 차에서는 뒷좌석 카메라와 마이크가 이 역할을 한다(아직 연결하지 않았다). 공모전 시연은 녹화해 둔 대본으로 대신한다.
 * 앱의 나머지 부분은 이 약속(인터페이스)만 보므로, 입력이 바뀌면 구현만 바꿔 끼우면 된다.
 * - DemoFeedSource: 시연용. 앱 저장소의 사진·소리 파일을 대본대로 흘려 보낸다.
 * 앱이 어느 구현을 쓸지는 DemoSetup.feed가 정한다.
 */
interface RearFeedSource {

    /** 영상과 소리를 받는 쪽. 부르는 스레드는 화면 스레드가 아니다 */
    interface Listener {
        fun onFrame(frame: Bitmap, timeMs: Long)

        /** 약 1초 분량의 소리(-1~1, 모노) */
        fun onAudio(samples: FloatArray, sampleRate: Int, timeMs: Long)

        fun onEnded() {}
        fun onError(message: String)
    }

    fun start(listener: Listener)
    fun stop()
}

/**
 * 시험용 입력: files/demo/scenario.csv 대본에 따라 사진 한 장과 소리 1초를 1초마다 보낸다.
 *
 * 대본 한 줄 = "초,사진,소리" (# 으로 시작하면 설명 줄)
 * - 사진: files/demo 안의 파일 이름. 같은 사진을 그 시간 동안 계속 보낸다(정지 영상)
 * - 소리: files 안의 WAV 경로. 여러 개면 | 로 잇고, 차례로 틀다가 끝나면 처음부터 다시 튼다
 * - 마지막 줄의 장면은 stop()까지 계속 이어진다
 * 실제 아기를 찍지 않았으므로 공개 사진과 공개 소리로 상황을 흉내 낸다. 출처: files/demo/SOURCES.csv
 */
class DemoFeedSource(private val dir: File, private val filesRoot: File) : RearFeedSource {

    private class Segment(val seconds: Int, val image: String, val sounds: List<String>)

    @Volatile private var running = false
    private var thread: Thread? = null

    override fun start(listener: RearFeedSource.Listener) {
        val script = File(dir, DemoSetup.SCRIPT)
        if (!script.exists()) {
            listener.onError("시연 대본이 없습니다: $script")
            return
        }
        val segments = script.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val (sec, image, sound) = line.split(",").map { it.trim() }
                Segment(sec.toInt(), image, sound.split("|").map { it.trim() })
            }
        running = true
        thread = Thread({ play(segments, listener) }, "DemoFeed").apply { start() }
    }

    override fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun play(segments: List<Segment>, listener: RearFeedSource.Listener) {
        val t0 = SystemClock.elapsedRealtime()
        var tick = 0L
        try {
            for ((index, seg) in segments.withIndex()) {
                val frame = BitmapFactory.decodeFile(File(dir, seg.image).path)
                    ?: return listener.onError("사진을 열 수 없습니다: ${seg.image}")
                val audio = loadSounds(seg.sounds)
                Log.i(TAG, "대본 ${index + 1}/${segments.size}: ${seg.seconds}초, ${seg.image}, ${seg.sounds}")
                // 마지막 장면은 멈출 때까지 계속 보낸다. 실제 카메라처럼 입력이 끊기지 않아야 "N분째"가 계속 늘어난다
                val seconds = if (index == segments.lastIndex) Int.MAX_VALUE else seg.seconds
                for (s in 0 until seconds) {
                    if (!running) return
                    val now = t0 + tick * 1000
                    listener.onFrame(frame, now)
                    // 소리를 1초씩 잘라 보낸다. 끝에 닿으면 처음부터 이어 붙인다
                    val chunk = FloatArray(RATE) { i -> audio[((s.toLong() * RATE + i) % audio.size).toInt()] }
                    listener.onAudio(chunk, RATE, now)
                    tick++
                    // 실제 시간에 맞춰 1초마다 보낸다(분석이 늦어도 대본 시간은 밀리지 않게 시작 시각 기준으로 잰다)
                    val wait = t0 + tick * 1000 - SystemClock.elapsedRealtime()
                    if (wait > 0) Thread.sleep(wait)
                }
            }
            listener.onEnded()
        } catch (_: InterruptedException) {
            // stop()으로 멈춤
        } catch (e: Exception) {
            Log.e(TAG, "시연 입력 오류", e)
            listener.onError("시연 입력 오류: ${e.message}")
        }
    }

    /** 여러 WAV를 이어 붙여 16kHz 소리 하나로 만든다. 속도가 다르면 16kHz로 맞춘다 */
    private fun loadSounds(paths: List<String>): FloatArray {
        val parts = paths.map { path ->
            val wav = WavFile.read(File(filesRoot, path))
            if (wav.sampleRate == RATE) wav.samples else resample(wav.samples, wav.sampleRate)
        }
        return parts.reduce { a, b -> a + b }
    }

    // 가장 가까운 숫자를 골라 읽는 간단한 방식. 시험 파일은 대부분 이미 16kHz라 거의 쓰이지 않는다
    private fun resample(x: FloatArray, from: Int): FloatArray {
        val n = (x.size.toLong() * RATE / from).toInt()
        return FloatArray(n) { i -> x[minOf((i.toLong() * from / RATE).toInt(), x.size - 1)] }
    }

    companion object {
        private const val TAG = "RearCareFeed"
        private const val RATE = 16_000
    }
}
