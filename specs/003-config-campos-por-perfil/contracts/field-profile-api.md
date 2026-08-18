# Contrato — API de Configuração de Campos por Perfil

Base: **`/api/sn/{snSiteId}/field/profile/{profileId}`** (novo controller
`TurSNSiteFieldProfileAPI`). Segue o padrão do console (`/api/sn/{site}/...`). Segurança:
`@Secured({"ROLE_ADMIN","SN_VIEW"})` na leitura, `SN_EDIT` na escrita (FR-10).

> O modo **global (Padrão)** continua usando o controller existente
> `TurSNSiteFieldExtAPI` (`/api/sn/{snSiteId}/field/ext`). Este contrato cobre só o modo **perfil**.

## GET `/api/sn/{snSiteId}/field/profile/{profileId}`
Lista os campos do site com o config de faceta **efetivo** para o perfil, marcando o que é herdado.

**200** → `TurSNSiteFieldProfileConfigDto[]`:
```jsonc
[
  {
    "fieldExtId": "…",
    "name": "catedras",
    "type": "STRING",            // global (somente leitura no modo perfil)
    "enabled": 1,                // global (somente leitura)
    "effective": {               // override ∪ global (o que o runtime aplicaria)
      "facet": 1,
      "facetName": "Cátedras",
      "facetSort": "COUNT",
      "facetType": "DEFAULT",
      "facetItemType": "DEFAULT",
      "facetRange": "DISABLED",
      "secondaryFacet": false,
      "showAllFacetItems": false
    },
    "overridden": {              // true = veio do override do perfil; false = herdado do global
      "facet": true,
      "facetName": true,
      "facetSort": false,
      "facetType": false,
      "facetItemType": false,
      "facetRange": false,
      "secondaryFacet": false,
      "showAllFacetItems": false
    },
    "global": {                  // valores globais do campo, para exibir "herdado: <valor>"
      "facet": 0,
      "facetName": "catedras",
      "facetSort": "COUNT",
      "facetType": "DEFAULT",
      "facetItemType": "DEFAULT",
      "facetRange": "DISABLED",
      "secondaryFacet": false,
      "showAllFacetItems": false
    }
  }
]
```
- Se `profileId` for o **Padrão**, `effective == global`, `overridden` tudo `false` (o modo Padrão
  edita via `/field/ext`; este GET ainda funciona como leitura consistente).

## PUT `/api/sn/{snSiteId}/field/profile/{profileId}/{fieldExtId}`
Grava o override de um campo para o perfil (upsert). Body = **request record sem primitivos**
(`Integer`/`Boolean`), `null` = herdar:
```jsonc
// TurProfileFieldConfigRequest
{
  "facet": 1,                 // null → herda
  "facetName": "Cátedras",    // null → herda
  "facetSort": "COUNT",       // null → herda
  "facetType": "DEFAULT",
  "facetItemType": "DEFAULT",
  "facetRange": "DISABLED",
  "secondaryFacet": false,
  "showAllFacetItems": false
}
```
**Regras:**
- `profileId` = **Padrão** → grava no **campo global** (`TurSNSiteFieldExt`), subconjunto de faceta
  apenas (D3); **não** cria `TurProfileFieldConfig`.
- `profileId` **não-padrão** → upsert em `TurProfileFieldConfig`. Se **todos** os campos vierem `null`
  (nada sobrescrito), **remove** a linha (volta a herdar).
- **200** → o `TurSNSiteFieldProfileConfigDto` do campo (efetivo recalculado).
- **404** se site/perfil/campo não existir; **403** sem `SN_EDIT`.

## DELETE `/api/sn/{snSiteId}/field/profile/{profileId}/{fieldExtId}`
Remove **todos** os overrides do campo para o perfil (volta a herdar o global).
- Perfil **Padrão** → **409** (não há override a remover; o global se edita em `/field/ext`).
- **200** → `true`.

## Notas
- Enums serializados como STRING (nomes do enum).
- `@CacheEvict` do `TurProfileFieldConfigRepository` roda no PUT/DELETE (NFR-01).
- O DTO de resposta é **derivado** (efetivo/overridden/global) — não é a entidade crua; assim a UI mostra
  "herdado" sem lógica duplicada no cliente.
