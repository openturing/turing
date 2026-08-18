# Tasks — Configuração de Campos por Perfil

Marcar `[x]` ao concluir. Reaproveita a Fase 7.1 do 002 (modelo já pronto).

## Fase 0 — Pesquisa ✅
- [x] T0.1 Decisões D1–D6 (AskUserQuestion 2026-07-17): escopo=só faceta; Padrão→global; herança null.
- [x] T0.2 Mapear integração (Campos, runtime de facetas, snapshot) — `research.md`.
- [x] T0.3 Confirmar reuso de `TurProfileFieldConfig`/repo/`v2026.2.7.10` (Fase 7.1 do 002).

## Herdado do 002 (Fase 7.1) — pronto ✅
- [x] TH.1 `TurProfileFieldConfig` (`tur_profile_field_config`, unique perfil×campo, overrides nuláveis).
- [x] TH.2 `TurProfileFieldConfigRepository` (findBy... + lote `...FieldExtIn`).
- [x] TH.3 Liquibase `v2026.2.7.10.yaml` + include no master.

## Fase 1 — Serviço + API (modo perfil) ✅ (11 testes verdes: 8 serviço + 3 API)
- [x] T1.1 `TurProfileFieldConfigService` (`profile/`): `getEffectiveConfigs`, `saveOverride` (Padrão→global
      com recálculo de `facetPosition`; upsert; limpar tudo → remove linha), `clearOverride` (Padrão → 409),
      `resolveEffective(TurProfile, fields)` → `Map<fieldId, FacetConfig>` (reutilizável no runtime).
- [x] T1.2 DTOs (`api/sn/bean/`): `TurSNSiteFieldProfileConfigDto` (nested `FacetConfig`/`OverrideFlags`;
      effective/overridden/global) + `TurProfileFieldConfigRequest` (record **sem primitivos**, `hasAnyOverride()`).
- [x] T1.3 API `TurSNSiteFieldProfileAPI` (`/api/sn/{snSiteId}/field/profile/{profileId}`) GET/PUT `/{fieldExtId}`
      /DELETE `/{fieldExtId}`; `@Secured` SN_VIEW (GET) / SN_EDIT (PUT/DELETE) — FR-10.
- [x] T1.4 Cache: `@Cacheable(findByProfileId)` **keyed by profileId String** + `@CacheEvict(allEntries)` em
      save/delete no `TurProfileFieldConfigRepository` (NFR-01; chave por entidade não daria hit).
- [x] T1.5 Testes: `TurProfileFieldConfigServiceTest` (8) + `TurSNSiteFieldProfileAPITest` (3). Compila `-pl turing-app`.

## Fase 2 — Frontend (seletor de perfil em Campos) ✅ (`tsc -p tsconfig.app.json --noEmit` EXIT 0)
- [x] T2.1 `models/profile/profile-field-config.model.ts` (`TurSNSiteFieldProfileConfig` + `TurFacetConfig`
      + `TurFacetOverrideFlags` + `TurProfileFieldConfigRequest`) + `services/sn/sn.field.profile.service.ts`
      (`query`/`saveOverride`/`clearOverride`).
- [x] T2.2 Seletor de Perfil em `sn.site.field.list.page.tsx` (pré-seleciona o Padrão; lista de perfis via
      `TurSNFacetProfileService.query` — mesma lista global). **Padrão → grid global existente** (retrocompat,
      schema editável); **não-padrão → tabela por perfil** (novo componente).
- [x] T2.3 **Grid único ciente de perfil** (ajuste pós-feedback): o `SNSiteFieldGridList` é o **mesmo**
      para Padrão e perfis não-padrão (idêntico visualmente — Campo/Tipo/MúltiplosValores/MLT/Faceta/
      Destaque/Habilitado/Ações). Sob perfil **não-padrão**: só o toggle **Faceta** é editável (grava
      override via `/field/profile`, refletindo o `effective.facet`); os demais toggles ficam **visíveis
      mas desabilitados** (schema é global — FR-05). Tabela custom `field.profile.config.list.tsx`
      **removida**. (Facet on/off é a única setting por-perfil na UI por ora; sort/type/range ficam para
      o editor de campo, ainda global.)
- [x] T2.4 i18n en/pt (`sn.fields.profile.*`). Gate: `tsc` do app EXIT 0.

