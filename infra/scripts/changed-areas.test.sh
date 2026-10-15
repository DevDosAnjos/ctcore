#!/usr/bin/env bash
# Prova de changed-areas.sh em repositório temporário (RNF-35, CA2, CA3).
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd)/changed-areas.sh"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
git init -q . && git config user.email t@t && git config user.name t
mkdir -p backend frontend docs .github docs
for f in backend/a frontend/a docs/openapi.yaml docs/x.md .github/w; do echo 0 > "$f"; done
git add . && git commit -qm base

check() { # nome, arquivo alterado, alvo, esperado
  git checkout -q -B t HEAD~0 2>/dev/null || true
  echo 1 >> "$2"; git commit -qam c
  got="$(bash "$script" HEAD~1 HEAD "$3" | tr '\n' ' ')"
  git reset -q --hard HEAD~1
  [ "$got" = "$4 " ] || { echo "FALHOU $1: obtido '$got', esperado '$4'"; exit 1; }
  echo "ok  $1"
}
check "só frontend"       frontend/a        dev  "backend=false frontend=true"
check "só backend"        backend/a         dev  "backend=true frontend=false"
check "só docs"           docs/x.md         dev  "backend=false frontend=false"
check "openapi"           docs/openapi.yaml dev  "backend=true frontend=true"
check "workflow"          .github/w         dev  "backend=true frontend=true"
check "main sem diferença" docs/x.md        main "backend=true frontend=true"
