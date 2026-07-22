# HANDOFF — Perfis Genéricos em "Gerenciamento" (spec 002)

> Estado consolidado para retomar em nova conversa. Última atualização: **2026-07-17**.
> Branch: **`feature/turing-generic-profiles`** (criada a partir de `feature/turing-facet-ordering-profiles`,
> que já tinha o spec 001 + merge de `origin/2026.2`).

## 1. O quê
Perfil vira **global e genérico**, criado em **"Gerenciamento"**; em **"Facetas"** só se seleciona
o perfil e configura a ordenação daquele site. Substitui o modelo per-site do spec 001. Ver
`README.md`/`spec.md`.

## 2. Decisões travadas (AskUserQuestion)
- **Global (sistema)**; **genérico/extensível** (só facetas agora); **substituir e migrar** o 001;
  **perfil padrão "Padrão" global protegido**.

## 3. Fase 0 — resolvida (decisões)
- **Privilégio**: nova categoria `PROFILE_VIEW/CREATE/EDIT/DELETE` em `TurPrivilegeOnStartup`;
  `@Secured({"ROLE_ADMIN","PROFILE_*"})`; sidebar `profiles → PROFILE_VIEW`.
- **Rota/ícone**: `/admin/profile/instance` (`ROUTES.PROFILE_*`); i18n `home.features.profiles.*`;
  ícone `IconId` (ajustável).
- **Migração — consolidação por nome**: homônimos entre sites → **um** perfil global com uma config
  por site; "Geral" per-site do 001 → **"Padrão"** global.
- **Tabelas do 001**: mantidas por 1 release (rollback); drop na Fase 6.
- **`profile`/SDK**: sem mudança de forma; entidades de perfil **sem** `@EntityListeners`.

## 4. Fase 1 — CONCLUÍDA ✅ (5 testes verdes; changelog agnóstico OK)
**Novos (backend):**
- `persistence/model/profile/TurProfile.java` (global, `tur_profile`, name único global)
- `persistence/model/profile/TurProfileFacetOrdering.java` (config perfil×site, unique)
- `persistence/model/profile/TurProfileFacetOrderingItem.java` (posição por faceta)
- `persistence/repository/profile/TurProfileRepository.java`,
  `TurProfileFacetOrderingRepository.java`, `TurProfileFacetOrderingItemRepository.java`
- `onstartup/profile/TurProfileMigrationOnStartup.java` (cria "Padrão" + migra 001 + fallback legado;
  idempotente e order-independente; `@Order(LOWEST_PRECEDENCE)`)
- `resources/db/changelog/v2026.2.7.9.yaml` (só DDL das 3 tabelas)
- teste: `onstartup/profile/TurProfileMigrationOnStartupTest.java` (5)

**Alterados (backend):** `db.changelog-master.yaml` (include do `.9`).

## 4b. Fase 2 — CONCLUÍDA ✅ (gate: 33 testes verdes)
**Novos (backend):**
- `profile/TurProfileService.java` — CRUD global; `ensureDefaultProfile()` (dono do
  `DEFAULT_PROFILE_NAME = "Padrão"`); `list()` semeia o padrão sob demanda; proteção do padrão
  (renomear/excluir → 409, descrição editável); 1º perfil criado vira padrão; `setDefault` troca o
  flag (sincronização DT-6 será ligada na Fase 3 via ordering service).
- `api/profile/TurProfileAPI.java` — base **`/api/profile`** (padrão `/api/{feature}` do console;
  contrato ajustado vs. rascunho `/api/v2/profile`); `@Secured({"ROLE_ADMIN","PROFILE_*"})`.
- `api/profile/bean/TurProfileDto.java` (record; `@JsonProperty("default")`) e
  `TurProfileRequest.java` (record sem primitivos — gotcha Jackson 3).
- testes: `profile/TurProfileServiceTest` (13), `api/profile/TurProfileAPITest` (1).

**Alterados (backend):**
- `onstartup/auth/TurPrivilegeOnStartup.java` — +4 privilégios `PROFILE_VIEW/CREATE/EDIT/DELETE`
  (categoria `PROFILE`; ROLE_ADMIN recebe automaticamente; nenhum teste amarra a lista).
- `onstartup/profile/TurProfileMigrationOnStartup.java` — delega `ensureDefaultProfile` ao service
  (DRY; constante movida p/ `TurProfileService`); teste ajustado (3, ensure* migraram p/ service test).

