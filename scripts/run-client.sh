#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${SOCIAL_TRUSTSTORE_PASSWORD:-}" && -f config/tls/password.local ]]; then
  export SOCIAL_TRUSTSTORE_PASSWORD="$(cat config/tls/password.local)"
fi
unset OPENAI_API_KEY SOCIAL_TLS_PASSWORD
exec java -jar client/target/client-1.0.0-SNAPSHOT-shaded.jar
