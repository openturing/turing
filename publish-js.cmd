@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM publish-js.cmd — Build and publish the Viglet Turing ES JavaScript packages
REM to npm DIRECTLY from your machine, without the GitHub Actions workflows
REM (.github/workflows/publish-js-sdk.yml + publish-react-ui.yml).
REM
REM Packages (all under frontend/packages/, scope @viglet):
REM   js-sdk     -> @viglet/turing-sdk        (vanilla JS, zero-dep)
REM   react-sdk  -> @viglet/turing-react-sdk  (React hooks + components)
REM   react-ui   -> @viglet/turing-react-ui   (headless React UI primitives)
REM   flow-dsl   -> @viglet/turing-flow-dsl   (chatflow DSL transpiler)
REM   cli        -> @viglet/turing-cli        (init/dev/deploy/logs/eval CLI)
REM
REM Auth (publish to registry.npmjs.org):
REM   Either be `npm login`-ed already, OR set a token:
REM     set NPM_TOKEN=npm_xxxxxxxxxxxxxxxxxxxx
REM   When NPM_TOKEN is set, it is exported as NODE_AUTH_TOKEN and a temporary
REM   ~/.npmrc auth line is used for the publish.
REM
REM Each target package.json gets a patch bump (npm version patch, no git tag)
REM automatically before it is built and published, so the new version is never
REM rejected by npm as already-published. Pass --no-bump to skip this.
REM
REM Usage:
REM   publish-js.cmd                       Patch-bump + publish ALL packages.
REM   publish-js.cmd js-sdk                Only the vanilla JS SDK.
REM   publish-js.cmd react-sdk react-ui    A chosen subset.
REM   publish-js.cmd flow-dsl cli          The DSL transpiler + CLI.
REM   publish-js.cmd --no-bump [targets]   Publish the current version as-is
REM                                        (no patch bump).
REM
REM Notes:
REM   * Every run patch-bumps each target package.json (npm version patch, no git
REM     tag) BEFORE building, so the new version is never rejected by npm as
REM     already-published. Pass --no-bump to opt out.
REM   * react-ui / flow-dsl / cli run their tests (and react-ui its bundle-size
REM     budget) as a publish gate.
REM   * Publishes to registry.npmjs.org (per each package's publishConfig).
REM ============================================================================

set "FRONTEND=%~dp0frontend"

REM --- Resolve pnpm: prefer the repo's bundled pnpm (correct version, no -------
REM     corepack self-download) over a PATH shim that tries to switch versions.
REM     Two pnpm settings keep turbo's script runs from re-spawning the broken
REM     global pnpm shim (do NOT prepend frontend\node to PATH — its npm.cmd is
REM     a stub that has no npm-cli.js and would shadow the real global npm):
REM       1. manage-package-manager-versions=false — never download/switch to
REM          the version in the `packageManager` field (the global shim fails
REM          this with ENOENT on .tools\...).
REM       2. verify-deps-before-run=false — skip the pre-run deps status check
REM          that spawns a child `pnpm` (which would resolve to the global
REM          v10.x and fail the engines.pnpm >=11 gate).
set "npm_config_manage_package_manager_versions=false"
set "npm_config_verify_deps_before_run=false"
if exist "%FRONTEND%\node\pnpm.cmd" (
    set "PNPM=%FRONTEND%\node\pnpm.cmd"
) else (
    set "PNPM=pnpm"
)

REM --- Parse args ------------------------------------------------------------
set "DO_BUMP=1"
set "WANT_JSSDK=0"
set "WANT_REACTSDK=0"
set "WANT_REACTUI=0"
set "WANT_FLOWDSL=0"
set "WANT_CLI=0"
set "ANY_TARGET=0"

