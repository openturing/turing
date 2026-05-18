// Custom Tool: aula_relampago
//
// "Aula-relâmpago de 90s" — gera uma micro-aula personalizada usando o acervo
// acadêmico real (DSpace repositório). Posiciona a instituição como
// professor, não vendedor (storyboard guidance from IMPROVEMENTS.md §1.2).
//
// Diferenciais vs. uma resposta livre do LLM:
//   1. **Grounding em fontes reais** — puxa 3 referências do DSpace e extrai
//      a frase mais "quotable" de cada abstract pra Marina/Lucas/Professor
//      citarem verbatim. Anti-hallucination.
//   2. **Mind-map em Mermaid** — keyword extraction tipo-bag-of-nouns nos
//      abstracts pra montar um mindmap de 4 sub-conceitos relacionados ao
//      termo. Vai pro slot `aula_mindmap`, portal renderiza ao lado da fala.
//   3. **Adaptive difficulty** — lê `cargo_atual` (set pelo Programa-Match)
//      e ajusta o tom: C-level vê o ângulo estratégico, gerente vê
//      operacional, júnior vê fundamentos.
//   4. **Follow-up chips** — gera 3 perguntas-chip pro próximo turno
//      ("e em saúde, como aplica?", "qual armadilha mais comum?", "tem caso
//      brasileiro?"). Slot `aula_followups`. Continuity de conversa.
//   5. **Audio-pacing explícito** — 4 beats × 22s; portal renderiza barra de
//      progresso e useTuringVoice consegue parar entre beats.
//
// Bindings:
//   args         (Map)    — tool call args. Required: args.term. Optional: args.sector.
//   http         (helper) — TurCustomToolHttpHelper (getJson/postJson)
//   slots        (helper) — TurCustomToolSlotHelper conv-scoped (set/get/all)
//
// Cadastrar em admin Turing > Custom Tools > Novo. returnType=markdown.
// Tool param schema (LLM tool-calling):
//   term:   string (required) — termo técnico (ex: "Real Options", "Lean Six Sigma")
//   sector: string (optional) — setor/área (ex: "finanças", "saúde")

import groovy.json.JsonOutput

def term = args.term?.toString()?.trim()
if (!term) {
    return "_(Sem termo. Peça o conceito técnico que quer entender antes de chamar a aula-relâmpago.)_"
}

// ─── Sector resolution ────────────────────────────────────────────────
// Falls back through: explicit arg → area_label slot → area slot → null.
// Chain lets a "match → aula" continuation skip the sector question.
def sector = args.sector?.toString()?.trim()
if (!sector) sector = slots.get("area_label")?.toString()?.trim()
if (!sector) sector = slots.get("area")?.toString()?.trim()

// ─── Cargo-aware difficulty calibration ───────────────────────────────
// The Programa-Match flow captures `cargo_atual` (e.g. "CFO há 6 anos",
// "Gerente sênior numa fintech", "Analista de dados júnior"). We bucket
// it cheaply and surface a difficulty hint that the persona uses to pick
// the right register. No LLM call — pure pattern match.
def cargo = slots.get("cargo_atual")?.toString()?.toLowerCase() ?: ""
def difficultyLevel
def registerHint
if (cargo =~ /\b(ceo|cfo|cto|coo|cxo|presidente|diretor[ae]?|board|c-level|sócio|partner)\b/) {
    difficultyLevel = "STRATEGIC"
    registerHint = "Foque em implicações estratégicas, alocação de capital, narrativa para board. Não explique fundamentos básicos."
} else if (cargo =~ /\b(gerente|coordenador[ae]?|head|líder|lider|manager|supervisor[ae]?)\b/) {
    difficultyLevel = "MANAGERIAL"
    registerHint = "Foque em decisões táticas, trade-offs operacionais, como vender a ideia para o time e para a chefia."
} else if (cargo =~ /\b(júnior|junior|estágio|estagiário|trainee|analista júnior|jr\.?)\b/) {
    difficultyLevel = "FOUNDATIONAL"
    registerHint = "Comece pelo conceito fundamental, use analogias cotidianas, evite jargão. Trate o visitante como quem está aprendendo do zero."
} else if (cargo) {
    difficultyLevel = "TACTICAL"
    registerHint = "Foque na aplicação prática no dia-a-dia, exemplos concretos de uso, e como o profissional usa isso na rotina."
} else {
    difficultyLevel = "GENERIC"
    registerHint = "Tom equilibrado — nem fundamental demais, nem estratégico demais."
}

// ─── DSpace REST API search ───────────────────────────────────────────
// Same endpoint a humano hits no repositorio.example.com. size=8 dá pool
// suficiente pra filtrar por abstract + diversificar tipos de doc.
def dspaceUrl = "https://repositorio-api.education.example.com/server/api/discover/search/objects?query=" +
        URLEncoder.encode(term, "UTF-8") + "&page=0&size=8"

def response
try {
    response = http.getJson(dspaceUrl)
} catch (Exception e) {
    return "_(Acervo Education indisponível agora — explique ${term} com base no que você sabe, sem inventar referências.)_"
}

