#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 004 — Enxugamento do PR de Perfis
# Fase 1, parte destrutiva: remove os 12 arquivos da formação 001 e o include
# do changelog, NA ORDEM OBRIGATÓRIA (consumidor → dono → schema).
#
# PRÉ-REQUISITOS (o script confere e aborta se faltar):
#   1. 00-verificar-base.sh rodado e B1 = AUSENTE_NA_BASE
#   2. As EDIÇÕES da Fase 1 já aplicadas (TurSNSiteImport, TurProfileMigrationOnStartup,
#      TurSNSite e respectivos testes). O script valida que não sobrou referência.
#
# Como rodar (Git Bash), a partir da raiz do repo `turing/`:
#     bash specs/004-enxugamento-perfis/scripts/01-fase1-remocao.sh          # simulação
#     bash specs/004-enxugamento-perfis/scripts/01-fase1-remocao.sh --apply  # aplica
# ---------------------------------------------------------------------------
set -euo pipefail

BASE="${BASE:-origin/2026.3}"
CHANGELOG_DIR="turing-app/src/main/resources/db/changelog"
MASTER="$CHANGELOG_DIR/db.changelog-master.yaml"
APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

hr() { printf '%s\n' "-------------------------------------------------------------"; }
die() { hr; echo "ABORTADO: $*" >&2; hr; exit 1; }
run() { if [ "$APPLY" = "1" ]; then eval "$@"; else echo "   [simulação] $*"; fi; }

[ -d .git ] || die "rode a partir da raiz do repositório turing/."

hr
[ "$APPLY" = "1" ] && echo "MODO: APLICAR" || echo "MODO: SIMULAÇÃO (use --apply para valer)"
echo "branch: $(git rev-parse --abbrev-ref HEAD)   base: $BASE"
hr

# --- Guarda 1: B1 -----------------------------------------------------------
# ATENÇÃO: a checagem correta é por CONTEÚDO, não por nome de arquivo.
# A base tem um v2026.2.7.8.yaml, mas ele é de outra feature ("chat mode toggle")
# — colisão de nome. O que importa é se ALGUM changelog da base cria a tabela.
echo "[guarda 1] B1 — algum changelog da base cria sn_site_facet_profile?"
HIT=""
while IFS= read -r f; do
  if git show "$BASE:$f" 2>/dev/null | grep -q 'sn_site_facet_profile'; then
    HIT="$HIT $f"
  fi
done < <(git ls-tree -r --name-only "$BASE" -- "$CHANGELOG_DIR/" 2>/dev/null | grep '\.yaml$')
if [ -n "$HIT" ]; then
  die "as tabelas do 001 SÃO criadas pela base, em:$HIT
  A decisão D2 inverte: existem em instalação publicada, é preciso dropTable +
  plano de migração, e o passo 1 de TurProfileMigrationOnStartup NÃO pode ser
  removido. Revise o spec 004 antes."
fi
echo "         ok — nenhum changelog da base cria a tabela."

# --- Guarda 1b: colisão de numeração ---------------------------------------
echo "[guarda 1b] os changelogs do branch colidem com a base?"
COL=""
for n in 8 9 10; do
  bb=$(git rev-parse "$BASE:$CHANGELOG_DIR/v2026.2.7.$n.yaml" 2>/dev/null || true)
  hb=$(git rev-parse "HEAD:$CHANGELOG_DIR/v2026.2.7.$n.yaml" 2>/dev/null || true)
  if [ -n "$bb" ] && [ -n "$hb" ] && [ "$bb" != "$hb" ]; then
    COL="$COL v2026.2.7.$n.yaml"
  fi
done
if [ -n "$COL" ]; then
  die "colisão de nome de changelog com a base:$COL
  Cada um desses nomes existe dos dois lados com CONTEÚDO DIFERENTE. Renumerar os
  do branch (T1.10) é OBRIGATÓRIO e deve vir ANTES desta remoção — senão o merge
  para $BASE conflita e o DATABASECHANGELOG fica ambíguo.
  Rode o 00b-verificar-colisao.sh para ver a próxima numeração livre."
fi
echo "         ok — sem colisão."

# --- Guarda 2: árvore limpa -------------------------------------------------
echo "[guarda 2] árvore de trabalho"
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "         AVISO: há alterações não commitadas (esperado: as edições da Fase 1)."
  git status --short --untracked-files=no | head -20
else
  echo "         árvore limpa."
fi

