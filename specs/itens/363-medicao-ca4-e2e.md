# 363 — Fechar a medição do CA4 da #343: tempo do job e2e em mais 4 PRs

Origem: issue #363 (Sprint 10 · Portal do aluno), CA4 da #343 (`specs/itens/343-cache-playwright-ci.md`, seção "Medição do CA4"). Branch: `item/363-medicao-ca4-e2e`.

## 1. Objetivo

Registrar o tempo do job `e2e` em mais 4 PRs que rodaram o `e2e`, para fechar o CA4 da #343 (5 de 5, com a #361) e dizer se o cache por imagem do Playwright cumpre os limites. Só documentação, sem código nem CI.

Fora do escopo: alterar o `ci.yml`, os testes e2e ou o cache; implementar a alternativa da D1 da #343 (só entra se a medição reprovar, como novo item).

## 2. Requisitos

- R1: a seção "Medição do CA4" de `specs/itens/343-cache-playwright-ci.md` ganha 4 linhas de PRs que rodaram o `e2e` após a #361, com run, "Inicializar containers", conferência de versão, `npm run e2e`, job `e2e` inteiro e resultado do CI, lidos de `gh run view <id> --json jobs` (startedAt/completedAt por passo) (D1).
- R2: PR com o `e2e` `skipped` não conta e não entra como medição (D1).
- R3: cada PR é avaliado contra os limites: "Inicializar containers" + conferência de versão abaixo de 60 s e `npm run e2e` abaixo de 7 min (job inteiro, `timeout-minutes: 15`).
- R4: a seção fecha com o resultado final (5 de 5, aprovado ou reprovado); se algum PR estourar, o resultado diz qual e aponta a alternativa da D1 da #343 (cache em `push` para `dev`), sem implementá-la (D2).

## 3. Critérios de aceite

- CA1 (R1, R2): dado a seção atualizada, então há 4 linhas novas de PRs com `e2e` executado (conclusão `success`), cada uma com run e os cinco tempos preenchidos, e nenhuma linha de PR com `e2e` skipped é contada.
- CA2 (R3): dado cada linha, então o veredito por PR (passou/reprovou) bate com os limites e com os tempos da própria linha.
- CA3 (R4): dado a seção atualizada, então "Medidos: 5 de 5" e uma frase de resultado final existem; se algum PR reprovou, o arquivo cita a alternativa da D1 e o resultado informa ao dono.
- CA4 (R1): dado o texto do CA4 da #343 e o resultado final, então o item 343 diz "5 de 5 medidos" sem alterar as seções fechadas (Verificação, Entrega).

## 4. Plano técnico

- Dados, API, interface, segurança: não mudam.
- Arquivos: `specs/itens/343-cache-playwright-ci.md` (visto), seção "Medição do CA4": acrescentar linhas à tabela e a frase de fechamento. Este arquivo recebe a Construção/Verificação/Entrega nas fases seguintes.
- Candidatos já existentes (vistos via `gh pr view`, e2e SUCCESS, CI verde): #381 (run 40370522195), #382 (40384879996), #383 (40414792380), #384 (40430676426); também #387 (40437848140) e #389 (40446890538), de reserva se algum run não puder ser lido.
- Comando de leitura: `gh run view <run> --json jobs --jq '.jobs[] | select(.name=="e2e") | .steps[] | [.name,.startedAt,.completedAt]'`; duração = completedAt - startedAt.
- Riscos: logs de runs antigos expirarem (retenção do GitHub) e `gh run view` não trazer os passos; nesse caso usar o próximo PR da lista.

## 5. Tarefas

- [x] T1: ler os passos "Initialize containers", conferência de versão e `npm run e2e` e o job inteiro dos runs #381, #382, #383 e #384, e calcular as durações — cobre CA1, CA2 — prova: `gh run view 40370522195 --json jobs` (e os outros três) com durações conferidas.
- [x] T2: registrar as 4 linhas, o veredito por PR e o fechamento "5 de 5" em `specs/itens/343-cache-playwright-ci.md`, e a frase de resultado — cobre CA2, CA3, CA4 — prova: `grep -n "5 de 5" specs/itens/343-cache-playwright-ci.md` e leitura da tabela.

