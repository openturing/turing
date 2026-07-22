# Research — Perfis Genéricos em "Gerenciamento"

Análise do estado atual (após o spec 001), onde encaixar a nova tela em "Gerenciamento", e as
decisões técnicas que embasam o `plan.md`.

---

## 1. Estado atual (o que o spec 001 entregou)

### 1.1 Persistência (per-site)
- `TurSNSiteFacetProfile` (tabela `sn_site_facet_profile`) — perfil **por site**, com `name`
  (único por site), `description`, `defaultProfile`, FK `sn_site_id`, e itens.
- `TurSNSiteFacetProfileItem` (tabela `sn_site_facet_profile_item`) — posição por faceta
  (`field_ext_id` **ou** `custom_facet_id`), `facet_position`.
- `TurSNSite.turSNSiteFacetProfiles` (`@OneToMany`).
- Migração/bootstrap: `TurSNSiteFacetProfileOnStartup` cria "Geral" (padrão) por site a partir do
  `facetPosition` legado. Liquibase `v2026.2.7.8.yaml`.

### 1.2 Serviço/API/runtime
- `TurSNSiteFacetProfileService` — CRUD por site, `ensureDefaultProfile`, get/saveOrdering,
  `setDefault`, sincronização do legado (DT-6). Custom facets salvos por `save()` item-a-item
  (dispara `@CacheEvict`; `saveAll` não).
- `TurSNSiteFacetedFieldAPI` (`/api/sn/{snSiteId}/facet`) — legados `GET /`, `PUT /ordering` (padrão)
  + `/profile` (CRUD) + `/profile/{id}/ordering`.
- Runtime: `TurSNFieldProcess.getTurSNSiteFieldOrdering(snSiteId, profile)` +
  `resolveProfilePositions` (nome→id→padrão); aplicado em
  `TurSolrResultProcessor.processSEResultsFacet`. Param `profile` propagado em
  **turing-commons** (`TurSNParamType`, `TurSNSearchParams`, `TurSEParameters`,
  `TurSNSitePostParamsBean`) e no **`@viglet/turing-react-sdk`** (preview de busca).

### 1.3 Frontend
- Facetas (`app/console/sn/facet/sn.site.facet.list.page.tsx`): `<Select>` de perfis + CRUD
  (criar/renomear/excluir/definir-padrão) + drag-and-drop (`SNSiteFacetDraggableList`).
- i18n `sn.json` (`sn.facets.profile.*`) e `search.json` (`search.profile*`).

> **Tudo isso é o alvo da refatoração**: o perfil deixa de ser per-site e sai de Facetas.

---

## 2. Onde encaixar "Gerenciamento" (confirmado no código)

O sidebar (`components/app-sidebar.tsx`) tem 3 grupos, montados em `navGroups`:
- `home.sections.generativeAi.label`
- `home.sections.enterpriseSearch.label` (contém **Navegação Semântica** → `/admin/sn/instance`)
- `home.sections.management.label` = **"Gerenciamento"** (pt) — itens: `assets`, `git`,
  `marketplace`, `import`, `logging`, `administration` (rotas `/admin/...`).

**Achado Q-A**: adicionar um item **"Perfis"** ao array `managementItems` do `app-sidebar.tsx`
(`key: "profiles"`, `titleKey: "home.features.profiles.title"`, `url: ROUTES.PROFILE_INSTANCE`,
ícone p.ex. `IconUsersGroup`/`IconId`). O grupo Management já filtra por flags/privilégio; definir
o privilégio em `ITEM_PRIVILEGE` (ver Q-D).

**Achado Q-B (rota)**: a tela vive fora do site SN. Criar rota de nível de console
(ex.: `ROUTES.PROFILE_ROOT`/`PROFILE_INSTANCE` = `/admin/profile/instance`) com uma
`profile.list.page.tsx` própria (não dentro de `sn.routes.tsx`). A sub-navegação do site SN
(`sn.routes.tsx`) **não** recebe o CRUD de perfis.

**Achado Q-C (Facetas permanece)**: `sn.site.facet.list.page.tsx` continua sendo o lugar de
**seleção + ordenação**, mas perde os diálogos de criar/renomear/excluir (movidos para a nova tela).

**Achado Q-D (privilégio)**: itens do sidebar mapeiam privilégio em `ITEM_PRIVILEGE`
(`app-sidebar.tsx`) e o backend valida via segurança do console. Como perfis são globais e de
administração, **recomendação**: exigir um privilégio de admin/gestão (ex.: reutilizar `SN_VIEW`
se perfis forem considerados parte de SN, ou um novo privilégio `PROFILE_VIEW`/apenas admin).
**Decisão final na Fase 0/1** conforme o modelo de privilégios existente.

