# Plano de issues · telas já construídas ajustadas ao canvas (Sprint 10.5)

Base: `auditoria-canvas-sprint-10.5.md` e `decisoes-canvas.md`. Plano aprovado pelo dono em 30/10/2026; o número de cada issue está em cada título e na lista "Issues abertas" no fim. Todas as issues vão para o marco **Sprint 10.5 · Identidade visual**, com as etiquetas `front-end` e `entrega 1 · produto` (e `back-end` quando a API muda), no formato da #434 (O que entregar, Evidência, Fazer, Pronto quando, Origem). Telas que ainda não existem no repositório ou no canvas ficam de fora: serão feitas na sprint delas, com a especificação, o canvas e os componentes base.

Como ler: **Fase** (1 bugs e regras quebradas; 2 componentes base; 3 telas por área), **IDs** do relatório (`↑` marca os reclassificados como alta pelo dono: M7, B14, M123, M153 e M135), **Depende de** e **Tamanho** (P até 4 IDs ou ajuste simples; M de 5 a 12; G acima disso ou componente transversal). IDs de causa comum aparecem nos componentes e nas telas: na issue da tela eles são só critério de conferência, o componente é quem refaz o padrão.

**Etiquetas.** Issue de produto: `front-end` e/ou `back-end` e `entrega 1 · produto`. Issue **técnica** (F1-06, F1-12, F1-13): etiqueta `técnica` mais a área, **sem** `entrega 1 · produto`. A etiqueta `técnica` ainda não existe no repositório (hoje há `back-end`, `front-end`, `infra`, `operação` e as de entrega); criar ao abrir as issues.

**Migração dos componentes.** A issue de componente **cria o componente e o aplica em uma ou duas telas como piloto** (campo "Piloto"); onde o componente já é compartilhado, a mudança se espalha sozinha. Cada issue de tela **troca o que a tela usa pelo componente novo e confere com o canvas**; o que o componente já resolve não é refeito na issue da tela.

## Resumo

| Fase | Issues | P | M | G |
|---|---|---|---|---|
| 1 · Bugs e regras quebradas | 13 | 9 | 3 | 1 |
| 2 · Componentes base | 15 | 3 | 10 | 2 |
| 3 · Telas por área | 37 | 18 | 12 | 7 |
| **Total** | 65 | 30 | 25 | 10 |

Fase 3 por área: Pessoas 7 · Operação 4 · Financeiro 6 · Configurações 7 · Acesso e Comunicação 4 · Portal 9.

## Fase 1 · Bugs e regras quebradas

### F1-01 · #437 · Cobranças e Comunicados somem do menu da gestão

- **IDs:** A1 · **Fase:** 1 · **Depende de:** — · **Tamanho:** P · **Etiquetas:** front-end
- As rotas `/cobrancas` e `/comunicados` existem, mas `available=false` em `menuCatalog.ts` as esconde (esquecimento, passo F aprovado em 30/10). Ligar os dois itens e conferir as trilhas.

### F1-02 · #438 · "Avisar turma" abre o comunicado vazio em vez do aviso pronto

- **IDs:** A3 · **Fase:** 1 · **Depende de:** — · **Tamanho:** P · **Etiquetas:** front-end
- RN-AGE-05 e RN-COM-12: o formulário abre com tipo "Aula cancelada" (ou "Aviso geral"), texto do modelo da organização, turma, data e horário. `NewAnnouncementPage.tsx:53-54` só lê `classId`; `LessonPanelPage.tsx:71` já monta `type`, `date` e `time`.

### F1-03 · #439 · Prévia da geração fica em branco com um motivo sem chave de tradução

- **IDs:** B14↑ · **Fase:** 1 · **Depende de:** — · **Tamanho:** P · **Etiquetas:** front-end
- `ChargePreviewRows.tsx` lança erro de chave ausente; o contrato deve aceitar todos os motivos do canvas e a tela cair num texto padrão, sem quebrar.

### F1-04 · #440 · Botão de tema invisível na barra escura do portal, no tema claro

- **IDs:** M135↑ · **Fase:** 1 · **Depende de:** — · **Tamanho:** P · **Etiquetas:** front-end
- `PortalLayout.tsx:65-67`: a cor do botão não herda da barra.

### F1-05 · #441 · Revogar uso de imagem no portal sem confirmação

- **IDs:** M153↑ · **Fase:** 1 · **Depende de:** — · **Tamanho:** P · **Etiquetas:** front-end
- `PortalSettings.tsx:55-80` revoga direto. Janela de confirmação do canvas ("Revogar uso de imagem" com resumo); o estilo final vem do padrão de janela (C6a/C6b) e do botão destrutivo (C9).

### F1-06 · #442 · MSW: agenda do Professor devolve todas as aulas da unidade

- **IDs:** M85 · **Fase:** 1 · **Depende de:** — · **Tamanho:** P · **Etiquetas:** técnica (front-end), sem "entrega 1 · produto"
- O back-end filtra (`Agenda.java`, `mine`); o handler do MSW em `frontend/src/mocks/handlers.ts` não. Corrigir o mock e o teste que o cobre.

### F1-07 · #443 · Professor chega à ficha do aluno pela aula (com a permissão "ver ficha do aluno")

- **IDs:** M123↑ · **Fase:** 1 · **Depende de:** — · **Tamanho:** M · **Etiquetas:** front-end
- Decisão 11: pela aula agora (e pelo Início, na Sprint 13). RF-ALU-03: só alunos das suas turmas, ficha sem valores, plano, benefício, CPF, endereço e situação financeira. O MSW já tem `GET /students/{id}/record` para o Professor; `App.tsx:138-146` bloqueia a rota.

### F1-08 · #444 · Gerar códigos de reserva sem a confirmação de senha e código

- **IDs:** M45 · **Fase:** 1 · **Depende de:** — · **Tamanho:** P · **Etiquetas:** back-end, front-end
- Decisão 3: a especificação não exige a confirmação; segue o canvas. Muda a API; atualizar contrato, MSW e testes. Janela CodigosReserva.

