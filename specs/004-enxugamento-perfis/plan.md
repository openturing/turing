# Plano de execução — 004 Enxugamento do PR de Perfis

Sete fases. A **Fase 1 tem ordem interna obrigatória** (§2) — as demais são independentes entre si e
podem ser feitas em qualquer sequência, desde que cada uma feche com build verde.

---

## §1 Pré-requisitos

| # | Item |
|---|---|
| **P0** | 🔶 **Rodar a reverificação de base** (`research.md` §0.3). A base do PR é `origin/2026.3`, mas a varredura foi feita contra `origin/2026.2`. **B1** e **B2** podem alterar decisões deste plano — ver P5 e §4. |
| P1 | Branch `feature/turing-generic-profiles` (`eb83366820…`) com o conteúdo do PR #2668. A estatística "130 arquivos, +10.374/-332" foi medida contra `origin/2026.2`; contra `origin/2026.3` será maior. |
| **P5** | 🔶 Medir o quanto o branch está atrás da base: `git rev-list --count HEAD..origin/2026.3`. A última integração com upstream foi um merge de **`origin/2026.2`** (reflog `86ad604`). Decidir com o revisor se o enxugamento vem **antes** ou **depois** de atualizar o branch para `2026.3` — fazer depois evita refazer conflitos, fazer antes deixa o diff limpo para a revisão. |
| P2 | **Baseline verde antes de tocar em qualquer coisa**: `mvn -pl turing-app test` e `pnpm -C frontend build`. Se o baseline já estiver vermelho, resolver isso primeiro — senão não há como atribuir uma quebra a este trabalho. |
| P3 | Decisões **Q1–Q7** do [`spec.md`](spec.md) §7 respondidas. **Q2** (destino da pasta `specs/001-*`) e **Q3** (renumerar changelogs) alteram o escopo das Fases 6 e 1 respectivamente; **Q7** decide se este plano roda antes ou depois de atualizar o branch para `origin/2026.3`. |
| P4 | Confirmar com o revisor que o PR é **rebase/force-push** e não merge incremental — várias tarefas apagam arquivos que o próprio PR adicionou, e o diff final deve ficar limpo. |

**Recomendação de commits:** um commit por fase, exceto a Fase 1, que precisa dos 8 commits da §2
(ou de um único commit atômico — ver a nota no fim da §2). Isso é o que dá reversibilidade (NFR-03).

---

## §2 Fase 1 — Remoção da formação 001 · **ORDEM OBRIGATÓRIA**

### Por que a ordem importa

`spring.jpa.hibernate.ddl-auto: none`
([application.yaml:194](../../turing-app/src/main/resources/application.yaml)) — o **Liquibase é a
única autoridade de schema**. Consequência: se o `v2026.2.7.8.yaml` for apagado antes dos consumidores,
as tabelas `sn_site_facet_profile` / `sn_site_facet_profile_item` deixam de existir, mas o código que
as consulta continua no ar. Como `TurSNSiteFacetProfileOnStartup` e o passo 1 de
`TurProfileMigrationOnStartup` são `ApplicationRunner`, a consulta acontece **no boot** e o Spring
aborta com `InvalidDataAccessResourceUsageException` em **qualquer base com ≥1 site SN**. Não é uma
falha de teste — é a aplicação não subir.

A regra é **consumidor antes de dono, schema por último**.

### Os 8 passos

