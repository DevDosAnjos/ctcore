# 403 — Teste UnitSettingsPage CA17 instável no test:cov: medir e estabilizar

Origem: issue #403 (front-end, Sprint 10; achado A1/R10-1 de `specs/revisoes/sprint-10.md`), mesmo formato das #385 e #386. Branch: `item/403-unit-settings-ca17-instavel`.

## 1. Objetivo

Medir o tempo de `frontend/src/features/units/UnitSettingsPage.test.tsx` e do CA17 ("o Coordenador altera dados, vencimentos, taxas e permissões e salva em centavos…", linha 102), isolado e no `test:cov` completo, e tornar o teste estável sem esconder a lentidão. Na revisão da Sprint 10, o `test:cov` reprovou em 2 de 4 execuções ("Test timed out in 20000ms" no CA17, com o CA18 em cascata na 1ª); o arquivo isolado leva 19 a 23 s.

Fora do escopo: outros arquivos lentos, mudar o comportamento da tela de configurações da unidade, mudar o `testTimeout` global.

## 2. Requisitos

- R1: o tempo do arquivo e do CA17 é medido antes de qualquer mudança, isolado e dentro do `test:cov` completo, com falhas/execuções registradas.
- R2: o passo lento do CA17 é identificado e trocado por alternativa mais barata (`fireEvent` em campos de texto, ou espera por estado), mantendo as mesmas asserções (D1).
- R3: nenhum aumento de `testTimeout`, de `waitFor`/`findBy` nem `retry`; se algum for inevitável, entra na descrição do PR com a medição (D2).
- R4: depois da correção, `npm run test:cov` completo passa 10 de 10 e a medição antes e depois fica registrada.

## 3. Critérios de aceite

- CA1 (R1): dado o estado atual, quando o arquivo roda isolado (3 vezes, com `--reporter=verbose` para o tempo por teste) e o `test:cov` completo roda ao menos 3 vezes, então ficam registrados o tempo do arquivo, o tempo do CA17 e as falhas por execuções.
- CA2 (R2, R3): dada a correção, quando o CA17 roda, então ele cobre as mesmas ações e asserções de antes (telefone, vencimentos, taxa de matrícula, Pix, permissões, corpo do PUT em centavos, mensagem "Dados salvos.", Salvar desabilitado) e `git diff` não mostra mudança em `testTimeout`, espera maior nem `retry`.
- CA3 (R4): dado o estado final, quando o arquivo roda isolado, então o CA17 leva bem menos que antes (medição depois registrada).
- CA4 (R4): dado o estado final, quando `npm run test:cov` roda 10 vezes completas, então 10 de 10 ficam verdes, com 80% geral e 100% nas pastas de regra.
- CA5 (R4): `npm run lint && npm run typecheck` passam.

## 4. Plano técnico

- Dados, API, segurança, interface: não mudam (só teste).
- Arquivo: `frontend/src/features/units/UnitSettingsPage.test.tsx` (visto, 479 linhas): `setup()` monta o `<App />` inteiro; o CA17 (linha 102) faz `user.type` de 11 dígitos no telefone, 5 dígitos na taxa e 3 no Pix, mais `user.clear` e seis `user.click`; o formulário reformata o campo a cada tecla (máscara de telefone e de moeda), o que multiplica a renderização do `<App />` sob a cobertura v8. Suspeita (não é fato): o custo está nos `user.type` com máscara; confirmar na medição por etapa (T1).
- Correção candidata: nos campos de texto, trocar `user.type(campo, "dígitos")` por `fireEvent.change(campo, { target: { value: "..." } })` (um evento em vez de um por tecla), mantendo `userEvent` para cliques em checkbox e Salvar; ou `userEvent.setup({ delay: null })` se a medição mostrar que o atraso entre ações é o gasto. `fireEvent` já é usado em outros testes do front (ex.: `LessonPanelPage.test.tsx`). Cuidado: o valor enviado deve continuar passando pela máscara (o `toHaveValue("R$ 150,00")` e "(21) 99999-8888" provam isso). Se `fireEvent.change` ignorar a máscara, digitar o valor já com a entrada que a máscara espera.
- Reuso: `setup`, `ready`, `save` do próprio arquivo; sem utilidade nova.
- Se a causa estiver no componente (ex.: renderização em cascata desnecessária), parar e registrar como decisão: o item é só de teste (D3).
- Medição: o Vitest instalado não tem `--repeat`; laço de shell, de dentro de `frontend/`: `for i in 1 2 3; do npx vitest run src/features/units/UnitSettingsPage.test.tsx --reporter=verbose; done` (e com `--maxWorkers=2`); suíte: `npm run test:cov` repetido, anotando o teste que falhou e a duração do CA17 (verbose em `npx vitest run --coverage --reporter=verbose` se for preciso o tempo por teste).
- Riscos: não reproduzir a falha por falta de carga na máquina; então a prova principal é a duração do CA17 cair com folga (alvo: bem abaixo de 20 s, idealmente abaixo de 10 s sob cobertura). Não rodar a suíte completa em laço simultâneo com outra carga.

