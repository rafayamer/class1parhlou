#!/usr/bin/env bash
# Saves a content bundle as the subject's DRAFT on Supabase. Students see nothing until the owner presses Publish
# in the owner panel. Usage: SUPABASE_ACCESS_TOKEN=sbp_... ./scripts/save-draft.sh <project-ref> <bundle.json>
set -euo pipefail
REF="${1:?project ref}"
BUNDLE="${2:?bundle.json}"
python3 - "$BUNDLE" <<'PY' | curl -sS -m 300 -X POST -H "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN:?set SUPABASE_ACCESS_TOKEN}" -H "Content-Type: application/json" "https://api.supabase.com/v1/projects/$REF/database/query" --data-binary @-
import json, sys
bundle = json.load(open(sys.argv[1]))
assert bundle.get("format") == 1 and bundle.get("subjectId"), "not a content bundle"
tag = "$parhlou_draft$"
text = json.dumps(bundle, ensure_ascii=False)
assert tag not in text
subject = bundle["subjectId"]
print(json.dumps({"query":
    f"insert into public.content_drafts (subject_id, bundle, updated_at) values ('{subject}', {tag}{text}{tag}::jsonb, now()) "
    f"on conflict (subject_id) do update set bundle = excluded.bundle, updated_at = now() returning subject_id, updated_at, length(bundle::text) as size"}))
PY
echo
