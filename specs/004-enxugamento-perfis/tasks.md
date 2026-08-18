# Tarefas — 004 Enxugamento do PR de Perfis

Checklist executável. Cada tarefa cita arquivo e linha. As tarefas **T1.x são sequenciais e a ordem é
obrigatória** ([`plan.md`](plan.md) §2) — as demais podem ser feitas em qualquer ordem.

Legenda de risco: 🔴 derruba boot / não compila se feito errado · 🟡 exige atenção · 🟢 mecânico.

---

## T0 — Preparação

- [ ] **T0.0** 🔶🔴 **Reverificação de base** — rodar os comandos de [`research.md`](research.md) §0.3
      contra **`origin/2026.3`** (a base correta do PR; a varredura original usou `origin/2026.2`).
      Fechar os 10 itens 🔶 BASE de §0.2 **antes** de executar qualquer tarefa abaixo. Em especial:
  - [ ] **B1** — `v2026.2.7.8.yaml` existe em `origin/2026.3`? Se **sim**, D2 inverte: T1.8 passa a
        exigir `dropTable` + plano de migração dos dados, e este checklist muda
  - [ ] **B2** — `2026.3` já usa os números 7.8/7.9/7.10? Se **sim**, há **colisão de changelog** e a
        **T1.10 deixa de ser opcional**
  - [ ] **B4–B9** — reconfirmar o que é escopo solto (T4a/T4c/T4d) e o argumento da Q2 (T4b) contra a
        base certa: o que já estiver em `2026.3` sai do escopo de reversão
  - [ ] Medir o atraso do branch: `git rev-list --count HEAD..origin/2026.3` (a última integração com
        upstream foi merge de **`origin/2026.2`**) e decidir com o revisor se o enxugamento vem antes
        ou depois de atualizar para `2026.3`
- [ ] **T0.1** 🟢 Confirmar baseline verde: `mvn -pl turing-app test` e `pnpm -C frontend build`.
- [ ] **T0.2** 🟢 Obter respostas para **Q1–Q7** ([`spec.md`](spec.md) §7).
- [ ] **T0.3** 🟢 `mvn clean` — garantir que nenhum `*MapperImpl` gerado antigo mascare erro.

---

## T1 — Fase 1 · Remoção da formação 001 · **ORDEM OBRIGATÓRIA** 🔴

> Regra: **consumidor antes de dono, schema por último.** Violar isso faz o Spring abortar no boot
> (`ddl-auto: none`, Liquibase é a única autoridade de schema).

### T1.1 — Hook de escrita no import 🔴

- [ ] `sn/TurSNSiteImport.java`
  - [ ] linha **58** — remover `import …TurSNSiteFacetProfileService;`
  - [ ] linha **99** — remover o campo `turSNSiteFacetProfileService`
  - [ ] linhas **121** e **142** — remover o parâmetro das duas assinaturas de construtor
  - [ ] linhas **195-198** — remover a chamada `ensureDefaultProfile(...)`
- [ ] `TurSNSiteImportTest.java`
  - [ ] linha **61** — remover o import
  - [ ] linhas **99-103** — remover o `@Mock` / setup
  - [ ] linhas **427-428** — remover `verify(turSNSiteFacetProfileService).ensureDefaultProfile(...)`
        ⚠️ **A proposta original esquecia esta linha — sem ela o módulo de teste não compila.**
- [ ] Compilar: `mvn -pl turing-app test-compile`

### T1.2 — Runner de seed do 001 🔴

- [ ] Deletar `onstartup/sn/TurSNSiteFacetProfileOnStartup.java` (66 linhas)
- [ ] Deletar `…/TurSNSiteFacetProfileOnStartupTest.java` (77 linhas)

### T1.3 — Passo 1 da migração 🔴 · **mesmo commit da T1.2**

