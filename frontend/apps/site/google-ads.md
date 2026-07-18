# Google Ads — turing.viglet.org/aem-search

Copy e configuração de campanha para promover a página **AI Search for Adobe
Experience Manager** (`https://turing.viglet.org/aem-search/`).

Posicionamento da página: dar ao AEM respostas de IA **citadas e em streaming**,
mais busca **facetada + semântica**, e ir além do que um add-on AEM-native
consegue — busca fora do AEM, agents/tools/skills/MCP, qualquer LLM, qualquer
engine, self-hosted sob **Apache 2.0**.

- **Final URL:** `https://turing.viglet.org/aem-search/`
- **Display path sugerido:** `viglet.org/AEM-Search`
- **Business name:** `Viglet` (empresa; `Turing ES` é o produto)

---

## 1. Nome da empresa (Business name)

| Campo | Valor |
| --- | --- |
| **Business name** (anúncio) | Viglet |
| **Título / reforço de marca** | Viglet Turing ES |
| **Display path / URL de exibição** | viglet.org/AEM-Search |
| **Nome da conta** (interno) | Viglet |

- Business name = nome comercial da empresa, **não** o produto nem slogan.
- Precisa bater com o domínio (`viglet.org`) e passa por verificação do Google.
- Se a conta estiver numa razão social diferente, o nome **verificado** segue os
  documentos, mas o **Business name exibido** continua `Viglet`.

---

## 2. Títulos curtos (Headlines — máx. 30 caracteres)

| Headline | Chars |
| --- | --- |
| AI Search for AEM | 17 |
| Adobe Experience Manager AI | 27 |
| AEM Search, Reimagined | 22 |
| Cited AI Answers for AEM | 24 |
| AEM Search + Generative AI | 26 |
| Search Your AEM Content | 23 |
| AEM 6.5 & Cloud Service | 23 |
| Beyond AEM Search | 17 |
| Self-Hosted AEM AI Search | 25 |
| Open Source, Apache 2.0 | 23 |
| No Re-Platforming | 17 |
| Bring Your Own LLM | 18 |
| AEM AI Without the Ceiling | 26 |
| Try It on WKND Live | 19 |
| Viglet Turing ES | 16 |

## 3. Títulos longos (Long headlines — máx. 90 caracteres)

| Headline | Chars |
| --- | --- |
| AI search for Adobe Experience Manager — cited, streaming answers over your content | 82 |
| Give AEM generative AI search without re-platforming, on any LLM and any engine | 79 |
| AEM search reimagined: faceted + semantic search and grounded AI answers, self-hosted | 85 |
| Add AI search to AEM 6.5 and Cloud Service via the Dumont connector — Apache 2.0 | 79 |
| More than AEM AI search: agents, tools, MCP and cross-repository search you own | 79 |

## 4. Descrições (Descriptions — máx. 90 caracteres)

| Description | Chars |
| --- | --- |
| Cited, streaming AI answers plus faceted and semantic search over your AEM content. | 83 |
| Connect AEM 6.5 or Cloud Service with the Dumont connector. Self-hosted, Apache 2.0. | 84 |
| Sits alongside AEM — no re-platforming. Bring your own LLM and search engine. | 77 |
| Keep every page and query in your own infrastructure. Open source AI search for AEM. | 84 |
| Search beyond AEM: agents, tools, skills and MCP. Try it live on Adobe's WKND site. | 83 |

> Anúncios de pesquisa responsivos aceitam até **15 headlines** e **4 descrições**.
> Considere **fixar** "Viglet Turing ES" na posição 1 para reforço de marca.

---

## 5. Sitelink extensions

Link text ≤ 25 chars · duas linhas de descrição ≤ 35 chars cada. Mínimo 2,
ideal 4–6. **Todos os destinos abaixo são páginas/âncoras reais** — as âncoras
`#demo`, `#matrix`, `#how`, `#faq`, `#cta` foram adicionadas às seções de
`/aem-search` (com `scroll-mt` para o header não cobrir o título).

| Link text | Descrição 1 | Descrição 2 | Final URL |
| --- | --- | --- | --- |
| Try Live on WKND | Search & ask Adobe's WKND site | Cited answers, real AEM content | `turing.viglet.org/aem-search/#demo` |
| AEM Capability Matrix | vs an AEM-native AI add-on | See where the depth gap opens | `turing.viglet.org/aem-search/#matrix` |
| Data Residency & Security | Self-hosted, your infrastructure | Every page and query stays yours | `turing.viglet.org/security` |
| AEM Connector Docs | Index AEM 6.5 or Cloud Service | Powered by the Dumont connector | `docs.viglet.org/dumont/connectors/aem` |
| Compare Alternatives | vs Algolia, Elasticsearch & RAG | An honest, side-by-side look | `turing.viglet.org/compare` |
| Get Started | Self-host under Apache 2.0 | Connect your AEM tier fast | `docs.viglet.org/turing/` |

Sitelinks extras (páginas reais, para rotação/A-B):

| Link text | Destino |
| --- | --- |
| Faceted Search | `turing.viglet.org/features/search` |
| RAG Chat | `turing.viglet.org/features/ask` |
| How It Plugs In | `turing.viglet.org/aem-search/#how` |