:parse
if "%~1"=="" goto after_parse
if /i "%~1"=="--no-bump"  ( set "DO_BUMP=0" & shift & goto parse )
if /i "%~1"=="js-sdk"     ( set "WANT_JSSDK=1"    & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="sdk"        ( set "WANT_JSSDK=1"    & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="react-sdk"  ( set "WANT_REACTSDK=1" & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="react-ui"   ( set "WANT_REACTUI=1"  & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="flow-dsl"   ( set "WANT_FLOWDSL=1"  & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="cli"        ( set "WANT_CLI=1"      & set "ANY_TARGET=1" & shift & goto parse )
echo [ERROR] Unknown argument: %~1
echo         Valid targets: js-sdk ^| react-sdk ^| react-ui ^| flow-dsl ^| cli   (flag: --no-bump)
exit /b 1

:after_parse
if "%ANY_TARGET%"=="0" (
    set "WANT_JSSDK=1"
    set "WANT_REACTSDK=1"
    set "WANT_REACTUI=1"
    set "WANT_FLOWDSL=1"
    set "WANT_CLI=1"
)

echo ============================================================
echo  Publishing Viglet Turing ES JS packages to npm
echo  js-sdk    (@viglet/turing-sdk)       : %WANT_JSSDK%
echo  react-sdk (@viglet/turing-react-sdk) : %WANT_REACTSDK%
echo  react-ui  (@viglet/turing-react-ui)  : %WANT_REACTUI%
echo  flow-dsl  (@viglet/turing-flow-dsl)  : %WANT_FLOWDSL%
echo  cli       (@viglet/turing-cli)       : %WANT_CLI%
echo  patch-bump before publish            : %DO_BUMP% ^(1=yes^)
echo ============================================================

REM --- Auth: if NPM_TOKEN is set, write a temp .npmrc that pnpm publish will ----
REM     read for the registry auth token. We expose it via the npm_config_userconfig
REM     env var (honored by BOTH npm and pnpm) only AFTER the workspace install,
REM     so the install still uses the real ~/.npmrc (for @openviglet / GitHub
REM     Packages / viglet-core). Deleted at the end. If NPM_TOKEN is unset we
REM     fall back to the user's existing ~/.npmrc / `npm login`.
set "NPMRC_TMP="
if not "%NPM_TOKEN%"=="" (
    set "NPMRC_TMP=%TEMP%\turing-publish-%RANDOM%%RANDOM%.npmrc"
    >  "!NPMRC_TMP!" echo registry=https://registry.npmjs.org/
    >> "!NPMRC_TMP!" echo //registry.npmjs.org/:_authToken=%NPM_TOKEN%
    >> "!NPMRC_TMP!" echo @viglet:registry=https://registry.npmjs.org/
    >> "!NPMRC_TMP!" echo always-auth=true
    echo [INFO] Using NPM_TOKEN for npm authentication ^(temp .npmrc^).
) else (
    echo [INFO] NPM_TOKEN not set — assuming an existing `npm login`.
)

REM --- Install workspace deps (frozen=false, like the workflows) --------------
echo [INFO] Installing workspace deps ...
call "%PNPM%" -C "%FRONTEND%" install --frozen-lockfile=false
if errorlevel 1 (
    echo [ERROR] pnpm install failed.
    exit /b 1
)

REM Now that deps are installed with the real ~/.npmrc, point npm/pnpm at the
REM token-only temp config for the publish steps below.
if not "%NPMRC_TMP%"=="" set "npm_config_userconfig=%NPMRC_TMP%"

REM Publish order matters: react-sdk declares `workspace:*` on BOTH sdk and
REM react-ui, and pnpm rewrites those to the dependency's CURRENT (bumped)
REM version at publish time. So sdk and react-ui MUST be bumped+published BEFORE
REM react-sdk — otherwise react-sdk pins the pre-bump version of its deps (it
REM did once: react-sdk@2026.3.4 → react-ui@2026.3.3 instead of 2026.3.4).
REM Therefore: sdk → react-ui → react-sdk (react-sdk always LAST).

REM flow-dsl and cli have NO workspace deps, so their order is free; publish
REM them first, then the sdk → react-ui → react-sdk chain.

REM ── flow-dsl ────────────────────────────────────────────────────────────
if "%WANT_FLOWDSL%"=="1" (
    echo.
    echo --- @viglet/turing-flow-dsl -------------------------------------------
    if "%DO_BUMP%"=="1" call :bump "%FRONTEND%\packages\flow-dsl" || exit /b 1
    call "%PNPM%" -C "%FRONTEND%" --filter "@viglet/turing-flow-dsl" run build
    if errorlevel 1 ( echo [ERROR] flow-dsl build failed. & exit /b 1 )
    echo [INFO] Publish gate: tests ...
    call "%PNPM%" -C "%FRONTEND%\packages\flow-dsl" run test
    if errorlevel 1 ( echo [ERROR] flow-dsl tests failed. & exit /b 1 )
    call :publish "%FRONTEND%\packages\flow-dsl" || exit /b 1
)

REM ── cli ─────────────────────────────────────────────────────────────────
if "%WANT_CLI%"=="1" (
    echo.
    echo --- @viglet/turing-cli ------------------------------------------------
    if "%DO_BUMP%"=="1" call :bump "%FRONTEND%\packages\cli" || exit /b 1
    call "%PNPM%" -C "%FRONTEND%" --filter "@viglet/turing-cli" run build
    if errorlevel 1 ( echo [ERROR] cli build failed. & exit /b 1 )
    echo [INFO] Publish gate: tests ...
    call "%PNPM%" -C "%FRONTEND%\packages\cli" run test
    if errorlevel 1 ( echo [ERROR] cli tests failed. & exit /b 1 )
    call :publish "%FRONTEND%\packages\cli" || exit /b 1
)

REM ── js-sdk ──────────────────────────────────────────────────────────────
if "%WANT_JSSDK%"=="1" (
    echo.
    echo --- @viglet/turing-sdk ------------------------------------------------
    if "%DO_BUMP%"=="1" call :bump "%FRONTEND%\packages\sdk" || exit /b 1
    call "%PNPM%" -C "%FRONTEND%" run build:sdk
    if errorlevel 1 ( echo [ERROR] build:sdk failed. & exit /b 1 )
    call :publish "%FRONTEND%\packages\sdk" || exit /b 1
)

REM ── react-ui ────────────────────────────────────────────────────────────
if "%WANT_REACTUI%"=="1" (
    echo.
    echo --- @viglet/turing-react-ui -------------------------------------------
    if "%DO_BUMP%"=="1" call :bump "%FRONTEND%\packages\react-ui" || exit /b 1
    call "%PNPM%" -C "%FRONTEND%" --filter "@viglet/turing-react-ui..." run build
    if errorlevel 1 ( echo [ERROR] react-ui build failed. & exit /b 1 )
    echo [INFO] Publish gate: bundle-size budget ...
    call "%PNPM%" -C "%FRONTEND%\packages\react-ui" run size
    if errorlevel 1 ( echo [ERROR] react-ui size budget exceeded. & exit /b 1 )
    echo [INFO] Publish gate: tests ...
    call "%PNPM%" -C "%FRONTEND%\packages\react-ui" run test
    if errorlevel 1 ( echo [ERROR] react-ui tests failed. & exit /b 1 )
    call :publish "%FRONTEND%\packages\react-ui" || exit /b 1
)

REM ── react-sdk (LAST — depends on sdk + react-ui above) ───────────────────
if "%WANT_REACTSDK%"=="1" (
    echo.
    echo --- @viglet/turing-react-sdk ------------------------------------------
    if "%DO_BUMP%"=="1" call :bump "%FRONTEND%\packages\react-sdk" || exit /b 1
    call "%PNPM%" -C "%FRONTEND%" run build:react-sdk
    if errorlevel 1 ( echo [ERROR] build:react-sdk failed. & exit /b 1 )
    call :publish "%FRONTEND%\packages\react-sdk" || exit /b 1
)

if not "%NPMRC_TMP%"=="" if exist "%NPMRC_TMP%" del /q "%NPMRC_TMP%"

echo.
echo [OK] Done.
endlocal
exit /b 0

REM ---------------------------------------------------------------------------
:bump
echo [INFO] Patch-bumping %~1 ...
pushd "%~1"
call npm version patch --no-git-tag-version
set "RC=%errorlevel%"
popd
if not "%RC%"=="0" ( echo [ERROR] npm version patch failed in %~1. & exit /b 1 )
exit /b 0

REM ---------------------------------------------------------------------------
:publish
REM Use `pnpm publish` (NOT `npm publish`): pnpm rewrites the workspace:* deps
REM (e.g. @viglet/turing-sdk, @viglet/turing-react-ui in react-sdk) to the real
REM published versions. `npm publish` leaves `workspace:*` verbatim, producing a
REM package that is uninstallable via npm ("Unsupported URL Type workspace:").
REM --no-git-checks: the working tree is dirty from the version bumps.
echo [INFO] Publishing %~1 ...
call "%PNPM%" -C "%~1" publish --access public --no-git-checks
set "RC=%errorlevel%"
if not "%RC%"=="0" (
    echo [ERROR] pnpm publish failed in %~1.
    if not "%NPMRC_TMP%"=="" if exist "%NPMRC_TMP%" del /q "%NPMRC_TMP%"
    exit /b 1
)
exit /b 0