## 4c. Fase 3 — CONCLUÍDA ✅ (gate: 53 testes verdes)
**Novos (backend):**
- `profile/TurProfileFacetOrderingService.java` — get/save da ordenação por **(perfil × site)**
  com upsert; fallback de leitura: config própria → config do Padrão → `facetPosition` legado;
  **DT-6**: salvar a config do Padrão sincroniza o legado do site; `setDefault(profileId)`
  orquestra `TurProfileService.setDefault` + reescreve o espelho legado em **todos** os sites onde
  o novo padrão tem config. Custom facets salvos via `save()` item-a-item (`@CacheEvict`, NFR-03);
  custom facets carregados via `findByFieldExtsWithDetails` (nunca coleção LAZY de `@Cacheable`).
- teste `profile/TurProfileFacetOrderingServiceTest.java` (7).

**Alterados (backend):**
- `api/sn/console/TurSNSiteFacetedFieldAPI.java` — REESCRITA: legados `GET /`+`PUT /ordering`
  (Padrão do site) + `GET /profile` (lista global, só seleção) + `GET/PUT /profile/{id}/ordering`;
  o CRUD de perfil saiu (está em `/api/profile`). Não usa mais `TurSNSiteFacetProfileService` (001).
- `api/profile/TurProfileAPI.java` — `PUT /{id}/default` delega ao
  `TurProfileFacetOrderingService.setDefault` (DT-6).
- testes reescritos/ajustados: `TurSNSiteFacetedFieldAPITest` (4), `TurProfileAPITest` (2).

**Nota:** o serviço 001 (`TurSNSiteFacetProfileService`) segue existindo (usado por
`TurSNSiteImport`/`TurSNSiteFacetProfileOnStartup`) até a Fase 6.

## 4d. Fase 4 — CONCLUÍDA ✅ (gate: 44 testes verdes)
**Alterados (backend):**
- `sn/TurSNFieldProcess.java` — `resolveProfilePositions`/`positionsByFacetId` migrados para o
  modelo global: resolve `TurProfile` (nome→id via `TurProfileRepository`) e lê a config de
  (perfil, site) via `TurProfileFacetOrderingRepository.findByTurProfileAndTurSNSite`; fallback à
  config do **Padrão** do site → mapa vazio (⇒ ordena pelo `facetPosition` legado da definição).
  Construtor trocou `TurSNSiteFacetProfileRepository` por `TurProfileRepository` +
  `TurProfileFacetOrderingRepository`.
- `TurSolrResultProcessor.processSEResultsFacet` **inalterado**; `profile` (busca),
  turing-commons e SDK sem mudança de forma — só a resolução no backend mudou.
- testes: `TurSNFieldProcessProfileTest` reescrito (4); `TurSNFieldProcessTest` (11) ajustado ao
  novo construtor.

## 4e. Fase 5 — CONCLUÍDA ✅ (gate: type-check do app EXIT 0)
**Gerenciamento (novos):**
- `models/profile/profile.model.ts` (`TurProfile`), `services/profile/profile.service.ts` (`/api/profile`).
- `app/console/profile/profile.root.page.tsx` (`<Page>`), `profile.list.page.tsx` (lista + CRUD via
  diálogos; padrão protegido: nome bloqueado, sem excluir/definir-padrão; badge "Padrão").
- `app/routes/profile.routes.tsx` + export no `app/routes/index.ts` + `{ProfileRoutes}` no `App.tsx`.
- `routes.const.ts`: `PROFILE_ROOT`/`PROFILE_INSTANCE` (`/admin/profile/instance`).
- `components/app-sidebar.tsx`: item "Perfis" (ícone `IconId`, `ITEM_PRIVILEGE.profiles=PROFILE_VIEW`,
  filtro em `filteredManagement`).
- i18n: `locales/{en,pt}/profile.json` (namespace `profile`, registrado no `i18n/index.ts`);
  `home.features.profiles` (en/pt).

**Facetas (alterados):**
- `app/console/sn/facet/sn.site.facet.list.page.tsx` reescrito: só Select de perfis **globais** +
  drag-and-drop; **sem** CRUD de perfil; ação "Gerenciar perfis" (`sn.facets.profile.manage`) →
  `/admin/profile/instance`. Usa `TurProfile`.
