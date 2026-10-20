# 50 — RF-AGE-02 · Painel da aula

Origem: issue #50 (Sprint 05); `docs/especificacao/06-turmas-e-agenda.md` (RF-AGE-02, RN-AGE-07, RN-AGE-08, "Dados"), `docs/especificacao/05-professores.md` (RN-PRO-06). Branch: `item/50-painel-da-aula`. Parte da aula (`lesson`, V25), de `Agenda` e `LessonCard` (#49) e do painel da turma (#47).

## 1. Objetivo

Ao clicar numa aula da agenda, Coordenador, Administrador e Professor abrem o painel dela: horário, turma, modalidade, professor (com selo "Substituição" e o titular quando é substituição), número de alunos, aulas experimentais marcadas e grupo de WhatsApp, com as ações Avisar turma, Editar aula, Cancelar aula e Ver turma. O servidor decide quem vê (RN-PRO-06: substituto só no dia da aula) e quem pode editar e cancelar (RN-AGE-07).

Fora do escopo: janelas Editar e Cancelar aula e a troca de professor (#51; aqui os botões aparecem conforme `canEdit`/`canCancel` e ficam desativados), aula avulsa (#52), dias sem aula (#53), comunicados (Sprint 09; "Avisar turma" desativado), lista de alunos (Sprint 06), aulas experimentais reais (Sprint 18; o número vem 0 num único ponto), validar que o substituto ensina a modalidade (acontece ao gravar a substituição, em #51). Não há recurso liberado aos poucos.

## 2. Requisitos

- R1 (RF-AGE-02): `GET /api/v1/agenda/lessons/{id}` devolve a aula com `id`, `date`, `startsAt`, `endsAt`, `status` (RN-AGE-03, calculado como na lista), `cancelReason`, `class`, `unit`, `modality`, `teacher` (professor da aula), `head` (titular da turma, só quando `substitute`), `substitute`, `activeStudents`, `trialClasses`, `groupLink`, `canEdit`, `canCancel` e `canViewClass`.
- R2 (RF-AGE-02): `activeStudents` e `trialClasses` vêm de um ponto único cada, hoje 0 (Sprints 07 e 18); `activeStudents` reusa `TrainingClasses.activeStudents`.
- R3 (RN-AGE-07, RN-AGE-08): `canEdit` e `canCancel` usam `Agenda.can`, as mesmas regras da lista; o Professor só os tem verdadeiros com `AGENDA_EDIT` efetivo e em aula de turma sua (titular ou auxiliar) ou em que é o professor da aula.
- R4 (RN-PRO-06): o professor da aula que não é da turma (substituto, de qualquer unidade da organização) só abre o painel de `substitute = true` do início do dia da aula até o fim desse dia, no fuso da unidade; fora disso, 404. Titular e auxiliar abrem sempre.
- R5 (permissão): `AGENDA_VIEW` conferida no servidor: Coordenador abre qualquer aula da organização; Administrador só de unidade dele (outra: 404); Professor só as aulas de R3/R4 (as demais: 404); Aluno e Responsável 403; sem sessão 401; aula inexistente ou de outra organização 404; id inválido 400.
- R6 (ações): `canViewClass` é verdadeiro só para quem tem `CLASSES_MANAGE` (Coordenador; Administrador com a unidade no alcance); "Ver turma" leva a `/turmas/:id`. "Avisar turma" fica desativado (Sprint 09). "Editar aula" e "Cancelar aula" só aparecem quando `canEdit`/`canCancel` e ficam desativados com a indicação de que chegam em #51.
- R7 (isolamento): organização só da sessão; filtro no código e RLS já existente em `lesson`; nenhum dado de outra organização.
- R8 (leitura): a rota não altera dado, então não grava auditoria; resposta sem dado pessoal além do nome do professor.
- R9 (tela): `/agenda/aulas/:id` mostra o painel; cada aula da grade e da lista do dia da agenda (#49) vira link para ele; cancelada mostra "Cancelada" e o motivo; substituição mostra o selo "Substituição" e o titular; grupo abre em nova aba só se o link for seguro (como `ClassPanelPage`); "Voltar à agenda".
- R10 (tela, geral): quatro estados (carregando, erro com "Tentar de novo", sem permissão, e "Aula não encontrada" no lugar do vazio), 360 px sem rolagem horizontal, axe nos dois temas, textos em `pt-BR.json`, cor nunca é a única indicação.
- R11 (contrato): `docs/openapi.yaml` (`/agenda/lessons/{id}`, `LessonDetail`) e handler MSW no formato do servidor; sem migração.
- R12 (RN-AGE-13, RN-TUR-06): nenhum campo ou texto novo cita lista de espera, presença ou frequência.

## 3. Critérios de aceite

- CA1 (R1, R2): dado aula futura de turma com grupo, quando o Coordenador chama a rota, então 200 com os campos de R1, `activeStudents` 0, `trialClasses` 0, `head` nulo e `substitute` falso.
- CA2 (R1): dado aula com `substitute = true` e professor diferente do titular, então `head` traz o titular e `teacher` o substituto; aula cancelada traz `status` `CANCELLED` e o motivo; aula em andamento ou concluída (relógio fixado) traz o `status` certo.
- CA3 (R3): dado aula futura, concluída há 5 dias, concluída há 40 dias e cancelada, então os `canEdit`/`canCancel` batem com os da lista (#49/CA8); Professor sem `AGENDA_EDIT` ou de turma alheia (mas com a aula visível por R4) tem ambos falsos; Professor com `AGENDA_EDIT` em aula da turma dele tem ambos verdadeiros.
- CA4 (R4): dado substituto de outra unidade, quando chama a rota na véspera, então 404; no dia da aula (mesmo antes do horário e depois do término), 200; no dia seguinte, 404; o titular da turma abre a aula em qualquer dia; unidade em outro fuso muda o dia no instante dela.
- CA5 (R5): dado Aluno e Responsável, então 403; sem sessão 401; Administrador de A com aula de unidade fora do alcance 404; Professor com aula de turma de outro professor 404; id de aula de B pela sessão de A 404; id que não é UUID 400.
- CA6 (R6): dado Coordenador e Administrador da unidade, então `canViewClass` verdadeiro; Professor, falso. Na tela, "Ver turma" só aparece com `canViewClass` e leva a `/turmas/:id`; "Avisar turma" aparece desativado; "Editar aula"/"Cancelar aula" aparecem só quando permitidos e desativados.
- CA7 (R7): dado duas organizações, então a sessão de A não vê aula de B por id; com `ctcore_app` sem escopo, nenhuma linha de `lesson` de B é visível (RLS de V25).
- CA8 (R8, R12): dado o esquema, o corpo bruto e `pt-BR.json`, então nada novo menciona lista de espera, presença ou frequência; a chamada não cria registro de auditoria.
- CA9 (R9): dado a agenda com aulas, quando clica numa aula (grade ou lista de 360 px), então abre `/agenda/aulas/:id` com horário, turma, modalidade, professor, alunos, experimentais, grupo e as ações; aula substituída mostra "Substituição" e o titular; cancelada mostra "Cancelada" e o motivo; "Voltar à agenda" retorna.
- CA10 (R10): dado carregando, erro 500 (com "Tentar de novo" que refaz), 403 e 404, então cada um aparece com seu texto; 360 px sem rolagem horizontal da página; axe sem violações nos temas claro e escuro.
- CA11 (R11): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota nova; teste MSW cobre `/agenda/lessons/{id}` no formato do servidor.
- CA12 (R9): ponta a ponta `@critical` já existente da Agenda estendido: o Coordenador clica numa aula e vê o painel dela.

## 4. Plano técnico

- Dados: não muda (sem migração; usa `lesson`, `training_class`, `teacher`, `unit` de V23 a V25). `activeStudents` e `trialClasses` são pontos únicos que devolvem 0.
- API: `GET /api/v1/agenda/lessons/{id}` (200 `LessonDetail`; 400, 401, 403, 404), cookie como as demais, só leitura; entra em `docs/openapi.yaml` no mesmo PR.
- Back-end (pacote `br.com.ctcore.agenda`): acrescentar `lesson(who, id)` e o record `LessonDetail` a `Agenda.java`, reusando `status`, `can` e o cálculo de "mine" da lista; acrescentar a regra de R4 como método estático testável (`substituteWindow`); alterar `AgendaController.java` (nova rota). `TrainingClasses.activeStudents` hoje é package-private: expor por método público fino (ex.: `activeStudentsOf`) ou equivalente, sem mudar o comportamento. `canViewClass` vem de `Permissions.effective(who).get(CLASSES_MANAGE)` e do alcance do Administrador.
- Interface: a criar `frontend/src/features/agenda/LessonPanelPage.tsx`, `useLesson.ts` e `LessonPanelPage.test.tsx`; a alterar `LessonCard.tsx` (vira link para `/agenda/aulas/:id`), `App.tsx` (rota `/agenda/aulas/:id` para os mesmos perfis da `/agenda`), `mocks/handlers.ts` (+ teste em `mocks/agenda.msw.test.ts`), `i18n/pt-BR.json` (`agenda.panel.*`), `e2e/agenda.spec.ts`. Reusa `Card`, o padrão de estados e `safeLink` de `ClassPanelPage.tsx`, `modalityHue` e `agenda.substitute`/`agenda.cancelled`.
- Segurança: perfil, organização e alcance do principal; parâmetros ligados; 404 sem revelar se a aula existe; Professor filtrado no servidor, nunca só no front; `canEdit`/`canCancel` são dicas, e #51 confere de novo ao gravar.
- Reuso: `Agenda.status/can`, `Permissions`, `OrganizationTransactions`, `TrainingClasses.Ref`, `ApiStatusException`, `AgendaBase` (testes), contrato MSW de `/agenda`.
- Riscos: (1) o substituto de outra unidade não vê a unidade da aula na lista da agenda (que filtra pelo alcance do usuário); o painel resolve pelo id e não depende disso, e a lista fica para #51, que cria a substituição; (2) fuso: o "dia da aula" é o da unidade; (3) `canViewClass` errado exporia o link a quem recebe 403 em `/turmas/:id`, percebido pelo teste de perfil.

## 5. Tarefas

- [x] T1: `lesson(who, id)` e `LessonDetail` em `Agenda`, regra de R4, método público de alunos em `TrainingClasses`, rota no `AgendaController`, com testes (`AgendaLessonIT`: campos, substituição, situações, `canEdit`/`canCancel`, janela do substituto, perfis, 400/403/404, isolamento e RLS, ausência de lista de espera/presença; teste unitário da janela) — cobre CA1, CA2, CA3, CA4, CA5, CA6 (flag), CA7, CA8 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AgendaLessonIT` e `test -Dtest='Agenda*Test'`.
- [x] T2: OpenAPI (`/agenda/lessons/{id}`, `LessonDetail`) e handler MSW com teste — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T3: `useLesson`, `LessonPanelPage`, rota, textos `agenda.panel.*`, quatro estados, ações de R6 — cobre CA6 (tela), CA9, CA10 — prova: `npx vitest run src/features/agenda` (inclui 360 px e axe nos dois temas).
- [x] T4: `LessonCard` como link para o painel (grade e lista do dia) e extensão do e2e `@critical` da Agenda — cobre CA9, CA12 — prova: `npx vitest run src/features/agenda` e `npm run e2e`.
- [x] T5: regressão completa (não cobre critério: confere cobertura e os 100% nos pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): RN-PRO-06 vira regra de leitura do painel: o substituto só abre a aula do início do dia dela até o fim desse dia, fora disso 404; a lista da agenda (#49) segue mostrando a aula dele. Alternativa: aplicar a janela também à lista; descartada porque o professor precisa ver na agenda o que vai cobrir, e a regra cita "a turma e os alunos dela", que só o painel mostra.
- D2 (muda o comportamento): "Editar aula" e "Cancelar aula" aparecem só quando `canEdit`/`canCancel`, ou seja, o Professor sem permissão ou sem aula sua não os vê (RN-AGE-07); ficam desativados até #51, como "Editar" em #47/D-janela. Alternativa: esconder sempre; descartada porque o card os pede no painel.
- D3: nova rota `GET /agenda/lessons/{id}` em vez de reusar `/agenda`, pois o painel precisa de grupo, titular e contagens que a lista não traz e a abre por link direto. Alternativa: passar a aula pelo estado da navegação; descartada por quebrar link e recarga.
- D4: `trialClasses` e `activeStudents` valem 0 até as Sprints 18 e 07, em um ponto único cada, como #47/D5; a tela mostra "0". Suposição: se o dono quiser ocultar o bloco sem dados, é ajuste de texto.
- D5: "Ver turma" usa `canViewClass` do servidor, pois o Professor não tem `CLASSES_MANAGE` e `/turmas/:id` lhe daria 403; ele já vê no painel da aula o que RN-PRO-06 permite. Alternativa: derivar do perfil no front; descartada (a barreira é do servidor).
- D6: o painel mostra `head` (titular) só quando há substituição, para o selo "Substituição" fazer sentido; fora dela o titular é o próprio professor da aula.
- D7: o canvas `AgendaPainel.dc.html` não está acessível aqui; a tela segue os padrões do projeto (#47) e o dono confere o layout no PR (como #46/D8, #48/D9, #49/D11).
- D8: a leitura do painel não gera auditoria, pois a regra do projeto exige auditoria só para ação que altera dado.

Fora do item (sugestão para #51): o substituto de outra unidade precisa aparecer na lista da agenda (hoje filtrada pelo alcance de unidades) e a validação "ensina a modalidade" da RN-PRO-06 deve ser feita ao gravar a substituição.

## Construção

- T1 — arquivos: `backend/.../agenda/Agenda.java` (`lesson`, `LessonDetail`, `substituteWindow`, `trialClasses`), `AgendaController.java`, `trainingclass/TrainingClasses.java` (`activeStudentsOf`), `AgendaLessonIT.java` (novo), `AgendaRulesTest.java` (janela) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AgendaLessonIT -Dtest='Agenda*Test'` → 5 IT e 5 unitários verdes.
- T2 — arquivos: `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts` (`mockLesson` extraído da lista e rota `/agenda/lessons/{id}`), `frontend/src/mocks/agenda.msw.test.ts` — prova: `OpenApiContractTest,ContractRoutesTest` → 7 verdes; `npx vitest run src/mocks` → verde.
- T3 — arquivos: `useLesson.ts`, `LessonPanelPage.tsx`, `LessonPanelPage.test.tsx` (novos), `App.tsx`, `i18n/pt-BR.json` (`agenda.panel.*`) — prova: `npx vitest run src/features/agenda src/mocks` → 18 arquivos, 112 testes verdes (360 px, axe claro e escuro).
- T4 — arquivos: `LessonCard.tsx` (cartão vira link), `e2e/agenda.spec.ts` (painel no `@critical`) — prova: `npm run e2e` → 27 passaram.
- T5 — prova: `./backend/mvnw -f backend/pom.xml verify` → 338 testes verdes, "All coverage checks have been met"; em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → 622 testes verdes, e2e 27 verdes.

Regressão: sem falhas. Na primeira rodada de `test:cov`, `TwoFactorLogin.test.tsx` falhou por tempo (carregando) sob carga; sozinho passa (13) e a segunda rodada completa passou: instável, não relacionado ao item.
Desvios: a consulta do painel reaproveita a lógica de `view` em vez de extrair método comum (SQL próprio por id); `trialClasses` é método de `Agenda` (ponto único). O teste de "em andamento" usa o relógio real (`assumeTrue` evita a virada do dia), pois os ITs não fixam relógio. O RLS de `lesson` no teste do painel repete o padrão de `LessonGenerationIT`.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 9cd65f56228b84a6662f2eeb982e02163bf873d4

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `AgendaLessonIT.fields` (verify verde) |
| CA2 | PASSOU | `AgendaLessonIT.statusAndFlags`; em andamento usa relógio real com `assumeTrue` (desvio anotado) |
| CA3 | PASSOU | `statusAndFlags` e `teacher` |
| CA4 | PASSOU | `AgendaLessonIT.substituteWindow` e `AgendaRulesTest.substituteWindow` (borda 23:59:59/00:00, Tóquio x São Paulo) |
| CA5 | PASSOU | `accessAndIsolation` |
| CA6 | PASSOU | flag no IT; tela em `LessonPanelPage.test.tsx` |
| CA7 | PASSOU | `accessAndIsolation` (isolamento e RLS) |
| CA8 | PASSOU | IT sem lista de espera/presença; grep em `pt-BR.json` sem ocorrência nova; rota só de leitura |
| CA9 | PASSOU | `LessonPanelPage.test.tsx`, `AgendaPage.test.tsx`, e2e |
| CA10 | PASSOU | testes dos quatro estados, 360 px e axe claro/escuro (test:cov verde) |
| CA11 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` dentro do verify; `agenda.msw.test.ts` |
| CA12 | PASSOU | `npm run e2e`: 27 passaram, inclui Agenda @critical |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify`: 338 testes, 0 falhas, "All coverage checks have been met", BUILD SUCCESS.
- em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`: exit 0; 73 arquivos, 622 testes; e2e 27 passaram.

Achados:
- sugestão, código, `Agenda.java` — import `java.time.Instant` fora da ordem alfabética; e `AgendaRulesTest` usa nomes totalmente qualificados em vez de imports.
- sugestão, código, `Agenda.lesson` — a consulta repete a lógica de `view` (desvio já anotado); extrair método comum quando #51 mexer nela.

## Entrega

Nova rota `GET /agenda/lessons/{id}` e tela Painel da aula (RF-AGE-02): dados da aula, selo de substituição, contagens, "Ver turma" conforme `canViewClass`, "Editar/Cancelar aula" desativados até #51. O substituto só abre a aula no dia dela (RN-PRO-06, 404 fora disso). Cartões da agenda viram link para o painel.

Verificação: `verify` do back-end (338 testes, cobertura ok) e, no front-end, lint, typecheck, build, `test:cov` (622 testes) e e2e (27) verdes; rodada 1 OK.

Decisões D1 a D8 acima. Limitações: `trialClasses` e `activeStudents` valem 0 até as Sprints 18 e 07; o canvas não estava acessível, o dono confere o layout no PR. `TwoFactorLogin.test.tsx` é instável sob carga, não relacionado.
