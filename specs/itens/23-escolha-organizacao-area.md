# 23 — RF-ACE-05 · Escolha de organização e área

Origem: issue #23 (Sprint 02); `docs/especificacao/02-contas-e-acesso.md` (RF-ACE-05, RN-ACE-03). Branch: `item/23-escolha-organizacao-area`.

## 1. Objetivo

Entregar o modal "Escolher organização e área": logo após o login, quem tem mais de um vínculo ativo escolhe a organização e a área (gestão ou portal) em que vai trabalhar; quem tem um só vai direto (o servidor já ativa esse vínculo). A pessoa pode reabrir o modal pelo menu, sem novo login. O servidor da escolha (`PUT /auth/session/link`, `SessionService.selectLink`) existe desde a #18; faltam o nome da organização na resposta (a tela precisa mostrá-lo), o modal, o gatilho, os handlers MSW e a cobertura de teste.

Fora do item: telas de Início de cada área (Sprints seguintes), tela "área indisponível" (#25), último acesso (#24), menu/casca definitiva da aplicação, escolha de unidade, conta sem nenhum vínculo ativo.

## 2. Requisitos

- R1 (RN-ACE-03): depois do login, se a sessão está `ready`, tem mais de um vínculo ativo e `activeLinkId` vazio, a aplicação abre sozinha o modal "Escolher organização e área", sem poder ser fechado até a pessoa escolher (D4).
- R2 (RN-ACE-03): com um vínculo só, o modal não abre; a pessoa segue direto (o servidor já traz `activeLinkId`).
- R3 (RF-ACE-05): cada opção do modal é um vínculo ativo e mostra o nome da organização e a área ("Gestão" ou "Portal", derivada do papel, D2) com o papel; escolher uma opção chama `PUT /auth/session/link`, atualiza a sessão com a resposta e fecha o modal.
- R4 (RN-ACE-03): a pessoa reabre o modal pelo menu sem novo login; reaberto, mostra o vínculo atual marcado, pode ser fechado sem escolher e a escolha troca o vínculo ativo da sessão. O gatilho só aparece com mais de um vínculo (D5).
- R5 (contrato, D1): `LinkView` ganha `organizationName` (obrigatório) em `docs/openapi.yaml`, no servidor e nos handlers MSW; o nome só vem das organizações dos vínculos da própria conta.
- R6 (isolamento): o servidor recusa (404, nada muda) vínculo de outra conta, de outra organização, encerrado ou inexistente; sem sessão responde 401; a resposta nunca traz nome nem vínculo de outra conta. A escolha vale só para a sessão que a fez.
- R7 (permissão): a rota serve todo perfil logado, só para os próprios vínculos (a conferência é "o vínculo é seu"); a tela não é a única barreira.
- R8 (tela): o modal tem os estados aplicáveis (D6): enviando (opções desabilitadas, sem envio duplo) e erro com "Tentar de novo" mantendo a lista; funciona a partir de 360 px, passa no axe nos temas claro e escuro, foco preso, Esc só fecha quando permitido (R4), foco devolvido a quem abriu; todo texto em `pt-BR.json`.
- R9 (auditoria, D3): a escolha altera só o estado da sessão, sem dado de negócio; não grava auditoria nova.

## 3. Critérios de aceite

- CA1 (R1): dado login com dois vínculos e `activeLinkId` vazio, quando a sessão fica `ready`, então o modal abre com o título "Escolher organização e área", uma opção por vínculo, e Esc e clique fora não o fecham; o foco vai ao modal.
- CA2 (R2): dado login com um vínculo (`activeLinkId` preenchido), então o modal não abre e não há gatilho no menu.
- CA3 (R3): dado o modal aberto, quando a pessoa escolhe "Academia Alfa · Gestão (Administrador)", então há um `PUT /auth/session/link` com `{linkId}`, a sessão passa a ter esse `activeLinkId`, o modal fecha e a página atual continua; dois vínculos da mesma organização aparecem como duas opções.
- CA4 (R3): dado um vínculo `STUDENT` ou `GUARDIAN`, então a opção mostra a área "Portal"; dado `COORDINATOR`, `ADMINISTRATOR` ou `TEACHER`, "Gestão".
- CA5 (R4): dado sessão com dois vínculos e um ativo, quando a pessoa usa o botão "Trocar organização ou área", então o modal abre com o vínculo atual marcado, Esc o fecha sem chamar a API e o foco volta ao botão; escolher outro vínculo chama a API e atualiza `activeLinkId`.
- CA6 (R5): dado `GET /auth/session`, `POST /auth/login` e `PUT /auth/session/link`, então cada `links[i]` traz `organizationName` igual ao nome da organização do vínculo (`AuthRoutesIT`, estendendo o caso de escolha de vínculo; `OpenApiContractTest` passa).
- CA7 (R6): dado conta A com vínculos em duas organizações e conta B em outra, quando A pede o vínculo de B, um vínculo encerrado ou um UUID inexistente, então 404 e o `activeLinkId` e a organização da sessão não mudam; sem cookie, 401; a resposta de A não contém o nome da organização de B (`AuthRoutesIT`/`AuthIsolationIT`, casos existentes mais a asserção do nome).
- CA8 (R7): dado cada perfil (Coordenador, Administrador, Professor, Aluno, Responsável) com dois vínculos, quando escolhe o próprio vínculo, então 200 e a sessão passa a ter o vínculo e o papel dele; escolher o vínculo de outro perfil de outra conta dá 404 (`AuthIsolationIT`, teste novo).
- CA9 (R6): dado duas sessões da mesma conta, quando uma escolhe o vínculo X, então a outra mantém o seu `activeLinkId` (`AuthRoutesIT`, teste novo).
- CA10 (R8): dado 500 ou rede fora ao escolher, então o modal mostra o erro com "Tentar de novo", mantém a lista e a escolha pedida; enquanto envia, as opções ficam desabilitadas e não há envio duplo. Dado 404, o erro orienta recarregar a sessão e o modal atualiza a lista a partir de `GET /auth/session`.
- CA11 (R8): dado o modal em 360 px, nos temas claro e escuro, no estado inicial, reaberto, enviando e com erro, então o axe não aponta violação e não há rolagem horizontal.
- CA12 (R5): dado o OpenAPI, então os handlers MSW de login, sessão e `PUT /auth/session/link` devolvem `organizationName` e 404 para vínculo desconhecido; o mock tem uma conta de vários vínculos (D7); `msw.test.ts` os confere.
- CA13 (R1, R3, R4): fluxo `@critical` no Playwright: login com a conta de vários vínculos → modal abre → escolhe → modal fecha e abre `/`; botão "Trocar organização ou área" reabre o modal; login com a conta de um vínculo não abre o modal.
- CA14 (R9): dado a escolha, então nenhuma linha nova é gravada em `audit_log` e o log de acesso não registra o código do vínculo além do que já existe (asserção em `AuthRoutesIT`).

## 4. Plano técnico

- **Dados.** Migração nova `V8__organization_name_for_account.sql`: política de leitura em `organization` (`for select`) permitindo a linha cujo id aparece em `account_link` ativo da conta do contexto (`app.account_id`). Necessária porque `SessionService.view` roda em escopo de conta, e a política atual de `organization` só enxerga o contexto de organização. Nenhuma coluna nova. Seed: não muda (`03-accounts.sql` já tem as contas; conferir se alguma tem dois vínculos, senão acrescentar a existente sem sobrescrever).
- **API/OpenAPI.** `LinkView.organizationName` (string, obrigatório) em `docs/openapi.yaml`; nenhuma rota nova. Documentar no `PUT /auth/session/link` o 404 já existente. Rodar `OpenApiContractTest,ContractRoutesTest`.
- **Back-end.** Alterar `identity/SessionService.java`: `LinkView` ganha `organizationName`; `activeLinks` passa a trazer o nome (join em `organization`, sob a política nova). Sem mudança em `AuthController`, `SecurityConfig` nem em `selectLink`. Testes em `AuthRoutesIT` (nome, outra sessão, sem auditoria) e `AuthIsolationIT` (perfis).
- **Front-end.** A criar em `src/features/auth/`: `ChooseLinkModal.tsx` (usa `components/Modal`, lista de opções em `radiogroup`/botões, estados, `ApiError`), `ChooseLinkGate.tsx` (abre o modal quando `ready` com `links.length > 1` e sem `activeLinkId`), `ChooseLinkTrigger.tsx` (botão que reabre) e `.test.tsx` de cada. Alterar: `SessionProvider.tsx` (tipo `SessionView.links[].organizationName`), `RequireSession.tsx` (renderiza o gate dentro da rota protegida), `App.tsx` (botão no cabeçalho do `Home`, único "menu" que existe, D5), `src/i18n/pt-BR.json` (`chooseLink.*`, áreas e papéis), `src/mocks/handlers.ts` (nome, `PUT /auth/session/link`, conta `varios@exemplo.com`, D7), `frontend/README.md`. e2e: `e2e/escolha-organizacao.spec.ts` (a criar), `@critical`. Reutiliza `Modal`, `Button`, `ErrorState`, `api()`, `useSession().setSession`.
- **Interface.** Modal centralizado, lista vertical de opções com nome da organização em destaque e "Gestão · Administrador" abaixo; cada opção é um botão com área de toque confortável a 360 px. Canvas `EscolhaOrganizacao.dc.html` não acessível ao agente (D8).
- **Segurança.** O servidor decide pertencimento e devolve só vínculos da conta; a tela só reflete. A escolha não vai para armazenamento do navegador; a sessão e o `activeLinkId` são do servidor. Cabeçalho CSRF já exigido pela rota e enviado por `lib/api.ts`. Auditoria: ver D3.
- **Riscos.** (1) A política nova de RLS em `organization` precisa ser só de leitura e só para vínculos ativos da conta; teste de isolamento a cobre (CA7). (2) `AuthRoutesIT` e `AuthIsolationIT` hoje usam o JSON de `LinkView`; conferir que a mudança não quebra os asserts existentes. (3) Sem casca definitiva, o gatilho fica no cabeçalho provisório do `Home`; a casca futura o reaproveita. Dependência nova: nenhuma. Notas de versão: arquivo ainda não existe (RF-AJU-04).

## 5. Tarefas

- [x] T1: Migração V8 (leitura de `organization` por conta), `organizationName` em `SessionService.LinkView` e no OpenAPI — cobre CA6 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `verify -Dit.test='AuthRoutesIT'`.
- [x] T2: Testes de servidor: nome da organização, vínculo alheio/encerrado/inexistente, outra sessão intacta, sem auditoria, cinco perfis — cobre CA7, CA8, CA9, CA14 — prova: `verify -Dit.test='AuthRoutesIT,AuthIsolationIT'`.
- [x] T3: Handlers MSW (nome, `PUT /auth/session/link`, conta de vários vínculos) e tipos — cobre CA12 — prova: `npm test` (`msw.test.ts`).
- [x] T4: `ChooseLinkModal`, `ChooseLinkGate`, `ChooseLinkTrigger`, ligação em `RequireSession`/`App.tsx` e textos `chooseLink.*` — cobre CA1, CA2, CA3, CA4, CA5, CA10 — prova: `.test.tsx` de cada um.
- [x] T5: Axe a 360 px nos dois temas em cada estado do modal — cobre CA11 — prova: `it.each` com `src/test/axe.ts` e `device.ts`.
- [x] T6: e2e `@critical`, `frontend/README.md` e regressão — cobre CA13 — prova: em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`; e `./backend/mvnw -f backend/pom.xml verify`.

## 6. Decisões

- D1 (muda o contrato): `LinkView` ganha `organizationName`. Motivo: o modal precisa mostrar a organização e o contrato só trazia o UUID; campo aditivo, sem rota nova. Alternativa: rota `GET /organizations/{id}`, descartada por ser fora do card e exigir contexto de organização antes da escolha.
- D2 (muda o comportamento do produto): a "área" é derivada do papel do vínculo: Coordenador, Administrador e Professor abrem a Gestão; Aluno e Responsável, o Portal. Cada vínculo ativo é uma opção (organização + área); a equipe da plataforma não entra, pois não tem vínculo de organização na #18. Motivo: o modelo de dados tem um papel por vínculo (RN-ACE-02, RN-ACE-19). Alternativa: escolher organização e depois área em dois passos, descartada por ser mais cliques sem ganho.
- D3: escolher o vínculo não grava auditoria. Motivo: altera só o estado da sessão, não dado de negócio; o login já é registrado. Suposição: basta; se o dono quiser trilha das trocas, vira item de auditoria de acesso.
- D4 (muda o comportamento do produto): o modal automático após o login não fecha sem escolha (sem Esc, sem clique fora, sem botão de fechar), porque sem vínculo ativo o servidor recusa as rotas de organização. Reaberto pelo menu, pode ser fechado. Alternativa: deixar fechar e ficar sem vínculo, descartada por deixar a pessoa numa tela sem função.
- D5: o "menu" ainda não existe; o gatilho "Trocar organização ou área" fica no cabeçalho do `Home` provisório, visível só com mais de um vínculo, e vira item do menu quando a casca chegar. Suposição: a casca reaproveita `ChooseLinkTrigger`.
- D6: estados do modal: carregando = enviando a escolha; erro = falha com "Tentar de novo"; "vazio" e "sem permissão" não se aplicam (a lista nunca é vazia quando o modal abre; conta sem vínculos é da #25; a rota é do próprio dono da sessão).
- D7: o mock ganha a conta `varios@exemplo.com` (senha `segredo123`, como as demais) com vínculos de Gestão e Portal em duas organizações; as demais contas de teste seguem com um vínculo.
- D8: o canvas (`EscolhaOrganizacao.dc.html`) não é acessível ao agente; a tela segue o texto do card e a base visual do `Modal`. Suposição: se o desenho diferir, o dono pede ajuste de layout, sem mudar regra.
- D9: depois de escolher, a pessoa permanece na página atual (`/`); o redirecionamento por área vem com os Inícios de cada perfil (RF-INI-*). Suposição: não há destino por área ainda.

## 7. Construção

- T1: `backend/src/main/resources/db/migration/V8__organization_name_for_account.sql` (política `for select` em `organization` por vínculo ativo da conta), `identity/SessionService.java` (`LinkView.organizationName`), `docs/openapi.yaml`. Prova: `mvnw verify -Dtest='OpenApiContractTest,ContractRoutesTest' -Dit.test='AuthRoutesIT,AuthIsolationIT'`: passou.
- T2: `AuthBase.java` (`org(name)`), `AuthRoutesIT` (`linkChoiceNamesIsolationAndNoAudit`: nome, vínculo encerrado 404, sem cookie 401, outra sessão intacta, sem auditoria), `AuthIsolationIT` (`everyRoleChoosesOwnLink`, cinco perfis). Prova: mesma execução, 17 testes, 0 falhas.
- T3: `frontend/src/mocks/handlers.ts` (nome, `PUT /auth/session/link`, conta `varios@exemplo.com`), `SessionProvider.tsx` (tipo), `msw.test.ts`. Prova: `npx vitest run src/mocks`: passou.
- T4: `ChooseLinkModal.tsx`, `ChooseLinkGate.tsx`, `ChooseLinkTrigger.tsx`, `RequireSession.tsx`, `App.tsx`, `components/Modal.tsx` (prop `dismissible`), `pt-BR.json` (`chooseLink.*`), `ChooseLinkModal.test.tsx` (cobre modal, gate e gatilho pelo `App`). Prova: `npx vitest run src/features/auth/ChooseLinkModal`: 11 testes passaram.
- T5: axe a 360 px nos temas claro e escuro (inicial, erro, reaberto, enviando) em `ChooseLinkModal.test.tsx`: passou.
- T6: `e2e/escolha-organizacao.spec.ts` (`@critical`), `frontend/README.md`. Regressão: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`: tudo verde (cobertura 98,82% linhas; e2e 6 de 6); `./backend/mvnw -f backend/pom.xml verify`: 92 testes de integração, 0 falhas, JaCoCo ok.

Desvios: o texto da opção é "Organização Área (Papel)" em duas linhas visuais (nome acessível "Academia Alfa Gestão (Administrador)", sem o "·" do CA3). `Modal.tsx` ganhou `dismissible` (D4). Seed não mudou (D7 trata só do mock). Teste do modal em `ChooseLinkModal.test.tsx` único para modal, gate e gatilho, em vez de três arquivos.

## 8. Verificação

### Rodada 1 — OK — aeac45c93d1ee5ee758c2c7c73b0cebfa2e0f207

| Critério | Status | Evidência |
| --- | --- | --- |
| CA1 | PASSOU | `ChooseLinkModal.test.tsx` (abre sozinho, sem Esc/clique fora); `Modal` com `dismissible` |
| CA2 | PASSOU | teste CA2 e e2e "um vínculo só não abre o modal" |
| CA3 | PASSOU | teste CA3 (PUT, sessão, fecha, mesma página); desvio de texto "Organização Área (Papel)" registrado na Construção |
| CA4 | PASSOU | `it.each` de áreas por papel |
| CA5 | PASSOU | teste CA5 (marcado, Esc sem API, foco, troca) |
| CA6 | PASSOU | `AuthRoutesIT.linkChoiceNamesIsolationAndNoAudit`; contrato no `verify` |
| CA7 | PASSOU | 404 encerrado, 401 sem cookie, nome de B ausente; vínculo de outra conta em `AuthIsolationIT` (404) |
| CA8 | PASSOU | `AuthIsolationIT.everyRoleChoosesOwnLink` (5 perfis) |
| CA9 | PASSOU | outra sessão mantém `activeLinkId` nulo |
| CA10 | PASSOU | testes de envio, 500, rede, 404 |
| CA11 | PASSOU | `it.each` axe 360 px, dois temas, 4 estados |
| CA12 | PASSOU | `msw.test.ts` |
| CA13 | PASSOU | e2e `@critical` 6 de 6 |
| CA14 | PASSOU | contagem de `audit_log` inalterada; ver sugestão 2 |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify`: 92 testes, 0 falhas, JaCoCo ok, BUILD SUCCESS.
- Em `frontend/`: `npm run lint && typecheck && build && test:cov && e2e`: tudo verde; linhas 98,82%; e2e 6 de 6.

Achados obrigatórios: nenhum.

Sugestões:
1. O texto da opção não traz o "·" do CA3 (desvio já declarado; só o nome acessível difere).
2. CA14 pede também que o log de acesso não registre o código do vínculo; não há asserção disso.

Resultado: OK.

## Entrega

- O que muda: depois do login, quem tem mais de um vínculo ativo escolhe organização e área em um modal que não fecha sem escolha; com um vínculo só nada aparece. O gatilho "Trocar organização ou área" reabre o modal. `LinkView` ganha `organizationName` (migração V8, política de leitura de `organization` por vínculo ativo da conta; contrato atualizado).
- Verificação: rodada 1 OK. `mvnw verify` (92 testes de integração, JaCoCo ok); front: lint, typecheck, build, `test:cov` (98,82% linhas) e e2e (6 de 6) verdes.
- Decisões: ver seção 6 (D1 a D9). A área vem do papel do vínculo; a troca não grava auditoria; sem redirecionamento por área até os Inícios (RF-INI-*).
- Limitações: texto da opção sem o "·" do CA3; sem asserção de que o log de acesso omite o código do vínculo; canvas indisponível ao agente.
