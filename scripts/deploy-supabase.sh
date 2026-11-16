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
  # Every source file of the function (tests excluded), so a new module can never be left behind.
  FILES=()
  for f in *.ts; do case "$f" in *.test.ts) ;; *) FILES+=(-F "file=@$f;filename=$f") ;; esac; done
  code=$(curl -sS -m 180 -X POST "${AUTH[@]}" "$API/functions/deploy?slug=ai" \
    -F 'metadata={"entrypoint_path":"index.ts","name":"ai","verify_jwt":false};type=application/json' \
    "${FILES[@]}" -F "file=@content/sample.json;filename=content/sample.json" -o /tmp/parhlou-fn.out -w "%{http_code}")
  echo "function ai: HTTP $code"; head -c 400 /tmp/parhlou-fn.out; echo
  [ "$code" = "201" ] || [ "$code" = "200" ]
fi
