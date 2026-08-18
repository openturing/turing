# Data Model — Perfis de Ordenação de Facetas

Modelo de dados, alterações em entidades existentes e estratégia de migração.

---

## 1. Visão de relacionamento

```
TurSNSite (1) ───< (N) TurSNSiteFacetProfile          [perfil pertence ao site]
                              │ (1)
                              │
                              ∨ (N)
                       TurSNSiteFacetProfileItem        [posição da faceta no perfil]
                              │  \
              (N) ┌───────────┘   └───────────┐ (N)
                  ∨                            ∨
        TurSNSiteFieldExt (0..1)      TurSNSiteCustomFacet (0..1)
        [faceta de campo]             [faceta customizada]
        — exatamente UMA das duas referências é preenchida —
```

- Um **site** tem N **perfis**; exatamente 1 perfil é o padrão.
- Um **perfil** tem N **itens** (um por faceta posicionada).
- Um **item** referencia **uma** faceta: ou um `TurSNSiteFieldExt` (faceta de campo) ou um
  `TurSNSiteCustomFacet` (faceta customizada). A outra referência é nula.

## 2. Entidades novas

### 2.1 `TurSNSiteFacetProfile`
- Pacote: `com.viglet.turing.persistence.model.sn.field`
- Tabela: `sn_site_facet_profile`
- **NÃO** usar `@EntityListeners(TurSNSiteSnapshotEvictionListener.class)`: a ordem é resolvida
  fora do snapshot (research Q1), logo alterar um perfil não deve invalidar o cache do snapshot.

| Campo | Tipo Java | Coluna | Observações |
|-------|-----------|--------|-------------|
| `id` | `String` | `id` | `@Id @TurAssignableUuidGenerator`, `updatable=false, nullable=false` |
| `name` | `String` | `name` | `nullable=false, length=100` |
| `description` | `String` | `description` | `length=500`, opcional |
| `defaultProfile` | `boolean` | `defaultProfile` | `nullable=false`, default `false`; exatamente 1 `true` por site |
| `turSNSite` | `TurSNSite` | `sn_site_id` (FK) | `@ManyToOne(fetch = LAZY)`, `@JoinColumn(name="sn_site_id", nullable=false)`, `@OnDelete(CASCADE)` |
| `facetProfileItems` | `Set<TurSNSiteFacetProfileItem>` | — | `@OneToMany(mappedBy="turSNSiteFacetProfile", cascade=ALL, orphanRemoval=true, fetch=LAZY)` |

Constraints:
- `UNIQUE (sn_site_id, name)` → nome único por site (suporta `facetProfile` por nome — DT-3).
- Garantir 1 padrão por site: lógica no serviço (ao marcar um como padrão, desmarca os demais
  do site). (Opcional: índice único parcial onde `defaultProfile=true` — evitar por
  portabilidade H2/MySQL/Postgres; preferir invariante no serviço.)

### 2.2 `TurSNSiteFacetProfileItem`
- Pacote: `com.viglet.turing.persistence.model.sn.field`
- Tabela: `sn_site_facet_profile_item`

| Campo | Tipo Java | Coluna | Observações |
|-------|-----------|--------|-------------|
| `id` | `String` | `id` | `@Id @TurAssignableUuidGenerator` |
| `turSNSiteFacetProfile` | `TurSNSiteFacetProfile` | `facet_profile_id` (FK) | `@ManyToOne(LAZY)`, `nullable=false`, `@OnDelete(CASCADE)` |
| `turSNSiteFieldExt` | `TurSNSiteFieldExt` | `field_ext_id` (FK) | `@ManyToOne(LAZY)`, **nullable**, `@OnDelete(CASCADE)` |
| `turSNSiteCustomFacet` | `TurSNSiteCustomFacet` | `custom_facet_id` (FK) | `@ManyToOne(LAZY)`, **nullable**, `@OnDelete(CASCADE)` |
| `facetPosition` | `Integer` | `facet_position` | posição (1 = primeira) |

Invariante (no serviço): exatamente um de (`turSNSiteFieldExt`, `turSNSiteCustomFacet`) é não
nulo. Recomenda-se também `UNIQUE (facet_profile_id, field_ext_id)` e
`UNIQUE (facet_profile_id, custom_facet_id)` para evitar duplicidade da mesma faceta no perfil.

> **Nota de convenção (agents.md)**: o projeto mapeia nomes Java **literalmente** (sem
> snake_case implícito). As facetas existentes misturam os dois estilos
> (`TurSNSiteFieldExt.facetPosition` literal vs `TurSNSiteCustomFacet.facet_position`
> explícito). Para as tabelas novas adotamos **snake_case explícito via `@Column(name=...)`**
> (como `sn_site_custom_facet`), mantendo o changelog Liquibase com os mesmos nomes. O importante
> é que **coluna Liquibase == `name` do `@Column`**.

## 3. Alterações em entidades existentes

