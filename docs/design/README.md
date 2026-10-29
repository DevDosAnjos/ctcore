# Design do ctcore

O visual do sistema segue o **canvas de Design** (especificação 1.2). Esta pasta traz a referência para dentro do repositório, porque o canvas não fica acessível aos agentes.

- `canvas/`: o código de cada tela do canvas (`Tela.dc.html`), mais `indice.json` (arquivo, título, página, largura e altura de cada tela) e `ver.html` (abre uma tela no navegador).
- `prints/`: prints gerados das telas. Não vão para o Git.

As telas do canvas são **referência visual**, não código para copiar. Usam estilo escrito no próprio elemento e dados fictícios da "Academia Leão". O código do sistema reproduz o visual com os tokens e componentes listados abaixo.

## Como ver uma tela

De dentro de `frontend/` (primeira vez: `npx playwright install chromium`):

```bash
node scripts/canvas-shot.mjs Alunos AlunoDetalhes   # gera docs/design/prints/Alunos.png e AlunoDetalhes.png
node scripts/canvas-shot.mjs                         # gera todas
```

Ou, sem gerar arquivo: `python3 -m http.server 8765 --directory docs/design/canvas` e abrir `http://localhost:8765/ver.html?f=Alunos.dc.html`.

Quando uma issue cita `Tela.dc.html` em "Telas no canvas", o arquivo é `docs/design/canvas/Tela.dc.html`.

## Tokens

Ficam como variáveis no `frontend/src/index.css` (`:root` e `.dark`), com os nomes do shadcn/ui. Nenhum componente usa cor solta: só as variáveis.

| Uso | Claro | Escuro |
| --- | --- | --- |
| Fundo da página | `#F4F2ED` | `#141416` |
| Cartão, campo, janela | `#FFFFFF` | `#1D1D21` |
| Fundo suave (cabeçalho de tabela, rodapé de janela) | `#FAF8F4` | `#19191C` |
| Texto | `#1B1B1F` | `#ECE9E2` |
| Texto secundário | `#6B675F` | `#A8A398` |
| Texto apagado | `#8C877D` | `#8F8A80` |
| Borda | `#E3DFD6` | `#34343A` |
| Divisória interna | `#EEEAE2` | `#2A2A2F` |
| Borda de campo e botão secundário | `#D9D4C9` | `#45454C` |
| Destaque (marca do ctcore, ação de venda) | `#C8462C` | `#C8462C` |
| Texto de link e destaque | `#B23C24` | `#F08A70` |
| Botão principal | fundo `#1B1B1F`, texto `#FFFFFF` | fundo `#ECE9E2`, texto `#1D1D21` |
| Menu lateral | fundo `#17171A`, texto `#D8D4CB`, item ativo `#2B2B30` | igual |

Cores de situação (fundo / texto), usadas em etiquetas e avisos:

| Tom | Claro | Escuro | Exemplos |
| --- | --- | --- | --- |
| `ok` | `#E2EFE5` / `#1D5E34` | `#1E3626` / `#8FD3A3` | Ativo, Paga, Em dia, Confirmada |
| `warn` | `#FAEBC8` / `#7A4E00` | `#3D3018` / `#F0C674` | Vence hoje, Encerramento solicitado, Termo pendente |
| `danger` | `#F8DED8` / `#9C2516` | `#45231D` / `#F4A08F` | Em atraso, Bloqueada, E-mail com problema |
| `info` | `#E1E8F4` / `#23427A` | `#1E2A40` / `#A9C1EA` | Em conferência, Agendado, Bolsista, Substituição |
| `neutral` | `#ECE9E2` / `#4A4740` | `#2C2C31` / `#CFCAC0` | Pendente, Inativo, Encerrada, Cancelada |
| `acc` | `#FBE7E1` / `#9C2F18` | `#43241C` / `#F2A48F` | Aniversário hoje, destaque de venda |

