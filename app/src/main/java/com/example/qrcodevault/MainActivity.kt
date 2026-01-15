package com.example.qrcodevault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.qrcodevault.ui.MainNavigation
import com.example.qrcodevault.ui.theme.*
import com.example.qrcodevault.utils.PreferencesManager

class MainActivity : FragmentActivity() {
    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefsManager = PreferencesManager(this)
        
        // Apply screenshot prevention if enabled
        updateScreenshotPrevention()
        
        val app = application as QRCodeVaultApplication
        
        setContent {
            QRCodeVaultTheme {
                // Handle lifecycle for screenshot prevention changes
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                updateScreenshotPrevention()
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // No authentication on app open - go straight to main app
                // Vault (secret folder) has its own authentication
                MainNavigation(repository = app.repository)
            }
        }
    }
    
    private fun updateScreenshotPrevention() {
        if (prefsManager.screenshotPreventionEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
