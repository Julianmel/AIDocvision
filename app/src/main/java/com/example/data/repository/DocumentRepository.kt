package com.example.data.repository

import com.example.data.local.DocumentDao
import com.example.data.model.DocumentEntity
import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val documentDao: DocumentDao) {

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> {
        return if (query.isBlank()) {
            documentDao.getAllDocuments()
        } else {
            documentDao.searchDocuments(query.trim())
        }
    }

    fun getDocumentsByCategory(category: String): Flow<List<DocumentEntity>> {
        return if (category == "Todos" || category.isBlank()) {
            documentDao.getAllDocuments()
        } else {
            documentDao.getDocumentsByCategory(category)
        }
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? = documentDao.getDocumentById(id)

    suspend fun insert(document: DocumentEntity): Long = documentDao.insertDocument(document)

    suspend fun insertAll(documents: List<DocumentEntity>) = documentDao.insertDocuments(documents)

    suspend fun update(document: DocumentEntity) = documentDao.updateDocument(document)

    suspend fun delete(document: DocumentEntity) = documentDao.deleteDocument(document)

    suspend fun deleteById(id: Long) = documentDao.deleteDocumentById(id)

    suspend fun deleteAll() = documentDao.deleteAllDocuments()

    suspend fun syncAllToCloud() = documentDao.syncAllDocumentsToCloud()

    suspend fun updateSyncStatus(id: Long, status: String) = documentDao.updateSyncStatus(id, status)
}
