package com.rearcare.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * 다닌 곳 카드에 부모가 넣은 사진. 기록 하나에 TripShelf.MAX_PHOTOS장까지 넣고, 카드에는 첫 사진을 보여 준다.
 * 고른 사진을 줄여서 앱 전용 폴더(files/diary/photos, 백업에서 빠지는 폴더)에 복사한다. 밖으로 보내지 않는다.
 * 앱이 사진을 직접 찍거나 고르지 않는다. 시스템 사진 선택기에서 부모가 고른 사진만 받으므로 저장소 권한이 필요 없다.
 * 기록 번호로 파일을 찾으므로 기록 파일의 형식은 바꾸지 않았다. 기록을 지우면 사진도 지운다(TripStore.delete).
 */
object TripPhotos {
    private const val TAG = "RearCareTrips"
    private const val DIR = "diary/photos"
    private const val MAX_SIDE = 1280 // 정함: 카드(372×440)와 넓은 창에서 보기에 충분하고 파일이 커지지 않는 크기
    private const val JPEG_QUALITY = 88

    private fun folder(context: Context) = File(context.filesDir, DIR)

    /** 이 기록의 사진 파일. 먼저 넣은 사진부터다. 사진을 빼도 남은 파일의 번호는 그대로 두므로 번호 사이가 비어 있을 수 있다 */
    private fun files(context: Context, id: String): List<File> =
        folder(context).listFiles().orEmpty()
            .mapNotNull { file -> TripShelf.photoNumber(id, file.name)?.let { it to file } }
            .sortedBy { it.first }
            .map { it.second }

    fun count(context: Context, id: String): Int = files(context, id).size

    fun has(context: Context, id: String): Boolean = files(context, id).isNotEmpty()

    /** 고른 사진을 순서대로 뒤에 넣고 넣은 장수를 돌려준다. MAX_PHOTOS를 넘는 사진은 넣지 않는다. 파일을 다루므로 메인 스레드 밖에서 부른다 */
    fun addAll(context: Context, id: String, uris: List<Uri>): Int = uris.count { add(context, id, it) }

    /** 고른 사진을 줄여서 저장하고 다시 읽어 확인한다. 저장한 사진이 그대로 읽힐 때만 true */
    private fun add(context: Context, id: String, uri: Uri): Boolean {
        val existing = files(context, id)
        if (existing.size >= TripShelf.MAX_PHOTOS) return false
        val number = existing.maxOfOrNull { TripShelf.photoNumber(id, it.name) ?: 0 }?.plus(1) ?: 0
        val target = File(folder(context), TripShelf.photoFileName(id, number))
        target.parentFile?.mkdirs()
        val atomic = AtomicFile(target)
        return try {
            // ImageDecoder는 사진의 회전 정보를 반영해 준다
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (width, height) = TripShelf.scaledSize(info.size.width, info.size.height, MAX_SIDE)
                decoder.setTargetSize(width, height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val out = atomic.startWrite()
            val written = try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out).also { if (it) atomic.finishWrite(out) else atomic.failWrite(out) }
            } catch (e: IOException) {
                atomic.failWrite(out)
                throw e
            }
            val width = bitmap.width
            val height = bitmap.height
            bitmap.recycle()
            val ok = written && readSize(target) == (width to height)
            Log.i(TAG, "사진 저장 ${if (ok) "성공" else "실패"}: ${target.name} ${width}x$height ${target.length()}바이트")
            ok
        } catch (e: IOException) {
            Log.e(TAG, "사진 저장 실패", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "사진을 읽을 수 없음", e)
            false
        }
    }

    private fun readSize(f: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, options)
        return options.outWidth to options.outHeight
    }

    /** index번째 사진을 원하는 크기에 맞춰 읽는다. 사진이 없거나 망가졌으면 null. 메인 스레드 밖에서 부른다 */
    fun load(context: Context, id: String, wantWidth: Int, wantHeight: Int, index: Int = 0): Bitmap? {
        val target = files(context, id).getOrNull(index) ?: return null
        val (width, height) = readSize(target)
        if (width <= 0 || height <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = TripShelf.sampleSize(width, height, wantWidth, wantHeight) }
        return BitmapFactory.decodeFile(target.path, options)
    }

    /** index번째 사진 한 장을 뺀다. 실제로 빠졌을 때만 true */
    fun removeAt(context: Context, id: String, index: Int): Boolean {
        val target = files(context, id).getOrNull(index) ?: return false
        AtomicFile(target).delete()
        return !target.exists()
    }

    fun delete(context: Context, id: String) {
        files(context, id).forEach { AtomicFile(it).delete() }
    }
}
