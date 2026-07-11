# ADR 0003 — Knowledge Base: microthesauri & index-time hierarchical term expansion

- **Status:** Accepted (2026-07-11)
- **Deciders:** Alexandre Oliveira
- **Task:** T668 (Block AQ) — the gate that fixes the domain model, the Solr
  field, and the indexing seam for T669–T678.
- **Related:** IMPROVEMENTS §XL (design rationale), ROADMAP Block AQ, CHANGELOG
  Block AP (native synonyms — the sibling this complements), the settled
  index-time enricher pattern in `TurSNContentFitIndexer` (T472).

## Context

Inspired by OpenText Semantic Navigation, we want a **Knowledge Base** of
**hierarchical microthesauri** (controlled vocabularies) that enrich indexing
with *related terms* in a controlled, structured way — a complement to, not a
replacement for, embedding-based semantic search and the just-shipped native
synonyms (Block AP).

The core behaviour: **at index time**, expand each document with the terms on the
hierarchical path from the root down to every term the document mentions
(`Doença → Doença respiratória → Infecção pulmonar → Pneumonia`), writing that
path into a **dedicated additional field** on the Solr document. Query time stays
simple and fast — no dynamic expansion. Parallel, unrelated branches are **not**
pulled in (a document about "pneumonia" is *not* expanded with "câncer").

Three findings from the exploration constrain this ADR:

1. **The indexing pipeline already has an enricher seam.**
   `TurSNProcessQueue.index(...)` mutates the `attributes` map in place through a
   chain of index-time enrichers (`TurSNContentFitIndexer` T472,
   `TurSNGeminiMediaIndexer` T501) *before* the document is handed to the
   search-engine plugin. A new enricher slots in here with **zero pipeline
   surgery**. `TurSNFieldProvisioner.ensureField(...)` provisions the backing
   Solr field per locale core; `TurSolrDocumentHandler.processAttribute` already
   turns a multi-valued attribute into repeated `addField` calls.

2. **There is no self-referencing tree entity anywhere in the backend.** The only
   precedents for hierarchy are the owner→child aggregate
   (`TurSNSiteSpotlight → TurSNSiteSpotlightTerm`) and the *soft* `parentId`
   id-pointer (`TurChatFlowState.parentStateId`). Language is modelled as a
   `java.util.Locale` column, one row per locale (`TurSNSiteLocale`,
   `TurSNSynonym`) — there is no translation-join convention.

3. **The old OpenText/nstein XML importer is unrecoverable here.** This clone is
   shallow (52 commits, a graft boundary); exhaustive history search
   (`--grep`, pickaxe `-S`, `--diff-filter=D` over `*thesaur*`/`*.dtd`) returns
   nothing, and no `.dtd`/`.xsd` exists in the tree. The XML contract must be
   **reconstructed** from a real sample authority file (see below), not recovered.
   The only surviving "thesaurus" footprint is the vestigial `TurSNSite.thesaurus`
   flag + `TurSNFieldType.THESAURUS` enum (a query-time NER/entity toggle) — it is
   **not** reused by this feature.

### The reconstructed source contract (nstein TME / OpenText SN authority file)

A real sample (`authorityFile`, namespace `http://dev.nstein.com/tme-authority-file/4.10`)
was analysed in full (253 terms). Verified structure:

- **`authorityFile`** → `name` (the microthesaurus name) + `terms`.
- **`term`**: `id` (stable numeric), `name` (preferred label), `enabled`,
  `variations`, `relations`, `attributes` (an always-empty extensibility slot).
- **`variation`**: a surface form for recognition — `weight` (e.g. `100.0`),
  `case` (`ci` = case-insensitive), `accent` (`as` = accent-sensitive),
  `language` (ISO 639-2/B, e.g. `por`).
- **`relation`**: a target `id` + a `type`. **Full verified type set:**
  `BT` broader · `NT` narrower · `RT` related · `UF` used-for · `U` use.
  `BT`↔`NT` and `U`↔`UF` are stored as **reciprocal inverse pairs** on both
  ends; `RT` is symmetric. Non-preferred terms are modelled as their own `term`
  entries linked by `U`/`UF` (in addition to `variations` for spelling variants).

This is the formal contract behind both the XML importer (T673) and the format we
instruct the LLM to emit when generating new hierarchies (T675).

## Decision

Build the Knowledge Base as a **tenant-scoped, reusable library** that SN sites
**opt into per site**, with all expansion complexity paid at **index time** and a
single additional Solr field consumed at query time.

### 1. Domain model — `kb` package, soft-parent tree, faithful relation set

New entities under `com.viglet.turing.persistence.model.kb` (plain `Tur*` prefix
— the library is *not* SN-scoped; only the selection is):

```
TurKnowledgeBase        (aggregate root, @TenantId)
  id, name, description, source{SYSTEM_SEED|AI_GENERATED|XML_IMPORT|USER}, timestamps
  └─ @OneToMany TurMicrothesaurus

TurMicrothesaurus        (one tree = one language + one domain)
  id, name, description
  language : Locale                    // ISO code; drives locale-filtered indexing
  domain   : String                    // GENERAL|EDUCATION|MEDICINE|... — String, not enum
  @ManyToOne knowledgeBase
  └─ @OneToMany TurThesaurusTerm

TurThesaurusTerm         (a node = preferred term / descriptor)
  id, label, enabled, termOrder, scopeNote (nullable), externalId (nullable, for round-trip)
  parentTermId : String                // SOFT FK — the broader/narrower spine
  @ManyToOne microthesaurus
  @ElementCollection variations         // surface forms (label + UF non-preferred) for recognition
  └─ @OneToMany TurThesaurusTermRelation

TurThesaurusTermRelation (a directed edge between two terms)
  sourceTermId, targetTermId
  type : TurThesaurusRelationType {BROADER, NARROWER, RELATED, USE, USED_FOR, CUSTOM}
  directionality : {UNIDIRECTIONAL, BIDIRECTIONAL}
```

