# Research — Mapeamento completo dos resíduos

Varredura exaustiva do PR #2668. Cada item foi verificado no código e, quando implica remoção, passou
por **verificação adversarial** (um segundo agente tentando refutar). Itens marcados **⚠ REFUTADO**
tiveram a proposta original corrigida — leia a coluna de observação.

Convenção: caminhos relativos à raiz do repo. `target/` e `node_modules/` ignorados em toda a varredura.

> ## ⚠️ CORREÇÃO DE BASE — leia antes de usar este documento
>
> A varredura original comparou `feature/turing-generic-profiles` × **`origin/2026.2`**. A base
> correta do PR é **`origin/2026.3`**. Isso **não invalida o mapeamento**, mas invalida um subconjunto
> específico de conclusões — ver **§0.2**. Enquanto a reverificação não roda, trate os itens marcados
> **🔶 BASE** como *hipótese*, não como fato.

---

## §0 — Base, ancestralidade e o que depende dela

### 0.1 O que está estabelecido sobre a linhagem do branch

Do reflog local (`.git/logs/refs/heads/feature/...`), que é evidência direta e não depende do shell:

```
feature/turing-facet-ordering-profiles:
  0000000 → 4a3c2b2   branch: Created from HEAD
  4a3c2b2 → 6943066   commit: feat: add facet ordering profiles for semantic navigation   ← cria o v2026.2.7.8.yaml
  6943066 → 10ca23f   commit: docs: update facet profiles spec-kit with validated session state
  10ca23f → 86ad604   commit (merge): Merge origin/2026.2 into feature/turing-facet-ordering-profiles

feature/turing-generic-profiles:
  0000000 → 86ad604   branch: Created from HEAD
  86ad604 → eb83366   commit: feat(sn): perfis genéricos globais + config de faceta por campo/perfil (specs 002/003)
```

Três fatos que saem daí:

1. **O `v2026.2.7.8.yaml` foi criado pelo commit `6943066`, no próprio branch de feature.** Não veio
   de upstream. Essa é a evidência mais forte de que o DDL do 001 nunca esteve publicado — e ela é
   independente de qual seja a base.
2. **O branch integrou `origin/2026.2`, não `origin/2026.3`.** A última integração com upstream foi o
   merge `86ad604`. Se o PR tem `2026.3` como base, o branch **está atrás da base** por tudo o que
   entrou em 2026.3 depois desse ponto.
3. O branch é: trabalho do spec 001 (3 commits) + merge de upstream + **um único commit** com os
   specs 002/003.

Refs correntes (de `.git/refs/`): `HEAD` = `eb83366820…`, `origin/2026.3` = `5e01e4050f…`,
`origin/2026.2` = `554189bfe7…`.

### 0.2 Classificação: o que sobrevive à troca de base e o que não

| Sobrevive ✅ — fato da *árvore de trabalho*, independe da base |
|---|
| §A.1 — inventário dos 12 arquivos e suas contagens de linha |
| §A.2 — correspondência 21/21 de métodos 001 → 002/003 (e os 6 idênticos) |
| §A.3 / §A.4 — pontos de acoplamento e números de linha (`TurSNSiteImport:195-198`, `TurSNSite:219-221`, etc.) |
| §A.5 — a **ordem obrigatória** de remoção (decorre de `ddl-auto: none` + `ApplicationRunner`, não da base) |
| §A.6 — passo 1 da migração é morto; passo 2 (`facetPosition`) deve ficar |
| §A.8 — MapStruct não é blocker |
| §B.0 — o sweep de 46 arquivos |
| §C — duplicação interna 002/003 |
| §E — defeitos, chaves i18n órfãs |
| Correções adversariais: `TurSNSiteImportTest:427-428`, stub em `TurProfileMigrationOnStartupTest:137`, ordem atômica do cluster `multiValued`, `TurSolrSearchEnginePlugin:207` |

| 🔶 **BASE** — precisa reverificar contra `origin/2026.3` | Impacto se mudar |
|---|---|
| **B1.** `v2026.2.7.8.yaml` não existe na base | Se existir: **D2 inverte** (passa a exigir `dropTable`), DT-7 deixa de ser premissa falsa, e é preciso plano de migração de dados |
| **B2.** Numeração 7.8/7.9/7.10 está livre na base | Se 2026.3 já usa esses números: **colisão de changelog**, e a Q4 deixa de ser cosmética — renumerar vira **obrigatório** |
| **B3.** `specs/` não existe na base | Muda o escopo do bloco F |
| **B4.** `@Secured` — `TurSNSiteFacetedFieldAPI:78` e `:86` cobrem rotas preexistentes **sem** `@Secured` | Se 2026.3 já anotou: a reversão T4d encolhe |
| **B5.** Cluster `multiValued` foi **adicionado por este PR** | Se já está em 2026.3: não há o que reverter (T4a cai) |
| **B6.** Resiliência do Solr (`TurSolr.executeSolrQuery`, `resolve.ts`, `search-content.tsx`) foi adicionada por este PR | Se já está em 2026.3: a discussão da Q2 desaparece |
| **B7.** `TurSolrQueryBuilder:840` (`resolveEnabledFacets`) é novo | Base do argumento da Q2 |
| **B8.** `TurSNSearchProcess:178` está intocado pelo PR | Idem |
| **B9.** `index.html` alterado pelo PR | Se já está em 2026.3: T4c cai |
| **B10.** Estatística "130 arquivos, +10.374/−332" | Só métrica; o diff contra 2026.3 será maior |

### 0.3 Comandos da reverificação *(pendente — shell bloqueado por indisponibilidade do classificador)*

```bash
# B1 — o DDL do 001 existe na base?
git cat-file -e origin/2026.3:turing-app/src/main/resources/db/changelog/v2026.2.7.8.yaml && echo EXISTE || echo AUSENTE
git log --oneline origin/2026.3 -- turing-app/src/main/resources/db/changelog/v2026.2.7.8.yaml

# B2 — numeração livre? (o achado mais provável de doer)
git show origin/2026.3:turing-app/src/main/resources/db/changelog/db.changelog-master.yaml | tail -20
git ls-tree -r --name-only origin/2026.3 -- turing-app/src/main/resources/db/changelog/ | sort -V | tail -20

# B3
git ls-tree -r --name-only origin/2026.3 -- specs/

# B4..B9 — o que o PR realmente adiciona contra a base certa
git diff --stat origin/2026.3...HEAD
git diff origin/2026.3...HEAD -- turing-app/src/main/java/com/viglet/turing/api/sn/TurSNSiteFacetedFieldAPI.java
git diff origin/2026.3...HEAD -- '*TurSolrUtils*' '*TurSolr.java' '*TurSNProcessQueue*'
git diff origin/2026.3...HEAD -- '*TurSolrQueryBuilder*' '*TurSNSearchProcess*'
git diff origin/2026.3...HEAD -- '*index.html' '*resolve.ts' '*search-content.tsx'

# quanto o branch está atrás da base
git rev-list --count HEAD..origin/2026.3
git log --oneline HEAD..origin/2026.3 | head -40
```

### 0.4 O que **não** muda, aconteça o que acontecer

A única fonte de dados legada **real** é `TurSNSiteFieldExt.facetPosition`, que existe em release
publicada. O **passo 2** de `TurProfileMigrationOnStartup` que a migra para o perfil "Geral" tem de
ser preservado em qualquer cenário.

### 0.5 RESULTADO da reverificação contra `origin/2026.3` — executada 2026-08-07

`HEAD = eb83366820` · `origin/2026.3 = 5e01e4050f` · `merge-base = fd78b2b5f`

