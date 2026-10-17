# 21 — RF-ACE-02 · Criar senha pelo convite

Origem: issue #21 (Sprint 02); `docs/especificacao/02-contas-e-acesso.md` (RF-ACE-02, RN-ACE-01, RN-ACE-08, RN-ACE-13). Branch: `item/21-criar-senha-pelo-convite`.

## 1. Objetivo

Entregar a tela do link do e-mail de convite (`/convite/:code`, o caminho que o servidor já monta em `EmailAccountMailer`): a pessoa vê o nome e o e-mail do convite e a validade, cria a senha com as exigências marcadas enquanto digita e entra. Convite vencido, usado ou cancelado explica e orienta pedir outro. O servidor de aceite já existe (#18); falta a tela, os dados que ela mostra (nome, e-mail e validade, que `GET /auth/invitations/{code}` ainda não devolve) e os handlers MSW. O componente das exigências da senha nasce reutilizável para a tela de nova senha (#22).

Fora do item: recuperação e nova senha (#22; só o componente de exigências é compartilhado), escolha de organização e área quando há mais de um vínculo (#23), envio, reenvio e cancelamento de convite pela tela de usuários (RF-ACE-06), duas etapas (#26).

## 2. Requisitos

- R1 (RF-ACE-02): a rota pública `/convite/:code` consulta `GET /auth/invitations/{code}` e mostra nome e e-mail do convite (somente leitura) e a validade (data e hora do vencimento).
- R2 (RF-ACE-02, RN-ACE-01, D1): `GET /auth/invitations/{code}` passa a devolver também `name` (pode ser nulo), `email` e `expiresAt`; só para código válido, nada para código inválido.
- R3 (RN-ACE-08): conta nova vê dois campos ("Senha" e "Confirmar senha") com a lista de exigências (8 ou mais caracteres, 1 número, 1 letra maiúscula) marcada enquanto digita, e botão "Mostrar"/"Ocultar".
- R4 (RF-ACE-02): "Criar senha e entrar" só fica habilitado com a senha válida (R3) e igual nos dois campos.
- R5 (RN-ACE-08): senha recusada pelo servidor por vazamento mostra "Essa senha é muito conhecida. Escolha outra." junto ao campo; fraca mostra a lista de exigências.
- R6 (RN-ACE-13, RF-ACE-02): convite vencido, usado, cancelado ou desconhecido mostra uma explicação própria de cada motivo e orienta a pedir outro convite a quem convidou; sem formulário.
- R7 (RN-ACE-01, D2): se o e-mail já tem conta (`existingAccount`), a tela mostra um campo só, "Sua senha atual", sem exigências nem confirmação, e diz que o novo acesso entra na conta que a pessoa já tem; senha errada mostra "E-mail ou senha incorretos." e 429 mostra o aviso de muitas tentativas.
- R8 (RF-ACE-02): aceito o convite, a sessão iniciada pelo servidor é guardada e a pessoa vai para `/` (D3).
- R9 (contrato): os handlers MSW de `GET /auth/invitations/{code}` e `POST /auth/invitations/{code}/accept` batem com `docs/openapi.yaml`; `msw.test.ts` passa.
- R10 (tela): estados aplicáveis (D4): carregando (consulta e envio), erro de rede ou 5xx com "Tentar de novo", convite inválido (R6); funciona a partir de 360 px, passa no axe nos temas claro e escuro, rótulo em todo campo, foco visível, `autocomplete="new-password"` (conta nova) ou `current-password` (conta existente), e-mail com `autocomplete="username"`. Todo texto em `pt-BR.json`.
- R11 (segurança): o código do convite nunca é registrado em log nem em armazenamento do navegador, e a senha só existe no estado do formulário.

## 3. Critérios de aceite

- CA1 (R1, R2): dado convite válido, quando `/convite/:code` abre, então aparecem nome e e-mail preenchidos e não editáveis e "Vale até" com a data e hora do vencimento; convite sem nome mostra só o e-mail.
- CA2 (R2): dado `GET /auth/invitations/{code}` com código válido, então o corpo traz `email`, `name` e `expiresAt` (7 dias depois da criação); com código inexistente, vencido, usado ou cancelado, responde 410 `INVITE_INVALID` sem esses campos (`AuthRoutesIT`).
- CA3 (R3): dado conta nova, quando digita a senha, então cada exigência troca para "atendida" ou "pendente" a cada tecla, com texto (não só cor) e anunciada a leitores de tela; "Mostrar" troca os dois campos e vira "Ocultar".
- CA4 (R4): dado senha válida mas diferente da confirmação, então o botão fica desabilitado e a confirmação mostra "As senhas não são iguais."; dado senha inválida e igual, também desabilitado; dado válida e igual, habilitado.
- CA5 (R5): dado `POST .../accept` respondendo 400 `PASSWORD_LEAKED`, então aparece "Essa senha é muito conhecida. Escolha outra." junto ao campo, o foco volta nele e a tela continua preenchida; dado `PASSWORD_WEAK`, aparece o erro com as exigências.
- CA6 (R6): dado 410 `INVITE_INVALID` com `reason` `EXPIRED`, `USED` ou `CANCELED`, então a tela mostra a explicação de cada um e a orientação de pedir outro convite; dado motivo ausente ou `DEACTIVATED`, mostra a explicação genérica "Este convite não é válido" com a mesma orientação; nenhum caso mostra formulário.
- CA7 (R6): dado 410 devolvido pelo `POST .../accept` (convite usado entre a abertura e o envio), então a tela troca para a explicação do motivo.
- CA8 (R7): dado `existingAccount` verdadeiro, então há um campo "Sua senha atual", sem lista de exigências nem confirmação, e o botão fica habilitado com a senha preenchida; 401 `INVALID_CREDENTIALS` mostra "E-mail ou senha incorretos.", limpa o campo e devolve o foco; 429 mostra o aviso de muitas tentativas.
- CA9 (R8): dado 200, então a sessão fica guardada (o `SessionProvider` passa a `ready`) e a rota vira `/`; o código não aparece na URL seguinte.
- CA10 (R10): dado 500 ou rede fora, na consulta ou no envio, então aparece o erro com "Tentar de novo" que repete; enquanto consulta ou envia, a tela mostra o carregando e o botão fica desabilitado, sem envio duplo.
- CA11 (R10): dado a tela em 360 px, nos temas claro e escuro, em cada estado (formulário de conta nova, de conta existente, convite inválido), então o axe não aponta violação e não há rolagem horizontal.
- CA12 (R9): dado o OpenAPI, então `msw.test.ts` confere que os dois handlers existem com status e corpo do contrato (inclui `name`, `email`, `expiresAt`).
- CA13 (R1, R3, R4, R8): fluxo `@critical` "Convite e criação de senha" no Playwright: abre `/convite/<código válido>`, vê nome e e-mail, a senha fraca deixa o botão desabilitado, a senha válida e igual habilita, entra e chega a `/`; outro código mostra o convite vencido.
- CA14 (R11): dado a tela montada, então nenhuma chamada a `localStorage`/`sessionStorage` nem `console` recebe o código ou a senha (teste com espiões).

## 4. Plano técnico

- **Dados.** Não muda (`account.name`, `account.email` e `account_invite.expires_at` já existem em `V6__identity.sql`).
- **API/OpenAPI.** Nenhuma rota nova. `GET /auth/invitations/{code}` ganha `name`, `email` e `expiresAt` na resposta (campos aditivos, `InviteInfo` em `docs/openapi.yaml`, existente). Rota continua pública e protegida pelo código (32 bytes aleatórios, só o hash no banco); quem tem o código já recebeu o e-mail, então mostrar o e-mail e o nome dele não vaza dado de outra conta (D1).
- **Back-end.** `identity/InviteService.java` (existente): `Info` ganha `email`, `name`, `expiresAt`; `describe` os devolve a partir de `Found`, cuja consulta (`usable`) passa a ler também `a.name`. `AuthController.java` não muda. Testes: `AuthRoutesIT` (existente) confere os três campos no convite válido e a ausência deles no 410.
- **Front-end.** A criar em `src/features/auth/`: `InvitePage.tsx` (consulta, formulário, estados), `PasswordRules.tsx` (lista de exigências marcada ao digitar e função `checkPassword`, reutilizável pela #22) e `PasswordFields.tsx` (senha, confirmação e Mostrar/Ocultar, com `autocomplete="new-password"`). Alterar: `src/App.tsx` (rota pública `/convite/:code`), `src/i18n/pt-BR.json` (`invite.*`, `password.*`), `src/mocks/handlers.ts` (os dois handlers, com códigos de teste `valido`, `existente`, `vencido`, `usado`, `cancelado`), `frontend/README.md` (códigos do mock, se o README lista os de login). Reutiliza `lib/api.ts`, `useSession().setSession`, `ErrorState`/`LoadingState`, `Button`, `SupportContact`, `ThemeToggle`, o layout de cartão da `LoginPage`, `RelativeDate` ou o formatador de `lib/format.ts` para o vencimento (usar o que já formata data e hora no fuso do navegador).
- **Interface.** Layout público igual ao do login (cabeçalho, cartão centralizado, uma coluna), sem o desenho do canvas (`PrimeiroAcesso.dc.html` não é acessível; D5). Quatro estados: carregando e erro; "vazio" e "sem permissão" não se aplicam a tela pública de link (D4); o convite inválido ocupa o lugar do formulário. `e2e/convite.spec.ts` (a criar) `@critical`.
- **Segurança.** O servidor decide validade, uso único, política de senha, lista de vazamento, bloqueio e limite por IP (#18); a tela só reflete. O front espelha as três exigências apenas como ajuda: o servidor é a barreira. Código só no caminho da URL (sem `next`, sem armazenamento, sem log); sem `Referer` para terceiros (o cartão não tem links externos). Mensagem única para senha atual errada (RN-ACE-11). Auditoria do aceite já existe no servidor (`invite.accepted`); esta mudança não grava dado novo.
- **Riscos.** Mostrar e-mail e nome a quem tem o código é a única exposição nova (D1). A conferência visual contra o canvas fica com o dono (D5). Dependência nova: nenhuma.
- **Doc.** Notas de versão: não existe arquivo ainda (RF-AJU-04); nada a atualizar.

## 5. Tarefas

- [x] T1: Back-end: `Info` com `email`, `name`, `expiresAt` e o `InviteInfo` do `docs/openapi.yaml` — cobre CA1 (dados), CA2 — prova: `./backend/mvnw -f backend/pom.xml verify -Dtest=OpenApiContractTest,ContractRoutesTest -Dit.test=AuthRoutesIT` (o IT confere os campos no válido e a ausência no 410).
- [x] T2: `PasswordRules` (com `checkPassword`) e `PasswordFields`, textos `password.*` — cobre CA3, CA4 — prova: `PasswordRules.test.tsx` e `PasswordFields.test.tsx` (marcação a cada tecla, confirmação, Mostrar/Ocultar).
- [x] T3: `InvitePage` com consulta, formulário de conta nova e de conta existente, mensagens por código e foco, rota em `App.tsx` e textos `invite.*` — cobre CA1, CA5, CA6, CA7, CA8, CA9, CA10, CA14 — prova: `InvitePage.test.tsx`.
- [x] T4: Handlers MSW dos dois endpoints e conferência com o OpenAPI — cobre CA12 — prova: `npm test` (`msw.test.ts`).
- [x] T5: Acessibilidade e 360 px de cada estado — cobre CA11 — prova: axe nos dois temas em `InvitePage.test.tsx` (com `src/test/axe.ts` e `device.ts`).
- [x] T6: e2e `@critical` `e2e/convite.spec.ts`, `frontend/README.md` e regressão — cobre CA13 — prova: em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`; e `./backend/mvnw -f backend/pom.xml verify`.

## 6. Decisões

- D1 (muda o comportamento do produto): `GET /auth/invitations/{code}` devolve nome, e-mail e validade do convite. Motivo: o card pede "nome e e-mail já preenchidos, validade do convite" e a resposta atual só tem papel e organização. O código é secreto e foi enviado a esse e-mail, e a resposta só sai para convite válido. Alternativa: pedir o e-mail na tela, descartada por contrariar o card.
- D2 (muda o comportamento do produto): para quem já tem conta, a tela pede só "Sua senha atual", porque o servidor já exige essa senha no aceite (RN-ACE-07) e não cria senha nova. Alternativa: mesma tela de criar senha, descartada por o servidor ignorar a senha nova e recusar a diferente.
- D3: depois do aceite a pessoa vai para `/`; a escolha de organização e área, quando há mais de um vínculo, é da #23 (como na D7 da #20). Sem `next`: o link do convite não tem destino além da área da pessoa.
- D4: tela pública sem lista: "vazio" e "sem permissão" não se aplicam; valem carregando, erro e o convite inválido (regra do card). Os quatro estados seguem obrigatórios nas telas autenticadas.
- D5: o canvas (`PrimeiroAcesso.dc.html`, `EmailConvite.dc.html`) não é acessível ao agente; a tela segue o texto do card e a base visual do login. Suposição: se o desenho diferir, o dono pede ajuste de layout, sem mudar regra. O e-mail de convite já existe (#19) e não muda.
- D6: "orientar a pedir outro" é texto ("peça um novo convite a quem convidou você") sem botão nem rota, porque o reenvio é feito por quem convidou (RF-ACE-06, outro item) e não há pedido de convite pelo convidado. Alternativa: formulário de pedido, descartada por criar rota pública nova fora do card (RN-ACE-04).
- D7: o limite de 3 reenvios por dia (RN-ACE-13) e o reenvio que cancela o anterior já são do servidor (`InviteService`, #18, com `InviteIT`); esta tela só mostra o resultado (convite cancelado), e a cobertura do código RN-ACE-13 aqui é CA2/CA6/CA7 mais os testes existentes. Sem tela de reenvio neste item.

## 7. Construção

- T1: `InviteService.java` (`Info` e `Found` com `email`, `name`, `expiresAt`), `docs/openapi.yaml` (`InviteInfo`), `AuthRoutesIT.java` (campos no válido, ausência no 410). Prova: `./backend/mvnw -f backend/pom.xml verify -Dtest=OpenApiContractTest,ContractRoutesTest -Dit.test=AuthRoutesIT`: AuthRoutesIT 10/10, ContractRoutesTest 3/3, OpenApiContractTest 4/4.
- T2: `PasswordRules.tsx`, `PasswordFields.tsx`, `password.*` em `pt-BR.json`, `PasswordRules.test.tsx`. Prova: `npx vitest run src/features/auth src/mocks`: passou.
- T3: `InvitePage.tsx`, rota em `App.tsx`, `invite.*` em `pt-BR.json`, `InvitePage.test.tsx`. Prova: mesmo comando: passou.
- T4: `src/mocks/handlers.ts` (consulta e aceite, códigos `valido`, `existente`, `vencido`, `usado`, `cancelado`) e `src/mocks/invite.msw.test.ts`. Prova: mesmo comando: passou (62 testes nas duas pastas).
- T5: axe nos dois temas a 360 px (conta nova, conta existente, convite inválido, erro de senha) em `InvitePage.test.tsx`. Prova: passou.
- T6: `e2e/convite.spec.ts`, `frontend/README.md`. Prova: em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`: exit 0, cobertura 98,55% de linhas, e2e 3/3 `@critical`; `./backend/mvnw -f backend/pom.xml verify`: exit 0.

Regressão: sem falha; nada que já falhava foi observado. Registros em `$TEMP/fe.log` e `$TEMP/be-full.log` (fora do repositório).

Desvios:
- A conferência dos handlers (CA12) ficou em `src/mocks/invite.msw.test.ts`, e não dentro de `msw.test.ts`; `msw.test.ts` continua passando.
- Sem os símbolos de marca (✓/○) na lista de exigências: a regra `i18next/no-literal-string` os recusa; o estado sai só em texto ("atendida"/"pendente"), o que já atende ao CA3.
- O vencimento usa `formatDateTime` (fuso padrão da aplicação, `America/Sao_Paulo`), conforme `lib/format.ts`, e não o fuso do navegador.
- `handlers.ts` reutiliza `isStrongPassword` de `PasswordRules.tsx`.

## 8. Verificação

### Rodada 1 — OK — 809fba9563d3ecf2789ed6634261141f06ae4bf1

| Critério | Status | Evidência |
| --- | --- | --- |
| CA1 | PASSOU | `InvitePage.test.tsx` (nome/e-mail somente leitura, validade, sem nome) e e2e |
| CA2 | PASSOU | `AuthRoutesIT` no `verify` (campos no válido, `expiresAt` = +7 dias, ausência no 410) |
| CA3 | PASSOU | `PasswordRules.test.tsx` (marcação por tecla com texto, Mostrar/Ocultar) |
| CA4 | PASSOU | `InvitePage.test.tsx` CA4 e e2e (fraca desabilita, válida e igual habilita) |
| CA5 | PASSOU | `InvitePage.test.tsx` (PASSWORD_LEAKED com foco e preenchimento; PASSWORD_WEAK) |
| CA6 | PASSOU | `it.each` de EXPIRED/USED/CANCELED e teste de DEACTIVATED |
| CA7 | PASSOU | teste CA7 (410 no aceite) |
| CA8 | PASSOU | teste CA8 (senha atual, 401 limpa e foca, 429) |
| CA9 | PASSOU | teste CA8/CA9 e e2e (chega a `/`) |
| CA10 | PASSOU | testes CA10 (500, rede, envio duplo) |
| CA11 | PASSOU | `it.each` axe a 360 px nos dois temas (conta nova, existente, inválido, erro) |
| CA12 | PASSOU | `invite.msw.test.ts` e `msw.test.ts` verdes (desvio de arquivo sem efeito no critério) |
| CA13 | PASSOU | `e2e/convite.spec.ts` @critical 3/3 |
| CA14 | PASSOU | teste CA14 com espiões |

Comandos:
- `cd frontend && npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`: exit 0; linhas 98,55%; e2e 3/3.
- `./backend/mvnw -f backend/pom.xml verify`: BUILD SUCCESS, 88 testes, 0 falhas (JaCoCo passou).

Achados obrigatórios: nenhum.

Sugestões:
1. T2 cita `PasswordFields.test.tsx`, que não existe; `PasswordFields` é coberto por `PasswordRules.test.tsx` e `InvitePage.test.tsx`.
2. Plano e CA1 dizem fuso do navegador; a tela usa `formatDateTime` (America/Sao_Paulo), já registrado em Desvios. Consistência a alinhar na especificação.

Resultado: OK

## 9. Entrega

- Tela pública `/convite/:code` para criar a senha (conta nova) ou informar a senha atual (conta existente), com lista de exigências, Mostrar/Ocultar, estados de carregando, erro e convite inválido (vencido, usado, cancelado, desativado).
- `GET /auth/invitations/{code}` passa a devolver nome, e-mail e validade (D1); contrato em `docs/openapi.yaml` e handlers MSW atualizados.
- Verificação: rodada 1 OK; `verify` do back-end (88 testes, JaCoCo ok) e lint, tipos, build, cobertura (98,55%) e e2e `@critical` 3/3 no front-end; axe nos dois temas a 360 px.
- Decisões: D1 a D7 (após o aceite vai para `/`; "pedir outro convite" é só texto). Limitação: canvas não acessível (D5); fuso da validade é America/Sao_Paulo.
