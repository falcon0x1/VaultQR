package com.example.qrcodevault.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import android.view.ViewGroup
import android.widget.Toast
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.qrcodevault.data.QRCodeEntity
import com.example.qrcodevault.data.QRCodeRepository
import com.example.qrcodevault.ui.components.GlassCard
import com.example.qrcodevault.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    repository: QRCodeRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    
    var scannedCode by remember { mutableStateOf<String?>(null) }
    var scannedType by remember { mutableStateOf<Int?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(true) }
    var isProcessingGallery by remember { mutableStateOf(false) }
    
    // Batch mode
    var batchMode by remember { mutableStateOf(false) }
    var batchScans by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
    var showBatchReview by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isProcessingGallery = true
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap: Bitmap? = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                bitmap?.let { bmp ->
                    val image = InputImage.fromBitmap(bmp, 0)
                    BarcodeScanning.getClient().process(image)
                        .addOnSuccessListener { barcodes ->
                            isProcessingGallery = false
                            if (barcodes.isNotEmpty()) {
                                val barcode = barcodes.first()
                                if (batchMode) {
                                    batchScans = batchScans + (barcode.rawValue!! to barcode.valueType)
                                    hapticFeedback(context)
                                } else {
                                    scannedCode = barcode.rawValue
                                    scannedType = barcode.valueType
                                    showBottomSheet = true
                                }
                            } else {
                                Toast.makeText(context, "No QR code found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { isProcessingGallery = false }
                }
            } catch (e: Exception) {
                isProcessingGallery = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize().background(VaultBackground)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            
                        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx), BarcodeAnalyzer { barcodes ->
                            if (isScanning) {
                                barcodes.firstOrNull()?.let { barcode ->
                                    barcode.rawValue?.let { code ->
                                        if (batchMode) {
                                            if (batchScans.none { it.first == code }) {
                                                isScanning = false
                                                batchScans = batchScans + (code to barcode.valueType)
                                                hapticFeedback(ctx)
                                                isScanning = true
                                            }
                                        } else {
                                            isScanning = false
                                            scannedCode = code
                                            scannedType = barcode.valueType
                                            showBottomSheet = true
                                        }
                                    }
                                }
                            }
                        })

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                        } catch (exc: Exception) { }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            
            ScannerOverlay()
            
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f), contentColor = Color.White)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                
                Text("Scan QR Code", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                
                Row {
                    // Batch Mode Toggle
                    IconButton(
                        onClick = { 
                            batchMode = !batchMode
                            if (!batchMode && batchScans.isNotEmpty()) showBatchReview = true
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (batchMode) VaultPrimary else Color.Black.copy(alpha = 0.3f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.PlaylistAdd, "Batch Mode")
                    }
                    
                    IconButton(
                        onClick = { isFlashOn = !isFlashOn; camera?.cameraControl?.enableTorch(isFlashOn) },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isFlashOn) VaultAccent else Color.Black.copy(alpha = 0.3f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash")
                    }
                }
            }
            
            // Batch counter
            if (batchMode) {
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp).statusBarsPadding()
                ) {
                    Surface(
                        color = VaultPrimary,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            "Batch: ${batchScans.size} scanned",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Bottom controls
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (batchMode) "Keep scanning, tap batch icon when done" else "Point camera at a QR code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Gallery")
                    }
                    
                    if (batchMode && batchScans.isNotEmpty()) {
                        Button(
                            onClick = { showBatchReview = true },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Done, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Review (${batchScans.size})")
                        }
                    }
                }
            }
            
            if (isProcessingGallery) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = VaultPrimary)
                        Spacer(Modifier.height(16.dp))
                        Text("Scanning...", color = Color.White)
                    }
                }
            }
            
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(80.dp), tint = TextSecondary)
                Spacer(Modifier.height(16.dp))
                Text("Camera permission required", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant Permission") }
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultPrimary)) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pick from Gallery")
                }
            }
        }
    }

    // Single scan result sheet
    if (showBottomSheet && scannedCode != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false; isScanning = true },
            sheetState = sheetState,
            containerColor = VaultSurface,
            contentColor = TextPrimary
        ) {
            ScanResultContent(
                code = scannedCode!!,
                type = scannedType ?: Barcode.TYPE_TEXT,
                repository = repository,
                onDismiss = { showBottomSheet = false; isScanning = true },
                onNavigateBack = onNavigateBack
            )
        }
    }
    
    // Batch review sheet
    if (showBatchReview) {
        ModalBottomSheet(
            onDismissRequest = { showBatchReview = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VaultSurface,
            contentColor = TextPrimary
        ) {
            BatchReviewContent(
                scans = batchScans,
                repository = repository,
                onSaveAll = {
                    scope.launch {
                        batchScans.forEach { (code, type) ->
                            repository.insert(QRCodeEntity(
                                content = code,
                                type = getTypeString(type),
                                label = code.take(30)
                            ))
                        }
                        Toast.makeText(context, "Saved ${batchScans.size} codes!", Toast.LENGTH_SHORT).show()
                        batchScans = emptyList()
                        batchMode = false
                        showBatchReview = false
                        onNavigateBack()
                    }
                },
                onRemove = { index -> batchScans = batchScans.filterIndexed { i, _ -> i != index } },
                onDismiss = { showBatchReview = false }
            )
        }
    }
}

