# 320 — Teste e2e de recuperação de senha instável: medir e estabilizar

Origem: issue #320 (front-end, Sprint 08). Branch: `item/320-recuperar-senha-e2e-instavel`. Mesmo formato da #318.

## 1. Objetivo

Medir se `@critical Recuperação de senha` (`frontend/e2e/recuperar-senha.spec.ts`) falha de forma intermitente e, se falhar, corrigir a causa sem mascarar. Se não falhar, fechar a issue com o resultado.

Fora do escopo: outros testes e2e, a suíte inteira em laço, mudança de comportamento da tela.

## 2. Requisitos

- R1: a instabilidade é medida antes de qualquer correção, só com Playwright, no teste afetado, `--repeat-each=20 --workers=1`, e o resultado (falhas/execuções) é registrado (D1).
- R2: se houver falha, a causa é achada (trace/erro) e corrigida na raiz; nenhum retry nem aumento de timeout sem registro na descrição do PR.
- R3: se houver 0 falhas, o código não muda e a issue fecha com os números (D2).
- R4: depois de uma correção, o teste passa 20 de 20 nas mesmas condições.

## 3. Critérios de aceite

- CA1 (R1): dado o teste de recuperação de senha, quando rodado com `--repeat-each=20 --workers=1 --retries=0`, então o PR e a issue trazem falhas/execuções e a linha da falha, se houver.
- CA2 (R2, R4): dado uma falha medida, quando a causa é corrigida, então 20 de 20 passam, sem retry e sem timeout maior.
- CA3 (R3): dado 0 falhas, quando a medição termina, então nenhum arquivo de código muda e a issue recebe os números e fecha.

## 4. Plano técnico

- Dados, API, segurança: não mudam. Interface: não muda.
- Medição feita na fase 2: `npx playwright test e2e/recuperar-senha.spec.ts --repeat-each=20 --workers=1 --retries=0`: **0 falhas em 20** (20 passed, 2,0 min, máquina local sem carga extra).
- Fluxo lido (para a investigação, se a falha aparecer): `ForgotPasswordPage.tsx` (`submit` → `api("/auth/password-reset")` → `setDone` exibe `<p role="status">`); handler MSW `/auth/password-reset` em `frontend/src/mocks/handlers.ts` (~1330) responde na hora; `main.tsx` espera `worker.start()` antes de renderizar, então não há corrida de MSW na primeira carga. Falha vista no CI: mensagem não visível em 5000 ms; no CI `retries: 1` e o `webServer` sobe `npm run dev` frio (primeira carga do Vite com otimização de dependências pode recarregar a página e perder o formulário preenchido).
- Suspeitas (não são fato): recarga do Vite no meio do teste por dependência otimizada tardiamente; `Turnstile` ou `SessionProvider` remontando a tela após o preenchimento; CI mais lento que os 5000 ms padrão do `expect`.
- Arquivos: `frontend/e2e/recuperar-senha.spec.ts` (só se a causa estiver na espera) ou o arquivo que a causa apontar. Nenhum a criar.
- Riscos: falha que só ocorre no CI e não reproduz local; neste caso o resultado é registrado como 0/20 e o item fecha (D2), ficando a observação do próximo run do CI.

## 5. Tarefas

- [x] T1: medir — cobre CA1 — prova: `cd frontend && npx playwright test e2e/recuperar-senha.spec.ts --repeat-each=20 --workers=1 --retries=0` (repetir na construção; registrar falhas/execuções)
- [x] T2: se T1 falhar, ler o trace/erro, achar a causa e corrigir na raiz (esperar o estado final, não o instante) — cobre CA2 — prova: o mesmo comando de T1, 20 de 20
- [ ] T3: registrar o resultado na descrição do PR (`Closes #320`) e comentar na issue os números — cobre CA3 se T1 deu 0 — prova: conferência da descrição do PR

## 6. Decisões

- D1: medição só com Playwright no teste afetado, `--workers=1`, nunca a suíte inteira em laço (pedido do dono). Alternativa descartada: `npm run e2e` em laço.
- D2: se T1 der 0 falhas, fecha-se com o resultado, sem alterar código, como a própria issue manda. Alternativa descartada: aumentar timeout ou ligar retry "por precaução", que é mascarar.
- Suposição: a falha original foi do CI (servidor de dev frio). Se estiver errada e for local, o dono reabre com o log; se a medição falhar na construção, T2 trata.

## Construção

- T1 — arquivos: nenhum — prova: `cd frontend && npx playwright test e2e/recuperar-senha.spec.ts --repeat-each=20 --workers=1 --retries=0` → 20 passed, 0 falhas em 20 (1,9 min)
- T2 — não aplicável: T1 deu 0 falhas, código inalterado (D2).
- T3 — fica por marcar: é da fase 5 (descrição do PR e comentário na issue com 0/20).
Regressão: não rodada, nenhum código mudou (CA3).
Desvios: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-22, estado 32bdc0110d83948c803a1fbf1644a0fa08ec53e0

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `cd frontend && npx playwright test e2e/recuperar-senha.spec.ts --repeat-each=20 --workers=1 --retries=0` → 20 passed, 0 falhas em 20 (1,8 min); confere com a medição da construção (0/20) |
| CA2 | NÃO CONFERIDO (não se aplica) | não houve falha medida; sem correção a conferir |
| CA3 | PASSOU | `git status` e diff contra a base: único arquivo novo é `specs/itens/320-recuperar-senha-e2e-instavel.md`; nenhum arquivo de código mudou. Números 0/20 a registrar na issue e no PR (T3, fase 5) |

Comandos: o de CA1 acima (20 passed); `git status --short` (só o arquivo do item).
Regressão: não rodada, nenhum código mudou.
Instáveis: nenhum em 20 execuções.
Achados:
- sugestão, processo — T3 (PR e comentário na issue com 0/20) fica para a fase 5; a falha original pode ser só do CI com servidor de dev frio, a observar no próximo run.

## Entrega

- **O que muda:** só o arquivo do item e a linha 320 do backlog. Nenhum arquivo de código, teste ou especificação de produto muda.
- **Medição:** `@critical Recuperação de senha` (`frontend/e2e/recuperar-senha.spec.ts`) com `npx playwright test e2e/recuperar-senha.spec.ts --repeat-each=20 --workers=1 --retries=0`: 0 falhas em 20 execuções (20 passed, 1,8 a 2,0 min).
- **Causa:** não se aplica; sem falha medida, nada foi corrigido. Sem retry, sem timeout maior, sem mudança de `expect`.
- **Verificação:** rodada 1 OK (estado 32bdc0110d83948c803a1fbf1644a0fa08ec53e0).
- **Limitação:** a falha original foi vista só no CI (servidor de dev frio, `retries: 1`), e não reproduziu local nem na medição. Se voltar no CI, o log do próximo run é a fonte; a suposição de causa segue na seção 4 como hipótese, não fato.
