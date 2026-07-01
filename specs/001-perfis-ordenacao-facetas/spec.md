# Spec 001 — Perfis de Ordenação de Facetas

- **Feature**: Perfis de ordenação de facetas para Semantic Navigation (SN)
- **Status**: Draft (especificação aprovada para planejamento)
- **Autor**: Arthur Nascimento
- **Projeto-alvo**: `turing` (turing-app backend + frontend/apps/turing-app)
- **Versão de referência**: 2026.2.7
- **Data**: 2026-06-25

---

## 1. Resumo

Hoje cada SN site possui **uma única** ordenação de facetas, gravada diretamente no campo
`facetPosition` de cada faceta (`TurSNSiteFieldExt.facetPosition` e
`TurSNSiteCustomFacet.facetPosition`). Não há como manter mais de uma configuração de ordem
nem alternar entre elas.

Esta feature introduz **perfis de ordenação**: conjuntos nomeados de posições de faceta,
pertencentes a um SN site. O consumidor da busca escolhe qual perfil aplicar através de um
parâmetro na requisição de busca; quando nenhum perfil é informado (ou o informado não
existe), aplica-se o **perfil padrão/Geral** do site.

A ordenação atual de cada site é preservada: na migração ela se torna o perfil **"Geral"**
(padrão) daquele site.

## 2. Decisões de produto (travadas)

| # | Decisão | Escolha |
|---|---------|---------|
| D1 | O que o perfil controla | **Apenas a ordem das facetas** (`facetPosition`). Sort de valores (`facetSort`) e visibilidade (`enabled`) permanecem como hoje, fora do perfil. |
| D2 | Seleção em runtime | **Parâmetro na busca** (`facetProfile`) + **fallback** para o perfil padrão quando ausente/inexistente. |
| D3 | Escopo dos perfis | **Por SN site**. Cada site tem seu próprio conjunto de perfis. |
| D4 | Perfil padrão inicial | **Migrar a ordenação atual** para um perfil "Geral" (padrão) por site, via Liquibase. |

## 3. Glossário

- **Faceta**: filtro de busca exibido na navegação (ex.: categoria, autor, modalidade).
  Pode ser uma *faceta de campo* (`TurSNSiteFieldExt` com `facet = 1`) ou uma *faceta
  customizada* (`TurSNSiteCustomFacet`).
- **Posição da faceta (`facetPosition`)**: inteiro que define a ordem da faceta na lista de
  facetas da resposta de busca (1 = primeira).
- **Perfil de ordenação**: conjunto nomeado de posições de faceta, pertencente a um site.
- **Perfil padrão / "Geral"**: o perfil aplicado quando a busca não informa um perfil válido.
  Exatamente um por site.
- **Sort da faceta (`facetSort`)**: ordem dos *valores dentro* de uma faceta
  (`COUNT`/`ALPHABETICAL`/`DEFAULT`). **Fora do escopo** desta feature.

## 4. Personas

- **Administrador do site (Console)**: cria, renomeia e exclui perfis; reordena as facetas de
  cada perfil via drag-and-drop.
- **Consumidor da busca (SDK/API/portal)**: envia o parâmetro `facetProfile` para receber as
  facetas na ordem desejada.

## 5. Histórias de usuário

- **US1** — Como administrador, quero criar vários perfis de ordenação de facetas para um site,
  para atender contextos distintos (ex.: "Geral", "Cursos EE", "Home").
- **US2** — Como administrador, quero reordenar as facetas dentro de um perfil por
  drag-and-drop, vendo apenas o efeito naquele perfil.
- **US3** — Como administrador, quero que a ordenação que já existe hoje continue funcionando
  sem nenhuma ação minha (vira o perfil "Geral").
- **US4** — Como consumidor da busca, quero informar `facetProfile=<nome>` e receber as facetas
  na ordem daquele perfil.
- **US5** — Como consumidor da busca, quero que, ao não informar perfil (ou informar um perfil
  inexistente), as facetas venham na ordem do perfil "Geral" — sem erro.