---

## 3. Decisões técnicas

### DT-1 — Perfil global neutro (`TurProfile`)
Nova entidade **global** `TurProfile` (pacote `com.viglet.turing.persistence.model.profile`, tabela
`tur_profile`): `id`, `name` (único **global**), `description`, `defaultProfile`. **Sem** vínculo a
site e **sem** vínculo a facetas — é agnóstica ao tipo de configuração (D2/FR-19).

### DT-2 — Configuração de facetas por (perfil × site)
A posição da faceta vai para `TurProfileFacetOrdering` (config por par perfil×site) + itens
`TurProfileFacetOrderingItem`. Assim o mesmo perfil global tem **uma configuração por site**.
Ver `data-model.md`.

### DT-3 — Identificador externo do perfil = nome (global)
`profile` na busca continua usando o **nome** do perfil, agora **único global**. Mantém a
compatibilidade do parâmetro e do SDK.

### DT-4 — Runtime: resolução global + fallback por site
`TurSNFieldProcess` resolve o perfil **global** (nome→id→padrão) e então busca a config de
**(perfil, site)** do site em questão; *fallback*: config do **Padrão** para o site → `facetPosition`
legado (FR-13). Continua **Solr apenas**; a "costura" segue em
`TurSolrResultProcessor.processSEResultsFacet`.

### DT-5 — Serviços
- `TurProfileService` — CRUD global + invariante de 1 padrão + proteção do padrão (não excluir/
  renomear) + `ensureDefaultProfile()`.
- `TurProfileFacetOrderingService` — get/saveOrdering por (perfil, site); sincroniza `facetPosition`
  legado quando a config do **Padrão** de um site é salva (DT-6). Substitui o
  `TurSNSiteFacetProfileService` do 001.

### DT-6 — `facetPosition` legado = espelho da config do Padrão (por site)
Mantido do spec 001, adaptado ao modelo global: ao salvar a config de facetas do **Padrão** para um
site (ou ao trocar o padrão global), o serviço reescreve o `facetPosition` legado das facetas
**daquele site**. ES/Lucene (fora do escopo) seguem o Padrão.

### DT-7 — Substituir e migrar o spec 001 (D5)
O modelo per-site (`sn_site_facet_profile*`) é **substituído**. A migração:
1. cria as tabelas novas (`tur_profile`, `tur_profile_facet_ordering`, `..._item`);
2. cria o **"Padrão"** global;
3. move os perfis/itens per-site para o novo modelo (ver `data-model.md §6` para a regra de
   consolidação por nome e o tratamento dos "Geral" per-site);
4. mantém as tabelas do 001 **por uma release** (não dropar de imediato) para rollback seguro —
   decisão de dropar/limpar fica na Fase 6.

> Contexto: o spec 001 está numa **feature branch** (não liberado em produção). Ainda assim a
> migração é escrita para funcionar tanto em bases limpas quanto em bases de dev que já tenham as
> tabelas do 001.

### DT-8 — Extensibilidade sem over-engineering
Cada **tipo** de configuração é a **sua própria entidade** referenciando `TurProfile` (ex.: hoje
`TurProfileFacetOrdering`; amanhã `TurProfileXpto`). **Não** usar tabela polimórfica/EAV única —
mantém type-safety e o estilo do projeto. O "genérico" está em `TurProfile` ser neutro e reutilizável.

---

## 4. Fase 0 — perguntas a confirmar (checklist)
- **Q1**: Privilégio da tela de Perfis em Gerenciamento (`ITEM_PRIVILEGE` + segurança backend). → §2 Q-D.
- **Q2**: Nome/rota exatos (`ROUTES.PROFILE_*`, `/admin/profile/instance`) e ícone do sidebar. → §2.
- **Q3**: Regra de consolidação de nomes na migração (perfis homônimos entre sites). → data-model §6.
- **Q4**: Manter as tabelas do 001 por 1 release (rollback) vs dropar na mesma migração. → DT-7.
- **Q5**: `profile` no SDK/commons **não** muda de forma (só a resolução no backend muda). Confirmar.
- **Q6**: Snapshot/export — perfil global não deve invalidar snapshot (como no 001: sem
  `@EntityListeners` de eviction nas entidades de perfil/config).

## 5. Nota — o que **não** muda
- Parâmetro `profile` (nome), beans em `turing-commons`, e o `@viglet/turing-react-sdk`
  (preview de busca) permanecem — só a **resolução no backend** passa a ser global + por site.
- A "costura" de runtime (`TurSolrResultProcessor.processSEResultsFacet`) continua no mesmo lugar.
- ES/Lucene continuam no `facetPosition` legado (DT-6).