> ## 🔴 O achado que domina tudo: o branch está **1316 commits atrás** da base
>
> `git rev-list --count HEAD..origin/2026.3` = **1316** (e 9 à frente). A base não avançou um pouco —
> mudou de linha de release: o `db.changelog-master.yaml` de `2026.3` termina em
> **`v2026.3.1.122.yaml`**, enquanto o do branch termina em `v2026.2.7.10.yaml`.
>
> O PR real contra a base correta é **135 arquivos, +10.460 / −625**.

| Item | Resultado | Consequência |
|---|---|---|
| **B1** | ⚠️ **Falso alarme.** O nome `v2026.2.7.8.yaml` **existe** na base, mas foi criado por `d78891df7f` *"feat: add chat mode toggle for LLM execution in agents"* — **outra feature**. No branch, o mesmo nome foi criado por `69430661b0` *"feat: add facet ordering profiles"*. São changesets **diferentes com o mesmo nome**. | **D2 provavelmente se mantém** (sem `dropTable`). Confirmação definitiva pelo `00b`, item 2: se nenhum changelog da base criar `sn_site_facet_profile`, D2 está fechada. |
| **B2** | 🔴 **Colisão real e tripla.** A base tem `v2026.2.7.0` … `v2026.2.7.13` — ou seja, **7.8, 7.9 e 7.10 já estão ocupados** por changesets alheios. | **Q3 deixa de ser opcional.** Renumerar é obrigatório — e **não** para 7.8: para a sequência viva da base, `v2026.3.1.123+`. |
| **B3** | ✅ `specs/` não existe na base. | Bloco F sem mudança. |
| **B4** | ⚠️ **Inverte o meu achado anterior.** `TurSNSiteFacetedFieldAPI.java` **não existe** no caminho esperado em `2026.3` — logo as rotas `:78`/`:86` **não** são pré-existentes lá. | **FR-16 encolhe**: some a parte do `TurSNSiteFacetedFieldAPI`, sobra só `TurSNSiteFieldExtAPI`. Confirmar o caminho real pelo `00b`, item 5 (pode ter sido movido de pacote). |
| **B5 / B8** | ❓ `TurSNProcessQueue` e `TurSNSearchProcess` deram *"sem diferença / caminho inexistente"* — o caminho que passei ao script provavelmente está errado. | Resolver pelo `00b`, item 6. Se de fato não houver diferença, o self-healing de `multiValued` **já está na base** e a **T4a cai inteira**. |
| **B6 / B7** | ✅ Confirmado que são **novos neste PR**: `TurSolr.java` **+109/−9**, `TurSolrQueryBuilder.java` **+85/−8**, `TurSolrUtils.java` **+37/−1**. | O argumento da **Q1** (resiliência do Solr é rede de segurança da feature) **se mantém**: `resolveEnabledFacets` é realmente novo. |
| **B9** | ✅ Novos: `index.html` +10/−1, `search-content.tsx` +8/−1, `resolve.ts` +9/−3. | T4c e as guardas do front seguem como mapeado. |
| **B10** | ✅ Medido: 135 arquivos, +10.460/−625. | Só métrica. |

### 0.6 O que isso muda no plano

1. **Q7 está respondida: atualizar o branch para `origin/2026.3` vem PRIMEIRO.** Com 1316 commits de
   diferença e três changelogs colidindo, enxugar antes significa refazer o trabalho no merge.
2. **A renumeração (Q3) virou pré-requisito da Fase 1**, não um opcional pós-remoção. E o alvo é
   `v2026.3.1.123+`, não `7.8`.
3. **O `db.changelog-master.yaml` vai conflitar de forma não trivial** — as duas pontas divergiram em
   linhas de release distintas. O `include` do 7.8 tem de ser resolvido **dentro** desse merge.
4. **B1 foi um falso positivo do meu script**: ele testou existência de **nome de arquivo**, não de
   conteúdo. O guard do `01-fase1-remocao.sh` foi corrigido para procurar `sn_site_facet_profile` em
   todos os changelogs da base, e ganhou uma segunda guarda para a colisão de numeração.

---

## §A — Formação 001: inventário de remoção

### A.1 Arquivos que somem por inteiro

| # | Arquivo | Linhas | Quem referencia hoje | Veredicto |
|---|---------|--------|----------------------|-----------|
| A1 | `turing-app/.../persistence/model/sn/field/TurSNSiteFacetProfile.java` | 112 | `TurSNSite:35,221`, `TurSNSiteFacetProfileService:43,92,97`, `TurSNSiteFacetProfileRepository:29,37`, `TurProfileMigrationOnStartup:39,112`, `TurSNSiteFacetProfileDto:24,39` | DELETAR |
| A2 | `turing-app/.../persistence/model/sn/field/TurSNSiteFacetProfileItem.java` | 93 | `TurSNSiteFacetProfileService:44,368`, `TurSNSiteFacetProfileItemRepository:27,28,39`, `TurProfileMigrationOnStartup:40,123` | DELETAR |
| A3 | `turing-app/.../persistence/repository/sn/field/TurSNSiteFacetProfileRepository.java` | 46 | `TurSNSiteFacetProfileService:68`, `TurProfileMigrationOnStartup:82` | DELETAR |
| A4 | `turing-app/.../persistence/repository/sn/field/TurSNSiteFacetProfileItemRepository.java` | 40 | **ninguém** (já é órfão) | DELETAR |
| A5 | `turing-app/.../sn/facet/TurSNSiteFacetProfileService.java` | 399 | `TurSNSiteFacetProfileOnStartup:53`, `TurSNSiteImport:99` | DELETAR |
| A6 | `turing-app/.../onstartup/sn/TurSNSiteFacetProfileOnStartup.java` | 66 | ninguém (é `ApplicationRunner`); comentário em `v2026.2.7.8.yaml:4` | DELETAR |
| A7 | `turing-app/.../api/sn/bean/TurSNSiteFacetProfileDto.java` | 47 | só `TurSNSiteFacetProfileService` | DELETAR |
| A8 | `turing-app/.../api/sn/bean/TurSNSiteFacetProfileRequest.java` | 34 | só `TurSNSiteFacetProfileService` | DELETAR |
| A9 | `turing-app/src/test/.../sn/facet/TurSNSiteFacetProfileServiceTest.java` | 324 | — | DELETAR |
| A10 | `turing-app/src/test/.../onstartup/sn/TurSNSiteFacetProfileOnStartupTest.java` | 77 | — | DELETAR |
| A11 | `turing-app/src/test/.../api/sn/bean/TurSNSiteFacetProfileRequestTest.java` | 54 | — | DELETAR |
| A12 | `turing-app/src/main/resources/db/changelog/v2026.2.7.8.yaml` | 114 | `db.changelog-master.yaml:108-109` | DELETAR **por último** |

**Total: 1.406 linhas** (~15% das 10.374 inserções do PR), sem perda funcional.

### A.2 Prova de que nada precisa ser portado — correspondência método a método

`TurSNSiteFacetProfileService` (001, 399 ln) × `TurProfileFacetOrderingService` (002, 341) +
`TurProfileService` (002, 172). **IDENT** = corpo byte-a-byte igual; **QUASE** = >80%; **PORT** =
mesma intenção, reescrita.

