# Plano de Implementação — Perfis Genéricos em "Gerenciamento"

Plano em **fases** entregáveis/verificáveis. Referências: `spec.md`, `research.md`,
`data-model.md`, `contracts/profile-api.md`.

> **Gate de cada fase**: backend `mvn test -pl turing-app -Dskip.npm=true` verde; frontend compila
> (`tsc --noEmit -p tsconfig.app.json` no `frontend/apps/turing-app`; e rebuild do SDK se tocá-lo).
> Não avançar com gate vermelho.

> **Escopo**: resolução por perfil **apenas no Solr**; `facetPosition` legado = espelho do Padrão
> (DT-6); parâmetro `profile` e SDK **inalterados na forma** (só a resolução no backend muda).

---

## Arquitetura da solução (visão geral)
1. **Perfil vira global e neutro** (`TurProfile`) — CRUD em **Gerenciamento**.
2. **Ordenação de facetas vira config por (perfil × site)** (`TurProfileFacetOrdering` + itens) —
   anexada em **Facetas**.
3. **Runtime**: `profile` resolve o perfil global; ordem lida da config de (perfil, site) com
   *fallback* ao Padrão (por site) → legado.
4. **Migração**: substitui o modelo per-site do spec 001; consolida "Geral" per-site no **Padrão**
   global.
5. **Frontend**: nova tela de Perfis em Gerenciamento; Facetas vira só seleção + ordenação.

---

## Fase 0 — Pesquisa e confirmação
Ver `research.md`. Confirmar: privilégio/rota/ícone do item de sidebar (Q1/Q2), regra de
consolidação de nomes (Q3), manter tabelas do 001 por 1 release (Q4), `profile`/SDK sem mudança
de forma (Q5), sem eviction de snapshot (Q6).

**Gate**: decisões da Fase 0 registradas no `HANDOFF.md`.

---

## Fase 1 — Modelo global + migração (backend)
**Entrega**: entidades globais, repositórios, migração e "Padrão" populado.

- Entidades (`@since 2026.2.<v>`, LAZY, sem `@EntityListeners`): `TurProfile`,
  `TurProfileFacetOrdering`, `TurProfileFacetOrderingItem` (data-model §2–4).
- Repositórios (data-model §5).
- Liquibase `v2026.2.7.9.yaml` (**só DDL** idempotente) incluído no master.
- Migração/bootstrap em runtime `TurProfileMigrationOnStartup` (idempotente):
  cria "Padrão"; migra perfis/itens do 001 (consolidação por nome); ou popula do `facetPosition`
  legado (data-model §6).
- Testes: entidades/repos + IT do bootstrap/migração.

**Gate**: compila; subir app cria "Padrão" global e migra dados existentes.

---

## Fase 2 — Serviço + API de perfis (Gerenciamento, backend)
**Entrega**: CRUD global de perfis + proteção do padrão.

- `TurProfileDto` (record, response) + `TurProfileRequest` (record, request, sem primitivos).
- `TurProfileService`: CRUD; invariante **1 padrão global** (FR-04); bloquear excluir/renomear o
  padrão (FR-03/03b); `setDefault` (+ sincronização DT-6 por site); `ensureDefaultProfile`.
- `TurProfileAPI` (`/api/v2/profile`) — contratos grupo A.
- Segurança/privilégio conforme Fase 0.
- Testes: serviço + API (CRUD, 409 duplicado/padrão, proteção do padrão).

**Gate**: testes de API verdes.

---

## Fase 3 — Config de ordenação por (perfil, site) (Facetas, backend)
**Entrega**: seleção de perfil + get/saveOrdering por (perfil, site) + retrocompat.

- `TurProfileFacetOrderingService` (substitui `TurSNSiteFacetProfileService`):
  - `getOrdering(profileId, siteId)` com fallback (perfil→Padrão→legado) (FR-08/FR-13).
  - `saveOrdering(profileId, siteId, dtos)` (upsert; custom facets por `save()` item-a-item — NFR-03).
  - `syncLegacyPositions(site)` quando salvar a config do **Padrão** do site (DT-6).
- Reescrever `TurSNSiteFacetedFieldAPI`: manter `GET/PUT` legados (padrão) + `/profile` (lista
  global) + `/profile/{id}/ordering`; **remover** o CRUD de perfil (foi p/ o grupo A).
- Limpeza: excluir faceta do site remove itens nas configs (FR-10) via cascade.
- Testes: ordenação por (perfil, site), fallback, retrocompat, cache do custom facet.

**Gate**: testes verdes; retrocompat e fallback comprovados.

---

## Fase 4 — Runtime de busca (Solr)
**Entrega**: `profile` (global) aplicado à ordem por (perfil, site) com fallback.

- `TurSNFieldProcess.getTurSNSiteFacetOrdering(snSiteId, profile)`: resolver perfil **global**
  (nome→id→Padrão) → config de (perfil, site) → fallback (Padrão do site → legado).
- `TurSolrResultProcessor.processSEResultsFacet` continua consumindo o mapa resolvido.
- `turing-commons`/SDK: **sem mudança de forma** (só a resolução muda). Confirmar.
- ES/Lucene: sem mudança (DT-6).
- Testes: ordem por perfil (FR-12), fallback (FR-13), nome global com espaço/encoding.

**Gate**: busca Solr com/sem `profile` comprovada.

---

## Fase 5 — Frontend
**Entrega**: tela de Perfis em Gerenciamento + Facetas só seleção/ordenação.

