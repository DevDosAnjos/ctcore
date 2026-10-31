# 442 — MSW: agenda do Professor devolve todas as aulas da unidade

Origem: issue #442 (Sprint 10.5, auditoria de telas do canvas, M85 / F1-06); RN-AGE-07 em `docs/especificacao/06-turmas-e-agenda.md`; tabela de permissões em `02-contas-e-acesso.md`. Branch: `item/442-msw-agenda-professor`.

## 1. Objetivo

O mock (MSW) passa a se comportar como o back-end: o Professor vê na agenda só as aulas em que é o professor da aula, o titular da turma ou auxiliar dela, e só nessas pode editar ou cancelar. Item técnico, só mock e teste; nenhuma regra de produto muda.

Fora do escopo: back-end (já filtra em `Agenda.java`), o `POST /agenda/lessons` do mock (continua 403 para o Professor), tela da agenda, OpenAPI.

## 2. Requisitos

- R1: `GET /agenda` com papel Professor devolve só as aulas "dele" (professor da aula, titular da turma ou auxiliar da turma é o professor logado); os demais papéis recebem a lista inteira como hoje.
- R2: `GET /agenda/lessons/{id}` de aula que não é do Professor devolve 404; Coordenador e Administrador não mudam.
- R3: `canEdit` e `canCancel` do Professor só podem ser `true` nas aulas dele (e seguem as demais condições da aula, como estar agendada).
- R4: o caso visual `moldura-professor` tem a referência regerada, mostrando só as aulas dele.

## 3. Critérios de aceite

- CA1 (R1): dado `prof@exemplo.com` (Ana, titular de "Jiu-Jitsu Adulto" e substituta/auxiliar nas demais regras do mock), quando lista a agenda de uma semana, então toda aula devolvida é de turma em que Ana é titular ou auxiliar, ou tem Ana como professor da aula, e "Judô Infantil" (titular João, sem auxiliares) não aparece.
- CA2 (R1): dado `ana@exemplo.com` (Coordenação) e `admin@exemplo.com`, quando listam a mesma semana, então recebem as aulas de todas as turmas da unidade, inclusive "Judô Infantil".
- CA3 (R2): dado `prof@exemplo.com`, quando abre `/agenda/lessons/{id}` de uma aula de "Judô Infantil", então 404; de uma aula dele, 200.
- CA4 (R3): dado `prof@exemplo.com`, quando lista ou abre aula futura agendada dele, então `canEdit` e `canCancel` são `true`; aula alheia não é alcançável (CA1, CA3).
- CA5 (R1): dado `prof@exemplo.com`, quando filtra por `teacherId` de outro professor, então a lista vem vazia (o filtro só estreita).
- CA6 (R4): dado o teste visual, quando se regera só `moldura-professor`, então o diff mostra apenas aulas a menos.

## 4. Plano técnico

- Dados: não muda.
- API: não muda o contrato; só o mock. `GET /agenda` e `GET /agenda/lessons/{id}` filtram por professor logado.
- Interface: não muda código de tela. O botão "Nova aula avulsa" segue como está (D1).
- Segurança: espelha a regra do servidor; a permissão continua conferida no back-end.
- Arquivos:
  - `frontend/src/mocks/handlers.ts`: criar um helper `teachesLesson(klass: MockClass | null, teacherId)` perto de `mockLesson` (linha ~596): `klass ? klass.head === ME || klass.assistants.includes(ME) : false` combinado com `lesson.teacher.id === ME`, com `ME = TEACHER_ANA` (id que o mock já usa para o Professor, ver linhas 1137, 1699, 3615). No resolver `/agenda` (linha ~2972) filtrar `lessons` (turmas e avulsas) quando `role === "TEACHER"`. Em `lessonView` (linha ~628) devolver 404 quando `role === "TEACHER"` e a aula não é dele; trocar `canEdit: role === "TEACHER" ? false : canEdit` (e `canCancel`) por `canEdit` direto, já que só aula dele chega ali. Lembrar que a lista também devolve `canEdit`/`canCancel` pelo `mockLesson`/`adhocLesson`, que já não dependem do papel.
  - `frontend/src/mocks/agenda.msw.test.ts`: novos casos e ajuste do caso existente "titular só na substituição; Professor sem canViewClass" (hoje usa `plain`, que pode ser de outro professor e passaria a dar 404; passar a usar uma aula dele).
  - `frontend/visual/referencias/moldura-professor-*.png` (4 arquivos): regerar só este caso.
- Reuso: `TEACHER_ANA`, `activeRole`, `classRow`, `mockLesson`, `adhocLesson`, `lessonView`; os helpers `login`, `get`, `week` do teste.
- Riscos: outros testes (unitários da agenda no front, e2e `@critical`, `varios@exemplo.com` com vínculo 13 como Professor) que contam com o Professor vendo tudo ou com `canEdit` falso; `npm test` e `npm run e2e` mostram. A semana corrente tem hoje como dia cancelado; usar semana seguinte nos testes que precisam de `canEdit`.

## 5. Tarefas

