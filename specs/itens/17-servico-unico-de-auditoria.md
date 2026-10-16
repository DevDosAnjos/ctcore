# 17 — Serviço único de auditoria

Origem: issue #17; `docs/especificacao/03-auditoria-e-lgpd.md` (RN-AUD-01 a RN-AUD-03, RN-LGP-11, registro de auditoria). Branch: `item/17-servico-unico-de-auditoria`.

## 1. Objetivo

Dar ao back-end o único ponto de gravação da auditoria: tabela `audit_log` só de inclusão no PostgreSQL e o serviço `AuditService`, que toda operação que altera dado chamará (os itens seguintes), com os valores antes e depois e sem senha, token ou dado de cartão. A organização vem do contexto da #14 e a gravação acontece na mesma transação da alteração.

Fora do escopo: rota HTTP de consulta, tela e visibilidade por perfil (RN-AUD-05); OpenAPI e MSW (nenhuma rota nasce); chamadas nas operações existentes (só `organization/Organizations` altera dado hoje e é provisionamento interno, sem usuário; cada item futuro chama o serviço); retenção de 5 anos (RN-AUD-04: nada apaga, não há rotina de exclusão); aceites e pedidos de titular (tabelas próprias, itens de LGPD); logs técnicos (já entregues na #5).

## 2. Requisitos

- R1: RN-AUD-01: `audit_log` aceita só inserção; `update`, `delete` e `truncate` são recusados pelo banco para qualquer usuário, inclusive o de migração (D1).
- R2: RN-AUD-02: cada registro guarda organização, unidade (opcional), quem (tipo de autor: conta, sistema, portal, site ou suporte da plataforma; e conta e perfil quando houver), ação, entidade, identificador, valores antes e depois só dos campos alterados, motivo (opcional), IP (opcional) e data e hora em UTC.
- R3: RN-AUD-02: `AuditService` é o único caminho de gravação e participa da transação da operação que audita: se a operação desfaz, o registro desfaz; sem transação aberta, recusa (D2).
- R4: RN-AUD-03: chaves de senha, token, segredo, chave de API e dado de cartão (número, validade, código de segurança) são removidas dos valores antes e depois, em qualquer nível, antes de gravar (D3).
- R5: A organização do registro vem de `OrganizationContext`, nunca de parâmetro; a unidade informada precisa pertencer à organização (RN-ORG-02, D4).
- R6: Isolamento: `audit_log` tem row-level security por organização: uma organização não lê nem insere registro de outra (RNF-06).
- R7: Ações da plataforma sem organização (suporte e dono) gravam com organização vazia, só quando o tipo de autor for da plataforma; para os demais tipos, sem organização no contexto a gravação é negada (D4).
- R8: RN-LGP-11: o serviço não escreve valores antes e depois nem dado pessoal no log técnico; a guarda de 30 dias do log já vale (#5).

## 3. Critérios de aceite

- CA1 (R1): dado um registro gravado, quando se tenta `update`, `delete` ou `truncate` na tabela com o usuário do runtime e com o de migração, então o banco recusa e o registro permanece intacto.
- CA2 (R2): dado o contexto da organização A, quando grava ação `student.update` com antes `{nome: "Ana", faixa: "branca"}` e depois `{nome: "Ana", faixa: "azul"}`, então a linha tem organização A, autor, ação, entidade, identificador, data em UTC do relógio da aplicação, e antes `{faixa: "branca"}` e depois `{faixa: "azul"}`.
- CA3 (R2): dada a criação (só depois) e a exclusão (só antes), quando grava, então o campo ausente fica vazio e todos os campos do lado presente são guardados.
- CA4 (R3): dada uma operação que grava auditoria e depois falha, quando a transação desfaz, então não sobra registro; fora de transação, o serviço lança erro e não grava.
- CA5 (R4): dado antes/depois com `password`, `senha`, `token`, `accessToken`, `secret`, `apiKey`, `cardNumber`, `cvv` e `expiry`, inclusive aninhados em mapa e lista, quando grava, então nenhuma dessas chaves nem seu valor aparece na linha, e os demais campos permanecem.
- CA6 (R5): dado o contexto da A, quando informa unidade da B, então lança acesso negado e nada é gravado; sem organização no contexto e autor de conta, lança acesso negado.
- CA7 (R6): dados registros de A e B, quando o runtime lê no escopo da A, então vê só os da A; no escopo da B, tentar inserir com organização A é recusado pelo banco; sem escopo, não lê nenhum.
- CA8 (R7): dado autor suporte da plataforma sem organização no contexto, quando grava, então a linha fica com organização vazia; com autor conta (ou portal, site, sistema), a mesma chamada é negada.
- CA9 (R8): dada uma gravação com valores sensíveis, quando se captura o log da aplicação, então ele contém no máximo ação, entidade e identificador, e nenhum valor de antes ou depois.

## 4. Plano técnico

- Dados: migração `V5__audit_log.sql` (a criar): tabela `audit_log` (`id uuid pk default gen_random_uuid()`, `organization_id uuid null references organization(id)`, `unit_id uuid null`, `actor_type text not null check in ('ACCOUNT','SYSTEM','PORTAL','SITE','PLATFORM_SUPPORT','PLATFORM_OWNER')`, `actor_id uuid null`, `actor_profile text null`, `action text not null`, `entity_type text not null`, `entity_id text not null`, `before_data jsonb null`, `after_data jsonb null`, `reason text null`, `ip text null`, `occurred_at timestamptz not null`; chave composta `(organization_id, unit_id)` para `unit(organization_id, id)`; check de que `organization_id` é nulo só para tipo `PLATFORM_*`). Gatilho `before update or delete` e `before truncate` que levanta exceção (vale para o dono da tabela). RLS ligado e forçado, com política de leitura por `app.organization_id` e de inserção que aceita organização igual à do escopo, ou organização nula com escopo vazio. Índices `(organization_id, occurred_at desc)` e `(organization_id, entity_type, entity_id)`. Recuperação: migração nova de correção; a tabela nasce vazia. Seed: não há (nenhum dado de negócio a auditar ainda; `infra/seed/` inalterado).
- API: não muda. Serviço interno, sem rota; OpenAPI e MSW intactos.
- Interface: não muda; quatro estados, tradução e axe não se aplicam.
- Segurança: organização só de `OrganizationContext.organizationId()`; remoção de chaves sensíveis em lista fixa, sem diferenciar maiúsculas, em mapas e listas aninhados; SQL só com parâmetros; o log do serviço traz ação, entidade e id. O que entra na auditoria é dado de negócio e, por isso, pode conter dado pessoal (RN-LGP-05/06: a anonimização do aluno terá de tratar `before_data`/`after_data`; fica para o item de anonimização, que precisará de exceção controlada ao gatilho, citada como risco).
- Arquivos a criar em `backend/src/main/java/br/com/ctcore/audit/`: `AuditEntry` (registro imutável: autor, ação, entidade, id, unidade, antes, depois, motivo, IP), `AuditActor` (tipo, conta, perfil) e `AuditService`. Serão alterados: nenhum arquivo de código existente; `backend/README.md` (seção "Auditoria": como chamar). Migração a criar: `backend/src/main/resources/db/migration/V5__audit_log.sql`. Testes a criar em `backend/src/test/java/br/com/ctcore/audit/`: `AuditSanitizerTest` (rápido: remoção de chaves, diff antes/depois; CA2, CA3, CA5) e `AuditServiceIT` (banco real: CA1, CA2, CA4 a CA9).
- Reuso: `OrganizationContext` e `OrganizationTransactions` (`isolation/`), `Clock` de `ClockConfig`, `JdbcClient`, `PostgresIT` (`db/`; já cria o papel `ctcore_app` sem bypass de RLS) e o padrão de captura de log de `logging/LogOutputTest`, `ObjectMapper` do Spring (Jackson já na dependência).
- Riscos: o gatilho bloqueia também a futura anonimização de `before_data`/`after_data` (decidir no item de LGPD); `jsonb` cresce se o chamador passar objeto inteiro (por isso grava só os campos alterados); `OrganizationTransactions.execute` já isola a transação, o `AuditService` só participa dela (`Propagation.MANDATORY`); o JaCoCo exige 80% global e a política de RLS precisa do IT.

## 5. Tarefas

- [x] T1: Migração `V5__audit_log.sql`: tabela, check de organização, gatilhos de só inclusão (update, delete, truncate), RLS e índices — cobre CA1, CA7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AuditServiceIT` (testes de banco) e `MigrationIT`
- [x] T2: Criar `AuditEntry`, `AuditActor` e a lógica pura de remoção de chaves sensíveis e de diff antes/depois — cobre CA2, CA3, CA5 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=AuditSanitizerTest`
- [x] T3: Criar `AuditService` (`@Transactional(MANDATORY)`, organização do contexto, validação de unidade e regra de organização vazia, relógio, log mínimo) — cobre CA2, CA4, CA6, CA8, CA9 — prova: `AuditServiceIT`
- [x] T4: Testes de banco e isolamento: update, delete e truncate recusados (runtime e migração), RLS entre A e B, rollback junto com a operação, log sem valores — cobre CA1, CA4, CA7, CA9 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AuditServiceIT`
- [x] T5: Documentar no `backend/README.md` e rodar a regressão completa — cobre todos (regressão, cobertura, `OpenApiContractTest`) — prova: `./backend/mvnw -f backend/pom.xml verify`

## 6. Decisões

- D1: Só inclusão garantida por gatilho no banco (update, delete e truncate), não só por permissão. Motivo: RN-AUD-01 diz "nem pela equipe da plataforma"; o `ctcore_app` recebe CRUD em todas as tabelas pelo script de preparação (`prepare-runtime-db.sh`), e o gatilho vale também para o dono. Alternativa descartada: só revogar `update/delete` do papel de runtime (não protege contra o usuário de migração e se perde nos privilégios padrão).
- D2: O serviço exige transação aberta e grava nela. Motivo: "toda operação que altera dado grava na auditoria" fica tudo ou nada (AGENTS.md), sem registro de operação que desfez. Alternativa descartada: gravar em transação própria (deixa registro órfão ou operação sem registro).
- D3: Remoção por nome de chave (lista fixa: password, senha, token, secret, apiKey, cardNumber, cvv, expiry e variantes), não mascaramento de valor. Motivo: RN-AUD-03 fala em nunca entrar; CPF, e-mail e nome são dado de negócio que a auditoria precisa mostrar, então o mascaramento de valor do log (`SensitiveDataMasker`) não se aplica aqui. Suposição: quem chama usa nomes de campo previsíveis; chave com nome novo e sensível exige acrescentar a regra e o teste.
- D4 (muda o comportamento): Sem organização no contexto, só autor da plataforma (suporte ou dono) grava, com organização vazia; as demais ações, inclusive as de portal e formulário público, exigem a organização, que essas rotas já terão resolvida. Motivo: RN-AUD-02 inclui ações da plataforma, que podem não pertencer a uma organização; o resto segue RN-ORG-02. Alternativa descartada: organização obrigatória sempre (perde ações como o preço de plano) ou opcional para todos (abre brecha de registro sem dono). Se uma ação pública não tiver organização, o item dela decide.
- D5: Leitura e visibilidade por perfil (RN-AUD-05) e a tela ficam para o item de consulta de auditoria; este item não cria rota, OpenAPI, MSW nem tela, porque a issue pede só o serviço no back-end. A gravação nas operações existentes não se aplica: o único código que altera dado hoje (`Organizations`) é provisionamento interno sem usuário; os itens com ação de usuário chamam o serviço.
- D6: Valores antes e depois guardados como `jsonb` só com os campos que mudaram (criação: só depois; exclusão: só antes). Motivo: RN-AUD-02 pede "valores antes e depois dos campos alterados" e evita guardar dado desnecessário. Alternativa descartada: guardar o objeto inteiro.
- D7: `entity_id` em texto e `ip` em texto. Motivo: entidades futuras podem ter chave que não é UUID; IP vem do cabeçalho tratado pela rota. Alternativa descartada: `uuid` e `inet` (restringem sem ganho agora).
- Suposição: o conjunto de ações auditáveis é livre (texto `entidade.verbo`), sem catálogo fechado; catálogo fechado exigiria enum e migração a cada recurso. Se o dono quiser catálogo, muda o tipo da coluna `action`.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V5__audit_log.sql` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AuditServiceIT` e `MigrationIT` (na regressão) → passou
- T2 — arquivos: `audit/AuditActor.java`, `audit/AuditEntry.java`, `audit/AuditSanitizer.java`, `AuditSanitizerTest` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=AuditSanitizerTest` → 3 testes, passou
- T3 — arquivos: `audit/AuditService.java` — prova: `AuditServiceIT` → passou
- T4 — arquivos: `AuditServiceIT` (7 testes: CA1 a CA9) — prova: `./backend/mvnw -f backend/pom.xml verify -Dtest=AuditSanitizerTest -Dit.test=AuditServiceIT` → 7 de 7 passaram
- T5 — arquivos: `backend/README.md` (seção "Auditoria") — prova: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 48 testes de integração sem falha, JaCoCo "All coverage checks have been met"
Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (inclui `OpenApiContractTest`). Front-end não alterado, não rodado.
Desvios: (1) a política de leitura de `audit_log` vale para select, update e delete (sem `for select`), para o gatilho recusar com erro em vez de o RLS ignorar a linha em silêncio. (2) Truncate pelo runtime é recusado por falta de privilégio (o papel não tem `truncate`); o gatilho o recusa para o usuário de migração. (3) `SeedDataIT.clean` passou a desligar o gatilho e apagar `audit_log` (só em teste, usuário administrador), porque o contêiner é compartilhado e a chave composta de `audit_log` impediria `delete from unit` depois do `AuditServiceIT`. (4) `AuditSanitizer` é classe própria no pacote `audit` (o plano a citava como "lógica pura").
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-16, estado 4b5cd7fccef3d6014b3786cbf574d2a66152b720

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `AuditServiceIT.appendOnly`: update/delete/truncate recusados no runtime e no usuário de migração (mensagem "append-only"); contagem permanece 1 |
| CA2 | PASSOU | `AuditSanitizerTest.onlyChangedFields` e `AuditServiceIT.writesDiffWithoutSecrets` (linha com ação, entidade, id, autor, data) |
| CA3 | PASSOU | `AuditSanitizerTest.creationAndDeletion` e `AuditServiceIT.creationAndDeletion` |
| CA4 | PASSOU | `AuditServiceIT.joinsTransaction`: rollback sem registro; fora de transação `IllegalTransactionStateException` |
| CA5 | PASSOU | `AuditSanitizerTest.sensitiveKeysRemoved` (todas as chaves, aninhadas em mapa e lista) |
| CA6 | PASSOU | `AuditServiceIT.unitAndScope`: unidade da B e conta sem organização dão `AccessDeniedException`, nada gravado |
| CA7 | PASSOU | `AuditServiceIT.rowLevelSecurity`: B vê só os seus, inserção cruzada recusada, sem escopo 0 linhas |
| CA8 | PASSOU | `AuditServiceIT.platformWithoutOrganization` (suporte grava; demais tipos negados) |
| CA9 | PASSOU | `writesDiffWithoutSecrets` captura o log: só ação, entidade e id |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS; 79 testes rápidos, 48 de integração (AuditServiceIT 7/7, MigrationIT, SeedDataIT, OpenApiContractTest incluído), JaCoCo "All coverage checks have been met". Front-end e OpenAPI não alterados, não rodados.
Achados:
- sugestão, código, `AuditServiceIT.writesDiffWithoutSecrets` — usa só `password` e `cardNumber`; a cobertura das demais chaves está só no teste rápido (suficiente para a regra).
- sugestão, código, `AuditSanitizer.SENSITIVE` — correspondência por trecho remove também chaves como `tokenizedAt`; está documentado no próprio código e é conservador.

## Entrega

- O que muda: tabela `audit_log` append-only (migração V5, gatilho recusa update, delete e truncate; RLS por organização) e `AuditService` no pacote `audit`, que grava na transação do chamador ação, entidade, autor e só os campos que mudaram, sem senha, token ou dado de cartão.
- Verificação: `./backend/mvnw -f backend/pom.xml verify` com BUILD SUCCESS (79 testes rápidos, 48 de integração, JaCoCo ok); CA1 a CA9 passaram na rodada 1.
- Decisões: ver D2 a D7 (transação obrigatória; remoção por nome de chave; sem organização só para autor da plataforma).
- Limitações: sem rota, tela nem OpenAPI (consulta é outro item); chave sensível com nome novo exige acrescentar a regra e o teste.
