# Spec — Perfis Genéricos em "Gerenciamento"

## 1. Contexto e problema
No spec [001](../001-perfis-ordenacao-facetas/spec.md), o **perfil** de ordenação de facetas nasceu
**preso ao site** e à tela **"Facetas"** (dentro de *Navegação Semântica*). Isso tem dois limites:

- O perfil só serve para **facetas** — não é reaproveitável para outras configurações.
- O perfil precisa ser **recriado em cada site** — não há reuso entre sites.

Queremos um **perfil genérico e global**: criado uma única vez em **"Gerenciamento"** (seção de
nível de sistema do console) e, em **"Facetas"** de cada site, apenas **selecionado** para receber a
configuração de ordenação daquele site.

## 2. Objetivo
Transformar o perfil em uma entidade **global, neutra e extensível**, gerenciada em "Gerenciamento",
e mover a *ordenação de facetas* para uma **configuração por (perfil × site)** anexada em "Facetas" —
preservando o comportamento de runtime (`profile` na busca) e a proteção do perfil padrão.

## 3. Decisões (travadas)
| # | Decisão | Valor |
|---|---------|-------|
| D1 | Escopo do perfil | **Global (sistema)** — criado em Gerenciamento, reutilizável por todos os sites. |
| D2 | Generalidade | **Genérico/extensível** — perfil neutro; facetas é o 1º *tipo de configuração*. Só facetas agora. |
| D3 | Local de criação | **Gerenciamento** (seção Management do sidebar). CRUD do perfil sai de "Facetas". |
| D4 | Local de uso | **Facetas** (site SN): seleciona um perfil global e configura a ordenação daquele site. |
| D5 | Relação com spec 001 | **Substituir e migrar** — novo modelo substitui o per-site; dados migrados. |
| D6 | Perfil padrão | **"Padrão" global protegido** (não excluir/renomear), *fallback* de runtime. |
| D7 | Runtime | `profile` (nome do perfil **global**) resolve a config de (perfil × site); Solr apenas. |
| D8 | ES/Lucene | Continuam lendo `facetPosition` legado, mantido como **espelho do perfil padrão** (DT-6). |

## 4. Glossário
- **Perfil (global)** — entidade neutra criada em Gerenciamento (`TurProfile`). Tem nome único
  **global**, descrição, e um flag de **padrão** (exatamente um por sistema).
- **Configuração de ordenação de facetas** — instância por **(perfil × site)** que guarda as
  posições das facetas daquele site sob aquele perfil (`TurProfileFacetOrdering` + itens).
- **Perfil padrão / "Padrão"** — o perfil global aplicado quando a busca não informa um perfil
  válido, ou quando o perfil escolhido não tem configuração para o site.

## 5. Personas / histórias
- **Administrador do sistema (Gerenciamento)**: cria, renomeia, exclui e define o padrão dos
  **perfis globais** — uma única vez, para todos os sites.
- **Administrador do site (Facetas)**: em um site, **escolhe** um perfil global e **ordena** as
  facetas daquele site para aquele perfil (drag-and-drop). Não cria/edita perfis aqui.
- **Consumidor da busca (AEM/portal)**: envia `profile=<nome do perfil>` para receber as
  facetas na ordem daquele perfil; sem o parâmetro, recebe a ordem do **Padrão**.

## 6. Requisitos funcionais

### Gerenciamento de perfis (Gerenciamento / backend)
- **FR-01**: Criar um perfil **global** com `name` **único no sistema** e `description` opcional.
- **FR-02**: Renomear e excluir perfis **não-padrão**.
- **FR-03**: **Não** permitir excluir o perfil padrão; promover outro a padrão antes.
- **FR-03b**: **Não** permitir **renomear** o perfil padrão (fixo/protegido). Descrição editável.
- **FR-04**: Garantir **exatamente um** perfil padrão no sistema em qualquer momento.
- **FR-05**: Garantir que o perfil **"Padrão"** exista (bootstrap/ao listar) — já vem criado.
- **FR-06**: A criação/edição de perfis vive na seção **"Gerenciamento"** do console (item novo no
  sidebar), fora de "Facetas".

