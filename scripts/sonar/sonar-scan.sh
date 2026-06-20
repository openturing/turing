#!/usr/bin/env bash
#
# Bootstraps the local SonarQube instance and runs a Maven analysis of turing-app
# against it (NOT SonarCloud). Idempotent: safe to re-run.
#
#   1. Changes the default admin password (admin/admin -> $SONAR_ADMIN_PASSWORD) on first run.
#   2. (Re)generates an analysis token and caches it in scripts/sonar/.sonar-token.
#   3. Runs `mvn ... sonar:sonar` against http://localhost:9100.
#
# Prereqs: the SonarQube stack must be up:
#   docker compose -f containers/sonarqube/docker-compose.yaml up -d
#
# Env overrides:
#   SONAR_URL              (default http://localhost:9100)
#   SONAR_ADMIN_PASSWORD   (default TuringSonar@2026)
#   SONAR_GOAL             (default "verify" — runs unit + integration tests so
#                           JaCoCo reports coverage. Use "test" for unit-only
#                           coverage, or "compile" to skip tests / report 0%.)
set -euo pipefail

SONAR_URL="${SONAR_URL:-http://localhost:9100}"
ADMIN_PW="${SONAR_ADMIN_PASSWORD:-TuringSonar@2026}"
GOAL="${SONAR_GOAL:-verify}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TOKEN_FILE="$SCRIPT_DIR/.sonar-token"

echo "==> Waiting for SonarQube at $SONAR_URL ..."
for i in $(seq 1 60); do
  status="$(curl -fsS "$SONAR_URL/api/system/status" 2>/dev/null || true)"
  case "$status" in
    *'"status":"UP"'*) echo "    SonarQube is UP"; break ;;
    *) printf '.'; sleep 5 ;;
  esac
  if [ "$i" -eq 60 ]; then echo; echo "ERROR: SonarQube did not come UP in time." >&2; exit 1; fi
done
echo

# 1) Change default admin password if still admin/admin (ignore failure if already changed).
echo "==> Ensuring admin password is set ..."
if curl -fsS -u "admin:admin" "$SONAR_URL/api/authentication/validate" 2>/dev/null | grep -q '"valid":true'; then
  curl -fsS -u "admin:admin" -X POST "$SONAR_URL/api/users/change_password" \
    --data-urlencode "login=admin" \
    --data-urlencode "previousPassword=admin" \
    --data-urlencode "password=$ADMIN_PW" >/dev/null && echo "    Password changed."
else
  echo "    Default password already changed (using \$SONAR_ADMIN_PASSWORD)."
fi

# 2) (Re)generate an analysis token. Token value is shown only once, so revoke + regenerate.
echo "==> Generating analysis token ..."
curl -fsS -u "admin:$ADMIN_PW" -X POST "$SONAR_URL/api/user_tokens/revoke" \
  --data-urlencode "name=turing-local" >/dev/null 2>&1 || true
TOKEN="$(curl -fsS -u "admin:$ADMIN_PW" -X POST "$SONAR_URL/api/user_tokens/generate" \
  --data-urlencode "name=turing-local" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
if [ -z "$TOKEN" ]; then echo "ERROR: could not generate token." >&2; exit 1; fi
printf '%s' "$TOKEN" > "$TOKEN_FILE"
echo "    Token cached in scripts/sonar/.sonar-token"

# 3) Run the analysis against the LOCAL instance.
#    Run from inside turing-app so it is the reactor's top-level project (the
#    sonar goal refuses to run as a `-pl` submodule). Sibling modules are
#    resolved from the local .m2 repo. Override host.url + token, and blank out
#    sonar.organization (a SonarCloud-only concept).
echo "==> Running Maven analysis ($GOAL sonar:sonar) ..."
cd "$REPO_ROOT/turing-app"
# -Dmaven.test.failure.ignore=true: a coverage scan should still publish the
#   JaCoCo report (and Sonar analysis) even if a test fails.
# jacoco.xmlReportPaths: the merged unit+integration report emitted at `verify`
#   by the root pom's `coverage` profile.
mvn $GOAL org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dskip.npm=true \
  -Dmaven.test.failure.ignore=true \
  -Dsonar.host.url="$SONAR_URL" \
  -Dsonar.token="$TOKEN" \
  -Dsonar.organization= \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml

echo
echo "==> Done. Open $SONAR_URL/dashboard?id=viglet_turing"
echo "    List issues with: scripts/sonar/sonar-issues.sh"