## 6. Requisitos funcionais

### Gestão de perfis (Console / backend)
- **FR-01**: O sistema deve permitir **criar** um perfil de ordenação associado a um SN site,
  com `name` único dentro do site.
- **FR-02**: O sistema deve permitir **renomear** e **excluir** perfis **não-padrão**.
- **FR-03**: O sistema **não deve** permitir excluir o perfil padrão; para excluir, outro perfil
  deve antes ser promovido a padrão.
- **FR-03b**: O sistema **não deve** permitir **renomear** o perfil padrão — ele é um perfil fixo e
  protegido (a descrição pode ser editada, o nome não). Tentativa de renomear via API → `409`.
- **FR-04**: O sistema deve garantir **exatamente um** perfil padrão por site em qualquer momento.
- **FR-05**: Ao criar um site novo (ou para sites existentes sem perfil), o sistema deve garantir
  a existência do perfil padrão "Geral". A **listagem** de perfis (`GET /profile`) também o
  semeia sob demanda, de modo que o perfil padrão **já venha criado** ao abrir a tela — o Console
  nunca exibe um seletor vazio e "nenhum perfil selecionado" sempre mapeia para o padrão.

### Ordenação por perfil (Console / backend)
- **FR-06**: O sistema deve listar todas as facetas habilitadas do site (de campo e customizadas)
  com a posição **segundo o perfil selecionado**.
- **FR-07**: O sistema deve persistir o reordenamento de facetas **no perfil selecionado**, sem
  afetar os demais perfis.
- **FR-08**: Facetas habilitadas do site que ainda não tenham posição em um perfil devem aparecer
  ao final da lista, em ordem estável (ex.: por rótulo), e podem ser posicionadas.

### Runtime de busca (backend)
- **FR-09**: A API de busca deve aceitar um parâmetro opcional `facetProfile` (nome do perfil).
- **FR-10**: Quando `facetProfile` for informado e existir no site, as facetas da resposta devem
  ser ordenadas conforme as posições daquele perfil.
- **FR-11**: Quando `facetProfile` for ausente, vazio ou inexistente, deve-se aplicar o perfil
  padrão "Geral" (fallback), **sem erro**.
- **FR-12**: O comportamento de sort dos valores (`facetSort`) e de visibilidade das facetas
  permanece inalterado.

### Frontend (Console)
- **FR-13**: A tela de ordenação de facetas deve exibir um **seletor de perfil** e operar a lista
  drag-and-drop sobre o perfil selecionado.
- **FR-14**: A tela deve permitir **criar / renomear / excluir / definir padrão** a partir da
  própria tela de ordenação. Para o **perfil padrão**, as ações **Renomear**, **Excluir** e
  **Definir como padrão** ficam **ocultas** (só "Novo perfil" e a reordenação permanecem).
- **FR-14b**: No seletor, o perfil padrão é exibido com o rótulo localizado **"Padrão" / "Default"**
  (não pelo nome interno "Geral"), e vem **pré-selecionado** quando nenhum outro está selecionado.
- **FR-15**: Textos devem ser internacionalizados (en / pt), seguindo o padrão de i18n do projeto.
- **FR-15b**: A **página de preview de busca** deve oferecer um **campo de texto "Perfil de
  facetas"** que define o parâmetro `facetProfile` da busca. Vazio = perfil padrão. O valor é
  refletido na URL (`?facetProfile=`) e dispara nova busca. Implica estender o SDK
  (`@viglet/turing-react-sdk`): `SearchParams.facetProfile` + `buildQueryString` + round-trip de URL.

### Compatibilidade
- **FR-16**: Os endpoints atuais `GET /api/sn/{snSiteId}/facet` e
  `PUT /api/sn/{snSiteId}/facet/ordering` devem continuar funcionando, operando sobre o **perfil
  padrão** do site (retrocompatibilidade).

## 7. Requisitos não-funcionais

- **NFR-01 (Performance)**: A resolução do perfil em runtime não pode regredir a latência de
  busca. Caches existentes (ex.: `findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition`) e o
  mecanismo de **snapshot** do site devem ser respeitados/atualizados (ver `research.md`).
