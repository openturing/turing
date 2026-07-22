# Data Model — Perfis Genéricos em "Gerenciamento"

Entidades novas (perfil global + configuração de facetas por site), alterações e migração do
spec 001.

---

## 1. Visão de relacionamento

```
TurProfile (GLOBAL)                     [perfil neutro, criado em Gerenciamento]
   │ (1)                                 name único GLOBAL; exatamente 1 defaultProfile=true
   │
   ∨ (N)
TurProfileFacetOrdering  ──(N)──> (1) TurSNSite     [config de facetas de UM perfil para UM site]
   │ (1)                                             UNIQUE (profile_id, sn_site_id)
   │
   ∨ (N)
TurProfileFacetOrderingItem            [posição de UMA faceta nessa config]
   │  \
 (0..1)└ TurSNSiteFieldExt      (0..1)┘ TurSNSiteCustomFacet
   — exatamente UMA das duas referências é preenchida —
```

- Um **perfil** é global; tem **N configurações de facetas**, no máximo **uma por site**.
- Uma **config de facetas** pertence a **(perfil, site)** e tem **N itens** (um por faceta).
- Um **item** referencia **uma** faceta (campo **ou** customizada); a outra referência é nula.
- Extensibilidade (DT-8): outros tipos de config futuros são **novas entidades** apontando para
  `TurProfile` (não alteram o perfil).

---

## 2. Entidade nova — `TurProfile` (global)
- Pacote: `com.viglet.turing.persistence.model.profile`
- Tabela: `tur_profile`
- **Sem** `@EntityListeners` de eviction de snapshot (a ordem é resolvida fora do snapshot).

| Campo | Tipo Java | Coluna | Observações |
|-------|-----------|--------|-------------|
| `id` | `String` | `id` | `@Id @TurAssignableUuidGenerator`, `updatable=false, nullable=false` |
| `name` | `String` | `name` | `nullable=false, length=100` — **único global** |
| `description` | `String` | `description` | `length=500`, opcional |
| `defaultProfile` | `boolean` | `default_profile` | `nullable=false` default `false`; exatamente 1 `true` no sistema |

Constraints:
- `UNIQUE (name)` → nome único global (suporta `profile` por nome — DT-3).
- 1 padrão global: invariante no serviço (ao marcar um, desmarca os demais). Não usar índice
  parcial (portabilidade H2/MySQL/Postgres).

> Coleções para os tipos de config (ex.: `Set<TurProfileFacetOrdering>`) são **opcionais** na
> entidade; preferir navegar pelos repositórios das configs (evita LAZY em entidade `@Cacheable`,
> lição do spec 001). Se adicionar, `@OneToMany(mappedBy=..., fetch=LAZY)`.

---

## 3. Entidade nova — `TurProfileFacetOrdering` (config por perfil×site)
- Pacote: `com.viglet.turing.persistence.model.profile` (ou `.../sn/field`)
- Tabela: `tur_profile_facet_ordering`

| Campo | Tipo Java | Coluna | Observações |
|-------|-----------|--------|-------------|
| `id` | `String` | `id` | `@Id @TurAssignableUuidGenerator` |
| `turProfile` | `TurProfile` | `profile_id` (FK) | `@ManyToOne(LAZY)`, `nullable=false`, `@OnDelete(CASCADE)` |
| `turSNSite` | `TurSNSite` | `sn_site_id` (FK) | `@ManyToOne(LAZY)`, `nullable=false`, `@OnDelete(CASCADE)` |
| `items` | `Set<TurProfileFacetOrderingItem>` | — | `@OneToMany(mappedBy="turProfileFacetOrdering", cascade=ALL, orphanRemoval=true, fetch=LAZY)` |

Constraints:
- `UNIQUE (profile_id, sn_site_id)` → uma config por (perfil, site).

---

## 4. Entidade nova — `TurProfileFacetOrderingItem`
- Tabela: `tur_profile_facet_ordering_item`

