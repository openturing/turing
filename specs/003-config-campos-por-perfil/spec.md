# Spec — Configuração de Campos por Perfil

## 1. Contexto e problema
No [002](../002-perfis-genericos-gerenciamento/spec.md), o perfil virou **global** e ganhou, em
**Facetas**, uma configuração por **(perfil × site)** — mas apenas a **ordenação** das facetas.

As demais configurações que definem **como um campo se comporta como faceta** (ligar/desligar faceta,
rótulo, tipo de ordenação de valores, tipo AND/OR, range de data, faceta secundária, "mostrar todos os
itens") vivem na aba **Campos** e hoje são **globais**: valem igual para toda busca, independente do
perfil. Ou seja, dois perfis não conseguem exibir o mesmo campo como faceta de formas diferentes
(ex.: perfil "Cursos EE" mostra `catedras` como faceta; perfil "Institucional" a esconde).

Queremos trazer o **seletor de Perfil** — o mesmo que já existe em Facetas — para **Campos**, de modo
que **cada perfil** tenha sua configuração de faceta por campo: **específica** daquele perfil ou
**herdada** do global.

## 2. Objetivo
Permitir, na aba **Campos**, **selecionar um perfil** e editar, por campo, as **configurações de faceta
por-perfil** (override), com **herança** do global quando não sobrescrito; e fazer o **runtime de busca**
montar as facetas respeitando a config **efetiva** do perfil aplicado à requisição
(`profile`/regra `SET_PROFILE`), com fallback ao Padrão/global — sem erro.

## 3. Decisões (travadas)
| # | Decisão | Valor |
|---|---------|-------|
| D1 | Escopo por-perfil | **Só configurações de faceta** do campo (lista em FR-02). Schema/índice (`type`, `enabled`, `multiValued`, HL, MLT, `required`, `defaultValue`) **continuam globais**. |
| D2 | Justificativa do D1 | **Um único core Solr** por locale é compartilhado por todos os perfis; não é possível o mesmo campo ter `type`/`multiValued` diferentes por perfil no mesmo índice. |
| D3 | Perfil "Padrão" | Editar Campos sob o **Padrão** grava no **campo global** (`TurSNSiteFieldExt`) — espelho **DT-6** (igual à ordenação). Perfis não-padrão gravam **override** (`TurProfileFieldConfig`). |
| D4 | Herança | Override `null` (coluna nula) = **herda** o valor global do campo. Config **efetivo** = override ∪ global. |
| D5 | Reuso | Reaproveita `TurProfileFieldConfig` + repo + Liquibase `v2026.2.7.10` (já criados na Fase 7.1 do 002). |
| D6 | Runtime | O perfil aplicado à busca (`profile`/`SET_PROFILE`) define o config efetivo das facetas (on/off + sort/type/itemType/range/secondaryFacet/showAll). Fallback: Padrão → global. Solr apenas. |

## 4. Glossário
- **Config de campo (global)** — os campos de faceta do `TurSNSiteFieldExt` (o que a aba Campos edita hoje).
- **Override de campo por perfil** — linha em `TurProfileFieldConfig` para um par (perfil × campo) com
  colunas nuláveis; `null` herda o global.
- **Config efetivo (perfil, campo)** — resultado da resolução: para cada atributo, usa o override do
  perfil se presente, senão o valor global do campo.

## 5. Personas / histórias
- **Administrador do site (Campos):** seleciona um perfil e ajusta, por campo, se ele é faceta e como
  (rótulo/sort/tipo/range/…), **para aquele perfil**; campos não tocados herdam o global (com indicação
  visual de "herdado").
- **Consumidor da busca (AEM/portal):** com `profile=<nome>` (ou via regra `SET_PROFILE`), recebe as
  facetas conforme o config efetivo daquele perfil; sem perfil válido, recebe o do **Padrão**/global.

## 6. Requisitos funcionais

### Campos com seletor de perfil (frontend + API)
- **FR-01:** A aba **Campos** ganha um **seletor de Perfil** (mesmo componente/UX de Facetas): lista os
  perfis **globais**; pré-seleciona o **Padrão**.
- **FR-02:** Com um perfil selecionado, cada campo pode sobrescrever **apenas** estas configurações de
  faceta: `facet` (liga/desliga), `facetName`, `facetSort`, `facetType`, `facetItemType`, `facetRange`,
  `secondaryFacet`, `showAllFacetItems`.
- **FR-03:** Um atributo **não sobrescrito herda** o valor global do campo; a UI indica visualmente o
  que é **herdado** vs **sobrescrito** e permite **limpar** um override (voltar a herdar).
- **FR-04:** Com o **Padrão** selecionado, salvar grava no **campo global** (`TurSNSiteFieldExt`) — não
  cria override. Com perfil não-padrão, salvar grava/atualiza `TurProfileFieldConfig` (upsert); limpar
  todos os overrides do campo remove a linha.
- **FR-05:** As configurações **fora de escopo** (type/enabled/multiValued/HL/MLT/required/defaultValue)
  **não** aparecem como por-perfil — permanecem editáveis só no modo global (Padrão) e são somente-leitura
  ("global") sob outros perfis.

### Runtime de busca (backend / Solr)
- **FR-06:** A busca resolve o config **efetivo** (perfil aplicado ∪ global) de cada campo ao montar as
  facetas: seleção de facetas (`facet` on/off), `facetSort`, `facetType`, `facetItemType`, `facetRange`,
  `secondaryFacet`, `showAllFacetItems`.
- **FR-07:** *Fallback sem erro (HTTP 200):* `profile` ausente/vazio/inexistente **ou** sem override do
  campo → usa o global (que é o espelho do Padrão, por D3). Nunca derruba a busca.
- **FR-08:** A **ordenação** das facetas (spec 002) e a **config efetiva** (este spec) são resolvidas para
  o **mesmo** perfil aplicado, de forma consistente.
- **FR-09:** Comportamento inalterado quando nenhum perfil tem override (100% retrocompatível com o global).

### Segurança / i18n
- **FR-10:** Endpoints novos sob `/api/sn/{snSiteId}/field/profile/{profileId}` nascem com
  `@Secured({"ROLE_ADMIN","SN_VIEW"})` (leitura) e `SN_EDIT` (escrita) — padrão do 002 §4j.
- **FR-11:** Textos internacionalizados (en/pt).

## 7. Requisitos não-funcionais
- **NFR-01 (Performance):** resolução do config efetivo em runtime não pode adicionar N+1; leitura dos
  overrides do perfil em **lote** por (perfil, campos do site) e **`@Cacheable`** (entidade flat, sem
  coleção LAZY → cache seguro), com `@CacheEvict` na escrita. Igual ao padrão de cache do 002 §4i.
- **NFR-02 (Snapshot):** a resolução deve conviver com o `TurSNSiteSearchSnapshot` (que hoje cacheia
  `enabledFields`/`enabledFacets` **globais**). O snapshot **não** pode "vazar" config global quando um
  perfil altera `facet` on/off — a sobreposição do perfil ocorre **após** o snapshot (ver `research §3`).
- **NFR-03 (Portabilidade):** nenhuma mudança de schema além do já existente (`v2026.2.7.10`); se algo
  faltar, changelog idempotente/portável (`boolean` minúsculo; ids `VARCHAR(255)`).
- **NFR-04 (Segurança):** ver FR-10; a busca pública só **aplica** o config (não lista/gera overrides).

## 8. Critérios de aceite
- **AC-01:** Em Campos, com **Padrão** selecionado, alterar uma config de faceta grava no campo global e
  reflete na busca sem perfil (retrocompat).
- **AC-02:** Selecionar um perfil não-padrão e ligar/desligar `facet` de um campo (ou mudar sort/type/…)
  cria/atualiza override **só** daquele perfil; outros perfis e o global ficam intactos.
- **AC-03:** Um campo sem override herda o global; a UI mostra "herdado" e permite limpar um override.
- **AC-04:** Busca com `profile=<perfil>` monta as facetas conforme o config efetivo do perfil
  (ex.: campo ligado como faceta só naquele perfil aparece; escondido some).
- **AC-05:** `profile` ausente/inexistente → facetas conforme o global/Padrão, HTTP 200, sem erro.
- **AC-06:** Regra de Busca `SET_PROFILE` (002 §4k) também troca o config efetivo, consistente com a
  ordenação.
- **AC-07:** Endpoints exigem `SN_VIEW`/`SN_EDIT`; suíte backend verde e frontend compila.

## 9. Fora de escopo
- Configurações de **schema/índice** por perfil (type/enabled/multiValued/HL/MLT/required/defaultValue) —
  exigiria índices separados por perfil.
- **3º tipo** de configuração anexável ao perfil (o modelo segue extensível, mas não implementamos outro).
- Seleção **automática** de perfil por template do AEM (é o [projeto de facetas por template](../001-perfis-ordenacao-facetas/), à parte).
- `facetLocales` (rótulos por locale) por perfil — segue global (incremental; não bloqueia).

## 10. Riscos
| Risco | Mitigação |
|-------|-----------|
| Snapshot servir `enabledFacets` globais e ignorar o override do perfil | Aplicar a sobreposição do perfil **depois** do snapshot, no ponto onde as facetas são montadas (research §3); testes cobrindo on/off por perfil. |
| N+1 ao ler overrides por campo na busca | Leitura em lote + `@Cacheable` por (perfil, site); evict na escrita (NFR-01). |
| Divergência entre ordenação (002) e config efetivo (003) | Ambos resolvem o **mesmo** perfil aplicado; ponto único de resolução do perfil no runtime (research §2). |
| UI confundir "herdado" com "desligado" | Estado visual explícito (badge "herdado" + valor global esmaecido) e ação "limpar override" (FR-03). |
