# 002 — Perfis Genéricos em "Gerenciamento"

Torna o **perfil** um conceito **global e genérico**, criado e gerenciado na seção
**"Gerenciamento"** do console (nível de sistema), e não mais dentro de **"Facetas"** (que fica em
*Navegação Semântica*). Em "Facetas", o administrador apenas **seleciona** um perfil existente e
**anexa a configuração** de ordenação daquele site.

> Evolução do [`001-perfis-ordenacao-facetas`](../001-perfis-ordenacao-facetas/README.md), que
> criava perfis **por site**, presos à tela de Facetas. Este spec **substitui** aquele modelo.

## Decisões travadas (com o Arthur)
1. **Escopo: global (sistema)** — um perfil é criado uma vez em Gerenciamento e é reutilizável por
   qualquer site SN. A ordenação de facetas passa a ser configurada por **(perfil × site)**.
2. **Genérico e extensível** — o perfil é uma entidade **neutra**; a *ordenação de facetas* é o
   **1º tipo de configuração** anexável. O modelo permite outros tipos no futuro (sort, regras…).
   **Só facetas** é implementado agora.
3. **Substituir e migrar** — refatora o modelo per-site do spec 001 para o global e **migra** os
   dados existentes (perfis/itens) para o novo modelo.
4. **Perfil padrão global protegido** — um perfil **"Padrão"** global, **não excluível nem
   renomeável**, usado como *fallback* quando o site/busca não seleciona um perfil.

## ▶ Retomada de sessão
Ainda não iniciado. Ao começar, criar o `HANDOFF.md` (como no spec 001) e mantê-lo como ponto de
partida consolidado.

## Documentos (ordem de leitura)
1. [`spec.md`](spec.md) — requisitos, histórias, critérios de aceite.
2. [`research.md`](research.md) — estado atual (spec 001), onde encaixar em "Gerenciamento",
   decisões técnicas e Fase 0.
3. [`data-model.md`](data-model.md) — entidades globais, config de facetas, migração do 001.
4. [`contracts/profile-api.md`](contracts/profile-api.md) — contratos REST (Gerenciamento + Facetas).
5. [`plan.md`](plan.md) — **fases de implementação** (0 a 6).
6. [`tasks.md`](tasks.md) — tarefas numeradas por fase.
7. [`quickstart.md`](quickstart.md) — cenários de validação end-to-end.

## Resumo da mudança central
```
ANTES (spec 001)            DEPOIS (spec 002)
TurSNSite 1─N               TurProfile (GLOBAL)  1─N  TurProfileFacetOrdering  N─1  TurSNSite
  TurSNSiteFacetProfile          (Gerenciamento)         (config por perfil×site)
  (perfil por site)                                            │ 1─N
    TurSNSiteFacetProfileItem                                  TurProfileFacetOrderingItem
```
O **perfil** vira global (criado em Gerenciamento). A **posição da faceta** passa a viver numa
configuração por **(perfil × site)** — anexada em Facetas. O runtime continua resolvendo `profile`
na busca (agora sobre perfis globais), com *fallback* ao **Padrão**.

> Não confundir com a demanda de *facetas por templateName* (config por template do AEM), separada.