| # | Método 001 | ln | Contraparte 002 | ln | Grau |
|---|-----------|-----|-----------------|-----|------|
| 1 | `ensureDefaultProfile(site)` | 91-95 | `TurProfileService.ensureDefaultProfile()` | 64-74 | PORT (site→global) |
| 2 | `seedDefaultProfile(site)` | 97-117 | `buildFromLegacy` + `legacyPositions` | 158-172 / 243-255 | PORT (dividido) |
| 3 | `list(snSiteId)` | 121-133 | `TurProfileService.list()` | 78-89 | QUASE |
| 4 | `create(snSiteId, req)` | 135-161 | `TurProfileService.create(req)` | 96-110 | QUASE ¹ |
| 5 | `update(...)` | 163-183 | `TurProfileService.update(...)` | 112-131 | IDENT |
| 6 | `setDefault(...)` | 185-199 | `TurProfileService.setDefault` + wrapper | 133-144 / 137-145 | PORT |
| 7 | `delete(...)` | 201-210 | `TurProfileService.delete(...)` | 146-155 | IDENT |
| 8 | `getOrdering(site,profile)` | 214-218 | `getOrdering(site,profile)` | 102-107 | PORT ² |
| 9 | `saveOrdering` | 220-225 | `saveOrdering` | 109-114 | IDENT |
| 10 | `getDefaultOrdering` | 227-231 | `getDefaultOrdering` | 116-121 | IDENT |
| 11 | `saveDefaultOrdering` | 233-238 | `saveDefaultOrdering` | 123-128 | IDENT |
| 12 | `buildOrdering` | 240-251 | `buildOrdering` | 149-161 | QUASE |
| 13 | `persistOrdering` | 253-303 | `persistOrdering` | 163-217 | QUASE ~90% |
| 14 | `syncLegacyPositions` | 305-325 | `syncLegacyPositions` | 257-278 | QUASE ³ |
| 15 | `loadCustomFacets` | 335-338 | `loadCustomFacets` | 288-291 | IDENT (inclusive javadoc) |
| 16 | `profilePositions` | 340-353 | `positionsByFacetId` | 293-306 | IDENT (renomeado) |
| 17 | `toOrderingDto` | 355-366 | `toOrderingDto` | 308-319 | IDENT |
| 18 | `buildItem` | 368-376 | `buildItem` | 321-328 | IDENT menos 1 arg |
| 19 | `requireSite` | 378-382 | `requireSite` | 330-334 | IDENT |
| 20 | `requireProfile(site,id)` | 384-390 | `requireProfile(id)` | 336-340 | PORT |
| 21 | `requireName` | 392-397 | `TurProfileService.requireName` | 165-171 | IDENT |

¹ O bloco `001:153-158` ("perfil novo nasce como cópia da ordenação do default") **não** foi portado —
o 002 substitui por fallback em tempo de leitura (`resolveReadPositions:223-240`), que é estritamente
melhor: não há cópia que possa divergir.
² O 002 acrescenta `resolveReadPositions`.
³ O 002 **corrige um defeito** do 001: `001:319` adiciona `customFacet.getTurSNSiteFieldExt()` ao set de
`fieldExts` a salvar (entidade errada); `002:272` salva o custom facet pelo repositório próprio.

**Conclusão: 21/21 cobertos. Nada precisa ser portado antes de deletar.**

### A.3 Entidades — o que o modelo novo não cobre

| Campo em `TurSNSiteFacetProfile` | Equivalente 002 | Importa? |
|---|---|---|
| `name`, `description` | `TurProfile.name/description` | sim, coberto |
| `defaultProfile` | `TurProfile.defaultProfile` | sim, coberto |
| `turSNSite` (FK) | `TurProfileFacetOrdering.turSNSite` (perfil × site) | sim, modelo melhor |
| `items` | `TurProfileFacetOrdering.items` | sim, coberto |

`TurSNSiteFacetProfileItem` × `TurProfileFacetOrderingItem`: mesmos campos (`field_ext_id` **ou**
`custom_facet_id`, `position`). Sem lacuna.

Assimetria reveladora: o modelo 002 **não** adiciona coleção nenhuma ao `TurSNSite` — referencia o site
por FK e resolve via repositório. A coleção do 001 existia só para cascatear exclusão, o que a FK
`onDelete: CASCADE` do Liquibase já faz.

### A.4 Pontos de acoplamento a limpar (arquivos que ficam)

| Arquivo | Linhas | O que sai |
|---|---|---|
| `persistence/model/sn/TurSNSite.java` | 35, 74, 219-221 | import + token `"turSNSiteFacetProfiles"` no `@JsonIgnoreProperties` + a coleção `@OneToMany`. Fica sem vestígio do 001; 🔶 equivale a "idêntico à base" só se `origin/2026.3` não tiver mexido na entidade. |
| `exchange/sn/TurSNSiteImport.java` | 58, 99, 121, 142, **195-198** | import, campo, param do ctor, atribuição, comentário+chamada. ⚠ a proposta original dizia "192-198"; o bloco real é comentário 195-197 + chamada 198. Restaurar `CacheManager cacheManager` como último param. |
| `src/test/.../exchange/sn/TurSNSiteImportTest.java` | 61, 99-103, **427-428** | ⚠ **REFUTADO**: a proposta citava só 61/103. Existe uma **terceira** ocorrência — o `verify(turSNSiteFacetProfileService).ensureDefaultProfile(...)` em 427-428. Sem removê-la o teste **não compila**. |
| `onstartup/profile/TurProfileMigrationOnStartup.java` | 39,40,43,46 (imports); 79,82 (campos); 87,90-91 (ctor); 94,97 (atrib.); 109 (`defaultOrderingReady`); **111-128** (passo 1); 143-147 (`getOrCreateGlobalProfile`) | Sai o passo 1. **Fica** `ensureDefaultProfile()` (104) e `buildFromLegacy` (130-140, 158-172). 182 → ~90 linhas. |
| `src/test/.../onstartup/profile/TurProfileMigrationOnStartupTest.java` | 100-127, 156-189 (2 testes); 61-62, 67-68 (mocks); 89-98 (helpers); **137** (stub); **25, 26, 32** (imports) | ⚠ **REFUTADO (gap)**: a proposta esquecia o stub da linha 137 (vira `UnnecessaryStubbingException` em strict stubs → o teste sobrevivente **falha**) e os imports `eq`, `never`, `Set`. Renomear o teste sobrevivente, que ainda diz `WhenNoSpec001Profiles`. |
| `db/changelog/db.changelog-master.yaml` | 108-109 | o `include` do `.8`. **Por último.** |

### A.5 ⚠ Refutação-chave: a ordem não é livre

Duas verificações independentes chegaram ao mesmo ponto crítico:

> `application.yaml:194` → `spring.jpa.hibernate.ddl-auto: none`. O Liquibase é a **única** autoridade
> de schema. Deletar `v2026.2.7.8.yaml` enquanto ainda houver entidade mapeada + `ApplicationRunner`
> consultando a tabela produz `InvalidDataAccessResourceUsageException` no startup e **o Spring Boot
> aborta** em qualquer base com ≥1 site SN.

Consultas executadas em **todo** boot: `TurSNSiteFacetProfileOnStartup:63-64` →
`findByTurSNSiteAndDefaultProfileTrue`, e `TurProfileMigrationOnStartup:112` → `findByTurSNSite`.
Em runtime normal: `TurSNSiteImport:198` e o cascade de `TurSNSite:214-221`.

**Ordem obrigatória** (consumidor → dono), consolidada em `plan.md` §2:

```
1. TurSNSiteImport + TurSNSiteImportTest        (hook do 001)
2. TurSNSiteFacetProfileOnStartup + teste
3. TurProfileMigrationOnStartup passo 1 + teste  ← mesmo commit (senão testes vermelhos)
4. TurSNSite (coleção)
5. TurSNSiteFacetProfileService + DTOs + testes
6. os 2 repositórios
7. as 2 entidades
8. v2026.2.7.8.yaml + include no master          ← só agora
```

