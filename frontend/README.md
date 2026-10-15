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

## Ponta a ponta

Os testes ficam em `e2e/` e rodam contra `npm run dev` (MSW ligado, só Chromium). Marque com `@critical` no título o fluxo que deve rodar em todo PR; os sem a marca rodam só no PR para `main`. O relatório sai em `playwright-report/`.

## Imagem Docker

`docker build -t ctcore-frontend frontend` (Node 24 gera o `dist`; nginx sem root serve em `8080`). `docker run --rm -p 8082:8080 ctcore-frontend`: `/` e rotas do app (ex.: `/qualquer`) devolvem `index.html`, `/assets/*` inexistente e `/api/*` devolvem 404. O CI constrói esta imagem quando a etapa `frontend` roda. Publicação: README da raiz.
