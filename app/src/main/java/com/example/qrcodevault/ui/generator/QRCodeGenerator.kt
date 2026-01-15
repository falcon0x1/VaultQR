package com.example.qrcodevault.ui.generator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR Code generator with custom eye styles.
 * FIXED: Uses ZXing's actual pixel coordinates, not module coordinates.
 */
object QRCodeGenerator {
    
    private const val TAG = "QRCodeGenerator"
    
    fun generateCustomQRCode(
        content: String,
        size: Int = 512,
        foregroundColor: Color = Color.Black,
        backgroundColor: Color = Color.White,
        eyeStyle: EyeStyle = EyeStyle.SQUARE,
        logo: Bitmap? = null
    ): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 0
            hints[EncodeHintType.ERROR_CORRECTION] = if (logo != null) ErrorCorrectionLevel.H else ErrorCorrectionLevel.M
            
            val writer = QRCodeWriter()
            // Request exact size - ZXing will scale to fit
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            
            Log.d(TAG, "BitMatrix: ${matrixWidth}x${matrixHeight}, Requested size: $size")
            
            // ZXing's BitMatrix is ALREADY in final pixel coordinates when size is specified!
            // We draw pixels 1:1
            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            val fgColor = foregroundColor.toArgb()
            val bgColor = backgroundColor.toArgb()
            
            val fgPaint = Paint().apply {
                color = fgColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            val bgPaint = Paint().apply {
                color = bgColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            // Fill background
            canvas.drawRect(0f, 0f, matrixWidth.toFloat(), matrixHeight.toFloat(), bgPaint)
            
            // Find the actual QR code boundaries (first and last black pixels)
            var minX = matrixWidth
            var minY = matrixHeight
            var maxX = 0
            var maxY = 0
            
            for (y in 0 until matrixHeight) {
                for (x in 0 until matrixWidth) {
                    if (bitMatrix[x, y]) {
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                }
            }
            
            val qrWidth = maxX - minX + 1
            val qrHeight = maxY - minY + 1
            
            Log.d(TAG, "QR content bounds: ($minX,$minY) to ($maxX,$maxY), size: ${qrWidth}x${qrHeight}")
            
            // Standard QR finder pattern is 7 modules
            // Calculate module size in pixels based on finder pattern
            // A version 1 QR is 21x21 modules, version 2 is 25x25, etc.
            // We can estimate module size = qrWidth / numModules
            // For a 21-module QR, finder is 7/21 = 1/3 of width
            val finderPixelSize = (qrWidth / 3).toFloat()  // Approximate
            
            Log.d(TAG, "Estimated finder pattern size: $finderPixelSize pixels")
            
            // Draw all pixels first
            for (y in 0 until matrixHeight) {
                for (x in 0 until matrixWidth) {
                    if (bitMatrix[x, y]) {
                        canvas.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), fgPaint)
                    }
                }
            }
            
            // If not square style, clear and redraw finder patterns
            if (eyeStyle != EyeStyle.SQUARE) {
                Log.d(TAG, "Drawing custom eye style: $eyeStyle")
                
                // Clear finder pattern areas
                // Top-left
                canvas.drawRect(minX.toFloat(), minY.toFloat(), 
                    minX + finderPixelSize, minY + finderPixelSize, bgPaint)
                // Top-right
                canvas.drawRect(maxX - finderPixelSize + 1, minY.toFloat(),
                    (maxX + 1).toFloat(), minY + finderPixelSize, bgPaint)
                // Bottom-left
                canvas.drawRect(minX.toFloat(), maxY - finderPixelSize + 1,
                    minX + finderPixelSize, (maxY + 1).toFloat(), bgPaint)
                
                // Draw custom eyes
                // Top-left
                drawFinderPattern(canvas, minX.toFloat(), minY.toFloat(), finderPixelSize, fgPaint, bgPaint, eyeStyle)
                // Top-right
                drawFinderPattern(canvas, maxX - finderPixelSize + 1, minY.toFloat(), finderPixelSize, fgPaint, bgPaint, eyeStyle)
                // Bottom-left
                drawFinderPattern(canvas, minX.toFloat(), maxY - finderPixelSize + 1, finderPixelSize, fgPaint, bgPaint, eyeStyle)
                
                Log.d(TAG, "Custom eyes drawn at: TL($minX,$minY), TR(${maxX - finderPixelSize + 1},$minY), BL($minX,${maxY - finderPixelSize + 1})")
            }
            
            // Overlay logo
            logo?.let { logoBitmap ->
                val logoSize = (matrixWidth * 0.22f).toInt()
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoSize, logoSize, true)
                val logoX = (matrixWidth - logoSize) / 2f
                val logoY = (matrixHeight - logoSize) / 2f
                
                val padding = 4f
                canvas.drawRoundRect(
                    RectF(logoX - padding, logoY - padding, logoX + logoSize + padding, logoY + logoSize + padding),
                    padding, padding, bgPaint
                )
                canvas.drawBitmap(scaledLogo, logoX, logoY, null)
            }
            
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            null
        }
    }
    
    private fun drawFinderPattern(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        fgPaint: Paint,
        bgPaint: Paint,
        style: EyeStyle
    ) {
        val centerX = x + size / 2f
        val centerY = y + size / 2f
        val unit = size / 7f  // Standard finder is 7 modules
        
        when (style) {
            EyeStyle.SQUARE -> {
                canvas.drawRect(x, y, x + size, y + size, fgPaint)
                canvas.drawRect(x + unit, y + unit, x + size - unit, y + size - unit, bgPaint)
                canvas.drawRect(x + 2*unit, y + 2*unit, x + size - 2*unit, y + size - 2*unit, fgPaint)
            }
            
            EyeStyle.ROUNDED -> {
                val r1 = size * 0.22f
                val r2 = size * 0.18f
                val r3 = size * 0.12f
                
                canvas.drawRoundRect(RectF(x, y, x + size, y + size), r1, r1, fgPaint)
                canvas.drawRoundRect(RectF(x + unit, y + unit, x + size - unit, y + size - unit), r2, r2, bgPaint)
                canvas.drawRoundRect(RectF(x + 2*unit, y + 2*unit, x + size - 2*unit, y + size - 2*unit), r3, r3, fgPaint)
            }
            
            EyeStyle.CIRCLE -> {
                val outerR = size / 2f
                val midR = (size - 2*unit) / 2f
                val innerR = (size - 4*unit) / 2f
                
                canvas.drawCircle(centerX, centerY, outerR, fgPaint)
                canvas.drawCircle(centerX, centerY, midR, bgPaint)
                canvas.drawCircle(centerX, centerY, innerR, fgPaint)
            }
        }
    }
    
    enum class EyeStyle {
        SQUARE,
        ROUNDED,
        CIRCLE
    }
}
