# 003 — Configuração de Campos por Perfil

Adiciona o seletor de **Perfil** (o mesmo que já existe em **Facetas**) à aba **Campos** de um site
de Navegação Semântica. Com um perfil selecionado, cada campo pode ter suas **configurações de faceta**
específicas daquele perfil — ou **herdar** o valor global. O runtime de busca passa a montar as facetas
respeitando a config **efetiva** do perfil aplicado (`profile`/`SET_PROFILE`).

## Relação com os specs anteriores
- Evolução direta do [002 — Perfis Genéricos em "Gerenciamento"](../002-perfis-genericos-gerenciamento/).
  O 002 tornou o perfil **global** (`TurProfile`) e trouxe a **ordenação** de facetas por (perfil × site).
- Este 003 **absorve e conclui a Fase 7** do 002 (FR-22..25). O modelo (`TurProfileFieldConfig`,
  repositório e Liquibase `v2026.2.7.10`) **já foi implementado** na Fase 7.1 do 002 e é **reaproveitado**
  aqui — este spec cobre o que falta: **serviço, API, runtime e frontend**.
- Perfil continua sendo criado/gerenciado em **Gerenciamento** (002); aqui ele só é **selecionado** em
  Campos para anexar a 2ª configuração (a 1ª foi a ordenação, em Facetas).

## Decisões travadas (com o Arthur, via AskUserQuestion — 2026-07-17)
- **D1 — Escopo por-perfil:** **só as configurações de faceta** do campo (`facet` on/off, `facetName`,
  `facetSort`, `facetType`, `facetItemType`, `facetRange`, `secondaryFacet`, `showAllFacetItems`).
  `type`/`enabled`/`multiValued`/HL/MLT/`required`/`defaultValue` **continuam globais** — obrigatório,
  pois há um **único core Solr** compartilhado por todos os perfis.
- **D2 — Perfil "Padrão":** editar Campos com o **Padrão** selecionado grava no **campo global**
  (`TurSNSiteFieldExt`) — espelho, mesmo padrão **DT-6** da ordenação. Perfis não-padrão gravam
  **overrides** em `TurProfileFieldConfig`. Um único lugar de verdade para o "global".
- **Herança:** override ausente (`null`) = **herda** o valor global do campo.

## Documentos
- [`spec.md`](spec.md) — problema, decisões, requisitos (FR/NFR), critérios de aceite.
- [`research.md`](research.md) — pontos de integração no código (Campos, runtime de facetas, snapshot).
- [`data-model.md`](data-model.md) — `TurProfileFieldConfig` (já existente) + resolução do config efetivo.
- [`contracts/field-profile-api.md`](contracts/field-profile-api.md) — API `/api/sn/{snSiteId}/field/profile/{profileId}`.
- [`plan.md`](plan.md) — fases de implementação.
- [`tasks.md`](tasks.md) — checklist por fase.
- [`quickstart.md`](quickstart.md) — roteiro de validação manual.
- [`HANDOFF.md`](HANDOFF.md) — estado consolidado para retomada.

> **Status:** especificação. Modelo (7.1 do 002) pronto; serviço/API/runtime/frontend a implementar.