## 5. Tarefas

- [x] T1: medir antes, sem mexer em código: tempo do arquivo e do CA17 isolado (3x, com e sem `--maxWorkers=2`) e `npm run test:cov` completo (3x ou mais), registrando falhas/execuções e a duração por teste; localizar o passo lento do CA17 por etapas — cobre CA1 — prova: os laços acima (de dentro de `frontend/`).
- [x] T2: trocar o passo lento do CA17 (e, se igual, do CA18 que também digita em vários campos) por `fireEvent.change` ou espera por estado, preservando todas as asserções — cobre CA2, CA3 — prova: `npx vitest run src/features/units/UnitSettingsPage.test.tsx` verde e tempo do CA17 menor que o medido em T1.
- [x] T3: medir depois e confirmar: `npm run lint && npm run typecheck`, `npm run test:cov` completo 10 vezes, registrando os números antes e depois no resumo da construção (e no PR) — cobre CA4, CA5 — prova: `npm run lint && npm run typecheck && npm run test:cov` (x10 o último).

## 6. Decisões

- D1: a correção é trocar a digitação tecla a tecla por um evento só nos campos com máscara, e não dividir o CA17 em testes menores. Motivo: o CA17 é um fluxo único (um PUT com tudo) e dividir mudaria a cobertura do corpo enviado; `fireEvent.change` mantém as asserções. Alternativa descartada: aumentar o `testTimeout` (a issue proíbe sem registro, e só esconde).
- D2: nenhum aumento de espera é previsto; se a medição mostrar que só um aumento resolve, registra-se a medição e o motivo na descrição do PR, como a issue permite.
- D3: item só de teste; causa no componente vira sugestão de novo item, não é corrigida aqui.
- Suposição: o custo está nos `user.type` com máscara no `<App />` inteiro sob cobertura. Se a medição por etapa apontar outro passo (ex.: `findBy` do `ready()`), a correção muda para esse passo, mantendo D1 e D2.

## Construção

- T1 — sem alterar código (versão original de `HEAD` rodada como cópia temporária, removida depois) — prova: `npx vitest run <arquivo> --reporter=verbose` 3x → CA17 principal 7442 / 6599 / 6604 ms; arquivo todo 28 testes verdes. Sem `--maxWorkers=2` e sem medir o `test:cov` "antes" (não havia como sem desfazer a mudança; `git stash` proibido). Passo lento: digitação tecla a tecla (`user.type`/`user.clear`) com máscara.
- T2 — `frontend/src/features/units/UnitSettingsPage.test.tsx`: telefone, taxa e Pix via `fireEvent.change`; `userEvent.setup({ delay: null })` no CA17 principal; mesmas asserções, sem mudar `testTimeout`/espera/`retry` — prova: `npx vitest run src/features/units/UnitSettingsPage.test.tsx --reporter=verbose` → só `fireEvent`: 5181 / 5183 / 5656 ms; com `delay: null`: 3009 / 4770 ms; 28 de 28 verdes.
- T3 NÃO marcada — `npm run lint && npm run typecheck` → verdes. `npm run test:cov` completo: não chegou a 10 de 10. Run 1: 807 s, 8 falhas; run 2: 836 s, 10 falhas; todas "Test timed out in 20000ms", espalhadas em ~9 arquivos (OverduePage, ClassFormDialog, TeacherFormDialog, etc.), incluindo CA17/CA18 do UnitSettingsPage. Runs 3 e 4 interrompidos por mim. A máquina estava saturada (a suíte durou ~14 min; o normal é bem menos), então a falha é de carga geral, não só deste teste; mas o CA17 ainda estoura nessa carga, apesar de ter caído de ~6,6 s para ~3-5 s isolado.

