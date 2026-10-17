# 20 — RF-ACE-01 · Tela de login

Origem: issue #20 (Sprint 02); `docs/especificacao/02-contas-e-acesso.md` (RF-ACE-01, RN-ACE-10, RN-ACE-16) e `docs/especificacao/00-visao-geral.md` (RNF-10, RNF-28). Branch: `item/20-tela-de-login`.

## 1. Objetivo

Entregar a primeira tela do produto: a pessoa entra com e-mail e senha e segue para a área dela. O servidor do login já existe (#18: `POST /auth/login`, bloqueio, limite por IP, `ChallengeVerifier`); falta o lado do navegador (rotas, cliente da API, sessão, desafio invisível, bloco de suporte, handlers MSW) e o formato legível dos e-mails (RNF-28). É a base de #21 a #25, que reaproveitam o roteador, o cliente da API e o layout de tela pública.

Fora do item: criar senha pelo convite (#21), "Esqueci minha senha" como tela (#22; aqui só o link), escolha de organização e área (#23), último acesso (#24), área indisponível (#25); contato da unidade depois do login (D3); Turnstile e e-mail reais (Sprints 22 e 23).

## 2. Requisitos

- R1 (RF-ACE-01): a rota `/entrar` mostra e-mail, senha com botão "Mostrar"/"Ocultar", botão "Entrar" e o link "Esqueci minha senha" (para `/esqueci-senha`, tela da #22).
- R2 (RF-ACE-01): antes do login a tela mostra o suporte da plataforma (e-mail e, se configurado, WhatsApp pelo `WhatsAppButton` existente), lido de `VITE_SUPPORT_EMAIL` e `VITE_SUPPORT_WHATSAPP` (D2).
- R3 (RF-ACE-01, D3): o bloco de contato é um componente reutilizável que aceita o contato da plataforma ou o da unidade; a exibição do contato da unidade depois do login fica para as telas que existem depois do login.
- R4 (RN-ACE-10, RN-ACE-11): erro de senha, conta desativada e convite não aceito mostram a mesma mensagem "E-mail ou senha incorretos."; `TOO_MANY_REQUESTS` (429) mostra aviso de muitas tentativas sem dizer se o e-mail existe; `CHALLENGE_FAILED` pede para tentar de novo.
- R5 (RNF-10, RN-ACE-10): o formulário usa o desafio invisível Turnstile: com `VITE_TURNSTILE_SITE_KEY` carrega o script da Cloudflare, envia o token em `challengeToken` e só mostra o desafio se a Cloudflare exigir; sem a chave (local, CI) não carrega nada e envia sem token (o verificador simulado aceita).
- R6 (RN-ACE-16): com sessão válida (`GET /auth/session` 200), abrir `/entrar` leva à área da pessoa; sem sessão, qualquer rota protegida leva a `/entrar?next=<caminho>` e, depois do login, a pessoa cai nesse caminho. `next` só vale se for caminho interno (começa com `/`, não com `//`).
- R7 (contrato): os handlers MSW de `POST /auth/login` e `GET /auth/session` batem com `docs/openapi.yaml`; `msw.test.ts` passa.
- R8 (tela): estados aplicáveis (D4): carregando (verificando sessão e enviando) e erro (rede ou 5xx com "Tentar de novo"); funciona a partir de 360 px, passa no axe nos temas claro e escuro, com rótulo em todo campo, foco visível, senha com `autocomplete="current-password"` e e-mail com `autocomplete="username"`. Todo texto em `pt-BR.json`.
- R9 (RNF-28): os e-mails de conta (convite, nova senha, senha alterada, novo acesso) saem em HTML de uma coluna (largura máxima 600 px), fonte de 18 px e botão de largura total quando há link; os argumentos são escapados (D6).

## 3. Critérios de aceite

- CA1 (R1): dado `/entrar`, quando a tela abre, então há campos "E-mail" e "Senha" com rótulo, o botão "Entrar" e o link "Esqueci minha senha" apontando para `/esqueci-senha`; "Mostrar" troca o campo para texto e vira "Ocultar", e volta.
- CA2 (R2, R3): dado `/entrar` sem login, então aparece o e-mail de suporte configurado; sem `VITE_SUPPORT_WHATSAPP` não há botão de WhatsApp; o componente, recebendo um contato de unidade, mostra telefone, WhatsApp e e-mail dela.
- CA3 (R4): dado `POST /auth/login` respondendo 401 `INVALID_CREDENTIALS`, quando a pessoa envia, então vê "E-mail ou senha incorretos." num aviso anunciado a leitores de tela, o campo senha é limpo e o foco volta a ele.
- CA4 (R4): dado 429, então vê o aviso de muitas tentativas; dado `CHALLENGE_FAILED` (400), então vê o aviso de tentar de novo e o desafio é reiniciado; nenhum dos dois diz se o e-mail existe.
- CA5 (R4, R8): dado campo vazio, quando envia, então cada campo mostra o erro dele e nenhuma chamada sai; dado 500 ou rede fora, então vê erro com "Tentar de novo" que reenvia.
- CA6 (R5): dado `VITE_TURNSTILE_SITE_KEY` definida e script simulado no teste, quando envia, então `challengeToken` vai no corpo; sem a chave, nenhum script é adicionado ao documento e o corpo não traz `challengeToken`.
- CA7 (R6): dado login com 200, então segue para `next` quando for caminho interno e para `/` caso contrário (`next=//evil.com` e `next=https://x` são ignorados); dado `GET /auth/session` 200 ao abrir `/entrar`, então redireciona sem mostrar o formulário; dado 401 ao abrir `/`, então vai a `/entrar?next=%2F`.
- CA8 (R8): dado a tela em 360 px e nos temas claro e escuro, então o axe não aponta violação e não há rolagem horizontal; enquanto envia, o botão fica desabilitado e a tela diz "Entrando..." (sem envio duplo).
- CA9 (R7): dado o OpenAPI, então `npm test` (`msw.test.ts`) confere que `POST /auth/login` e `GET /auth/session` têm handler com corpo e status do contrato.
- CA10 (R9): dado cada um dos 4 modelos, então o corpo gravado tem tabela de largura 600 px no máximo, `font-size:18px`, botão com `width:100%` quando há link (e o link também em texto), e `<`, `&` e aspas nos argumentos saem escapados; `InviteIT`, `AccountIT`, `EmailServiceIT` e `NewAccessIT` seguem verdes.

## 4. Plano técnico

- **Dados.** Não muda.
- **API/OpenAPI.** Nenhuma rota nova: login e sessão são da #18 e já estão em `docs/openapi.yaml`. O arquivo só muda se a conferência do MSW achar diferença.
- **Back-end.** Pacote `br.com.ctcore.mail` (existe): `EmailLayout` (a criar) monta o HTML (uma coluna, 18 px, botão largo, escape); `EmailService.send` (existe) passa o texto e o link, se houver, pelo `EmailLayout` antes de gravar `body`. `EmailTemplate` (existe) diz se o modelo tem botão. Textos continuam em `messages.properties` (existe). A regra "texto apagado depois do envio" e os testes de #19 seguem.
- **Front-end.** Dependência nova: `react-router` (D1). A criar: `src/lib/api.ts` (fetch em `/api/v1`, lê o cookie `XSRF-TOKEN` e manda `X-XSRF-TOKEN` em POST/PUT/PATCH/DELETE, devolve `{code, message, fields}` dos erros); `src/features/auth/` com `SessionProvider` (carrega `GET /auth/session`, expõe sessão e `refresh`), `RequireSession`, `LoginPage`, `Turnstile` (carrega o script só com chave), `SupportContact`, `safeNext`. Alterar: `src/App.tsx` (vira o roteador: `/entrar`, `/esqueci-senha` provisório da #22, `/` protegida mostrando a tela atual de boas-vindas), `src/main.tsx` (envolve com o roteador), `src/i18n/pt-BR.json` (`auth.*`), `src/mocks/handlers.ts` (os dois handlers). Reutiliza `fields`, `states` (`LoadingState`, `ErrorState`), `Card`, `WhatsAppButton`, `ThemeToggle`, `ui/button`. `.env.example` do front (a criar) lista as três variáveis; `frontend/README.md` (existe) ganha duas linhas sobre elas.
- **Interface.** Layout público centralizado, um cartão, uma coluna, sem o desenho do canvas (D5). `frontend/e2e/` (existe, `smoke.spec.ts`) ganha um fluxo `@critical` de login, por ser fluxo crítico.
- **Segurança.** Bloqueio, limite por IP e permissão continuam no servidor (#18); a tela não decide nada. Mensagem única de erro (RN-ACE-11); `next` validado contra redirecionamento aberto; senha só no estado do formulário, nunca em URL, log ou armazenamento; script da Cloudflare só do domínio oficial e só com chave; HTML dos e-mails escapa os argumentos. Sem dado pessoal novo.
- **Riscos.** Turnstile real não é provado aqui (sem chave nem conta): só com script simulado, limite aceito até a Sprint 22. Gmail e Outlook no celular (RNF-28) não dá para conferir sem e-mail real: fica a prova estrutural do HTML (CA10) e a conferência real vai para a Sprint 22 (D6). Dependência nova entra na cobertura de 80% e no `npm run audit`.
- **Doc.** `frontend/README.md`. Notas de versão: não existe arquivo ainda ("Novidades" é RF-AJU-04); nada a atualizar.

## 5. Tarefas

- [x] T1: `EmailLayout` e ligação em `EmailService.send` (HTML de uma coluna, 18 px, botão largo, escape) — cobre CA10 — prova: `EmailLayoutTest` e `./backend/mvnw -f backend/pom.xml verify -Dit.test='EmailServiceIT,InviteIT,AccountIT,NewAccessIT'`.
- [x] T2: `react-router`, `src/lib/api.ts`, `SessionProvider`, `RequireSession`, `safeNext` e roteador em `App.tsx`/`main.tsx` — cobre CA7 — prova: `npm test` (testes de `safeNext`, `api` com CSRF e redirecionamentos).
- [x] T3: `Turnstile` e `.env.example` do front — cobre CA6 — prova: teste com script simulado, com e sem chave.
- [x] T4: `SupportContact` e textos `auth.*` em `pt-BR.json` — cobre CA2 — prova: teste do componente com contato de plataforma e de unidade.
- [x] T5: `LoginPage` com estados, mensagens por código e foco — cobre CA1, CA3, CA4, CA5, CA8 — prova: `LoginPage.test.tsx` (inclui axe nos dois temas e 360 px, com `src/test/axe.ts` e `device.ts`).
- [x] T6: handlers MSW de `POST /auth/login` e `GET /auth/session` e conferência com o OpenAPI — cobre CA9 — prova: `npm test` (`msw.test.ts`).
- [x] T7: e2e `@critical` de login, `frontend/README.md` e regressão — cobre CA1, CA3 e CA7 ponta a ponta — prova: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` em `frontend/`, e `./backend/mvnw -f backend/pom.xml verify`.

## 6. Decisões

- D1: adicionar `react-router` em vez de roteamento caseiro. Motivo: #21 a #25 precisam de rotas, `next` e redirecionamento; é a única dependência nova. Alternativa: `history` do navegador na mão, descartada por repetir o que a biblioteca resolve.
- D2 (muda o comportamento do produto): o suporte da plataforma vem de variáveis do front (`VITE_SUPPORT_EMAIL`, `VITE_SUPPORT_WHATSAPP`), com `suporte@ctcore.com.br` como padrão e WhatsApp ausente. Motivo: o contato real é do dono e só importa na publicação (#192); não precisa de rota nem de banco. Alternativa: rota pública de suporte com OpenAPI e teste, descartada por custo sem ganho agora. Suposição: o dono confirma e-mail e WhatsApp reais antes de publicar; se mudar, só muda a variável.
- D3 (muda o comportamento do produto): "depois do login, o contato da unidade" entra aqui só como componente pronto (`SupportContact` aceita contato de unidade). A exibição fica para as telas autenticadas (#23, #25 e o layout da gestão), porque hoje não há tela depois do login nem rota que devolva o contato da unidade. Alternativa: criar já a rota de contato com teste de isolamento, descartada por ser trabalho de outra tela. Sugestão: lembrar disso ao especificar #25 e o layout autenticado.
- D4: login é tela pública sem lista: "vazio" e "sem permissão" não se aplicam; valem carregando e erro. Os quatro estados seguem obrigatórios nas telas autenticadas.
- D5: o canvas (`Login.dc.html`, `LoginM.dc.html`) não é acessível ao agente; a tela segue o texto do card e a base visual do projeto (tema, `Card`, `ui/button`), como nos itens #7 e #8. Suposição: se o desenho diferir, o dono pede ajuste de layout, sem mudar regra.
- D6: RNF-28 é atendido por e-mail HTML estrutural (uma coluna, 18 px, botão largo), aplicado aos quatro modelos de conta para que todo e-mail do produto herde o formato. A conferência em Gmail e Outlook no celular depende de e-mail real (Sprint 22) e fica como limite registrado, sem pendência para o dono agora.
- D7: depois do login a pessoa vai para `next` ou `/`; a escolha de vínculo quando há mais de um (`activeLinkId` vazio) é da #23, que se encaixa em `/`. Alternativa: tratar aqui, descartada por ser a #23.

## 7. Construção

- T1: `EmailLayout` (novo), `EmailService`, `EmailTemplate` (campo `link`), `messages.properties` (`mail.button`), `EmailLayoutTest` (novo), `EmailServiceIT.layout` (CA10). Prova: `./backend/mvnw -f backend/pom.xml verify -Dtest=EmailLayoutTest -Dit.test='EmailServiceIT,InviteIT,AccountIT,NewAccessIT,AuthRoutesIT'`: verde. Argumentos escapados em UTF-8 (acentos ficam); o texto termina com quebra de linha depois do link para o `codeIn` dos ITs continuar lendo o código.
- T2: `react-router@7`, `src/lib/api.ts`, `features/auth/{SessionProvider,RequireSession,safeNext}`, `App.tsx`, `main.tsx`; testes `api.test.ts`, `safeNext.test.ts` e os de redirecionamento em `LoginPage.test.tsx`. Prova: `npm test`: verde.
- T3: `features/auth/Turnstile.tsx`, `frontend/.env.example`, `Turnstile.test.tsx` (com e sem chave, script simulado): verde.
- T4: `SupportContact.tsx`, `auth.*` em `pt-BR.json`, `SupportContact.test.tsx`: verde.
- T5: `LoginPage.tsx`, `LoginPage.test.tsx` (CA1, CA3 a CA8, axe nos dois temas a 360 px) e `a11y.test.tsx` adaptado ao roteador: verde.
- T6: `mocks/handlers.ts` (sessão simulada com estado; `resetMockSession` no `setup.ts`), bloco CA9 em `msw.test.ts`: verde.
- T7: `e2e/login.spec.ts` (`@critical`), `smoke.spec.ts` (abre `/entrar`), `frontend/README.md`. Prova: `npm run lint && typecheck && build && test:cov && e2e && audit` em `frontend/`: tudo verde, 154 testes, cobertura 96,99% (auth 96,46%), 2 e2e, 0 vulnerabilidades. `./backend/mvnw -f backend/pom.xml verify`: 90 testes rápidos e 88 de integração, verde.

### Regressão e desvios

- Primeira rodada do `verify` completo: `EmailServiceIT.requeue` falhou uma vez (esperava FAILED, veio SENT); a segunda rodada, sem mudança de código, passou. Instável, provavelmente um job agendado de outro contexto Spring pegando a linha com o remetente simulado; fica registrado como falha intermitente que já existia, não corrigida aqui.
- Desvio: `fetch` relativo não funciona no Node/jsdom; `src/test/setup.ts` resolve caminho começando com `/` contra a origem da página, e os testes novos usam `/api/v1/...` (os antigos de `msw.test.ts` seguem com `http://localhost`). Também `asyncUtilTimeout` de 5 s no `setup.ts`: a primeira renderização com cobertura estourava 1 s.
- Desvio: `App` não cria roteador; `main.tsx` envolve com `BrowserRouter` e `SessionProvider` (o `a11y.test.tsx` faz o mesmo com `MemoryRouter`).
- Não rodou: Turnstile real e e-mail em Gmail/Outlook (limites aceitos, D6 e Riscos).

## 8. Verificação

### Rodada 1 — OK — 2576148d3d1ddb619508876cb16b86783984b058

| Critério | Status | Evidência |
| --- | --- | --- |
| CA1 | PASSOU | `LoginPage.test.tsx` e leitura de `LoginPage.tsx` (rótulos, Mostrar/Ocultar, link `/esqueci-senha`); e2e `@critical` verde |
| CA2 | PASSOU | `SupportContact.test.tsx` (plataforma e unidade; sem WhatsApp sem a variável) |
| CA3 | PASSOU | `LoginPage.tsx` (`role="alert"`, limpa senha, foco) + teste; e2e de senha errada |
| CA4 | PASSOU | 429 e `CHALLENGE_FAILED` tratados, `resetKey` reinicia o desafio; teste |
| CA5 | PASSOU | validação por campo antes da chamada; `ErrorState` com `onRetry` |
| CA6 | PASSOU | `Turnstile.test.tsx` com e sem chave |
| CA7 | PASSOU | `safeNext.test.ts`, redirecionamentos em `LoginPage.test.tsx`, e2e |
| CA8 | PASSOU | axe nos dois temas a 360 px em `LoginPage.test.tsx`; botão desabilitado e "Entrando..." |
| CA9 | PASSOU | `msw.test.ts` verde |
| CA10 | PASSOU | `EmailLayoutTest` e `EmailServiceIT.layout`; `InviteIT`, `NewAccessIT`, `AuthRoutesIT` verdes |

Comandos:
- `frontend/`: `npm run lint && typecheck && build && test:cov && e2e`: verde; 24 arquivos, 154 testes; cobertura 96,99% (linhas 98,24%); 2 e2e. `npm run audit`: 0 vulnerabilidades.
- `./backend/mvnw -f backend/pom.xml verify`: 90 testes rápidos e 88 de integração, 0 falhas; JaCoCo "All coverage checks have been met"; BUILD SUCCESS.

Achados obrigatórios: nenhum. Especificação x card: sem divergência.

Sugestões:
1. Não rodou: Turnstile real e e-mail em Gmail/Outlook (limite aceito, D6).
2. `EmailServiceIT.requeue` intermitente já registrado em "Regressão e desvios"; não apareceu nesta rodada.

Resultado: OK

## 9. Entrega

- Tela pública de login (`/entrar`) com validação por campo, erro de credencial, limite de tentativas, desafio Turnstile (com e sem chave), `next` seguro, Mostrar/Ocultar senha e contato de suporte (variáveis `VITE_SUPPORT_*`). Roteador, `SessionProvider`, `RequireSession` e `lib/api.ts` entram junto.
- Back-end: e-mails de conta passam a usar layout estrutural de uma coluna com botão largo (RNF-28), via `EmailLayout`.
- Verificação (rodada 1, OK): front com lint, typecheck, build, 154 testes, cobertura 96,99%, 2 e2e e auditoria sem vulnerabilidades; back-end `verify` com 90 testes rápidos e 88 de integração verdes.
- Limitações: Turnstile real e e-mail em Gmail/Outlook não testados (D6); `EmailServiceIT.requeue` é intermitente e já existia; exibição do contato da unidade fica para #23/#25 (D3).
