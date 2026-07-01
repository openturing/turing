# 001 — Perfis de Ordenação de Facetas

Sistema de **perfis** para a configuração de ordenação de facetas do Semantic Navigation:
múltiplas ordenações nomeadas por site, selecionáveis na busca via parâmetro `facetProfile`,
com fallback para o perfil padrão **"Geral"** (que herda a ordenação atual via migração).

## Decisões travadas
- Perfil controla **só a ordem** das facetas (`facetPosition`).
- Seleção em runtime por **parâmetro `facetProfile`** na busca + **fallback** ao padrão.
- Perfis **por SN site**.
- Perfil **"Geral"** criado por migração herdando a ordenação atual.

## ▶ Retomada de sessão

**Comece por [`HANDOFF.md`](HANDOFF.md)** — estado consolidado de tudo que foi feito (fases,
correções, gotchas, pendências, arquivos). É o ponto de partida para continuar em uma nova conversa
sem perder o histórico.

## Documentos (ordem de leitura)
1. [`spec.md`](spec.md) — o quê e por quê (requisitos, histórias, critérios de aceite).
2. [`research.md`](research.md) — análise do estado atual + decisões técnicas + Fase 0.
3. [`data-model.md`](data-model.md) — entidades novas, alterações, migração.
4. [`contracts/facet-profile-api.md`](contracts/facet-profile-api.md) — contratos REST.
5. [`plan.md`](plan.md) — **fases de implementação** (0 a 5) por camada.
6. [`tasks.md`](tasks.md) — tarefas numeradas por fase.
7. [`quickstart.md`](quickstart.md) — cenários de validação end-to-end.

## Resumo da mudança central
A posição da faceta sai de dentro de `TurSNSiteFieldExt`/`TurSNSiteCustomFacet` e passa para uma
junção **perfil × faceta → posição** (`TurSNSiteFacetProfileItem`). O runtime continua ordenando
em `TurSolrResultProcessor.processSEResultsFacet`, mas o mapa de posições passa a ser resolvido
pelo perfil escolhido (com fallback ao padrão).

> Não confundir com a demanda de *facetas por templateName* (configuração por template do AEM),
> que é separada.