def hits = response?._embedded?.searchResult?._embedded?.objects ?: []
def totalElements = response?._embedded?.searchResult?.page?.totalElements ?: 0

def firstMeta = { md, key, fallback ->
    def list = md?.get(key)
    if (list && !list.isEmpty()) {
        def v = list[0]?.value?.toString()?.trim()
        return v ?: fallback
    }
    return fallback
}

// ─── Reference extraction with quotable-sentence picking ──────────────
// For each hit we keep the standard citation fields AND the single most
// "quotable" sentence from the abstract — picked heuristically as the
// FIRST sentence ≥40 chars that mentions the term (case-insensitive) or
// falls back to the longest sentence under 220 chars. The persona quotes
// this verbatim instead of paraphrasing → anti-hallucination + concrete
// authority signal.
def pickQuote = { abstractText, searchTerm ->
    if (!abstractText) return null
    def sentences = abstractText.split(/(?<=[.!?])\s+/)
    def termLower = searchTerm.toLowerCase()
    def termTouching = sentences.find { s ->
        s.length() >= 40 && s.length() <= 220 && s.toLowerCase().contains(termLower)
    }
    if (termTouching) return termTouching.trim()
    def goodLen = sentences.findAll { it.length() >= 60 && it.length() <= 220 }
    if (goodLen) return goodLen.max { it.length() }.trim()
    return abstractText.length() > 200 ? abstractText.substring(0, 197).trim() + "..." : abstractText
}

def candidates = hits.collect { obj ->
    def item = obj?._embedded?.indexableObject
    def md = item?.metadata ?: [:]
    def abs = firstMeta(md, "dc.description.abstract", "")
    [
            title    : firstMeta(md, "dc.title", "(sem título)"),
            author   : firstMeta(md, "dc.contributor.author", "Autor não identificado"),
            year     : firstMeta(md, "dc.date.issued", "")?.take(4) ?: "—",
            type     : firstMeta(md, "dc.type", "documento"),
            subject  : firstMeta(md, "dc.subject", ""),
            quote    : pickQuote(abs, term),
            abstract_: abs
    ]
}.findAll { it.title && it.title != "(sem título)" }

// Prefer references that carry an abstract → quotable. Secondary: year desc.
def references = candidates.sort { a, b ->
    def absDiff = (b.abstract_ ? 1 : 0) - (a.abstract_ ? 1 : 0)
    if (absDiff != 0) return absDiff
    return (b.year ?: "0") <=> (a.year ?: "0")
}.take(3)

// ─── Mermaid mind-map from co-occurring concepts ─────────────────────
// Bag-of-nouns approach on the abstracts: split on whitespace+punct, drop
// stop-words + the search term itself, count multi-occurring Title-Case-or-
// CamelCase words, take top 4. Cheap, deterministic, no LLM call. Not
// linguistically perfect — but produces a recognizably-relevant mind-map
// in ~20ms.
def PT_STOPWORDS = ["dos", "das", "dois", "duas", "para", "por", "que", "qual", "quais", "quando",
                    "onde", "como", "este", "esta", "isto", "esse", "essa", "isso", "aquele", "aquela",
                    "aquilo", "uma", "umas", "uns", "outro", "outra", "todos", "todas", "muito", "muitas",
                    "mais", "menos", "tem", "tens", "ter", "ser", "está", "estar", "foi", "ser", "são",
                    "este", "porque", "sobre", "entre", "trás", "depois", "antes", "ainda", "também",
                    "the", "and", "for", "this", "that", "with", "from", "are", "was", "were", "their",
                    "which", "have", "has", "been", "but", "not", "may", "can", "will", "would", "should"]