### F1-09 · #445 · Login travado devolve o horário de liberação (429)

- **IDs:** M37 · **Fase:** 1 · **Depende de:** — · **Tamanho:** P · **Etiquetas:** back-end, front-end
- RN-ACE-10 (15 minutos): a resposta traz o horário; a tela mostra o horário e o atalho "Criar nova senha" do canvas (LoginTravadoM).

### F1-10 · #446 · Botão "Ver alunos" do painel da turma está sempre desabilitado

- **IDs:** M7↑ · **Fase:** 1 · **Depende de:** — · **Tamanho:** P · **Etiquetas:** front-end
- `ClassPanelPage.tsx:61-63`: `<Button disabled>` fixo. RF-TUR-02 manda "Ver alunos": abrir a lista de Alunos filtrada pela turma (o filtro `classId` já existe em `StudentsPage.tsx`). M7 foi reclassificada como alta pelo dono; a troca de página por gaveta é do componente C7 e da issue O4.

### F1-11 · #447 · Retenção de 30 dias das notificações da equipe (RN-NOT-05)

- **IDs:** M28 · **Fase:** 1 · **Depende de:** — · **Tamanho:** M · **Etiquetas:** back-end, front-end
- Decisão 12 (aprovada em 30/10): limpeza diária apaga a notificação do sino 30 dias depois de criada, lida ou não; o contador de não lidas acompanha; rodapé "As notificações somem depois de 30 dias" no painel. Regra já registrada na especificação (RN-NOT-05); teste com o relógio fixo e o fuso, sem comparar a data da JVM.

### F1-12 · #448 · Técnica · Captura do teste visual com o navegador em pt-BR (antes da Fase 2)

- **IDs:** — · **Fase:** 1 · **Depende de:** — · **Tamanho:** M · **Etiquetas:** técnica (infra, front-end), sem "entrega 1 · produto"
- Passo C da auditoria: o Chromium de captura mostra "dd/mm/yyyy" e "Choose File / No file chosen"; no Edge em pt-BR sai "dd/mm/aaaa" e "Escolher arquivo / Nenhum arquivo escolhido". O `locale: pt-BR` do Playwright só muda a ordem da data; os textos vêm do idioma da interface do navegador. Fazer a captura de referência (`playwright.visual.config.ts`, imagem do CI) e a de comparação com o canvas usarem um navegador em pt-BR, ou garantir que os campos (C4) tragam o texto pelo arquivo pt-BR. Decidir e registrar a opção recomendada no arquivo do item; regerar as referências que mudarem.

### F1-13 · #449 · Técnica · Dados do MSW para as telas que ficaram "sem comparar"

- **IDs:** — · **Fase:** 1 · **Depende de:** F1-12 · **Tamanho:** G · **Etiquetas:** técnica (front-end), sem "entrega 1 · produto"
- Dados fictícios no MSW (e casos do teste visual) para: lista de cobranças com situações variadas e painel da cobrança, recebimentos do dia, comprovante em conferência (um e vários), cobrança em aberto do aluno e da família (portal), termo de matrícula pendente do aluno e de dois filhos, aluno com responsáveis, uso de imagem concedido, graduações por modalidade, aulas e alunos na turma, pedidos do portal por tipo e experimentais de exemplo. A lista final vem das seções "Sem comparar" do relatório. Só dados fictícios; fixtures temporárias da auditoria servem de ponto de partida.

## Fase 2 · Componentes base

### C1 · #454 · Moldura da gestão: menu lateral, barra de cima, cabeçalho, seletor de unidade e menu do celular

