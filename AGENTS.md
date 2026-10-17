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
- Dados fictícios (#12), com o ambiente de pé e o back-end já rodado uma vez (migrações): `bash infra/scripts/seed.sh`. Idempotente; cada item que cria tabela acrescenta `infra/seed/NN-nome.sql` (`on conflict do nothing`); o `SeedDataIT` os aplica no CI.
- Contrato da API: `docs/openapi.yaml`; rota nova entra nele no mesmo PR. Teste de contrato (RNF-32): `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` (o segundo reprova rota de controller que não esteja no contrato).
- Front-end (de dentro de `frontend/`, Node 24; detalhes em `frontend/README.md`): instalar `npm ci`; subir `npm run dev`; build `npm run build`; lint (inclui texto literal) `npm run lint`; tipos `npm run typecheck`; testes `npm test`; cobertura (exige 80%) `npm run test:cov`; ponta a ponta `npm run e2e` (só `@critical`) e `npm run e2e:full` (primeira vez: `npx playwright install chromium`); dependências `npm run audit`.
- Regressão local e o que o CI exige (`.github/workflows/ci.yml`, status `ci-gate`): `./backend/mvnw -f backend/pom.xml verify` (JaCoCo: 80% de linhas; 100% nos pacotes `billing`, `payment`, `subscription`, `deactivation`, `isolation`) e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` (100% em `src/features/{billing,payments,subscription,deactivation}`). O CI ainda roda gitleaks e OSV-Scanner a cada PR (reprova nota 7 ou mais e alerta sem nota; filtro em `infra/scripts/osv-high.sh`, prova `bash infra/scripts/osv-high.test.sh`); no PR para `main` roda tudo, com `e2e:full`. Seleção por pasta: `bash infra/scripts/changed-areas.test.sh`.
- Imagens (#11): `docker build -t ctcore-backend backend` e `docker build -t ctcore-frontend frontend`; o CI as constrói quando a pasta muda e exige no `ci-gate`. Subir e provar: README, seção "Imagens e Publicação". Publicação (`publish.yml`, pulada sem chaves): `PUBLISH_REGISTRY`, `PUBLISH_REGISTRY_USER`, `PUBLISH_REGISTRY_TOKEN`; teste local do envio: `bash infra/scripts/publish-image.sh <backend|frontend> <branch>` contra `registry:2`.
- Banco do back-end (#14): antes de iniciar o back-end local (e depois de `down -v`), `bash infra/scripts/prepare-runtime-db.sh` cria o papel operacional sem superusuário e sem `BYPASSRLS`. O back-end usa `DB_RUNTIME_USER/PASSWORD`; o Flyway usa `DB_MIGRATION_USER/PASSWORD` (padrão: `POSTGRES_USER/PASSWORD`). Nunca rodar o back-end com credencial administrativa.

## Docker local

- Imagens definitivas, que ficam na máquina: as do `infra/docker-compose.yml` (`postgres:16.4`, `chrislusf/seaweedfs:3.80`, `axllent/mailpit:v1.20.5`) e as dos testes de integração (`postgres:16.4-alpine`, `testcontainers/ryuk`). Mudou uma versão no compose ou nos `*IT`, a lista muda junto e a imagem da versão antiga é removida.
- Imagens do próprio projeto, que podem ser reutilizadas (`ctcore-backend` e `ctcore-frontend`), podem ficar na máquina, mas só uma cópia por nome: ao reconstruir, a versão anterior, que vira `<none>`, é removida pelo ID. Para isso, comparar `docker images` antes e depois do build e remover só o `<none>` que o build deixou; nunca outro `<none>`, que pode ser de outro projeto. Etiquetas de prova da mesma imagem não se acumulam: reutilizar a mesma etiqueta ou remover as anteriores.
- Toda outra imagem que a tarefa construir ou baixar é descartável por padrão e sai ao fim da prova que a usou: registro local (`registry:2`), imagens enviadas a ele (`localhost:5000/...`) e ferramentas rodadas por Docker (ex.: `rhysd/actionlint`). Containers de prova usam `docker run --rm`. Se a prova deixou container ou rede, eles saem também; volume de prova tem nome explícito ou vai com `--rm`, nunca anônimo.
- Remover só pelo nome ou ID exato do que a tarefa criou (`docker rm -f <container>`, `docker rmi <imagem:etiqueta ou ID>`). Nunca usar `docker system prune`, `docker image prune -a` nem `docker volume prune`, porque apagam coisas de outros projetos da máquina. Nunca apagar os volumes `ctcore_postgres-data` e `ctcore_storage-data`.
- A linha de prova registra a limpeza, ex.: "registro e imagens `localhost:5000/teste/*` removidos; `ctcore-backend` reconstruída, versão anterior removida".

## Limitações aceitas

- Até a Sprint 22 não há hospedagem: não existe ambiente publicado. Tudo roda com o docker-compose e no CI do GitHub. A publicação está preparada na #11 e é ligada na #192.

## Issues

- Conta do agente no `gh`: `DevDosAnjos`. Nenhuma issue tem responsável hoje; o que é de pessoa é marcado pela etiqueta `depende de você`.
- Etiquetas: `back-end`, `front-end`, `infra`, `operação` (área); `entrega 1 · produto`, `entrega 2 · conexões`, `entrega 3 · piloto e lançamento` (entrega); `depende de você` (item de pessoa). Todas as demais issues são trabalho do agente. Não há issue de roteiro nem de decisão.
- Atribuir a issue ao começar: não. Fechar no merge: sim (`Closes #N`). Parar ao fim de cada sprint: sim.
