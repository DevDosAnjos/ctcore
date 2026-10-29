# 88 — RF-POR-01 · Portal: barra e navegação

Origem: issue #88 (Sprint 10 · Portal do aluno); `docs/especificacao/14-portal-do-aluno.md` (RF-POR-01, RN-POR-01, RN-POR-08), `12-inativacao.md` (RN-INA-08, RN-INA-09). Branch: `item/88-portal-barra-navegacao`. Parte do que a #85 e a #86 deixaram: `PortalStudents`, `PortalTerms.requireAccepted`, `RequirePortalTerm`, `AccountFrame` e o cabeçalho solto da `Home` do portal. Os canvas `PortalNav`, `PortalReduzido`, `PortalMTopo` e `PortalMTabs` não são lidos pelo agente (D8).

## 1. Objetivo

Dar ao aluno adulto e ao responsável a moldura única do portal: barra com logo e nome da organização, abas Início, Agenda, Financeiro, Avisos e Minhas informações, sino com os avisos não lidos, e nome e papel de quem está logado; no celular as abas ficam embaixo. O servidor entrega, numa rota só, a marca da organização, o contador do sino e se o portal é reduzido (aluno Inativo ou ex-aluno), e a moldura esconde Agenda e Avisos quando é.

Fora do escopo: o conteúdo das abas (Início #89, carteirinha #90, Agenda #91, Financeiro #92, Avisos #93, Minhas informações #94, estados de tela #95); marcar aviso como lido e a lista de avisos (#93); bloqueio e reativação por atraso, e o prazo de 12 meses do ex-aluno (Sprint 12); PWA e uso sem internet (Sprint 21); esconder os dados de cada rota de conteúdo para o reduzido, que cada item das abas faz com o apoio criado aqui (R4).

## 2. Requisitos

- R1 (RF-POR-01): a barra do portal mostra logo e nome da organização do vínculo ativo, o nome de quem está logado e o papel (Aluno ou Responsável), o sino com o contador de não lidos, o atalho para a conta e o tema; sem logo, só o nome.
- R2 (RF-POR-01): as abas são Início (`/`), Agenda (`/portal/agenda`), Financeiro (`/portal/financeiro`), Avisos (`/portal/avisos`) e Minhas informações (`/portal/minhas-informacoes`); a aba da tela aberta fica marcada como atual. Do computador ao celular é a mesma navegação: abaixo de 768 px (a partir de 360 px) as abas ficam fixas embaixo da tela e de 768 px para cima ficam na barra.
- R3 (RF-POR-01): o sino mostra a contagem de avisos enviados e ainda não lidos para os alunos da conta (99+ acima de 99, sem número em zero) e leva à aba Avisos. A contagem é do servidor.
- R4 (RN-POR-08, RN-INA-08, RN-INA-09, D3): o portal é reduzido quando a conta tem ao menos um aluno matriculado e nenhum deles tem modalidade ativa. No reduzido, Agenda e Avisos não aparecem nas abas, o sino some, abrir essas rotas pelo endereço leva ao Início, e a moldura mostra o aviso de portal reduzido. O servidor expõe a regra num ponto reutilizável (`PortalShell.reduced`) para as rotas de conteúdo.
- R5 (RN-POR-01): a rota `GET /api/v1/portal/shell` considera só os alunos ligados à conta da sessão; contador e situação nunca somam aluno de outra conta nem de outra organização.
- R6 (permissão): a rota é só de Aluno e Responsável; gestão e Professor recebem 403, sem sessão 401; com termo pendente que a conta deve aceitar, 403 `TERM_PENDING` (RN-POR-03).
- R7 (RF-POR-08, padrão de tela): a moldura tem carregando, erro com "Tentar de novo" e sem permissão; funciona a partir de 360 px, passa no axe nos dois temas, a navegação é operável pelo teclado e todo texto vem de `pt-BR.json`.
- R8 (D4): as telas que já existem no portal (Início, Minhas contas, pedidos) passam a usar a moldura nova; as quatro abas ainda sem conteúdo abrem uma página provisória dentro dela.
- R9 (RNF-32): `docs/openapi.yaml` e o handler MSW refletem a rota nova.

## 3. Critérios de aceite

- CA1 (R1, R7): dado Aluno logado, quando o portal abre, então a barra mostra o nome da organização, o logo (quando existe), o nome da pessoa e "Aluno"; dado Responsável, mostra "Responsável"; sem logo não há imagem quebrada.
- CA2 (R2): dado portal aberto em `/`, então as cinco abas aparecem na ordem do card, Início tem `aria-current="page"` e cada aba é um link para o endereço do R2; em `/portal/financeiro` a atual é Financeiro.
- CA3 (R2, R7): dado tela de 360 px, então as abas estão fixas na borda de baixo, não há rolagem horizontal e o conteúdo não fica escondido atrás delas; a 1024 px estão na barra de cima.
- CA4 (R3): dado aluno com dois comunicados enviados para a turma dele, um já lido, quando `GET /portal/shell`, então `unreadAnnouncements` é 1; comunicado agendado (não enviado) e comunicado de turma de que ele não faz parte não contam; comunicado da organização inteira conta.
- CA5 (R3): dado responsável com dois filhos, o comunicado enviado a ambos e lido só por um dos filhos, então conta 1 (um aviso, não dois); lido pelos dois, conta 0.
- CA6 (R3): dado contador 120 na tela, então o sino mostra "99+"; em 0 não mostra número; o rótulo acessível do sino diz a contagem e ele leva a `/portal/avisos`.
- CA7 (R4): dado aluno cujas modalidades todas estão encerradas, quando `GET /portal/shell`, então `reduced` é verdadeiro, `unreadAnnouncements` é 0 e a tela mostra só Início, Financeiro e Minhas informações, sem sino e com o aviso de portal reduzido.
- CA8 (R4): dado responsável com um filho ativo e outro inativo, então `reduced` é falso e as cinco abas aparecem; dado conta de aluno sem matrícula ainda, `reduced` é falso.
- CA9 (R4): dado portal reduzido, quando a pessoa abre `/portal/agenda` ou `/portal/avisos` pelo endereço, então é levada a `/`.
- CA10 (R5): dado a organização B com comunicados e alunos, e a conta X da organização A, quando X chama `GET /portal/shell`, então nada de B entra na resposta; dado outra conta Y da mesma organização, nada do aluno de X entra na resposta de Y; consulta direta ao banco no contexto de A não devolve linha de B (RLS).
- CA11 (R6): dado Administrador, Coordenador ou Professor, quando chama `GET /portal/shell`, então 403 `FORBIDDEN`; sem sessão, 401; dado aluno com termo pendente, 403 `TERM_PENDING`.
- CA12 (R7): dado a moldura carregando, com erro de leitura (botão "Tentar de novo" refaz a chamada) e com 403, então cada estado aparece no lugar do conteúdo, sem quebrar a barra de abas; o axe passa nos dois temas a 360 px; Tab percorre logo, abas, sino, conta e tema, nessa ordem.
- CA13 (R8): dado Início, Minhas contas e pedidos do portal, então todos aparecem dentro da moldura nova (um só cabeçalho); as páginas provisórias mostram o título da aba e um estado vazio com texto próprio.
- CA14 (R9): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota nova; o handler MSW responde no formato do OpenAPI, inclusive o 403 para a gestão e a conta reduzida.

## 4. Plano técnico

- Dados: não muda (sem migração nem seed; só leitura de `announcement`, `announcement_read`, `enrollment_modality`, `organization`).
- API: `GET /api/v1/portal/shell`, só Aluno e Responsável, depois do portão `requireAccepted`. Resposta `{ organization: { name, color, logoUrl|null }, reduced, unreadAnnouncements }`; nome e papel de quem está logado já vêm da sessão (`GET /auth/session`), por isso não entram aqui. `logoUrl` é a URL temporária assinada que a gestão já recebe. Erros: 401, 403 (`FORBIDDEN`, `TERM_PENDING`). Não altera dado, então não audita.
  - `reduced`: a conta tem aluno ligado com matrícula (`enrollment`) e nenhum aluno ligado tem `enrollment_modality.status = 'ACTIVE'` (mesma regra de `Students.STATUS`, `FORMER`).
  - `unreadAnnouncements`: 0 se `reduced`; senão, comunicados com `sent_at` preenchido cujo alvo alcança algum aluno ligado e ativo (`ORGANIZATION`: todos; `UNIT`: alguma modalidade ativa na unidade; `MODALITY`: modalidade ativa do aluno; `CLASS`: turma ativa do aluno) e sem linha em `announcement_read` para ao menos um desses alunos alcançados; conta comunicados distintos.
- Interface: `PortalLayout` (a criar) com barra, abas e sino, usada por `Home` (papel de portal), `AccountFrame` (ramo de portal) e pelas páginas provisórias; `usePortalShell` busca a rota na abertura e a cada 60 s (mesmo ritmo do sino da gestão). Abas: um só `<nav aria-label>` com links; no celular `fixed inset-x-0 bottom-0 border-t` com o conteúdo ganhando `pb` do tamanho da barra, e a partir de `md` dentro da barra de cima. Cor da organização numa faixa na borda da barra; texto sobre ela sempre nas cores do tema (contraste do axe). `PortalSectionPage` (a criar) é a página provisória das quatro abas sem conteúdo (`EmptyState`). Guarda `RequireFullPortal` leva `/portal/agenda` e `/portal/avisos` ao Início quando `reduced`. Quatro estados da moldura: `LoadingState`, `ErrorState` com `onRetry`, `ForbiddenState`.
- Segurança: perfil e vínculo conferidos no servidor (`PortalStudents.cards` como molde e `PortalTerms.requireAccepted`); SQL só com parâmetros, tudo em `OrganizationTransactions.execute` (RLS); a resposta não traz dado de aluno, só marca e contadores. O esconder das abas é conforto; a barreira de cada rota de conteúdo é do servidor, via `PortalShell.reduced`.
- Arquivos a criar: `backend/src/main/java/br/com/ctcore/student/PortalShell.java` (`view(who)` e `reduced(who)`); `backend/src/test/java/br/com/ctcore/student/PortalShellIT.java`; `frontend/src/features/portal/PortalLayout.tsx`, `usePortalShell.ts`, `PortalSectionPage.tsx`, `RequireFullPortal.tsx`, `PortalLayout.test.tsx`; `frontend/src/mocks/portal-shell.msw.test.ts`; `frontend/e2e/portal-barra.spec.ts`.
- Arquivos a alterar (vistos no repositório): `backend/src/main/java/br/com/ctcore/student/PortalStudentController.java` (`GET /shell`); `backend/src/main/java/br/com/ctcore/organization/OrganizationProfile.java` (método público de marca, nome, cor e URL do logo sem exigir `ORGANIZATION_SETTINGS`, reaproveitando `loaded` e `view`); `docs/openapi.yaml` (`/portal/shell` e `PortalShell`); `frontend/src/App.tsx` (`Home` do portal, rotas `/portal/agenda`, `/portal/financeiro`, `/portal/avisos`, `/portal/minhas-informacoes`); `frontend/src/features/account/AccountFrame.tsx` (ramo de portal); `frontend/src/mocks/handlers.ts` (`/portal/shell`); `frontend/src/i18n/pt-BR.json` (grupo `portalShell`).
- Reuso: `PortalStudents` e `PortalTerms.requireAccepted`, `OrganizationTransactions`, `OrganizationProfile` (URL assinada do logo), `Students.STATUS` como regra de situação, `LoadingState`, `ErrorState`, `ForbiddenState`, `EmptyState`, `AccountLink`, `ChooseLinkTrigger`, `ThemeToggle`, o rótulo/contador de `BellButton` (99+) copiado no comportamento, não no componente, porque aquele chama a API da gestão.
- Riscos: (1) `OrganizationProfile.read` hoje exige permissão de gestão; o método novo só devolve nome, cor e logo e é chamado depois de conferir o perfil de portal, para não abrir o resto. (2) Colunas de `announcement`, de `enrollment_modality` (`class_id`, `unit_id`, `modality_id`) e a ligação aluno–turma devem ser conferidas na construção; se a turma do aluno vier de outra tabela, muda só a consulta. (3) `Home` e `AccountFrame` têm testes que contam cabeçalhos ou textos do portal antigo (`app.name`); podem quebrar e precisam de ajuste. (4) A tela a 360 px com abas fixas pode cobrir botões do fim da página; coberto pelo `pb` e pelo e2e.

## 5. Tarefas

- [x] T1: `PortalShell` (`view`, `reduced`, contador), método de marca em `OrganizationProfile`, rota `GET /portal/shell` com portão do termo, e `/portal/shell` no OpenAPI — cobre CA4, CA5, CA7, CA8 (servidor), CA10, CA11, CA14 (servidor) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PortalShellIT -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` (Docker ativo)
- [x] T2: textos `portalShell` em `pt-BR.json`, handler MSW de `/portal/shell` (conta ativa, conta reduzida, 403 para gestão) e `usePortalShell` — cobre CA14 (MSW) — prova: em `frontend/`, `npm test -- portal-shell.msw.test.ts`
- [x] T3: `PortalLayout` (barra, logo, nome e papel, abas, sino com 99+, abas embaixo no celular, quatro estados) com testes de tela e axe — cobre CA1, CA2, CA3, CA6, CA12 — prova: em `frontend/`, `npm test -- PortalLayout`
- [x] T4: ligar a moldura: `Home` e `AccountFrame` usam `PortalLayout`; `PortalSectionPage` e as quatro rotas; `RequireFullPortal` para o reduzido — cobre CA7 (tela), CA8 (tela), CA9, CA13 — prova: em `frontend/`, `npm test -- PortalLayout AccountFrame App` (os testes de `Home` e conta existentes ajustados)
- [x] T5: ponta a ponta a 360 px com abas embaixo e axe nos dois temas, e a regressão completa — cobre CA3 (ponta a ponta) e CA12 — prova: em `frontend/`, `npm run e2e -- portal-barra`; depois `./backend/mvnw -f backend/pom.xml verify` e `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: o nome e o papel de quem está logado vêm da sessão que o front já tem (`account.name` ou, sem nome, o e-mail; papel do vínculo ativo), e a rota nova traz só o que a sessão não tem (marca, reduzido e contador). Alternativa: repetir tudo na rota nova, descartada por duplicar fonte de verdade.
- D2 (muda o comportamento): o sino do portal conta avisos (comunicados enviados e não lidos), porque RF-AVI-02 e RF-POR-06 ligam o contador do sino ao Avisos; ele não usa as notificações da gestão (`/notifications` dá 403 ao portal por decisão da #84). Clicar leva à aba Avisos; marcar como lido e a lista são da #93. Alternativa: sino sem contador até a #93, descartada porque o card pede o contador agora. → RF-POR-01
- D3 (muda o comportamento): "reduzido" é calculado do que existe hoje: aluno matriculado sem modalidade ativa. Para a conta com vários alunos, vale se nenhum está ativo (responsável com um filho ativo vê o portal completo e a decisão por filho fica para o Início, #89). Bloqueio por atraso, reativação e o fim do acesso do ex-aluno depois de 12 meses chegam na Sprint 12 e entram na mesma regra (`PortalShell.reduced`), sem mudar esta tela. Alternativa: esperar a Sprint 12, descartada por deixar RN-POR-08 sem teste neste item. → RN-POR-08
- D4: a moldura vale para todas as telas de portal que já existem e as abas Agenda, Financeiro, Avisos e Minhas informações já têm endereço fixo, com página provisória, para os itens #89 a #94 só trocarem o conteúdo. A rota `/portal/pedidos` continua; a #94 a liga a Minhas informações. Alternativa: abas desativadas, descartada por dar links que não levam a lugar nenhum e por esconder o teste de navegação.
- D5: o contador é buscado na abertura e a cada 60 s; sem tempo real, como na gestão (D6 da #84).
- D6: a marca não passa pela sessão: o logo é URL assinada e temporária, então vem da rota nova a cada leitura. Alternativa: pôr o logo em `/auth/session`, descartada por ela ser lida em toda página e o logo expirar.
- D7: sem recurso desligado por organização (a moldura é parte básica do portal) e sem nota de versão (o arquivo não existe no repositório); a descrição do PR leva: "Barra e abas do portal, sino com avisos não lidos e portal reduzido para aluno inativo".
- D8: os canvas citados na issue não são acessíveis ao agente; o layout segue `AppLayout`, as telas do portal existentes e a especificação, e o dono confere o visual na revisão da sprint (como a D9 da #84).
- Suposição: o alcance de cada tipo de alvo de comunicado (turma, modalidade, unidade, organização) segue o que o envio da #82 grava; se a #82 resolveu destinatários de outro jeito, só a consulta do contador muda.
- Suposição: o cabeçalho antigo do portal (`app.name`) pode ser removido sem perder função, pois conta, troca de vínculo e tema ficam na barra nova.

## Construção

- T1 — arquivos: `backend/.../student/PortalShell.java` (novo), `PortalStudentController.java` (`GET /shell`), `organization/OrganizationProfile.java` (`brand`, `logoUrl` extraído de `view`), `docs/openapi.yaml` (`/portal/shell`, `PortalShell`), `backend/src/test/.../student/PortalShellIT.java` (novo, 6 testes) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PortalShellIT -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → 7 testes rápidos e 6 IT, BUILD SUCCESS
- T2 — arquivos: `frontend/src/i18n/pt-BR.json` (`portalShell`), `frontend/src/mocks/handlers.ts` (`/portal/shell`, conta `reduzido@exemplo.com`), `frontend/src/features/portal/usePortalShell.ts`, `frontend/src/mocks/portal-shell.msw.test.ts` — prova: `npx vitest run src/mocks/portal-shell.msw.test.ts` → 5 testes passam
- T3 — arquivos: `frontend/src/features/portal/PortalLayout.tsx`, `PortalLayout.test.tsx` — prova: `npx vitest run src/features/portal/PortalLayout.test.tsx` → 14 testes passam (axe nos dois temas incluso)
- T4 — arquivos: `frontend/src/App.tsx` (Home do portal e rotas), `features/account/AccountFrame.tsx`, `features/portal/PortalSectionPage.tsx`, `RequireFullPortal.tsx`; testes ajustados com `frontend/src/test/portalShell.ts` em `AccountPage`, `LoginPage`, `TwoFactorLogin`, `PortalRequestsPage`, `PortalTermPage` — prova: `npx vitest run src/features/portal src/features/account` → 67 passam (CA7 a CA9 e CA13 em `PortalLayout.test.tsx`)
- T5 — arquivos: `frontend/e2e/portal-barra.spec.ts` (3 testes `@critical`: 360 px nos dois temas e portal reduzido) — prova: `npm run e2e -- portal-barra` → 3 passam
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 162 testes rápidos e 806 de integração, 0 falhas, regras de cobertura atendidas; em `frontend/`: `npm run lint && npm run typecheck && npm run build` → ok; `npm run test:cov` → 147 arquivos, 1262 testes, cobertura global 93,31% de instruções; `npm run e2e` → 45 passam. Sem piora em relação à linha de base.
Desvios: (1) a tela do aceite do termo (`PortalTermPage`) não usa a moldura nova: a rota da moldura responde 403 `TERM_PENDING` até o aceite, então `AccountFrame` ganhou a opção `plain`, que mantém o cabeçalho simples antigo só nessa tela. (2) Os testes que montam o portal sem login no mock (`AccountPage`, `LoginPage`, `TwoFactorLogin`, `PortalRequestsPage`, `PortalTermPage`) ganharam o handler `portalShellOk`, previsto no risco 3 do plano. (3) `color` em `PortalShell.organization` é `nullable` no OpenAPI, pois a organização pode não ter cor. (4) Os testes do `PortalShellIT` gravam o aceite do termo direto no banco, pois o portão do termo trata matrícula sem aceite como pendente.
Instável: `PortalLayout.test.tsx` (CA13, `/conta`) estourou 10 s uma vez em execução paralela da suíte inteira e passou em seguida isolado e em duas rodadas dos arquivos do portal; o teste foi dividido em um caso por rota.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-26, estado 1ef0df6c71b9311910575dfdf2a805b4fa305940

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `PortalLayout.test.tsx` CA1 (organização, logo, "Ana Silva · Aluno/Responsável", sem `img` sem logo) |
| CA2 | PASSOU | `PortalLayout.test.tsx` CA2 (ordem, hrefs, `aria-current`) e em `/portal/financeiro` |
| CA3 | PASSOU | teste de classes `fixed inset-x-0 bottom-0 md:static`, `pb-24`; e2e a 360 px: nav termina em y=740 e sem rolagem horizontal (light e dark) |
| CA4 | PASSOU | `PortalShellIT.unreadCount` e `modalityAndClasses` |
| CA5 | PASSOU | `PortalShellIT.twoChildren` |
| CA6 | PASSOU | `PortalLayout.test.tsx` CA6 (99+, zero sem número, rótulo, href) |
| CA7 | PASSOU | `PortalShellIT.reduced` e `PortalLayout.test.tsx` CA7; e2e reduzido |
| CA8 | PASSOU | `PortalShellIT.reduced` (responsável com filho ativo, conta sem matrícula) e CA8 de tela |
| CA9 | PASSOU | `PortalLayout.test.tsx` CA7/CA9 (agenda e avisos) e e2e (`/portal/avisos` vai a `/`) |
| CA10 | PASSOU | `PortalShellIT.isolation` (outra organização, outra conta); a consulta direta com RLS não tem teste próprio neste item, mas as consultas rodam em `OrganizationTransactions` e a RLS é coberta pelos testes de isolamento da base |
| CA11 | PASSOU | `PortalShellIT.permissions` (403 FORBIDDEN para três perfis, 401, 403 TERM_PENDING) |
| CA12 | PASSOU | `PortalLayout.test.tsx` CA12 (erro com retry, carregando, 403, ordem do Tab, axe nos dois temas); e2e com axe a 360 px |
| CA13 | PASSOU | `PortalLayout.test.tsx` it.each `/`, `/conta`, `/portal/pedidos` (um `banner`); página provisória com EmptyState |
| CA14 | PASSOU | `verify` completo inclui `OpenApiContractTest`, `ContractRoutesTest`; `portal-shell.msw.test.ts` passa |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` — BUILD SUCCESS, 806 testes de integração, 0 falhas, regras de cobertura atendidas
- `npm run lint && npm run typecheck && npm run build` (frontend) — ok
- `npm run test:cov` — 147 arquivos, 1262 testes passam, limiares atendidos
- `npm run e2e` — 44 passam, 1 falha (`comprovantes.spec.ts:57`, light) por timeout de 30 s com o `verify` do back-end rodando ao mesmo tempo; isolado, 2 execuções seguidas (light e dark): passam

Instáveis: `e2e/comprovantes.spec.ts` (gestão recusa e confirma comprovantes, light) — 1 falha por timeout em 1 execução sob carga, 4 passagens em 4 execuções isoladas/dark. Não é do item (arquivo da #87); abrir item separado se reaparecer no CI.

Achados:
- sugestão, código, `frontend/src/features/portal/RequireFullPortal.tsx` e `PortalLayout.tsx` — cada um chama `usePortalShell`, então Agenda e Avisos leem `/portal/shell` duas vezes (e dois relógios de 60 s); considerar um provedor único.
- sugestão, código, `PortalShellIT` — CA10 cita consulta direta com RLS; não há asserção própria disso no teste do item.

## Entrega

- O que muda: moldura única do portal (`PortalLayout`) com logo e nome da organização, nome e papel de quem está logado, abas Início, Agenda, Financeiro, Avisos e Minhas informações (embaixo da tela no celular, na barra a partir de 768 px), sino com avisos não lidos (99+) e portal reduzido para aluno inativo ou ex-aluno sem modalidade ativa.
- Back-end: `GET /api/v1/portal/shell` (só Aluno e Responsável, depois do portão do termo), `PortalShell` com `reduced` reutilizável pelas rotas de conteúdo, e `/portal/shell` no OpenAPI.
- Verificação: rodada 1 OK no estado `1ef0df6c71b9311910575dfdf2a805b4fa305940`; regressão completa sem falha do item (`verify`, `lint`, `typecheck`, `build`, `test:cov`, `e2e` com a instabilidade de `comprovantes.spec.ts` registrada na verificação).
- Decisões que o revisor precisa saber: o sino conta avisos do portal e não as notificações da gestão (D2); "reduzido" vale quando nenhum aluno da conta está ativo (D3); a tela do aceite do termo mantém o cabeçalho simples (`AccountFrame` com `plain`), pois a rota da moldura responde 403 `TERM_PENDING` até o aceite.
- Limitações: `usePortalShell` é chamado em mais de um componente (Agenda e Avisos leem `/portal/shell` duas vezes; sugestão de provedor único); o teste de `PortalShellIT` não tem asserção própria de RLS direto no banco (CA10); o `PortalLayout.test.tsx` teve um estouro de tempo isolado em execução paralela, já dividido em casos.
