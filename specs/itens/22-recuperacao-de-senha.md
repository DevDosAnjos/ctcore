# 22 — RF-ACE-03 · Recuperação de senha e nova senha

Origem: issue #22 (Sprint 02); `docs/especificacao/02-contas-e-acesso.md` (RF-ACE-03, RN-ACE-08, RN-ACE-11, RN-ACE-14). Branch: `item/22-recuperacao-de-senha`.

## 1. Objetivo

Entregar as duas telas públicas da recuperação de senha: "Recuperar senha" (`/esqueci-senha`, pede o e-mail e mostra sempre a mesma mensagem) e "Nova senha" (`/redefinir-senha/:code`, o caminho que o servidor já monta em `EmailAccountMailer.passwordReset`), com as mesmas exigências de senha do convite. Depois de criar a senha, a pessoa já está logada e vai para `/`. O servidor (`POST /auth/password-reset` e `POST /auth/password-reset/{code}`, `AuthService.requestReset/reset`) e o contrato já existem desde a #18; faltam as telas, os handlers MSW e a cobertura de teste que o card exige (isolamento e as regras RN-ACE-11 e RN-ACE-14 nos casos ainda sem teste).

Fora do item: troca de senha com sessão (`POST /auth/password`, RN-ACE-20), escolha de organização e área (#23), duas etapas (#26), mudar o servidor de recuperação (já cumpre RN-ACE-11 e RN-ACE-14).

## 2. Requisitos

- R1 (RF-ACE-03): a rota pública `/esqueci-senha` (hoje provisória em `App.tsx`) mostra o campo "E-mail" e o botão "Enviar link", com o desafio invisível quando houver chave, e um link de volta para "Entrar".
- R2 (RN-ACE-11): depois do envio, para qualquer e-mail (com conta, sem conta, desativada ou com convite pendente), a tela mostra a mesma mensagem "Se esse e-mail tiver uma conta, você vai receber um link em instantes.", vinda da resposta do servidor, e nunca diz se o e-mail existe.
- R3 (RN-ACE-14): o servidor só envia o link a conta ativa; conta desativada não recebe nada; conta com convite pendente recebe o convite de novo e não o link; um novo pedido cancela o link anterior; o link vale 1 hora e é de uso único (regras já implementadas; cada uma precisa de teste que a prove).
- R4 (RF-ACE-03, RN-ACE-08): a rota pública `/redefinir-senha/:code` mostra "Senha" e "Confirmar senha" com as exigências marcadas enquanto digita e "Mostrar"/"Ocultar" (mesmo componente `PasswordFields` da #21); "Criar senha e entrar" só fica habilitado com senha válida e igual nos dois campos.
- R5 (RN-ACE-08): senha recusada por vazamento mostra "Essa senha é muito conhecida. Escolha outra." junto ao campo; senha fraca mostra o erro com as exigências; nos dois casos o link não é consumido e a tela segue preenchida.
- R6 (RN-ACE-14): link vencido, já usado, substituído ou desconhecido (410 `RESET_LINK_INVALID`) mostra a explicação e orienta pedir outro link, com acesso a `/esqueci-senha`; sem formulário (D2).
- R7 (RF-ACE-03): senha aceita (200) guarda a sessão iniciada pelo servidor e a pessoa vai para `/`, sem passar pelo login; o código não fica na URL seguinte (D3).
- R8 (contrato): os handlers MSW de `POST /auth/password-reset` e `POST /auth/password-reset/{code}` batem com `docs/openapi.yaml`; sem mudança de contrato (D4).
- R9 (isolamento): a recuperação de uma conta não altera senha, sessões nem links de outra conta, nem o que outra organização enxerga (regra transversal "toda rota nova tem teste de isolamento"; as rotas já existem sem esse teste).
- R10 (tela): as duas telas têm os estados aplicáveis (D5): carregando (envio), erro de rede ou 5xx com "Tentar de novo", e o link inválido (R6); funcionam a partir de 360 px, passam no axe nos temas claro e escuro, rótulo em todo campo, foco visível, `autocomplete="username"` no e-mail e `new-password` nas senhas. Todo texto em `pt-BR.json`.
- R11 (segurança): o código do link e a senha nunca vão para log nem para armazenamento do navegador; o e-mail digitado não é guardado.
- R12 (limite): 429 no pedido (muitas tentativas, RN-ACE-10) mostra o aviso de espera, sem revelar nada sobre o e-mail (D6).

## 3. Critérios de aceite

- CA1 (R1): dado `/esqueci-senha`, quando abre, então há "E-mail" (`autocomplete="username"`), "Enviar link" e o link "Entrar" para `/entrar`; e o link "Esqueci minha senha" do login leva a essa tela.
- CA2 (R1): dado e-mail vazio, quando envia, então aparece "Informe o seu e-mail." junto ao campo, sem chamar a API, e o foco vai ao campo.
- CA3 (R2): dado qualquer e-mail, quando envia e o servidor responde 200, então a tela troca o formulário pela mensagem exata "Se esse e-mail tiver uma conta, você vai receber um link em instantes." e o link "Entrar"; o texto é o mesmo para os três casos testados (com conta, sem conta, desativada). `AuthRoutesIT` já confere a igualdade no servidor.
- CA4 (R3): dado conta ativa, desativada, inexistente e com convite pendente, quando pede o link, então só a ativa gera e-mail de `redefinir-senha`; a desativada e a inexistente não geram nada; a de convite pendente recebe `/convite/` e não `redefinir-senha` (`AuthRoutesIT`, casos existentes mais a asserção do convite anterior cancelado).
- CA5 (R3): dado dois pedidos seguidos, então o primeiro link responde 410 `RESET_LINK_INVALID`; dado o segundo link usado uma vez, então o segundo uso responde 410; dado o link usado após 1 hora e 1 segundo, então 410; dado 59 minutos, então 200 (`AuthRoutesIT`, existente; acrescentar o limite exato de 1 hora).
- CA6 (R4): dado `/redefinir-senha/:code`, quando digita, então cada exigência troca para "atendida" ou "pendente" a cada tecla, a confirmação divergente mostra "As senhas não são iguais.", e o botão só habilita com senha válida e igual.
- CA7 (R5): dado `POST .../password-reset/{code}` respondendo 400 `PASSWORD_LEAKED`, então aparece "Essa senha é muito conhecida. Escolha outra." junto ao campo, o foco volta nele e os campos continuam preenchidos; dado `PASSWORD_WEAK`, aparece o erro com as exigências; o mesmo link serve para nova tentativa (servidor: `AuthRoutesIT` já prova que senha fraca não consome).
- CA8 (R6): dado 410 `RESET_LINK_INVALID` ao enviar, então a tela mostra "Este link não é válido ou já foi usado" com a orientação de pedir outro e o link "Pedir novo link" para `/esqueci-senha`, sem formulário; dado `/redefinir-senha/vencido` no mock, o mesmo ao primeiro envio (D2).
- CA9 (R7): dado 200, então o `SessionProvider` fica `ready`, a rota vira `/` sem passar por `/entrar`, e a URL seguinte não contém o código.
- CA10 (R10): dado 500 ou rede fora, no pedido ou na nova senha, então aparece o erro com "Tentar de novo" que repete o envio com os dados preenchidos; enquanto envia, o botão fica desabilitado e a tela mostra o carregando, sem envio duplo.
- CA11 (R10): dado cada tela em 360 px, nos temas claro e escuro, em cada estado (formulário, mensagem enviada, erro, link inválido), então o axe não aponta violação e não há rolagem horizontal.
- CA12 (R8): dado o OpenAPI, então os handlers MSW dos dois endpoints existem com status e corpo do contrato (200 com `message`; 200 com `SessionView`; 400 `PASSWORD_WEAK`/`PASSWORD_LEAKED`; 410 `RESET_LINK_INVALID`) e `msw.test.ts` ou teste próprio os confere.
- CA13 (R9): dado duas organizações com uma conta cada, quando a conta A pede e usa o link, então a senha e as sessões da conta B continuam as mesmas, o log de auditoria `account.password_reset` fica só na organização de A, e o link de A usado no lugar do de B responde 410 (`AuthIsolationIT`, teste novo).
- CA14 (R9): dado qualquer perfil logado (Coordenador, Administrador, Professor, Aluno, Responsável), quando chama as duas rotas, então o comportamento é o mesmo de quem está sem sessão (rotas públicas, sem dado de perfil): `AuthIsolationIT` percorre os perfis e confere que nenhuma resposta traz dado da conta alheia.
- CA15 (R11): dado as telas montadas, então nenhuma chamada a `localStorage`/`sessionStorage` nem `console` recebe o código, a senha ou o e-mail digitado (espiões).
- CA16 (R12): dado 429 no pedido, então a tela mostra "Muitas tentativas. Espere alguns minutos e tente de novo." e mantém o formulário.
- CA17 (R2, R4, R7): fluxo `@critical` "Recuperação de senha" no Playwright: login → "Esqueci minha senha" → envia e-mail → vê a mensagem; abre `/redefinir-senha/valido`, a senha fraca deixa o botão desabilitado, a válida e igual habilita, entra e chega a `/`; `/redefinir-senha/vencido` mostra o link inválido.

## 4. Plano técnico

- **Dados.** Não muda (`password_reset` já existe em `V6__identity.sql`).
- **API/OpenAPI.** Nenhuma rota nova nem campo novo: `POST /auth/password-reset` e `POST /auth/password-reset/{code}` já estão em `docs/openapi.yaml` e em `AuthController` (públicas em `SecurityConfig`). Só conferir no OpenAPI que o 429 do pedido e o 429 da nova senha estão documentados (se faltar, acrescentar; é aditivo). Por RN-ACE-11 não há rota que diga se o link é válido antes do envio (D2).
- **Back-end.** Sem mudança de código de produção. Testes (existentes: `AuthRoutesIT.passwordReset`, `pendingInviteGetsInviteAgain`): acrescentar em `AuthRoutesIT` o limite exato de 1 hora e a asserção de que a desativada não gera e-mail (CA4/CA5); novo teste em `AuthIsolationIT` (CA13, CA14), usando os auxiliares de `AuthBase`.
- **Front-end.** A criar em `src/features/auth/`: `ForgotPasswordPage.tsx` (e-mail, `Turnstile`, mensagem única, estados) e `ResetPasswordPage.tsx` (`PasswordFields` da #21, estados, link inválido), com seus `.test.tsx`. Alterar: `src/App.tsx` (trocar o `ForgotPassword` provisório pela página nova e acrescentar a rota pública `/redefinir-senha/:code`), `src/i18n/pt-BR.json` (`forgot.*`, `reset.*`; reutilizar `auth.*`, `password.*`), `src/mocks/handlers.ts` (dois handlers; códigos de teste `valido`, `vencido`, `fraca`, `vazada`), `frontend/README.md` (códigos do mock). Reutiliza `lib/api.ts`, `useSession().setSession`, `ErrorState`/`LoadingState`, `Button`, `Turnstile`, `SupportContact`, `ThemeToggle`, o cartão da `LoginPage`/`InvitePage`, `PasswordFields`/`isStrongPassword` e a leitura de erro por `ApiError.code`. e2e: `e2e/recuperar-senha.spec.ts` (a criar), `@critical`.
- **Interface.** Layout público igual ao do login (cabeçalho, cartão centralizado). Telas do canvas (`RecuperarSenha.dc.html`, `NovaSenha.dc.html`) não são acessíveis ao agente (D7). Quatro estados: carregando e erro valem; "vazio" e "sem permissão" não se aplicam a tela pública de formulário/link (D5); o link inválido ocupa o lugar do formulário. Depois do pedido, o formulário dá lugar à mensagem (sem campo editável) com foco no texto para leitor de tela (`role="status"`).
- **Segurança.** O servidor decide validade, uso único, política de senha, lista de vazamento, desafio e limite (#18); a tela só reflete. A mensagem do pedido vem do servidor e é idêntica para qualquer e-mail (RN-ACE-11); a tela não tem ramo por existência de conta. Sem `next` nem `Referer` para terceiros; código só no caminho da URL. Auditoria: a troca já grava `account.password_reset` e `account.sessions_ended` sem senha nem código; o pedido do link não altera dado de negócio e não grava (já assim no servidor, D8). E-mail de "senha alterada" já é enviado pelo servidor. Sessões antigas da conta são encerradas pelo servidor.
- **Riscos.** Link do e-mail segue `redefinir-senha/` (conferido em `EmailAccountMailer`): o texto do e-mail e o caminho da rota precisam continuar iguais, o e2e e o teste da página cobrem. A conferência visual com o canvas fica com o dono (D7). Dependência nova: nenhuma. Notas de versão: não existe arquivo ainda (RF-AJU-04); nada a atualizar.

## 5. Tarefas

- [x] T1: Back-end, testes: limite exato de 1 hora e desativada sem e-mail em `AuthRoutesIT`; teste de isolamento e de perfis em `AuthIsolationIT`; conferir 429 no OpenAPI das duas rotas — cobre CA4, CA5, CA13, CA14 — prova: `./backend/mvnw -f backend/pom.xml verify -Dtest=OpenApiContractTest,ContractRoutesTest -Dit.test='AuthRoutesIT,AuthIsolationIT'`.
- [x] T2: `ForgotPasswordPage` com e-mail, desafio, mensagem única, 429, erro e carregando; trocar o provisório em `App.tsx`; textos `forgot.*` — cobre CA1, CA2, CA3, CA10 (pedido), CA15, CA16 — prova: `ForgotPasswordPage.test.tsx`.
- [x] T3: `ResetPasswordPage` com `PasswordFields`, erros por código, link inválido com "Pedir novo link", sessão e redirecionamento; rota `/redefinir-senha/:code`; textos `reset.*` — cobre CA6, CA7, CA8, CA9, CA10 (nova senha), CA15 — prova: `ResetPasswordPage.test.tsx`.
- [x] T4: Handlers MSW dos dois endpoints (códigos `valido`, `vencido`, `fraca`, `vazada`) e conferência com o OpenAPI — cobre CA12 — prova: `npm test` (`src/mocks/reset.msw.test.ts`, no padrão de `invite.msw.test.ts`).
- [x] T5: Axe nos dois temas a 360 px em cada estado das duas telas — cobre CA11 — prova: `it.each` com `src/test/axe.ts` e `device.ts` dentro dos dois `.test.tsx`.
- [x] T6: e2e `@critical` `e2e/recuperar-senha.spec.ts`, `frontend/README.md` e regressão — cobre CA17 — prova: em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`; e `./backend/mvnw -f backend/pom.xml verify`.

## 6. Decisões

- D1 (muda o comportamento do produto): depois do pedido a tela mostra só a mensagem do servidor, sem confirmar nem negar a conta, também para conta desativada e para convite pendente (que recebe o convite de novo, texto igual). Motivo: RN-ACE-11 e o servidor já se comportam assim. Alternativa: mensagem específica para convite pendente ("reenviamos seu convite"), descartada por revelar que o e-mail existe.
- D2 (muda o comportamento do produto): a validade do link só é descoberta ao enviar a nova senha (410), porque o contrato não tem consulta de link e RN-ACE-11/RN-ACE-14 não pedem uma. Motivo: evita rota pública nova fora do card; o custo é a pessoa digitar a senha antes de saber que o link venceu. Alternativa: `GET /auth/password-reset/{code}` que valida antes, descartada por criar rota fora do card. Se o dono preferir validar antes, vira item novo.
- D3: após a nova senha a pessoa vai para `/`, sem `next` (como D3 da #21); a escolha de organização e área é da #23.
- D4: o contrato não muda; o servidor da #18 já cumpre o card. O item é de telas e de testes que faltavam. O 429 só entra no OpenAPI se faltar.
- D5: telas públicas de formulário e de link: "vazio" e "sem permissão" não se aplicam (regra do card cumprida com carregando, erro e link inválido; mesma leitura da D4 da #21).
- D6: o 429 do pedido mostra o aviso de espera mesmo sem a conta existir, porque o limite é por e-mail/IP (RN-ACE-10) e vale para qualquer e-mail, então não revela nada.
- D7: o canvas (`Login.dc.html`, `RecuperarSenha.dc.html`, `NovaSenha.dc.html`) não é acessível ao agente; as telas seguem o texto do card e a base visual do login/convite. Suposição: se o desenho diferir, o dono pede ajuste de layout, sem mudar regra.
- D8: o pedido de link não grava auditoria no servidor hoje (nenhum dado de negócio muda e a conta pode nem existir); a troca grava. Suposição: basta; se o dono quiser trilha dos pedidos, vira item de auditoria de acesso.
- D9: a mensagem de "link inválido" cobre vencido, usado, substituído e desconhecido com um texto só, porque o servidor devolve um único código (`RESET_LINK_INVALID`) e dizer o motivo revelaria estado da conta.

## 7. Construção

- T1: `AuthRoutesIT` (novo `resetLinkBoundary`: desativada sem e-mail, 1 h exata dá 410, 1 h menos 1 s dá 200) e `AuthIsolationIT` (novo `passwordResetStaysWithTheAccount`: isolamento entre A e B, auditoria só de A, cinco perfis logados). Prova: `verify -Dtest=OpenApiContractTest,ContractRoutesTest -Dit.test='AuthRoutesIT,AuthIsolationIT'`: contrato 7 ok, `AuthRoutesIT` 11 ok, `AuthIsolationIT` 4 ok.
- T2: `ForgotPasswordPage.tsx` + `.test.tsx`; `App.tsx` (provisório removido); `pt-BR.json` (`forgot.*`). Prova: `npm test`, ok.
- T3: `ResetPasswordPage.tsx` + `.test.tsx`; rota `/redefinir-senha/:code`; `pt-BR.json` (`reset.*`). Prova: `npm test`, ok.
- T4: `src/mocks/handlers.ts` (dois handlers; `limite@exemplo.com` dá 429 no pedido) e `src/mocks/reset.msw.test.ts`. Prova: `npm test`, ok.
- T5: axe a 360 px nos dois temas, em formulário, erro de campo/aviso, mensagem enviada e link inválido, dentro dos dois `.test.tsx`. Prova: `npm test`, ok.
- T6: `e2e/recuperar-senha.spec.ts`, `frontend/README.md` (códigos do mock). Regressão: `npm run lint && typecheck && build && test:cov && e2e` ok (205 testes, linhas 98,72%; e2e 4 de 4); `./backend/mvnw -f backend/pom.xml verify` ok (90 IT, cobertura exigida atendida).

Desvios:
- Plano pedia conferir/acrescentar o 429 no OpenAPI. Não acrescentei: `AuthService.requestReset` não devolve 429 hoje (só o login conta tentativas), então documentar seria falso. A tela trata 429 (R12, CA16) pelo mock. Se o dono quiser limite no pedido de link (RN-ACE-10), é item de servidor.
- Docker: nenhuma imagem criada nem baixada pela tarefa além das já existentes dos `*IT`.

## Verificação

### Rodada 1 — OK — a0c7e888c34b88f425db6253f93f3dcfb0864692

| Critério | Status | Evidência |
| --- | --- | --- |
| CA1, CA2, CA3, CA16 | PASSOU | `ForgotPasswordPage.test.tsx` (it CA1, CA2, CA3 x3, CA16); código lido |
| CA4, CA5 | PASSOU | `AuthRoutesIT` (`resetLinkBoundary` novo, mais `passwordReset` e `pendingInviteGetsInviteAgain` existentes); `verify` 90 IT ok |
| CA6, CA7, CA8, CA9 | PASSOU | `ResetPasswordPage.test.tsx` |
| CA10, CA15 | PASSOU | testes CA10 e CA15 nas duas páginas |
| CA11 | PASSOU | `it.each` de axe nos dois `.test.tsx` (passaram em `test:cov`) |
| CA12 | PASSOU | `reset.msw.test.ts` |
| CA13, CA14 | PASSOU | `AuthIsolationIT.passwordResetStaysWithTheAccount` |
| CA17 | PASSOU | e2e `recuperar-senha.spec.ts` ok (4 de 4) |

Comandos: `npm run lint && typecheck && test:cov` ok (linhas 98,72%); `npm run build && npm run e2e` 4 de 4; `./backend/mvnw -f backend/pom.xml verify` BUILD SUCCESS, 90 IT, 0 falhas. Não conferidos: `references/checklist.md` ausente na máquina (conferência feita pelo diff e pelo AGENTS.md).

Achados obrigatórios: nenhum.
Sugestões: (1) `Turnstile` com `resetKey={0}` fixo: o token de desafio não é renovado após erro e nova tentativa; revisar se o servidor o torna de uso único. (2) Desvio do 429 documentado na Construção (OpenAPI sem 429 nas rotas porque o servidor não o devolve) é coerente; RN-ACE-10 para o pedido de link fica sem limite no servidor, a decidir pelo dono.

RESULTADO: OK

## Entrega

Telas públicas "Recuperar senha" (`/esqueci-senha`) e "Nova senha" (`/redefinir-senha/:code`), com mensagem única do servidor (RN-ACE-11), exigências de senha do convite, link inválido, erro, carregando e aviso de 429. Depois da nova senha a pessoa entra e vai para `/`.

- Verificado: rodada 1 OK. Front: lint, typecheck, build, `test:cov` (205 testes, linhas 98,72%), e2e 4 de 4. Back: `verify` com 90 IT, 0 falhas. Testes novos de limite de 1 hora e de isolamento/perfis (`AuthRoutesIT`, `AuthIsolationIT`).
- Sem mudança de contrato nem de código de produção do servidor.
- Limitações: o servidor não limita o pedido de link (RN-ACE-10), então o 429 só existe no mock; o token do desafio não é renovado após erro (sugestão da verificação); conferência visual com o canvas fica com o dono.