| Campo | Tipo Java | Coluna | Observações |
|-------|-----------|--------|-------------|
| `id` | `String` | `id` | `@Id @TurAssignableUuidGenerator` |
| `turProfileFacetOrdering` | `TurProfileFacetOrdering` | `ordering_id` (FK) | `@ManyToOne(LAZY)`, `nullable=false`, `@OnDelete(CASCADE)` |
| `turSNSiteFieldExt` | `TurSNSiteFieldExt` | `field_ext_id` (FK) | `@ManyToOne(LAZY)`, **nullable**, `@OnDelete(CASCADE)` |
| `turSNSiteCustomFacet` | `TurSNSiteCustomFacet` | `custom_facet_id` (FK) | `@ManyToOne(LAZY)`, **nullable**, `@OnDelete(CASCADE)` |
| `facetPosition` | `Integer` | `facet_position` | posição (1 = primeira) |

Invariante (serviço): exatamente um de (`turSNSiteFieldExt`, `turSNSiteCustomFacet`) não nulo.
Recomenda-se `UNIQUE (ordering_id, field_ext_id)` e `UNIQUE (ordering_id, custom_facet_id)`.

> **Convenção de nomes** (agents.md): tabelas novas usam **snake_case explícito via
> `@Column(name=...)`**; coluna Liquibase == `name` do `@Column`. `boolean` minúsculo no changelog.

---

## 5. Repositórios (Spring Data — DT-5)
- `TurProfileRepository extends JpaRepository<TurProfile, String>`
  - `Optional<TurProfile> findByNameIgnoreCase(String name)`
  - `Optional<TurProfile> findByDefaultProfileTrue()`
  - `boolean existsByNameIgnoreCase(String name)`
- `TurProfileFacetOrderingRepository extends JpaRepository<TurProfileFacetOrdering, String>`
  - `Optional<TurProfileFacetOrdering> findByTurProfileAndTurSNSite(TurProfile p, TurSNSite s)`
  - `List<TurProfileFacetOrdering> findByTurSNSite(TurSNSite s)` (para limpeza ao excluir faceta)
  - (custom facets carregados via `TurSNSiteCustomFacetRepository.findByFieldExtsWithDetails(...)`,
    **nunca** navegando a coleção LAZY de entidade `@Cacheable` — lição do spec 001)
- `TurProfileFacetOrderingItemRepository extends JpaRepository<TurProfileFacetOrderingItem, String>`

---

## 6. Migração (Liquibase + runtime)

**Novo arquivo** (não editar existentes): `db/changelog/v2026.2.7.9.yaml` (subversão após o `.8` do
spec 001), incluído no `db.changelog-master.yaml`. Todos os changesets idempotentes
(`preConditions` com `onFail: MARK_RAN`).

### 6.1 DDL (changesets 1–3)
1. `createTable tur_profile` (+ unique `name`).
2. `createTable tur_profile_facet_ordering` (+ FKs, unique `(profile_id, sn_site_id)`).
3. `createTable tur_profile_facet_ordering_item` (+ FKs).

### 6.2 Migração de dados do spec 001 (runtime — recomendado)
Como a geração de UUID é da aplicação (`TurAssignableUuidGenerator`), fazer o **backfill em runtime**
(componente idempotente `TurProfileMigrationOnStartup`/dentro do bootstrap), não em SQL:

1. **Padrão global**: garantir um `TurProfile` `name='Padrão'`, `defaultProfile=true` (FR-05).
   - Se o spec 001 tinha perfis "Geral" por site, cada "Geral" per-site vira a **config do "Padrão"
     global para aquele site** (seus itens → `TurProfileFacetOrdering` de (Padrão, site)).
2. **Perfis não-padrão do 001** (per-site, ex.: "Cursos EE"): para cada nome distinto, garantir um
   `TurProfile` global com aquele nome (**consolidação por nome** — Q3):
   - itens per-site daquele perfil → `TurProfileFacetOrdering` de (perfil global, site).
   - **Colisão de nomes** entre sites (dois sites com "Cursos EE") → **um** perfil global "Cursos EE"
     com **duas** configs (uma por site). Logar cada consolidação.
3. **Fonte alternativa** (bases sem spec 001 aplicado): popular a config do "Padrão" por site a
   partir do `facetPosition` legado das facetas habilitadas (como o bootstrap do 001).

> Tabelas do 001 (`sn_site_facet_profile*`) **não** são dropadas nesta migração (rollback seguro,
> DT-7/Q4). Um changeset de limpeza pode vir numa release seguinte após validação.

