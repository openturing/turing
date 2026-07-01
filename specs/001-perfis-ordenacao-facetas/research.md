# Research — Perfis de Ordenação de Facetas

Análise do estado atual do projeto `turing` e decisões técnicas que embasam o `plan.md`.
**Fase 0 concluída em 2026-06-25** — ver §3 (achados confirmados).

---

## 1. Estado atual (como a ordenação funciona hoje)

### 1.1 Persistência (fonte da posição)
A posição de cada faceta vive **dentro da própria faceta**, presa ao site:

- `TurSNSiteFieldExt.facetPosition` — `Integer`, coluna literal `facetPosition`
  (`@Column` sem `name`), tabela `sn_site_field_ext`.
  - `.../persistence/model/sn/field/TurSNSiteFieldExt.java:108`
  - Também possui `facetSort` (linha 106) e a relação `customFacets` (`OneToMany`, linha 94).
- `TurSNSiteCustomFacet.facetPosition` — `Integer`, coluna `facet_position`
  (`@Column(name = "facet_position")`), tabela `sn_site_custom_facet`.
  - `.../persistence/model/sn/field/TurSNSiteCustomFacet.java:82-83`; FK `field_ext_id` (não nula).
- Default global de sort (não de ordem): `TurSNSite.facetSort`, `.../model/sn/TurSNSite.java:134`.
- Id em ambas: `@Id @TurAssignableUuidGenerator @Column(name="id")` → `String` (UUID atribuível).

### 1.2 Leitura/escrita da ordenação (Console)
- Controller `TurSNSiteFacetedFieldAPI`, base `/api/sn/{snSiteId}/facet`.
  - `GET /` → `turSNFieldProcess.getTurSNSiteFacetOrdering(snSiteId)` → `TurSNSiteFacetOrderingDto`.
  - `PUT /ordering` → escreve `facetPosition` em cada faceta + `@CacheEvict`.
- Montagem da lista: `TurSNFieldProcess.getTurSNSiteFacetOrdering(siteId)` +
  `TurSNFacetDefinitionFactory.fromFields(...)`, ordenado por `position` e `label`.

### 1.3 Aplicação em runtime (busca)
- `TurSolrResultProcessor.processSEResultsFacet(...)` (≈ 311-343): monta `List<TurSEFacetResult>`,
  chama `turSNFieldProcess.getTurSNSiteFieldOrdering(siteId)`, cria `Map<nome,posição>` e **ordena**
  a lista por `facetPosition` (`nullsLast`).

## 2. Decisões técnicas

### DT-1 — Mover a posição para uma junção perfil × faceta
A posição passa a viver em `TurSNSiteFacetProfileItem` (perfil → faceta → posição). Os campos
`facetPosition` legados em `TurSNSiteFieldExt`/`TurSNSiteCustomFacet` **permanecem** (ver DT-6).

### DT-2 — Resolução por perfil na "costura" existente (confirmado pela Fase 0)
Alterar a origem do `positionByName` em `TurSolrResultProcessor.processSEResultsFacet`, via novo
`TurSNFieldProcess.getTurSNSiteFacetOrdering(snSiteId, profileNameOrId)` (fallback ao padrão).
A ordem é resolvida **fora do snapshot** (ver §3 Q1) — seguro.

### DT-3 — Identificador externo do perfil = nome
`facetProfile` usa o **nome** do perfil (legível). Exige `name` único por site (constraint).

### DT-4 — Endpoints novos sob a base existente
Manter `/api/sn/{snSiteId}/facet` e adicionar `/profile` e `/profile/{profileId}/ordering`.
Endpoints legados operam sobre o perfil padrão.

### DT-5 — Estilo de persistência
Spring Data `JpaRepository` direto (como `TurSNSiteFieldExtRepository`) + serviço/controller.

### DT-6 — `facetPosition` legado = espelho do perfil PADRÃO (decisão da Fase 0)
Como **apenas Solr** suporta perfis (decisão de escopo) e **Elasticsearch/Lucene continuam lendo
`facetPosition` legado** (§3 Q3), o campo legado é mantido como **espelho do perfil padrão**:
- a migração/bootstrap popula o perfil "Geral" a partir do legado;
- ao **salvar a ordenação do perfil padrão** (ou **promover** um perfil a padrão), o serviço
  **sincroniza de volta** o `facetPosition` legado das facetas.
- Salvar perfis **não-padrão** não toca o legado.

Efeito: ES/Lucene e qualquer leitor legado sempre refletem o "Geral", sem precisar conhecer
perfis. Solr resolve qualquer perfil via os itens.

## 3. Achados da Fase 0 (confirmados)

