# 10 — Esteira de CI do monorepo

Origem: issue #10 (Sprint 00 · Fundação), RNF-35, RNF-36 e RNF-37 (`docs/plano-de-sprints.md`). Branch: `item/10-esteira-ci`.

## 1. Objetivo

Criar a esteira do GitHub Actions que valida todo PR: etapas separadas para `backend/` e `frontend/`, que rodam só quando a pasta muda (e todas no PR de `dev` para `main`), com build, testes, contrato, acessibilidade, ponta a ponta, cobertura mínima e varredura de dependências e segredos. Um PR com falha em qualquer etapa não passa.

Fora do escopo: imagens Docker e fluxo de publicação (#11); hospedagem (#192); os testes de ponta a ponta dos fluxos de negócio (cada item de tela cria os seus); as regras de cobrança, pagamentos, assinatura, inativação e isolamento (pacotes ainda não existem, ver D4).

## 2. Requisitos

- R1: um workflow roda em todo PR para `dev` e para `main` (RNF-35, RNF-36, RNF-37).
- R2: a etapa do back-end roda quando `backend/` muda e executa build, testes unitários, integração com Testcontainers e contrato contra o OpenAPI (`mvnw verify`) (D1).
- R3: a etapa do front-end roda quando `frontend/` muda e executa lint, tipos, build e Vitest, que já inclui o axe nos dois temas (D1).
- R4: as etapas rodam só quando a sua pasta muda; no PR de `dev` para `main` rodam todas; mudança em `docs/openapi.yaml`, `.github/` ou `infra/ci` conta como mudança das duas pastas (D2).
- R5: cobertura mínima de 80% nos dois projetos, e o PR falha abaixo disso (RNF-37) (D3).
- R6: nos pacotes das regras de cobrança, pagamentos, assinatura, inativação e isolamento a cobertura mínima é 100%, configurada já agora para valer quando os pacotes existirem (RNF-37) (D4).
- R7: Playwright roda os fluxos críticos (marcados `@critical`) em todo PR que muda `frontend/`, e a suíte completa no PR de `dev` para `main` (RNF-36) (D5).
- R8: varredura de dependências com falha conhecida (Maven e npm) e de segredos no código a cada PR; achado falha o PR (RNF-35) (D6).
- R9: um job final `ci-gate` falha se qualquer etapa falhou ou foi cancelada e passa se as não executadas foram só puladas por pasta (D2).
- R10: `README.md` e `AGENTS.md` (seção Comandos) dizem como rodar localmente cada etapa e o que o CI exige.

## 3. Critérios de aceite

- CA1 (R1): dado um PR para `dev` ou para `main`, quando é aberto ou recebe novo commit, então o workflow `CI` roda e mostra o status `ci-gate`.
- CA2 (R4): dado um PR que muda só `frontend/src`, quando o workflow roda, então as etapas do back-end ficam puladas e `ci-gate` passa se as do front-end passam; o inverso vale para só `backend/`.
- CA3 (R4): dado um PR de `dev` para `main`, quando o workflow roda, então back-end, front-end e Playwright completo rodam mesmo sem diferença de pasta; dado mudança só em `docs/openapi.yaml`, então as duas rodam.
- CA4 (R2): dado um teste unitário, um `*IT` ou o `OpenApiContractTest` quebrado, quando a etapa do back-end roda, então ela falha e `ci-gate` falha.
- CA5 (R3): dado erro de lint, de tipo, de teste ou violação do axe em um dos temas, quando a etapa do front-end roda, então ela falha.
- CA6 (R5): dada a cobertura de linhas do back-end ou do front-end abaixo de 80%, quando a etapa roda, então ela falha com o relatório de cobertura anexado ao run.
- CA7 (R6): dada uma classe nos pacotes críticos (ver D4) sem cobertura total, quando a etapa roda, então ela falha; sem classes nesses pacotes, a regra passa.
- CA8 (R7): dado um fluxo `@critical` quebrado, quando o PR muda `frontend/`, então o job Playwright falha e anexa o relatório; no PR de `dev` para `main` roda também o que não tem a marca.
- CA9 (R8): dado um segredo de teste (chave falsa em formato reconhecido) commitado, quando a varredura roda, então ela falha apontando o arquivo; dada uma dependência com falha conhecida de severidade alta ou crítica, então a varredura falha.
- CA10 (R9): dada uma etapa que falha, é cancelada, ou todas puladas num PR que não muda nenhuma pasta, quando `ci-gate` roda, então falha nos dois primeiros casos e passa no último.
- CA11 (R10): dado o `README.md` e o `AGENTS.md`, quando se lê, então trazem os comandos locais de cada etapa e a lista do que o CI exige.

## 4. Plano técnico

- Dados: não muda.
- API: não muda; `docs/openapi.yaml` só é lido (contrato já testado por `OpenApiContractTest`).
- Interface: não muda de comportamento; entra Playwright como ferramenta de teste.
- Segurança: workflow com `permissions: contents: read`; sem segredo de repositório (nada de fornecedor); actions fixadas por versão exata (`@vX.Y.Z`), e o gitleaks e o osv-scanner baixados como binário de versão fixa quando não houver action oficial gratuita; `pull_request` (não `pull_request_target`), para PR de fork não receber segredos.
- Arquivos: existem e mudam: `backend/pom.xml` (plugin JaCoCo com relatório e regras de 80% e 100%), `frontend/package.json` (scripts `test:cov`, `e2e`, `audit`, devDependency `@playwright/test`), `frontend/vite.config.ts` (bloco `coverage.thresholds`: 80 global e 100 nas pastas críticas), `README.md`, `AGENTS.md` (Comandos), `frontend/README.md`, `backend/README.md`. A criar: `.github/workflows/ci.yml`, `infra/scripts/changed-areas.sh` (decide `backend`/`frontend` pelas pastas alteradas) e `infra/scripts/changed-areas.test.sh` (prova local), `frontend/playwright.config.ts` (sobe `npm run dev` com MSW), `frontend/e2e/smoke.spec.ts` (`@critical`: app abre, textos em pt-BR, alterna tema claro e escuro), `.gitleaks.toml` se for preciso liberar falso positivo.
- Reuso: `mvnw verify` (surefire para `*Test`, failsafe para `*IT`, já configurados no `pom.xml`); `OpenApiContractTest` e `frontend/src/mocks/msw.test.ts` para o contrato; `frontend/src/test/a11y.test.tsx` e `axe.ts` para o axe nos dois temas; `@vitest/coverage-v8` já instalado; Node 24 e Java 21 como no README.
- Riscos: (1) a cobertura atual pode estar abaixo de 80% e quebrar o primeiro run: medir antes e completar testes dos itens já feitos só se faltar pouco; senão registrar na construção. (2) Testcontainers no runner `ubuntu-latest` exige o Docker dele, que já existe. (3) Fixture de falso positivo no gitleaks (chaves de exemplo na documentação): ajustar `.gitleaks.toml`. (4) Varredura de dependências muda sem o código mudar (nova falha publicada): pode reprovar PR sem relação; o `ci-gate` mostra qual achado, e a correção é atualizar a dependência. (5) `ci-gate` só vira barreira se for exigido na proteção de branch (T9).

## 5. Tarefas

- [x] T1: criar `infra/scripts/changed-areas.sh` (entrada: base e cabeça do PR, mais o nome do branch alvo; saída: `backend=true|false`, `frontend=true|false`) e `changed-areas.test.sh` com os casos de CA2 e CA3 — cobre CA2, CA3 — prova: `bash infra/scripts/changed-areas.test.sh`.
- [x] T2: medir a cobertura atual (`mvnw verify` com JaCoCo e `npm test -- --coverage`); adicionar JaCoCo ao `pom.xml` e `coverage.thresholds` ao `vite.config.ts` com 80% global e as regras de 100% nos pacotes críticos (D4) — cobre CA6, CA7 — prova: `./backend/mvnw -f backend/pom.xml verify` e `npm test -- --coverage` verdes; baixando o limite de teste para 99 na verificação local, falham.
- [x] T3: adicionar Playwright (`@playwright/test`), `playwright.config.ts`, `e2e/smoke.spec.ts` com a marca `@critical` e os scripts `e2e` (só `@critical`) e `e2e:full` — cobre CA8 — prova: `npm run e2e` e `npm run e2e:full` verdes; quebrando o texto esperado, falham.
- [ ] T4: criar `.github/workflows/ci.yml` com os jobs `changes`, `backend`, `frontend`, `e2e`, `security` (dependências e segredos) e `ci-gate` — cobre CA1, CA4, CA5, CA6, CA8, CA9, CA10 — prova: `actionlint` (ou leitura do run) sem erro e o run do próprio PR verde.
- [ ] T5: provar as falhas: em branch descartável (não vira PR de item) commitar um segredo falso, quebrar um teste do back-end e um do front-end, e conferir que `ci-gate` falha em cada caso; registrar os links dos runs na Construção — cobre CA4, CA5, CA9, CA10 — prova: runs vermelhos e branch descartável apagada.
- [ ] T6: provar a seleção por pasta no PR do item: o PR do item muda `backend/`, `frontend/` e `.github/`, então roda tudo; conferir uma segunda vez com um PR descartável só de `docs/` ou de `frontend/` — cobre CA2, CA3 — prova: runs com jobs pulados e `ci-gate` verde.
- [x] T7: atualizar `README.md`, `AGENTS.md` (Comandos), `frontend/README.md` e `backend/README.md` com os comandos locais e o que o CI exige — cobre CA11 — prova: leitura dos arquivos.
- [x] T8: referenciar RNF-35, RNF-36 e RNF-37 em ao menos um teste/artefato de cada: `changed-areas.test.sh`, `smoke.spec.ts` e os limites de cobertura (comentário com o código na configuração) — não cobre critério: a issue exige "cada código tem ao menos um teste" e o verificador procura o código — prova: `grep -rn "RNF-3[567]"` retorna os três.
- [ ] T9: depois do primeiro run verde, tentar exigir o status `ci-gate` em `dev` e `main` por `gh api` (proteção de branch); se o plano do GitHub não permitir, registrar na Construção e na decisão D7 — não cobre critério: faz o `ci-gate` ser barreira real — prova: `gh api repos/{owner}/{repo}/branches/dev/protection` mostra o status exigido, ou a resposta do GitHub fica registrada.

## 6. Decisões

- D1: o back-end roda `mvnw verify` num único job (build, `*Test`, `*IT`, contrato e cobertura), e o front-end num job (lint, tipos, build, Vitest com axe e cobertura). Motivo: o projeto já separa assim as fases do Maven e os testes do axe e do contrato estão no Vitest e no JUnit. Alternativa: um job por tipo de teste, descartada por repetir o setup e o custo de minutos sem ganho agora.
- D2: a seleção por pasta é feita por `infra/scripts/changed-areas.sh`, não por `paths:` do workflow nem por action de terceiros. Motivo: workflow filtrado por `paths:` nem aparece no PR e travaria a proteção de branch; com um job `ci-gate` que sempre roda, "pulado por pasta" conta como verde. `docs/openapi.yaml`, `.github/` e `infra/ci` acionam as duas pastas porque o contrato e a esteira afetam ambas. Alternativa: `dorny/paths-filter`, descartada para não depender de ação de terceiros.
- D3: cobertura medida por linhas: JaCoCo no back-end e `v8` no front-end, 80% global. Motivo: é o que as ferramentas já instaladas entregam. Suposição: se a cobertura atual estiver abaixo de 80%, a construção completa os testes que faltam ou, se for muito, devolve o caso ao dono em vez de baixar o limite.
- D4 (muda o comportamento): "100% das regras de cobrança, pagamentos, assinatura, inativação e isolamento" é tratado em duas partes: (a) cobertura de linhas de 100% nos pacotes `br.com.ctcore.billing`, `payment`, `subscription`, `deactivation` e `isolation` do back-end e nas pastas `src/features/{billing,payments,subscription,deactivation}` do front-end, configurada agora e sem efeito até existirem classes; (b) "cada código de regra tem ao menos um teste" segue como critério de pronto de cada item, conferido na verificação, sem script. Motivo: não há como medir "regra" automaticamente sem inventar um formato de marcação; linha coberta é o que a esteira consegue exigir. Alternativa: script que cruza códigos da especificação com os testes, descartada por exigir convenção que os itens seguintes teriam de seguir e dar falso verde. Os nomes de pacote são suposição: se os itens das sprints seguintes usarem outros, ajustam o `pom.xml` e o `vite.config.ts` no mesmo PR.
- D5: Playwright roda contra `npm run dev` (MSW ligado), com Chromium só; `@critical` em todo PR que muda `frontend/` e todos os testes no PR de `dev` para `main`. Motivo: não há back-end publicado nem fluxo de negócio ainda; o fluxo crítico de hoje é abrir o app, ver os textos e trocar de tema, e cada item de tela acrescenta o seu. Alternativa: subir o back-end e o banco no CI para o e2e, descartada por ser o que os itens de login (Sprint 02) já precisam e podem fazer então.
- D6: varredura de dependências com OSV-Scanner (cobre `pom.xml` e `package-lock.json`, sem chave e sem custo) reprovando severidade alta e crítica, e de segredos com gitleaks sobre o histórico do PR. Motivo: uma ferramenta cobre as duas pilhas e roda em runner gratuito. Alternativas: OWASP Dependency-Check (lento e pede chave da NVD) e `dependency-review-action` (exige recurso pago em repositório privado); ficam como troca possível se o OSV falhar no uso. Suposição: o repositório é privado no plano gratuito; se for público, a varredura continua válida.
- D7 (muda o comportamento): o merge só é impedido de verdade se `ci-gate` for exigido na proteção de branch (T9). Motivo: a issue diz que o PR não passa se algo falhar; sem proteção, é só a regra do agente (AGENTS.md: merge só com verificações verdes). Suposição: o plano do GitHub talvez não ofereça proteção de branch em repositório privado gratuito; nesse caso a regra continua do agente e o fato é devolvido ao dono como informação, sem travar o item.
- Suposição: o que a #11 acrescenta (imagens Docker, publicação) entra na mesma `ci.yml` depois, como jobs novos; esta esteira não deixa gancho para isso.

## Construção

- T1 — arquivos: `infra/scripts/changed-areas.sh`, `infra/scripts/changed-areas.test.sh` — prova: `bash infra/scripts/changed-areas.test.sh` → 6 casos ok (só frontend, só backend, só docs, openapi, workflow, main sem diferença)
- T2 — arquivos: `backend/pom.xml` (jacoco 0.8.15: relatório, regra BUNDLE 80% e PACKAGE 100% em billing/payment/subscription/deactivation/isolation), `frontend/vite.config.ts` (`coverage.thresholds`) — medido: back-end 282/295 linhas (95,6%), front-end 96,86% — prova: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, "All coverage checks have been met"; `npm run test:cov` → verde. Negativos: mínimo 0,99 no back-end → "Rule violated for bundle … 0.95 … 0.99"; regra de pacote apontada para `br.com.ctcore.integration` → "Rule violated for package … 0.89 … 1.00" (confirma que o padrão de pacote funciona); `lines: 99` no front-end → "Coverage for lines (96.86%) does not meet global threshold (99%)". Limites restaurados.
- T3 — arquivos: `frontend/package.json` (+`@playwright/test` 1.64.1, scripts `test:cov`, `e2e`, `e2e:full`, `audit`), `frontend/playwright.config.ts`, `frontend/e2e/smoke.spec.ts`, `frontend/vite.config.ts` (`test.exclude: e2e/**`), `frontend/tsconfig.node.json` — prova: `npm run e2e` e `npm run e2e:full` → 1 passed cada; texto esperado quebrado → 1 failed; `npm run lint`, `typecheck`, `build`, `npm test` (87 testes) verdes.
- T7 — arquivos: `README.md` (seção CI e comandos), `AGENTS.md` (Comandos), `frontend/README.md`, `backend/README.md` — prova: leitura.
- T8 — prova: `grep -rn "RNF-3[567]"` → `changed-areas.sh`, `changed-areas.test.sh` (RNF-35), `smoke.spec.ts` (RNF-36), `vite.config.ts` e `pom.xml` (RNF-37).
- T4 (parcial, desmarcada) — arquivo: `.github/workflows/ci.yml` (jobs changes, backend, frontend, e2e, security, ci-gate); `actionlint` 1.7.12 → sem erro. Falta o run verde do PR, que só existe depois do push na entrega. Não rodou local: gitleaks, osv-scanner e `jq` (não instalados aqui); o filtro de severidade do `jq` e o formato do JSON do OSV (`results[].packages[].groups[].max_severity`) só serão provados no run.
- T5, T6, T9 — não feitas: exigem push, branches descartáveis, PRs e proteção de branch no GitHub, o que é da fase 5 (esta fase não faz commit nem push).

Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (15 ITs, Docker ativo); em `frontend/`: `npm run lint`, `typecheck`, `build`, `test:cov` (19 arquivos, 87 testes), `npm audit --audit-level=high` (0 vulnerabilidades), `npm run e2e` → todos verdes. Não havia linha de base registrada com falhas.
Desvios: versões das actions fixadas nas últimas releases (checkout v7.0.1, setup-java v6.1.0, setup-node v7.1.0, upload-artifact v7.0.2), gitleaks 8.30.1 e osv-scanner v2.7.0 baixados como binário. Filtro de severidade do OSV feito por `jq` sobre o JSON, pois o scanner não tem limite de severidade. Sem `.gitleaks.toml` (nenhum falso positivo conhecido ainda).
Não é do item: nada.
