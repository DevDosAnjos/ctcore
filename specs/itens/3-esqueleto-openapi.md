# 3 — Esqueleto do OpenAPI em /api/v1

Origem: issue #3 (Sprint 00 · Fundação). Branch: `item/3-esqueleto-openapi`.

## 1. Objetivo

Criar `docs/openapi.yaml`, a fonte única do contrato da API, com o padrão que toda rota seguinte copia: nomes, paginação (25, 50 ou 100, com total), erros e datas. Entrega também o suporte mínimo no back-end (parâmetros e resposta de página) e um teste que confere as respostas reais contra o arquivo (RNF-32). Serve a todos os itens de API e aos handlers MSW (#7, #8).

Fora do escopo: rotas de negócio (cada item publica as suas no arquivo), handlers MSW, tela de lista e filtros no endereço (RNF-02, parte de front), esteira de CI (#10), documentação navegável publicada, autenticação e permissões (#18, #27).

## 2. Requisitos

- R1: existe `docs/openapi.yaml` (OpenAPI 3.0.3, válido) com servidor base `/api/v1`, `paths` vazio e `components` reutilizáveis (D1, D2).
- R2: convenções de nomes documentadas no próprio arquivo e seguidas pelos schemas: caminhos em plural e `kebab-case`, JSON em `camelCase`, enums em `UPPER_SNAKE_CASE`, ids `uuid`, valores em centavos como inteiros com sufixo `Cents` (D3).
- R3: datas e horas: instante é `date-time` ISO-8601 em UTC com `Z`; data de calendário é `date` (`AAAA-MM-DD`); o fuso da unidade só se aplica na exibição (D4).
- R4: erro: o schema `Error` descreve exatamente o corpo de `ApiError` (`code`, `message`, `changed`, `fields`, `traceId`), com o enum de códigos, e há respostas reutilizáveis para 400, 401, 403, 404, 405, 409, 415 e 500 (D5).
- R5: paginação: parâmetros reutilizáveis `page` (a partir de 1, padrão 1) e `size` (25, 50 ou 100, padrão 25) e envelope `{ items, page, size, total }`; `size` fora dos três valores ou `page` menor que 1 responde 400 `VALIDATION_ERROR` com o campo, sem ajustar em silêncio (D6).
- R6: ordenação: parâmetro reutilizável `sort` no formato `campo,asc|desc`; cada rota declara a lista permitida de campos (D7).
- R7: o back-end tem um `PageRequest` (leitura e validação de `page`, `size`, `sort` contra a lista permitida) e um `PageResponse<T>` no formato do envelope, para todas as listas reutilizarem.
- R8: parâmetro obrigatório ausente, parâmetro com tipo errado (ex.: `size=abc`) e tipo de mídia não suportado deixam de cair em 500: respondem 400 `VALIDATION_ERROR` e 415 `UNSUPPORTED_MEDIA_TYPE` no formato de R4 (achado da verificação da #2; necessário porque `size=abc` é entrada de paginação).
- R9: (RNF-32) teste de contrato: as respostas reais de erro e de página produzidas pelo back-end são validadas contra os schemas de `docs/openapi.yaml`, e o próprio arquivo é validado como OpenAPI.
- R10: `AGENTS.md` e `docs/README.md` dizem onde está o OpenAPI e como rodar o teste de contrato; regra de fluxo: rota nova entra no arquivo no mesmo PR.

## 3. Critérios de aceite

- CA1 (R1): dado `docs/openapi.yaml`, quando o teste o carrega com o validador de OpenAPI, então não há erro de formato e `servers[0].url` é `/api/v1`.
- CA2 (R2, R3): dado o arquivo, quando o teste confere os schemas, então a descrição de convenções existe e os campos de data usam `format: date-time` ou `date`, e `Page` não tem campo em `snake_case`.
- CA3 (R4): dado um corpo de erro real de validação (400), um 404 e um 500 gerados pelo back-end, quando validados contra o schema `Error`, então todos conferem; um corpo sem `traceId` ou com `code` fora do enum é recusado pelo mesmo schema.
- CA4 (R4): dado o arquivo, quando se listam `components.responses`, então existem as respostas para 400, 401, 403, 404, 405, 409, 415 e 500, todas apontando para `Error`.
- CA5 (R5, R7): dado um controlador de teste de lista com `PageRequest`, quando se chama sem parâmetros, então devolve `page=1`, `size=25` e `total` correto; com `size=50` e `size=100`, aceita; com `page=2`, devolve o recorte certo.
- CA6 (R5): dado o mesmo controlador, quando se chama com `size=10`, `size=0`, `page=0` ou `page=-1`, então a resposta é 400 `VALIDATION_ERROR` com `fields` apontando `size` ou `page` e `changed = false`.
- CA7 (R5, R9): dado o envelope devolvido pelo controlador de teste, quando validado contra o schema `Page` do arquivo, então confere; um envelope sem `total` é recusado.
- CA8 (R6, R7): dado `sort=name,desc` com `name` permitido, então o `PageRequest` expõe campo e direção; com `sort=password,asc` (fora da lista) ou `sort=name,up`, a resposta é 400 `VALIDATION_ERROR`.
- CA9 (R8): dado o controlador de teste, quando se chama com `size=abc`, sem parâmetro obrigatório, e com corpo `text/plain` onde se espera JSON, então as respostas são 400, 400 e 415, no formato de R4 e sem pilha no corpo.
- CA10 (R9): dado o conjunto de testes, quando se confere o mapa de códigos da issue, então RNF-32 tem ao menos um teste, com o código no nome ou no `@DisplayName`.
- CA11 (R10): dado o `AGENTS.md`, quando se lê a seção "Comandos" e o `docs/README.md`, então trazem o caminho do OpenAPI e o comando do teste de contrato.

## 4. Plano técnico

- Dados: não muda.
- API: sem rota de negócio. `docs/openapi.yaml` a criar com: `info`, `servers: [/api/v1]`, `paths: {}`, `components.schemas` (`Error`, `FieldError`, `Page` como base do envelope, com a regra de que cada lista declara `items` próprio por composição), `components.parameters` (`Page`, `Size`, `Sort`), `components.responses` (400, 401, 403, 404, 405, 409, 415, 500) e `components.securitySchemes` (cookie de sessão e cabeçalho `X-XSRF-TOKEN`, só descritos; o login é da #18). Nova resposta de erro 415 com código `UNSUPPORTED_MEDIA_TYPE`. Quem pode chamar: não se aplica.
- Interface: não muda (os handlers MSW da #7 e #8 partirão deste arquivo).
- Segurança: não se aplica a dados; a lista de campos permitidos em `sort` evita ordenar por coluna sensível ou injetar nome de coluna; `size` limitado a 100 impede lista sem limite. Nenhum dado pessoal.
- Arquivos: a alterar (existem): `backend/pom.xml` (dependência de teste do validador de OpenAPI), `backend/src/main/java/br/com/ctcore/api/error/ErrorCode.java` (acrescenta `UNSUPPORTED_MEDIA_TYPE`), `ApiExceptionHandler.java` (parâmetro ausente, tipo errado e 415), `backend/src/main/resources/messages.properties` (mensagem do novo código), `AGENTS.md` (Comandos), `docs/README.md`. A criar: `docs/openapi.yaml`; `backend/src/main/java/br/com/ctcore/api/page/PageRequest.java` e `PageResponse.java`; testes em `backend/src/test/java/br/com/ctcore/api/`: `page/PageRequestTest`, `page/PageControllerTest` (controlador de teste com MockMvc), `contract/OpenApiContractTest` (apoio: leitura de `../docs/openapi.yaml`, caminho relativo a `backend/`).
- Reuso: `ApiError`, `ErrorCode`, `ApiExceptionHandler` e `messages.properties` da #2; MockMvc e padrão de controlador de teste de `ApiExceptionHandlerTest`.
- Riscos: sem rota real no arquivo, o teste valida os schemas e não caminhos; a validação por rota entra com a primeira rota publicada (regra de R10). Versão da biblioteca do validador (swagger-request-validator ou swagger-parser) precisa existir no Maven Central e ser fixada no `pom.xml`, pois o BOM do Spring Boot não a gerencia. OpenAPI 3.0.3 foi escolhido por suporte maduro nas ferramentas; se o gerador do MSW exigir 3.1, migra-se na #7. Mudar `size`/`page` depois que o front consumir exige conferir os consumidores.

## 5. Tarefas

- [x] T1: criar `docs/openapi.yaml` com `info`, `servers`, `paths: {}`, schemas `Error`/`FieldError`/`Page`, parâmetros `Page`/`Size`/`Sort`, respostas de erro e descrição das convenções (nomes, datas, centavos, paginação, ordenação) — cobre CA1, CA2, CA4 — prova: `OpenApiContractTest` (arquivo válido, servidor `/api/v1`, respostas e formatos de data).
- [x] T2: acrescentar `UNSUPPORTED_MEDIA_TYPE` (415) em `ErrorCode` e `messages.properties`, e tratar parâmetro ausente, tipo errado e 415 em `ApiExceptionHandler` — cobre CA9 — prova: teste do tratador/`PageControllerTest` com `size=abc`, sem parâmetro obrigatório e `text/plain`.
- [x] T3: criar `PageRequest` (leitura de `page`, `size`, `sort`; erro de validação com o campo no formato da #2) e `PageResponse<T>` — cobre CA5, CA6, CA8 — prova: `PageRequestTest` e `PageControllerTest`.
- [x] T4: adicionar a dependência de teste do validador de OpenAPI ao `pom.xml` (versão fixada, confirmada no Maven Central) e escrever `OpenApiContractTest` que valida erros reais (400, 404, 500) e o envelope de página contra os schemas do arquivo, com casos negativos — cobre CA3, CA7, CA10 — prova: `./backend/mvnw -f backend/pom.xml verify` com `OpenApiContractTest` verde, nomeado com RNF-32.
- [x] T5: atualizar `docs/README.md` (local do OpenAPI e regra de rota nova no mesmo PR) e a seção "Comandos" do `AGENTS.md` (teste de contrato) — cobre CA11 — prova: leitura dos dois arquivos e `verify` passando.

## 6. Decisões

- D1: um único arquivo `docs/openapi.yaml`, em YAML, OpenAPI 3.0.3, sem rotas por enquanto. Motivo: o AGENTS.md manda o contrato em `docs/` e a issue pede só o esqueleto; cada item acrescenta suas rotas. Alternativa: gerar o contrato a partir do código (springdoc), descartada porque o arquivo precisa ser a fonte e vir antes do código e dos handlers MSW.
- D2: sem rota de exemplo no arquivo de produção. Motivo: rota "ping" seria contrato sem uso. Consequência: o teste de contrato valida schemas, e a validação por caminho começa na primeira rota real.
- D3: nomes em inglês (código, rotas e campos), JSON `camelCase`, rotas em plural e `kebab-case`, ids UUID, centavos em inteiro com sufixo `Cents`. Motivo: AGENTS.md (código em inglês), `projeto.md` (valores em centavos) e uso comum no ecossistema Java/TypeScript. Alternativa: `snake_case` no JSON, descartada por exigir conversão no TypeScript.
- D4: instantes em `date-time` UTC com `Z` e datas de calendário em `date`; hora sem data de aula (horário de turma) será definida pelo item de turmas, em `time` com o fuso da unidade. Motivo: coerente com a D4 da #2 e RNF-23.
- D5: o schema `Error` reproduz o `ApiError` da #2 sem mudar o formato; a #2 já registrou que o contrato fica no OpenAPI desta issue.
- D6 (muda o comportamento): `page` começa em 1 e `size` aceita só 25, 50 ou 100; valor inválido é erro 400 e não é corrigido em silêncio. Motivo: RNF-02 fixa 25/50/100 com total, e página 1-based combina com o endereço da tela que o usuário compartilha. Alternativa: `page` a partir de 0 e `size` limitado ao máximo de 100 (padrão Spring), descartada porque aceitaria tamanhos que a tela não oferece.
- D7: ordenação por `sort=campo,asc|desc` com lista de campos permitidos por rota; sem `sort`, a rota define uma ordem estável padrão. Motivo: ordem estável evita repetir ou perder linhas entre páginas, e a lista evita ordenar por coluna interna. Alternativa: ordenação livre do Spring Data, descartada por segurança.
- D8: o item inclui tratar parâmetro ausente, tipo errado e 415 (hoje viram 500, achado da verificação da #2), porque `size=abc` é entrada de paginação e o contrato de erro deve ser completo. Motivo: pequeno e necessário. Alternativa: deixar para a primeira rota de negócio, descartada porque deixaria o contrato dizendo 400 e o servidor respondendo 500.
- Suposição: o filtro e a página "no endereço da tela" (RNF-02) são do front (#8, #9 e telas); aqui só se define o contrato dos parâmetros. Se o dono quiser `cursor` em vez de `page` para alguma lista grande, muda-se em item próprio.
- Suposição: a lista de erros do contrato cobre só os códigos que a #2 e este item criam; novos códigos entram no enum no item que os cria.

## Construção

- T1 — arquivos: `docs/openapi.yaml` — prova: `./backend/mvnw -f backend/pom.xml test` → `OpenApiContractTest` (fileIsValid, conventions) verde
- T2 — arquivos: `ErrorCode`, `messages.properties`, `ApiExceptionHandler`, `ApiValidationException` (novo) — prova: `PageControllerTest.parameterErrors` → verde (400, 400, 415)
- T3 — arquivos: `api/page/PageRequest.java`, `PageResponse.java`, `PageRequestTest`, `PageControllerTest` — prova: `test` → 7 testes verdes
- T4 — arquivos: `backend/pom.xml` (swagger-parser 2.1.48 e json-schema-validator 1.5.9, escopo test, versões conferidas no Maven Central), `OpenApiContractTest` (RNF-32, com casos negativos) — prova: `test` → 4 testes verdes
- T5 — arquivos: `docs/README.md`, `AGENTS.md` (Comandos) — prova: leitura e `verify` verde
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 24 testes rápidos e 5 de integração, 0 falhas, BUILD SUCCESS (linha de base da #2: 13 rápidos e 5 de integração).
Desvios: `PageRequest.of(page, size, sort, allowed)` é chamado pelo controlador com `@RequestParam(required = false)` em vez de argument resolver, e erro de entrada sai por `ApiValidationException` (nova, no pacote `error`); o plano não a citava. `PageControllerTest` e seu `Probe` ficaram públicos para o teste de contrato reaproveitá-los.

## Verificação

### Rodada 1 — OK — 2026-10-15, estado d886d09ab9c8d1829efd3040876d1c78f7b61a08

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `OpenApiContractTest.fileIsValid` verde (parser sem mensagens, servidor `/api/v1`) |
| CA2 | PASSOU | `conventions` verde: descrição com camelCase/centavos/date-time/sort; `Page` sem `_`. Não há campo de data no arquivo ainda, então o formato de data não é exercitado (sem campo a conferir) |
| CA3 | PASSOU | `errorsMatchSchema` verde: 400, 404 e 500 reais conferem; sem `traceId` e `code` fora do enum recusados |
| CA4 | PASSOU | `conventions` verde: 8 respostas, todas com `$ref` para `Error`; enum igual a `ErrorCode.values()` |
| CA5 | PASSOU | `PageControllerTest.defaults` e `sizesAndSlice` verdes |
| CA6 | PASSOU | `invalidPaging` verde (size=10/0, page=0/-1; 400, `changed=false`, campo) |
| CA7 | PASSOU | `pageMatchesSchema` verde; sem `total` recusado |
| CA8 | PASSOU | `invalidPaging` (sort=password,asc e name,up dão 400; name,desc dá 200); `PageRequestTest` 3 verdes |
| CA9 | PASSOU | `parameterErrors` verde: 400, 400, 415, sem pilha |
| CA10 | PASSOU | quatro testes com "RNF-32" no `@DisplayName` |
| CA11 | PASSOU | diff de `AGENTS.md` (Comandos) e `docs/README.md` traz caminho e comando |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 24 testes rápidos e 5 de integração, 0 falhas, BUILD SUCCESS (linha de base da #2: 13 e 5).
Achados:
- sugestão, código, `docs/openapi.yaml` `Page.items` — `items: {}` aceita qualquer coisa; ok para base, cada rota deve compor.

## Entrega

- Cria `docs/openapi.yaml` (OpenAPI 3.0.3, servidor `/api/v1`, sem rotas) com schemas `Error`, `FieldError` e `Page`, parâmetros `Page`/`Size`/`Sort`, respostas de erro e convenções (camelCase, centavos, datas).
- Back-end: `PageRequest` e `PageResponse` (página a partir de 1; `size` 25, 50 ou 100; `sort` com lista de campos permitidos), novo código `UNSUPPORTED_MEDIA_TYPE` (415) e tratamento de parâmetro ausente e tipo errado como 400 em vez de 500.
- Verificação: `./backend/mvnw -f backend/pom.xml verify` com 24 testes rápidos e 5 de integração, 0 falhas; teste de contrato (RNF-32) valida erros e página reais contra o arquivo.
- Decisões: `page` 1-based e `size` fixo em 25/50/100 (D6); validação por caminho só entra com a primeira rota real.
- Limitação: `Page.items` é aberto; cada lista compõe o seu.