O mapa completo de situação para tom fica num só lugar no código (ex.: `statusTone.ts`), nunca repetido em cada tela.

**Tipografia.** Títulos (h1 das páginas, títulos de janelas, números grandes): **Barlow Condensed** 600, caixa alta, `letter-spacing: 0.02em`. Página 38 px, janela 22 px, número de destaque 34 px. Texto: **IBM Plex Sans** 400/500/600, base 14 px, secundário 13 px, legendas 12 px. As fontes entram pelo pacote npm (`@fontsource`), não por link externo, para funcionar no app instalável e sem internet.

**Forma.** Cantos: cartão 12 px, campo e botão 8 px, janela 16 px, etiqueta 999 px. Altura: campo e botão 40 px, botão pequeno 32 px, botão grande 48 px, etiqueta 22 px. Sombra só em janela, painel lateral e menu suspenso. Espaçamento da página: 28 px no topo e 32 px nas laterais; 16 px no celular. Ícones: `lucide-react`, traço 2, 16 a 20 px.

## Componentes

Cada peça do canvas tem um componente em `frontend/src/components`. Tela nova usa estes componentes; estilo solto (classe de cor ou tamanho fora dos tokens) só dentro deles.

| No canvas | Componente | Observação |
| --- | --- | --- |
| Botão | `Button` | `primary`, `secondary`, `ghost`, `danger`, `dangerSolid`, `accent`, `whatsapp`; tamanhos `sm`, `md`, `lg`; com ícone |
| Etiqueta de situação | `StatusBadge` | recebe a situação e escolhe o tom pelo mapa |
| Selo (Bolsista, Novo, Termo pendente) | `Badge` | tom explícito |
| Cartão e seção com título | `Card`, `Section` | |
| Número de destaque | `Stat` | rótulo, valor, nota com tom |
| Campo com rótulo, dica e erro | `Field` + `Input`, `Select`, `Textarea` | contador de caracteres no `Textarea` |
| Caixa, opção e chave | `Checkbox`, `RadioCard`, `Switch` | `RadioCard` é a opção em cartão das janelas |
| Tabela | `DataTable` | cabeçalho suave, linhas de 52 px, valores alinhados à direita |
| Abas e filtros rápidos | `Tabs`, `QuickFilters` | filtro rápido com contador |
| Janela | `Modal`, `ConfirmDialog` | resumo do que vai acontecer; confirmação digitada nas ações graves |
| Painel lateral (ficha) | `SidePanel` | 680 px, abas no topo |
| Aviso na página | `Banner` | tons `info`, `warn`, `danger`, `ok` |
| Resumo do que vai acontecer | `SummaryBox` | lista com setas, usado nas janelas |
| Pessoa com avatar | `Person`, `Avatar` | iniciais com cor estável pelo nome |
| Etapas do assistente | `Stepper` | |
| Os quatro estados | `components/states` | carregando com esqueleto, vazio, erro com "Tentar de novo", sem acesso |
| Menu lateral, barra de cima, cabeçalho da página | `AppLayout`, `PageHeader` | menu lateral e barra de cima são partes do `AppLayout`; celular: barra de cima e abas embaixo |
| Barra e abas do portal | `PortalLayout` | cor principal da organização no topo |
| Moldura das telas de acesso | `AuthShell` | painel escuro da marca a partir de 768 px e formulário de 420 px; no celular só o formulário |

## Regras

1. Toda tela nova ou alterada segue a tela do canvas citada na issue, nos dois temas e a partir de 360 px.
2. O que aparece no canvas e é de uma sprint futura fica de fora; não se cria tela, botão ou dado que a issue não pede.
3. O canvas mostra o visual; a regra vem da especificação e da issue. Se os dois divergirem, vale a especificação, e a diferença fica registrada no arquivo do item.
4. Peça que o canvas usa e ainda não existe vira componente novo na pasta `components`, no mesmo PR, e entra na tabela acima.
5. Texto de tela continua nos arquivos de tradução.
