# Contratos REST — Perfis Genéricos

Dois grupos de endpoints:
- **A. Gerenciamento** (perfis **globais**) — CRUD do perfil neutro.
- **B. Facetas** (config por site) — seleção de perfil global + ordenação de (perfil, site).

> Convenções: Jackson 3 (Spring Boot 4) → records de `@RequestBody` **sem primitivos** (usar um
> record de request separado). `boolean` de resposta exposto como `default` via `@JsonProperty`.

---

## A. Gerenciamento — perfis globais

Base sugerida: `/api/profile` (novo `TurProfileAPI`; controller de nível de sistema).

### `GET /api/profile`
Lista os perfis globais.
- **200** → `TurProfileDto[]` (o padrão vem com `default=true`).
- Efeito colateral: **semeia o "Padrão"** sob demanda se ausente (FR-05).

### `POST /api/profile`
Cria um perfil global.
- **Body**: `{ "name": "Cursos EE", "description": "..." }` (`TurProfileRequest`).
- **201** → `TurProfileDto`
- **409** → nome já existe (global).
- Regra: novo perfil **não** nasce padrão (a menos que seja o primeiro do sistema).

### `PUT /api/profile/{profileId}`
Atualiza nome/descrição.
- **Body**: `TurProfileRequest`
- **200** → `TurProfileDto`
- **409** → nome duplicado.
- **409** → tentativa de **renomear o padrão** (FR-03b). Descrição do padrão editável (mesmo `name`).

### `PUT /api/profile/{profileId}/default`
Promove o perfil a padrão (desmarca o anterior; sincroniza `facetPosition` legado por site — DT-6).
- **200** → `TurProfileDto[]` (lista atualizada).

### `DELETE /api/profile/{profileId}`
Exclui um perfil.
- **204** → excluído.
- **409** → tentativa de excluir o **padrão** (FR-03). (Excluir um perfil remove suas configs de
  facetas em todos os sites via cascade.)

### DTOs
```
TurProfileDto {
  id: string
  name: string
  description: string | null
  default: boolean          // @JsonProperty("default")
}
// (facetOrderingCount informativo foi adiado — adicionar na Fase 5 se a UI precisar)
TurProfileRequest { name: string, description: string | null }   // sem primitivos
```

---

## B. Facetas — config de ordenação por (perfil, site)

Base existente: `/api/sn/{snSiteId}/facet` (`TurSNSiteFacetedFieldAPI`). O **CRUD de perfil sai
daqui** (vai para o grupo A). Ficam só seleção + ordenação.

### `GET /api/sn/{snSiteId}/facet/profile`
Lista os perfis **globais** disponíveis para seleção neste site.
- **200** → `TurProfileDto[]` (padrão marcado). Não cria perfis.

### `GET /api/sn/{snSiteId}/facet/profile/{profileId}/ordering`
Lista as facetas habilitadas do site com a posição segundo **(perfil, site)** (FR-08).
- **200** → `TurSNSiteFacetOrderingDto[]` (facetas sem posição ao fim, ordem estável).
- Fallback de leitura: se não há config de (perfil, site), retorna a do **Padrão** do site; se nem
  isso, o `facetPosition` legado.

### `PUT /api/sn/{snSiteId}/facet/profile/{profileId}/ordering`
Salva a ordenação de **(perfil, site)** (FR-09; upsert da config e itens).
- **Body**: `TurSNSiteFacetOrderingDto[]`
- **200** → lista reordenada.
- Custom facets persistidos via `save()` item-a-item (dispara `@CacheEvict`; NFR-03).
- Se `profileId` é o **padrão**, sincroniza o `facetPosition` legado do site (DT-6).

### Legados (retrocompatível — FR-21)
- `GET /api/sn/{snSiteId}/facet` → equivalente a `.../facet/profile/{padrão}/ordering` (GET).
- `PUT /api/sn/{snSiteId}/facet/ordering` → salva na config do **Padrão** para o site.

### `TurSNSiteFacetOrderingDto` (inalterado do 001)
```
{
  id, name, facetName, facetPosition,
  fieldExtId | null, fieldExtName | null, customFacet: boolean
}
```

---

## C. Runtime de busca
- Param **`profile`** (nome do perfil **global**) em `/api/(v2/)sn/{siteName}/search` (GET/POST).
  **Renomeado** de `facetProfile` (spec 001) → `profile`, para ficar genérico (URL + binding do
  `TurSNSearchParams.profile`; `TurSNParamType.PROFILE="profile"`; SDK `SearchParams.profile`).
- Resolução (backend): nome→id→**Padrão**; depois config de (perfil, **site da busca**); *fallback*
  config do Padrão do site → `facetPosition` legado (FR-13). Solr apenas.

## D. Códigos de status
| Cenário | Código |
|---------|--------|
| Operação ok (GET/PUT) | 200 |
| Criação de perfil | 201 |
| Exclusão de perfil | 204 |
| Perfil/recurso inexistente | 404 |
| Nome de perfil duplicado (global) | 409 |
| Excluir perfil padrão | 409 |
| Renomear perfil padrão | 409 |
| Busca com `profile` inexistente / sem config p/ site | 200 (fallback ao Padrão) |

---

## E. Campos — config de faceta por (perfil, campo) [Fase 7]

Base: `/api/sn/{snSiteId}/field` (fields API). Endpoints novos para o config de campo por perfil.

### `GET /api/sn/{snSiteId}/field/profile/{profileId}`
Lista os campos do site com o config de faceta **efetivo** para o perfil (override ∪ global).
- **200** → lista de campos com `{ facet, facetName, facetSort, facetType, facetItemType,
  facetRange, secondaryFacet, showAllFacetItems, inherited: {..} }` (flags de herança por setting).

### `PUT /api/sn/{snSiteId}/field/profile/{profileId}`
Salva os overrides de faceta por campo (upsert). Override nulo/ausente ⇒ herda o global.
- **200** → lista efetiva atualizada.
- Se `profileId` é o **Padrão** → grava no `TurSNSiteFieldExt` global (espelho, FR-24).