# --- Guarda 3: as edições da Fase 1 já foram feitas? ------------------------
echo "[guarda 3] sobrou referência ao 001 fora dos 12 arquivos que vão sumir?"
LEFTOVER=$(grep -rIl --exclude-dir=target --exclude-dir=node_modules --exclude-dir=.git \
  -E 'TurSNSiteFacetProfile' turing-app/src 2>/dev/null \
  | grep -v 'TurSNSiteFacetProfile' || true)
if [ -n "$LEFTOVER" ]; then
  hr
  echo "Arquivos que AINDA referenciam a formação 001 e NÃO serão deletados:"
  echo "$LEFTOVER" | sed 's/^/   /'
  die "aplique primeiro as EDIÇÕES da Fase 1 (T1.1, T1.3, T1.4) nesses arquivos.
  Deletar antes disso quebra a compilação."
fi
echo "         ok — nenhuma referência pendente."

hr
echo "REMOÇÃO — ordem obrigatória: consumidor → dono → schema"
hr

echo "[passo 2] runner de seed do 001"
run "git rm -q turing-app/src/main/java/com/viglet/turing/onstartup/sn/TurSNSiteFacetProfileOnStartup.java"
run "git rm -q turing-app/src/test/java/com/viglet/turing/onstartup/sn/TurSNSiteFacetProfileOnStartupTest.java"

echo "[passo 5] serviço, DTOs e testes"
run "git rm -q turing-app/src/main/java/com/viglet/turing/sn/facet/TurSNSiteFacetProfileService.java"
run "git rm -q turing-app/src/main/java/com/viglet/turing/api/sn/bean/TurSNSiteFacetProfileDto.java"
run "git rm -q turing-app/src/main/java/com/viglet/turing/api/sn/bean/TurSNSiteFacetProfileRequest.java"
run "git rm -q turing-app/src/test/java/com/viglet/turing/sn/facet/TurSNSiteFacetProfileServiceTest.java"
run "git rm -q turing-app/src/test/java/com/viglet/turing/api/sn/bean/TurSNSiteFacetProfileRequestTest.java"

echo "[passo 6] repositórios"
run "git rm -q turing-app/src/main/java/com/viglet/turing/persistence/repository/sn/field/TurSNSiteFacetProfileRepository.java"
run "git rm -q turing-app/src/main/java/com/viglet/turing/persistence/repository/sn/field/TurSNSiteFacetProfileItemRepository.java"

echo "[passo 7] entidades"
run "git rm -q turing-app/src/main/java/com/viglet/turing/persistence/model/sn/field/TurSNSiteFacetProfile.java"
run "git rm -q turing-app/src/main/java/com/viglet/turing/persistence/model/sn/field/TurSNSiteFacetProfileItem.java"

echo "[passo 8] schema — POR ÚLTIMO"
run "git rm -q $CHANGELOG_DIR/v2026.2.7.8.yaml"

echo "         removendo o include do master…"
if [ "$APPLY" = "1" ]; then
  python - "$MASTER" <<'PY'
import sys
p = sys.argv[1]
with open(p, encoding='utf-8') as f:
    lines = f.readlines()
out, i, dropped = [], 0, 0
while i < len(lines):
    if lines[i].strip() == '- include:' and i + 1 < len(lines) \
       and 'v2026.2.7.8.yaml' in lines[i + 1]:
        i += 2
        dropped += 1
        continue
    out.append(lines[i]); i += 1
if dropped != 1:
    sys.exit(f"ERRO: esperava remover 1 bloco include do 7.8, removi {dropped}. Nada foi gravado.")
with open(p, 'w', encoding='utf-8', newline='') as f:
    f.writelines(out)
print(f"         ok — bloco include removido de {p}")
PY
else
  echo "   [simulação] remover o bloco '- include: db/changelog/v2026.2.7.8.yaml' de $MASTER"
fi

hr
echo "VERIFICAÇÃO"
hr
echo "-- ocorrências restantes de FacetProfile em turing-app/src (esperado: 0):"
grep -rIn --exclude-dir=target -E 'TurSNSiteFacetProfile|facetProfile' turing-app/src | head -20 || echo "   nenhuma"
echo
echo "-- master changelog, últimas 8 linhas:"
tail -8 "$MASTER"

hr
if [ "$APPLY" = "1" ]; then
  echo "PRÓXIMO PASSO — validar (nada foi commitado ainda):"
  echo "   ./mvnw.cmd -pl turing-app -am -Dskip.npm=true clean test"
  echo "   (e depois: boot em base COM dados, base VAZIA, e duas vezes seguidas)"
  echo
  echo "Para desfazer tudo:   git checkout -- . && git reset"
else
  echo "Simulação concluída. Rode de novo com --apply para valer."
fi
hr
