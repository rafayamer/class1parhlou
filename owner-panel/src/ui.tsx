import type { ReactNode } from "react";

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="field"><span>{label}</span>{children}</label>;
}

export function Text({ label, value, onChange, placeholder }: { label: string; value: string | undefined | null; onChange: (v: string) => void; placeholder?: string }) {
  return <Field label={label}><input value={value ?? ""} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} /></Field>;
}

export function Area({ label, value, onChange }: { label: string; value: string | undefined | null; onChange: (v: string) => void }) {
  return <Field label={label}><textarea value={value ?? ""} onChange={(e) => onChange(e.target.value)} /></Field>;
}

export function Num({ label, value, onChange, step = 1 }: { label: string; value: number | undefined | null; onChange: (v: number) => void; step?: number }) {
  return <Field label={label}><input type="number" step={step} value={value ?? 0} onChange={(e) => onChange(Number(e.target.value))} /></Field>;
}

/** One item per line. */
export function Lines({ label, value, onChange }: { label: string; value: string[] | undefined; onChange: (v: string[]) => void }) {
  return (
    <Field label={`${label} (one per line)`}>
      <textarea value={(value ?? []).join("\n")} onChange={(e) => onChange(e.target.value.split("\n"))} />
    </Field>
  );
}

/** Accepted answer patterns: one group per line, alternatives in a group separated by " | ". */
export function Patterns({ label, value, onChange }: { label: string; value: string[][] | undefined; onChange: (v: string[][]) => void }) {
  return (
    <Field label={`${label} — each line is one way to earn the point; on a line, patterns that must all appear are separated by " | "; start a pattern with ! to forbid it`}>
      <textarea value={(value ?? []).map((g) => g.join(" | ")).join("\n")}
        onChange={(e) => onChange(e.target.value.split("\n").map((l) => l.split("|").map((p) => p.trim())))} />
    </Field>
  );
}

export function Check({ label, value, onChange }: { label: string; value: boolean | undefined; onChange: (v: boolean) => void }) {
  return <label className="check field"><input type="checkbox" checked={!!value} onChange={(e) => onChange(e.target.checked)} />{label}</label>;
}

export function Stat({ n, label }: { n: ReactNode; label: string }) {
  return <div className="card stat"><div className="n">{n}</div><div className="l">{label}</div></div>;
}

export const when = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" }) : "—");
