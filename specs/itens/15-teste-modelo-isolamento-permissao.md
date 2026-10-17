# 15 — Teste-modelo de isolamento e permissão no servidor

Origem: issue #15; `docs/especificacao/00-visao-geral.md` (RNF-05, RNF-31), `01-organizacao-e-isolamento.md` e `02-contas-e-acesso.md` (matriz de perfis). Branch: `item/15-teste-modelo-isolamento`.

## 1. Objetivo

Entregar o teste-modelo que toda rota futura copia para provar, em banco real, que a organização A não lê nem altera a B (inclusive por identificador adivinhado) e que um perfil não alcança o que não deve. É um molde reutilizável de teste mais o guia de uso para quem cria rotas, apoiado no contexto, no filtro e na RLS da #14.

Fora do escopo: rota, tela ou contrato novos de produto; autenticação real (Sprint 02); tabela de permissões (#27); auditoria (#17). Nenhuma rota entra no OpenAPI/MSW.

## 2. Requisitos

- R1: RNF-31: existe um molde de teste que, para uma rota, prova que usuário da organização A não lê dados da B, mesmo com identificador adivinhado da B (organização ou unidade na URL).
- R2: RNF-31: o molde prova que usuário da A não altera dados da B (resposta negada ou sem efeito, e linha da B inalterada no banco).
- R3: RNF-05 e RNF-31: o molde prova que a permissão é conferida no servidor: anônimo recebe 401, perfil sem permissão recebe 403 e nenhum efeito no banco, perfil permitido recebe sucesso, e a recusa não depende de nada enviado pelo cliente (D2).
- R4: O molde é copiável: uma classe base com os cenários e pontos de extensão mínimos (rota, perfis permitidos/negados, criação dos dados de A e B), com exemplo concreto funcional e instruções de cópia (D1).
- R5: Os testes nomeiam RNF-05 e RNF-31 e rodam com banco real e credencial operacional restrita, sem afrouxar as proteções da #14 (D3).
- R6: A regra "toda rota nova tem teste de isolamento entre organizações e entre perfis" fica documentada com o passo a passo para usar o molde.

## 3. Critérios de aceite

- CA1 (R1): dadas duas organizações com dados, quando o usuário da A lê pelo identificador (unidade ou organização) da B, então a resposta não traz dado da B (404 ou vazio, igual ao de identificador inexistente).
- CA2 (R2): quando o usuário da A tenta alterar recurso da B pelo identificador, então a resposta nega ou não tem efeito e a leitura administrativa mostra a linha da B inalterada.
- CA3 (R3): anônimo recebe 401; autenticado sem principal de organização recebe 403; perfil não permitido recebe 403 e nada é gravado; perfil permitido recebe 2xx e a gravação ocorre apenas na própria organização.
- CA4 (R3): cabeçalho `X-Organization-Id` e parâmetro com organização da B não mudam o resultado de A nem concedem ao perfil negado.
- CA5 (R4): uma rota de exemplo nova, ligando só as classes de ponto de extensão, executa todos os cenários sem copiar a lógica; remover a proteção (filtro, `@PreAuthorize` ou RLS) faz algum cenário falhar (verificado ao menos com a rota sem `@PreAuthorize`).
- CA6 (R5): `verify` passa, os testes têm RNF-05 e RNF-31 em seus `@DisplayName`, JaCoCo mantém 80% global e 100% em `isolation`, e a suíte de contrato segue passando.
- CA7 (R6): `backend/README.md` descreve como copiar o molde para uma rota nova e `AGENTS.md` não precisa mudar (regra já existe).

## 4. Plano técnico

- Dados: não muda (sem migração). O exemplo usa tabelas reais da #13/#14 (`unit_settings`, `organization_feature`) com fixtures criadas por `Organizations.initialize` como em `OrganizationIsolationIT`.
- API: não muda; contrato, OpenAPI e MSW intactos. A rota de exemplo é um `@RestController` exclusivo de teste (padrão do `Probe` em `OrganizationIsolationIT`), sob `/api/v1/`, usando `Organizations.unitSettings` (leitura por unidade) e `FeatureFlags.setEnabled` (escrita), protegida por `@PreAuthorize` com os papéis sintéticos `ROLE_MANAGER`/`ROLE_ADMIN` permitidos e `ROLE_TEACHER` negado, como já usado nos testes.
- Interface: não muda; quatro estados, tradução e axe não se aplicam.
- Segurança: reutiliza `OrganizationPrincipal`, `OrganizationScopeFilter`, `OrganizationContext`, `Scopes` (todos existentes). Nenhum papel real é criado; a matriz de perfis fica no item #27. Sem dados pessoais.
- Arquivos a criar em `backend/src/test/java/br/com/ctcore/isolation/`: `RouteIsolationModelIT` (classe base abstrata com os cenários, estende `PostgresIT`) e `RouteIsolationModelExampleIT` (exemplo concreto com o controller de teste). Alterar `backend/README.md` (seção do molde). Reuso: `PostgresIT` (inclui `adminJdbc()`), `Scopes`, `MockMvc`, padrão `authentication(...)` de `OrganizationIsolationIT`.
- Pontos de extensão da base (mínimos): criar fixture de A e B com identificadores da rota, métodos para montar a requisição de leitura e de alteração com o identificador de um alvo, conjunto de papéis permitidos e negados, e consulta administrativa que devolve o estado do recurso da B.
- Riscos: molde complicado demais para copiar (mitigado pelo exemplo e pelo README); falso positivo se a rota devolver 200 com corpo vazio sem distinguir de B (o cenário compara com identificador inexistente); testes dependem do Docker (já exigido pelos `*IT`).

## 5. Tarefas

- [x] T1: Criar `RouteIsolationModelIT` com os cenários de leitura cruzada, alteração cruzada, identificador adivinhado, anônimo, sem escopo, perfil negado, perfil permitido e cabeçalho/parâmetro forjado — cobre CA1, CA2, CA3, CA4 — prova: execução pelo exemplo (T2).
- [x] T2: Criar `RouteIsolationModelExampleIT` com o controller de teste e os pontos de extensão ligados; incluir verificação de que a rota sem `@PreAuthorize` falha o cenário de perfil — cobre CA5 e CA6 (nomes RNF-05/RNF-31) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=RouteIsolationModelExampleIT` (Windows: `backend/mvnw.cmd`).
- [x] T3: Documentar em `backend/README.md` como copiar o molde para uma rota nova (passo a passo, pontos de extensão, o que prova cada cenário) — cobre CA7 — prova: leitura do README e `git diff --check`.
- [x] T4: Regressão completa do back-end e conferência de cobertura e contrato — cobre CA6 — prova: `./backend/mvnw -f backend/pom.xml verify` (JaCoCo 80%/100% isolation, `OpenApiContractTest`).

## 6. Decisões

- D1: Molde como classe base abstrata de teste (JUnit) com exemplo, e não utilitário em código de produção nem rota fictícia no contrato. Motivo: é o que "toda rota futura copia", sem endpoint novo (consistente com D1 do item 14). Alternativa descartada: biblioteca/anotação própria, que seria abstração sem segundo uso ainda.
- D2: Perfis do molde usam os papéis sintéticos já adotados nos testes da #14 (`ROLE_MANAGER`, `ROLE_ADMIN`, `ROLE_TEACHER`), pois a tabela de permissões real é da #27 e a autenticação da Sprint 02; quando existirem, o molde troca só o ponto de extensão dos papéis. Alternativa descartada: antecipar permissões reais.
- D3: Os cenários rodam por MockMvc com banco real e papel restrito (RLS ativa), não por mocks de repositório, para que a prova valha para filtro e RLS juntos. Alternativa descartada: testes unitários com banco simulado.
- Suposição: o item não exige OpenAPI, MSW, tela nem notas de versão, porque a issue só pede o teste-modelo; se o dono quiser a rota real de exemplo no contrato, vira outro item.

## Construção

- T1 — arquivos: `backend/src/test/java/br/com/ctcore/isolation/RouteIsolationModelIT.java` — prova: executada pelo exemplo (T2) → 7 testes passam
- T2 — arquivos: `backend/src/test/java/br/com/ctcore/isolation/RouteIsolationModelExampleIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=RouteIsolationModelExampleIT` → 7 testes, BUILD SUCCESS; o teste `modelCatchesMissingPreAuthorize` mostra que a rota sem `@PreAuthorize` deixa o perfil negado gravar (2xx), o que o cenário `deniedProfile` reprovaria
- T3 — arquivos: `backend/README.md` (seção "Teste-modelo de isolamento e permissão") — prova: leitura e `git diff --check` limpo
- T4 — prova: `./backend/mvnw -f backend/pom.xml verify` → 74 testes rápidos (inclui `OpenApiContractTest`) e 36 `*IT` passam, "All coverage checks have been met", BUILD SUCCESS
Regressão: `verify` completo sem falhas.
Desvios: o exemplo devolve só `{unitId}` na leitura (a linha crua de `unit_settings` não serializa em JSON). A prova "sem `@PreAuthorize`" é uma rota paralela `Unprotected` no próprio exemplo, em vez de remover a anotação da rota principal.

## Verificação

### Rodada 1 — OK — 2026-10-16, estado b7b5b130e96e365bd2c237f64ff6efc752fd232a

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `crossRead` em RouteIsolationModelExampleIT: leitura da B por A = mesmo 404 do id inexistente, sem o id da B no corpo |
| CA2 | PASSOU | `crossWrite`: 403/404 e estado da B (consulta admin) inalterado |
| CA3 | PASSOU | `anonymousAndUnscoped` (401/403), `deniedProfile` (403 sem gravação), `allowedProfile` (2xx só na própria org) |
| CA4 | PASSOU | `forgedOrganization`: cabeçalho e parâmetro da B não concedem nem alteram |
| CA5 | PASSOU | exemplo liga só os pontos de extensão; `modelCatchesMissingPreAuthorize` mostra a escrita 2xx sem `@PreAuthorize`. Filtro e RLS não foram removidos para provar falha (o critério exige ao menos o caso do `@PreAuthorize`) |
| CA6 | PASSOU | `verify`: 74 testes rápidos e 36 IT sem falha (7 do exemplo, com RNF-05/RNF-31 nos `@DisplayName`), "All coverage checks have been met", `OpenApiContractTest` 4/4 |
| CA7 | PASSOU | seção no `backend/README.md`; `git diff --check` limpo; AGENTS.md sem alteração |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS; `git diff --check` limpo
Achados:
- sugestão, código, RouteIsolationModelExampleIT.modelCatchesMissingPreAuthorize — não roda os cenários do molde contra a rota `Unprotected`; só reproduz a escrita do perfil negado. Rodar `deniedProfile` contra ela e esperar falha daria prova mais direta.
- sugestão, especificação, cabeçalho do item cita branch `item/15-teste-modelo-isolamento`, mas a branch é `item/15-isolation-permission-model-test`.

## Entrega

Entrega o teste-modelo reutilizável de isolamento entre organizações e permissão no servidor (`RouteIsolationModelIT`), um exemplo que o exercita (`RouteIsolationModelExampleIT`, 7 testes) e a seção "Teste-modelo de isolamento e permissão" no `backend/README.md`. Nenhuma rota de produto, OpenAPI ou MSW.

Verificação: rodada 1 OK; `./backend/mvnw -f backend/pom.xml verify` com 74 testes rápidos e 36 `*IT` verdes e cobertura atendida.

Limitações: a prova de falha cobre a rota sem `@PreAuthorize`; filtro e RLS não foram removidos para provar falha. O cabeçalho do item cita um nome de branch diferente do real (`item/15-isolation-permission-model-test`).