Outras refutações de justificativa (a ação era segura, o *porquê* estava errado):

- `del-facetprofile-onstartup`: apagar o OnStartup **não** "habilita a remoção do service e das
  entidades" — o service continua injetado em `TurSNSiteImport` e as entidades/repos continuam usados
  **diretamente** por `TurProfileMigrationOnStartup` (que usa o *repository*, não o *service*).
- `del-tursnsite-001-collection`: idem. O símbolo `turSNSiteFacetProfiles` aparece em exatamente 2
  lugares (declaração + `@JsonIgnoreProperties`); não há getter/setter usado, nem MapStruct, nem JSON
  de exchange. A ação é segura hoje, mas não destrava a deleção das entidades sozinha.

### A.6 Por que o passo 1 da migração é comprovadamente morto

O `TurSNSiteFacetProfileOnStartup` semeia o 001 a partir de `facetPosition`; o passo 1 da migração lê
o 001 e converte para o 002. Mas o passo 2 (`buildFromLegacy`) lê **a mesma fonte com o mesmo filtro**:

| | `TurSNSiteFacetProfileService.seedDefaultProfile:104-114` | `TurProfileMigrationOnStartup.buildFromLegacy:158-172` |
|---|---|---|
| query | `findByTurSNSiteAndEnabled(site, 1)` | `findByTurSNSiteAndEnabled(site, 1)` |
| filtro | `facet == 1 && facetPosition != null` | `facet == 1 && facetPosition != null` |
| custom facets | `findByFieldExtsWithDetails` | `findByFieldExtsWithDetails` |

Resultado bit-a-bit idêntico. O round-trip 001→002 é auto-infligido e descartável.

**Risco de ordem hoje existente:** ambos os runners declaram `@Order(Ordered.LOWEST_PRECEDENCE)`
(`TurSNSiteFacetProfileOnStartup:49` e `TurProfileMigrationOnStartup:75`) — a ordem relativa é
**indefinida**. Funciona por acidente porque o "Geral" nasce `defaultProfile=true`
(`TurSNSiteFacetProfileService:100`) e a migração o dobra em "Padrão". Mudar essa flag faria o boot
criar um `TurProfile` global chamado "Geral" visível na UI, de forma não determinística entre
ambientes. Remover o 001 elimina a classe de bug.

**Perda aceita:** perfis 001 **não-default** criados num build intermediário deste branch (ex.: alguém
que criou "Cursos EE" rodando a branch localmente) deixam de ser convertidos. Aceitável — o 001 nunca
foi liberado.

### A.7 Liquibase — inventário exato e decisão

| Changelog | Changesets | Tabelas | PKs | FKs | UQs | `createIndex` | Rollback |
|---|---|---|---|---|---|---|---|
| `v2026.2.7.8` (001) | 2 | `sn_site_facet_profile`, `sn_site_facet_profile_item` | 2 | 4 (todas CASCADE) | 1 | 0 | 0 |
| `v2026.2.7.9` (002) | 3 | `tur_profile`, `tur_profile_facet_ordering`, `tur_profile_facet_ordering_item` | 3 | 5 | 2 | 0 | 0 |
| `v2026.2.7.10` (003) | 1 | `tur_profile_field_config` | 1 | 2 | 1 | 0 | 0 |

`.9` e `.10` **não** referenciam nenhuma tabela do 001 (`grep facet_profile` = sem match) — não há
dependência entre changelogs.

**Ausência de `rollback:` é o padrão do projeto**, não uma falha: `grep -l rollback` nos 55 changelogs
retorna **zero**. Todos os changesets são DDL puro, que o Liquibase reverte automaticamente. Não
adicionar.

**Decisão D2 — deletar o arquivo, sem changeset de DROP.** Não existe base publicada com as tabelas,
logo não há checksum a preservar (o Liquibase ignora silenciosamente linhas órfãs em
`DATABASECHANGELOG`; só falha por checksum divergente de changeset **presente**).

⚠ **Contraponto registrado:** um verificador defendeu o changeset de DROP guardado por
`preConditions: tableExists / onFail: MARK_RAN`, porque bases de **dev** que rodaram o branch ficam
com 2 tabelas órfãs cujas 3 FKs apontam para tabelas **vivas** (`sn_site`, `sn_site_field_ext`,
`sn_site_custom_facet`) e podem bloquear DDL futuro nelas. O default é H2 em **arquivo persistente**
(`application.yaml:177`: `jdbc:h2:file:./store/db/turingDB`), então o banco do dev sobrevive entre
execuções. **Mitigação escolhida:** nota no PR — apagar `store/db/turingDB.mv.db`, ou
`DROP TABLE sn_site_facet_profile_item; DROP TABLE sn_site_facet_profile;` (filho primeiro).
Escrever o DROP reintroduziria a string `FacetProfile` no repo, que é exatamente o que o revisor pediu
para sumir.

**Renumeração (Q3):** o projeto tem sequência **densa** verificada em todas as séries —
`v2026.1.14.{0..8}`, `v2026.1.17.{0..3}`, `v2026.2.4.{0..13}`, `v2026.2.5.{0..7}`, `v2026.2.6.{0..3}`,
`v2026.2.7.{0..10}`: **zero buracos em 55 changelogs**. Renumerar `.9`→`.8` e `.10`→`.9` é seguro
*porque* todos os changesets têm `preConditions: not tableExists / onFail: MARK_RAN` — o Liquibase vê
changesets novos (a coluna `FILENAME` muda) e apenas marca `MARK_RAN` sem tentar recriar. Sem essas
preconditions quebraria toda base de dev. São 4 `id:` internos a ajustar + os includes.

**Prefixo `tur_` (Q4):** levantados os 56 `@Table(name=...)` do projeto. Convenção dominante: remove o
prefixo `Tur` da classe e usa snake_case do domínio (`TurLocale`→`locale`, `TurIntent`→`intent`,
`TurConfigVar`→`configuration`, `TurCustomTool`→`custom_tool`, `TurPersona`→`ai_persona`). Prefixos em
uso: `auth_`, `sn_`, `ai_`, `llm_`, `se_`, `store_`, `chat_`, `intent_`, ou nome nu. **Exatamente 4
tabelas usam `tur_`** — as 4 novas. Ironicamente as tabelas do 001 seguiam a convenção corretamente.
`PROFILE` não é palavra reservada em H2/MySQL/PostgreSQL (só Oracle, sem evidência de suporte no
projeto) e `globally_quoted_identifiers: true` (`application.yaml:188`) já cita todos os identificadores.

### A.8 MapStruct — verificado, **não é blocker**

O arquivo **gerado** `target/generated-sources/.../TurSNSiteMapperImpl.java` referencia
`TurSNSiteFacetProfile`, o que à primeira vista sugeriria um mapper a ajustar. Verificado:

- A **fonte** `persistence/mapper/sn/TurSNSiteMapper.java` (43 linhas) **não menciona** o 001 —
  `grep "TurSNSiteFacetProfile|facetProfile|turSNSiteFacetProfiles"` em todo
  `persistence/mapper/` retorna **zero**. O único `@Mapping` explícito é `genAiEnabled` (:33-38).
- Não existe `unmappedTargetPolicy`, `unmappedSourcePolicy` nem `ReportingPolicy` em **nenhum** arquivo
  de `turing-app/src/main/java` — o default do MapStruct é `WARN`, não `ERROR`. Remover o campo da
  entidade **não** faz a compilação falhar.
- `TurSNSiteDto` não tem o campo (não aparece entre os 46 arquivos do sweep §B.0).

