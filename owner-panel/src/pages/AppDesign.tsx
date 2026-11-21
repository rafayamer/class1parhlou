import { useEffect, useMemo, useState, type CSSProperties, type ReactNode } from "react";
import { db, explain } from "../supabase";
import { when } from "../ui";
import {
  ACCENTS, DEFAULT_ACCENT, DEFAULT_MAINTENANCE, DEFAULT_UPDATE, FEATURES, LIMITS, TUNING,
  bannerLive, changedSections, contrastWithWhite, defaults, fromSaved, newBannerId, problems, risks, tint, toSaved,
  type Audience, type Customization, type FeatureKey, type Section, type Tone,
} from "../customization";

const SECTIONS: [Section | "history", string, string][] = [
  ["brand", "🎨", "Brand"],
  ["announcement", "📣", "Announcement"],
  ["features", "🧩", "Features"],
  ["game", "🎮", "Game & XP"],
  ["texts", "✍️", "Texts"],
  ["health", "🛟", "App health"],
  ["history", "🕘", "History"],
];
const SECTION_NAME = Object.fromEntries(SECTIONS.map(([id, , name]) => [id, name])) as Record<Section | "history", string>;
const TONES: [Tone, string][] = [["info", "Info"], ["success", "Good news"], ["warning", "Heads up"], ["danger", "Urgent"]];
const EMOJIS = ["📣", "🎯", "📚", "🎉", "⏰", "🏆", "⭐", "📝", "🔥", "🎁"];
const today = () => new Date().toLocaleDateString("en-CA");
const MIGRATION = "Run the database update 20261121000001_app_customization.sql in the Supabase SQL editor first (see docs/owner-setup.md).";

type Screen = "home" | "plus" | "cover";
interface Version { saved_at: string; value: unknown }

/**
 * App design: everything about the app the owner can change without a new version (brand colour, a Home announcement,
 * feature switches with gradual rollout, XP numbers, texts, maintenance mode, a minimum version), with a live phone
 * preview, a check before anything risky goes out, and earlier versions to restore.
 */
