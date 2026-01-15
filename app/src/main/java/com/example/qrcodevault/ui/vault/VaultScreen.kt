package com.example.qrcodevault.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.qrcodevault.data.QRCategories
import com.example.qrcodevault.data.QRCodeEntity
import com.example.qrcodevault.data.QRCodeRepository
import com.example.qrcodevault.ui.components.GlassCard
import com.example.qrcodevault.ui.components.GlassTextField
import com.example.qrcodevault.ui.generator.generateQRCode
import com.example.qrcodevault.ui.theme.*
import com.example.qrcodevault.utils.BiometricPromptManager
import com.example.qrcodevault.utils.ExportUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    repository: QRCodeRepository,
    onNavigateToScanner: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(QRCategories.ALL) }
    var showSecretVault by remember { mutableStateOf(false) }
    var vaultUnlocked by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf<QRCodeEntity?>(null) }
    var showAuthPending by remember { mutableStateOf(false) }
    
    // Selection mode for bulk operations
    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Int>()) }
    var showExportDialog by remember { mutableStateOf(false) }
    
    // Data flows
    val allCodes by repository.allQrCodes.collectAsState(initial = emptyList())
    val secretCodes by repository.secretQrCodes.collectAsState(initial = emptyList())
    
    // Filter codes
    val displayedCodes = remember(allCodes, searchQuery, selectedCategory, showSecretVault, secretCodes, vaultUnlocked) {
        val baseCodes = if (showSecretVault && vaultUnlocked) secretCodes else allCodes
        baseCodes.filter { code ->
            val matchesSearch = searchQuery.isEmpty() || 
                code.label.contains(searchQuery, ignoreCase = true) ||
                code.content.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory.isEmpty() || code.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }
    
    fun hapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (e: Exception) { /* ignore */ }
    }
    
    // Biometric authentication for vault
    fun authenticateForVault() {
        val activity = context as? FragmentActivity
        if (activity != null) {
            showAuthPending = true
            val biometricManager = BiometricPromptManager(activity)
            
            // Collect results
            scope.launch {
                biometricManager.promptResults.collect { result ->
                    showAuthPending = false
                    when (result) {
                        is BiometricPromptManager.BiometricResult.AuthenticationSuccess -> {
                            vaultUnlocked = true
                            showSecretVault = true
                            hapticFeedback()
                        }
                        else -> {
                            Toast.makeText(context, "Authentication required", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            // Show the prompt
            biometricManager.showBiometricPrompt(
                title = "Unlock Vault",
                description = "Authenticate to access your secure QR codes"
            )
        } else {
            // Fallback if no FragmentActivity
            vaultUnlocked = true
            showSecretVault = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (showSecretVault) "🔐 Vault" else "VaultQR",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (showSecretVault) VaultAccent else TextPrimary
                    )
                )
                Text(
                    text = if (selectionMode) "${selectedItems.size} selected" else "${displayedCodes.size} codes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Selection mode actions
                if (selectionMode) {
                    IconButton(onClick = {
                        selectedItems = if (selectedItems.size == displayedCodes.size) emptySet() 
                        else displayedCodes.map { it.id }.toSet()
                    }) {
                        Icon(
                            if (selectedItems.size == displayedCodes.size) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = "Select All",
                            tint = VaultPrimary
                        )
                    }
                    IconButton(onClick = {
                        if (selectedItems.isNotEmpty()) {
                            scope.launch {
                                displayedCodes.filter { it.id in selectedItems }.forEach { repository.delete(it) }
                                Toast.makeText(context, "Deleted ${selectedItems.size} items", Toast.LENGTH_SHORT).show()
                                selectedItems = emptySet()
                                selectionMode = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Delete, "Delete Selected", tint = ErrorRed)
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.FileDownload, "Export Selected", tint = VaultSecondary)
                    }
                    IconButton(onClick = { selectionMode = false; selectedItems = emptySet() }) {
                        Icon(Icons.Default.Close, "Cancel", tint = TextSecondary)
                    }
                } else {
                    // Enter Vault Button
                    Surface(
                        onClick = {
                            hapticFeedback()
                            if (showSecretVault) {
                                showSecretVault = false
                                vaultUnlocked = false
                            } else {
                                authenticateForVault()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (showSecretVault) VaultAccent.copy(alpha = 0.2f) else VaultCard
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (showSecretVault) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Vault",
                                tint = if (showSecretVault) VaultAccent else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (showSecretVault) "Exit" else "Vault",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (showSecretVault) VaultAccent else TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = TextSecondary)
                    }
                }
            }
        }
        
        // Search Bar
        GlassTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search codes...",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(Modifier.height(12.dp))
        
        // Category Filter
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(QRCategories.defaultCategories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(if (category.isEmpty()) "All" else category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VaultPrimary,
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Auth pending overlay
        if (showAuthPending) {
            Box(
                modifier = Modifier.fillMaxSize().background(VaultBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VaultPrimary)
                    Spacer(Modifier.height(16.dp))
                    Text("Authenticating...", color = TextSecondary)
                }
            }
        } else if (displayedCodes.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (showSecretVault) Icons.Default.Lock else Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = TextMuted
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (showSecretVault) "Vault is empty" else "No codes yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (showSecretVault) "Hide codes here for extra protection" else "Create or scan QR codes to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayedCodes, key = { it.id }) { code ->
                    VaultItem(
                        code = code,
                        isInVault = showSecretVault,
                        isSelected = code.id in selectedItems,
                        selectionMode = selectionMode,
                        onLongPress = {
                            hapticFeedback()
                            selectionMode = true
                            selectedItems = setOf(code.id)
                        },
                        onSelect = {
                            if (selectionMode) {
                                selectedItems = if (code.id in selectedItems) selectedItems - code.id else selectedItems + code.id
                                if (selectedItems.isEmpty()) selectionMode = false
                            }
                        },
                        onDelete = { scope.launch { repository.delete(code) } },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("QR Code", code.content))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            hapticFeedback()
                        },
                        onShare = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, code.content)
                                setType("text/plain")
                            }, "Share"))
                        },
                        onOpen = {
                            if (code.type == "URL") {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.content)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onMoveToVault = {
                            scope.launch {
                                repository.moveToSecret(code.id)
                                Toast.makeText(context, "Moved to Vault", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRemoveFromVault = {
                            scope.launch {
                                repository.removeFromSecret(code.id)
                                Toast.makeText(context, "Removed from Vault", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onChangeCategory = { showCategoryDialog = code }
                    )
                }
                
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
    
    // Category Dialog
    showCategoryDialog?.let { code ->
        CategorySelectionDialog(
            currentCategory = code.category,
            onSelect = { category ->
                scope.launch { repository.updateCategory(code.id, category) }
                showCategoryDialog = null
            },
            onDismiss = { showCategoryDialog = null }
        )
    }
    
    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export ${selectedItems.size} items") },
            text = { Text("Choose export format") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val items = displayedCodes.filter { it.id in selectedItems }
                        ExportUtils.exportToCsv(context, items)
                        showExportDialog = false
                        selectionMode = false
                        selectedItems = emptySet()
                    }
                }) { Text("CSV") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        val items = displayedCodes.filter { it.id in selectedItems }
                        ExportUtils.exportToPdf(context, items)
                        showExportDialog = false
                        selectionMode = false
                        selectedItems = emptySet()
                    }
                }) { Text("PDF") }
            },
            containerColor = VaultSurface,
            titleContentColor = TextPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultItem(
    code: QRCodeEntity,
    isInVault: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    onLongPress: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onMoveToVault: () -> Unit,
    onRemoveFromVault: () -> Unit,
    onChangeCategory: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val qrBitmap = remember(code.content) { generateQRCode(code.content, 200) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val formattedDate = remember(code.timestamp) { dateFormat.format(Date(code.timestamp)) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { onShare(); false }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> ErrorRed.copy(alpha = 0.8f)
                    SwipeToDismissBoxValue.StartToEnd -> VaultSecondary.copy(alpha = 0.8f)
                    else -> Color.Transparent
                }, label = "SwipeColor"
            )
            val scale by animateFloatAsState(if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1f, label = "Scale")
            
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(color).padding(horizontal = 20.dp),
                contentAlignment = when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }
            ) {
                Icon(
                    when (dismissState.dismissDirection) {
                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Share
                        else -> Icons.Default.Delete
                    },
                    null, modifier = Modifier.scale(scale), tint = Color.White
                )
            }
        },
        content = {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { if (selectionMode) onSelect() else expanded = !expanded },
                        onClickLabel = null
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Selection checkbox or QR Preview
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelect() },
                            colors = CheckboxDefaults.colors(checkedColor = VaultPrimary)
                        )
                    } else {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(4.dp)
                            )
                        }
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = when (code.type) {
                                    "URL" -> VaultSecondary
                                    "WIFI" -> VaultAccent
                                    "VCARD" -> InfoBlue
                                    "EMAIL" -> VaultAccent
                                    else -> VaultPrimary
                                }.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    code.type,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (code.type) {
                                        "URL" -> VaultSecondary
                                        "WIFI" -> VaultAccent
                                        "VCARD" -> InfoBlue
                                        "EMAIL" -> VaultAccent
                                        else -> VaultPrimary
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            if (code.category.isNotEmpty()) {
                                Surface(color = VaultCardBorder, shape = RoundedCornerShape(4.dp)) {
                                    Text(code.category, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(4.dp))
                        
                        Text(
                            code.label.ifBlank { code.content },
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            maxLines = if (expanded) 5 else 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(Modifier.height(2.dp))
                        Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                
                // Expanded Actions
                if (expanded && !selectionMode) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = VaultCardBorder)
                    Spacer(Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionButton(Icons.Default.ContentCopy, "Copy", VaultPrimary, onCopy)
                        ActionButton(Icons.Default.Share, "Share", VaultSecondary, onShare)
                        if (code.type == "URL") ActionButton(Icons.Default.OpenInNew, "Open", VaultAccent, onOpen)
                        ActionButton(Icons.Default.Category, "Tag", TextSecondary, onChangeCategory)
                        if (isInVault) ActionButton(Icons.Default.LockOpen, "Unhide", VaultAccent, onRemoveFromVault)
                        else ActionButton(Icons.Default.Lock, "Hide", VaultAccent, onMoveToVault)
                        ActionButton(Icons.Default.Delete, "Delete", ErrorRed, onDelete)
                    }
                }
            }
        }
    )
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CategorySelectionDialog(currentCategory: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category") },
        text = {
            Column {
                QRCategories.defaultCategories.filter { it.isNotEmpty() }.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(category) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentCategory == category, onClick = { onSelect(category) }, colors = RadioButtonDefaults.colors(selectedColor = VaultPrimary))
                        Spacer(Modifier.width(8.dp))
                        Text(category, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = VaultSurface,
        titleContentColor = TextPrimary
    )
}
