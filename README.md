# DocMiner AI 📄🔍🤖

> **Inteligência Artificial Multimodal, OCR Avançado e Mineração de Dados de Documentos para Android.**

O **DocMiner AI** é um aplicativo Android nativo desenvolvido em **Kotlin** e **Jetpack Compose**, projetado para digitalizar, estruturar, analisar e cruzar dados de documentos fiscais, contratos, comprovantes e faturas de forma automatizada, utilizando a tecnologia **Google Gemini**.

---

## 🚀 Principais Funcionalidades

### 1. Digitalização Inteligente & OCR Multimodal
- **Captura com Câmera e Galeria**: Fotografe documentos físicos ou selecione imagens/PDFs salvos na memória do aparelho.
- **Entrada de Texto Direto**: Permite colar transcrições ou textos brutos para estruturação imediata.
- **Extração com Gemini 3.5 Flash**: Identificação automática de:
  - **Categoria**: Faturas, Contratos, Documentos Fiscais (NFS-e/DANFE), Identificação, Relatórios Técnicos e Correspondências.
  - **Metadados Chave**: Data de emissão, emissor/fornecedor, valor monetário (R$) e número do documento/CNPJ.
  - **Resumo Executivo**: Síntese dos principais pontos do documento.
  - **Tabela de Campos Dinâmicos**: Extração estruturada chave-valor (ex: código de barras, vencimento, itens, condições contratuais).

### 2. Gestão e Organização de Documentos
- **Filtros e Busca em Tempo Real**: Pesquisa por texto completo, fornecedor, categoria e ordenação por data ou valor.
- **Visualização Detalhada**: Modal com resumo executivo, dados brutos, status de sincronização e campos extraídos.
- **Exclusão e Edição**: Gerenciamento completo de documentos cadastrados.

### 3. Data Mining e Inteligência Analítica
- **Métricas e KPIs Financeiros**:
  - Total de documentos processados.
  - Soma total de valores transacionados e média por documento.
  - Distribuição percentual por categoria com barras gráficas.
- **Algoritmo de Mineração (Data Mining)**:
  - **Padrões de Gastos Recorrentes**: Detecção de despesas contínuas (energia, nuvem, telecom).
  - **Top Fornecedores / Credores**: Concentração de gastos por empresa.
  - **Obrigações e Riscos Contratuais**: Mapeamento de prazos de renovação, rescisões e carências.
  - **Detecção de Anomalias**: Identificação de variações bruscas de preço ou pagamentos fora do padrão.
  - **Oportunidades de Otimização**: Recomendações práticas de redução de custos e consolidação fiscal.
- **Gerador de Relatórios Executivos**:
  - Emissão com um toque de relatórios para **Auditoria Financeira**, **Gestão de Contratos** ou **Relatórios Personalizados via Prompt**.
  - Suporte a cópia de relatório para a área de transferência e compartilhamento com outros aplicativos.

### 4. Assistente Conversacional (Chat IA Documental)
- **Q&A com Base no seu Acervo**: Faça perguntas em linguagem natural e receba respostas fundamentadas exclusivamente nos documentos cadastrados.
- **Cruzamento de Informações**: Cálculos agregados, comparação de contratos, localização de comprovantes de pagamento e conferência de cláusulas.
- **Atalhos Rápidos**: Sugestões de perguntas pré-configuradas para agilizar a rotina.

### 5. Nuvem, Importação e Exportação em Lote
- **Armazenamento Offline-First**: Banco de dados local **Room (SQLite)** de alta performance que funciona sem conexão com a internet.
- **Sincronização em Nuvem**: Módulo para envio e backup com indicação visual de status por documento.
- **Massa de Dados de Demonstração (Brasil)**: Botão de teste para carregar instantaneamente 8 documentos brasileiros do mundo real (Fatura Enel, AWS Cloud, NFS-e, Comprovante PIX, Apólice de Seguro, Laudo Técnico, Contrato de Aluguel e CND Federal).
- **Importação e Exportação**:
  - **JSON**: Estrutura completa de metadados, campos e resumos.
  - **CSV / Planilhas**: Compatível diretamente com Microsoft Excel e Google Planilhas.

---

