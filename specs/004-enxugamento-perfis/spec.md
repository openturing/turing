# Spec — Enxugamento do PR de Perfis

> ## ⚠️ Correção de base — 2026-08-07
>
> Este documento foi escrito comparando o branch com **`origin/2026.2`**. A base correta do PR é
> **`origin/2026.3`**. O mapeamento estrutural continua válido (é fato da árvore de trabalho), mas
> **10 conclusões** dependem da base e estão marcadas 🔶 BASE em [`research.md`](research.md) **§0.2**.
> Duas podem inverter decisões deste spec: **B1 → D2** e **B2 → D7/Q3**. Comandos em `research.md` §0.3.

## 1. Contexto e problema

O PR #2668 (`feature/turing-generic-profiles` → **`2026.3`**) entrega três specs de uma vez: 001
(perfil de ordenação de facetas **por site**), 002 (perfil **global genérico**) e 003 (config de
campo por perfil). O 001 foi **abandonado** durante o próprio desenvolvimento e substituído pelo 002,
mas seus artefatos permaneceram no branch por uma decisão explícita de "manter 1 release para
rollback" (`002/research.md:103-110`, DT-7).

O resultado é um PR de **130 arquivos / +10.374 linhas** onde convivem:

1. **Duas formações completas de perfil.** `TurSNSiteFacetProfileService` (399 linhas, spec 001) e
   `TurProfileFacetOrderingService` (341, spec 002) implementam a mesma coisa — 21 de 21 métodos têm
   contraparte, e 6 são cópia byte-a-byte (tabela em `research.md` §A.2).
2. **Código morto que escreve no banco a cada boot.** `TurSNSiteFacetProfileOnStartup` popula
   `sn_site_facet_profile` toda inicialização; a única coisa que lê essa tabela é o passo 1 do
   `TurProfileMigrationOnStartup`, que a converte de volta para o modelo 002 — um round-trip
   auto-infligido cujo resultado é idêntico ao do fallback que lê `facetPosition` direto.
3. **Resíduos de nomenclatura `facetProfile`** em código que **fica** (não só no 001).
4. **Três workstreams alheios a perfis** que vazaram para o PR.

### A premissa do rollback é falsa · 🔶 confirmar contra `origin/2026.3` (B1)

`v2026.2.7.8.yaml` (cria `sn_site_facet_profile` e `sn_site_facet_profile_item`) **não existe em
`origin/2026.2`**: `git log --oneline origin/2026.2 -- .../v2026.2.7.8.yaml` retorna vazio e o
`db.changelog-master.yaml` da base termina em `v2026.2.7.7`. O diff adiciona 7.8, 7.9 e 7.10 juntos.

Evidência **independente da base**, do reflog local: o arquivo foi criado pelo commit `6943066`
(*"feat: add facet ordering profiles for semantic navigation"*) **no próprio branch de feature**, e a
última integração do branch com upstream foi um merge de `origin/2026.2` (`86ad604`). Ou seja: o DDL
nasceu aqui, não veio de cima.

**Se** `origin/2026.3` também não o contiver (item **B1**, pendente), então **nenhuma instalação
publicada jamais teve as tabelas do 001** — não há dado de usuário a migrar nem caminho de rollback a
proteger, e a decisão DT-7 pode ser revertida sem risco. **Se contiver**, D2 inverte e é preciso
`dropTable` + plano de migração.

O que **existe de verdade** em release publicada é a coluna legada `TurSNSiteFieldExt.facetPosition`.
É dela que o perfil "Padrão" do 002 é semeado (`buildFromLegacy`), e ela **deve continuar existindo**.

## 2. Objetivo

Deixar no PR apenas a formação 002/003, sem nenhuma referência à formação 001, sem duplicação interna
evitável, e com os workstreams alheios separados — preservando 100% do comportamento entregue e sem
quebrar o build em nenhum passo intermediário.

## 3. Decisões

