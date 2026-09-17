package com.sielo.music.room.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.sielo.music.R

object QrCodeGenerator {

    fun generateQrBitmap(
        content: String,
        context: Context? = null,
        sizePx: Int = 512,
        foregroundColor: Int = 0xFFFAF0CA.toInt(), // Sielo Sand/Cream
        backgroundColor: Int = 0xFF0D1321.toInt()  // Sielo Dark Navy
    ): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 2
        )
        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
            }
        }
        val qrBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        qrBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        if (context != null) {
            try {
                val logoRaw = BitmapFactory.decodeResource(context.resources, R.drawable.app_logo)
                if (logoRaw != null) {
                    val canvas = Canvas(qrBitmap)
                    val logoSize = (width * 0.22f).toInt()
                    val badgeSize = (logoSize * 1.15f).toInt()
                    val center = width / 2f

                    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = backgroundColor
                        style = Paint.Style.FILL
                    }
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = foregroundColor
                        style = Paint.Style.STROKE
                        strokeWidth = (width * 0.008f).coerceAtLeast(2f)
                    }

                    val badgeRadius = badgeSize / 2f
                    canvas.drawCircle(center, center, badgeRadius, badgePaint)
                    canvas.drawCircle(center, center, badgeRadius, borderPaint)

                    val logoRadius = logoSize / 2f
                    val scaledLogo = Bitmap.createScaledBitmap(logoRaw, logoSize, logoSize, true)
                    val circularLogo = getCircularBitmap(scaledLogo)
                    canvas.drawBitmap(circularLogo, center - logoRadius, center - logoRadius, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return qrBitmap
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        canvas.drawOval(rectF, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    fun generateQrImageBitmap(
        content: String,
        context: Context? = null,
        sizePx: Int = 512,
        foregroundColor: Int = 0xFFFAF0CA.toInt(),
        backgroundColor: Int = 0xFF0D1321.toInt()
    ): ImageBitmap {
        return generateQrBitmap(content, context, sizePx, foregroundColor, backgroundColor).asImageBitmap()
    }
}