**Conclusão:** a referência no gerado é consequência automática da coleção existir em `TurSNSite` (ou
resíduo de build antigo — `target/` é descartável). Basta remover a coleção (§A.4) e recompilar.
Nenhum mapper-fonte precisa ser tocado.

### A.9 Exchange / export — sem vazamento

`grep` repo-wide não encontra `FacetProfile` em `TurSNSiteExport` nem em nenhum bean de exchange. O
único ponto do exchange é o **hook de escrita** em `TurSNSiteImport:198` (§A.4). O `@JsonIgnoreProperties`
em `TurSNSite:74` já impedia o vazamento na serialização. Exports gerados por builds deste branch
continuam importáveis depois da limpeza — o payload nunca carregou perfis 001.

---

## §B — Resíduos de nomenclatura em código que **fica**

> ### ⚠️ Correção — o frontend NÃO tinha resíduo (2026-08-07)
>
> A entrada original de §B.1 ("frontend — 4 arquivos, 8 ocorrências") estava **errada**. As ocorrências
> são a classe `TurSNFacetProfileService` e seus 3 imports, e ela **não é resíduo do 001**: é a service
> da spec **002**, e é **simétrica** a `TurSNFieldProfileService` da spec 003. O padrão estabelecido é:
>
> | Classe | Arquivo | Papel |
> |---|---|---|
> | `TurProfileService` | `services/profile/profile.service.ts:10` | perfis **globais** (Gerenciamento) |
> | `TurSNFacetProfileService` | `services/sn/sn.facet.profile.service.ts:10` | config de **facetas** de um perfil, por site (002) |
> | `TurSNFieldProfileService` | `services/sn/sn.field.profile.service.ts:14` | config de **campos** de um perfil, por site (003) |
>
> `TurSN<domínio>ProfileService` é a convenção, não um vestígio. Renomear quebraria a simetria com a
> spec 003. **Tarefa T2.3 cancelada.**
>
> Fica valendo apenas o resíduo de **backend**: o parâmetro `facetProfile` em
> `sn/TurSNFieldProcess.java` (6 ocorrências) — que era genuíno e já foi renomeado para
> `profileNameOrId` (não `profile`: colidiria com o `Optional<TurProfile> profile` da linha 170).

### B.0 Sweep final — confirmação de cobertura

Varredura repo-wide (`FacetProfile|facetProfile|facet_profile|FACET_PROFILE|sn_site_facet_profile`,
todas as extensões, excluindo `target/` e `node_modules/`, incluindo `.github/`, `scripts/`, `k8s/`,
`containers/`, `docker-compose.yaml`, `*.cmd`): **46 arquivos**, distribuídos assim —

| Grupo | Arq. | Situação |
|---|---|---|
| Arquivos do 001 que serão deletados (§A.1) | 12 | resolvido por A |
| Arquivos que ficam, com resíduo (§A.4 + §B) | 8 | `TurSNSite`, `TurSNSiteImport` (+teste), `TurProfileMigrationOnStartup` (+teste), `TurSNFieldProcess` (+teste), `agents.md` |
| Frontend (§B.1) | 4 | identificadores |
| `specs/` 001/002/003 (§F) | 19 | documentação |
| `specs/004-enxugamento-perfis/` | 3 | este próprio spec |

**Zero ocorrências fora desses grupos** — nada em CI, scripts de build, manifests k8s, Docker ou
configuração. O mapeamento está fechado.

Esta é a queixa literal do revisor. Varredura repo-wide por `facetProfile` (fora `target/`,
`node_modules/`, `specs/`):

| Local | Linhas | Natureza | Ação |
|---|---|---|---|
| `turing-app/.../sn/TurSNFieldProcess.java` | 136 (`@param`), 141 (assinatura), 145, 169, 171, 172 | **nome de parâmetro** em código 002 — resolve contra `turProfileRepository`, nada a ver com o 001 | renomear → `profile` |
| `agents.md` | **322** | allowlist do SDK documentado como `(…, facetProfile)`. **Factualmente errado hoje**: `react-sdk/src/core/api.ts:33` declara `profile?: string` e `:50` faz `qs.append("profile", …)` | trocar por `profile` |
| `agents.md` | **316** | exemplo canônico `TurSNSiteFacetProfileRequest(String name, String description)` | → `TurProfileRequest` (shape idêntico, javadoc já explica o mesmo motivo) |
| `agents.md` | **320** | gotcha LAZY+`@Cacheable` ancorado em `TurSNSiteFacetProfileService.seedDefaultProfile`/`persistOrdering` | reapontar para `TurProfileFacetOrderingService` |
| `agents.md` | 321 | linha em branco solta no meio de lista de bullets contígua | remover |
| `.../model/sn/field/TurSNSiteFacetProfile.java` | 57 | javadoc "via the `facetProfile` parameter" | some com o arquivo (A1) |
| `db/changelog/v2026.2.7.9.yaml` | 2-6 | comentário cita "migration of spec-001 per-site profiles (`TurProfileMigrationOnStartup`)" | reescrever |
| `db/changelog/v2026.2.7.10.yaml` | 2 | atribui a tabela ao "spec 002"; é do **003** | corrigir |

**Nenhum** `@RequestParam("facetProfile")`, `qs.append("facetProfile")`, campo de bean, header ou chave
de cache com o nome antigo. **Não existe alias** de retrocompatibilidade — e não deve existir (NFR-04).

### B.1 Frontend — 8 ocorrências, todas identificadores

| Arquivo | Linhas | Ação |
|---|---|---|
| `services/sn/sn.facet.profile.service.ts` | 10 (classe `TurSNFacetProfileService`) + o nome do arquivo | renomear → `profile.facet.ordering.service.ts` / `TurProfileFacetOrderingService` |
| `app/console/sn/facet/sn.site.facet.list.page.tsx` | 15 (import); 21 (`profileService` — já ok) | ajustar import |
| `app/console/sn/fields/sn.site.field.list.page.tsx` | 19 (import), 26, 52 (`facetProfileService`) | → `profileService` |
| `components/sn/search-rule/sn.site.search.rule.form.tsx` | 17 (import), 34, 65 (`turSNFacetProfileService`) | → `profileService` |

O parâmetro e as chaves i18n **já** foram renomeados corretamente no 002 (`search.facetProfile*` →
`search.profile*`). O `sn-site-facet-profile.model.ts` do 001 foi de fato deletado — sem resíduo de model.

---

## §C — Duplicação **interna** à formação nova

Se apagarmos só o 001 e deixarmos isto, a crítica "está duplicado" continua válida — agora dentro do
002/003. ~90 linhas repetidas em 4 classes novas:

| Helper | Cópia 1 | Cópia 2 | Cópia 3 |
|---|---|---|---|
| `positionsByFacetId` | `TurProfileFacetOrderingService:293-306` | `TurSNFieldProcess:189-202` | — |
| `buildItem` | `TurProfileFacetOrderingService:321-328` | `TurProfileMigrationOnStartup:174-181` | — |
| leitura de posições legadas | `TurProfileFacetOrderingService.legacyPositions:243-255` | `TurProfileMigrationOnStartup.buildFromLegacy:158-172` | — |
| `loadCustomFacets` | `TurProfileFacetOrderingService:288-291` | inline em `TurProfileMigrationOnStartup:166` | — |
| `requireSite` / `requireProfile` | `TurProfileFacetOrderingService:330,336` | `TurProfileFieldConfigService:277,283` (byte-a-byte) | `TurProfileService:159-163` (privado) |

### C.1 Superfície de repositório sem consumidor

