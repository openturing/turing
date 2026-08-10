# HANDOFF — 004 Enxugamento do PR de Perfis

**Status:** especificação completa. **Nenhuma alteração de código aplicada.**
**Branch:** `feature/turing-generic-profiles` (`eb83366820…`) → base **`origin/2026.3`** (`5e01e4050f…`).
**⚠️ A varredura foi feita contra `origin/2026.2`.** Ver §2.1 — 10 itens precisam de reverificação.
**Origem:** revisão do criador do Turing — *"Parece que está duplicado o `TurProfile` e o
`TurSNSiteFacetProfile`. Ainda aparece referência a `FacetProfile`. […] Tenta enxugar o que for possível."*

---

## 1. O diagnóstico do revisor está certo — e o problema é maior que os 4 arquivos citados

O PR carrega **duas formações de perfil completas em paralelo**:

| | Formação **001** (antiga) | Formação **002/003** (atual) |
|---|---|---|
| Entidade raiz | `TurSNSiteFacetProfile` | `TurProfile` |
| Escopo | por site | global |
| Tabelas | `sn_site_facet_profile`, `…_item` | `tur_profile`, `tur_profile_facet_ordering`, `…_item` |
| Liquibase | `v2026.2.7.8.yaml` | `v2026.2.7.9`, `v2026.2.7.10` |
| Param de busca | `facetProfile` | `profile` |

Os 4 arquivos que o revisor listou são a **ponta**: 2 deles (`TurSNSiteFacetProfile`,
`TurSNSiteFacetProfileItem`) devem sumir; os outros 2 (`TurProfileFacetOrdering`,
`TurProfileFacetOrderingItem`) são a formação **correta** e ficam. O total a remover é de
**12 arquivos / ~1.406 linhas**, mais 4 arquivos a editar por acoplamento.

---

## 2. O achado que destrava tudo

A spec 002 decidiu (`002/research.md:103-110`, **DT-7**) manter os artefatos do 001 *"por 1 release
para rollback"*. **Essa premissa é provavelmente falsa.** Contra `origin/2026.2`:

```
git log --oneline origin/2026.2 -- …/db/changelog/v2026.2.7.8.yaml   → vazio
git show origin/2026.2:…/db.changelog-master.yaml | tail -3          → último include = v2026.2.7.7.yaml
git ls-tree -r --name-only origin/2026.2 -- specs/                   → vazio
```

E, independentemente da base, o **reflog** prova a origem do arquivo:

```
6943066  feat: add facet ordering profiles for semantic navigation   ← criou o v2026.2.7.8.yaml
```

— commit **do próprio branch de feature**, não de upstream. Se o arquivo também estiver ausente de
`origin/2026.3` (item **B1**), então nenhuma instalação publicada teve as tabelas do 001, **não existe
dado legado a preservar nem rollback a proteger**, e não faz sentido escrever um `dropTable`.

### 2.0 🔴 RESULTADO da reverificação (2026-08-07) — o branch está 1316 commits atrás

`origin/2026.3` não avançou um pouco: mudou de linha de release. O master changelog da base termina em
**`v2026.3.1.122.yaml`**; o do branch, em `v2026.2.7.10.yaml`. O PR real é **135 arquivos, +10.460/−625**.

Três consequências que reordenam o trabalho:

1. **A renumeração de changelog virou pré-requisito, não opcional.** A base já ocupa `v2026.2.7.8`,
   `7.9` e `7.10` com changesets alheios. Os três do branch precisam ir para `v2026.3.1.123+`.
2. **Atualizar o branch para `2026.3` vem PRIMEIRO** (Q7 respondida). Enxugar antes é refazer no merge.
3. **B1 foi falso alarme meu.** O `v2026.2.7.8.yaml` existe na base, mas criado por
   `d78891df7f "feat: add chat mode toggle for LLM execution in agents"` — outra feature, mesmo nome.
   Meu guard testava o **nome** do arquivo, não o conteúdo; já foi corrigido para procurar
   `sn_site_facet_profile` em todos os changelogs da base.

Também mudou **B4**: `TurSNSiteFacetedFieldAPI` não aparece no caminho esperado em `2026.3`, então as
rotas `:78`/`:86` **não** são pré-existentes lá — a **FR-16 encolhe** para só `TurSNSiteFieldExtAPI`.
E **B6/B7 confirmaram** que `TurSolrQueryBuilder` (+85/−8), `TurSolr` (+109/−9) e `TurSolrUtils`
(+37/−1) são novos neste PR, o que **sustenta o argumento da Q1**.

Detalhamento em [`research.md`](research.md) §0.5 e §0.6. Pendências finas no
`scripts/00b-verificar-colisao.sh`.

### 2.1 ⚠️ Correção de base — pendente de reverificação

