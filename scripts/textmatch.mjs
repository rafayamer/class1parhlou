// Same matching rules as the app's TextMatch (android/.../ai/TextMatch.kt), so the content builder can prove that
// every model answer earns its marks before the app ever sees it. Keep the two in step.
const lettersOnly = /^[a-z ]+$/;
const SUPERSCRIPTS = { "⁻": "-", "⁰": "0", "¹": "1", "²": "2", "³": "3", "⁴": "4", "⁵": "5", "⁶": "6", "⁷": "7", "⁸": "8", "⁹": "9" };

export function normalise(raw) {
  let s = raw.toLowerCase().replaceAll("\n", " ; ");
  s = s.replace(/[−–—]/g, "-").replace(/[×·⋅∙]/g, "*").replace(/÷/g, "/");
  s = s.replace(/…/g, " ").replace(/\.{2,}/g, " ");
  s = s.replace(/square root of/g, "√").replace(/square root/g, "√").replace(/sqrt/g, "√");
  s = s.replace(/\broot\b/g, "√");
  s = s.replace(/\bpi\b/g, "π").replace(/\btheta\b/g, "θ");
  // Powers: "x²", "x^2", "x squared" and "10⁻³", "10^-3", "10^(-3)" all become caret form.
  s = s.replace(/\s*squared/g, "^2").replace(/\s*cubed/g, "^3");
  s = s.replace(/[⁻⁰¹²³⁴⁵⁶⁷⁸⁹]+/g, (m) => "^" + [...m].map((c) => SUPERSCRIPTS[c] ?? c).join(""));
  s = s.replace(/\^\s*\(\s*(-?\d+)\s*\)/g, "^$1").replace(/\^\s+/g, "^");
  s = s.replace(/√\s*\(\s*(\d+(?:\.\d+)?)\s*\)/g, "√$1").replace(/√\s+/g, "√");
  s = s.replace(/(\d)\s*\*\s*√/g, "$1√");
  return s.replace(/\s+/g, " ").trim();
}

const compact = (s) => s.replaceAll(" ", "");
const escape = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

export function matches(answer, pattern) {
  if (pattern.startsWith("!")) return !matches(answer, pattern.slice(1));
  const text = normalise(answer);
  if (pattern.startsWith("p:")) return new RegExp("\\b" + escape(normalise(pattern.slice(2)))).test(text);
  const openEnd = pattern.endsWith("~");
  const body = normalise(openEnd ? pattern.slice(0, -1) : pattern);
  if (!body) return false;
  if (lettersOnly.test(body)) return new RegExp("\\b" + escape(body) + "\\b").test(text);
  return compactContains(compact(text), compact(body), openEnd);
}

function compactContains(hay, needle, openEnd) {
  const isDigit = (c) => c !== undefined && c >= "0" && c <= "9";
  let from = 0;
  for (;;) {
    const i = hay.indexOf(needle, from);
    if (i < 0) return false;
    const before = hay[i - 1], after = hay[i + needle.length];
    const startOk = !isDigit(needle[0]) || before === undefined || !(isDigit(before) || before === ".");
    const endOk = openEnd || !isDigit(needle[needle.length - 1]) || after === undefined || !isDigit(after);
    if (startOk && endOk) return true;
    from = i + 1;
  }
}

export const matchesAll = (answer, group) => group.length > 0 && group.every((p) => matches(answer, p));
export const matchesAny = (answer, groups) => groups.some((g) => matchesAll(answer, g));

export const SYNTAX = `Accepted-answer patterns ("accept"): a list of alternative groups; a group matches when ALL its patterns appear in the student's typed answer.
- letters and spaces only, e.g. "irrational" → whole-word match
- "p:prefix" → start-of-word match ("p:terminat" matches "terminating")
- anything with digits or symbols, e.g. "9/20" or "x=3" → matched ignoring spaces and not inside a longer number
- trailing "~" allows more digits after ("2.7~" matches 2.777)
- leading "!" means the rest must NOT appear
Students type ×, *, ÷, /, √, "root", "sqrt", "^2", "²" interchangeably; all of these are normalised. Use short key expressions (the result of the step), never whole sentences.`;
