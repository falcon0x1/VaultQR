package com.example.qrcodevault.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.qrcodevault.data.QRCodeEntity
import com.example.qrcodevault.ui.generator.generateQRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExportUtils {
    
    /**
     * Export vault items to CSV file
     */
    suspend fun exportToCsv(context: Context, items: List<QRCodeEntity>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val filename = "QRVault_Export_${dateFormat.format(Date())}.csv"
                
                val csvContent = buildString {
                    // Header
                    appendLine("ID,Label,Content,Type,Category,Date")
                    
                    // Data rows
                    items.forEach { item ->
                        val escapedContent = item.content.replace("\"", "\"\"")
                        val escapedLabel = item.label.replace("\"", "\"\"")
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(item.timestamp))
                        
                        appendLine("${item.id},\"$escapedLabel\",\"$escapedContent\",${item.type},${item.category},\"$date\"")
                    }
                }
                
                saveToDownloads(context, filename, csvContent.toByteArray(), "text/csv")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Exported to Downloads/$filename", Toast.LENGTH_LONG).show()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                false
            }
        }
    }
    
    /**
     * Export vault items to PDF with QR codes
     */
    suspend fun exportToPdf(context: Context, items: List<QRCodeEntity>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val filename = "QRVault_Export_${dateFormat.format(Date())}.pdf"
                
                val document = PdfDocument()
                val pageWidth = 595 // A4 width in points
                val pageHeight = 842 // A4 height in points
                val margin = 50
                val qrSize = 150
                val itemsPerPage = 3
                
                var pageNumber = 0
                var yPosition = margin
                var currentPage: PdfDocument.Page? = null
                var canvas: Canvas? = null
                
                val titlePaint = Paint().apply {
                    textSize = 24f
                    isFakeBoldText = true
                    color = android.graphics.Color.BLACK
                }
                
                val labelPaint = Paint().apply {
                    textSize = 14f
                    isFakeBoldText = true
                    color = android.graphics.Color.BLACK
                }
                
                val contentPaint = Paint().apply {
                    textSize = 10f
                    color = android.graphics.Color.DKGRAY
                }
                
                val typePaint = Paint().apply {
                    textSize = 10f
                    color = android.graphics.Color.parseColor("#8B5CF6")
                }
                
                items.forEachIndexed { index, item ->
                    if (index % itemsPerPage == 0) {
                        // Finish previous page
                        currentPage?.let { document.finishPage(it) }
                        
                        // Start new page
                        pageNumber++
                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        currentPage = document.startPage(pageInfo)
                        canvas = currentPage?.canvas
                        yPosition = margin
                        
                        // Page title
                        if (pageNumber == 1) {
                            canvas?.drawText("QR Code Vault Export", margin.toFloat(), yPosition.toFloat(), titlePaint)
                            yPosition += 50
                        }
                    }
                    
                    // Generate QR code bitmap
                    val qrBitmap = generateQRCode(item.content, qrSize)
                    
                    // Draw QR code
                    qrBitmap?.let {
                        canvas?.drawBitmap(it, margin.toFloat(), yPosition.toFloat(), null)
                    }
                    
                    // Draw text info
                    val textX = margin + qrSize + 20f
                    canvas?.drawText("[${item.type}] ${item.category.ifEmpty { "Uncategorized" }}", textX, yPosition + 20f, typePaint)
                    canvas?.drawText(item.label.ifBlank { "No Label" }, textX, yPosition + 45f, labelPaint)
                    
                    // Wrap content text
                    val maxWidth = pageWidth - textX.toInt() - margin
                    val contentLines = wrapText(item.content, contentPaint, maxWidth.toFloat())
                    contentLines.take(4).forEachIndexed { lineIndex, line ->
                        canvas?.drawText(line, textX, yPosition + 65f + (lineIndex * 14f), contentPaint)
                    }
                    
                    yPosition += qrSize + 40
                }
                
                // Finish last page
                currentPage?.let { document.finishPage(it) }
                
                // Save PDF
                val fos: OutputStream?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    fos = uri?.let { context.contentResolver.openOutputStream(it) }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, filename)
                    fos = FileOutputStream(file)
                }
                
                fos?.use { document.writeTo(it) }
                document.close()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Exported to Downloads/$filename", Toast.LENGTH_LONG).show()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                false
            }
        }
    }
    
    private fun saveToDownloads(context: Context, filename: String, data: ByteArray, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(data)
                }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)
            FileOutputStream(file).use { it.write(data) }
        }
    }
    
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        
        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        
        return lines
    }
}