| # | Decisão | Valor | Status |
|---|---------|-------|--------|
| **D1** | Formação 001 | **Remover integralmente neste PR** (entidades, repos, serviço, OnStartup, DTOs, testes, `v2026.2.7.8`). Reverte a DT-7 do 002. | travada pela evidência (§1) |
| **D2** | Changeset de DROP | 🔶 **Condicional a B1.** Se o `.8` nunca rodou em ambiente publicado: **não escrever** — basta deletar o arquivo + o include; bases de **dev** que rodaram o branch ficam com tabelas órfãs, resolver por nota no PR (`DROP TABLE` manual ou apagar `store/db/turingDB.mv.db`). Se o `.8` **estiver** em `origin/2026.3`: **inverte** — `dropTable` obrigatório + plano de migração dos dados. | **pendente de B1**; alternativa em `research.md` §A.7 |
| **D3** | Ordem de remoção | **Obrigatória, do consumidor para o dono**, com o Liquibase por último. `ddl-auto: none` faz o boot quebrar se invertida. | travada pela verificação adversarial |
| **D4** | Resiliência do Solr | **Manter no PR**, com justificativa explícita — é rede de proteção da própria spec 003, não escopo solto. **Ou** revertê-la *somente* após adicionar filtro em `TurSolrQueryBuilder` que descarte facet fields ausentes do core. | **a decidir** (ver §7 Q1) |
| **D5** | Self-healing multiValued | **Reverter** — escopo solto genuíno, sem acoplamento a perfis. Cluster atômico de 5 arquivos. | travada |
| **D6** | Spec 001 (documentação) | **Deletar a pasta** `specs/001-perfis-ordenacao-facetas/`, corrigindo os 3 links que quebram. Não há precedente de spec versionada no repo (`specs/` inteira é nova neste PR). | **a decidir** (ver §7 Q2) |
| **D7** | Renumeração dos changelogs | **A decidir.** O projeto tem sequência densa em 55 changelogs, sem nenhum buraco; deletar o `.8` cria o primeiro. | **a decidir** (ver §7 Q3) |
| **D8** | Prefixo `tur_` nas tabelas | **A decidir.** 4 tabelas em 56 usam `tur_`; a convenção dominante é sem prefixo (`TurIntent`→`intent`). Momento ideal é agora, pré-merge. | **a decidir** (ver §7 Q4) |

## 4. Requisitos funcionais

### Bloco A — Remoção da formação 001

- **FR-01:** Nenhum arquivo cujo nome contenha `FacetProfile` permanece em `turing-app/src/`.
- **FR-02:** `TurSNSite.java` fica **sem nenhum vestígio do 001** — sem a coleção
  `turSNSiteFacetProfiles`, sem o import, sem o token no `@JsonIgnoreProperties`. 🔶 Se `origin/2026.3`
  não tiver alterado a entidade por conta própria, isso equivale a ficar **idêntico** à base.
- **FR-03:** `TurSNSiteImport` deixa de injetar e chamar o serviço do 001. Não é preciso substituir por
  um hook do 002: o runtime já degrada para `facetPosition` via
  `TurProfileFacetOrderingService.resolveReadPositions` → `legacyPositions`, produzindo o mesmo mapa.
- **FR-04:** `TurProfileMigrationOnStartup` perde o passo 1 (leitura do 001) e **mantém**
  `ensureDefaultProfile()` + `buildFromLegacy()` — este é o bootstrap real do "Padrão" e o backfill a
  partir de `facetPosition`. A classe é renomeada para `TurProfileOnStartup`.
- **FR-05:** `v2026.2.7.8.yaml` e seu `include` em `db.changelog-master.yaml` (linhas 108-109) são
  removidos **por último**, depois que nada mais consulta as tabelas.
- **FR-06:** Nenhum passo intermediário do plano deixa o build (`mvn test -pl turing-app -am`) vermelho.

### Bloco B — Nomenclatura residual em código que fica

- **FR-07:** `TurSNFieldProcess`: o parâmetro `facetProfile` (linhas 136, 141, 145, 169, 171, 172) é
  renomeado para `profile` e o javadoc ajustado. Depois disso
  `grep -rn facetProfile turing-app/src/main` deve retornar **vazio**.
- **FR-08:** `agents.md` — corrigir os 3 resíduos: `:322` (allowlist do SDK diz `facetProfile`, o código
  usa `profile` — está **factualmente errado hoje**), `:316` (exemplo `TurSNSiteFacetProfileRequest` →
  `TurProfileRequest`), `:320` (gotcha ancorado em `TurSNSiteFacetProfileService` →
  `TurProfileFacetOrderingService`). Remover também a linha em branco solta em `:321`.
