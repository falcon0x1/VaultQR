package com.example.qrcodevault.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.qrcodevault.ui.components.GlassCard
import com.example.qrcodevault.ui.theme.*
import com.example.qrcodevault.utils.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    
    var screenshotPrevention by remember { mutableStateOf(prefsManager.screenshotPreventionEnabled) }
    var selectedTimeout by remember { mutableStateOf(prefsManager.autoLockTimeout) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showSecretPinDialog by remember { mutableStateOf(false) }
    var secretUseBiometric by remember { mutableStateOf(prefsManager.secretFolderUseBiometric) }
    
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassBackground)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security Section
            SectionHeader("Security")
            
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                // Screenshot Prevention
                SettingsToggle(
                    icon = Icons.Default.Screenshot,
                    title = "Screenshot Prevention",
                    subtitle = "Block screenshots in the app",
                    checked = screenshotPrevention,
                    onCheckedChange = {
                        screenshotPrevention = it
                        prefsManager.screenshotPreventionEnabled = it
                    }
                )
                
                HorizontalDivider(color = GlassCardBorder, modifier = Modifier.padding(vertical = 8.dp))
                
                // Auto-Lock
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "Auto-Lock Timer",
                    subtitle = getTimeoutLabel(selectedTimeout),
                    onClick = { showTimeoutDialog = true }
                )
                
                HorizontalDivider(color = GlassCardBorder, modifier = Modifier.padding(vertical = 8.dp))
                
                // Secret Folder Biometric
                SettingsToggle(
                    icon = Icons.Default.Fingerprint,
                    title = "Secret Folder Biometric",
                    subtitle = "Use biometrics for secret folder",
                    checked = secretUseBiometric,
                    onCheckedChange = {
                        secretUseBiometric = it
                        prefsManager.secretFolderUseBiometric = it
                    }
                )
                
                HorizontalDivider(color = GlassCardBorder, modifier = Modifier.padding(vertical = 8.dp))
                
                // Secret Folder PIN
                SettingsItem(
                    icon = Icons.Default.Pin,
                    title = "Secret Folder PIN",
                    subtitle = if (prefsManager.secretFolderPinHash != null) "PIN is set" else "Set a PIN",
                    onClick = { showSecretPinDialog = true }
                )
            }
            
            // About Section
            SectionHeader("About")
            
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0",
                    onClick = {}
                )
                
                HorizontalDivider(color = GlassCardBorder, modifier = Modifier.padding(vertical = 8.dp))
                
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Developer",
                    subtitle = "falcon0x1",
                    onClick = {}
                )
                
                HorizontalDivider(color = GlassCardBorder, modifier = Modifier.padding(vertical = 8.dp))
                
                SettingsItem(
                    icon = Icons.Default.Code,
                    title = "Source Code",
                    subtitle = "github.com/falcon0x1",
                    onClick = {}
                )
            }
            
            // Made with love banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Made with 🔐 by falcon0x1",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
    
    // Timeout Selection Dialog
    if (showTimeoutDialog) {
        TimeoutSelectionDialog(
            currentTimeout = selectedTimeout,
            onSelect = { timeout ->
                selectedTimeout = timeout
                prefsManager.autoLockTimeout = timeout
                showTimeoutDialog = false
            },
            onDismiss = { showTimeoutDialog = false }
        )
    }
    
    // PIN Setup Dialog
    if (showSecretPinDialog) {
        PinSetupDialog(
            onSetPin = { pin ->
                prefsManager.secretFolderPinHash = prefsManager.hashPin(pin)
                showSecretPinDialog = false
            },
            onDismiss = { showSecretPinDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = GlassPrimary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GlassPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GlassPrimary,
                checkedTrackColor = GlassPrimary.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GlassPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary
        )
    }
}

@Composable
private fun TimeoutSelectionDialog(
    currentTimeout: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        PreferencesManager.TIMEOUT_1_MIN to "1 minute",
        PreferencesManager.TIMEOUT_5_MIN to "5 minutes",
        PreferencesManager.TIMEOUT_15_MIN to "15 minutes",
        PreferencesManager.TIMEOUT_30_MIN to "30 minutes",
        PreferencesManager.TIMEOUT_NEVER to "Never"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-Lock Timer") },
        text = {
            Column {
                options.forEach { (timeout, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(timeout) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTimeout == timeout,
                            onClick = { onSelect(timeout) },
                            colors = RadioButtonDefaults.colors(selectedColor = GlassPrimary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = GlassSurface,
        titleContentColor = TextPrimary
    )
}

@Composable
private fun PinSetupDialog(
    onSetPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Secret Folder PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it },
                    label = { Text("Enter PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> error = "PIN must be at least 4 digits"
                    pin != confirmPin -> error = "PINs don't match"
                    else -> onSetPin(pin)
                }
            }) {
                Text("Set PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = GlassSurface,
        titleContentColor = TextPrimary
    )
}

private fun getTimeoutLabel(timeout: Long): String {
    return when (timeout) {
        PreferencesManager.TIMEOUT_1_MIN -> "1 minute"
        PreferencesManager.TIMEOUT_5_MIN -> "5 minutes"
        PreferencesManager.TIMEOUT_15_MIN -> "15 minutes"
        PreferencesManager.TIMEOUT_30_MIN -> "30 minutes"
        PreferencesManager.TIMEOUT_NEVER -> "Never"
        else -> "5 minutes"
    }
}
