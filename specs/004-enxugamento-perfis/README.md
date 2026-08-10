# 004 — Enxugamento do PR de Perfis (remoção da formação 001)

Remove do PR [#2668](https://github.com/openviglet/turing/pull/2668) tudo o que sobrou da **formação
antiga** de perfis (`TurSNSiteFacetProfile`, parâmetro `facetProfile`, Liquibase `v2026.2.7.8`),
corrige os resíduos de nomenclatura que ainda citam "FacetProfile" e separa do PR as mudanças que
não pertencem à feature de perfis.

> ## ⚠️ Correção de base — 2026-08-07
>
> A varredura foi feita contra **`origin/2026.2`**; a base correta do PR é **`origin/2026.3`**.
> O mapeamento **não foi invalidado** — inventário, correspondência de métodos, acoplamentos e a ordem
> obrigatória de remoção são fatos da árvore de trabalho e independem da base. O que precisa de
> reverificação são **10 itens** listados em [`research.md`](research.md) **§0.2**, marcados 🔶 BASE.
>
> Dois deles podem mudar decisões:
> - **B1** — se `v2026.2.7.8.yaml` existir em `2026.3`, a decisão **D2 inverte** (passaria a exigir `dropTable`).
> - **B2** — se `2026.3` já usa os números 7.8/7.9/7.10, há **colisão de changelog** e a **Q4 deixa de ser
>   cosmética**: renumerar vira obrigatório.
>
> Evidência já obtida sem shell (reflog local): o `v2026.2.7.8.yaml` foi criado pelo commit `6943066`
> **no próprio branch de feature**, e a última integração com upstream foi um merge de **`origin/2026.2`**
> — ou seja, o branch está atrás de `2026.3`. Ver `research.md` §0.1.
>
> Os comandos da reverificação estão em `research.md` §0.3. Estão pendentes por indisponibilidade do
> classificador de segurança que autoriza execução de shell.

## Origem

Revisão do criador do Turing no PR #2668:

> "Parece que está duplicado o `TurProfile` e o `TurSNSiteFacetProfile`. Ainda aparece referência a
> `FacetProfile`. [...] assim vc consegue ver os arquivos modificados, e ver se tem arquivo sobrando.
> Tenta enxugar o que for possível."

O diagnóstico está correto e é mais amplo do que os 4 arquivos citados. O PR carrega **duas formações
de perfil completas em paralelo** (001 per-site e 002 global), porque a spec 002 decidiu
(`002/research.md:103-110`, DT-7) manter os artefatos do 001 "por 1 release para rollback". Essa
premissa é **provavelmente falsa**: o `v2026.2.7.8.yaml`, que cria as tabelas do 001, foi criado pelo
commit `6943066` **no próprio branch de feature** (evidência: reflog, `research.md` §0.1) e não existe
em `origin/2026.2`. Se também não existir em `origin/2026.3` — reverificação **B1** pendente — então
nenhum usuário jamais teve essas tabelas, e não existe dado legado a preservar nem rollback a proteger.

## O que este spec cobre

| Bloco | Conteúdo |
|-------|----------|
| **A** | Remoção da formação 001 (~1.406 linhas: entidades, repos, serviço, OnStartup, DTOs, testes, Liquibase) |
| **B** | Resíduos de nomenclatura `facetProfile` em código que **fica** (`TurSNFieldProcess`, frontend, `agents.md`) |
| **C** | Duplicação **interna** à formação nova (~90 linhas repetidas em 4 classes 002/003) |
| **D** | Escopo solto: 3 workstreams que vazaram para o PR |
| **E** | Defeitos reais encontrados na varredura (cache stale, resolução divergente de perfil, i18n órfão) |
| **F** | Documentação: specs 001/002/003, `agents.md`, CHANGELOG |

## Documentos

- [`spec.md`](spec.md) — problema, decisões, requisitos e critérios de aceite.
- [`research.md`](research.md) — **o mapeamento completo, arquivo por arquivo**, com evidência e veredicto.
- [`plan.md`](plan.md) — as 7 fases e a **ordem obrigatória** de remoção.
- [`tasks.md`](tasks.md) — checklist executável.
- [`HANDOFF.md`](HANDOFF.md) — estado consolidado e decisões pendentes.

## Achado mais importante da varredura

A ordem das remoções **não é livre**. O projeto roda com `spring.jpa.hibernate.ddl-auto: none`
([application.yaml:194](../../turing-app/src/main/resources/application.yaml)) — o Liquibase é a única
autoridade de schema. Apagar o `v2026.2.7.8.yaml` antes de remover as entidades e os `ApplicationRunner`
que consultam `sn_site_facet_profile` **derruba o boot** de qualquer instalação com ≥1 site SN. O
changelog tem de ser o **último** passo. Ver `plan.md` §2.

## Contra-achado (atenção)

A verificação adversarial **refutou** a classificação de "escopo solto" para o grupo de resiliência do
Solr (retry de faceta indefinida + guardas no front). Ele é **causalmente acoplado à spec 003**:
`TurSolrQueryBuilder.resolveEnabledFacets` permite que um perfil ligue faceta num campo que pode não
existir no core, e o Solr então rejeita a **query inteira**. Ver `spec.md` D4 e `research.md` §D.2.

> **Status:** especificação. Nenhuma alteração aplicada ainda.
