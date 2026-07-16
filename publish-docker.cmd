@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM publish-docker.cmd — Build the Viglet Turing ES Docker image LOCALLY and
REM push it to the GitHub Container Registry (ghcr.io/openviglet/turing-ce).
REM
REM Unlike .github/workflows/publish-docker.yml (which clones + builds the source
REM INSIDE the container), this builds the Spring Boot fat-jar on the HOST using
REM your authenticated ~/.m2 settings, then packages it with Dockerfile.local.
REM Why: viglet-core is a PRIVATE GitHub Packages dependency — an in-container
REM Maven build has no credentials and fails with 401. Building on the host also
REM guarantees the image contains your local working-tree changes (e.g. the
REM viglet-core 2026.3.8 bump), not just the last pushed commit.
REM
REM Auth (push to ghcr.io): set a GitHub PAT with `write:packages` scope. To let
REM this script prune old image versions after the push (see below), the PAT also
REM needs the `delete:packages` scope:
REM   set CR_PAT=ghp_xxxxxxxxxxxxxxxxxxxx
REM   set CR_USER=your-github-username        (defaults to "openviglet")
REM If CR_PAT is unset, the script assumes you are already `docker login`-ed
REM (and cleanup is skipped, since it needs the token to call the GitHub API).
REM
REM Cleanup: every `--push` creates a NEW image digest on GHCR. The `latest` and
REM `<version>` tags move to the new digest, but the previous digest lingers —
REM now tagged only `sha-<old>`. Left unchecked, these accumulate as dozens of
REM "variations" of the same version. After a successful push this script calls
REM the GitHub Packages API to DELETE those leftover sha-only versions, keeping
REM only the freshly-pushed image (the one carrying `latest`). Semver-tagged
REM releases (e.g. an older 2026.3.3) and fully-untagged manifests are preserved.
REM
REM Usage:
REM   publish-docker.cmd            Build the jar on the host, then image + push.
REM   publish-docker.cmd --nobuild  Skip the Maven build; reuse the existing jar
REM                                 at turing-app/target/viglet-turing.jar.
REM   publish-docker.cmd --noclean  Skip the post-push GHCR cleanup step.
REM ============================================================================

set "REGISTRY=ghcr.io"
set "IMAGE_NAME=openviglet/turing-ce"
set "IMAGE=%REGISTRY%/%IMAGE_NAME%"
set "JAR=turing-app\target\viglet-turing.jar"

REM Split IMAGE_NAME (owner/package) for the GitHub Packages API cleanup call.
for /f "tokens=1,2 delims=/" %%a in ("%IMAGE_NAME%") do (
    set "CR_OWNER=%%a"
    set "PKG_NAME=%%b"
)

set "SKIP_BUILD=0"
set "SKIP_CLEAN=0"
:parse_args
if "%~1"=="" goto after_args
if /i "%~1"=="--nobuild" set "SKIP_BUILD=1"
if /i "%~1"=="-n"        set "SKIP_BUILD=1"
if /i "%~1"=="--noclean" set "SKIP_CLEAN=1"
shift
goto parse_args
:after_args

