# Plano de Implementação — Perfis de Ordenação de Facetas

Plano técnico em **fases**. Cada fase é entregável e verificável de forma independente.
Referências: `spec.md`, `research.md` (Fase 0 concluída), `data-model.md`,
`contracts/facet-profile-api.md`.

> **Gate de cada fase**: backend `mvn test -pl turing-app -Dskip.npm=true` verde; frontend
> compila (`pnpm exec tsc -b --noEmit` ou diagnostics da IDE). Não avançar com gate vermelho.

> **Escopo (Fase 0)**: resolução por perfil **apenas no Solr**; **sem alterações nos SDKs**
> (Java/JS); `facetPosition` legado é mantido como **espelho do perfil padrão** (research DT-6).

---

## Arquitetura da solução (visão geral)

1. **Posição sai da faceta e vai para a junção perfil × faceta** (`TurSNSiteFacetProfileItem`).
2. **A "costura" de runtime não muda de lugar**: continua em
   `TurSolrResultProcessor.processSEResultsFacet`; o `Map<nome→posição>` passa a ser resolvido a
   partir do **perfil** (fallback ao padrão). Confirmado fora do snapshot (research Q1).
3. **`facetProfile`** é propagado como o parâmetro `sort` (research Q2), incluindo beans em
   **`turing-commons`**.
4. **`facetPosition` legado** é sincronizado com o perfil padrão (DT-6) → ES/Lucene seguem o "Geral".
5. **Frontend** ganha seletor de perfil + CRUD; a lista drag-and-drop opera por perfil.
6. **Migração + bootstrap** preservam a ordenação atual como perfil "Geral".

---

## Fase 0 — Pesquisa e confirmação ✅ CONCLUÍDA

Ver `research.md §3-§4`. Resumo: ordem resolvida fora do snapshot (Q1); cadeia de propagação de
`facetProfile` mapeada espelhando `sort`, com beans em `turing-commons` (Q2); 3 backends leem
`facetPosition`, mas só Solr entra no escopo (Q3); import não quebra (Q4); seed por DDL + bootstrap
em runtime (Q5). Escopo: Apenas Solr; sem SDKs.

---

## Fase 1 — Modelo de dados e persistência (backend)

**Entrega**: tabelas, entidades, repositórios e migração; perfil "Geral" populado.

- Criar entidades (`@since 2026.2.7`, LAZY):
  - `TurSNSiteFacetProfile` (`sn_site_facet_profile`) — **sem** `@EntityListeners` de eviction
    (research Q1).
  - `TurSNSiteFacetProfileItem` (`sn_site_facet_profile_item`).
- Alterar entidades existentes:
  - `TurSNSite`: `Set<TurSNSiteFacetProfile> facetProfiles` (OneToMany, cascade/orphan).
  - `TurSNSiteFieldExt`/`TurSNSiteCustomFacet`: `OneToMany` para os itens (orphanRemoval);
    `@Deprecated` no Javadoc do `facetPosition` legado (agora espelho do padrão — DT-6).
- Repositórios Spring Data (ver `data-model.md §4`).
- Liquibase: novo `v2026.2.7.<sub>.yaml` (**apenas DDL** idempotente) incluído no master.
- Backfill: `TurSNSiteFacetProfileOnStartup` (runtime, idempotente) cria "Geral" e popula itens a
  partir do `facetPosition` legado (FR-05/D4).
- Testes: entidades/repos (unit) + IT mínimo do bootstrap (`AbstractTuringSpringIT`).

**Gate**: compila + testes de persistência verdes; subir app cria "Geral" em sites existentes.

---

## Fase 2 — API de perfis e ordenação por perfil (backend)

**Entrega**: CRUD de perfis + ordenação por perfil + retrocompat + sincronização do padrão.

- DTO `TurSNSiteFacetProfileDto` (record).
- Serviço `TurSNSiteFacetProfileService`:
  - CRUD; invariante **1 padrão por site** (FR-04); bloquear exclusão do padrão (FR-03); promover
    padrão; criar **clonando o padrão**.
  - `getOrdering(profileId)` (FR-06/FR-08); `saveOrdering(profileId, dtos)` (FR-07, upsert).
  - **DT-6**: ao salvar a ordenação do **perfil padrão** ou **promover** um perfil a padrão,
    sincronizar o `facetPosition` legado das facetas (campo + cache `@CacheEvict`).
- Estender `TurSNSiteFacetedFieldAPI` com `/profile` e `/profile/{profileId}/ordering`.
- Adaptar legados `GET /facet` e `PUT /facet/ordering` para operar sobre o **padrão** (FR-16).
- Testes: serviço + API (CRUD, 409 padrão/duplicado, retrocompat, FR-08, sincronização DT-6).

**Gate**: testes de API verdes; retrocompat e sincronização comprovadas por teste.

---

## Fase 3 — Resolução por perfil no runtime de busca (Solr)

**Entrega**: `facetProfile` aplicado à ordem das facetas no Solr, com fallback.

- Novo método `TurSNFieldProcess.getTurSNSiteFacetOrdering(snSiteId, profileNameOrId)` →
  resolve perfil (nome/id) com fallback ao padrão.
