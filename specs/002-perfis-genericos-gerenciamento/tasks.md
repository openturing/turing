# Tasks — Perfis Genéricos em "Gerenciamento"

Tarefas numeradas por fase (ver `plan.md`). Marcar `[x]` ao concluir.

## Fase 0 — Pesquisa ✅
- [x] T0.1 Privilégio: nova categoria `PROFILE_VIEW/CREATE/EDIT/DELETE` (padrão do projeto).
- [x] T0.2 Rota `/admin/profile/instance`; i18n `home.features.profiles.*`; ícone `IconId`.
- [x] T0.3 Consolidação por nome (homônimos entre sites → 1 perfil global + config por site).
- [x] T0.4 Manter tabelas do 001 por 1 release; drop na Fase 6.
- [x] T0.5 `profile`/SDK sem mudança de forma; entidades sem eviction de snapshot.
- [x] T0.6 `HANDOFF.md` criado.

## Fase 1 — Modelo global + migração ✅ (5 testes verdes; changelog agnóstico OK)
- [x] T1.1 Entidade `TurProfile` (`tur_profile`) — sem `@EntityListeners`, LAZY.
- [x] T1.2 Entidade `TurProfileFacetOrdering` (`tur_profile_facet_ordering`, unique perfil+site).
- [x] T1.3 Entidade `TurProfileFacetOrderingItem` (`tur_profile_facet_ordering_item`).
- [x] T1.4 Repositórios `TurProfileRepository`, `TurProfileFacetOrderingRepository`, `...ItemRepository`.
- [x] T1.5 Liquibase `v2026.2.7.9.yaml` (DDL idempotente) + include no master.
- [x] T1.6 `TurProfileMigrationOnStartup` (idempotente): "Padrão" + migração do 001 + fallback legado.
- [x] T1.7 Testes: `TurProfileMigrationOnStartupTest` (5) — ensureDefault(2), migra default, consolida homônimos, fallback legado.

## Fase 2 — Serviço + API de perfis (Gerenciamento) ✅ (33 testes verdes no gate)
- [x] T2.1 `TurProfileDto` (record, `@JsonProperty("default")`) + `TurProfileRequest` (sem primitivos).
- [x] T2.2 `TurProfileService`: CRUD, 1 padrão global, proteção do padrão (renomear/excluir → 409),
      `setDefault`, `ensureDefaultProfile` (list() semeia sob demanda; migrador delega — DRY).
- [x] T2.3 `TurProfileAPI` — base **`/api/profile`** (padrão do console `/api/{feature}`, não `/api/v2`).
- [x] T2.4 Privilégios `PROFILE_VIEW/CREATE/EDIT/DELETE` em `TurPrivilegeOnStartup` (categoria
      PROFILE) + `@Secured({"ROLE_ADMIN","PROFILE_*"})` por endpoint.
- [x] T2.5 Testes: `TurProfileServiceTest` (13) + `TurProfileAPITest` (1 delegação) +
      `TurProfileMigrationOnStartupTest` ajustado (3; ensure* migrou p/ o service test).

## Fase 3 — Config por (perfil, site) (Facetas) ✅ (53 testes verdes no gate)
- [x] T3.1 `TurProfileFacetOrderingService`: get/save por (perfil, site) com upsert; fallback de
      leitura perfil→Padrão→legado; DT-6 ao salvar o Padrão **e** no `setDefault(profileId)`
      (orquestra `TurProfileService.setDefault` + sync do espelho legado em todos os sites com
      config); custom facets via `save()` item-a-item (NFR-03).
- [x] T3.2 `TurSNSiteFacetedFieldAPI` reescrita: legados GET/PUT (Padrão) + `GET /profile` (lista
      global via `TurProfileService`) + `GET/PUT /profile/{id}/ordering`; **sem CRUD** de perfil.
      `TurProfileAPI.profileSetDefault` passou a delegar ao ordering service (DT-6).
- [x] T3.3 Limpeza ao excluir faceta: FK `ON DELETE CASCADE` nas colunas `field_ext_id`/
      `custom_facet_id` de `tur_profile_facet_ordering_item` (Liquibase `.9`, Fase 1).
- [x] T3.4 Testes: `TurProfileFacetOrderingServiceTest` (7 — own config, fallback Padrão, fallback
      legado, DT-6 no save do padrão, não-padrão não toca legado, upsert, DT-6 no setDefault) +
      `TurSNSiteFacetedFieldAPITest` (4, reescrito) + `TurProfileAPITest` (2).

## Fase 4 — Runtime de busca (Solr) ✅ (44 testes verdes no gate)
- [x] T4.1 `TurSNFieldProcess.getTurSNSiteFieldOrdering(siteId, profile)` reescrito:
      `resolveProfilePositions` agora resolve o perfil **global** (`TurProfileRepository`
      nome→id) e lê a config de **(perfil, site)** (`TurProfileFacetOrderingRepository`),
      com fallback à config do **Padrão** do site → mapa vazio (⇒ `facetPosition` legado).
      Deps trocadas: sai `TurSNSiteFacetProfileRepository`, entram
      `TurProfileRepository` + `TurProfileFacetOrderingRepository`.
- [x] T4.2 `TurSolrResultProcessor.processSEResultsFacet` inalterado (consome o mapa resolvido);
      `profile`/turing-commons/SDK sem mudança de forma (só a resolução mudou).
