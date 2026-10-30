# 439 — Prévia da geração fica em branco com um motivo sem chave de tradução

Origem: issue #439 (Sprint 10.5 · Identidade visual; auditoria B14 / F1-03). Branch: `item/439-previa-geracao-motivo-traducao`.

## 1. Objetivo

Fazer a Prévia da geração (`/cobrancas/previa`) nunca quebrar por um motivo de "não será cobrado" desconhecido: todo motivo do canvas tem texto em pt-BR e um motivo novo cai em "Não será cobrado". Para a gestão (RF-COB-03).

Causa achada no código: `ChargePreviewRows.tsx` já passa `defaultValue` ao `t`, mas o `parseMissingKeyHandler` de `frontend/src/i18n/index.ts` (modo estrito fora de produção) é chamado para a chave ausente e lança o erro antes de o `defaultValue` valer. Em produção a tela mostraria a própria chave. O reparo é não pedir a chave que não existe.

Fora: o servidor passar a emitir os motivos novos (backend); data "até 12/2026" na bolsa (ver D2); mudar o modo estrito do i18n.

## 2. Requisitos

- R1: um `reason` sem chave de tradução mostra "Não será cobrado" na linha, sem erro e sem tela em branco.
- R2: os motivos do canvas têm o texto do canvas: "Bolsa de 100%" (`FULL_BENEFIT`), "Modalidade bloqueada por atraso", "Encerramento solicitado", "Modalidade encerrada" (D1).
- R3: o `reason` da prévia no `docs/openapi.yaml` é um texto aberto, com os valores conhecidos documentados; valor novo é aceito.
- R4: o MSW continua batendo com o contrato (D3).

## 3. Critérios de aceite

- CA1 (R1): com `reason: "X"`, a página abre e a linha do aluno traz "Não será cobrado".
- CA2 (R2): com cada um dos quatro motivos conhecidos, a linha traz o texto do canvas.
- CA3 (R2): `FULL_BENEFIT` segue mostrando "Bolsa de 100%" (teste existente).
- CA4 (R3, R4): `ContractRoutesTest`/`OpenApiContractTest` passam e o teste do MSW da prévia (`chargePreview.msw.test.ts`) passa.
- CA5: os testes novos (CA1, CA2) falham sem a correção de `ChargePreviewRows.tsx`.
- CA6: lint, tipos e testes do front passam; axe e quatro estados da tela não mudam (nenhum layout alterado).

## 4. Plano técnico

- Dados, API (código), segurança: não mudam. Só o contrato em `docs/openapi.yaml` (R3).
- Interface:
  - `frontend/src/features/charges/ChargePreviewRows.tsx`: no `<li>` de `skipped`, calcular a chave com `i18n.exists(\`charges.preview.reasons.${s.reason}\`)` (via `useTranslation()` → `i18n`), senão `charges.preview.reasons.OTHER`; remover o `defaultValue`.
  - `frontend/src/i18n/pt-BR.json`, `charges.preview.reasons`: acrescentar `OVERDUE_BLOCK`, `END_REQUESTED`, `MODALITY_ENDED`; trocar `OTHER` para "Não será cobrado".
  - `frontend/src/features/charges/types.ts`: `reason: string` já é aberto; não muda.
- Contrato: `docs/openapi.yaml:4339` troca `enum: [FULL_BENEFIT]` por `type: string` com `description` listando `FULL_BENEFIT`, `OVERDUE_BLOCK`, `END_REQUESTED`, `MODALITY_ENDED` e dizendo que valor novo deve ser tratado como "Não será cobrado"; ajustar a frase da linha 1173 (`quem não será cobrado (\`FULL_BENEFIT\`)`).
- MSW: `frontend/src/mocks/handlers.ts:4060` só emite `FULL_BENEFIT`, que continua válido; sem mudança.
- Testes a alterar: `frontend/src/features/charges/ChargePreviewPage.test.tsx` (usa `setup`/`data` existentes; casos com `reason: "X"` e com cada motivo do canvas).
- Riscos: o backend (`MonthlyGeneration.java`) usa o enum? Se houver enum Java ou teste de contrato que reprove `string`, ajustar só o contrato; conferir com CA4.

## 5. Tarefas

- [x] T1: escrever em `ChargePreviewPage.test.tsx` os testes de motivo inventado e dos quatro motivos do canvas (devem falhar antes da correção) — cobre CA1, CA2, CA5 — prova: `cd frontend && npx vitest run src/features/charges/ChargePreviewPage.test.tsx`.
- [x] T2: corrigir `ChargePreviewRows.tsx` e cadastrar os textos em `pt-BR.json` — cobre CA1, CA2, CA3 — prova: mesmo comando da T1.
- [x] T3: abrir o `reason` no `docs/openapi.yaml` e conferir o MSW — cobre CA4 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `cd frontend && npx vitest run src/mocks/chargePreview.msw.test.ts`.
- [x] T4: `npm run lint && npm run typecheck`; conferir à mão com o MSW devolvendo `reason: "X"` — cobre CA6 — prova: `cd frontend && npm run lint && npm run typecheck && npx vitest run src/features/charges`.

