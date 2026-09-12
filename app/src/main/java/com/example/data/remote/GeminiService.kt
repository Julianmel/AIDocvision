package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class DocumentAnalysisResult(
    val title: String,
    val category: String,
    val issuer: String,
    val docDate: String,
    val amount: Double?,
    val currency: String,
    val summary: String,
    val fullText: String,
    val extractedFieldsJson: String,
    val tags: String
)

class GeminiService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val modelName = "gemini-3.5-flash"
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

    private fun getApiKey(): String {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isBlank() || key == "MY_GEMINI_API_KEY") "" else key
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun analyzeDocumentImage(bitmap: Bitmap): DocumentAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext fallbackAnalysis("Documento Digitalizado", "Fatura / Recibo", "OCR Local (Configure chave Gemini para leitura multimodal profunda)")
        }

        val base64Image = bitmapToBase64(bitmap)
        val prompt = """
            Você é uma IA avançada de OCR, visão computacional e extração de dados estruturados para Data Mining.
            Analise cuidadosamente a imagem deste documento.
            
            Retorne EXCLUSIVAMENTE um objeto JSON válido (sem blocos markdown ```json) com a seguinte estrutura:
            {
              "title": "Título conciso e descritivo do documento",
              "category": "Escolha exatamente uma: Fatura / Recibo, Contrato, Fiscal / Tributário, Identificação / Pessoal, Relatório Técnico, Correspondência / Ofício, Outros",
              "issuer": "Nome do emissor / empresa / instituição",
              "docDate": "Data do documento em formato YYYY-MM-DD ou DD/MM/AAAA",
              "amount": 123.45 (número float do valor total/principal, ou null se não houver valor monetário),
              "currency": "R$",
              "summary": "Resumo executivo de 2 a 3 frases sobre o teor e objetivo deste documento",
              "fullText": "Transcrição textual legível e completa de todo o conteúdo visível no documento",
              "extractedFields": {
                "Campo 1": "Valor 1",
                "Campo 2": "Valor 2"
              },
              "tags": "tag1, tag2, tag3"
            }
        """.trimIndent()

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        // Text prompt part
                        put(JSONObject().apply { put("text", prompt) })
                        // Image part
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            })
        }

        try {
            val responseText = executePost(requestJson.toString(), apiKey)
            parseAnalysisResponse(responseText)
        } catch (e: Exception) {
            Log.e("GeminiService", "Erro ao analisar imagem", e)
            fallbackAnalysis("Documento Capturado", "Outros", "Erro na leitura via IA: ${e.localizedMessage}")
        }
    }

    suspend fun analyzeDocumentText(rawText: String): DocumentAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext fallbackAnalysis("Registro de Texto", "Outros", rawText)
        }

        val prompt = """
            Você é uma IA de Processamento de Linguagem Natural e Data Mining.
            Analise o seguinte texto bruto de um documento/registro e extraia as informações estruturadas.
            
            TEXTO DO DOCUMENTO:
            $rawText
            
            Retorne EXCLUSIVAMENTE um objeto JSON válido com a seguinte estrutura:
            {
              "title": "Título conciso e descritivo do documento",
              "category": "Escolha exatamente uma: Fatura / Recibo, Contrato, Fiscal / Tributário, Identificação / Pessoal, Relatório Técnico, Correspondência / Ofício, Outros",
              "issuer": "Nome do emissor / empresa / instituição",
              "docDate": "Data do documento em formato YYYY-MM-DD ou DD/MM/AAAA",
              "amount": 123.45 (número float do valor total/principal, ou null se não houver valor monetário),
              "currency": "R$",
              "summary": "Resumo executivo de 2 a 3 frases sobre o teor deste documento",
              "fullText": "Texto completo transcrito ou formatado",
              "extractedFields": {
                "Campo 1": "Valor 1",
                "Campo 2": "Valor 2"
              },
              "tags": "tag1, tag2, tag3"
            }
        """.trimIndent()

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            })
        }

        try {
            val responseText = executePost(requestJson.toString(), apiKey)
            parseAnalysisResponse(responseText)
        } catch (e: Exception) {
            Log.e("GeminiService", "Erro ao analisar texto", e)
            fallbackAnalysis("Registro Importado", "Outros", rawText)
        }
    }

    suspend fun executeDataMining(documents: List<DocumentEntity>): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext buildLocalDataMiningSummary(documents)
        }

        if (documents.isEmpty()) {
            return@withContext "Nenhum documento cadastrado para realizar data mining. Capture fotos ou importe arquivos primeiro."
        }

        val docsSummary = buildDocumentsCorpusSummary(documents)

        val prompt = """
            Você é um Cientista de Dados e Auditor Especialista em Data Mining Corporativo.
            Analise a seguinte massa de dados contendo ${documents.size} documentos cadastrados:
            
            CORPUS DE DADOS:
            $docsSummary
            
            REALIZE UMA MINERAÇÃO COMPLETA DE DADOS (DATA MINING) ESTRUTURADA EM:
            1. 📊 PANORAMA GERAL & VOLUMETRIA:
               - Distribuição percentual por categoria.
               - Frequência e volume temporal de emissões.
            
            2. 💰 ANÁLISE FINANCEIRA & PADRÕES DE GASTOS:
               - Total financeiro consolidado e média por documento.
               - Principais fontes de custos / credores / fornecedores.
               - Padrões de gastos recorrentes ou desvios relevantes.
               
            3. 📑 GESTÃO DE CONTRATOS, TRIBUTOS E RISCOS:
               - Obrigações identificadas, contratos em vigor e prazos críticos.
               - Identificação de potenciais riscos de vencimento ou inconformidades.
               
            4. 🔍 ANOMALIAS & INSIGHTS DESCOBERTOS:
               - Dados atípicos, valores duplicados ou desvios de padrão detectados.
               - Cruzamento de dados entre documentos de emissores semelhantes.
               
            5. 💡 RECOMENDAÇÕES ESTRATÉGICAS:
               - Oportunidades de economia, organização ou renegociação.
               
            Escreva de forma profissional, executiva, clara e bem estruturada, usando títulos, tópicos e marcadores.
        """.trimIndent()

        val requestJson = createTextPromptJson(prompt)
        try {
            executePostAndExtractText(requestJson.toString(), apiKey)
        } catch (e: Exception) {
            Log.e("GeminiService", "Erro no Data Mining", e)
            "Erro ao processar mineração na nuvem: ${e.localizedMessage}\n\n${buildLocalDataMiningSummary(documents)}"
        }
    }

    suspend fun generateReport(reportType: String, customPrompt: String?, documents: List<DocumentEntity>): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "⚠️ Configure sua chave de API Gemini no painel Secrets para gerar relatórios detalhados com inteligência artificial.\n\n${buildLocalDataMiningSummary(documents)}"
        }

        if (documents.isEmpty()) {
            return@withContext "O acervo de documentos está vazio. Fotografe ou importe documentos para gerar relatórios."
        }

        val docsSummary = buildDocumentsCorpusSummary(documents)
        val prompt = """
            Você é um auditor sênior e consultor corporativo.
            Gere um relatório formal e detalhado do tipo: '$reportType'.
            
            ${if (!customPrompt.isNullOrBlank()) "DIRETRIZ ESPECÍFICA DO USUÁRIO: $customPrompt" else ""}
            
            BASE DE DOCUMENTOS PARA O RELATÓRIO:
            $docsSummary
            
            ESTRUTURA DO RELATÓRIO:
            - Cabeçalho executivo (Título, Data de Emissão, Escopo da Amostra)
            - Sumário Executivo
            - Análise Aprofundada dos Dados
            - Tabela Resumo dos Principais Itens (em texto formatado)
            - Conclusão e Parecer Técnico
            
            Seja minucioso com valores numéricos, emissores, datas e categorias.
        """.trimIndent()

        val requestJson = createTextPromptJson(prompt)
        try {
            executePostAndExtractText(requestJson.toString(), apiKey)
        } catch (e: Exception) {
            Log.e("GeminiService", "Erro ao gerar relatório", e)
            "Falha ao gerar relatório: ${e.localizedMessage}"
        }
    }

    suspend fun askQuestion(
        question: String,
        documents: List<DocumentEntity>,
        history: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "⚠️ Chave de API Gemini não configurada. Por favor, adicione sua GEMINI_API_KEY no painel Secrets para conversar com a IA sobre seus documentos."
        }

        val docsSummary = buildDocumentsCorpusSummary(documents)
        val historyContext = history.takeLast(4).joinToString("\n") { (user, ai) ->
            "Usuário: $user\nIA: $ai"
        }

        val prompt = """
            Você é o assistente inteligente de pesquisa e datamining do DocMiner AI.
            Você tem acesso direto aos documentos salvos pelo usuário:
            
            ACERVO DE DOCUMENTOS CADASTRADOS (${documents.size} itens):
            $docsSummary
            
            HISTÓRICO DA CONVERSA:
            $historyContext
            
            PERGUNTA ATUAL DO USUÁRIO:
            $question
            
            INSTRUÇÕES:
            - Responda de forma precisa, citando nomes dos documentos, emissores, valores (R$) e datas quando relevante.
            - Se a informação solicitada puder ser respondida a partir dos dados acima, forneça a resposta direta com clareza.
            - Se a informação não constar nos documentos, explique educadamente o que foi encontrado e o que está ausente.
            - Faça cálculos quando solicitado (somas, comparações, médias).
        """.trimIndent()

        val requestJson = createTextPromptJson(prompt)
        try {
            executePostAndExtractText(requestJson.toString(), apiKey)
        } catch (e: Exception) {
            Log.e("GeminiService", "Erro no chat IA", e)
            "Erro ao consultar IA: ${e.localizedMessage}"
        }
    }

    private fun createTextPromptJson(promptText: String): JSONObject {
        return JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", promptText) })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
            })
        }
    }

    private fun executePost(bodyString: String, apiKey: String): String {
        val request = Request.Builder()
            .url("$baseUrl?key=$apiKey")
            .post(bodyString.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: $responseBody")
            }
            return responseBody
        }
    }

    private fun executePostAndExtractText(bodyString: String, apiKey: String): String {
        val rawResponse = executePost(bodyString, apiKey)
        val json = JSONObject(rawResponse)
        val candidates = json.optJSONArray("candidates") ?: return "Sem resposta da IA."
        if (candidates.length() == 0) return "Sem candidatos gerados."
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return "Conteúdo vazio."
        val parts = content.optJSONArray("parts") ?: return "Sem partes na resposta."
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            sb.append(part.optString("text", ""))
        }
        return sb.toString().trim()
    }

    private fun parseAnalysisResponse(responseJsonStr: String): DocumentAnalysisResult {
        val json = JSONObject(responseJsonStr)
        val candidates = json.optJSONArray("candidates") ?: throw Exception("Nenhum candidato retornado.")
        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        var text = parts.getJSONObject(0).getString("text")

        // Clean any markdown formatting if present
        text = text.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json").trim()
        }
        if (text.startsWith("```")) {
            text = text.removePrefix("```").trim()
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }

        val data = JSONObject(text)
        val title = data.optString("title", "Documento Sem Título")
        val category = data.optString("category", "Outros")
        val issuer = data.optString("issuer", "")
        val docDate = data.optString("docDate", "")
        val amount = if (data.has("amount") && !data.isNull("amount")) data.optDouble("amount") else null
        val currency = data.optString("currency", "R$")
        val summary = data.optString("summary", "")
        val fullText = data.optString("fullText", "")
        val extractedFields = data.optJSONObject("extractedFields")?.toString() ?: "{}"
        val tags = data.optString("tags", "")

        return DocumentAnalysisResult(
            title = title,
            category = category,
            issuer = issuer,
            docDate = docDate,
            amount = amount,
            currency = currency,
            summary = summary,
            fullText = fullText,
            extractedFieldsJson = extractedFields,
            tags = tags
        )
    }

    private fun fallbackAnalysis(title: String, category: String, text: String): DocumentAnalysisResult {
        return DocumentAnalysisResult(
            title = title,
            category = category,
            issuer = "Não identificado",
            docDate = "",
            amount = null,
            currency = "R$",
            summary = "Documento importado/digitalizado para organização e datamining.",
            fullText = text,
            extractedFieldsJson = "{}",
            tags = "digitalizado, local"
        )
    }

    private fun buildDocumentsCorpusSummary(documents: List<DocumentEntity>): String {
        val sb = StringBuilder()
        documents.take(50).forEachIndexed { index, doc ->
            sb.appendLine("--- DOCUMENTO #${index + 1} ---")
            sb.appendLine("ID: ${doc.id}")
            sb.appendLine("Título: ${doc.title}")
            sb.appendLine("Categoria: ${doc.category}")
            sb.appendLine("Emissor: ${doc.issuer}")
            sb.appendLine("Data: ${doc.docDate}")
            if (doc.amount != null) {
                sb.appendLine("Valor: ${doc.currency} ${"%.2f".format(doc.amount)}")
            }
            sb.appendLine("Tags: ${doc.tags}")
            sb.appendLine("Resumo: ${doc.summary}")
            if (doc.extractedFieldsJson != "{}" && doc.extractedFieldsJson.isNotBlank()) {
                sb.appendLine("Campos Extraídos: ${doc.extractedFieldsJson}")
            }
            sb.appendLine("Trecho Texto OCR: ${doc.fullText.take(200)}")
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun buildLocalDataMiningSummary(documents: List<DocumentEntity>): String {
        if (documents.isEmpty()) return "Nenhum documento para minerar."
        val totalAmount = documents.mapNotNull { it.amount }.sum()
        val categoryCounts = documents.groupBy { it.category }.mapValues { it.value.size }
        val topIssuers = documents.map { it.issuer.ifBlank { "Sem emissor" } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)

        val sb = StringBuilder()
        sb.appendLine("📊 RESUMO DE DATA MINING LOCAL")
        sb.appendLine("• Total de Documentos: ${documents.size}")
        sb.appendLine("• Soma Financeira Identificada: R$ ${"%.2f".format(totalAmount)}")
        sb.appendLine("\n📁 Distribuição por Categorias:")
        categoryCounts.forEach { (cat, count) ->
            sb.appendLine("  - $cat: $count (${"%.1f".format(count * 100.0 / documents.size)}%)")
        }
        sb.appendLine("\n🏢 Principais Emissores:")
        topIssuers.forEach { (issuer, count) ->
            sb.appendLine("  - $issuer: $count documentos")
        }
        sb.appendLine("\n💡 Dica: Conecte sua GEMINI_API_KEY no painel Secrets para data mining avançado com IA generativa!")
        return sb.toString()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Resize bitmap if very large to prevent memory overhead while keeping high OCR fidelity
        val maxDimension = 1600
        val scale = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val max = maxOf(bitmap.width, bitmap.height)
            maxDimension.toFloat() / max
        } else {
            1f
        }

        val scaledBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