### Q1 — Snapshot do site → RESOLVIDO (resolver FORA do snapshot)
- Mecanismo: cache Spring in-memory `turSNSiteSearchSnapshot`, record imutável
  `sn/snapshot/TurSNSiteSearchSnapshot.java`, construído por
  `sn/snapshot/TurSNSiteSearchSnapshotService.build()` (≈121-156), keyed por `siteName:locale`.
- O snapshot **contém** `enabledFields` (com `facetPosition`) e `customFacets` (com
  `facetPosition`), mas isso é usado pelo **query builder** apenas para decidir **quais** facetas
  entram na query (`TurSolrQueryBuilder.getEnabledFields()` ≈789-801, com fallback ao DB).
- A **ordem final** é re-resolvida no `TurSolrResultProcessor.processSEResultsFacet` **a partir do
  DB** (`getTurSNSiteFieldOrdering`, ≈326) — **não** do snapshot.
- `TurSNSiteSnapshotEvictionListener` (`@PostPersist/@PostUpdate/@PostRemove`) faz `cache.clear()`
  (limpa todo o cache) quando `TurSNSiteFieldExt`/`TurSNSiteCustomFacet` mudam.
- **Conclusão**: resolver a ordem por perfil no result processor é seguro. As **entidades de
  perfil NÃO devem ter** `@EntityListeners(TurSNSiteSnapshotEvictionListener.class)` (mudança de
  perfil não precisa evict). Correção aplicada ao `data-model.md §2.1`.

### Q2 — Propagação de `facetProfile` → RESOLVIDO (espelhar o parâmetro `sort`)
Caminho (mesmo do `sort`):
`TurSNSiteSearchAPI` (`api/sn/search/`, `GET/POST /api/sn/{siteName}/search`,
`@ModelAttribute TurSNSearchParams`) → `TurSEParameters` → `TurSNSiteSearchContext`
(`TurSNUtils.getTurSNSiteSearchContext`) → `TurSNSearchProcess.search` → `TurSolr.retrieveSolrFromSN`
→ `TurSolrQueryContext` → `TurSolrResultProcessor.processSEResultsFacet`.

Pontos a alterar (⚠️ vários em **`turing-commons`**):
1. `turing-commons` `.../sn/search/TurSNParamType.java` → `FACET_PROFILE = "facetProfile"`.
2. `turing-commons` `.../sn/bean/TurSNSearchParams.java` → `String facetProfile`.
3. `turing-commons` `.../se/TurSEParameters.java` → `facetProfile` (+ constructor + `overrideFromPost`).
4. `turing-commons` `.../sn/bean/TurSNSitePostParamsBean.java` → `String facetProfile`.
5. `turing-app` `TurSNSiteSearchAPI` → `@RequestParam(name=FACET_PROFILE)` (set no params).
6. `turing-app` `solr/TurSolrQueryContext.java` → campo `facetProfile`.
7. `turing-app` `solr/TurSolr.retrieveSolrFromSN` → passar ao builder do context.
8. `turing-app` `TurSolrResultProcessor.processSEResultsFacet` → usar `queryContext.getFacetProfile()`.

### Q3 — Leitores legados de `facetPosition` → MAPEADOS (3 backends!)
- **Solr**: `TurSolrResultProcessor.java:326` (no escopo — resolve por perfil).
- **Elasticsearch**: `TurElasticsearch.java:90,149,407` (FORA do escopo — lê legado = padrão).
- **Lucene**: `TurLuceneResultProcessor.java:308`, `TurLuceneQueryBuilder.java:109` (FORA do
  escopo — lê legado = padrão).
- Console/escrita: `TurSNSiteFieldExtAPI.java:177,306,330,376,388`,
  `TurSNSiteCustomFacetAPI.java:226-230,298,403` (auto-incremento de posição ao criar faceta).
- Definições: `TurSNFieldFacetDefinition.java:38`, `TurSNCustomFacetDefinition.java:49`,
  `TurSNFacetDefinition.java:50`.
- Repositório: `TurSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition`.
- **Decisão (D backends = Apenas Solr)**: só `TurSolrResultProcessor` passa a resolver por perfil.
  ES/Lucene leem o `facetPosition` legado, que DT-6 mantém igual ao perfil padrão.

### Q4 — Export/Import → SEM RISCO
`TurSNSiteImport.java:642-646` reidrata `customFacets`/itens. O `facetPosition` viaja **dentro**
das entidades serializadas (atributo). Mantendo o campo legado, o import **não quebra**; o perfil
"Geral" é derivado dele pelo bootstrap após o import. Incluir perfis no export é escopo futuro.

### Q5 — Seed do "Geral" → DDL no Liquibase + backfill em runtime
Id é UUID atribuível pela app (`@TurAssignableUuidGenerator`); gerar UUID portável em SQL puro é
problemático. Liquibase faz só o DDL (tabelas/FKs/uniques); um bootstrap idempotente em runtime
cria o "Geral" e popula os itens a partir do `facetPosition` legado (cobre sites existentes,
novos e importados).

