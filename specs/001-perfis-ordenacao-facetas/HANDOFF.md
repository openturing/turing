# HANDOFF — Estado consolidado da sessão

> **Propósito**: ponto único de retomada. Reúne tudo o que foi feito nesta linha de trabalho
> (feature + correções operacionais) para continuar em uma **nova conversa** sem perder histórico.
> Última atualização: **2026-07-03**. Branch: **`2026.2`** (turing). Feature **commitada
> localmente** (`69430661b0`); `origin/2026.2` está **2 commits à frente** — integrar (ver §7.1).
>
> Leia também, nesta pasta: `spec.md` (requisitos), `plan.md` (fases), `tasks.md` (checklist),
> `research.md` (achados técnicos + §6 indexação), `data-model.md`, `contracts/facet-profile-api.md`.

---

## 1. Feature: Perfis de ordenação de facetas

Sistema de **perfis** para a ordenação de facetas do Semantic Navigation: várias ordenações
nomeadas por site, selecionáveis na busca via parâmetro `facetProfile`, com fallback para o perfil
padrão **"Geral"**.

**Decisões travadas (com Arthur):**
- Perfil controla **só a ordem** das facetas (`facetPosition`).
- Seleção em runtime por **parâmetro `facetProfile`** + fallback ao padrão.
- Perfis **por SN site**.
- Perfil **"Geral"** criado por migração/bootstrap, herdando a ordenação atual.
- Runtime: **apenas Solr**; **sem SDKs** (só API REST). `facetPosition` legado = espelho do padrão.

## 2. Estado das fases

| Fase | Escopo | Status |
|------|--------|--------|
| 0 | Pesquisa (snapshot, propagação, backends) | ✅ concluída |
| 1 | Modelo de dados + Liquibase + bootstrap | ✅ concluída, testes verdes |
| 2 | API de perfis + ordenação + retrocompat (DT-6) | ✅ concluída, testes verdes |
| 3 | Runtime Solr (`facetProfile`) | ✅ concluída, testes verdes |
| 4 | Frontend (seletor + CRUD + i18n) | ✅ concluída, `tsc` verde |
| 5 | ITs end-to-end + suíte completa + validação | ⏳ **pendente** (ver §6) |

## 3. Cronologia de correções/problemas desta sessão

1. **Liquibase `BOOLEAN` não-agnóstico** — `TurLiquibaseChangelogAgnosticTest` proíbe (case-sensitive)
   `BOOLEAN`. Corrigido `v2026.2.7.8.yaml` para `type: boolean` (minúsculo).
2. **Checksum mismatch (H2 local)** — editar um changeset já aplicado quebra o boot. Resolvido
   removendo os registros stale da `DATABASECHANGELOG` (RunScript H2; ver §5 gotchas).
3. **Criar perfil → 400 "Request body is missing or malformed"** — Jackson 3
   (`FAIL_ON_NULL_FOR_PRIMITIVES=true`) + record com primitivos. Criado
   `TurSNSiteFacetProfileRequest(name, description)` (sem primitivos) para create/update.
4. **Import de site (export.json antigo) → facetas no "Geral"** — `TurSNSiteImport.importSNSite`
   passou a chamar `ensureDefaultProfile(savedSite)` após salvar as facetas.
5. **Indexação: `Invalid Date Math String`** (`endOfClasses`) — campo de data multivalor NÃO
   `multiValued` → handler concatena com `lineSeparator`. Correção de produto: marcar `multiValued`.
6. **Indexação: `multiple values encountered for non multiValued field`** (`dateOfClassesText`) —
   campo `multiValued` no Turing mas single no Solr. Implementada **auto-reconciliação** em
   `TurSNProcessQueue.createMissingFields` (REPLACE no Solr, 1× por site:field por JVM).
7. **Logs analisados**: Solr/Elasticsearch indisponíveis (CircuitBreaker `search-engine.solr` OPEN,
   ES "connection refused") — **ambiental**, não código (ver §6).
8. **Incidente `preco` (`numBytes=8`→`4`)** — a 1ª versão da reconciliação passava o **tipo do
   Turing** no REPLACE, mudando `pdouble`→`pfloat`. Corrigido: `reconcileMultiValued` **preserva o
   tipo** do Solr. Índice já corrompido exige **reindex limpo** (ver research §6.2). Soma-se a
   `promoteMultiValuedFromPayload` (promoção automática de tipos não-concatenáveis) e ao
   `catch (Exception)` em `addSolrDocument` (resiliência da fila JMS) — research §6.3.