- [x] T4.3 Testes: `TurSNFieldProcessProfileTest` reescrito (4 — config por nome, fallback→Padrão,
      fallback quando ausente, fallback→legado) + `TurSNFieldProcessTest` (11) ajustado ao novo
      construtor.

## Fase 5 — Frontend ✅ (type-check `tsc --noEmit -p tsconfig.app.json` = EXIT 0)
- [x] T5.1 `routes.const` (`PROFILE_ROOT`/`PROFILE_INSTANCE` = `/admin/profile/instance`) +
      `app/routes/profile.routes.tsx` + export no barrel + registro no `App.tsx` (dentro do ConsoleRoot).
- [x] T5.2 `models/profile/profile.model.ts` (`TurProfile`) + `services/profile/profile.service.ts`
      (`/api/profile` CRUD global).
- [x] T5.3 `app/console/profile/profile.root.page.tsx` (`<Page>` header + Outlet) +
      `profile.list.page.tsx` (lista + CRUD via diálogos; padrão com nome bloqueado, sem excluir/
      definir-padrão; badge "Padrão").
- [x] T5.4 Item "Perfis" em `app-sidebar.tsx` (`managementItems`, ícone `IconId`, `ITEM_PRIVILEGE`
      `PROFILE_VIEW` + checagem em `filteredManagement`) + i18n `home.features.profiles.*`.
- [x] T5.5 `sn.site.facet.list.page.tsx` reescrito: Select de perfis **globais** + drag-and-drop;
      **removidos** os diálogos de criar/renomear/excluir; ação "Gerenciar perfis" → Gerenciamento.
- [x] T5.6 `sn.facet.profile.service.ts` enxugado (só `query`/`queryOrdering`/`saveOrdering`; CRUD
      saiu — agora em `TurProfileService`). Usa o model global `TurProfile`.
- [x] T5.7 i18n: novo namespace `profile.json` (en/pt) registrado no `i18n/index.ts`;
      `home.features.profiles` (en/pt); `sn.facets.profile.manage` (en/pt).
- [x] T5.8 Type-check do frontend OK. (SDK não tocado nesta fase.)

## Fase 6 — Integração, validação, limpeza, docs ✅ (com ressalva do IT)
- [~] T6.1 IT end-to-end (`AbstractTuringSpringIT`) do `quickstart.md` — **BLOQUEADO** pelo Hazelcast
      em `@SpringBootTest` (loopback; ambiental, mesma limitação do spec 001 Fase 5). Gate prático:
      81 testes de unidade + type-check do frontend. IT fica pendente para ambiente estável.
- [x] T6.2 Gate abrangente: **81 testes** verdes (profile service/API/ordering/migração + runtime +
      facet API + import + queue + Liquibase agnóstico + 001 mantido) via `-am`; frontend `tsc` EXIT 0.
- [~] T6.3 Drop das tabelas do 001 — **adiado** (Q4: manter 1 release p/ rollback). Criar
      `v2026.2.7.10.yaml` (drop `sn_site_facet_profile*`) + remover entidades/repos/serviço/OnStartup
      do 001 numa release futura, após validação em ambiente.
- [x] T6.4 `agents.md` atualizado (gotcha `-am`/commons); model TS órfão do 001 removido; spec 001
      marcado "a ser substituído pelo 002" (README); `HANDOFF.md` atualizado.

## Fase 7 — Config de campo por perfil ("Campos") — PROMOVIDA PARA O SPEC 003
- [x] T7.1 Modelo: `TurProfileFieldConfig` (`tur_profile_field_config`, unique perfil×campo, overrides
      nuláveis) + `TurProfileFieldConfigRepository` + Liquibase `v2026.2.7.10.yaml` (+ master).
      Gate: compila (`-am`) + changelog agnóstico OK. **(reaproveitado pelo 003)**
- [→] T7.2/7.3/7.4 **movidas** para [`../003-config-campos-por-perfil/`](../003-config-campos-por-perfil/tasks.md)
      (serviço/API, runtime Solr e frontend do seletor de perfil em Campos).

## Correções durante testes (não previstas nas fases) ✅
- [x] TC.1 Resiliência a `facet.field` inexistente no core (NFR-05): `TurSolr.executeSolrQuery`
      remove o facet ofensor e reenvia (loop limitado; casa nome com `{!ex=}`); frontend
      `search-content.tsx` degrada para `SearchError` quando `widget` ausente. Achado com core
      parcialmente indexado (`catedras`/`temas-secundarios`/`base-de-dados0`), **não** regressão do
      perfil. Gate: `TurSolrTest` 40 verdes (+2). Ver `HANDOFF §4l`.

## Pendências para uma release futura (não bloqueiam)
- IT end-to-end quando o Hazelcast/`@SpringBootTest` subir no ambiente.
- Remoção dos artefatos do 001 (T6.3): entidades `TurSNSiteFacetProfile*`, repos,
  `TurSNSiteFacetProfileService`, `TurSNSiteFacetProfileOnStartup`, testes, e o changeset de drop.
  Enquanto isso, o migrador (`TurProfileMigrationOnStartup`) segue lendo o 001 quando presente e,
  na ausência, semeia o Padrão a partir do `facetPosition` legado (order-independente).
- `facetOrderingCount` no `TurProfileDto`/UI, se necessário.