A base correta do PR é **`origin/2026.3`**, não `origin/2026.2`. O reflog mostra que a última
integração do branch com upstream foi um **merge de `origin/2026.2`** (`86ad604`), ou seja: o branch
está **atrás** da base.

O mapeamento **não foi invalidado**: inventário, correspondência 21/21, acoplamentos, ordem obrigatória,
MapStruct e sweep são fatos da árvore de trabalho. O que muda são **10 itens** (`research.md` §0.2,
marcados 🔶 BASE). Dois podem alterar decisões:

| | Se confirmar o contrário… |
|---|---|
| **B1** — `v2026.2.7.8.yaml` ausente em `2026.3` | **D2 inverte**: passaria a exigir `dropTable` + plano de migração de dados |
| **B2** — números 7.8/7.9/7.10 livres em `2026.3` | **Colisão de changelog**; a **Q4 deixa de ser cosmética** e renumerar vira obrigatório |

Comandos prontos em `research.md` §0.3. Bloqueados por indisponibilidade do classificador de segurança
que autoriza execução de shell — nenhum `git` pôde rodar nesta sessão.

⚠️ **O dado legado real é outro:** `TurSNSiteFieldExt.facetPosition`. É ele que o **passo 2** de
`TurProfileMigrationOnStartup` migra para o perfil "Geral" — e esse passo **tem de continuar**.

---

## 3. Prova de que nada precisa ser portado antes de remover

`TurSNSiteFacetProfileService` (399 linhas) → **21 de 21 métodos** têm contraparte em
`TurProfileFacetOrderingService` (341) + `TurProfileService` (172). Seis são idênticos byte a byte:
`loadCustomFacets`, `toOrderingDto`, `requireSite`, `positionsByFacetId`, `buildItem`, `requireName`.

O único comportamento exclusivo do 001 — `create():153-158`, que **copiava** a ordenação do perfil
default no momento da criação — foi substituído por um fallback de **leitura** em
`resolveReadPositions:223-240`, que é estritamente melhor (não congela a cópia).

E o 002 ainda **corrige um defeito** do 001: `001:319` salvava a entidade errada
(`customFacet.getTurSNSiteFieldExt()`); `002:272` salva a faceta custom pelo repositório próprio.

---

## 4. O que **não** pode ser feito na ordem errada

`spring.jpa.hibernate.ddl-auto: none` (`application.yaml:194`) — Liquibase é a única autoridade de
schema. Apagar o `v2026.2.7.8.yaml` **antes** dos consumidores derruba o boot com
`InvalidDataAccessResourceUsageException` em qualquer base com ≥1 site SN, porque
`TurSNSiteFacetProfileOnStartup` e o passo 1 da migração são `ApplicationRunner`.

**Consumidor antes de dono, schema por último.** Os 8 passos estão em [`plan.md`](plan.md) §2 e como
checklist em [`tasks.md`](tasks.md) T1.

---

## 5. Correções que a verificação adversarial trouxe

Cada remoção proposta foi submetida a um segundo agente encarregado de **refutá-la**. Quatro
propostas não sobreviveram na forma original:

| Proposta | Correção |
|---|---|
| Remover o hook do import | Esquecia `TurSNSiteImportTest.java:427-428` (`verify(...).ensureDefaultProfile(...)`) → **não compila** |
| Enxugar o teste da migração | Esquecia o **stub da linha 137** (→ `UnnecessaryStubbingException`, Mockito estrito) e os imports 25/26/32 |
| Reverter o self-healing de `multiValued` | Reverter **só** `TurSolrUtils` quebra (`TurSolrSearchEnginePlugin:207` chama direto); reverter **só** o override vira **no-op silencioso**. Ordem atômica: teste → `TurSNProcessQueue` → override → `TurSolrUtils` → **interface por último** |
| Reverter `@Secured` "exceto nos endpoints novos" | `TurSNSiteFacetedFieldAPI:78` e `:86` cobrem rotas que **já existiam** em `origin/2026.2` sem `@Secured` — também precisam ser revertidas |

### E a correção mais importante — um "escopo solto" que **não é**

O grupo de resiliência do Solr (retry em `TurSolr.executeSolrQuery`, guardas em `resolve.ts` e
`search-content.tsx`) foi classificado como escopo solto e **isso foi refutado**. Ele é **causalmente
acoplado à feature**: `TurSolrQueryBuilder:840` (`resolveEnabledFacets`, adicionado por este PR)
permite que um perfil não-default ligue uma faceta num campo que pode não existir no core; o Solr
rejeita a **query inteira**, documentos incluídos; `TurSNSearchProcess:178` (intocado pelo PR) devolve
bean vazio → **tela branca**, acionável por configuração de perfil.

**Recomendação:** manter no PR e explicar ao revisor. Ver **Q1**.

---

## 6. Decisões pendentes

Numeração canônica em [`spec.md`](spec.md) §7 — esta tabela é o resumo.

