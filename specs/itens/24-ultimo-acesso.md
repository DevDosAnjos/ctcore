# 24 — RF-ACE-07 · Registrar o último acesso a cada login

Origem: issue #24 (Sprint 02); `docs/especificacao/02-contas-e-acesso.md` (modelo da conta "último acesso", RF-ACE-07). Branch: `item/24-ultimo-acesso`.

## 1. Objetivo

Guardar na conta a data e a hora do último acesso, atualizada a cada login bem-sucedido, para que a lista da equipe (RF-ACE-06) e o painel da plataforma (RF-PNL-02) a mostrem nos itens futuros. Fora do item: exibir o valor em qualquer tela ou rota, "último acesso da gestão" por organização (RF-PNL-02), renovação da sessão (`last_used_at` não conta como login).

## 2. Requisitos

- R1 (RF-ACE-07): `account` tem o campo `last_access_at` (vazio até o primeiro login).
- R2 (RF-ACE-07): todo início de sessão bem-sucedido grava `last_access_at` com o instante atual: login por e-mail e senha, aceite de convite e redefinição de senha (os três já passam por `SessionService.create`).
- R3: login recusado (senha errada, conta bloqueada ou desativada, desafio inválido) não altera o campo.
- R4 (isolamento): o campo só muda a conta da sessão criada; nenhuma outra conta é alterada, e nenhuma rota nova expõe o valor.
- R5 (D2): a atualização falha junto com a criação da sessão (mesma transação), sem login sem carimbo nem carimbo sem sessão.

## 3. Critérios de aceite

- CA1 (R1): dado o banco migrado, então `account.last_access_at` existe, é `timestamptz` e aceita nulo; contas existentes ficam com nulo (`AuthRoutesIT`).
- CA2 (R2): dado conta ativa com `last_access_at` nulo, quando faz login correto, então o campo passa a ser o instante do relógio injetado; num segundo login, mais tarde, o valor avança (`AuthRoutesIT`, teste novo com `Clock` controlável se já existir no `AuthBase`).
- CA3 (R2): dado aceite de convite e redefinição de senha que abrem sessão, então o campo é preenchido (`InviteIT` e `AuthRoutesIT`, asserção nos casos existentes).
- CA4 (R3): dado senha errada, conta bloqueada por tentativas ou desativada, então o campo continua como estava.
- CA5 (R4): dado duas contas, quando A faz login, então o campo de B não muda (`AuthIsolationIT`, asserção).
- CA6 (R5): dado falha ao inserir a sessão, então o campo não muda (teste em `SessionService`/`AuthRoutesIT` se viável sem mock pesado; senão coberto pela transação única, ver D2).

## 4. Plano técnico

- **Dados.** Migração `V9__account_last_access.sql`: `alter table account add column last_access_at timestamptz;` sem índice (nenhuma consulta ainda). `account` não tem RLS (V6); nada a ajustar. Seed: não muda.
- **API/OpenAPI.** Não muda: nenhuma rota nova nem campo novo na resposta.
- **Back-end.** Alterar `identity/SessionService.create`: dentro do `accountTx.forAccount` que insere a sessão, acrescentar `update account set last_access_at = :now where id = :a` com o mesmo `now`. Não mexer em `authenticate` (renovação). `AuthService`, `InviteService` e `AuthController` já chamam `create`; sem mudança. Testes em `AuthRoutesIT`, `InviteIT`, `AuthIsolationIT` (reaproveitar `AuthBase`).
- **Front-end.** Não muda.
- **Segurança.** Dado de baixo risco, sem senha, token nem IP. Não vai para log nem para métrica.
- **Riscos.** Conferir que `forAccount` abre transação única (CA6); conferir o relógio usado por `now()` para o teste ser determinístico.

## 5. Tarefas