- `services/sn/sn.facet.profile.service.ts` enxugado (`query`/`queryOrdering`/`saveOrdering`).
- `models/sn/sn-site-facet-profile.model.ts` (do 001) ficou órfão → **removido na Fase 6**.

## 4f. Fase 6 — CONCLUÍDA ✅ (com ressalva do IT)
- **Gate**: 81 testes de unidade verdes (`-am`) + `tsc` do frontend EXIT 0.
- **Limpeza feita**: model TS órfão removido; `agents.md` + gotcha `-am`/commons; spec 001 marcado
  "a ser substituído" (README).
- **IT end-to-end**: BLOQUEADO pelo Hazelcast (`@SpringBootTest` loopback) — pendente p/ ambiente.
- **Drop do 001**: ADIADO (Q4, rollback 1 release). Futuro: `v2026.2.7.10.yaml` (drop
  `sn_site_facet_profile*`) + remover entidades/repos/`TurSNSiteFacetProfileService`/
  `TurSNSiteFacetProfileOnStartup`/testes do 001. Até lá, ambos os OnStartup rodam (001 semeia
  per-site; 002 migra p/ global — order-independente).

## 4g. Ajuste pós-Fase 6 — parâmetro de busca renomeado `facetProfile` → `profile`
A pedido (URL/filtro mais genérico). Trocado o **parâmetro de busca** (não os nomes de classe
`TurSNSiteFacetProfile*` do 001):
- turing-commons: `TurSNParamType.PROFILE="profile"`; `TurSNSearchParams.profile`,
  `TurSEParameters.profile` (+ get/set/overrideFromPost), `TurSNSitePostParamsBean.profile`.
- turing-app: `TurSolrResultProcessor` usa `getProfile()`.
- SDK `@viglet/turing-react-sdk`: `SearchParams.profile` + `buildQueryString`("profile") +
  round-trip de URL; **dist rebuildado**.
- Frontend: `search-header.tsx` (`store.params.profile`, `updateParams({profile})`), i18n
  `search.profile*` (rótulo genérico "Perfil"/"Profile").
- Gate: 60 testes verdes (inclui `TurSNSiteSearchAPITest`/V2) + SDK build + app `tsc` = EXIT 0.
- URL agora: `.../search?q=*&profile=Cursos%20EE`.

## 4h. Fase 7 — Config de campo por perfil ("Campos") — PROMOVIDA PARA O SPEC 003
> **Movido:** 7.2–7.4 agora vivem em [`../003-config-campos-por-perfil/`](../003-config-campos-por-perfil/)
> (spec dedicado, criado em 2026-07-17). A 7.1 (modelo) permanece aqui e é **reaproveitada** pelo 003.
2º tipo de config anexado ao `TurProfile` (FR-19/FR-22..25). Escopo travado (AskUserQuestion):
**config de faceta por campo** (facet on/off, facetName, facetSort, facetType, facetItemType,
facetRange, secondaryFacet, showAllFacetItems); índice/schema seguem globais. Formalizado
**dentro do 002**.
- **7.1 ✅ Modelo**: `persistence/model/profile/TurProfileFieldConfig.java` (`tur_profile_field_config`,
  unique perfil×campo, overrides **nuláveis** = herda global; enums `@Enumerated(STRING)`),
  `persistence/repository/profile/TurProfileFieldConfigRepository.java`,
  `resources/db/changelog/v2026.2.7.10.yaml` (+ include no master). Gate: compila (`-am`) + Liquibase
  agnóstico OK.
- **7.2 ⏳ Serviço+API**: `TurProfileFieldConfigService` (efetivo=override∪global; save upsert;
  **Padrão→global** como DT-6) + `GET/PUT /api/sn/{snSiteId}/field/profile/{profileId}` + DTO + testes.
- **7.3 ⏳ Runtime**: aplicar config efetivo na montagem de facetas no Solr (facet on/off + sort/type/
  range por perfil). Mapear onde `facet=1`/`facetSort`/… são lidos hoje na busca.
- **7.4 ⏳ Frontend**: seletor de perfil em **Campos** + override das settings de faceta (com
  "herdado"); model/service/i18n.

