#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 004 — Enxugamento do PR de Perfis
# Passo 0b: desambiguar o resultado do 00.
#
# O 00 reportou "B1 = EXISTE_NA_BASE", mas o histórico mostra que o
# v2026.2.7.8.yaml da base foi criado por "feat: add chat mode toggle for LLM
# execution in agents" — outra feature. Ou seja: provável COLISÃO DE NOME, não
# o mesmo changeset. Este script prova ou refuta isso, e mede o estrago.
#
# SOMENTE LEITURA.
#
#     bash specs/004-enxugamento-perfis/scripts/00b-verificar-colisao.sh
# ---------------------------------------------------------------------------
set -uo pipefail

BASE="${BASE:-origin/2026.3}"
CL="turing-app/src/main/resources/db/changelog"
MASTER="$CL/db.changelog-master.yaml"
OUT="specs/004-enxugamento-perfis/scripts/colisao-report.txt"

[ -d .git ] || { echo "ERRO: rode da raiz do repo turing/." >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"
exec > >(tee "$OUT") 2>&1

hr(){ printf '%s\n' "-------------------------------------------------------------"; }
sec(){ hr; echo "$*"; hr; }

sec "1. B1 DEFINITIVO — o 7.8/7.9/7.10 da base é o mesmo do branch?"
for n in 8 9 10; do
  f="$CL/v2026.2.7.$n.yaml"
  echo "### v2026.2.7.$n.yaml"
  bh=$(git rev-parse "$BASE:$f" 2>/dev/null || echo "-")
  hh=$(git rev-parse "HEAD:$f"  2>/dev/null || echo "-")
  echo "   blob base   : $bh"
  echo "   blob branch : $hh"
  if [ "$bh" = "$hh" ]; then
    echo "   >>> IDÊNTICOS (mesmo changeset dos dois lados)"
  else
    echo "   >>> DIFERENTES — colisão de NOME entre changesets distintos"
  fi
  echo "   -- changeSet ids NA BASE:"
  git show "$BASE:$f" 2>/dev/null | grep -E '^\s*-?\s*(changeSet|id|author):' | head -8 | sed 's/^/      /'
  echo "   -- tabelas citadas NA BASE:"
  git show "$BASE:$f" 2>/dev/null | grep -oE '(tableName|baseTableName|referencedTableName):[[:space:]]*[a-z_]+' | sort -u | sed 's/^/      /'
  echo "   -- tabelas citadas NO BRANCH:"
  git show "HEAD:$f"  2>/dev/null | grep -oE '(tableName|baseTableName|referencedTableName):[[:space:]]*[a-z_]+' | sort -u | sed 's/^/      /'
  echo
done

sec "2. A formação 001 chegou a existir na base, sob QUALQUER nome?"
echo "-- procurando 'sn_site_facet_profile' em TODO o changelog da base:"
found=0
while IFS= read -r f; do
  if git show "$BASE:$f" 2>/dev/null | grep -q 'sn_site_facet_profile'; then
    echo "   !! ENCONTRADO em $f"; found=1
  fi
done < <(git ls-tree -r --name-only "$BASE" -- "$CL/" | grep '\.yaml$')
[ "$found" = "0" ] && echo "   nenhum changelog da base cria sn_site_facet_profile  ->  D2 CONFIRMADA (sem dropTable)"
echo
echo "-- e o código da formação 001 existe na base?"
git ls-tree -r --name-only "$BASE" 2>/dev/null | grep -i 'FacetProfile' | sed 's/^/   /' || echo "   nenhum arquivo FacetProfile na base"

sec "3. A base já implementou perfis por conta própria?"
echo "-- changelogs da base que criam tabelas tur_profile*:"
while IFS= read -r f; do
  if git show "$BASE:$f" 2>/dev/null | grep -qE 'tur_profile'; then echo "   !! $f"; fi
done < <(git ls-tree -r --name-only "$BASE" -- "$CL/" | grep '\.yaml$')
echo "-- classes TurProfile* na base:"
git ls-tree -r --name-only "$BASE" 2>/dev/null | grep -E 'TurProfile' | sed 's/^/   /' || echo "   nenhuma (a formação 002 é 100% nova)"

sec "4. Próximo número LIVRE de changelog na base"
echo "-- últimos includes do master da base:"
git show "$BASE:$MASTER" 2>/dev/null | grep -oE 'v[0-9.]+\.yaml' | tail -5 | sed 's/^/   /'
echo
echo "-- maior v2026.3.1.N da base:"
git ls-tree -r --name-only "$BASE" -- "$CL/" | grep -oE 'v2026\.3\.1\.[0-9]+\.yaml' | sort -V | tail -3 | sed 's/^/   /'
echo
echo ">>> Os 3 changelogs do branch devem ser RENUMERADOS para a sequência acima + 1."

sec "5. B4 — onde foi parar o TurSNSiteFacetedFieldAPI na base?"
git ls-tree -r --name-only "$BASE" 2>/dev/null | grep -iE 'FacetedField|SNSiteFieldExtAPI' | sed 's/^/   /' \
  || echo "   nenhum arquivo com esse nome na base"

sec "6. B5/B8 — caminhos reais (o 00 errou o path)"
for pat in TurSNProcessQueue TurSNSearchProcess; do
  echo "### $pat"
  echo "   base   : $(git ls-tree -r --name-only "$BASE" 2>/dev/null | grep "/$pat.java" | head -2 | tr '\n' ' ')"
  echo "   branch : $(git ls-tree -r --name-only HEAD    2>/dev/null | grep "/$pat.java" | head -2 | tr '\n' ' ')"
  p=$(git ls-tree -r --name-only HEAD | grep "/$pat.java" | head -1)
  [ -n "$p" ] && echo "   diff   : $(git diff --numstat "$BASE"...HEAD -- "$p" | head -1)"
done

sec "7. ESTRAGO DO MERGE — arquivos alterados dos DOIS lados"
echo "(estes vão conflitar ao atualizar o branch para $BASE)"
MB=$(git merge-base HEAD "$BASE")
comm -12 \
  <(git diff --name-only "$MB" HEAD    | sort) \
  <(git diff --name-only "$MB" "$BASE" | sort) \
  | tee /tmp/conflitos.txt | head -40
echo
echo "TOTAL de arquivos tocados dos dois lados: $(wc -l < /tmp/conflitos.txt)"

sec "RESUMO — o que decidir"
echo "1. Se o item 1 disser DIFERENTES e o item 2 não achar sn_site_facet_profile:"
echo "     -> D2 CONFIRMADA (sem dropTable). O falso alarme do 00 foi só nome de arquivo."
echo "2. A renumeração (Q3) é OBRIGATÓRIA: use a sequência do item 4."
echo "3. Q7: com 1316 commits de atraso e o item 7 acima, atualizar para $BASE vem PRIMEIRO."