## 6. Decisões

- D1: contam os 4 primeiros PRs, após a #361, com o job `e2e` executado e verde, em ordem de merge: #381, #382, #383, #384. Motivo: a issue pede "os próximos" que rodem o `e2e`, e esses já existem, então não há espera. Alternativa: esperar novos PRs (atrasaria sem ganho); #387 e #389 ficam de reserva.
- D2: se algum PR reprovar, este item registra o estouro e o resultado "reprovado", e a alternativa da D1 da #343 vira item novo; não é implementada aqui. Motivo: a issue diz que o estouro faz esta issue virar prioridade, não que ela implemente a troca.
- Suposição: o run vinculado ao PR é o último da branch (o do `ci-gate` verde); se houver mais de um run, vale o que gerou o `ci-gate` verde. Se os passos não estiverem disponíveis, usar o próximo PR de reserva.

## Construção

- T1 — arquivos: nenhum (leitura) — prova: `gh run view <run> --json jobs` nos runs 40370522195, 40384879996, 40414792380, 40430676426 → job `e2e` success em todos; container 26/27/27/37 s, conferência 0 s, `npm run e2e` 57/62/69/64 s, job 105/110/111/118 s.
- T2 — arquivos: `specs/itens/343-cache-playwright-ci.md` — prova: `grep -n "5 de 5" specs/itens/343-cache-playwright-ci.md` → linhas de "Medidos: 5 de 5" e "Resultado final: 5 de 5 medidos, aprovado"; 4 linhas novas na tabela, todas dentro dos limites.
Regressão: não rodada, só documentação (nenhum código tocado).
Desvios: nenhum.
Não é do item: `specs/backlog.md` já estava modificado antes da sessão.

## Verificação

### Rodada 1 — OK — 2026-10-27, estado 486d73346ecdfee7ab05cc901c58e469396129d3

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | 4 linhas novas (#381, #382, #383, #384) com os cinco tempos; `gh run view` mostra job `e2e` success nos 4 runs; nenhuma linha skipped contada |
| CA2 | PASSOU | tempos recalculados dos `startedAt/completedAt`: container 26/27/27/37 s, conferência 0 s, `npm run e2e` 57/62/69/64 s, job 105/110/111/118 s; todos abaixo de 60 s e 7 min |
| CA3 | PASSOU | "Medidos: 5 de 5" e "Resultado final: 5 de 5 medidos, aprovado" presentes; nenhum PR reprovou |
| CA4 | PASSOU | diff do 343 só toca "Medição do CA4"; Verificação e Entrega intactas |

Comandos: `gh run view <run> --json jobs` nos 4 runs (valores conferem); `git diff --stat` (só 343 e o arquivo do item). Regressão não rodada: só documentação.
Achados:
- sugestão, especificação, 343 "Decisão do dono" — ainda diz "1 de 5 hoje"; é registro histórico datado, aceitável.

## Entrega

Fecha o CA4 da #343: a seção "Medição do CA4" de `specs/itens/343-cache-playwright-ci.md` passa de 1 para 5 de 5 medidos, com #381, #382, #383 e #384 (todos com o job `e2e` executado e verde).

- Tempos (de `startedAt`/`completedAt` do `gh run view --json jobs`): container + conferência de 26 s a 37 s, `npm run e2e` de 57 s a 69 s, job inteiro de 105 s a 118 s. Todos dentro dos limites (60 s, 7 min e 15 min).
- Resultado: aprovado. O cache por imagem do Playwright cumpre os limites; a alternativa D1 da #343 (cache em `push` para `dev`) não é necessária.
- Só documentação: nenhum código, teste ou CI tocado. Regressão não rodada (base `dev` sem commits novos desde a verificação).
- `specs/backlog.md`: item marcado como `feito`.