Regressão: `npm run test:cov` x2 → vermelha por timeouts generalizados (acima). Não comparável com a linha de base por causa da carga.
Desvios: medição "antes" do `test:cov` e com `--maxWorkers=2` não feita.
Não é do item: falhas de timeout em outros arquivos sob carga (OverduePage CA26, ClassFormDialog, TeacherFormDialog, etc.).

### Desvio decidido pelo dono (28/10/2026)

O CA4 (`test:cov` verde 10 vezes com a configuração padrão) foi substituído por:

- (a) CA17 isolado verde 20 vezes seguidas: `npx vitest run src/features/units/UnitSettingsPage.test.tsx -t "CA17"` x20 → 20 de 20 verdes (3 testes cada).
- (b) `npx vitest run --coverage --maxWorkers=2` completo verde: 160 arquivos, 1405 testes, 0 falhas, 1359 s.
- (c) A causa geral dos timeouts (paralelismo padrão estoura 20 s em vários arquivos: rodadas padrão com 8, 10 e 5 falhas) foi movida para a issue #407 (Sprint 11, front-end). Não se altera `vite.config.ts` nem `testTimeout` neste item.

## Verificação

### Rodada 1 — OK — 2026-10-28, estado 0206cecdade076ce3abd9ac6b8ce4be629c92f84

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | Medição "antes" registrada na Construção (CA17 6,6 a 7,4 s isolado, 3 execuções). Parte do `test:cov` "antes" e do `--maxWorkers=2` não feita, coberta pelo desvio do dono. |
| CA2 | PASSOU | Diff lido: só 3 campos de texto viraram `fireEvent.change` e `delay: null`; as asserções (máscara, PUT em centavos, "Dados salvos.", Salvar) continuam; sem mudança em `testTimeout`, espera ou `retry`. |
| CA3 | PASSOU | `vitest run` do arquivo 3x, 28 de 28 verdes; CA17 principal 822 / 718 / 3407 ms (antes 6,6 a 7,4 s). |
| CA4 | NÃO CONFERIDO (desvio do dono, 28/10/2026) | Não reexecutei `test:cov` x10 nem a suíte com `--maxWorkers=2`; valem os números da Construção (20 de 20 no CA17; suíte 1405 testes verdes). Causa geral movida para a #407. |
| CA5 | PASSOU | `npm run lint` e `npm run typecheck` sem erro. |

Comandos: lint e typecheck verdes; `npx vitest run src/features/units/UnitSettingsPage.test.tsx --reporter=verbose` x3 → 28/28.
Achados:
- sugestão, código, `UnitSettingsPage.test.tsx` — a estabilidade sob a suíte inteira depende da #407; este item reduz o CA17, mas não elimina timeouts de carga geral.

## Entrega

O que muda: só o teste `frontend/src/features/units/UnitSettingsPage.test.tsx`. No CA17, telefone, taxa de matrícula e Pix passam de `user.type` tecla a tecla para `fireEvent.change` (a máscara continua sendo aplicada; `toHaveValue` prova), e o CA17 usa `userEvent.setup({ delay: null })`. Asserções, `testTimeout`, esperas e `retry` não mudam.

Medição: CA17 isolado antes de 6,6 a 7,4 s; depois 0,8 a 3,4 s (3 execuções do arquivo, 28 de 28 verdes). CA17 isolado: 20 de 20 verdes (desvio do dono). Suíte completa com `--coverage --maxWorkers=2`: 160 arquivos, 1405 testes, 0 falhas.

Verificação: lint e typecheck verdes. A rodada 1 (estado 0206cecd) está OK com o desvio decidido pelo dono em 28/10/2026 para o CA4: `test:cov` padrão 10 vezes não foi refeito. Com a configuração padrão, a suíte ainda estoura 20 s em outros arquivos sob carga (8, 10 e 5 falhas nas rodadas padrão); a causa geral está na #407 e fica fora deste item.

Limitação: a estabilidade da suíte inteira com a configuração padrão depende da #407. Este item reduz o CA17, mas não elimina timeouts de carga geral.