- [ ] `onstartup/profile/TurProfileMigrationOnStartup.java`
  - [ ] remover o bloco **111-128** (migração `sn_site_facet_profile` → `tur_profile_*`)
  - [ ] remover imports e injeções que ficaram órfãos
  - [ ] ✅ **NÃO tocar no passo 2** — a migração `TurSNSiteFieldExt.facetPosition` → perfil "Geral" é
        a única fonte de dado legado **real** e tem de continuar
- [ ] `TurProfileMigrationOnStartupTest.java`
  - [ ] imports **25**, **26**, **32**
  - [ ] **stub da linha 137** ⚠️ deixá-lo órfão gera `UnnecessaryStubbingException` (Mockito estrito)
- [ ] Rodar só esta classe de teste antes de seguir

### T1.4 — Acoplamento em `TurSNSite` 🟡

- [ ] `persistence/model/sn/TurSNSite.java`
  - [ ] linha **35** — import
  - [ ] linha **74** — entrada em `@JsonIgnoreProperties`
  - [ ] linhas **219-221** — a coleção `Set<TurSNSiteFacetProfile>`
- [ ] ✅ MapStruct **não é blocker** — verificado (`research.md` §A.8): `TurSNSiteMapper.java` não
      cita o 001 e não existe `unmappedTargetPolicy` no projeto. A referência no `*MapperImpl` é gerada.

### T1.5 — Serviço, DTOs e testes 🟢

- [ ] Deletar `sn/facet/TurSNSiteFacetProfileService.java` (399)
- [ ] Deletar `api/sn/bean/TurSNSiteFacetProfileDto.java` (47)
- [ ] Deletar `api/sn/bean/TurSNSiteFacetProfileRequest.java` (34)
- [ ] Deletar `…/TurSNSiteFacetProfileServiceTest.java` (324)
- [ ] Deletar `…/TurSNSiteFacetProfileRequestTest.java` (54)

### T1.6 — Repositórios 🟢

- [ ] Deletar `persistence/repository/sn/field/TurSNSiteFacetProfileRepository.java` (46)
- [ ] Deletar `persistence/repository/sn/field/TurSNSiteFacetProfileItemRepository.java` (40) — **já órfão**

### T1.7 — Entidades 🟢

- [ ] Deletar `persistence/model/sn/field/TurSNSiteFacetProfile.java` (112)
- [ ] Deletar `persistence/model/sn/field/TurSNSiteFacetProfileItem.java` (93)

### T1.8 — Schema · **POR ÚLTIMO** 🔴

- [ ] Deletar `resources/db/changelog/v2026.2.7.8.yaml` (114)
- [ ] `resources/db/changelog/db.changelog-master.yaml` — remover o `include` das linhas **108-109**
- [ ] ❌ **NÃO** criar changeset `dropTable` (decisão D2 — as tabelas nunca existiram em produção)

### T1.9 — Validação da Fase 1 🔴

- [ ] `mvn -pl turing-app test`
- [ ] Boot contra base **com** ≥1 site SN
- [ ] Boot contra base **vazia**
- [ ] Boot **duas vezes seguidas** na mesma base (idempotência do passo 2 da migração)

### T1.10 — Renumeração de changelogs *(só se Q3 = "renumerar" · 🔶 obrigatório se B2 revelar colisão)* 🟡

- [ ] `v2026.2.7.9.yaml` → `v2026.2.7.8.yaml`; `v2026.2.7.10.yaml` → `v2026.2.7.9.yaml`
- [ ] Ajustar os dois `include` no master
- [ ] Conferir se algum `changeSet id` carrega o número no nome
- [ ] Avisar o time: invalida ambientes de dev que já rodaram o branch (`DATABASECHANGELOG.filename`)

---

## T2 — Fase 2 · Resíduos de nomenclatura em código que fica 🟢