9. **`LazyInitializationException` ao reordenar facetas (frontend)** — `field.getCustomFacets()` em
   entidade detached vinda de repositório `@Cacheable`. Corrigido injetando
   `TurSNSiteCustomFacetRepository` e usando `findByFieldExtsWithDetails(...)` em
   `seedDefaultProfile`/`persistOrdering` (research §6.4). **Validado**: `TurSNSiteFacetProfileServiceTest`
   (8) + `TurSNSiteFacetedFieldAPITest` (4) + `TurSNSiteImportTest` (10) = 22 verdes.
10. **Perfil padrão fixo e protegido (2026-06-30)** — a pedido: o padrão (i) **já vem criado**
    (`list()` agora chama `ensureDefaultProfile`), (ii) **não pode ser renomeado** (guarda em
    `update()` → `409`, FR-03b) além de não poder ser excluído, (iii) é exibido no seletor como
    **"Padrão"/"Default"** e pré-selecionado, com os botões Renomear/Excluir/Definir-padrão
    **ocultos** no Console. Nome canônico no banco continua **"Geral"** (continuidade + fallback de
    runtime). **Validado**: `TurSNSiteFacetProfileServiceTest` (11, +3 novos) + API/Import/OnStartup =
    27 verdes; `tsc` do frontend OK. Spec/contrato atualizados (FR-02/03b/05/14/14b, AC-04b/04c).

11. **Campo de perfil na página de preview de busca (2026-06-30)** — a pedido: campo de texto
    "Perfil de facetas" no `SearchHeader` da página de preview (`src/search/`). Commit em Enter/blur
    → `store.updateParams({ facetProfile })` → URL `?facetProfile=` → requisição. Exigiu **estender o
    SDK** `@viglet/turing-react-sdk` (`SearchParams.facetProfile`, `buildQueryString`,
    `urlToSearchParams`/`searchParamsToUrl`/`parseHrefToParams`) e **rebuildar o `dist`** (o app
    consome `dist`, não a source — `main: dist/index.js`). **Validado**: build do SDK (`tsc`) + 
    type-check do app (`tsc --noEmit -p tsconfig.app.json`) = EXIT 0. Backend já aceitava
    `facetProfile` (V1/V2 via `@ModelAttribute TurSNSearchParams`).
    - Frontend alterados: `packages/react-sdk/src/core/api.ts`, `.../core/provider.tsx`,
      `apps/turing-app/src/search/components/search-header.tsx`,
      `apps/turing-app/src/i18n/locales/{en,pt}/search.json` (+ `dist/` do SDK rebuildado).

> **Nota build do SDK (Windows)**: o app resolve `@viglet/turing-react-sdk` pelo `dist`
> (`main`/`types` → `dist/`), **sem alias para a source**. Após editar `packages/react-sdk/src/**`,
> rebuildar: `cd packages/react-sdk && node node_modules/typescript/lib/tsc.js`. Sem isso, app não
> vê a mudança (runtime e type-check).

> **Nota incremental-compile (Windows)**: erros transitórios do tipo `X cannot be converted to X`
> (mesmo nome, "pacotes" distintos) ou `cannot find symbol class TurSNSite` em arquivos não tocados
> são **cache de compilação incremental defasado** do maven-compiler nesta máquina. Solução: rodar
> com `clean` (`.\mvnw.cmd -pl turing-app clean ... test`). Não é bug do código.

12. **Commit da feature (2026-07-01)** — todo o trabalho acima foi commitado na `2026.2` local como
    **`69430661b0`** (`feat: add facet ordering profiles for semantic navigation`). Resolve a
    antiga pendência "commit do trabalho".

13. **Validação manual no app (2026-07-02)** — app local de pé (porta 2700) com a feature. Import
    do `export.json` do site **`insper-stage-author`** OK (48 fields/fieldExts, 2 locales; índices
    `_pt_BR`/`_en_US` já existentes no Solr; caches do SN limpos após import). Console operando.

