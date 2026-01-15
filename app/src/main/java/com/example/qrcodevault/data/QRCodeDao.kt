package com.example.qrcodevault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QRCodeDao {
    // Get all non-secret codes
    @Query("SELECT * FROM qr_codes WHERE isSecret = 0 ORDER BY timestamp DESC")
    fun getAllQrCodes(): Flow<List<QRCodeEntity>>
    
    // Get all codes including secret (for authenticated access)
    @Query("SELECT * FROM qr_codes ORDER BY timestamp DESC")
    fun getAllQrCodesIncludingSecret(): Flow<List<QRCodeEntity>>
    
    // Get only secret codes
    @Query("SELECT * FROM qr_codes WHERE isSecret = 1 ORDER BY timestamp DESC")
    fun getSecretQrCodes(): Flow<List<QRCodeEntity>>
    
    // Get codes by category
    @Query("SELECT * FROM qr_codes WHERE category = :category AND isSecret = 0 ORDER BY timestamp DESC")
    fun getQrCodesByCategory(category: String): Flow<List<QRCodeEntity>>
    
    // Search codes
    @Query("SELECT * FROM qr_codes WHERE isSecret = 0 AND (label LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchQrCodes(query: String): Flow<List<QRCodeEntity>>
    
    // Check for duplicate by content hash
    @Query("SELECT * FROM qr_codes WHERE contentHash = :hash LIMIT 1")
    suspend fun findByContentHash(hash: String): QRCodeEntity?
    
    // Check if content exists
    @Query("SELECT COUNT(*) FROM qr_codes WHERE content = :content")
    suspend fun countByContent(content: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQrCode(qrCode: QRCodeEntity)

    @Update
    suspend fun updateQrCode(qrCode: QRCodeEntity)

    @Delete
    suspend fun deleteQrCode(qrCode: QRCodeEntity)
    
    // Move to/from secret folder
    @Query("UPDATE qr_codes SET isSecret = :isSecret WHERE id = :id")
    suspend fun setSecretStatus(id: Int, isSecret: Boolean)
    
    // Update category
    @Query("UPDATE qr_codes SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Int, category: String)
}
