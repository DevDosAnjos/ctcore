#!/usr/bin/env bash
# Constrói e envia a imagem de uma pasta (#11). Usado por .github/workflows/publish.yml e testável em registro local.
# Uso: publish-image.sh <backend|frontend> <branch>
# Ambiente: PUBLISH_REGISTRY (host[:porta][/caminho]), PUBLISH_REGISTRY_USER, PUBLISH_REGISTRY_TOKEN.
set -euo pipefail
area="${1:?backend|frontend}"; branch="${2:?branch}"
: "${PUBLISH_REGISTRY:?}" "${PUBLISH_REGISTRY_USER:?}" "${PUBLISH_REGISTRY_TOKEN:?}"
tag="${branch//\//-}"                       # etiqueta não aceita "/"
sha="${GITHUB_SHA:-$(git rev-parse HEAD)}"
image="${PUBLISH_REGISTRY%/}/ctcore-$area"

printf '%s' "$PUBLISH_REGISTRY_TOKEN" | docker login "${PUBLISH_REGISTRY%%/*}" -u "$PUBLISH_REGISTRY_USER" --password-stdin
docker build -t "$image:$tag" -t "$image:$sha" "$area"
docker push "$image:$tag"
docker push "$image:$sha"
