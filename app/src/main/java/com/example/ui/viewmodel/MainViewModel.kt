package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.importer.BulkDataManager
import com.example.data.local.AppDatabase
import com.example.data.model.DocumentEntity
import com.example.data.remote.GeminiService
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    TITLE_ASC
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    private val geminiService = GeminiService()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = DocumentRepository(db.documentDao())
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Todos")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedDocument = MutableStateFlow<DocumentEntity?>(null)
    val selectedDocument: StateFlow<DocumentEntity?> = _selectedDocument.asStateFlow()

    // Analysis / Scan state
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisStatus = MutableStateFlow("")
    val analysisStatus: StateFlow<String> = _analysisStatus.asStateFlow()

    private val _lastScannedDocument = MutableStateFlow<DocumentEntity?>(null)
    val lastScannedDocument: StateFlow<DocumentEntity?> = _lastScannedDocument.asStateFlow()

    // Data Mining state
    private val _isMining = MutableStateFlow(false)
    val isMining: StateFlow<Boolean> = _isMining.asStateFlow()

    private val _miningResult = MutableStateFlow<String?>(null)
    val miningResult: StateFlow<String?> = _miningResult.asStateFlow()

    // Report generation state
    private val _isGeneratingReport = MutableStateFlow(false)
    val isGeneratingReport: StateFlow<Boolean> = _isGeneratingReport.asStateFlow()

    private val _reportResult = MutableStateFlow<String?>(null)
    val reportResult: StateFlow<String?> = _reportResult.asStateFlow()

    // Q&A Chat state
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            sender = "ai",
            text = "Olá! Sou a IA do DocMiner. Posso responder a qualquer dúvida, cruzar dados financeiros, consultar contratos ou fazer análises nos seus documentos salvos. O que você gostaria de saber hoje?"
        )
    ))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    // Cloud sync state
    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _cloudMessage = MutableStateFlow<String?>(null)
    val cloudMessage: StateFlow<String?> = _cloudMessage.asStateFlow()

    // Raw documents from Room
    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered and sorted documents stream
    val displayedDocuments: StateFlow<List<DocumentEntity>> = combine(
        allDocuments,
        searchQuery,
        selectedCategory,
        sortOrder
    ) { docs, query, category, sort ->
        var list = docs

        // Category filter
        if (category != "Todos") {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }

        // Search query filter
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.issuer.lowercase().contains(q) ||
                it.tags.lowercase().contains(q) ||
                it.summary.lowercase().contains(q) ||
                it.fullText.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }

        // Sorting
        when (sort) {
            SortOrder.DATE_DESC -> list.sortedByDescending { it.docDate.ifBlank { it.createdAt.toString() } }
            SortOrder.DATE_ASC -> list.sortedBy { it.docDate.ifBlank { it.createdAt.toString() } }
            SortOrder.AMOUNT_DESC -> list.sortedByDescending { it.amount ?: 0.0 }
            SortOrder.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun selectDocument(document: DocumentEntity?) {
        _selectedDocument.value = document
    }

    fun clearScannedDocument() {
        _lastScannedDocument.value = null
    }

    fun clearCloudMessage() {
        _cloudMessage.value = null
    }

    fun scanDocumentBitmap(bitmap: Bitmap, imageUri: String? = null) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisStatus.value = "Digitalizando imagem e preparando análise..."

            try {
                _analysisStatus.value = "IA Gemini: Lendo conteúdo e transcrevendo OCR..."
                val result = geminiService.analyzeDocumentImage(bitmap)

                _analysisStatus.value = "Estruturando campos e salvando no acervo..."
                val entity = DocumentEntity(
                    title = result.title,
                    category = result.category,
                    issuer = result.issuer,
                    docDate = result.docDate,
                    amount = result.amount,
                    currency = result.currency,
                    summary = result.summary,
                    fullText = result.fullText,
                    extractedFieldsJson = result.extractedFieldsJson,
                    tags = result.tags,
                    imageUri = imageUri,
                    cloudSyncStatus = "SYNCED"
                )

                val newId = repository.insert(entity)
                val savedEntity = entity.copy(id = newId)
                _lastScannedDocument.value = savedEntity
                _analysisStatus.value = "Concluído com sucesso!"
            } catch (e: Exception) {
                _analysisStatus.value = "Erro na digitalização: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun scanDocumentUri(uri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisStatus.value = "Carregando imagem do documento..."
            try {
                val context = getApplication<Application>()
                @Suppress("DEPRECATION")
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                scanDocumentBitmap(bitmap, uri.toString())
            } catch (e: Exception) {
                _analysisStatus.value = "Erro ao ler imagem: ${e.localizedMessage}"
                _isAnalyzing.value = false
            }
        }
    }

    fun processRawTextDocument(title: String, rawText: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisStatus.value = "IA Gemini: Processando texto e extraindo campos..."

            try {
                val result = geminiService.analyzeDocumentText(rawText)
                val entity = DocumentEntity(
                    title = if (title.isNotBlank()) title else result.title,
                    category = result.category,
                    issuer = result.issuer,
                    docDate = result.docDate,
                    amount = result.amount,
                    currency = result.currency,
                    summary = result.summary,
                    fullText = result.fullText,
                    extractedFieldsJson = result.extractedFieldsJson,
                    tags = result.tags,
                    cloudSyncStatus = "LOCAL"
                )
                val newId = repository.insert(entity)
                _lastScannedDocument.value = entity.copy(id = newId)
                _analysisStatus.value = "Documento textual processado com sucesso!"
            } catch (e: Exception) {
                _analysisStatus.value = "Erro: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun runDataMining() {
        viewModelScope.launch {
            _isMining.value = true
            try {
                val docs = allDocuments.value
                val result = geminiService.executeDataMining(docs)
                _miningResult.value = result
            } catch (e: Exception) {
                _miningResult.value = "Falha no Data Mining: ${e.localizedMessage}"
            } finally {
                _isMining.value = false
            }
        }
    }

    fun generateReport(reportType: String, customPrompt: String? = null) {
        viewModelScope.launch {
            _isGeneratingReport.value = true
            try {
                val docs = allDocuments.value
                val result = geminiService.generateReport(reportType, customPrompt, docs)
                _reportResult.value = result
            } catch (e: Exception) {
                _reportResult.value = "Erro ao gerar relatório: ${e.localizedMessage}"
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }

    fun sendChatMessage(question: String) {
        if (question.isBlank()) return
        val currentHistory = _chatMessages.value
        val userMsg = ChatMessage(sender = "user", text = question.trim())
        _chatMessages.value = currentHistory + userMsg

        viewModelScope.launch {
            _isAiThinking.value = true
            try {
                val historyPairs = _chatMessages.value.map { it.sender to it.text }
                val response = geminiService.askQuestion(question, allDocuments.value, historyPairs)
                val aiMsg = ChatMessage(sender = "ai", text = response)
                _chatMessages.value = _chatMessages.value + aiMsg
            } catch (e: Exception) {
                val errorMsg = ChatMessage(sender = "ai", text = "Erro na resposta da IA: ${e.localizedMessage}")
                _chatMessages.value = _chatMessages.value + errorMsg
            } finally {
                _isAiThinking.value = false
            }
        }
    }

    fun clearChatHistory() {
        _chatMessages.value = listOf(
            ChatMessage(
                sender = "ai",
                text = "Conversa reiniciada. O que você gostaria de minerar ou perguntar sobre os documentos?"
            )
        )
    }

    fun syncAllToCloud() {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            try {
                repository.syncAllToCloud()
                _cloudMessage.value = "Todos os ${allDocuments.value.size} documentos foram sincronizados na nuvem!"
            } catch (e: Exception) {
                _cloudMessage.value = "Erro ao sincronizar na nuvem: ${e.localizedMessage}"
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    fun importBulkFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val content = BulkDataManager.readUriContent(context, uri)
                val importedList = if (content.trim().startsWith("[") || content.trim().startsWith("{")) {
                    BulkDataManager.parseJsonContent(content)
                } else {
                    BulkDataManager.parseCsvContent(content)
                }

                if (importedList.isNotEmpty()) {
                    repository.insertAll(importedList)
                    _cloudMessage.value = "${importedList.size} documentos importados com sucesso a partir do arquivo!"
                } else {
                    _cloudMessage.value = "Nenhum documento reconhecido no arquivo selecionado."
                }
            } catch (e: Exception) {
                _cloudMessage.value = "Erro ao importar arquivo: ${e.localizedMessage}"
            }
        }
    }

    fun loadBrazilianDemoDataset() {
        viewModelScope.launch {
            val demoList = BulkDataManager.getSampleBrazilianCorpus()
            repository.insertAll(demoList)
            _cloudMessage.value = "Massa de teste carregada! ${demoList.size} documentos diversos inseridos para Data Mining."
        }
    }

    fun clearAllDocuments() {
        viewModelScope.launch {
            repository.deleteAll()
            _miningResult.value = null
            _reportResult.value = null
            _cloudMessage.value = "Base de documentos limpa com sucesso."
        }
    }

    fun updateDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.update(doc)
            _selectedDocument.value = doc
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.delete(doc)
            if (_selectedDocument.value?.id == doc.id) {
                _selectedDocument.value = null
            }
        }
    }
}
