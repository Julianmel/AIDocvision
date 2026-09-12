package com.example.data.importer

import android.content.Context
import android.net.Uri
import com.example.data.model.DocumentEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object BulkDataManager {

    fun parseJsonContent(jsonString: String): List<DocumentEntity> {
        val result = mutableListOf<DocumentEntity>()
        val trimmed = jsonString.trim()

        if (trimmed.startsWith("[")) {
            val jsonArray = JSONArray(trimmed)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(parseSingleJsonObject(obj))
            }
        } else if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            if (root.has("documents")) {
                val arr = root.getJSONArray("documents")
                for (i in 0 until arr.length()) {
                    result.add(parseSingleJsonObject(arr.getJSONObject(i)))
                }
            } else {
                result.add(parseSingleJsonObject(root))
            }
        }
        return result
    }

    private fun parseSingleJsonObject(obj: JSONObject): DocumentEntity {
        val title = obj.optString("title", "Documento Importado")
        val category = obj.optString("category", "Outros")
        val issuer = obj.optString("issuer", "")
        val docDate = obj.optString("docDate", obj.optString("date", ""))
        val amount = if (obj.has("amount") && !obj.isNull("amount")) obj.optDouble("amount") else null
        val currency = obj.optString("currency", "R$")
        val summary = obj.optString("summary", "")
        val fullText = obj.optString("fullText", obj.optString("text", ""))
        val extractedFields = if (obj.has("extractedFields")) {
            obj.optJSONObject("extractedFields")?.toString() ?: "{}"
        } else if (obj.has("extractedFieldsJson")) {
            obj.optString("extractedFieldsJson", "{}")
        } else "{}"
        val tags = obj.optString("tags", "importado")

        return DocumentEntity(
            title = title,
            category = category,
            issuer = issuer,
            docDate = docDate,
            amount = amount,
            currency = currency,
            summary = summary,
            fullText = fullText,
            extractedFieldsJson = extractedFields,
            tags = tags,
            cloudSyncStatus = "LOCAL"
        )
    }

    fun parseCsvContent(csvString: String): List<DocumentEntity> {
        val lines = csvString.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val header = lines.first().split(",", ";").map { it.trim().lowercase() }
        val result = mutableListOf<DocumentEntity>()

        for (i in 1 until lines.size) {
            val parts = lines[i].split(",", ";").map { it.trim().removeSurrounding("\"") }
            if (parts.isEmpty()) continue

            var title = "Documento CSV #$i"
            var category = "Outros"
            var issuer = ""
            var date = ""
            var amount: Double? = null
            var summary = ""
            var fullText = ""
            var tags = "csv, importado"

            header.forEachIndexed { colIndex, colName ->
                val value = parts.getOrNull(colIndex) ?: ""
                when {
                    colName.contains("tit") || colName.contains("title") || colName.contains("nome") -> title = value
                    colName.contains("cat") -> category = value
                    colName.contains("emissor") || colName.contains("issuer") || colName.contains("empresa") -> issuer = value
                    colName.contains("data") || colName.contains("date") -> date = value
                    colName.contains("valor") || colName.contains("amount") || colName.contains("total") -> {
                        amount = value.replace("R$", "").replace(" ", "").replace(",", ".").toDoubleOrNull()
                    }
                    colName.contains("resumo") || colName.contains("summary") -> summary = value
                    colName.contains("texto") || colName.contains("text") || colName.contains("conteudo") -> fullText = value
                    colName.contains("tag") -> tags = value
                }
            }

            result.add(
                DocumentEntity(
                    title = title,
                    category = category,
                    issuer = issuer,
                    docDate = date,
                    amount = amount,
                    currency = "R$",
                    summary = summary.ifBlank { "Importado via lote CSV com sucesso." },
                    fullText = fullText.ifBlank { "Registro bruto importado de planilha CSV." },
                    extractedFieldsJson = "{}",
                    tags = tags,
                    cloudSyncStatus = "LOCAL"
                )
            )
        }
        return result
    }

    fun readUriContent(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).readText()
        } ?: ""
    }

    fun exportToJson(documents: List<DocumentEntity>): String {
        val root = JSONObject()
        val array = JSONArray()
        documents.forEach { doc ->
            val obj = JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("category", doc.category)
                put("issuer", doc.issuer)
                put("docDate", doc.docDate)
                put("amount", doc.amount)
                put("currency", doc.currency)
                put("summary", doc.summary)
                put("fullText", doc.fullText)
                put("extractedFieldsJson", doc.extractedFieldsJson)
                put("tags", doc.tags)
                put("cloudSyncStatus", doc.cloudSyncStatus)
                put("createdAt", doc.createdAt)
            }
            array.put(obj)
        }
        root.put("exportDate", System.currentTimeMillis())
        root.put("totalRecords", documents.size)
        root.put("documents", array)
        return root.toString(2)
    }

    fun exportToCsv(documents: List<DocumentEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("ID,Título,Categoria,Emissor,Data,Valor,Moeda,Status Nuvem,Tags,Resumo")
        documents.forEach { doc ->
            val title = "\"${doc.title.replace("\"", "\"\"")}\""
            val category = "\"${doc.category.replace("\"", "\"\"")}\""
            val issuer = "\"${doc.issuer.replace("\"", "\"\"")}\""
            val docDate = "\"${doc.docDate.replace("\"", "\"\"")}\""
            val amount = doc.amount?.let { "%.2f".format(it) } ?: ""
            val currency = doc.currency
            val status = doc.cloudSyncStatus
            val tags = "\"${doc.tags.replace("\"", "\"\"")}\""
            val summary = "\"${doc.summary.replace("\"", "\"\"")}\""
            sb.appendLine("${doc.id},$title,$category,$issuer,$docDate,$amount,$currency,$status,$tags,$summary")
        }
        return sb.toString()
    }

    fun getSampleBrazilianCorpus(): List<DocumentEntity> {
        return listOf(
            DocumentEntity(
                title = "Fatura Enel Distribuição - 08/2026",
                category = "Fatura / Recibo",
                issuer = "Enel Distribuição SP",
                docDate = "2026-08-10",
                amount = 485.60,
                currency = "R$",
                summary = "Fatura mensal de consumo de energia elétrica referente a julho/agosto 2026. Consumo registrado de 420 kWh com bandeira tarifária verde.",
                fullText = "ENEL DISTRIBUIÇÃO SÃO PAULO S.A. CNPJ: 61.695.227/0001-93. Unidade Consumidora: 00984214-7. Mês Referência: 08/2026. Vencimento: 22/08/2026. Total a Pagar: R$ 485,60. Consumo: 420 kWh. Código de Barras: 83670000004 85600108202 60098421470 20260822000.",
                extractedFieldsJson = """{"CNPJ": "61.695.227/0001-93", "Unidade Consumidora": "00984214-7", "Vencimento": "2026-08-22", "Consumo kWh": "420 kWh", "Bandeira": "Verde"}""",
                tags = "energia, enel, despesa fixa, utilidades",
                cloudSyncStatus = "SYNCED"
            ),
            DocumentEntity(
                title = "Contrato Prestação Serviços TI Cloud",
                category = "Contrato",
                issuer = "Nexus Tech Cloud Soluções Ltda",
                docDate = "2026-02-15",
                amount = 24000.00,
                currency = "R$",
                summary = "Instrumento contratual de prestação de serviços de arquitetura em nuvem, suporte DevOps e inteligência de dados, com vigência de 12 meses e parcelas mensais de R$ 2.000,00.",
                fullText = "CONTRATO DE PRESTAÇÃO DE SERVIÇOS TÉCNICOS DE TI Nº 2026/042. CONTRATADA: Nexus Tech Cloud Soluções Ltda, CNPJ 33.456.789/0001-12. CLÁUSULA 3ª - DO VALOR E FORMA DE PAGAMENTO: O valor global é de R$ 24.000,00 divididos em 12 parcelas de R$ 2.000,00. Vigência: 15/02/2026 a 14/02/2027.",
                extractedFieldsJson = """{"Contrato Nº": "2026/042", "Vigência Inicial": "2026-02-15", "Vigência Final": "2027-02-14", "Parcela Mensal": "R$ 2.000,00", "Objeto": "DevOps & Cloud"}""",
                tags = "contrato, ti, nuvem, recorrente",
                cloudSyncStatus = "SYNCED"
            ),
            DocumentEntity(
                title = "NFS-e Consultoria Estratégica",
                category = "Fiscal / Tributário",
                issuer = "Prisma Governança e Finanças",
                docDate = "2026-07-28",
                amount = 5800.00,
                currency = "R$",
                summary = "Nota Fiscal de Serviços Eletrônica emitida pela Prefeitura Municipal referente a honorários de consultoria contábil e estruturação societária.",
                fullText = "PREFEITURA MUNICIPAL - NOTA FISCAL DE SERVIÇOS ELETRÔNICA - NFS-e Nº 0019284. Prestador: Prisma Governança, CNPJ 19.827.364/0001-50. Tomador: Julian Empreendimentos. Discriminação dos Serviços: Consultoria tributária e revisão fiscal 2º Trimestre/2026. Valor dos Serviços: R$ 5.800,00. ISS Retido: Não.",
                extractedFieldsJson = """{"NFS-e Nº": "0019284", "Código Tributação": "17.01", "ISS": "R$ 290,00", "Tomador": "Julian Empreendimentos"}""",
                tags = "fiscal, nfs-e, consultoria, tributário",
                cloudSyncStatus = "SYNCED"
            ),
            DocumentEntity(
                title = "Comprovante Transferência PIX Fornecedor",
                category = "Fatura / Recibo",
                issuer = "Banco Inter / PIX",
                docDate = "2026-08-05",
                amount = 1350.00,
                currency = "R$",
                summary = "Comprovante de pagamento instantâneo via PIX para aquisição de suprimentos de escritório e periféricos computacionais.",
                fullText = "COMPROVANTE DE TRANSFERÊNCIA PIX. Instituição: Banco Inter. Data/Hora: 05/08/2026 14:32:10. ID da Transação: E00416968202608051432xkz9812. Beneficiário: Oficina Maker Distribuidora, Chave PIX: contato@makerloja.com.br. Valor: R$ 1.350,00.",
                extractedFieldsJson = """{"EndToEndId": "E00416968202608051432xkz9812", "Chave PIX": "contato@makerloja.com.br", "Tipo Transferência": "Instantânea"}""",
                tags = "pix, comprovante, suprimentos, banco",
                cloudSyncStatus = "LOCAL"
            ),
            DocumentEntity(
                title = "Apólice Seguro Empresarial All-Risks",
                category = "Contrato",
                issuer = "Porto Seguro Companhia de Seguros",
                docDate = "2026-05-10",
                amount = 3600.00,
                currency = "R$",
                summary = "Apólice de seguro multirrisco para proteção patrimonial, cobertura contra incêndio, danos elétricos e responsabilidade civil das instalações físicas.",
                fullText = "PORTO SEGURO CIA DE SEGUROS GERAIS. Apólice Nº 0531.10.88721. Vigência: 10/05/2026 até 10/05/2027. Cobertura Básica: Incêndio, Raio e Explosão (LMI R$ 1.500.000,00). Prêmio Total: R$ 3.600,00 em 4 parcelas de R$ 900,00.",
                extractedFieldsJson = """{"Apólice Nº": "0531.10.88721", "LMI": "R$ 1.500.000,00", "Vigência Término": "2027-05-10", "Franquia": "R$ 1.000,00"}""",
                tags = "seguro, patrimônio, porto seguro, contrato",
                cloudSyncStatus = "SYNCED"
            ),
            DocumentEntity(
                title = "Relatório Auditoria Conexões de Rede",
                category = "Relatório Técnico",
                issuer = "Departamento de Cibersegurança",
                docDate = "2026-08-01",
                amount = null,
                currency = "R$",
                summary = "Relatório técnico avaliando a integridade da infraestrutura de telecomunicações, testes de penetração e conformidade com a LGPD.",
                fullText = "RELATÓRIO TÉCNICO DE AUDITORIA DE REDE E CONFORMIDADE LGPD. Período Avaliado: Julho/2026. Escopo: Servidores de banco de dados, endpoints móveis e gateways VPN. Conclusão: 98.4% de conformidade com padrões CIS Benchmarks. Nenhuma vulnerabilidade crítica ativa.",
                extractedFieldsJson = """{"Conformidade": "98.4%", "Vulnerabilidades Críticas": "0", "Classificação": "Excelente"}""",
                tags = "auditoria, ti, seguranca, lgpd, relatorio",
                cloudSyncStatus = "SYNCED"
            ),
            DocumentEntity(
                title = "Recibo Locação Sala Comercial 402",
                category = "Fatura / Recibo",
                issuer = "Administradora Imobiliária Horizonte",
                docDate = "2026-08-01",
                amount = 3200.00,
                currency = "R$",
                summary = "Recibo de quitação de aluguel comercial e taxa de condomínio referente à Sala 402 do Edifício Metropolitan Business.",
                fullText = "RECIBO DE ALUGUEL E ENCARGOS LOCATÍCIOS. Locador: Administradora Imobiliária Horizonte. Imóvel: Conjunto 402 - Av. Paulista, 1000. Aluguel: R$ 2.400,00. Condomínio: R$ 650,00. IPTU: R$ 150,00. Total Pago: R$ 3.200,00. Quitado em 01/08/2026.",
                extractedFieldsJson = """{"Imóvel": "Sala 402", "Aluguel": "R$ 2.400,00", "Condomínio": "R$ 650,00", "IPTU": "R$ 150,00"}""",
                tags = "aluguel, imovel, escritorio, fixo",
                cloudSyncStatus = "SYNCED"
            ),
            DocumentEntity(
                title = "Certidão Negativa de Débitos Federais (CND)",
                category = "Fiscal / Tributário",
                issuer = "Receita Federal do Brasil / PGFN",
                docDate = "2026-06-15",
                amount = null,
                currency = "R$",
                summary = "Certidão Conjunta Positiva com Efeitos de Negativa de Débitos relativos aos Tributos Federais e à Dívida Ativa da União, válida por 180 dias.",
                fullText = "MINISTÉRIO DA FAZENDA - SECRETARIA DA RECEITA FEDERAL DO BRASIL. CERTIDÃO DE DÉBITOS RELATIVOS A CRÉDITOS TRIBUTÁRIOS FEDERAIS. Código de Controle: 8F2A.99B1.442E.11A0. Emissão: 15/06/2026. Validade: 12/12/2026.",
                extractedFieldsJson = """{"Código de Controle": "8F2A.99B1.442E.11A0", "Validade": "2026-12-12", "Status": "Regular"}""",
                tags = "cnd, receita federal, certidao, regularidade",
                cloudSyncStatus = "SYNCED"
            )
        )
    }
}
