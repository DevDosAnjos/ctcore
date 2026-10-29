# 405 — Agenda do portal mostra o nome do professor (D2)

Origem: issue #405 (Sprint 10 · Portal do aluno, decisão D2 do dono, 28/10/2026); `docs/especificacao/14-portal-do-aluno.md` (RN-POR-06, RN-POR-06a da #404). Branch: `item/405-agenda-portal-nome-professor`. Parte da #91 (`PortalSchedule`, `PortalLessonItem`, `usePortalSchedule`) e da #404 (regra já escrita).

## 1. Objetivo

A Agenda do portal passa a mostrar o nome do professor que dá cada aula (o substituto, quando há) ao aluno ou responsável da turma ou da aula. Hoje o nome é omitido (#91 D3); a regra RN-POR-06a (#404) agora permite. Nada além do nome sai pela API.

Fora do escopo: aviso (#93) e treinos por modalidade (#94), que já mostram o nome; foto, graduação, telefone e e-mail do professor; qualquer ação sobre a aula.

## 2. Requisitos

- R1 (RN-POR-06a): cada aula de `GET /api/v1/portal/schedule` traz `teacherName`, o nome do professor que dá a aula (o substituto, quando `substitute` é verdadeiro) (D1).
- R2 (RN-POR-06a, RN-POR-06): a resposta não traz foto, graduação, telefone, e-mail nem id do professor; só o nome.
- R3 (RN-POR-06a, RN-POR-02): o nome só sai nas aulas que o aluno já vê (turma em que está matriculado ou aula avulsa visível a ele); aluno sem matrícula na turma não recebe a aula, logo nem o nome.
- R4 (RN-POR-06a): a lista, a grade e o resumo da aula alterada mostram o nome; com substituto, o resumo mostra "Professor substituto: [nome]".
- R5 (RNF-32): OpenAPI e handler MSW trazem `teacherName`; textos em pt-BR.

## 3. Critérios de aceite

- CA1 (R1): dado aula de turma com titular, quando o aluno matriculado lê a agenda, então `teacherName` é o nome do professor da aula.
- CA2 (R1): dado aula com substituto, então `teacherName` é o do substituto e `substitute` é verdadeiro.
- CA3 (R2): dada qualquer resposta da agenda, então o JSON não contém foto, graduação, telefone nem e-mail do professor, nem o id dele (verificado com professor de teste que tem esses dados preenchidos).
- CA4 (R3): dado aluno sem matrícula na turma da aula, quando lê a agenda, então a aula e o nome não aparecem; responsável de outra conta ou de outra organização recebe 404 e o nome não vaza; gestão e Professor recebem 403; sem sessão, 401.
- CA5 (R4): dada a agenda no celular e no computador, então cada aula mostra o nome do professor e a aula com substituto mostra "Professor substituto: [nome]".
- CA6 (R5): o contrato (`OpenApiContractTest`) e o teste da tela com o handler MSW passam; a tela passa no axe nos dois temas.

## 4. Plano técnico

- Dados: não muda (`lesson.teacher_id` já existe, V25).
- API: `backend/src/main/java/br/com/ctcore/student/PortalSchedule.java`: no record `Lesson` incluir `String teacherName`; em `lessons(...)` juntar `teacher t on t.organization_id = l.organization_id and t.id = l.teacher_id` e selecionar `t.name`, lido no `query` (acrescentar ao final, índice 20). Ajustar o comentário da classe ("sem professor" vira "só o nome do professor"). Não selecionar nenhuma outra coluna de `teacher`. `docs/openapi.yaml` (schema da aula, ~linha 6339): campo `teacherName` obrigatório, e trocar "Sem professor" na descrição da rota (~3243) por "do professor, só o nome".
- Front: `frontend/src/features/portal/usePortalSchedule.ts`: `teacherName: string` em `ScheduleLesson`. `PortalLessonItem.tsx`: linha com o nome (`portalAgenda.teacher`) e o resumo com `portalAgenda.substituteName` no lugar de `portalAgenda.substitute`. `frontend/src/i18n/pt-BR.json` (bloco `portalAgenda`, ~linha 2927): chaves novas. Grade e lista usam `PortalLessonItem` (conferir `PortalAgendaGrid.tsx` e `PortalAgendaDayList.tsx`; se a grade tem célula própria, incluir o nome).
- MSW: `frontend/src/mocks/handlers.ts` (~4727): `teacherName` no helper `lesson`, com nome fictício; uma aula com `substitute: true`.
- Testes: `backend/src/test/java/br/com/ctcore/student/PortalScheduleIT.java` (acrescentar teste; os casos de acesso e isolamento já existem em `access()`); `frontend/src/features/portal/PortalAgendaPage.test.tsx` (ajustar fixture `lesson` com `teacherName`).
- Risco: `teacher` tem foto, graduação, telefone e e-mail; a consulta não pode trazê-los (R2); o teste CA3 guarda isso.
- Segurança: rota já exige perfil do portal e termo aceito; nada muda no acesso.

## 5. Tarefas

- [x] T1: back-end: `teacherName` no `PortalSchedule` e no OpenAPI. Cobre CA1, CA2, CA3, CA4, CA6. Prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `./backend/mvnw -f backend/pom.xml verify -Dit.test=PortalScheduleIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`.
- [x] T2: teste `PortalScheduleIT`: nome do titular e do substituto; professor com foto, graduação, telefone e e-mail preenchidos e a resposta sem nenhum deles nem o id; aluno sem matrícula na turma não vê a aula. Cobre CA1 a CA4. Mesma prova da T1.
- [x] T3: front: tipo, `PortalLessonItem`, textos pt-BR, grade/lista se preciso, handler MSW e teste da tela. Cobre CA5, CA6. Prova: `npm test -- PortalAgendaPage` e `npm run lint && npm run typecheck` em `frontend/`.

## 6. Decisões

- D1: `teacherName` é o professor que dá a aula (`lesson.teacher_id`), que já é o substituto quando há; não se expõe o titular separado nem o id. Motivo: a issue pede "o professor da aula e do substituto" e o resumo precisa só do nome de quem dá a aula. Alternativa: campo separado para o titular, descartada por não ser pedida.
- D2: aula cancelada também mostra o nome (a tela já mostra os demais dados dela). Alternativa: ocultar; sem ganho de privacidade, pois o aluno é o mesmo matriculado.

## Construção

- T1 — arquivos: `backend/.../student/PortalSchedule.java` (record `Lesson.teacherName`, join `teacher` só com `t.name`, índice 20), `docs/openapi.yaml` (campo obrigatório e descrição da rota) — prova: `OpenApiContractTest,ContractRoutesTest` → 7 testes, BUILD SUCCESS (1ª rodada falhou por YAML: vírgula em descrição de mapa de fluxo; corrigido com aspas)
- T2 — arquivos: `PortalScheduleIT.java` (novo `teacherNameOnly`; asserção antiga que proibia "teacher"/"professor" na resposta trocada, pois o campo agora existe) — prova: `verify -Dit.test=PortalScheduleIT` → 7 testes, 0 falhas (o JaCoCo do módulo reprova só porque a rodada é parcial)
- T3 — arquivos: `usePortalSchedule.ts`, `PortalLessonItem.tsx` (linha "Professor: X"; resumo "Professor substituto: X"; grade e lista reutilizam o componente), `pt-BR.json` (`teacher`, `substituteName`; `substitute` removida), `handlers.ts` (MSW), `PortalAgendaPage.test.tsx` — prova: `npm test -- PortalAgenda PortalStates agenda.msw` → 56 passam; `npm run lint` e `npm run typecheck` → 0
Regressão: não rodei `verify` completo, `test:cov` nem `e2e` nesta sessão (só as provas do item; axe da tela está em `PortalAgendaPage.test.tsx`, que passou).
Desvios: nenhum além do ajuste da asserção antiga.
- Ajuste extra: `portal-schedule.msw.test.ts` proibia `teacherName` no contrato; agora exige `teacherName` string (4 testes passam).

## Verificação

### Rodada 1 — OK — 2026-10-28, estado 3109c9e4a8048b57b851cc43b0edba0701710fd8

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | PortalScheduleIT.teacherNameOnly (7 testes, 0 falhas) |
| CA2 | PASSOU | mesmo teste: substituto com substitute=true |
| CA3 | PASSOU | mesmo teste: resposta sem id, foto, e-mail, telefone, bio; SQL seleciona só t.name |
| CA4 | PASSOU | teste da aula de outra turma ausente + casos de acesso existentes em PortalScheduleIT |
| CA5 | PASSOU | PortalAgendaPage.test.tsx: "Professor: ..." e "Professor substituto: ..." (lista e grade reutilizam PortalLessonItem) |
| CA6 | PASSOU | OpenApiContractTest+ContractRoutesTest 7 ok; vitest 60 ok com axe; lint e typecheck sem erro |

Comandos: contrato (7 ok); verify -Dit.test=PortalScheduleIT (7 ok; BUILD FAILURE só pelo JaCoCo da rodada parcial); vitest PortalAgenda/PortalStates/agenda.msw/portal-schedule (60 ok); npm run lint e typecheck (0); npm run test:cov (160 arquivos, 1405 testes ok).
Não rodados: verify completo do back-end e e2e (o item só toca PortalSchedule e sua tela; o CI cobre).
Achados:
- sugestão, código, PortalScheduleIT — a asserção "doesNotContain(... "email", "phone")" depende de nomes de campo; aceitável.

## Entrega

- O que muda: a agenda do portal do aluno mostra o nome do professor em cada aula ("Professor: X"; "Professor substituto: X" quando a aula tem substituto). O back-end devolve só `teacherName` (nome, sem id, foto, e-mail, telefone ou bio), e a lista e a grade usam o mesmo componente.
- Verificado: rodada 1 OK no estado `3109c9e4a8048b57b851cc43b0edba0701710fd8` (critérios CA1 a CA6); contrato OpenAPI (7 testes), `PortalScheduleIT` (7 testes), vitest das telas e do MSW, `lint`, `typecheck` e `test:cov` (1405 testes) verdes.
- Regressão: `verify` completo do back-end e `e2e` não rodaram nesta entrega; o item só toca `PortalSchedule`, sua tela e o contrato, e o CI cobre o restante.
- Decisão: o nome do professor vem só de `t.name` (RN-POR-06a da #404), sem outros dados do cadastro.
- Limitação: `teacherName` é obrigatório e sem nulo no contrato; a consulta junta o professor (`join teacher`) e o caso de aula sem professor cadastrado não tem teste próprio nesta entrega.
