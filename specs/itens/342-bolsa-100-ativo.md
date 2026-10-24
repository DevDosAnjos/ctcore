# 342 — Afirmar que o aluno com bolsa de 100% continua Ativo

Origem: issue #342 (Sprint 09), achado R08-7 da revisão da Sprint 08; "Pronto quando" do marco da Sprint 08 ("aluno com bolsa de 100% não recebe mensalidade e continua Ativo"). Branch: `item/342-bolsa-100-ativo`.

## 1. Objetivo

Os testes da bolsa de 100% só provam que não há mensalidade. Passam a provar também que a modalidade e o aluno seguem ativos. Só testes; nenhum código de produto muda.

Fora do escopo: qualquer mudança de regra ou de código de produção.

## 2. Requisitos

- R1: depois da geração mensal, a modalidade com bolsa de 100% continua com `enrollment_modality.status = 'ACTIVE'` e o aluno continua ativo (`MonthlyGenerationIT`, CA12).
- R2: depois da conclusão da matrícula com bolsa de 100%, a modalidade criada fica `ACTIVE` e o aluno ativo (`EnrollmentConclusionIT`, CA19).
- R3: a asserção é sensível: trocar `ACTIVE` por `INACTIVE` reprova os dois testes (D1).

## 3. Critérios de aceite

- CA1 (R1): dado modalidade com bolsa PERCENT 100, quando a geração mensal roda e pula a mensalidade, então a modalidade está `ACTIVE` e o aluno tem modalidade ativa.
- CA2 (R2): dado matrícula concluída com bolsa PERCENT 100, quando `conclude` responde 201 sem cobranças, então a modalidade criada está `ACTIVE` e o aluno tem modalidade ativa.
- CA3 (R3): dado a asserção trocada por `INACTIVE`, quando os dois ITs rodam, então ambos reprovam; depois a troca é desfeita.

## 4. Plano técnico

- Dados, API, interface, segurança: não mudam.
- Arquivos (vistos):
  - `backend/src/test/java/br/com/ctcore/billing/MonthlyGenerationIT.java`, método `zeroValueAndEnded` (CA12): após `run(...)`, afirmar `select status from enrollment_modality where id = ?` com `full` igual a `ACTIVE` e `student(full)` com `count(*) from enrollment_modality where student_id = ? and status = 'ACTIVE'` maior que zero.
  - `backend/src/test/java/br/com/ctcore/student/EnrollmentConclusionIT.java`, método do CA18/CA19/CA23, trecho `r2` (bolsa de 100%): afirmar com `count(...)` que a modalidade desse aluno (`r2` / aluno de `adult(t)`) tem `status = 'ACTIVE'`. Atenção: o teste faz `update enrollment_modality set status = 'ENDED'` sem filtro antes? Conferir a ordem e, se o `r2` reutiliza o aluno, filtrar pelo `student_id`/`enrollment` devolvido em `r2`.
- Reuso: `adminJdbc()`, `student(UUID)`, `count(...)` já nos testes.
- Riscos: o aluno não tem coluna `status`; "aluno ativo" é ter ao menos uma modalidade `ACTIVE` (D2). Asserção fraca se contar modalidades de outros alunos: filtrar por `student_id`.

## 5. Tarefas

- [x] T1: asserção de `ACTIVE` na modalidade e no aluno em `MonthlyGenerationIT` (CA12) — cobre CA1 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=MonthlyGenerationIT`
- [x] T2: asserção equivalente em `EnrollmentConclusionIT` (CA19) — cobre CA2 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=EnrollmentConclusionIT`
- [x] T3: trocar `ACTIVE` por `INACTIVE` nas duas, ver reprovar, desfazer — cobre CA3 — prova: os dois comandos acima, vermelho com a troca e verde depois de desfeita (registrar na construção)

## 6. Decisões

- D1: a prova de sensibilidade (CA3) é feita uma vez à mão e desfeita, como a issue pede; não vira teste. Alternativa descartada: teste de mutação.
- D2: não existe `student.status`; "aluno ativo" é ter modalidade `ACTIVE` (a situação do aluno é derivada, ver `docs/especificacao/16`). Se houver outra definição, a asserção do aluno muda para ela.
- Suposição: o `status` do aluno na issue refere-se a essa situação derivada; se estiver errada, só a consulta do aluno muda.

## Construção

- T1 — arquivos: `backend/src/test/java/br/com/ctcore/billing/MonthlyGenerationIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=MonthlyGenerationIT` → 8 testes, 0 falhas
- T2 — arquivos: `backend/src/test/java/br/com/ctcore/student/EnrollmentConclusionIT.java` (aluno de `r2` em variável `s2`, filtro por `student_id`) — prova: `... -Dit.test=EnrollmentConclusionIT` → 17 testes, 0 falhas
- T3 — troca por `INACTIVE` nas duas: `zeroValueAndEnded` e `firstCharges` reprovaram (2 falhas); desfeita, os dois ITs voltaram a 8/8 e 17/17 verdes (rodada antes da troca; arquivos restaurados de cópia, sem `INACTIVE` restante)
Regressão: só os dois ITs rodados (`-Djacoco.skip=true`); `verify` completo e frontend não rodados, pois só dois arquivos de teste mudaram.
Desvios: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-24, estado 8477f53aeaa26f9f90619121800ec9901e07d3e5

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `MonthlyGenerationIT` 8/8 verdes; `zeroValueAndEnded` afirma `status = 'ACTIVE'` da modalidade `full` e contagem de ativas do aluno > 0 |
| CA2 | PASSOU | `EnrollmentConclusionIT` 17/17 verdes; `r2` afirma 1 modalidade `ACTIVE` e 0 não ativas, filtrando por `student_id` de `s2` |
| CA3 | PASSOU | por leitura do diff: ambas comparam com o valor real do banco, logo trocar por `INACTIVE` reprova; a prova manual está registrada na Construção (2 falhas) e não foi refeita aqui, pois a skill proíbe alterar testes |

Comandos: `./backend/mvnw -f backend/pom.xml verify -Dit.test='MonthlyGenerationIT,EnrollmentConclusionIT' -Djacoco.skip=true` → BUILD SUCCESS (162 unitários, 25 ITs, 0 falhas). Regressão completa e frontend não rodados: o diff só toca dois arquivos de teste do back-end.
Achados:
- nenhum

## Entrega

- Muda: duas asserções em testes de integração. `MonthlyGenerationIT.zeroValueAndEnded` (bolsa de 100%) afirma que a modalidade fica `ACTIVE` e que o aluno tem modalidade ativa; `EnrollmentConclusionIT` faz o mesmo no caso da bolsa de 100% (`r2`), filtrando por `student_id`. Sem mudança de código de produto, API, dados ou interface.
- Verificado: rodada 1 da verificação, `OK`, com os dois ITs (8/8 e 17/17 verdes). A sensibilidade foi provada à mão na construção: trocar para `INACTIVE` reprova os dois testes, e a troca foi desfeita.
- Decisões: "aluno ativo" é ter ao menos uma modalidade `ACTIVE` (D2), já que não existe `student.status`.
- Limitação: a regressão completa (`verify` inteiro, `test:cov`, `e2e`) não foi rodada localmente; o diff toca só dois arquivos de teste do back-end. O CI do PR roda a verificação completa.
