package com.rearcare.car

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAV 파일(16비트 PCM)을 읽어 -1~1 사이 숫자 배열로 바꾼다. 시험용.
 *
 * WAV 파일은 "RIFF" 머리 뒤에 여러 조각(chunk)이 이어진 구조다.
 * - "fmt " 조각: 채널 수, 1초당 샘플 수, 샘플당 비트 수
 * - "data" 조각: 실제 소리 숫자들
 * 채널이 여럿이면 평균을 내서 모노 하나로 만든다.
 */
object WavFile {

    class Audio(val samples: FloatArray, val sampleRate: Int)

    fun read(file: File): Audio {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "WAV 파일이 아님: ${file.name}" }

        var channels = 1
        var sampleRate = 16000
        var bits = 16
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val body = pos + 8
            val size = minOf(buf.getInt(pos + 4), bytes.size - body) // 크기가 잘못 적힌 파일 대비
            when (id) {
                "fmt " -> {
                    channels = buf.getShort(body + 2).toInt()
                    sampleRate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                }
                "data" -> {
                    require(bits == 16) { "16비트 WAV만 읽을 수 있음: ${file.name} (${bits}비트)" }
                    val frames = size / (2 * channels)
                    val out = FloatArray(frames)
                    for (i in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) sum += buf.getShort(body + (i * channels + c) * 2)
                        out[i] = sum / channels / 32768f // 16비트 정수(-32768~32767)를 -1~1로
                    }
                    return Audio(out, sampleRate)
                }
            }
            pos = body + size + (size and 1) // 조각 크기가 홀수면 1바이트 채움이 붙는다
        }
        error("소리(data) 부분이 없음: ${file.name}")
    }
}
