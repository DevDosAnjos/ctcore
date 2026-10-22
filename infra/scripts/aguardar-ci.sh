#!/usr/bin/env bash
# Espera o CI de um PR terminar, uma vez, e diz o resultado pelo código de saída.
#
#   bash infra/scripts/aguardar-ci.sh <PR>          (rodar em segundo plano)
#
# Códigos de saída:
#   0  passou          todos os checks do PR passaram (gh pr checks saiu com 0)
#   1  falhou          `gh pr checks` devolveu 1 de verdade (check falhou); ler `gh run view <run> --log-failed` antes de corrigir
#   2  job fantasma    job pendente/em andamento com todos os passos já concluídos; rerodar só ele
#                      uma vez (`gh run rerun <run> --job <id>`); se travar de novo, validar local,
#                      comentar no PR e parar para o dono decidir o merge
#   3  tempo estourou  passou o limite sem terminar e sem fantasma
#   4  já há watcher   outro aguardar-ci.sh está rodando para este PR; não iniciar outro
#   5  erro            uso errado, head do PR mudou (push novo) ou 3 falhas seguidas do gh
#   6  passou, com fantasma de exibição   run concluído com success e job ci-gate com success, mas
#                      checks ainda "pendentes" na exibição; não bloqueiam. Seguir para o merge e
#                      registrar um comentário no PR com os jobs listados na saída
#
# Variáveis: CI_LIMITE (segundos, padrão 2400 = ~4x o CI normal de ~10 min),
#            CI_INTERVALO (padrão 30), CI_FANTASMA (carência em segundos, padrão 600).
# Nunca corta a saída do gh; `gh pr checks` com código 8 é "pendente", não falha.
# Falhas passageiras do gh (rede, 5xx, código inesperado do `gh pr checks`): a chamada que falhar
# é tentada de novo no próximo ciclo, depois de CI_INTERVALO; só 3 falhas seguidas dão código 5.
# Um ciclo bom zera a contagem. "gh falhou" nunca é confundido com "head mudou" (esse sai com 5 na hora).
set -u

pr="${1:-}"
[[ "$pr" =~ ^[0-9]+$ ]] || { echo "uso: $0 <número-do-PR>" >&2; exit 5; }
limite="${CI_LIMITE:-2400}"; intervalo="${CI_INTERVALO:-30}"; carencia="${CI_FANTASMA:-600}"
max_falhas=3

# Um watcher por PR: diretório de trava com o pid; trava de processo morto é refeita.
trava="${TMPDIR:-/tmp}/aguardar-ci-pr$pr.lock"
if ! mkdir "$trava" 2>/dev/null; then
  if kill -0 "$(cat "$trava/pid" 2>/dev/null)" 2>/dev/null; then
    echo "já há watcher para o PR #$pr (pid $(cat "$trava/pid"))"; exit 4
  fi
  rm -rf "$trava"; mkdir "$trava" || exit 5
fi
echo $$ > "$trava/pid"
trap 'rm -rf "$trava"' EXIT

inicio=$SECONDS
run=""
sha=""

# Jobs não concluídos cujos passos já terminaram há mais de $carencia segundos.
fantasmas() {
  gh run view "$run" --json jobs --jq "[.jobs[] | select(.status != \"completed\")
    | select((.steps | length) > 0 and all(.steps[]; .status == \"completed\"))
    | select(now - ([.steps[].completedAt | select(. != null) | fromdateiso8601] | max) > $carencia)
    | \"\(.databaseId) \(.name)\"] | .[]"
}

# Um ciclo. Sai do script nos resultados finais; devolve 10 quando uma chamada do gh falhou
# (falha passageira) e 0 quando é só esperar o próximo ciclo.
ciclo() {
  local atual estado code info f
  atual=$(gh pr view "$pr" --json headRefOid --jq .headRefOid) || return 10
  [ -n "$atual" ] || return 10
  [ -n "$sha" ] || sha=$atual
  [ "$atual" = "$sha" ] || { echo "o head do PR #$pr mudou; reiniciar a espera"; exit 5; }

  # Run mais recente do CI para este commit; a tentativa certa é a última do run (gh view já a devolve).
  if [ -z "$run" ]; then
    run=$(gh run list --workflow CI --commit "$sha" --json databaseId,createdAt \
      --jq 'sort_by(.createdAt) | last | .databaseId // empty') || { run=""; return 10; }
  fi
  [ -n "$run" ] || return 0

  estado=$(gh run view "$run" --json status --jq .status) || return 10
  if [ "$estado" = completed ]; then
    gh pr checks "$pr"; code=$?
    case $code in
      0) exit 0 ;;
      1) echo "run $run: ver gh run view $run --log-failed"; exit 1 ;;
      8) # pendente na exibição: com o run em success e o ci-gate em success, são fantasmas de exibição
         info=$(gh run view "$run" --json conclusion,jobs --jq '[.conclusion, ([.jobs[] | select(.name == "ci-gate") | .conclusion] | first // "")] | join(" ")') || return 10
         if [ "$info" = "success success" ]; then
           echo "run $run success e ci-gate success; checks pendentes são fantasmas de exibição:"; fantasmas; exit 6
         fi
         f=$(fantasmas) || return 10
         [ -z "$f" ] || { echo "run $run concluído sem ci-gate verde, com job fantasma:"; echo "$f"; exit 2; } ;;
      *) return 10 ;;           # código inesperado do gh (rede, 5xx): falha passageira
    esac
  else
    f=$(fantasmas) || return 10
    [ -z "$f" ] || { echo "job fantasma no run $run:"; echo "$f"; exit 2; }
  fi
  return 0
}

falhas=0
while :; do
  ciclo; rc=$?
  if [ "$rc" -eq 10 ]; then
    falhas=$((falhas + 1))
    echo "falha passageira do gh ($falhas/$max_falhas)"
    [ "$falhas" -lt "$max_falhas" ] || { echo "$max_falhas falhas seguidas do gh"; exit 5; }
  else
    falhas=0
  fi

  if (( SECONDS - inicio >= limite )); then
    echo "limite de ${limite}s estourado (run ${run:-nenhum})"
    if [ -n "$run" ] && f=$(fantasmas) && [ -n "$f" ]; then echo "job fantasma:"; echo "$f"; exit 2; fi
    exit 3
  fi
  sleep "$intervalo"
done
