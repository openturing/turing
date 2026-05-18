// Custom Tool: compor_proposta_in_company
//
// Compõe uma PROPOSTA DE TREINAMENTO IN-COMPANY a partir do briefing
// capturado pela Camila (account exec) no chat-flow B2B. Diferente do
// `search_ee_programs` (busca semântica de programas abertos), este tool é
// um GERADOR DETERMINÍSTICO: dado o número de pessoas, tema e prazo,
// retorna sempre uma proposta precificada com módulos + cronograma. Sem
// LLM no caminho crítico de preços — comprador NÃO pode receber preços
// diferentes a cada turno.
//
// Diferenciais (vs uma resposta livre do LLM):
//   1. **Pricing tier determinístico** — 4 faixas (small/medium/large/
//      enterprise) com volume discount fixo (20%, 35%, 48%). Comprador
//      pode auditar; consultor sabe o que negociou.
//   2. **Módulos curados por tema** — 5 temas (finanças, liderança, saúde,
//      tecnologia, outros) com 3 módulos cada, ementa expandida. Sem
//      hallucination de programa que não existe.
//   3. **Cronograma adaptativo por prazo** — urgente (≤30d) puxa
//      paralelismo + fast-track flag; semestre vira programa estruturado
//      com pré-trabalho.
//   4. **Casos similares via turingSearch** — se houver template
//      `cases-in-company` indexado, lista 2-3 empresas reais (fallback
//      genérico quando não há).
//   5. **Estrutura React-ready** — slot `proposta_in_company` é JSON que
//      o portal Education renderiza como CompanyProposalCard.
//
// Bindings:
//   args         — vazio; tudo vem dos slots da conversa.
//   slots        — leitura dos B2B slots (razao_social, num_pessoas, tema, …).
//   turingSearch — opcional, busca de casos similares (degrada se indisponível).
//
// Cadastrar em admin Turing > Custom Tools. returnType=markdown.
// Parameters: nenhum.

import groovy.json.JsonOutput

// ─── Leitura dos slots de briefing ────────────────────────────────────
def razaoSocial    = (slots.get("razao_social") ?: "Sua empresa")?.toString()?.trim()
def numPessoasRaw  = (slots.get("num_pessoas") ?: "0")?.toString()?.trim()
def tema           = (slots.get("tema") ?: "")?.toString()?.trim()
def setor          = (slots.get("setor_empresa") ?: "")?.toString()?.trim()
def prazo          = (slots.get("prazo") ?: "trimestre")?.toString()?.trim().toLowerCase()
def formato        = (slots.get("formato") ?: "Híbrido")?.toString()?.trim()
def decisionRole   = (slots.get("decision_role") ?: "")?.toString()?.trim()

// Extrai inteiro tolerantemente de "35", "35 pessoas", "~35", "30-40", etc.
// Usa o PRIMEIRO inteiro encontrado; quando o input é faixa "30-40" preferimos
// o limite inferior (a proposta inicial não infla números).
def parseInt = { raw ->
    if (!raw) return 0
    def m = (raw =~ /\d+/)
    return m ? Integer.parseInt(m[0]) : 0
}
def numPessoas = parseInt(numPessoasRaw)
if (numPessoas <= 0) numPessoas = 15  // default seguro pra não travar a proposta

// ─── Tier de pricing por volume ───────────────────────────────────────
// 4 tiers com volume discount escalonado. Os valores são parametrizáveis no
// admin (em produção viraria config); pinned como constantes aqui pra
// o demo ser determinístico e auditável pelo comprador.
def tierConfig = numPessoas <= 14   ? [tier: "small",      tierLabel: "Workshop turma aberta",   basePrice: 2500, discount: 0,    cargaHoras: 12, formatoSugerido: "Online ao vivo ou Híbrido"] :
                 numPessoas <= 49   ? [tier: "medium",     tierLabel: "Programa customizado",    basePrice: 2000, discount: 20,   cargaHoras: 28, formatoSugerido: "Híbrido"] :
                 numPessoas <= 99   ? [tier: "large",      tierLabel: "In-company dedicado",     basePrice: 1625, discount: 35,   cargaHoras: 36, formatoSugerido: "Híbrido (2 cohorts)"] :
                                      [tier: "enterprise", tierLabel: "Programa estruturado",    basePrice: 1300, discount: 48,   cargaHoras: 40, formatoSugerido: "Híbrido (múltiplas turmas + kickoff exec)"]

