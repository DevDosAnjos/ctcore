# 18 — Conta, vínculo, sessão e convite no servidor

Origem: issue #18 (Sprint 02); `docs/especificacao/02-contas-e-acesso.md` (RN-ACE-01 a RN-ACE-14, RN-ACE-16, RN-ACE-19, RN-ACE-20). Branch: `item/18-conta-vinculo-sessao-convite-servidor`.

## 1. Objetivo

Dar ao back-end o núcleo de identidade: conta única por pessoa, vínculos com organização e papel, convites, login com senha Argon2, recuperação de senha, tentativas de acesso com bloqueio, e sessões no servidor (30 dias para Aluno/Responsável, 12 horas para gestão) que se encerram ao trocar senha, desativar conta ou remover o último vínculo de gestão. É a base dos itens #19 (e-mails), #20 a #25 (telas) e #26/#27 (duas etapas e permissões).

Fora do item (cada um tem item próprio): telas, handlers MSW e textos de tela (#20 a #23, #25); e-mail com modelos, registro de falha, reenvio e aviso de "Novo acesso", RN-ACE-22 (#19); último acesso, RF-ACE-07 (#24); duas etapas e senha em ação crítica, RN-ACE-17/18/21 (#26); matriz de permissões por rota (#27); Minha conta e troca de e-mail, RN-ACE-15 e RF-ACE-04 (#36); lista e convite pela tela "Configurações › Usuários", RF-ACE-06; aviso aos 18 anos (RN-ACE-06, precisa da tabela de alunos); conta da equipe da plataforma (painel da plataforma). Turnstile real e e-mail real seguem simulados (Sprints 22 e 23).

## 2. Requisitos

- R1 (RN-ACE-01, RN-ACE-02): `account` tem e-mail único sem diferenciar maiúsculas; uma conta tem vários vínculos (`account_link`), na mesma organização ou em outras; nunca uma segunda conta para outro papel.
- R2 (RN-ACE-04): não existe rota pública que crie conta. Conta nasce só de convite ou do provisionamento interno (cadastro da organização, autocadastro aprovado), por serviço, nunca por rota aberta.
- R3 (RN-ACE-05): convite em cascata no serviço `InviteService`: Coordenador convida Administradores; Administrador convida Professores só para unidades dele; convite do portal (Aluno/Responsável) só pelo sistema (matrícula). Qualquer outra combinação é negada.
- R4 (RN-ACE-06): convite de Aluno exige maioridade (data de nascimento informada pelo chamador, 18 anos ou mais); menor de idade não recebe conta; o Responsável pode ser convidado.
- R5 (RN-ACE-07): se o e-mail convidado já tem conta, o convite cria só o novo vínculo (sem segunda conta) e é aceito com a senha que a pessoa já tem.
- R6 (RN-ACE-13): convite vale 7 dias, é de uso único, o código só existe em hash; reenviar cancela o anterior; no máximo 3 reenvios por dia por pessoa.
- R7 (RN-ACE-08): senha com 8 ou mais caracteres, ao menos 1 número e 1 maiúscula, sem símbolo obrigatório; senha da lista de senhas vazadas é recusada com "Essa senha é muito conhecida. Escolha outra."
- R8 (RN-ACE-09): a senha é guardada com Argon2 (`PasswordEncoder` existente); nenhuma rota nem serviço define ou redefine a senha de outra conta; a academia não tem como fazê-lo.
- R9 (RN-ACE-10): 5 senhas erradas seguidas travam aquele e-mail por 15 minutos; há limite de falhas por IP; login e recuperação exigem o desafio invisível pela porta `ChallengeVerifier`. Toda tentativa (e-mail, IP, data, resultado) é registrada em `login_attempt`.
- R10 (RN-ACE-11): login erra sempre com "E-mail ou senha incorretos." e a recuperação responde sempre "Se esse e-mail tiver uma conta, você vai receber um link em instantes.", em resposta idêntica exista ou não a conta.
- R11 (RN-ACE-12): conta com convite não aceito ou desativada não entra, com a mesma mensagem e o mesmo código HTTP do erro de senha.
- R12 (RN-ACE-14): link de nova senha vale 1 hora, uso único, novo pedido cancela o anterior; conta desativada não recebe link; conta com convite pendente recebe o convite de novo (R6). Redefinir a senha por esse link inicia a sessão (R13) e aplica R7 e R14.
- R13 (RN-ACE-19): sessão no servidor (`account_session`), cookie `SESSION` com valor aleatório guardado em hash; vale 30 dias sem uso se todos os vínculos ativos da conta são Aluno/Responsável e 12 horas sem uso nos demais casos (D3); cada uso renova; não existe "manter conectado"; logout encerra a sessão.
- R14 (RN-ACE-20): trocar a senha (logado ou por link), desativar a conta ou remover o último vínculo de gestão encerra na hora as sessões da conta em outros aparelhos (na desativação e na remoção, todas), e a troca de senha envia e-mail de aviso pelo `EmailSender`.
- R15 (RN-ACE-16, RN-ACE-03): `GET /auth/session` devolve a conta, os vínculos e o vínculo ativo para o front decidir o destino (sem sessão responde 401); conta com um vínculo só entra com ele ativo; com mais de um, o vínculo ativo fica vazio até `PUT /auth/session/link` escolher um (de outra conta ou inexistente: 404).
- R16 (isolamento, RNF-05): sem vínculo ativo escolhido, rotas de organização recusam (403); com ele, o contexto da organização (#14) vem do vínculo, nunca de parâmetro; vínculos e convites têm RLS por organização; uma conta nunca vê vínculo, convite ou sessão de outra.
- R17 (auditoria): convite criado/reenviado/cancelado/aceito, vínculo criado/encerrado, conta desativada, senha trocada/redefinida e sessões encerradas gravam `AuditService`, sem senha, token nem código (D5). Login e logout não são auditados (a tentativa de acesso é o registro).
- R18 (contrato): rotas novas entram em `docs/openapi.yaml` no mesmo PR, com códigos de erro; `OpenApiContractTest` e `ContractRoutesTest` passam.

## 3. Critérios de aceite

- CA1 (R1): dada a conta `Ana@x.com`, ao criar outra com `ana@X.com`, o banco recusa; e a mesma conta recebe vínculo Professor na organização A e Aluno na B, e dois papéis diferentes na A, sem nova conta.
- CA2 (R2): dado o conjunto de rotas, não existe rota que crie conta sem convite; `POST /auth/login` com e-mail desconhecido não cria conta; rota de cadastro inexistente (ex.: `/auth/register`) responde 401/404.
- CA3 (R3): Coordenador convida Administrador: ok; Administrador convida Professor em unidade sua: ok; Administrador convida Professor em unidade que não é dele, ou convida Administrador, ou Professor convida: negado; Coordenador convida Coordenador ou Aluno: negado; o sistema convida Aluno/Responsável: ok.
- CA4 (R4): convite de Aluno com 17 anos e 364 dias é recusado (`MINOR_HAS_NO_ACCOUNT`); com 18 anos é aceito; Responsável não precisa da data.
- CA5 (R5): e-mail já cadastrado convidado para outra organização: nenhuma conta nova, só um vínculo pendente; aceitar com a senha atual ativa o vínculo; senha errada no aceite não ativa e conta como falha (R9).
- CA6 (R6): convite aceito com o código válido funciona uma vez; segunda vez, vencido (8º dia), cancelado ou código inexistente dão o mesmo erro `INVITE_INVALID` com motivo distinguível (usado, vencido, cancelado) só para o convite com código certo; reenviar invalida o anterior; o 4º reenvio do dia é recusado (429); o banco só guarda o hash.
- CA7 (R7): senhas `abcdefg1` (sem maiúscula), `Abcdefgh` (sem número), `Ab1` (curta) e `Password1` (lista de vazadas) são recusadas, esta última com a mensagem exata; `Abcdefg1` passa; senha sem símbolo passa.
- CA8 (R8): a senha gravada começa com `$argon2id$`; nenhuma resposta, log nem auditoria traz a senha; nenhuma rota recebe senha para conta diferente da sessão ou do código.
- CA9 (R9): 5 senhas erradas seguidas para um e-mail, a 6ª tentativa (mesmo com a senha certa) responde 429 por 15 minutos e volta a funcionar depois (relógio injetado); o mesmo vale para e-mail inexistente; falhas de IP acima do limite (D4) dão 429; desafio recusado dá erro e não conta como senha errada; todas as tentativas aparecem em `login_attempt`.
- CA10 (R10): login com e-mail inexistente e com senha errada devolvem corpo e status idênticos; a recuperação devolve a mesma resposta para e-mail existente, inexistente e desativado.
- CA11 (R11): conta com convite não aceito e conta desativada, mesmo com a senha certa, recebem a resposta idêntica ao erro de senha e não ganham sessão.
- CA12 (R12): link de nova senha: vale na 1ª hora e uma vez; novo pedido cancela o anterior; conta desativada não gera link; conta com convite pendente recebe o convite de novo (e não link de senha); redefinir com senha inválida (R7) não consome o link; depois de redefinir, a resposta já traz a sessão iniciada.
- CA13 (R13): login cria sessão e cookie `SESSION` (HttpOnly, Secure, SameSite=Lax) sem `Max-Age` de "manter conectado"; conta só Aluno/Responsável vence com 30 dias parada (29d23h ainda vale, 30d1min não), conta com vínculo de gestão com 12 horas; uso dentro do prazo renova; o valor do cookie não aparece no banco; logout invalida o cookie.
- CA14 (R14): com duas sessões A e B, trocar a senha estando na A encerra a B (próxima chamada da B dá 401) e mantém a A; redefinir por link encerra todas; desativar a conta e remover o último vínculo de gestão encerram todas; remover um vínculo de gestão que não é o último não encerra; a troca de senha deixa um e-mail na caixa simulada, sem a senha no texto.
- CA15 (R15): `GET /auth/session` sem cookie dá 401 (código `UNAUTHENTICATED`); com sessão de conta de um vínculo, devolve o vínculo ativo; com dois, ativo vazio, e `PUT /auth/session/link` com vínculo próprio ativa; com vínculo de outra conta ou inexistente dá 404 e nada muda.
- CA16 (R16): sessão sem vínculo ativo chama rota de organização e recebe 403; com vínculo ativo da organização A, não alcança dado da B; a conta A não lista vínculos, convites nem sessões da conta B; RLS: leitura de `account_link`/convite com o escopo da B não traz linhas da A.
- CA17 (R17): cada ação listada em R17 grava uma linha de auditoria (ação, entidade, autor) sem as chaves senha, token e código; login e logout não gravam.
- CA18 (R18): `OpenApiContractTest` e `ContractRoutesTest` passam com as rotas novas.

## 4. Plano técnico

- **Dados** (migração `V6__identity.sql`, a criar; última existente é V5): `account` (`id`, `email` com índice único em `lower(email)`, `name`, `phone`, `password_hash` nulo até o aceite, `status` ACTIVE/INVITED/DEACTIVATED, `created_at`; sem colunas de duas etapas e último acesso, que vêm em #26 e #24); `account_link` (`id`, `account_id`, `organization_id`, `role` COORDINATOR/ADMINISTRATOR/TEACHER/STUDENT/GUARDIAN, `status` PENDING/ACTIVE/ENDED, `starts_on`, `ends_on`; único `(account_id, organization_id, role)`; chave `(organization_id, id)`) e `account_link_unit` (`link_id`, `organization_id`, `unit_id`, chave composta com `unit`); `account_invite` (`id`, `account_id`, `link_id`, `organization_id`, `code_hash` único, `expires_at`, `used_at`, `canceled_at`, `created_at`, `sent_by`); `password_reset` (`account_id`, `code_hash`, `expires_at`, `used_at`, `canceled_at`); `account_session` (`id`, `account_id`, `token_hash` único, `active_link_id` nulo, `device`, `ip`, `created_at`, `last_used_at`, `expires_at`); `login_attempt` (`email`, `ip`, `occurred_at`, `result`; índices por e-mail e por IP + data). RLS em `account_link`, `account_link_unit` e `account_invite` com política "organização do contexto, ou conta da transação (`app.account_id`), ou código do convite da transação (`app.invite_code_hash`)", mais `force row level security`; as demais tabelas não têm organização e só o pacote `identity` as usa. Padrão de RLS: V3/V4. Seed: `infra/seed/03-accounts.sql` com contas sintéticas (`on conflict do nothing`; hash Argon2 de senha fictícia gerado e fixo no arquivo), exigido por `SeedDataIT`.
- **API** (a criar em `backend/.../identity/`, prefixo `/api/v1/auth`): `POST /login`, `POST /logout`, `GET /session`, `PUT /session/link`, `GET /invitations/{code}` (estado do convite para a tela, sem dado de outras contas), `POST /invitations/{code}/accept` (corpo: senha para conta nova; senha atual para conta existente), `POST /password-reset` (pedido), `POST /password-reset/{code}` (nova senha), `POST /password` (trocar logado: senha atual e nova). Rotas públicas: login, pedido e uso do reset, consulta e aceite de convite; as demais exigem sessão. Erros pelos `ErrorCode`/`ApiExceptionHandler` existentes, com códigos novos `ACCOUNT_LOCKED`/`TOO_MANY_REQUESTS` (429), `INVITE_INVALID`, `PASSWORD_WEAK`, `PASSWORD_LEAKED`, `MINOR_HAS_NO_ACCOUNT`; mensagens pt-BR em `messages.properties`. `docs/openapi.yaml` ganha as rotas e esquemas (hoje `paths: {}`). Serviços sem rota (usados por itens seguintes): `InviteService.invite/resend/cancel`, `LinkService.end`, `AccountService.deactivate`, `AccountService.provision` (cadastro da organização). Bearer não há: só o cookie `SESSION` mais CSRF existente.
- **Segurança** (`config/SecurityConfig.java`, existente, e `isolation/OrganizationScopeFilter.java`, existente): trocar a sessão do contêiner (HttpSession) por um filtro de autenticação `SessionAuthenticationFilter` que lê o cookie `SESSION`, busca o hash em `account_session`, verifica validade e renova (a gravação de `last_used_at` no máximo uma vez por minuto), e coloca no contexto um `AccountPrincipal` que implementa `OrganizationPrincipal` (organização e unidades do vínculo ativo; organização nula sem vínculo ativo). Cadeia sem estado (`SessionCreationPolicy.STATELESS`). `OrganizationScopeFilter` passa a liberar `/api/v1/auth/**` para principal sem organização (cada rota confere a própria conta); fora de `/auth`, mantém a recusa. Rotas públicas liberadas em `authorizeHttpRequests`. Códigos de convite/reset: 32 bytes aleatórios (`SecureRandom`), base64url, guardados com SHA-256; comparação por consulta ao hash. Tempo de execução do login igual para e-mail existente ou não (rodar o Argon2 contra um hash falso fixo quando a conta não existe), para não revelar a conta pelo tempo. Nunca registrar senha, código, cookie ou hash em log (`SensitiveDataMasker` existente cobre; conferir). Lista de senhas vazadas: arquivo `backend/src/main/resources/passwords/common-passwords.txt` (a criar, as mais comuns de dado público, minúsculas) lido na subida (D2).
- **Auditoria**: `AuditService` exige organização no contexto e transação (#17). Eventos com organização conhecida (convite, vínculo) abrem o contexto da organização do convite; eventos de conta (senha, desativação, sessões) gravam uma linha por organização em que a conta tem vínculo ativo, pelo autor `ACCOUNT`; conta sem vínculo ativo grava só o que tem organização (D5). Um auxiliar interno `ScopedAudit` (a criar) abre o contexto com um `OrganizationPrincipal` de sistema.
- **Transações**: `OrganizationTransactions` (existente) cobre o escopo de organização; criar `AccountTransactions` (a criar, em `identity/`) que faz `set_config('app.account_id'/'app.invite_code_hash', ..., true)` na transação, com a mesma proteção contra trocar de escopo dentro da transação.
- **E-mail**: #19 ainda não existe; criar a interface `AccountMailer` (convite, link de nova senha, senha alterada) com uma implementação mínima sobre `EmailSender` (existente, simulado), texto em `messages.properties` e link montado com `ctcore.app.base-url` (propriedade nova em `application.yml`). #19 troca o miolo por modelos e registro de falha sem mudar quem chama.
- **Interface**: não muda (itens #20 a #25). Quatro estados, tradução de tela, axe, MSW e ponta a ponta não se aplicam aqui (D1).
- **Reuso**: `PasswordEncoder` Argon2 (`SecurityConfig`), `Clock` (`ClockConfig`, fixável nos testes), `ChallengeVerifier` e `EmailSender` simulados, `Outbox`, `AuditService`/`AuditActor`, `OrganizationContext`/`OrganizationTransactions`, `ApiError`/`ErrorCode`, `PostgresIT` e `RouteIsolationModelIT` (molde, adaptado a rotas de conta; D6), `Organizations` para criar organizações de teste.
- **Testes** (em `backend/src/test/java/br/com/ctcore/identity/`, a criar): `PasswordPolicyTest` (CA7), `InviteRulesTest` (CA3, CA4), `LoginLockoutTest` (CA9 com relógio fixo), `AccountIT` (banco: CA1, CA5, CA6, CA11, CA12, CA17), `AuthRoutesIT` (MockMvc + banco: CA2, CA8, CA10, CA13, CA14, CA15, CA18 de contrato), `AuthIsolationIT` (CA16, no molde da #15). `SessionCookieCsrfTest` (existente) usa `HttpSession` e deve ser adaptado ao novo cookie. `PasswordEncoderTest` (existente) permanece.
- **Riscos**: (1) trocar a sessão do contêiner mexe em `SecurityConfig` e em `SessionCookieCsrfTest`; (2) RLS com três escopos (organização, conta, código) é a parte mais delicada: cada política precisa de teste que prove que não vaza; (3) a lista de senhas vazadas local é pequena frente a bases reais (D2); (4) cobertura: JaCoCo exige 80% global e a #18 não está nos pacotes de 100%, mas `isolation` (que o item altera) exige 100%; (5) tamanho: o item passa de oito tarefas e por isso está em três partes, cada uma uma sessão.

## 5. Tarefas

**Parte A — dados, senha e tentativas de acesso**

- [x] T1: Migração `V6__identity.sql` (tabelas, índices, RLS de três escopos, `force row level security`), seed `infra/seed/03-accounts.sql` e `AccountTransactions` — cobre CA1, CA16 (RLS) — prova: `MigrationIT`, `SeedDataIT` e `./backend/mvnw -f backend/pom.xml verify -Dit.test=AccountIT`
- [x] T2: `PasswordPolicy` (regra de 8 caracteres, número, maiúscula e lista de vazadas, arquivo `common-passwords.txt`) — cobre CA7, CA8 (hash Argon2) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=PasswordPolicyTest`
- [x] T3: `LoginAttempts` (registro em `login_attempt`, bloqueio por e-mail e limite por IP, propriedades de limite) — cobre CA9 — prova: `LoginLockoutTest` (relógio fixo) e `AccountIT`

**Parte B — vínculos e convites**

- [x] T4: `AccountService` (`provision`, `deactivate`), `LinkService` (`end`) e `InviteService` (`invite`, `resend`, `cancel`, `accept`) com cascata, maioridade, 7 dias, uso único, reenvio e limite de 3 por dia, `AccountMailer` mínimo — cobre CA1, CA3, CA4, CA5, CA6 — prova: `InviteRulesTest` e `AccountIT`
- [x] T5: Auditoria dos eventos de convite, vínculo e conta (`ScopedAudit`) — cobre CA17 — prova: `AccountIT` (linhas em `audit_log`, sem senha, token ou código)

**Parte C — sessão, rotas e contrato**

- [x] T6: `SessionService` e `SessionAuthenticationFilter`, `AccountPrincipal`, `SecurityConfig` sem estado, ajuste do `OrganizationScopeFilter` e do `SessionCookieCsrfTest` — cobre CA13, CA16 (403 sem vínculo ativo) — prova: `AuthRoutesIT` e `SessionCookieCsrfTest`
- [x] T7: `AuthController`: login (desafio, bloqueio, mensagem única, tempo igual), logout, `GET /session`, `PUT /session/link` — cobre CA2, CA9, CA10, CA11, CA13, CA15 — prova: `AuthRoutesIT`
- [x] T8: Rotas de convite (`GET` e aceite, que já inicia a sessão) — cobre CA5, CA6 — prova: `AuthRoutesIT`
- [x] T9: Recuperação de senha (pedido e uso, regras de conta desativada e convite pendente) e troca logado, com encerramento das outras sessões e e-mail de aviso — cobre CA10, CA12, CA14 — prova: `AuthRoutesIT` (fluxo "troca de senha encerrando as outras sessões", duas sessões, equivalente de servidor ao ponta a ponta)
- [x] T10: Encerramento de sessões ao desativar conta e ao remover o último vínculo de gestão — cobre CA14 — prova: `AccountIT`
- [x] T11: `docs/openapi.yaml` com as rotas, esquemas e erros; códigos de erro e mensagens pt-BR — cobre CA18 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`
- [x] T12: `AuthIsolationIT` no molde da #15 (organizações, contas e perfis) — cobre CA16 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AuthIsolationIT`
- [x] T13: Documentar no `backend/README.md` (seção "Contas e sessões": rotas, serviços, propriedades) e rodar a regressão completa — cobre todos (cobertura, contrato) — prova: `./backend/mvnw -f backend/pom.xml verify`

## 6. Decisões

- D1 (muda o escopo): o item é só de back-end, como a issue diz ("**Back-end**"). Handlers MSW, telas, axe e o ponta a ponta "Troca de senha encerrando as outras sessões" não entram aqui: as telas e os handlers vêm com #20 a #23 e #25, e o ponta a ponta precisa da tela de troca de senha de #36 (Sprint 03). Neste item o fluxo é provado no servidor (T9, duas sessões em HTTP). Motivo: não há interface alguma a testar no navegador. Alternativa descartada: construir telas provisórias só para o ponta a ponta. Os três itens "Pronto quando" de interface (quatro estados, 360 px, axe, textos) ficam como não se aplicam; o ponta a ponta precisa constar em #36.
- D2: senhas vazadas conferidas contra lista local de senhas mais comuns versionada no repositório. Motivo: conexão externa só pela porta e simulada até as Sprints 22 e 23 (AGENTS.md); verificação de vazamento por serviço externo seria uma conexão nova. Alternativa descartada: consulta a serviço de vazamentos. Suposição: a lista cobre as senhas óbvias; se o dono quiser base de vazamentos real, entra uma porta `LeakedPasswordCheck` na Sprint 22.
- D3 (muda o comportamento): o prazo da sessão é decidido pelos vínculos da conta: 30 dias parada só se todos os vínculos ativos são Aluno/Responsável; qualquer vínculo de gestão (Coordenador, Administrador, Professor) leva a 12 horas, em qualquer área aberta. Motivo: RN-ACE-19 liga o prazo à "área aberta", mas a escolha de área é do front (#23); o critério por conta é o conservador e testável no servidor. Alternativa descartada: prazo por área aberta (permite 30 dias a quem também é gestor ao abrir só o portal; exige trocar o prazo a cada troca de área). Professor entra na gestão por ter acesso à gestão (RN-ACE-19 fala em "gestão ou painel").
- D4: limite por IP: 20 falhas de login em 15 minutos por IP respondem 429 até a janela esvaziar; bloqueio por e-mail: 5 falhas seguidas em 15 minutos, 15 minutos de bloqueio, contado também para e-mail inexistente (a resposta de bloqueio é a mesma para qualquer e-mail). Os valores ficam em propriedades (`ctcore.security.login.*`). O texto da regra diz só "limite por IP" sem número; 20 é suposição, trocar é mudar uma propriedade.
- D5: auditoria de conta: uma linha por organização em que a conta tem vínculo ativo, com autor `ACCOUNT`; evento sem organização possível (conta sem vínculo ativo) não grava em `audit_log` (a tabela exige organização para autor que não é da plataforma, D4 da #17); login e logout ficam só em `login_attempt`/`account_session`. Alternativa descartada: relaxar a regra da #17 para aceitar conta sem organização.
- D6: o teste de isolamento da #15 é por recurso de organização; as rotas de `/auth` são de conta. O `AuthIsolationIT` adapta o molde: duas organizações, duas contas e perfis diferentes, provando que um não alcança vínculo, convite ou sessão do outro e que rota de organização recusa quem não escolheu vínculo.
- D7: sem `POST /auth/register`: o cadastro da organização (#18 da especificação) e o autocadastro aprovado chamam `AccountService.provision`, que não é rota. O envio de convite (e o reenvio, cancelamento, desativação e remoção de vínculo) é serviço interno neste item; a tela e a rota "Configurações › Usuários" (RF-ACE-06) os expõem depois.
- D8: `PUT /auth/session/link` e a lista de vínculos em `GET /auth/session` entram porque RN-ACE-03 e a escolha de organização e área (#23) precisam do servidor, e sem elas conta com dois vínculos não consegue usar rota de organização. Alternativa descartada: deixar para #23 (a sessão ficaria sem organização para sempre).
- D9: aviso de "Novo acesso" (RN-ACE-22) e as telas de e-mail ficam em #19; aqui só os três e-mails que as regras do item exigem (convite, link de nova senha, senha alterada), por uma interface que #19 passa a alimentar com modelos.
- D10 (muda o escopo; ajuste pedido pela Verificação, rodada 1, achado 1): a D1 prometia que o ponta a ponta "Troca de senha encerrando as outras sessões" constaria em #36, mas a promessa não estava registrada em issue alguma, e o plano (`docs/plano-de-sprints.md`, linha 108) o põe na Sprint 02. Ajuste: o ponta a ponta fica **fora deste item** e passa a ser uma entrega declarada de quem criar a tela de troca de senha logado (hoje prevista em #36, Minha conta, Sprint 03); este item entrega a prova de servidor (T9, `AuthRoutesIT`, duas sessões em HTTP), que cobre CA14. Como o card e a issue #36 não podem ser alterados por esta fase, o registro vai ao dono no resumo ("Fora do item"): incluir o ponta a ponta no "Pronto quando" da #36 e ajustar a linha do plano para a Sprint 03, ou, se o dono preferir manter na Sprint 02, criar a tela de troca de senha em #20 a #25. Recomendada: mover para a #36. Alternativa descartada: construir tela provisória neste item (D1). Se o dono escolher manter na Sprint 02, o item #18 não muda; muda só a issue que ganha a tela. Sugestões da verificação (imports duplicados em `SecurityConfig`, contagem de tentativas na troca de senha, conferir #20 a #25 quanto aos "Pronto quando" de interface) não são ajuste de especificação: seguem para a fase de construção/novos itens.
- Suposição: não existe conta da equipe da plataforma neste item (sem papel de plataforma em `account_link`); o item do painel da plataforma acrescenta o que faltar sem quebrar as tabelas. Se estiver errada, só muda a coluna de papel.

## 7. Construção

### Parte A (sessão 1): T1 a T3 feitas; partes B e C por fazer

- T1: criados `backend/src/main/resources/db/migration/V6__identity.sql`, `infra/seed/03-accounts.sql` (5 contas, 6 vínculos, 1 unidade de vínculo; senha fictícia `SenhaFicticia1` com hash Argon2 fixo), `backend/src/main/java/br/com/ctcore/identity/AccountTransactions.java` (`forAccount`, `forInvite`, recusa trocar de escopo na transação); alterado `SeedDataIT` (limpeza das tabelas novas e teste `accountsSeed`); criado `AccountIT` (CA1 e RLS de organização, conta e código, escritas fora do escopo). Prova: `./backend/mvnw -f backend/pom.xml verify -Dtest=NoSuch -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='AccountIT,SeedDataIT,MigrationIT,RowLevelSecurityIT' -Djacoco.skip=true`: 22 testes, 0 falhas.
- T2: criados `identity/PasswordPolicy.java` (`check` devolve `WEAK`, `LEAKED` ou nulo), `backend/src/main/resources/passwords/common-passwords.txt` (lista local, D2) e `PasswordPolicyTest` (CA7; CA8 conferido com o `PasswordEncoder`, prefixo `$argon2id$`). Prova: `./backend/mvnw -f backend/pom.xml -q test -Dtest='PasswordPolicyTest,LoginLockoutTest'`: passou.
- T3: criados `identity/LoginAttempts.java` (`record`, `check`, regra pura `emailLocked`), `LoginLockoutTest` (relógio fixo) e, em `AccountIT`, os testes de bloqueio por e-mail (inclui e-mail inexistente e volta após 15 min), acerto/desafio recusado e limite por IP; propriedades `ctcore.security.login.*` em `application.yml` (D4). Prova: mesmos comandos de T1 e T2: passou.
- Regressão: `./backend/mvnw -f backend/pom.xml verify`: BUILD SUCCESS, 58 testes de integração e os rápidos sem falha, "All coverage checks have been met" (JaCoCo 80% e 100% nos pacotes exigidos).
- Desvios: (1) `login_attempt` ganhou a coluna `id` (identity) para ordenar tentativas do mesmo instante; (2) `account_invite`, `password_reset`, `account_session` ganharam `id`/`created_at` onde o plano não listava; (3) `PasswordPolicy.check` devolve enum em vez de lançar erro: os códigos `PASSWORD_WEAK`/`PASSWORD_LEAKED` entram no `ErrorCode` em T11; (4) o `LoginLockoutTest` testa a regra pura sem banco, e o caminho com banco fica no `AccountIT`.
- Não rodou: nada. Cobertura dos pacotes de 100% não é afetada (`identity` fora da lista).

### Parte B (sessão 2): T4 e T5 feitas; parte C por fazer

- T4: criados em `backend/src/main/java/br/com/ctcore/identity/`: `Role`, `IdentityException` (códigos de regra; o mapeamento para `ErrorCode`/HTTP fica em T11), `InviteRules` (cascata e maioridade, regra pura), `Codes` (código aleatório de 32 bytes e SHA-256), `AccountMailer` + `EmailAccountMailer` (sobre `EmailSender`, textos `mail.*` em `messages.properties`, `ctcore.app.base-url` em `application.yml`), `AccountService` (`provision`, `find`, `deactivate`), `LinkService` (`end`), `InviteService` (`invite`, `resend`, `cancel`, `accept`). Criados `InviteRulesTest` (CA3, CA4) e `InviteIT` (CA3 a CA6 no banco). Prova: `./backend/mvnw -f backend/pom.xml verify -Dtest='InviteRulesTest,PasswordPolicyTest,LoginLockoutTest' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='InviteIT,AccountIT' -Djacoco.skip=true`: 14 testes de integração e 9 rápidos, 0 falhas.
- T5: criado `ScopedAudit` (abre o contexto da organização com um principal de sistema e junta-se à transação de quem chama). Auditam: `invite.created/resent/canceled/accepted`, `link.created`, `link.ended`, `account.deactivated` (uma linha por organização com vínculo ativo, D5). `InviteIT` confere as linhas em `audit_log` e que nenhuma traz senha, token ou código. Mesmo comando de T4: passou.
- Regressão: `./backend/mvnw -f backend/pom.xml verify`: BUILD SUCCESS, 63 testes de integração e os rápidos sem falha, "All coverage checks have been met".
- Desvios: (1) os testes de T4/T5 estão em `InviteIT` (arquivo novo) e não em `AccountIT`: a limpeza de `AccountIT` apaga organizações e `audit_log` é append-only; `InviteIT` limpa suas linhas de auditoria desligando o gatilho, como o `SeedDataIT`; (2) `V6__identity.sql` ganhou `account_invite.resend boolean` para contar os reenvios do dia (o "4º reenvio" não é deduzível só de `created_at`); como a V6 ainda não foi commitada, foi editada no lugar (banco local que já aplicou a V6 precisa de `down -v`); (3) a auditoria de evento de conta é gravada em transação própria por organização, depois da transação principal: `OrganizationTransactions` recusa trocar de organização dentro de uma transação, então uma linha por organização não cabe numa só; (4) `IdentityException.Code.ALREADY_LINKED` (vínculo ativo já existente) entra para T11 mapear em `CONFLICT`.
- Atenção para a parte C (T6/T9): `ScopedAudit` usa `OrganizationContext.open`, que recusa abrir contexto de outra organização quando já há um aberto. Rotas `/auth` com vínculo ativo já têm contexto aberto pelo `OrganizationScopeFilter`; para eventos de conta (troca de senha) o filtro deve deixar `/auth/**` sem contexto aberto ou o auxiliar deve aceitar o contexto existente.
- Não rodou: nada. `identity` fora dos pacotes de 100%.

### Parte C (sessão 3): T6 a T13 feitas; item completo

- T6: criados em `identity/`: `AccountPrincipal`, `SessionCookie`, `SessionAuthenticationFilter`, `SessionService`; alterados `config/SecurityConfig` (filtro de sessão, `STATELESS`, rotas públicas de `/auth`), `isolation/OrganizationScopeFilter` (`/api/v1/auth/**` passa sem abrir contexto) e `SessionCookieCsrfTest` (a sonda usa `SessionCookie.create`, o mesmo cookie do controller). Prova: `AuthRoutesIT` (`sessionLifetime`, `linkChoice`) e `SessionCookieCsrfTest`: passaram.
- T7: criados `AuthService` (login com desafio, bloqueio, mensagem única, Argon2 contra hash falso quando a conta não vale) e `AuthController` (login, logout, `GET /session`, `PUT /session/link`). Prova: `AuthRoutesIT` (`noRegistration`, `sessionLifetime`, `linkChoice`, `identicalLoginFailures`, `lockout`): passaram.
- T8: `GET /invitations/{code}` e `POST /invitations/{code}/accept` (inicia a sessão); `InviteService` ganhou `describe`/`usable` (motivo só com código certo, no `ApiExceptionHandler`) e `resendToAccount`. Prova: `AuthRoutesIT` (`acceptInvite`, `acceptWithExistingAccount`): passaram.
- T9: `POST /password-reset`, `POST /password-reset/{code}`, `POST /password` em `AuthService`/`AuthController`; e-mail de aviso pelo `AccountMailer`. Prova: `AuthRoutesIT` (`changePassword`, duas sessões; `passwordReset`; `pendingInviteGetsInviteAgain`): passaram.
- T10: `LinkService.end` e `AccountService.deactivate` encerram sessões (`SessionService.endAll`/`endAllIfNoManagement`) e auditam `account.sessions_ended` (`AccountAudit`). Prova: `InviteIT.sessionsEndWithManagementLinkOrAccount`: passou.
- T11: `docs/openapi.yaml` com as 9 rotas, esquemas e os 8 códigos novos; `ErrorCode`, `ApiExceptionHandler` (mapeia `IdentityException`) e `messages.properties`. Prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`: passou.
- T12: `AuthIsolationIT` (3 testes: vínculos e contexto separados, troca de senha não toca a conta de outra organização, RLS de organização, conta e código). Prova: `verify -Dit.test=AuthIsolationIT`: passou.
- T13: seção "Contas e sessões" em `backend/README.md`. Regressão: `./backend/mvnw -f backend/pom.xml verify`: BUILD SUCCESS, 88 testes rápidos e 77 de integração sem falha, "All coverage checks have been met" (inclui 100% em `isolation`).
- Desvios: (1) só um código `TOO_MANY_REQUESTS` (429) para bloqueio por e-mail e por IP, sem `ACCOUNT_LOCKED`, para a resposta ser igual para qualquer e-mail; (2) códigos novos além dos do plano: `INVALID_CREDENTIALS` (401), `CHALLENGE_FAILED` (400), `RESET_LINK_INVALID` (410); `INVITE_INVALID` responde 410, com o motivo (`USED`, `EXPIRED`, `CANCELED`) em `fields[reason]` só quando o código existe; `ALREADY_LINKED` vira `CONFLICT` e vínculo inexistente vira `NOT_FOUND`; (3) o desafio recusado também é gravado em `login_attempt` (resultado `CHALLENGE_FAILED`) na recuperação de senha; (4) senha atual errada em `POST /password` responde 400 `VALIDATION_ERROR` no campo `currentPassword` (não 401, para o front não encerrar a sessão); (5) o aceite de convite usa a mesma regra de vínculo ativo do login (um vínculo: ativo; mais de um: vazio); (6) `HttpsTest` passou a declarar `controllers = Probe` porque o `@WebMvcTest` sem lista agora carrega o `AuthController`; sondas de teste novas usam `@TestComponent` para não entrar no `ContractRoutesTest`; (7) `server.servlet.session.cookie.*` permanece no `application.yml` porque o `ContractRoutesTest` lê o nome do cookie dali.
- Limites aceitos (sem teste): troca de senha logado não aplica contagem de tentativas à senha atual; o tempo do pedido de recuperação difere entre conta existente (envia e-mail) e inexistente.
- Não rodou: ponta a ponta de navegador e axe (não se aplicam, D1). Nada mais.

### Correção da rodada 1

- Achado obrigatório 1 (ponta a ponta sem dono): é de especificação, já atendido pela fase 2 na D10; sem mudança de código.
- Sugestão atendida por ser trivial: imports duplicados removidos de `backend/src/main/java/br/com/ctcore/config/SecurityConfig.java`. As outras duas sugestões (contagem de tentativas na senha atual; conferir #20 a #25) são opcionais e não foram feitas; a primeira segue como limite aceito.
- Regressão: `./backend/mvnw -f backend/pom.xml verify`: BUILD SUCCESS, 77 testes de integração, 0 falhas, "All coverage checks have been met". Front-end não tocado.

## 8. Verificação

### Rodada 1 — CORRIGIR_ESPEC — 4154ad8c9c94e98d79f329eea15f49b8cf18554e

| Critério | Status | Evidência |
| --- | --- | --- |
| CA1 | PASSOU | `AccountIT` (índice único em `lower(email)`, vínculos múltiplos); `MigrationIT`, `SeedDataIT` verdes |
| CA2 | PASSOU | `AuthRoutesIT.noRegistration`; `ContractRoutesTest` |
| CA3, CA4 | PASSOU | `InviteRulesTest`, `InviteIT` |
| CA5, CA6 | PASSOU | `InviteIT`, `AuthRoutesIT.acceptInvite/acceptWithExistingAccount` |
| CA7, CA8 | PASSOU | `PasswordPolicyTest` (prefixo `$argon2id$`) |
| CA9 | PASSOU | `LoginLockoutTest`, `AccountIT`, `AuthRoutesIT.lockout`; leitura de `LoginAttempts` confirma que desafio recusado não entra na contagem |
| CA10, CA11 | PASSOU | `AuthRoutesIT.identicalLoginFailures`; `AuthService.login` usa uma só exceção e Argon2 contra hash falso |
| CA12 | PASSOU | `AuthRoutesIT.passwordReset`, `pendingInviteGetsInviteAgain` |
| CA13 | PASSOU | `AuthRoutesIT.sessionLifetime`; `SessionCookie` (HttpOnly, Secure, Lax, sem Max-Age); só o hash vai ao banco |
| CA14 | PASSOU | `AuthRoutesIT.changePassword` (duas sessões), `InviteIT.sessionsEndWithManagementLinkOrAccount` |
| CA15 | PASSOU | `AuthRoutesIT.linkChoice` |
| CA16 | PASSOU | `AuthIsolationIT` (3 testes), RLS em `AccountIT` |
| CA17 | PASSOU | `InviteIT` confere linhas de `audit_log` sem senha, token ou código |
| CA18 | PASSOU | `OpenApiContractTest`, `ContractRoutesTest` dentro do `verify` |

Comandos: `./backend/mvnw -f backend/pom.xml verify`: BUILD SUCCESS, 77 testes de integração, 0 falhas, "All coverage checks have been met" (JaCoCo 80% e 100% nos pacotes exigidos). Sem linha de base de falha anterior; nada reprovou. Front-end não tocado pelo item (não rodou).

Achados obrigatórios
1. (especificação) Seção 6, D1, e card (issue #18, "Pronto quando"): o card exige o ponta a ponta "Troca de senha encerrando as outras sessões" na suíte do Playwright, e `docs/plano-de-sprints.md` (linha 108) o coloca na Sprint 02. A D1 o adia dizendo que "precisa constar em #36", mas a issue #36 (Sprint 03) não o cita e nenhuma issue da Sprint 02 (#19 a #25) tem a tela de troca de senha logado. O ponta a ponta fica sem dono e a promessa da D1 não está registrada em lugar nenhum. Corrigir: registrar o ponta a ponta no escopo da #36 (ou de outra issue/tela que o torne possível) e anotar na D1 o ajuste do plano; se for preciso mudar o marco do ponta a ponta no plano, é decisão do dono.

Sugestões (3)
- `config/SecurityConfig.java` tem imports duplicados (`SessionAuthenticationFilter`, `SessionService`, `HttpMethod`, `SessionCreationPolicy`); compila, mas deve ser limpo.
- A troca de senha logado não conta tentativas da senha atual (limite aceito na seção 7); sessão roubada pode tentar adivinhar a senha atual sem bloqueio.
- Os "Pronto quando" de interface (MSW, quatro estados, axe) ficam como não se aplicam por D1; conferir que as issues #20 a #25 os assumem.

RESULTADO: CORRIGIR_ESPEC

### Rodada 2 — OK — 7d1e0eacd1fe7e0c130541821fea1fdcb3cf068a

Diff lido: `git diff 4154ad8… 7d1e0ea…` (só `SecurityConfig` sem imports duplicados, linha do plano e arquivo do item).

| Critério | Status | Evidência |
| --- | --- | --- |
| CA1 a CA18 | PASSOU | mantido da rodada 1; código alterado só por remoção de imports duplicados em `SecurityConfig`; `verify` completo reexecutado e verde |

Achado obrigatório da rodada 1 (ponta a ponta sem dono): atendido na D10. O ponta a ponta sai deste item, a prova de servidor (`AuthRoutesIT`, duas sessões em HTTP) cobre CA14, e `docs/plano-de-sprints.md` agora põe o fluxo na Sprint 03. Pendência do dono, fora do item: incluir o ponta a ponta no "Pronto quando" da #36 e retirá-lo do da #18, para o card e o plano baterem.

Comandos: `./backend/mvnw -f backend/pom.xml verify`: BUILD SUCCESS, 77 testes de integração, 0 falhas, "All coverage checks have been met". Front-end não tocado (não rodou).

Achados obrigatórios: nenhum.

Sugestões (2, mantidas): contagem de tentativas na senha atual da troca logada (limite aceito); conferir que #20 a #25 assumem os "Pronto quando" de interface.

RESULTADO: OK

## 9. Entrega

Conta, vínculo, sessão e convite no servidor (#18): migração `V6__identity.sql` com RLS, contas e vínculos por organização, convite (cascata, maioridade, reenvio, cancelamento), política de senha (Argon2id, lista local), bloqueio de login por e-mail e IP com desafio, sessão por cookie (HttpOnly, Secure, Lax; só o hash no banco), troca e recuperação de senha, escolha de vínculo, encerramento de sessões e auditoria sem segredo. Nove rotas em `/api/v1` entram no `docs/openapi.yaml`.

Verificação: rodada 2 `OK`; `./backend/mvnw -f backend/pom.xml verify`: 77 testes de integração, 0 falhas, JaCoCo 80% e 100% nos pacotes exigidos. Front-end não tocado.

Decisões e limites: o ponta a ponta "Troca de senha encerrando as outras sessões" sai deste item (D10) e é coberto aqui pela prova de servidor com duas sessões; o dono deve incluí-lo no "Pronto quando" da #36 e retirá-lo do da #18. A troca de senha logado não conta tentativas da senha atual (limite aceito). Banco local que aplicou uma V6 anterior precisa de `down -v`.