- [x] **T2.1** `sn/TurSNFieldProcess.java` *(caminho corrigido — não é `sn/field/`)* — as 6 ocorrências
      do parâmetro `facetProfile` renomeadas para **`profileNameOrId`**.
      ⚠️ **Não** dá para usar `profile`: já existe `Optional<TurProfile> profile` na linha 170 do mesmo
      método (`resolveProfilePositions`) — colidiria. `profileNameOrId` é literalmente o que o javadoc
      da `:136` descreve ("the requested (global) profile name or id"). Parâmetro é escopo local e Java
      não tem argumento nomeado, então nenhum chamador muda.
- [x] **T2.2** `TurSNFieldProcessProfileTest.java` — javadoc `:55` reescrito para "the `profile` search
      parameter" e método `:191` renomeado `fallsBackToDefaultProfileWhenFacetProfileAbsent` →
      `fallsBackToDefaultProfileWhenProfileAbsent`
- [x] **T2.3** Frontend — ❌ **TAREFA CANCELADA: o achado original estava errado.**
      As 9 ocorrências são a classe `TurSNFacetProfileService` (+ 3 imports), que **não é resíduo do
      001** — é a service da spec **002**, e é **simétrica** a `TurSNFieldProfileService` da spec 003
      (`services/sn/sn.field.profile.service.ts:14`), ambas sob `TurProfileService`
      (`services/profile/profile.service.ts:10`) para os perfis globais. O padrão do projeto é
      `TurSN<domínio>ProfileService` = "config de \<domínio\> de um perfil, por site". Renomear
      quebraria a simetria e seria churn gratuito. **Nada a fazer.**
- [x] **T2.4** [`agents.md:316`](../../agents.md) — exemplo Jackson 3 usava `TurSNSiteFacetProfileRequest`
      → trocado por **`TurProfileRequest(String name, String description)`** (existe em
      `api/profile/bean/TurProfileRequest.java:33`, mesma forma do exemplo antigo)
- [x] **T2.5** [`agents.md:320`](../../agents.md) — gotcha LAZY + `@Cacheable` reancorado em
      **`TurProfileFacetOrderingService.loadCustomFacets`/`persistOrdering`**
      (`profile/TurProfileFacetOrderingService.java:288` e `:163`)
- [x] **T2.6** [`agents.md:321`](../../agents.md) — linha em branco perdida removida
- [x] **T2.7** [`agents.md:322`](../../agents.md) — allowlist do SDK corrigida de `facetProfile` para
      **`profile`**; conferido contra `react-sdk/src/core/api.ts:33` (`profile?: string`) e `:50`
      (`qs.append("profile", …)`)

> ✅ `agents.md` está **sem nenhuma ocorrência** de `FacetProfile`/`facetProfile` (grep). Feito sem
> shell; não passou por build — mas são 4 edições em Markdown, sem impacto de compilação.

---

## T3 — Fase 3 · Deduplicação interna 002/003 🟡

- [ ] **T3.1** Extrair os ~90 linhas de helpers repetidos entre `TurProfileService`,
      `TurProfileFacetOrderingService`, `TurProfileFieldConfigService` e a API (`research.md` §C)
- [ ] **T3.2** Remover a superfície órfã de repositório — métodos de query declarados e nunca chamados (§C.1)
- [ ] **T3.3** ⚠️ Ler o **§C.2** antes de generalizar: há duplicações **aparentes** que são contratos
      distintos e **não** devem ser unificadas

---

## T4 — Fase 4 · Escopo solto

### T4a — Self-healing de `multiValued` · **REVERTER ATOMICAMENTE** 🔴

Ordem obrigatória — reverter parcialmente quebra:

- [ ] **1.** o teste
- [ ] **2.** `TurSNProcessQueue`
- [ ] **3.** o override no cliente Solr
- [ ] **4.** `TurSolrUtils`
- [ ] **5.** a interface — **por último**

⚠️ Armadilhas medidas: reverter **só** `TurSolrUtils` quebra (`TurSolrSearchEnginePlugin:207` chama
direto); reverter **só** o override deixa a feature como **no-op silencioso**.

### T4b — Resiliência do Solr · **MANTER** *(condicional à Q1)* 🟡

