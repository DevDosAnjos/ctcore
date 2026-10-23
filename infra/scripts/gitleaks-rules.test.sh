#!/usr/bin/env bash
# Prova das regras próprias do .gitleaks.toml (RNF-35).
#
#   bash infra/scripts/gitleaks-rules.test.sh [caminho-do-gitleaks]   (padrão: gitleaks do PATH)
#
# Cada caso grava um arquivo num diretório temporário e roda o gitleaks com a configuração do repositório.
# Os segredos de exemplo são montados por partes ("APP_""USR-"...), para este arquivo não casar com as regras
# e o próprio gitleaks do CI não reprovar a prova. Todos os valores são inventados.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
gl="${1:-gitleaks}"
command -v "$gl" >/dev/null || { echo "gitleaks não encontrado: $gl"; exit 2; }
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

hex32="8f3a9c1e7b2d4f6a0e5c8b1d3a7f9e2c"
mp_token="APP_""USR-4829163057281946-102315-${hex32}-918273645"
mp_test="TE""ST-4829163057281946-102315-${hex32}-918273645"
mp_public="APP_""USR-6f1c2a3b-4d5e-4f60-9a7b-8c9d0e1f2a3b"
ts_secret="0x4""AAAAAAAkq9Zp3Xr7Vb2NcLm8Wd5Tf1Hy"
ts_dummy="1x0000000000000000000000000000000AA"
db_pass="S3nh4Pr0d""Real2026"
smtp_pass="Ctc0re""SmtpProd2026"

check() { # nome, caminho relativo, conteúdo, regra que deve aparecer ("" = nenhum achado)
  local dir="$tmp/$1"; mkdir -p "$(dirname "$dir/$2")"
  printf '%s\n' "$3" > "$dir/$2"
  local out
  out="$("$gl" dir --no-banner --config "$root/.gitleaks.toml" --report-format json --report-path - "$dir" 2>/dev/null || true)"
  local got
  got="$(printf '%s' "$out" | python3 -c 'import json,sys
d=json.load(sys.stdin)
print(",".join(sorted({x["RuleID"] for x in d})))' 2>/dev/null || echo "ERRO")"
  # Esperado vazio: nenhum achado. Esperado com regra: ela está entre os achados (outra regra também pode casar).
  if [ -z "$4" ]; then [ -z "$got" ]; else case ",$got," in *",$4,"*) true ;; *) false ;; esac; fi \
    || { echo "FALHOU $1: achou '${got}', esperado '${4:-nenhum}'"; exit 1; }
  echo "ok  $1"
}

# Mercado Pago (Sprint 23)
check "mp token em .env"           ".env"                                   "MP_ACCESS_TOKEN=$mp_token"                         "mercadopago-access-token"
check "mp token em properties"     "backend/src/main/resources/app.properties" "mercadopago.access-token=$mp_token"          "mercadopago-access-token"
check "mp token de teste em java"  "backend/src/main/java/Mp.java"          "String t = \"$mp_test\";"                         "mercadopago-access-token"
check "mp chave pública passa"     "frontend/src/mp.ts"                     "const publicKey = \"$mp_public\";"                ""

# Turnstile (Sprint 22)
check "turnstile secreta"          ".env.production"                        "TURNSTILE_SECRET_KEY=$ts_secret"                   "turnstile-secret-key"
check "turnstile do site passa"    "frontend/.env.example"                  "VITE_TURNSTILE_SITE_KEY=$ts_secret"                ""
check "turnstile fictícia passa"   ".env.example"                           "TURNSTILE_SECRET_KEY=$ts_dummy"                    ""

# URL de conexão com senha (Sprint 22)
check "url do banco com senha"     "infra/deploy.yml"                       "url: postgres://ctcore:${db_pass}@db.prod.exemplo.com:5432/ctcore" "connection-url-password"
check "jdbc com senha"             "backend/src/main/java/Db.java"          "String u = \"jdbc:postgresql://app:${db_pass}@10.0.0.5/ctcore\";" "connection-url-password"
check "url com variável passa"     "infra/deploy2.yml"                      'url: postgres://ctcore:${DB_PASSWORD}@db:5432/ctcore' ""
check "url de dev passa"           "README.txt"                             "postgres://ctcore:ctcore_dev@localhost:5432/ctcore" ""

# Senha literal em configuração (Sprints 22 e 23)
check "senha smtp em properties"   "backend/src/main/resources/application-prod.properties" "spring.mail.password=$smtp_pass" "config-password-literal"
check "senha smtp em yml"          "backend/src/main/resources/application-prod.yml" "    password: \"$smtp_pass\""        "config-password-literal"
check "segredo em .env"            ".env.staging"                           "export WEBHOOK_SECRET=$smtp_pass"                  "config-password-literal"
check "senha por variável passa"   "backend/src/main/resources/application.yml" '    password: ${DB_RUNTIME_PASSWORD:ctcore_app_dev}' ""
check "senha de dev passa"         "infra/.env.example"                     "POSTGRES_PASSWORD=ctcore_dev"                      ""
check "segredo de dev passa"       "infra/storage.yml"                      "secret-key: dev-secret"                            ""
check "mensagem com senha passa"   "backend/src/main/resources/messages.properties" "auth.password.current-wrong=A senha atual está incorreta." ""
check "configuração de teste passa" "backend/src/test/resources/application.properties" "spring.mail.password=$smtp_pass" ""

# As regras padrão continuam ligadas
pk_head="-----BEGIN OPENSSH ""PRIVATE KEY-----"; pk_tail="-----END OPENSSH ""PRIVATE KEY-----"
check "regra padrão segue ativa"   "id_test"                                "$(printf '%s\n%s\n%s' "$pk_head" 'b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW' "$pk_tail")" "private-key"

echo "Todas as regras próprias do gitleaks se comportam como esperado."
