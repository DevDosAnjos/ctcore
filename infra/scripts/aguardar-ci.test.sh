#!/usr/bin/env bash
# Prova de aguardar-ci.sh com `gh` falso (#344): códigos de saída e arquivo de resultado.
set -u
script="$(cd "$(dirname "$0")" && pwd)/aguardar-ci.sh"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/bin"
cat > "$tmp/bin/gh" <<'GH'
#!/usr/bin/env bash
case "$*" in
  "pr view"*) echo abc ;;
  "run list"*) echo 1 ;;
  "pr checks"*) exit "${GH_CHECKS:-0}" ;;
  *"conclusion,jobs"*) echo "${GH_INFO:-}" ;;
  *"--json jobs"*) [ -z "${GH_FANT:-}" ] || echo "$GH_FANT" ;;
  *"--json conclusion"*) echo "${GH_CONC:-failure}" ;;
  *"--json status"*) echo "${GH_STATUS:-completed}" ;;
esac
GH
chmod +x "$tmp/bin/gh"
export PATH="$tmp/bin:$PATH" TMPDIR="$tmp" CI_INTERVALO=1 CI_LIMITE=2
res="$tmp/aguardar-ci-pr7.resultado"

falha() { echo "FALHOU $1"; exit 1; }
check() { # nome, código esperado, trecho esperado no arquivo (env já definido pelo chamador)
  bash "$script" 7 >/dev/null 2>&1; got=$?
  [ "$got" -eq "$2" ] || falha "$1: saiu com $got, esperado $2"
  grep -q "^$2 .*$3" "$res" || falha "$1: resultado '$(cat "$res" 2>/dev/null)'"
  echo "ok  $1"
}
(GH_CHECKS=0 check "passou (0)" 0 "passou")
(GH_CHECKS=1 GH_CONC=failure check "falhou (1)" 1 "run 1")
(GH_STATUS=in_progress GH_FANT="9 job" check "fantasma (2)" 2 "job fantasma")
(GH_STATUS=in_progress check "tempo (3)" 3 "limite de 2s estourado")
(GH_CHECKS=8 GH_INFO="success success" check "fantasma de exibição (6)" 6 "fantasmas")
bash "$script" x >/dev/null 2>&1; [ $? -eq 5 ] || falha "uso errado (5)"; echo "ok  uso errado (5)"

# 4: segundo watcher com o primeiro vivo; o resultado do primeiro fica intacto
rm -f "$res"
GH_STATUS=in_progress CI_LIMITE=30 bash "$script" 7 >/dev/null 2>&1 & pid=$!
sleep 2
out="$(bash "$script" 7)"; got=$?
[ "$got" -eq 4 ] || falha "4: saiu com $got"
for t in "pid $pid" "$res" "kill $pid"; do grep -qF -- "$t" <<<"$out" || falha "4: saída sem '$t'"; done
[ ! -e "$res" ] || falha "4: o segundo watcher gravou resultado"
kill "$pid"; wait "$pid" 2>/dev/null
echo "ok  já há watcher (4)"
