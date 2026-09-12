package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importer.BulkDataManager
import com.example.data.local.AppDatabase
import com.example.data.model.DocumentEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DocMiner AI", appName)
  }

  @Test
  fun `test bulk data parsing and room database operations`() = runBlocking {
    val sampleCorpus = BulkDataManager.getSampleBrazilianCorpus()
    assertTrue("Amostra brasileira deve conter 8 documentos", sampleCorpus.size == 8)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val dao = inMemoryDb.documentDao()

    dao.insertDocuments(sampleCorpus)
    val doc = dao.getDocumentById(1L)
    assertNotNull("Documento deve ter sido inserido no Room", doc)

    val jsonExport = BulkDataManager.exportToJson(sampleCorpus)
    assertTrue("Exportação JSON deve conter faturas e contratos", jsonExport.contains("Enel Distribuição"))

    val csvExport = BulkDataManager.exportToCsv(sampleCorpus)
    assertTrue("Exportação CSV deve conter cabeçalho", csvExport.startsWith("ID,Título"))

    inMemoryDb.close()
  }
}