### 6.3 Bootstrap contínuo
`TurProfileService.ensureDefaultProfile()` (idempotente) garante o "Padrão" global em qualquer boot
e ao listar (FR-05). Novos sites **não** precisam criar perfis; herdam os globais e, sem config
própria, caem no fallback do Padrão (FR-13).

---

## 7. Alterações em entidades existentes
| Entidade | Alteração |
|----------|-----------|
| `TurSNSite` | (opcional) `@OneToMany` para `TurProfileFacetOrdering` (`mappedBy="turSNSite"`, LAZY) — só se necessário para cascade de exclusão do site; senão o `@OnDelete(CASCADE)` na FK basta. Remover o `turSNSiteFacetProfiles` do 001 ao aposentar aquele modelo. |
| `TurSNSiteFieldExt` / `TurSNSiteCustomFacet` | Sem alteração estrutural. Remoção de itens ao excluir a faceta via FK `ON DELETE CASCADE` + `@OnDelete`. `facetPosition` legado segue como espelho do Padrão (DT-6). |

---

## 8. Impacto em snapshot / export
- Entidades de perfil/config **sem** `@EntityListeners` de eviction (a ordem é resolvida fora do
  snapshot — como no spec 001).
- Export/import de site: não deve quebrar; exportar perfis globais é incremental (fora de escopo).

---

## 9. Config de campo por perfil ("Campos") — 2º tipo de config (Fase 7)

Segundo tipo de configuração anexado ao `TurProfile` (FR-19): overrides de **faceta por campo**.

### 9.1 `TurProfileFieldConfig`
- Pacote: `com.viglet.turing.persistence.model.profile`; tabela `tur_profile_field_config`.
- Relação: `(N) TurProfileFieldConfig ──> (1) TurProfile` e `──> (1) TurSNSiteFieldExt`. O campo já é
  por-site (`TurSNSiteFieldExt.turSNSite`), então (perfil, campo) é implicitamente por-site.
- `UNIQUE (profile_id, field_ext_id)`; FKs `ON DELETE CASCADE`.

| Campo | Tipo | Coluna | Observações |
|-------|------|--------|-------------|
| `id` | `String` | `id` | `@TurAssignableUuidGenerator` |
| `turProfile` | `TurProfile` | `profile_id` | `@ManyToOne(LAZY)`, nn, `@OnDelete(CASCADE)` |
| `turSNSiteFieldExt` | `TurSNSiteFieldExt` | `field_ext_id` | `@ManyToOne(LAZY)`, nn, `@OnDelete(CASCADE)` |
| `facet` | `Integer` | `facet` | **nulável** (null = herda o global) |
| `facetName` | `String` | `facet_name` | nulável |
| `facetSort` | `TurSNSiteFacetFieldSortEnum` | `facet_sort` | `@Enumerated(STRING)`, nulável |
| `facetType` | `TurSNSiteFacetFieldEnum` | `facet_type` | nulável |
| `facetItemType` | `TurSNSiteFacetFieldEnum` | `facet_item_type` | nulável |
| `facetRange` | `TurSNSiteFacetRangeEnum` | `facet_range` | nulável |
| `secondaryFacet` | `Boolean` | `secondary_facet` | nulável |
| `showAllFacetItems` | `Boolean` | `show_all_facet_items` | nulável |

> **Regra de herança**: cada override nulo ⇒ usa o valor global do `TurSNSiteFieldExt`. Só settings
> de **faceta/exibição** são sobrescrevíveis; índice/schema (type/enabled/multiValued/hl/mlt/required)
> continuam globais.

### 9.2 Repositório
`TurProfileFieldConfigRepository`: `findByTurProfileAndTurSNSiteFieldExt`,
`findByTurProfileAndTurSNSiteFieldExtIn`, `findByTurProfile`.

### 9.3 Migração
Liquibase **`v2026.2.7.10.yaml`** (só DDL da tabela) incluído no master. Sem backfill (ausência de
override = herança do global).

### 9.4 Semântica do Padrão (Fase 7.2)
Editar o config de campo do perfil **Padrão** grava direto no `TurSNSiteFieldExt` global (o Padrão é
espelho do global, análogo ao DT-6); perfis **não-padrão** gravam linhas em `TurProfileFieldConfig`.
