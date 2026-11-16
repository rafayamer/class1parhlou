// Figures in Ask Ustaad answers: the model describes a figure as data, the app draws it exactly.
// This checks a figure before it leaves the server. A figure that doesn't make sense is dropped, never sent half-broken.

export type FigureKind = "graph" | "number_line" | "coordinate" | "geometry" | "venn" | "bar" | "pie" | "table";
export interface Figure {
  kind: FigureKind; title: string; description: string;
  functions: string[]; shade: { expression: string; side: "above" | "below"; strict: boolean } | null;
  xMin: number; xMax: number; yMin: number; yMax: number;
  points: { x: number; y: number; label: string }[];
  segments: { from: string; to: string; label: string }[];
  polygons: string[][];
  angles: { at: string; label: string; right: boolean }[];
  circles: { center: string; radius: number; label: string }[];
  intervals: { from: number; to: number; fromClosed: boolean; toClosed: boolean; label: string }[];
  sets: { name: string; only: string[] }[]; both: string[]; outside: string[];
  categories: string[]; values: number[];
  headers: string[]; rows: string[][];
}

const KINDS: FigureKind[] = ["graph", "number_line", "coordinate", "geometry", "venn", "bar", "pie", "table"];
/** Expressions may only use x, numbers, + − * / ^ ( ) and these functions; anything else is refused. */
const FUNCS = ["sqrt", "abs", "sin", "cos", "tan", "log", "ln", "exp"];

export function safeExpression(e: unknown): string | null {
  if (typeof e !== "string") return null;
  const t = e.replace(/\s+/g, "").replace(/×/g, "*").replace(/÷/g, "/").replace(/−/g, "-").replace(/√/g, "sqrt").replace(/π/g, "pi");
  if (!t || t.length > 80) return null;
  const words = t.match(/[a-z]+/gi) ?? [];
  if (words.some((w) => w !== "x" && w !== "pi" && !FUNCS.includes(w.toLowerCase()))) return null;
  if (!/^[0-9x.+\-*/^()a-z]+$/i.test(t)) return null;
  let depth = 0;
  for (const ch of t) { if (ch === "(") depth++; if (ch === ")" && --depth < 0) return null; }
  return depth === 0 ? t : null;
}

const fin = (v: unknown, lo = -1e9, hi = 1e9) => typeof v === "number" && Number.isFinite(v) && v >= lo && v <= hi;
const str = (v: unknown, max = 60) => (typeof v === "string" ? v.trim().slice(0, max) : "");
const strs = (v: unknown, n: number, max = 40) => (Array.isArray(v) ? v.filter((x) => typeof x === "string").slice(0, n).map((x: string) => x.trim().slice(0, max)) : []);

export function toFigure(raw: any): Figure | null {
  if (!raw || !KINDS.includes(raw.kind)) return null;
  const k = raw.kind as FigureKind;
  const range = (a: unknown, b: unknown, dl: number, dh: number): [number, number] =>
    fin(a, -1e4, 1e4) && fin(b, -1e4, 1e4) && (a as number) < (b as number) ? [a as number, b as number] : [dl, dh];
  const [xMin, xMax] = range(raw.x_min, raw.x_max, -10, 10);
  const [yMin, yMax] = range(raw.y_min, raw.y_max, -10, 10);
  const points = (Array.isArray(raw.points) ? raw.points : []).filter((p: any) => p && fin(p.x, -1e4, 1e4) && fin(p.y, -1e4, 1e4)).slice(0, 20)
    .map((p: any) => ({ x: p.x, y: p.y, label: str(p.label, 12) }));
  const labels = new Set(points.map((p: any) => p.label).filter(Boolean));
  const f: Figure = {
    kind: k, title: str(raw.title, 80), description: str(raw.description, 240),
    functions: (Array.isArray(raw.functions) ? raw.functions : []).map(safeExpression).filter((x: string | null): x is string => !!x).slice(0, 3),
    shade: null, xMin, xMax, yMin, yMax, points,
    segments: (Array.isArray(raw.segments) ? raw.segments : []).filter((s: any) => s && labels.has(str(s.from, 12)) && labels.has(str(s.to, 12))).slice(0, 20)
      .map((s: any) => ({ from: str(s.from, 12), to: str(s.to, 12), label: str(s.label, 16) })),
    polygons: (Array.isArray(raw.polygons) ? raw.polygons : []).map((p: unknown) => strs(p, 8, 12)).filter((p: string[]) => p.length >= 3 && p.every((l) => labels.has(l))).slice(0, 4),
    angles: (Array.isArray(raw.angles) ? raw.angles : []).filter((a: any) => a && labels.has(str(a.at, 12))).slice(0, 8)
      .map((a: any) => ({ at: str(a.at, 12), label: str(a.label, 16), right: a.right === true })),
    circles: (Array.isArray(raw.circles) ? raw.circles : []).filter((c: any) => c && labels.has(str(c.center, 12)) && fin(c.radius, 0.0001, 1e4)).slice(0, 3)
      .map((c: any) => ({ center: str(c.center, 12), radius: c.radius, label: str(c.label, 16) })),
    intervals: (Array.isArray(raw.intervals) ? raw.intervals : []).filter((i: any) => i && fin(i.from) && fin(i.to) && i.from <= i.to).slice(0, 4)
      .map((i: any) => ({ from: i.from, to: i.to, fromClosed: i.from_closed === true, toClosed: i.to_closed === true, label: str(i.label, 30) })),
    sets: (Array.isArray(raw.sets) ? raw.sets : []).filter((s: any) => s && str(s.name, 8)).slice(0, 3).map((s: any) => ({ name: str(s.name, 8), only: strs(s.only, 12, 10) })),
    both: strs(raw.both, 12, 10), outside: strs(raw.outside, 12, 10),
    categories: strs(raw.categories, 12, 20), values: (Array.isArray(raw.values) ? raw.values : []).filter((v: unknown) => fin(v, -1e6, 1e6)).slice(0, 12),
    headers: strs(raw.headers, 6, 24), rows: (Array.isArray(raw.rows) ? raw.rows : []).slice(0, 12).map((r: unknown) => strs(r, 6, 24)),
  };
  const sh = raw.shade;
  if (sh && (sh.side === "above" || sh.side === "below")) {
    const e = safeExpression(sh.expression);
    if (e) f.shade = { expression: e, side: sh.side, strict: sh.strict === true };
  }
  // Each kind must carry what it needs to be drawn.
  const ok = {
    graph: f.functions.length > 0 || f.shade !== null,
    number_line: f.intervals.length > 0 || f.points.length > 0,
    coordinate: f.points.length > 0,
    geometry: f.points.length >= 2,
    venn: f.sets.length >= 1,
    bar: f.categories.length >= 1 && f.categories.length === f.values.length,
    pie: f.categories.length >= 1 && f.categories.length === f.values.length && f.values.every((v) => v >= 0) && f.values.some((v) => v > 0),
    table: f.headers.length >= 1 && f.rows.length >= 1,
  }[k];
  return ok ? f : null;
}
