package com.example.qrcodevault.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    // Auto-lock timeout in milliseconds
    var autoLockTimeout: Long
        get() = prefs.getLong(KEY_AUTO_LOCK_TIMEOUT, DEFAULT_TIMEOUT_5_MIN)
        set(value) = prefs.edit { putLong(KEY_AUTO_LOCK_TIMEOUT, value) }
    
    // Last activity timestamp
    var lastActivityTime: Long
        get() = prefs.getLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
        set(value) = prefs.edit { putLong(KEY_LAST_ACTIVITY, value) }
    
    // Screenshot prevention enabled
    var screenshotPreventionEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREENSHOT_PREVENTION, true)
        set(value) = prefs.edit { putBoolean(KEY_SCREENSHOT_PREVENTION, value) }
    
    // Secret folder PIN (hashed)
    var secretFolderPinHash: String?
        get() = prefs.getString(KEY_SECRET_PIN_HASH, null)
        set(value) = prefs.edit { putString(KEY_SECRET_PIN_HASH, value) }
    
    // Secret folder uses biometric
    var secretFolderUseBiometric: Boolean
        get() = prefs.getBoolean(KEY_SECRET_USE_BIOMETRIC, true)
        set(value) = prefs.edit { putBoolean(KEY_SECRET_USE_BIOMETRIC, value) }
    
    // Check if auto-lock should trigger
    fun shouldAutoLock(): Boolean {
        if (autoLockTimeout == TIMEOUT_NEVER) return false
        val elapsed = System.currentTimeMillis() - lastActivityTime
        return elapsed > autoLockTimeout
    }
    
    // Update activity timestamp
    fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
    }
    
    // Hash a PIN for storage
    fun hashPin(pin: String): String {
        return pin.hashCode().toString() // Simple hash for demo; use proper hashing in production
    }
    
    // Verify PIN
    fun verifyPin(pin: String): Boolean {
        return secretFolderPinHash == hashPin(pin)
    }
    
    companion object {
        private const val PREFS_NAME = "qr_vault_prefs"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val KEY_LAST_ACTIVITY = "last_activity"
        private const val KEY_SCREENSHOT_PREVENTION = "screenshot_prevention"
        private const val KEY_SECRET_PIN_HASH = "secret_pin_hash"
        private const val KEY_SECRET_USE_BIOMETRIC = "secret_use_biometric"
        
        // Timeout values
        const val TIMEOUT_1_MIN = 60_000L
        const val TIMEOUT_5_MIN = 300_000L
        const val TIMEOUT_15_MIN = 900_000L
        const val TIMEOUT_30_MIN = 1_800_000L
        const val TIMEOUT_NEVER = -1L
        
        private const val DEFAULT_TIMEOUT_5_MIN = TIMEOUT_5_MIN
    }
}
