package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BulkImportExportDialog
import com.example.ui.components.DocumentDetailDialog
import com.example.ui.components.DocumentScannerDialog
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.CloudAndDataScreen
import com.example.ui.screens.DataMiningScreen
import com.example.ui.screens.DocumentListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DocMinerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocMinerApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current

    // State observation
    val displayedDocs by viewModel.displayedDocuments.collectAsStateWithLifecycle()
    val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val selectedDoc by viewModel.selectedDocument.collectAsStateWithLifecycle()

    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val analysisStatus by viewModel.analysisStatus.collectAsStateWithLifecycle()
    val lastScannedDoc by viewModel.lastScannedDocument.collectAsStateWithLifecycle()

    val isMining by viewModel.isMining.collectAsStateWithLifecycle()
    val miningResult by viewModel.miningResult.collectAsStateWithLifecycle()

    val isGeneratingReport by viewModel.isGeneratingReport.collectAsStateWithLifecycle()
    val reportResult by viewModel.reportResult.collectAsStateWithLifecycle()

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()

    val isCloudSyncing by viewModel.isCloudSyncing.collectAsStateWithLifecycle()
    val cloudMessage by viewModel.cloudMessage.collectAsStateWithLifecycle()

    var currentTab by remember { mutableIntStateOf(0) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showBulkDialog by remember { mutableStateOf(false) }

    // Display feedback toast on cloud/bulk events
    LaunchedEffect(cloudMessage) {
        cloudMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearCloudMessage()
        }
    }

    // Automatically open detail dialog when a new document is successfully scanned
    LaunchedEffect(lastScannedDoc) {
        lastScannedDoc?.let { doc ->
            showScannerDialog = false
            viewModel.selectDocument(doc)
            viewModel.clearScannedDocument()
        }
    }

    val tabs = listOf(
        NavTabItem("Documentos", Icons.Default.Description, "nav_tab_documents"),
        NavTabItem("Data Mining", Icons.Default.Analytics, "nav_tab_datamining"),
        NavTabItem("Chat IA", Icons.Default.AutoAwesome, "nav_tab_chat"),
        NavTabItem("Nuvem", Icons.Default.CloudSync, "nav_tab_cloud")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DocMiner AI",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showBulkDialog = true },
                        modifier = Modifier.testTag("topbar_bulk_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Importar / Exportar em Lote",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, item ->
                    val isSelected = currentTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = index },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag(item.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                when (tab) {
                    0 -> DocumentListScreen(
                        documents = displayedDocs,
                        allDocumentsCount = allDocs.size,
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        sortOrder = sortOrder,
                        onSearchChange = viewModel::setSearchQuery,
                        onCategoryChange = viewModel::setSelectedCategory,
                        onSortChange = viewModel::setSortOrder,
                        onDocumentClick = viewModel::selectDocument,
                        onOpenScanner = { showScannerDialog = true },
                        onLoadDemoData = viewModel::loadBrazilianDemoDataset
                    )
                    1 -> DataMiningScreen(
                        documents = allDocs,
                        isMining = isMining,
                        miningResult = miningResult,
                        isGeneratingReport = isGeneratingReport,
                        reportResult = reportResult,
                        onRunMining = viewModel::runDataMining,
                        onGenerateReport = viewModel::generateReport
                    )
                    2 -> AiChatScreen(
                        messages = chatMessages,
                        isAiThinking = isAiThinking,
                        documentsCount = allDocs.size,
                        onSendMessage = viewModel::sendChatMessage,
                        onClearChat = viewModel::clearChatHistory
                    )
                    3 -> CloudAndDataScreen(
                        documents = allDocs,
                        isCloudSyncing = isCloudSyncing,
                        onSyncToCloud = viewModel::syncAllToCloud,
                        onOpenBulkDialog = { showBulkDialog = true },
                        onLoadDemoDataset = viewModel::loadBrazilianDemoDataset,
                        onClearAll = viewModel::clearAllDocuments
                    )
                }
            }
        }

        // Scanner Dialog
        if (showScannerDialog) {
            DocumentScannerDialog(
                isAnalyzing = isAnalyzing,
                analysisStatus = analysisStatus,
                onDismiss = { showScannerDialog = false },
                onScanBitmap = { bitmap ->
                    viewModel.scanDocumentBitmap(bitmap)
                },
                onScanUri = { uri ->
                    viewModel.scanDocumentUri(uri)
                },
                onProcessText = { title, text ->
                    viewModel.processRawTextDocument(title, text)
                }
            )
        }

        // Document Details Dialog
        selectedDoc?.let { doc ->
            DocumentDetailDialog(
                document = doc,
                onDismiss = { viewModel.selectDocument(null) },
                onDelete = {
                    viewModel.deleteDocument(it)
                },
                onSyncToggle = {
                    val updated = it.copy(
                        cloudSyncStatus = if (it.cloudSyncStatus == "SYNCED") "LOCAL" else "SYNCED"
                    )
                    viewModel.updateDocument(updated)
                }
            )
        }

        // Bulk Data Import / Export Dialog
        if (showBulkDialog) {
            BulkImportExportDialog(
                documents = allDocs,
                onDismiss = { showBulkDialog = false },
                onImportUri = { uri ->
                    viewModel.importBulkFromUri(uri)
                },
                onLoadDemoDataset = {
                    viewModel.loadBrazilianDemoDataset()
                },
                onClearAll = {
                    viewModel.clearAllDocuments()
                }
            )
        }
    }
}
