# 446 — Botão "Ver alunos" do painel da turma está sempre desabilitado

Origem: issue #446 (Sprint 10.5 · Identidade visual); RF-TUR-02 (painel da turma), relatório M7 (`docs/auditoria/auditoria-canvas-sprint-10.5.md`), plano F1-10, decisão 15 de `docs/auditoria/decisoes-canvas.md`. Branch: `item/446-ver-alunos-painel-turma`.

## 1. Objetivo

No painel da turma, "Ver alunos" hoje é um botão sempre desabilitado. Este item o troca por um link para a lista de Alunos já filtrada por aquela turma, para Coordenador e Administrador. Fora do escopo: gaveta lateral do painel (#463, #475) e qualquer mudança visual.

## 2. Requisitos

- R1 (RF-TUR-02): "Ver alunos" é um link para `/alunos?classId=<id da turma>`, nunca um botão desabilitado (D1).
- R2 (RF-TUR-02): `/alunos?classId=<id>` abre a lista com o filtro "Turma" já aplicado e visível, e a consulta ao servidor leva `classId`.

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador no painel de uma turma ativa, "Ver alunos" é um link com `href="/alunos?classId=c1"` e não está desabilitado.
- CA2 (R1): dado o painel de uma turma desativada, "Ver alunos" continua um link com o mesmo `href` (D1); Editar, Avisar turma e Desativar seguem como hoje.
- CA3 (R2): dado `/alunos?classId=k1`, a lista chama a API com `classId=k1` e o select "Turma" mostra a turma k1 selecionada.
- CA4 (R1, R2): axe sem violação no painel da turma, nos dois temas (testes existentes do painel continuam passando).

## 4. Plano técnico

- Dados / API: não muda. `GET /students` já aceita `classId`.
- Interface:
  - `frontend/src/features/classes/ClassPanelPage.tsx` (`Panel`): trocar o `<Button variant="outline" className="min-h-11" disabled>` de `classes.panel.viewStudents` por `<Button variant="outline" className="min-h-11" asChild><Link to={`/alunos?classId=${klass.id}`}>…</Link></Button>`, igual ao padrão de "Avisar turma" no mesmo arquivo.
  - `frontend/src/features/students/StudentsPage.tsx`: não muda; `QueryPage` já lê `classId` da URL (`filterKeys`) e `StudentFilters` mostra `get("classId")` no select.
  - `frontend/src/features/classes/ClassPanelPage.test.tsx`: substituir o teste "CA8: Ver alunos fica desativado" (linha ~90) por um que exige o link com o `href`.
  - `frontend/src/features/students/StudentsPage.test.tsx`: novo teste que renderiza com `initialEntries={["/alunos?classId=k1"]}` (o `setup` hoje fixa `/alunos`; aceitar a URL por parâmetro).
- Segurança: a barreira continua no servidor (`/students` confere permissão e organização); o painel já é só da gestão. Rota e permissões não mudam.
- Reuso: `Button asChild` + `Link`, `QueryPage`, `StudentFilters`.
- Riscos: turma desativada pode não constar nas opções do select "Turma" (vindas de `useStudentFilterOptions`); a consulta ainda leva `classId`, só o rótulo do select pode ficar vazio. Se o teste mostrar isso, anotar como limitação, sem ampliar o item.

## 5. Tarefas

- [x] T1: `ClassPanelPage` com o link "Ver alunos" e ajuste do teste existente — cobre CA1, CA2 — prova: `cd frontend && npx vitest run src/features/classes/ClassPanelPage.test.tsx`
- [x] T2: teste da lista com `?classId=k1` (consulta com `classId` e select "Turma" aplicado); ajustar `StudentsPage` só se o teste falhar — cobre CA3 — prova: `cd frontend && npx vitest run src/features/students/StudentsPage.test.tsx`
- [x] T3: conferir que os dois testes falham sem a correção (reverter T1 por um instante) e rodar axe/lint/tipos — cobre CA4 — prova: `cd frontend && npm run lint && npm run typecheck && npx vitest run src/features/classes src/features/students`

## 6. Decisões

- D1 (muda o comportamento): o link fica ativo também em turma desativada, pois ver os alunos é só leitura e a lista mostra ex-alunos pelo filtro de situação; a issue só pede o botão ativo em turma ativa e deixa as outras ações como estão. Alternativa descartada: desabilitar em turma desativada (esconderia um caminho de consulta sem ganho).
- D2: sem captura nova nem caso novo no teste visual: o item não altera o layout (botão vira link com o mesmo estilo); os prints de referência existentes de `turma-painel` não mudam. Suposição: se o botão desabilitado aparecia diferente na referência, o teste visual acusa e a referência é regerada no container (`npm run test:visual:docker -- --update-snapshots -g "turma-painel"`).

## Construção

- T1 — arquivos: `frontend/src/features/classes/ClassPanelPage.tsx`, `ClassPanelPage.test.tsx` — prova: `npx vitest run src/features/classes/ClassPanelPage.test.tsx` → passa
- T2 — arquivos: `frontend/src/features/students/StudentsPage.test.tsx` (`setup` aceita a URL; teste novo de `?classId=k1`); `StudentsPage` não precisou mudar — prova: `npx vitest run src/features/students/StudentsPage.test.tsx` → passa (38 testes nos dois arquivos)
- T3 — prova: com o botão desabilitado de volta o teste do painel falha (1 falha); restaurado, `npm run lint && npm run typecheck && npx vitest run src/features/classes src/features/students` → exit 0, 17 arquivos, 204 testes
Regressão: só o front das pastas afetadas rodou (lint, tipos, classes e students); `test:cov`, `e2e`, teste visual e back-end não rodaram (item só de front; ficam para o CI do PR).
Desvios: nenhum. Limitação do risco do plano (rótulo do select em turma desativada) não foi exercitada.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-31, estado b1c03bc9efd8076641e97e7a2cfb84fc07b51832

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | teste CA8 do painel exige link com `href="/alunos?classId=c1"`; passa |
| CA2 | PASSOU | mesmo teste, com turma INACTIVE; passa |
| CA3 | PASSOU | teste "CA3 (#446)" em StudentsPage.test.tsx: `classId=k1` na consulta e select Turma com valor k1; passa |
| CA4 | PASSOU | testes de axe existentes do painel continuam passando na suíte de classes |

Comandos: `npm run lint` ok; `npm run typecheck` ok; `npx vitest run src/features/classes src/features/students --maxWorkers=2` → 17 arquivos, 204 testes passaram.
Achados:
- sugestão, código, ClassPanelPage.test.tsx — `test:cov`, `e2e` e teste visual de `turma-painel` não rodaram aqui; ficam para o CI do PR (AGENTS.md, autonomia 2).

## Entrega

- O botão "Ver alunos" do painel da turma vira link para `/alunos?classId=<id da turma>`, ativo também em turma desativada (D1), para quem tem a permissão de gestão do painel.
- A lista de Alunos já lê `classId` da URL: o filtro "Turma" aparece aplicado e a consulta ao servidor leva `classId`, sem mudar `StudentsPage`.
- Verificado: lint, tipos e a suíte de `classes` e `students` passaram (17 arquivos, 204 testes), e o teste do painel falha com o botão desabilitado de volta (T3).
- Limitações: `test:cov`, `e2e` e o teste visual de `turma-painel` ficam para as verificações do PR. Sem captura nova e sem caso novo no teste visual (D2). A limitação do rótulo do select em turma desativada não foi exercitada.
