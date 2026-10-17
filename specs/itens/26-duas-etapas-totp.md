# 26 — Duas etapas (TOTP), códigos de reserva e senha nas ações críticas

Origem: issue #26 (Sprint 03); `docs/especificacao/02-contas-e-acesso.md` (RN-ACE-17, RN-ACE-18, RN-ACE-21; RF-ACE-04 só nas telas de ativar e de códigos). Branch: `item/26-duas-etapas-totp`.

## 1. Objetivo

Entregar a verificação em duas etapas por app autenticador (TOTP) com 10 códigos de reserva, obrigatória para o Coordenador (e para a equipe da plataforma quando o papel existir, D2), e a confirmação por senha (mais código, se ligado) que as ações críticas pedem. O item é feito em duas partes: **A** servidor (regras, banco, rotas, OpenAPI) e **B** telas (segundo passo do login, ativar duas etapas, códigos de reserva, janela de confirmação, configuração obrigatória no primeiro acesso).

Fora do item: a tela "Minha conta" que hospeda ligar, desligar e ver códigos (#36, que reutiliza os componentes da parte B); as ações críticas em si (transferir coordenação #33, desativar unidade #34, exportação completa, cancelar assinatura, Mercado Pago, anonimizar aluno), que só passam a usar a confirmação; o papel da equipe da plataforma (Sprint 15); o chamado de suporte que desativa as duas etapas (Sprint 15); qualquer código por e-mail ou SMS (proibido pela RN-ACE-18).

## 2. Requisitos

Parte A (servidor)

- R1 (RN-ACE-17): uma conta liga as duas etapas por app autenticador (TOTP: RFC 6238, SHA-1, 6 dígitos, 30 s, tolerância de uma janela para cada lado). O segredo é gerado no servidor, guardado criptografado e só vira ativo depois que a pessoa prova com um código válido.
- R2 (RN-ACE-17): conta com vínculo ativo de Coordenador é obrigada a ter as duas etapas; sem elas, a sessão só alcança a configuração das duas etapas, a leitura da sessão e a saída; todo o resto responde 403 `TWO_FACTOR_SETUP_REQUIRED` (D2). Para Administrador, Professor, Aluno e Responsável é opcional.
- R3 (RN-ACE-17): quem é obrigado não consegue desligar as duas etapas; os demais desligam com senha e código (D5).
- R4 (RN-ACE-18): ao ativar, a pessoa recebe 10 códigos de reserva de uso único, mostrados uma única vez; no banco só o hash. Pedir códigos novos (senha e código) invalida os anteriores e devolve outros 10.
- R5 (RN-ACE-17): no login, conta com duas etapas ligadas e senha correta não ganha sessão; recebe um bilhete de uso único (5 minutos) e só entra ao enviar um código TOTP ou um código de reserva válido (D3). Código de reserva é consumido ao usar; código TOTP não vale duas vezes (D4).
- R6 (RN-ACE-10, por D3): erros de código no segundo passo contam para a trava de 15 minutos do e-mail e para o limite por IP; 5 erros no mesmo bilhete o invalidam; a resposta de erro é a mesma para código errado, usado ou vencido.
- R7 (RN-ACE-18): não existe envio de código por e-mail nem por SMS, nem rota para isso; quem perdeu o celular e os códigos fica com a recusa e a orientação de abrir chamado (o desbloqueio é do suporte, fora do item).
- R8 (RN-ACE-21): a confirmação de ação crítica (`POST /auth/reauth`) recebe a ação (uma das seis: `TRANSFER_COORDINATION`, `CANCEL_SUBSCRIPTION`, `FULL_EXPORT`, `DISCONNECT_MERCADO_PAGO`, `ANONYMIZE_STUDENT`, `DEACTIVATE_UNIT`), a senha e, se as duas etapas estão ligadas, o código; devolve um token de uso único, válido por 5 minutos, preso à sessão e à ação (D6). Senha ou código errados contam para a trava (R6).
- R9 (RN-ACE-21): um guarda do servidor, `CriticalActionGuard`, é o que as rotas críticas das próximas sprints chamam: sem token, com token de outra ação, de outra sessão, vencido ou já usado, responde 403 `REAUTH_REQUIRED`; com token válido, consome-o.
- R10 (transversal): toda ação que altera dado grava auditoria (ligar, desligar, códigos novos, uso de código de reserva, confirmação aceita e recusada), sem segredo, código, senha ou token; toda rota nova tem teste de isolamento entre contas e entre perfis, e consta no OpenAPI.
- R11 (transversal): o segredo TOTP é criptografado com chave de ambiente (D7); sem chave configurada fora do perfil local, o back-end não sobe.

Parte B (telas)

- R12 (RF-ACE-01 / canvas `LoginDuasEtapas`): quando o login responde "duas etapas exigidas", o login mostra o segundo passo (campo do código de 6 dígitos, alternância "Usar um código de reserva", "Voltar", orientação de chamado para quem perdeu tudo) e conclui o login com o resultado do servidor.
- R13 (canvas `DuasEtapas`): janela "Ativar duas etapas" com QR code e chave para digitar, campo de código e confirmação; termina mostrando os códigos de reserva.
- R14 (canvas `CodigosReserva`): janela dos 10 códigos de reserva (copiar, baixar, aviso de que só aparecem uma vez); a mesma janela serve ao pedido de códigos novos (pede senha e código antes).
- R15 (canvas `TransferirCoordenacao` e `PacoteCompleto`, parte da confirmação): janela reutilizável de confirmação de ação crítica (`ReauthDialog`): pede a senha e, se as duas etapas estão ligadas, o código; devolve o token para a tela que a chamou.
- R16 (R2 na tela): Coordenador sem duas etapas, depois do login, é levado a uma página de configuração obrigatória, da qual só sai ativando ou saindo da conta.
- R17 (transversal): telas com os quatro estados, a partir de 360 px, axe nos dois temas, textos em `pt-BR.json`, handlers MSW iguais ao OpenAPI.

## 3. Critérios de aceite

Parte A

- CA1 (R1): dado conta sem duas etapas, quando `POST /auth/two-factor/setup`, então responde segredo (base32) e `otpauthUri`, nada fica ativo e `GET /auth/session` continua com `twoFactor.enabled=false`.
- CA2 (R1): dado setup feito, quando `POST /auth/two-factor/enable` com código TOTP válido, então fica ativo e responde 10 códigos de reserva; com código inválido, responde 400/401 `INVALID_TWO_FACTOR_CODE` e continua desligado.
- CA3 (R1): o TOTP passa nos vetores de teste da RFC 6238 (SHA-1) e aceita o passo anterior e o seguinte, mas não dois passos de diferença (teste unitário de `TotpService`).
- CA4 (R1, R11): o segredo no banco não contém o segredo em claro; trocar a chave impede ler o segredo antigo; sem chave fora do perfil local o contexto não sobe.
- CA5 (R2): dado Coordenador sem duas etapas e com sessão, quando chama qualquer rota fora de `/auth/session`, `/auth/logout` e `/auth/two-factor/**`, então 403 `TWO_FACTOR_SETUP_REQUIRED`; depois de ativar, a mesma rota responde normalmente.
- CA6 (R2): dado Administrador (ou Professor, Aluno, Responsável) sem duas etapas, então nenhuma rota é bloqueada e `GET /auth/session` traz `twoFactor.required=false`.
- CA7 (R3): dado Coordenador com duas etapas, quando `DELETE /auth/two-factor` com senha e código corretos, então 403 `FORBIDDEN` e continua ligado; dado Administrador, a mesma chamada desliga, apaga segredo e códigos e audita; com senha errada, recusa e nada muda.
- CA8 (R4): dado duas etapas ligadas, quando ativa, então são 10 códigos distintos; no banco há 10 hashes e nenhum código em claro; `POST /auth/two-factor/backup-codes` (senha e código) invalida os 10 anteriores e devolve 10 novos; sem senha ou com código errado, recusa e os antigos continuam valendo.
- CA9 (R5): dado conta com duas etapas, quando `POST /auth/login` com senha correta, então 200 `{twoFactorRequired:true, ticket}` sem cookie `SESSION` e sem criar sessão nem atualizar último acesso; com senha errada, a resposta é a de sempre (R1 da #18, 401).
- CA10 (R5): dado o bilhete, quando `POST /auth/login/two-factor` com TOTP válido, então 200 com a sessão e o cookie; o bilhete não serve de novo; o e-mail "Novo acesso" e o último acesso seguem a regra do login comum.
- CA11 (R5): dado um código de reserva válido, quando usado no segundo passo, então entra e o código fica gasto (segunda tentativa com o mesmo código: recusa); os outros nove continuam valendo.
- CA12 (R5): dado um TOTP já aceito num login, quando é enviado de novo no mesmo passo de 30 s por outro bilhete, então recusa.
- CA13 (R5): dado bilhete vencido (mais de 5 minutos), inexistente ou de outra conta, então 401 `TWO_FACTOR_TICKET_INVALID` e nenhuma sessão.
- CA14 (R6): dado 5 códigos errados seguidos no mesmo e-mail, então o e-mail trava por 15 minutos (429 `TOO_MANY_REQUESTS`, inclusive com a senha certa) e o bilhete morre; 20 falhas no mesmo IP em 15 minutos limitam o IP; a mensagem de erro é igual para errado, usado e vencido.
- CA15 (R7): dado o conjunto de rotas e o envio de e-mail, então não há rota nem chamada ao serviço de e-mail que entregue código de duas etapas (teste que reprova se `EmailService` for chamado em ativar, login e códigos novos).
- CA16 (R8): dado sessão, quando `POST /auth/reauth` com ação válida, senha correta (e código, se ligado), então 200 com `reauthToken` e `expiresAt` em até 5 minutos; senha errada, código ausente (com duas etapas) ou ação fora da lista: 400/401 sem token e conta para a trava.
- CA17 (R9): dado token válido da ação X, quando `CriticalActionGuard.consume(principal, X, token)`, então passa uma vez; segunda vez, outra ação, outra sessão, vencido ou ausente: 403 `REAUTH_REQUIRED`.
- CA18 (R10): dado cada rota nova, então: sem sessão 401; conta A não vê nem altera segredo, códigos nem token da conta B; Coordenador, Administrador, Professor, Aluno e Responsável alcançam as rotas só sobre a própria conta; a escrita sem o cabeçalho CSRF é recusada.
- CA19 (R10): dado cada operação que altera dado (ligar, desligar, códigos novos, uso de código de reserva, reauth aceito, reauth recusado), então há linha de auditoria com ação, conta e IP e sem segredo, código, senha ou token (conferido contra o texto salvo).
- CA20 (R10): `ContractRoutesTest` e `OpenApiContractTest` passam com as rotas novas.

Parte B

- CA21 (R12): dado login com conta de duas etapas no mock, quando envia e-mail e senha, então aparece o segundo passo; com código certo entra na área; com código errado mostra a mensagem de erro, mantém o campo e o foco; "Usar um código de reserva" troca o campo; "Voltar" volta ao e-mail e senha; bilhete vencido volta ao primeiro passo com aviso.
- CA22 (R13, R14): dado a janela de ativar, então mostra QR e chave, valida o código, mostra os 10 códigos, permite copiar e baixar, e só fecha depois de uma confirmação de que foram guardados; código inválido mostra o erro sem avançar.
- CA23 (R15): dado `ReauthDialog` com duas etapas desligadas, então pede só a senha; com ligadas, senha e código; senha errada mostra o erro e não devolve token; sucesso devolve o token à tela chamadora.
- CA24 (R16): dado Coordenador sem duas etapas no mock, quando entra, então chega à página de configuração obrigatória; ela bloqueia o resto da aplicação (qualquer endereço volta a ela) e oferece "Sair"; ao ativar, segue para a área.
- CA25 (R17): dado cada tela e janela, então carregando, vazio (onde couber), erro e sem permissão existem; axe sem violações nos temas claro e escuro a 360 px; não há texto literal no código (`npm run lint`).
- CA26 (R12): ponta a ponta `@critical` "Login com duas etapas": entra com e-mail e senha, vê o segundo passo, informa o código e chega à área; roda em todo PR.

## 4. Plano técnico

- **Dados.** Migração `V11__two_factor.sql` (a criar, nunca editar depois):
  - `account_two_factor(account_id uuid pk references account, secret_encrypted text not null, enabled_at timestamptz null, last_used_step bigint, created_at)`; linha com `enabled_at` nulo é o setup pendente (um por conta; novo setup substitui).
  - `account_backup_code(id, account_id, code_hash text, used_at, created_at)`; índice por conta; só hash SHA-256 (`Codes.hash`; os códigos de reserva têm entropia alta, de 10 caracteres aleatórios de alfabeto sem ambiguidade, D8).
  - `login_ticket(id, account_id, token_hash unique, expires_at, used_at, failed_attempts int)`.
  - `reauth_token(id, account_id, session_id references account_session on delete cascade, action text check in (...), token_hash unique, expires_at, used_at)`.
  - `login_attempt.result` ganha `WRONG_CODE` (alterar a restrição `check` por nova migração; a V6 não se edita).
  - RLS forçada nas quatro tabelas pelo escopo `app.account_id` (mesmo padrão da V6/`AccountTransactions.forAccount`). Recuperação: migração só acrescenta; desfazer = nova migração que remove as tabelas (nenhum dado de outra tabela depende delas). Sem seed: segredo é dado do usuário; as contas de `03-accounts.sql` continuam sem duas etapas, e o Coordenador de teste cai na configuração obrigatória.
- **API** (`docs/openapi.yaml` primeiro, mesmo PR; tags de segurança `sessionCookie` + `csrfHeader`):
  - `POST /auth/login` passa a poder responder 200 `TwoFactorRequired {twoFactorRequired:true, ticket}` (`oneOf` com `SessionView`); o front que hoje consome o login (LoginPage) é o único consumidor e muda na parte B.
  - `POST /auth/login/two-factor {ticket, code}` (pública; desafio invisível não se aplica ao segundo passo, o limite é o do bilhete e da trava); `code` é TOTP de 6 dígitos ou código de reserva.
  - `POST /auth/two-factor/setup` → `{secret, otpauthUri}`; `POST /auth/two-factor/enable {code}` → `{backupCodes[10]}`; `DELETE /auth/two-factor {password, code}` → 204; `POST /auth/two-factor/backup-codes {password, code}` → `{backupCodes[10]}`.
  - `POST /auth/reauth {action, password, code?}` → `{reauthToken, expiresAt}`.
  - `SessionView` ganha `twoFactor {enabled, required}`.
  - Erros novos em `ErrorCode`, com texto em `messages.properties`: `INVALID_TWO_FACTOR_CODE` (401), `TWO_FACTOR_TICKET_INVALID` (401), `TWO_FACTOR_SETUP_REQUIRED` (403), `REAUTH_REQUIRED` (403); mapeamento em `ApiExceptionHandler`; enum de erros do OpenAPI atualizado. Mensagens não revelam qual parte falhou.
- **Back-end** (`backend/src/main/java/br/com/ctcore/identity/`, todos a criar salvo indicação):
  - `Totp` (RFC 6238 com `javax.crypto.Mac`, sem dependência nova; compara em tempo constante), `Base32`, `SecretCipher` (AES-256-GCM com chave `TWO_FACTOR_ENCRYPTION_KEY`, base64 de 32 bytes; padrão fixo só no perfil `local` e nos testes; `application.yml` e `infra/.env.example` ganham o nome).
  - `TwoFactorService`: setup, enable, disable, regenerate, verify (TOTP com `last_used_step`, ou código de reserva), `requiredFor(accountId)` = tem vínculo ativo `COORDINATOR` (ponto único onde a equipe da plataforma entra na Sprint 15).
  - `AuthService` (existente): `login` devolve o resultado em dois formatos (sessão ou bilhete); novo `loginSecondFactor`; usa `LoginAttempts` (existente) com `WRONG_CODE`.
  - `ReauthService` + `CriticalActionGuard` + `CriticalAction` (enum das seis ações).
  - `TwoFactorGateFilter`: após `SessionAuthenticationFilter`, bloqueia com 403 `TWO_FACTOR_SETUP_REQUIRED` a sessão que exige e não tem; registra em `config/SecurityConfig.java` (existente), que também libera `POST /auth/login/two-factor` como pública.
  - `AuthController` (existente) ou novo `TwoFactorController`; `SessionService.view` (existente) completa `twoFactor`; `AccountAudit` (existente) grava as ações (`account.two_factor_enabled`, `_disabled`, `backup_codes_regenerated`, `backup_code_used`, `reauth_confirmed`, `reauth_failed`).
  - Testes: `TotpTest` (RFC, janela), `SecretCipherTest`, `TwoFactorServiceIT`, `TwoFactorLoginIT`, `TwoFactorGateIT`, `ReauthIT`, `TwoFactorIsolationIT` (base `AuthBase`/`AuthIsolationIT` existentes), `TwoFactorAuditIT`. Cobertura: o JaCoCo do pacote `identity` segue 80%; a regra de confirmação crítica não está nos pacotes de 100%, mas todo ramo de R8/R9 tem teste.
- **Interface** (`frontend/src/features/auth/`, a criar salvo indicação): `LoginPage.tsx` (existente) ganha o segundo passo (`TwoFactorStep.tsx`); `TwoFactorSetupDialog.tsx` (QR com a biblioteca `qrcode` em versão exata fixada, auditada por `npm run audit`; alternativa descartada: só a chave, pior de usar; D9), `BackupCodesDialog.tsx`, `ReauthDialog.tsx` (em `features/auth` ou `components`, conforme o uso futuro), `RequireTwoFactor.tsx` + `TwoFactorRequiredPage.tsx` (guarda e página obrigatória, dentro de `RequireSession.tsx` existente); `SessionProvider.tsx` (existente) lê `twoFactor`; `App.tsx`; `pt-BR.json` (`twoFactor.*`, `reauth.*`); `mocks/handlers.ts` e `mocks/*.msw.test.ts` (contas com e sem duas etapas, Coordenador obrigado); `e2e/duas-etapas.spec.ts` `@critical`. Reuso: `PasswordFields`, `SupportContact`, componentes de estado de `components/states`, padrões de janela do item #8.
- **Segurança.** Senha e código só no corpo (nunca na URL nem em log); respostas de erro uniformes (R6); comparação em tempo constante; bilhete, token de confirmação e códigos de reserva só como hash; segredo criptografado, mostrado uma vez no setup; CSRF nas rotas autenticadas; limite de tentativas já cobre o segundo passo e a confirmação; auditoria sem segredos. LGPD: dado novo é só o segredo e os hashes, necessários ao contrato de segurança da conta; apagados ao desligar.
- **Riscos.** (1) Bloqueio do Coordenador de teste/seed pelo guarda obrigatório: percebido nos ITs e no seed; o `AuthBase` precisa de helper "conta com duas etapas". (2) O login muda de formato de resposta: percebido pelo teste de contrato e pelos testes do front. (3) Fuso/relógio: `Totp` usa o `Clock` injetado. (4) Perda de chave de criptografia invalida todos os segredos: documentado no README do back-end.

## 5. Tarefas

Parte A — servidor

- [x] T1: Migração `V11__two_factor.sql` (quatro tabelas, RLS, `WRONG_CODE`) e chave de criptografia na configuração — não cobre critério: base dos demais — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AccountIT` e subida do contexto.
- [x] T2: `Base32`, `Totp` e `SecretCipher` — cobre CA3, CA4 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='TotpTest,SecretCipherTest'`.
- [x] T3: `TwoFactorService` (setup, enable, disable, códigos de reserva) e erros novos — cobre CA1, CA2, CA7, CA8, CA15 — prova: `TwoFactorServiceIT`.
- [x] T4: Login em dois passos, bilhete, trava e anti-reuso — cobre CA9 a CA14 — prova: `TwoFactorLoginIT`.
- [x] T5: `TwoFactorGateFilter`, `requiredFor` e `SessionView.twoFactor` — cobre CA5, CA6 — prova: `TwoFactorGateIT`.
- [x] T6: `ReauthService`, `CriticalActionGuard`, `POST /auth/reauth` — cobre CA16, CA17 — prova: `ReauthIT`.
- [x] T7: Rotas e controlador, `docs/openapi.yaml`, auditoria e testes de isolamento e de perfil — cobre CA18, CA19, CA20 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `TwoFactorIsolationIT,TwoFactorAuditIT`.

Parte B — telas

- [x] T8: Textos `twoFactor.*`/`reauth.*`, cliente de API, handlers MSW e `qrcode` — cobre CA25 (textos e mocks) — prova: `npm run lint && npm run typecheck` e testes MSW.
- [x] T9: Segundo passo do login — cobre CA21 — prova: `npx vitest run src/features/auth/LoginPage`.
- [x] T10: `TwoFactorSetupDialog`, `BackupCodesDialog` e `ReauthDialog` — cobre CA22, CA23 — prova: testes dos componentes.
- [x] T11: Guarda e página de configuração obrigatória — cobre CA24 — prova: `RequireTwoFactor.test.tsx`.
- [x] T12: Estados, 360 px e axe nos dois temas de todas as telas — cobre CA25 — prova: os testes dos componentes acima.
- [x] T13: e2e `@critical` "Login com duas etapas" — cobre CA26 — prova: `npm run e2e`.
- [x] T14: Regressão — não cobre critério: fecha a definição de pronto — prova: `./backend/mvnw -f backend/pom.xml verify`; em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: o item é um só (uma entrega, uma PR) em duas partes, A e B, construíveis em sessões separadas; a parte B depende da A concluída. Alternativa: dividir em duas issues, descartada por contrariar o backlog (uma issue = um PR).
- D2 (muda o comportamento): "obrigatória" vale hoje para quem tem vínculo ativo de Coordenador; a equipe da plataforma não tem papel no código (surge na Sprint 15), então `TwoFactorService.requiredFor` é o ponto onde ela entra. Enquanto não configura, a sessão do Coordenador só alcança a configuração, a leitura da sessão e a saída (403 `TWO_FACTOR_SETUP_REQUIRED` nas demais). Alternativa: bloquear só no login, descartada por deixar sessões existentes sem a regra.
- D3 (muda o comportamento): o login com duas etapas é em dois passos: senha certa devolve um bilhete de 5 minutos e 5 erros de código o invalidam; os erros contam na trava da RN-ACE-10 (mesmo e-mail, mesmo IP). Alternativa: enviar o código junto da senha, descartada porque revela se a conta tem duas etapas só depois da senha certa e complica a tela do canvas (`LoginDuasEtapas` é um passo separado).
- D4: código TOTP não é aceito duas vezes no mesmo passo de 30 s (`last_used_step`), para impedir repetição de código visto por terceiros.
- D5 (muda o comportamento): quem é obrigado não desliga as duas etapas; os demais desligam com senha e código. Alternativa: deixar o Coordenador desligar, descartada por contrariar a RN-ACE-17. Quem perdeu celular e códigos depende do suporte (RN-ACE-18, fora do item).
- D6 (muda o comportamento): a confirmação de ação crítica é um token de uso único, 5 minutos, preso à sessão e à ação, obtido em `POST /auth/reauth` e consumido pela rota crítica (cabeçalho `X-Reauth-Token`, definido por quem criar a rota). Motivo: janela genérica reutilizável por seis ações sem repetir a senha no corpo de cada rota. Alternativa: senha e código no corpo de cada rota, descartada por duplicar lógica e espalhar a senha. Nenhuma rota crítica existe ainda: este item entrega o guarda e a prova por serviço; as rotas das #33, #34 etc. o aplicam e têm teste próprio.
- D7: segredo criptografado com AES-256-GCM e chave `TWO_FACTOR_ENCRYPTION_KEY` do ambiente; padrão de desenvolvimento só no perfil `local` e nos testes. Alternativa: guardar em claro, descartada (o canvas e a spec falam em "segredo criptografado").
- D8: códigos de reserva com 10 caracteres de um alfabeto sem ambiguidade, mostrados uma vez, guardados só por hash SHA-256 (entropia alta; Argon2 só se encareceria sem ganho). Alternativa: hash lento por código, descartada.
- D9: o QR é gerado no navegador com a biblioteca `qrcode` (versão exata, auditada); a chave em texto sempre aparece junto. Se o dono não quiser a dependência, mostra-se só a chave e o link `otpauth://`.
- D10: sem notas de versão neste item (o arquivo de Novidades nasce na Sprint 21) e sem recurso por organização (a regra vale para todas as contas; não é liberado aos poucos).
- Suposição: os textos e a disposição das telas seguem a descrição da issue e dos nomes dos canvas; os arquivos `.dc.html` não estão acessíveis ao agente. Se divergirem, ajustam-se só textos e layout, não as regras.
- Suposição: o "equipe da plataforma" do enunciado não tem sessão nesta base; se já houver conta de equipe fora do que foi visto, ela entra em `requiredFor` sem mudar o resto.

## Construção

Parte A (servidor) concluída na primeira sessão; parte B (telas) concluída na segunda.

- T1 — arquivos: `backend/src/main/resources/db/migration/V11__two_factor.sql`, `application.yml`, `application-local.yml`, `backend/src/test/resources/config/application.properties`, `infra/.env.example`, `backend/README.md` — prova: `./backend/mvnw -f backend/pom.xml verify` (todos os `*IT`, com a V11 aplicada e RLS forçada) → 117 testes de integração passam
- T2 — arquivos: `Base32.java`, `Totp.java`, `SecretCipher.java`, `TotpTest.java`, `SecretCipherTest.java` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='TotpTest,SecretCipherTest'` → 5 testes passam (vetores da RFC 6238, janela de ±1 passo, cifra e chave ausente)
- T3 — arquivos: `TwoFactorService.java`, `IdentityException.java`, `ErrorCode.java`, `ApiExceptionHandler.java`, `messages.properties`, `TwoFactorServiceIT.java` — prova: `-Dit.test=TwoFactorServiceIT` → 3 testes passam (CA1, CA2, CA4, CA7, CA8, CA15)
- T4 — arquivos: `AuthService.java` (`login` devolve `LoginOutcome`; novo `loginSecondFactor`), `AuthController.java`, `LoginAttempts.java` (`WRONG_CODE`), `SecurityConfig.java`, `TwoFactorLoginIT.java` — prova: `-Dit.test=TwoFactorLoginIT` → 6 testes passam (CA9 a CA14)
- T5 — arquivos: `TwoFactorGateFilter.java`, `SecurityErrorHandlers.java` (`write` público), `SessionService.java` (`twoFactor` na `SessionView`), `TwoFactorGateIT.java` — prova: `-Dit.test=TwoFactorGateIT` → 4 testes passam (CA5, CA6)
- T6 — arquivos: `CriticalAction.java`, `CriticalActionGuard.java`, `ReauthService.java`, `ReauthIT.java` — prova: `-Dit.test=ReauthIT` → 4 testes passam (CA16, CA17)
- T7 — arquivos: `TwoFactorController.java`, `docs/openapi.yaml`, `TwoFactorIsolationIT.java`, `TwoFactorAuditIT.java`, `AuthBase.java` (helpers de duas etapas), `AuthIsolationIT.java` (Coordenadores com duas etapas ligadas) — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `-Dit.test='TwoFactorIsolationIT,TwoFactorAuditIT'` → 7 e 4 testes passam (CA18, CA19, CA20)

Regressão (parte A): `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS; 95 testes rápidos e 117 de integração, 0 falhas; "All coverage checks have been met" (JaCoCo). O item não registra linha de base; não houve teste que passava e passou a falhar. Front-end não foi tocado nesta parte e não foi rodado.

Desvios:
- O guarda obrigatório (`TwoFactorGateFilter`) libera exatamente `/auth/session`, `/auth/logout` e `/auth/two-factor/**`; a rota `PUT /auth/session/link` e as rotas públicas também recebem 403 `TWO_FACTOR_SETUP_REQUIRED` quando há sessão de Coordenador sem duas etapas (segue o texto de R2).
- No login com duas etapas a senha certa não grava `SUCCESS` em `login_attempt` (só o segundo passo grava), para que o `SUCCESS` da senha não zere a sequência de falhas de código da trava (R6). O bilhete é achado pelo hash com o escopo `app.invite_code_hash` já existente (política da `login_ticket` aceita conta ou hash).
- Confirmações com senha ou código errados em desligar, códigos novos e `reauth` respondem 401 `INVALID_CREDENTIALS` / `INVALID_TWO_FACTOR_CODE`, e contam na trava; ação fora da lista responde 400 e não conta. Estado inválido (ligar com as duas etapas já ligadas, desligar sem estar ligada) responde 409 `CONFLICT`.
- Código TOTP aceito em ligar (`enable`) não grava `last_used_step`; só os usos de verificação (login, desligar, códigos novos, reauth) gravam (D4).
- `SecretCipher` recebe a chave por `ctcore.security.two-factor-key`; os testes usam `backend/src/test/resources/config/application.properties` (pasta nova, chave fixa de teste).
- Nenhum `infra/seed` novo (como o plano): o Coordenador de teste do seed cai na configuração obrigatória.

Parte B (telas)

- T8 — arquivos: `frontend/src/i18n/pt-BR.json` (`twoFactor.*`, `reauth.*`), `mocks/handlers.ts` (contas `duas@`, `vencida@`, `coord@exemplo.com`; rotas de duas etapas, reauth e logout), `SessionProvider.tsx` (`twoFactor`), `package.json`/`package-lock.json` (`qrcode` 1.5.4 e `@types/qrcode` 1.5.6, versões exatas), `mocks/twofactor.msw.test.ts`; testes antigos ganharam `twoFactor` na sessão — prova: `npm run lint && npm run typecheck` e `twofactor.msw.test.ts` → passam; `npm run audit` → 0 vulnerabilidades
- T9 — `CodeField.tsx`, `TwoFactorStep.tsx`, `LoginPage.tsx`, `TwoFactorLogin.test.tsx` — prova: `npx vitest run src/features/auth/TwoFactor` → passa (CA21)
- T10 — `BackupCodesDialog.tsx` (inclui `BackupCodesPanel`), `TwoFactorSetupDialog.tsx`, `ReauthDialog.tsx`, `TwoFactorDialogs.test.tsx` → passa (CA22, CA23)
- T11 — `RequireTwoFactor.tsx`, `TwoFactorRequiredPage.tsx`, `App.tsx` (rota `/configurar-duas-etapas`); testes em `TwoFactorLogin.test.tsx` → passa (CA24)
- T12 — carregando, erro, sem permissão (403) e axe a 360 px nos dois temas nos testes acima → passa (CA25)
- T13 — `frontend/e2e/duas-etapas.spec.ts` — prova: `npm run e2e` → 8 passam (CA26)
- T14 — Regressão da parte B: em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → tudo passa; 36 arquivos e 258 testes; cobertura de linhas 98,06%. `./backend/mvnw -f backend/pom.xml verify` → relatórios sem falhas (JaCoCo ok).

Desvios (parte B):
- O texto de `mocks/handlers.ts` mantém `ana@exemplo.com` (Coordenador no mock) com `twoFactor {enabled:false, required:false}` para não quebrar os fluxos existentes; o Coordenador obrigado é `coord@exemplo.com`.
- A guarda redireciona para `/configurar-duas-etapas` (rota dentro de `RequireSession`); a página volta a `/` quando não é mais necessária.
- Estados "vazio" não se aplicam às janelas; "sem permissão" é a resposta 403 do servidor mostrada com `ForbiddenState`.
- Código de reserva no mock tem 10 caracteres (`RESERVA001`), como o servidor.

Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-17, estado d5f7f3faae7bc4cc722d2f552021fe94fc60c52e

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2 | PASSOU | `TwoFactorServiceIT` dentro do `verify` (117 `*IT` sem falha); código de `setup`/`enable` lido |
| CA3 | PASSOU | `TotpTest` (vetores RFC 6238, janela ±1) no `verify` (95 testes rápidos sem falha) |
| CA4 | PASSOU | `SecretCipherTest` (chave trocada, ausente, tamanho errado) e `TwoFactorServiceIT`; `application.yml` com chave vazia fora do perfil local |
| CA5, CA6 | PASSOU | `TwoFactorGateIT`; `TwoFactorGateFilter` libera só `/auth/session`, `/auth/logout`, `/auth/two-factor/**` |
| CA7, CA8 | PASSOU | `TwoFactorServiceIT`, `TwoFactorIsolationIT`; `disable` recusa quem `requiredFor` com 403, hashes SHA-256 no banco |
| CA9 a CA14 | PASSOU | `TwoFactorLoginIT` (6 testes); `login` não cria sessão com bilhete, `last_used_step` com update condicional, 5 erros matam o bilhete |
| CA15 | PASSOU | `TwoFactorServiceIT` confere que nenhum e-mail do outbox traz código ou segredo; não há rota de envio |
| CA16, CA17 | PASSOU | `ReauthIT`; `CriticalActionGuard.consume` com update condicional (sessão, ação, validade, uso único) |
| CA18, CA19 | PASSOU | `TwoFactorIsolationIT`, `TwoFactorAuditIT` |
| CA20 | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` rodam dentro do `verify`; diff do `openapi.yaml` cobre as seis rotas, `SessionView.twoFactor` e os quatro erros |
| CA21 a CA25 | PASSOU | `TwoFactorLogin.test.tsx`, `TwoFactorDialogs.test.tsx`, `twofactor.msw.test.ts` dentro de `npm run test:cov` (36 arquivos, 258 testes); `npm run lint` sem texto literal |
| CA26 | PASSOU | `npm run e2e`: 8 passam, incluindo `duas-etapas.spec.ts` `@critical` |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS; 95 rápidos e 117 de integração, 0 falhas; "All coverage checks have been met"
- em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov` → passam; 258 testes; linhas 98,06%
- em `frontend/`: `npm run e2e` → 8 passam
- Linha de base: sem regressão (nenhum teste anterior falhou).

Achados:
- sugestão, código, `TwoFactorService.setup` — grava o segredo pendente sem linha de auditoria; o AGENTS.md pede auditoria em toda ação que altera dado. Considerar `account.two_factor_setup_started` (sem segredo).
- sugestão, código, `TwoFactorService.verify` com `AuthService.loginSecondFactor` — o código de reserva é gasto antes de o bilhete ser consumido; se o consumo do bilhete falhar por corrida, o código se perde. Janela mínima.
- sugestão, código, `TwoFactorGateFilter` — chama `setupRequired` (duas consultas) em toda requisição autenticada fora da lista; cache por sessão se pesar.
- sugestão, código, `TwoFactorService.verify` (D4) — um TOTP usado no login não vale para `reauth` no mesmo passo de 30 s; comportamento previsto, vale aviso na tela se aparecer reclamação.

## Entrega

Duas etapas por TOTP (RFC 6238, janela ±1), 10 códigos de reserva com hash, segredo cifrado em repouso, segundo passo no login com bilhete de uso único (5 erros o invalidam), exigência por perfil (porta de acesso até configurar) e senha de novo nas ações críticas (uso único, atrelada à sessão e à ação). Seis rotas novas em `docs/openapi.yaml`, migração `V11__two_factor.sql`, telas de configuração, códigos de reserva, login e reautenticação com os quatro estados e textos em pt-BR.

Verificação (rodada 1, OK): `verify` do back-end (95 rápidos e 117 de integração, JaCoCo atendido); no front, lint, typecheck, build, `test:cov` (258 testes, 98,06% de linhas) e `e2e` (8 passam).

Limitações aceitas (sugestões da verificação): `setup` não grava auditoria; código de reserva gasto antes do consumo do bilhete (janela mínima); `TwoFactorGateFilter` faz duas consultas por requisição; TOTP usado no login não vale para `reauth` no mesmo passo de 30 s.

Verificações do PR #246: `security` (gitleaks) reprovou — regra `generic-api-key` em `backend/src/test/resources/config/application.properties` linha 2 (`ctcore.security.two-factor-key`, chave fixa de teste). Demais verificações passaram. Corrigir na fase 3 (por exemplo, gerar a chave no teste ou allowlist do gitleaks para esse arquivo) e reverificar.