## 4. Escopo confirmado (decisões da Fase 0)

- **Backends**: **apenas Solr** resolve por perfil. ES e Lucene mantêm o comportamento atual
  (usam `facetPosition` legado = perfil padrão, via DT-6).
- **Clientes**: **apenas a API REST**. Não alterar `turing-java-sdk` (`TurSNQuery`) nem
  `react-sdk` (`SearchParams`) nesta entrega.

## 5. Alternativas consideradas (e descartadas)

- **A1** — posição em colunas por perfil: inviável (nº dinâmico de perfis).
- **A2** — perfil como JSON na entidade do site: perde integridade/cascade.
- **A3** — "perfil ativo no site": descartado por D2.
- **A4** — padrão hexagonal completo (Domain/Port/Adapter): mais cerimônia que o necessário (DT-5).
- **A5** — incluir perfil no snapshot: descartado por Q1 (ordem é resolvida fora do snapshot;
  evitaria fragmentação de cache `siteName:locale:profile`).

## 6. Nota operacional relacionada: campos de data multivalor que viram facetas

> Descoberto em 2026-06-26 durante a importação/indexação de conteúdo do Insper
> (`insper-aem-plugin`). Não faz parte do escopo de *perfis de ordenação*, mas afeta os campos
> que se tornam facetas — registrado aqui a pedido.

**Sintoma.** A indexação aborta documentos com erro do Solr (ver `turing.log`):
`RemoteSolrException: Error adding field 'endOfClasses'='2026-04-24...⏎2026-04-12...⏎2026-04-30...'
msg=Invalid Date Math String` (ex.: doc `/content/insper-portal/pt/cursos/economiaee1`).

**Causa (no projeto turing).** O conector envia `endOfClasses` como **lista** de datas (uma por
oferta/contest — `DumAemExtInsperModelJson:184` `setAllDates(END_OF_CLASSES, endDates)`). No Turing,
como o campo **não** está marcado `multiValued`,
`TurSolrDocumentHandler.processArrayList`/`processJSONArray` (linhas 281-315) **concatena** os
valores via `concatenateString` (`System.lineSeparator()`, linhas 142-148). O Solr recebe três
datas concatenadas num campo `date` → `Invalid Date Math String` e **descarta o documento inteiro**.

**Correção (decisão: tornar `multiValued`).** Marcar o campo (`endOfClasses` e demais campos de
data de oferta com múltiplos valores) como `multiValued=1` em `TurSNSiteFieldExt`/`TurSNSiteField`:
- o handler passa a indexar cada data como valor separado (ramo `isMultiValued`, linha 284/307);
- `TurSolrUtils.addOrUpdateField` (linhas 132-148) cria/atualiza o campo no Solr com
  `"multiValued", true`;
- a faceta range de data passa a refletir todas as ofertas do curso.

**Atenção operacional.** Se o campo já existe no Solr como *single-valued*, é preciso
**substituir** (replace-field) e **reindexar** — alterar só o flag no Turing não converte o campo
já materializado no schema do Solr. Aplicar a mesma análise a outros campos de oferta com N valores
(ex.: `startOfClasses`).

### 6.1 Sintoma derivado e correção implementada (2026-06-26)

Após marcar o campo como `multiValued` **no Turing** sem reconciliar o Solr, a indexação passa a
falhar com `multiple values encountered for non multiValued field dateOfClassesText` (o handler
envia os N valores; o campo no Solr ainda é single-valued).

**Correção (auto-reconciliação na indexação).** `TurSNProcessQueue.createMissingFields`, para campos
**já existentes** cujo `TurSNSiteField.multiValued = 1`, chama `plugin.reconcileMultiValued(...)` →
`TurSolrUtils.reconcileMultiValued`, que torna o campo do Solr `multiValued` **preservando o tipo**
(REPLACE só quando o campo ainda é single-valued). Roda **antes** do `indexDocument` no mesmo job e
**no máximo 1× por (site, campo) por JVM** (`Set reconciledMultiValuedFields`). Solr only (ES/Lucene:
default no-op). Teste: `TurSNProcessQueueTest.testReceiveIndexingQueue_ReconcilesMultiValuedFieldInSolr`.

### 6.2 Incidente `preco` (2026-06-26) — REPLACE NÃO pode mudar o tipo

A 1ª versão da reconciliação passava o **tipo do Turing** no REPLACE
(`addOrUpdateField(..., fieldExt.getType(), ...)`). Como `preco` é `multiValued` e seu tipo no Turing
era `FLOAT` (`pfloat`, 4 bytes) enquanto o índice tinha documentos `pdouble` (8 bytes), o REPLACE
mudou o tipo do campo no Solr e a indexação passou a falhar com
`cannot change field "preco" from points ... numBytes=8 to ... numBytes=4`. Corrigido fazendo a
reconciliação **preservar o tipo** (ver §6.1). **Lição**: mexer só no flag `multiValued`, nunca no
tipo, de campos já indexados.