- **IDs (causa comum):** A1, M1, M51, M52, M58, M114, B6, B8 · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** G
- **Piloto (aplica o componente em):** Alunos (P1) e Agenda (O1). Menu com ícones de grupo e itens certos por perfil, avatar e nome do usuário, seletor de unidade com "Só consulta" para o Coordenador, trilha, barra do celular com voltar, título e unidade. Inclui o botão de sair (#434).

### C2 · #455 · Moldura do portal e shell das telas de acesso

- **IDs (causa comum):** M1, M2, M135↑, B23, B27 · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** G
- **Piloto (aplica o componente em):** Início do portal (R1) e Login (H1). Coluna do portal na largura do canvas, rótulo acima do título, barra com avatar e nome, tema, sino, rodapé; marca com ícone, slogan e coluna escura das telas de acesso.

### C3a · #456 · Busca e filtro em pílula

- **IDs (causa comum):** M1, M5, M6, M8, M9, M12, M13, M14, M15, M16, M17, M24, M25, M46, M50, M52, M73, M77, M97, M104, M114, M115, M131, M142, M146, M147, M149, M151, B2, B25 · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** M
- **Piloto (aplica o componente em):** Alunos (P1) e Professores (P6). Busca com ícone e texto de ajuda; filtro em pílula ("Rótulo Valor") com popover, no lugar do seletor com rótulo acima.

### C3b · #457 · Abas com contagem, paginação e folha de filtros no celular

- **IDs (causa comum):** M5, M17, M20, M43, M97, M146 · **Fase:** 2 · **Depende de:** F1-12, C3a · **Tamanho:** M
- **Piloto (aplica o componente em):** Alunos (P1) e Cobranças (F1). Abas em pílula com contagem, paginação do canvas e a folha de filtros do celular.

### C4 · #453 · Campos: data, arquivo, interruptor, caixa, cartão de escolha e código em 6 caixas

- **IDs (causa comum):** M1, M2, M5, M6, M8, M9, M12, M13, M15, M16, M17, M18, M20, M23, M30, M52, M54, M55, M65, M73, M77, M106, M108, M117, M124, M146, M147, M149, M151, B3, B4, B7, B9, B21, B22, B24, B25 · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** M
- **Piloto (aplica o componente em):** Novo comunicado (H3) e Verificação em duas etapas (H1). Passo C: com o navegador em pt-BR a data sai "dd/mm/aaaa" e o arquivo "Escolher arquivo"; em outro idioma, no idioma do navegador. O componente tem o aspecto do canvas e o texto do botão de arquivo vem do arquivo pt-BR.

### C5a · #458 · Tabela em cartões no celular

- **IDs (causa comum):** A2, M6, M11, M22, M30, M31, M33, M51, M52, M56, M57, M60, M73, M77, M82, M83, M89, M91, M93, M96, M97, M100, M103, M104, M107, M109, M116, M118, M121, M128, M137, M151, B2, B3, B8, B18 · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** M
- **Piloto (aplica o componente em):** Professores (P6) e Turmas (O4). Abaixo de 640 px a tabela vira lista de cartões, com a ação principal no fim; sem rolagem lateral.

### C5b · #459 · Linha de lista com avatar (usa os blocos da C10)

- **IDs (causa comum):** M2, M5, M12, M14, M16, M21, M42, M52, M76, M92, M105, M112, B1 · **Fase:** 2 · **Depende de:** F1-12, C5a, C10 · **Tamanho:** P
- **Piloto (aplica o componente em):** Professores (P6) e Alunos (P1). Só a composição da linha: avatar, nome, texto secundário e ação (ex.: WhatsApp). O avatar, os selos e a faixa vêm da C10; esta issue não os recria.

### C6a · #460 · Janela base: tamanhos, título em caixa alta e rodapé fixo

- **IDs (causa comum):** A2, M11, M19, M23, M27, M28, M30, M31, M42, M44, M45, M49, M61, M63, M64, M66, M67, M69, M70, M72, M73, M74, M77, M78, M79, M81, M83, M87, M89, M94, M95, M101, M106, M107, M116, M120, M121, M122, M124, M127, M128, M129, M132, M133, M151, M152, B3, B4, B11, B18, B26 · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** M
- **Piloto (aplica o componente em):** Nova aula avulsa (O2) e Nova cobrança avulsa (F2). Larguras, título em caixa alta, botão fechar, rodapé "Cancelar | ação" e rolagem do corpo.

### C6b · #461 · Janela: blocos de resumo, motivo e cartões de escolha

- **IDs (causa comum):** A2, M10, M11, M27, M70, M73, M78, M80, M86, M87, M103, M108, M114, M118, M120, M121, M122, M125, M127, M130, M151, B3, B11 · **Fase:** 2 · **Depende de:** F1-12, C6a · **Tamanho:** M
- **Piloto (aplica o componente em):** Encerrar modalidade (P4) e Cancelar cobrança (F2). Bloco "Resumo", campo de motivo obrigatório, cartões de escolha.

### C6c · #462 · Janela: passos e bloqueio com checklist

- **IDs (causa comum):** M37, M44, M45, M63, M64, M66, M114 · **Fase:** 2 · **Depende de:** F1-12, C6a · **Tamanho:** M
- **Piloto (aplica o componente em):** Desativar turma (O4) e Desativar unidade (G3). Janela em passos (assistentes curtos) e a janela de bloqueio que lista o que impede a ação.

### C7 · #463 · Gaveta lateral e popover

- **IDs (causa comum):** M5, M7↑, M28, M68, M69, M81, M88, M98, M151 · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** M
- **Piloto (aplica o componente em):** Painel da turma (O4) e Notificações (H4). Gaveta à direita (tela cheia no celular) com rodapé de ações; popover das notificações. M7 foi reclassificada como alta pelo dono.

### C8 · #464 · Estados: carregando, vazio, erro, sem permissão, bloqueio e link vencido

- **IDs (causa comum):** M3, M4, M5, M20, M37, M40, M46, M48, M63, M64, M66, M75, M126, M134, M148, M152, B13 · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** M
- **Piloto (aplica o componente em):** Alunos (P1) e Sem acesso (H1). Os quatro estados no padrão do canvas, com a ação de cada um; o sem permissão mantém o menu e a barra.

### C9 · #451 · Botão destrutivo e confirmação de ação sensível

- **IDs (causa comum):** M47, M70, M74, M80, M101, M110, M125, M127, M130, M153↑, B3, B11 · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** P
- **Piloto (aplica o componente em):** Inativar professor (P6) e Revogar uso de imagem (F1-05). Botão em vermelho para inativar, desativar, cancelar e revogar; confirmação digitada ("DESATIVAR") quando o canvas pede.

### C10 · #452 · Selos, avatar e graduação (faixa visual)

- **IDs (causa comum):** M1, M5, M6, M9, M11, M12, M13, M16, M17, M18, M19, M27, M31, M42, M76, M95, M98, M105, M106, M112, M124, B1, B20 · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** M
- **Piloto (aplica o componente em):** Professores (P6) e Turmas (O4). Avatar de iniciais por pessoa, selos de situação, faixa com graus e ponto de cor da modalidade. São os blocos que a C5b usa.

### C11 · #450 · Padrão "Em breve" (R3): item visível e desabilitado, sem backend

- **IDs (causa comum):** — · **Fase:** 2 · **Depende de:** F1-12 · **Tamanho:** P
- **Piloto (aplica o componente em):** Alunos (Exportar CSV, aba Bloqueados) e Comunicados (Push, rascunho). Selo "Em breve" e estado desabilitado com dica; usado por todas as telas que trazem itens de sprints futuras (lista na seção 4 do relatório). Texto no arquivo pt-BR.

## Fase 3 · Telas por área

### Pessoas

#### P1 · #465 · Alunos: lista, filtros, estados e celular

- **IDs cobertos:** M5, M96, M97, B19, B20 · **Fase:** 3 · **Depende de:** C1, C3a, C3b, C5a, C5b, C8, C10, C11 · **Tamanho:** G
- Coluna Graduação ("—" até a Sprint 12), aba Bloqueados e Exportar CSV em "Em breve".

#### P2 · #466 · Ficha do aluno: cabeçalho, Resumo e celular

- **IDs cobertos:** M5, M98, M99, M100, M106, M107, M109, B17, B21 · **Fase:** 3 · **Depende de:** C6a, C6b, C7, C10, C11 · **Tamanho:** M
- Decisão 8: "⋯" em "Em breve"; "Cobrar" abre a cobrança avulsa do aluno. Próximo vencimento e Termo com dado real ou "Em breve".

#### P3 · #467 · Ficha do aluno: abas Matrícula, Financeiro, Graduações, Responsáveis e Histórico

- **IDs cobertos:** M101, M102, M103, M104, M105, M110, M111, M112, M113 · **Fase:** 3 · **Depende de:** P2, C5a, C5b, C10, C11 · **Tamanho:** M
- Graduações e partes de Financeiro em "Em breve" quando dependem de sprint futura.

#### P4 · #468 · Janelas de ação do aluno: encerrar modalidade, trocar turma e plano, transferir, desligar responsável

- **IDs cobertos:** M108, M120, M121, M122, M130, B18 · **Fase:** 3 · **Depende de:** C6a, C6b, C9 · **Tamanho:** M
- Motivo e resumo como no canvas; regras RN-MAT-17/18 e a promoção do responsável seguem a especificação.

#### P5 · #469 · Assistente de matrícula (etapas 1 a 5)

- **IDs cobertos:** M114, M115, M116, M117, M118, M119 · **Fase:** 3 · **Depende de:** C4, C6a, C6b, C6c, C11 · **Tamanho:** G
- Decisões 6 e 7: etapa 1 com busca por CPF e apresentação do canvas; vencimento da primeira cobrança pela RN-MAT-05/07; pagamento na etapa 5 (RF-MAT-01).

#### P6 · #470 · Professores: lista, novo, painel, inativar e recusa de modalidade

- **IDs cobertos:** M6, M7↑, M124, M125, M126 · **Fase:** 3 · **Depende de:** C3a, C3b, C5a, C5b, C6a, C6b, C7, C10 · **Tamanho:** M
- Situação padrão "Ativos"; inativar com novo titular por turma e motivo (R1 + R2); bloqueio preventivo de modalidade.

#### P7 · #471 · Pedidos do portal na gestão: lista e janelas de recusar e aprovar

- **IDs cobertos:** M16, M127, M128, M129, B22 · **Fase:** 3 · **Depende de:** C3a, C3b, C6a, C6b, C11 · **Tamanho:** M
- Prazo pela especificação (decisão 1); aba "Aguardando aprovação" em "Em breve".

### Operação

#### O1 · #472 · Agenda: grade semanal, celular e visão do Professor

- **IDs cobertos:** M7↑, M8, M84, M85, B15, B16 · **Fase:** 3 · **Depende de:** C1, C3a, C3b, C5a, C5b, C8 · **Tamanho:** G
- Botão "Dias sem aula", alternador de semana, legenda de modalidades.

#### O2 · #473 · Aula: painel, editar, cancelar, corrigir, avisar turma e aula avulsa

- **IDs cobertos:** A3, M11, M86, M87, M88, M89, B3 · **Fase:** 3 · **Depende de:** C6a, C6b, C7, C9, C11 · **Tamanho:** M
- Cancelar com experimental marcada: opções em "Em breve". Painel como gaveta.

#### O3 · #474 · Dias sem aula: lista, feriados sugeridos, novo dia

- **IDs cobertos:** A2, M90, M91 · **Fase:** 3 · **Depende de:** C5a, C5b, C6a, C6b · **Tamanho:** P
- "Ignorar" e a tabela de lançados com selo de aviso (R1 + R2; RF-DSA atualizado antes).

#### O4 · #475 · Turmas: lista, painel, editar, desativar e titular inativo

- **IDs cobertos:** M1, M7↑, M9, M11, M92, M93, M94, M95 · **Fase:** 3 · **Depende de:** C3a, C3b, C5a, C5b, C6a, C6b, C6c, C7, C10 · **Tamanho:** M
- Painel como gaveta; 409 de desativar devolve a lista de alunos.

### Financeiro

#### F1 · #476 · Cobranças: lista, filtros, celular e estados

- **IDs cobertos:** A1, M1, M4, M5, M13, M75, M76, M83, B12, B13 · **Fase:** 3 · **Depende de:** C3a, C3b, C5a, C5b, C8, C11 · **Tamanho:** M
- Exportar CSV em "Em breve" se ainda não existir.

#### F2 · #477 · Painel da cobrança e janelas: registrar, renegociar, cancelar, nova avulsa e gerar próximas

- **IDs cobertos:** M68, M69, M70, M77, M78, M79, B4 · **Fase:** 3 · **Depende de:** C6a, C6b, C7, C9, C11 · **Tamanho:** G
- Estornar em "Em breve" (#104). Renegociar uma cobrança por vez, até 6 parcelas (RN-COB-09), com o visual do canvas adaptado a essa regra (decisão 13); renegociar várias de uma vez é melhoria futura e precisa entrar antes na especificação.

#### F3 · #478 · Em atraso, conferência de comprovantes e comprovante de várias cobranças

- **IDs cobertos:** M14, M71, M72 · **Fase:** 3 · **Depende de:** C5a, C5b, C6a, C6b, C11 · **Tamanho:** P
- Lembrete por WhatsApp e mensagem pronta como no canvas; um comprovante por cobrança, agrupado no visual (RN-COB-14).

#### F4 · #479 · Recebimentos do dia e Prévia da geração

- **IDs cobertos:** M15, B14↑ · **Fase:** 3 · **Depende de:** C5a, C5b, C8 · **Tamanho:** P
- Depende da correção de Fase 1 para o motivo sem chave.

#### F5 · #480 · Planos e benefícios: resumo, tabela de planos, editar, desativar e reativar

- **IDs cobertos:** M5, M10, M11, M80, M81, M82 · **Fase:** 3 · **Depende de:** C5a, C5b, C6a, C6b, C9 · **Tamanho:** M
- Reajuste conforme RN-PLA-05; reativar com a regra de nome (RN-PLA-06).

#### F6 · #481 · Benefícios: lista, conceder, encerrar e substituir

- **IDs cobertos:** M12, M73, M74, B11 · **Fase:** 3 · **Depende de:** C3a, C3b, C6a, C6b, C10 · **Tamanho:** P
- Janela de substituir como na RN-BEN-08.

### Configurações

#### G1 · #482 · Organização: marca, regras da organização, contraste e transferir coordenação

- **IDs cobertos:** M19, M67, B5 · **Fase:** 3 · **Depende de:** C4, C6a, C6b, C11 · **Tamanho:** P
- Regras com backend ativas; as de funcionalidade futura em "Em breve".

#### G2 · #483 · Unidade: abas Dados, Pagamentos, Lembretes, Links e Permissões do Professor

- **IDs cobertos:** M20, M53, M54, M55, M56, M57 · **Fase:** 3 · **Depende de:** C4, C5a, C5b, C11 · **Tamanho:** G
- Decisão 5: taxas no modelo atual, com a prévia do total; diretório em "Em breve"; lembretes pela RN-COB-17.

#### G3 · #484 · Unidades: lista, nova unidade, desativar e unidade única

- **IDs cobertos:** M21, M62, M63, M64 · **Fase:** 3 · **Depende de:** C6a, C6b, C6c, C9, C11 · **Tamanho:** P
- Desativar com "DESATIVAR" e senha (RN-ACE-21); assistente de 4 etapas.

#### G4 · #485 · Modalidades: lista, graduação, recusa e desativar

- **IDs cobertos:** M22, M23, M59, M65, M66 · **Fase:** 3 · **Depende de:** C5a, C5b, C6a, C6b, C6c, C10 · **Tamanho:** M
- 409 devolve turmas e alunos que impedem; "Não achei minha modalidade".

#### G5 · #486 · Faixas etárias

- **IDs cobertos:** B1 · **Fase:** 3 · **Depende de:** C5a, C5b · **Tamanho:** P
- Coluna "Usada em" e aviso de categorias de competição.

#### G6 · #487 · Usuários: lista, convidar e tabela de permissões

- **IDs cobertos:** M24, M60, M61 · **Fase:** 3 · **Depende de:** C3a, C3b, C5a, C5b, C6a, C6b, C10 · **Tamanho:** P
- Tabela de permissões nova (a regra do servidor já existe, #27).

#### G7 · #488 · Termos e Textos

- **IDs cobertos:** M25, M26, B9 · **Fase:** 3 · **Depende de:** C4, C6a, C6b · **Tamanho:** P
- Termos com versões e aceites; Textos com lista e editor lado a lado, chips de campos e prévia.

### Acesso e Comunicação

#### H1 · #489 · Telas de acesso: login, duas etapas, travado, recuperar e nova senha, convite, escolha de organização e sem acesso

- **IDs cobertos:** M2, M3, M4, M37, M38, M39, M40, M41, M42, M46 · **Fase:** 3 · **Depende de:** C2, C4, C8 · **Tamanho:** M
- Decisão 4: sem "Agora não" para o Coordenador; convite com nome e e-mail somente leitura.

#### H2 · #490 · Minha conta: abas, duas etapas, códigos de reserva, notificações e sessões

- **IDs cobertos:** M27, M43, M44, M45 · **Fase:** 3 · **Depende de:** C4, C6a, C6b, C11 · **Tamanho:** P
- Decisão 9: "Receber tudo" em Notificações (só Coordenador); resumo diário e boas-vindas em "Em breve".

#### H3 · #491 · Comunicados: lista, novo, cancelar agendado, limite diário e organização inteira

- **IDs cobertos:** A1, M17, M18, M47, M48, M49, M50 · **Fase:** 3 · **Depende de:** C3a, C3b, C4, C5a, C5b, C6a, C6b, C11 · **Tamanho:** G
- Contagem do dia e "Fixado até" no contrato; editar agendado, Push e rascunho em "Em breve".

#### H4 · #492 · Notificações: popover, grupos e "Marcar todas como lidas"

- **IDs cobertos:** M28 · **Fase:** 3 · **Depende de:** C7, C11 · **Tamanho:** P
- "Receber tudo" sai do painel (decisão 9); o rodapé de retenção de 30 dias (RN-NOT-05) vem da issue F1-11.

### Portal

#### R1 · #493 · Início do portal: tudo em dia, estados, responsável e Toda a família

- **IDs cobertos:** M1, M30, M34, M133, M134, M135↑, M136, M137, M138, M139, M140, M141, M142, B25, B26 · **Fase:** 3 · **Depende de:** C2, C8, C11 · **Tamanho:** G
- Confirmar nova modalidade como janela com "Depois".

#### R2 · #494 · Termo de matrícula

- **IDs cobertos:** M35, M144, M145 · **Fase:** 3 · **Depende de:** C2, C4, C11 · **Tamanho:** P
- Versão e "Sair"; "Baixar PDF" em "Em breve"; dois filhos em lista de progresso.

#### R3 · #495 · Agenda do portal

- **IDs cobertos:** M29, M131 · **Fase:** 3 · **Depende de:** C2, C3a, C3b, C5a, C5b · **Tamanho:** P
- Grade de 7 colunas legível; filtro de modalidade.

#### R4 · #496 · Avisos e aviso aberto

- **IDs cobertos:** M132, B2 · **Fase:** 3 · **Depende de:** C3a, C3b, C6a, C6b, C11 · **Tamanho:** P
- Tipo, hora, autor, "Ver na agenda" e "Abrir o grupo da turma".

#### R5 · #497 · Financeiro do portal e Pix na chave

- **IDs cobertos:** M1, M30, M146, M147, B24 · **Fase:** 3 · **Depende de:** C5a, C5b, C6a, C6b, C11 · **Tamanho:** M
- Seleção múltipla e comprovante agora; "Pagar" por Pix, cartão e boleto em "Em breve" (Sprint 11).

#### R6 · #498 · Minhas informações, editar contato e Minha conta do portal

- **IDs cobertos:** M27, M31, M150 · **Fase:** 3 · **Depende de:** C2, C4, C6a, C6b, C11 · **Tamanho:** P
- "Baixar meus dados" e "Excluir minha conta" em "Em breve" (Sprint 13).

#### R7 · #499 · Carteirinha

- **IDs cobertos:** M32, M148, M149 · **Fase:** 3 · **Depende de:** C2, C10, C11 · **Tamanho:** P
- Cartão visual do canvas; "Baixar imagem" em "Em breve".

#### R8 · #500 · Pedidos do portal e pedido indisponível

- **IDs cobertos:** M33, M151, M152 · **Fase:** 3 · **Depende de:** C5a, C5b, C6a, C6b, C11 · **Tamanho:** P
- Prazos pela especificação (decisão 1); janela explicando o bloqueio (RN-MAT-18).

#### R9 · #501 · Portal reduzido (aluno sem modalidade ativa)

- **IDs cobertos:** M143 · **Fase:** 3 · **Depende de:** C2, R5, R6, R7 · **Tamanho:** P
- Decisão 2: Financeiro e Minhas informações nos dois tamanhos; cartão "Fale com a academia" (RN-INA-08).

## Achados sem issue de tela

- **M36 (Início da gestão)** não entra: é entrega das issues #122, #123 e #124 (Sprint 13).
- Achados de telas que ainda não existem (relatórios, experimentais, graduações, importação, assinatura, auditoria, site, painel da plataforma) ficam para a sprint de cada uma.

## Sequência sugerida dentro da Sprint 10.5

1. **Fase 1** primeiro: são pequenas e independentes. F1-12 (captura em pt-BR) vem antes de qualquer issue da Fase 2; F1-13 (dados do MSW) vem antes das telas que ficaram "sem comparar".
2. **Fase 2**, cada issue com um ou dois pilotos, nesta ordem: C11 (Em breve), C9, C10, C4 → C1 e C2 (molduras) → C3a, C3b, C5a, C5b → C6a, C6b, C6c → C7, C8.
3. **Fase 3** por área, cada issue só depois dos componentes de que depende. Uma issue por PR. Cada PR de tela **regera só os prints de referência da própria tela, depois da comparação com o canvas** (`npm run test:visual:docker -- --update-snapshots` limitado aos casos da tela); referência de outra tela não é tocada. A descrição do PR traz a comparação com o canvas (regra proposta abaixo).
4. A #435 (comparação automática com o canvas) fecha o ciclo e pode andar em paralelo à Fase 2, porque detecta regressão nas telas já ajustadas.

## Passo F: Cobranças e Comunicados no menu (aprovado)

É esquecimento. `menuCatalog.ts` tem a regra D7 (o item só vira `available=true` no item que entrega a tela). As telas foram entregues completas: `/cobrancas` na #72 e `/comunicados` na #81; as duas PRs mexeram em `App.tsx`, na página e no teste, mas não em `menuCatalog.ts`. As entregas vizinhas ligaram o item na própria PR (#53, #55, #58, #64, #70, #83, #86). Mantida como **F1-01**.

## Regra final proposta para o AGENTS.md

Em "Regras que valem para todo item", trocar a linha "Visual igual à tela do canvas citada na issue…" por:

> - **Canvas e decisões do dono.** Toda tela nova ou alterada que tem tela no canvas é comparada com ela antes da entrega (1440 e 390 px, e nos temas em que o canvas existe) e segue `docs/auditoria/decisoes-canvas.md`: o canvas vence em layout, textos, rótulos, botões, janelas e confirmações; se a API precisa mudar para igualar, ela muda, desde que não contrarie a especificação, e a especificação, o contrato e o MSW mudam junto; quando o canvas contradiz uma regra de negócio, a especificação vence na regra e o canvas no visual. **A captura para comparar com o canvas é feita com o navegador em pt-BR** (os textos nativos de data e arquivo dependem do idioma do navegador). Tela sem canvas segue a especificação e os componentes base padronizados na 10.5 (`docs/design/README.md`). Item de sprint futura que o canvas já mostra aparece na tela **visível e desabilitado com "Em breve"**, sem backend. A descrição do PR lista as divergências achadas na comparação e como foram resolvidas, e o PR regera só os prints de referência da própria tela; sem comparação, o PR não é mesclado.

## Issues abertas (30/10/2026)

Todas no marco **Sprint 10.5 · Identidade visual**. A F1-01 (#437) foi aprovada antes das demais. "Bloqueada por" está marcada no GitHub (Relationships) e escrita na descrição de cada issue. A Fase 2 foi aberta na ordem da sequência sugerida (não na ordem alfabética dos códigos) para que cada dependência já existisse.

### Fase 1 · Bugs, regras quebradas e itens técnicos

- F1-01 · [#437](https://github.com/DevDosAnjos/ctcore/issues/437) · Cobranças e Comunicados somem do menu da gestão (P)
- F1-02 · [#438](https://github.com/DevDosAnjos/ctcore/issues/438) · "Avisar turma" abre o comunicado vazio em vez do aviso pronto (P)
- F1-03 · [#439](https://github.com/DevDosAnjos/ctcore/issues/439) · Prévia da geração fica em branco com um motivo sem chave de tradução (P)
- F1-04 · [#440](https://github.com/DevDosAnjos/ctcore/issues/440) · Botão de tema invisível na barra escura do portal, no tema claro (P)
- F1-05 · [#441](https://github.com/DevDosAnjos/ctcore/issues/441) · Revogar uso de imagem no portal sem confirmação (P)
- F1-06 · [#442](https://github.com/DevDosAnjos/ctcore/issues/442) · MSW: agenda do Professor devolve todas as aulas da unidade (P, técnica)
- F1-07 · [#443](https://github.com/DevDosAnjos/ctcore/issues/443) · Professor chega à ficha do aluno pela aula (com a permissão "ver ficha do aluno") (M)
- F1-08 · [#444](https://github.com/DevDosAnjos/ctcore/issues/444) · Gerar códigos de reserva sem a confirmação de senha e código (P)
- F1-09 · [#445](https://github.com/DevDosAnjos/ctcore/issues/445) · Login travado devolve o horário de liberação (429) (P)
- F1-10 · [#446](https://github.com/DevDosAnjos/ctcore/issues/446) · Botão "Ver alunos" do painel da turma está sempre desabilitado (P)
- F1-11 · [#447](https://github.com/DevDosAnjos/ctcore/issues/447) · Retenção de 30 dias das notificações da equipe (RN-NOT-05) (M)
- F1-12 · [#448](https://github.com/DevDosAnjos/ctcore/issues/448) · Técnica · Captura do teste visual com o navegador em pt-BR (antes da Fase 2) (M, técnica)
- F1-13 · [#449](https://github.com/DevDosAnjos/ctcore/issues/449) · Técnica · Dados do MSW para as telas que ficaram "sem comparar" (G, técnica; depende de #448)

### Fase 2 · Componentes base

- C11 · [#450](https://github.com/DevDosAnjos/ctcore/issues/450) · Componente base: Padrão "Em breve" (R3): item visível e desabilitado, sem backend (P; depende de #448)
- C9 · [#451](https://github.com/DevDosAnjos/ctcore/issues/451) · Componente base: Botão destrutivo e confirmação de ação sensível (P; depende de #448)
- C10 · [#452](https://github.com/DevDosAnjos/ctcore/issues/452) · Componente base: Selos, avatar e graduação (faixa visual) (M; depende de #448)
- C4 · [#453](https://github.com/DevDosAnjos/ctcore/issues/453) · Componente base: Campos: data, arquivo, interruptor, caixa, cartão de escolha e código em 6 caixas (M; depende de #448)
- C1 · [#454](https://github.com/DevDosAnjos/ctcore/issues/454) · Componente base: Moldura da gestão: menu lateral, barra de cima, cabeçalho, seletor de unidade e menu do celular (G; depende de #448)
- C2 · [#455](https://github.com/DevDosAnjos/ctcore/issues/455) · Componente base: Moldura do portal e shell das telas de acesso (G; depende de #448)
- C3a · [#456](https://github.com/DevDosAnjos/ctcore/issues/456) · Componente base: Busca e filtro em pílula (M; depende de #448)
- C3b · [#457](https://github.com/DevDosAnjos/ctcore/issues/457) · Componente base: Abas com contagem, paginação e folha de filtros no celular (M; depende de #448, #456)
- C5a · [#458](https://github.com/DevDosAnjos/ctcore/issues/458) · Componente base: Tabela em cartões no celular (M; depende de #448)
- C5b · [#459](https://github.com/DevDosAnjos/ctcore/issues/459) · Componente base: Linha de lista com avatar (usa os blocos da C10) (P; depende de #448, #458, #452)
- C6a · [#460](https://github.com/DevDosAnjos/ctcore/issues/460) · Componente base: Janela base: tamanhos, título em caixa alta e rodapé fixo (M; depende de #448)
- C6b · [#461](https://github.com/DevDosAnjos/ctcore/issues/461) · Componente base: Janela: blocos de resumo, motivo e cartões de escolha (M; depende de #448, #460)
- C6c · [#462](https://github.com/DevDosAnjos/ctcore/issues/462) · Componente base: Janela: passos e bloqueio com checklist (M; depende de #448, #460)
- C7 · [#463](https://github.com/DevDosAnjos/ctcore/issues/463) · Componente base: Gaveta lateral e popover (M; depende de #448)
- C8 · [#464](https://github.com/DevDosAnjos/ctcore/issues/464) · Componente base: Estados: carregando, vazio, erro, sem permissão, bloqueio e link vencido (M; depende de #448)

### Fase 3 · Telas por área


**Pessoas**

- P1 · [#465](https://github.com/DevDosAnjos/ctcore/issues/465) · Alunos: lista, filtros, estados e celular no visual do canvas (G; depende de #454, #456, #457, #458, #459, #464, #452, #450)
- P2 · [#466](https://github.com/DevDosAnjos/ctcore/issues/466) · Ficha do aluno: cabeçalho, Resumo e celular no visual do canvas (M; depende de #460, #461, #463, #452, #450, #449)
- P3 · [#467](https://github.com/DevDosAnjos/ctcore/issues/467) · Ficha do aluno: abas Matrícula, Financeiro, Graduações, Responsáveis e Histórico no visual do canvas (M; depende de #466, #458, #459, #452, #450, #449)
- P4 · [#468](https://github.com/DevDosAnjos/ctcore/issues/468) · Janelas de ação do aluno: encerrar modalidade, trocar turma e plano, transferir, desligar responsável no visual do canvas (M; depende de #460, #461, #451)
- P5 · [#469](https://github.com/DevDosAnjos/ctcore/issues/469) · Assistente de matrícula (etapas 1 a 5) no visual do canvas (G; depende de #453, #460, #461, #462, #450)
- P6 · [#470](https://github.com/DevDosAnjos/ctcore/issues/470) · Professores: lista, novo, painel, inativar e recusa de modalidade no visual do canvas (M; depende de #456, #457, #458, #459, #460, #461, #463, #452)
- P7 · [#471](https://github.com/DevDosAnjos/ctcore/issues/471) · Pedidos do portal na gestão: lista e janelas de recusar e aprovar no visual do canvas (M; depende de #456, #457, #460, #461, #450)

**Operação**

- O1 · [#472](https://github.com/DevDosAnjos/ctcore/issues/472) · Agenda: grade semanal, celular e visão do Professor no visual do canvas (G; depende de #454, #456, #457, #458, #459, #464)
- O2 · [#473](https://github.com/DevDosAnjos/ctcore/issues/473) · Aula: painel, editar, cancelar, corrigir, avisar turma e aula avulsa no visual do canvas (M; depende de #460, #461, #463, #451, #450)
- O3 · [#474](https://github.com/DevDosAnjos/ctcore/issues/474) · Dias sem aula: lista, feriados sugeridos, novo dia no visual do canvas (P; depende de #458, #459, #460, #461)
- O4 · [#475](https://github.com/DevDosAnjos/ctcore/issues/475) · Turmas: lista, painel, editar, desativar e titular inativo no visual do canvas (M; depende de #456, #457, #458, #459, #460, #461, #462, #463, #452)

**Financeiro**

- F1 · [#476](https://github.com/DevDosAnjos/ctcore/issues/476) · Cobranças: lista, filtros, celular e estados no visual do canvas (M; depende de #456, #457, #458, #459, #464, #450, #449)
- F2 · [#477](https://github.com/DevDosAnjos/ctcore/issues/477) · Painel da cobrança e janelas: registrar, renegociar, cancelar, nova avulsa e gerar próximas no visual do canvas (G; depende de #460, #461, #463, #451, #450, #449)
- F3 · [#478](https://github.com/DevDosAnjos/ctcore/issues/478) · Em atraso, conferência de comprovantes e comprovante de várias cobranças no visual do canvas (P; depende de #458, #459, #460, #461, #450, #449)
- F4 · [#479](https://github.com/DevDosAnjos/ctcore/issues/479) · Recebimentos do dia e Prévia da geração no visual do canvas (P; depende de #458, #459, #464, #449)
- F5 · [#480](https://github.com/DevDosAnjos/ctcore/issues/480) · Planos e benefícios: resumo, tabela de planos, editar, desativar e reativar no visual do canvas (M; depende de #458, #459, #460, #461, #451)
- F6 · [#481](https://github.com/DevDosAnjos/ctcore/issues/481) · Benefícios: lista, conceder, encerrar e substituir no visual do canvas (P; depende de #456, #457, #460, #461, #452)

**Configurações**

- G1 · [#482](https://github.com/DevDosAnjos/ctcore/issues/482) · Organização: marca, regras da organização, contraste e transferir coordenação no visual do canvas (P; depende de #453, #460, #461, #450)
- G2 · [#483](https://github.com/DevDosAnjos/ctcore/issues/483) · Unidade: abas Dados, Pagamentos, Lembretes, Links e Permissões do Professor no visual do canvas (G; depende de #453, #458, #459, #450)
- G3 · [#484](https://github.com/DevDosAnjos/ctcore/issues/484) · Unidades: lista, nova unidade, desativar e unidade única no visual do canvas (P; depende de #460, #461, #462, #451, #450)
- G4 · [#485](https://github.com/DevDosAnjos/ctcore/issues/485) · Modalidades: lista, graduação, recusa e desativar no visual do canvas (M; depende de #458, #459, #460, #461, #462, #452)
- G5 · [#486](https://github.com/DevDosAnjos/ctcore/issues/486) · Faixas etárias no visual do canvas (P; depende de #458, #459)
- G6 · [#487](https://github.com/DevDosAnjos/ctcore/issues/487) · Usuários: lista, convidar e tabela de permissões no visual do canvas (P; depende de #456, #457, #458, #459, #460, #461, #452)
- G7 · [#488](https://github.com/DevDosAnjos/ctcore/issues/488) · Termos e Textos no visual do canvas (P; depende de #453, #460, #461)

**Acesso e Comunicação**

- H1 · [#489](https://github.com/DevDosAnjos/ctcore/issues/489) · Telas de acesso: login, duas etapas, travado, recuperar e nova senha, convite, escolha de organização e sem acesso no visual do canvas (M; depende de #455, #453, #464)
- H2 · [#490](https://github.com/DevDosAnjos/ctcore/issues/490) · Minha conta: abas, duas etapas, códigos de reserva, notificações e sessões no visual do canvas (P; depende de #453, #460, #461, #450)
- H3 · [#491](https://github.com/DevDosAnjos/ctcore/issues/491) · Comunicados: lista, novo, cancelar agendado, limite diário e organização inteira no visual do canvas (G; depende de #456, #457, #453, #458, #459, #460, #461, #450)
- H4 · [#492](https://github.com/DevDosAnjos/ctcore/issues/492) · Notificações: popover, grupos e "Marcar todas como lidas" no visual do canvas (P; depende de #463, #450)

**Portal**

- R1 · [#493](https://github.com/DevDosAnjos/ctcore/issues/493) · Início do portal: tudo em dia, estados, responsável e Toda a família no visual do canvas (G; depende de #455, #464, #450)
- R2 · [#494](https://github.com/DevDosAnjos/ctcore/issues/494) · Termo de matrícula no visual do canvas (P; depende de #455, #453, #450, #449)
- R3 · [#495](https://github.com/DevDosAnjos/ctcore/issues/495) · Agenda do portal no visual do canvas (P; depende de #455, #456, #457, #458, #459)
- R4 · [#496](https://github.com/DevDosAnjos/ctcore/issues/496) · Avisos e aviso aberto no visual do canvas (P; depende de #456, #457, #460, #461, #450)
- R5 · [#497](https://github.com/DevDosAnjos/ctcore/issues/497) · Financeiro do portal e Pix na chave no visual do canvas (M; depende de #458, #459, #460, #461, #450, #449)
- R6 · [#498](https://github.com/DevDosAnjos/ctcore/issues/498) · Minhas informações, editar contato e Minha conta do portal no visual do canvas (P; depende de #455, #453, #460, #461, #450, #449)
- R7 · [#499](https://github.com/DevDosAnjos/ctcore/issues/499) · Carteirinha no visual do canvas (P; depende de #455, #452, #450)
- R8 · [#500](https://github.com/DevDosAnjos/ctcore/issues/500) · Pedidos do portal e pedido indisponível no visual do canvas (P; depende de #458, #459, #460, #461, #450)
- R9 · [#501](https://github.com/DevDosAnjos/ctcore/issues/501) · Portal reduzido (aluno sem modalidade ativa) no visual do canvas (P; depende de #455, #497, #498, #499)