- **FR-09:** Frontend — renomear `sn.facet.profile.service.ts` / `TurSNFacetProfileService` e as
  variáveis `facetProfileService` / `turSNFacetProfileService` (nomenclatura do 001 em telas que não
  tratam de facetas).
- **FR-10:** Comentários de changelog: `v2026.2.7.9.yaml:6` (cita a migração do spec-001) e
  `v2026.2.7.10.yaml:2` (atribui ao spec 002 uma tabela que é do 003).

### Bloco C — Duplicação interna à formação nova

- **FR-11:** Eliminar as ~90 linhas repetidas entre `TurProfileFacetOrderingService`,
  `TurProfileFieldConfigService`, `TurProfileService`, `TurSNFieldProcess` e `TurProfileOnStartup`
  (`positionsByFacetId`, `buildItem`, `legacyPositions`/`buildFromLegacy`, `requireSite`/`requireProfile`).
- **FR-12:** Remover a superfície de repositório sem consumidor: `TurProfileFacetOrderingItemRepository`
  (interface inteira), `TurProfileFacetOrderingRepository.findByTurSNSite`,
  `TurProfileFieldConfigRepository.findByTurProfileAndTurSNSiteFieldExtIn` e `.findByTurProfile`.
- **FR-13:** Unificar a resolução do perfil aplicado à busca em **um** ponto (hoje há duas
  implementações divergentes — ver FR-17).

### Bloco D — Escopo solto

- **FR-14:** Reverter o cluster **self-healing multiValued** como unidade atômica de 5 arquivos:
  `TurSNProcessQueue`, `TurSearchEnginePlugin`, `TurSolrSearchEnginePlugin`, `TurSolrUtils` e
  `TurSNProcessQueueTest`, mais o bullet `agents.md:314`. Reabrir em PR próprio.
- **FR-15:** Reverter `index.html` (`translate="no"` / `notranslate`) — isolado e sem dependências.
- **FR-16:** 🔶 *(B4)* Reverter o `@Secured` aplicado a endpoints **pré-existentes**: 6 em
  `TurSNSiteFieldExtAPI` **e também** `TurSNSiteFacetedFieldAPI:78` e `:86` (rotas
  `GET /api/sn/{id}/facet` e `PUT /api/sn/{id}/facet/ordering`, que já existiam em `origin/2026.2` —
  **confirmar contra `origin/2026.3`**: se a base já as anotou, a reversão encolhe).
  **Manter** o `@Secured` nas rotas genuinamente novas (`TurSNSiteFacetedFieldAPI:96,105,114` e
  `TurSNSiteFieldProfileAPI:67,75,84`).

### Bloco E — Defeitos encontrados

- **FR-17:** *(bug)* `?profile=<uuid>` produz resposta meio-perfil: `TurSNFieldProcess` aceita
  nome **ou** id, `TurSolrQueryBuilder.resolveAppliedProfile` só aceita nome. A ordenação das facetas
  vem do perfil, a config de faceta por campo vem do global. Unificar a semântica.
- **FR-18:** *(bug)* `PUT /api/profile/{id}/default` não tem `@CacheEvict`, enquanto os dois endpoints
  irmãos que gravam `facetPosition` têm. O cache
  `findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition` — lido por `TurElasticsearch:90` — serve a
  ordem antiga após promover um perfil a padrão, furando o espelho DT-6.
- **FR-19:** *(bug de UX/permissão)* `PROFILE_VIEW` não é concedível pela UI: falta a categoria
  `PROFILE` em `PRIVILEGE_LAYOUT` (`admin.role.form.tsx:32-40`). O item "Perfis" fica invisível para
  todo usuário não-admin, permanentemente.
- **FR-20:** *(lacuna)* GraphQL não expõe `profile` (`TurSNSearchParamsInput` +
  `TurSNSiteSearchGraphQLController.convertToTurSNSearchParams`). Implementar **ou** registrar como
  limitação conhecida.
