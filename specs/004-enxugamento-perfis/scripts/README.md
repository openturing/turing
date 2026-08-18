# Scripts — 004 Enxugamento do PR de Perfis

Estes scripts existem porque a sessão de trabalho ficou **sem acesso a shell** (o classificador de
segurança que autoriza execução de comandos ficou indisponível, bloqueando `Bash`, `PowerShell` e
subagentes). Sem shell não é possível: rodar `git`, **deletar arquivos**, nem compilar.

Eles empacotam exatamente a parte que precisa de shell. O resto (edições) é feito direto no editor.

## Protocolo em duas rodadas

```
  ┌─ rodada 1 ────────────────────────────────────────────────┐
  │ você:  bash specs/004-enxugamento-perfis/scripts/00-verificar-base.sh
  │        └─ cola a saída (ou o base-report.txt) na conversa
  │ eu:    leio B1/B2/B3, ajusto o spec se necessário,
  │        e aplico as EDIÇÕES da Fase 1 (T1.1, T1.3, T1.4)
  └───────────────────────────────────────────────────────────┘
  ┌─ rodada 2 ────────────────────────────────────────────────┐
  │ você:  bash .../01-fase1-remocao.sh            (simulação)
  │        bash .../01-fase1-remocao.sh --apply    (aplica)
  │        └─ cola a saída, e depois a do build
  └───────────────────────────────────────────────────────────┘
```

## Os scripts

| Arquivo | O que faz | Destrutivo? |
|---|---|---|
| [`00-verificar-base.sh`](00-verificar-base.sh) | Fecha **B1–B10** contra `origin/2026.3`: existência do `v2026.2.7.8.yaml` na base, colisão de numeração 7.8/7.9/7.10, `specs/`, atraso do branch, diffstat real e o que o PR de fato adiciona nos arquivos de escopo solto. Grava `base-report.txt`. | **Não.** Só leitura. |
| [`01-fase1-remocao.sh`](01-fase1-remocao.sh) | Remove os 12 arquivos da formação 001 na ordem obrigatória e tira o `include` do `db.changelog-master.yaml`. | **Sim** — mas só com `--apply`; sem a flag é simulação. |

## As três guardas do `01`

O script **se recusa a rodar** se:

1. **B1 falhar** — se `v2026.2.7.8.yaml` existir em `origin/2026.3`, aborta. Nesse caso a decisão D2
   inverte (passa a exigir `dropTable` + migração de dados) e o passo 1 de
   `TurProfileMigrationOnStartup` **não** pode ser removido.
2. **As edições não tiverem sido feitas** — ele faz `grep` por referências remanescentes ao 001 fora
   dos 12 arquivos que vão sumir. Deletar antes das edições quebra a compilação.
3. **O `include` não bater** — o trecho Python só grava se remover **exatamente um** bloco do 7.8.
   Qualquer outro resultado é erro e nada é escrito.

Ordem interna: passo 2 (runner) → 5 (serviço/DTOs/testes) → 6 (repos) → 7 (entidades) → 8 (schema,
**por último**). Os passos 1, 3 e 4 são edições, feitas fora do script — ver [`plan.md`](../plan.md) §2.

Nada é commitado. Para desfazer: `git checkout -- . && git reset`.

## Alternativa: destravar o shell na origem

Se o bloqueio for só do modo de permissão automático, adicionar regras de allow em `/permissions`
para `Bash(git:*)`, `Bash(bash:*)` e `Bash(./mvnw.cmd:*)` tende a contornar o classificador — aí não
há necessidade destes scripts e o trabalho volta a correr direto na sessão.