| Alvo | Situação |
|---|---|
| `TurProfileFacetOrderingItemRepository` (interface inteira, 36 ln) | o item só é persistido por cascade a partir de `TurProfileFacetOrdering.items` |
| `TurProfileFacetOrderingRepository.findByTurSNSite` | :64 — sem chamador |
| `TurProfileFieldConfigRepository.findByTurProfileAndTurSNSiteFieldExtIn` | :59 — versão batch abandonada quando o serviço passou a usar o `findByProfileId` cacheado (`TurProfileFieldConfigService:177`) |
| `TurProfileFieldConfigRepository.findByTurProfile` | :62 — sem chamador |
| `TurProfileRepository.existsByNameIgnoreCase` | :59 — `create()` usa `exists*` (não cacheado) e `update()` usa `find*` (cacheado) para a **mesma** checagem. Herdado do 001. |

### C.2 Coerência que **não** precisa mudar (verificado, para não perder tempo)

- **Pacote `com.viglet.turing.profile` está CORRETO.** O projeto organiza serviços em pacotes de
  domínio de 1º nível (`sn`, `solr`, `lucene`, `elasticsearch`, `genai`, `system`, `email`, `se`,
  `resilience`, `observability`). Quem estava fora do lugar era o serviço do 001 dentro de `sn/facet`,
  que é um pacote de **modelo** de faceta — e some sozinho.
- **`TurProfileFieldConfigRequest` em `api/sn/bean` está CORRETO.** O controller é
  `api/sn/console/TurSNSiteFieldProfileAPI` e o endpoint é site-scoped; todos os controllers de
  `api/sn/console` usam `api/sn/bean`. O que está errado é só o **nome**: o irmão de resposta chama-se
  `TurSNSiteFieldProfileConfigDto`. Renomear o request para `TurSNSiteFieldProfileConfigRequest`,
  **sem mover de pacote**.
- **Os 4 touchpoints de `profile` em `turing-commons` são necessários.** `TurSNParamType.PROFILE`
  (constante), `TurSNSearchParams.profile` (binding GET via `@ModelAttribute`),
  `TurSNSitePostParamsBean.profile` (corpo POST), `TurSEParameters.profile` (modelo interno + merge em
  `overrideFromPost`). Espelha 1:1 o que `sort`, `group` e `fl` já fazem. `TurSNParamType.PROFILE` não
  é referenciado por código algum — mas `FIELD_LIST` também não, pelo mesmo motivo.
- **`SET_PROFILE` está consistente.** Nunca existiu `SET_FACET_PROFILE` no branch (confirmado no diff);
  alinhado a `SET_SORT`/`SET_ROWS`/`SET_BOOST_QUERY`; front espelhado. Único nit: o javadoc na linha 16
  diz "Sets the facet profile" — ficou estreito depois do 003.
- **`TurSNSiteFacetedFieldAPI` (191 ln no diff) NÃO é reescrita gratuita.** É relocação de ~110 linhas
  de lógica para `TurProfileFacetOrderingService` + 2 endpoints novos. Vale sinalizar no corpo do PR
  para o revisor não gastar revisão à toa.
- **Não falta endpoint de ordenação.** `TurProfileFacetOrderingService` está exposto em
  `TurSNSiteFacetedFieldAPI` (`:77` GET, `:84` PUT ordering, `:95` GET /profile, `:104` GET
  /profile/{id}/ordering, `:112` PUT) e `TurProfileAPI:103` (setDefault).

---

## §D — Escopo solto: 3 workstreams que vazaram

### D.1 Self-healing multiValued — REVERTER (cluster atômico)

Origem: incidente de indexação de ofertas de Educação Executiva (campos `endOfClasses`/
`dateOfClassesText`), registrado pelo próprio autor como **problema de sessão** em
`001/HANDOFF.md:48-58` e `001/research.md:148-177` — não como requisito de nenhuma spec.

| Arquivo | Delta | O que sai |
|---|---|---|
| `api/sn/queue/TurSNProcessQueue.java` | +115 | campo `reconciledMultiValuedFields` (~88); chamada `promoteMultiValuedFromPayload` (227); `else { reconcileMultiValuedField(...) }` (242-243); métodos 256-348; imports `Collection`/`ConcurrentHashMap`/`Collectors`/`TurSEFieldType` |
| `plugins/se/solr/TurSolrSearchEnginePlugin.java` | +5 | override 205-208 |
| `solr/TurSolrUtils.java` | +33 | `reconcileMultiValued` 150-181 |
| `plugins/se/TurSearchEnginePlugin.java` | +9 | default method 99-107 — **por último** (é a interface) |
| `src/test/.../TurSNProcessQueueTest.java` | +101 | os 2 testes (hunk único, 108-208) |
| `agents.md` | :314 | o bullet que documenta esses métodos |

⚠ **REFUTADO 3×.** As propostas originais eram incompletas de três formas diferentes:
1. Omitiam `TurSNProcessQueueTest` — `verify(plugin).reconcileMultiValued(...)` em `:154` e `:206` **não
   compila** sem o método na interface.
2. Reverter só `TurSolrUtils` quebra: `TurSolrSearchEnginePlugin:207` chama o estático **diretamente**.
3. Reverter só o override do plugin mantendo o default vazio transforma o self-healing em **no-op
   silencioso** — os testes continuam passando (mock não executa corpo real) e o comportamento se perde
   sem sinal.

**Ordem obrigatória:** teste → `TurSNProcessQueue` → override do Solr → `TurSolrUtils` → interface.

Par correlato: `solr/TurSolrDocumentHandler.java` (`catch (SolrServerException | IOException)` →
`catch (Exception)`). Verificado como reversão limpa — `TurSolrDocumentHandlerTest` não foi tocado e
não tem nenhum `doThrow`/`thenThrow`. Reverter **junto**, senão fica meio self-healing.

### D.2 ⚠ Resiliência de busca — REFUTADO, provavelmente NÃO é escopo solto

**Este é o achado que mais muda a conclusão.** Dois verificadores independentes derrubaram a
classificação de "escopo solto":

