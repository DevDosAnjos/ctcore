# docs

- `especificacao/`: a regra de cada código (RN, RF, RNF). Mudou uma regra, muda primeiro aqui.
- `plano-de-sprints.md`: ordem, qualidade e definição de pronto.
- `ctcore-especificacao-1.2.pdf`: registro da versão 1.2.
- `openapi.yaml`: OpenAPI 3.0.3, fonte única do contrato da API (`/api/v1`): convenções, paginação, ordenação e erros. Rota nova entra neste arquivo no mesmo PR que a cria. Teste de contrato (RNF-32): `./backend/mvnw -f backend/pom.xml test -Dtest=OpenApiContractTest`.