**Why the broader/narrower hierarchy is `parentTermId`, not a relation row.** The
BT/NT spine is the one structure that must be walked *fast* for every document at
index time. Modelling it as a soft `parentId` pointer (the
`TurChatFlowState.parentStateId` precedent) keeps ancestor-walking a simple loop,
matches repo convention (no self-referencing JPA entity exists), and avoids
recursive lazy-loading. The **relation table** carries everything *else* the
thesaurus contract needs — `RELATED` (RT, associative), `USE`/`USED_FOR` (U/UF,
equivalence), and `CUSTOM` uni/bi links — so the full ISO-style vocabulary
round-trips through XML import/export while expansion stays trivial. Rejected:
one relation table for BT/NT too (turns a `while (parent != null)` walk into
repeated relation queries on the hot path).

**Recognition vs expansion — the split that makes it work.**
`variations` (the preferred label + `USE`/`USED_FOR` non-preferred terms) feed the
**recognition dictionary**: they are what we match in document text. The `BROADER`
chain feeds **expansion**: once a term is recognised, we write the root→term
ancestor path. So a document that uses a synonym of "pneumonia" is still
recognised and still expanded to the canonical path.

### 2. Solr field — one multi-valued TEXT field, low boost, free concept facet

Provision **`microthesaurus_terms`** as `text_general`, **multi-valued, stored**,
via `TurSNFieldProvisioner.ensureField(...)`. This yields two uses from one field
because Turing TEXT fields already auto-create a `_str` copy field:

- `microthesaurus_terms` (tokenised) → added to `qf` with a **low boost**
  (≈ 0.5–1.0, below title/body) → controlled-vocabulary **recall expansion**
  (searching "doença" now reaches the "pneumonia" documents) without dominating
  relevance.
- `microthesaurus_terms_str` (auto copy field) → the **concept facet** for
  Semantic-Navigation-style browse/drill-down.

Rejected: `strings` (exact, facet-only — loses the full-text recall gain);
reusing the vestigial `THESAURUS` field type (couples a new feature to a legacy
query-time toggle for no benefit). A hierarchical drill-down `microthesaurus_path`
field (path tokens `0/Doença`, `1/Doença/Doença respiratória`, …) is deferred to
T677 — the MVP field is flat.

### 3. Indexing seam — a new enricher, modelled exactly on T472

New `TurSNMicrothesaurusIndexer` in `com.viglet.turing.sn.kb` with
`enrich(TurSNSite site, Locale locale, Map<String,Object> attributes)`, wired into
`TurSNProcessQueue.index(...)` **after** the existing enrichers. Flow:

1. Early return if the site selected no microthesauri → **byte-identical legacy
   indexing path** (the T472 discipline).
2. Load (cached) the compiled recognition dictionary for
   (site's selected microthesauri ∩ document locale).
3. Extract text (title/description/body — same helper shape as
   `TurSNContentFitIndexer.extractText`).
4. Match terms (normalised: lowercase + optional accent-fold per the variation's
   `case`/`accent`; multi-word via an Aho-Corasick automaton).
5. For each match, collect the root→term ancestor labels; dedupe.
6. `ensureField(...)` + `attributes.put("microthesaurus_terms", List<String>)`.
7. **Defensive by construction** — any failure is swallowed and logged; a
   thesaurus problem can never block a document from indexing.

**All complexity is at index time.** The compiled automaton is cached per
(site, locale) and evicted when a knowledge base, term, or site selection changes
(the caching-skill rule: cache immutable read-models, never repositories, always
pair with an eviction). Query time stays a plain `qf` entry + a facet.

### 4. Selection & customisation — opt-in per site

A join entity `TurSNSiteMicrothesaurus` (site ↔ selected microthesaurus, `enabled`)
plus per-site index config (field name / boost / include-synonyms) records which
microthesauri a site indexes with. Customers **select** what they use, and can
**create/edit** terms during a project through the CRUD (T669) and the bento
editor (T676) — the library is not all-or-nothing.

## Consequences

- **T668** (this ADR's task) lands the model + persistence + Liquibase +
  repositories. **T669** adds the console CRUD API. Only a plain
  `Tur*Repository extends JpaRepository<_, String>` is added; a `*Domain` record
  is introduced later *only* if a read proves hot/cached/cross-aggregate.
- **T670** adds per-site selection + field-provisioning config. **T671** builds
  the cached recognition dictionary/matcher; **T672** wires the enricher into the
  queue, provisions `microthesaurus_terms`, adds the `qf` boost + facet, and
  proves it with an index-time integration test.
- **T673** reconstructs the authority-file XSD/DTD as the formal contract and
  implements the XML importer (`BT`/`NT` → `parentTermId`, `RT`/`U`/`UF` →
  relations, `variations` → recognition surface forms).
- **T674** ships the pre-populated **EDUCATION-first** knowledge base as an
  importable seed bundle (the T373/T655 seed-ZIP precedent, not baked into
  startup). **T675** adds LLM-assisted generation of new hierarchies emitting the
  T673 format. **T676** is the bento admin editor.
- **T677/T678** (💭) are the hierarchical concept facet and a query-time
  related-term suggestion surface built on the `RT` links.
- This is a **complement** to Block AP synonyms and embedding search, not a
  replacement: synonyms rewrite the *query analyzer*; microthesauri enrich the
  *document* with controlled hierarchical vocabulary at index time. The decision
  is mirrored into IMPROVEMENTS §XL so it guides day-to-day work without a doc dive.