- Propagar `facetProfile` espelhando `sort` (research Q2), nesta ordem:
  - **`turing-commons`**: `TurSNParamType.FACET_PROFILE`, `TurSNSearchParams.facetProfile`,
    `TurSEParameters.facetProfile` (constructor + `overrideFromPost`),
    `TurSNSitePostParamsBean.facetProfile`.
  - **`turing-app`**: `TurSNSiteSearchAPI` (`@RequestParam`), `TurSolrQueryContext.facetProfile`,
    `TurSolr.retrieveSolrFromSN` (popular o context), `TurSolrResultProcessor.processSEResultsFacet`
    (usar `queryContext.getFacetProfile()` na origem do `positionByName`).
- ES/Lucene: **sem mudança** (leem `facetPosition` legado = padrão por DT-6).
- Testes: ordem por perfil (FR-10), fallback ausente/inexistente (FR-11), nome com espaço/encoding.

**Gate**: testes de runtime verdes; busca Solr com/sem `facetProfile` comprovada.

---

## Fase 4 — Frontend (Console)

**Entrega**: seletor de perfil + CRUD + drag-and-drop por perfil, i18n.

- Models: `models/sn/sn-site-facet-profile.model.ts`.
- Service: `services/sn/sn.facet.profile.service.ts` (query/create/update/remove/setDefault/
  queryOrdering(profileId)/saveOrdering(profileId, items)).
- React Query v5: `api/queries/facet-profile.queries.ts` + chaves em `api/queries/keys.ts`.
- UI em `app/console/sn/facet/sn.site.facet.list.page.tsx`: `<Select>` (shadcn) de perfis +
  ações criar/renomear/excluir/definir padrão (dialogs + `GradientButton`).
- `SNSiteFacetDraggableList` recebe `profileId` e salva via `saveOrdering(profileId, ...)`.
- i18n: chaves em `i18n/locales/{en,pt}/forms.json` (e/ou `sn.json`).

**Gate**: type-check/compile do frontend ok; fluxo manual de criar/reordenar por perfil funciona.

---

## Fase 5 — Integração, validação e documentação

- IT end-to-end (`AbstractTuringSpringIT`) cobrindo `quickstart.md` (incl. fallback e DT-6).
- Suíte completa backend + build frontend.
- Validar export/import (research Q4) e que ES/Lucene seguem o padrão (DT-6).
- Atualizar docs (`turing/docs` / README de facetas) se aplicável.

**Gate**: todos os critérios de aceite (`spec.md §8`) verdes.

---

## Arquivos impactados (mapa rápido)

**Backend — `turing-app` (novos)**
- `persistence/model/sn/field/TurSNSiteFacetProfile.java`, `TurSNSiteFacetProfileItem.java`
- `persistence/repository/sn/field/TurSNSiteFacetProfileRepository.java`,
  `TurSNSiteFacetProfileItemRepository.java`
- `sn/facet/TurSNSiteFacetProfileService.java`, `onstartup/sn/TurSNSiteFacetProfileOnStartup.java`
- `api/sn/bean/TurSNSiteFacetProfileDto.java`
- `resources/db/changelog/v2026.2.7.<sub>.yaml`

**Backend — `turing-app` (alterados)**
- `persistence/model/sn/TurSNSite.java`, `.../field/TurSNSiteFieldExt.java`,
  `.../field/TurSNSiteCustomFacet.java`
- `api/sn/console/TurSNSiteFacetedFieldAPI.java`, `sn/TurSNFieldProcess.java`
- `solr/TurSolrResultProcessor.java`, `solr/TurSolrQueryContext.java`, `solr/TurSolr.java`
- `api/sn/search/TurSNSiteSearchAPI.java`, `db.changelog-master.yaml`

**Backend — `turing-commons` (alterados)** ⚠️
- `.../sn/search/TurSNParamType.java`, `.../sn/bean/TurSNSearchParams.java`,
  `.../se/TurSEParameters.java`, `.../sn/bean/TurSNSitePostParamsBean.java`

**Frontend (novos)**
- `models/sn/sn-site-facet-profile.model.ts`, `services/sn/sn.facet.profile.service.ts`,
  `api/queries/facet-profile.queries.ts`

**Frontend (alterados)**
- `app/console/sn/facet/sn.site.facet.list.page.tsx`,
  `components/sn/facet/sn.site.facet.draggable.list.tsx`, `api/queries/keys.ts`,
  `i18n/locales/{en,pt}/forms.json`

**Fora de escopo**: `TurElasticsearch`, `TurLucene*` (seguem o padrão via DT-6);
`turing-java-sdk` `TurSNQuery`; `react-sdk` `SearchParams`.

## Riscos e mitigações

| Risco | Mitigação |
|-------|-----------|
| ES/Lucene divergirem do "Geral" | DT-6: sincronizar `facetPosition` legado ao salvar/promover o padrão |
| Esquecer um bean em `turing-commons` | Checklist da Fase 3 (Q2) com os 8 pontos |
| Portabilidade do seed UUID no Liquibase | DDL no Liquibase + backfill em runtime (Q5) |
| Quebra de retrocompat dos endpoints legados | Teste dedicado + delegar ao serviço de perfis |
| Eviction de snapshot indevido por perfil | Entidades de perfil sem `@EntityListeners` (Q1) |
| EBUSY no build frontend (Windows) | Diagnostics da IDE; backend é o gate primário |
