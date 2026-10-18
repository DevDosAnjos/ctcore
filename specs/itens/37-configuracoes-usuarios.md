# 37 — RF-ACE-06 · Configurações › Usuários

Origem: issue #37 (Sprint 03); `docs/especificacao/02-contas-e-acesso.md` (RF-ACE-06, RN-ACE-05, RN-ACE-07, RN-ACE-13, RN-ACE-20, tabela de permissões) e `01-organizacao-e-isolamento.md` (RN-ORG-05, RN-ORG-07). Branch: `item/37-configuracoes-usuarios`.

## 1. Objetivo

A tela Configurações › Usuários e a API por trás dela: a equipe da gestão da organização em lista (perfil, unidades, último acesso, situação) e as quatro ações do card: convidar, reenviar convite, alterar unidades e desativar. O Coordenador gere Administradores; o Administrador gere Professores das suas unidades. O servidor garante que nenhuma unidade ativa fique sem Administrador (RN-ORG-07) e oferece ao Coordenador a forma de nomear a si mesmo.

O servidor já tem os blocos: `InviteService` (convidar, reenviar, cancelar, #18), `LinkService.end` (encerrar vínculo com a regra de sessões da RN-ACE-20) e a linha `STAFF_USERS` da tabela de permissões (#27). Falta a rota HTTP, a regra RN-ORG-07, a troca de unidades e a tela.

Fora do escopo: cadastro do Professor como pessoa (Sprint 05); reativar vínculo desativado por botão (convidar de novo o mesmo e-mail faz isso, D6); desativar a conta inteira (a conta é da pessoa, não da academia, RN-ACE-09); visão dos Professores pelo Coordenador (D2); papéis Aluno e Responsável (portal); notas de versão (só existem na Sprint 15, #142).

## 2. Requisitos

- R1 (RF-ACE-06, D2): `GET /api/v1/users` lista a equipe do vínculo ativo: o Coordenador vê os Administradores da organização; o Administrador vê os Professores que tenham ao menos uma das suas unidades; Professor, Aluno e Responsável recebem 403. Cada linha traz `linkId`, nome, e-mail, perfil, unidades (id e nome), último acesso, situação (`PENDING`, `ACTIVE`, `ENDED`) e `canManage`.
- R2 (RF-ACE-06, D3): a lista pode ser filtrada por situação com `?status=`; sem filtro devolve `PENDING` e `ACTIVE`; `ENDED` só quando pedido.
- R3 (RF-ACE-06, RN-ACE-05): `POST /api/v1/users` (`email`, `name`, `unitIds`) convida: o Coordenador convida Administrador (perfil implícito), o Administrador convida Professor, só em unidades dele; usa `InviteService.invite`. O perfil nunca vem do cliente.
- R4 (RN-ACE-07): e-mail que já tem conta cria só o novo vínculo; e-mail já ligado a esta organização no mesmo perfil, com vínculo `PENDING` ou `ACTIVE`, responde 409 `ALREADY_LINKED`; conta desativada responde 403.
- R5 (RN-ORG-05, D4): convidar não passa de 200 usuários da gestão (vínculos `PENDING` ou `ACTIVE` de Coordenador, Administrador e Professor da organização); acima disso 409 `STAFF_LIMIT_REACHED`.
- R6 (RF-ACE-06, RN-ACE-13): `POST /api/v1/users/{linkId}/resend` reenvia o convite de vínculo `PENDING` que a pessoa pode gerir (cancela o anterior; no máximo 3 reenvios por dia por pessoa, 429 além disso).
- R7 (RF-ACE-06, D5): `PUT /api/v1/users/{linkId}/units` troca as unidades de um vínculo `PENDING` ou `ACTIVE` que a pessoa pode gerir. O Coordenador define a lista inteira de um Administrador; o Administrador mexe só nas unidades dele num Professor e as demais do Professor ficam como estão. `unitIds` são as unidades desejadas dentro do alcance de quem chama. O vínculo nunca fica sem unidade (400; para sair, desativar).
- R8 (RN-ORG-07): tirar uma unidade de um Administrador `ACTIVE` ou desativá-lo é recusado com 409 `LAST_UNIT_ADMINISTRATOR`, listando as unidades afetadas, quando for o último Administrador `ACTIVE` de alguma unidade ativa. Convites `PENDING` não contam.
- R9 (RN-ORG-07, D7): `POST /api/v1/users/coordinator-appointment` (`unitIds`), só do Coordenador, cria (ou reativa) para a própria conta um vínculo `ACTIVE` de Administrador nas unidades ativas indicadas, para destravar o R8. Sem unidade indicada ou com unidade inativa ou de outra organização, 400.
- R10 (RF-ACE-06, RN-ACE-20): `DELETE /api/v1/users/{linkId}` desativa: vínculo `ACTIVE` vira `ENDED` por `LinkService.end` (a conta e os vínculos em outras organizações ficam; as sessões acabam se era o último vínculo de gestão); vínculo `PENDING` tem o convite cancelado por `InviteService.cancel`. O Administrador só desativa Professor cujas unidades estejam todas entre as dele (D5). Ninguém desativa a si mesmo (409) nem o Coordenador (403).
- R11 (permissão): toda rota confere no servidor: perfil (`STAFF_USERS`), o vínculo-alvo ser da organização da sessão e do perfil que o chamador gere, e as unidades estarem no alcance do chamador. Alvo de outra organização ou fora do alcance responde 404 (não revela existência).
- R12 (auditoria): convidar, reenviar, cancelar, desativar (já gravados por `InviteService` e `LinkService`), alterar unidades (`link.units_changed`, com unidades antes e depois) e nomear a si mesmo (`link.coordinator_appointed`) gravam auditoria com ator, sem token, código nem senha.
- R13: a tela `/configuracoes/usuarios` (item "Usuários da gestão" do menu, que já existe) mostra a lista, o botão "Convidar" (janela do canvas `UsuarioConvidar`), e por linha "Reenviar convite" (se `PENDING`), "Alterar unidades" e "Desativar"; só mostra as ações com `canManage`. Tem os quatro estados (carregando, vazio, erro com "Tentar de novo", sem permissão), funciona a partir de 360 px, passa no axe nos dois temas e tem todos os textos em `pt-BR.json`.
- R14 (RN-ORG-07, D7): quando o servidor devolve `LAST_UNIT_ADMINISTRATOR`, a tela explica quais unidades ficariam sem Administrador e, para o Coordenador, oferece "Nomear a mim mesmo" (R9) além de convidar ou alterar as unidades de outro Administrador; o Administrador só vê a explicação.
- R15: `docs/openapi.yaml` e os handlers MSW refletem as rotas novas e batem com a implementação.

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador com 2 Administradores e 1 Professor na organização, quando `GET /users`, então devolve só os 2 Administradores, cada um com unidades, `lastAccessAt` (nulo se nunca entrou), situação e `canManage = true`; sem token, hash nem dado de outras organizações.
- CA2 (R1): dado Administrador da unidade A, quando `GET /users`, então só vê Professores com ao menos a unidade A (um Professor das unidades A e B aparece, com as duas unidades); Professor da unidade C não aparece; `canManage` é `true` quando todas as unidades do Professor estão entre as do chamador e `false` caso contrário.
- CA3 (R1, R11): dado Professor, Aluno ou Responsável logado, quando chama qualquer rota de `/users`, então 403; sem sessão, 401.
- CA4 (R2): dado um vínculo `ENDED`, quando `GET /users` sem filtro, então não aparece; com `?status=ENDED` aparece.
- CA5 (R3): dado Coordenador, quando `POST /users` com e-mail novo, nome e `unitIds` válidas, então 201, vínculo `PENDING` de Administrador nessas unidades, e-mail de convite enviado, auditoria `invite.created`.
- CA6 (R3, R11): dado Administrador, quando convida com unidade que não é dele, ou com `role` no corpo, então 403 na unidade fora do alcance e o `role` é ignorado (o vínculo nasce Professor); Coordenador convidando para unidade de outra organização recebe 400.
- CA7 (R4): dado e-mail com conta em outra organização, quando convidado, então o convite cria só o vínculo; dado e-mail já ligado ao mesmo perfil nesta organização, 409 `ALREADY_LINKED`; dado conta desativada, 403 e nada é criado.
- CA8 (R5): dado organização com 200 vínculos de gestão `PENDING` ou `ACTIVE`, quando convida, então 409 `STAFF_LIMIT_REACHED` e nada é criado; com 199 funciona.
- CA9 (R6): dado vínculo `PENDING`, quando reenvia, então o convite anterior deixa de valer (410), o novo é enviado e há auditoria `invite.resent`; o quarto reenvio no dia responde 429; vínculo `ACTIVE`, de outro perfil ou fora do alcance responde 404.
- CA10 (R7): dado Administrador de A e B, quando o Coordenador troca para só A e C, então as unidades viram A e C, a auditoria registra antes e depois; com `unitIds` vazio, 400; com unidade de outra organização ou inativa, 400.
- CA11 (R7, R11): dado Professor de A e B e o Administrador de A e D, quando este envia `unitIds = [A, D]`, então o Professor fica com A, B (preservada) e D; com `[D]`, fica com B e D; com `[]` num Professor só de A, 400 (ficaria sem unidade); `unitIds` com unidade fora do alcance do chamador, 403.
- CA12 (R8): dado a unidade A com um único Administrador `ACTIVE`, quando o Coordenador o desativa ou tira A dele, então 409 `LAST_UNIT_ADMINISTRATOR` com A nos campos e nada muda; com um segundo Administrador `ACTIVE` em A funciona; com o segundo apenas `PENDING` continua recusado.
- CA13 (R9): dado o caso do CA12, quando o Coordenador chama `coordinator-appointment` com `unitIds = [A]`, então passa a existir vínculo `ACTIVE` de Administrador da conta dele em A, a unidade tem dois Administradores e a desativação do CA12 passa; chamar de novo é idempotente (um vínculo só); Administrador chamando recebe 403; unidade inativa ou de outra organização, 400; auditoria `link.coordinator_appointed`.
- CA14 (R10): dado Administrador `ACTIVE` que não é o último, quando o Coordenador desativa, então 204, vínculo `ENDED`, auditoria `link.ended`, e se era o último vínculo de gestão da conta as sessões dela acabam (401), sem afetar a conta nem vínculos de outras organizações.
- CA15 (R10): dado vínculo `PENDING`, quando desativado, então o convite fica cancelado (link do e-mail responde 410), o vínculo `ENDED` e há auditoria `invite.canceled`.
- CA16 (R10): dado Professor de A e B e Administrador só de A, quando este tenta desativá-lo, então 403 e nada muda; o mesmo Professor só de A é desativado. Desativar a si mesmo, 409; desativar o Coordenador, 403.
- CA17 (R11): dado vínculo de outra organização, ou de perfil que o chamador não gere (Administrador mexendo em Administrador; Coordenador mexendo em Professor), quando chama resend, units ou delete, então 404 e nada muda; teste de isolamento entre organizações e entre perfis para cada rota nova.
- CA18 (R12): dado cada ação de escrita, então existe registro de auditoria com ator, ação e alvo, e sem e-mail do convidado, código do convite, senha nem token.
- CA19 (R13): dado a tela em 360 px, então sem rolagem horizontal, axe sem violações nos dois temas, estados de carregando, vazio, erro com "Tentar de novo" e sem permissão exibidos; Coordenador vê "Convidar" e as ações dos Administradores; Administrador vê as ações só nos Professores com `canManage`; linha `PENDING` mostra "Reenviar convite".
- CA20 (R13): dado a janela Convidar, quando e-mail inválido, nome vazio ou nenhuma unidade escolhida, então o erro aparece no campo e nada é enviado; com dados válidos fecha, avisa e a nova linha aparece como "Convite enviado".
- CA21 (R14): dado `LAST_UNIT_ADMINISTRATOR` ao desativar, então a tela lista as unidades afetadas; o Coordenador usa "Nomear a mim mesmo" e repete a desativação com sucesso; o Administrador vê só a explicação.
- CA22 (R15): `OpenApiContractTest` e `ContractRoutesTest` passam; testes MSW cobrem as rotas novas no formato do servidor.
- CA23 (todos): ponta a ponta Playwright `@critical` "Coordenador convida um Administrador e desativa outro": entra, convida, vê a linha "Convite enviado", tenta desativar o último Administrador de uma unidade, nomeia a si mesmo e desativa com sucesso.

## 4. Plano técnico

- **Dados.** Não muda o esquema: `account_link`, `account_link_unit`, `account_invite` e `account.last_access_at` (V9) bastam. Nenhuma migração. Seed: não muda.
- **API** (`/api/v1/users`, cookie de sessão e CSRF como nas demais; `linkId` é o id da linha): `GET /users?status=` (200), `POST /users` (201, corpo `email`, `name`, `unitIds`), `POST /users/{linkId}/resend` (204), `PUT /users/{linkId}/units` (200, a linha atualizada), `DELETE /users/{linkId}` (204), `POST /users/coordinator-appointment` (200). Erros novos em `api/error/ErrorCode.java` com `messages.properties`: `LAST_UNIT_ADMINISTRATOR` (409, campos `unitIds`), `STAFF_LIMIT_REACHED` (409); reuso de `ALREADY_LINKED` (já mapeado a partir de `IdentityException`, conferir o status), `TOO_MANY_REQUESTS`, `NOT_FOUND`, `CONFLICT`. Contrato em `docs/openapi.yaml`.
- **Back-end:** pacote a criar `br.com.ctcore.staff`: `StaffUsers.java` (lista, convite, reenvio, troca de unidades, desativação, nomeação do Coordenador; confere `Permissions.grant(who, Permission.STAFF_USERS, null/unidade)`, o perfil-alvo e o alcance; contagem da RN-ORG-05 com `select ... for update` na organização, como `UnitCreation`; usa `Organizations.requireManagementUserCapacity`), `StaffUsersController.java` e `StaffRules.java` (regras puras: quem gere quem, alcance das unidades, mescla de unidades do Professor, última administração por unidade; testável sem banco, como `InviteRules`). Reuso: `InviteService.invite/resend/cancel` com `Inviter(who.accountId(), who.role(), who.units())`, `LinkService.end`, `ScopedAudit`/`AuditService` para o que é novo, `OrganizationTransactions.execute`. `InviteService.cancel`/`resend` hoje conferem o alcance por `InviteRules.allowed`; a camada nova confere antes, devolvendo 404 em vez de 403 para alvo fora do alcance (R11). Auditoria nova: `link.units_changed`, `link.coordinator_appointed`.
- **Interface:** a criar `frontend/src/features/users/`: `UsersPage.tsx` (rota `/configuracoes/usuarios`, dentro de `ManagementLayout`, mesmo padrão de `UnitsPage.tsx`), `InviteUserDialog.tsx`, `EditUnitsDialog.tsx`, `DeactivateUserDialog.tsx` (inclui o estado `LAST_UNIT_ADMINISTRATOR` e "Nomear a mim mesmo"), `useUsers.ts` (TanStack Query, como `useUnits.ts`) e testes. A alterar: `App.tsx` (rota; o item de menu `staffUsers` já aponta para `/configuracoes/usuarios` em `menuCatalog.ts`), `mocks/handlers.ts` e teste MSW, `i18n/pt-BR.json`. Reuso: `Card`, `states/*`, `ConfirmDialog`, `RelativeDate`, `lib/api.ts`, `useUnits` (lista de unidades para o seletor). Os canvas `ConfigUsuarios.dc.html`, `UsuarioConvidar.dc.html` e `Sidebar.dc.html` não estão acessíveis ao agente (como em #36): o layout segue os padrões do projeto.
- **Segurança.** Perfil, organização e alcance de unidades vêm sempre do principal da sessão, nunca do corpo (RNF-05); `role` do corpo é ignorado; a organização do alvo é checada além do RLS (filtro no código mais RLS, RN-ORG-01); alvo fora do alcance responde 404; lista sem token, hash nem IP; e-mail só aparece para quem gere aquele vínculo; mensagens de erro sem revelar vínculos de outras organizações; trava da organização (`for update`) nas escritas que contam ou removem Administradores, para que duas desativações simultâneas não deixem uma unidade sem Administrador.
- **Riscos.** (1) O vínculo extra de Administrador do Coordenador (D7) pode aparecer como segunda opção no modal de escolha de vínculo (`ChooseLinkModal`) e confundir a sessão: conferir na construção como `SessionService`/`ChooseLinkGate` tratam dois vínculos de gestão na mesma organização; se atrapalhar, esconder o vínculo extra do modal em vez de mudar a regra. (2) `InviteService.invite` lança `AccessDeniedException` para conta desativada, o que vira 403 genérico; conferir a mensagem. (3) Sem a tabela de Professores (Sprint 05), "Professor" aqui é só o vínculo de gestão; nada é criado na entidade Professor. (4) Padrão de listagem sem paginação: até 200 usuários cabem numa resposta (RN-ORG-05).

## 5. Tarefas

Duas partes, cada uma construível em uma sessão: servidor (T1 a T6) e tela (T7 a T10).

- [x] T1: regras puras `StaffRules` (quem gere quem, alcance, mescla de unidades do Professor, última administração) — cobre CA2 (`canManage`), CA11, CA12 (lógica), CA16 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=StaffRulesTest`.
- [x] T2: `StaffUsers` e `StaffUsersController`: lista com filtro e `canManage`, convite (limite de 200, perfil implícito, alcance), reenvio — cobre CA1, CA2, CA3, CA4, CA5, CA6, CA7, CA8, CA9 — prova: `verify -Dit.test='StaffUsersListIT,StaffUsersInviteIT'` (nomes a criar).
- [x] T3: troca de unidades e desativação com a regra RN-ORG-07 (`LAST_UNIT_ADMINISTRATOR`), trava da organização e novos `ErrorCode` com mensagens — cobre CA10, CA11, CA12, CA14, CA15, CA16 — prova: `verify -Dit.test='StaffUsersUnitsIT,StaffUsersDeactivateIT'`.
- [x] T4: `coordinator-appointment` — cobre CA13 — prova: `verify -Dit.test=StaffUsersAppointmentIT`.
- [x] T5: testes de isolamento entre organizações e entre perfis, acesso sem sessão, e auditoria de cada escrita sem dados proibidos — cobre CA3, CA17, CA18 — prova: `verify -Dit.test=StaffUsersIsolationIT`.
- [x] T6: OpenAPI (seis rotas, esquemas, erros) e handlers MSW — cobre CA22 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks`.
- [x] T7: `UsersPage` com lista, quatro estados, ações por linha conforme `canManage`, rota e textos pt-BR — cobre CA19 (estados e permissões) — prova: `npx vitest run src/features/users`.
- [x] T8: janelas Convidar e Alterar unidades com validação de campo — cobre CA20 — prova: `npx vitest run src/features/users`.
- [x] T9: janela Desativar com o estado `LAST_UNIT_ADMINISTRATOR` e "Nomear a mim mesmo"; 360 px e axe nos dois temas — cobre CA19, CA21 — prova: `npx vitest run src/features/users`.
- [x] T10: e2e `@critical` "Coordenador convida um Administrador e desativa outro" — cobre CA23 — prova: `npm run e2e` em `frontend/`.
- [x] T11: regressão completa — não cobre critério: confere o "Pronto quando" (cobertura e 100% nos pacotes exigidos pelo CI) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: o identificador da linha na API é o `linkId`, não a conta: o vínculo é o que se convida, troca de unidade e desativa, e a mesma pessoa pode ter vínculos em outras organizações. Alternativa descartada: `accountId`, que vazaria a identidade entre organizações.
- D2 (muda o comportamento): a lista segue a tabela de permissões ao pé da letra: o Coordenador gere e vê só os Administradores; o Administrador vê e gere Professores com ao menos uma das suas unidades. O Coordenador não vê Professores nesta tela; a gestão de Professores vem na Sprint 05 (módulo Professores). Alternativa: Coordenador vendo Professores só para consulta.
- D3: a lista esconde vínculos desativados (`ENDED`) por padrão e os mostra com o filtro, para a tela de uso diário ficar curta sem perder o histórico de quem saiu.
- D4: o limite de 200 usuários da gestão (RN-ORG-05) conta vínculos `PENDING` e `ACTIVE` de Coordenador, Administrador e Professor, porque convite pendente já vai virar usuário. "Só pelo suporte" ainda não tem fluxo; o limite recusa sempre. Suposição: o suporte autoriza por tela própria em sprint posterior (Painel). Se estiver errada, só muda o ponto de autorização.
- D5 (muda o comportamento): o Administrador mexe só nas unidades dele num Professor que tenha unidades fora do alcance dele: pode adicionar ou tirar as próprias, e as outras ficam. Só desativa o Professor se todas as unidades dele forem do Administrador; senão tira as suas unidades. Motivo: evitar Professor que nenhum Administrador consegue remover, sem dar poder sobre unidades alheias. Alternativa: exigir que todas as unidades do Professor sejam do Administrador para qualquer ação (deixaria Professor de duas unidades sem gestor).
- D6: "desativar" encerra o vínculo desta organização (`ENDED`), não a conta (a conta é da pessoa, vale em toda a plataforma, RN-ACE-01/09). Para voltar, convidar o mesmo e-mail de novo (o `InviteService` já reabre o vínculo como `PENDING`). Alternativa: botão "Reativar", que fica como sugestão de item.
- D7 (muda o comportamento): "o Coordenador nomeia outro, podendo ser ele mesmo" vira, para o próprio Coordenador, um vínculo extra `ACTIVE` de Administrador da mesma conta nas unidades escolhidas, criado por `coordinator-appointment`. Motivo: é a forma mais curta de a regra funcionar com o modelo de dados atual (um vínculo por papel; sem unidades no vínculo de Coordenador). Alternativa: contar o Coordenador implicitamente como Administrador de todas as unidades, o que esconderia o caso da lista da tela de Unidades (#29) e da regra de transferência (#33). Suposição a confirmar na construção: duas opções de vínculo na mesma organização no modal de escolha (risco 1).
- D8: RN-ORG-07 vale a partir de agora nas ações desta tela. Unidades criadas sem Administrador (convite opcional no assistente, #30) e Administradores que saem por outros caminhos não são corrigidas aqui; a regra impede só que uma ação sua deixe de 1 para 0. Alternativa: bloquear também a criação de unidade sem Administrador (muda o assistente já entregue).
- D9: os canvas desta tela não estão acessíveis ao agente; o layout e os textos seguem os padrões do projeto e o texto do card, e o dono confere no PR.

## Construção

Parte 1 de 2 (servidor, T1 a T6), sessão anterior. Parte 2 (tela, T7 a T10) e regressão final (T11) abaixo, em "Parte 2".

- T1 — arquivos: `backend/src/main/java/br/com/ctcore/staff/StaffRules.java`, `backend/src/test/java/br/com/ctcore/staff/StaffRulesTest.java` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=StaffRulesTest` → 3 testes passam
- T2 — arquivos: `staff/StaffUsers.java`, `staff/StaffUsersController.java`, `ErrorCode.java` (`LAST_UNIT_ADMINISTRATOR`, `STAFF_LIMIT_REACHED`, `ALREADY_LINKED`), `ApiExceptionHandler.java` (ALREADY_LINKED deixa de virar `CONFLICT`), `messages.properties`; testes `StaffBase`, `StaffUsersListIT`, `StaffUsersInviteIT` — prova: `verify -Dit.test='StaffUsersListIT,StaffUsersInviteIT'` → 9 testes passam
- T3 — arquivos: `StaffUsers.java` (troca de unidades, desativação, trava `select ... for update` na organização), `identity/LinkService.java` (novos `endInScope` e `sessionsAfter`, `end` continua igual para quem o chama), `identity/AccountAudit.java`; testes `StaffUsersUnitsIT`, `StaffUsersDeactivateIT` — prova: `verify -Dit.test='StaffUsersUnitsIT,StaffUsersDeactivateIT'` → 8 testes passam
- T4 — arquivos: `StaffUsers.appointCoordinator`, `StaffUsersAppointmentIT` — prova: `verify -Dit.test=StaffUsersAppointmentIT` → 1 teste passa
- T5 — arquivos: `StaffUsersIsolationIT` — prova: `verify -Dit.test=StaffUsersIsolationIT` → 4 testes passam (organização, perfil, sem sessão, auditoria sem e-mail, código, senha ou token)
- T6 — arquivos: `docs/openapi.yaml` (seis operações, `StaffUser`, `StaffInvite`, `StaffUnits`, três códigos de erro), `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/users.msw.test.ts` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → passam; `npx vitest run src/mocks` → 10 arquivos, 43 testes passam
Regressão (parte do servidor): `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 233 testes, JaCoCo (80% e 100% nos pacotes exigidos) sem reprovação; em `frontend/`, `npm run typecheck` e `npm run lint` limpos. Front-end completo (`test:cov`, `build`, `e2e`) não foi rodado nesta parte.
Desvios:
- `LinkService.end` foi dividido em `endInScope` (banco) e `sessionsAfter` (fim das sessões, RN-ACE-20): dentro da transação da organização, o registro `account.sessions_ended` tentava abrir outra organização e dava 403. A desativação agora termina as sessões depois do commit. O comportamento de `end` para os demais chamadores não mudou.
- `AccountAudit.record` passou a pular organizações diferentes da do escopo da requisição (a escrita nelas era impossível e derrubava a requisição). Ao desativar o último vínculo de gestão de uma conta com vínculo ativo em outra organização, o `account.sessions_ended` fica só na organização da sessão (que já não tem vínculo ativo), ou seja, não é gravado; o `link.ended` continua gravado.
- O limite de 200 e o `ALREADY_LINKED` são conferidos em `StaffUsers`, antes do `InviteService.invite`, que só recusava vínculo `ACTIVE`; vínculo `PENDING` do mesmo perfil agora dá 409 (R4).
- `ALREADY_LINKED` ganhou código próprio no `ErrorCode` (antes saía como `CONFLICT`); nenhum teste anterior dependia disso.
- `LAST_UNIT_ADMINISTRATOR` devolve as unidades em um campo `unitIds` com os ids separados por vírgula (o contrato de erro só tem `field` e `message`); a tela (T9) cruza com a lista de unidades para mostrar os nomes.
Para a parte 2 (risco 1 do plano): o vínculo extra de Administrador do Coordenador (D7) existe no servidor; o comportamento de `ChooseLinkModal` com dois vínculos na mesma organização ainda não foi conferido.
Não é do item: `specs/backlog.md` já estava alterado antes desta sessão.

### Parte 2 (tela, T7 a T11)

- T7 — arquivos: `frontend/src/features/users/{UsersPage.tsx,useUsers.ts,UnitChecks.tsx}`, `App.tsx` (rota), `features/permissions/menuCatalog.ts` (`staffUsers` disponível), `i18n/pt-BR.json` (bloco `users`), `buildMenu.test.ts` (menu agora traz Usuários) — prova: `npx vitest run src/features/users` → 11 testes passam (lista, `canManage`, Reenviar convite só em pendente, carregando, erro com nova tentativa, vazio, 403)
- T8 — arquivos: `InviteUserDialog.tsx`, `EditUnitsDialog.tsx` — prova: mesmo comando → validação de campo sem envio, convite com nova linha "Convite enviado", 409 `ALREADY_LINKED`, troca de unidades, sem unidade bloqueia
- T9 — arquivos: `DeactivateUserDialog.tsx`, `UsersPage.test.tsx` — prova: mesmo comando → `LAST_UNIT_ADMINISTRATOR` lista as unidades, Coordenador nomeia a si mesmo e a desativação é repetida, Administrador vê só a explicação; axe a 360 px nos temas claro e escuro, com lista e janela aberta, sem violações
- T10 — arquivos: `frontend/e2e/usuarios.spec.ts` — prova: `npm run e2e` → 15 testes passam, incluindo o novo `@critical`
- T11 — prova: em `frontend/`, `npm run lint`, `npm run typecheck`, `npm run build`, `npm run test:cov` (55 arquivos, 428 testes, limites de cobertura atendidos) e `npm run e2e` (15 passam) → todos verdes. O back-end não mudou nesta parte; `verify` completo (233 testes, BUILD SUCCESS) é o da parte 1 e não foi repetido.
Desvios:
- O seletor de unidades das janelas usa `/units/reachable` (`useUnitScope`), que serve Coordenador (todas as ativas) e Administrador (as dele); não `/units`, que é só do Coordenador.
- Depois de "Nomear a mim mesmo" a desativação é repetida automaticamente, sem novo clique.
- O filtro `?status=` não tem controle na tela (R13 não pede); a lista mostra `PENDING` e `ACTIVE`.
Risco 1 (D7): `ChooseLinkModal` lista todos os vínculos da sessão com organização e perfil, então o vínculo extra de Administrador do Coordenador aparece como segunda opção "(Administrador)" quando existir; não quebra o fluxo, não foi alterado.
Não é do item: `specs/backlog.md` já estava alterado antes das sessões.

## Verificação

### Rodada 1 — OK — 2026-10-18, estado 25de3988aac2ae9b54bf43b899dc38ff9a019823

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2, CA4 | PASSOU | `StaffUsersListIT` (4) e `StaffRulesTest` (3) passam no `verify` |
| CA3, CA17, CA18 | PASSOU | `StaffUsersIsolationIT` (4): organização, perfil, sem sessão, auditoria sem dados proibidos |
| CA5, CA6, CA7, CA8, CA9 | PASSOU | `StaffUsersInviteIT` (5), inclui 199/200 e o quarto reenvio 429 |
| CA10, CA11 | PASSOU | `StaffUsersUnitsIT` (3) |
| CA12, CA14, CA15, CA16 | PASSOU | `StaffUsersDeactivateIT` (5) |
| CA13 | PASSOU | `StaffUsersAppointmentIT` (1) |
| CA19, CA20, CA21 | PASSOU | `UsersPage.test.tsx` no `test:cov` (axe a 360 px, dois temas; estados; Nomear a mim mesmo) |
| CA22 | PASSOU | `verify` inclui `OpenApiContractTest`/`ContractRoutesTest`; `users.msw.test.ts` no `test:cov` |
| CA23 | PASSOU | `npm run e2e`: 15 passam, inclui `usuarios.spec.ts` @critical |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → exit 0 (JaCoCo sem reprovação); em `frontend/`: `npm run lint`, `typecheck`, `build` limpos; `npm run test:cov` → limites atendidos; `npm run e2e` → 15 passam.
Achados:
- sugestão, código, `StaffUsers.deactivate` — D5 prevê que o Administrador "tira as suas unidades" do Professor com unidades alheias; hoje só há 403, e a tela de Alterar unidades cobre o caso.
- sugestão, código, `StaffUsers.resend` — a conferência de alcance e o reenvio rodam em transações separadas; sem impacto prático.

## Entrega

- Configurações › Usuários: lista de Administradores e Professores da organização, convite (com reenvio limitado), alteração de unidades, desativação e "Nomear a mim mesmo" quando o Coordenador desativa o único responsável; rotas novas em `/api/v1` no pacote `staff`, com isolamento por organização e por perfil, auditoria sem dados proibidos e contrato em `docs/openapi.yaml`.
- Front-end: página `features/users` com os quatro estados, axe a 360 px nos dois temas, textos em pt-BR, handlers MSW e e2e `@critical`.
- Verificação (rodada 1, OK): `verify` do back-end (233 testes) e, no front-end, lint, typecheck, build, `test:cov` e `e2e` (15) verdes.
- Limitações: a desativação de Professor com unidades alheias devolve 403 (a tela de Alterar unidades cobre o caso); reenvio e conferência de alcance em transações separadas, sem impacto prático.

Refs #37
