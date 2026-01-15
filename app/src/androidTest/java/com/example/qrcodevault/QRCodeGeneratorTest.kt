package com.example.qrcodevault

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.qrcodevault.ui.generator.QRCodeGenerator
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QRCodeGeneratorTest {
    
    companion object {
        private const val TAG = "QRCodeGeneratorTest"
    }
    
    @Test
    fun testEyeStylesProduceDifferentOutput() {
        val testContent = "https://example.com"
        val size = 512
        
        // Generate QR with SQUARE eyes
        val squareBitmap = QRCodeGenerator.generateCustomQRCode(
            content = testContent,
            size = size,
            foregroundColor = Color.Black,
            backgroundColor = Color.White,
            eyeStyle = QRCodeGenerator.EyeStyle.SQUARE
        )
        assertNotNull("SQUARE generated", squareBitmap)
        
        // Generate QR with CIRCLE eyes
        val circleBitmap = QRCodeGenerator.generateCustomQRCode(
            content = testContent,
            size = size,
            foregroundColor = Color.Black,
            backgroundColor = Color.White,
            eyeStyle = QRCodeGenerator.EyeStyle.CIRCLE
        )
        assertNotNull("CIRCLE generated", circleBitmap)
        
        // Compare pixels in the top-left finder pattern area
        // The finder pattern is 7 modules. For a 512px image with ~25 modules, that's ~144px
        val finderAreaSize = size / 4  // Approximate finder area
        
        var diffPixels = 0
        for (y in 0 until finderAreaSize) {
            for (x in 0 until finderAreaSize) {
                val squarePixel = squareBitmap!!.getPixel(x, y)
                val circlePixel = circleBitmap!!.getPixel(x, y)
                if (squarePixel != circlePixel) {
                    diffPixels++
                }
            }
        }
        
        val totalTopLeftPixels = finderAreaSize * finderAreaSize
        val diffPercentage = (diffPixels.toFloat() / totalTopLeftPixels) * 100
        
        Log.d(TAG, "Top-left finder area ($finderAreaSize x $finderAreaSize pixels):")
        Log.d(TAG, "Different pixels between SQUARE and CIRCLE: $diffPixels out of $totalTopLeftPixels")
        Log.d(TAG, "Difference percentage: ${"%.2f".format(diffPercentage)}%")
        
        // If eye style is working, at least 5% of pixels in finder area should be different
        // (corners of squares vs curves of circles)
        assertTrue(
            "Eye styles should produce at least 5% different pixels in finder area. Got ${"%.2f".format(diffPercentage)}%",
            diffPercentage >= 5.0f
        )
        
        Log.d(TAG, "TEST PASSED: Eye styles produce visually different finder patterns!")
    }
}
