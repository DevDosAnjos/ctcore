# 33 — RF-ORG-06 · Transferir coordenação

Origem: issue #33 (Sprint 03); `docs/especificacao/01-organizacao-e-isolamento.md` (RF-ORG-06, RN-ORG-06), `02-contas-e-acesso.md` (RN-ACE-17, RN-ACE-21), `00-visao-geral.md` (RNF-26). Branch: `item/33-transferir-coordenacao`.

## 1. Objetivo

Permitir que o Coordenador passe a coordenação da organização a outra conta da equipe, com confirmação digitada, senha (e código de duas etapas, se ligado) e e-mail de confirmação ao novo Coordenador. O antigo vira Administrador de todas as unidades. A organização termina sempre com um único Coordenador.

Fora do escopo: transferência feita pela equipe da plataforma com comprovação (RF-PNL-04, Sprint do painel); lista de usuários e convite de Administrador (itens de Configurações › Usuários); desativar unidade (#34); consulta da auditoria (#35).

## 2. Requisitos

- R1 (RN-ORG-06): `POST /api/v1/organization/coordination-transfer` troca o Coordenador da organização do vínculo ativo; só o Coordenador (nível `FULL` em `ORGANIZATION_SETTINGS`); os demais perfis recebem 403 sem efeito.
- R2 (RN-ORG-06, RN-ACE-21): a rota exige o token de uso único da ação `TRANSFER_COORDINATION` em `X-Reauth-Token`; sem token válido responde 403 `REAUTH_REQUIRED` e nada muda.
- R3 (RN-ORG-06, RNF-26): a rota exige a confirmação digitada `TRANSFERIR` (sem diferenciar maiúsculas, sem espaços nas pontas); outra coisa é 400 `VALIDATION_ERROR` no campo `confirmation` e nada muda (D3).
- R4 (RN-ORG-06): o novo Coordenador é indicado pelo e-mail e precisa ser uma conta com vínculo ativo de Administrador na mesma organização; e-mail desconhecido, de outra organização, de outro perfil, de vínculo não ativo ou da própria conta é 400 `VALIDATION_ERROR` no campo `email` (D2).
- R5 (RN-ORG-06): na troca, o vínculo do alvo passa a Coordenador (sem unidades específicas) e o do antigo passa a Administrador de todas as unidades da organização; tudo ou nada, e a organização nunca fica com zero nem com dois Coordenadores ativos (D4).
- R6 (RN-ORG-06): o banco garante no máximo um vínculo de Coordenador ativo por organização.
- R7 (RN-ORG-06): o novo Coordenador recebe e-mail de confirmação da transferência, enviado só depois da troca gravada.
- R8: a troca vale na hora para as duas contas, sem novo login: as permissões vêm do vínculo a cada requisição (D5).
- R9 (RN-ACE-17): o novo Coordenador sem duas etapas é obrigado a configurá-las no próximo acesso (regra já existente; o item só prova).
- R10 (auditoria): a troca grava `organization.coordination_transferred` com antes e depois (contas e perfis), sem senha, token nem código; recusas não gravam nada.
- R11 (RNF-26): a tela abre janela com o resumo do que acontece, e-mail do novo Coordenador, confirmação digitada e, depois, a janela de senha (`ReauthDialog`); só então chama a rota.
- R12: a tela é visível só ao Coordenador, tem os quatro estados, funciona a partir de 360 px, passa no axe nos dois temas e os textos ficam em pt-BR.
- R13: a rota entra em `docs/openapi.yaml` e no handler MSW batendo com ele.

## 3. Critérios de aceite

- CA1 (R1, R5): dado o Coordenador A e o Administrador B ativo da organização, quando A chama a rota com token, confirmação e e-mail de B, então 204; B é Coordenador ativo e A é Administrador com todas as unidades da organização.
- CA2 (R1): dado um Administrador, Professor ou conta do portal, quando chama a rota, então 403 e nada muda.
- CA3 (R2): dado ausência de token, token de outra ação, de outra sessão, vencido ou já usado, quando A chama a rota, então 403 `REAUTH_REQUIRED` e nada muda; o mesmo token não serve duas vezes.
- CA4 (R3): dado confirmação em branco ou diferente de `TRANSFERIR`, então 400 `VALIDATION_ERROR` em `confirmation`, nada muda; `  transferir ` é aceito.
- CA5 (R4): dado e-mail inexistente, de Administrador de outra organização, de Professor, de vínculo pendente ou encerrado, ou o do próprio Coordenador, então 400 `VALIDATION_ERROR` em `email` e nada muda.
- CA6 (R5): dado falha no meio da troca, então nenhum vínculo muda (transação única); o antigo Coordenador recebe os vínculos de unidade de todas as unidades da organização, e o novo não mantém vínculos de unidade.
- CA7 (R6): dado o banco com um Coordenador ativo, quando se tenta ativar um segundo na mesma organização, então o índice único recusa; em organizações diferentes é permitido.
- CA8 (R7): dado troca concluída, então um e-mail sai para o endereço do novo Coordenador com o nome da organização; dado recusa, nenhum e-mail sai.
- CA9 (R8): dado troca concluída, quando A e B (sessões já abertas) chamam `GET /organization` e `PATCH /organization`, então A lê e recebe 403 ao alterar, e B lê e altera.
- CA10 (R9): dado B sem duas etapas, quando B acessa depois da troca, então a regra de configuração obrigatória o barra até configurar.
- CA11 (R10): dado troca concluída, então há um registro de auditoria da organização com antes e depois e sem senha, token ou código; dado recusa por qualquer motivo, nenhum registro.
- CA12 (R1, isolamento): dado Coordenador de outra organização, quando tenta indicar um Administrador desta, então 400 `VALIDATION_ERROR` em `email`; e as duas organizações do teste mantêm seus Coordenadores.
- CA13 (R11): dado a tela da organização, quando o Coordenador clica em "Transferir coordenação", então a janela mostra o resumo; o botão de continuar só habilita com e-mail e confirmação digitada; ao continuar abre a janela de senha e só então chama a rota com `X-Reauth-Token`.
- CA14 (R11, R12): dado sucesso, então a sessão é recarregada, a janela fecha, aparece aviso de sucesso e a tela passa a mostrar a organização como Administrador (sem o botão); dado 400 mostra a mensagem no campo; dado erro de rede, estado de erro com nova tentativa; dado 403, estado sem permissão.
- CA15 (R12): dado Administrador ou Professor, então o botão não aparece; dado 360 px e os temas claro e escuro, então sem rolagem horizontal e sem violações do axe.
- CA16 (R13): o teste de contrato e o teste do handler MSW passam com a rota nova.

## 4. Plano técnico

- Dados: migração `V16__single_active_coordinator.sql` com índice único parcial `account_link (organization_id) where role = 'COORDINATOR' and status = 'ACTIVE'`. Sem tabela nova; seed não muda. A troca atualiza `account_link.role` e `account_link_unit`.
- API: `POST /api/v1/organization/coordination-transfer`, corpo `{ email, confirmation }`, cabeçalho `X-Reauth-Token`; 204; erros 403 (perfil ou `REAUTH_REQUIRED`), 400 `VALIDATION_ERROR` com `fields` (`confirmation`, `email`), como no resto da API. Chama `CriticalActionGuard.consume` por último, depois de todas as validações, e a troca roda numa transação da organização com a linha da organização travada (`for update`). Ordem na transação: rebaixar o antigo para Administrador, promover o alvo, trocar os vínculos de unidade (índice único parcial evita estado duplo; a ordem respeita a restrição `unique (account_id, organization_id, role)`).
- Interface: botão "Transferir coordenação" em `OrganizationSettingsPage` (só Coordenador), janela `TransferCoordinationDialog` (resumo, e-mail, confirmação digitada), `ReauthDialog` com `TRANSFER_COORDINATION`, recarga da sessão e aviso. Estados: carregando (enviando), erro, sem permissão; "vazio" não se aplica a uma janela de ação (a tela da organização já os tem).
- Segurança: Coordenador apenas (servidor); senha/código via reauth de uso único; e-mail do alvo só resolvido dentro da organização do contexto (RLS + filtro `organization_id`); mesma resposta 400 para qualquer alvo inelegível (não revela contas de outras organizações); e-mail do alvo e token fora do log e fora da auditoria.
- Arquivos: a criar `backend/src/main/java/br/com/ctcore/organization/CoordinationTransfer.java`, `V16__single_active_coordinator.sql`, `frontend/src/features/organization/TransferCoordinationDialog.tsx` (+ teste), testes `CoordinationTransferIT`, `CoordinationTransferRouteIT`; a alterar `OrganizationController.java` (rota), `mail/EmailTemplate.java` e `messages.properties` (modelo `COORDINATION_TRANSFERRED`, sem código secreto, sem link), `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts` (+ teste MSW), `OrganizationSettingsPage.tsx`, `frontend/src/i18n/pt-BR.json`.
- Reuso: `CriticalAction.TRANSFER_COORDINATION`, `CriticalActionGuard`, `ReauthDialog`, `Permissions`/`ORGANIZATION_SETTINGS`, `OrganizationTransactions`, `AuditService`, `EmailService`, `OrganizationBase` (base dos ITs), regra de duas etapas obrigatórias do Coordenador (`TwoFactorService`).
- Riscos: dados antigos com dois Coordenadores ativos quebram a migração (percebido ao subir; seed e testes conferidos); sessão do front mostra o perfil velho até recarregar (recarga após sucesso); e-mail falhando não pode desfazer a troca (envio pela fila `EmailService`, que tenta de novo).

## 5. Tarefas

- [x] T1: migração `V16` do índice único parcial do Coordenador — cobre CA7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=CoordinationTransferIT`
- [x] T2: serviço `CoordinationTransfer` (permissão, confirmação, alvo elegível, troca de perfis e unidades, auditoria, e-mail pós-commit) e rota no controller — cobre CA1, CA4, CA5, CA6, CA8, CA11 — prova: `CoordinationTransferIT` e teste unitário da validação
- [x] T3: modelo de e-mail `COORDINATION_TRANSFERRED` e textos — cobre CA8 — prova: teste do `CoordinationTransferIT` lendo a caixa simulada
- [x] T4: testes de rota: perfis, reauth, isolamento entre organizações, efeito imediato nas sessões e duas etapas do novo Coordenador — cobre CA2, CA3, CA9, CA10, CA12 — prova: `CoordinationTransferRouteIT`
- [x] T5: OpenAPI e handler MSW da rota — cobre CA16 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npm test` (teste MSW)
- [x] T6: `TransferCoordinationDialog`, botão na tela da organização, textos pt-BR, recarga da sessão — cobre CA13, CA14, CA15 — prova: testes de tela com axe nos dois temas (`npm test`)
- [x] T7: regressão completa e cobertura — não cobre critério: confere o "Pronto quando" — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov`

## 6. Decisões

- D1 (muda o comportamento): a troca é imediata depois de confirmação digitada e senha do Coordenador; o e-mail ao novo Coordenador confirma o que aconteceu, sem etapa de aceite dele. Motivo: a regra lista "e-mail de confirmação ao novo Coordenador" como parte do ato do atual, e a organização nunca fica sem Coordenador nem pendente. Alternativa descartada: transferência pendente até o novo aceitar (tabela, expiração, cancelamento; crescimento que a regra não pede).
- D2 (muda o comportamento): o novo Coordenador é um Administrador com vínculo ativo na mesma organização, indicado pelo e-mail. Motivo: ainda não há lista de usuários e o Administrador é o perfil imediatamente abaixo, já conhecido pela organização. Alternativa descartada: qualquer e-mail ou conta de outro perfil (criaria vínculo novo e convite, fora do escopo).
- D3: confirmação digitada é a palavra `TRANSFERIR`, validada no servidor e na tela. Motivo: o canvas (`TransferirCoordenacao.dc.html`) não está no repositório; a palavra fixa é a leitura mais simples de RNF-26. Alternativa: digitar o nome da organização.
- D4 (muda o comportamento): "Administrador de todas as unidades" vira vínculos de unidade para todas as unidades existentes da organização no momento da troca (unidades criadas depois seguem a regra comum de atribuição). O novo Coordenador perde seus vínculos de unidade, pois o Coordenador alcança todas.
- D5: não se encerram as sessões das duas contas; o perfil vem do vínculo a cada requisição. Motivo: efeito imediato sem forçar novo login. Alternativa: encerrar as sessões do antigo (pior experiência, sem exigência na regra).
- D6 (ajuste pedido na Construção): erros de campo respondem 400 `VALIDATION_ERROR` com `fields`, padrão de toda a API e do `docs/openapi.yaml` (`ApiValidationException`), e não 422. R3, R4, CA4, CA5, CA12 e CA14 mantêm o número e só trocam o código. Alternativa descartada: criar 422 só para esta rota.
- Suposição: a rota usa `ORGANIZATION_SETTINGS` nível `FULL` para decidir quem é Coordenador, como em #28, sem permissão nova na tabela. Se a tabela de permissões ganhar linha própria, só a verificação muda.
- Suposição: o desenho do canvas não pôde ser consultado; a janela segue os componentes de `ReauthDialog`/`Modal`. Se o canvas divergir, ajusta-se só a tela.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V16__single_active_coordinator.sql` (índice único parcial; também amplia o check `email_message_template_check` com `COORDINATION_TRANSFERRED`) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=CoordinationTransferIT` → 8 testes verdes (CA7 incluído)
- T2 — arquivos: `organization/CoordinationTransfer.java`, `OrganizationController.java` (rota), `CoordinationTransferTest.java`, `CoordinationTransferIT.java` — prova: mesmo comando → verdes (CA1, CA4, CA5, CA6, CA8, CA11)
- T3 — arquivos: `mail/EmailTemplate.java`, `messages.properties` — prova: `CoordinationTransferIT` confere a linha em `email_message` → verde
- T4 — arquivos: `CoordinationTransferIT.java` (perfis, reauth, isolamento, sessões reais, duas etapas) — prova: mesmo comando → verde (CA2, CA3, CA9, CA10, CA12)
- T5 — arquivos: `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/organization.msw.test.ts` — prova: `./backend/mvnw -f backend/pom.xml verify` (inclui `OpenApiContractTest`, `ContractRoutesTest`) → BUILD SUCCESS; `npx vitest run src/mocks/organization.msw.test.ts` → verde
- T6 — arquivos: `TransferCoordinationDialog.tsx` (+ `.test.tsx`), `OrganizationSettingsPage.tsx`, `i18n/pt-BR.json`, `lib/api.ts` (cabeçalhos opcionais) — prova: `npx vitest run src/features/organization` → verde, axe nos dois temas a 360 px
Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (JaCoCo incluso); em `frontend/`: `npm run lint`, `npm run typecheck`, `npm run build` sem erro; `npm run test:cov` → 50 arquivos, 351 testes verdes, linhas 97,21%. Não rodou: `npm run e2e` (Playwright), fora desta sessão.
Desvios: (1) erros de campo respondem 400 `VALIDATION_ERROR` (padrão de toda a API e do OpenAPI), não 422 (ver pedido de ajuste); (2) um único `CoordinationTransferIT` no lugar de `CoordinationTransferIT` + `CoordinationTransferRouteIT`; (3) a migração V16 também amplia o check de modelos de e-mail; (4) recarga da sessão pelo `setSession` após `GET /auth/session`, sem passar por "carregando", para o aviso não se perder; (5) falha no meio da troca responde 409 (DataIntegrity, tratado pelo handler comum), tudo desfeito.
Não é do item: nenhum.
Pedido de ajuste: R3, R4, CA4, CA5, CA12, CA14 — pedem 422 para `confirmation` e `email`, mas a API inteira e o `docs/openapi.yaml` usam 400 `VALIDATION_ERROR` com `fields` (`ApiValidationException`); o código e o contrato do item seguem 400. Ajustar o texto para 400 (ou decidir criar 422 para toda a API).
Pedido de ajuste atendido pela fase 2 (D6): a especificação já pede 400; nenhum ajuste pendente. Retomada: conferidos `CoordinationTransferIT`/`CoordinationTransferTest` (`mvnw verify -Dit.test=CoordinationTransferIT`, sem erro) e `npx vitest run src/features/organization src/mocks/organization.msw.test.ts` → 5 arquivos, 33 testes verdes.

## Verificação

### Rodada 1 — OK — 2026-10-18, estado d7734234e23af927a84965269723113446e11776

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `CoordinationTransferIT.transfers` dentro de `mvnw verify` (185 testes, 0 falhas) |
| CA2 | PASSOU | `CoordinationTransferIT.profiles` |
| CA3 | PASSOU | `CoordinationTransferIT.reauth` |
| CA4 | PASSOU | `CoordinationTransferIT.confirmation` e `CoordinationTransferTest` |
| CA5 | PASSOU | `CoordinationTransferIT.target` |
| CA6 | PASSOU | `CoordinationTransferIT.atomic`; vínculos de unidade em `transfers` |
| CA7 | PASSOU | `CoordinationTransferIT.singleCoordinator` |
| CA8 | PASSOU | `CoordinationTransferIT.transfers` (linha em `email_message`); recusas sem e-mail em `target`/`atomic` |
| CA9 | PASSOU | `CoordinationTransferIT.immediate` |
| CA10 | PASSOU | `CoordinationTransferIT.immediate` |
| CA11 | PASSOU | `CoordinationTransferIT.transfers` |
| CA12 | PASSOU | `CoordinationTransferIT.target` |
| CA13 | PASSOU | `TransferCoordinationDialog.test.tsx` (CA13/CA14) |
| CA14 | PASSOU | `TransferCoordinationDialog.test.tsx` (sucesso, 400, 403, erro) |
| CA15 | PASSOU | `TransferCoordinationDialog.test.tsx` (Administrador sem botão; axe a 360 px nos dois temas) |
| CA16 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` dentro do `verify`; `organization.msw.test.ts` dentro do `test:cov` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 185 ITs, JaCoCo ok; em `frontend/`: `npm run lint`, `npm run typecheck`, `npm run test:cov` (linhas 97,21%), `npm run build` → sem erro. Não rodado: `npm run e2e` (Playwright).
Achados:
- sugestão, código, `CoordinationTransfer.java` — `guard.consume` queima o token antes da troca; se a troca falhar (409), o usuário precisa informar a senha de novo. Aceitável pelo plano técnico.

## Entrega

Rota de transferência da coordenação (RF-ORG-06): o Coordenador indica um Administrador ativo da organização pelo e-mail, digita `TRANSFERIR` e confirma com a senha; a troca é imediata, atômica e auditada, o antigo Coordenador vira Administrador de todas as unidades e o novo recebe e-mail de confirmação. Migração V16 garante um único Coordenador ativo por organização. Tela `TransferCoordinationDialog` na página da organização, OpenAPI e handler MSW atualizados.

Verificação: `mvnw verify` (185 ITs, JaCoCo ok); `npm run lint`, `typecheck`, `build`, `test:cov` (linhas 97,21%) verdes. `npm run e2e` não rodado localmente; fica para o CI.
Decisões: ver D1 a D6 (troca sem aceite do novo, erros de campo em 400 `VALIDATION_ERROR`). Limitação: o canvas não estava no repositório; a janela segue `ReauthDialog`/`Modal`.

Closes #33