@Composable
private fun BatchReviewContent(
    scans: List<Pair<String, Int>>,
    repository: QRCodeRepository,
    onSaveAll: () -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
        Text("Batch Scan Review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("${scans.size} codes scanned", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp)) {
            items(scans.size) { index ->
                val (code, type) = scans[index]
                GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(getTypeString(type), style = MaterialTheme.typography.labelSmall, color = VaultPrimary)
                            Text(code, maxLines = 2, overflow = TextOverflow.Ellipsis, color = TextPrimary)
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Default.Close, "Remove", tint = ErrorRed)
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text("Continue Scanning")
            }
            Button(onClick = onSaveAll, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary)) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Save All")
            }
        }
    }
}

@Composable
private fun ScanResultContent(code: String, type: Int, repository: QRCodeRepository, onDismiss: () -> Unit, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val typeString = getTypeString(type)
    
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = VaultPrimary, shape = RoundedCornerShape(8.dp)) {
            Text(typeString, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(16.dp))
        Text("Scanned Content", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(code, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, maxLines = 5, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        
        Spacer(Modifier.height(24.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (type == Barcode.TYPE_URL) {
                Button(
                    onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code))) } catch (e: Exception) { } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultSecondary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Link", fontWeight = FontWeight.Bold)
                }
            }
            
            Button(
                onClick = {
                    scope.launch {
                        repository.insert(QRCodeEntity(content = code, type = typeString, label = code.take(30)))
                        Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Save to Vault", fontWeight = FontWeight.Bold)
            }
            
            OutlinedButton(
                onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, code); setType("text/plain") }, "Share")) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Share", fontWeight = FontWeight.Bold)
            }
            
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Scan Another", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ScannerOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val alpha by infiniteTransition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "Alpha")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val boxSize = 280.dp.toPx()
        val left = (size.width - boxSize) / 2
        val top = (size.height - boxSize) / 2
        val cornerLength = 50.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val color = VaultPrimary.copy(alpha = alpha)

        listOf(
            Offset(left, top) to listOf(Offset(left + cornerLength, top), Offset(left, top + cornerLength)),
            Offset(left + boxSize, top) to listOf(Offset(left + boxSize - cornerLength, top), Offset(left + boxSize, top + cornerLength)),
            Offset(left, top + boxSize) to listOf(Offset(left + cornerLength, top + boxSize), Offset(left, top + boxSize - cornerLength)),
            Offset(left + boxSize, top + boxSize) to listOf(Offset(left + boxSize - cornerLength, top + boxSize), Offset(left + boxSize, top + boxSize - cornerLength))
        ).forEach { (start, ends) ->
            ends.forEach { end -> drawLine(color, start, end, strokeWidth) }
        }
    }
}

private fun hapticFeedback(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (e: Exception) { }
}

private fun getTypeString(type: Int) = when (type) {
    Barcode.TYPE_URL -> "URL"
    Barcode.TYPE_WIFI -> "WIFI"
    else -> "TEXT"
}