> `TurSolrQueryBuilder:840` (`resolveEnabledFacets`, **adicionado por este PR**) permite que um perfil
> não-padrão **ligue faceta** num campo que não é faceta globalmente — o próprio javadoc em `:831-836`
> diz *"a profile can both enable a field that is a facet only under it"*. E `:886`
> (`applyProfileFacetConfig`) aplica `config.facet() != null ? config.facet() : field.getFacet()`.
>
> Um `TurSNSiteFieldExt` habilitado pode perfeitamente **não existir no schema de um core Solr
> específico** — cenário que o produto já reconhece (`TurSNSiteFieldCheckAPI:173`, `.exists(false)`) e
> que o comentário em `TurSolr:310-314` descreve textualmente ("partially-indexed cores, where a
> configured facet field was never created because no document ever carried it").
>
> Nesse caso o Solr rejeita a **query inteira** — documentos inclusive — e `TurSNSearchProcess:178`
> (arquivo **não** alterado pelo PR) devolve um `TurSNSiteSearchBean` vazio → o front estoura em
> `data.widget.locales[0]` → **tela branca**.

Ou seja: a feature de perfis **aumenta a probabilidade** do modo de falha que essas guardas cobrem.

| Arquivo | Delta | Papel |
|---|---|---|
| `solr/TurSolr.java` | ~70 das +92 | `UNDEFINED_FIELD` (76-80), `executeSolrQuery` com retry (307-380), helpers `removeUndefinedFacetField`/`parseUndefinedField`/`bareFacetField` (347-378), import `Matcher` (15) |
| `src/test/.../TurSolrTest.java` | 2 testes (213-249) + import `SolrException` (45) | cobrem o retry |
| `react-sdk/src/core/resolve.ts` | 8-11, 29-30 | guarda de `queryContext` ausente |
| `search/components/search-content.tsx` | 72-78 | guarda de `data.widget` ausente |

Observações de tipagem que reforçam o risco: `react-sdk/src/core/types.ts:18` declara
`widget: TurWidget` (**obrigatório**), mas o bean backend
`TurSNSiteSearchBean.java:39` tem `widget` **nullable** — o tipo TS mente.

**As 4 peças são um grupo só.** Reverter parcialmente é o pior cenário: `resolve.ts` degradaria em
silêncio devolvendo `[]` e `search-content` estouraria em seguida.

**Recomendação:** manter no PR, com 1 linha de justificativa no HANDOFF/corpo do PR. Se o revisor
insistir em separar, o pré-requisito é um filtro em `TurSolrQueryBuilder` que descarte do `facet.field`
os campos habilitados por perfil ausentes do core.

Mantida a parte da feature em `TurSolr.java` (injeção de `TurProfileRepository` +
`TurProfileFieldConfigService` no construtor) e em `TurSolrTest` (mocks 115-119, args 709-711).

### D.3 `index.html` — REVERTER (limpo)

`translate="no"` (linha 2) + comentário e `<meta name="google" content="notranslate">` (5-12).
Workaround do crash `removeChild`/`insertBefore` do React causado pelo Google Translate. Problema
pré-existente de toda a aplicação, nenhum componente de perfis depende disso.

Verificado: nenhum teste ou e2e referencia `notranslate`; diff puramente aditivo; não toca `<script>`,
entrypoint nem `<title>`. A cópia em `turing-app/src/main/resources/public/index.html` **não é
versionada** (`git ls-files` vazio) — é build output, fora do PR.

### D.4 ⚠ `@Secured` — REFUTADO: a proposta original deixava a mesma regressão

`TurSNSiteFieldExtAPI`: +7 linhas (import `:41` + 6 anotações em `:118, 140, 151, 165, 175, 194`) sobre
endpoints **pré-existentes** → clientes sem `ROLE_ADMIN`/`SN_VIEW`/`SN_EDIT` passam a receber 403.

⚠ Mas a frase "manter `@Secured` apenas nos endpoints NOVOS (`TurSNSiteFacetedFieldAPI` e
`TurSNSiteFieldProfileAPI`), onde não há regressão possível" é **falsa**. Conferido em
`git show origin/2026.2:.../TurSNSiteFacetedFieldAPI.java`: as rotas `GET /api/sn/{snSiteId}/facet`
(base:77) e `PUT /api/sn/{snSiteId}/facet/ordering` (base:83) **já existiam sem `@Secured`**, e hoje
estão anotadas em `:78` e `:86`.

> 🔶 **B4** — conferido contra `origin/2026.2`. Repetir com
> `git show origin/2026.3:.../TurSNSiteFacetedFieldAPI.java`: se a base já anotou essas rotas, elas
> saem do escopo da reversão e a FR-16 encolhe para os 6 de `TurSNSiteFieldExtAPI`.

| Anotação | Rota | Veredicto |
|---|---|---|
| `TurSNSiteFieldExtAPI:118,140,151,165,175,194` | pré-existentes | **remover** |
| `TurSNSiteFacetedFieldAPI:78, :86` | **pré-existentes** | **remover** (a proposta original esquecia) |
| `TurSNSiteFacetedFieldAPI:96, :105, :114` | novas | **manter** |
| `TurSNSiteFieldProfileAPI:67, :75, :84` | novas | **manter** |

Manter o import em `TurSNSiteFacetedFieldAPI:27` (continua usado pelas três novas).
O padrão `@Secured` já existe na base (`TurSNSiteAPI`), então não é a introdução do padrão que incomoda
— é aplicá-lo a endpoints que o PR não está criando.

### D.5 Falsos positivos (verificados e legítimos — não mexer)

`TurSNSiteFacetedFieldAPI` (relocação), `TurSNSiteFacetedFieldAPITest` (encolheu: 57 add / 120 del,
virou teste de delegação), `i18n/index.ts` (+6, só registra o namespace `profile.json`), os 5 arquivos
de `SET_PROFILE` (é o FR-26 do 002), `TurPrivilegeOnStartup` (+6: `CATEGORY_PROFILE` + 4 privilégios
`PROFILE_*`, todos consumidos por `TurProfileAPI` e pela sidebar — são do **002**, não do 001).

---

## §E — Defeitos reais encontrados na varredura

| # | Defeito | Evidência | Sev. |
|---|---|---|---|
| E1 | **Resolução de perfil divergente.** `TurSolrQueryBuilder.resolveAppliedProfile:863-873` só faz `findByNameIgnoreCase` e devolve `null` se não achar. `TurSNFieldProcess.resolveProfilePositions:169-187` faz name → id → `findByDefaultProfileTrue`. Consumem o **mesmo** `TurSEParameters.getProfile()`. Consequência: `?profile=<uuid>` aplica a **ordenação** do perfil mas a **config de faceta** do global — resposta meio-perfil. Divergem também no nome inexistente. | `TurSolrQueryBuilder:863,872`; `TurSNFieldProcess:169,173,175,182` | alta |
| E2 | **`setDefault` sem `@CacheEvict`.** `PUT /api/profile/{id}/default` (`TurProfileAPI:102-109`) reescreve `sn_site_field_ext.facet_position` via `syncLegacyPositions:276` (`saveAll`, que **não** passa pelo aspecto `@CacheEvict` de `save`). Os dois endpoints irmãos (`TurSNSiteFacetedFieldAPI:85` e `:113`) têm o evict; este não. O cache `findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition` — lido por `TurElasticsearch:90` — continua servindo a ordem antiga, furando o espelho DT-6. | `TurProfileFacetOrderingService:213-214` (o código **reconhece** o problema, mas só corrige para custom facets) | alta |
| E3 | **`PROFILE_VIEW` não é concedível pela UI.** Falta a categoria `PROFILE` em `PRIVILEGE_LAYOUT` (`admin.role.form.tsx:32-40`). O item "Perfis" fica invisível para todo não-admin, permanentemente (`app-sidebar.tsx:67,153` filtra por ele; `hasPrivilege` só devolve `true` para `ROLE_ADMIN`). | `app-sidebar.tsx:64-68,128-135,153` | alta |
| E4 | **GraphQL sem `profile`.** `TurSNSearchParamsInput` não declara o campo e `convertToTurSNSearchParams:297-320` não o copia. REST GET e POST suportam; GraphQL não. Nenhum dos dois arquivos está no diff. | `TurSNSearchParamsInput:35`; `TurSNSiteSearchGraphQLController:306,312` | média |
| E5 | **Cache não cobre cascade de banco.** `TurProfile` não tem relação JPA com `TurProfileFacetOrdering`/`TurProfileFieldConfig` — o cascade é só de FK (`v2026.2.7.9:85`, `v2026.2.7.10:66`). `TurProfileRepository.delete` evicta 3 caches, nenhum deles o de ordering/field-config. Latente hoje (toda leitura passa antes por um lookup já evictado), mas estoura quando alguém adicionar leitura direta por id. | `TurProfileService:153-154` | baixa |
| E6 | **`@Transactional(readOnly)` incoerente.** `TurProfileFacetOrderingService.getOrdering:102` e `TurProfileFieldConfigService.getEffectiveConfigs:85` são leituras puras sem `readOnly`, enquanto `TurProfileService.get:91` e `resolveEffective:101` têm. O 001 tinha `readOnly` na leitura equivalente (`:214`) — a reescrita perdeu. | — | baixa |
| E7 | **`clearOverride` morto no frontend.** `sn.field.profile.service.ts:38-47` implementado, nunca chamado. A chave i18n correspondente (`sn.fields.profile.clear`) também é órfã. O botão "voltar ao global" do 003 foi especificado e não construído. | grep retorna só a definição | baixa |
| E8 | **Fallback inalcançável.** `sn.site.facet.draggable.list.tsx:125-131` tem `else` para quando `onSaveOrder` é ausente, mas o único consumidor sempre a passa (`sn.site.facet.list.page.tsx:101`). Deixa `turSNFacetedFieldService` (103) e `TurSNFacetedFieldService.saveOrdering` sem chamador. | — | baixa |
| E9 | **i18n cross-namespace.** `sn.site.search.rule.actions.tsx:272` usa `t("sn.facets.profile.default")` — um componente de Search Rule pescando chave do namespace de **facetas**. Quebra silenciosamente se o bloco de facetas for limpo. | existe `profile.default` em `profile.json:5` | média |

