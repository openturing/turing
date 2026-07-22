# Data Model — Configuração de Campos por Perfil

## 1. Entidade (JÁ EXISTENTE — Fase 7.1 do 002)
`TurProfileFieldConfig` — **um override por (perfil × campo)**. Tabela `tur_profile_field_config`.

| Coluna | Tipo | Nulo? | Observação |
|--------|------|-------|------------|
| `id` | VARCHAR(255) | não | UUID atribuído no runtime (`TurAssignableUuidGenerator`) |
| `profile_id` | VARCHAR(255) | não | FK → `tur_profile.id`, `ON DELETE CASCADE` |
| `field_ext_id` | VARCHAR(255) | não | FK → `tur_sn_site_field_ext.id`, `ON DELETE CASCADE` |
| `facet` | INT | **sim** | 1/0; `null` herda global |
| `facet_name` | VARCHAR(50) | **sim** | `null` herda global |
| `facet_sort` | VARCHAR(50) | **sim** | enum `TurSNSiteFacetFieldSortEnum` (STRING) |
| `facet_type` | VARCHAR(50) | **sim** | enum `TurSNSiteFacetFieldEnum` (STRING) |
| `facet_item_type` | VARCHAR(50) | **sim** | enum `TurSNSiteFacetFieldEnum` (STRING) |
| `facet_range` | VARCHAR(50) | **sim** | enum `TurSNSiteFacetRangeEnum` (STRING) |
| `secondary_facet` | BOOLEAN | **sim** | `null` herda global |
| `show_all_facet_items` | BOOLEAN | **sim** | `null` herda global |

- **Unique:** `uq_tur_profile_field_config_profile_field (profile_id, field_ext_id)`.
- **Nulo = herda** o valor global do `TurSNSiteFieldExt` (campo já é por site).
- **Não** há colunas de schema/índice (fora de escopo — D1/D2).

### Diagrama
```
TurProfile (tur_profile) 1 ───────── N TurProfileFieldConfig (tur_profile_field_config) N ───────── 1 TurSNSiteFieldExt
   (global, spec 002)                     (override por perfil × campo)                               (config GLOBAL do campo, por site)
```
Comparar com o 002: `TurProfile 1─N TurProfileFacetOrdering` (ordenação por perfil×site). Este 003 é a
**2ª configuração** anexada ao mesmo `TurProfile` (FR-19 do 002 — extensibilidade).

## 2. Liquibase (JÁ EXISTENTE)
`v2026.2.7.10.yaml` cria `tur_profile_field_config` (idempotente, incluído no master). **Nenhuma nova
migração** é necessária para este spec, salvo índice auxiliar se o profiling indicar (não previsto).

## 3. Resolução do config efetivo
`efetivo(perfil, campo)` por atributo `a`:
```
a_efetivo = (override != null && override.a != null) ? override.a : campo.a (global)
```
- **Leitura em lote (runtime/console):** `findByTurProfileAndTurSNSiteFieldExtIn(perfil, camposDoSite)`
  → `Map<fieldExtId, TurProfileFieldConfig>`; para cada campo, aplica a regra acima.
- **Perfil Padrão:** por D3 nunca grava override → `efetivo == global`. Fallback de runtime (perfil
  ausente/inexistente) também resolve para o global. Um só "lugar de verdade" para o global.

## 4. Cache (NFR-01)
Adicionar no `TurProfileFieldConfigRepository` (entidade **flat**, sem coleção LAZY navegada → cache seguro):
- `@Cacheable` em `findByTurProfileAndTurSNSiteFieldExtIn` e `findByTurProfileAndTurSNSiteFieldExt`.
- `@CacheEvict(allEntries = true)` em `save`/`delete` (padrão do 002 §4i; escrita rara vs. leitura).

## 5. Escrita (serviço)
- **Perfil não-padrão:** upsert em `TurProfileFieldConfig` (uma linha por perfil×campo). Se **todos** os
  overrides do campo forem limpos (todos `null`), **remover** a linha (mantém a tabela enxuta; herança
  volta ao global).
- **Perfil Padrão (D3):** escrever nas colunas de faceta do `TurSNSiteFieldExt` (global) — **sem** criar
  linha em `TurProfileFieldConfig`. Reusa a lógica de update do campo (subconjunto de faceta apenas).

## 6. Invariантes
- Nunca existe linha `TurProfileFieldConfig` para o perfil **Padrão**.
- `(profile_id, field_ext_id)` único; excluir perfil ou campo remove os overrides em cascata (FK).
- Atributos fora de escopo nunca são gravados por perfil.