def precoTotal = numPessoas * tierConfig.basePrice
def formatPrice = { v ->
    def brLocale = new java.util.Locale("pt", "BR")
    return "R\$ " + String.format(brLocale, "%,.2f", v as BigDecimal)
}

// ─── Normalização de tema → chave de módulo ───────────────────────────
def normalizeTheme = { raw ->
    if (!raw) return "outros"
    // Strip diacritics BEFORE matching so the unaccented prefixes in the
    // regex (financ, gestao, saud, …) catch their accented Portuguese
    // counterparts ("Finanças" → "financas" → matches "financ";
    // "Gestão" → "gestao" → matches; "Saúde" → "saude" → matches).
    // Without this, the regex silently misses the most common chat
    // inputs that come WITH accents.
    def lower = java.text.Normalizer
            .normalize(raw.toLowerCase(), java.text.Normalizer.Form.NFD)
            .replaceAll(/\p{M}/, "")
    if (lower =~ /(financ|invest|valuat|mna|m&a|risk|risco|cfo|controlador)/) return "financas"
    if (lower =~ /(lider|gestao|people|times|equipe|cultura|coach)/)           return "lideranca"
    if (lower =~ /(saud|hospital|clinic|farmac|esg saud)/)                     return "saude"
    // Short tokens (\bia\b, \bai\b, \bml\b, \bdata\b) get word boundaries
    // to avoid false positives — without `\b`, "ia" matches inside
    // "auditoria"/"compliance"/"regulatorio" and wrongly classifies them
    // as tecnologia. The longer alternatives (tech, tecnologi, dados,
    // machine, digital, cloud) are specific enough to stand alone.
    if (lower =~ /(tech|tecnologi|\bia\b|dados|\bdata\b|\bml\b|machine|\bai\b|digital|cloud)/) return "tecnologia"
    return "outros"
}
def temaKey = normalizeTheme(tema)

// ─── Catálogo determinístico de módulos por tema ─────────────────────
def CATALOG = [
    financas: [
        [nome: "Fundamentos de Finanças Corporativas", carga: "16h",
         ementa: ["DCF e Múltiplos", "Estrutura de capital", "Análise de viabilidade"]],
        [nome: "Decisão de Investimento Avançada", carga: "12h",
         ementa: ["Real Options", "M&A simulado", "Risk-adjusted returns"]],
        [nome: "Liderança Financeira & Board", carga: "8h",
         ementa: ["Narrativa pro comitê", "Compliance & ESG", "Métricas executivas"]]
    ],
    lideranca: [
        [nome: "Liderança de Alta Performance", carga: "16h",
         ementa: ["Times de alta performance", "Feedback e 1:1s", "Gestão de mudança"]],
        [nome: "Estratégia e Comunicação Executiva", carga: "12h",
         ementa: ["Storytelling exec", "Negociação", "Decisões em ambiguidade"]],
        [nome: "Cultura, Diversidade e Engajamento", carga: "8h",
         ementa: ["Cultura mensurável", "Pipeline de talentos", "ESG-S"]]
    ],
    saude: [
        [nome: "Gestão Hospitalar e Operação Clínica", carga: "16h",
         ementa: ["Indicadores de qualidade", "Sinistralidade", "Compliance ANS/ANVISA"]],
        [nome: "Inovação em Saúde", carga: "12h",
         ementa: ["Healthtechs e IA clínica", "Telemedicina", "Modelos de cuidado"]],
        [nome: "Saúde Suplementar e Regulação", carga: "8h",
         ementa: ["Operadoras vs prestadores", "Reajustes", "Regras da ANS"]]
    ],
    tecnologia: [
        [nome: "IA Generativa para Negócios", carga: "16h",
         ementa: ["LLMs e RAG", "Casos de uso por setor", "Ética e LGPD"]],
        [nome: "Workshop Hands-on (dados da empresa)", carga: "12h",
         ementa: ["Mini-projetos reais", "Code review", "MLOps básico"]],
        [nome: "Roadmap de Adoção de IA", carga: "4h",
         ementa: ["Sessão executiva", "Plano 12 meses", "KPIs e ROI"]]
    ],
    outros: [
        [nome: "Trilha customizada — Bloco 1", carga: "16h",
         ementa: ["Conteúdo desenhado com base no briefing", "Definição de objetivos", "Diagnóstico"]],
        [nome: "Trilha customizada — Bloco 2", carga: "12h",
         ementa: ["Aprofundamento prático", "Casos da empresa", "Workshops por área"]],
        [nome: "Sessão de fechamento executivo", carga: "4h",
         ementa: ["Apresentação dos projetos", "Próximos passos", "Avaliação de impacto"]]
    ]
]
def modulos = CATALOG.getOrDefault(temaKey, CATALOG.outros)