| Entidade | Alteração |
|----------|-----------|
| `TurSNSite` | Adicionar `@OneToMany(mappedBy="turSNSite", cascade=ALL, orphanRemoval=true, fetch=LAZY) Set<TurSNSiteFacetProfile> facetProfiles`. (Excluir site → exclui perfis.) |
| `TurSNSiteFieldExt` | **Não alterado na Fase 1.** A remoção de itens ao excluir a faceta é garantida pelo FK `ON DELETE CASCADE` (Liquibase) + `@OnDelete` no `TurSNSiteFacetProfileItem`. `facetPosition` legado mantido em uso (espelho do padrão — DT-6); **sem** `@Deprecated` (evita poluir ES/Lucene/APIs que o usam legitimamente). |
| `TurSNSiteCustomFacet` | **Não alterado na Fase 1** (mesma justificativa). |

> Atenção a `FetchType.LAZY` em **todas** as associações (regra do `agents.md`).

> **DT-6 (espelho do padrão)**: o `facetPosition` legado deixa de ser a fonte de verdade do
> Solr, mas é mantido **sincronizado com o perfil padrão**. O serviço (Fase 2) reescreve o
> `facetPosition` legado quando a ordenação do perfil **padrão** é salva ou quando outro perfil é
> promovido a padrão. Assim Elasticsearch e Lucene (que continuam lendo o legado — research Q3)
> sempre refletem o "Geral", coerente com "perfis só no Solr".

## 4. Repositórios (Spring Data — DT-5)

- `TurSNSiteFacetProfileRepository extends JpaRepository<TurSNSiteFacetProfile, String>`
  - `List<TurSNSiteFacetProfile> findByTurSNSite(TurSNSite site)`
  - `Optional<TurSNSiteFacetProfile> findByTurSNSiteAndNameIgnoreCase(TurSNSite site, String name)`
  - `Optional<TurSNSiteFacetProfile> findByTurSNSiteAndDefaultProfileTrue(TurSNSite site)`
- `TurSNSiteFacetProfileItemRepository extends JpaRepository<TurSNSiteFacetProfileItem, String>`
  - `List<TurSNSiteFacetProfileItem> findByTurSNSiteFacetProfile(TurSNSiteFacetProfile profile)`

## 5. Migração Liquibase

- **Novo arquivo** (não editar existentes): `db/changelog/v2026.2.7.<sub>.yaml`, incluído em
  `db.changelog-master.yaml`. Numerar a subversão conforme o último existente.
- **Changesets** (todos idempotentes — `preConditions` com `onFail: MARK_RAN`):

  1. **createTable `sn_site_facet_profile`** (`id`, `name`, `description`, `defaultProfile`,
     `sn_site_id`) + FK para `sn_site` + unique `(sn_site_id, name)`.
     - `precondition: not tableExists sn_site_facet_profile`.
  2. **createTable `sn_site_facet_profile_item`** (`id`, `facet_profile_id`, `field_ext_id`,
     `custom_facet_id`, `facet_position`) + FKs.
     - `precondition: not tableExists sn_site_facet_profile_item`.
  3. **Seed do perfil "Geral" por site** (D4): para cada `sn_site`, inserir um
     `sn_site_facet_profile` com `name='Geral'`, `defaultProfile=true`.
     - Implementar via `<sql>` portável (INSERT ... SELECT a partir de `sn_site`, gerando `id`).
       Como o id é UUID atribuível na app, no SQL de migração gerar via função do banco pode não
       ser portável → **alternativa recomendada**: changeset de **dados** custom + um
       *bootstrap em runtime* (ver §6) para sites sem perfil. Decisão final em Fase 1.
  4. **Popular itens do "Geral"** a partir do `facetPosition` legado:
     - de `sn_site_field_ext` (onde `facet=1` e `facetPosition` não nulo) →
       `sn_site_facet_profile_item(field_ext_id, facet_position)`;
     - de `sn_site_custom_facet` (onde `facet_position` não nulo) →
       `sn_site_facet_profile_item(custom_facet_id, facet_position)`.

  > **Portabilidade da geração de UUID na migração**: o projeto usa `TurAssignableUuidGenerator`
  > na aplicação. Para evitar `RANDOM_UUID()`/`UUID()` específicos de banco em SQL, a estratégia
  > preferida é fazer o **seed/backfill em runtime** (§6) e deixar no Liquibase apenas o DDL
  > (changesets 1 e 2). Confirmar na Fase 1.

## 6. Bootstrap em runtime (garantir perfil padrão) — FR-05

Componente idempotente (ex.: `TurSNSiteFacetProfileOnStartup` (pacote `onstartup/sn`) com `ApplicationRunner`, ou
verificação *lazy* no serviço) que, para cada site sem perfil padrão:
1. cria o perfil "Geral" (`defaultProfile=true`);
2. popula os itens a partir do `facetPosition` legado das facetas habilitadas do site.

Isso cobre tanto sites pré-existentes (caso o seed Liquibase fique só com DDL) quanto sites
criados após o deploy. A criação de site deve chamar o mesmo serviço.

## 7. Impacto em snapshot / export (a confirmar — research Q1/Q4)

- Se houver `TurSNSiteSnapshot` que serialize posições de faceta, decidir entre incluir os
  perfis no snapshot ou resolver a ordem fora dele (preferência: fora — ver `research.md` DT-2).
- `TurSNSiteExchange`/`TurSNSiteImport`: garantir que o import de site existente não quebre.
  Incluir perfis no export é escopo incremental (não bloqueia esta feature).