- **Gerenciamento**:
  - Rota `ROUTES.PROFILE_ROOT`/`PROFILE_INSTANCE` (`/admin/profile/instance`) +
    `app/console/profile/profile.list.page.tsx` (CRUD + definir padrão; padrão "Padrão" protegido).
  - Item no `managementItems` do `app-sidebar.tsx` (+ `ITEM_PRIVILEGE`, ícone, i18n
    `home.features.profiles.*`).
  - `models/profile/profile.model.ts` + `services/profile/profile.service.ts`.
- **Facetas** (`sn.site.facet.list.page.tsx`): Select de perfis **globais** (via
  `GET .../facet/profile`) + drag-and-drop por (perfil, site); **remover** diálogos de criar/
  renomear/excluir (link "Gerenciar perfis" → Gerenciamento). Ajustar `sn.facet.profile.service.ts`.
- i18n en/pt (novas chaves + limpeza das de CRUD que saíram de Facetas).
- Preview de busca: inalterado (FR-17).

**Gate**: type-check do frontend ok; criar perfil em Gerenciamento e usá-lo em Facetas funciona.

---

## Fase 6 — Integração, validação, limpeza e docs
- IT end-to-end (`AbstractTuringSpringIT`) cobrindo `quickstart.md` (incl. migração e fallback).
- Suíte completa backend + build frontend/SDK.
- Decidir dropar/limpar tabelas do spec 001 (changeset separado) após validação (DT-7/Q4).
- Atualizar `agents.md` (gotchas novos) e marcar o spec 001 como **substituído por 002**.

**Gate**: todos os critérios de aceite (`spec.md §8`) verdes.

---

## Fase 7 — Config de campo por perfil ("Campos") — 2º tipo de config (FR-19)
**Entrega**: um perfil pode sobrescrever as **configurações de faceta de cada campo** (facet on/off,
facetSort, facetType, facetItemType, facetRange, showAllFacetItems, secondaryFacet, facetName) —
além da ordem. Índice/schema (type/enabled/multiValued/…) continuam globais.

- **7.1 Modelo** ✅: entidade `TurProfileFieldConfig` (`tur_profile_field_config`, unique
  perfil×campo; overrides **nuláveis** = herda o global), repo `TurProfileFieldConfigRepository`,
  Liquibase `v2026.2.7.10.yaml` (+ master).
- **7.2 Serviço + API**: `TurProfileFieldConfigService` — `getFieldConfigs(site, profile)` (efetivo =
  override ∪ global) e `saveFieldConfigs(site, profile, ...)` (upsert). **Default → global**: editar
  o perfil **Padrão** grava direto no `TurSNSiteFieldExt` (espelho, análogo ao DT-6); não-padrão
  grava overrides. Endpoints sob `/api/sn/{snSiteId}/field/profile/{profileId}`.
- **7.3 Runtime**: aplicar o config efetivo do perfil na construção das facetas no Solr (facet
  on/off + sort/type/range por perfil). Requer mapear onde `facet=1`/`facetSort`/… são lidos hoje
  na montagem da busca e injetar o override do perfil (resolvido por nome, como a ordem).
- **7.4 Frontend**: **Campos** ganha seletor de perfil; ao escolher um não-padrão, o form mostra as
  settings de faceta como **override** do perfil (com indicação de "herdado"). Model/service/i18n.

**Gate por sub-fase**: backend `-am` verde; frontend `tsc` EXIT 0.

---

## Arquivos impactados (mapa rápido)

**Backend `turing-app` (novos)**
- `persistence/model/profile/TurProfile.java`, `TurProfileFacetOrdering.java`,
  `TurProfileFacetOrderingItem.java`
- `persistence/repository/profile/TurProfileRepository.java`,
  `TurProfileFacetOrderingRepository.java`, `TurProfileFacetOrderingItemRepository.java`
- `profile/TurProfileService.java`, `profile/TurProfileFacetOrderingService.java`
- `onstartup/profile/TurProfileMigrationOnStartup.java`
- `api/profile/TurProfileAPI.java`, `api/profile/bean/TurProfileDto.java`, `TurProfileRequest.java`
- `resources/db/changelog/v2026.2.7.9.yaml`

**Backend `turing-app` (alterados)**
- `api/sn/console/TurSNSiteFacetedFieldAPI.java` (remove CRUD de perfil; usa serviço novo)
- `sn/TurSNFieldProcess.java`, `solr/TurSolrResultProcessor.java`
- `db.changelog-master.yaml`
- (aposentar) `sn/facet/TurSNSiteFacetProfileService.java` e entidades do 001 — na Fase 6

**Frontend (novos)**
- `app/console/profile/profile.list.page.tsx`, `models/profile/profile.model.ts`,
  `services/profile/profile.service.ts`, entrada em `routes.const`

**Frontend (alterados)**
- `components/app-sidebar.tsx`, `app/console/sn/facet/sn.site.facet.list.page.tsx`,
  `services/sn/sn.facet.profile.service.ts`, `i18n/locales/{en,pt}/{home,sn}.json`

**Fora de escopo**: ES/Lucene; export/import de perfis; `profile` no SDK/commons (forma).

## Riscos e mitigações
| Risco | Mitigação |
|-------|-----------|
| Consolidação de nomes na migração | Regra por nome + log (data-model §6); revisar em dev antes de prod |
| Perfil sem config para um site | Fallback ao Padrão do site (FR-13) coberto por teste |
| Regressão do que o 001 entregou | Fase 0 mapeia pontos a substituir; testes de retrocompat |
| Cache de custom facet | `save()` item-a-item (NFR-03), como no 001 |
| Cache incremental / SDK dist (Windows) | `clean` no maven; rebuild do SDK (gotchas no agents.md) |
