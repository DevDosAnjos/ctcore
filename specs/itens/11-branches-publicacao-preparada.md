# 11 — Branches dev e main e publicação preparada (sem hospedagem)

Origem: issue #11 (Sprint 00 · Fundação). Branch: `item/11-branches-publicacao-preparada`.

## 1. Objetivo

Deixar a publicação pronta para ser ligada na #192: conferir que o CI cobre os PRs para `dev` e `main`, gerar a imagem Docker do back-end e a do front-end no CI (cada uma só quando a sua pasta muda), escrever e testar o fluxo de publicação até o ponto de enviar (que é pulado, sem falhar, quando faltam as chaves) e documentar no README como ligá-lo.

Fora do item: contratar hospedagem, ligar a publicação automática em `dev` e `main`, aprovação humana de `main` e backup (RNF-17): são a #192 e a #193.

## 2. Requisitos

- R1: o workflow `CI` roda nos PRs para `dev` e para `main` (inclusive o PR de `dev` para `main`) e `ci-gate` é exigido em `dev` (D1).
- R2: existe um `Dockerfile` para o back-end e outro para o front-end, cada um gerando uma imagem que sobe (D2).
- R3: o CI constrói a imagem do back-end só quando a etapa `backend` roda, e a do front-end só quando a `frontend` roda; as duas entram em `ci-gate` (D3).
- R4: existe um fluxo de publicação (`publish.yml`) que constrói as duas imagens, cada uma só se a sua pasta mudou, e as envia ao registro de imagens (D4).
- R5: sem as chaves do ambiente, o envio é pulado e o fluxo termina verde, mostrando no resumo do run que foi pulado e por quê (D5).
- R6: nenhuma chave, usuário ou endereço de fornecedor fica no repositório; o fluxo só lê variáveis e segredos do GitHub com nomes genéricos (D5).
- R7: o README traz o passo a passo para ligar a publicação quando a hospedagem for contratada (quais segredos e variáveis cadastrar, como testar).
- R8: `README.md`, `AGENTS.md` (Comandos), `backend/README.md` e `frontend/README.md` dizem como construir e rodar as imagens localmente e o que o CI passa a exigir.

## 3. Critérios de aceite

- CA1 (R1): dado o repositório, quando se consulta `gh api repos/{owner}/{repo}/branches/dev/protection`, então `ci-gate` consta como status exigido; e `ci.yml` tem `on.pull_request.branches: [dev, main]`.
- CA2 (R2): dado `docker build` em `backend/` e em `frontend/`, quando termina, então gera as imagens; a do back-end sobe com o PostgreSQL do compose e responde `200` em `/actuator/health`; a do front-end serve a página inicial em `/` e devolve `index.html` numa rota inexistente do app (ex.: `/qualquer`), e não serve `/api` como arquivo.
- CA3 (R3): dado um PR que muda só `frontend/src`, quando `CI` roda, então só a imagem do front-end é construída e `ci-gate` passa; o inverso vale para só `backend/`; dado um Dockerfile quebrado, então o job da imagem falha e `ci-gate` falha.
- CA4 (R4): dado um push em `dev` ou `main`, ou um disparo manual, quando `publish.yml` roda, então constrói a imagem de cada pasta alterada (no disparo manual, as duas) e, havendo chaves, envia com as etiquetas `<branch>` e o SHA do commit.
- CA5 (R5): dado um run sem as variáveis e segredos de publicação, quando `publish.yml` roda, então as etapas de login e envio ficam puladas, o run termina verde e o resumo diz "publicação pulada: faltam chaves".
- CA6 (R6): dado `gitleaks` no PR e `grep -rniE "ghcr|amazonaws|digitalocean|azurecr|gcr.io|docker.io/[a-z]" .github` , então sem achado de segredo nem de fornecedor fixo.
- CA7 (R7, R8): dado o `README.md`, quando se lê, então traz a seção "Publicação" com os nomes dos segredos e das variáveis, o passo de ligar e o de testar; e `AGENTS.md`, `backend/README.md` e `frontend/README.md` trazem os comandos de imagem.
- CA8 (R4, R5): dado o `publish.yml`, quando passa pelo `actionlint`, então não há erro; e as permissões do workflow são só `contents: read`, com `packages: write` apenas se o registro escolhido na #192 pedir (D4).