14. **Bug pré-existente (fora da feature): `LazyInitializationException` na lista de Classificação
    de Resultados** — `GET /api/sn/{id}/ranking-expression` estoura em **cache hit** de
    `TurSNRankingExpressionRepository.findByTurSNSite` (`@Cacheable` + Hazelcast serializa o valor →
    entidade volta **detached**; o MapStruct lê a coleção lazy `turSNRankingConditions` sem sessão —
    mesmo padrão do item 9; `enable_lazy_load_no_trans` não salva cópia desserializada). Diagnóstico
    confirmado no `turing.log` e fix local com `@EntityGraph` validado (testes verdes), porém o
    **upstream já corrigiu o mesmo bug** com `@Query` + `left join fetch` (**`99d2ba63af`** na
    `origin/2026.2`). A branch local foi resetada e ficou **sem** a correção — o erro ainda
    reproduz localmente até integrar a origin (§7.1).

## 4. Arquivos (commit `69430661b0` na branch 2026.2 local)

**Novos (backend):**
- `persistence/model/sn/field/TurSNSiteFacetProfile.java`, `TurSNSiteFacetProfileItem.java`
- `persistence/repository/sn/field/TurSNSiteFacetProfileRepository.java`, `...ItemRepository.java`
- `sn/facet/TurSNSiteFacetProfileService.java`
- `onstartup/sn/TurSNSiteFacetProfileOnStartup.java`
- `api/sn/bean/TurSNSiteFacetProfileDto.java`, `TurSNSiteFacetProfileRequest.java`
- `resources/db/changelog/v2026.2.7.8.yaml`
- testes: `TurSNSiteFacetProfileServiceTest`, `TurSNFieldProcessProfileTest`,
  `TurSNSiteFacetProfileRequestTest`, `onstartup/sn/TurSNSiteFacetProfileOnStartupTest`

**Alterados (backend):**
- `persistence/model/sn/TurSNSite.java` (OneToMany `turSNSiteFacetProfiles`)
- `api/sn/console/TurSNSiteFacetedFieldAPI.java` (delega ao service + endpoints `/profile`)
- `sn/TurSNFieldProcess.java` (`getTurSNSiteFieldOrdering(siteId, facetProfile)` + repo de perfil)
- `solr/TurSolrResultProcessor.java` (lê `facetProfile` do contexto)
- `api/sn/queue/TurSNProcessQueue.java` (auto-reconciliação + promoção multiValued)
- `solr/TurSolrUtils.java` (`reconcileMultiValued` — REPLACE preservando tipo)
- `se/TurSearchEnginePlugin.java` (default `reconcileMultiValued` no-op) +
  `solr/.../TurSolrSearchEnginePlugin.java` (override Solr)
- `solr/TurSolrDocumentHandler.java` (`catch (Exception)` em `addSolrDocument` — resiliência JMS)
- `sn/facet/TurSNSiteFacetProfileService.java` (fix LazyInit: `findByFieldExtsWithDetails`)
- `exchange/sn/TurSNSiteImport.java` (ensureDefaultProfile no import)
- `turing-commons`: `TurSNParamType`, `TurSNSearchParams`, `TurSEParameters`, `TurSNSitePostParamsBean`
  (campo `facetProfile`)
- `db.changelog-master.yaml` (include do v2026.2.7.8)
- testes alterados: `TurSNSiteFacetedFieldAPITest`, `TurSNProcessQueueTest`, `TurSNSiteImportTest`,
  `TurSNFieldProcessTest`

**Novos (frontend):** `models/sn/sn-site-facet-profile.model.ts`,
`services/sn/sn.facet.profile.service.ts`
**Alterados (frontend):** `app/console/sn/facet/sn.site.facet.list.page.tsx`,
`components/sn/facet/sn.site.facet.draggable.list.tsx`, `i18n/locales/{en,pt}/sn.json`

**Doc:** `agents.md` (memória operacional — gotchas), `specs/001-perfis-ordenacao-facetas/*`.

## 5. Gotchas técnicos (registrados também no agents.md)

- **Jackson 3** (`tools.jackson` 3.x; Spring Boot 4): `FAIL_ON_NULL_FOR_PRIMITIVES=true`. Records
  `@RequestBody` **não** podem ter primitivos não enviados pelo cliente → separar request/response.
- **Liquibase agnóstico**: usar `type: boolean` (minúsculo); proibidos `BOOLEAN`,
  `TIMESTAMP WITH TIME ZONE`, `CHARACTER LARGE OBJECT`, `BINARY LARGE OBJECT`.
