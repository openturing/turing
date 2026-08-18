# API Contracts — Perfis de Ordenação de Facetas

Base atual: `/api/sn/{snSiteId}/facet` (controller `TurSNSiteFacetedFieldAPI`).
Novos recursos: `/profile` e `/profile/{profileId}/ordering`.

Todos os corpos são JSON. Autenticação/sessão segue o padrão do console (CSRF para writes).

---

## 1. DTOs

### `TurSNSiteFacetProfileDto`
```jsonc
{
  "id": "string",
  "name": "string",
  "description": "string | null",
  "default": true,            // defaultProfile
  "itemCount": 12             // opcional: nº de facetas posicionadas
}
```

### `TurSNSiteFacetOrderingDto` (já existe — reutilizado)
```jsonc
{
  "id": "string",            // id da faceta (fieldExt ou customFacet)
  "name": "string",
  "facetName": "string",
  "facetPosition": 1,
  "fieldExtId": "string | null",
  "fieldExtName": "string | null",
  "customFacet": false
}
```

---

## 2. Gestão de perfis

### `GET /api/sn/{snSiteId}/facet/profile`
Lista os perfis do site.
- **200** → `TurSNSiteFacetProfileDto[]` (o padrão vem com `default=true`).
- Efeito colateral: **semeia o perfil padrão sob demanda** se o site ainda não tiver um (FR-05) —
  a lista nunca volta sem o padrão.

### `POST /api/sn/{snSiteId}/facet/profile`
Cria um perfil.
- **Body**: `{ "name": "Cursos EE", "description": "..." }`
- **201** → `TurSNSiteFacetProfileDto`
- **409** → nome já existe no site.
- Regra: novo perfil **não** nasce padrão (a menos que seja o primeiro do site).
- Itens iniciais: pode nascer vazio (facetas aparecem "sem posição") ou clonar do padrão —
  **decisão**: nascer **clonando o padrão** (melhor UX; ver `plan.md`). Configurável depois.

### `PUT /api/sn/{snSiteId}/facet/profile/{profileId}`
Atualiza nome/descrição.
- **Body**: `{ "name": "...", "description": "..." }`
- **200** → `TurSNSiteFacetProfileDto`
- **409** → nome duplicado.
- **409** → tentativa de **renomear o perfil padrão** (FR-03b). A descrição do padrão pode ser
  alterada desde que o `name` seja enviado inalterado.

### `PUT /api/sn/{snSiteId}/facet/profile/{profileId}/default`
Promove o perfil a padrão (desmarca o anterior).
- **200** → `TurSNSiteFacetProfileDto[]` (lista atualizada).

### `DELETE /api/sn/{snSiteId}/facet/profile/{profileId}`
Exclui um perfil.
- **204** → ok.
- **409** → tentativa de excluir o **perfil padrão** (FR-03).

---

## 3. Ordenação por perfil

### `GET /api/sn/{snSiteId}/facet/profile/{profileId}/ordering`
Lista todas as facetas habilitadas do site com a posição **segundo o perfil**.
- **200** → `TurSNSiteFacetOrderingDto[]`
  - facetas sem posição no perfil vêm ao final (`facetPosition` nulo ou contínuo), ordenadas por
    rótulo (FR-08).
- **404** → perfil não encontrado no site.

### `PUT /api/sn/{snSiteId}/facet/profile/{profileId}/ordering`
Persiste o reordenamento **no perfil**.
- **Body**: `TurSNSiteFacetOrderingDto[]` (cada item com `id`, `customFacet`, `facetPosition`).
- **200** → `TurSNSiteFacetOrderingDto[]` (estado após salvar).
- Efeito: upsert dos `TurSNSiteFacetProfileItem` do perfil; **não** altera outros perfis nem o
  `facetPosition` legado.
- **Cache**: invalidar caches de ordenação relevantes (ver `@CacheEvict` atual).

---

## 4. Compatibilidade (endpoints legados — FR-16)

Operam **sobre o perfil padrão** do site:

- `GET /api/sn/{snSiteId}/facet` → equivalente a `.../profile/{default}/ordering` (GET).
- `PUT /api/sn/{snSiteId}/facet/ordering` → equivalente a `.../profile/{default}/ordering` (PUT).

Comportamento idêntico ao atual para qualquer cliente existente.

---

## 5. Runtime de busca (FR-09..FR-11)

Parâmetro **opcional** `facetProfile` (nome do perfil) na API de busca SN
(ex.: `GET /api/sn/{snSiteName}/search?q=...&facetProfile=Cursos%20EE`).

- `facetProfile` informado e existente no site → ordena facetas por aquele perfil.
- ausente / vazio / inexistente → usa o perfil padrão "Geral" (**sem erro**, HTTP 200).
- Demais comportamentos de facet (sort de valores, visibilidade) inalterados.

> **Confirmado na Fase 0 (research Q2)**: `facetProfile` segue a mesma cadeia do parâmetro `sort`
> — `TurSNParamType.FACET_PROFILE` (`turing-commons`) → `TurSNSearchParams` → `TurSEParameters`
> (+ `TurSNSitePostParamsBean` para POST) → `TurSolrQueryContext` → `processSEResultsFacet`.
> Escopo: **apenas Solr**; **sem SDKs** (Java/JS) nesta entrega.

---

## 6. Resumo de status codes

| Cenário | Código |
|---------|--------|
| Operação ok (GET/PUT) | 200 |
| Criação | 201 |
| Exclusão | 204 |
| Perfil/recurso inexistente (gestão) | 404 |
| Nome de perfil duplicado | 409 |
| Excluir perfil padrão | 409 |
| Renomear perfil padrão | 409 |
| Busca com `facetProfile` inexistente | 200 (fallback ao padrão) |