- **NFR-02 (Integridade)**: Excluir uma faceta (campo/custom) deve remover suas entradas em todos
  os perfis (cascade / orphan removal).
- **NFR-03 (Migração sem perda)**: Nenhuma ordenação existente pode ser perdida na migração.
- **NFR-04 (Convenções)**: Seguir as convenções do `agents.md` (JPA literal camelCase ou
  snake_case conforme a tabela, Liquibase com novo arquivo idempotente, `@since 2026.2.7`,
  construtor injection, React Query v5, rotas `React.lazy`).

## 8. Critérios de aceite (alto nível)

- **AC-01**: Em um site com ordenação pré-existente, após a migração existe um perfil "Geral"
  (padrão) com exatamente as mesmas posições de antes, e a busca sem `facetProfile` devolve as
  facetas na mesma ordem de antes.
- **AC-02**: Criar um segundo perfil, reordenar nele e buscar com `facetProfile=<2º perfil>`
  devolve a nova ordem; buscar sem parâmetro continua devolvendo a ordem do "Geral".
- **AC-03**: `facetProfile` inexistente devolve a ordem do "Geral" sem erro (HTTP 200).
- **AC-04**: Não é possível excluir o perfil padrão (erro tratado); é possível promover outro
  perfil a padrão e então excluir o antigo.
- **AC-04b**: Não é possível **renomear** o perfil padrão (API → `409`); no Console o botão
  "Renomear" não aparece para ele.
- **AC-04c**: Ao abrir a tela de facetas de um site que ainda não tinha perfis, o perfil padrão
  já aparece criado e selecionado, rotulado como "Padrão"/"Default".
- **AC-05**: Excluir uma faceta do site remove suas entradas em todos os perfis.
- **AC-06**: Suite de testes backend (`mvn test -pl turing-app -Dskip.npm=true`) verde e
  compilação do frontend ok.

## 9. Fora de escopo

- Sort de valores (`facetSort`) por perfil.
- Visibilidade / habilitar-desabilitar facetas por perfil.
- Perfis globais (compartilhados entre sites).
- Seleção de perfil por "perfil ativo no site" (escolhemos parâmetro na busca — D2).
- Regras de seleção automática de perfil por template do AEM (é outra demanda — ver memória
  `project_facetas_por_template`).
- Resolução por perfil em **Elasticsearch e Lucene** (decisão Fase 0: **apenas Solr**). Esses
  motores continuam usando o `facetPosition` legado, que é mantido espelhando o perfil padrão
  (research DT-6).
- Exposição de `facetProfile` nos **SDKs** (`turing-java-sdk`, `react-sdk`) — apenas API REST
  nesta entrega.

## 10. Questões resolvidas (Fase 0)

> Detalhes e referências de código em `research.md §3-§4`.

- **Q1 (snapshot) ✅**: a ordem final das facetas é resolvida **fora** do snapshot, no
  `TurSolrResultProcessor` (lê do DB). As entidades de perfil **não** levam `@EntityListeners` de
  eviction.
- **Q2 (propagação) ✅**: `facetProfile` segue o caminho do parâmetro `sort`; vários beans ficam
  em `turing-commons`.
- **Q3 (identificação) ✅**: o parâmetro usa o **nome** do perfil; resolvido para id internamente;
  `name` único por site (constraint).
- **Backends ✅**: resolução por perfil **apenas no Solr**. ES/Lucene usam o `facetPosition`
  legado, mantido como espelho do perfil padrão (**FR-17 / DT-6**).
- **Clientes ✅**: apenas API REST (sem SDKs nesta entrega).

### Requisito adicional derivado da Fase 0
- **FR-17**: ao salvar a ordenação do **perfil padrão** ou promover outro perfil a padrão, o
  sistema deve sincronizar o `facetPosition` legado das facetas, garantindo que leitores legados
  (ES/Lucene/import) reflitam o perfil padrão.