## 4i. Cache do "profile" (minimizar requests ao banco) — ✅
Os repositórios de profile não tinham cache; o runtime de busca (`TurSNFieldProcess`) fazia 2–4
queries de profile **por busca** (perfil por nome/id/padrão + ordering perfil×site), fora do snapshot.
Implementado (padrão do projeto `@Cacheable`/`@CacheEvict(allEntries=true)`, CacheManager default):
- `TurProfileRepository`: `@Cacheable` em `findByNameIgnoreCase`, `findByDefaultProfileTrue`,
  `findById`; `@CacheEvict` em `save`/`delete`. (Entidade flat, sem LAZY → cache seguro.)
- `TurProfileFacetOrderingRepository`: nova query `@Cacheable`
  `findByTurProfileAndTurSNSiteWithItems` (**`LEFT JOIN FETCH items`** — evita LazyInit no cache hit);
  `@CacheEvict` em `save`/`delete`. Mantido `findByTurProfileAndTurSNSite` (uncached) p/ console/save.
- `TurSNFieldProcess.resolveProfilePositions` usa a query `WithItems` (ambos os ramos: perfil e
  fallback ao padrão). Após o 1º hit por (site, perfil), **zero** queries de profile na busca.
- Escritas gravam via `save()`/`delete()` singulares (sem `saveAll` nesses repos) → eviction cobre
  create/update/delete/setDefault/saveOrdering. Gate: 45 testes verdes.
- Opção extra (cache do mapa resolvido por (site, nomePerfil)) não feita — repo-level já zera as
  queries; fica como melhoria futura se necessário.
- **Fase 7.3** (config de campo no runtime) deve seguir o mesmo padrão (`@Cacheable` no
  `TurProfileFieldConfigRepository`).

## 4j. Autorização dos endpoints de console (segurança) — ✅ (escopo do profile)
O cache não é falha (é server-side; a autorização do controller roda antes). Mas havia um gap real:
`TurSNSiteFacetedFieldAPI` (que lista os perfis globais no seletor de Facetas) **não tinha
`@Secured`** — só `authenticated()`. Corrigido:
- `TurSNSiteFacetedFieldAPI`: `@Secured({"ROLE_ADMIN","SN_VIEW"})` nos GET, `SN_EDIT` nos PUT.
- `TurSNSiteFieldExtAPI` (Campos, `/field/ext`): mesmo padrão (SN_VIEW leitura, SN_EDIT escrita).
- `TurProfileAPI` (`/api/profile`) já era `PROFILE_*`; busca pública (`/search?profile=`) só **aplica**
  o perfil (ordenação/exibição), não lista perfis nem filtra dados → ok.
- `@EnableMethodSecurity(securedEnabled=true)` confirmado ativo. Gate: 57 testes verdes.
- **Fase 7.2**: os endpoints novos `/api/sn/{site}/field/profile/{profileId}` devem nascer com
  `@Secured` (SN_VIEW/SN_EDIT).

> **Gap mais amplo (pré-existente, NÃO corrigido aqui):** só 3 de ~18 controllers do console SN têm
> `@Secured` (TurSNSiteAPI + os 2 acima). Os outros 15 (CustomFacet, SearchRule, CustomSort, Locale,
> Spotlight, Metrics, MergeProviders, FieldCheck/Repair, Field, etc.) dependem só de `authenticated()`.
> **Não** dá para aplicar em bloco — alguns são públicos de propósito (`/api/sn/names` está no
> permitAll). Recomenda-se uma passada dedicada de hardening, controller a controller.

## 4k. Ação de Regra de Busca `SET_PROFILE` — ✅ (FR-26)
Integra o perfil ao motor de Regras de Busca: quando a regra casa, a ação define o perfil da busca.
- **Backend**: `TurSNSiteSearchRuleActionTypeEnum.SET_PROFILE` + `TurSNSearchRuleEvaluator.applyAction`
  → `case SET_PROFILE -> params.setProfile(value)` (value = nome do perfil; resolvido pelo
  `TurSNFieldProcess` como o `?profile=`). Regra **sobrescreve** o profile da requisição (igual
  `SET_SORT`). Teste: `TurSNSearchRuleEvaluatorTest` (2 — casa/não casa).
- **Frontend**: `sn-site-search-rule.model.ts` (union +SET_PROFILE); `sn.site.search.rule.actions.tsx`
  (ACTION_TYPES + single-use + seletor de perfil no value); `sn.site.search.rule.form.tsx` busca os
  perfis (`TurSNFacetProfileService.query(siteId)`) e passa via prop `profiles`; i18n
  `sn.searchRule.actionTypeValues.SET_PROFILE` + `sn.searchRule.selectProfile` (en/pt).
