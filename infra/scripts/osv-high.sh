#!/usr/bin/env bash
# Reprova dependência com falha de severidade alta ou crítica (nota CVSS 7 ou mais) ou sem nota (RNF-35).
# Uso: osv-high.sh <osv.json>, a saída de `osv-scanner scan source --format json`.
# Sem nota, o osv-scanner grava max_severity "" (ex.: pacote malicioso MAL-*): não dá para saber se é baixa, então reprova.
set -euo pipefail
file="${1:?osv.json}"

found="$(jq -r '
  [.results[]?.packages[]? | .package as $p | .groups[]?
   | (.max_severity | tonumber? // null) as $s
   | select($s == null or $s >= 7)
   | "\($p.name)@\($p.version) \(.ids | join(",")) "
     + (if $s == null then "sem nota de severidade" else "severidade \(.max_severity)" end)]
  | .[]' "$file")"

if [ -n "$found" ]; then
  printf '%s\n' "$found"
  echo "Dependências com falha de severidade alta ou crítica, ou sem nota."
  exit 1
fi
echo "Nenhuma dependência com falha de severidade alta ou crítica."
