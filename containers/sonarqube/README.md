# Local SonarQube (dev code analysis)

Self-contained SonarQube Community + PostgreSQL for analyzing **turing-app**
locally, instead of (or before) pushing to SonarCloud.

## 1. Start the stack

```bash
# from repo root
docker compose -f containers/sonarqube/docker-compose.yaml up -d
```

First boot takes ~1–2 min. Check it's ready at <http://localhost:9100>
(or `curl http://localhost:9100/api/system/status` → `"status":"UP"`).

> **WSL2 / Docker Desktop note:** if SonarQube keeps restarting, the embedded
> Elasticsearch needs a higher `vm.max_map_count`. The compose file disables the
> bootstrap check, but if it still fails run once in a WSL shell:
> `wsl -d docker-desktop sysctl -w vm.max_map_count=262144`.

## 2. Run an analysis

```bash
scripts/sonar/sonar-scan.sh
```

This bootstraps the admin password + an analysis token (cached, git-ignored, in
`scripts/sonar/.sonar-token`), then runs `mvn ... sonar:sonar` against the local
instance. Default goal is `clean compile` (fast, finds bugs/smells/vulns). Set
`SONAR_GOAL=verify` to also produce test coverage.

## 3. List the issues

```bash
scripts/sonar/sonar-issues.sh            # bugs + vulns + smells
SONAR_SEVERITIES=BLOCKER,CRITICAL scripts/sonar/sonar-issues.sh   # only the worst
```

Output is `file:line  [rule]  SEVERITY  message` — paste-friendly to drive
fix-one-at-a-time work, or just browse the dashboard at
<http://localhost:9100/dashboard?id=viglet_turing>.

## Stop / reset

```bash
docker compose -f containers/sonarqube/docker-compose.yaml down       # keep data
docker compose -f containers/sonarqube/docker-compose.yaml down -v    # wipe data
```

## Notes

- This is **separate** from the repo-root `docker-compose.yaml` (the app stack)
  on purpose — analysis doesn't need the app running.
- The project's `pom.xml` keeps SonarCloud as the default target; the local scan
  overrides `sonar.host.url`/`sonar.token` and blanks `sonar.organization` (a
  SonarCloud-only concept) on the command line, so nothing in the POM changes.
- Default credentials: `admin` / `TuringSonar@2026` (override via `SONAR_ADMIN_PASSWORD`).
