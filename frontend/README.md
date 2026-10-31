# frontend

Aplicação web do ctcore: TypeScript, React, Vite, shadcn/ui, Tailwind CSS, Radix e MSW. Os handlers MSW seguem o OpenAPI de `../docs/`. A API que ela consome é a de `../backend/`.

Precisa do Node 24 e do npm. Rode os comandos de dentro de `frontend/`.

| Para quê | Comando |
| --- | --- |
| Instalar as dependências | `npm ci` |
| Subir em desenvolvimento (http://localhost:5173) | `npm run dev` |
| Construir | `npm run build` |
| Lint (inclui a regra de texto literal) | `npm run lint` |
| Conferir tipos | `npm run typecheck` |
| Testes | `npm test` |
| Testes com cobertura (falha abaixo de 80% de linhas; 100% em `src/features/{billing,payments,subscription,deactivation}`) | `npm run test:cov` |
| Ponta a ponta, só `@critical` (Playwright; 1ª vez: `npx playwright install chromium`) | `npm run e2e` |
| Ponta a ponta, suíte completa | `npm run e2e:full` |
| Teste visual, no container do CI (gera/atualiza as referências com `-- --update-snapshots`) | `npm run test:visual:docker` |
| Teste visual direto (só vale no container do CI; ver "Teste visual") | `npm run test:visual` |
| Lado a lado sistema x canvas em `docs/design/prints/lado-a-lado/index.html` | `npm run visual:canvas` |
| Dependências com falha conhecida (alta ou crítica) | `npm run audit` |

## Como o projeto funciona

- **Proxy:** em desenvolvimento, `/api` vai para `http://localhost:8080` (o back-end local).
- **MSW:** em desenvolvimento o MSW liga sozinho; para desligar, `VITE_USE_MSW=false npm run dev`. Nos testes ele sempre liga e rota sem handler falha o teste. Os handlers ficam em `src/mocks/handlers.ts`, com o caminho como está no OpenAPI; `src/mocks/msw.test.ts` confere cada um contra `docs/openapi.yaml`.
- **Tema:** claro e escuro, com os tokens como variáveis CSS em `src/index.css`. Sem escolha salva, segue o aparelho; o botão do cabeçalho troca e guarda a escolha neste navegador. Um script em `index.html` aplica o tema antes da pintura.
- **Textos:** todo texto de tela está em `src/i18n/pt-BR.json` e entra por `useTranslation`. `npm run lint` falha com texto literal em JSX e em `aria-label`, `title`, `placeholder` e `alt`. Fora de produção, chave de tradução inexistente lança erro.
- **Mensagens de erro:** ficam em `errors.<nome>` com três partes: `aconteceu`, `alterado` e `fazer`.
- **Testes:** Vitest, Testing Library, MSW e axe (contraste dos tokens por teste de razão, pois o jsdom não mede cor). `src/test/screen-states.test.tsx` é o modelo dos quatro estados de tela.

## Componentes

Ficam em `src/components/`; todo texto padrão vem de `pt-BR.json` e pode ser sobrescrito por propriedade. A página `src/test/Showcase.tsx` (só para teste, usada no axe) mostra todos juntos.

| Componente | Uso |
| --- | --- |
| `layout/AppLayout` | Menu lateral (gaveta no celular) e topo com tema. Recebe `groups` e `activeId`; itens são links comuns. |
| `states/` (`LoadingState`, `EmptyState`, `ErrorState`, `ForbiddenState`) | Os quatro estados de tela. `ErrorState` recebe `onRetry`; `EmptyState` recebe a ação principal. |
| `query/QueryPage` | Filtros, busca, ordenação e tabela paginada, com o estado no endereço (`q`, `pagina`, `ordem`, `tamanho`). `tamanho` é 25, 50 ou 100, como o `size` do contrato (padrão 25, fora do endereço; valor inválido vira 25). Recebe `columns`, `fetchPage` (identidade estável) e `rowKey`; mostra o total e o seletor de itens por página. Mostra "busca sem resultado" com "Limpar filtros". `DataTable`, `Pagination` e `useQueryParams` também saem soltos. |
| `Modal` e `SidePanel` | Janela e painel lateral (tela cheia no celular) sobre Radix Dialog: foco preso, Esc e retorno do foco. |
| `ConfirmDialog` | Resumo, `reason` (`none`, `optional`, `required`) e `typedText` para confirmação digitada; impede duplo clique. Fechar de qualquer jeito (Cancelar, Esc, X) limpa motivo e texto. |
| `Stepper` | Etapas com `valid` por etapa, "Voltar" e "Avançar". |
| `Card` e `CardGrid` | Cartão com título e ações; grade de 1 coluna a 360 px até 3. |
| `CharCounter` | Campo de texto com contador e limite `max`. |
| `fields/` (`CpfField`, `PhoneField`, `MoneyField`, `DateField`) | Máscara ao digitar; `onChange` entrega o valor limpo (dígitos, centavos, data ISO). Telefone colado com `+55` perde o código do país; valor colado em `MoneyField` é lido em reais (`R$ 1.250` vira 125000 centavos). Funções puras em `masks.ts`. |

## Formatos brasileiros, datas relativas e WhatsApp

- **Formatadores** (`src/lib/format.ts`), todos no fuso recebido (padrão `America/Sao_Paulo`): `formatDate` (`08/10/2026`), `formatTime` (`19h30`), `formatDateTime`, `formatCurrency(centavos)` (`R$ 1.250,00`), `formatPhoneDisplay` (`(11) 91234-5678`; número inválido volta o texto original) e `relativeDay`. Ex.: `formatDateTime(iso, "America/Manaus")`. Data de calendário do contrato (`date`, `AAAA-MM-DD`, ex.: vencimento) é o mesmo dia em qualquer fuso e sai sem hora (`isCalendarDate`).
- **`RelativeDate`**: `<RelativeDate value={iso} timeZone="America/Sao_Paulo" />` mostra "hoje", "ontem" e "há N dias" (até 30; depois, a data). A data e a hora exatas aparecem ao passar o mouse, focar ou tocar; Esc fecha. Valor vazio ou inválido mostra "—".
- **`WhatsAppButton`** (`components/whatsapp/`): `<WhatsAppButton phone="(11) 91234-5678" message="Olá" />` abre `wa.me` em nova aba; `groupUrl="https://chat.whatsapp.com/..."` abre o grupo. Não renderiza sem destino válido nem quando `<WhatsAppProvider enabled={false}>` envolve a tela (padrão: ligado).

## Login e sessão

Rotas em `src/App.tsx` (`react-router`); `SessionProvider` carrega `GET /auth/session` e `RequireSession` manda quem não tem sessão para `/entrar?next=<caminho>`. `src/lib/api.ts` chama `/api/v1` e manda `X-XSRF-TOKEN` nos métodos que alteram dado. Variáveis (veja `.env.example`): `VITE_SUPPORT_EMAIL` e `VITE_SUPPORT_WHATSAPP` (suporte mostrado antes do login), `VITE_TURNSTILE_SITE_KEY` (sem ela, nenhum script é carregado). No `npm run dev` o MSW aceita qualquer senha, menos `errada` (401) e `limite` (429). Convites simulados em `/convite/<código>`: `valido` (conta nova), `existente` (já tem senha: aceita qualquer senha menos `errada` e `limite`), `vencido`, `usado` e `cancelado`; outro código é desconhecido. Recuperação de senha em `/redefinir-senha/<código>`: `valido` (aceita senha forte), `fraca` e `vazada` (sempre 400), `vencido` ou outro código (410); em `/esqueci-senha`, o e-mail `limite@exemplo.com` dá 429. Escolha de organização e área (RF-ACE-05): `ChooseLinkGate` (em `RequireSession`) abre o modal sozinho com mais de um vínculo e nenhum ativo, e `ChooseLinkTrigger` (cabeçalho do `Home`) o reabre; no MSW, entrar com `varios@exemplo.com` dá três vínculos (Gestão e Portal em duas organizações) sem vínculo ativo, e `PUT /auth/session/link` escolhe um deles.

Duas etapas (#26): no MSW, `duas@exemplo.com` pede o segundo passo (código `123456` ou de reserva `RESERVA001`; `vencida@exemplo.com` dá bilhete vencido) e `coord@exemplo.com` é Coordenador sem as duas etapas e cai em `/configurar-duas-etapas`. Componentes reutilizáveis para a #36: `TwoFactorSetupDialog`, `BackupCodesDialog` e `ReauthDialog` (em `features/auth`).

## Ponta a ponta

Os testes ficam em `e2e/` e rodam contra `npm run dev` (MSW ligado, só Chromium). Marque com `@critical` no título o fluxo que deve rodar em todo PR; os sem a marca rodam só no PR para `main`. O relatório sai em `playwright-report/`.

**Trocar a resposta do MSW num teste:** importe `test` e `expect` de `e2e/support/msw` e use `msw.override({ método, caminho do contrato, status, corpo })`. Só vale naquele teste (a fixture chama `resetHandlers` ao fim) e não mexe em `src/mocks/handlers.ts`. O gancho (`window.__mswOverride`) existe só em `npm run dev` com MSW ligado. Registre **antes do login**: o login e o estado do mock vivem na página e se perdem em recarga; antes do `page.goto` vale já para a primeira leitura (ex.: erro de `/units/reachable`).

```ts
import { expect, test } from "./support/msw";

test("sino 99+", async ({ page, msw }) => {
  await msw.override({ method: "get", path: "/notifications/unread-count", status: 200, body: { count: 150 } });
  await page.goto("/entrar");
  // ...login...
  await expect(page.getByRole("button", { name: /99\+/ })).toBeVisible();
});
```

## Imagem Docker

`docker build -t ctcore-frontend frontend` (Node 24 gera o `dist`; nginx sem root serve em `8080`). `docker run --rm -p 8082:8080 ctcore-frontend`: `/` e rotas do app (ex.: `/qualquer`) devolvem `index.html`, `/assets/*` inexistente e `/api/*` devolvem 404. O CI constrói esta imagem quando a etapa `frontend` roda. Publicação: README da raiz.

## Teste visual

`visual/` guarda o teste que abre as telas de `visual/casos.ts` com os dados do MSW, em 1440 × 1000 e 390 × 844, nos temas claro e escuro, e compara com as imagens de `visual/referencias/` (`toHaveScreenshot`, tolerância de 0,1% dos pixels). Relógio fixo em 29/10/2026 12:00 (America/Sao_Paulo). É uma configuração à parte (`playwright.visual.config.ts`): `e2e` e `e2e:full` não o executam. O CI roda o job `visual` em todo PR que mexe em `frontend/`.

- **Rode no container:** `npm run test:visual:docker` usa a mesma imagem do CI (`mcr.microsoft.com/playwright:v<versão>-noble`). Rodar `npm run test:visual` direto na sua máquina dá fontes e serrilhado diferentes e reprova sem motivo; não é o caminho.
- **Quando atualizar as referências:** só quando a issue pede a mudança visual. Rode `npm run test:visual:docker -- --update-snapshots`, **olhe cada imagem alterada** (o diff do PR mostra) e commite. Nunca atualize só para o teste passar.
- **Tela nova:** acrescente um caso em `visual/casos.ts` (`nome`, `rota`, `perfil`, `canvas`); `visual/casos.test.ts` confere a rota e a tela do canvas.
- **Capture sempre em pt-BR:** o Chromium completo (`channel: "chromium"`) com `--lang=pt-BR` e `LANG`/`LANGUAGE` no container mostra "dd/mm/aaaa" e "Escolher arquivo"; o headless shell ignora o idioma. `visual/idioma.visual.ts` reprova se o navegador voltar ao inglês, e `scripts/canvas-shot.mjs` usa a mesma configuração.
- **Revisão com o canvas:** `npm run visual:canvas` gera `docs/design/prints/lado-a-lado/index.html` (pasta fora do Git) com o print do sistema ao lado do print do canvas, a partir das referências.
- **Comparação automática com o canvas (#435):** `npm run visual:canvas` põe a referência do sistema (`visual/referencias`, pt-BR) ao lado do print do canvas (`scripts/canvas-shot.mjs`) em `docs/design/prints/lado-a-lado/index.html` (pasta fora do Git) e grava `resultado.json` (tela, largura, tema, diferença, limite, aprovada). A métrica (`visual/comparar.mjs`) reduz as duas imagens a blocos de 32 px de cor média e conta a fração de blocos de cor diferente, na altura comum; por isso texto e dados do MSW não pesam, mas estrutura, blocos, fundos e tamanhos pesam. Compara 1440 (claro e, com `canvasEscuro`, escuro) e 390 (só com `canvasCelular`). O limite padrão (`LIMITE_PADRAO`) vale para todos; um caso que precise de mais traz `limiteCanvas: { valor, motivo }` em `casos.ts`. Sem opção, é relatório (sai 0); com `-- --exige`, sai 1 se alguma tela reprovar. O CI roda em modo relatório e publica o artefato `canvas-resultado`. Texto, rótulos e tom continuam para o olho humano e para os testes de texto.
- **Telas do canvas sem caso:** `semCaso` em `visual/casos.ts` lista, com o motivo, cada tela de gestão e portal (páginas "Desktop · Gestão", "Celular · Gestão", "Desktop · Portal do aluno" e "Celular · Portal do aluno") que não tem caso; `visual/casos.test.ts` reprova tela dessas páginas fora de um caso e fora da lista. Ao construir uma tela, tire-a de `semCaso` e acrescente o caso.
