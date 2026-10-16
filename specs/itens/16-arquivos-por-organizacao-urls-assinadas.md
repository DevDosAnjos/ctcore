# 16 — Arquivos por organização com URLs temporárias assinadas

Origem: issue #16; `docs/especificacao/01-organizacao-e-isolamento.md` (RN-ORG-03) e `00-visao-geral.md` §9 e limites (arquivos em serviço compatível com S3, pastas por organização, 5 MB). Branch: `item/16-arquivos-por-organizacao`.

## 1. Objetivo

Dar ao back-end o serviço interno que guarda e entrega arquivos: cada arquivo vive na pasta da sua organização no armazenamento compatível com S3 (o `storage` do docker-compose) e só é acessado por URL temporária assinada. As features futuras (fotos, termos, importações) usam este serviço; a organização vem sempre do contexto da #14, nunca de parâmetro.

Fora do escopo: rota HTTP, tela, OpenAPI e MSW (nenhum envio ou download de usuário existe ainda); regras de tipo, corte e redução de foto (itens de cada recurso); auditoria (#17); URL pública permanente.

## 2. Requisitos

- R1: RN-ORG-03: todo arquivo é gravado sob a pasta `org/<organizationId>/` do bucket, com a organização tirada do `OrganizationContext` (D2).
- R2: RN-ORG-03: o acesso a um arquivo é por URL de leitura assinada e temporária, que expira no prazo configurado (D3) e não pode ser usada depois dele.
- R3: RN-ORG-03: uma organização não obtém URL, nem lê, nem apaga arquivo de outra, mesmo com o nome ou o caminho completo da outra (inclusive com `..`, `/` ou prefixo `org/` no nome) (D2).
- R4: Arquivo acima de 5 MB ou vazio é recusado antes de ir ao armazenamento (limite de `00-visao-geral.md`).
- R5: Sem organização no contexto, qualquer operação é negada e nada é gravado (RN-ORG-02, mesma regra da #14).
- R6: O serviço fica atrás de uma porta (`FileStorage`) com adaptador S3 configurável por propriedades `ctcore.storage.*`, com padrões iguais aos do docker-compose (D1).

## 3. Critérios de aceite

- CA1 (R1): dado o contexto da organização A, quando grava `foto.png`, então o objeto existe no bucket em `org/<A>/foto.png`.
- CA2 (R2): dado um arquivo gravado, quando se pede a URL de leitura, então ela traz assinatura e prazo e o GET dentro do prazo devolve o conteúdo.
- CA3 (R2): dada uma URL cujo prazo passou (prazo curto de teste), quando se faz GET, então o armazenamento recusa (403); URL com a assinatura alterada também é recusada.
- CA4 (R3): dado um arquivo da A, quando o contexto é da B e pede URL, lê ou apaga pelo mesmo nome, então não encontra o arquivo (erro de não encontrado) e o objeto da A permanece; nomes com `..`, `/`, `\` ou prefixo `org/<A>/` são recusados como nome inválido.
- CA5 (R4): dado conteúdo de 5 MB + 1 byte ou de 0 byte, quando grava, então é recusado e nada é criado no bucket; 5 MB exatos é aceito.
- CA6 (R5): dado nenhum contexto aberto, quando qualquer operação é chamada, então lança acesso negado e o bucket não muda.
- CA7 (R6): dadas as propriedades `ctcore.storage.*` com os padrões do compose, quando a aplicação sobe, então o bean `FileStorage` é criado e o bucket é criado se não existir; a suíte de contrato (`OpenApiContractTest`) segue passando sem mudança no OpenAPI.

## 4. Plano técnico

- Dados: não muda (sem migração; arquivos ficam no armazenamento). Metadados por recurso (tabelas de anexos) ficam nos itens que usam arquivos.
- API: não muda. Serviço interno, sem rota; OpenAPI e MSW intactos.
- Interface: não muda; quatro estados, tradução e axe não se aplicam.
- Segurança: organização só de `OrganizationContext.organizationId()` (existente em `isolation/`); nome validado (sem `/`, `\`, `..`, caracteres de controle; até 200 caracteres) para não sair da pasta; URL assinada SigV4 (query string) com prazo curto; credenciais e endpoint só por variável de ambiente, nunca em log. O serviço não lê dados pessoais.
- Arquivos a criar em `backend/src/main/java/br/com/ctcore/storage/`: `FileStorage` (porta: `put(name, bytes, contentType)`, `readUrl(name)`, `delete(name)`), `S3FileStorage` (adaptador), `StorageProperties` (`ctcore.storage.endpoint/bucket/region/access-key/secret-key/url-expiry`), `StorageConfig` (beans e criação do bucket). A alterar: `backend/pom.xml` (dependência `software.amazon.awssdk:s3` em versão exata), `backend/src/main/resources/application.yml` (bloco `ctcore.storage`), `backend/README.md` (seção curta), `infra/.env.example` só se surgirem variáveis novas. Testes a criar em `backend/src/test/java/br/com/ctcore/storage/`: `FileStorageNameTest` (rápido: nome e tamanho) e `S3FileStorageIT` (Testcontainers com a imagem `chrislusf/seaweedfs:3.80`, a mesma do compose).
- Reuso: `OrganizationContext`/`OrganizationPrincipal` (vistos em `isolation/`), `ClockConfig` (`config/`), padrão de propriedades de `integration/IntegrationProperties`.
- Riscos: o SeaweedFS pode tratar a assinatura diferente do S3 real (aparece em CA2/CA3 no IT; ajustar acesso por caminho, `pathStyleAccess`); a dependência aumenta a imagem e entra no OSV-Scanner; o JaCoCo exige 80% global, então o IT precisa cobrir o adaptador; o IT precisa de Docker (já exigido pelos `*IT`).

## 5. Tarefas

- [x] T1: Adicionar a dependência S3 do AWS SDK v2 ao `pom.xml` e o bloco `ctcore.storage` ao `application.yml` — não cobre critério: preparação — prova: `./backend/mvnw -f backend/pom.xml compile`
- [x] T2: Criar `FileStorage`, `StorageProperties` e a validação de nome e tamanho (5 MB, não vazio) — cobre CA4 (nome) e CA5 — prova: `FileStorageNameTest`
- [x] T3: Criar `S3FileStorage` e `StorageConfig` (pasta `org/<id>/` do contexto, URL SigV4 com prazo, criação do bucket) — cobre CA1, CA7 — prova: `S3FileStorageIT`
- [x] T4: Testes de integração de URL e isolamento: GET dentro do prazo, prazo vencido, assinatura alterada, organização B sem acesso, sem contexto — cobre CA2, CA3, CA4, CA6 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=S3FileStorageIT`
- [x] T5: Documentar o serviço no `backend/README.md` (propriedades, uso em feature nova, limite de 5 MB) e rodar a regressão completa — cobre CA7 — prova: `./backend/mvnw -f backend/pom.xml verify` (cobertura e `OpenApiContractTest`)

## 6. Decisões

- D1: Porta `FileStorage` com adaptador S3 via AWS SDK v2, em vez de assinar à mão ou gravar em disco. Motivo: RNF-39 e o `storage` do compose já são compatíveis com S3; o SDK gera a URL SigV4 correta. Sem versão simulada própria, porque compose e Testcontainers dão armazenamento real local; trocar de fornecedor muda só o adaptador. Alternativa descartada: SigV4 manual (código de segurança próprio sem necessidade).
- D2: A organização nunca é parâmetro: vem do `OrganizationContext`, e o nome é relativo à pasta e validado. Motivo: o isolamento não depende de quem chama (RN-ORG-02). Alternativa descartada: receber o caminho completo e conferir o prefixo.
- D3: Prazo padrão da URL de 5 minutos, configurável (`ctcore.storage.url-expiry`). A spec diz "temporária" sem número; é suposição.
- D4: Sem rota HTTP, OpenAPI, MSW, tela nem auditoria neste item, porque a issue pede só pastas e URLs no back-end e nenhuma ação de usuário altera dado ainda; a auditoria de envio e exclusão entra nos itens que expõem essas ações (o mecanismo vem na #17). Se o dono quiser rota, vira outro item.
- D5: Credenciais do bucket só por variável de ambiente, com padrão de desenvolvimento do compose (sem autenticação, valores fictícios). Em produção (#192) vêm do ambiente.
- D6 (ajuste, vindo da Rodada 1 de "Verificação", achado sobre D5): D5 fica corrigido em parte. O SeaweedFS sem identidades recusa requisições assinadas, então o compose passa a usar `infra/storage/s3.json` com credenciais fictícias de desenvolvimento (`dev`/`dev-secret`), montado com `-s3.config`; os padrões de `ctcore.storage.access-key/secret-key` seguem esses valores e o IT usa o mesmo arquivo. A regra de produção não muda: credenciais reais só por variável de ambiente (#192). Os dois achados de código da verificação são sugestões e não alteram requisitos nem tarefas.
- Suposição: o limite de 5 MB vale para qualquer arquivo; limites por tipo (foto reduzida a 512 px, PDF só para termos) ficam nos itens de cada recurso. Se o limite do serviço tiver de variar por tipo, muda a assinatura de `put`.

## Construção

- T1 — arquivos: `backend/pom.xml` (`software.amazon.awssdk:s3:2.31.50`), `backend/src/main/resources/application.yml` (bloco `ctcore.storage`) — prova: `./backend/mvnw -f backend/pom.xml -q compile` → ok
- T2 — arquivos: `storage/FileStorage.java`, `storage/StorageProperties.java`, `FileStorageNameTest` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=FileStorageNameTest` → 2 testes, 0 falhas
- T3 — arquivos: `storage/S3FileStorage.java`, `storage/StorageConfig.java` (bucket criado por `ApplicationRunner`, tolerante a armazenamento fora do ar) — prova: `S3FileStorageIT` → passou
- T4 — arquivos: `backend/src/test/java/br/com/ctcore/storage/S3FileStorageIT.java` (CA1 a CA6) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=S3FileStorageIT` → 5 testes, 0 falhas (URL válida 200, vencida 403, assinatura alterada 403, organização B NotFound, sem contexto AccessDenied)
- T5 — arquivos: `backend/README.md` (seção "Arquivos por organização") — prova: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (76 testes rápidos e 41 de integração, JaCoCo e `OpenApiContractTest` incluídos)
Regressão: `./backend/mvnw -f backend/pom.xml verify` → verde. Front-end não alterado, não rodou.
Desvios: o SeaweedFS sem identidades recusa requisições assinadas ("Signed request requires setting up SeaweedFS S3 authentication"), então D5 (compose sem autenticação) não serve. Criado `infra/storage/s3.json` (credenciais fictícias `dev`/`dev-secret`) e montado no `storage` de `infra/docker-compose.yml` com `-s3.config`; o IT usa o mesmo arquivo. Adicionados checksums `WHEN_REQUIRED` no cliente S3 por compatibilidade. Variáveis de ambiente `STORAGE_*` não entraram em `infra/.env.example` (só `STORAGE_PORT`, já existente).
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-16, estado 029f298c44625d2dcf22c662f733d42cd150de79

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `S3FileStorageIT.storesUnderOrganizationFolder...`: URL contém `/org/<A>/foto.png` e o GET devolve o conteúdo |
| CA2 | PASSOU | mesmo teste: `X-Amz-Signature`, `X-Amz-Expires=300`, GET 200 |
| CA3 | PASSOU | `refusesExpiredAndTamperedUrls`: vencida 403, assinatura alterada 403 |
| CA4 | PASSOU | `anotherOrganizationNeverReachesTheFile` (NotFound, objeto da A permanece) e `FileStorageNameTest` (nomes inválidos) |
| CA5 | PASSOU | `refusesEmptyAndOversizedFilesAndCreatesNothing` e `FileStorageNameTest` (5 MB exatos aceito, +1 e 0 recusados) |
| CA6 | PASSOU | `deniesEveryOperationWithoutOrganization` (AccessDeniedException nas três operações) |
| CA7 | PASSOU | contextos Spring dos `*IT` sobem com o bean e o runner de bucket (log "Storage bucket not ready" só com armazenamento ausente, sem derrubar); `OpenApiContractTest` 4 testes, 0 falhas |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → EXIT 0 (JaCoCo incluído); S3FileStorageIT 5/5, FileStorageNameTest 2/2, OpenApiContractTest 4/4; 117 testes no total, 0 falhas. Front-end não alterado, não rodado.
Achados:
- sugestão, código, `StorageConfig` — não há teste que afirme explicitamente a criação do bean `FileStorage` no contexto; só é coberto indiretamente pelos `*IT` que sobem a aplicação.
- sugestão, código, `S3FileStorage.ensureBucket` — o ramo `catch S3Exception` com 404 duplica o de `NoSuchBucketException`; inofensivo.
- sugestão, especificação, "Desvios" — `infra/storage/s3.json` e a mudança do compose alteram D5; vale atualizar D5 (hoje diz compose sem autenticação).

## Entrega

- Serviço de arquivos por organização (`storage`): `put`, `signedUrl` e remoção sempre sob a pasta da organização do contexto; URLs temporárias assinadas (5 min); nomes inválidos, arquivo vazio ou acima de 5 MB recusados; sem contexto de organização, acesso negado.
- Verificação: `./backend/mvnw -f backend/pom.xml verify` verde (117 testes, JaCoCo incluído); CA1 a CA7 passaram na Rodada 1. Front-end não alterado.
- Decisão a saber: o armazenamento local exige credenciais; `infra/storage/s3.json` traz credenciais fictícias de desenvolvimento (D6). Em produção, só por variável de ambiente (#192).
- Limitação: o limite de 5 MB vale para qualquer tipo de arquivo; limites por tipo ficam nos itens de cada recurso.

Refs #16
