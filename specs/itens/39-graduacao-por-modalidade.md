# 39 — RF-MOD-02 · Graduação por modalidade e editor do sistema de graduação

Origem: issue #39 (Sprint 04); `docs/especificacao/04-modalidades-e-graduacao.md` (RF-MOD-02, RN-GRA-01, RN-MOD-01, RN-MOD-02; RN-GRA-09 só no que toca ao "exigir comprovante") e permissões em `02-contas-e-acesso.md`. Branch: `item/39-graduacao-por-modalidade`. Parte do que o #38 criou (`modality`, `organization_modality`, `Modalities`, `ModalitiesPage`).

## 1. Objetivo

Em Configurações › Modalidades, cada modalidade ativa ganha a janela "Sistema de graduação": o Coordenador liga ou desliga a graduação, marca "Exigir comprovante da graduação de origem" e edita os níveis em ordem (nome, cor, grau máximo, idade mínima e máxima, infantil ou adulto). O catálogo traz o sistema padrão do Jiu-Jitsu (IBJJF completo, com faixas infantis); ao ativar a modalidade a organização recebe uma cópia editável. O Administrador só consulta. Quem usa as definições são os itens seguintes (matrícula, Sprint 07; registrar graduação, Sprint 12).

Fora do escopo: registrar graduações, histórico, avisos e lote (RN-GRA-02 a 13, Sprints 07 e 12); o uso do comprovante na matrícula (Sprint 07: aqui só se guarda a opção); modalidades oferecidas pela unidade (#40, RN-MOD-02 na parte da unidade); faixas etárias (#41); edição do padrão do catálogo (painel, Sprint 15, RF-PNL-05); botão "restaurar padrão"; notas de versão (Sprint 15, #142).

## 2. Requisitos

- R1 (RN-MOD-01, RN-GRA-01, D1): o catálogo da plataforma guarda, por modalidade, um sistema de graduação padrão (lista ordenada de níveis). Nesta entrega só o Jiu-Jitsu tem padrão (IBJJF completo: faixas infantis intermediárias e grau máximo por faixa); as outras 13 modalidades não têm padrão. O catálogo é somente leitura para a aplicação.
- R2 (RN-GRA-01, D2): ao ativar uma modalidade para a organização, os níveis do padrão são copiados para a organização, se ela ainda não tiver níveis dessa modalidade. A cópia é da organização: mudar o padrão depois não altera o que ela já tem. Desativar e reativar não apaga nem recopia. Organizações com modalidade já ativa recebem a cópia na migração.
- R3 (RF-MOD-02, D3): `GET /api/v1/modalities/{id}/graduation` devolve, para a organização da sessão, `enabled`, `requireOriginProof`, `canManage` e a lista ordenada de níveis (`id`, `name`, `color`, `secondaryColor`, `maxDegree`, `minAge`, `maxAge`, `kids`). Só para modalidade ativa na organização.
- R4 (RF-MOD-02, RN-GRA-01, D3, D4): `PUT /api/v1/modalities/{id}/graduation` grava de uma vez `enabled`, `requireOriginProof` e a lista completa de níveis na ordem enviada (a posição é a ordem do array): nível com `id` é atualizado, sem `id` é criado, e o que ficou de fora é removido. Tudo ou nada. Só o Coordenador; só modalidade ativa.
- R5 (validação, D5): por nível, `name` de 1 a 40 caracteres e único na modalidade sem diferenciar maiúsculas e acentos; `color` (e `secondaryColor`, opcional) em `#RRGGBB`; `maxDegree` inteiro de 0 a 10; `minAge` e `maxAge` opcionais, inteiros de 0 a 120, com `minAge <= maxAge` quando os dois existem; `kids` booleano; no máximo 30 níveis. Ligar a graduação (`enabled = true`) exige ao menos um nível. Desligar mantém os níveis.
- R6 (permissão): toda rota confere no servidor `ORGANIZATION_SETTINGS`: Coordenador altera (`FULL`); Administrador só lê (`READ`, `canManage = false`); Professor, Aluno e Responsável recebem 403; sem sessão 401. A organização vem sempre da sessão.
- R7 (auditoria): gravar o sistema de graduação grava `modality.graduation_updated` com ator, organização, modalidade e antes/depois (`enabled`, `requireOriginProof`, nomes dos níveis na ordem), sem dados pessoais. Gravar sem mudança alguma é idempotente: 200 e sem nova auditoria.
- R8 (isolamento): `graduation_level` e as opções por organização são isoladas por organização no código e com row-level security; o padrão do catálogo é global e somente leitura.
- R9 (lista): `GET /api/v1/modalities` passa a trazer `graduationEnabled` por modalidade, para a tela mostrar o estado e o botão.
- R10 (tela): em "Suas modalidades", cada linha tem o botão "Graduação" (Coordenador: edita; Administrador: "Ver graduação", somente leitura). Abre a janela "Sistema de graduação" com o interruptor de graduação, a opção "Exigir comprovante da graduação de origem" e a lista de níveis em ordem, com subir/descer, adicionar e remover, cor, grau máximo, idades e infantil/adulto. Tem os quatro estados (carregando, vazio quando a modalidade não tem níveis, erro com "Tentar de novo", sem permissão), funciona a partir de 360 px, passa no axe nos dois temas e tem todos os textos em `pt-BR.json`. Erro de campo do servidor aparece no campo.
- R11: `docs/openapi.yaml` e os handlers MSW refletem as rotas novas e o campo novo, e batem com a implementação; o seed ativa e copia o padrão nas organizações de teste.

## 3. Critérios de aceite

- CA1 (R1, R8): dado o banco migrado, então o padrão do Jiu-Jitsu tem os níveis do IBJJF em ordem (faixas infantis, Branca, Azul, Roxa, Marrom, Preta, Coral, Vermelha), cada um com cor, grau máximo e idades, as demais modalidades não têm padrão, e o papel operacional não consegue inserir, alterar nem apagar o padrão.
- CA2 (R2): dado Coordenador de organização sem níveis, quando ativa o Jiu-Jitsu, então `GET graduation` devolve os níveis do padrão com `enabled = false`; dado que ele editou e depois desativa e reativa, então a edição continua e nada foi recopiado; ativar o Judô (sem padrão) devolve lista vazia.
- CA3 (R2, R8): dado uma organização com cópia editada e o padrão do catálogo alterado no banco pelo administrador de teste, então a organização continua com o que salvou; a migração copia o padrão para quem já tinha o Jiu-Jitsu ativo.
- CA4 (R3, R6): dado Coordenador, `GET graduation` de modalidade ativa responde 200 com `canManage = true`; Administrador responde 200 com `canManage = false`; Professor, Aluno e Responsável, 403; sem sessão, 401. Modalidade fora do catálogo, 404; modalidade do catálogo não ativa na organização, 409 `MODALITY_NOT_ACTIVE`.
- CA5 (R4, R7): dado Coordenador, quando grava `enabled = true`, `requireOriginProof = true` e uma lista com um nível novo, um editado (mesmo `id`), um fora da lista, então 200, a ordem devolvida é a enviada, o nível ausente some, os `id` mantidos continuam os mesmos e há uma auditoria `modality.graduation_updated`; repetir a mesma gravação devolve 200 sem nova auditoria.
- CA6 (R4, R6): dado Administrador, Professor, Aluno ou Responsável, quando `PUT graduation`, então 403 e nada muda; sem sessão, 401; modalidade inativa na organização, 409 `MODALITY_NOT_ACTIVE`; modalidade inexistente, 404.
- CA7 (R5): dado cada violação (nome vazio ou com mais de 40; nome repetido com outra caixa ou sem acento; cor fora de `#RRGGBB`; grau máximo -1 ou 11; idade -1 ou 121; `minAge` maior que `maxAge`; 31 níveis; `enabled = true` com lista vazia; corpo sem `enabled`), então 400 com o campo apontado e nada é gravado (nem os demais níveis do mesmo pedido); `id` de nível de outra modalidade ou organização, 400.
- CA8 (R5, R4): dado `enabled = false` com lista vazia, então 200 e a graduação fica desligada sem níveis; dado `enabled = false` com níveis, eles são mantidos.
- CA9 (R8): dado Coordenadores de duas organizações com a mesma modalidade, quando cada um edita, então um não vê nem altera os níveis do outro (API; e consulta direta de `graduation_level` com `ctcore_app`: sem contexto 0 linhas, com contexto de A só A, update/delete de B afetam 0, insert com B negado).
- CA10 (R7): dado cada escrita, então a auditoria tem ator, ação e alvo, sem e-mail, senha ou token.
- CA11 (R9): dado uma modalidade ativa com graduação ligada, quando `GET /modalities`, então o item traz `graduationEnabled = true`; as demais `false`.
- CA12 (R10): dado a tela em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas (lista e janela); Coordenador vê "Graduação" nas linhas ativas e edita; Administrador vê "Ver graduação" e a janela sem controles de edição; modalidade inativa não tem o botão; a janela mostra carregando, vazio ("Nenhum nível ainda"), erro com "Tentar de novo" e sem permissão.
- CA13 (R10, R4): dado a janela aberta, quando o Coordenador muda um nome, sobe um nível, adiciona e remove outro e salva, então o pedido sai com a ordem da tela, a janela fecha e avisa "Sistema de graduação salvo"; com nome vazio o erro aparece no campo e nada é enviado; erro 400 do servidor aparece no campo apontado.
- CA14 (R11): `OpenApiContractTest` e `ContractRoutesTest` passam; testes MSW cobrem as rotas novas e `graduationEnabled` no formato do servidor; `SeedDataIT` aplica o novo seed.
- CA15 (todos): ponta a ponta Playwright `@critical` "Coordenador liga a graduação do Jiu-Jitsu e edita um nível": entra, abre Modalidades, ativa Jiu-Jitsu, abre "Graduação", liga, renomeia um nível, salva e reabre vendo a mudança.

## 4. Plano técnico

- **Dados.** Migração `V20__graduation.sql` (a próxima depois de `V19__modality.sql`; revert comentado no topo como `V19`): `modality_default_level` (global, sem RLS; `modality_id` referência a `modality`, `position int`, `name`, `color`, `secondary_color`, `max_degree`, `min_age`, `max_age`, `kids`; chave `(modality_id, position)`; checks iguais ao R5) com os níveis do Jiu-Jitsu inseridos na migração, e o mesmo `revoke insert, update, delete` em loop usado em `V19` (o grant em massa de `prepare-runtime-db.sh` já foi tratado para `modality`; conferir se cobre a tabela nova, senão acrescentar a linha no script, como no #38). `alter table organization_modality add column graduation_enabled boolean not null default false, add column require_origin_proof boolean not null default false`. `graduation_level` (`id uuid` chave, `organization_id`, `modality_id`, `position`, mesmos campos e checks; únicos `(organization_id, modality_id, position)` deferrable e `(organization_id, modality_id, lower(name))`; RLS `organization_scope` como em `V19`; índice por `(organization_id, modality_id)`). Backfill: copiar o padrão para as linhas de `organization_modality` já ativas. Seed `infra/seed/06-graduacao.sql` (`on conflict do nothing`): copia o padrão para as organizações fictícias que têm Jiu-Jitsu ativo (o seed 05 insere a ativação direto, sem passar pelo código) e liga a graduação em uma delas.
- **API:** `GET` e `PUT /api/v1/modalities/{id}/graduation`; novo campo `graduationEnabled` em `GET /modalities`. Erro novo `MODALITY_NOT_ACTIVE` (409) em `api/error/ErrorCode.java` e `messages.properties`; validação por `ApiValidationException(campo, mensagem)` com campo como `levels[2].name`, como em `Modalities`. Rotas em `docs/openapi.yaml` no mesmo PR (esquemas `Graduation`, `GraduationLevel`, `GraduationUpdate`; `graduationEnabled` em `Modality`; código novo no enum de erro).
- **Back-end:** pacote a criar `br.com.ctcore.graduation`: `Graduation.java` (leitura e gravação; mesmo `access`/`requireFull` de `Modalities` com `Permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null)`; transação da organização via `OrganizationTransactions`; auditoria por `AuditService` com `AuditEntry`; nome comparado com `Modalities.normalize`, hoje `static` de pacote: tornar `public`) e `GraduationController.java`. A alterar: `modality/Modalities.java` (`Item` ganha `graduationEnabled`; `setActive(true)` copia o padrão se a organização não tem níveis). Gravação: carrega os níveis atuais, valida tudo antes de escrever, apaga os ausentes, atualiza ou insere e regrava as posições na mesma transação (posição única deferrable evita conflito na troca de ordem).
- **Interface:** a criar em `frontend/src/features/modalities/`: `GraduationDialog.tsx` (janela "Sistema de graduação", mesmo padrão de `RequestModalityDialog.tsx`, ordenação por botões subir/descer acessíveis em vez de arrastar, `<input type="color">` nativo), `useGraduation.ts` (TanStack Query como `useModalities.ts`) e testes. A alterar: `ModalitiesPage.tsx` (botão "Graduação" nas linhas ativas), `useModalities.ts` (tipo com `graduationEnabled`), `mocks/handlers.ts` e `mocks/modalities.msw.test.ts`, `i18n/pt-BR.json`, `frontend/e2e/modalidades.spec.ts`. Reuso: `Card`, `states/*`, `Button`, validação de campo de `RequestModalityDialog.tsx`. Canvas `ModalidadeGraduacao.dc.html` não acessível ao agente (D6).
- **Segurança.** Organização e perfil vêm do principal da sessão, nunca do corpo (RNF-05); o `modalityId` da URL é sempre conferido contra o catálogo e a ativação da organização; `id` de nível vindo do corpo só vale se pertencer à organização e à modalidade da URL (400 caso contrário, sem vazar existência); cores validadas por padrão fixo (sem CSS livre); nomes são texto puro, escapados pelo React; auditoria sem dado pessoal; limite de 30 níveis contra abuso.
- **Riscos.** (1) Níveis hoje não são referenciados por nada; quando a Sprint 12 criar graduações, remover um nível em uso deve ser recusado (nota para #7x/Sprint 12: trocar a remoção por checagem de uso, como `ModalityUsage`). (2) O padrão IBJJF (idades e graus) é suposição (D5): fica só no SQL da migração, fácil de ajustar antes do merge. (3) Último gravador vence: sem controle de versão (D7).

## 5. Tarefas

Uma parte só, em duas etapas: servidor (T1 a T4) e tela (T5 a T7).

- [x] T1: migração `V20__graduation.sql` (padrão do Jiu-Jitsu, colunas em `organization_modality`, `graduation_level` com RLS, backfill, revert comentado) e seed `infra/seed/06-graduacao.sql` — cobre CA1, CA3, CA14 (seed) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='GraduationCatalogIT,SeedDataIT,RowLevelSecurityIT'` (o primeiro a criar; inclui a prova de RLS e de escrita negada no padrão).
- [x] T2: `Graduation` e `GraduationController`, `ErrorCode.MODALITY_NOT_ACTIVE` e mensagem, cópia do padrão e `graduationEnabled` em `Modalities` — cobre CA2, CA4, CA5, CA7, CA8, CA11 — prova: `verify -Dit.test='GraduationIT,ModalityIT'` (o primeiro a criar).
- [x] T3: testes de isolamento entre organizações e entre perfis, sem sessão, e auditoria sem dados proibidos — cobre CA4, CA6, CA9, CA10 — prova: `verify -Dit.test=GraduationIT` (casos de permissão e isolamento).
- [x] T4: OpenAPI (duas operações, esquemas, campo novo, erro) e handlers MSW — cobre CA14 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T5: `useGraduation`, botão "Graduação" em `ModalitiesPage` e `GraduationDialog` com os quatro estados, modo somente leitura e textos pt-BR — cobre CA12 — prova: `npx vitest run src/features/modalities`.
- [x] T6: edição na janela (interruptor, comprovante, níveis com subir/descer/adicionar/remover, validação de campo, erro do servidor); 360 px e axe nos dois temas — cobre CA12, CA13 — prova: `npx vitest run src/features/modalities`.
- [x] T7: e2e `@critical` "Coordenador liga a graduação do Jiu-Jitsu e edita um nível" e regressão completa — cobre CA15; a regressão não cobre critério: confere o "Pronto quando" (cobertura e 100% nos pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: só o Jiu-Jitsu tem sistema padrão. A issue e a RN-GRA-01 só citam o do Jiu-Jitsu; para as outras modalidades o Coordenador monta os níveis. Alternativa: inventar padrões para Judô, Karatê etc.; descartada por não haver fonte na especificação.
- D2: a cópia do padrão acontece ao ativar a modalidade (e na migração para as já ativas), não na primeira leitura, porque leitura não deve gravar. O dado fica da organização desde então (dado da spec: "cópia editável do padrão"). Alternativa: copiar ao ligar a graduação; descartada porque o editor precisa dos níveis mesmo com a graduação desligada.
- D3 (muda o comportamento): só o Coordenador liga, desliga e edita (RN-GRA-01); o Administrador consulta, a mesma regra de permissão da tela de modalidades (`ORGANIZATION_SETTINGS`, D5 do #38). Alternativa: Administrador também editar; se o dono quiser, muda só o `Access` exigido. A janela só abre para modalidade ativa (RF-MOD-02 "por modalidade ativa").
- D4: o editor grava o sistema inteiro numa chamada só, com `id` estável dos níveis existentes, para que graduações futuras possam referenciar o nível sem quebrar ao reordenar. Alternativa: uma rota por nível; descartada por multiplicar rotas e deixar a ordem inconsistente entre chamadas.
- D5: limites de validação (nome até 40, 30 níveis, grau 0 a 10, idade 0 a 120, cor `#RRGGBB` com segunda cor opcional para faixas de duas cores) e o conteúdo exato do padrão IBJJF (faixas infantis de 4 a 15 anos com grau máximo 4; Branca, Azul, Roxa, Marrom e Preta adultas com idade mínima 16, 16, 16, 18 e 19; Preta com 6 graus; Coral e Vermelha com idade mínima 31 e 41) são suposição minha, não vêm da especificação. Se estiverem errados, muda só a migração (antes do merge) ou a validação.
- D6: o canvas `ModalidadeGraduacao.dc.html` não está acessível ao agente (como no #38); layout e textos seguem os padrões do projeto e o texto da issue, e o dono confere no PR.
- D7 (muda o comportamento): desligar a graduação mantém os níveis e a opção de comprovante; ligar sem nenhum nível é recusado. Gravações simultâneas: vence a última, sem controle de versão (suposição: só o Coordenador edita, e é raro haver dois). Se estiver errada, o custo é uma coluna de versão como em `V17__config_version.sql`.
- D8: "Exigir comprovante da graduação de origem" só é guardada e exposta aqui (`requireOriginProof`); quem a aplica é a matrícula (Sprint 07, RN-GRA-09). RN-MOD-02 na parte "cada unidade marca quais oferece" é o #40; a parte do Coordenador já foi entregue no #38.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V20__graduation.sql`, `infra/seed/06-graduacao.sql`, `infra/scripts/prepare-runtime-db.sh` (revoke de escrita em `modality_default_level`), `GraduationCatalogIT` (novo), `SeedDataIT` e limpezas de `graduation_level` em `ModalityIT`/`ModalityCatalogIT` — prova: `verify -Dit.test='GraduationCatalogIT,GraduationIT,ModalityIT,ModalityCatalogIT,SeedDataIT,RowLevelSecurityIT'` → todos passam (3 + 6 + 5 + 2 + 9 + 1); a migração é provada também por Flyway em esquema à parte (backfill, CA3).
- T2 — arquivos: `graduation/Graduation.java`, `graduation/GraduationController.java`, `modality/Modalities.java` (`graduationEnabled`, cópia do padrão ao ativar, `normalize` público), `ErrorCode.MODALITY_NOT_ACTIVE`, `messages.properties`, `GraduationIT` — prova: mesma execução acima → passa.
- T3 — arquivos: `GraduationIT` (perfis, 401, 404, 409, isolamento entre organizações, auditoria sem dado pessoal) — prova: mesma execução → passa.
- T4 — arquivos: `docs/openapi.yaml` (rota GET/PUT, esquemas `Graduation`, `GraduationLevel`, `GraduationUpdate`, `graduationEnabled`, `MODALITY_NOT_ACTIVE`), `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/modalities.msw.test.ts` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes passam; `npx vitest run src/mocks src/features/modalities` → 66 passam.
- T5, T6 — arquivos: `frontend/src/features/modalities/{useGraduation.ts,GraduationDialog.tsx,GraduationDialog.test.tsx,ModalitiesPage.tsx,useModalities.ts,ModalitiesPage.test.tsx}`, `frontend/src/i18n/pt-BR.json` — prova: `npx vitest run src/features/modalities` → 18 passam (quatro estados, somente leitura, subir/adicionar/remover, erro de campo local e do servidor, axe a 360 px nos dois temas).
- T7 — arquivos: `frontend/e2e/modalidades.spec.ts` — prova: `npm run e2e` → 17 passam (inclui o `@critical` novo); `npm run lint`, `npm run typecheck`, `npm run build` limpos; `npm run test:cov` → 452 testes, cobertura 94,08% de linhas.
Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 252 testes de integração sem falha, JaCoCo sem regra violada; no front, `npm run lint && typecheck && build && test:cov && e2e` verdes (e2e: 17). Sem piora em relação à linha de base.
Desvios: (1) padrão do Jiu-Jitsu com 19 níveis: 12 infantis de duas cores (Cinza e Branca … Verde e Preta, idades mínimas 4, 7, 10 e 13, máxima 15, grau máximo 4), Branca, Azul, Roxa, Marrom, Preta (6 graus), Coral e Vermelha, com `secondaryColor` para as faixas de duas cores (suposição D5). (2) O seed usa `on conflict (id) do nothing` mais `not exists`, porque `ON CONFLICT` sem alvo não aceita a restrição de posição deferrable. (3) Para trocar nomes sem colidir no índice único não deferrable, a gravação renomeia temporariamente os níveis mantidos para o próprio `id` antes de regravar. (4) A janela monta o conteúdo só quando abre, para reabrir sempre ler o servidor (o e2e achou dado velho com cache). (5) Gravar sem `requireOriginProof` é lido como falso. (6) Primeira rodada do `verify` completo falhou em `S3FileStorageIT` (2 erros) por eu ter rodado dois `verify` ao mesmo tempo; sozinho o teste passa (5 de 5).
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 16352c47b697f95fdd0eb682712f671c38a6c8ea

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `GraduationCatalogIT.defaultIsReadOnlyAndOnlyForJiuJitsu` (19 níveis em ordem, só Jiu-Jitsu, insert/update/delete negados a `ctcore_app`); `verify` verde |
| CA2 | PASSOU | `GraduationIT.copyOnActivation` |
| CA3 | PASSOU | `GraduationIT.copyIsIndependent`; `GraduationCatalogIT.migrationBackfillsActiveModalities` |
| CA4 | PASSOU | `GraduationIT.permissions` (200/403/401/404/409) |
| CA5 | PASSOU | `GraduationIT.save` (ids mantidos, ausente some, troca de nomes, repetição sem nova auditoria) |
| CA6 | PASSOU | `GraduationIT.permissions` |
| CA7 | PASSOU | `GraduationIT.validation` (16 casos, nada gravado, id de outra org 400) |
| CA8 | PASSOU | `GraduationIT.save` (desliga com lista vazia e com níveis) |
| CA9 | PASSOU | `GraduationIT.isolation`; `GraduationCatalogIT.rlsIsolatesLevels` |
| CA10 | PASSOU | `GraduationIT.save` (auditoria sem `@`, password, token) |
| CA11 | PASSOU | `GraduationIT.copyOnActivation` |
| CA12 | PASSOU | `GraduationDialog.test.tsx`/`ModalitiesPage.test.tsx` (quatro estados, somente leitura, axe 360 px nos dois temas); 452 testes do front passam |
| CA13 | PASSOU | `GraduationDialog.test.tsx` (ordem enviada, erro de campo local e do servidor) |
| CA14 | PASSOU | `verify` inclui `OpenApiContractTest`, `ContractRoutesTest`, `SeedDataIT`; `modalities.msw.test.ts` no front |
| CA15 | PASSOU | `npm run e2e`: 17 passam, inclui o `@critical` novo |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (126 unitários, 252 de integração, 0 falhas, JaCoCo sem regra violada); em `frontend/`, `npm run lint && typecheck && build && test:cov && e2e` → exit 0 (452 testes, 94,08% de linhas, e2e 17 passam).
Achados:
- sugestão, código, `Graduation.save` — gravação sem controle de versão (último vence), já registrada como D7.
- sugestão, código, `Graduation.save` — remover nível ainda é livre; já anotado em Riscos para a Sprint 12.

## Entrega

Entrega o editor do sistema de graduação por modalidade (RF-MOD-02): migração `V20__graduation.sql` com o padrão do Jiu-Jitsu copiado ao ativar a modalidade (e no backfill das já ativas), rota `GET/PUT /api/v1/modalities/{id}/graduation` (Coordenador edita, Administrador consulta, isolamento por organização com RLS, auditoria sem dado pessoal), `graduationEnabled` na lista de modalidades e a janela "Sistema de graduação" na tela de modalidades, com os quatro estados, 360 px, axe nos dois temas e textos em pt-BR. Contrato, MSW e seed atualizados.

Verificação: `verify` do back-end verde (126 unitários, 252 de integração, JaCoCo ok); front com lint, typecheck, build, `test:cov` (94,08%) e `e2e` (17) verdes.

Decisões a conferir no PR: D5 (conteúdo e limites do padrão IBJJF são suposição, não vêm da especificação), D6 (canvas inacessível ao agente) e D7 (gravação simultânea: vence a última). Limitação: remover nível ainda é livre até a Sprint 12.