## 6. Decisões

- D1: chaves novas `OVERDUE_BLOCK`, `END_REQUESTED`, `MODALITY_ENDED` e textos iguais aos do canvas. Motivo: a issue pede os textos do canvas e o contrato não define os códigos; nomes em inglês, no padrão de `FULL_BENEFIT`. Alternativa: outros nomes; só mudam quando o backend emitir.
- D2: "Bolsa de 100%" fica sem o "até 12/2026" do canvas (suposição). Motivo: o contrato da linha não traz a data de fim da bolsa; incluí-la exige mudar API, o que a issue não pede. Se o dono quiser a data, vira item novo (backend + contrato + tela).
- D3: o `reason` aberto não muda o formato da resposta; nenhum handler MSW precisa de alteração, só a conferência.

## Construção

- T1 — arquivos: `frontend/src/features/charges/ChargePreviewPage.test.tsx` — prova: `npx vitest run src/features/charges/ChargePreviewPage.test.tsx` antes da correção → 1 falha (chave ausente `charges.preview.reasons.X`), como esperado
- T2 — arquivos: `frontend/src/features/charges/ChargePreviewRows.tsx`, `frontend/src/i18n/pt-BR.json` — prova: mesmo comando → passa (CA3 coberto pelo teste existente)
- T3 — arquivos: `docs/openapi.yaml` (`reason` aberto com descrição; frase da rota ajustada) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes, BUILD SUCCESS; `npx vitest run src/mocks/chargePreview.msw.test.ts` → passa; MSW sem mudança
- T4 — prova: `npm run lint` e `npm run typecheck` → 0; `npx vitest run src/features/charges src/mocks/chargePreview.msw.test.ts` → 9 arquivos, 91 testes passam. Conferência manual no navegador não feita; coberta pelo teste com `reason: "X"`.
Regressão: não rodada completa (`verify`, `test:cov`, `e2e`); só as provas acima, mudança restrita a uma linha de texto, um contrato e um teste.
Desvios: nenhum.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-30, estado ec01f52d5689ef0dca46330e100c92a9e6f1c58d

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | teste novo com `reason: "X"` em ChargePreviewPage.test.tsx passa (vitest, 91 testes) |
| CA2 | PASSOU | mesmo teste cobre OVERDUE_BLOCK, END_REQUESTED, MODALITY_ENDED; textos em pt-BR.json batem com o canvas descrito |
| CA3 | PASSOU | teste existente de FULL_BENEFIT passa |
| CA4 | PASSOU | OpenApiContractTest + ContractRoutesTest: 7 testes, BUILD SUCCESS; chargePreview.msw.test.ts passa |
| CA5 | PASSOU | pelo registro da Construção (falha antes da correção) e pela leitura do diff: o código antigo pedia a chave ausente; não reexecutado sem a correção para não tocar nos arquivos |
| CA6 | PASSOU | npm run lint e typecheck sem erro; src/features/charges passa; nenhum layout alterado |

Comandos: vitest src/features/charges + chargePreview.msw.test.ts (9 arquivos, 91 passam); npm run lint (0); npm run typecheck (0); mvnw test -Dtest='OpenApiContractTest,ContractRoutesTest' (7 passam). Regressão completa (verify, test:cov, e2e) não rodada; mudança restrita a um componente, texto, contrato e teste.
Instáveis: nenhum
Achados:
- sugestão, código, ChargePreviewPage.test.tsx — CA1 e CA2 ficam num único teste; separar daria falha mais localizada.

## Entrega

- O que muda: a Prévia da geração (`/cobrancas/previa`) não quebra mais com um motivo de "não será cobrado" sem chave de tradução; a linha mostra "Não será cobrado". Os motivos do canvas (`FULL_BENEFIT`, `OVERDUE_BLOCK`, `END_REQUESTED`, `MODALITY_ENDED`) têm texto em pt-BR. O `reason` da prévia no contrato passa a ser texto aberto, com os valores conhecidos documentados.
- Como foi verificado: testes de motivo inventado e dos quatro motivos do canvas em `ChargePreviewPage.test.tsx`; contrato (`OpenApiContractTest`, `ContractRoutesTest`) e MSW (`chargePreview.msw.test.ts`); lint, tipos e testes de `src/features/charges`. Verificação de 30/10/2026, rodada 1, OK.
- Decisões: D1 (chaves novas em inglês com textos do canvas) e D2 (sem a data "até 12/2026" na bolsa) estão na seção 6.
- Limitações: a regressão completa (`verify`, `test:cov`, `e2e`) não foi rodada localmente; fica para as verificações do PR. Os motivos novos só aparecem quando o backend passar a emiti-los.
