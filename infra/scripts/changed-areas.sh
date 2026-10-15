#!/usr/bin/env bash
# Decide quais etapas da esteira rodam (RNF-35).
# Uso: changed-areas.sh <base> <head> <branch-alvo>
# Saída: backend=true|false e frontend=true|false.
set -euo pipefail
base="${1:?base}"; head="${2:?head}"; target="${3:?branch alvo}"

if [ "$target" = "main" ]; then
  echo "backend=true"; echo "frontend=true"; exit 0
fi

files="$(git diff --name-only "$base" "$head")"
has() { printf '%s\n' "$files" | grep -Eq "$1"; }
shared='^(docs/openapi\.yaml|\.github/|infra/ci/|infra/scripts/changed-areas)'

be=false; fe=false
{ has '^backend/' || has "$shared"; } && be=true
{ has '^frontend/' || has "$shared"; } && fe=true
echo "backend=$be"; echo "frontend=$fe"
