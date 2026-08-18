# Research — Configuração de Campos por Perfil

Mapeamento dos pontos de integração no código (confirmado por leitura), para o serviço/API/runtime/frontend.

## 1. O que já existe (Fase 7.1 do 002 — reaproveitar)
- **Entidade** `persistence/model/profile/TurProfileFieldConfig.java` — `tur_profile_field_config`,
  unique `(profile_id, field_ext_id)`; `@ManyToOne(LAZY)` para `TurProfile` e `TurSNSiteFieldExt` (ambos
  `@OnDelete(CASCADE)`); colunas de override **nuláveis**: `facet` (Integer), `facetName`, `facetSort`,
  `facetType`, `facetItemType`, `facetRange` (enums `@Enumerated(STRING)`), `secondaryFacet`,
  `showAllFacetItems` (Boolean). `@JsonIgnoreProperties({"turProfile","turSNSiteFieldExt"})`.
- **Repositório** `persistence/repository/profile/TurProfileFieldConfigRepository.java` —
  `findByTurProfileAndTurSNSiteFieldExt`, `findByTurProfileAndTurSNSiteFieldExtIn(profile, fieldExts)`
  (leitura em lote — base do NFR-01), `findByTurProfile`.
- **Liquibase** `resources/db/changelog/v2026.2.7.10.yaml` (tabela) já incluído no master.

> Como o campo (`TurSNSiteFieldExt`) já é **por site**, o par (perfil, campo) é implicitamente por site —
> não há coluna de site em `TurProfileFieldConfig`.

## 2. Runtime — onde as facetas são montadas hoje
- `solr/TurSolr.retrieveSolrFromSN(...)` chama
  `turSolrQueryBuilder.prepareQueryFacet(turSNSite, query, filterParams, turSEParameters)`.
  **`turSEParameters.getProfile()`** já existe (002 §4g) e carrega o perfil aplicado (inclui o
  `SET_PROFILE` da regra de busca, 002 §4k) — é o gancho para resolver o config efetivo aqui.
- `solr/TurSolrQueryBuilder`:
  - `getEnabledFacets(turSNSite)` decide **quais** campos são faceta: `isFacetEnabled(field)` =
    `field.getFacet()==1` **ou** campo tem custom facet. Lê de **snapshot** (`enabledFields`/
    `fieldIdsWithCustomFacets`) ou do repo `findByTurSNSiteAndEnabled(site,1)`.
  - `setFacetFields(...)` lê **do próprio `TurSNSiteFieldExt`**: `getFacetSort()`, `getItemsPerFacet()`,
    monta `TurSNSiteFieldExtDto(field)`, `isDateRangeFacet` (usa `facetRange`), `addFacetField`
    (usa `facetType`/`facetItemType`/`setEntityPrefix`), etc.
  - **Conclusão:** para aplicar o perfil, é preciso **sobrepor** os atributos de faceta do `TurSNSiteFieldExt`
    com o override do perfil **antes** de `getEnabledFacets`/`setFacetFields` lerem. Duas abordagens:
    - **(A) Overlay em memória**: para cada `TurSNSiteFieldExt` da lista, produzir uma cópia/def efetiva
      (facet/facetName/sort/type/itemType/range/secondary/showAll = override∪global) e alimentar o builder.
    - **(B) Efetivo no DTO**: aplicar o override ao construir `TurSNSiteFieldExtDto` e ajustar a seleção
      de facetas para usar o `facet` efetivo. Requer também filtrar `getEnabledFacets` pelo efetivo.
  - **Preferir (A)**: um único ponto (`prepareQueryFacet`) resolve os overrides do perfil em lote e passa
    a lista **efetiva** adiante; `getEnabledFacets`/`setFacetFields` continuam lendo "de um `TurSNSiteFieldExt`"
    (agora efetivo), minimizando mudança de assinatura.
- **Ordenação (002)**: `sn/TurSNFieldProcess` já resolve o perfil (nome→id) e a config de (perfil,site).
  A resolução do **perfil aplicado** deve ser **única**/consistente entre ordenação (002) e config
  efetivo (003) — reutilizar o mesmo `TurProfileRepository.findByNameIgnoreCase(profile)` + fallback ao
  Padrão (`findByDefaultProfileTrue`).

## 3. Snapshot (NFR-02) — atenção
`sn/snapshot/TurSNSiteSearchSnapshotService` cacheia `enabledFields`/`enabledFacets`/`fieldIdsWithCustomFacets`
**globais** por site. Se o overlay do perfil for aplicado **depois** de obter a lista de campos do snapshot,
está correto (o snapshot fornece o universo de campos; o perfil só reescreve os atributos de faceta e o
flag `facet` efetivo). **Não** cachear a lista **já sobreposta** dentro do snapshot global (senão vaza
entre perfis). O overlay do perfil deve ser resolvido por (perfil, site) — com seu próprio `@Cacheable`
no `TurProfileFieldConfigRepository` (NFR-01), separado do snapshot.

## 4. Campos — frontend
- Página lista: `app/console/sn/fields/sn.site.field.list.page.tsx` (usa `TurSNFieldService`,
  `SNSiteFieldGridList`). A edição das configs de faceta ocorre no **form/grid** do campo.
- Padrão de **seletor de perfil** a copiar de `app/console/sn/facet/sn.site.facet.list.page.tsx`:
  `TurSNFacetProfileService.query(siteId)` → lista de `TurProfile`; pré-seleciona `profile.default`;
  `<Select>` com rótulo "Padrão" para o default. Reutilizar `sn.facets.profile.*` i18n como base.
- API de campos hoje: `api/sn/console/TurSNSiteFieldExtAPI` (`/api/sn/{snSiteId}/field/ext`,
  já `@Secured` SN_VIEW/SN_EDIT). O modo **global (Padrão)** reusa esses endpoints; o modo **perfil**
  usa os **novos** endpoints (contracts).

## 5. Config efetivo — regra de resolução (para o serviço e o runtime)
Para cada atributo `a ∈ {facet, facetName, facetSort, facetType, facetItemType, facetRange,
secondaryFacet, showAllFacetItems}`:

```
efetivo(perfil, campo).a =
    override(perfil, campo).a  se override existe e a != null
    senão                       campo.a           (global TurSNSiteFieldExt)
```

- Perfil **Padrão** nunca tem override (D3): seu efetivo == global. Por isso a UI do Padrão escreve no
  global e o fallback de runtime (perfil ausente) cai no global naturalmente.
- `facet` efetivo governa a **inclusão** da faceta (getEnabledFacets); os demais governam **como** a
  faceta é montada.

## 6. Versão / convenções
- `pom.xml` atual: **2026.2.7** → novos arquivos Java com `@since 2026.2.7`.
- Records de request sem primitivos (Jackson 3 — usar `Integer`/`Boolean`, nunca `int`/`boolean`).
- Serviço novo segue construtor-injection; enums `@Enumerated(STRING)`.
