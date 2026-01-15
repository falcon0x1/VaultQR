package com.example.qrcodevault.data

import kotlinx.coroutines.flow.Flow

class QRCodeRepository(private val qrCodeDao: QRCodeDao) {
    
    // Standard vault (non-secret)
    val allQrCodes: Flow<List<QRCodeEntity>> = qrCodeDao.getAllQrCodes()
    
    // All codes including secret
    val allQrCodesIncludingSecret: Flow<List<QRCodeEntity>> = qrCodeDao.getAllQrCodesIncludingSecret()
    
    // Secret codes only
    val secretQrCodes: Flow<List<QRCodeEntity>> = qrCodeDao.getSecretQrCodes()
    
    // Get by category
    fun getByCategory(category: String): Flow<List<QRCodeEntity>> {
        return if (category.isEmpty()) {
            allQrCodes
        } else {
            qrCodeDao.getQrCodesByCategory(category)
        }
    }
    
    // Search
    fun search(query: String): Flow<List<QRCodeEntity>> {
        return qrCodeDao.searchQrCodes(query)
    }
    
    // Check for duplicate
    suspend fun isDuplicate(content: String): Boolean {
        return qrCodeDao.countByContent(content) > 0
    }
    
    // Find existing by content
    suspend fun findByContentHash(content: String): QRCodeEntity? {
        val hash = content.hashCode().toString()
        return qrCodeDao.findByContentHash(hash)
    }

    suspend fun insert(qrCode: QRCodeEntity) {
        qrCodeDao.insertQrCode(qrCode)
    }
    
    suspend fun update(qrCode: QRCodeEntity) {
        qrCodeDao.updateQrCode(qrCode)
    }

    suspend fun delete(qrCode: QRCodeEntity) {
        qrCodeDao.deleteQrCode(qrCode)
    }
    
    suspend fun moveToSecret(id: Int) {
        qrCodeDao.setSecretStatus(id, true)
    }
    
    suspend fun removeFromSecret(id: Int) {
        qrCodeDao.setSecretStatus(id, false)
    }
    
    suspend fun updateCategory(id: Int, category: String) {
        qrCodeDao.updateCategory(id, category)
    }
}
