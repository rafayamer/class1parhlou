// Reads the 2027 smart-syllabus exclusions (content/class9-maths/syllabus-2027.json) and decides whether a textbook
// reference is excluded from the exam. References look like "Ex 6.4 Q2(vii)", "Review Ex 2 Q7", "Example 12".
// Exclusion entries: "Ex 1.3" (whole exercise), "Ex 6.3 Q10-12", "Ex 6.4 Q2(vii)", "Review Ex 1 Q8-10", "Example 10-14", "*".

function parseRef(ref) {
  const s = ref.trim();
  let m = /^example\s*(\d+)/i.exec(s);
  if (m) return { kind: "example", n: Number(m[1]) };
  m = /^(review\s*ex(?:ercise)?\.?\s*(\d+)|ex(?:ercise)?\.?\s*(\d+(?:\.\d+)?))\s*(?:q\s*(\d+)(?:\s*-\s*(\d+))?)?\s*(?:\(([ivxl]+)\))?/i.exec(s);
  if (!m) return null;
  return { kind: "ex", ex: m[2] ? `r${m[2]}` : m[3], q: m[4] ? Number(m[4]) : null, qTo: m[5] ? Number(m[5]) : null, part: m[6]?.toLowerCase() ?? null };
}

function entryCovers(entry, ref) {
  const e = entry.trim();
  if (e === "*") return true;
  const range = /^example\s*(\d+)(?:\s*-\s*(\d+))?$/i.exec(e);
  if (range) return ref.kind === "example" && ref.n >= Number(range[1]) && ref.n <= Number(range[2] ?? range[1]);
  const x = parseRef(e);
  if (!x || x.kind !== "ex" || ref.kind !== "ex" || x.ex !== ref.ex) return false;
  if (x.q == null) return true;
  if (ref.q == null) return false;
  if (ref.q < x.q || ref.q > (x.qTo ?? x.q)) return false;
  return x.part == null || x.part === ref.part;
}

/** True when the reference is left out of the given exam by the smart syllabus. */
export function isExcluded(syllabus, unit, source) {
  if (syllabus.excludedUnits.includes(unit)) return true;
  const ref = parseRef(source);
  if (!ref) return false;
  return (syllabus.excluded[String(unit)] ?? []).some((e) => entryCovers(e, ref));
}
