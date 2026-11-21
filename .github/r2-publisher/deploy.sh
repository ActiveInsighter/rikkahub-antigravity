#!/usr/bin/env bash
set -euo pipefail
: "${R2_UPDATE_ACCOUNT_ID:?Set the Cloudflare account ID}"
: "${R2_UPDATE_BUCKET:?Set the update bucket name}"
: "${R2_UPDATE_WORKERS_SUBDOMAIN:?Set the workers.dev subdomain label for the account}"
: "${CLOUDFLARE_API_TOKEN:?Set a token that can deploy Workers in the account}"
: "${R2_UPDATE_PUBLISH_TOKEN:?Set the token shared with the Android workflow}"
export R2_UPDATE_BUCKET R2_UPDATE_PUBLISH_TOKEN
umask 077

directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
account="https://api.cloudflare.com/client/v4/accounts/$R2_UPDATE_ACCOUNT_ID/workers"
script="$account/scripts/rikkahub-apk-update-publisher"
metadata=$(mktemp)
trap 'rm -- "$metadata"' EXIT

jq -n --arg today "$(date -u +%F)" '{
  main_module: "index.mjs",
  compatibility_date: $today,
  observability: {enabled: true, traces: {enabled: true}},
  bindings: [
    {type: "r2_bucket", name: "UPDATES", bucket_name: env.R2_UPDATE_BUCKET},
    {type: "secret_text", name: "PUBLISH_TOKEN", text: env.R2_UPDATE_PUBLISH_TOKEN}
  ]
}' > "$metadata"

cloudflare_api() {
  curl --silent --show-error --max-time 60 \
    --header @<(printf 'Authorization: Bearer %s\n' "$CLOUDFLARE_API_TOKEN") "$@"
}

cloudflare_api --request PUT \
  --form "metadata=@$metadata;type=application/json" \
  --form "index.mjs=@$directory/index.mjs;type=application/javascript+module" "$script" |
  jq -e 'if .success then true else error(.errors | tostring) end' > /dev/null
cloudflare_api --header 'Content-Type: application/json' \
  --data '{"enabled":true,"previews_enabled":false}' "$script/subdomain" |
  jq -e 'if .success then true else error(.errors | tostring) end' > /dev/null

printf 'https://rikkahub-apk-update-publisher.%s.workers.dev\n' "$R2_UPDATE_WORKERS_SUBDOMAIN"
