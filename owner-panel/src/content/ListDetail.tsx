import { useState, type ReactNode } from "react";

/** A list on the left, the selected item's form on the right. */
export function ListDetail<T>({ items, label, onAdd, addLabel, onDelete, children }: {
  items: T[];
  label: (item: T, i: number) => string;
  onAdd?: () => void;
  addLabel?: string;
  onDelete?: (i: number) => void;
  children: (item: T, i: number) => ReactNode;
}) {
  const [sel, setSel] = useState(0);
  const i = Math.min(sel, items.length - 1);
  return (
    <div className="split">
      <div className="card list">
        {items.map((it, n) => (
          <button key={n} className={n === i ? "on" : ""} onClick={() => setSel(n)} title={label(it, n)}>{label(it, n)}</button>
        ))}
        {!items.length && <p className="muted small">None yet.</p>}
        {onAdd && <button onClick={() => { onAdd(); setSel(items.length); }}>+ {addLabel ?? "Add"}</button>}
      </div>
      <div className="card">
        {i >= 0 && items[i] !== undefined ? (
          <>
            {children(items[i], i)}
            {onDelete && (
              <button className="danger" onClick={() => confirm("Delete this item?") && (onDelete(i), setSel(Math.max(0, i - 1)))}>Delete</button>
            )}
          </>
        ) : <p className="muted">Nothing selected.</p>}
      </div>
    </div>
  );
}

export const newId = (prefix: string, taken: string[]) => {
  let n = taken.length + 1;
  while (taken.includes(`${prefix}${n}`)) n++;
  return `${prefix}${n}`;
};
