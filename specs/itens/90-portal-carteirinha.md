# 90 — RF-POR-03 · Portal: carteirinha

Origem: issue #90 (Sprint 10 · Portal do aluno); `docs/especificacao/14-portal-do-aluno.md` (RF-POR-03, RN-POR-01, RN-POR-03, RN-POR-04, RN-POR-08, RN-POR-09, RN-POR-11), `12-inativacao.md` (RN-INA-08), `00-visao-geral.md` (RNF-22). Branch: `item/90-portal-carteirinha`. Parte do que a #88 e a #89 deixaram: `PortalLayout`, `PortalShell.reduced`, `PortalStudents.cards`, `PortalTerms.requireAccepted`, `OrganizationProfile.brand`, `PortalHome` (consulta de faixa), `PortalFamilyStrip` e `PortalTag`. Os canvas citados na issue não são lidos pelo agente (D9).

## 1. Objetivo

Dar ao aluno e ao responsável a carteirinha digital do aluno: foto, nome, organização (logo e cor), modalidades com unidade e faixa, e o selo "Ativo" ou "Inativo" com a data do dia. Abre a partir do Início e de Minhas informações, sem QR code. Sem internet, a tela mostra a última versão lida, com "Sem conexão" e "Atualizada em [data e hora]". O responsável abre a carteirinha de cada filho (e a sua, se também treina). O aluno inativo e o ex-aluno continuam vendo a carteirinha, com o selo "Inativo" (RN-INA-08).