## 🛠️ Arquitetura e Tecnologias

O projeto segue as diretrizes modernas do **Android Modern Architecture (MVVM)** e **Clean Architecture**:

- **Linguagem**: [Kotlin 2.0+](https://kotlinlang.org/)
- **Interface Declarativa**: [Jetpack Compose](https://developer.android.com/jetpack/compose) com [Material Design 3](https://m3.material.io/)
- **Banco de Dados Local**: [AndroidX Room](https://developer.android.com/training/data-storage/room) com KSP (Kotlin Symbol Processing)
- **Motor de IA**: [Google Gemini API](https://ai.google.dev/) (Gemini 3.5 Flash) para visão computacional, OCR e inferência analítica
- **Concorrência & Reatividade**: Kotlin Coroutines & StateFlow / SharedFlow
- **Injeção de Dependências**: Constructor Injection orientado a ViewModel
- **Testes Automatizados**: [Robolectric](https://robolectric.org/) (JVM tests sem emulador) e [Roborazzi](https://github.com/takahirom/roborazzi) (testes visuais de screenshot)

---

## 📁 Estrutura de Pastas do Projeto

```
app/src/main/java/com/example/
├── MainActivity.kt                  # Ponto de entrada, Scaffold e Bottom Navigation
├── data/
│   ├── importer/
│   │   └── BulkDataManager.kt       # Importação/Exportação JSON/CSV e Corpus Brasileiro
│   ├── local/
│   │   ├── AppDatabase.kt           # Configuração do Room Database
│   │   └── DocumentDao.kt           # Operações de persistência e consultas SQL
│   ├── model/
│   │   └── DocumentEntity.kt        # Entidade de dados e modelos de categorização
│   ├── remote/
│   │   └── GeminiService.kt         # Integração direta com a API do Google Gemini
│   └── repository/
│       └── DocumentRepository.kt    # Repositório com orquestração local e remota
└── ui/
    ├── components/
    │   ├── BulkImportExportDialog.kt# Modal de carga e exportação de dados
    │   ├── DocumentCard.kt          # Card visual do documento com badges
    │   ├── DocumentDetailDialog.kt  # Modal com visão detalhada e ações
    │   └── DocumentScannerDialog.kt # Captura via câmera, galeria ou texto
    ├── screens/
    │   ├── AiChatScreen.kt          # Chat conversacional com o acervo
    │   ├── CloudAndDataScreen.kt    # Gestão de nuvem, backup e estatísticas
    │   ├── DataMiningScreen.kt      # Painel de mineração e relatórios
    │   └── DocumentListScreen.kt    # Lista pesquisável e filtrável de documentos
    ├── theme/
    │   ├── Color.kt                 # Paleta de cores moderna M3
    │   └── Theme.kt                 # Configuração de tema claro e escuro
    └── viewmodel/
        └── MainViewModel.kt         # Gerenciamento de estado de telas e chamadas assíncronas
```

---

## 📲 Como Instalar e Rodar no Celular

### Opção A: Executar via Streaming no Navegador (Web)
Abra o link compartilhado do ambiente de desenvolvimento diretamente no Chrome ou Safari do seu celular:
- Não requer download de APK.
- Suporta toque, uso de câmera e teclado virtual nativo.
- Adicione à tela inicial do celular pelo menu do navegador para usar em tela cheia.

### Opção B: Instalação Nativa via APK (Android)
1. No painel do Google AI Studio, selecione **"Download APK"** ou **"Export Project"**.
2. Transfira o arquivo `.apk` gerado (`app-debug.apk`) para o seu celular.
3. Abra o arquivo no gerenciador de arquivos do Android e confirme a instalação.

---

## 🧪 Testes Automatizados

O projeto conta com bateria de testes unitários e de integração com **Robolectric**:

```bash
# Executar testes unitários e de banco de dados Room no JVM:
gradle :app:testDebugUnitTest
```

Os testes cobrem:
- Validação de strings e recursos de internacionalização.
- Inserção e recuperação no banco de dados SQLite/Room in-memory.
- Serialização e deserialização do importador/exportador JSON e CSV.

---

## 📄 Licença

Este projeto é disponibilizado sob a licença **Apache 2.0**.