export function AppDesign() {
  const [saved, setSaved] = useState<Customization | null>(null);
  const [c, setC] = useState<Customization | null>(null);
  const [section, setSection] = useState<Section | "history">("brand");
  const [screen, setScreen] = useState<Screen>("home");
  const [dark, setDark] = useState(() => matchMedia("(prefers-color-scheme: dark)").matches);
  const [status, setStatus] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = () => db.from("app_config").select("value").eq("key", "app_customization").maybeSingle().then(({ data, error }) => {
    if (error) return setStatus(explain(error));
    const next = fromSaved(data?.value ?? {});
    setSaved(next); setC(next);
    if (!data) setStatus(MIGRATION);
  });
  useEffect(() => { load(); }, []);

  // The preview follows the section being edited.
  useEffect(() => { if (section === "health") setScreen("cover"); else if (section !== "history") setScreen((s) => (s === "cover" ? "home" : s)); }, [section]);

  const changed = useMemo(() => (c && saved ? changedSections(saved, c) : []), [c, saved]);
  const blocking = useMemo(() => (c ? problems(c) : []), [c]);
  const warnings = useMemo(() => (c && saved ? risks(saved, c) : []), [c, saved]);

  if (!c || !saved) return <p className="muted">{status || "Loading…"}</p>;
  const set = (patch: Partial<Customization>) => { setC({ ...c, ...patch }); setConfirming(false); };

  const publish = async () => {
    if (warnings.length && !confirming) return setConfirming(true);
    setSaving(true); setStatus("Publishing…");
    const { error } = await db.rpc("owner_set_config", { p_key: "app_customization", p_value: toSaved(c) });
    setSaving(false); setConfirming(false);
    if (error) return setStatus(error.message.includes("SETTING_NOT_ALLOWED") ? MIGRATION : explain(error));
    setStatus("Published. Students get it the next time they open the app.");
    load();
  };

  return (
    <>
      <h1>App design</h1>
      <p className="muted">Change how the app looks and works for every student, without a new version. Phones pick up what you publish the next time they open the app; if a phone is offline it keeps the last version it saw.</p>
      <div className="tabs" role="tablist" aria-label="App design sections">
        {SECTIONS.map(([id, icon, name]) => {
          const bad = blocking.some((p) => p.section === id);
          return (
            <button key={id} role="tab" aria-selected={section === id} className={section === id ? "on" : ""} onClick={() => setSection(id)}>
              {icon} {name}{bad ? <span className="dot bad" title="Needs fixing" /> : changed.includes(id as Section) ? <span className="dot" title="Changed" /> : null}
            </button>
          );
        })}
      </div>
      <div className="design">
        <div className="design-main">
          {section === "brand" && <Brand c={c} set={set} />}
          {section === "announcement" && <Announcement c={c} set={set} />}
          {section === "features" && <Features c={c} set={set} />}
          {section === "game" && <Game c={c} set={set} />}
          {section === "texts" && <Texts c={c} set={set} />}
          {section === "health" && <Health c={c} set={set} />}
          {section === "history" && <History current={saved} onLoad={(v) => { set(v); setSection("brand"); setStatus("An earlier version is loaded. Check it, then publish to restore it."); }} />}
        </div>
        <aside className="design-preview" aria-label="Phone preview">
          <div className="row spread" style={{ flexWrap: "nowrap" }}>
            <div className="seg" role="group" aria-label="Preview screen">
              {(["home", "plus", "cover"] as Screen[]).map((s) => (
                <button key={s} aria-pressed={screen === s} className={screen === s ? "on" : ""} onClick={() => setScreen(s)}>
                  {s === "home" ? "Home" : s === "plus" ? "Premium" : c.maintenance ? "Maintenance" : "Update"}
                </button>
              ))}
            </div>
            <button className="small-btn" aria-pressed={dark} aria-label="Dark preview" title={dark ? "Dark preview" : "Light preview"} onClick={() => setDark(!dark)}>{dark ? "🌙" : "☀️"}</button>
          </div>
          <Phone c={c} screen={screen} dark={dark} />
          <p className="muted small" style={{ textAlign: "center" }}>A sketch of the app with your changes. Feature switches with a rollout below 100% show here as on.</p>
        </aside>
      </div>

      <div className={`savebar ${changed.length || status ? "show" : ""}`} role="region" aria-label="Publish changes">
        <div className="savebar-text">
          {blocking.length ? (
            <ul className="problems">{blocking.map((p, i) => <li key={i} className="error"><button className="linkish" onClick={() => setSection(p.section)}>{SECTION_NAME[p.section]}</button>: {p.message}</li>)}</ul>
          ) : confirming ? (
            <><strong>Before you publish:</strong><ul className="problems">{warnings.map((w) => <li key={w} className="warn">{w}</li>)}</ul></>
          ) : changed.length ? (
            <span>Unpublished changes: <strong>{changed.map((s) => SECTION_NAME[s]).join(", ")}</strong></span>
          ) : (
            <span role="status">{status}</span>
          )}
        </div>
        <div className="row">
          <button disabled={!changed.length || saving} onClick={() => { setC(saved); setConfirming(false); setStatus(""); }}>Discard</button>
          <button className="primary" disabled={!changed.length || !!blocking.length || saving} onClick={publish}>
            {saving ? "Publishing…" : confirming ? "Yes, publish" : "Publish to students"}
          </button>
        </div>
      </div>
    </>
  );
}

type EditorProps = { c: Customization; set: (patch: Partial<Customization>) => void };

function Card({ title, hint, children }: { title: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <div className="card">
      <h2 style={{ marginTop: 0 }}>{title}</h2>
      {hint && <p className="muted small" style={{ marginTop: -4 }}>{hint}</p>}
      {children}
    </div>
  );
}