- [ ] **T4b.1** ❌ **Não reverter.** A classificação como escopo solto foi **refutada** — é rede de
      segurança da própria feature: `TurSolrQueryBuilder:840` (`resolveEnabledFacets`, novo neste PR)
      deixa um perfil ligar faceta em campo inexistente no core → Solr rejeita a **query inteira** →
      `TurSNSearchProcess:178` devolve bean vazio → tela branca
- [ ] **T4b.2** Explicar isso ao revisor no comentário do PR
- [ ] **T4b.3** Se ele ainda quiser fora: remover **e abrir issue** para o caminho de tela branca

### T4c — `index.html` 🟢

- [ ] Reverter a alteração cosmética

### T4d — `@Secured` · **atenção ao escopo real** 🟡

- [ ] Reverter as anotações adicionadas fora dos endpoints novos
- [ ] ⚠️ **Incluir `TurSNSiteFacetedFieldAPI:78` e `:86`** — a proposta original as tratava como
      "endpoints novos", mas `GET /api/sn/{id}/facet` e `PUT /api/sn/{id}/facet/ordering` **já
      existiam em `origin/2026.2` sem `@Secured`**. Adicionar autorização a rota preexistente é
      mudança de comportamento de API e não pertence a este PR

---

## T5 — Fase 5 · Defeitos reais *(escopo depende da Q6)* 🟡

- [ ] **T5.1** E1–E9 conforme `research.md` §E. Recomendação: corrigir neste PR os que são
      **consequência direta** da feature (cache stale, resolução divergente de perfil)
- [ ] **T5.2** §E.10 — 33 chaves de i18n órfãs (risco zero, pode ir junto)
- [ ] **T5.3** §E.11 — nit de diff

---

## T6 — Fase 6 · Documentação 🟢

- [ ] **T6.1** *(Q2)* `specs/001-perfis-ordenacao-facetas/` — marcar `SUPERSEDED BY 002` **ou** deletar.
      Recomendação: marcar; o registro de por que a abordagem mudou é justamente o que o revisor perguntou
- [ ] **T6.2** `specs/002-.../research.md:103-110` — anotar *in loco* que a decisão **DT-7** partiu de
      premissa falsa (`v2026.2.7.8` nunca esteve em `origin/2026.2`). Corrigir, não apagar
- [ ] **T6.3** Referências a `facetProfile` / `TurSNSiteFacetProfile` nas specs 002 e 003 que descrevem
      o estado **corrente** (`research.md` §F)
- [ ] **T6.4** Entrada de CHANGELOG para o enxugamento

---

## T7 — Fase 7 · Validação final

- [ ] **V1** `mvn -pl turing-app test` verde
- [ ] **V2** `pnpm -C frontend build` verde
- [ ] **V3** `grep -rIn "FacetProfile\|facetProfile\|facet_profile\|FACET_PROFILE\|sn_site_facet_profile"`
      repo-wide (excluindo `target/`, `node_modules/`) → **zero** fora de `specs/001-*` (se mantida) e
      `specs/004-*`
- [ ] **V4** Boot em base com dados; perfil "Geral" recebe as posições de `facetPosition`
- [ ] **V5** Boot em base vazia
- [ ] **V6** Boot duas vezes seguidas — sem duplicar nem sobrescrever
- [ ] **V7** `mvn clean` antes da validação final
- [ ] **V8** Revisão do diff final arquivo a arquivo — *o diff enxuto é o entregável*

---

## Resumo quantitativo

| Bloco | Arquivos deletados | Arquivos editados | Linhas removidas (aprox.) |
|---|---|---|---|
| A — formação 001 | 12 | 4 | ~1.406 + acoplamentos |
| B — nomenclatura | 0 | 8 | — (renomeação) |
| C — deduplicação | 0 | 4+ | ~90 |
| D — escopo solto | 0 | ~8 | reversões |
| E — defeitos | 0 | a definir (Q3) | — |
| F — documentação | 0 ou 1 pasta (Q1) | ~6 | — |