- **FR-21:** i18n — remover **33 chaves órfãs** (66 entradas en+pt): 18 do CRUD de perfil que migrou
  para o namespace `profile`, 13 do editor de campo do 003 que não foi construído, `profile.rename`,
  `home.features.profiles.description`. Corrigir o uso cross-namespace em
  `sn.site.search.rule.actions.tsx:272`.

### Bloco F — Documentação

- **FR-22:** Reverter, nas specs 002/003, os 10+ pontos que gravam a política "manter o 001 por 1
  release" (DT-7/Q4), e resolver a contradição interna `002/HANDOFF.md:23` × `:116`.
- **FR-23:** Corrigir os status desatualizados: `002/README.md:23` ("Ainda não iniciado", com 7 fases
  concluídas) e `003/README.md:37` + `003/HANDOFF.md:83` ("Nada novo commitado", com o código no PR).
- **FR-24:** Deletar `specs/003-config-campos-por-perfil/Resumo-Config-Campos-por-Perfil.pdf` — binário
  gerado, sem consumidor, e que o git trata como **texto** (137 linhas no diff, sem `.gitattributes`).
- **FR-25:** Adicionar seção `## 2026.2` ao `CHANGELOG.md` com as 5 mudanças visíveis ao usuário.

## 5. Requisitos não-funcionais

- **NFR-01 (Build sempre verde):** cada fase do `plan.md` é um commit que compila e passa
  `mvn test -pl turing-app -am -Dskip.npm=true`. Fases que quebrariam isoladamente são explicitamente
  marcadas como "commit único" no plano.
- **NFR-02 (Zero perda funcional):** nenhuma remoção pode alterar o comportamento entregue. Provado
  para o 001 pela tabela de correspondência 21/21 (`research.md` §A.2) — nada precisa ser portado.
- **NFR-03 (Reversibilidade):** cada bloco (A…F) é um commit separado e independentemente revertível.
- **NFR-04 (Sem alias):** **não** criar retrocompatibilidade para `facetProfile` na URL. O 001 nunca
  foi liberado; um alias seria resíduo novo.

## 6. Critérios de aceite

- **AC-01:** `grep -rn "FacetProfile\|facetProfile\|facet_profile" turing-app/src turing-commons/src frontend/apps frontend/packages --exclude-dir=target --exclude-dir=node_modules` retorna **vazio**.
- **AC-02:** `find turing-app/src -iname "*FacetProfile*"` retorna **vazio**.
- **AC-03:** 🔶 `git diff origin/2026.3...HEAD -- turing-app/.../persistence/model/sn/TurSNSite.java` retorna **vazio**. *(Critério válido apenas se `2026.3` não tiver alterado essa entidade por conta própria — conferir junto com B4–B9; se tiver, o critério vira "sem nenhuma referência a `TurSNSiteFacetProfile`".)*
- **AC-04:** A app sobe do zero (base limpa) e sobre uma base 2026.2 existente, com o perfil "Padrão"
  criado e a ordenação semeada a partir de `facetPosition`.
- **AC-05:** Busca sem `profile`, com `profile=<nome válido>` e com `profile=<inexistente>` devolvem
  HTTP 200 com o comportamento de hoje.
- **AC-06:** `mvn test -pl turing-app -am -Dskip.npm=true` verde; frontend type-check limpo.
- **AC-07:** Nenhum arquivo binário em `specs/`.
- **AC-08:** O diff do PR encolhe em ~1.400 linhas (bloco A) + ~400 (bloco D) + ~1.323 (spec 001, se D6
  for aprovada), sem perda funcional.

## 7. Decisões que dependem do Arthur / do revisor

- **Q1 (D4) — Resiliência do Solr fica ou sai?** Dois verificadores independentes concluíram que o
  retry de faceta indefinida **protege a própria feature de perfis**: `resolveEnabledFacets` deixa um
  perfil não-padrão ligar faceta num campo que pode não existir no core, e o Solr rejeita a query
  inteira (`TurSNSearchProcess:178` devolve bean vazio → tela branca). Recomendação: **manter**,
  justificando no corpo do PR. Se sair, tem de sair junto com `resolve.ts` e `search-content.tsx`, e
  antes disso é preciso filtrar facet fields ausentes no `TurSolrQueryBuilder`.
