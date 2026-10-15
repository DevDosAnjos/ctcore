# 9 — Campos e formatos brasileiros, datas relativas e botão de WhatsApp

Origem: issue #9 (Sprint 00 · Fundação), RNF-23 e RNF-42 em `docs/especificacao/00-visao-geral.md`. Branch atual: `item/9-componentes-transversais`.

## 1. Objetivo

Entregar o que as telas das próximas sprints usarão para mostrar dados no padrão brasileiro e falar por WhatsApp: formatadores de data, hora, moeda e telefone no fuso da unidade; o componente de data relativa ("hoje", "ontem", "há 3 dias") com a data exata ao passar o mouse ou tocar; e o componente único do botão de WhatsApp (link `wa.me`, sem API) que some quando o WhatsApp está desligado pela plataforma.

Fora do escopo: os campos de digitação de data, moeda, telefone e CPF (já entregues na #8, `components/fields/`); telas e rotas de negócio; o endpoint que informa se o WhatsApp está ligado e o fuso da unidade (cada tela os passa; ligação ao servidor fica para os itens de configuração); mensagens prontas de cada tela (cobrar, lembrete etc.), que pertencem aos itens que usam o botão.

## 2. Requisitos

- R1: formatadores de exibição, no pt-BR e no fuso recebido (padrão `America/Sao_Paulo`): data `08/10/2026`, hora `19h30`, data e hora juntas, moeda `R$ 1.250,00` a partir de centavos inteiros e telefone com DDD `(11) 91234-5678` a partir de dígitos (RNF-23, D1).
- R2: a data e a hora exibidas usam o fuso da unidade, não o do navegador: o mesmo instante UTC mostra dia e hora diferentes conforme o fuso (RNF-23, D2).
- R3: o componente de data relativa mostra "hoje", "ontem" e "há N dias" contando dias de calendário no fuso da unidade (RNF-23, D3).
- R4: o componente de data relativa mostra a data e a hora exatas ao passar o mouse, ao focar pelo teclado e ao tocar, sem depender só do mouse (RNF-23, RNF-20, D4).
- R5: o botão de WhatsApp é um único componente que recebe o destino (telefone ou link de grupo) e a mensagem pronta, e gera um link `https://wa.me/55<telefone>?text=<mensagem codificada>` ou abre o link do grupo, em nova aba, sem API (RNF-42, D5).
- R6: com o WhatsApp desligado pela plataforma, o botão não renderiza nada e o resto da tela não muda; sem destino válido, também não renderiza (RNF-42, D6).
- R7: o botão de WhatsApp tem nome acessível, alvo de toque de 44 px e texto vindo de `pt-BR.json` (AGENTS.md, RNF-20).
- R8: os componentes funcionam a partir de 360 px e passam no axe nos dois temas (issue, RNF-19).
- R9: `frontend/README.md` documenta os formatadores e os dois componentes.

## 3. Critérios de aceite

- CA1 (R1): dado o instante `2026-10-08T22:30:00Z` no fuso `America/Sao_Paulo`, quando formatado, então data é `08/10/2026`, hora é `19h30` e data e hora é `08/10/2026 19h30`; minutos com um dígito saem com zero (`19h05`) e hora cheia sai `19h00`.
- CA2 (R1): dado `125000` centavos, quando formatado como moeda, então sai `R$ 1.250,00`; `0` sai `R$ 0,00`; `5` sai `R$ 0,05`.
- CA3 (R1): dados `11912345678` e `1112345678`, quando formatados como telefone, então saem `(11) 91234-5678` e `(11) 1234-5678`; entrada com máscara ou espaços é aceita; número inválido volta o texto original sem erro.
- CA4 (R2): dado `2026-10-09T02:30:00Z`, quando formatado em `America/Sao_Paulo` e em `America/Manaus`, então a data é `08/10/2026` nos dois e a hora é `23h30` e `22h30`; em `Asia/Tokyo` a data muda para `09/10/2026` (prova que não usa o fuso da máquina).
- CA5 (R2): dada a meia-noite local de Brasília (`2026-10-08T03:00:00Z`), quando formatada, então sai `08/10/2026 00h00`, não o dia anterior.
- CA6 (R3): dado "agora" fixo, quando a data é do mesmo dia local, de ontem local, de 3 dias antes ou de 30 dias antes, então o componente mostra "hoje", "ontem", "há 3 dias" e "há 30 dias"; um instante às 23h50 de ontem visto às 00h10 de hoje mostra "ontem" (dia de calendário, não 24 horas).
- CA7 (R3): dada uma data com mais de 30 dias ou uma data futura, quando renderizada, então mostra a data exata `dd/mm/aaaa` (D3).
- CA8 (R3): dada uma data inválida ou vazia, quando renderizada, então não quebra e mostra um traço "—".
- CA9 (R4): dado o componente, quando a pessoa passa o mouse, foca por Tab ou toca, então a data e a hora exatas (`08/10/2026 19h30`) aparecem e ficam ligadas ao elemento por `aria-describedby`; ao sair o mouse, perder o foco, tocar de novo ou apertar Esc, somem (D4).
- CA10 (R4): dado o componente, quando renderizado, então é um `<time>` com `dateTime` em ISO dentro de um botão alcançável por teclado.
- CA11 (R5): dado o telefone `(11) 91234-5678` e a mensagem "Olá, tudo bem?", quando o botão é renderizado, então o link é `https://wa.me/5511912345678?text=Ol%C3%A1%2C%20tudo%20bem%3F`, com `target="_blank"` e `rel="noopener noreferrer"`.
- CA12 (R5): dado um link de grupo `https://chat.whatsapp.com/abc`, quando o botão é renderizado, então o link é esse endereço e a mensagem é ignorada; link de grupo de outro domínio não é aceito (D5).
- CA13 (R6): dado o provedor de WhatsApp desligado, quando o botão é renderizado numa tela com outros elementos, então nada dele aparece e os outros elementos continuam iguais; com o provedor ligado (padrão) o botão aparece.
- CA14 (R6): dado telefone vazio, curto demais ou destino ausente, quando o botão é renderizado, então não aparece e não há erro.
- CA15 (R7): dado o botão, quando lido por leitor de tela, então tem nome acessível vindo de `pt-BR.json` (ex.: "Abrir conversa no WhatsApp"), foco visível, e as classes de alvo `min-h-11 min-w-11`; o lint não acusa texto literal.
- CA16 (R8): dada uma demonstração dos dois componentes nos temas claro e escuro, quando o axe roda, então não há violação; em 360 px não há largura fixa (conferido por classes, D7).
- CA17 (R9): dado `frontend/README.md`, quando lido, então lista os formatadores, `RelativeDate` e `WhatsAppButton` com um exemplo de uso de cada.
- CA18 (R1 a R7): dados RNF-23 e RNF-42, quando se busca nos testes, então cada código aparece em ao menos um teste (descrição do teste cita o código).

## 4. Plano técnico

- Dados e API: não mudam. `docs/openapi.yaml` não muda; não há rota nova (a configuração "WhatsApp ligado" será um campo de itens futuros; aqui entra só o contexto do front, D6).
- Interface: a criar `frontend/src/lib/format.ts` (`formatDate`, `formatTime`, `formatDateTime`, `formatCurrency`, `formatPhoneDisplay`, `relativeDay`, com `Intl.DateTimeFormat` e `timeZone`), `frontend/src/components/RelativeDate.tsx`, `frontend/src/components/whatsapp/` com `WhatsAppButton.tsx` e `WhatsAppContext.tsx` (contexto `enabled`, padrão ligado) e `index.ts`. Testes ao lado: `format.test.ts`, `RelativeDate.test.tsx`, `WhatsAppButton.test.tsx`.
- Reuso: `onlyDigits`, `formatPhone`, `isValidPhone` e `formatMoney` de `components/fields/masks.ts` (o formatador de exibição reaproveita em vez de duplicar; a restrição "masks não vira formatador de exibição" da #8 é cumprida porque a exibição fica em `lib/format.ts`); `Button` e `cn`; `axeViolations` de `src/test/axe.ts`; `setDevicePrefersDark`; `ThemeProvider`; chaves e regra `i18next/no-literal-string`; `tone.test.ts` valida o tom dos textos novos.
- Arquivos a alterar (existem): `frontend/src/i18n/pt-BR.json` (chaves `format.*`, `whatsapp.*`), `frontend/src/test/Showcase.tsx` e `frontend/src/test/a11y.test.tsx` (incluir os dois componentes), `frontend/README.md`. Sem dependência nova: tudo com `Intl` do navegador.
- Segurança: o telefone vai no link `wa.me` (é o objetivo do item), mas não em log nem em endereço da aplicação; a mensagem é codificada com `encodeURIComponent`; link de grupo só do domínio `chat.whatsapp.com` e `https` (evita `javascript:` e destino externo arbitrário); `rel="noopener noreferrer"`. O botão que some não é barreira de permissão: o servidor continua recusando (AGENTS.md).
- Riscos: (1) o jsdom/Node precisa de dados de fuso completos (ICU): conferir que `Intl` com `America/Manaus` e `Asia/Tokyo` funciona no ambiente de teste. (2) Saída de `Intl` pode trazer espaço não separável em moeda; os testes comparam com o texto normalizado definido por `formatCurrency`. (3) "Hoje" depende do relógio: o componente recebe `now` opcional e os testes usam tempo falso. (4) O telefone de entrada pode vir de dados antigos mal formatados; sem validação passa o texto original (CA3).

## 5. Tarefas

- [x] T1: criar `lib/format.ts` (data, hora, data e hora, moeda, telefone, dia relativo, tudo no fuso recebido) — cobre CA1 a CA5, CA18 — prova: `format.test.ts` (inclui `America/Manaus` e `Asia/Tokyo`).
- [x] T2: criar `RelativeDate` com data exata ao passar o mouse, focar ou tocar, e as chaves `format.*` em `pt-BR.json` — cobre CA6 a CA10, CA18 — prova: `RelativeDate.test.tsx` (tempo falso, `userEvent` com hover, Tab, clique e Esc).
- [x] T3: criar `WhatsAppContext` e `WhatsAppButton` (wa.me, grupo, some se desligado ou sem destino) e as chaves `whatsapp.*` — cobre CA11 a CA15, CA18 — prova: `WhatsAppButton.test.tsx`, `npm run lint`, `tone.test.ts`.
- [x] T4: incluir os dois componentes na `Showcase`, conferir axe nos dois temas e atualizar `frontend/README.md` — cobre CA16, CA17 — prova: `a11y.test.tsx` e `npm run build && npm run lint && npm run typecheck && npm test -- --coverage` verdes, cobertura mínima de 80%.

## 6. Decisões

- D1: moeda e telefone de exibição ficam em `lib/format.ts` e reaproveitam `masks.ts`; a #8 já entregou os campos de digitação, então este item não recria campos (a issue lista só o que falta). Alternativa: duplicar a lógica, descartada.
- D2: o fuso vem por propriedade (`timeZone`) com padrão `America/Sao_Paulo` (padrão da unidade em `01-organizacao-e-isolamento.md`); ainda não há dado da unidade no front, então cada tela passa o fuso quando o item dela o tiver. Alternativa: contexto global de fuso, descartada por não haver fonte do valor ainda.
- D3 (muda o comportamento): "há N dias" vale de 2 a 30 dias, contando dias de calendário no fuso da unidade; acima de 30 dias e datas futuras mostram a data exata. A especificação só dá os exemplos "hoje", "ontem", "há 3 dias"; o limite de 30 é suposição. Se estiver errada, só muda uma constante e o teste do limite.
- D4: "tocar" é tratado com um botão que alterna a data exata (aparece e some ao tocar), além de hover e foco, em vez de depender do atributo `title`, que não funciona em toque nem leitor de tela. Sem biblioteca de tooltip nova.
- D5: o botão aceita `phone` (dígitos ou com máscara, DDD de 2 dígitos, prefixo 55 acrescentado se faltar) ou `groupUrl`; se vier o grupo, ele vale e a mensagem é ignorada (o WhatsApp não aceita texto pronto em convite de grupo). Só `https://chat.whatsapp.com/` é aceito. Telefone só do Brasil, como na #8 (D5).
- D6 (muda o comportamento): "WhatsApp desligado pela plataforma" chega ao front por um contexto React (`WhatsAppProvider enabled`), com padrão ligado; a ligação ao dado real da plataforma é feita pelo primeiro item que expuser essa configuração. Motivo: não há rota nem campo no OpenAPI ainda e a regra desta issue é só o comportamento do componente. Alternativa: criar a rota agora, descartada por ser alteração de contrato fora do pedido.
- D7: como na #7 (D8) e na #8 (D6), axe e 360 px são conferidos em jsdom, com `color-contrast` desligado; navegador real fica para a #10.
- Suposição: o componente de WhatsApp mostra ícone e texto curto (nome acessível sempre presente); o desenho final de cada tela (botão primário, secundário, só ícone) é escolha da tela, por isso aceita `variant` do `Button` existente, sem outra variação.
- Suposição: `19h30` é sempre 24 horas, sem segundos; a hora cheia sai `19h00`.
- Suposição: o branch `item/9-componentes-transversais` já criado é mantido, embora o slug não siga o título da issue.
- Suposição: "Esteira de CI verde" do "Pronto quando" vale quando a #10 existir, como nas #7 e #8; AGENTS.md só muda se o README mudar o modo de rodar (não muda).

## Construção

- T1 — arquivos: `frontend/src/lib/format.ts`, `format.test.ts` — prova: `npx vitest run` → passa (inclui Manaus e Tokyo)
- T2 — arquivos: `frontend/src/components/RelativeDate.tsx`, `RelativeDate.test.tsx`, `i18n/pt-BR.json` (`format.*`) — prova: `npx vitest run` → passa (hover, Tab, clique, Esc)
- T3 — arquivos: `frontend/src/components/whatsapp/{WhatsAppContext.tsx,WhatsAppButton.tsx,index.ts}`, `WhatsAppButton.test.tsx`, `pt-BR.json` (`whatsapp.*`) — prova: `npm run lint` limpo; `tone.test.ts` passa
- T4 — arquivos: `frontend/src/test/Showcase.tsx`, `frontend/README.md` — prova: `a11y.test.tsx` passa nos dois temas; `npm run build`, `lint`, `typecheck` verdes; `npx vitest run --coverage` → 19 arquivos, 87 testes passam, cobertura global 95,25%
Regressão: `npx vitest run --coverage` → 87 de 87 passam.
Desvios: `onBlur` também desliga o estado de toque (para a data exata sumir ao perder o foco, CA9). Telefone com prefixo 55 e 12-13 dígitos tem o 55 removido antes de validar (D5).
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-15, estado 81fa1a5cf0df362c15ab3f8ddbf19b49a77ab741

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA5 | PASSOU | `format.test.ts` (inclui Manaus, Tokyo, meia-noite); código de `lib/format.ts` lido |
| CA6 a CA10 | PASSOU | `RelativeDate.test.tsx` (hover, Tab, clique, Esc); `<time dateTime>` dentro de `<button>`, `aria-describedby` |
| CA11 a CA15 | PASSOU | `WhatsAppButton.test.tsx`; domínio de grupo restrito; `min-h-11 min-w-11`; `npm run lint` limpo |
| CA16 | PASSOU | `a11y.test.tsx` com Showcase nos dois temas; sem largura fixa |
| CA17 | PASSOU | `frontend/README.md` lista formatadores, `RelativeDate`, `WhatsAppButton` com exemplos |
| CA18 | PASSOU | RNF-23 em 14 descrições de teste, RNF-42 em 6 |

Comandos: `npx vitest run --coverage` → 19 arquivos, 87 testes passam, cobertura 95,25%; `npm run lint` limpo; `npm run typecheck` limpo; `npm run build` ok.
Achados:
- sugestão, código, `RelativeDate.tsx` — o texto "há 1 dias" não ocorre (dias 2 a 30), mas "há N dias" não usa plural do i18n; sem impacto hoje.

## Entrega

- Adiciona `lib/format.ts` (CPF, CNPJ, telefone, CEP, moeda em centavos, datas e hora `19h30` no fuso da organização), `RelativeDate` (data relativa com data exata em hover, foco, toque e Esc) e `WhatsAppButton` com contexto, tudo com textos em pt-BR.
- Verificação: `vitest --coverage` 87 de 87 passam, cobertura 95,25%; lint, typecheck e build limpos; axe nos dois temas.
- Decisões: telefone com prefixo 55 e 12-13 dígitos tem o 55 removido antes de validar; `onBlur` desliga o estado de toque.
- Limitação: "há N dias" não usa plural do i18n (sem impacto, dias 2 a 30). Esteira de CI vale quando a #10 existir.