// Pequeno ajuste por tier: enterprise ganha um 4º bloco de project work;
// small remove o módulo 3 (workshop curto não comporta sessão executiva).
if (tierConfig.tier == "enterprise") {
    modulos = modulos + [[
        nome: "Projeto Aplicado com mentoria",
        carga: "8h",
        ementa: ["Projeto da empresa supervisionado", "2 checkpoints com mentor", "Apresentação final"]
    ]]
} else if (tierConfig.tier == "small") {
    modulos = modulos.take(2)
}
def cargaTotal = modulos.collect { (it.carga ?: "0h").replace("h", "") as int }.sum()

// ─── Cronograma adaptativo por prazo ─────────────────────────────────
// Cada `prazo` produz um array de marcos (marco + quando) que o React UI
// renderiza como timeline. urgente → paralelismo; flex → 2 cenários.
def cronograma
def prazoLabel
def fastTrackFlag = false
switch (prazo) {
    case "urgente":
    case "30":
    case "30 dias":
    case "próximos 30 dias":
        fastTrackFlag = true
        prazoLabel = "≤ 30 dias (Fast-track)"
        cronograma = [
            [marco: "Briefing executivo + assinatura", quando: "Dias 1-3"],
            [marco: "Kickoff + Módulo 1 (paralelo)",   quando: "Dias 5-12"],
            [marco: "Módulos 2-3 em paralelo",         quando: "Dias 13-25"],
            [marco: "Fechamento + handoff",            quando: "Dias 26-30"]
        ]
        break
    case "trimestre":
    case "90":
    case "próximo trimestre":
        prazoLabel = "≤ 3 meses"
        cronograma = [
            [marco: "Briefing + alinhamento",   quando: "Semana 1"],
            [marco: "Kickoff e Módulo 1",       quando: "Semanas 2-4"],
            [marco: "Módulos 2-3",              quando: "Semanas 5-10"],
            [marco: "Closing executivo",        quando: "Semanas 11-12"]
        ]
        break
    case "semestre":
    case "6 meses":
    case "6 meses+":
    case "180":
        prazoLabel = "≤ 6 meses"
        cronograma = [
            [marco: "Pré-trabalho (diagnóstico)",     quando: "Mês 1"],
            [marco: "Kickoff + Módulos 1-2",          quando: "Meses 2-3"],
            [marco: "Módulos 3-4 + projeto aplicado", quando: "Meses 4-5"],
            [marco: "Apresentação executiva",         quando: "Mês 6"]
        ]
        break
    default:
        prazoLabel = "Flexível"
        cronograma = [
            [marco: "Cenário Rápido (3 meses)",       quando: "Conclusão Q+1"],
            [marco: "Cenário Estruturado (6 meses)",  quando: "Conclusão Q+2"]
        ]
}

// ─── Casos similares via turingSearch (best-effort) ──────────────────
// Procura no índice por template `cases-in-company`. Quando não há índice
// (deployment novo), fallback estático genérico. Não bloqueia a proposta —
// só enriquece.
def casosSimilares = []
try {
    def hits = turingSearch.ann(
        site: "education-dev-test-author",
        query: "in-company ${tema} ${setor}",
        locale: "pt",
        topK: 3,
        templateName: "cases-in-company"
    )
    if (hits) {
        casosSimilares = hits.collect { h ->
            [empresa: (h.title ?: h.empresa ?: "—")?.toString(),
             pessoas: (h.numPessoas ?: h.tamanho ?: "—")?.toString(),
             tema:    (h.tema ?: h.area ?: "—")?.toString()]
        }
    }
} catch (Exception e) {
    // turingSearch indisponível ou template ausente — segue sem casos.
}
if (!casosSimilares) {
    // Fallback determinístico por setor — placeholders auditáveis até
    // o índice `cases-in-company` ser populado.
    def CASES_FALLBACK = [
        financas:   [[empresa: "Banco do estudo de caso ABC",  pessoas: "60+", tema: "Valuation"],
                     [empresa: "Asset Management referência",   pessoas: "25",  tema: "Risk"],
                     [empresa: "Fintech mid-cap referência",    pessoas: "40",  tema: "M&A"]],
        lideranca:  [[empresa: "Varejo nacional referência",    pessoas: "120", tema: "Liderança"],
                     [empresa: "Indústria multinacional",       pessoas: "80",  tema: "Cultura"],
                     [empresa: "Tech scale-up referência",      pessoas: "50",  tema: "Gestão"]],
        saude:      [[empresa: "Operadora de saúde referência", pessoas: "200", tema: "Gestão hospitalar"],
                     [empresa: "Rede de clínicas referência",   pessoas: "60",  tema: "Operação clínica"]],
        tecnologia: [[empresa: "Banco digital referência",      pessoas: "150", tema: "IA"],
                     [empresa: "Varejo digital referência",     pessoas: "90",  tema: "Dados"],
                     [empresa: "Indústria 4.0 referência",      pessoas: "60",  tema: "Transformação digital"]],
        outros:     [[empresa: "Multinacional industrial",      pessoas: "100", tema: "Liderança + estratégia"],
                     [empresa: "Scale-up brasileira",           pessoas: "45",  tema: "Customizado"]]
    ]
    casosSimilares = CASES_FALLBACK.getOrDefault(temaKey, CASES_FALLBACK.outros)
}

