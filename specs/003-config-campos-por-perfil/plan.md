# Plano — Configuração de Campos por Perfil

Herda o modelo da Fase 7.1 do 002 (`TurProfileFieldConfig` + repo + Liquibase `v2026.2.7.10`). As fases
abaixo **substituem** as antigas 7.2–7.4 do 002 (que ficam apontando para este spec).

## Fase 0 — Pesquisa ✅ (neste spec)
- Decisões D1–D6 travadas (AskUserQuestion). Pontos de integração mapeados (`research.md`).
- Sem perguntas em aberto que bloqueiem.

## Fase 1 — Serviço + API (modo perfil)
- **`profile/TurProfileFieldConfigService`**:
  - `getEffectiveConfigs(snSiteId, profileId)` → lista de DTO efetivo/overridden/global (leitura em lote
    `findByTurProfileAndTurSNSiteFieldExtIn` + join com os `TurSNSiteFieldExt` do site).
  - `saveOverride(snSiteId, profileId, fieldExtId, request)` → **Padrão**: grava subconjunto de faceta no
    `TurSNSiteFieldExt` (reusar/extrair do `TurSNSiteFieldExtAPI.updateFieldExtProperties`, só as colunas
    de faceta); **não-padrão**: upsert `TurProfileFieldConfig` (remove linha se tudo `null`).
  - `clearOverride(snSiteId, profileId, fieldExtId)` → remove a linha (Padrão → 409).
  - `resolveEffective(profile, fieldExts)` → `Map<fieldExtId, EffectiveFacetConfig>` (regra data-model §3),
    **reutilizável pelo runtime** (Fase 3).
- **DTOs**: `TurSNSiteFieldProfileConfigDto` (record de resposta: fieldExtId/name/type/enabled + effective/
  overridden/global) e `TurProfileFieldConfigRequest` (record sem primitivos).
- **API** `api/sn/console/TurSNSiteFieldProfileAPI` (`/api/sn/{snSiteId}/field/profile/{profileId}`) —
  GET/PUT/DELETE (contracts), `@Secured` SN_VIEW/SN_EDIT.
- **Cache**: `@Cacheable`/`@CacheEvict` no `TurProfileFieldConfigRepository` (data-model §4).
- **Testes**: `TurProfileFieldConfigServiceTest` (efetivo=override∪global; Padrão→global; upsert; limpar
  remove linha; fallback) + `TurSNSiteFieldProfileAPITest` (delegação + segurança).
- **Gate**: `-am` verde.

## Fase 2 — Frontend (seletor de perfil em Campos)
- **Model/service**: `models/profile/profile-field-config.model.ts` + estender/criar
  `services/sn/sn.field.profile.service.ts` (`query(siteId, profileId)`, `saveOverride`, `clearOverride`).
- **Página Campos** (`app/console/sn/fields/sn.site.field.list.page.tsx`): adicionar o **seletor de
  Perfil** (copiar UX de Facetas), pré-selecionando o Padrão. Sob o Padrão, editar como hoje (global);
  sob perfil, editar overrides.
- **Form/grid do campo**: nas configs **de faceta** (escopo D1), indicar **herdado vs sobrescrito**
  (badge + valor global esmaecido) e permitir **limpar** override; as demais (schema) ficam read-only
  ("global") quando um perfil não-padrão está selecionado.
- **i18n** en/pt (`sn.fields.profile.*`, reutilizando o tom de `sn.facets.profile.*`).
- **Gate**: `tsc` do app EXIT 0 (SDK não é tocado).

## Fase 3 — Runtime (aplicar config efetivo no Solr)
- **`TurSolrQueryBuilder.prepareQueryFacet`**: resolver o perfil aplicado (`turSEParameters.getProfile()`
  → `TurProfile` via `TurProfileRepository`, fallback ao Padrão) e, via `TurProfileFieldConfigService
  .resolveEffective`, produzir a **lista efetiva** de `TurSNSiteFieldExt` (overlay em memória — research
  §2 abordagem A) antes de `getEnabledFacets`/`setFacetFields`.
  - `facet` efetivo governa inclusão; `facetSort/type/itemType/range/secondaryFacet/showAll` governam a
    montagem.
  - **Sem perfil válido** → lista global (retrocompat; NFR/FR-07).
- **Snapshot (NFR-02)**: overlay aplicado **após** obter os campos do snapshot; **não** cachear a lista
  sobreposta no snapshot global.
- **Consistência com 002**: a resolução do perfil aplicado é a **mesma** usada pela ordenação
  (`TurSNFieldProcess`) — extrair um resolvedor comum se necessário.
- **Testes**: estender os testes de facet-building (perfil liga/desliga faceta; muda sort/type/range;
  fallback ao global; regra `SET_PROFILE`).
- **Gate**: `-am` verde.

## Fase 4 — Integração, validação e docs
- Rodar o `quickstart.md` (manual).
- Gate abrangente (`-am` + `tsc`); atualizar `HANDOFF.md`/`tasks.md` e a memória do projeto.
- Marcar as tarefas 7.2–7.4 do 002 como **movidas para o 003**.

## Ordem sugerida
Fase 1 (backend service/API) → Fase 2 (frontend, já testável no modo edição) → Fase 3 (runtime, fecha o
ciclo busca) → Fase 4 (validação/docs). Fases 1 e 2 entregam valor visível (config por perfil na UI)
mesmo antes do runtime.
