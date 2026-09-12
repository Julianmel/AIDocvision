package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val category: String,
    val issuer: String = "",
    val docDate: String = "",
    val amount: Double? = null,
    val currency: String = "R$",
    val summary: String = "",
    val fullText: String = "",
    val extractedFieldsJson: String = "{}",
    val tags: String = "",
    val imageUri: String? = null,
    val cloudSyncStatus: String = "LOCAL", // "LOCAL", "PENDING", "SYNCED"
    val createdAt: Long = System.currentTimeMillis()
)