> **Recuperação do índice já corrompido**: como há documentos com o tipo antigo, o conflito só some
> com **reindex limpo**. Decidir o tipo de `preco` (recomendado `DOUBLE`/`CURRENCY` para valores
> monetários — `FLOAT` perde precisão), alinhar Turing↔Solr (deletar/recriar o campo no Solr ou o
> core), limpar o core e reindexar.

### 6.3 Loop de indexação + promoção automática (2026-06-26)

**Loop de reprocessamento (resiliência da fila).** `TurSolrDocumentHandler.addSolrDocument`
capturava apenas `SolrServerException | IOException`; a `RemoteSolrException` (que é
`RuntimeException`) **escapava** e propagava até o listener JMS, que reentregava o job em ciclo
(retries a cada ~0,5s) — travando a fila de indexação e arriscando abrir o `CircuitBreaker` do Solr
(derruba a indexação de todos os sites). **Corrigido**: `catch (Exception e)` (loga e segue) — um
documento inválido não derruba mais a fila.

**Promoção automática a multiValued.** `endOfClasses`/`startOfClasses` seguiam `multiValued=0` no
Turing → o handler concatenava as datas → `Invalid Date Math String`. Novo
`TurSNProcessQueue.promoteMultiValuedFromPayload` (chamado em `index()` após `createMissingFields`,
antes do `indexDocument`): detecta atributos com **múltiplos valores** cujo campo é de tipo
**não-concatenável** (`DATE`/`INT`/`LONG`/`FLOAT`/`DOUBLE`/`BOOL`/`CURRENCY`) e `multiValued=0`, e
**promove** o campo a `multiValued` (`TurSNSiteField` + `TurSNSiteFieldExt`) + reconcilia o Solr
(preservando o tipo, via §6.1). `STRING`/`TEXT`/`ARRAY` não são promovidos (concatenar texto é
válido). 1× por (site, campo) por JVM. O `@CacheEvict` do `save` garante que o handler já veja
`multiValued=1` no mesmo job. Teste:
`TurSNProcessQueueTest.testReceiveIndexingQueue_PromotesNonConcatenableFieldToMultiValued`.

> Mudar single→multi no Solr é alteração de metadado (não reindexa automaticamente). Documentos
> indexados *antes* sob o schema single permanecem com 1 valor até serem reindexados — para o
> conteúdo que estava **falhando** não há perda (nunca foi indexado).

### 6.4 `LazyInitializationException` ao salvar/semear ordenação (2026-06-30)

**Sintoma.** Ao reordenar facetas no frontend (e ao semear o perfil "Geral"), o
`TurSNSiteFacetProfileService` lançava
`org.hibernate.LazyInitializationException: Cannot lazily initialize collection of role
'...TurSNSiteFieldExt.customFacets'` em `persistOrdering` (≈ linha 255) e `seedDefaultProfile`
(≈ linha 104).

**Causa (no projeto turing).** `TurSNSiteFieldExtRepository.findByTurSNSiteAndEnabled` é
`@Cacheable` → devolve entidades **detached** (fora de sessão). Acessar a relação **LAZY**
`field.getCustomFacets()` nessas entidades dispara a inicialização tardia sem sessão ativa → exceção.
(Regra do projeto: relações são `FetchType.LAZY`; repositórios de leitura são `@Cacheable`.)

**Correção (padrão seguro já usado em `TurSNFacetDefinitionFactory`).** Injetar
`TurSNSiteCustomFacetRepository` e carregar os custom facets por **query explícita** em vez de
navegar a coleção LAZY:
```java
private List<TurSNSiteCustomFacet> loadCustomFacets(List<TurSNSiteFieldExt> enabledFields) {
    return enabledFields.isEmpty() ? Collections.emptyList()
            : turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(enabledFields);
}
```
`seedDefaultProfile` e `persistOrdering` passam a usar `loadCustomFacets(...)`; os custom facets são
salvos **diretamente** via `turSNSiteCustomFacetRepository.saveAll(...)` (não mais por cascade do
`fieldExt` pai, que também viria detached). Testes (verdes):
`TurSNSiteFacetProfileServiceTest` (8) — stubs de `findByFieldExtsWithDetails` nos cenários de seed
e save.

**Lição.** Nunca navegar relações LAZY de entidades vindas de repositório `@Cacheable`; usar a
query `...WithDetails(fields)` correspondente. Vale para todo serviço novo que parta de
`findByTurSNSiteAndEnabled`.