Fora do escopo: instalar como PWA, service worker e abrir o app sem rede (Sprint 21; aqui só vale o que já foi carregado na aba, D3); a Agenda e o resto do uso sem internet (RN-POR-11, Sprint 21); QR code; a tela Minhas informações em si (#94: aqui só o link para a carteirinha entra no marcador provisório); presença, frequência e progresso de graduação; baixar a carteirinha como imagem ou PDF.

## 2. Requisitos

- R1 (RF-POR-03): `GET /api/v1/portal/card` devolve, para o aluno escolhido, nome, foto, a marca da organização (nome, cor, logo), as modalidades ativas com unidade e faixa atual (nível, cores e grau), o selo e o momento da leitura.
- R2 (RF-POR-03, D1): o selo é `ACTIVE` quando o aluno tem ao menos uma modalidade ativa e `INACTIVE` caso contrário; vem com a data do dia no fuso da unidade de cadastro do aluno (`today`), e a tela mostra "Ativo" ou "Inativo" com essa data.
- R3 (RF-POR-03, RN-POR-04, RN-GRA-04): cada modalidade ativa traz a unidade e a faixa pelo registro de graduação mais recente da modalidade (mesma regra da #89); modalidade sem registro vem sem faixa. Aluno inativo vem sem modalidades.
- R4 (RN-POR-01, RN-POR-05): `studentId` opcional, padrão o primeiro cartão da conta; aluno não ligado à conta ou de outra organização é 404; a resposta traz a lista de alunos ligados (id, nome) para o responsável escolher; gestão e Professor recebem 403, sem sessão 401, termo pendente 403 `TERM_PENDING`. O responsável vê a carteirinha de qualquer filho ligado, mesmo sem ser o pagador (D2).
- R5 (RN-INA-08, RN-POR-08, D4): a rota e a tela funcionam também com o portal reduzido; a carteirinha é o único conteúdo, além da cobrança, que o aluno inativo e o ex-aluno veem.
- R6 (RF-POR-03, D5): a tela da carteirinha tem a rota `/portal/carteirinha` (aluno escolhido em `?aluno=`), com a barra do portal; o Início e Minhas informações têm o link "Ver carteirinha"; no responsável com mais de um aluno, a tela mostra o seletor de aluno.
- R7 (RNF-22, D6): a cor da organização aparece na faixa superior da carteirinha e o logo no topo (some se falhar); a foto cai para as iniciais quando falta ou falha. Sem QR code.
- R8 (RN-POR-11, D3): a cada leitura bem-sucedida a tela guarda a resposta no navegador (por aluno); sem conexão (falha de rede, não erro 4xx/5xx), mostra a última leitura guardada com o aviso "Sem conexão" e "Atualizada em [data e hora]", no fuso da unidade; sem leitura guardada, mostra o estado de erro com "Tentar de novo". Online, mostra "Atualizada em" com a hora da leitura.
- R9 (RN-POR-09): a resposta e a tela não trazem presença, frequência nem progresso de graduação, nem CPF, telefone ou e-mail.
- R10 (RF-POR-08, padrão de tela): carregando, vazio (aluno sem nenhuma modalidade ativa: só o selo "Inativo" e o texto de sem modalidades), erro com "Tentar de novo" e sem permissão; a partir de 360 px, axe nos dois temas e todo texto em `pt-BR.json`.
- R11 (RNF-32): `docs/openapi.yaml` e o handler MSW refletem a rota nova.

## 3. Critérios de aceite

- CA1 (R1, R3): dado aluno com Jiu-Jitsu (unidade A, faixa azul grau 2) e Judô (unidade B, sem graduação), quando `GET /portal/card`, então `modalities` traz as duas com unidade, a primeira com `levelName`, cores e grau, a segunda com `belt` nulo; `organization` traz nome, cor e logo; `student` traz nome e foto.
- CA2 (R2): dado aluno com modalidade ativa, então `status` é `ACTIVE`; dado aluno sem nenhuma (inativo ou ex-aluno), `status` é `INACTIVE` e `modalities` vem vazio; `today` é a data do fuso da unidade de cadastro, em teste com fuso diferente de UTC perto da virada do dia (`Pacific/Kiritimati`).
- CA3 (R4): dado responsável com dois filhos, então `students` traz os dois (e "Você", se treina), o padrão é o primeiro cartão e `studentId` do segundo devolve a carteirinha dele; o responsável não pagador do menor também a vê.
- CA4 (R4): dado `studentId` de outra conta da mesma organização ou da organização B, então 404, sem distinguir; nada de outra conta ou organização na resposta; consulta ao banco no contexto de A não devolve linha de B (RLS).
- CA5 (R4): dado Administrador, Coordenador ou Professor, então 403; sem sessão, 401; aluno com termo pendente, 403 `TERM_PENDING`.
- CA6 (R5): dado aluno inativo com portal reduzido, então a rota responde 200 com `status` `INACTIVE`, e a tela mostra a carteirinha com o selo "Inativo" e a data; `/portal/carteirinha` não é bloqueada por `RequireFullPortal`.
- CA7 (R9): dado qualquer resposta, então não há campo de presença, frequência, progresso, CPF, telefone nem e-mail, e a tela não mostra esse texto.
- CA8 (R6): dado o Início e Minhas informações, então cada um tem o link "Ver carteirinha" que leva a `/portal/carteirinha`; no responsável, o Início leva ao filho escolhido; com mais de um aluno ligado, a tela traz o seletor e trocar de aluno refaz a leitura.
- CA9 (R7): dado logo que falha ao carregar, então a carteirinha aparece sem o logo e sem erro; dado aluno sem foto ou com foto que falha, aparecem as iniciais; não há QR code nem imagem de código.
- CA10 (R8): dado leitura bem-sucedida seguida de falha de rede, quando a página é aberta de novo, então mostra a carteirinha guardada com "Sem conexão" e "Atualizada em" com a data e hora da leitura guardada; dado erro 403 ou 500, então não usa a cópia e mostra o estado de erro/sem permissão; dado falha de rede sem cópia, então erro com "Tentar de novo".
- CA11 (R8): dado conexão normal, então a tela mostra "Atualizada em" com a hora da resposta e nenhum aviso de "Sem conexão".
- CA12 (R10): dado a tela carregando, com erro ("Tentar de novo" refaz a leitura), com 403 e aluno sem modalidade, então cada estado aparece; sem rolagem horizontal a 360 px e axe sem violação nos dois temas.
- CA13 (R11): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota nova; o handler MSW responde no formato do OpenAPI, inclusive 403 para a gestão, aluno inativo e responsável com dois filhos.

## 4. Plano técnico

- Dados: não muda (sem migração nem seed; só leitura de `student`, `enrollment_modality`, `modality`, `unit`, `student_graduation`, `graduation_level`). Rota de leitura, não grava nem audita.
- API: `GET /api/v1/portal/card?studentId=<uuid opcional>`, só Aluno e Responsável, depois de `terms.requireAccepted`. Resposta `PortalCard`: `organization {name, color|null, logoUrl|null}`, `student {id, name, photoUrl|null}`, `students [{id, name, self}]`, `status (ACTIVE|INACTIVE)`, `today` (date), `generatedAt` (date-time UTC), `timezone`, `modalities [{modality {id,name}, unit {id,name}, belt {levelName, color, secondaryColor|null, degree}|null}]`. Erros: 401, 403 (`FORBIDDEN`, `TERM_PENDING`), 404 (`NOT_FOUND`). `today` e `timezone` vêm de `unit.timezone` da unidade de cadastro (`student.registered_unit_id`). `generatedAt` vem do `Clock` injetado.
- Interface: `usePortalCard(studentId)` (a criar) lê a rota, grava a resposta em `localStorage` (`ctcore.portalCard.<studentId>`, em try/catch) e, em falha de rede (`fetch` rejeitado, não `ApiError`), devolve a cópia com `offline: true`; `PortalCardPage` (a criar) dentro de `PortalLayout`, com `LoadingState`, `ErrorState`, `ForbiddenState` e `EmptyState`; `PortalMemberCard` (a criar) desenha a carteirinha (faixa superior na cor da organização, logo, foto/iniciais, nome, selo e data, modalidades com etiqueta de unidade e faixa pelas cores, "Atualizada em"); seletor de aluno com `select` quando `students.length > 1`. Rota `/portal/carteirinha` dentro de `RequirePortalTerm`, fora de `RequireFullPortal`. Link "Ver carteirinha" no `PortalHomePage` (com `?aluno=` do cartão escolhido) e no marcador de Minhas informações (`PortalSectionPage`, aba `info`). Textos no grupo `portalCard` de `pt-BR.json`; datas com `formatDate`/`formatDateTime` de `lib/format` passando `timezone`.
- Segurança: perfil, vínculo e termo conferidos no servidor (`PortalStudents.cards`, `requireAccepted`); SQL só com parâmetros dentro de `OrganizationTransactions.execute` (RLS); sem CPF, telefone, e-mail, nem dado financeiro; `studentId` desconhecido é 404 sem distinguir de outra conta. A cópia local guarda só o que a tela mostra (nome, foto como URL, marca, modalidades, faixa); a URL assinada da foto expira, então offline a foto pode cair para as iniciais.
- Arquivos a criar: `backend/src/main/java/br/com/ctcore/student/PortalCard.java` (serviço `view(who, studentId)`); `backend/src/test/java/br/com/ctcore/student/PortalCardIT.java`; `frontend/src/features/portal/usePortalCard.ts`, `PortalCardPage.tsx`, `PortalMemberCard.tsx`, `PortalCardPage.test.tsx`; `frontend/src/mocks/portal-card.msw.test.ts`; `frontend/e2e/portal-carteirinha.spec.ts`.
- Arquivos a alterar (vistos no repositório): `backend/.../student/PortalStudentController.java` (`GET /card`); `backend/.../student/PortalHome.java` (extrair a consulta de faixa por modalidade para reuso em `PortalCard`, sem mudar o resultado; `PortalHomeIT` como rede); `docs/openapi.yaml` (`/portal/card`, `PortalCard`); `frontend/src/App.tsx` (rota `/portal/carteirinha`); `frontend/src/features/portal/PortalHomePage.tsx` (link); `frontend/src/features/portal/PortalSectionPage.tsx` (link na aba `info`); `frontend/src/mocks/handlers.ts` (`/portal/card`: aluno com duas modalidades, inativo `reduzido@exemplo.com`, responsável `familia@exemplo.com` com dois filhos, 403 para a gestão); `frontend/src/i18n/pt-BR.json` (`portalCard`, link em `portalHome`).
- Reuso: `PortalStudents.cards`, `PortalTerms.requireAccepted`, `OrganizationProfile.brand`, `PortalShell.reduced` (não necessário na resposta), consulta de faixa da #89, `PortalTag`, `Card`, `LoadingState`, `ErrorState`, `ForbiddenState`, `EmptyState`, `formatDate`, `formatDateTime`.
- Riscos: (1) a coluna da foto e o gerador da URL (`Students.photoUrl`) são usados como em `PortalStudents.cards`; conferir. (2) `OrganizationProfile.brand(who)` abre transação própria; chamar fora da transação de leitura, como `PortalShell.view`. (3) A cópia local fica no navegador do aparelho: em aparelho compartilhado, outra conta vê a última carteirinha só se abrir o mesmo `studentId` com falha de rede; confirmar na construção se há ponto de saída da sessão para limpar as chaves `ctcore.portalCard.*`, e, se houver, limpar (D3). (4) Distinguir falha de rede de erro de API no `api()` de `lib/api.ts`: ler antes de mexer. (5) Teste de fuso: a data do dia na unidade, não a da JVM.

## 5. Tarefas

- [x] T1: `PortalCard` (resumo do aluno, selo, `today` no fuso da unidade, modalidades com unidade e faixa, `students`, marca), extrair a consulta de faixa de `PortalHome`, rota `GET /portal/card` e `/portal/card` no OpenAPI — cobre CA1, CA2, CA3, CA4, CA5, CA6 (servidor), CA7, CA13 (servidor) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='PortalCardIT,PortalHomeIT' -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` (Docker ativo)
- [x] T2: textos `portalCard` em `pt-BR.json`, handler MSW de `/portal/card` e `usePortalCard` com a cópia local e a falha de rede — cobre CA10, CA11, CA13 (MSW) — prova: em `frontend/`, `npm test -- portal-card.msw.test.ts`
- [x] T3: `PortalMemberCard` e `PortalCardPage` (seletor de aluno, selo, faixas, logo e foto com queda, "Sem conexão" e "Atualizada em", quatro estados), testes de tela e axe nos dois temas — cobre CA3 (tela), CA6 (tela), CA7 (tela), CA8 (seletor), CA9, CA10, CA11, CA12 — prova: em `frontend/`, `npm test -- PortalCardPage`
- [x] T4: rota `/portal/carteirinha` em `App.tsx` (fora de `RequireFullPortal`), link "Ver carteirinha" no Início e em Minhas informações, ajuste dos testes existentes que contam textos — cobre CA6 (rota), CA8 — prova: em `frontend/`, `npm test -- PortalHomePage PortalLayout App`
- [x] T5: ponta a ponta a 360 px (aluno com duas modalidades, responsável trocando de aluno, portal reduzido com selo "Inativo", sem conexão com cópia) com axe nos dois temas, e a regressão completa — cobre CA6, CA8, CA10, CA12 (ponta a ponta) — prova: em `frontend/`, `npm run e2e -- portal-carteirinha`; depois `./backend/mvnw -f backend/pom.xml verify` e `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1 (muda o comportamento): "Ativo" é ter ao menos uma modalidade ativa, a mesma regra de `Students.STATUS`; "Inativo" cobre inativo e ex-aluno, como a RN-INA-08. A data do dia é a do fuso da unidade de cadastro do aluno, porque o aluno pode treinar em unidades de fusos diferentes e a carteirinha tem uma data só. Alternativa: um selo por unidade, descartada por o card pedir um selo. → RF-POR-03
- D2 (muda o comportamento): o responsável vê a carteirinha de todo filho ligado, pagador ou não, porque ela não traz dado financeiro (a #89 esconde só a cobrança). Alternativa: só o pagador, descartada por esconder o documento do aluno de quem o acompanha. → RF-POR-03
- D3: "Funciona sem internet" nesta sprint vale para a aba que já abriu o app: a última resposta fica em `localStorage` por aluno e é mostrada quando a rede falha. Abrir o app sem rede (service worker, PWA, RN-POR-10 e RN-POR-11) é da Sprint 21; nesta entrega a página ainda precisa ser carregada uma vez. Mostrar a cópia só em falha de rede, nunca em 4xx/5xx, para não exibir carteirinha de quem perdeu o acesso. Alternativa: service worker agora, descartada por ser a Sprint 21 e por exigir a infraestrutura de instalação.
- D4: o portal reduzido e o aluno inativo continuam com a carteirinha (RN-INA-08), por isso a rota não passa por `RequireFullPortal` e o servidor não usa `shell.reduced` para recusar.
- D5: a carteirinha é a rota `/portal/carteirinha`, não uma aba da barra (a barra tem as cinco abas da RF-POR-01); abre por link no Início e em Minhas informações. Como a #94 ainda não existe, o link entra no marcador provisório e a #94 o mantém ao trocar o miolo. Alternativa: criar a aba, descartada por a RF-POR-01 listar as cinco.
- D6: a cor da organização vai na faixa superior e na borda, não como fundo do texto, porque a cor tem contraste AA garantido contra o fundo da página e não necessariamente contra texto sobre ela (RNF-22); o logo usa o mesmo caminho da barra (`OrganizationProfile.brand`).
- D7: o botão para o responsável vem do Início com `?aluno=<cartão escolhido>`; sem o parâmetro, a tela usa o primeiro cartão. A escolha não é gravada.
- D8: a tela mostra "Atualizada em" também online, com `generatedAt`, para o aluno saber de que momento é a imagem que mostra à portaria; a data do selo (`today`) é a do dia na unidade, não a do aparelho.
- D9: os canvas citados na issue não são acessíveis ao agente; o layout segue `PortalLayout`, o Início e a especificação, e o dono confere o visual na revisão da sprint (como a D9 da #89).
- D10: sem recurso desligado por organização (é parte básica do portal) e sem nota de versão (o arquivo não existe no repositório); a descrição do PR leva: "Carteirinha do aluno no portal, com selo Ativo ou Inativo, faixa por modalidade e última versão sem internet".
- Suposição: o botão "Ver carteirinha" também aparece no portal reduzido do Início (só a cobrança é a seção; o link vai junto), por a RN-INA-08 manter a carteirinha. Se estiver errada, só o link some.
- Suposição: `student.registered_unit_id` sempre aponta unidade com `timezone` preenchido (`unit.timezone` não nulo); conferir na construção.

## Construção

- T1 — arquivos: `backend/.../student/PortalCard.java` (novo), `PortalHome.java` (`belts` passou a package-private, sem mudar o resultado), `PortalStudentController.java` (`GET /card`), `docs/openapi.yaml` (`/portal/card`, `PortalCard`), `PortalCardIT.java` (novo) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='PortalCardIT,PortalHomeIT' -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → 7 testes rápidos e 11 de integração, 0 falhas
- T2 — arquivos: `frontend/src/i18n/pt-BR.json` (`portalCard`), `mocks/handlers.ts` (`/portal/card`), `features/portal/usePortalCard.ts`, `mocks/portal-card.msw.test.ts` — prova: `npm test -- portal-card.msw.test.ts` → 5 passaram (a cópia local e a falha de rede do hook são provadas em T3)
- T3 — arquivos: `PortalMemberCard.tsx`, `PortalCardPage.tsx`, `PortalCardPage.test.tsx` — prova: `npm test -- PortalCardPage` → passaram, inclusive axe nos dois temas
- T4 — arquivos: `App.tsx` (rota fora de `RequireFullPortal`), `PortalHomePage.tsx` e `PortalSectionPage.tsx` (link "Ver carteirinha"), `PortalHomePage.test.tsx` (envolvido em `MemoryRouter`, mais o teste do link) — prova: `npm test -- PortalCardPage PortalHomePage PortalLayout` → 22 passaram (os testes de rota e de portal reduzido estão em `PortalCardPage.test.tsx`)
- T5 — arquivos: `frontend/e2e/portal-carteirinha.spec.ts` — prova: `npm run e2e -- portal-carteirinha` → 5 passaram (360 px, axe claro e escuro)

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 817 testes, 0 falhas, BUILD SUCCESS (JaCoCo incluído). Em `frontend/`: `npm run lint`, `typecheck`, `build` ok; `npm run test:cov` → 151 arquivos, 1295 testes passaram; `npm run e2e` → 55 passaram. Numa primeira rodada, feita junto do `verify` do back-end, `LessonPanelPage.test.tsx` (CA10) falhou por tempo; passou sozinho (21 testes) e na rodada completa seguinte: instável sob carga, sem relação com o item.
Desvios:
- A cópia local é gravada também pela chave do `id` do aluno (`ctcore.portalCard.<id>`), além de `first` quando a tela pede o primeiro cartão; assim o seletor acha a cópia sem rede.
- O link "Ver carteirinha" usa o texto `portalCard.link` (e não um item de `portalHome`).
- O risco 3 do plano: não há ponto único de saída da sessão no front (o único `/auth/logout` é o da tela de 2 etapas), então as chaves `ctcore.portalCard.*` não são limpas na saída; fica em aberto para a #94 / Sprint 21.
- Teste de RLS direto no banco (CA4, última cláusula) não foi escrito à parte: a rota usa `OrganizationTransactions` e o isolamento é provado pelos 404 de outra conta e de outra organização no `PortalCardIT`.
- O e2e de "sem conexão" simula a falha no `fetch` da página (o service worker do MSW não é alcançado por `page.route`).
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-27, estado f63c539bb15b3a084e8f2d94ed8b5321f840fba8

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `PortalCardIT.active` (duas modalidades, faixa azul grau 2 e `belt` nulo, marca e aluno) |
| CA2 | PASSOU | `PortalCardIT.active` e `inactiveAndZone` (`Pacific/Kiritimati`, `today` igual à data do fuso no banco) |
| CA3 | PASSOU | `PortalCardIT.family` (dois filhos, "Você", padrão o primeiro, `studentId` do segundo; responsável não pagador) |
| CA4 | PASSOU | `PortalCardIT.access` (404 para outra conta e outra organização, sem nome na resposta); RLS direto não testado à parte, declarado na Construção; a rota usa `OrganizationTransactions` |
| CA5 | PASSOU | `PortalCardIT.access` (403 para Administrador, Coordenador e Professor, 401, 403 `TERM_PENDING`) |
| CA6 | PASSOU | `inactiveAndZone` (200 `INACTIVE`) e e2e "portal reduzido continua com a carteirinha"; rota fora de `RequireFullPortal` no diff |
| CA7 | PASSOU | `PortalCardIT.active` (resposta sem presença, CPF, telefone nem e-mail); testes de tela |
| CA8 | PASSOU | `PortalHomePage`/`PortalCardPage` testes e e2e do responsável trocando de aluno; link em `PortalSectionPage` aba `info` |
| CA9 | PASSOU | `PortalCardPage.test.tsx` (logo e foto com queda, sem QR); código de `PortalMemberCard` lido |
| CA10 | PASSOU | `PortalCardPage.test.tsx` e e2e sem conexão; `usePortalCard` usa a cópia só com `ApiError` status 0 (`lib/api.ts` linha 36) |
| CA11 | PASSOU | `PortalCardPage.test.tsx`; `PortalMemberCard` mostra "Atualizada em" sempre e "Sem conexão" só com `offline` |
| CA12 | PASSOU | testes de tela (quatro estados) e e2e 360 px com axe claro e escuro |
| CA13 | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` 7 testes ok; `portal-card.msw.test.ts` ok |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify -Dit.test='PortalCardIT,PortalHomeIT' -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → 7 rápidos, 11 de integração, 0 falhas, BUILD SUCCESS
- `npx vitest run portal-card.msw PortalCardPage PortalHomePage PortalLayout --maxWorkers=2` → 4 arquivos, 43 testes passaram
- `npm run lint`, `npm run typecheck` → sem erros
- `npm run e2e -- portal-carteirinha` → 5 passaram
- Regressão completa (`verify` inteiro, `test:cov`, `e2e` inteiro) não foi reexecutada nesta rodada; vale o relato da Construção (817 testes do back-end, 1295 do front, 55 e2e) e a cobertura do pacote alterado pelos testes acima.

Instáveis: nenhum visto nesta rodada (a Construção cita `LessonPanelPage.test.tsx` CA10 instável sob carga, fora do item).

Achados:
- sugestão, código, `frontend/src/features/portal/usePortalCard.ts` (cópia em `localStorage`) — a cópia não é limpa na saída da sessão (risco 3 do plano, declarado na Construção); em aparelho compartilhado, outra conta abre a mesma carteirinha só com falha de rede e mesmo `studentId`. Tratar na #94 ou na Sprint 21.
- sugestão, código, `PortalCardIT` — não há teste de RLS direto no banco para a carteirinha (CA4, última cláusula); o isolamento é provado pelos 404 por HTTP.

## Entrega

- Adiciona a carteirinha do aluno em `/portal/carteirinha`, com `GET /api/v1/portal/card` (aluno e responsável; `studentId` opcional, padrão o primeiro cartão): foto ou iniciais, marca da organização (cor na faixa superior, logo que some se falhar), modalidades com unidade e faixa, selo "Ativo" ou "Inativo" com a data do dia no fuso da unidade de cadastro e "Atualizada em". A última leitura fica no navegador por aluno e aparece com "Sem conexão" só em falha de rede. Link "Ver carteirinha" no Início e em Minhas informações; a rota fica fora de `RequireFullPortal`, então o aluno inativo e o portal reduzido também a veem.
- Verificação: `PortalCardIT` e `PortalHomeIT`, `OpenApiContractTest` e `ContractRoutesTest` passam; back-end `verify` 817 testes sem falha; front `test:cov` 1295 testes, `lint`, `typecheck` e `build` limpos; e2e de 360 px com axe nos dois temas (5 de `portal-carteirinha`, 55 no total).
- Decisões: "Ativo" é ter ao menos uma modalidade ativa; o responsável vê a carteirinha de todo filho ligado (sem dado financeiro); a cópia offline vale só para a aba que já abriu o app (service worker e PWA são da Sprint 21).
- Limitações: a cópia em `localStorage` não é limpa na saída da sessão (não há ponto único de saída no front; fica para a #94 ou a Sprint 21); RLS direto no banco não tem teste próprio, o isolamento é provado pelos 404 por HTTP; `LessonPanelPage.test.tsx` é instável sob carga, fora do item.