function Switch({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button type="button" role="switch" aria-checked={on} aria-label={label} className={`switch ${on ? "on" : ""}`} onClick={() => onChange(!on)}>
      <span className="knob" />
    </button>
  );
}

function Counted({ label, value, max, onChange, area, placeholder }: { label: string; value: string; max: number; onChange: (v: string) => void; area?: boolean; placeholder?: string }) {
  const near = value.length > max * 0.9;
  return (
    <label className="field">
      <span className="field-label"><span>{label}</span><span className={near ? "warn" : ""}>{value.length}/{max}</span></span>
      {area
        ? <textarea value={value} maxLength={max} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
        : <input value={value} maxLength={max} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />}
    </label>
  );
}

function Brand({ c, set }: EditorProps) {
  const accent = c.accent || DEFAULT_ACCENT;
  const ratio = contrastWithWhite(accent);
  const [hex, setHex] = useState(accent);
  useEffect(() => setHex(accent), [accent]);
  return (
    <Card title="Brand colour" hint="Buttons, progress rings, the selected tab and links use this colour. Pick one with enough contrast: white text sits on it.">
      <div className="swatches">
        {ACCENTS.map((a) => (
          <button key={a.hex} className={`swatch ${accent.toUpperCase() === a.hex ? "on" : ""}`} aria-pressed={accent.toUpperCase() === a.hex}
            onClick={() => set({ accent: a.hex === DEFAULT_ACCENT ? "" : a.hex })}>
            <span className="chip" style={{ background: a.hex }} />{a.name}
          </button>
        ))}
      </div>
      <div className="row" style={{ marginTop: 12 }}>
        <input type="color" aria-label="Pick any colour" value={accent} onChange={(e) => set({ accent: e.target.value.toUpperCase() })} style={{ width: 56, height: 40, padding: 2 }} />
        <input aria-label="Colour code" value={hex} style={{ width: 130 }}
          onChange={(e) => { setHex(e.target.value); if (/^#[0-9A-Fa-f]{6}$/.test(e.target.value)) set({ accent: e.target.value.toUpperCase() }); }} />
        {c.accent && <button onClick={() => set({ accent: "" })}>Back to Parhlou teal</button>}
      </div>
      <p className={ratio >= 4.5 ? "ok small" : "warn small"} role="status">
        White text on this colour: {ratio.toFixed(1)} : 1 — {ratio >= 4.5 ? "easy to read ✓" : ratio >= 3 ? "fine for big buttons only; a darker shade is safer" : "too light; text will be hard to read"}
      </p>
    </Card>
  );
}

function Announcement({ c, set }: EditorProps) {
  const b = c.banner;
  const setB = (patch: Partial<typeof b>) => set({ banner: { ...b, ...patch } });
  const state = bannerLive(b, today());
  const label = { off: "Not showing", scheduled: `Starts ${b.startsAt}`, live: "Live now", ended: `Ended ${b.endsAt}` }[state];
  return (
    <>
      <Card title="Home announcement" hint="A card at the top of Home: exam reminders, new features, offers. Students can close it; it stays closed for them until you post a new one.">
        <div className="row spread">
          <label className="check"><Switch on={b.on} onChange={(on) => setB({ on })} label="Show the announcement" /> Show on Home</label>
          <span className={`pill ${state === "live" ? "ok" : ""}`}>{label}</span>
        </div>
        <div className="field" style={{ marginTop: 12 }}>
          <span className="field-title">Emoji</span>
          <div className="row">
            {EMOJIS.map((e) => <button key={e} className={`emoji ${b.emoji === e ? "on" : ""}`} aria-pressed={b.emoji === e} aria-label={`Emoji ${e}`} onClick={() => setB({ emoji: e })}>{e}</button>)}
            <input aria-label="Any emoji" value={b.emoji} maxLength={16} style={{ width: 70 }} onChange={(e) => setB({ emoji: e.target.value })} />
          </div>
        </div>
        <Counted label="Title" value={b.title} max={LIMITS.title} onChange={(title) => setB({ title })} placeholder="Board exams in 30 days" />
        <Counted label="Message" area value={b.message} max={LIMITS.message} onChange={(message) => setB({ message })} placeholder="Do one Guess Paper this week and check your weak chapters." />
        <div className="field">
          <span className="field-title">Colour</span>
          <div className="seg">{TONES.map(([t, n]) => <button key={t} aria-pressed={b.tone === t} className={b.tone === t ? "on" : ""} onClick={() => setB({ tone: t })}>{n}</button>)}</div>
        </div>
      </Card>
      <Card title="Who and when">
        <label className="field"><span>Show to</span>
          <select value={b.audience} onChange={(e) => setB({ audience: e.target.value as Audience })}>
            <option value="all">Everyone</option><option value="free">Free plan only</option><option value="plus">Premium only</option>
          </select>
        </label>
        <div className="two">
          <label className="field"><span>From (optional)</span><input type="date" value={b.startsAt} onChange={(e) => setB({ startsAt: e.target.value })} /></label>
          <label className="field"><span>Until, including that day (optional)</span><input type="date" value={b.endsAt} onChange={(e) => setB({ endsAt: e.target.value })} /></label>
        </div>
        <p className="muted small">Dates follow each phone's own calendar day.</p>
      </Card>
      <Card title="Button (optional)" hint="Opens a web page, for example your WhatsApp channel or a form. Only https:// links work.">
        <div className="two">
          <Counted label="Button text" value={b.button} max={LIMITS.button} onChange={(button) => setB({ button })} placeholder="Join the channel" />
          <label className="field"><span>Link</span><input value={b.link} inputMode="url" placeholder="https://…" onChange={(e) => setB({ link: e.target.value })} /></label>
        </div>
      </Card>
      <div className="row">
        <button onClick={() => setB({ id: newBannerId() })} title="Students who closed it will see it again">Show again to students who closed it</button>
        <button className="danger" onClick={() => set({ banner: { ...b, on: false, title: "", message: "", button: "", link: "", startsAt: "", endsAt: "" } })}>Clear announcement</button>
      </div>
    </>
  );
}

function Features({ c, set }: EditorProps) {
  const setF = (key: FeatureKey, patch: Partial<{ on: boolean; rollout: number }>) =>
    set({ features: { ...c.features, [key]: { ...c.features[key], ...patch } } });
  return (
    <Card title="Feature switches" hint={<>Turn parts of the app on or off. With <em>rollout</em> a feature reaches only a share of students first (each student always gets the same answer), so you can try something on 10% before everyone.</>}>
      <div className="features">
        {FEATURES.map((f) => {
          const v = c.features[f.key];
          return (
            <div key={f.key} className={`feature ${v.on ? "" : "off"}`}>
              <div className="feature-head">
                <span className="feature-emoji" aria-hidden>{f.emoji}</span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="feature-name">{f.name}</div>
                  <div className="muted small">{f.what}</div>
                </div>
                <Switch on={v.on} label={f.name} onChange={(on) => setF(f.key, { on })} />
              </div>
              {v.on && v.rollout === 100 && (
                <div className="rollout"><button className="small-btn" onClick={() => setF(f.key, { rollout: 50 })}>Try on a share of students first</button></div>
              )}
              {v.on && v.rollout < 100 && (
                <div className="rollout">
                  <input type="range" min={0} max={95} step={5} value={v.rollout} aria-label={`${f.name}: share of students`}
                    onChange={(e) => setF(f.key, { rollout: Number(e.target.value) })} />
                  <span className="rollout-n">{v.rollout}% of students</span>
                  <button className="small-btn" onClick={() => setF(f.key, { rollout: 100 })}>Give to everyone</button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </Card>
  );
}

function Game({ c, set }: EditorProps) {
  const t = c.tuning;
  const goal20 = 20 * t.xpPerMinute;
  return (
    <>
      <Card title="XP and rewards" hint="How many XP points each thing earns. Bigger numbers feel generous; keep them steady once students compare scores in the League.">
        {TUNING.map((s) => {
          const v = t[s.key];
          return (
            <label key={s.key} className="field">
              <span className="field-label"><span>{s.label}</span><span>{s.min}–{s.max}{v !== s.def ? ` · built-in ${s.def}` : ""}</span></span>
              <div className="row" style={{ flexWrap: "nowrap" }}>
                <input type="range" min={s.min} max={s.max} value={Math.min(s.max, Math.max(s.min, v))} aria-label={s.label}
                  onChange={(e) => set({ tuning: { ...t, [s.key]: Number(e.target.value) } })} />
                <input type="number" min={s.min} max={s.max} value={v} style={{ width: 90 }} aria-label={`${s.label} number`}
                  onChange={(e) => set({ tuning: { ...t, [s.key]: Math.round(Number(e.target.value)) } })} />
                <button className="small-btn" disabled={v === s.def} onClick={() => set({ tuning: { ...t, [s.key]: s.def } })}>Reset</button>
              </div>
            </label>
          );
        })}
      </Card>
      <Card title="What a student sees">
        <ul className="facts">
          <li>A 20-minute daily goal is <strong>{goal20} XP</strong>; a 45-minute one is <strong>{45 * t.xpPerMinute} XP</strong>.</li>
          <li>One lesson and two marked written answers: <strong>{t.lessonXp + 2 * t.writtenXp} XP</strong>.</li>
          <li>A full paper handed in: <strong>{t.paperXp} XP</strong>.</li>
          <li>The daily chest gives between <strong>{t.chestMinXp} and {Math.max(t.chestMinXp, t.chestMaxXp)} XP</strong>.</li>
        </ul>
      </Card>
    </>
  );
}

function Texts({ c, set }: EditorProps) {
  const reminder = (c.reminderText || "Your {streak}-day streak is waiting. 10 minutes keeps it alive!").replaceAll("{streak}", "7");
  return (
    <>
      <Card title="Home greeting line" hint="Under “Good evening, Ali” on Home. Leave empty for Ustaad's own line, which changes with the student's streak and progress.">
        <Counted label="Message" value={c.homeMessage} max={LIMITS.homeMessage} onChange={(homeMessage) => set({ homeMessage })} placeholder="Ustaad's line (automatic)" />
      </Card>
      <Card title="Daily reminder" hint={<>The evening notification. Write <code>{"{streak}"}</code> where the student's streak length should go.</>}>
        <Counted label="Reminder" area value={c.reminderText} max={LIMITS.reminderText} onChange={(reminderText) => set({ reminderText })} placeholder="The app's own reminder (automatic)" />
        <div className="row"><button className="small-btn" onClick={() => set({ reminderText: (c.reminderText + " {streak}").trimStart().slice(0, LIMITS.reminderText) })}>Insert {"{streak}"}</button></div>
        <div className="notif" aria-label="Notification preview">
          <div className="notif-app">🟢 Parhlou · now</div>
          <div>{reminder}</div>
        </div>
      </Card>
      <Card title="Premium pitch" hint="The line on the Premium screen that says what Premium gives. Leave empty for the automatic one.">
        <Counted label="Pitch" area value={c.plusPitch} max={LIMITS.plusPitch} onChange={(plusPitch) => set({ plusPitch })} placeholder="Every chapter of every book, the Guess Paper and all past papers, your own tests, Ask Ustaad and AI marking. No ads." />
      </Card>
    </>
  );
}

function Health({ c, set }: EditorProps) {
  return (
    <>
      <Card title="Maintenance mode" hint="Covers the whole app with a friendly “We'll be right back” screen. Use it while you fix something serious, then switch it off.">
        <label className="check"><Switch on={c.maintenance} onChange={(maintenance) => set({ maintenance })} label="Maintenance mode" /> {c.maintenance ? <strong className="warn">On: students can't study</strong> : "Off"}</label>
        <Counted label="Message" area value={c.maintenanceMessage} max={LIMITS.notice} onChange={(maintenanceMessage) => set({ maintenanceMessage })} placeholder={DEFAULT_MAINTENANCE} />
      </Card>
      <Card title="Minimum version" hint="Phones with an older build must update before they can study. Use it after a fix that every student needs. Each APK on GitHub Releases is named with its build number (for example build 71).">
        <label className="field"><span>Oldest build allowed (0 = no minimum)</span>
          <input type="number" min={0} value={c.minBuild} style={{ width: 140 }} onChange={(e) => set({ minBuild: Math.max(0, Math.round(Number(e.target.value) || 0)) })} />
        </label>
        <Counted label="Update message" area value={c.updateMessage} max={LIMITS.notice} onChange={(updateMessage) => set({ updateMessage })} placeholder={DEFAULT_UPDATE} />
      </Card>
    </>
  );
}

function History({ current, onLoad }: { current: Customization; onLoad: (c: Customization) => void }) {
  const [rows, setRows] = useState<Version[] | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    db.rpc("owner_customization_history").then(({ data, error }) => (error ? setError(error.code === "PGRST202" ? MIGRATION : explain(error)) : setRows((data ?? []) as Version[])));
  }, []);
  const exportJson = () => {
    const url = URL.createObjectURL(new Blob([JSON.stringify(toSaved(current), null, 2)], { type: "application/json" }));
    const a = Object.assign(document.createElement("a"), { href: url, download: `parhlou-app-design-${today()}.json` });
    a.click(); URL.revokeObjectURL(url);
  };
  const importJson = (file: File | undefined) => {
    if (!file) return;
    file.text().then((t) => { try { onLoad(fromSaved(JSON.parse(t))); } catch { setError("That file isn't an App design export."); } });
  };
  return (
    <>
      <Card title="Earlier versions" hint="Every publish is kept. Load one into the editor, check it in the preview, then publish to restore it.">
        {error && <p className="error">{error}</p>}
        {!rows && !error && <p className="muted">Loading…</p>}
        {rows && !rows.length && <p className="muted">Nothing published yet.</p>}
        {rows && rows.length > 0 && (
          <div className="scroll"><table>
            <thead><tr><th>Published</th><th>Different from now</th><th /></tr></thead>
            <tbody>{rows.map((r, i) => {
              const v = fromSaved(r.value);
              const diff = changedSections(current, v);
              return (
                <tr key={r.saved_at + i}>
                  <td>{when(r.saved_at)}{i === 0 && <span className="pill ok" style={{ marginLeft: 6 }}>live</span>}</td>
                  <td className="muted">{diff.length ? diff.map((s) => SECTION_NAME[s]).join(", ") : "Same as now"}</td>
                  <td><button className="small-btn" disabled={!diff.length} onClick={() => onLoad(v)}>Load</button></td>
                </tr>
              );
            })}</tbody>
          </table></div>
        )}
      </Card>
      <Card title="Backup" hint="Save the published design as a file, or load one you saved before.">
        <div className="row">
          <button onClick={exportJson}>Download as file</button>
          <label className="button-like">Load from file<input type="file" accept="application/json,.json" hidden onChange={(e) => importJson(e.target.files?.[0])} /></label>
          <button onClick={() => onLoad(defaults())}>Load the built-in design</button>
        </div>
      </Card>
    </>
  );
}

/** A sketch of the app's Home, Premium screen and covers with the owner's changes. */
function Phone({ c, screen, dark }: { c: Customization; screen: Screen; dark: boolean }) {
  const accent = c.accent || DEFAULT_ACCENT;
  const on = (k: FeatureKey) => c.features[k].on && c.features[k].rollout > 0;
  const b = c.banner;
  const showBanner = bannerLive(b, today()) === "live";
  const tone = { info: accent, success: "#1C9E5E", warning: "#D99A00", danger: "#E5484D" }[b.tone];
  const vars = { "--accent": accent, "--accent-soft": dark ? "#1D3039" : tint(accent, 0.86) } as CSSProperties;
  const tabs = [["🏠", "Home"], ["📚", "Books"], ["📝", "Papers"], ...(on("league") ? [["🏆", "League"]] : []), ["👤", "Profile"]];
  return (
    <div className={`phone ${dark ? "dark" : ""}`} style={vars}>
      <div className="phone-status"><span>9:41</span><span>▮▮▮ 🔋</span></div>
      {screen === "cover" ? (
        <div className="phone-cover">
          <div className="mascot" aria-hidden>🧑‍🏫</div>
          <h3>{c.maintenance ? "We'll be right back" : "Time to update"}</h3>
          <p>{c.maintenance ? (c.maintenanceMessage || DEFAULT_MAINTENANCE) : (c.updateMessage || DEFAULT_UPDATE)}</p>
          {!c.maintenance && <div className="p-btn">Update now</div>}
          {!c.maintenance && c.minBuild === 0 && <p className="p-muted">(No minimum is set, so nobody sees this.)</p>}
        </div>
      ) : screen === "plus" ? (
        <div className="phone-body">
          <div className="p-title">Parhlou Premium</div>
          <div className="p-card">
            <strong>Free</strong>
            <p className="p-muted">Chapters 1 and 2, one past paper and 3 tries of Ask Ustaad.</p>
            <strong style={{ color: "var(--accent)" }}>Premium</strong>
            <p className="p-muted">{c.plusPitch || "Every chapter of every book, the Guess Paper and all past papers, your own tests, Ask Ustaad and AI marking. No ads."}</p>
            <div className="p-price">Rs 3,999 a month</div>
          </div>
          {!on("plus") && <p className="p-muted">The Premium upgrade switch is off: students can't open this screen.</p>}
        </div>
      ) : (
        <div className="phone-body">
          <div className="p-hello">Good evening, Ali 👋</div>
          <div className="p-muted">{c.homeMessage || "Ustaad: one small step today keeps your 7-day streak alive."}</div>
          {showBanner && (
            <div className="p-banner" style={{ borderColor: tone, background: dark ? "#1D3039" : tint(tone, 0.88) }}>
              <span className="p-emoji">{b.emoji}</span>
              <div style={{ flex: 1 }}>
                <strong>{b.title}</strong>
                {b.message && <div>{b.message}</div>}
                {b.button && b.link && <div className="p-link" style={{ color: tone }}>{b.button}</div>}
              </div>
              <span className="p-muted">✕</span>
            </div>
          )}
          <div className="p-stats"><span>🔥 7</span><span>⚡ 240 XP</span><span>🎯 {20 * c.tuning.xpPerMinute} XP goal</span></div>
          <div className="p-card p-journey">
            <div className="p-ring" />
            <div style={{ flex: 1 }}><strong>Mathematics</strong><div className="p-muted">Unit 2 · Logarithms</div></div>
          </div>
          <div className="p-btn">Continue learning</div>
          {on("askUstaad") && <div className="p-btn p-soft">🧑‍🏫 Ask Ustaad</div>}
          {on("chest") && <div className="p-card p-chest">🎁 Daily chest · {c.tuning.chestMinXp}–{Math.max(c.tuning.chestMinXp, c.tuning.chestMaxXp)} XP</div>}
        </div>
      )}
      {screen !== "cover" && (
        <div className="phone-tabs">
          {tabs.map(([icon, name], i) => <div key={name} className={i === (screen === "plus" ? tabs.length - 1 : 0) ? "on" : ""}><span>{icon}</span>{name}</div>)}
        </div>
      )}
    </div>
  );
}
