#!/usr/bin/env bash
# Espera o CI de um PR terminar, uma vez, e diz o resultado pelo código de saída.
#
#   bash infra/scripts/aguardar-ci.sh <PR>          (rodar em segundo plano)
#
# Códigos de saída:
#   0  passou          todos os checks do PR passaram (gh pr checks saiu com 0)
#   1  falhou          algum check falhou; ler `gh run view <run> --log-failed` antes de corrigir
#   2  job fantasma    job pendente/em andamento com todos os passos já concluídos; rerodar só ele
#                      uma vez (`gh run rerun <run> --job <id>`); se travar de novo, validar local,
#                      comentar no PR e parar para o dono decidir o merge
#   3  tempo estourou  passou o limite sem terminar e sem fantasma
#   4  já há watcher   outro aguardar-ci.sh está rodando para este PR; não iniciar outro
#   5  erro            uso errado, gh sem login, PR sem run, ou o head do PR mudou (push novo)
#
# Variáveis: CI_LIMITE (segundos, padrão 2400 = ~4x o CI normal de ~10 min),
#            CI_INTERVALO (padrão 30), CI_FANTASMA (carência em segundos, padrão 600).
# Nunca corta a saída do gh; `gh pr checks` com código 8 é "pendente", não falha.
set -u

pr="${1:-}"
[[ "$pr" =~ ^[0-9]+$ ]] || { echo "uso: $0 <número-do-PR>" >&2; exit 5; }
limite="${CI_LIMITE:-2400}"; intervalo="${CI_INTERVALO:-30}"; carencia="${CI_FANTASMA:-600}"

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

sha=$(gh pr view "$pr" --json headRefOid --jq .headRefOid) || exit 5
inicio=$SECONDS
run=""

# Jobs não concluídos cujos passos já terminaram há mais de $carencia segundos.
fantasmas() {
  gh run view "$run" --json jobs --jq "[.jobs[] | select(.status != \"completed\")
    | select((.steps | length) > 0 and all(.steps[]; .status == \"completed\"))
    | select(now - ([.steps[].completedAt | select(. != null) | fromdateiso8601] | max) > $carencia)
    | \"\(.databaseId) \(.name)\"] | .[]"
}

while :; do
  [ "$(gh pr view "$pr" --json headRefOid --jq .headRefOid)" = "$sha" ] \
    || { echo "o head do PR #$pr mudou; reiniciar a espera"; exit 5; }

  # Run mais recente do CI para este commit; a tentativa certa é a última do run (gh view já a devolve).
  [ -n "$run" ] || run=$(gh run list --workflow CI --commit "$sha" --json databaseId,createdAt \
    --jq 'sort_by(.createdAt) | last | .databaseId // empty')

  if [ -n "$run" ]; then
    estado=$(gh run view "$run" --json status --jq .status) || exit 5
    if [ "$estado" = completed ]; then
      gh pr checks "$pr"; code=$?
      case $code in
        0) exit 0 ;;
        8) f=$(fantasmas) && [ -n "$f" ] && { echo "run $run concluído, mas há job fantasma:"; echo "$f"; exit 2; } ;;  # pendente: seguir esperando
        *) echo "run $run: ver gh run view $run --log-failed"; exit 1 ;;
      esac
    else
      f=$(fantasmas) && [ -n "$f" ] && { echo "job fantasma no run $run:"; echo "$f"; exit 2; }
    fi
  fi

  if (( SECONDS - inicio >= limite )); then
    echo "limite de ${limite}s estourado (run ${run:-nenhum})"
    [ -n "$run" ] && f=$(fantasmas) && [ -n "$f" ] && { echo "job fantasma:"; echo "$f"; exit 2; }
    exit 3
  fi
  sleep "$intervalo"
done
