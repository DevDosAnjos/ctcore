# AGENTS.md · ctcore

Regras para todo agente que trabalha neste repositório. Valem em toda sessão, para todo item.

## Projeto

Plataforma SaaS de gestão para academias de artes marciais. Monorepo:

| Pasta | O que tem |
| --- | --- |
| `backend/` | Java 21, Spring Boot 3, Maven, Flyway, PostgreSQL com row-level security, API REST em `/api/v1` |
| `frontend/` | TypeScript, React, Vite, shadcn/ui, Tailwind CSS, Radix, MSW |
| `infra/` | docker-compose (PostgreSQL, arquivos compatíveis com S3, caixa de e-mail falsa), scripts e publicação |
| `docs/` | especificação e OpenAPI |

## Fontes

- **O que fazer em cada item:** a issue no GitHub (`gh issue view <número>`). Ela traz o que entregar, as regras com o texto completo, as telas do canvas e o "Pronto quando".
- **A regra de cada código** (RN, RF, RNF): `docs/especificacao/`. Mudou uma regra, muda primeiro a especificação.
- **Ordem, qualidade e definição de pronto:** `docs/plano-de-sprints.md`.
- O PDF `docs/ctcore-especificacao-1.2.pdf` é só registro da versão 1.2; não é preciso lê-lo.

## Backlog

- O backlog são as **issues do GitHub**. Cada issue aberta é um item; o ID do item é o número da issue (ex.: `12`).
- Ordem: marco (milestone) `Sprint NN · ...` em ordem crescente e, dentro dele, número da issue. Uma issue que diga "fazer antes da #X" vem antes dela.
- `specs/backlog.md`, se existir, espelha as issues abertas nessa ordem e aponta para elas; não copia o conteúdo. As issues prevalecem sobre ele.
- Issue com a etiqueta `depende de você` é item de pessoa: o agente não começa sem o dono liberar.
- Não criar issues de "decisão" nem de "bloqueio". Dúvida vira decisão registrada no arquivo do item (opção recomendada e motivo); só vai para o dono o que exige dinheiro, conta, credencial, contrato ou regra de negócio que a especificação não define.

## Branches e entrega

- **Base: `dev`.** Todo código é escrito a partir de `dev`.
- Cada item sai de `dev` num branch `item/<número>-slug` e volta para `dev` por **um único PR**.
- Commit do item: `#<número>: <título da issue>`. A descrição do PR traz `Closes #<número>`, para a issue fechar no merge.
- **O agente faz o push, abre o PR e faz o merge em `dev` por squash**, só com as verificações do PR verdes.
- **`main` é produção e é do dono.** O agente nunca faz commit, PR de item nem merge em `main`. No fim de cada sprint, o dono revisa a sprint inteira e faz o PR de `dev` para `main` com o método "Merge" (nunca squash).
- Nada de `push --force`, `rebase`, `reset --hard` ou `stash`.

## Parada no fim da sprint

Antes de começar um item, comparar o marco dele com o marco do último item mergeado em `dev`. Se for de outra sprint, **PARAR**: a sprint acabou e o dono vai revisá-la. Só seguir quando o dono disser que a sprint foi revisada.

## Regras que valem para todo item

- Isolamento entre organizações nas duas camadas: filtro no código e row-level security no PostgreSQL. Toda rota nova tem teste de isolamento entre organizações e entre perfis.
- Permissão conferida no servidor em toda rota; o front nunca é a única barreira.
- Toda ação que altera dado grava na auditoria (sem senha, token ou dado de cartão).
- Valores em centavos; operações financeiras idempotentes; lotes gravam tudo ou nada.
- Conexões externas só pela porta e pela versão simulada até as Sprints 22 e 23; nenhuma regra cita fornecedor.
- O OpenAPI em `docs/` é a fonte única do contrato; os handlers MSW batem com ele.
- Telas com os quatro estados (carregando, vazio, erro, sem permissão), funcionando a partir de 360 px e passando no axe nos dois temas. Celular é a mesma aplicação responsiva, não telas novas.
- Textos de tela nos arquivos de tradução pt-BR, nunca no código.
- Todo código de regra citado na issue tem ao menos um teste. Cobertura mínima de 80%; 100% das regras de cobrança, pagamentos, assinatura, inativação e isolamento.

## Idioma

- Respostas, issues, PRs, especificações e textos de tela em português do Brasil.
- Código, nomes de classes, tabelas, rotas e mensagens de commit técnicas em inglês. O título do commit do item segue o título da issue.

## Comandos

- Subir o ambiente local (PostgreSQL, armazenamento S3, caixa de e-mail): `docker compose -f infra/docker-compose.yml up -d --wait` (ou `docker compose up -d --wait` dentro de `infra/`). Parar: `down`; apagar dados: `down -v`. Portas e endereços: README da raiz.
- Back-end (Docker ativo para os `*IT`; detalhes em `backend/README.md`): compilar `./backend/mvnw -f backend/pom.xml compile`; testes rápidos `./backend/mvnw -f backend/pom.xml test`; testes completos (rápidos e de integração) `./backend/mvnw -f backend/pom.xml verify`; rodar `./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local`.
- Contrato da API: `docs/openapi.yaml`; rota nova entra nele no mesmo PR. Teste de contrato (RNF-32): `./backend/mvnw -f backend/pom.xml test -Dtest=OpenApiContractTest`.
- A definir nas fundações #7 e #10: testes do front-end, ponta a ponta e regressão.

## Limitações aceitas

- Até a Sprint 22 não há hospedagem: não existe ambiente publicado. Tudo roda com o docker-compose e no CI do GitHub. A publicação está preparada na #11 e é ligada na #192.

## Issues

- Conta do agente no `gh`: `DevDosAnjos`. Nenhuma issue tem responsável hoje; o que é de pessoa é marcado pela etiqueta `depende de você`.
- Etiquetas: `back-end`, `front-end`, `infra`, `operação` (área); `entrega 1 · produto`, `entrega 2 · conexões`, `entrega 3 · piloto e lançamento` (entrega); `depende de você` (item de pessoa). Todas as demais issues são trabalho do agente. Não há issue de roteiro nem de decisão.
- Atribuir a issue ao começar: não. Fechar no merge: sim (`Closes #N`). Parar ao fim de cada sprint: sim.