### Configuração de ordenação por perfil (Facetas / backend)
- **FR-07**: Em um site, **listar os perfis globais** disponíveis para seleção.
- **FR-08**: Listar as facetas habilitadas do site com a posição **segundo o perfil selecionado**
  para **aquele site** (facetas ainda sem posição vão ao fim, ordem estável por rótulo).
- **FR-09**: Persistir o reordenamento no par **(perfil, site)**, sem afetar outros perfis ou sites.
- **FR-10**: Uma faceta excluída/desabilitada do site some das configurações de todos os perfis
  (para aquele site).

### Runtime de busca (backend)
- **FR-11**: A busca aceita `profile` (nome do perfil **global**), como hoje.
- **FR-12**: Com `profile` válido, ordenar as facetas conforme a config de **(perfil, site)**.
- **FR-13**: *Fallback sem erro* (HTTP 200): `profile` ausente/vazio/inexistente **ou** perfil
  sem config para o site → usar a config do **Padrão** para o site → se ausente, `facetPosition`
  legado.
- **FR-14**: Sort de valores (`facetSort`) e visibilidade das facetas permanecem inalterados.

### Frontend (Console)
- **FR-15**: **Gerenciamento** ganha uma tela de **Perfis** (novo item no sidebar da seção
  Management) com CRUD + definir padrão; o padrão é exibido como **"Padrão"/"Default"** e tem as
  ações renomear/excluir/definir-padrão ocultas.
- **FR-16**: **Facetas** passa a **selecionar** um perfil global (Select) e operar o drag-and-drop
  da ordenação sobre o par (perfil, site). As ações de **criar/renomear/excluir** perfil **saem**
  de Facetas (com um link/atalho para Gerenciamento).
- **FR-17**: A página de **preview de busca** mantém o campo "Perfil de facetas" (`profile`)
  do spec 001 (agora resolvendo perfis globais).
- **FR-18**: Textos internacionalizados (en/pt).

### Extensibilidade (modelo)
- **FR-19**: O perfil (`TurProfile`) é **agnóstico ao tipo de configuração**. A ordenação de
  facetas (`TurProfileFacetOrdering`) é a **primeira** configuração anexável; novos tipos futuros
  adicionam a própria entidade referenciando `TurProfile`, **sem** alterar o perfil.

### Config de campo por perfil ("Campos") — Fase 7 (2º tipo de config)
- **FR-22**: Em **Campos** (site SN), com um perfil selecionado, o usuário pode sobrescrever por
  campo as **configurações de faceta**: `facet` (liga/desliga), `facetName`, `facetSort`,
  `facetType`, `facetItemType`, `facetRange`, `secondaryFacet`, `showAllFacetItems`. Índice/schema
  (type/enabled/multiValued/hl/mlt/required/defaultValue) **não** são por-perfil.
- **FR-23**: Cada override ausente **herda** o valor global do campo (`TurSNSiteFieldExt`).
- **FR-24**: Editar o config de campo do perfil **Padrão** grava no campo global (espelho, como o
  DT-6 da ordem); perfis não-padrão gravam overrides (`TurProfileFieldConfig`).
- **FR-25**: No runtime de busca (Solr), as facetas respeitam o config **efetivo** do perfil
  (override ∪ global) — facet on/off + sort/type/range por perfil; fallback ao Padrão/global.

### Regra de Busca define o perfil (ação `SET_PROFILE`) — ✅ implementado
- **FR-26**: Uma **Regra de Busca** pode ter uma **ação** `SET_PROFILE` que, quando as condições da
  regra casam, define o **perfil** aplicado à busca (por **nome**, gravado no `value` da ação).
  Fluxo: `TurSNSearchRuleEvaluator.applyAction` faz `params.setProfile(value)` → resolvido pelo
  `TurSNFieldProcess` como se fosse `?profile=`. Consistente com as demais ações (a regra
  **sobrescreve** o `profile` da requisição, igual ao `SET_SORT`). No Console, a ação aparece como
  "Definir Perfil"/"Set Profile" com **dropdown** dos perfis globais. Ação **single-use** por regra.

