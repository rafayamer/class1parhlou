#!/usr/bin/env bash
# Run the database tests against a throwaway local PostgreSQL 16 with pgTAP,
# using a minimal Supabase auth stub. Use `supabase test db` instead when Docker is available.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PGBIN="${PGBIN:-/usr/lib/postgresql/16/bin}"
PORT="${PGPORT_TEST:-54329}"
WORK="$(mktemp -d)"
trap '"$PGBIN/pg_ctl" -D "$WORK/data" -m immediate stop >/dev/null 2>&1 || true; rm -rf "$WORK"' EXIT

RUN=()
if [ "$(id -u)" = "0" ]; then
  chown -R postgres "$WORK"
  RUN=(runuser -u postgres --)
fi

"${RUN[@]}" "$PGBIN/initdb" -D "$WORK/data" -U postgres -A trust >/dev/null
"${RUN[@]}" "$PGBIN/pg_ctl" -D "$WORK/data" -o "-p $PORT -k $WORK -c listen_addresses=''" -l "$WORK/log" start >/dev/null

PSQL=("${RUN[@]}" psql -h "$WORK" -p "$PORT" -U postgres -v ON_ERROR_STOP=1 -q)
"${PSQL[@]}" -c "create database parhlou_test" >/dev/null
DB=("${PSQL[@]}" -d parhlou_test)

"${DB[@]}" -f "$ROOT/supabase/tests/local/auth_stub.sql" >/dev/null
for f in "$ROOT"/supabase/migrations/*.sql; do
  echo "migrate: $(basename "$f")"
  "${DB[@]}" -f "$f" >/dev/null
done
echo "seed: seed.sql"
"${DB[@]}" -f "$ROOT/supabase/seed.sql" >/dev/null

status=0
for t in "$ROOT"/supabase/tests/database/*.test.sql; do
  echo "test: $(basename "$t")"
  out="$("${RUN[@]}" psql -h "$WORK" -p "$PORT" -U postgres -d parhlou_test -X -A -t -f "$t" 2>&1)" || status=1
  echo "$out"
  if grep -qE '^not ok|Looks like you (failed|planned)|ERROR' <<<"$out"; then status=1; fi
done
exit $status