- **H2 de teste/dev é persistente** (`turing-app/store/db/turingDB.mv.db`, `sa`/sem senha). Editar
  changeset já aplicado → checksum mismatch. Reset não-destrutivo:
  `java -cp <.m2>/com/h2database/h2/2.4.240/h2-2.4.240.jar org.h2.tools.RunScript -url "jdbc:h2:file:./store/db/turingDB;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true" -user sa -script del.sql`
  com `DELETE FROM DATABASECHANGELOG WHERE FILENAME='db/changelog/<arquivo>.yaml';` (omitir
  `-password` — arg vazio quebra no Windows).
- **Facet ordering** resolvido FORA do snapshot (`TurSolrResultProcessor`), por isso as entidades de
  perfil **não** têm eviction listener.
- **Campos multivalor que viram facetas** devem ser `multiValued` no Turing **e** no Solr; o
  `TurSNProcessQueue` agora auto-reconcilia o Solr (REPLACE). Fallback manual:
  `TurSNSiteFieldRepairAPI` (`POST /api/sn/{siteId}/field/repair`, `REPAIR_ALL`).

## 6. Problemas ambientais conhecidos (NÃO são bugs da feature)

- **Hazelcast / loopback**: `@SpringBootTest` falham com `Failed to open a Selector` /
  `Unable to establish loopback connection`. Bloqueia toda a Fase 5 (ITs). Mitigar: VPN/firewall,
  reiniciar a máquina, rerodar.
- **Solr/Elasticsearch indisponíveis**: import de conteúdo retorna 0 docs com CircuitBreaker
  `search-engine.solr` OPEN. Garantir Solr de pé (`docker-compose` tem solr) e o endpoint da
  SE instance acessível localmente antes de indexar.
- **Frontend npm/EBUSY (Windows)**: `pnpm` não está no PATH; type-check via
  `node node_modules/typescript/lib/tsc.js --noEmit -p tsconfig.app.json` (na pasta
  `frontend/apps/turing-app`).
- **Maven trava com o app local rodando**: a instância do Turing segura os assets de
  `target/classes` → `maven-resources-plugin` falha com `AccessDeniedException` ao copiar
  recursos. Contorno validado: rodar testes com `"-Dmaven.resources.skip=true"` (ou parar o app).
- **`javac` do PATH é Java 11**: compilar/testar **sempre via `.\mvnw.cmd`** (usa JDK 21).
  `javac` direto falha com `class file has wrong version 61.0/65.0, should be 55.0`.

## 7. Pendências / próximos passos

1. **Integrar `origin/2026.2`** (2 commits à frente do local): `4ac6e9a437`
   ("enhance custom facet handling in Semantic Navigation API") e `99d2ba63af` (remove
   `AulaRelampagoDspaceIT` + **fix do ranking**, §3.14). Atenção no merge:
   - `TurSNSiteFacetedFieldAPI.java` foi reescrito no commit local **e** alterado no `4ac6e9a437`
     → conflito provável; resolver preservando a delegação ao `TurSNSiteFacetProfileService`.
   - Em `TurSNRankingExpressionRepository.java`, ficar com a versão upstream
     (`@Query` + `left join fetch`) — equivalente ao `@EntityGraph` validado localmente
     (não manter os dois). Depois do merge, **push**.
2. **Fase 5** — ITs end-to-end (`AbstractTuringSpringIT`) dos cenários do `quickstart.md` (criar
   perfis, ordenar, buscar com `facetProfile`, fallback, DT-6). **Bloqueada** pelo Hazelcast (§6).
3. Rodar a **suíte completa**: `mvn test -pl turing-app -Dskip.npm=true` (quando o ambiente subir;
   com o app local de pé, adicionar `"-Dmaven.resources.skip=true"` — ver §6).
4. Validar **export/import** de site com perfis (export de perfis ainda não implementado — só o
   import deriva o "Geral"). Import do `insper-stage-author` já validado manualmente (§3.13).
5. **Operacional Insper**: subir Solr, reindexar `economiaee1`; reindex completo após single→multi.
6. Avaliar varredura de outros records `@RequestBody` com primitivos (gotcha Jackson 3).

## 8. Como retomar (nova conversa)

- Comece lendo este `HANDOFF.md` + `tasks.md`.
- Gates: backend `mvn test -pl turing-app -Dskip.npm=true` (PowerShell: citar `"-Dskip.npm=true"`);
  frontend `tsc` conforme §6.
- Build a partir da raiz `turing/` com `./mvnw.cmd` (Windows).
- Memória persistente do agente: `project_perfis_ordenacao_facetas.md` resume o mesmo estado.
