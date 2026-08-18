# Tasks — Perfis de Ordenação de Facetas

Quebra em tarefas por fase. `[P]` = paralelizável dentro da fase. Marque `[x]` ao concluir.
Gate por fase: ver `plan.md`.

---

## Fase 0 — Pesquisa e confirmação ✅ CONCLUÍDA (ver research.md §3-§4)
- [x] **T001** Snapshot: ordem é resolvida **fora** do snapshot (result processor lê do DB).
  Entidades de perfil **não** levam `@EntityListeners` de eviction. (research Q1)
- [x] **T002** Propagação de `facetProfile` mapeada (espelha `sort`); beans em **turing-commons**
  + turing-app (8 pontos). (research Q2)
- [x] **T003** Leitores legados mapeados: Solr (no escopo), Elasticsearch + Lucene (fora; leem
  legado). (research Q3)
- [x] **T004** Export/Import não quebra (`facetPosition` viaja na entidade). (research Q4)
- [x] **T005** Seed: DDL no Liquibase + backfill em runtime. (research Q5)
- [x] **T006** Escopo travado: **Apenas Solr**; **sem SDKs**; `facetPosition` legado = espelho do
  padrão (DT-6).

## Fase 1 — Modelo de dados e persistência ✅ CONCLUÍDA (2026-06-25)
- [x] **T010** Criar `TurSNSiteFacetProfile` (`sn_site_facet_profile`), `@since 2026.2.7`, LAZY,
  **sem** eviction listener.
- [x] **T011** Criar `TurSNSiteFacetProfileItem` (`sn_site_facet_profile_item`) — dois FKs nuláveis
  (fieldExt/customFacet) + `@OnDelete(CASCADE)`.
- [x] **T012** Alterar `TurSNSite` (OneToMany `turSNSiteFacetProfiles`, cascade/orphan + import +
  `@JsonIgnoreProperties`).
- [x] **T013/T014** Remoção de itens ao excluir faceta resolvida via **FK `ON DELETE CASCADE`**
  (Liquibase + `@OnDelete` no item) — `TurSNSiteFieldExt`/`TurSNSiteCustomFacet` **não** foram
  alterados (menos risco; `facetPosition` legado mantido em uso por DT-6, sem `@Deprecated`).
- [x] **T015** `TurSNSiteFacetProfileRepository` (findByTurSNSite, ...AndNameIgnoreCase,
  ...AndDefaultProfileTrue, existsByTurSNSiteAndNameIgnoreCase).
- [x] **T016** `TurSNSiteFacetProfileItemRepository`.
- [x] **T017** Liquibase `v2026.2.7.8.yaml` (DDL idempotente, ids `VARCHAR(255)`) + incluído no master.
- [x] **T018** `onstartup/sn/TurSNSiteFacetProfileOnStartup` (`@Order(LOWEST_PRECEDENCE)`,
  idempotente): cria "Geral" + popula itens do `facetPosition` legado (FR-05/D4).
- [x] **T019** `TurSNSiteFacetProfileOnStartupTest` (seed, idempotência, perfil vazio) — 3/3 verde.
- [x] **T020** Gate Fase 1: `mvnw -pl turing-app -am compile` + teste **BUILD SUCCESS**.

## Fase 2 — API de perfis e ordenação ✅ CONCLUÍDA (2026-06-25)
- [x] **T030** `TurSNSiteFacetProfileDto` (record, `@JsonProperty("default")`, `from(...)`).
- [x] **T031** `sn/facet/TurSNSiteFacetProfileService`: CRUD + 1-padrão (FR-04) + bloqueio de
  exclusão do padrão 409 (FR-03) + `setDefault` + `create` clonando o padrão + nome duplicado 409.
- [x] **T032** `getOrdering`/`saveOrdering` (+ `getDefaultOrdering`/`saveDefaultOrdering`); FR-08
  (facetas sem posição no perfil vão ao final, por rótulo).
- [x] **T033** **DT-6**: `saveOrdering` no padrão e `setDefault` sincronizam o `facetPosition`
  legado (`syncLegacyPositions` / save no fieldExt → `@CacheEvict` do repo).
- [x] **T034** Endpoints `/profile` (GET/POST 201/PUT/PUT `default`/DELETE 204).
- [x] **T035** Endpoints `/profile/{profileId}/ordering` (GET/PUT).
- [x] **T036** Legados `GET /facet` e `PUT /facet/ordering` agora operam sobre o padrão (FR-16).
- [x] **T037** Testes: `TurSNSiteFacetProfileServiceTest` (8), `TurSNSiteFacetedFieldAPITest` (4,
  delegação), `OnStartupTest` (2, delegação). 25/25 verde com `TurSNFieldProcessTest`.
- [x] **T038** Gate Fase 2: **BUILD SUCCESS**.

> **Nota de design**: o seed do perfil padrão foi centralizado em
> `TurSNSiteFacetProfileService.ensureDefaultProfile`; o `OnStartup` agora apenas delega (DRY).
> Os endpoints legados/`getDefaultOrdering` criam o padrão on-demand se ausente (robustez).

