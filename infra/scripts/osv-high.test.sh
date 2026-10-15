#!/usr/bin/env bash
# Prova de osv-high.sh com saídas do osv-scanner montadas à mão (RNF-35).
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd)/osv-high.sh"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

pkg() { # nome, nota -> resultado com um pacote e um grupo, como o osv-scanner v2 grava
  printf '{"results":[{"source":{"path":"x","type":"lockfile"},"packages":[{"package":{"name":"%s","version":"1.0.0","ecosystem":"npm"},"groups":[{"ids":["GHSA-xxxx"],"aliases":null,"max_severity":"%s"}]}]}]}' "$1" "$2"
}

check() { # nome, json, código esperado (!0 = qualquer falha), trecho esperado na saída
  printf '%s' "$2" > "$tmp/osv.json"
  code=0; out="$(bash "$script" "$tmp/osv.json" 2>&1)" || code=$?
  if [ "$3" = "!0" ]; then [ "$code" != 0 ]; else [ "$code" = "$3" ]; fi \
    || { echo "FALHOU $1: código $code, esperado $3"; echo "$out"; exit 1; }
  case "$out" in *"$4"*) ;; *) echo "FALHOU $1: saída sem '$4'"; echo "$out"; exit 1 ;; esac
  echo "ok  $1"
}
check "sem resultados"  '{"results":[]}'                 0   "Nenhuma"
check "sem pacotes"     '{"results":[{"packages":[]}]}'  0   "Nenhuma"
check "nota média"      "$(pkg media 6.7)"               0   "Nenhuma"
check "nota alta"       "$(pkg alta 7.5)"                1   "alta@1.0.0 GHSA-xxxx severidade 7.5"
check "nota crítica"    "$(pkg critica 9.8)"             1   "severidade 9.8"
check "sem nota"        "$(pkg maliciosa '')"            1   "maliciosa@1.0.0 GHSA-xxxx sem nota de severidade"
check "json inválido"   'nao e json'                     !0  ""
