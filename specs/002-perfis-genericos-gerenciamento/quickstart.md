# Quickstart — Perfis Genéricos em "Gerenciamento"

Cenários de validação end-to-end. Substitua `{site}` pelo id do site no Console e `{siteName}`
pelo nome usado na API de busca.

---

## Cenário 1 — Criar perfil global em Gerenciamento (AC-01)
1. No sidebar, seção **Gerenciamento**, abrir **Perfis**.
2. Criar perfil `Cursos EE`. → aparece na lista; **não** é padrão.
3. O perfil **"Padrão"** já existe, marcado como padrão, com renomear/excluir ocultos.
4. Tentar `PUT /api/v2/profile/{padrão}` mudando o `name` → **409**. `DELETE` do padrão → **409**.

✅ *Esperado*: CRUD global; nome único global (409 em duplicado); padrão protegido e pré-criado.

## Cenário 2 — Selecionar perfil e ordenar em Facetas (AC-02)
1. Abrir um site SN → **Facetas**. O Select lista os perfis **globais** (não há mais criar/renomear
   aqui; há um link "Gerenciar perfis" para Gerenciamento).
2. Selecionar `Cursos EE` e reordenar as facetas (drag-and-drop) → salvar.
3. Abrir **outro** site → Facetas → `Cursos EE`: a ordem é **independente** (config por site).

✅ *Esperado*: a ordenação fica salva para **(perfil, site)**; não afeta outros sites nem outros perfis.

## Cenário 3 — Runtime por perfil (AC-03)
1. `GET /api/sn/{siteName}/search?q=*&profile=Cursos%20EE` → facetas na ordem de (Cursos EE, site).

✅ *Esperado*: ordem conforme a config de (perfil, site) no Solr.

## Cenário 4 — Fallback ao Padrão (AC-04)
1. `GET .../search?q=*` (sem `profile`) → ordem do **Padrão** para o site.
2. `GET .../search?q=*&profile=NaoExiste` → **200**, ordem do Padrão.
3. `GET .../search?q=*&profile=Cursos%20EE` num site **sem** config desse perfil → **200**,
   ordem do Padrão do site (fallback), sem erro.

✅ *Esperado*: sempre HTTP 200; fallback perfil→Padrão(site)→`facetPosition` legado.

## Cenário 5 — Migração do spec 001 (AC-06)
1. Em base com dados do spec 001 (perfis per-site), subir a aplicação.
2. Em Gerenciamento → Perfis: os perfis do 001 aparecem como **globais**; os "Geral" per-site
   consolidados no **"Padrão"**; homônimos entre sites viram **um** perfil global com config por site.
3. `GET .../search?q=*` em cada site → mantém a ordem anterior (via Padrão/config migrada).

✅ *Esperado*: dados migrados sem perda; busca sem `profile` preserva a ordem de cada site.

## Cenário 6 — Faceta excluída limpa as configs (AC-07)
1. Ter uma faceta posicionada em `Cursos EE` e no `Padrão` de um site.
2. Excluir/desabilitar a faceta do site.
3. `GET .../facet/profile/{cursosId}/ordering` e do Padrão → a faceta não aparece mais posicionada.

✅ *Esperado*: nenhum item órfão (cascade).

## Cenário 7 — Retrocompatibilidade e preview (AC-08)
1. `GET /api/sn/{site}/facet` → igual a `.../facet/profile/{padrão}/ordering`.
2. `PUT /api/sn/{site}/facet/ordering` → salva no **Padrão** do site; busca sem parâmetro reflete.
3. Página de preview de busca: campo "Perfil de facetas" (`profile`) continua funcionando
   (agora resolvendo perfis globais).

✅ *Esperado*: clientes antigos e o preview continuam operando (sobre o Padrão / perfis globais).

---

## Comandos de gate
```bash
# Backend (na raiz turing/)
mvn test -pl turing-app -Dskip.npm=true
# Frontend (frontend/apps/turing-app)
node node_modules/typescript/lib/tsc.js --noEmit -p tsconfig.app.json
# SDK, se tocado (frontend/packages/react-sdk)
node node_modules/typescript/lib/tsc.js
```