## 4. Plano técnico

- Dados e API: não mudam. `/actuator/health` já existe (`application.yml` expõe `health`).
- Interface: não muda de comportamento; só ganha empacotamento.
- Segurança: workflow com `permissions: contents: read`; sem `pull_request_target`; actions fixadas por versão exata como em `ci.yml`; segredo nunca em `if:` direto (usa variável de ambiente intermediária); a imagem do back-end roda sem root e sem credencial embutida (configuração só por variável de ambiente, como já diz o `application.yml`); a do front-end não carrega código-fonte nem `node_modules`; `.dockerignore` em cada pasta para não levar `.env`, `target`, `node_modules`, `coverage`.
- Arquivos que existem e mudam: `.github/workflows/ci.yml` (jobs `image-backend` e `image-frontend`, entram em `needs` do `ci-gate`), `README.md` (seção Publicação e imagens), `AGENTS.md` (Comandos), `backend/README.md`, `frontend/README.md`, `specs/backlog.md` (só pela fase de entrega).
- A criar: `backend/Dockerfile` (multi-stage: Maven/JDK 21 constrói o jar com `-DskipTests`, imagem JRE 21 roda), `frontend/Dockerfile` (multi-stage: Node 24 `npm ci && npm run build`, nginx serve `dist`), `frontend/nginx.conf` (fallback de SPA para `index.html`), `backend/.dockerignore`, `frontend/.dockerignore`, `.github/workflows/publish.yml`.
- Reuso: `infra/scripts/changed-areas.sh` para decidir as pastas no `publish.yml` (compara `github.event.before` e `github.sha`; sem base válida, constrói as duas); jobs `changes`, versões de actions e cache do `ci.yml`; `infra/docker-compose.yml` para provar a imagem do back-end com banco; `docker/build-push-action` e `docker/login-action` fixadas por versão exata, só se o setup gratuito do Buildx não for suficiente (senão `docker build` e `docker push` direto).
- Riscos: (1) as versões exatas das actions do Docker têm de ser verificadas na construção, como foi feito com as outras; (2) o jar precisa subir sem Docker de teste e sem o perfil `local`: confirmar a configuração mínima por variáveis (CA2); (3) o build de imagem no PR soma minutos de CI: usa cache de camadas só se passar de uns 5 min; (4) o `publish.yml` só prova o envio de verdade na #192; até lá, o ponto de enviar é provado pelo caminho "pulado" (CA5) e por um `docker push` local contra um registro descartável (D6).

## 5. Tarefas