- **Q2 (D6) — A pasta `specs/` fica no upstream?** 🔶 *(depende de B3)* `specs/` inteira (29 arquivos,
  ~4.400 linhas) é nova neste PR e não existe em `origin/2026.2`; confirmar o mesmo em `origin/2026.3`.
  Opções: (a) deletar só o 001; (b) deletar `specs/` inteira e deixar só o CHANGELOG; (c) manter tudo,
  corrigindo os status. Recomendação: **(c) com o 001 marcado `SUPERSEDED BY 002`** — o registro de por
  que a abordagem mudou é exatamente o que o revisor perguntou.
- **Q3 (D7) — Renumerar `.9`→`.8` e `.10`→`.9`?** 🔶 **Depende de B2 e pode deixar de ser opcional.**
  Se `origin/2026.3` já publicou changelogs com esses números, não é churn — é **colisão de `filename`
  no `DATABASECHANGELOG`**, e renumerar vira obrigatório. Se os números estiverem livres: seguro
  (todos os changesets têm `preConditions: not tableExists / onFail: MARK_RAN`), mas é churn, e sem
  renumerar fica o primeiro buraco de numeração em 55 changelogs. Recomendação nesse caso: **deixar o
  buraco**.
- **Q4 (D8) — Renomear as 4 tabelas `tur_*`?** Convenção do projeto é sem prefixo (`intent`, `locale`,
  `custom_tool`). Agora é rename de DDL puro; depois do merge vira migração. Recomendação: fora do
  escopo deste enxugamento; se for para padronizar, vira spec própria.
- **Q5 — Os workstreams revertidos viram PRs separados agora ou depois?** São 3: self-healing
  multiValued, resiliência de busca (se Q1 = sair) e `notranslate`.
- **Q6 — Os defeitos do Bloco E entram neste PR ou viram follow-up?** Recomendação: neste PR os que são
  **consequência direta** da feature de perfis (cache stale, resolução divergente de perfil); as 33
  chaves de i18n órfãs e o nit de diff podem ir junto por serem triviais e de risco zero.
- **Q7 🔶 — O enxugamento vem antes ou depois de atualizar o branch para `origin/2026.3`?** A última
  integração com upstream foi um merge de **`origin/2026.2`** (`86ad604`), então o branch está atrás da
  base. Fazer o enxugamento **antes** deixa o diff limpo para a revisão, mas os conflitos do
  merge/rebase podem ressuscitar arquivos já removidos. Fazer **depois** resolve conflito uma vez só,
  mas o revisor continua vendo o PR inchado nesse intervalo. Recomendação: **atualizar para `2026.3`
  primeiro**, justamente porque B1/B2 só têm resposta definitiva depois disso.

## 8. Fora de escopo

- Qualquer mudança de comportamento da feature de perfis (002/003) além dos defeitos listados no Bloco E.
- Suporte a `profile` no `turing-java-sdk` (nunca esteve no escopo; registrar como pendência).
- A demanda de **facetas por template do AEM** — projeto à parte, não confundir.

## 9. Riscos

| Risco | Mitigação |
|-------|-----------|
| Remover o changelog antes das entidades derruba o boot (`ddl-auto: none`) | Ordem obrigatória do `plan.md` §2; o changelog é o passo final. |
| Reverter só parte do cluster multiValued não compila | Cluster atômico de 5 arquivos, interface **por último** (`tasks.md` T4.1). |
| Reverter a resiliência do Solr sem filtro deixa a feature capaz de derrubar a busca | Q1 em aberto; se sair, o filtro em `TurSolrQueryBuilder` é pré-requisito. |
| Bases de dev com tabelas órfãs e FKs apontando para tabelas vivas | Nota no PR com o `DROP TABLE` manual (filho→pai) ou apagar o H2 local. |
| Encolher o construtor de produção antes do teste injeta `null` nos mocks | Produção primeiro, teste depois, **no mesmo commit** (`tasks.md` T1.3). |
| Remover 2/3 do `TurProfileMigrationOnStartupTest` deixa stub órfão → `UnnecessaryStubbingException` | Remover também o stub da linha 137 e os imports 25/26/32. |
