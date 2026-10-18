# 34 — RF-ORG-07 · Desativar unidade

Origem: issue #34 (Sprint 03); `docs/especificacao/01-organizacao-e-isolamento.md` (RF-ORG-07, RN-ORG-11), `02-contas-e-acesso.md` (RN-ACE-21), `00-visao-geral.md` (RNF-26). Branch: `item/34-desativar-unidade`.

## 1. Objetivo

Permitir que o Coordenador desative uma unidade a partir de Configurações › Unidades, vendo antes o que impede (alunos com modalidade ativa, turmas ativas), com confirmação digitada e senha. Unidade desativada some dos seletores e fica só para consulta de histórico.

Fora do escopo: reativar unidade; transferir alunos ou encerrar turmas (itens de matrícula e turmas); regra RN-ORG-07 de "último Administrador" (não bloqueia desativar a unidade); consulta da auditoria (#35).

## 2. Requisitos

- R1 (RN-ORG-11): `POST /api/v1/units/{id}/deactivation` desativa a unidade da organização do vínculo ativo (`unit.active = false`); só o Coordenador (nível `FULL` em `UNITS`); demais perfis recebem 403 sem efeito.
- R2 (RN-ORG-11): havendo aluno com modalidade ativa ou turma ativa na unidade, a rota responde 409 `UNIT_HAS_BLOCKERS` com as contagens (`activeStudents`, `activeClasses`) e nada muda (D1).
- R3 (RF-ORG-07): `GET /api/v1/units/{id}/deactivation-check` devolve `{ activeStudents, activeClasses, lastActiveUnit, canDeactivate }`, mesmas regras de acesso de R1, para a tela mostrar o que impede.
- R4 (RN-ORG-11, RNF-26): a rota exige confirmação digitada igual ao nome da unidade (sem diferenciar maiúsculas, sem espaços nas pontas); outra coisa é 400 `VALIDATION_ERROR` no campo `confirmation` e nada muda (D2).
- R5 (RN-ACE-21): a rota exige o token de uso único da ação `DEACTIVATE_UNIT` em `X-Reauth-Token`; sem token válido, 403 `REAUTH_REQUIRED` e nada muda; o token é consumido por último, depois das demais validações.
- R6 (RN-ORG-04): a única unidade ativa da organização não pode ser desativada: 409 `UNIT_HAS_BLOCKERS` com `lastActiveUnit = true` (D3).
- R7: unidade já desativada responde 409 `CONFLICT`; unidade inexistente ou de outra organização responde 404, igual (D4).
- R8 (RN-ORG-11, "histórico para consulta"): a desativação não apaga nada; a unidade segue em `GET /units` e `GET /units/{id}` com `active = false`, e deixa de aparecer em `GET /units/reachable` (já filtra por `active`).
- R9 (auditoria): a desativação grava `unit.deactivated` com antes e depois (`active`), sem senha nem token; recusas não gravam nada.
- R10: a bloqueio por alunos e turmas passa por uma porta (`UnitDeactivationBlockers`) com versão que devolve zero até existirem as tabelas de matrícula e turma (D1).
- R11 (RF-ORG-07): a lista de unidades mostra "Desativar" por unidade ativa, só ao Coordenador; a janela mostra o que impede (contagens e aviso de unidade única), e só habilita a confirmação quando nada impede; pede o nome da unidade digitado e depois a janela de senha (`ReauthDialog`); só então chama a rota.
- R12: a tela tem os quatro estados, funciona a partir de 360 px, passa no axe nos dois temas e os textos ficam em pt-BR.
- R13: as duas rotas entram em `docs/openapi.yaml` e nos handlers MSW batendo com ele.

## 3. Critérios de aceite

- CA1 (R1, R8): dado o Coordenador e uma unidade ativa sem impedimentos, havendo outra unidade ativa, quando chama a rota com token e confirmação, então 204, `active = false`, e a unidade continua em `GET /units/{id}` e some de `GET /units/reachable`.
- CA2 (R1): dado Administrador, Professor ou conta do portal, quando chama qualquer das duas rotas, então 403 e nada muda.
- CA3 (R2, R10): dado a porta devolvendo 2 alunos e 1 turma ativos, quando o Coordenador chama a rota, então 409 `UNIT_HAS_BLOCKERS` com `activeStudents = 2` e `activeClasses = 1`, a unidade segue ativa e o token de reauth não é consumido.
- CA4 (R3): dado a mesma situação, quando chama `deactivation-check`, então 200 com as contagens e `canDeactivate = false`; sem impedimentos, `canDeactivate = true`.
- CA5 (R4): dado confirmação em branco ou diferente do nome da unidade, então 400 `VALIDATION_ERROR` em `confirmation` e nada muda; o nome com outra caixa e espaços nas pontas é aceito.
- CA6 (R5): dado ausência de token, token de outra ação, de outra sessão, vencido ou já usado, então 403 `REAUTH_REQUIRED` e nada muda; o mesmo token não serve duas vezes.
- CA7 (R6): dado a organização com uma só unidade ativa, quando o Coordenador tenta desativá-la, então 409 `UNIT_HAS_BLOCKERS` com `lastActiveUnit = true` e nada muda.
- CA8 (R7): dado unidade já desativada, então 409 `CONFLICT`; dado id de unidade de outra organização ou inexistente, então 404 nas duas rotas, e a unidade da outra organização segue ativa.
- CA9 (R9): dado desativação concluída, então há um registro `unit.deactivated` da unidade com antes e depois e sem senha ou token; dado qualquer recusa, nenhum registro.
- CA10 (R11): dado a lista de unidades, quando o Coordenador abre "Desativar", então a janela consulta `deactivation-check` e mostra o que impede; com impedimento, o botão de continuar fica desabilitado; sem impedimento, habilita ao digitar o nome e abre a janela de senha; só então chama a rota com `X-Reauth-Token`.
- CA11 (R11, R12): dado sucesso, então a janela fecha, a lista recarrega com a unidade como desativada, sem o botão, e aparece aviso; dado 409 mostra o que impede; dado 400 mostra a mensagem no campo; dado erro de rede, estado de erro com nova tentativa; dado 403, estado sem permissão.
- CA12 (R12): dado Administrador, o botão não aparece; dado 360 px e os temas claro e escuro, sem rolagem horizontal e sem violações do axe.
- CA13 (R13): os testes de contrato e dos handlers MSW passam com as rotas novas.

## 4. Plano técnico

- Dados: não muda (a coluna `unit.active` existe desde V3). Seed não muda.
- API: `GET /api/v1/units/{id}/deactivation-check` (200) e `POST /api/v1/units/{id}/deactivation`, corpo `{ confirmation }`, cabeçalho `X-Reauth-Token`, 204. Erros: 400 `VALIDATION_ERROR` com `fields`, 403 (perfil ou `REAUTH_REQUIRED`), 404, 409 `CONFLICT` e 409 `UNIT_HAS_BLOCKERS` (código novo em `ErrorCode`, com `messages.properties`; contagens no corpo do erro, ver `ApiError`). Ordem: permissão, localizar unidade (404), já desativada, confirmação, impedimentos, última unidade ativa, `CriticalActionGuard.consume`, e então a transação da organização trava a unidade (`for update`), reconfere impedimentos e unidades ativas (para corrida) e atualiza `active`.
- Interface: botão "Desativar" por unidade ativa em `UnitsPage` (só Coordenador); janela `DeactivateUnitDialog` (consulta, impedimentos, nome digitado), `ReauthDialog` com `DEACTIVATE_UNIT`, recarga da lista e aviso. Estados: carregando (consulta/envio), erro, sem permissão; "vazio" não se aplica a uma janela de ação (a lista já os tem).
- Segurança: Coordenador apenas, no servidor; reauth de uso único; unidade resolvida só dentro da organização do contexto (RLS + filtro `organization_id`); 404 igual para outra organização; nada de senha ou token na auditoria.
- Arquivos: a criar `backend/src/main/java/br/com/ctcore/organization/UnitDeactivation.java`, `UnitDeactivationBlockers.java` (porta + versão zero), `frontend/src/features/units/DeactivateUnitDialog.tsx` (+ teste), `UnitDeactivationIT`; a alterar `UnitController.java`, `api/error/ErrorCode.java`, `messages.properties`, `UnitList.java` (o `activeStudents` passa a usar a porta), `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts` (+ teste MSW), `UnitsPage.tsx`, `frontend/src/i18n/pt-BR.json`.
- Reuso: `CriticalAction.DEACTIVATE_UNIT`, `CriticalActionGuard`, `ReauthDialog`, `Permissions`/`UNITS`, `OrganizationTransactions`, `AuditService`, `UnitBase` (base dos ITs de unidade), `UnitScope` (já filtra por `active`), padrão de `CoordinationTransfer` e `TransferCoordinationDialog`.
- Riscos: sem tabelas de aluno e turma o bloqueio só é provado com a porta substituída nos testes; quando as tabelas chegarem, a versão real da porta precisa ser trocada (marcada com `ponytail:`); duas desativações simultâneas das duas últimas unidades (cobertas pela trava e reconferência na transação).

## 5. Tarefas

- [x] T1: porta `UnitDeactivationBlockers` (versão zero), código `UNIT_HAS_BLOCKERS` e mensagem — não cobre critério: base de CA3, CA4 e CA7 — prova: `./backend/mvnw -f backend/pom.xml compile`
- [x] T2: serviço `UnitDeactivation` (check e desativação: permissão, localização, confirmação, impedimentos, última unidade, reauth, trava, auditoria) e rotas no `UnitController`; `UnitList` usa a porta — cobre CA1, CA3, CA4, CA5, CA7, CA8, CA9 — prova: `UnitDeactivationIT`
- [x] T3: testes de rota: perfis, reauth, isolamento entre organizações, saída de `reachable` e histórico preservado — cobre CA1, CA2, CA6, CA8 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=UnitDeactivationIT`
- [x] T4: OpenAPI e handlers MSW das duas rotas — cobre CA13 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npm test` (teste MSW)
- [x] T5: `DeactivateUnitDialog`, botão em `UnitsPage`, textos pt-BR, recarga da lista — cobre CA10, CA11, CA12 — prova: testes de tela com axe nos dois temas a 360 px (`npm test`)
- [x] T6: regressão completa e cobertura — não cobre critério: confere o "Pronto quando" — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov`

## 6. Decisões

- D1 (muda o comportamento): impedimentos vêm de uma porta `UnitDeactivationBlockers` que hoje devolve zero alunos e zero turmas, porque as tabelas de matrícula e turma só nascem em sprints seguintes (mesmo atalho já usado em `UnitList.activeStudents`). A regra, o código de erro e a tela ficam prontos e testados com a porta substituída; a versão real entra junto com as tabelas. Alternativa descartada: criar tabelas provisórias de aluno e turma (fora do item).
- D2: a confirmação digitada é o nome da unidade, validada no servidor e na tela. Motivo: o canvas (`UnidadeDesativar.dc.html`) não está no repositório; digitar o nome é o padrão para ação destrutiva e distingue de qualquer outra janela. Alternativa: palavra fixa `DESATIVAR`, como na transferência de coordenação.
- D3 (muda o comportamento): a única unidade ativa da organização não pode ser desativada. Motivo: RN-ORG-04 diz que toda organização nasce com uma unidade e o seletor e o resto da aplicação supõem ao menos uma ativa. Alternativa descartada: permitir e deixar a organização sem unidade operacional.
- D4: unidade de outra organização responde 404 como inexistente (padrão de `UnitSettings`); já desativada responde 409 `CONFLICT`. Reativar fica para item futuro, se a especificação pedir.
- D5: a desativação não mexe nos vínculos de Administrador da unidade nem em arquivos; "histórico para consulta" é mantido deixando tudo como está. Alternativa: encerrar vínculos (perde a informação de quem administrava).
- Suposição: o desenho do canvas não pôde ser consultado; a janela segue `ReauthDialog`/`Modal` e `TransferCoordinationDialog`. Se divergir, ajusta-se só a tela.
- Suposição: a lista `GET /units` continua devolvendo unidades desativadas (a tela já mostra "Desativada"); se o canvas pedir ocultá-las por padrão, muda só a tela.

## Construção

- T1 — arquivos: `UnitDeactivationBlockers.java` (porta + versão zero), `ErrorCode.java`, `messages.properties`, `ApiStatusException.java` e `ApiExceptionHandler.java` (campos opcionais no erro de status, usados para as contagens) — prova: `./backend/mvnw -f backend/pom.xml compile` → ok
- T2 — arquivos: `UnitDeactivation.java`, `UnitController.java`, `UnitList.java` (usa a porta) — prova: `UnitDeactivationIT` → 7 testes passam
- T3 — arquivos: `UnitDeactivationIT.java` (perfis, reauth, isolamento, reachable, histórico, última unidade, auditoria) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=UnitDeactivationIT` → 7 testes, 0 falhas
- T4 — arquivos: `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/units.msw.test.ts` — prova: `verify` (inclui `OpenApiContractTest` e `ContractRoutesTest`) e `npm run test:cov` (teste MSW) → passam
- T5 — arquivos: `DeactivateUnitDialog.tsx` (+ `.test.tsx`), `UnitsPage.tsx`, `useUnits.ts` (`refresh`), `pt-BR.json` — prova: `npx vitest run src/features/units` → 9 testes novos passam, com axe a 360 px nos dois temas
- T6 — prova: `./backend/mvnw -f backend/pom.xml verify` → 192 testes de integração, 0 falhas, BUILD SUCCESS (JaCoCo ok); em `frontend/`: `npm run lint`, `npm run typecheck`, `npm run build` ok, `npm run test:cov` → 51 arquivos, 362 testes passam, limites de cobertura atendidos
Regressão: backend `verify` e frontend lint/typecheck/build/test:cov verdes. `npm run e2e` não rodou nesta sessão (a mudança não toca fluxos `@critical`).
Desvios: (1) contagens do 409 `UNIT_HAS_BLOCKERS` vão em `fields` do erro (texto), sem alterar o formato de `ApiError`; (2) a porta e a versão zero ficam no mesmo arquivo (classe aninhada `None`); (3) o botão "Desativar" tem nome acessível "Desativar <unidade>" via texto só para leitor de tela, como os demais botões da lista.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-18, estado bf07dd859bff51ad4bc76c85c96e8a57f2a66882

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | UnitDeactivationIT.deactivates (verify verde) |
| CA2 | PASSOU | UnitDeactivationIT.profiles |
| CA3 | PASSOU | UnitDeactivationIT.blocked |
| CA4 | PASSOU | UnitDeactivationIT.blocked |
| CA5 | PASSOU | UnitDeactivationIT.confirmation e deactivates |
| CA6 | PASSOU | UnitDeactivationIT.reauth |
| CA7 | PASSOU | UnitDeactivationIT.lastUnit |
| CA8 | PASSOU | UnitDeactivationIT.isolation (404); 409 CONFLICT lido no código, ver sugestão |
| CA9 | PASSOU | UnitDeactivationIT.deactivates |
| CA10 | PASSOU | DeactivateUnitDialog.test.tsx |
| CA11 | PASSOU | DeactivateUnitDialog.test.tsx |
| CA12 | PASSOU | DeactivateUnitDialog.test.tsx (axe, 360 px, dois temas) |
| CA13 | PASSOU | verify (OpenApiContractTest, ContractRoutesTest) e teste MSW |

Comandos: backend verify (exit 0); frontend lint, typecheck, build, test:cov (exit 0, linhas 97%). e2e não rodado (não toca fluxo critico).
Achados:
- sugestão, código, UnitDeactivationIT — não localizei teste do 409 CONFLICT para unidade já desativada (CA8).

## Entrega

Desativar unidade (RF-ORG-07): rota de desativação com confirmação digitada do nome, reautenticação e auditoria; bloqueia a única unidade ativa e unidades com alunos ou turmas ativos (409 `UNIT_HAS_BLOCKERS`, com contagens); janela de confirmação na lista de unidades; OpenAPI e handlers MSW atualizados.

Verificação: rodada 1 OK (backend `verify` e frontend lint, typecheck, build, test:cov verdes). `npm run e2e` não rodou (não toca fluxo `@critical`).

Decisões e limitações: os impedimentos usam uma porta que hoje devolve zero alunos e zero turmas (as tabelas nascem em sprints seguintes); o 409 `CONFLICT` de unidade já desativada não tem teste dedicado (sugestão da verificação). Detalhes em "Decisões".

Closes #34
