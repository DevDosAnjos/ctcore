# 419 — Telas de acesso, conta, configurações e usuários no visual do canvas (Sprints 01 a 03)

Origem: issue #419 (Sprint 10.5 · Identidade visual), telas listadas na issue em `docs/design/canvas` (todas as 42 existem). Branch: `item/419-telas-acesso-conta-config`. Depois da #415, #416, #417 e #418 (feitas).

## 1. Objetivo

Refazer no padrão do canvas as telas que já existem no sistema para entrar (login, duas etapas, convite, recuperar senha, escolha de organização, área indisponível), a conta (minha conta, duas etapas, códigos de reserva, trocar e-mail), a organização e as unidades (dados, cor, transferir coordenação, lista, nova, desativar, dados da unidade) e os usuários (lista, convidar). Só o visual: mesmos textos de regra, mesmas rotas, mesmo comportamento. Quem usa: todos os perfis.

Fora do escopo (issue): regra, rota, texto de regra ou campo novo; o que o canvas mostra de sprint futura; mudança das telas marcadas "Ajustada" no canvas; conteúdo das demais telas de gestão (#420 a #423).

## 2. Requisitos

- R1: as telas de acesso (`/entrar`, `/esqueci-senha`, `/redefinir-senha/:code`, `/convite/:code`, `/sem-acesso`, escolha de organização/área, passo de duas etapas e código de reserva) usam uma moldura única `AuthShell` do canvas: painel escuro da marca com chamada e contato de suporte à esquerda (a partir de 768 px) e formulário de 420 px à direita; no celular só o formulário, com a marca no topo (D1).
- R2: o título de cada tela de acesso é h1 em `Barlow Condensed` caixa alta (36 px) com subtítulo, campos e botões pelos componentes da #415 (`Field`, `Input`, `Button`, `Banner`), botão principal `lg` de largura total.
- R3: os estados de acesso do canvas têm o visual novo com o texto e a regra de hoje: e-mail travado 15 minutos (`LoginTravado`, resposta 429), convite vencido/usado (`ConviteVencido`), link de senha vencido (`LinkSenhaVencido`), convite com conta existente (`PrimeiroAcessoConta`), código de reserva (`LoginCodigoReserva`), área indisponível (`SemAcessoM`) e escolha de organização (`EscolhaOrgM`) (D2).
- R4: minha conta (`/conta`, gestão e portal) usa `PageHeader`, `Card`/`Section` e `Badge`/`StatusBadge`; as janelas Ativar duas etapas, Códigos de reserva, Trocar e-mail e reautenticação usam `Modal`, `SummaryBox` e `Banner` (`MinhaConta`, `DuasEtapas`, `CodigosReserva`, `PortalTrocarEmail`, `GMConta`).
- R5: configurações da organização (`ConfigAcademia`, `ConfigOrgContraste`, `TransferirCoordenacao`) e das unidades (`ConfigUnidades`, `UnidadeNova`, `UnidadeDesativar`, `UnidadeUnica`, `ConfigUnidade`, `GMConfig`, `GMConfigUnidade`) usam `PageHeader`, `Section`, `Field`, `DataTable`, `Modal`/`ConfirmDialog`, `Banner` e `components/states`; o aviso de cor sem contraste e o de única unidade seguem o canvas.
- R6: usuários (`ConfigUsuarios`, `UsuarioConvidar`, `GMUsuarios`) usam `PageHeader`, `DataTable`, `Person`/`Avatar`, `StatusBadge`, `Modal` e `QuickFilters` onde o canvas os traz; a seção de permissões do Professor na unidade segue `ConfigPermProf` (D3).
- R7: toda tela tocada tem os quatro estados (carregando, vazio, erro, sem permissão) por `components/states`, funciona de 360 a 1440 px e passa no axe nos temas claro e escuro.
- R8: nenhuma cor, fonte ou tamanho solto nas pastas tocadas (`auth`, `account`, `organization`, `units`, `users`, `AuthShell`): sem hex nem classe de paleta fixa; textos em `pt-BR.json`. O `TwoFactorSetupDialog` e o `ColorField` (hoje com cor fixa) passam a usar tokens, exceto a amostra da cor que o usuário escolhe, que entra por variável CSS.
- R9: as telas ganham casos no teste visual (#416) com referências geradas no container; casos de estado e de janela abrem a tela pelos passos do caso (D4).
- R10: comportamento igual: testes de tela e `e2e` continuam passando; teste só muda onde procurava classe de estilo.
- R11: o arquivo do item registra a tabela "tela do canvas → rota → o que ficou diferente e por quê" (D5).

## 3. Critérios de aceite

- CA1 (R1): dado `/entrar` a 1440 px, então há painel escuro com marca, chamada e suporte, e o formulário ao lado; a 390 px só o formulário com a marca no topo, sem rolagem horizontal; as demais rotas de acesso usam o mesmo `AuthShell`.
- CA2 (R2): dada cada tela de acesso, então existe um h1 só, e e-mail, senha e códigos têm rótulo ligado ao campo; erro de campo aparece junto do campo com `aria-describedby`.
- CA3 (R3): dado login com senha `limite`, então aparece a tela de e-mail travado com o texto atual; convite `vencido`/`usado` e redefinição com código inválido mostram o estado de link vencido; convite `existente` mostra o aceite com a conta existente; código de reserva `RESERVA001` entra.
- CA4 (R3): dado Professor abrindo `/configuracoes/usuarios`, então a tela de área indisponível usa o `AuthShell` e "Sair" funciona; dado usuário com mais de um vínculo, a escolha de organização e área usa o `Modal`/tela nova e escolhe e abre a área.
- CA5 (R4): dado `/conta`, então os blocos Dados, Senha, Duas etapas e Sessões são `Section`; ativar duas etapas, ver códigos de reserva e trocar e-mail abrem `Modal` com o mesmo fluxo de antes (senha `errada` dá 401, `limite` dá 429, código 123456 vale).
- CA6 (R5): dado `/configuracoes/organizacao` com cor `#F5E663`, então aparece o aviso de contraste e o salvar fica recusado como hoje; transferir coordenação mantém a confirmação digitada.
- CA7 (R5): dado Coordenador em `/configuracoes/unidades`, então lista, nova unidade e desativar usam os componentes novos; desativar a única unidade mostra o aviso `UnidadeUnica` e não executa; `/configuracoes/unidade` mostra os dados e a seção de permissões do Professor.
- CA8 (R6): dado `/configuracoes/usuarios`, então a lista e os filtros usam `DataTable`/`QuickFilters`, convidar abre `Modal` e desativar/editar unidades mantêm a regra; Professor e Aluno recebem a tela de sem permissão.
- CA9 (R7): dada cada tela tocada, então carregando, vazio, erro com "Tentar de novo" e sem permissão aparecem por `components/states` (testes de tela existentes cobrem; acrescentar onde faltar); `axeViolations` é zero nos dois temas, em jsdom e nos e2e a 360 e 1024 px.
- CA10 (R8): dado o código novo ou alterado, quando o lint e o teste de varredura rodam, então não há hex, classe de paleta fixa nem texto literal nas pastas tocadas.
- CA11 (R9): dado `visual/casos.ts`, então há um caso por tela da lista que existe no sistema (e por estado/janela que o MSW alcança), 1440 e 390, claro e escuro, ligado ao nome do canvas; as referências novas estão em `visual/referencias/` e nenhuma referência de outra tela mudou.
- CA12 (R10, R7): dada a regressão do front, então `lint`, `typecheck`, `build`, `test:cov` (100% em `features/{billing,charges,payments,subscription,deactivation}`) e `e2e` passam.
- CA13 (R11): dado este arquivo, então a seção "Construção" traz a tabela com as 42 telas da issue, a rota de cada uma ou "sem tela no sistema", e a diferença.

## 4. Plano técnico

- Dados e API: não mudam. Segurança: não muda (permissão continua no servidor; só marcação e classes).
- Interface: 42 telas do canvas viram, no sistema, as telas de `features/auth`, `account`, `organization`, `units` e `users`, que já usam parte dos componentes da #415 mas com cartão e campos antigos. Falta uma peça: a moldura de acesso (`AuthShell`), que entra na tabela de componentes de `docs/design/README.md` (regra 4). Desktop e celular são a mesma aplicação responsiva (corte `md`, 768 px).
- Arquivos vistos (todos com a marcação própria de hoje): `features/auth/LoginPage.tsx` (cartão `max-w-md`, `<input>` e `<label>` soltos, cabeçalho com `ThemeToggle`), `TwoFactorStep.tsx`, `CodeField.tsx`, `InvitePage.tsx`, `ForgotPasswordPage.tsx`, `ResetPasswordPage.tsx`, `PasswordFields.tsx`, `AreaUnavailablePage.tsx`, `TwoFactorRequiredPage.tsx`, `ChooseLinkModal.tsx`, `ChooseLinkGate.tsx`, `SupportContact.tsx`, `BackupCodesDialog.tsx`, `ReauthDialog.tsx`, `TwoFactorSetupDialog.tsx` (cor fixa); `features/account/AccountPage.tsx`, `AccountFrame.tsx`, `ChangeEmailForm.tsx`, `ChangePasswordForm.tsx`, `SessionsSection.tsx`, `TwoFactorSection.tsx`, `ConfirmEmailPage.tsx`; `features/organization/OrganizationSettingsPage.tsx`, `ColorField.tsx` (cor fixa), `LogoCropDialog.tsx`, `TransferCoordinationDialog.tsx`; `features/units/UnitsPage.tsx`, `NewUnitPage.tsx`, `DeactivateUnitDialog.tsx`, `UnitSettingsPage.tsx` (440 linhas, tem as permissões do Professor), `ShortNameDialog.tsx`, `UnitModalitiesSection.tsx`, `UnitSwitcher.tsx`; `features/users/UsersPage.tsx`, `InviteUserDialog.tsx`, `EditUnitsDialog.tsx`, `DeactivateUserDialog.tsx`, `UnitChecks.tsx`; `frontend/visual/casos.ts` e `telas.visual.ts`; `frontend/src/i18n/pt-BR.json`; `docs/design/README.md`.
- A criar: `frontend/src/components/layout/AuthShell.tsx` (+ teste); varredura de tokens e de h1 dessas pastas (`frontend/src/features/telasCanvas419.test.ts`, no molde de `portalPages.test.ts`); casos em `visual/casos.ts`; referências PNG.
- Reuso: `PageHeader`, `Card`, `Section`, `Field`, `Input`, `Select`, `Button`, `Banner`, `Badge`, `StatusBadge`, `SummaryBox`, `Modal`, `ConfirmDialog`, `DataTable`, `QuickFilters`, `Person`, `Avatar`, `components/states`, `ThemeToggle`, `axeViolations`, tokens da #414, `passesContrast`, `canvas-shot.mjs` e `visual-canvas.mjs`.
- Riscos: (1) muitos testes de tela procuram rótulo, papel e texto; trocar `<input>` por `Field`/`Input` pode mudar `aria-describedby` e quebrar `getByLabel` (percebido nos testes de cada pasta); (2) o `e2e` de login, convite, recuperar senha e duas etapas usa os mesmos rótulos, que devem permanecer; (3) a moldura de acesso muda o posicionamento de `Turnstile` (script só com chave `VITE_*`, sem chave nada é carregado); (4) estados e janelas só viram caso visual com passos no caso (D4); (5) PNGs: cerca de 30 casos × 4 ≈ 120 arquivos, medir o tamanho total.
- Camada de dados, API e segurança: não mudam.

### Divisão em partes (cada uma cabe numa sessão)

- Parte A, acesso: T1 a T4. Parte B, conta: T5 a T6. Parte C, organização e unidades: T7 a T9. Parte D, usuários, casos visuais e regressão: T10 a T12. É um item só, com uma verificação e uma entrega.

## 5. Tarefas

- [x] T1: criar `AuthShell` (painel da marca, formulário de 420 px, marca no topo no celular, `ThemeToggle`, `SupportContact`) com teste e linha na tabela de componentes do `docs/design/README.md` — cobre CA1 — prova: em `frontend/`, `npx vitest run AuthShell design-readme`
- [x] T2: `LoginPage`, `TwoFactorStep`, `CodeField`, estado de e-mail travado e código de reserva no `AuthShell` com `Field`/`Input`/`Button`/`Banner` — cobre CA1, CA2, CA3 — prova: `npx vitest run LoginPage TwoFactorStep CodeField`
- [x] T3: `InvitePage` (criar senha, conta existente, convite vencido), `ForgotPasswordPage`, `ResetPasswordPage` (link vencido) e `PasswordFields` — cobre CA2, CA3 — prova: `npx vitest run InvitePage ForgotPasswordPage ResetPasswordPage PasswordFields`
- [x] T4: `AreaUnavailablePage`, `TwoFactorRequiredPage`, `ChooseLinkModal`/`ChooseLinkGate` e `SupportContact` no padrão — cobre CA4 — prova: `npx vitest run AreaUnavailablePage TwoFactorRequiredPage ChooseLink`
- [x] T5: `AccountPage` e seções (`Section`, `Badge`), `SessionsSection`, `ChangePasswordForm`, `ChangeEmailForm`, `ConfirmEmailPage` e `AccountFrame` — cobre CA5, CA9 — prova: `npx vitest run features/account`
- [x] T6: janelas `TwoFactorSetupDialog` (sem cor fixa), `BackupCodesDialog`, `ReauthDialog` em `Modal`/`SummaryBox`/`Banner` — cobre CA5, CA10 — prova: `npx vitest run TwoFactorSetupDialog BackupCodesDialog ReauthDialog`
- [x] T7: `OrganizationSettingsPage`, `ColorField` (sem cor fixa, amostra por variável CSS), `LogoCropDialog` e `TransferCoordinationDialog` — cobre CA6 — prova: `npx vitest run features/organization`
- [x] T8: `UnitsPage`, `NewUnitPage`, `DeactivateUnitDialog` (incluindo o aviso da única unidade) e `UnitSwitcher` — cobre CA7 — prova: `npx vitest run UnitsPage NewUnitPage DeactivateUnitDialog UnitSwitcher`
- [x] T9: `UnitSettingsPage` (dados, permissões do Professor), `ShortNameDialog` e `UnitModalitiesSection` — cobre CA7 — prova: `npx vitest run UnitSettingsPage ShortNameDialog UnitModalitiesSection`
- [x] T10: `UsersPage`, `InviteUserDialog`, `EditUnitsDialog`, `DeactivateUserDialog`, `UnitChecks` — cobre CA8 — prova: `npx vitest run features/users`
- [x] T11: teste de varredura de tokens, texto literal e h1 nas pastas tocadas, e axe nos dois temas das telas de cada parte; ajuste de testes que procuravam classe de estilo — cobre CA2, CA9, CA10 — prova: `npx vitest run telasCanvas419 a11y` e `npm run lint`
- [x] T12: casos visuais (passos para estado e janela, D4) em `visual/casos.ts`/`telas.visual.ts`, referências no container, tabela "tela → rota → diferença" em "Construção" e regressão completa — cobre CA11, CA12, CA13 — prova: `npx vitest run visual`, `npm run test:visual:docker -- --update-snapshots`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: uma moldura única `AuthShell` para todas as telas de acesso, em vez de repetir o painel em cada página. Motivo: o canvas usa o mesmo painel nas 22 telas; é a regra 4 do `docs/design/README.md` (peça que falta vira componente). Alternativa descartada: marcação em cada página.
- D2: telas de estado do canvas (travado, vencido, link vencido) são as mesmas telas do sistema nos casos 429 e 410, não rotas novas. Motivo: o item não cria rota nem regra.
- D3: `ConfigPermProf` é a seção de permissões do Professor dentro de `UnitSettingsPage`. `PermissoesTabela` ("Usuários › Tabela de permissões") não tem tela no sistema; se na construção não houver tela equivalente em `UsersPage`, fica de fora e é registrada na tabela como "sem tela no sistema", sem criar tela.
- D4 (muda o comportamento do teste visual): o caso do teste visual ganha `passos` opcionais (clicar e preencher antes do print) para chegar a janelas e estados (convite vencido, link vencido, travado, janelas). Motivo: sem isso só a página aberta é comparada. Alternativa descartada: ficar só com páginas e deixar janelas sem referência, o que contraria o "Pronto quando" da issue sobre estados.
- D5: o canvas e a especificação não divergem por ora; qualquer divergência achada na construção entra na tabela de CA13 e vale a especificação (README do design, regra 3).
- D6: telas só de celular do canvas (`LoginM`, `GMConta` etc.) são a mesma tela responsiva vista a 390 px, sem tela nova (regra do `AGENTS.md`).
- Suposição: todas as telas listadas existem no sistema exceto, possivelmente, `PermissoesTabela`; se outra faltar, entra na tabela como "sem tela no sistema". Se o MSW não alcançar um estado (por exemplo escolha de organização), o caso visual dele é omitido e anotado.

## Construção

- T1 — arquivos: `components/layout/AuthShell.tsx` (+ `.test.tsx`), `i18n/pt-BR.json` (chamada do painel), `docs/design/README.md` (linha na tabela) — prova: `npx vitest run AuthShell design-readme` → 3 passaram (axe claro e escuro)
- T2 — arquivos: `auth/LoginPage.tsx`, `TwoFactorStep.tsx`, `CodeField.tsx`, `components/ui/field.tsx` (`alertError` e `id` fixo, para manter `role="alert"` e os ids que os testes usam) — prova: `npx vitest run LoginPage TwoFactorStep CodeField TwoFactorLogin field` → 61 passaram
- T3 — arquivos: `auth/InvitePage.tsx`, `ForgotPasswordPage.tsx`, `ResetPasswordPage.tsx`, `PasswordFields.tsx` — prova: `npx vitest run InvitePage ForgotPasswordPage ResetPasswordPage PasswordFields PasswordRules` → passaram
- T4 — arquivos: `auth/AreaUnavailablePage.tsx`, `TwoFactorRequiredPage.tsx`, `ChooseLinkModal.tsx`, `RequireArea.test.tsx` (só o `ThemeProvider` em volta, porque a moldura tem `ThemeToggle`) — prova: `npx vitest run AreaUnavailablePage TwoFactorRequiredPage ChooseLink RequireArea SupportContact` → 25 passaram
- T5 — arquivos: `account/AccountPage.tsx`, `ChangePasswordForm.tsx`, `ChangeEmailForm.tsx`, `SessionsSection.tsx`, `TwoFactorSection.tsx`, `ConfirmEmailPage.tsx` — prova: `npx vitest run features/account` → 20 passaram
- T6 — arquivos: `auth/TwoFactorSetupDialog.tsx` (sem `bg-white`; a imagem do QR já é opaca), `BackupCodesDialog.tsx`, `ReauthDialog.tsx` — prova: `npx vitest run TwoFactorSetupDialog BackupCodesDialog ReauthDialog TwoFactorDialogs` → 15 passaram
- T7 — arquivos: `organization/OrganizationSettingsPage.tsx`, `ColorField.tsx` (amostra por `--amostra`, texto `text-brand-foreground`), `LogoCropDialog.tsx`, `TransferCoordinationDialog.tsx` — prova: `npx vitest run features/organization` → 39 passaram
- T8 — arquivos: `units/UnitsPage.tsx`, `NewUnitPage.tsx`, `DeactivateUnitDialog.tsx`, `UnitSwitcher.tsx`, `Text.tsx` (agora `Field`/`Input`) — prova: `npx vitest run features/units` → 69 passaram
- T9 — arquivos: `units/UnitSettingsPage.tsx`, `ShortNameDialog.tsx`, `UnitModalitiesSection.tsx` — prova: `npx vitest run features/units` → 69 passaram
- T10 — arquivos: `users/UsersPage.tsx`, `InviteUserDialog.tsx`, `EditUnitsDialog.tsx`, `DeactivateUserDialog.tsx`, `UnitChecks.tsx` — prova: `npx vitest run features/users` → 11 passaram
- T11 — arquivos: `features/telasCanvas419.test.ts` (cor fixa, campo solto e h1 só do `AuthShell`), axe claro e escuro no `AuthShell.test.tsx`; os demais axe nos dois temas já estavam nos testes de tela — prova: `npx vitest run telasCanvas419 AuthShell` e `npm run lint` → 53 passaram; lint limpo
- T12 — arquivos: `visual/casos.ts` (`passos`, 21 casos novos), `visual/telas.visual.ts` (executa os passos; entra com o código 123456 nas contas `duas@`), 80 PNGs novos e 20 PNGs trocados em `visual/referencias/` — prova: `npx vitest run visual` → 57 passaram; `npm run test:visual:docker -- --update-snapshots -g "<casos do item>"` → 100 + 4 passaram; referências em 13 MB no total (eram 8,4 MB). A imagem do Playwright já estava na máquina e foi mantida.

Regressão (de `frontend/`): `npm run lint && npm run typecheck && npm run build` → ok; `npm run test:cov` → 187 arquivos, 1713 testes passaram, limites de cobertura atendidos; `npm run e2e` → 81 passaram. O back-end não foi tocado e não rodou.

Referências que mudaram de propósito (telas tocadas): `entrar`, `conta`, `configuracoes-organizacao`, `configuracoes-unidade` (1440 e 390) e `configuracoes-unidades`, `configuracoes-usuarios` (só 390). Nenhuma outra tela mudou.

Desvios:
- O contato de suporte fica uma vez só, abaixo do formulário (nos dois tamanhos), e não dentro do painel escuro; evita duplicar a região "Precisa de ajuda?" e o celular continua com o contato.
- `UnitsPage` e `UsersPage` ficaram com um cartão por linha (`Card`/`Section`) e não com `DataTable`: os testes de tela procuram o `section` de cada unidade e usuário. `StatusBadge`, `Banner` e botões `secondary` foram aplicados.
- A seção `ConfigPermProf` é a de "Permissões do Professor" dentro de `UnitSettingsPage` (D3).
- E-mail travado, link vencido e convite vencido são os mesmos componentes em outros estados (D2), mostrados em `Banner`.
- O prettier padrão reformatou por engano alguns arquivos de `account/` e `ChooseLinkModal.tsx` (quebras de linha mais curtas); o repositório não tem configuração dele. Conteúdo igual.

Sem caso visual (o harness não alcança): `EscolhaOrganizacao`/`EscolhaOrgM` (o login simulado já entra com vínculo ativo; a janela `ChooseLinkModal` tem teste de tela e axe) e `UnidadeUnica` (o MSW tem mais de uma unidade; o aviso tem teste em `DeactivateUnitDialog`).

### Tabela tela do canvas → rota → diferença

| Tela do canvas | Rota no sistema | O que ficou diferente e por quê |
| --- | --- | --- |
| Login, LoginM | `/entrar` | `AuthShell` com painel escuro e chamada nova ("Sua academia, em ordem."); suporte abaixo do formulário (desvio 1) |
| LoginDuasEtapas, DuasEtapasM | `/entrar` (após a senha) | igual; o título é o texto de hoje da etapa |
| LoginCodigoReserva, CodigoReservaM | `/entrar` (duas etapas, "Usar um código de reserva") | igual |
| LoginTravado, LoginTravadoM | `/entrar` (resposta 429) | `Banner` de perigo com o texto de hoje, acima do formulário |
| PrimeiroAcesso, PrimeiroAcessoM | `/convite/:code` | igual |
| PrimeiroAcessoConta, PrimeiroAcessoContaM | `/convite/existente` | aviso da conta existente em texto simples (um `Banner` de status quebrava o teste de carregamento) |
| ConviteVencido, ConviteVencidoM | `/convite/vencido` | `Banner` de aviso com o motivo (vencido, usado, cancelado) |
| RecuperarSenha, RecuperarSenhaM | `/esqueci-senha` | igual; confirmação em `Banner` de sucesso |
| NovaSenha, NovaSenhaM | `/redefinir-senha/:code` | igual |
| LinkSenhaVencido, LinkSenhaVencidoM | `/redefinir-senha/vencido` | igual |
| EscolhaOrgM | janela `ChooseLinkModal` (qualquer rota, com mais de um vínculo) | sem caso visual (ver acima) |
| SemAcessoM | `AreaUnavailablePage` (ex.: Professor em `/configuracoes/usuarios`) | no `AuthShell`; a 390 px é a mesma tela (D6) |
| MinhaConta, GMConta | `/conta` | `PageHeader`, `Section`, `Badge`/`Banner` de situação; GMConta é a mesma tela a 390 px |
| DuasEtapas | `/conta` → "Ativar duas etapas" | `Modal`; QR sem fundo branco fixo |
| CodigosReserva | `/conta` → "Gerar códigos de reserva novos" | `Modal` com códigos em `surface-soft` |
| PortalTrocarEmail | `/conta` (Aluno e Responsável) | mesma tela de gestão e portal |
| ConfigAcademia | `/configuracoes/organizacao` | `Field`, `Banner` |
| ConfigOrgContraste | `/configuracoes/organizacao` (cor `#F5E663`) | amostra por variável CSS; aviso com ícone e texto, salvar recusado como antes |
| TransferirCoordenacao | `/configuracoes/organizacao` → "Transferir coordenação" | `Modal`, aviso em `Banner`, confirmação digitada mantida |
| ConfigUnidades, GMConfig | `/configuracoes/unidades` | um cartão por unidade (desvio 2) |
| UnidadeNova | `/configuracoes/unidades/nova` | `Stepper` e `Field` |
| UnidadeDesativar | `/configuracoes/unidades` → "Desativar" | impedimentos em `Banner` |
| UnidadeUnica | mesma janela, quando é a última unidade ativa | aviso "última unidade" em `Banner`; sem caso visual |
| ConfigUnidade, GMConfigUnidade | `/configuracoes/unidade` | `Checkbox`, `Banner`; GMConfigUnidade é a mesma tela a 390 px |
| ConfigUsuarios, GMUsuarios | `/configuracoes/usuarios` | cartão por usuário com `StatusBadge` (desvio 2) |
| UsuarioConvidar | `/configuracoes/usuarios` → "Convidar" | `Modal`, `Field`, `UnitChecks` com `Checkbox` |
| ConfigPermProf | `/configuracoes/unidade`, seção "Permissões do Professor" | igual |
| PermissoesTabela | sem tela no sistema | D3; nenhuma tela criada |

## Verificação

### Rodada 1 — CORRIGIR_CODIGO — 2026-10-29, estado 2c37fa89e6f57037fe4615f0cec2e31c9a2a0409

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `AuthShell.test.tsx` + referências `entrar` 1440/390; `vitest run visual telasCanvas419 AuthShell` → 110 passaram |
| CA2 | PASSOU | varredura de h1 em `telasCanvas419.test.ts` e testes de tela de `auth` (test:cov verde) |
| CA3 | PASSOU | testes de LoginPage, InvitePage, ResetPasswordPage e `TwoFactorLogin` verdes; casos visuais travado/vencido/reserva |
| CA4 | PASSOU | `AreaUnavailablePage`, `RequireArea`, `ChooseLink*` verdes |
| CA5 | PASSOU | `features/account` verde (test:cov) |
| CA6 | PASSOU | `features/organization` verde; caso visual de contraste |
| CA7 | PASSOU | `features/units` verde (aviso da última unidade testado em `DeactivateUnitDialog`); `UnitsPage` sem `DataTable` não é exigida aqui (a lista é de R5, ver achado) |
| CA8 | NÃO PASSOU | `grep DataTable\|QuickFilters` em `users/` e `units/` não acha nada; a lista de usuários não usa `DataTable` nem `QuickFilters` |
| CA9 | PASSOU | `components/states` em `UsersPage`/`UnitsPage`; axe nos dois temas nos testes; e2e 81 passaram |
| CA10 | PASSOU | `npm run lint` limpo; varredura sem hex nem classe de paleta (único `#000000` é valor de reserva do `<input type=color>` em `ColorField.tsx:42`) |
| CA11 | PASSOU | 21 casos novos em `visual/casos.ts`, 45 PNGs listados no diff, nenhuma referência de outra tela alterada (diff só em telas tocadas) |
| CA12 | PASSOU | lint, typecheck, build ok; test:cov 187 arquivos / 1713 testes; e2e 81 passaram |
| CA13 | PASSOU | tabela presente em "Construção" com as 42 telas agrupadas; `PermissoesTabela` como "sem tela no sistema" (D3) |

Comandos: `npm run lint && npm run typecheck && npm run build` → ok; `npm run test:cov` → 1713 passaram; `npm run e2e` → 81 passaram; `npx vitest run visual telasCanvas419 AuthShell` → 110 passaram. Back-end não tocado, não rodado. `test:visual:docker` não reexecutado aqui.
Achados:
- obrigatório, código, `frontend/src/features/users/UsersPage.tsx` (e `units/UnitsPage.tsx`, R5) — esperado: R6/CA8 pedem `DataTable` e `QuickFilters` na lista de usuários (e `DataTable` na de unidades, R5); observado: um `Card` por linha, sem `DataTable` nem `QuickFilters`, declarado como desvio 2 só porque os testes procuram o `section` de cada item; evidência: grep sem ocorrência nas duas pastas; corrigir: usar `DataTable`/`QuickFilters` e ajustar os testes de estrutura, ou, se o dono aceitar o desvio, ajustar R5, R6 e CA8 (nesse caso o resultado vira especificação). Obs.: o desvio é a causa; teste que procura `section` não é teste de classe de estilo (R10).
- sugestão, código, `ColorField.tsx:42` — o `#000000` de reserva é inofensivo, mas é um literal de cor; considerar `getComputedStyle` do token ou comentário.
- sugestão, especificação — a regressão do back-end não rodou; ok pois não foi tocado.

### Correção da rodada 1
- CA8/R5/R6 (lista sem `DataTable`/`QuickFilters`) corrigido; o desvio 2 acima deixa de valer — arquivos: `users/UsersPage.tsx` (`DataTable` com `Person`, `StatusBadge` e `QuickFilters` Todos/Ativos/Convite enviado com contagem), `units/UnitsPage.tsx` (`DataTable`), `i18n/pt-BR.json` (`users.actions`, `users.filters`, `units.actions`), testes `UsersPage.test.tsx` (acha a linha da tabela em vez do `section`; novo teste dos filtros) e `UnitsPage.test.tsx`, e2e `usuarios`, `unidades`, `nova-unidade` (linha/célula em vez de heading) — prova: `npx vitest run features/users features/units telasCanvas419 visual` → 187 passaram
- Referências visuais regeneradas no container: `npm run test:visual:docker -- --update-snapshots -g "configuracoes-unidades|configuracoes-usuarios|configuracoes-usuario-convidar|configuracoes-unidade-desativar|sem-acesso"` → 20 passaram
- Sugestão (`#000000` em `ColorField.tsx`) não aplicada: opcional.
Regressão: `npm run lint && npm run typecheck && npm run test:cov && npm run e2e` → ok; 187 arquivos, 1714 testes; e2e 81 passaram. Back-end não tocado, não rodou.

### Rodada 2 — OK — 2026-10-29, estado 65b81669018464315f7a553c410c15b4b9cd1cae

| Critério | Status | Evidência |
|---|---|---|
| CA8 | PASSOU | `UsersPage.tsx` usa `DataTable`, `Person`, `StatusBadge` e `QuickFilters`; `UnitsPage.tsx` usa `DataTable`; `npx vitest run src/features/users src/features/units` → 81 passaram; e2e verde |
| CA7 | PASSOU | mantido da rodada 1; `UnitsPage` agora com `DataTable`, testes de units verdes |
| CA9 | PASSOU | `test:cov` verde (axe nos testes de tela); e2e 81 passaram |
| CA11 | PASSOU | diff desde a rodada 1 só toca referências de `configuracoes-unidades`, `-usuarios`, `-usuario-convidar`, `-unidade-desativar` (telas alteradas) |
| CA12 | PASSOU | lint, typecheck, build ok; `test:cov` 187 arquivos / 1714 testes; e2e 81 passaram |
| CA1–CA6, CA10, CA13 | PASSOU | mantidos da rodada 1; mudança posterior não os toca |

Comandos: `npm run lint && npm run typecheck && npm run build` → ok; `npm run test:cov` → 1714 passaram; `npm run e2e` → 81 passaram. Back-end não tocado, não rodado. `test:visual:docker` não reexecutado (PNGs regenerados pelo construtor).
Achados:
- sugestão, código, `ColorField.tsx:42` — `#000000` de reserva segue como literal (da rodada 1).
- sugestão, especificação — o item registra `UnitsPage`/`UsersPage` com `DataTable`, mas o texto do desvio 2 em "Construção" e as linhas da tabela "um cartão por unidade/usuário" ficaram desatualizados.

## Entrega

Telas de acesso, conta, organização, unidades e usuários refeitas no visual do canvas (Sprint 10.5). Só aparência: mesmas rotas, regras e textos de regra.

- Acesso: moldura única `AuthShell` nas telas de login, duas etapas, convite, recuperação de senha, escolha de organização e área indisponível. Os estados travado, vencido e link vencido são as rotas existentes (429 e 410), sem rota nova.
- Conta, organização e unidades: mesmas telas no padrão do canvas. Usuários com `DataTable` e `QuickFilters`; unidades com `DataTable`.
- Teste visual: 21 casos novos em `frontend/visual/casos.ts`, com `passos` opcionais (D4), e referências em `frontend/visual/referencias/` (45 PNGs, 1440 e 390 px, claro e escuro).
- Verificação: rodada 2 `OK`. `npm run lint`, `typecheck`, `build` e `test:cov` (1714 testes) e `e2e` (81) verdes. Back-end não tocado.

Decisões: D1 a D6 acima.

Limitações:
- `PermissoesTabela` ("Usuários › Tabela de permissões") não tem tela no sistema: fica na tabela CA13 como "sem tela no sistema" (D3).
- `#000000` de reserva em `frontend/src/features/organization/ColorField.tsx` segue como literal (sugestão, não aplicada).
- O texto de "Construção" que descreve as listas como cartões ficou desatualizado após a rodada 2 (sugestão, especificação; não alterado nesta entrega).
- `test:visual:docker` não foi reexecutado na rodada 2; as referências foram regeneradas na rodada 1.