### E.10 i18n — 33 chaves órfãs (66 entradas en+pt)

| Bloco | Órfãs | Usadas | Observação |
|---|---|---|---|
| `sn.facets.profile` (`{en,pt}/sn.json:89-113`) | **18** | 5 (`label`, `select`, `manage`, `default`, `loadError`) | resíduo literal do 001 — `git show 69430661b0` mostra exatamente esse conjunto criado quando o CRUD morava em Facetas. Duplicam 1:1 o namespace `profile`. |
| `sn.fields.profile` (`{en,pt}/sn.json:56-76`) | **13** | 6 | descrevem o editor completo de faceta-por-perfil do 003, que não foi construído (a UI só liga/desliga o toggle) |
| `profile.rename` (`{en,pt}/profile.json:8`) | 1 | — | a página usa `profile.edit` |
| `home.features.profiles.description` | 1 | — | "Perfis" não foi adicionado à bento home — inconsistência de navegação, não só chave morta |

Zero chaves usadas-e-não-definidas. Zero divergência en/pt. Namespace `profile` **está** registrado em
`i18n/index.ts` (imports 35/76, spread 95/105). `search.profile*` — as 3 usadas, ok.

### E.11 Nit de diff

`{en,pt}/sn.json` perderam a newline final (`\ No newline at end of file`), fazendo a última linha
aparecer como modificada sem mudança real, nos dois locales.

---

## §F — Documentação

| Alvo | Problema | Ação |
|---|---|---|
| `specs/001-perfis-ordenacao-facetas/` (9 arq., ~1.323 ln) | descreve o modelo abandonado. Único sinal: um blockquote em `README.md:3-5` dizendo "a ser substituído" (futuro). Os outros 8 arquivos não têm marcação. | deletar (Q2) |
| links quebrados por isso | `002/README.md:8`, `002/spec.md:4`, `003/spec.md:107` | ⚠ corrigir **no mesmo commit** |
| `003/spec.md:107` | além de quebrar, o link está **errado**: aponta "facetas por template" para o 001, contrariando os avisos explícitos em `001/README.md:38-39` e `002/README.md:48` | corrigir independentemente |
| `003/Resumo-Config-Campos-por-Perfil.pdf` | 11.601 bytes, ReportLab, único não-`.md` em `specs/`. Conteúdo já existe em markdown ao lado. **E o git o trata como TEXTO** — `git diff --numstat` retorna `137 0` em vez de `- -`, porque não há byte NUL nos primeiros 8000 bytes e não existe `.gitattributes`. Em checkout Windows com `core.autocrlf` a normalização LF→CRLF desloca os offsets do `xref` e pode **corromper** o PDF. | deletar |
| política DT-7 nas specs 002 | gravada em 10+ pontos: `002/research.md:103-110,127`; `data-model.md:131-132,144`; `plan.md:29,118,163`; `tasks.md:9,83-85,105-108`; `HANDOFF.md:23,76-77,116-119`. Contradição interna: `HANDOFF.md:23` diz "drop na Fase 6", `:116` diz "ADIADO". | reverter |
| `002/tasks.md:84` e `HANDOFF.md:116` | mandam criar `v2026.2.7.10.yaml` para o DROP — número **já consumido** pelo `tur_profile_field_config` do 003. É a evidência documental de que a limpeza foi planejada e esquecida. | reescrever |
| `002/contracts/profile-api.md:116-129` (seção E) | duplica o contrato do 003 **e está errada**: documenta `PUT .../field/profile/{profileId}` (lote) sem DELETE; o controller real é per-campo (`TurSNSiteFieldProfileAPI:74,83`) | remover, deixar ponteiro |
| `002/README.md:22-24` | "Ainda não iniciado. Ao começar, criar o HANDOFF.md" — com 7 fases concluídas e o HANDOFF existindo (18 KB) | atualizar |
| `003/README.md:37` / `003/HANDOFF.md:83` | "a implementar" / "Nada novo commitado" — com Fases 1-3 concluídas e o código no PR | atualizar |
| `003/HANDOFF.md:75` | gate cita `TurSNSiteFacetProfileServiceTest`, que vai ser deletado | recontar |
| `002/research.md:25-29` | descreve o que "o 001 entregou" usando a nomenclatura **posterior** (`profile`); o §1.1 logo acima usa a do 001 | corrigir ou remover |
| `CHANGELOG.md` | não tocado pelo PR. Topo é `## 2026.1`; não há seção `## 2026.2`, embora a base seja 2026.2. O PR entrega 5 mudanças visíveis ao usuário sem registro fora das specs. | adicionar `## 2026.2` |
| `docs/` e `README.adoc` | **verificado: não há dívida.** Não existe referência em prosa dos parâmetros da API de busca no repo. `docs/` é roadmap/propostas (`DOCS_INDEX.md:1-3`), não referência de API. Nenhum doc ficou desatualizado por causa do `profile`. | nada |

Nota: as referências a `TurSNFacetProfileService` em `003/HANDOFF.md:37,51`, `research.md:57` e
`tasks.md:32` apontam para a classe **frontend**, que existe — essas estão corretas. O resíduo ali é o
nome da classe TS (§B.1), não a doc.

Acoplamento: há **71 ocorrências de "001"** nos 9 arquivos da spec 002. Deletar só o 001 deixa o 002
narrando extensivamente um documento inexistente — argumento a favor de tratar `specs/` como bloco (Q2).

---

## §G — O que ficou sem verificar

- Nada foi compilado nem testado. Todas as afirmações vêm de leitura de código, `git diff` contra
  `origin/2026.2` e grep. A lista de blockers de compilação é derivada de grep, não do compilador.
- `git config core.autocrlf` na máquina (relevante só para o risco do PDF).
- Se o `@Secured` em `TurSNSiteFieldExtAPI` foi pedido pelo mantenedor em algum canal fora do repo.
- Se o mantenedor prefere CHANGELOG manual ou gerado dos Conventional Commits.
- Um teste ponta-a-ponta provando que um perfil consegue de fato ligar faceta em campo ausente do core
  (o cenário de D.2 foi derivado do código, não reproduzido). **É o que decide a Q1.**
- Se remover `GET /sn/{id}/facet/profile` quebra algum cliente externo (só verifiquei consumidores
  dentro do repo). Nota: esse endpoint (`TurSNSiteFacetedFieldAPI:97`) faz literalmente
  `return turProfileService.list()`, ignorando o `snSiteId` — é a mesma lista de `GET /api/profile`,
  com `@Secured` diferente (`SN_VIEW` × `PROFILE_VIEW`). Duplicação de endpoint candidata a remoção.
