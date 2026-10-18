# Revisão da Sprint 03 · Duas etapas, usuários, permissões e configurações

### Rodada 1 — AJUSTAR — 2026-10-18, estado a01cb564a7adec4394a771cacffa791befa01f2d

#### Resumo e veredito

A Sprint 03 entregou as 12 issues do marco: duas etapas (TOTP, códigos de reserva, confirmação de ação crítica), tabela de permissões no servidor, Configurações › Organização, Unidades (lista, assistente, edição, seletor, transferência de coordenação, desativação), "Última alteração" com `If-Match`, Minha conta e Configurações › Usuários. Todas fechadas por PR de squash com `ci-gate` verde. O clone limpo passou em tudo (back-end 359 testes, front 428 testes, e2e 15 de 15, imagens, seed idempotente) e o axe não achou violação em 8 telas nos dois temas a 360 px.

Veredito: **AJUSTAR**, sem BLOQUEIA. Antes do PR `dev`→`main` devem entrar cinco ajustes pequenos (A1 a A5): registro da liberação da sprint, especificação atualizada com as decisões que mudaram regra, uma falha provável na transferência de coordenação, o comando da imagem no README e a citação de códigos RN nos testes. O dono decide três pontos (D1 a D3).

#### Estado revisado

| | |
|---|---|
| `dev` | `a01cb564a7adec4394a771cacffa791befa01f2d` (igual a `origin/dev`, pasta limpa) |
| `main` | `537fb68e0a9b7881e208086a92a3234f23802e31` (`origin/main`, Sprint 02, PR #245; não existe branch `main` local) |
| Ferramentas | Java 25.0.4 no PATH (o `verify` compila com `release 21`), Node 24.21.0, Docker 29.8.1, data 2026-10-18 |
| Diff | `git diff origin/main...dev --stat`: 237 arquivos, +20154 −78 (back-end `identity`, `organization`, `staff`, `permission`, V11 a V18, OpenAPI +892 linhas, front `features/{auth,units,account,users,organization,permissions}`, MSW, 8 e2e novos, seed 04, 12 arquivos de item). Workflows e `osv-scanner.toml` sem mudança |
| PRs | #246 (#26), #247 (#27), #248 (#28), #249 (#29), #250 (#30), #251 (#31), #252 (#32), #253 (#33), #254 (#34), #255 (#35), #256 (#36), #257 (#37). Nenhum PR fora de item |

Pré-condições: `dev` limpa e igual a `origin/dev`; nenhum PR aberto contra `dev`; `gh auth` válido; Docker ativo. Marco com 12 issues fechadas, nenhuma com a etiqueta `depende de você`; no `specs/backlog.md` as 12 linhas estão "feito". Nenhum PR fechou issue de outro marco. **Ressalvas:** (a) existem 10 branches remotas `item/28-...` a `item/37-...` já mergeadas (R03-9); (b) o cabeçalho do backlog ainda diz "Sprint liberada: Sprint 02" (R03-1). Criei a branch local `revisao/sprint-03` só para gravar este arquivo, sem commit.

#### Trilha de processo

| Item | PR | Verificação | Entrega | Observação |
|---|---|---|---|---|
| #26 Duas etapas (TOTP) | #246, squash (4 commits no PR), `ci-gate` SUCCESS, `Closes #26` | R1 OK, R2 OK | sim | entrada em `.gitleaksignore` para a chave de teste |
| #27 Tabela de permissões | #247, 1 commit, SUCCESS, `Closes` | R1 OK | sim | |
| #28 Organização | #248, 3 commits, SUCCESS, `Closes` | R1 OK, R2 OK | sim | |
| #29 Unidades (lista) | #249, 3 commits, SUCCESS, `Closes` | R1 CORRIGIR_ESPEC, R2 OK, R3 OK | sim | 3 rodadas (no limite); 422 virou 400 na especificação |
| #30 Assistente de nova unidade | #250, 1 commit, SUCCESS, `Closes` | R1 OK | sim | |
| #31 Configurações › Unidade | #251, 1 commit, SUCCESS, `Closes` | R1 OK | sim | |
| #32 Seletor de unidade | #252, 1 commit, SUCCESS, `Closes` | R1 OK | sim | |
| #33 Transferir coordenação | #253, 1 commit, SUCCESS, `Closes` | R1 OK | sim | |
| #34 Desativar unidade | #254, 1 commit, SUCCESS, `Closes` | R1 OK | sim | |
| #35 Última alteração e auditoria | #255, 1 commit, SUCCESS, `Closes` | R1 OK | sim | nome do branch difere do citado no arquivo (sugestão da verificação) |
| #36 Minha conta | #256, 1 commit, SUCCESS, `Closes` | R1 NAO_CONFERIDO (Docker parado), R2 OK | sim | |
| #37 Configurações › Usuários | #257, 1 commit, SUCCESS, `Closes` | R1 OK | sim | |

Conferido com `gh pr view` (base `dev`, commits, corpo com `Closes #N`, `ci-gate`) e `grep -n '^## \|^### Rodada'` nos 12 arquivos: seis seções, "Construção", ao menos uma rodada `OK`, "Entrega", 0 tarefas abertas. Nenhum item com mais de três rodadas, nenhum merge sem rodada `OK`, nenhuma correção fora do PR do item. Os 12 PRs entraram em sequência entre 17/10 20:39 e 18/10 14:27 (horário de Brasília).

#### "Pronto quando"

**Do marco** (`gh api repos/{owner}/{repo}/milestones/4`)

| Bullet | Status | Evidência |
|---|---|---|
| Coordenador sem duas etapas não passa do primeiro acesso sem configurar | PASSOU | Prova minha no back-end vivo (jar do clone, seed): login de `coordenador@ficticia-1.example` (sem duas etapas) e `GET /api/v1/units` devolveu `403 TWO_FACTOR_SETUP_REQUIRED`; `GET /auth/session` continua 200. Código: `TwoFactorGateFilter` deixa passar só `/auth/session`, `/auth/logout` e `/auth/two-factor*`. Testes: `TwoFactorGateIT`, `TwoFactorLoginIT` rodaram no `verify`. Equipe da plataforma não tem papel ainda (D2 do #26; entra em `requiredFor` na Sprint 15) |
| Rota fora do perfil, pelo endereço ou pela API, é recusada no servidor | PASSOU | Prova minha: login de `professor@ficticia-1.example`; `GET /units`, `/users`, `/organization` devolveram `403 FORBIDDEN` (corpo sem vazar existência). `GET /permissions` 200 com os níveis do Professor. Testes: `PermissionRouteIT`, `PermissionsIT`, `UnitRouteIT`, `OrganizationRouteIT`. A permissão é conferida em `Permissions.require/grant` dentro dos serviços, por vínculo da sessão |
| Com uma unidade nenhum seletor; com duas, aparece | PASSOU | `UnitScope.test.tsx` "CA5: com uma unidade não há seletor; com duas há" (jsdom), `UnitReachableIT` no servidor. Não vi no navegador (a tela real exige a sessão do MSW; e2e de menu cobre o perfil, não o seletor) |
| Não é possível ficar com uma unidade ativa sem Administrador | PASSOU para as ações da tela de Usuários; ressalva na criação (D1) | `StaffUsers.requireOtherAdministrators` conta Administradores `ACTIVE` por unidade sob `select ... for update` da organização (sem corrida entre dois pedidos), recusa com `LAST_UNIT_ADMINISTRATOR`; testes `StaffUsersDeactivateIT`, `StaffUsersUnitsIT`, `StaffUsersAppointmentIT` passaram. A unidade nasce sem Administrador se o convite do assistente for omitido ou falhar (D8 do #37, D6 do #30) e a regra RN-ORG-07 diz "toda unidade ativa tem pelo menos um" (ver D1) |
| Fluxo ponta a ponta "login com duas etapas" na suíte | PASSOU contra MSW | `duas-etapas.spec.ts` "@critical login com duas etapas: senha, segundo passo, código e área"; `npm run e2e` no clone: 15 de 15 em 39 s. O servidor real é provado por `TwoFactorLoginIT` (MockMvc + banco). Não há e2e com navegador e servidor reais (D3) |
| "Pronto quando" geral em cada item | PASSOU, com os ajustes A2 a A5 | tabela abaixo |

Também herdado do marco da Sprint 02 (movido para a #36): "troca de senha encerrando as outras sessões". `AuthRoutesIT.changePassword` e `SessionListIT` (servidor, duas sessões) passaram; `minha-conta.spec.ts` prova a tela contra MSW. Foi o que o plano previu; a lacuna de integração real é a D3.

**De todo item** (`docs/plano-de-sprints.md`, "Pronto quando (vale para todo item)")

| Regra | Status | Evidência ou "não se aplica nesta sprint: motivo" |
|---|---|---|
| Código de regra citado tem ao menos um teste | PASSOU em parte (A5) | Varredura dos códigos das 12 issues contra `backend/src/test`, `frontend/src`, `frontend/e2e`: citados RN-ACE-15, 17, 18, 21, RN-ORG-07, RF-ORG-01 a 08, RF-ACE-04, 06. Sem nenhuma citação: RN-ORG-06 (#33), RN-ORG-09 (#32), RN-ORG-11 (#34), RN-ORG-14 (#30). Os testes existem e citam o RF equivalente (`CoordinationTransferIT`: "RF-ORG-06"; `UnitDeactivationIT`, `UnitScope.test.tsx`), não o RN. Nenhuma é de cobrança, pagamento, assinatura, inativação de aluno ou isolamento; AJUSTAR |
| Cobertura mínima de 80%; 100% nas regras críticas | PASSOU | Back-end: JaCoCo 2679 de 2751 linhas (97,38%), "All coverage checks have been met"; `isolation` 64/64, `staff` 193/193, `permission` 97/97, `identity` 1007/1046, `organization` 735/744. Front: linhas 96,56% (2107/2182), instruções 94,07%; menores: `ChangeUnitsDialog.tsx` 73,5% linhas. Pacotes `billing`, `payment`, `subscription`, `deactivation`: não existem ainda (a desativação de unidade fica em `organization`) |
| Rota nova com teste de isolamento entre organizações e entre perfis | PASSOU em parte (A6, herdado do R02-4) | Há teste por grupo de rotas: `AccountRoutesIsolationIT`, `TwoFactorIsolationIT`, `StaffUsersIsolationIT`, `UnitRouteIT`, `UnitSettingsRouteIT`, `UnitDeactivationIT.isolation`, `LastChangeIT.isolation`, `PermissionRouteIT`. Não achei rota sem nenhum teste, mas a guarda que reprova rota sem teste (A4 da Sprint 02, proposta de "primeiro item da Sprint 03") não foi criada: nenhuma issue, nenhum teste |
| OpenAPI, implementação e MSW batem | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` passaram no `verify`. 41 operações nos controllers (auth 15, two-factor 5, organization 5, units 9, permissions 1, users 6) contra o OpenAPI (+892 linhas). MSW: handlers de `/auth/logout` e `/auth/password` agora existem (A6 da Sprint 02 feito), mais `organization`, `units`, `users`, `reachable`, `account`, `twofactor` com testes `*.msw.test.ts` |
| Auditoria em toda escrita | PASSOU com sugestões | Itens verificados: `TwoFactorAuditIT`, `OrganizationProfileIT`, `UnitSettingsIT` (Pix e CNPJ viram `[alterado]`, sem valor), `CoordinationTransferIT`, `UnitDeactivationIT`, `LastChangeIT`, `StaffUsers*IT`, `EmailChangeIT` (sem endereços completos). Sugestão aberta: `TwoFactorService.setup` não audita |
| Quatro estados, 360 px e axe nos dois temas | PASSOU nas telas do menu; NÃO CONFERIDO em modais e na tela de configuração das duas etapas | Navegador real (Chromium do Playwright, 360x800, `axe-core` do repositório com `wcag2a/aa/21a/21aa` incluindo contraste, `prefers-color-scheme` claro e escuro), MSW com o Coordenador de teste: `/`, `/conta`, `/configuracoes/organizacao`, `/configuracoes/unidades`, `/configuracoes/unidades/nova`, `/configuracoes/unidade`, `/configuracoes/usuarios`: rolagem horizontal 0 px e 0 violações nas 14 combinações. `/configurar-duas-etapas` renderizou a Home (o mock não exige as duas etapas para esse perfil), então a tela `TwoFactorRequiredPage`, o diálogo de ativação (QR e códigos), `TransferCoordinationDialog`, `DeactivateUnitDialog` e `ChangeUnitsDialog` ficaram só com os testes jsdom (os quatro estados estão neles; sem contraste) |
| Textos de tela só nos arquivos de tradução | PASSOU | `npm run lint` (regra de texto literal) exit 0; textos novos em `src/i18n/pt-BR.json` |
| Recurso novo desligado por organização | não se aplica nesta sprint | nenhum item está na lista de recursos liberados aos poucos; registrado em D8/D9/D10 dos itens #26, #27, #30, #31 |
| Notas de versão | não se aplica nesta sprint | o arquivo de Novidades nasce na Sprint 21 (D10 do #26, D9 do #30) |

#### Clone limpo

Clone novo de `origin/dev` no scratchpad (`...\scratchpad\r1\clone`, removido ao fim), projeto Compose `ctcorerev` (portas padrão estavam livres), seguindo o README e "Comandos".

| Passo | Resultado real |
|---|---|
| ambiente | `docker compose up -d --wait`: postgres, storage e mailbox saudáveis; `prepare-runtime-db.sh` criou o papel `ctcore_app` (exit 0) |
| back-end | `verify`: BUILD SUCCESS em 8 min 10 s (inclui compose); 126 testes rápidos e 233 de integração, 0 falhas; JaCoCo 97,38%, "All coverage checks have been met" |
| front-end | `npm ci` (0 vulnerabilidades), `lint`, `typecheck`, `test:cov`, `build`: exit 0; vitest 55 arquivos e 428 testes; `npm run e2e`: 15 de 15 `@critical` em 39 s; cadeia inteira em 5 min |
| seed (3x) | `seed.sh` exit 0 nas três vezes (01 a 04); contagens iguais: 3 organizações, 3 unidades, 5 contas, 6 vínculos, 1 vínculo-unidade |
| back-end vivo | jar com perfil `local`: readiness UP; provas do marco (bullets 1 e 2) feitas com o seed |
| imagens | `ctcore-backend` sem root (`uid=10001`), readiness UP na porta de gestão **só depois de passar `TWO_FACTOR_ENCRYPTION_KEY`**; sem ela o contêiner cai em `SecretCipher: TWO_FACTOR_ENCRYPTION_KEY must be 32 bytes in base64`. O comando do README (`docker run ... -e DB_HOST=host.docker.internal ... ctcore-backend`, linha 82) deixou de funcionar (A3). `ctcore-frontend`: 200 em `/` e em `/conta`, 404 em `/api/v1/auth/session`; **nenhum** cabeçalho `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options` ou `Referrer-Policy` na resposta (A5 da Sprint 02 não feito) |
| recomeço | `down -v` e novo `up --wait` em 27 s sem estado escondido; `down -v` final; volumes `ctcore_postgres-data` e `ctcore_storage-data` do dono presentes; `docker images` igual ao de antes (imagens do clone removidas pelo nome) |

Comparação com a Sprint 02 rodada 2 (186 testes no back-end, 96,18%; 227 no front, 98,84%): back-end 359 testes e 97,38%; front 428 testes e 96,56% de linhas (queda de 2,3 pontos, ainda acima de 80%). Nada falhou.

Limpeza: contêineres `rev-be` e `rev-fe`, projeto `ctcorerev`, imagens `ctcore-backend` e `ctcore-frontend`, clone e processos `java` (PID próprio) e `vite` (porta 5199) removidos. Existe um contêiner `competent_neumann` que não é meu; não toquei.

#### CI e proteções (consultados no GitHub)

- `dev`: proteção com `ci-gate` exigido, push forçado `false`, sem revisão obrigatória, sem `strict`, administradores não incluídos (`enforce_admins=false`) — igual à Sprint 02.
- `main`: sem proteção clássica (404), mas o ruleset `protege-main` está ativo: bloqueia exclusão e não-fast-forward, exige PR com **apenas o método `merge`** e `ci-gate`.
- Repositório permite squash, merge e rebase; `delete_branch_on_merge=false` (origem das branches do R03-9).
- `ci.yml`: últimos runs dos PRs #33 a #37 em `success`; `Publish` em `dev` verde nos 8 últimos commits (pulado sem chaves). Workflows, `osv-scanner.toml` e `.github` não mudaram na sprint; não reli os workflows.
- `osv-scanner.toml`: 2 exceções (GHSA-j9f9-w8pj-32f8 e GHSA-pc63-qcmh-9cmg), ambas com motivo e `ignoreUntil = 2027-01-15` — inalteradas.
- `.gitleaksignore`: 2 impressões digitais (a nova é a chave de teste das duas etapas, `ecfc681...:application.properties:generic-api-key:2`).
- `gh secret list` e `gh variable list`: vazios (a publicação fica pulada, limitação aceita até a Sprint 22).
- Dependências novas: só `qrcode 1.5.4` e `@types/qrcode 1.5.6` (versões fixas); `pom.xml` sem mudança. Migrações V11 a V18 novas, nenhuma migração antiga editada; todas as tabelas novas com `enable` e `force row level security` (V11, V13, V18 conferidas).

#### Achados

**R03-1 · AJUSTAR (antes do PR) · processo** — Liberação da Sprint 03 não registrada. Esperado: `AGENTS.md` ("só seguir quando o dono disser que a sprint foi revisada") e o precedente `docs: libera a Sprint 02 no backlog` (#233). Observado: `specs/backlog.md` linha 5 ainda diz "Sprint liberada: Sprint 02" e não há commit de liberação da 03; o PR `dev`→`main` da Sprint 02 (#245) foi mergeado às 19:51 e o primeiro item (#26) às 20:39 de 17/10. É coerente com uma liberação verbal, mas não há registro. Efeito: a parada de sprint não é auditável. Proposta: o dono confirma a data e um commit de documentação (fora de item, como #233) atualiza a linha 5 e `specs/projeto.md`.

**R03-2 · AJUSTAR (antes do PR) · especificação** — Decisões "muda o comportamento" não estão em `docs/especificacao/` (`git diff origin/main...dev --stat -- docs` lista só `openapi.yaml`). `AGENTS.md`: "mudou uma regra, muda primeiro a especificação". Texto a propor, por regra:
- RN-ACE-17/18/21: login com duas etapas em dois passos (bilhete de 5 min, 5 erros o invalidam, erros contam na trava da RN-ACE-10); código TOTP não vale duas vezes no mesmo passo; confirmação de ação crítica por token de uso único de 5 min preso à sessão e à ação (#26 D2, D3, D4, D6).
- RN-ORG-06: a troca é imediata, sem aceite do novo Coordenador; o novo Coordenador é um Administrador ativo da organização; o antigo vira Administrador de todas as unidades existentes naquele momento (#33 D1, D2, D4).
- RN-ORG-11: a única unidade ativa da organização não pode ser desativada; confirmação digitada é o nome da unidade (#34 D2, D3).
- RN-ORG-05: o limite de 200 usuários conta convites pendentes (#37 D4); 10 unidades, 409 `UNIT_LIMIT_REACHED` (#30 D5).
- RF-ORG-01: contraste AA mínimo 4,5:1 recusa a cor (#28 D4); Administrador vê em leitura (#28 D8). RF-ORG-02: nome curto antigo fica reservado à unidade que o usou (#29 D4). RF-ORG-03: modalidades fora do assistente até a Sprint 04, latitude e longitude no lugar do mapa (#30 D2, D3, D4). RF-ORG-05: padrão do Coordenador é "Todas as unidades" (#32 D3).
- RF-ORG-08: `If-Match` obrigatório (428 sem ele) em `PATCH /organization` e `PUT /units/{id}` (#35 D2, D3).
- RF-ACE-04: troca de e-mail exige senha e código; senha atual errada passa pela trava da RN-ACE-10; sessão atual não se encerra pela lista (#36 D2, D3, D7). RF-ACE-06: o Coordenador vê só Administradores; o Administrador só Professores das suas unidades; "desativar" encerra o vínculo, não a conta (#37 D2, D5, D6, D7).
- Erros de campo são 400 `VALIDATION_ERROR`, não 422 (CA dos itens #28, #29, #33; já corrigido em #29).
Tamanho: pequeno (só texto). Como provar: reler as RN/RF contra os testes citados.

**R03-3 · AJUSTAR (antes do PR) · dados/API · PLAUSÍVEL, não executado** — A transferência de coordenação falha para um Coordenador que se nomeou Administrador. Local: `CoordinationTransfer.swap` (`update account_link set role = 'ADMINISTRATOR' where ... role = 'COORDINATOR'`) contra `V6__identity.sql` linha 21 `unique (account_id, organization_id, role)`; e `StaffUsers.appoint` (D7 do #37) cria um segundo vínculo `ADMINISTRATOR` ativo para a mesma conta. Esperado: a RN-ORG-07 manda "o Coordenador nomeia outro, podendo ser ele mesmo" e a RN-ORG-06 manda transferir depois. Observado (leitura): quem fez as duas coisas terá dois vínculos da mesma conta; o `update` do rebaixamento viola a unicidade e a transferência devolve erro de banco. Nenhum teste combina `StaffUsersAppointmentIT` com `CoordinationTransferIT`. Como era só leitura, não reproduzi; o clone já tinha sido removido quando percebi. Proposta (corrigir no item #33): se a conta já tiver vínculo de Administrador, apagar o vínculo de Coordenador (as unidades dele não existem), garantir o de Administrador `ACTIVE` com todas as unidades, senão manter o `update`; teste de integração com o caso; estimativa: pequena.

**R03-4 · AJUSTAR (antes do PR) · documentação** — `README.md` linha 82 (`docker run --rm -e DB_HOST=host.docker.internal ... ctcore-backend`) não sobe mais: falta `TWO_FACTOR_ENCRYPTION_KEY` (obrigatória fora do perfil `local`, `backend/README.md` linhas 39 e 183). Evidência: contêiner encerrou com `TWO_FACTOR_ENCRYPTION_KEY must be 32 bytes in base64`; com `-e TWO_FACTOR_ENCRYPTION_KEY=$(openssl rand -base64 32)` o readiness ficou UP. Proposta: acrescentar a variável ao comando e uma frase de como gerar. Tamanho: uma linha. A #192 (publicação) precisa listar a chave entre os segredos.

**R03-5 · AJUSTAR (antes do PR) · teste** — Códigos RN sem citação nos testes: RN-ORG-06, RN-ORG-09, RN-ORG-11, RN-ORG-14 (ver tabela). Proposta: acrescentar o código aos `@DisplayName` de `CoordinationTransferIT`, `UnitDeactivationIT`, `UnitCreateIT` (coordenadas) e `UnitScope.test.tsx`, sem mexer em corpo de teste. Tamanho: pequeno.

**R03-6 · AJUSTAR (depois do PR) · teste** — A4 da Sprint 02 (guarda de teste de isolamento por rota) segue sem issue nem teste; era para ser o primeiro item da Sprint 03. Com 41 rotas hoje, nada impede a rota 50 sem teste. Proposta: item novo "Guarda de teste de isolamento por rota" (texto da rodada 1 da Sprint 02, RNF-31), marco Sprint 04, antes do primeiro item que acrescenta rota.

**R03-7 · AJUSTAR (depois do PR) · API/segurança** — A5 da Sprint 02 (cabeçalhos do front e cookie XSRF) segue sem item: `curl -I` na imagem do front não mostra CSP, `nosniff`, `frame-ancestors` nem `Referrer-Policy`. Proposta: item pequeno (Sprint 04) ou levar para a #192 com o texto da rodada 1 da Sprint 02; `/convite/*`, `/redefinir-senha/*` e `/confirmar-email/*` carregam códigos na URL.

**R03-8 · AJUSTAR (depois do PR) · especificação/dados** — `UnitDeactivationBlockers.None` devolve sempre zero (RN-ORG-11 não é imposta de fato) e nenhuma issue futura manda trocá-la: `grep` nas issues abertas por `UnitDeactivationBlockers`, `RN-ORG-11` e "desativar unidade" não achou #46, #47 nem as de matrícula. Hoje é inofensivo (não há alunos nem turmas), mas na Sprint 05 uma unidade com turmas ativas poderá ser desativada. Proposta: acrescentar ao "Pronto quando" de #46 (turmas) e da primeira matrícula (#67, Sprint 07) "trocar `UnitDeactivationBlockers.None` pela contagem real, com teste de `UNIT_HAS_ACTIVE_STUDENTS` e `..._CLASSES`" (editar as issues é decisão do dono).

**R03-9 · SUGESTÃO · processo** — Dez branches remotas `item/28-...` a `item/37-...` continuam no `origin` (PRs mergeados). `delete_branch_on_merge=false`. Proposta: o dono as apaga e liga a opção; a Sprint 02 estava sem branches.

**R03-10 · SUGESTÃO · teste/código** (vindas das verificações dos itens, ainda abertas): `UnitDeactivationIT` sem teste do 409 de unidade já desativada (#34 CA8); `StaffUsers.deactivate` devolve 403 onde a D5 do #37 diz "tira as suas unidades"; `UnitSettings.update` lê o "antes" antes do `for update` (auditoria pode ficar velha) (#31); `LastChange.find` sem desempate; `UnitCreation.invite` só captura duas exceções e outra falha de envio dá 500 depois da unidade criada (#30); sem teste concorrente do nome curto (#29); `TwoFactorService.setup` sem auditoria; `TwoFactorGateFilter` faz duas consultas por requisição; `UnitUpdate.java` linha 13 cita o fornecedor num comentário em `backend/src` (RNF-39 vale para código de regra; o `NoVendorNamesTest` passou). Sem BLOQUEIA entre elas.

**R03-11 · SUGESTÃO · front** — `ChangeUnitsDialog.tsx` com 73,5% de linhas e 31,6% de ramos é o ponto mais fraco do front; abaixo de 80% fora de regra crítica.

#### Decisões do dono

**D1 · RN-ORG-07 ("toda unidade ativa tem ao menos um Administrador") contra RF-ORG-03 ("convite opcional do Administrador")** — a unidade nasce sem Administrador quando o convite é omitido ou falha, e o item #37 (D8) aceita isso. Recomendada: manter o assistente como está e reescrever a RN-ORG-07 para "a ação de um usuário nunca deixa uma unidade ativa com zero Administradores; a unidade recém-criada sem convite aparece com o aviso 'sem Administrador' nos Primeiros passos" — motivo: obrigar o convite alongaria o assistente curto do canvas, e o Coordenador alcança todas as unidades. Alternativa: exigir um Administrador (ou o próprio Coordenador como Administrador) na criação, o que muda o #30.

**D2 · Taxas por meio de pagamento só em valor fixo (centavos)** — a especificação 11 fala em "percentual e/ou valor fixo"; o modelo da #13 guarda centavos e o #31 (D5) fixou teto de R$ 1.000,00 por meio e de R$ 1.000.000,00 na taxa de matrícula. Recomendada: aceitar agora e mandar a #97 decidir percentual e prévia do total, atualizando a especificação — motivo: sem a regra de cálculo definida, colunas de percentual seriam descartadas. Regra de negócio, por isso é sua.

**D3 · Aceitar que os ponta a ponta de tela rodam só contra MSW** — sem navegador e servidor reais na mesma execução (D5 da Sprint 00); o "login com duas etapas" e a "troca de senha encerrando sessões" ficam provados em duas metades (tela com MSW, servidor com MockMvc e banco). Recomendada: aceitar até a Sprint 22 e abrir lá um fumaça com a pilha inteira — motivo: foi a decisão da Sprint 00 e nenhuma regra desta sprint depende só da junção. Alternativa: item novo agora, com compose + back-end + Playwright.

#### Ações do dono

1. Decidir D1, D2 e D3 e confirmar a data da liberação da Sprint 03 (R03-1).
2. Olhar as telas no navegador contra o canvas (o agente nunca viu os `.dc.html`, todos os itens registram isso). `cd frontend && npm run dev`, abrir `http://localhost:5173`, entrar com `ana@exemplo.com` / `segredo123` (Coordenador do MSW) e olhar em 360 px e 1280 px, tema claro e escuro: `/conta` (trocar senha, e-mail, ativar duas etapas com QR e códigos, lista de sessões), `/configuracoes/organizacao`, `/configuracoes/unidades`, `/configuracoes/unidades/nova`, `/configuracoes/unidade` (seções, Pix, taxas, lembretes), `/configuracoes/usuarios` (convidar, alterar unidades, desativar, nomear a si mesmo), diálogos de transferir coordenação e de desativar unidade, o seletor de unidade com duas unidades, e o aviso de conflito de edição ("alguém alterou antes"). O agente provou rolagem e axe só nas telas de página.
3. Apagar as 10 branches remotas `item/28-...` a `item/37-...` e, se quiser, ligar "Automatically delete head branches" (R03-9).
4. Com os ajustes A1 a A5 feitos e a rodada 2 `APROVAR`: abrir o PR `dev`→`main` com o método "Merge" (o ruleset só aceita esse) e, depois de revisar, dizer "sim" para liberar a Sprint 04.

#### Ajustes propostos

Ordem sugerida: A1, A2, A3, A4 e A5 num único PR fora de item (como o #243 da Sprint 02), depois o PR `dev`→`main`; A6 a A8 na Sprint 04.

**A1 · Registro da liberação da Sprint 03** (resolve R03-1) — antes. Tipo: ajuste de documentação. Mudar a linha 5 do `specs/backlog.md` e o trecho equivalente de `specs/projeto.md`. Como provar: `git diff` só em `specs/`. Pequeno.

**A2 · Especificação atualizada** (resolve R03-2) — antes. Tipo: ajuste de especificação, nos trechos listados no R03-2, mais a decisão do dono sobre D1 e D2. Provar: `git diff -- docs/especificacao` só texto. Pequeno.

**A3 · Transferência de coordenação com vínculo duplicado** (resolve R03-3) — antes. Tipo: corrigir no item #33 (`CoordinationTransfer.swap`). Provar: teste de integração novo: Coordenador se nomeia Administrador (`/users/coordinator-appointment`), transfere a um Administrador e vira só Administrador de todas as unidades, sem erro; `verify` verde. Pequeno.

**A4 · Comando da imagem no README** (resolve R03-4) — antes. Acrescentar `-e TWO_FACTOR_ENCRYPTION_KEY=$(openssl rand -base64 32)` ao `docker run` da linha 82 e a frase de como gerar. Provar: subir a imagem seguindo o README e ver readiness UP. Uma linha.

**A5 · Códigos RN nos testes** (resolve R03-5) — antes. Pequeno; sem mudar corpo de teste.

**A6 · Guarda de isolamento por rota** (R03-6) e **A7 · Cabeçalhos do front** (R03-7) — depois, Sprint 04, textos de issue na rodada 1 da Sprint 02 ("Ajustes propostos", A4 e A5).

**A8 · Trocar o impedimento de desativação por contagem real** (R03-8) — depois: ajuste no texto das issues #46 e #67 (Sprint 05 e 07); decisão do dono editar as issues.

#### Limitações e o que não foi conferido

- Canvas contra as telas e tom dos textos: só uma pessoa (arquivos `.dc.html` não acessíveis, registrado em todos os itens).
- axe em navegador real nos modais, no diálogo de ativação das duas etapas (QR) e em `TwoFactorRequiredPage`: NÃO CONFERIDO. Foco, teclado e zoom: NÃO CONFERIDO fora dos testes dos itens.
- `e2e:full`, gitleaks, OSV-Scanner e `actionlint` locais: não rodei; o CI roda (`security` e `ci-gate` SUCCESS nos 12 PRs). `npm audit` do clone: 0 vulnerabilidades.
- Publicação, hospedagem, e-mail e desafio reais, localizador por IP, mapa e Mercado Pago real: NÃO CONFERIDO (limitação aceita, Sprints 22 e 23; `AGENTS.md`, "Limitações aceitas").
- R03-3 é leitura de código e do esquema, não reprodução. As skills `/code-review`, `/security-review` e `ponytail-audit` não foram acionadas separadamente; li `TwoFactorGateFilter`, `TwoFactorService.confirm/verify`, `CriticalActionGuard`, `ReauthService`, `EmailChangeService`, `CoordinationTransfer`, `StaffUsers.changeUnits/requireOtherAdministrators`, `Permissions`, `UnitDeactivationBlockers`, V11, V16 e V18, `nginx` por `curl -I`. Não li linha a linha o restante do `staff`, `organization` (exceto o citado), `SecretCipher`, `Totp`, as telas e os testes além dos nomes. Excesso de código: nada a registrar.
- Isolamento por rota: conferi por nomes de teste e pelo código das permissões, não rodei teste mutante.

#### Riscos para a próxima sprint

- **Sprint 04** (modalidades, faixas etárias, graduação, professores): a etapa "modalidades oferecidas" do assistente (#30 D2) e a escolha na unidade entram na #40; a gestão de Professores (#37 D2 deixou o Coordenador sem ver Professores) vem na #42 a #45. Qualquer rota nova precisa de teste de isolamento enquanto a guarda (A6) não existir.
- **Impedimentos de desativação** (R03-8) viram real na Sprint 05 (turmas) e 07 (matrícula).
- **Tabela de permissões** (#27): os itens de menu nascem `available: false` e cada tela nova precisa ligar o seu; `teacher_permissions` tem ordem fixa (D3 do #27).
- **Conexão com o Mercado Pago**: a tela só mostra a situação (`settings.paymentAccount`); conectar e desconectar vêm na Sprint 11 e 23, e "desconectar" é ação crítica (RN-ACE-21) que já tem o `CriticalActionGuard`.
- **Ações críticas restantes** (RN-ACE-21): cancelar assinatura, exportação completa e anonimizar aluno usarão `X-Reauth-Token`; só transferir coordenação e desativar unidade o aplicam hoje.
- **Sprint 15**: equipe da plataforma entra em `TwoFactorService.requiredFor`; hoje só o Coordenador é obrigado.
- **Sprint 22** (#192, #193): `TWO_FACTOR_ENCRYPTION_KEY` entra nos segredos da publicação; perder a chave invalida todos os segredos TOTP; cabeçalhos do front (A7), localizador por IP (a lista de sessões mostra cidade vazia), e-mail real.
- **Sprint 13 (LGPD)**: nova tabela `account_email_change` guarda e-mail novo sem retenção definida; `holder_document` (CPF/CNPJ do titular, #28 D3) precisa de tratamento na LGPD.
- **Exceções OSV com validade 2027-01-15**: reavaliar com o Boot 4 ou o Spring 6.2.20.
- Nenhuma issue `depende de você` trava a Sprint 04.
- Decisões herdadas: sessão por conta (Sprint 02), perfil lido do vínculo a cada requisição (#33 D5), Coordenador-Administrador como segundo vínculo (#37 D7, ver R03-3), `If-Match` obrigatório nas edições de organização e unidade.

**Resultado da rodada 1: AJUSTAR** — sem BLOQUEIA; cinco ajustes pequenos (A1 a A5) devem entrar antes do PR `dev`→`main`, três decisões do dono (D1 a D3) e as ações de olho humano listadas.
