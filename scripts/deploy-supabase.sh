#!/usr/bin/env bash
# Installs the Parhlou database and AI function into a Supabase project through the Management API.
# Needs a Supabase personal access token (sbp_...) in SUPABASE_ACCESS_TOKEN. Never commit the token.
# Usage: SUPABASE_ACCESS_TOKEN=sbp_... ./scripts/deploy-supabase.sh <project-ref> [db|function|all]
set -euo pipefail
REF="${1:?project ref}"; WHAT="${2:-all}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API="https://api.supabase.com/v1/projects/$REF"
AUTH=(-H "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN:?set SUPABASE_ACCESS_TOKEN}")

sql() {  # runs one SQL file as a single statement batch
  python3 -c 'import json,sys; print(json.dumps({"query": open(sys.argv[1]).read()}))' "$1" |
    curl -sS -m 120 -X POST "${AUTH[@]}" -H "Content-Type: application/json" "$API/database/query" --data-binary @- -o /tmp/parhlou-sql.out -w "%{http_code}"
}

if [ "$WHAT" = "db" ] || [ "$WHAT" = "all" ]; then
  for f in "$ROOT"/supabase/migrations/*.sql "$ROOT/supabase/seed.sql"; do
    code=$(sql "$f"); echo "$(basename "$f"): HTTP $code"
    [ "$code" = "201" ] || [ "$code" = "200" ] || { head -c 600 /tmp/parhlou-sql.out; echo; exit 1; }
  done
fi

if [ "$WHAT" = "function" ] || [ "$WHAT" = "all" ]; then
  cd "$ROOT/supabase/functions/ai"
  code=$(curl -sS -m 180 -X POST "${AUTH[@]}" "$API/functions/deploy?slug=ai" \
    -F 'metadata={"entrypoint_path":"index.ts","name":"ai","verify_jwt":false};type=application/json' \
    -F "file=@index.ts;filename=index.ts" -F "file=@handler.ts;filename=handler.ts" -F "file=@core.ts;filename=core.ts" -F "file=@youtube.ts;filename=youtube.ts" \
    -F "file=@content/sample.json;filename=content/sample.json" -o /tmp/parhlou-fn.out -w "%{http_code}")
  echo "function ai: HTTP $code"; head -c 400 /tmp/parhlou-fn.out; echo
  [ "$code" = "201" ] || [ "$code" = "200" ]
fi
