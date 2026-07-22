# Quickstart — Configuração de Campos por Perfil (validação manual)

Pré-requisitos: um site SN com campos indexados e ≥ 2 perfis globais (Gerenciamento → Perfis): o
**Padrão** e um não-padrão (ex.: "Cursos EE").

## 1. Modo Padrão (global) — retrocompat
1. Console → site → **Campos**. O seletor de **Perfil** aparece com **Padrão** pré-selecionado.
2. Ligue `facet` de um campo (ex.: `catedras`) e defina `facetSort=COUNT`. Salve.
3. Esperado: gravou no **campo global** (não criou override). Facetas (Padrão) e a busca **sem** perfil
   já mostram `catedras`.

## 2. Modo perfil não-padrão — override
1. Em **Campos**, troque o seletor para **"Cursos EE"**.
2. **Desligue** `facet` de `catedras` só nesse perfil e mude `facetName` para "Cátedras EE". Salve.
3. Esperado: criou/atualizou **override** (`TurProfileFieldConfig`) só para "Cursos EE". O campo aparece
   como **sobrescrito**; os demais atributos como **herdado** (valor global esmaecido).
4. Volte ao **Padrão**: `catedras` continua ligado e com o nome global (override não vazou).

## 3. Herança / limpar override
1. Sob "Cursos EE", clique **limpar** no override de `facetName`.
2. Esperado: `facetName` volta a exibir "herdado: catedras". Se limpar **todos** os overrides do campo,
   a linha some (herança total do global).

## 4. Runtime de busca (config efetivo)
1. Busque sem perfil:
   `.../sn/<site>?q=*` → facetas conforme o **global/Padrão** (inclui `catedras`).
2. Busque com o perfil:
   `.../sn/<site>?q=*&profile=Cursos%20EE` → `catedras` **não** aparece como faceta (desligada no perfil);
   os demais campos usam o efetivo (override∪global).
3. Perfil inexistente:
   `.../sn/<site>?q=*&profile=NaoExiste` → cai no global/Padrão, **HTTP 200**, sem erro.

## 5. Regra de Busca `SET_PROFILE` (002 §4k)
1. Crie uma Regra de Busca com ação **Definir Perfil = "Cursos EE"** e uma condição que case.
2. Esperado: buscas que casam a regra aplicam o config efetivo de "Cursos EE" (facetas), consistente com
   a ordenação daquele perfil.

## 6. Segurança
- `GET /api/sn/{site}/field/profile/{profileId}` exige `SN_VIEW`; `PUT`/`DELETE` exigem `SN_EDIT`
  (403 sem privilégio).

## Sinais de sucesso
- Override afeta **só** o perfil editado; Padrão edita o global; herança visível e reversível; busca
  monta facetas conforme o perfil aplicado, com fallback sem erro; suíte backend verde e frontend compila.