| # | Passo | Arquivos / linhas | Por que aqui |
|---|-------|-------------------|--------------|
| **1** | Hook de escrita no import | `sn/TurSNSiteImport.java` — 58 (import), 99 (campo), 121 + 142 (construtor), **195-198** (chamada `ensureDefaultProfile`) · `TurSNSiteImportTest.java` — 61, 99-103, **427-428** | É o consumidor mais externo. **Atenção:** o teste tem `verify(turSNSiteFacetProfileService).ensureDefaultProfile(...)` na 427-428; removê-lo é parte deste passo ou o módulo **não compila**. |
| **2** | Runner de seed do 001 | Deletar `onstartup/sn/TurSNSiteFacetProfileOnStartup.java` (66) e `TurSNSiteFacetProfileOnStartupTest.java` (77) | Segundo consumidor no boot. |
| **3** | Passo 1 da migração | `onstartup/profile/TurProfileMigrationOnStartup.java` — bloco **111-128** (migração `sn_site_facet_profile` → `tur_profile_*`) · `TurProfileMigrationOnStartupTest.java` — imports **25/26/32** e o **stub da linha 137** | **Mesmo commit** do passo 2. Deixar o stub órfão gera `UnnecessaryStubbingException` (o projeto roda Mockito em modo estrito). O passo 2 da migração (`TurSNSiteFieldExt.facetPosition` → perfil "Geral") **fica** — é a única fonte de dado legado real. Ver `research.md` §A.6. |
| **4** | Acoplamento na entidade de site | `persistence/model/sn/TurSNSite.java` — 35 (import), 74 (`@JsonIgnoreProperties`), **219-221** (`Set<TurSNSiteFacetProfile>`) | Só depois que ninguém mais navega pela coleção. **MapStruct não é blocker** — verificado em `research.md` §A.8. |
| **5** | Serviço, DTOs e testes | Deletar `sn/facet/TurSNSiteFacetProfileService.java` (399) · `api/sn/bean/TurSNSiteFacetProfileDto.java` (47) · `api/sn/bean/TurSNSiteFacetProfileRequest.java` (34) · `TurSNSiteFacetProfileServiceTest.java` (324) · `TurSNSiteFacetProfileRequestTest.java` (54) | Sem injeções restantes depois dos passos 1-3. |
| **6** | Repositórios | Deletar `persistence/repository/sn/field/TurSNSiteFacetProfileRepository.java` (46) e `...ItemRepository.java` (40) | O `ItemRepository` **já é órfão** no PR — nunca foi injetado em lugar nenhum. |
| **7** | Entidades | Deletar `persistence/model/sn/field/TurSNSiteFacetProfile.java` (112) e `TurSNSiteFacetProfileItem.java` (93) | Os 2 dos 4 arquivos que o revisor citou nominalmente. |
| **8** | Schema — **por último** | Deletar `db/changelog/v2026.2.7.8.yaml` (114) e remover o `include` em `db.changelog-master.yaml` **108-109** | Só é seguro agora. Ver §3 sobre o que **não** fazer aqui. |

**Total: 12 arquivos deletados, ~1.406 linhas.** Os outros 4 arquivos são editados, não removidos.

> **Alternativa aceitável:** fazer os 8 passos num **único commit atômico**. A ordem continua
> importando dentro do commit (para não ficar com um estado intermediário que não compila enquanto se
> edita), mas a árvore nunca fica publicada num estado que não sobe. O que **não** é aceitável é
> deletar o changelog num commit anterior ao dos consumidores.

---

## §3 O que a Fase 1 **não** faz

| Não fazer | Por quê |
|---|---|
| Escrever um changeset `dropTable` para `sn_site_facet_profile` | 🔶 **Condicional a B1.** O `v2026.2.7.8.yaml` foi criado por commit do próprio branch (reflog `6943066`) e não está em `origin/2026.2`. **Se também estiver ausente de `origin/2026.3`**, as tabelas nunca existiram em instalação publicada e um `dropTable` só criaria migração para um estado que ninguém tem — decisão D2 do `spec.md`. **Se estiver presente**, D2 inverte: `dropTable` passa a ser obrigatório e é preciso plano de migração dos dados existentes. |
| Remover o passo 2 de `TurProfileMigrationOnStartup` | É a migração de `TurSNSiteFieldExt.facetPosition` (dado legado **real**) para o perfil "Geral". Sem ele, toda instalação existente perde a ordenação de facetas. |
| Manter um alias `facetProfile` no parâmetro de busca | NFR-04. O parâmetro é `profile` e ponto — o `facetProfile` nunca foi publicado, um alias só perpetuaria o nome que o revisor pediu para sumir. |
| Renumerar os changelogs 7.9/7.10 para ocupar o 7.8 | Depende da Q4. Ver §4 abaixo. |

---

## §4 Fase 1-bis — Renumeração dos changelogs *(condicional à Q3 · 🔶 e a B2)*

Após o passo 8, a sequência fica `…7.7 → 7.9 → 7.10`, com um buraco no 7.8.

> 🔶 **Verificar B2 primeiro.** A árvore atual tem upstream até `v2026.2.7.7` e depois os três
> changelogs do branch (7.8, 7.9, 7.10 — `db.changelog-master.yaml:108-113`). Mas a última integração
> com upstream foi um merge de **`origin/2026.2`**. Se `origin/2026.3` já publicou os seus próprios
> `v2026.2.7.8/7.9/7.10` (ou equivalentes), há **colisão de nome de changelog** — dois arquivos
> diferentes disputando o mesmo `filename` no `DATABASECHANGELOG`. Nesse caso a Q4 **deixa de ser
> estética**: renumerar os do branch é obrigatório e vira parte da Fase 1, não um opcional.

