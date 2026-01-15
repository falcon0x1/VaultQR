package com.example.qrcodevault.ui.generator

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.qrcodevault.data.QRCategories
import com.example.qrcodevault.data.QRCodeEntity
import com.example.qrcodevault.data.QRCodeRepository
import com.example.qrcodevault.ui.components.GlassButton
import com.example.qrcodevault.ui.components.GlassCard
import com.example.qrcodevault.ui.components.GlassTextField
import com.example.qrcodevault.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URLEncoder

// QR Type definitions
enum class QRType(val displayName: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TEXT("Text", Icons.Default.TextFields),
    URL("URL", Icons.Default.Link),
    WIFI("WiFi", Icons.Default.Wifi),
    VCARD("Contact", Icons.Default.Person),
    EMAIL("Email", Icons.Default.Email)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    repository: QRCodeRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var inputText by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedType by remember { mutableStateOf(QRType.TEXT) }
    var selectedCategory by remember { mutableStateOf("") }
    
    // WiFi fields
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var wifiSecurity by remember { mutableStateOf("WPA") }
    
    // vCard fields
    var vcardName by remember { mutableStateOf("") }
    var vcardPhone by remember { mutableStateOf("") }
    var vcardEmail by remember { mutableStateOf("") }
    var vcardCompany by remember { mutableStateOf("") }
    var vcardAddress by remember { mutableStateOf("") }
    
    // Email fields
    var emailTo by remember { mutableStateOf("") }
    var emailSubject by remember { mutableStateOf("") }
    var emailBody by remember { mutableStateOf("") }
    
    // Customization
    var foregroundColor by remember { mutableStateOf(Color.Black) }
    var backgroundColor by remember { mutableStateOf(Color.White) }
    var eyeStyle by remember { mutableStateOf(QRCodeGenerator.EyeStyle.SQUARE) }
    var showColorPicker by remember { mutableStateOf<String?>(null) }
    var logoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Dialogs
    var showDuplicateDialog by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    val typeScrollState = rememberScrollState()
    
    // Logo picker
    val logoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                logoBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load logo", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun getQRContent(): String {
        return when (selectedType) {
            QRType.TEXT -> inputText
            QRType.URL -> inputText
            QRType.WIFI -> "WIFI:T:$wifiSecurity;S:$wifiSsid;P:$wifiPassword;;"
            QRType.VCARD -> buildString {
                append("BEGIN:VCARD\n")
                append("VERSION:3.0\n")
                if (vcardName.isNotBlank()) append("FN:$vcardName\n")
                if (vcardPhone.isNotBlank()) append("TEL:$vcardPhone\n")
                if (vcardEmail.isNotBlank()) append("EMAIL:$vcardEmail\n")
                if (vcardCompany.isNotBlank()) append("ORG:$vcardCompany\n")
                if (vcardAddress.isNotBlank()) append("ADR:;;$vcardAddress;;;;\n")
                append("END:VCARD")
            }
            QRType.EMAIL -> {
                val params = mutableListOf<String>()
                if (emailSubject.isNotBlank()) params.add("subject=${URLEncoder.encode(emailSubject, "UTF-8")}")
                if (emailBody.isNotBlank()) params.add("body=${URLEncoder.encode(emailBody, "UTF-8")}")
                if (params.isEmpty()) "mailto:$emailTo" else "mailto:$emailTo?${params.joinToString("&")}"
            }
        }
    }
    
    fun getDefaultLabel(): String {
        return when (selectedType) {
            QRType.TEXT -> inputText.take(20)
            QRType.URL -> inputText.take(30)
            QRType.WIFI -> wifiSsid
            QRType.VCARD -> vcardName
            QRType.EMAIL -> emailTo
        }
    }
    
    fun hapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (e: Exception) { /* ignore */ }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassBackground)
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Create QR Code",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Type selector - Horizontal scrollable row
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "QR Type",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(typeScrollState),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QRType.entries.forEach { type ->
                    val isSelected = selectedType == type
                    Surface(
                        onClick = { 
                            selectedType = type
                            hapticFeedback()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) VaultPrimary else VaultCard,
                        modifier = Modifier.width(80.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                type.icon,
                                contentDescription = type.displayName,
                                tint = if (isSelected) TextPrimary else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                type.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic input fields
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            when (selectedType) {
                QRType.TEXT, QRType.URL -> {
                    GlassTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = if (selectedType == QRType.URL) "Enter URL" else "Enter Text",
                        placeholder = if (selectedType == QRType.URL) "https://example.com" else "Your text here..."
                    )
                }
                
                QRType.WIFI -> {
                    GlassTextField(value = wifiSsid, onValueChange = { wifiSsid = it }, label = "Network Name (SSID)")
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(value = wifiPassword, onValueChange = { wifiPassword = it }, label = "Password")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("WPA", "WEP", "Open").forEach { sec ->
                            FilterChip(
                                selected = wifiSecurity == sec,
                                onClick = { wifiSecurity = sec },
                                label = { Text(sec) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultSecondary,
                                    selectedLabelColor = TextPrimary
                                )
                            )
                        }
                    }
                }
                
                QRType.VCARD -> {
                    GlassTextField(value = vcardName, onValueChange = { vcardName = it }, label = "Full Name", placeholder = "John Doe")
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(value = vcardPhone, onValueChange = { vcardPhone = it }, label = "Phone", placeholder = "+1 234 567 890")
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(value = vcardEmail, onValueChange = { vcardEmail = it }, label = "Email", placeholder = "email@example.com")
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(value = vcardCompany, onValueChange = { vcardCompany = it }, label = "Company (optional)")
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(value = vcardAddress, onValueChange = { vcardAddress = it }, label = "Address (optional)")
                }
                
                QRType.EMAIL -> {
                    GlassTextField(value = emailTo, onValueChange = { emailTo = it }, label = "To Email", placeholder = "recipient@example.com")
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(value = emailSubject, onValueChange = { emailSubject = it }, label = "Subject (optional)")
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(value = emailBody, onValueChange = { emailBody = it }, label = "Body (optional)")
                }
            }
            
            Spacer(Modifier.height(16.dp))
            GlassTextField(value = label, onValueChange = { label = it }, label = "Label (optional)", placeholder = "My QR Code")
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Customization
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("Customize", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ColorOption("Foreground", foregroundColor) { showColorPicker = "foreground" }
                ColorOption("Background", backgroundColor) { showColorPicker = "background" }
                
                // Logo
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { logoLauncher.launch("image/*") }) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(VaultCard)
                            .border(2.dp, if (logoBitmap != null) VaultPrimary else VaultCardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoBitmap != null) {
                            Image(bitmap = logoBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                        } else {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Logo", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            
            if (logoBitmap != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { logoBitmap = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove Logo", color = TextSecondary)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Eye Style
            Text("Eye Style", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QRCodeGenerator.EyeStyle.entries.forEach { style ->
                    val isSelected = eyeStyle == style
                    Surface(
                        onClick = { 
                            eyeStyle = style
                            hapticFeedback()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) VaultPrimary else VaultCard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Visual preview of the style
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .then(
                                        when (style) {
                                            QRCodeGenerator.EyeStyle.SQUARE -> Modifier.background(if (isSelected) TextPrimary else TextSecondary)
                                            QRCodeGenerator.EyeStyle.ROUNDED -> Modifier.background(if (isSelected) TextPrimary else TextSecondary, RoundedCornerShape(6.dp))
                                            QRCodeGenerator.EyeStyle.CIRCLE -> Modifier.background(if (isSelected) TextPrimary else TextSecondary, CircleShape)
                                        }
                                    )
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                style.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Generate Button
        GlassButton(
            onClick = {
                val content = getQRContent()
                if (content.isNotBlank()) {
                    generatedBitmap = QRCodeGenerator.generateCustomQRCode(
                        content = content,
                        size = 512,
                        foregroundColor = foregroundColor,
                        backgroundColor = backgroundColor,
                        eyeStyle = eyeStyle,
                        logo = logoBitmap
                    )
                    hapticFeedback()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.QrCode, null)
            Spacer(Modifier.width(8.dp))
            Text("Generate QR Code", fontWeight = FontWeight.Bold)
        }

        // Generated QR Display
        generatedBitmap?.let { bitmap ->
            Spacer(Modifier.height(24.dp))
            
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Generated QR Code",
                        modifier = Modifier.size(250.dp).clip(RoundedCornerShape(16.dp))
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val content = getQRContent()
                            if (repository.isDuplicate(content)) {
                                showDuplicateDialog = true
                            } else {
                                repository.insert(QRCodeEntity(
                                    content = content,
                                    type = selectedType.name,
                                    label = label.ifBlank { getDefaultLabel() },
                                    category = selectedCategory
                                ))
                                Toast.makeText(context, "Saved to Vault!", Toast.LENGTH_SHORT).show()
                                hapticFeedback()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultPrimary)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }

                OutlinedButton(
                    onClick = { scope.launch { saveQRCodeToGallery(context, bitmap, label.ifBlank { "VaultQR" }) } },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultSecondary)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Download")
                }

                OutlinedButton(
                    onClick = { scope.launch { shareQRCode(context, bitmap) } },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultAccent)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
        
        Spacer(Modifier.height(100.dp))
    }
    
    // Color Picker Dialog
    showColorPicker?.let { pickerType ->
        ColorPickerDialog(
            currentColor = if (pickerType == "foreground") foregroundColor else backgroundColor,
            onColorSelected = { color ->
                if (pickerType == "foreground") foregroundColor = color else backgroundColor = color
                showColorPicker = null
            },
            onDismiss = { showColorPicker = null }
        )
    }
    
    // Duplicate Dialog
    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text("Duplicate Found") },
            text = { Text("This QR code already exists in your vault.") },
            confirmButton = { TextButton(onClick = { showDuplicateDialog = false }) { Text("OK") } },
            containerColor = VaultSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun ColorOption(label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(color).border(2.dp, VaultCardBorder, CircleShape))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun ColorPickerDialog(currentColor: Color, onColorSelected: (Color) -> Unit, onDismiss: () -> Unit) {
    val colors = listOf(
        Color.Black, Color.White, Color(0xFF10B981), Color(0xFF14B8A6),
        Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF8B5CF6),
        Color(0xFFEC4899), Color(0xFF22C55E), Color(0xFF0A0F1C), Color(0xFF6366F1)
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color") },
        text = {
            Column {
                colors.chunked(4).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(if (color == currentColor) 3.dp else 1.dp, if (color == currentColor) VaultPrimary else VaultCardBorder, CircleShape)
                                    .clickable { onColorSelected(color) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {},
        containerColor = VaultSurface,
        titleContentColor = TextPrimary
    )
}

fun generateQRCode(content: String, size: Int = 512): Bitmap? {
    return try {
        val hints = hashMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1
        
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

suspend fun saveQRCodeToGallery(context: Context, bitmap: Bitmap, name: String) {
    withContext(Dispatchers.IO) {
        try {
            val filename = "${name}_${System.currentTimeMillis()}.png"
            val fos: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VaultQR")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { context.contentResolver.openOutputStream(it) }
            } else {
                FileOutputStream(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename))
            }
            fos?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            withContext(Dispatchers.Main) { Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }
}

suspend fun shareQRCode(context: Context, bitmap: Bitmap) {
    withContext(Dispatchers.IO) {
        try {
            val cachePath = File(context.cacheDir, "images").also { it.mkdirs() }
            val file = File(cachePath, "shared_qr_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    setType("image/png")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share QR Code"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }
}
