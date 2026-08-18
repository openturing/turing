#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 004 — Enxugamento do PR de Perfis
# Passo 0: reverificação dos itens B1..B10 contra a base correta (origin/2026.3)
#
# SOMENTE LEITURA. Não altera nada no repositório.
#
# Como rodar (Git Bash), a partir da raiz do repo `turing/`:
#     bash specs/004-enxugamento-perfis/scripts/00-verificar-base.sh
#
# A saída vai para a tela e para specs/004-enxugamento-perfis/scripts/base-report.txt
# ---------------------------------------------------------------------------
set -uo pipefail

BASE="${BASE:-origin/2026.3}"
CHANGELOG_DIR="turing-app/src/main/resources/db/changelog"
MASTER="$CHANGELOG_DIR/db.changelog-master.yaml"
OUT="specs/004-enxugamento-perfis/scripts/base-report.txt"

if [ ! -d .git ]; then
  echo "ERRO: rode a partir da raiz do repositório turing/ (não achei .git)." >&2
  exit 1
fi
mkdir -p "$(dirname "$OUT")"
exec > >(tee "$OUT") 2>&1

hr() { printf '%s\n' "-------------------------------------------------------------"; }
sec() { hr; echo "$*"; hr; }

sec "CONTEXTO"
echo "branch atual : $(git rev-parse --abbrev-ref HEAD)"
echo "HEAD         : $(git rev-parse --short HEAD)"
echo "base         : $BASE = $(git rev-parse --short "$BASE" 2>/dev/null || echo '<NAO RESOLVE>')"
echo "merge-base   : $(git merge-base HEAD "$BASE" 2>/dev/null | cut -c1-9 || echo '<falhou>')"
echo
echo "AVISO: se a base não resolver, rode antes:  git fetch origin 2026.3"

sec "B1 — o DDL da formação 001 já existe na base?  (DECIDE a D2)"
if git cat-file -e "$BASE:$CHANGELOG_DIR/v2026.2.7.8.yaml" 2>/dev/null; then
  echo "B1 = EXISTE_NA_BASE"
  echo
  echo ">>> ATENÇÃO: a decisão D2 INVERTE."
  echo ">>> As tabelas sn_site_facet_profile existem em instalação publicada."
  echo ">>> Passa a exigir changeset dropTable + plano de migração de dados,"
  echo ">>> e o passo 1 de TurProfileMigrationOnStartup NÃO pode ser removido."
  echo ">>> NÃO rode o 01-fase1-remocao.sh. Revise o spec 004 antes."
else
  echo "B1 = AUSENTE_NA_BASE   (premissa do spec confirmada)"
fi
echo
echo "-- histórico do arquivo na base (deve ser vazio se AUSENTE):"
git log --oneline "$BASE" -- "$CHANGELOG_DIR/v2026.2.7.8.yaml" | head -5
echo "-- quem criou o arquivo no branch:"
git log --oneline HEAD -- "$CHANGELOG_DIR/v2026.2.7.8.yaml" | tail -3

sec "B2 — colisão de numeração de changelog?  (DECIDE a Q3)"
echo "-- últimos includes do master NA BASE:"
git show "$BASE:$MASTER" 2>/dev/null | tail -14 || echo "<master não encontrado na base>"
echo
echo "-- changelogs 7.x presentes NA BASE:"
git ls-tree -r --name-only "$BASE" -- "$CHANGELOG_DIR/" 2>/dev/null \
  | grep -E 'v2026\.2\.7\.[0-9]+\.yaml' | sort -V || echo "<nenhum>"
echo
for n in 8 9 10; do
  if git cat-file -e "$BASE:$CHANGELOG_DIR/v2026.2.7.$n.yaml" 2>/dev/null; then
    echo "  !! COLISÃO: v2026.2.7.$n.yaml já existe na base"
  else
    echo "  ok: v2026.2.7.$n.yaml livre na base"
  fi
done
echo
echo ">>> Se houver QUALQUER colisão, a Q3 deixa de ser estética:"
echo ">>> renumerar os changelogs do branch vira OBRIGATÓRIO (T1.10)."

sec "B3 — a pasta specs/ existe na base?"
if [ -z "$(git ls-tree -r --name-only "$BASE" -- specs/ 2>/dev/null)" ]; then
  echo "B3 = specs/ NÃO existe na base (é nova neste PR)"
else
  echo "B3 = specs/ JÁ existe na base:"
  git ls-tree -r --name-only "$BASE" -- specs/ | head -20
fi

sec "ATRASO DO BRANCH  (DECIDE a Q7)"
echo "commits da base que faltam no branch : $(git rev-list --count HEAD.."$BASE" 2>/dev/null)"
echo "commits do branch à frente da base   : $(git rev-list --count "$BASE"..HEAD 2>/dev/null)"
echo
echo "-- últimos 25 commits da base ausentes no branch:"
git log --oneline HEAD.."$BASE" 2>/dev/null | head -25

sec "TAMANHO REAL DO PR CONTRA A BASE CORRETA"
git diff --stat "$BASE"...HEAD 2>/dev/null | tail -3

sec "B4 — @Secured já existe na base?"
git show "$BASE:turing-app/src/main/java/com/viglet/turing/api/sn/TurSNSiteFacetedFieldAPI.java" 2>/dev/null \
  | grep -nE '@Secured|@(Get|Put|Post|Delete)Mapping' | head -30 \
  || echo "<arquivo não existe na base — então TODAS as rotas dele são novas>"

sec "B5/B6/B7/B8/B9 — o que o PR realmente adiciona vs. a base"
for f in \
  'turing-app/src/main/java/com/viglet/turing/solr/TurSolrUtils.java' \
  'turing-app/src/main/java/com/viglet/turing/solr/TurSolr.java' \
  'turing-app/src/main/java/com/viglet/turing/sn/queue/TurSNProcessQueue.java' \
  'turing-app/src/main/java/com/viglet/turing/solr/TurSolrQueryBuilder.java' \
  'turing-app/src/main/java/com/viglet/turing/sn/TurSNSearchProcess.java'
do
  n=$(git diff --numstat "$BASE"...HEAD -- "$f" 2>/dev/null)
  printf '%-72s %s\n' "$(basename "$f")" "${n:-<sem diferença / caminho inexistente>}"
done
echo
echo "-- frontend e index.html:"
git diff --stat "$BASE"...HEAD -- '*index.html' '*resolve.ts' '*search-content.tsx' 2>/dev/null | tail -6

sec "RESUMO"
echo "Cole a saída acima (ou o arquivo $OUT) de volta na conversa."
echo "As duas linhas que decidem tudo são a de B1 e as três de colisão em B2."