> **Atenção:** `/compare` compara Turing vs **Algolia, Elasticsearch & DIY RAG**
> — *não* AEM-native search. A comparação contra o AEM-native AI add-on está na
> própria página `/aem-search` (âncora `#matrix`). Os textos acima já refletem
> isso.
>
> **URLs confirmadas no código** (`components/brand.tsx`):
> `LINKS.docs = https://docs.viglet.org/turing/` ·
> `LINKS.aemConnector = https://docs.viglet.org/dumont/connectors/aem`.

### Âncoras disponíveis em /aem-search (adicionadas nesta rodada)

| Âncora | Seção |
| --- | --- |
| `#demo` | Live WKND demo (playground) |
| `#how` | How it plugs into AEM (sits alongside, no re-platform) |
| `#matrix` | Capability matrix vs AEM-native AI add-on |
| `#depth` | Depth cards (onde o add-on chega ao teto) |
| `#faq` | AEM teams ask us (objections) |
| `#cta` | Add AI search to AEM — on your terms |

---

## 6. Callout extensions

Não clicáveis · máx. 25 chars cada · mostre 6–8, mínimo 2. Sem CTA (o Google
reprova callout que parece botão).

| Callout | Chars |
| --- | --- |
| Open Source, Apache 2.0 | 23 |
| Self-Hosted | 11 |
| Cited AI Answers | 16 |
| AEM 6.5 + Cloud Service | 23 |
| No Re-Platforming | 17 |
| Bring Your Own LLM | 18 |
| Any Search Engine | 17 |
| Faceted + Semantic Search | 25 |
| Data Stays With You | 19 |
| Dumont AEM Connector | 20 |
| Search Beyond AEM | 17 |
| Streaming Answers | 17 |

Melhores apostas (diferenciais que o AEM-native não cobre): **Open Source,
Apache 2.0** · **Self-Hosted** · **Bring Your Own LLM** · **Search Beyond AEM**.

---

## 7. Temas de pesquisa (Search themes — Performance Max)

Até 25 temas por grupo de recursos, cada um ≤ 10 palavras / ~80 chars. São
*sinais*, não correspondência exata. Não misture idiomas.

**Núcleo — AEM + busca/IA**
- ai search for adobe experience manager
- aem search solution
- aem semantic search
- aem generative ai search
- ai answers for aem content
- aem as a cloud service search
- aem 6.5 search integration
- aem site search engine

**Diferenciais / cauda longa**
- self-hosted enterprise search
- open source ai search platform
- rag chat over cms content
- faceted search for aem
- bring your own llm search
- cited ai search answers
- dumont aem connector
- search across aem repositories

**Categoria / genéricos (mais volume)**
- enterprise search software
- ai powered site search
- semantic search platform
- retrieval augmented generation search
- headless cms search
- adobe experience manager add-on
- llm search for enterprise content
- knowledge base ai search

> Em **Search comum** (não PMax), estes viram palavras-chave — recomende
> correspondência de frase: `"aem search"`, `"aem ai search"`,
> `"adobe experience manager search"`.

---

## 8. Indicador de público-alvo (Audience signal — Performance Max)

Não limita o alcance — dá pista de quem converte para acelerar o aprendizado.

**Segmento personalizado (prioridade) — termos de pesquisa:**
- ai search for adobe experience manager
- aem search integration
- aem semantic search
- enterprise search software
- headless cms search
- rag chat enterprise
- aem as a cloud service

**Segmento personalizado — sites/URLs que o público visita:**
- experienceleague.adobe.com
- experience.adobe.com
- solr.apache.org
- elastic.co
- algolia.com
- aem-ai-search.com (concorrente direto citado na página)

**Seus dados (sinal mais forte, se disponível):**
- Visitantes do site (tag do Google em turing.viglet.org, especialmente `/aem-search`)
- Lista de clientes/leads (quem baixou docs, testou a demo)

**Interesses e dados demográficos detalhados:**
- No mercado: Enterprise Software, Web Design & Development Services, CRM/CMS Software
- Afinidade: Technology Early Adopters, Business Professionals
- Foco: setor de TI/desenvolvimento

> **Recomendação:** comece com *segmento personalizado de intenção* +
> *visitantes do site*. Interesses/in-market entram como reforço.

---

## 9. Estrutura de campanha sugerida

- **Search comum** com ad groups temáticos: `AEM Search`, `AEM AI Search`,
  `AEM Semantic Search`, `Enterprise Search`.
- Ad group **defensivo/comparativo** mirando buscas por `aem-ai-search` e
  alternativas AEM-native (a página é explicitamente posicionada contra um
  produto Cloud-Service-native).
- Hook forte de CTA: a **demo ao vivo no WKND** (`/aem-search/#demo`).

## 10. Páginas reais do site (para escolher destinos)

Rotas prerenderizadas (HTML estático) disponíveis hoje:

- `/` (landing) · `/aem-search` · `/compare` · `/migrate` · `/persona` · `/security`
- `/features/search` · `/features/ask` · `/features/automate` · `/features/run`

Docs externas: `docs.viglet.org/turing/` · `docs.viglet.org/dumont/connectors/aem`.

## 11. Pendências / verificar antes de publicar

- Confirmar as âncoras em produção após o build/deploy do `apps/site`
  (`#demo`, `#matrix`, `#how`, `#depth`, `#faq`, `#cta`).
- Decidir idioma da campanha (EN internacional vs PT Brasil) — esta cópia está
  em **inglês**; gerar equivalente PT se for segmentar o Brasil.
