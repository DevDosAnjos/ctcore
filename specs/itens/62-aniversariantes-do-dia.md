# 62 — RF-ALU-06 · Aniversariantes do dia

Origem: issue #62 (Sprint 06); `docs/especificacao/08-alunos-e-responsaveis.md` (RF-ALU-06), `15-inicio-atencao-e-busca.md` (RF-INI-02, RF-INI-03), `02` (permissões), `00-visao-geral.md` (RNF-42, WhatsApp). Branch: `item/62-aniversariantes-do-dia`. Parte de `Students` (selo `BIRTHDAY_TODAY`, `sealPredicate`, `reachPredicate`, `rows`), `StudentRecords.teacherReach` (#60) e `WhatsAppButton` (#9).

## 1. Objetivo

Mostrar no Início do Administrador e do Professor quem faz aniversário hoje, com botão de WhatsApp e mensagem de parabéns pronta. Não há e-mail de aniversário. O servidor entrega a lista já com o corte de cada perfil; o Início atual (`Home` em `App.tsx`) ganha o cartão, e as Sprints 13 (#123 e #124) o reposicionam no Início completo.

Fora do escopo: o resto do Início (atenção, números, aulas), na Sprint 13; envio automático de qualquer mensagem; aluno só com matrícula ativa e o "alunos das turmas" do Professor, que dependem da matrícula (#63); Início do Coordenador (RF-INI-01 não cita aniversariantes). Notas de versão: Sprint 15, como #54.

## 2. Requisitos

- R1: `GET /api/v1/students/birthdays` devolve os alunos da organização que fazem aniversário hoje (data do relógio do servidor, como o selo), em ordem de nome; sem paginação (lista do dia) (RF-ALU-06).
- R2: Quem faz aniversário em 29/02 entra em 28/02 nos anos não bissextos, igual ao selo "Aniversário hoje" (mesmo predicado, sem duplicar a regra).
- R3: Administrador vê só alunos cuja unidade de cadastro está no alcance dele; Professor só os das suas turmas; Coordenador e Aluno e Responsável: 403 (D1, D2).
- R4: Cada item traz `id`, `name`, `photoUrl`, `age` (a que completa hoje), `ageRange` e `registeredUnit`; nunca CPF, endereço, e-mail nem data de nascimento completa.
- R5: Cada item traz o contato para o WhatsApp: telefone do próprio aluno; se não tiver, o do responsável financeiro e, sem ele, o de outro responsável, com `contactName` e `contactIsGuardian`. Para o Professor o contato só vem com a permissão "ver contatos" (RN-PER, `STUDENT_CONTACTS`); sem ela, `null` (D3).
- R6: O cartão "Aniversariantes de hoje" aparece no Início do Administrador e do Professor, com nome, foto (iniciais se não houver), idade e faixa; o botão `WhatsAppButton` abre a conversa com a mensagem pronta em pt-BR, que muda quando o destino é o responsável (D4). Sem contato válido ou com WhatsApp desligado, o botão some (RNF-42).
- R7: O cartão tem os quatro estados (carregando, vazio "Ninguém faz aniversário hoje", erro com "Tentar de novo", sem permissão), funciona a partir de 360 px, passa no axe nos dois temas e tem textos em pt-BR.
- R8: A rota só lê: não grava auditoria. Toda leitura é feita na transação da organização (RLS) e com filtro de organização no código.
- R9: OpenAPI e handlers MSW cobrem a rota e batem com o servidor (RNF-32).

## 3. Critérios de aceite

- CA1 (R1, R2): dados alunos nascidos hoje, ontem e amanhã (em anos diferentes), quando o Administrador chama a rota, então só os de hoje voltam, por nome; com relógio em 28/02 de ano não bissexto, quem nasceu em 29/02 volta; em ano bissexto, só em 29/02.
- CA2 (R3): dado Administrador da unidade A, quando há aniversariantes cadastrados nas unidades A e B, então só os da A voltam; Administrador sem unidades: lista vazia.
- CA3 (R3): dado Coordenador, Aluno ou Responsável, quando chama, então 403; sem sessão, 401; aniversariante de outra organização nunca aparece (isolamento entre organizações, em duas organizações com a mesma data).
- CA4 (R3): dado Professor, quando chama, então 200 e só alunos das turmas dele; hoje, sem matrícula, a lista é vazia (D2) e o teste fixa o ponto único que a #63 troca.
- CA5 (R4): dado aniversariante, quando a lista volta, então não há `cpf`, `email`, endereço nem `birthDate` no JSON.
- CA6 (R5): dado adulto com telefone, então `contactPhone` é o dele e `contactIsGuardian` falso; menor sem telefone, então o do responsável financeiro com o nome dele e `contactIsGuardian` verdadeiro; sem nenhum telefone, `contactPhone` nulo; Professor sem "ver contatos": contato nulo.
- CA7 (R6): dada a lista com um adulto e um menor, quando o cartão renderiza, então mostra nome, idade e faixa; o link do botão é `https://wa.me/55<tel>?text=…` com a mensagem do aluno para o adulto e a do responsável para o menor; item sem telefone não mostra botão.
- CA8 (R6): dado `Home` com perfil Administrador ou Professor, então o cartão aparece; com Coordenador não.
- CA9 (R7): dado carregando, lista vazia, erro 500 (com "Tentar de novo") e 403, então o cartão mostra o estado certo; axe sem violações nos temas claro e escuro, em 360 px.
- CA10 (R8): dada a chamada, então `audit_log` não ganha registro.
- CA11 (R9): `OpenApiContractTest` e `ContractRoutesTest` passam; teste MSW cobre 200 e 403.

## 4. Plano técnico

- Dados: não muda; sem migração nem seed (o selo e as colunas já existem). Índice não é necessário: a consulta já roda por organização e `to_char(birth_date)` como o selo.
- API: `GET /students/birthdays` sem parâmetros; resposta `StudentBirthday[]` (`id`, `name`, `photoUrl`, `age`, `ageRange {id,name}`, `registeredUnit {id,name}`, `contactName`, `contactPhone`, `contactIsGuardian`). Erros: 401, 403. Rota literal convive com `/{id}` como `/lookup`.
- Interface: `BirthdaysCard` em `frontend/src/features/students/`, hook `useBirthdays`, montado em `Home` (`App.tsx`) para ADMINISTRATOR e TEACHER; `WhatsAppButton` com a mensagem de `pt-BR.json` (`birthdays.messageStudent`, `birthdays.messageGuardian`, com nome e unidade); estados de `components/states`.
- Segurança: Administrador: `STUDENT_MANAGE` com `reachPredicate`; Professor: `STUDENT_VIEW` com `teacherReach` (hoje falso); Coordenador 403 por perfil. Organização só da sessão, dentro de `OrganizationTransactions` (RLS). Contato mínimo e só o necessário ao botão; Professor só com `STUDENT_CONTACTS`.
- Arquivos a alterar (vistos): `backend/src/main/java/br/com/ctcore/student/Students.java` (método `birthdays`, reaproveitando `sealPredicate`, `sealParams`, `reachPredicate`), `StudentController.java`, `StudentRecords.java` (o `teacherReach` passa a ser usado também aqui, sem mudar o comportamento), `docs/openapi.yaml`, `frontend/src/App.tsx`, `frontend/src/features/students/types.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json`. A criar: `backend/src/test/java/br/com/ctcore/student/StudentBirthdaysIT.java`, `frontend/src/features/students/BirthdaysCard.tsx` e `.test.tsx`, `useBirthdays.ts`, `frontend/src/mocks/birthdays.msw.test.ts`.
- Reuso: selo `BIRTHDAY_TODAY`, `Students.rows` (ou a mesma base de select), `photoUrl`, `WhatsAppButton`, `components/states`, `Avatar`/iniciais da lista de alunos.
- Riscos: dois pontos de verdade para "aniversário hoje" (evitado reusando o predicado); `teacherReach` falso esconde tudo do Professor até a #63 (testado e documentado); `Home` mexe em arquivo compartilhado, os testes do `App` cobrem.

## 5. Tarefas

- [x] T1: `Students.birthdays` e `GET /students/birthdays`, com alcance, contato (aluno, responsável) e corte do Professor — cobre CA1 a CA6, CA10 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=StudentBirthdaysIT` (inclui isolamento entre organizações e entre perfis) e teste unitário do 29/02.
- [x] T2: Publicar a rota e o schema `StudentBirthday` em `docs/openapi.yaml` — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`.
- [x] T3: Handler MSW da rota, com 200 e 403 — cobre CA11 — prova: `birthdays.msw.test.ts` via `npm test`.
- [x] T4: `BirthdaysCard`, `useBirthdays`, mensagens em pt-BR e montagem em `Home` para Administrador e Professor — cobre CA7 a CA9 — prova: `BirthdaysCard.test.tsx` (incluindo axe a 360 px nos dois temas) e o teste de `Home`.
- [x] T5: Regressão completa — não cobre critério: confirma cobertura e o que o CI exige — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): O Coordenador não recebe a rota nem o cartão. Motivo: a especificação cita o aniversariante só no Início do Administrador (RF-INI-02) e do Professor (RF-INI-03); o do Coordenador (RF-INI-01) não o cita. Alternativa: liberar ao Coordenador com alcance "todas as unidades"; trivial de somar depois.
- D2 (muda o comportamento): O Professor vê só alunos das suas turmas; como a matrícula ainda não existe (#63), hoje a lista dele é vazia e o cartão mostra o vazio. O servidor já aplica o corte por `teacherReach`, o ponto único que a #63 troca. Alternativa: mostrar a todos os alunos da unidade, o que contraria RN-ALU e a regra de "só os das suas turmas".
- D3: O Professor recebe contato só com `STUDENT_CONTACTS` (já é o que a ficha usa); sem ela vê o aniversariante, sem botão. Alternativa: sempre dar o telefone, que vazaria contato por outro caminho.
- D4: Menor sem telefone próprio é parabenizado pelo responsável: a mensagem fala com ele e cita o aluno. Alternativa: mensagem igual para todos, que soaria estranha enviada ao responsável.
- D5: "Hoje" é a data do relógio do servidor, como o selo da lista (#59), e não o fuso de cada unidade; se uma unidade ficar em outro fuso, o ajuste é único e vale para o selo também. Suposição: unidades em `America/Sao_Paulo` no piloto.
- D6: Sem exigir matrícula ativa, porque ela ainda não existe; quando a #63 chegar, o filtro "com modalidade ativa" entra no mesmo ponto que o alcance. Suposição: aluno anonimizado (RF-ALU-05) ainda não existe, então não é filtrado.
- D7: O cartão vai no `Home` atual, sem esperar o Início completo; as Sprints 13 (#123, #124) o reposicionam. Alternativa: só a API, o que deixaria o RF sem tela.

## Construção

- T1 — arquivos: `backend/.../student/Students.java` (`Birthday`, `birthdays`, reaproveita `sealPredicate`/`sealParams`/`reachPredicate`), `StudentRecords.java` (`birthdays`, perfis, `STUDENT_CONTACTS`, corte por `teacherReach`), `StudentController.java`, `backend/src/test/.../StudentBirthdaysIT.java` (6 testes: CA1 a CA6, CA10, isolamento entre organizações e perfis, ponto único `teacherReach` via spy) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=StudentBirthdaysIT` → 6 testes, 0 falhas
- T2 — `docs/openapi.yaml` (rota e `StudentBirthday`) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes, 0 falhas
- T3 — `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/birthdays.msw.test.ts` — prova: `npx vitest run src/mocks/birthdays.msw.test.ts` → 2 testes passam (200 Administrador e Professor, 401, 403)
- T4 — `BirthdaysCard.tsx`, `BirthdaysCard.test.tsx`, `useBirthdays.ts`, `useStudentRecord.ts` (exporta `useResource`), `types.ts`, `pt-BR.json`, `App.tsx` — prova: `npx vitest run src/features/students/BirthdaysCard.test.tsx` → 6 testes passam (inclui axe a 360 px nos dois temas)
- T5 — prova: `./backend/mvnw -f backend/pom.xml verify` → todos os relatórios sem falha, JaCoCo gerado; em `frontend/`: `npm run lint`, `typecheck`, `build` ok; `npm run test:cov` → 89 arquivos, 811 testes; `npm run e2e` → 31 passaram

Regressão: sem falhas; nada que passava passou a falhar.
Desvios: o plano citava teste unitário do 29/02; ele ficou no `StudentBirthdaysIT` (banco real, relógio simulado), já que o predicado é SQL. `Students.birthdays` é chamado por `StudentRecords.birthdays` (que tem `teacherReach` e as permissões) para evitar dependência circular; a rota usa `StudentRecords`.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-20, estado ff1b0930d035e3458676125e19f6025c043f5dac

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `StudentBirthdaysIT.today`: hoje/ontem/amanhã, ordem por nome, 29/02 em 28/02 (2025) e só em 29/02 (2024) |
| CA2 | PASSOU | `StudentBirthdaysIT.adminReach` (unidade A x B; sem unidades vazio) |
| CA3 | PASSOU | `profilesAndIsolation`: Coordenador/Aluno/Responsável 403, anônimo 401, duas organizações |
| CA4 | PASSOU | `teacher`: 200 vazio; spy em `teacherReach` filtra; contato nulo sem STUDENT_CONTACTS |
| CA5 | PASSOU | `contact`: JSON sem cpf, email, birthDate, endereço |
| CA6 | PASSOU | `contact`: aluno, responsável financeiro, outro responsável, nulo |
| CA7 | PASSOU | `BirthdaysCard.test.tsx` CA7: links wa.me e mensagens, sem botão sem telefone |
| CA8 | PASSOU | `BirthdaysCard.test.tsx` CA8 (Professor sim, Coordenador não) |
| CA9 | PASSOU | testes de erro/retry, vazio, 403, carregando, axe 360 px claro e escuro |
| CA10 | PASSOU | `noAudit`: contagem de `audit_log` inalterada |
| CA11 | PASSOU | `OpenApiContractTest,ContractRoutesTest` 7 testes ok; `birthdays.msw.test.ts` 2 testes ok |

Comandos: `mvnw verify` completo → 450 testes, 0 falhas, BUILD SUCCESS; `-Dit.test=StudentBirthdaysIT` 6 ok; frontend `lint`, `typecheck`, `build` ok; `test:cov` 89 arquivos, 811 testes ok; vitest do cartão e MSW 8 ok. `npm run e2e` não reexecutado (item não toca fluxos e2e; construção registra 31 passaram).
Achados:
- sugestão, código, `frontend/src/mocks/handlers.ts` — handler do Professor devolve sempre `[]`; coerente com D2, trocar na #63.

## Entrega

- Nova rota `GET /api/v1/students/birthdays` (Administrador e Professor) com os aniversariantes do dia, filtrados por unidade/alcance, ordenados por nome, 29/02 tratado em anos não bissextos; contato só com a permissão de contatos; sem auditoria (leitura).
- Cartão `BirthdaysCard` no `Home`, com botão WhatsApp (mensagem ao aluno ou ao responsável, no caso de menor) e os quatro estados; textos em pt-BR.
- Contrato em `docs/openapi.yaml`; handlers MSW batem com ele.
- Verificação: rodada 1 OK (`mvnw verify` 450 testes; lint, typecheck, build e `test:cov` ok).
- Limitações: handler MSW do Professor devolve `[]` até a #63 (matrícula); "hoje" usa o relógio do servidor (D5).
- CI do PR #299 falhou em `backend` (`StudentBirthdaysIT.clean` violava `account_link_account_id_fkey`); corrigido na limpeza do teste e conferido na rodada 2 (56 ITs de aluno, 0 falhas).

### Correção da entrega
- `StudentBirthdaysIT.clean` apagava `account` sem antes apagar `account_invite`, `account_session`, `account_link_unit` e `account_link`; agora segue a limpeza dos demais ITs de aluno — arquivos: `backend/src/test/java/br/com/ctcore/student/StudentBirthdaysIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='Student*IT,PortalStudentsIT,EmailProblemsIT,MajorityNoticeJobIT'` → 56 ITs, 0 falhas, BUILD SUCCESS
Regressão: só o teste mudou; o `verify` completo não foi reexecutado, apenas os ITs de aluno em conjunto (a ordem que falhou no CI).

### Rodada 2 — OK — 2026-10-20, estado 312c1c1ea8c29ea8cefba3a67f549b57a19d4d7a

Achado da entrega (CI do PR #299, `StudentBirthdaysIT.clean` violando `account_link_account_id_fkey`) conferido; o diff desde a rodada 1 só toca `StudentBirthdaysIT.clean` (apaga `account_invite`, `account_session`, `account_link_unit` e `account_link` das contas `%.example` antes de `account`).

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA11 | PASSOU (mantido da rodada 1) | mudança só na limpeza do teste |
| Falha do CI | PASSOU | `mvnw verify -Dit.test='Student*IT,PortalStudentsIT,EmailProblemsIT,MajorityNoticeJobIT'` → 56 testes, 0 falhas, BUILD SUCCESS |

Comandos: o `verify` acima. Suíte completa não reexecutada (só o teste mudou); o conjunto que falhou no CI passa.
Achados: nenhum obrigatório.