- [x] T1: Migração V9 e atualização em `SessionService.create` — cobre CA1, CA2, CA3, CA6 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='AuthRoutesIT,InviteIT'`.
- [x] T2: Testes de recusa e de isolamento entre contas — cobre CA4, CA5 — prova: `verify -Dit.test='AuthRoutesIT,AuthIsolationIT'`.
- [x] T3: Regressão — prova: `./backend/mvnw -f backend/pom.xml verify` (JaCoCo ok).

## 6. Decisões

- D1: o campo fica em `account` (o modelo da especificação o lista na conta) e não em tabela à parte. Alternativa: derivar de `account_session.created_at`, descartada porque sessões são apagadas ao expirar e a lista precisaria de agregação.
- D2: "login" = criação de sessão, no ponto único `SessionService.create`, que cobre também aceite de convite e redefinição, onde a pessoa entra sem passar pela tela de login. Alternativa: só `AuthService.login`, descartada por deixar entradas sem carimbo. Suposição: se o dono quiser só o login por senha, restringe-se a um parâmetro.
- D3: sem auditoria nova. É carimbo derivado do login, não ação do usuário sobre dado de negócio, e o login já é registrado; sem evento, sem senha ou token. Suposição: basta.
- D4: sem tela nem rota neste item (issue sem canvas); o valor é consumido por RF-ACE-06 e RF-PNL-02. Notas de versão: sem mudança visível.

## 7. Construção

- T1: criados `backend/src/main/resources/db/migration/V9__account_last_access.sql`; alterado `identity/SessionService.create` (update de `last_access_at` na mesma transação do insert da sessão, mesmo `now`). Testes novos `AuthRoutesIT.lastAccess` e `lastAccessOnInviteAndReset` (CA1-CA4). Prova: `./backend/mvnw -f backend/pom.xml verify` passou (95 ITs, 0 falhas).
- T2: `AuthRoutesIT.lastAccess` (senha errada, desativada, bloqueada: campo intocado) e `AuthIsolationIT.lastAccessStaysWithTheAccount` (CA5). Prova: mesma execução do `verify`.
- T3: `./backend/mvnw -f backend/pom.xml verify`: BUILD SUCCESS, JaCoCo "All coverage checks have been met".
- Desvios: CA3 coberto em `AuthRoutesIT` (aceite e redefinição pelas rotas), não em `InviteIT` (não abre sessão). CA6: sem teste dedicado; `forAccount` envolve insert e update na mesma transação (D2).

## 8. Verificação

### Rodada 1 — OK — b027b36f569c6c042762eaee6e3b181082d37646

| Critério | Status | Evidência |
| --- | --- | --- |
| CA1 | PASSOU | V9 `add column last_access_at timestamptz` (nulo); `AuthRoutesIT.lastAccess` assere nulo antes do primeiro login. Tipo `timestamptz` visto na migração. |
| CA2 | PASSOU | `lastAccess`: valor = relógio injetado; segundo login, 1 h depois, avança. |
| CA3 | PASSOU | `lastAccessOnInviteAndReset` cobre aceite e redefinição (em `AuthRoutesIT`, não `InviteIT`; desvio declarado, sem impacto: `InviteIT` não abre sessão). |
| CA4 | PASSOU | `lastAccess`: senha errada, desativada e bloqueada (429) não alteram o campo. |
| CA5 | PASSOU | `AuthIsolationIT.lastAccessStaysWithTheAccount`. |
| CA6 | PASSOU | Sem teste dedicado (aceito no critério). `AccountTransactions.scoped` usa `transactions.execute`; insert e update estão no mesmo bloco, em transação única. |

Comandos: `./backend/mvnw -f backend/pom.xml verify` — 95 ITs, 0 falhas, JaCoCo "All coverage checks have been met", BUILD SUCCESS. Item sem front-end nem OpenAPI (nenhuma rota nova).

Achados obrigatórios: nenhum.
Sugestões: 1) `newAccess.loggedIn` roda fora da transação; fora do escopo do item.

Resultado: OK.

## 9. Entrega

- O que muda: `account.last_access_at` (`timestamptz`, nulo até o primeiro login), migração V9. `SessionService.create` grava o campo na mesma transação que insere a sessão, cobrindo login, aceite de convite e redefinição de senha. Login recusado não altera o campo; sem rota, tela ou OpenAPI novos.
- Verificação: `./backend/mvnw -f backend/pom.xml verify` com 95 ITs, 0 falhas e JaCoCo ok (rodada 1 OK).
- Decisões: ver D1-D3 (campo na conta, "login" = criação de sessão, sem auditoria nova).
- Limitações: CA6 sem teste dedicado (coberto pela transação única).

Closes #24