REM --- Resolve project version from the root pom.xml -------------------------
for /f "delims=" %%v in ('powershell -NoProfile -Command "(Select-Xml -Path pom.xml -XPath '/*[local-name()=\"project\"]/*[local-name()=\"version\"]').Node.InnerText"') do set "VERSION=%%v"
if "%VERSION%"=="" (
    echo [ERROR] Could not resolve project version from pom.xml.
    exit /b 1
)

REM --- Resolve current commit SHA for the image revision label ---------------
for /f "delims=" %%s in ('git rev-parse HEAD') do set "GIT_SHA=%%s"
set "SHA_SHORT=%GIT_SHA:~0,12%"

echo ============================================================
echo  Image   : %IMAGE%
echo  Version : %VERSION%
echo  Commit  : %GIT_SHA%
echo  Build   : %SKIP_BUILD%==0 ^(host mvn build^) / 1 ^(reuse jar^)
echo ============================================================

REM --- Build the fat-jar on the host (authenticated ~/.m2) -------------------
if "%SKIP_BUILD%"=="0" (
    echo [INFO] Building viglet-turing.jar on the host ^(mvn clean install, no tests / IT^) ...
    call mvnw.cmd clean install -DskipTests -Dmaven.test.skip=true -DskipITs -pl turing-app -am
    if errorlevel 1 (
        echo [ERROR] Maven build failed.
        exit /b 1
    )
) else (
    echo [INFO] --nobuild: reusing existing jar.
)

if not exist "%JAR%" (
    echo [ERROR] %JAR% not found. Run without --nobuild, or build it first.
    exit /b 1
)

REM --- Log in to ghcr.io if a PAT is provided --------------------------------
if not "%CR_PAT%"=="" (
    if "%CR_USER%"=="" set "CR_USER=openviglet"
    echo [INFO] Logging in to %REGISTRY% as !CR_USER! ...
    echo !CR_PAT! | docker login %REGISTRY% -u !CR_USER! --password-stdin
    if errorlevel 1 (
        echo [ERROR] docker login failed.
        exit /b 1
    )
) else (
    echo [INFO] CR_PAT not set — assuming an existing docker login to %REGISTRY%.
)

REM --- Build the runtime image (multi-tag: latest, version, sha) and push ----
docker buildx build ^
    --file Dockerfile.local ^
    --tag %IMAGE%:latest ^
    --tag %IMAGE%:%VERSION% ^
    --tag %IMAGE%:sha-%SHA_SHORT% ^
    --build-arg CACHE_BUST=%GIT_SHA% ^
    --push ^
    .

if errorlevel 1 (
    echo [ERROR] Docker build/push failed.
    exit /b 1
)

echo.
echo [OK] Pushed:
echo      %IMAGE%:latest
echo      %IMAGE%:%VERSION%
echo      %IMAGE%:sha-%SHA_SHORT%

REM --- Prune leftover old image versions (sha-only, no `latest`/semver) -------
if "%SKIP_CLEAN%"=="1" (
    echo [INFO] --noclean: skipping GHCR cleanup.
    goto done
)
if "%CR_PAT%"=="" (
    echo [INFO] CR_PAT not set — skipping GHCR cleanup ^(needs a PAT with delete:packages^).
    goto done
)

echo.
echo [INFO] Pruning old %IMAGE_NAME% image versions on %REGISTRY% ...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$h=@{Authorization='Bearer '+$env:CR_PAT; Accept='application/vnd.github+json'; 'X-GitHub-Api-Version'='2022-11-28'};" ^
  "$base='https://api.github.com/orgs/%CR_OWNER%/packages/container/%PKG_NAME%/versions';" ^
  "try { $vs=Invoke-RestMethod -Headers $h -Uri ($base+'?per_page=100') }" ^
  "catch { Write-Host '[WARN] Could not list package versions:' $_.Exception.Message; exit 0 };" ^
  "$deleted=0;" ^
  "foreach($v in $vs){" ^
  "  $tags=@($v.metadata.container.tags);" ^
  "  if($tags -contains 'latest'){continue};" ^
  "  if($tags | Where-Object {$_ -match '^[0-9]{4}\.[0-9]'}){continue};" ^
  "  if(-not ($tags | Where-Object {$_ -like 'sha-*'})){continue};" ^
  "  try { Invoke-RestMethod -Method Delete -Headers $h -Uri ($base+'/'+$v.id) | Out-Null;" ^
  "        Write-Host ('[INFO] Deleted old image version '+$v.id+' (tags: '+($tags -join ', ')+')'); $deleted++ }" ^
  "  catch { Write-Host ('[WARN] Could not delete version '+$v.id+': '+$_.Exception.Message) }" ^
  "};" ^
  "Write-Host ('[OK] Cleanup complete — removed '+$deleted+' old image version(s).')"

:done
endlocal
