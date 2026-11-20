#!/usr/bin/env bash
# Publishes a content bundle to Supabase. Every phone picks it up the next time the app is opened.
# Usage: SUPABASE_ACCESS_TOKEN=sbp_... ./scripts/publish-content.sh <project-ref> [bundle.json]
# Without a bundle file, the content packed in the app is exported first.
set -euo pipefail
REF="${1:?project ref}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUNDLE="${2:-$ROOT/supabase/functions/ai/content/sample.json}"
if [ -z "${2:-}" ]; then
  (cd "$ROOT/android" && ./gradlew --no-daemon -q :app:testPrototypeDebugUnitTest --tests '*ContentExportTest' -PcontentExport="$BUNDLE" ${ROBOLECTRIC_DIR:+-ProbolectricDir=$ROBOLECTRIC_DIR})
fi
python3 - "$BUNDLE" <<'PY' | curl -sS -m 120 -X POST -H "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN:?set SUPABASE_ACCESS_TOKEN}" -H "Content-Type: application/json" "https://api.supabase.com/v1/projects/$REF/database/query" --data-binary @-
import json, sys
bundle = json.load(open(sys.argv[1]))
assert bundle.get("format") == 1 and bundle.get("subjectId"), "not a content bundle"
tag = "$parhlou_bundle$"
text = json.dumps(bundle, ensure_ascii=False)
assert tag not in text
print(json.dumps({"query": f"select public.publish_content('{bundle['subjectId']}', {tag}{text}{tag}::jsonb) as published_version"}))
PY
echo