Duas saídas, **assumindo B2 confirmado (números livres em `2026.3`)**:

- **Q3 = "deixar o buraco"** *(recomendado)* — nada a fazer. Numeração de changelog é identidade, não
  contagem; buracos são inofensivos e renomear arquivo de Liquibase é a classe de mudança que quebra
  base de quem já rodou o branch.
- **Q3 = "renumerar"** — renomear `v2026.2.7.9.yaml` → `v2026.2.7.8.yaml` e `v2026.2.7.10.yaml` →
  `v2026.2.7.9.yaml`, ajustar os dois `include` no master, **e** conferir se algum `changeSet id`
  carrega o número no nome. Só é seguro porque nenhuma instalação publicada rodou esses changelogs —
  mas invalida qualquer ambiente de dev que já tenha rodado o branch (o `DATABASECHANGELOG` guarda o
  `filename`). Se escolher isso, avisar o time.

---

## §5 Fase 2 — Resíduos de nomenclatura em código que fica

Independente da Fase 1. Oito pontos em backend + frontend + `agents.md` (`research.md` §B e §B.1).

| Alvo | Ação |
|---|---|
| `sn/field/TurSNFieldProcess.java` + teste | Renomear identificadores/parâmetros locais que ainda dizem `facetProfile` para `profile`. Sem mudança de comportamento. |
| Frontend — 4 arquivos, 8 ocorrências | Idem: nomes de variável/prop/chave. O contrato de rede já é `profile` (`react-sdk/src/core/api.ts:33` declara `profile?: string`, `:50` faz `qs.append("profile", …)`). |
| [`agents.md:316`](../../agents.md) | Exemplo canônico de Jackson 3 usa `TurSNSiteFacetProfileRequest(String name, String description)` — trocar por um record que continuará existindo. |
| [`agents.md:320`](../../agents.md) | Gotcha de LAZY + `@Cacheable` está ancorado em `TurSNSiteFacetProfileService.seedDefaultProfile`/`persistOrdering` — reancorar no equivalente 002. |
| [`agents.md:321`](../../agents.md) | Linha em branco perdida no meio de uma lista contígua de bullets. |
| [`agents.md:322`](../../agents.md) | **Está factualmente errado hoje**: documenta a allowlist do SDK terminando em `facetProfile`. O código real usa `profile`. Corrigir. |

---

## §6 Fase 3 — Deduplicação interna à formação nova

~90 linhas repetidas em 4 classes das specs 002/003 (`research.md` §C) — helpers copiados entre
`TurProfileService`, `TurProfileFacetOrderingService`, `TurProfileFieldConfigService` e a API.
Extrair para um ponto único.

Também nesta fase: a **superfície órfã de repositório** (§C.1) — métodos de query declarados e nunca
chamados. Remover.

**Atenção ao §C.2** do `research.md`: há duplicações **aparentes** que não devem ser tocadas (assinaturas
que coincidem por serem de contratos diferentes). Ler antes de generalizar.

---

## §7 Fase 4 — Escopo solto

Três workstreams vazaram para o PR. **Um deles foi refutado** pela verificação adversarial.

### 4a — Self-healing de `multiValued` · **REVERTER, atomicamente e nesta ordem**

Reverter parcialmente **quebra**. A ordem é obrigatória:

1. o teste;
2. `TurSNProcessQueue`;
3. o override no cliente Solr;
4. `TurSolrUtils`;
5. **a interface por último**.

Duas armadilhas medidas: reverter **só** o `TurSolrUtils` quebra porque
`TurSolrSearchEnginePlugin:207` o chama direto; reverter **só** o override transforma a feature num
**no-op silencioso** — pior que o estado atual, porque o código continua lá dando a impressão de
funcionar.

### 4b — Resiliência do Solr · **MANTER** *(condicional à Q1)*

A classificação de "escopo solto" foi **refutada**. `TurSolrQueryBuilder:840` (`resolveEnabledFacets`,
adicionado por este PR) permite que um perfil não-default **ligue uma faceta num campo que pode não
existir no core**. O Solr então rejeita a **query inteira**, documentos incluídos, e
`TurSNSearchProcess:178` (intocado pelo PR) devolve um bean vazio → tela branca. O retry em
`TurSolr.executeSolrQuery`, as guardas em `resolve.ts` e a guarda em `search-content.tsx` são a **rede
de segurança da própria feature de perfis**. Removê-las deixaria um caminho de tela branca acionável
por configuração de perfil.

