package com.example.qrcodevault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qr_codes")
data class QRCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val type: String, // e.g., "TEXT", "WIFI", "URL"
    val label: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "", // "Personal", "Work", "WiFi", etc.
    val isSecret: Boolean = false, // If true, requires extra authentication
    val contentHash: String = content.hashCode().toString() // For duplicate detection
)

// Predefined categories
object QRCategories {
    const val ALL = ""
    const val PERSONAL = "Personal"
    const val WORK = "Work"
    const val WIFI = "WiFi"
    const val LINKS = "Links"
    const val OTHER = "Other"
    
    val defaultCategories = listOf(ALL, PERSONAL, WORK, WIFI, LINKS, OTHER)
}