### Compatibilidade / migração
- **FR-20**: Migrar os dados do spec 001: perfis per-site → perfis **globais**; itens per-site →
  configurações de **(perfil, site)**. Os perfis "Geral" per-site consolidam no **"Padrão"** global.
- **FR-21**: Endpoints legados `GET /api/sn/{snSiteId}/facet` e `PUT .../facet/ordering` continuam
  funcionando, operando sobre a config do **Padrão** para o site (retrocompatível).

## 7. Requisitos não-funcionais
- **NFR-01 (Performance)**: resolução do perfil em runtime não pode regredir a latência da busca
  (sem N+1; leitura por queries diretas/`@Cacheable` como hoje).
- **NFR-02 (Portabilidade)**: migração Liquibase idempotente e portável (H2/MySQL/Postgres);
  `boolean` minúsculo; ids `VARCHAR(255)`; geração de UUID no runtime (não em SQL).
- **NFR-03 (Cache)**: escrita de config de facetas invalida os caches corretos (`@CacheEvict`),
  incluindo custom facets salvos por `save()` item-a-item (lição do spec 001).
- **NFR-04 (Segurança)**: CRUD de perfis em Gerenciamento respeita as privilégios do console
  (ver `research`: qual privilégio exigir para a nova tela).
- **NFR-05 (Resiliência de busca)**: uma faceta configurada para um campo **inexistente no core
  Solr** (índice parcial) não pode derrubar a busca inteira. O runtime remove o `facet.field`
  ofensor e reenvia a query (facetas válidas preservadas); o Console degrada para a tela de erro
  em vez de white-screen quando a resposta vier sem `widget`. Ver `HANDOFF §4l`.

## 8. Critérios de aceite (alto nível)
- **AC-01**: Em Gerenciamento, criar/renomear/excluir/definir-padrão de perfis globais (nome único
  global; 409 em duplicado). O padrão vem criado e protegido.
- **AC-02**: Em Facetas de um site, selecionar um perfil global e reordenar; a ordem fica salva para
  **aquele (perfil, site)** e não afeta outros sites/perfis.
- **AC-03**: `profile=<perfil>` na busca devolve as facetas na ordem de (perfil, site) (Solr).
- **AC-04**: `profile` ausente/inexistente **ou** sem config para o site → ordem do **Padrão**
  para o site, sem erro (HTTP 200).
- **AC-05**: Não é possível excluir nem renomear o perfil padrão (409).
- **AC-06**: Após a migração, os dados do spec 001 aparecem no novo modelo; a busca sem `profile`
  mantém a ordem anterior de cada site (via "Padrão").
- **AC-07**: Excluir uma faceta do site remove suas entradas nas configs de todos os perfis (site).
- **AC-08**: Endpoints legados continuam operando sobre o padrão; preview de busca continua funcionando.
- **AC-09**: Suíte backend verde e frontend compila.

## 9. Fora de escopo
- Outros tipos de configuração além de ordenação de facetas (só o **modelo** fica extensível).
- Perfis aplicados por template do AEM / seleção automática de perfil.
- Sort de valores (`facetSort`) e visibilidade por perfil.
- Export/import de perfis entre ambientes (incremental; não bloqueia).

## 10. Riscos
| Risco | Mitigação |
|-------|-----------|
| Colisão de nomes ao consolidar perfis per-site em globais | Migração define regra de merge por nome (ver data-model §6) e loga os merges. |
| Perfil global sem config para um site | Fallback explícito ao Padrão do site (FR-13). |
| ES/Lucene divergirem do Padrão | DT-6: `facetPosition` legado espelha a config do Padrão por site. |
| Quebrar o que o spec 001 entregou | Fase 0 mapeia todos os pontos do 001 a substituir; testes de retrocompat. |
| Onde exatamente pendurar a tela em Gerenciamento | Fase 0 confirma sidebar/rota/privilégio (`app-sidebar.tsx`, `routes.const`). |