- Gate: 16 testes backend verdes + `tsc` do app EXIT 0.

## 4l. Resiliência de busca a facet inexistente no Solr — ✅ (achado durante testes)
Durante os testes (indexação parcial), o acesso a conteúdos quebrava com dois erros encadeados —
**não** causados pelo trabalho de perfis (a lista de `facet.field` vem de `getEnabledFacets`, que não
tocamos), mas expostos por um core Solr indexado só parcialmente:
- **Backend (Solr)**: um único `facet.field` para um campo **inexistente no core** (`catedras`,
  `temas-secundarios`, `base-de-dados0`, …) fazia o Solr **rejeitar a query inteira** (documentos
  incluídos). O `executeSolrQuery` engolia a `SolrException` e devolvia vazio → resposta sem `widget`.
- **Frontend**: com a resposta vazia, `search-content.tsx` quebrava em `data.widget.locales`
  (tela vermelha "Cannot read properties of undefined (reading 'locales')").

**Correções:**
- `solr/TurSolr.java` — `executeSolrQuery` agora, ao receber `undefined field`, **remove só aquele
  `facet.field` e reenvia a query** (loop **limitado pelo nº de facetas**; casa o nome mesmo com
  local-params `{!ex=tag}campo`). Se o campo indefinido não for uma faceta removível (veio de
  `q`/`fq`/`hl`), mantém o comportamento antigo (loga WARN + retorna vazio, sem loop). Regex
  `UNDEFINED_FIELD` + helpers `removeUndefinedFacetField`/`parseUndefinedField`/`bareFacetField`.
- `frontend/apps/turing-app/src/search/components/search-content.tsx` — guard: se `data.widget`
  vier ausente, cai na `SearchError` em vez de white-screen (mesmo padrão do guard de `queryContext`
  em `resolve.ts`). Mudança **no app** (não no SDK) → **não** precisa rebuildar o SDK.
- **Gate**: `TurSolrTest` **40 verdes** (+2 novos: strip-and-retry de 2 facets inválidos preservando
  a válida; e "não é faceta → 1 tentativa e desiste").

> **Remediação de dados (para as facetas de fato aparecerem):** o retry faz o conteúdo carregar, mas
> `catedras`/`temas-secundarios`/`base-de-dados0` só terão valores quando existirem no core — reindexar
> conteúdo que os carregue, **ou** desabilitar essas facetas no site, **ou** rodar Field Check/Repair
> (`POST /api/sn/{siteId}/field/repair`). No "projeto original" não dava o erro porque o core estava
> totalmente indexado.

## 5. Status geral
Spec 002 **implementado (Fases 1–6)**, backend + frontend, na branch `feature/turing-generic-profiles`.
**Nada commitado ainda** (aguardando "pode commitar"). Pendências não-bloqueantes: IT (Hazelcast),
remoção dos artefatos do 001, `facetOrderingCount` na UI — ver `tasks.md`.

## 6. Gotchas / gates (deste projeto)
- **Build multi-módulo**: rodar com **`-am`** (rebuilda `turing-commons`, que tem `getFacetProfile`);
  sem `-am` + `clean` pega commons defasado do `.m2` → `cannot find symbol getFacetProfile`.
  Para `-Dtest=...` em multi-módulo: adicionar `-Dsurefire.failIfNoSpecifiedTests=false`.
- Gate backend: `.\mvnw.cmd -pl turing-app -am -Dskip.npm=true -Djacoco.skip=true -Dtest=... test`.
- H2 dev persistente + Hazelcast em `@SpringBootTest` (loopback) — usar testes de unidade (Mockito).
- Liquibase: `boolean` minúsculo; ids `VARCHAR(255)`; changelog novo idempotente (preConditions).
- **Facet.field inexistente no core derruba a query inteira** (não só a faceta) — por isso o
  strip-and-retry no `TurSolr` (§4l). Ao ver `undefined field: "X"` nos logs durante testes, a causa
  é campo não criado no core (índice parcial), **não** o perfil. Remediar com reindex/Field Repair.
- Ainda **não commitado** (aguardando "pode commitar").