## Fase 3 — Runtime de busca (Solr) ✅ CONCLUÍDA (2026-06-25)
- [x] **T040** `TurSNFieldProcess.getTurSNSiteFieldOrdering(snSiteId, facetProfile)` +
  `resolveProfilePositions` (nome → id → perfil padrão → legado se não houver perfil). Injetado
  `TurSNSiteFacetProfileRepository`. Posições ausentes no perfil → `MAX_VALUE` (vão ao fim).
- [x] **T041** `turing-commons`: `TurSNParamType.FACET_PROFILE`, `TurSNSearchParams.facetProfile`,
  `TurSEParameters.facetProfile` (constructor + `overrideFromPost`), `TurSNSitePostParamsBean.facetProfile`.
- [x] **T042** Propagação: **não foi preciso** tocar `TurSNSiteSearchAPI`, `TurSolr` nem
  `TurSolrQueryContext` — o GET binda `facetProfile` via `@ModelAttribute TurSNSearchParams`, e
  `TurSEParameters` (que carrega `facetProfile`) já flui no `TurSolrQueryContext`. Menos superfície.
- [x] **T043** `TurSolrResultProcessor.processSEResultsFacet`: lê
  `queryContext.getTurSEParameters().getFacetProfile()` e chama o novo método; sort de valores/
  visibilidade inalterados (FR-12).
- [x] **T044** `TurSNFieldProcessProfileTest` (2: aplica perfil por nome / fallback ao padrão);
  `TurSNFieldProcessTest` ajustado (4º arg do construtor) e verde; ES/Lucene não tocados.
- [x] **T045** Gate Fase 3: **BUILD SUCCESS** (turing-commons + app).

## Fase 4 — Frontend ✅ CONCLUÍDA (2026-06-25)
- [x] **T050** `models/sn/sn-site-facet-profile.model.ts` (`TurSNSiteFacetProfile`).
- [x] **T051** `services/sn/sn.facet.profile.service.ts` (query/create/update/setDefault/remove/
  queryOrdering/saveOrdering).
- [x] **T052** ~~React Query~~ → **seguido o padrão da própria tela** (`useState`/`useEffect` +
  service), por coesão local; a tela de facetas ainda não usa React Query. (Sem `keys.ts`.)
- [x] **T053** Seletor `<Select>` de perfis + estado em `sn.site.facet.list.page.tsx`.
- [x] **T054** CRUD via `Dialog` (criar/renomear) + `GradientButton` (definir padrão/excluir),
  `toast` de sucesso/erro; padrão protegido na UI.
- [x] **T055** `SNSiteFacetDraggableList` ganhou callback `onSaveOrder` (a página injeta
  `saveOrdering(siteId, profileId, items)`); fallback legado preservado.
- [x] **T056** i18n `pt`/`en` em `sn.json` (`sn.facets.profile.*`).
- [x] **T057** Gate Fase 4: `tsc --noEmit -p tsconfig.app.json` **sem erros** (node 16 + tsc local).

## Fase 5 — Integração e validação
- [x] **T059** Commit da feature na `2026.2` local: `69430661b0` (2026-07-01).
- [ ] **T060** IT end-to-end (`AbstractTuringSpringIT`) — cenários do `quickstart.md` (incl. DT-6).
- [ ] **T061** Suíte completa backend + build frontend.
- [ ] **T062** Validar export/import (T004) e ES/Lucene seguindo o padrão (DT-6). *Parcial*: import
  do `insper-stage-author` validado manualmente no app (2026-07-02) — perfil "Geral" derivado OK.
- [ ] **T063** Atualizar docs se aplicável.
- [ ] **T064** Revisar critérios de aceite (`spec.md §8`).
- [ ] **T065** Integrar `origin/2026.2` (2 commits: `4ac6e9a437` custom facet handling +
  `99d2ba63af` fix ranking) e **push**. Conflito provável em `TurSNSiteFacetedFieldAPI.java`
  (preservar delegação ao service); no repositório de ranking, ficar com a versão upstream
  (`@Query left join fetch`). Ver HANDOFF §3.14 e §7.1.

> **Validação manual (2026-07-02)**, com o app local de pé: import do site `insper-stage-author`
> OK e Console operando. Descoberto bug **pré-existente** na lista de Classificação de Resultados
> (`LazyInitializationException` em cache hit — `@Cacheable` + Hazelcast + coleção lazy); a
> correção já existe na `origin/2026.2` (`99d2ba63af`) e entra com o T065 — até lá o erro ainda
> reproduz localmente.

---

### Dependências (resumo)
- Fase 0 ✅ → libera tudo.
- Fase 1 → Fases 2/3/4.
- Fase 2 (API) → Fase 4 (frontend consome).
- Fase 3 em paralelo à Fase 4 (ambas dependem da Fase 1).
- Fase 5 → todas.
