#!/usr/bin/env bash
# Teste visual no container do CI (mesma imagem do job e2e), para as referências não variarem entre máquinas.
# Uso (de dentro de frontend/): npm run test:visual:docker [-- --update-snapshots]
set -euo pipefail
cd "$(dirname "$0")/.."
version=$(node -p "require('@playwright/test/package.json').version")
image="mcr.microsoft.com/playwright:v${version}-noble"
had=$(docker images -q "$image")
host=$(pwd -W 2>/dev/null || pwd)
mkdir -p visual/referencias test-results

set +e
MSYS_NO_PATHCONV=1 docker run --rm --ipc=host \
  -v "$host":/repo:ro \
  -v "$host/visual/referencias":/out/referencias \
  -v "$host/test-results":/out/test-results \
  -e CI= "$image" bash -c '
    set -e
    mkdir /work && cd /repo && tar --exclude=node_modules --exclude=test-results --exclude=playwright-report --exclude=dist --exclude=coverage -cf - . | tar -xf - -C /work
    cd /work && npm ci --no-audit --no-fund >/dev/null
    rc=0; npx playwright test -c playwright.visual.config.ts "$@" || rc=$?
    cp -r visual/referencias/. /out/referencias/ 2>/dev/null || true
    cp -r test-results/. /out/test-results/ 2>/dev/null || true
    exit $rc' _ "$@"
rc=$?
set -e

# imagem baixada só para esta prova sai ao fim (AGENTS, "Docker local")
[ -z "$had" ] && docker rmi "$image" >/dev/null 2>&1 || true
exit $rc
