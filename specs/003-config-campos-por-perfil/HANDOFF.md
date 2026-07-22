# HANDOFF — Configuração de Campos por Perfil (spec 003)

> Estado para retomada. Criado: **2026-07-17**. Branch de trabalho prevista:
> **`feature/turing-generic-profiles`** (mesma do 002; 003 continua o mesmo esforço de perfis).

## 1. O quê
Trazer o **seletor de Perfil** (que já existe em **Facetas**) para a aba **Campos**: cada perfil recebe
sua configuração de **faceta por campo** — específica (override) ou **herdada** do global. O runtime de
busca monta as facetas pelo config **efetivo** do perfil aplicado. Ver `README.md`/`spec.md`.

## 2. Decisões travadas (AskUserQuestion 2026-07-17)
- **D1** Escopo por-perfil = **só configs de faceta** (`facet`, `facetName`, `facetSort`, `facetType`,
  `facetItemType`, `facetRange`, `secondaryFacet`, `showAllFacetItems`). Schema/índice seguem **globais**
  (um único core Solr por locale — inviável por-perfil).
- **D2/D3** Perfil **Padrão** grava no **campo global** (`TurSNSiteFieldExt`) — espelho DT-6; perfis
  não-padrão gravam **override** (`TurProfileFieldConfig`). `null` = herda global.

## 3. Relação com o 002
- 003 **absorve e conclui a Fase 7** do 002. O **modelo já existe** (Fase 7.1 do 002): entidade
  `TurProfileFieldConfig`, `TurProfileFieldConfigRepository`, Liquibase `v2026.2.7.10` (no master).
- As tarefas 7.2–7.4 do 002 passam a ser as **Fases 1–3 deste 003**.

## 4. O que falta implementar (por fase — ver `plan.md`/`tasks.md`)
- **Fase 1 (backend)**: ✅ **CONCLUÍDA** (11 testes verdes). Entregue: `profile/TurProfileFieldConfigService`
  (`getEffectiveConfigs`, `saveOverride`, `clearOverride`, `resolveEffective`), DTOs em `api/sn/bean/`
  (`TurSNSiteFieldProfileConfigDto` com `FacetConfig`/`OverrideFlags`; `TurProfileFieldConfigRequest`
  sem primitivos), `api/sn/console/TurSNSiteFieldProfileAPI` (`@Secured` SN_VIEW/SN_EDIT), cache
  `findByProfileId` (keyed by String) + evict no `TurProfileFieldConfigRepository`. Testes:
  `TurProfileFieldConfigServiceTest` (8) + `TurSNSiteFieldProfileAPITest` (3).
- **Fase 2 (frontend)**: ✅ **CONCLUÍDA** (`tsc` EXIT 0). Seletor de Perfil em
  `sn.site.field.list.page.tsx`. **UI ajustada pós-feedback:** telas de perfil não-padrão são o **mesmo
  grid** do Padrão (visualmente idênticas); sob perfil não-padrão só o toggle **Faceta** é editável
  (grava override via `/field/profile`, mostra `effective.facet`), os outros toggles ficam **visíveis e
  desabilitados** (schema global). `SNSiteFieldGridList` ganhou props `profileId`/`isDefaultProfile`.
  Tabela custom `field.profile.config.list.tsx` **removida**. Model `profile-field-config.model.ts` +
  service `sn.field.profile.service.ts` + i18n `sn.fields.profile.*`. Lista de perfis via
  `TurSNFacetProfileService.query`.
- **Fase 3 (runtime)**: ✅ **CONCLUÍDA** (`-am`: TurSolrQueryBuilderTest 73 + TurSolrTest 40 + serviço 8).
  `TurSolrQueryBuilder.resolveEnabledFacets` aplica o config efetivo (via `resolveEffective`) sobre cópias
  `toBuilder()` dos campos quando um perfil não-padrão é resolvido de `turSEParameters.getProfile()`; o
  `facet` efetivo governa a inclusão; perfil em branco/inexistente/Padrão → `getEnabledFacets` (global).
  `TurSolr` passou a injetar `TurProfileRepository` + `TurProfileFieldConfigService` e repassá-los ao builder.