- [x] T1: filtro por professor em `GET /agenda` e `GET /agenda/lessons/{id}` e `canEdit`/`canCancel` pelo dono da aula — cobre CA1, CA2, CA3, CA4, CA5 — prova: `cd frontend && npx vitest run src/mocks/agenda.msw.test.ts`
- [x] T2: testes novos (prof lista só as dele; ana e admin listam todas; detalhe alheio 404; canEdit dele true; filtro por outro professor vazio) e ajuste do caso existente; conferir que falham sem a correção — cobre CA1 a CA5 — prova: mesma classe de teste
- [x] T3: rodar `npm test` e `npm run lint && npm run typecheck`; corrigir testes do front que dependiam do Professor ver tudo — não cobre critério: regressão do mock — prova: `cd frontend && npm test`
- [x] T4: regerar só `moldura-professor` e conferir o diff — cobre CA6 — prova: `cd frontend && npm run test:visual:docker -- --update-snapshots -g moldura-professor`

## 6. Decisões

- D1: o `POST /agenda/lessons` do mock continua 403 para o Professor. A issue pede só os itens 1 a 5 e o botão é decisão da tela; mudar o POST seria ampliar o escopo. Alternativa: liberar aula avulsa dele no mock (RN-AGE-07), sugerida como item à parte se a tela precisar.
- D2: "dele" no mock é sempre `TEACHER_ANA`, qualquer login com papel Professor (inclusive `varios@` no vínculo 13), como os demais handlers do mock já fazem.
- Suposição: o botão "Nova aula avulsa" já aparece para o Professor sem depender de `canEdit` de aula; se não aparecer, é item de tela, não deste.

## Construção

- T1 — arquivos: `frontend/src/mocks/handlers.ts` (helper `teachesLesson`; filtro em `GET /agenda` para turmas e avulsas; 404 em `lessonView`; `canEdit`/`canCancel` sem forçar `false` para Professor) — prova: `npx vitest run src/mocks/agenda.msw.test.ts` → 16 passam
- T2 — arquivos: `frontend/src/mocks/agenda.msw.test.ts` (2 casos novos no describe "agenda do Professor (RN-AGE-07)"; caso existente passa a usar aula de "Jiu-Jitsu Adulto") — prova: mesma classe → 16 passam. Falha sem a correção não foi rodada à parte (sem stash permitido); o caso "Judô Infantil não aparece" só passa com o filtro.
- T3 — prova: `npm test` → 192 arquivos, 1905 testes passam; `npm run lint` limpo; `npm run typecheck` limpo (após ajuste de cast no teste)
- T4 — arquivos: `frontend/visual/referencias/moldura-professor-{1440,390}-{claro,escuro}.png` — prova: `npm run test:visual:docker -- --update-snapshots -g moldura-professor` → 4 passam; 1440 claro conferido: só Jiu-Jitsu Adulto (seg e qua)
Regressão: `npm test`, lint e typecheck verdes. `npm run e2e` e `build` não rodados nesta sessão.
Desvios: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-30, estado e7d92ca93d56672740543048aab0886780ca9c2a

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `agenda.msw.test.ts` "Professor lista só as dele": sem Judô Infantil, só Jiu-Jitsu ou professor Ana; 16 passam |
| CA2 | PASSOU | mesmo caso: ana e admin veem Judô Infantil |
| CA3 | PASSOU | caso "detalhe alheio 404, o dele 200"; `lessonView` filtra por `teachesLesson` |
| CA4 | PASSOU | mesmo caso: `canEdit`/`canCancel` true na lista e no detalhe |
| CA5 | PASSOU | mesmo caso: `teacherId` de outro professor devolve `[]` |
| CA6 | PASSOU | `moldura-professor-1440-claro.png` aberto: só 2 aulas de Jiu-Jitsu Adulto (seg, qua); demais 3 PNGs regerados (não abertos) |

Comandos: `npx vitest run src/mocks/agenda.msw.test.ts` 16 ok; `npm test` 1905 ok; `npm run lint` e `typecheck` limpos; `npm run e2e` 81 ok. Build, test:visual:docker e comparação canvas não rodados (item técnico, sem tela alterada).
Achados:
- sugestão, código, `agenda.msw.test.ts` — o teste de CA1 aceita `teacher.id === ana` OU Jiu-Jitsu, e não prova separadamente o caso de auxiliar; sem impacto.

## Entrega

- O que muda: o mock (`frontend/src/mocks/handlers.ts`) filtra `GET /agenda` e `GET /agenda/lessons/{id}` pelo professor logado; `canEdit` e `canCancel` do Professor só são `true` nas aulas dele. Teste em `frontend/src/mocks/agenda.msw.test.ts` (16 casos passam).
- Referências `moldura-professor` (1440 e 390, claro e escuro) regeradas; só a `1440-claro` foi aberta e conferida, as outras três não.
- Verificado (rodada 1, OK): `npm test` 1905 ok, `npm run lint` e `typecheck` limpos, `npm run e2e` 81 ok.
- Limitações: `build`, `test:visual:docker` e comparação com o canvas não rodados (item técnico, sem tela nova).
