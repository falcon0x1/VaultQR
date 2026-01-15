package com.example.qrcodevault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [QRCodeEntity::class], version = 2, exportSchema = false)
abstract class QRCodeDatabase : RoomDatabase() {
    abstract fun qrCodeDao(): QRCodeDao

    companion object {
        @Volatile
        private var Instance: QRCodeDatabase? = null

        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns with default values
                db.execSQL("ALTER TABLE qr_codes ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE qr_codes ADD COLUMN isSecret INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE qr_codes ADD COLUMN contentHash TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): QRCodeDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, QRCodeDatabase::class.java, "qrcode_vault_database")
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
