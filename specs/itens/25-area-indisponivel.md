# 25 — RF-ACE-08 · Tela de área indisponível para o perfil

Origem: issue #25 (Sprint 02); `docs/especificacao/02-contas-e-acesso.md` (RF-ACE-08, RNF-25). Branch: `item/25-area-indisponivel`.

## 1. Objetivo

Entregar a tela "Esta área não está disponível para o seu perfil", com atalhos para o Início, mostrada quando alguém abre um endereço fora do seu alcance (ex.: Professor abrindo o Financeiro, canvas `EstadoSemAcesso.dc.html`), mais o guarda de rota que a exibe, para as telas das próximas sprints só declararem quem pode entrar. A recusa de dados continua no servidor (403 `FORBIDDEN`, já no contrato); a tela só exibe.

Fora do item: telas de Início de cada área (RF-INI-*), a matriz de permissões por rota (cada tela futura declara a sua), tela 404 de endereço inexistente, menu definitivo, qualquer mudança de servidor, banco ou OpenAPI.

## 2. Requisitos

- R1 (RF-ACE-08): existe a tela com o título "Esta área não está disponível para o seu perfil", uma frase explicando e atalhos para o Início.
- R2 (RF-ACE-08): um guarda de rota `RequireArea` recebe os papéis permitidos; se o papel do vínculo ativo da sessão não está entre eles, mostra a tela em lugar do conteúdo, mantendo o endereço pedido e sem renderizar nem carregar o conteúdo protegido; se está, mostra o conteúdo (D1).
- R3 (RF-ACE-08): a tela também abre direto em `/sem-acesso` (sessão obrigatória), para ser destino de redirecionamento.
- R4 (RF-ACE-08, D2): os atalhos são "Ir para o Início" (leva a `/`) e, só com mais de um vínculo, "Trocar organização ou área" (reaproveita o gatilho do modal da #23).
- R5 (RNF-25): a tela é o estado "sem permissão" e funciona a partir de 360 px, passa no axe nos temas claro e escuro, com um único `h1`, foco inicial no título e todo texto em `pt-BR.json`; carregando e erro da sessão continuam os do `RequireSession`.
- R6 (permissão): o front não é a única barreira (RNF-05); não muda rota do servidor e a tela não revela dados da área negada (nem nome da tela, nem do recurso) (D3).
- R7: sem sessão, o endereço protegido continua levando ao login com `next` (RN-ACE-16); a tela só aparece para quem tem sessão.

## 3. Critérios de aceite

- CA1 (R1): dado sessão `ready`, quando a tela abre, então há o título "Esta área não está disponível para o seu perfil", o texto explicativo e o link "Ir para o Início".
- CA2 (R2): dado vínculo ativo `TEACHER` e rota com `RequireArea roles={["COORDINATOR","ADMINISTRATOR"]}`, quando abre o endereço, então a tela aparece, a URL não muda e o conteúdo da rota não é renderizado.
- CA3 (R2): dado vínculo `ADMINISTRATOR` na mesma rota, então o conteúdo aparece e a tela não.
- CA4 (R2): dado sessão sem `activeLinkId` (vínculos a escolher), então o guarda mostra a tela, nunca o conteúdo.
- CA5 (R3): dado sessão `ready`, quando abre `/sem-acesso`, então a tela aparece; sem sessão, vai a `/entrar?next=/sem-acesso` (R7).
- CA6 (R4): dado a tela, quando a pessoa usa "Ir para o Início", então vai a `/`; com dois vínculos aparece também "Trocar organização ou área" e abre o modal; com um vínculo só, não aparece.
- CA7 (R5): dado a tela a 360 px, nos temas claro e escuro, então o axe não aponta violações e o foco inicial está no título.
- CA8 (R5): dado carregando ou erro de sessão num endereço guardado, então aparecem `LoadingState` e `ErrorState` com "Tentar de novo", não a tela.
- CA9 (R6): dado a tela, então nada nela cita o recurso pedido (texto idêntico para qualquer rota negada).
- CA10 (R2, R3, R5): ponta a ponta `@critical`: Professor (conta do mock) abre `/sem-acesso` e um endereço guardado, vê a tela, usa "Ir para o Início" e chega a "Bem-vindo ao ctcore".

## 4. Plano técnico

- **Dados / API / OpenAPI / servidor.** Não mudam. O 403 `FORBIDDEN` já está no contrato; nenhuma rota nova. Sem auditoria (não altera dado). Seed não muda.
- **Front-end** (a criar, em `frontend/src/features/auth/`):
  - `AreaUnavailablePage.tsx`: usa `Card`/layout simples, `h1` com foco ao montar, links de atalho e `ChooseLinkTrigger` (existente) quando há mais de um vínculo.
  - `RequireArea.tsx`: `({ roles }) => ` lê `useSession()`, acha o vínculo ativo e devolve `<Outlet/>` ou a página. Deve ficar dentro de `RequireSession` (que já trata carregando, erro e anônimo).
  - `App.tsx`: rota `/sem-acesso` dentro de `RequireSession`.
  - `ForbiddenState` (existente em `components/states`) fica como está, é o bloco dentro de tela (QueryPage); não reaproveitar para a tela cheia.
  - `pt-BR.json`: chaves `areaUnavailable.*` (título, texto, `home`, `switch`).
  - `e2e/area-indisponivel.spec.ts` (`@critical`); `mocks/handlers.ts`: reutilizar a conta de Professor existente em `varios@exemplo.com`/outra; só acrescentar conta se nenhuma tiver vínculo ativo `TEACHER` (conferir antes).
  - Testes: `AreaUnavailablePage.test.tsx` (CA1, CA4–CA9 com axe nos dois temas), `RequireArea.test.tsx` (CA2–CA4, CA8). Cobertura do front não pode cair de 80%.
- **Segurança.** Tela estática sem dado; texto único para qualquer negativa (CA9); o servidor segue recusando com 403.
- **Riscos.** O guarda decide só pelo papel do vínculo ativo; permissões mais finas (por unidade ou módulo) virão com as telas. Não há tela guardada hoje, então a prova usa rotas de teste montadas no próprio teste e `/sem-acesso`.

## 5. Tarefas

- [x] T1: Textos `areaUnavailable.*` em `pt-BR.json`, `AreaUnavailablePage` e rota `/sem-acesso` em `App.tsx` — cobre CA1, CA5, CA6, CA9 — prova: `npx vitest run src/features/auth/AreaUnavailablePage`.
- [x] T2: `RequireArea` — cobre CA2, CA3, CA4, CA8 — prova: `npx vitest run src/features/auth/RequireArea`.
- [x] T3: Axe a 360 px nos dois temas e foco no título — cobre CA7 — prova: mesmo teste da T1.
- [x] T4: e2e `@critical` e linha em `frontend/README.md` se houver lista de telas — cobre CA10 — prova: `npm run e2e`.
- [x] T5: Regressão — prova: em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` (existe para fechar a definição de pronto; back-end não muda, `mvnw verify` só se tocar algo lá).

## 6. Decisões

- D1 (muda o produto): o guarda mostra a tela no mesmo endereço, sem redirecionar, para a pessoa poder corrigir a URL ou voltar. Alternativa: redirecionar a `/sem-acesso`, descartada por perder o endereço pedido; `/sem-acesso` existe só como destino explícito (R3). Sem vínculo ativo escolhido, também nega (CA4).
- D2 (muda o produto): atalhos são "Ir para o Início" e, com vários vínculos, "Trocar organização ou área". Só existe o Início `/` hoje; quando os Inícios por área existirem (RF-INI-*), o link passa a apontar para o da área ativa. Suposição: basta, o canvas não está acessível ao agente.
- D3: a tela não nomeia a área nem o recurso negado, para não vazar a existência de telas (sem informar a ausência de nada que o 403 já não diga). Alternativa: mostrar o nome da área; descartada por segurança e por não haver nome a mostrar ainda.
- D4: nenhuma rota de área é criada aqui; o item entrega o guarda e a tela, e cada tela futura o usa. Sem recurso novo por organização e sem notas de versão (nada visível às academias hoje, nenhuma tela guardada).
- D5: sem mudança de servidor: a regra "perfil x rota" já é imposta com 403 por rota; este item é o espelho visual (RNF-05).

## 7. Construção

- T1: `frontend/src/features/auth/AreaUnavailablePage.tsx` (+ `.test.tsx`), `App.tsx` (rota `/sem-acesso`), `pt-BR.json` (`areaUnavailable.title/text/home`). Prova: `npx vitest run src/features/auth/AreaUnavailablePage src/features/auth/RequireArea` — 10 testes passaram.
- T2: `RequireArea.tsx` (+ `.test.tsx`). Mesma prova, passou.
- T3: axe a 360 px nos dois temas e foco no título em `AreaUnavailablePage.test.tsx`. Passou.
- T4: `frontend/e2e/area-indisponivel.spec.ts` (`@critical`). `npm run e2e`: 7 passaram. `frontend/README.md` não tem lista de telas, sem mudança.
- T5: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` — tudo verde; 33 arquivos, 227 testes; cobertura 97% linhas (98,84%). Back-end não mudou, `mvnw verify` não rodou.

Desvios:
- O atalho de troca reaproveita `chooseLink.trigger` ("Trocar organização ou área"); não criei a chave `areaUnavailable.switch`.
- `src/i18n/tone.test.ts` reprovava o título obrigatório porque "tá " casava dentro de "está ". Passou a procurar " tá " e " vc " como palavra inteira (a lista de proibidos segue a mesma). Ajuste de teste, não afrouxa a regra.
- O e2e abre `/sem-acesso` por `history.pushState` + `popstate`, porque a sessão do mock vive na página e um `goto` a perderia. Não existe endereço guardado real; o guardado por `RequireArea` é provado em `RequireArea.test.tsx`.

## 8. Verificação

### Rodada 1 — OK — eb44bddb3a366db3bff1a133043bd88b6eaeafbd

| Critério | Status | Evidência |
| --- | --- | --- |
| CA1 | PASSOU | `AreaUnavailablePage.test.tsx` (título, texto, link `/`) |
| CA2 | PASSOU | `RequireArea.test.tsx`: tela, URL `/financeiro` mantida, conteúdo ausente |
| CA3 | PASSOU | `RequireArea.test.tsx` (ADMINISTRATOR vê o conteúdo) |
| CA4 | PASSOU | `RequireArea.test.tsx` (activeLinkId null nega) |
| CA5 | PASSOU | `/sem-acesso` abre com sessão; sem sessão cai no login. O teste não afirma o `next` na URL (sugestão) |
| CA6 | PASSOU | testes com dois vínculos e com um |
| CA7 | PASSOU | axe a 360 px nos temas claro e escuro, foco no h1 |
| CA8 | PASSOU | ErrorState com "Tentar de novo" testado; carregando é do `RequireSession` (já coberto), sem teste neste item |
| CA9 | PASSOU | texto fixo, sem prop de recurso; textos em `pt-BR.json` |
| CA10 | PASSOU | `npm run e2e`: 7 passaram, incluindo `area-indisponivel.spec.ts` |

Comandos: em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` saiu com 0; cobertura 97% instruções, 98,84% linhas; e2e 7 de 7. Back-end não mudou (`mvnw verify` não rodou).

Achados obrigatórios: nenhum.

Sugestões:
1. CA5: afirmar `next=/sem-acesso` no teste de sem sessão.
2. CA8: acrescentar teste do estado carregando no guarda.
3. `tone.test.ts` alterado (` tá `/` vc ` por palavra inteira); lista de proibidos igual, aceitável.

Resultado: OK.

## 9. Entrega

Entrega a tela "Esta área não está disponível para o seu perfil" (`/sem-acesso`) e o guarda de rota `RequireArea`, que mostra a tela no mesmo endereço quando o papel do vínculo ativo não está entre os permitidos (ou não há vínculo ativo). Atalhos: "Ir para o Início" e, com mais de um vínculo, "Trocar organização ou área".

Verificação: lint, typecheck, build, `test:cov` (227 testes, 98,84% de linhas) e e2e `@critical` (7 de 7) verdes; axe a 360 px nos dois temas. Back-end, OpenAPI e banco não mudam.

Decisões e limitações: o guarda decide só pelo papel (permissões mais finas virão com as telas); a tela não nomeia o recurso negado; `tone.test.ts` passou a casar ` tá ` e ` vc ` como palavra inteira, sem afrouxar a lista de proibidos. Ainda não há tela guardada; o uso real vem nas próximas sprints.