- **Fase 4**: quickstart + gate (`-am` + `tsc`) + docs.

## 5. Pontos de integração (de `research.md`)
- Runtime: `TurSolr.retrieveSolrFromSN` → `TurSolrQueryBuilder.prepareQueryFacet(...turSEParameters)`;
  `getEnabledFacets` (usa `facet==1`/snapshot) e `setFacetFields` (lê `facetSort/type/itemType/range` do
  `TurSNSiteFieldExt`). Aplicar overlay do perfil **antes** desses.
- Perfil aplicado já resolvido no runtime (002): `turSEParameters.getProfile()` (inclui `SET_PROFILE`).
- Frontend Campos: `app/console/sn/fields/sn.site.field.list.page.tsx` (`TurSNFieldService`,
  `SNSiteFieldGridList`). Seletor a copiar de Facetas (`TurSNFacetProfileService.query(siteId)`).
- Modo global (Padrão) reusa `TurSNSiteFieldExtAPI` (`/field/ext`, já `@Secured`).

## 6. Gotchas / gates (deste projeto)
- Build com **`-am`** (rebuilda `turing-commons`); para `-Dtest=...` multi-módulo, adicionar
  `-Dsurefire.failIfNoSpecifiedTests=false`.
- Record de request **sem primitivos** (Jackson 3 `FAIL_ON_NULL_FOR_PRIMITIVES`).
- Novos arquivos Java: `@since 2026.2.7` (versão atual do `pom.xml`).
- Cache seguro pois `TurProfileFieldConfig` é **flat** (sem coleção LAZY navegada) — `@Cacheable` no
  finder simples + `@CacheEvict(allEntries=true)` na escrita.
- **NFR-02 (snapshot)**: não cachear a lista já sobreposta pelo perfil no snapshot global (vazaria entre
  perfis) — overlay é por (perfil, site), com cache próprio no `TurProfileFieldConfigRepository`.

## 6b. Interlink Campos ↔ Facetas (ajuste pós-teste) ✅
Bug reportado: desativar uma faceta de um campo num perfil (em "Campos") **não** a removia de "Facetas"
daquele perfil — a lista de Facetas vinha das facetas **globais**. Corrigido tornando a montagem dos defs
ciente do perfil:
- `TurSNFieldProcess.getTurSNSiteFacetOrdering(snSiteId, TurProfile)`: sobrepõe o config efetivo do perfil
  (`TurProfileFieldConfigService.resolveEffective`) em cópias `toBuilder` dos campos **antes** do
  `TurSNFacetDefinitionFactory.fromFields`. Assim `facet` efetivo governa quais facetas aparecem em Facetas
  (desligar remove; ligar adiciona), e a ordem é mantida. Custom facets seguem independentes.
- `TurProfileFacetOrderingService.buildOrdering` passa o perfil (get/save/default). Injetado
  `TurProfileFieldConfigService` no `TurSNFieldProcess`.
- Gate: `TurSNFieldProcessProfileTest` 5 (+ `facetDeactivated...IsHidden...`), `TurSNFieldProcessTest` 11,
  `TurProfileFacetOrderingServiceTest` 7, `TurSNSiteFacetProfileServiceTest` 12 — verdes.
- **Follow-up (edge):** salvar reordenação de faceta que só existe por override do perfil (global off,
  ligada no perfil) — `persistOrdering` filtra por `facet==1` global; posição não persiste nesse caso raro.

## 7. Status
**Fases 1, 2 e 3 concluídas** — backend serviço/API/cache (11 verdes), frontend seletor + tabela por perfil
(`tsc` EXIT 0) e runtime Solr (overlay do config efetivo; TurSolrQueryBuilderTest 73 + TurSolrTest 40 verdes).
Modelo (7.1 do 002) pronto. Falta só a **Fase 4** (validação end-to-end via `quickstart.md` + docs). Nada
novo commitado.