| # | Pergunta | Recomendação |
|---|---|---|
| **Q1** (D4) | Manter a resiliência do Solr no PR? | **Manter** — é rede de segurança da própria feature (§5). Se o revisor insistir em remover, o filtro de facet fields ausentes no `TurSolrQueryBuilder` vira pré-requisito |
| **Q2** (D6) | A pasta `specs/` fica? Deletar só o 001, deletar tudo, ou manter? | **Manter, com o 001 marcado `SUPERSEDED BY 002`** — o registro de *por que* a abordagem mudou é exatamente a pergunta que o revisor fez |
| **Q3** (D7) | Renumerar `.9`→`.8` e `.10`→`.9`? | 🔶 **Depende de B2.** Se `2026.3` não usa esses números: **deixar o buraco**. Se **usa**: renumerar é **obrigatório** — é colisão de `DATABASECHANGELOG.filename`, não estética |
| **Q4** (D8) | Padronizar o prefixo `tur_` das 4 tabelas novas? | Fora do escopo deste enxugamento; se for padronizar, vira spec própria |
| **Q5** | Os workstreams revertidos viram PRs separados agora ou depois? | São 3: self-healing `multiValued`, resiliência de busca (só se Q1 = sair) e `notranslate` |
| **Q6** | Defeitos do Bloco E neste PR ou follow-up? | Neste PR os que são consequência direta da feature (cache stale, resolução divergente); i18n órfão e o nit de diff vão junto (risco zero) |
| **Q7** 🔶 | Enxugar antes ou depois de atualizar o branch para `origin/2026.3`? | **Atualizar primeiro** — B1 e B2 só têm resposta definitiva depois disso |

---

## 7. Estado da verificação

| Verificado | Como |
|---|---|
| Inventário completo do 001 (12 arquivos, 1.406 linhas) | Leitura arquivo a arquivo |
| Correspondência 21/21 dos métodos | Comparação método a método |
| Pontos de acoplamento (4 arquivos, com linhas) | Grep + leitura |
| Ordem obrigatória de remoção | Análise de `ddl-auto` + `ApplicationRunner` + refutação adversarial |
| MapStruct não é blocker | `TurSNSiteMapper.java` não cita o 001; **zero** `unmappedTargetPolicy` no projeto (`research.md` §A.8) |
| Sweep repo-wide | 46 arquivos casam os padrões; **todos** mapeados. Zero em `.github/`, `scripts/`, `k8s/`, `containers/`, `docker-compose.yaml`, `*.cmd` (`research.md` §B.0) |
| Sem vazamento no exchange/export | `research.md` §A.9 |

| 🔶 **Contra a base correta (`origin/2026.3`)** | **NÃO verificado** — 10 itens em `research.md` §0.2; comandos em §0.3 |
| 🔶 Branch está atrás da base | **NÃO medido** (`git rev-list --count HEAD..origin/2026.3`) |

**Não verificado** (ver `research.md` §G): comportamento em runtime contra base real — a validação
V4/V5/V6 de [`tasks.md`](tasks.md) T7 existe justamente para isso.

**Bloqueio da sessão:** nenhum comando de shell pôde ser executado — `Bash` e `PowerShell` retornaram
*"temporarily unavailable, so auto mode cannot determine the safety"* em todas as tentativas. As
evidências de git nesta seção vieram de leitura direta de `.git/packed-refs`, `.git/refs/` e
`.git/logs/refs/` (arquivos de texto). O GitHub também não serviu de alternativa: `api.github.com` e
`raw.githubusercontent.com` devolvem **404** para `openviglet/turing` (repositório privado ou fetch
bloqueado).

---

## 8. Próximo passo

1. 🔶 **Rodar a reverificação de base** (`research.md` §0.3) assim que houver shell — fecha B1–B10 e
   dá resposta definitiva a Q3 e Q7.
2. Responder Q1–Q7 ([`spec.md`](spec.md) §7).
3. Executar [`tasks.md`](tasks.md) na ordem: **T0.0 → T1 → T7 → T4a → T7 → demais**. As duas fases de
   alto risco ficam isoladas entre validações completas.
3. No comentário do PR, responder o revisor cobrindo: (a) o que foi removido e quanto; (b) por que
   não há `dropTable`; (c) por que a resiliência do Solr fica.

---

## 9. Documentos

- [`README.md`](README.md) — visão geral e blocos A–F
- [`spec.md`](spec.md) — decisões D1–D8, requisitos FR-01…FR-25, NFR-01…NFR-04, aceite AC-01…AC-08, riscos
- [`research.md`](research.md) — **o mapeamento completo, arquivo por arquivo, com evidência e veredicto**
- [`plan.md`](plan.md) — 7 fases e a ordem obrigatória
- [`tasks.md`](tasks.md) — checklist executável com linhas exatas