- [x] T1: conferir R1: ler `ci.yml` e `gh api .../branches/dev/protection` e `.../branches/main/protection` (ou o ruleset); anotar o resultado na Construção. Se `ci-gate` não for exigido em `dev`, corrigir por `gh api` como na #10; `main` é do dono (só relatar) — cobre CA1 — prova: saída do `gh api` registrada.
- [x] T2: criar `backend/Dockerfile` e `backend/.dockerignore`; imagem sem root, porta 8080, configuração por variável — cobre CA2 (back-end) — prova: `docker build -t ctcore-backend backend`; subir com `docker compose -f infra/docker-compose.yml up -d --wait` e `docker run --network host` e `curl localhost:8080/actuator/health` devolve 200.
- [x] T3: criar `frontend/Dockerfile`, `frontend/nginx.conf` e `frontend/.dockerignore` — cobre CA2 (front-end) — prova: `docker build -t ctcore-frontend frontend`; `docker run -p 8081:80` e `curl` em `/` (200) e `/qualquer` (200 com o `index.html`).
- [ ] T4: acrescentar `image-backend` e `image-frontend` ao `ci.yml` (`needs: [changes, backend]` e `[changes, frontend]`, só se a etapa roda), e ao `needs` do `ci-gate` — cobre CA3 — prova: `actionlint`; run do PR com as duas imagens verdes; PR descartável só de `frontend/` mostra a imagem do back-end pulada; Dockerfile quebrado em branch descartável deixa `ci-gate` vermelho.
- [ ] T5: criar `.github/workflows/publish.yml` (gatilhos `push` em `dev` e `main` e `workflow_dispatch`; job `changes`; jobs de imagem por pasta; etapas de login e envio condicionadas a `env.PUBLISH_ENABLED`; resumo do run) — cobre CA4, CA5, CA6, CA8 — prova: `actionlint`; disparo manual do workflow em branch descartável sem chaves termina verde com o resumo "publicação pulada"; `grep` de fornecedor sem achado.
- [x] T6: provar o ponto de enviar com chaves falsas: em registro local descartável (`docker run -p 5000:5000 registry`), rodar os mesmos comandos de login e envio do `publish.yml` (script ou `docker` direto) e conferir que a imagem chega com as duas etiquetas — cobre CA4 — prova: `curl localhost:5000/v2/_catalog` lista as duas imagens; registro descartável removido.
- [x] T7: escrever a seção "Publicação" do `README.md` (segredos e variáveis, como ligar, como testar, o que a #192 ainda faz) e atualizar `AGENTS.md` (Comandos), `backend/README.md` e `frontend/README.md` com os comandos de imagem — cobre CA7 — prova: leitura dos arquivos e `markdown` sem links quebrados.
- [x] T8: citar o item nos arquivos (sem código de regra: a issue não traz RN/RF/RNF próprios, só referencia RNF-17 como fora do item) — não cobre critério: confirma que nenhum código de regra da issue ficou sem teste — prova: `grep -rn "RNF-17"` aponta só para a #192 e nada neste item o afirma como entregue.

## 6. Decisões

- D1: R1 é conferência, não construção: o `ci.yml` do item #10 já cobre `dev` e `main`, e `dev` já exige `ci-gate`; `main` tem o ruleset `protege-main`, do dono, que não é alterado. Motivo: a issue pede "conferir". Alternativa: reconfigurar `main`, descartada por ser do dono (`AGENTS.md`).
- D2: Dockerfiles multi-stage dentro de cada pasta (`backend/Dockerfile`, `frontend/Dockerfile`), contexto de build na própria pasta. Motivo: permite construir e publicar cada imagem de forma independente, como a issue pede. Imagem final do back-end: JRE 21 (Temurin) e usuário sem privilégio; do front-end: nginx servindo `dist`. Alternativa: buildpacks do Spring (`spring-boot:build-image`), descartada para usar a mesma forma nas duas pastas e não depender de download de buildpack.
- D3: as imagens são construídas (sem enviar) no `ci.yml` em todo PR que muda a pasta, para o Dockerfile não quebrar sem ninguém ver. Suposição: o custo em minutos é aceitável; se passar, usar cache de camadas.
- D4: o envio vive num workflow separado, `publish.yml`, disparado por `push` em `dev` e `main` e por `workflow_dispatch`, não dentro do `ci.yml`. Motivo: o `ci.yml` roda em `pull_request` e sem segredos (decisão D de segurança do #10); o envio precisa de `push` e de chaves. A escolha do registro (e se precisa de `packages: write`) é da #192; aqui o fluxo usa um registro genérico configurado por variáveis. Alternativa: um job `publish` no `ci.yml` com `if: github.event_name == 'push'`, descartada porque mistura gatilhos e enfraquece o `ci-gate`.
- D5 (muda o comportamento): nomes genéricos de configuração: variável `PUBLISH_REGISTRY` (endereço do registro, incluindo o caminho da imagem), segredos `PUBLISH_REGISTRY_USER` e `PUBLISH_REGISTRY_TOKEN`. O envio só ocorre se as três existirem; qualquer uma faltando conta como "sem chaves": o fluxo é pulado sem falhar e diz isso no resumo. Motivo: a issue manda pular sem erro; nenhum fornecedor é citado. Alternativa: falhar quando faltar, descartada pela issue. Se a #192 escolher um registro com outra forma de autenticação, ajusta os nomes e o login no mesmo PR.
- D6: o "ponto de enviar" é testado em duas partes: o caminho "pulado" no próprio GitHub (CA5) e o login e envio reais contra um registro local descartável (T6), pois não há registro de verdade até a #192. Suposição: o `docker push` local usa os mesmos comandos do fluxo; diferença entre registros fica para a #192 provar.
- D7: `publish.yml` publica as duas imagens com as etiquetas `<nome-do-branch>` e `<sha>`; sem etiqueta `latest`. Motivo: `dev` e `main` publicam em ambientes diferentes e o SHA permite voltar atrás. Alternativa: só `latest`, descartada por não dar para voltar a uma versão.
- Suposição: não há `docker-compose` de produção neste item; subir a imagem junto do banco e da caixa de e-mail em produção é da #192.
- Suposição: o back-end sobe a partir só de variáveis de ambiente (URL e senha do banco). Se faltar alguma variável no `application.yml`, a construção a acrescenta como configuração por variável, sem tocar nas regras.

## Construção

- T1 — arquivos: nenhum — prova: `gh api repos/DevDosAnjos/ctcore/branches/dev/protection` → `required_status_checks.contexts: ["ci-gate"]`; `ci.yml` tem `on.pull_request.branches: [dev, main]`; `main` sem proteção clássica (404) e com o ruleset `protege-main` (id 25019105, ativo), do dono, não alterado.
- T2 — arquivos: `backend/Dockerfile`, `backend/.dockerignore` — prova: `docker build -t ctcore-backend backend` → ok; compose com postgres + `docker run -e DB_HOST=host.docker.internal -p 8080:8080 -p 8081:8081` → `GET :8081/actuator/health/readiness` = 200 `{"status":"UP"}` (com banco); processo roda com uid 10001.
- T3 — arquivos: `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/.dockerignore` — prova: `docker build -t ctcore-frontend frontend` → ok; `docker run -p 8082:8080` → `/` 200, `/qualquer` 200, `/api/v1/x` 404, `/assets/nada.js` 404; uid 101 (sem root).
- T6 — arquivos: `infra/scripts/publish-image.sh` (login, build, push; é o que o `publish.yml` chama) — prova: contra `registry:2` em `localhost:5000` com chaves falsas, `curl /v2/_catalog` → `teste/ctcore-backend` e `teste/ctcore-frontend`; tags `dev`+SHA e `feature-x`+SHA; registro removido.
- T7 — arquivos: `README.md` (seção "Imagens e Publicação"), `AGENTS.md` (Comandos), `backend/README.md`, `frontend/README.md` — prova: leitura; sem links novos.
- T8 — prova: `grep -rn RNF-17` → só especificação, README (como fora do item, #193) e este arquivo; nada o afirma entregue.
- T4 (por marcar) — arquivos: `.github/workflows/ci.yml` (jobs `image-backend`, `image-frontend`; ambos no `needs` do `ci-gate`) — prova feita: `actionlint` (rhysd/actionlint via docker) → sem erro. Falta a prova que só existe no GitHub (run do PR com as imagens verdes, PR só de `frontend/`, Dockerfile quebrado): precisa de push, fora desta fase.
- T5 (por marcar) — arquivos: `.github/workflows/publish.yml` — prova feita: `actionlint` → sem erro; `grep -rniE "ghcr|amazonaws|digitalocean|azurecr|gcr.io|docker.io/[a-z]" .github` → sem achado; permissões só `contents: read`; lógica da matriz conferida à mão. Falta o disparo manual no GitHub (resumo "publicação pulada: faltam chaves") e o gitleaks no PR: precisa de push.

Regressão: `bash infra/scripts/changed-areas.test.sh` → ok. Código do back-end e do front-end não mudou, então `mvnw verify` e `npm run test:cov` não foram repetidos.
Desvios: (1) o health do back-end fica na porta de gestão 8081 (`management.server.port`, D8 do `application.yml`), não em 8080 como diz o CA2; documentado. (2) `--network host` não alcança o host no Docker Desktop do Windows: a prova usou `-p` e `DB_HOST=host.docker.internal`. (3) Para não repetir comandos, criei `infra/scripts/publish-image.sh`, chamado pelo `publish.yml`. (4) Sem actions do Docker: só `docker` direto. (5) O `Dockerfile` do back-end remove CRLF do `mvnw` (checkout no Windows).