## Fase 3 — Runtime (Solr) ✅ (`-am`: TurSolrQueryBuilderTest 73 + TurSolrTest 40 + serviço 8 verdes)
- [x] T3.1 `TurSolrQueryBuilder.prepareQueryFacet` → novo `resolveEnabledFacets(site, params)`: resolve o
      perfil aplicado (`turSEParameters.getProfile()` → `TurProfileRepository.findByNameIgnoreCase`) e, para
      perfil não-padrão, aplica o config efetivo (`TurProfileFieldConfigService.resolveEffective`) sobre
      **cópias** dos `TurSNSiteFieldExt` (`toBuilder`, sem mutar o original/cache) antes de montar as facetas.
- [x] T3.2 O `facet` **efetivo** governa a inclusão (candidato = efetivo.facet==1 || tem custom facet);
      sort/type/itemType/range/secondary/showAll vêm do efetivo. Perfil em branco/inexistente **ou** Padrão →
      caminho global inalterado `getEnabledFacets` (FR-06/FR-07; sem query extra no hot path sem perfil).
- [x] T3.3 Snapshot (NFR-02): overlay aplicado **após** `getEnabledFields` (que lê o snapshot); a lista
      sobreposta **não** é cacheada no snapshot global (é por-request, sobre cópias).
- [x] T3.4 Resolução do perfil por nome + fallback consistente com a ordenação (002) — mesmo
      `findByNameIgnoreCase` (cacheado). `SET_PROFILE` (002 §4k) grava `params.setProfile(value)`, então
      passa pelo mesmo caminho (coberto pelo teste de overlay).
- [x] T3.5 Testes: `testPrepareQueryFacetAppliesProfileFacetConfig` (liga 'beta'/desliga 'alpha' por perfil)
      + `testPrepareQueryFacetWithoutProfileUsesGlobalFacets` (sem perfil → global; serviço não é chamado).
      Construtores de `TurSolr`/`TurSolrQueryBuilder` (+2 deps) atualizados nos testes existentes. Gate `-am` verde.

## Interlink Campos ↔ Facetas (ajuste pós-teste) ✅ (5+11+7+12 testes verdes)
- [x] TI.1 A lista de **Facetas** (ordenação por perfil) agora respeita o config de campo do **mesmo
      perfil**: desativar uma faceta de um campo no perfil (em Campos) a **remove** de Facetas daquele
      perfil (e ativar faz aparecer), mantendo a ordem. Antes vinha das facetas **globais**.
- [x] TI.2 `TurSNFieldProcess.getTurSNSiteFacetOrdering(snSiteId, TurProfile)` — sobrepõe o config efetivo
      (`resolveEffective`) nos campos (cópias `toBuilder`) antes do `TurSNFacetDefinitionFactory.fromFields`;
      `facet==1` efetivo governa quais defs existem (custom facets seguem independentes). Injetado
      `TurProfileFieldConfigService` no `TurSNFieldProcess`.
- [x] TI.3 `TurProfileFacetOrderingService.buildOrdering(snSiteId, profile, positions)` passa o perfil
      (get/save/default). Construtores de `TurSNFieldProcess` (+1 dep) e stubs `getTurSNSiteFacetOrdering`
      (2-arg) atualizados. Teste novo: `facetDeactivatedForProfileInFieldsIsHiddenFromFacets`.
- [ ] TI.4 *(follow-up, edge)* salvar reordenação de faceta que só existe por override do perfil (campo
      global off, ligado no perfil): `persistOrdering` filtra por `facet==1` global — posição não persiste
      nesse caso raro. Não bloqueia o interlink de exibição.

## Fase 4 — Integração, validação e docs
- [ ] T4.1 Rodar `quickstart.md` (manual).
- [ ] T4.2 Gate abrangente (`-am` + `tsc`); atualizar `HANDOFF.md`, memória do projeto.
- [ ] T4.3 Marcar 7.2–7.4 do 002 como **movidas para o 003** (HANDOFF/tasks do 002).

## Pendências / notas
- IT end-to-end depende do ambiente (Hazelcast em `@SpringBootTest`, mesma limitação dos specs 001/002).
- `facetLocales` por perfil fica fora de escopo (rótulos por locale seguem globais).
