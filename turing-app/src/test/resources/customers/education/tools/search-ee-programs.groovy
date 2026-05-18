// Custom Tool: search_ee_programs
//
// Args available as variables (e.g. `query`) and as the `args` map.
// Return any value — it will be coerced to string for the LLM.
//
// O resultado visual final é: a Marina fala uma frase curta confirmando
// + os 3 cards aparecem dinamicamente em "Programas recomendados pra você"
// no portal (FlowOutputs/ProgramCompareCards via useTuringSlots).
//
// Bindings disponíveis:
//   args         (Map)    — argumentos do LLM. Uso: args.query
//   http         (helper) — TurCustomToolHttpHelper (postJson/getJson)
//   turingSearch (helper) — TurCustomToolSearchHelper (ann/sn)
//   slots        (helper) — TurCustomToolSlotHelper conv-scoped (set/get/all)
//
// Cadastrar no admin Turing > Custom Tools > Novo. returnType=markdown.

import groovy.json.JsonOutput

def q = args.query?.toString()?.trim()
if (!q) {
    return "_(Sem query. Peça uma área de interesse antes de chamar esta ferramenta.)_"
}

// turingSearch.ann faz vector similarity search no índice do site
// Executive Education e JÁ DEDUPLICA por documento — `topK: 3` significa
// "3 cursos distintos" (não 3 chunks). O helper over-fetcha chunks
// internamente e colapsa por source_id pra entregar documentos únicos.
def hits = turingSearch.ann(
    site: "education-dev-test-author",
    query: q,
    locale: "pt",
    topK: 3,
    templateName: "educacao-executiva"
)

if (!hits) {
    return "_Nenhum programa encontrado para `${q}`._ Sugira ao visitante conversar com um consultor para mapear alternativas."
}

// Formata "8500.0" -> "R$ 8.500,00" usando locale pt-BR. Aceita string
// ou número; em caso de erro de parse devolve null (caller cai em "Sob consulta").
def formatPriceBR = { raw ->
    if (raw == null) return null
    try {
        def num = new BigDecimal(raw.toString().trim())
        def brLocale = new java.util.Locale("pt", "BR")
        return "R\$ " + String.format(brLocale, "%,.2f", num)
    } catch (NumberFormatException e) {
        return null
    }
}

// Extrai uma justificativa curta para o card a partir do que o indexador
// expôs: prefere campos descritivos (`richText`, `description`, `summary`),
// senão pega o primeiro paragrafo legível do chunk content (que vem com
// metadados YAML-ish no topo — pulamos eles). Fallback final: texto genérico.
def extractWhy = { hit ->
    def candidates = [hit.richText, hit.description, hit.summary]
    for (c in candidates) {
        def s = c?.toString()?.trim()
        if (s) {
            return s.length() > 160 ? s.substring(0, 157) + "..." : s
        }
    }
    def content = hit.content?.toString()
    if (content) {
        // Os chunks começam com "# Título\nkey: value\nkey: value\n---\n<corpo>".
        // O corpo (depois de "---") é o texto descritivo. Quando não tem "---",
        // pula linhas key:value e fica com a primeira linha "normal".
        def body = content.contains("\n---\n")
                ? content.split("\n---\n", 2)[1]
                : content.readLines().findAll {
                    !it.matches(/^\s*[a-zA-Z_][a-zA-Z0-9_-]*:\s.*/) && !it.startsWith("#")
                }.join(" ")
        def clean = body?.replaceAll("\\s+", " ")?.trim()
        if (clean) {
            return clean.length() > 160 ? clean.substring(0, 157) + "..." : clean
        }
    }
    return "Programa do catálogo Executive Education selecionado pela busca semântica com base no seu objetivo."
}

// Mapeia os hits únicos para a estrutura que o portal React espera no
// slot programas_match (interface ProgramMatch em FlowOutputs.tsx).
// Campos: name, duration, format, priceFrom, tag, why, url.
def programs = hits.collect { hit ->
    def title  = hit.title ?: "(programa sem título)"
    def dates  = hit.dateOfClassesText?.toString()
    def url    = hit.url?.toString()
    def aberto = hit.inscricaoAberta?.toString()?.toLowerCase() == "true"
    def precoBR = formatPriceBR(hit.preco)
    def formato = hit["formato-de-aula"]?.toString()?.trim()
    [
        name: title,
        duration: dates ?: "Consulte o consultor",
        format: formato ?: "Educação Executiva",
        priceFrom: precoBR ?: "Sob consulta",
        tag: aberto ? "Inscrições abertas" : "Próxima turma",
        why: extractWhy(hit),
        url: url
    ]
}

// Escreve no slot que o ProgramCompareCards lê via useTuringSlots.
// A próxima janela de polling (1.5s no FlowOutputs) faz os cards
// aparecerem automaticamente no portal.
slots.set("programas_match", JsonOutput.toJson(programs))

// Garante que o CTA "Agendar com consultor" apareça abaixo dos cards.
if (!slots.get("cta_visible")) {
    slots.set("cta_visible", "true")
}

// Markdown enxuto pro LLM repetir VERBATIM. Cards fazem o visual pesado;
// a Marina só precisa confirmar curto. priceFrom já vem formatado (R$ X.XXX,XX).
def n = programs.size()
def sb = new StringBuilder()
sb << "**Selecionei ${n} programa${n == 1 ? '' : 's'} de Executive Education pra você** (veja os cards ao lado):\n\n"
programs.eachWithIndex { p, i ->
    sb << "${i + 1}. **${p.name}** — ${p.priceFrom}"
    if (p.duration && p.duration != "Consulte o consultor") {
        sb << " — próximas datas: ${p.duration}"
    }
    sb << "\n"
}
return sb.toString()
