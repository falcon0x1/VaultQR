package com.example.qrcodevault

import android.app.Application
import com.example.qrcodevault.data.QRCodeDatabase
import com.example.qrcodevault.data.QRCodeRepository

class QRCodeVaultApplication : Application() {
    val database by lazy { QRCodeDatabase.getDatabase(this) }
    val repository by lazy { QRCodeRepository(database.qrCodeDao()) }
}
