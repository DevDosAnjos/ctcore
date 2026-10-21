# 68 — RF-MAT-02 · Avisos de faixa etária e capacidade na matrícula

Origem: issue #68 (Sprint 07); `docs/especificacao/09-matricula.md` (RF-MAT-02), `04` (RN-MOD-07: "fora da faixa, o sistema avisa e não impede"), `06` (RN-TUR-02: capacidade só avisa), `01` (RN-ORG-17: idade sempre calculada pelo nascimento). Branch: `item/68-avisos-faixa-etaria-capacidade`. Parte da #67 (assistente `enrollment-wizard`, `StepModalities.tsx`) e de `TrainingClasses` (`activeStudents`, `overCapacity`, `AgeRangeRef`).

## 1. Objetivo

Na etapa 2 do assistente de nova matrícula, quando a gestão (Coordenador e Administrador) escolhe a turma principal ou outros horários, a tela mostra avisos: "aluno com N anos, fora da faixa etária da turma (Nome, mín–máx)" e "turma acima da capacidade com esta matrícula (X/Y)". Os avisos nunca impedem avançar nem concluir.

Fora do escopo: mudar a conclusão (#67) para recusar ou registrar avisos; avisos na matrícula de nova modalidade da ficha (`RecordEnrollment`), na transferência e nos pedidos do portal; lista de espera (RN-AGE-13); aula experimental e diretório.

## 2. Requisitos

- R1 (RF-MAT-02, RN-MOD-07, RN-ORG-17): `GET /api/v1/students/{id}/enrollment/class-warnings?classId=<uuid>` devolve, sem gravar nada, `ageOutOfRange` (verdadeiro quando a turma tem faixa etária e a idade do aluno hoje, calculada pelo nascimento no fuso da unidade da turma, é menor que a mínima ou maior que a máxima), a idade e a faixa (id, nome, mínima, máxima), ou faixa nula se a turma não tem.
- R2 (RF-MAT-02, RN-TUR-02): a mesma resposta traz `capacity`, `activeStudents` e `overCapacity`, verdadeiro quando a turma tem capacidade e `activeStudents` + 1 (esta matrícula) passa dela; se o aluno já está ativo na turma, não soma 1 (D1).
- R3 (RF-MAT-02): na etapa 2, ao escolher a turma principal e cada outro horário, a tela chama a rota e mostra o aviso de faixa etária e o de capacidade em texto visível (`role="status"`), por turma, sem desabilitar "Avançar" nem "Concluir matrícula".
- R4 (RF-MAT-02): a conclusão (`POST .../enrollment/conclude`) continua aceitando turma fora da faixa e acima da capacidade; nenhuma regra nova a recusa.
- R5 (permissão, isolamento): só Coordenador e Administrador com `STUDENT_MANAGE` na unidade da turma chamam a rota (403 senão); aluno ou turma de outra organização: 404; sem sessão: 401.
- R6 (RNF-32): rota no `docs/openapi.yaml`, handler MSW igual, textos em `pt-BR.json`.
- R7 (quatro estados): o aviso mostra carregando, erro (com tentar de novo, sem bloquear a matrícula) e some se não há o que avisar; sem permissão mostra o estado padrão. Funciona a partir de 360 px e passa no axe nos dois temas.

## 3. Critérios de aceite

- CA1 (R1): dado aluno com 10 anos e turma com faixa Juvenil (13 a 17), quando consulta, então `ageOutOfRange` é verdadeiro com idade 10 e a faixa; aluno com 15 anos: falso.
- CA2 (R1): dado turma sem faixa, então `ageOutOfRange` é falso e a faixa é nula; faixa sem máxima (Adulto 18+) e aluno com 40 anos: falso; aluno que faz aniversário hoje conta a idade nova.
- CA3 (R2): dado turma com capacidade 2 e 2 alunos ativos, quando consulta para aluno de fora, então `overCapacity` é verdadeiro; com 1 aluno ativo, falso; sem capacidade, falso.
- CA4 (R2): dado aluno já ativo na turma que está com 2/2, então `overCapacity` é falso.
- CA5 (R3): dado turma fora da faixa e cheia na etapa 2, quando escolhe a turma, então aparecem os dois avisos e o botão de avançar continua habilitado; trocar para turma sem problema remove os avisos; o mesmo vale para cada "outro horário" marcado.
- CA6 (R4): dado pedido de conclusão com turma fora da faixa e acima da capacidade, então a matrícula é gravada com sucesso.
- CA7 (R5): dado Professor, Aluno ou Responsável, então 403; Administrador com turma de unidade fora do alcance: 403; aluno ou turma de outra organização: 404; sem sessão: 401; `classId` ausente ou inválido: 400; nada é gravado.
- CA8 (R6): `OpenApiContractTest` e `ContractRoutesTest` passam e o MSW devolve o mesmo formato.
- CA9 (R7): erro na consulta mostra a mensagem com "tentar de novo" sem bloquear; 403 mostra o estado sem permissão; axe sem violações nos dois temas; sem rolagem horizontal em 360 px.

## 4. Plano técnico

- Dados: não muda (usa `age_range`, `training_class.capacity`, `enrollment_modality`, `enrollment_modality_class` e o nascimento do aluno).
- API: `GET /students/{id}/enrollment/class-warnings?classId=` em `StudentController`, resposta `ClassWarnings { studentAge, ageRange (id, name, minAge, maxAge) | null, ageOutOfRange, capacity | null, activeStudents, overCapacity }`. Erros: 400, 401, 403, 404. É leitura: sem auditoria (não altera dado). Entra em `docs/openapi.yaml`.
- Interface: em `StepModalities.tsx`, componente `ClassWarnings` que consulta a rota para a turma principal e cada outro horário e lista os avisos abaixo do campo; chaves novas em `enrollmentWizard.modalities.warn*` no `pt-BR.json`.
- Segurança: permissão `STUDENT_MANAGE` na unidade da turma conferida no servidor; filtro por organização e RLS como na prévia; resposta sem dados de outros alunos (só contagem).
- Arquivos: a alterar (vistos) `backend/src/main/java/br/com/ctcore/student/StudentController.java`, `docs/openapi.yaml`, `frontend/src/features/students/enrollment-wizard/StepModalities.tsx`, `types.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json`, `EnrollmentWizardPage.test.tsx`, `EnrollmentConclusionIT`. A criar: `backend/src/main/java/br/com/ctcore/student/ClassWarnings.java`, `ClassWarningsIT`, `frontend/src/features/students/enrollment-wizard/ClassWarnings.tsx` e seu teste.
- Reuso: `TrainingClasses.activeStudentsOf` e `overCapacity`, cálculo de idade de `Students`, checagem de alcance da prévia (`EnrollmentConclusion`), `useResource`, `ErrorState`, `ForbiddenState`.
- Riscos: a contagem muda entre a escolha e a conclusão (o aviso é informativo, aceito); `ContractRoutesTest` reprova a rota se faltar no contrato.

## 5. Tarefas

- [x] T1: servidor: `ClassWarnings` (idade, faixa, capacidade, aluno já na turma) e a rota com permissão e isolamento — cobre CA1, CA2, CA3, CA4, CA7 — prova: `ClassWarningsIT` e teste unitário de idade/faixa no `./backend/mvnw -f backend/pom.xml verify`.
- [x] T2: teste de que a conclusão aceita turma fora da faixa e cheia — cobre CA6 — prova: caso novo em `EnrollmentConclusionIT`.
- [x] T3: contrato OpenAPI e handler MSW da rota — cobre CA8 — prova: `OpenApiContractTest,ContractRoutesTest` e teste MSW.
- [x] T4: componente `ClassWarnings` na etapa 2, com textos pt-BR e os estados — cobre CA5, CA9 — prova: `EnrollmentWizardPage.test.tsx` e `ClassWarnings.test.tsx` (inclui axe e avanço não bloqueado), e `npm run lint && npm run typecheck && npm run test:cov`.

## 6. Decisões

- D1 (muda o comportamento): "acima da capacidade" avisa quando esta matrícula faria a turma passar dela (ativos + 1 > capacidade), não só quando já está acima; ajuda quem está escolhendo. Aluno já ativo na turma não conta de novo. Alternativa: avisar só com a turma já acima (RN-TUR-02 literal), descartada por chegar tarde.
- D2: o cálculo fica no servidor, em rota de leitura nova, porque a idade é sempre calculada no servidor (RN-ORG-17) e precisa de teste de isolamento e permissão. Alternativa: usar `ageRange`/`activeStudents` de `GET /classes` e a idade da ficha no front.
- D3: a rota só avisa; não grava nem audita, e a conclusão não registra que houve aviso. Alternativa: guardar "matriculado fora da faixa", sem pedido na regra.
- D4: avisos só no assistente (etapa 2), como diz o RF-MAT-02; ficha, transferência e portal ficam para outros itens.
- Suposição: o fuso da idade é o da unidade da turma; se a regra for outra, muda só o cálculo de "hoje".

## Construção

- T1 — arquivos: `backend/.../student/ClassWarnings.java` (novo, registro e regras puras), `EnrollmentConclusion.java` (método `warnings`, injeta `TrainingClasses`), `StudentController.java` (rota), `backend/src/test/.../student/ClassWarningsIT.java` (novo, inclui teste das regras puras) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ClassWarningsIT ...` → 4 testes passam
- T2 — arquivos: `EnrollmentConclusionIT.java` (caso `warningsDoNotBlock`) — prova: `verify -Dit.test=EnrollmentConclusionIT` → passa
- T3 — arquivos: `docs/openapi.yaml` (rota e schema `ClassWarnings`), `frontend/src/mocks/handlers.ts` (handler), `ClassWarnings.test.tsx` (formato do simulado) — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 4 e 3 testes passam; teste MSW passa
- T4 — arquivos: `frontend/src/features/students/enrollment-wizard/ClassWarnings.tsx` (novo), `StepModalities.tsx`, `types.ts`, `i18n/pt-BR.json`, `ClassWarnings.test.tsx` (novo), `EnrollmentWizardPage.test.tsx` — prova: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → 104 arquivos / 936 testes passam, e2e 33 passam
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 524 testes, 0 falhas, BUILD SUCCESS (JaCoCo ok); front-end acima, sem piora.
Desvios: a contagem de alunos ativos reutiliza `TrainingClasses.activeStudentsOf` (injetado no `EnrollmentConclusion`), pois `activeStudents` não existia nele; o axe a 360 px dos avisos foi testado no componente (`ClassWarnings.test.tsx`, dois temas), não nas cinco etapas do assistente.
Não é do item: nada.

### Correção da rodada 1
- CA9, prova de 360 px — arquivos: `frontend/e2e/matricula.spec.ts` (turma Infantil com capacidade 1 criada pela API; a 360 px, o aviso de faixa etária aparece, "Avançar" fica habilitado, `scrollWidth <= innerWidth`; trocar para a turma Adulto remove o aviso) — prova: `npx playwright test e2e/matricula.spec.ts` → 2 passam; `npm run e2e` → 33 passam; `npm run lint` e `npm run typecheck` ok.
- Sugestões opcionais (X+1 com aluno já ativo; asserção de "outro horário") não feitas: não são obrigação.

## Verificação

### Rodada 1 — NAO_CONFERIDO — 2026-10-21, estado 72d88f59a7226fc0b1511c34b932e05141499451

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `ClassWarningsIT.age` (10 anos fora, 15 dentro, 18 fora); verify 524 testes ok |
| CA2 | PASSOU | `ClassWarningsIT.age` (sem faixa, 18+ com 40 anos, aniversário hoje) e `rules` |
| CA3 | PASSOU | `ClassWarningsIT.capacity` (1 ativo falso, 2/2 verdadeiro, sem capacidade falso) |
| CA4 | PASSOU | `ClassWarningsIT.capacity` (aluno já ativo em 2/2 falso) |
| CA5 | PASSOU | `EnrollmentWizardPage.test.tsx` (dois avisos, "Avançar" habilitado) e `ClassWarnings.test.tsx`; vitest 28 passam. Troca de turma remove avisos pelo `key`/consulta por turma; o teste do assistente só afirma a presença, não a remoção, nem o caso de "outro horário" |
| CA6 | PASSOU | `EnrollmentConclusionIT.warningsDoNotBlock` dentro do verify (BUILD SUCCESS) |
| CA7 | PASSOU | `ClassWarningsIT.access` (403, 404, 401, 400, nada gravado) |
| CA8 | PASSOU | `ContractRoutesTest`/`OpenApiContractTest` dentro do verify; teste do formato do simulado em `ClassWarnings.test.tsx` |
| CA9 | NÃO CONFERIDO | erro com "tentar de novo", 403 e axe nos dois temas passam em `ClassWarnings.test.tsx`; "sem rolagem horizontal em 360 px" não foi exercido por nenhum teste (jsdom não mede layout) e não é limitação aceita no AGENTS.md |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 524 testes, 0 falhas, JaCoCo ok, BUILD SUCCESS; em `frontend/`: `npm run lint` e `npm run typecheck` ok, `npm run test:cov` ok (lines 96,5%), `npx vitest run src/features/students/enrollment-wizard` → 28 passam. `build` e `e2e` não rodados nesta rodada (aceito o relato da Construção: 33 e2e).
Achados:
- sugestão, código, `ClassWarnings.tsx` (`count: w.activeStudents + 1`) — aluno já ativo em turma acima da capacidade mostra X+1 em vez de X; usar `activeStudents` quando o aluno já está na turma (hoje a resposta não informa isso).
- sugestão, código, `EnrollmentWizardPage.test.tsx` — acrescentar asserção de que trocar a turma remove os avisos e de que "outro horário" também avisa (CA5).
- pendência de conferência, CA9 — provar 360 px (e2e/Playwright com viewport 360 ou inspeção do layout) para fechar o critério.
RESULTADO: NAO_CONFERIDO — só falta a prova de "sem rolagem horizontal em 360 px"; sem achado obrigatório de código ou especificação.

### Rodada 2 — OK — 2026-10-21, estado 560456869ec7e14cadf3c1914698f61ac9ae0134

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA4, CA6, CA7, CA8 | PASSOU | mantido da rodada 1 (diff desde 72d88f5 só toca `frontend/e2e/matricula.spec.ts` e este arquivo; backend, contrato e MSW inalterados) |
| CA5 | PASSOU | mantido da rodada 1; o e2e agora também afirma que trocar a turma remove o aviso |
| CA9 | PASSOU | `npx playwright test e2e/matricula.spec.ts` → 2 passam: a 360 px o aviso "fora da faixa" aparece, "Avançar" fica habilitado e `scrollWidth <= innerWidth`; erro/403/axe dois temas mantidos de `ClassWarnings.test.tsx` |

Comandos: `npx playwright test e2e/matricula.spec.ts` → 2 passam; `npm run lint` e `npm run typecheck` ok; `npx vitest run src/features/students/enrollment-wizard` → 28 passam. Backend não reexecutado (sem mudança desde a rodada 1: verify 524 testes ok).
Achados:
- sugestão, código, `ClassWarnings.tsx` (`count: w.activeStudents + 1`) — aluno já ativo em turma acima da capacidade mostra X+1; mantida da rodada 1.
- sugestão, código, `EnrollmentWizardPage.test.tsx` — asserção de "outro horário" ainda não existe; mantida.

## Entrega

- Nova rota de leitura de avisos na matrícula: faixa etária (idade calculada no servidor) e capacidade da turma, exibidos na etapa 2 do assistente sem bloquear o avanço.
- Capacidade avisa quando a matrícula faria a turma passar do limite (D1); a rota só avisa, não grava nem audita (D3).
- Contrato em `docs/openapi.yaml` e handler MSW atualizados; textos em pt-BR.
- Verificação (rodada 2, OK): back-end `verify` 524 testes; front-end lint, typecheck, test:cov e e2e (33) verdes; 360 px provado no e2e.
- Limitações: com aluno já ativo em turma acima da capacidade o texto mostra X+1; falta asserção de "outro horário" (sugestões não obrigatórias).
