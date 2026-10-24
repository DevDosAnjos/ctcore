# 344 — aguardar-ci.sh: resultado recuperável de um watcher órfão

Origem: issue #344 (Sprint 09), achado R08-9 da revisão da Sprint 08. Branch: `item/344-aguardar-ci-orfao`.

## 1. Objetivo

Quando o `aguardar-ci.sh` perde o terminal (no #327 o CI levou 12 min 56 s, acima dos 10 min de um comando em primeiro plano), o desfecho dele passa a ficar em arquivo, e o código 4 diz como ler esse desfecho e como encerrar o processo. Fora do escopo: mudar a lógica de trava, de fantasmas ou os códigos de saída 0 a 6.

## 2. Requisitos

- R1: ao sair (trap `EXIT`), o watcher grava em `${TMPDIR:-/tmp}/aguardar-ci-pr<N>.resultado` o código de saída e a última linha do desfecho (D1).
- R2: no código 4, a saída traz o pid do watcher vivo, o caminho do arquivo de resultado e o comando `kill <pid>`.
- R3: o cabeçalho do script diz que ele nunca roda em primeiro plano.
- R4: os códigos de saída 0 a 6 e seus significados não mudam; o watcher que sai com 4 não grava nem apaga o resultado do outro.

## 3. Critérios de aceite

- CA1 (R2, R4): dado um watcher rodando para o PR, quando uma segunda chamada é feita, então ela sai com 4 e imprime pid, caminho do resultado e `kill <pid>`.
- CA2 (R1): dado o fim da primeira chamada, então o arquivo existe e traz o código de saída e a mensagem (por exemplo `3` e "limite de ...s estourado").
- CA3 (R1): dado um fim sem mensagem (código 0), então o arquivo traz `0` e uma linha de texto "passou".
- CA4 (R4): dado o `gh` simulado, os códigos 0, 1, 2, 3, 5 e 6 seguem saindo como antes.
- CA5 (R3): o cabeçalho contém a frase de que nunca roda em primeiro plano.

## 4. Plano técnico

- Dados, API, interface: não mudam.
- `infra/scripts/aguardar-ci.sh` (visto): trocar o `trap 'rm -rf "$trava"' EXIT` (linha do trap) por uma função `fim` que guarda `$?`, grava `"$code <última linha>"` em `$resultado` e remove a trava. Para a última linha, uma variável `ultima` atualizada pela função `dizer()`, que substitui os `echo` de desfecho (as mensagens de `exit 1/2/3/5/6`); sem mensagem, `ultima` sai de um mapa curto por código (0 "passou"). No bloco do código 4, imprimir `pid`, `$resultado` e `kill $pid`. Apagar o resultado antigo logo depois de obter a trava (depois do bloco do código 4). Cabeçalho: nova linha "Nunca roda em primeiro plano".
- `infra/scripts/aguardar-ci.test.sh` a criar, no padrão de `changed-areas.test.sh`: `gh` falso num diretório temporário no `PATH`, `TMPDIR` próprio, `CI_INTERVALO=1`, `CI_LIMITE` curto. Cobre CA1 a CA4.
- Riscos: o `trap` não roda em `kill -9`; aceito (D2).

## 5. Tarefas

- [x] T1: criar `infra/scripts/aguardar-ci.test.sh` com `gh` falso (casos 0, 1, 3, 4 e arquivo de resultado) — cobre CA1 a CA4 — prova: `bash infra/scripts/aguardar-ci.test.sh` (falha antes de T2).
- [x] T2: editar `aguardar-ci.sh` (função `fim`, `dizer`, bloco do 4, cabeçalho) — cobre CA1 a CA5 — prova: `bash infra/scripts/aguardar-ci.test.sh` e `bash -n infra/scripts/aguardar-ci.sh`.
- [x] T3: conferir a menção ao script em `AGENTS.md`/`README.md`, se houver, e acrescentar o arquivo de resultado — cobre R3 — prova: `grep -rn "aguardar-ci" AGENTS.md README.md`.

## 6. Decisões

- D1: formato do arquivo: uma linha `<código> <mensagem>`. Motivo: simples de ler com `cat`. Alternativa: duas linhas ou JSON.
- D2: `kill -9` não grava resultado. Motivo: nenhum shell consegue; o pid e a trava velha já são tratados na próxima chamada.
- D3: o resultado antigo é apagado quando um novo watcher começa, para não confundir resultado velho com o do run atual.

## Construção

- T1 — arquivos: `infra/scripts/aguardar-ci.test.sh` (novo; casos 0, 1, 2, 3, 5, 6 e 4 com resultado) — prova: `bash infra/scripts/aguardar-ci.test.sh` antes de T2 → falhou (sem arquivo de resultado), como previsto.
- T2 — arquivos: `infra/scripts/aguardar-ci.sh` (cabeçalho, `resultado`, `dizer`, `fim`, bloco do 4) — prova: `bash -n infra/scripts/aguardar-ci.sh` ok; `bash infra/scripts/aguardar-ci.test.sh` → 7 casos ok.
- T3 — `grep -rn "aguardar-ci" AGENTS.md README.md` → sem menção; nada a acrescentar.
Regressão: item só de script; `verify`/`test:cov`/`e2e` não rodados, pois nenhum código de back-end ou front-end mudou.
Desvios: o teste cobre também os códigos 2 e 5 (além dos pedidos), sem custo extra.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-24, estado d464d7e08c1e928727308fb9e8a4c320515e5718

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | caso "já há watcher (4)" do teste: sai 4 e imprime pid, caminho e `kill <pid>`; resultado não gravado pelo segundo |
| CA2 | PASSOU | caso "tempo (3)": arquivo `3 limite de 2s estourado ...` |
| CA3 | PASSOU | caso "passou (0)": arquivo `0 passou` |
| CA4 | PASSOU | casos 0, 1, 2, 3, 5, 6 do teste com `gh` falso, códigos inalterados no diff |
| CA5 | PASSOU | cabeçalho do script: "Nunca roda em primeiro plano" |

Comandos: `bash -n infra/scripts/aguardar-ci.sh` ok; `bash infra/scripts/aguardar-ci.test.sh` 7 casos ok. Regressão de back-end/front-end não rodada: o diff só toca scripts em infra/ e o arquivo do item.
Achados:
- sugestão, código, aguardar-ci.sh, `fim` — no código 6 e no 3 com fantasma, a "última linha" gravada é a mensagem inicial, não a lista de jobs; aceitável pelo D1.

## Entrega

- O que muda: `infra/scripts/aguardar-ci.sh` grava o desfecho em `${TMPDIR:-/tmp}/aguardar-ci-pr<N>.resultado` (uma linha: código e mensagem, ou `0 passou`); o código 4 imprime pid, caminho do resultado e `kill <pid>`, sem mexer no resultado do outro watcher; o resultado antigo é apagado quando um watcher novo ganha a trava; o cabeçalho diz que ele nunca roda em primeiro plano. Códigos de saída 0 a 6 inalterados.
- Teste novo: `infra/scripts/aguardar-ci.test.sh` (`gh` falso, 7 casos: 0, 1, 2, 3, 4, 5, 6).
- Verificação: rodada 1 OK (CA1 a CA5); `bash -n` e o teste passam.
- Regressão: só script de infra; `verify`, `test:cov` e `e2e` não rodados, pois nenhum código de back-end ou front-end mudou.
- Decisões: D1 (formato de uma linha), D2 (`kill -9` não grava resultado), D3 (resultado antigo apagado no início de um watcher novo).
- Limitação: se o processo for morto com `kill -9`, não há arquivo de resultado; a próxima chamada trata a trava velha.