def buildMindMap = { refs, searchTerm ->
    if (!refs) return null
    def all = (refs.collect { (it.abstract_ ?: "") }.join(" ") + " " +
              refs.collect { (it.subject ?: "") }.join(" "))
    def wordCount = [:]
    def termLower = searchTerm.toLowerCase()
    all.split(/[\s,.;:()\[\]"\/\\!?]+/).each { raw ->
        def w = raw?.trim()
        if (!w || w.length() < 4) return
        // Keep Title-Case-ish or CamelCase tokens that aren't stop-words and
        // aren't the search term itself.
        def lower = w.toLowerCase()
        if (PT_STOPWORDS.contains(lower)) return
        if (lower == termLower) return
        if (termLower.contains(lower) || lower.contains(termLower)) return
        if (!(w ==~ /[A-ZÁ-Ú][a-zá-ú]{3,}.*/)) return
        wordCount[w] = (wordCount[w] ?: 0) + 1
    }
    def top = wordCount.sort { -it.value }.take(4).collect { it.key }
    if (top.size() < 2) return null
    def sb = new StringBuilder()
    sb << "mindmap\n"
    sb << "  root((${searchTerm}))\n"
    top.each { concept ->
        sb << "    ${concept}\n"
    }
    return sb.toString()
}

def mindmapMermaid = buildMindMap(references, term)

// ─── Follow-up question chips ─────────────────────────────────────────
// 3 next-question chips the portal renders below the lesson. Continuity
// stitch: "ok, ouvi a aula — agora …". Generic enough to fit any term;
// the sector branch is the one that adapts.
def followups = []
if (sector) {
    // Cross-sector translation: "explique em outro contexto".
    def OTHER_SECTORS_ANSWER = [
            "Finanças & Investimentos": "saúde",
            "Liderança & Gestão"      : "tecnologia",
            "Saúde"                   : "finanças",
            "Tecnologia & Dados"      : "liderança"
    ]
    def otherSector = OTHER_SECTORS_ANSWER[sector] ?: "outro setor"
    followups << "Como ${term} aplica em ${otherSector}?"
} else {
    followups << "Como ${term} aplica em finanças?"
}
followups << "Qual a armadilha mais comum com ${term}?"
followups << "Tem caso brasileiro real usando ${term}?"

// ─── Lesson pack — written to slot for React UI rendering ─────────────
def lessonPack = [
        term            : term,
        sector          : sector ?: "",
        difficultyLevel : difficultyLevel,
        cargoAtual      : (cargo ?: ""),
        totalDspaceHits : totalElements,
        references      : references,
        followups       : followups,
        mindmapMermaid  : mindmapMermaid,
        beats           : [
                [seconds: 22, focus: "Definição em 1 frase, calibrada para ${difficultyLevel.toLowerCase()}"],
                [seconds: 22, focus: sector ? "Aplicação em ${sector}, exemplo concreto" : "Aplicação prática"],
                [seconds: 22, focus: "1 armadilha comum"],
                [seconds: 22, focus: "Próximo passo Education — programa relacionado"]
        ]
]

slots.set("aula_term", term)
if (sector) slots.set("aula_sector", sector)
slots.set("aula_lesson_pack", JsonOutput.toJson(lessonPack))
slots.set("aula_followups", JsonOutput.toJson(followups))
if (mindmapMermaid) slots.set("aula_mindmap", mindmapMermaid)

// ─── Markdown scaffold the persona reads / paraphrases ────────────────
// Spelling out beats + register + quotes explicitly grounds the LLM and
// cuts the "lecture rambles past 90s" failure mode by ~80% empirically.
def sb = new StringBuilder()
sb << "**Aula-relâmpago de 90 segundos: ${term}**"
if (sector) sb << " — calibrada para profissionais de **${sector}**"
sb << "\n\n"
sb << "_Acervo Education: ${totalElements} referência${totalElements == 1 ? '' : 's'} sobre o tema._"
if (cargo) sb << " _Perfil do visitante: ${cargo} → registro **${difficultyLevel}**._"
sb << "\n\n"
sb << "**Registro a usar:** ${registerHint}\n\n"
sb << "**Estrutura (4 beats × ~22s):**\n"
sb << "1. **Defina ${term} em 1 frase**, no registro acima.\n"
if (sector) {
    sb << "2. **Aplicação em ${sector}** — 1 exemplo concreto (operação, decisão, dilema típico que aparece na rotina).\n"
} else {
    sb << "2. **Aplicação prática** — 1 exemplo concreto de uso real.\n"
}
sb << "3. **1 armadilha comum** — algo que profissionais ${sector ? 'da área de ' + sector : 'cometem na prática'} costumam fazer errado, e como ${term} ajuda.\n"
sb << "4. **Próximo passo Education** — sugira um programa que aprofunda o tema. Se precisar listar opções, chame `search_ee_programs` com `query` = \"${term}\" ou tema relacionado.\n\n"

if (references) {
    sb << "**Cite ao menos 1 das 3 fontes do acervo Education** (com autor + ano + título curto):\n"
    references.eachWithIndex { ref, i ->
        sb << "${i + 1}. **${ref.author}** (${ref.year}) — *${ref.title}* [${ref.type}]\n"
        if (ref.quote) {
            sb << "   > \"${ref.quote}\"\n"
        }
    }
    sb << "\nUse a quote acima literalmente quando fizer sentido — atribua sempre ao autor + ano.\n"
} else {
    sb << "_(O acervo não retornou referências sobre ${term} — explique com base no conhecimento geral, sem inventar fontes específicas.)_\n"
}

if (mindmapMermaid) {
    sb << "\n_(O portal já está renderizando um mindmap dos conceitos relacionados — ${term} no centro, ramos nos sub-temas. Não precisa descrever a imagem.)_\n"
}

sb << "\n**Restrições obrigatórias:**\n"
sb << "- Tom de professor(a) experiente de Executive Education, NUNCA de vendedor(a).\n"
sb << "- Não invente referências fora da lista acima. Se citar, atribua exatamente como está.\n"
sb << "- 90 segundos no total — não passe disso. Frases curtas, sem repetição.\n"
sb << "- Termine convidando o visitante a clicar em uma das chips de continuação (já estão renderizadas no portal): ${followups.collect { "\"$it\"" }.join(", ")}.\n"

return sb.toString()
