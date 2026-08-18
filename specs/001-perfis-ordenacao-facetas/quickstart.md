# Quickstart / Cenários de Validação

Roteiro de validação end-to-end (mapeia os critérios de aceite do `spec.md §8`).
Use um SN site que já tenha facetas habilitadas e ordenação configurada.

> Substitua `{site}` pelo id do site no Console e `{siteName}` pelo nome usado na API de busca.

---

## Cenário 1 — Migração preserva a ordem atual (AC-01)
1. **Antes do deploy**: anote a ordem atual das facetas (`GET /api/sn/{site}/facet`).
2. Suba a aplicação com a Fase 1 aplicada (Liquibase + bootstrap).
3. `GET /api/sn/{site}/facet/profile` → deve existir um perfil `Geral` com `default=true`.
4. `GET /api/sn/{site}/facet/profile/{geralId}/ordering` → ordem **idêntica** à do passo 1.
5. Busca sem parâmetro: `GET /api/sn/{siteName}/search?q=*` → facetas na **mesma ordem** de antes.

✅ *Esperado*: nenhuma ordenação perdida; "Geral" reflete o estado anterior.

## Cenário 2 — Segundo perfil com ordem própria (AC-02)
1. `POST /api/sn/{site}/facet/profile` com `{ "name": "Cursos EE" }` → 201.
2. No Console, selecione "Cursos EE" e reordene as facetas (drag-and-drop).
   (ou `PUT .../profile/{cursosId}/ordering` com posições trocadas.)
3. `GET /api/sn/{siteName}/search?q=*&facetProfile=Cursos%20EE` → facetas na **nova** ordem.
4. `GET /api/sn/{siteName}/search?q=*` (sem parâmetro) → ordem do **Geral** (inalterada).

✅ *Esperado*: perfis independentes; um não afeta o outro.

## Cenário 3 — Fallback para o padrão (AC-03)
1. `GET /api/sn/{siteName}/search?q=*&facetProfile=NaoExiste` → **200**, facetas na ordem do
   "Geral" (sem erro).
2. `GET /api/sn/{siteName}/search?q=*&facetProfile=` (vazio) → mesma coisa.

✅ *Esperado*: nunca quebra; sempre cai no padrão.

## Cenário 4 — Proteção do perfil padrão (AC-04)
1. `DELETE /api/sn/{site}/facet/profile/{geralId}` → **409** (não pode excluir o padrão).
2. `PUT /api/sn/{site}/facet/profile/{geralId}` com `{"name":"Outro"}` → **409** (não pode renomear
   o padrão, AC-04b). Enviar o mesmo `name` permite editar só a descrição.
3. `PUT /api/sn/{site}/facet/profile/{cursosId}/default` → promove "Cursos EE" a padrão.
4. `DELETE /api/sn/{site}/facet/profile/{geralId}` → agora **204**.

✅ *Esperado*: sempre há exatamente um padrão; excluir/renomear o padrão é bloqueado. No Console,
o padrão já vem criado, rotulado "Padrão"/"Default", com Renomear/Excluir/Definir-padrão ocultos.

## Cenário 5 — Exclusão de faceta limpa os perfis (AC-05)
1. Tenha a mesma faceta posicionada em "Geral" e "Cursos EE".
2. Exclua/desabilite essa faceta do site (fluxo existente de field/custom facet).
3. `GET .../profile/{geralId}/ordering` e `.../profile/{cursosId}/ordering` → a faceta não
   aparece mais como posicionada em nenhum (orphan removal / cascade).

✅ *Esperado*: nenhum item órfão de faceta excluída.

## Cenário 6 — Retrocompatibilidade dos endpoints legados (FR-16)
1. `GET /api/sn/{site}/facet` → mesma resposta que `.../profile/{default}/ordering`.
2. `PUT /api/sn/{site}/facet/ordering` (payload antigo) → altera o **perfil padrão**; busca sem
   parâmetro reflete a mudança.

✅ *Esperado*: clientes antigos continuam funcionando, operando sobre o padrão.

## Cenário 7 — Campo de perfil na página de preview de busca (FR-15b)
1. Abra a página de preview de busca do site (`src/search/`).
2. No header, preencha o campo **"Perfil de facetas"** com `Cursos EE` e tecle **Enter** (ou saia
   do campo). A URL passa a conter `?facetProfile=Cursos%20EE` e a busca é refeita.
3. As facetas vêm na ordem do perfil "Cursos EE".
4. Limpe o campo (vazio) + Enter → `facetProfile` some da URL → ordem do perfil **padrão**.

✅ *Esperado*: o campo controla o parâmetro `facetProfile` da busca; vazio = padrão; nome
inexistente cai no padrão sem erro (Cenário 3).

## Comandos de gate

```bash
# Backend (na raiz turing/)
mvn test -pl turing-app -Dskip.npm=true
# Integração (Docker/Testcontainers)
mvn verify -pl turing-app -Dskip.npm=true

# Frontend (em frontend/apps/turing-app) — type-check
pnpm exec tsc -b --noEmit
```

> Windows/PowerShell: cite as flags `-D`, ex.: `"-Dskip.npm=true"`. Se o build npm travar com
> EBUSY, use os diagnostics da IDE e trate o backend como gate primário (ver `agents.md`).
