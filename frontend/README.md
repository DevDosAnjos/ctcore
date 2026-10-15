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
| Testes com cobertura | `npm test -- --coverage` |

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
| `query/QueryPage` | Filtros, busca, ordenação e tabela paginada, com o estado no endereço (`q`, `pagina`, `ordem`). Recebe `columns`, `fetchPage` (identidade estável) e `rowKey`. Mostra "busca sem resultado" com "Limpar filtros". `DataTable`, `Pagination` e `useQueryParams` também saem soltos. |
| `Modal` e `SidePanel` | Janela e painel lateral (tela cheia no celular) sobre Radix Dialog: foco preso, Esc e retorno do foco. |
| `ConfirmDialog` | Resumo, `reason` (`none`, `optional`, `required`) e `typedText` para confirmação digitada; impede duplo clique. |
| `Stepper` | Etapas com `valid` por etapa, "Voltar" e "Avançar". |
| `Card` e `CardGrid` | Cartão com título e ações; grade de 1 coluna a 360 px até 3. |
| `CharCounter` | Campo de texto com contador e limite `max`. |
| `fields/` (`CpfField`, `PhoneField`, `MoneyField`, `DateField`) | Máscara ao digitar; `onChange` entrega o valor limpo (dígitos, centavos, data ISO). Funções puras em `masks.ts`. |