// ─── Share URL B2B — deeplink pra outro stakeholder ──────────────────
// Mesma ideia do Programa-Match: outro stakeholder (CEO, RH, comprador)
// abre o link e vê a proposta já contextualizada. Params curtos: empresa,
// tema, pessoas — não vaza pricing detalhado.
def shareUrlB2b = "https://ee.education.example.com/in-company?empresa=" +
        URLEncoder.encode(razaoSocial, "UTF-8") +
        "&tema=" + URLEncoder.encode(tema, "UTF-8") +
        "&pessoas=" + numPessoas

// ─── Proposta estruturada (React-ready) ──────────────────────────────
def proposta = [
    empresa            : razaoSocial,
    setorEmpresa       : setor ?: "",
    numPessoas         : numPessoas,
    tier               : tierConfig.tier,
    tierLabel          : tierConfig.tierLabel,
    fastTrack          : fastTrackFlag,
    tema               : tema,
    temaKey            : temaKey,
    formato            : formato,
    formatoSugerido    : tierConfig.formatoSugerido,
    decisionRole       : decisionRole,
    cargaTotal         : "${cargaTotal}h".toString(),
    modulos            : modulos,
    precoPorPessoa     : tierConfig.basePrice,
    precoPorPessoaLabel: formatPrice(tierConfig.basePrice),
    precoTotal         : precoTotal,
    precoTotalLabel    : formatPrice(precoTotal),
    descontoPercentual : tierConfig.discount,
    descontoLabel      : tierConfig.discount > 0 ?
                           "${tierConfig.discount}% (volume tier ${tierConfig.tier})".toString() :
                           "Sem desconto de volume — turma aberta",
    prazo              : prazo,
    prazoLabel         : prazoLabel,
    cronograma         : cronograma,
    casosSimilares     : casosSimilares,
    proximoPasso       : fastTrackFlag ?
                           "Account exec entra em contato em até 4 horas úteis (fast-track)" :
                           "Account exec entra em contato em 1 dia útil para ajustar a proposta"
]

slots.set("proposta_in_company", JsonOutput.toJson(proposta))
slots.set("tier", tierConfig.tier)
slots.set("tier_label", tierConfig.tierLabel)
slots.set("tema_normalized", temaKey)
slots.set("prazo_label", prazoLabel)
slots.set("share_url_b2b", shareUrlB2b)
slots.set("cta_visible", "true")  // libera CompanyProposalCard no portal

// ─── Markdown enxuto pra Camila falar (cards visuais carregam o peso) ─
def sb = new StringBuilder()
sb << "**Proposta gerada para ${razaoSocial} — ${tierConfig.tierLabel}**\n\n"
sb << "Veja o resumo ao lado nos cards. Highlights:\n\n"
sb << "- **${numPessoas} pessoas** · **${cargaTotal}h** de carga · formato **${tierConfig.formatoSugerido}**\n"
sb << "- **${proposta.precoTotalLabel}** total (${proposta.precoPorPessoaLabel}/pessoa)"
if (tierConfig.discount > 0) sb << " · desconto de **${tierConfig.discount}%** por volume\n"
else sb << "\n"
sb << "- Prazo **${prazoLabel}**"
if (fastTrackFlag) sb << " ⚡ *fast-track*"
sb << "\n\n"
sb << "Composto por **${modulos.size()} módulos**: ${modulos.collect { it.nome }.join(' · ')}.\n\n"
sb << "**Como prefere prosseguir?**"
return sb.toString()
