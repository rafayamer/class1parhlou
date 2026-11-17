#!/usr/bin/env bash
# Saves a content bundle as the subject's DRAFT on Supabase. Students see nothing until the owner presses Publish
# in the owner panel. Usage: SUPABASE_ACCESS_TOKEN=sbp_... ./scripts/save-draft.sh <project-ref> <bundle.json>
# Large bundles are sent in pieces (the rest of the bundle first, then one unit at a time appended to it), because the
# database API refuses very large requests.
set -euo pipefail
REF="${1:?project ref}"
BUNDLE="${2:?bundle.json}"
WORK="$(mktemp -d)"
trap 'rm -f "$WORK"/q-*.json; rmdir "$WORK"' EXIT
python3 - "$BUNDLE" "$WORK" <<'PY'
import json, sys
bundle = json.load(open(sys.argv[1]))
out = sys.argv[2]
assert bundle.get("format") == 1 and bundle.get("subjectId"), "not a content bundle"
tag = "$parhlou_draft$"
subject = bundle["subjectId"]
chapters = bundle["subject"]["chapters"]
# Large top-level lists (notes, quiz, papers, written) are sent in pieces too.
BIG = [k for k, v in bundle.items() if isinstance(v, list) and len(json.dumps(v, ensure_ascii=False)) > 200_000]
head = dict(bundle, subject=dict(bundle["subject"], chapters=[]), **{k: [] for k in BIG})
def lit(v):
    t = json.dumps(v, ensure_ascii=False)
    assert tag not in t
    return f"{tag}{t}{tag}::jsonb"
queries = [
    f"insert into public.content_drafts (subject_id, bundle, updated_at) values ('{subject}', {lit(head)}, now()) "
    f"on conflict (subject_id) do update set bundle = excluded.bundle, updated_at = now()"
]
for c in chapters:
    queries.append(
        f"update public.content_drafts set bundle = jsonb_set(bundle, '{{subject,chapters}}', (bundle->'subject'->'chapters') || jsonb_build_array({lit(c)})), "
        f"updated_at = now() where subject_id = '{subject}'")
for k in BIG:
    piece, size = [], 0
    def flush():
        queries.append(
            f"update public.content_drafts set bundle = jsonb_set(bundle, '{{{k}}}', (bundle->'{k}') || {lit(piece)}), "
            f"updated_at = now() where subject_id = '{subject}'")
    for item in bundle[k]:
        n = len(json.dumps(item, ensure_ascii=False))
        if piece and size + n > 400_000:
            flush(); piece, size = [], 0
        piece.append(item); size += n
    if piece: flush()
queries.append(f"select subject_id, updated_at, jsonb_array_length(bundle->'subject'->'chapters') as units, length(bundle::text) as size "
               f"from public.content_drafts where subject_id = '{subject}'")
for i, q in enumerate(queries):
    open(f"{out}/q-{i:03d}.json", "w").write(json.dumps({"query": q}))
PY
for f in "$WORK"/q-*.json; do
  reply=$(curl -sS -m 300 -X POST -H "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN:?set SUPABASE_ACCESS_TOKEN}" -H "Content-Type: application/json" \
    "https://api.supabase.com/v1/projects/$REF/database/query" --data-binary @"$f")
  case "$reply" in *message*) echo "failed at $(basename "$f"): $reply" >&2; exit 1 ;; esac
done
echo "$reply"