**Recomendação:** manter no PR e explicar isso ao revisor no comentário. Se ele ainda quiser fora,
tirar — mas então o caminho de tela branca precisa virar issue.

### 4c — `index.html` · **REVERTER**

Alteração cosmética sem relação com perfis.

### 4d — `@Secured` · **REVERTER — atenção ao escopo real**

A proposta original dizia "manter `@Secured` só nos endpoints **novos**". Isso está **errado**:
`TurSNSiteFacetedFieldAPI:78` e `:86` cobrem rotas que **já existiam em `origin/2026.2` sem
`@Secured`** — `GET /api/sn/{id}/facet` e `PUT /api/sn/{id}/facet/ordering`. Adicionar autorização a
rota preexistente é mudança de comportamento de API e não pertence a este PR. Reverter também essas
duas.

---

## §8 Fase 5 — Defeitos reais

Nove defeitos (E1–E9) mais i18n e um nit de diff — `research.md` §E. São achados **legítimos** da
varredura, não limpeza: cache stale, resolução divergente de perfil, 33 chaves de i18n órfãs.

**Decisão Q6:** corrigir dentro deste PR ou abrir follow-up? Recomendação: corrigir os que são
**consequência direta** da feature de perfis (cache stale e resolução divergente) neste PR; as 33
chaves órfãs de i18n e o nit de diff podem ir junto por serem triviais e de risco zero.

---

## §9 Fase 6 — Documentação

| Alvo | Ação |
|---|---|
| `specs/001-perfis-ordenacao-facetas/` | **Q2**: deletar a pasta ou marcar `SUPERSEDED BY 002`? Recomendação: marcar, não deletar — o registro de por que a abordagem mudou tem valor, e é exatamente a pergunta que o revisor fez. |
| `specs/002-.../research.md:103-110` | A decisão DT-7 ("manter artefatos do 001 por 1 release para rollback") está baseada em premissa falsa. Anotar a correção *in loco*, não apagar. |
| `specs/002` e `003` | Ajustar as referências a `facetProfile` / `TurSNSiteFacetProfile` que descrevem o estado corrente (`research.md` §F). |
| `agents.md` | Já coberto na Fase 2. |
| CHANGELOG | Entrada única para o enxugamento. |

---

## §10 Fase 7 — Validação e fechamento

| # | Critério | Como verificar |
|---|---|---|
| V1 | Build backend verde | `mvn -pl turing-app test` |
| V2 | Build frontend verde | `pnpm -C frontend build` |
| V3 | **Zero ocorrências** de `FacetProfile\|facetProfile\|facet_profile\|FACET_PROFILE\|sn_site_facet_profile` fora de `specs/001-*` (se mantida) e `specs/004-*` | `grep -rIn` repo-wide, excluindo `target/` e `node_modules/` |
| V4 | Boot limpo em base **com dados** | Subir contra uma base que tenha ≥1 site SN e `TurSNSiteFieldExt.facetPosition` preenchido; conferir que o perfil "Geral" recebe as posições |
| V5 | Boot limpo em base **vazia** | Idem, base zerada |
| V6 | Idempotência do boot | Subir **duas vezes** seguidas na mesma base — o passo 2 da migração não pode duplicar nem sobrescrever |
| V7 | `target/` regenerado | `mvn clean` antes da validação final, para garantir que nenhum `*MapperImpl` velho está mascarando erro |
| V8 | Diff final revisado arquivo a arquivo | O objetivo do PR é *enxugar* — o diff é o entregável |

---

## §11 Estimativa e ordem de ataque sugerida

| Fase | Risco | Nota |
|---|---|---|
| 1 | **Alto** | Ordem obrigatória; é onde mora a possibilidade de derrubar boot |
| 2 | Baixo | Renomeação mecânica |
| 3 | Médio | Refatoração; cuidado com §C.2 |
| 4 | **Alto** (4a) | Reversão atômica em 5 passos |
| 5 | Médio | Depende da Q3 |
| 6 | Baixo | Texto |
| 7 | — | Validação |

**Sugestão:** Fase 1 → Fase 7 (validar) → Fase 4a → Fase 7 → demais fases. As duas de alto risco
ficam isoladas entre validações completas, o que torna trivial identificar o culpado se algo quebrar.
