package com.rearcare.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * 내비 위 작은 창의 둥근 사진 카드. 얼굴을 찾았으면 얼굴 중심으로 키우고, 못 찾았으면 사진 전체를 넣는다(PhotoFit).
 * 사진과 카드의 비율이 달라 남는 곳은 같은 사진을 흐리게 깔아 채운다. 그래야 카드 모서리까지 사진으로 보이고, 사진 비율이 달라도 카드 크기가 변하지 않는다.
 * 언제 사진을 바꿀지는 본 화면(RearStatusActivity.updatePhoto)이 정하고, 이 카드는 받은 사진을 놓기만 한다.
 */
class PhotoCardView(context: Context) : FrameLayout(context) {

    private val fill = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val photo = ImageView(context).apply {
        scaleType = ImageView.ScaleType.MATRIX
        visibility = View.GONE
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
    }
    private val note = Ui.text(context, NOTE_WAITING, color = Ui.BODY).apply {
        gravity = Gravity.CENTER
        val gap = Ui.dp(context, Ui.GAP_DP)
        setPadding(gap, gap, gap, gap)
    }

    // 보이는 사진과 그 사진의 얼굴 위치. 창 크기가 바뀌거나 얼굴이 움직이면 이 값으로 사진을 다시 놓는다(place)
    private var shownFrame: Bitmap? = null
    private var shownFocus: PhotoFit.Focus? = null
    private val photoMatrix = Matrix()

    init {
        background = Ui.rounded(context, Ui.PHOTO)
        clipToOutline = true
        Ui.lift(this)
        addView(fill, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(photo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(note, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun show(frame: Bitmap, focus: PhotoFit.Focus?) {
        // 시연 입력은 한 장면 동안 같은 사진을 매초 다시 보낸다. 같은 사진이면 흐린 바탕을 다시 만들지 않고
        // 얼굴 위치만 맞춘다. 사진을 지우면(clear) shownFrame이 비어 다음 사진은 처음부터 넣는다
        if (frame === shownFrame) {
            if (focus != shownFocus) {
                shownFocus = focus
                place()
            }
            return
        }
        shownFrame = frame
        shownFocus = focus
        photo.setImageBitmap(frame)
        photo.visibility = View.VISIBLE
        place()
        fill.setImageBitmap(softened(frame))
        fill.visibility = View.VISIBLE
        note.visibility = View.GONE
    }

    fun clear() {
        shownFrame = null
        shownFocus = null
        photo.setImageDrawable(null)
        photo.visibility = View.GONE
        fill.setImageDrawable(null)
        fill.visibility = View.GONE
        note.visibility = View.VISIBLE
    }

    /** 사진을 카드에 맞춰 놓는다. 얼굴을 찾았으면 얼굴을 기준으로 키운다(PhotoFit) */
    fun place() {
        val frame = shownFrame ?: return
        val w = photo.width
        val h = photo.height
        if (w == 0 || h == 0) return
        val p = PhotoFit.place(frame.width, frame.height, w, h, shownFocus)
        photoMatrix.setScale(p.scale, p.scale)
        photoMatrix.postTranslate(p.dx, p.dy)
        photo.imageMatrix = photoMatrix
    }

    /** 카드 아래에 상태 띠를 얹을 때 사진이 띠에 가리지 않게 띠 위까지만 놓는다 */
    fun setPhotoBottom(px: Int) {
        listOf<View>(fill, photo, note).forEach {
            (it.layoutParams as LayoutParams).bottomMargin = px
            it.requestLayout()
        }
    }

    /**
     * 카드의 남는 곳을 채울 흐린 그림. 아주 작게 줄였다가 다시 늘리면 번져 보인다.
     * 한 번에 많이 줄이면 네모난 자국이 남아서(에뮬레이터 화면으로 확인) 반씩 줄이고 두 배씩 늘린다.
     * RenderEffect 흐림은 내비 위 작은 창에서 그려지지 않아(2026-11-04 확인) 쓰지 않는다.
     */
    private fun softened(frame: Bitmap): Bitmap {
        fun resized(source: Bitmap, width: Int) =
            Bitmap.createScaledBitmap(source, width, (width * source.height / source.width).coerceAtLeast(1), true)
        var image = resized(frame, FILL_PX)
        while (image.width > FILL_SMALL_PX) image = resized(image, image.width / 2)
        while (image.width < FILL_PX) image = resized(image, image.width * 2)
        return image
    }

    companion object {
        private const val NOTE_WAITING = "화면을 기다리는 중이에요"
        // 흐린 그림의 가로 크기(px). 192 → 12 → 192로 줄였다 늘린다. SMALL이 작을수록 더 흐리다
        private const val FILL_PX = 192
        private const val FILL_SMALL_PX = 12
    }
}
