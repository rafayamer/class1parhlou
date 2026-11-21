/**
 * The owner's app customisation (owner panel → App design), saved as one setting, `app_customization`. Phones read it
 * through public_app_settings() and apply it the next time they open. The app checks and clamps every value again
 * (android/.../data/AppSettings.kt), so the limits here mirror the app's: what the panel saves is what students get.
 */

export type Tone = "info" | "success" | "warning" | "danger";
export type Audience = "all" | "free" | "plus";

export interface Banner {
  on: boolean;
  /** Changing the id shows the banner again to students who closed it. */
  id: string;
  emoji: string;
  title: string;
  message: string;
  button: string;
  link: string;
  tone: Tone;
  audience: Audience;
  startsAt: string;
  endsAt: string;
}

export interface Feature { on: boolean; rollout: number }

export interface Tuning {
  lessonXp: number; writtenXp: number; paperXp: number; chestMinXp: number; chestMaxXp: number; xpPerMinute: number;
}

export interface Customization {
  accent: string;
  homeMessage: string;
  reminderText: string;
  plusPitch: string;
  banner: Banner;
  features: Record<FeatureKey, Feature>;
  tuning: Tuning;
  maintenance: boolean;
  maintenanceMessage: string;
  minBuild: number;
  updateMessage: string;
}

/** Every switch the app understands (the same list as AppSettings.FEATURES in the app). */
export const FEATURES = [
  { key: "league", name: "League tab", emoji: "🏆", what: "The weekly league, XP ranking and badges. Off hides the tab." },
  { key: "chest", name: "Daily chest", emoji: "🎁", what: "The reward chest students open after the day's quests." },
  { key: "celebrations", name: "Celebrations", emoji: "🎉", what: "Full-screen confetti for level-ups, streaks and new badges." },
  { key: "askUstaad", name: "Ask Ustaad on Home", emoji: "🧑‍🏫", what: "The big Ask Ustaad button on Home. AI marking is not affected (use AI settings to stop all AI)." },
  { key: "guessPaper", name: "Guess Paper", emoji: "🎯", what: "The predicted board paper on the Papers tab." },
  { key: "pastPapers", name: "Past papers", emoji: "📜", what: "Real board papers from earlier years on the Papers tab." },
  { key: "makeTest", name: "Make my own test", emoji: "🛠️", what: "Students build a test from the chapters they choose." },
  { key: "videos", name: "Video lessons", emoji: "▶️", what: "YouTube lectures on each section page." },
  { key: "plus", name: "Premium upgrade", emoji: "⭐", what: "The way into the Premium screen from Profile." },
  { key: "paywall", name: "Premium paywall", emoji: "🔒", what: "Locks Premium content for free students (chapters 3+, papers, Ask Ustaad, AI marking). Off makes everything free." },
] as const;
export type FeatureKey = (typeof FEATURES)[number]["key"];

/** Built-in game numbers (domain/Game.kt Tuning) with the app's allowed ranges. */
export const TUNING = [
  { key: "lessonXp", label: "XP for finishing a lesson", def: 15, min: 0, max: 200 },
  { key: "writtenXp", label: "XP for a written answer marked by Ustaad", def: 10, min: 0, max: 200 },
  { key: "paperXp", label: "XP for handing in a full paper", def: 50, min: 0, max: 500 },
  { key: "chestMinXp", label: "Daily chest: smallest prize (XP)", def: 20, min: 0, max: 200 },
  { key: "chestMaxXp", label: "Daily chest: biggest prize (XP)", def: 35, min: 0, max: 300 },
  { key: "xpPerMinute", label: "Daily goal: XP per minute of study", def: 4, min: 1, max: 20 },
] as const satisfies readonly { key: keyof Tuning; label: string; def: number; min: number; max: number }[];

export const ACCENTS = [
  { name: "Parhlou teal", hex: "#0B8A8A" },
  { name: "Indigo", hex: "#4F53D6" },
  { name: "Green", hex: "#1C7F4C" },
  { name: "Royal blue", hex: "#1F5FBF" },
  { name: "Plum", hex: "#8A3FA8" },
  { name: "Crimson", hex: "#B8333A" },
  { name: "Burnt orange", hex: "#B5531B" },
  { name: "Charcoal", hex: "#33434C" },
] as const;

export const DEFAULT_ACCENT = "#0B8A8A";
export const DEFAULT_MAINTENANCE = "Parhlou is getting an upgrade. Please come back in a little while.";
export const DEFAULT_UPDATE = "A new version of Parhlou is ready. Please update to keep studying.";
export const LIMITS = { homeMessage: 120, reminderText: 160, plusPitch: 160, title: 80, message: 300, button: 30, notice: 300 } as const;

const HEX = /^#[0-9A-Fa-f]{6}$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export function emptyBanner(): Banner {
  return { on: false, id: newBannerId(), emoji: "📣", title: "", message: "", button: "", link: "", tone: "info", audience: "all", startsAt: "", endsAt: "" };
}

export function newBannerId(): string {
  return "b" + Date.now().toString(36);
}

export function defaults(): Customization {
  return {
    accent: "", homeMessage: "", reminderText: "", plusPitch: "", banner: emptyBanner(),
    features: Object.fromEntries(FEATURES.map((f) => [f.key, { on: true, rollout: 100 }])) as Record<FeatureKey, Feature>,
    tuning: Object.fromEntries(TUNING.map((t) => [t.key, t.def])) as unknown as Tuning,
    maintenance: false, maintenanceMessage: "", minBuild: 0, updateMessage: "",
  };
}

const clamp = (n: unknown, lo: number, hi: number, def: number) => {
  const x = typeof n === "number" && Number.isFinite(n) ? Math.round(n) : def;
  return Math.min(hi, Math.max(lo, x));
};
const str = (v: unknown, max: number) => (typeof v === "string" ? v.slice(0, max) : "");

/** Reads a saved value (anything at all) into a complete, valid customisation. */
export function fromSaved(raw: unknown): Customization {
  const d = defaults();
  const o = raw && typeof raw === "object" && !Array.isArray(raw) ? (raw as Record<string, unknown>) : {};
  const b = o.banner && typeof o.banner === "object" ? (o.banner as Record<string, unknown>) : null;
  const f = o.features && typeof o.features === "object" ? (o.features as Record<string, unknown>) : {};
  const t = o.tuning && typeof o.tuning === "object" ? (o.tuning as Record<string, unknown>) : {};
  const features = { ...d.features };
  for (const { key } of FEATURES) {
    const x = f[key];
    if (typeof x === "boolean") features[key] = { on: x, rollout: 100 };
    else if (x && typeof x === "object") {
      const y = x as Record<string, unknown>;
      features[key] = { on: y.on !== false, rollout: clamp(y.rollout, 0, 100, 100) };
    }
  }
  const tuning = { ...d.tuning };
  for (const s of TUNING) tuning[s.key] = clamp(t[s.key], s.min, s.max, s.def);
  if (tuning.chestMaxXp < tuning.chestMinXp) tuning.chestMaxXp = tuning.chestMinXp;
  const tone = ["info", "success", "warning", "danger"].includes(b?.tone as string) ? (b!.tone as Tone) : "info";
  const audience = ["all", "free", "plus"].includes(b?.audience as string) ? (b!.audience as Audience) : "all";
  return {
    accent: typeof o.accent === "string" && HEX.test(o.accent) ? o.accent.toUpperCase() : "",
    homeMessage: str(o.homeMessage, LIMITS.homeMessage),
    reminderText: str(o.reminderText, LIMITS.reminderText),
    plusPitch: str(o.plusPitch, LIMITS.plusPitch),
    banner: b ? {
      on: b.on !== false && typeof b.title === "string" && b.title.trim() !== "",
      id: typeof b.id === "string" && b.id ? b.id : newBannerId(),
      emoji: str(b.emoji, 16) || "📣", title: str(b.title, LIMITS.title), message: str(b.message, LIMITS.message),
      button: str(b.button, LIMITS.button), link: str(b.link, 500), tone, audience,
      startsAt: typeof b.startsAt === "string" && DATE.test(b.startsAt) ? b.startsAt : "",
      endsAt: typeof b.endsAt === "string" && DATE.test(b.endsAt) ? b.endsAt : "",
    } : d.banner,
    features, tuning,
    maintenance: o.maintenance === true,
    maintenanceMessage: str(o.maintenanceMessage, LIMITS.notice),
    minBuild: clamp(o.minBuild, 0, 1_000_000, 0),
    updateMessage: str(o.updateMessage, LIMITS.notice),
  };
}

/**
 * What gets saved: only values that differ from the app's built-in ones, so the setting stays small and the app's own
 * defaults keep applying to anything the owner never touched.
 */
export function toSaved(c: Customization): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  const text = (k: "homeMessage" | "reminderText" | "plusPitch" | "maintenanceMessage" | "updateMessage") => {
    const v = c[k].trim();
    if (v) out[k] = v;
  };
  if (HEX.test(c.accent) && c.accent.toUpperCase() !== DEFAULT_ACCENT) out.accent = c.accent.toUpperCase();
  text("homeMessage"); text("reminderText"); text("plusPitch");
  const b = c.banner;
  if (b.title.trim()) {
    const banner: Record<string, unknown> = { on: b.on, id: b.id, emoji: b.emoji.trim() || "📣", title: b.title.trim() };
    if (b.message.trim()) banner.message = b.message.trim();
    if (b.button.trim() && b.link.trim()) { banner.button = b.button.trim(); banner.link = b.link.trim(); }
    if (b.tone !== "info") banner.tone = b.tone;
    if (b.audience !== "all") banner.audience = b.audience;
    if (b.startsAt) banner.startsAt = b.startsAt;
    if (b.endsAt) banner.endsAt = b.endsAt;
    out.banner = banner;
  }
  const features: Record<string, Feature> = {};
  for (const { key } of FEATURES) {
    const f = c.features[key];
    if (!f.on || f.rollout < 100) features[key] = { on: f.on, rollout: f.rollout };
  }
  if (Object.keys(features).length) out.features = features;
  const tuning: Record<string, number> = {};
  for (const s of TUNING) if (c.tuning[s.key] !== s.def) tuning[s.key] = c.tuning[s.key];
  if (Object.keys(tuning).length) out.tuning = tuning;
  if (c.maintenance) out.maintenance = true;
  text("maintenanceMessage");
  if (c.minBuild > 0) out.minBuild = c.minBuild;
  text("updateMessage");
  return out;
}

export interface Problem { section: Section; message: string }
export type Section = "brand" | "announcement" | "features" | "game" | "texts" | "health";

/** Problems that block saving, each tied to the section to fix it in. */
export function problems(c: Customization): Problem[] {
  const p: Problem[] = [];
  if (c.accent && !HEX.test(c.accent)) p.push({ section: "brand", message: "The colour must look like #0B8A8A (# and six letters or digits)." });
  const b = c.banner;
  if (b.on && !b.title.trim()) p.push({ section: "announcement", message: "The announcement needs a title." });
  if (b.link.trim() && !/^https:\/\/[^\s/]+\.[^\s]+$/.test(b.link.trim())) p.push({ section: "announcement", message: "The button link must start with https:// (other links are ignored by the app)." });
  if (b.button.trim() && !b.link.trim()) p.push({ section: "announcement", message: "The button needs a link, or clear the button text." });
  if (b.startsAt && b.endsAt && b.endsAt < b.startsAt) p.push({ section: "announcement", message: "The announcement ends before it starts." });
  if (c.tuning.chestMaxXp < c.tuning.chestMinXp) p.push({ section: "game", message: "The chest's biggest prize must be at least its smallest." });
  for (const s of TUNING) {
    const v = c.tuning[s.key];
    if (!Number.isInteger(v) || v < s.min || v > s.max) p.push({ section: "game", message: `${s.label}: use a whole number from ${s.min} to ${s.max}.` });
  }
  if (!Number.isInteger(c.minBuild) || c.minBuild < 0) p.push({ section: "health", message: "The minimum build must be 0 or a whole number." });
  if (JSON.stringify(toSaved(c)).length >= 20000) p.push({ section: "texts", message: "Everything together is too long to save. Shorten the texts." });
  return p;
}

/** Warnings shown before saving changes that affect every student at once. */
export function risks(before: Customization, after: Customization): string[] {
  const r: string[] = [];
  if (after.maintenance && !before.maintenance) r.push("Maintenance mode is on: every student sees the “We'll be right back” screen until you switch it off.");
  if (after.minBuild > before.minBuild) r.push(`Students on builds older than ${after.minBuild} must update before they can study.`);
  const off = FEATURES.filter((f) => before.features[f.key].on && !after.features[f.key].on).map((f) => f.name);
  if (off.length) r.push(`Switched off for everyone: ${off.join(", ")}.`);
  return r;
}

/** Which sections differ, so the save bar can list them. */
export function changedSections(a: Customization, b: Customization): Section[] {
  const same = (x: unknown, y: unknown) => JSON.stringify(x) === JSON.stringify(y);
  const s: Section[] = [];
  if (!same(a.accent, b.accent)) s.push("brand");
  if (!same(a.banner, b.banner)) s.push("announcement");
  if (!same(a.features, b.features)) s.push("features");
  if (!same(a.tuning, b.tuning)) s.push("game");
  if (!same([a.homeMessage, a.reminderText, a.plusPitch], [b.homeMessage, b.reminderText, b.plusPitch])) s.push("texts");
  if (!same([a.maintenance, a.maintenanceMessage, a.minBuild, a.updateMessage], [b.maintenance, b.maintenanceMessage, b.minBuild, b.updateMessage])) s.push("health");
  return s;
}

/** Whether the banner shows on [today] (YYYY-MM-DD) — the same rule as the app. */
export function bannerLive(b: Banner, today: string): "off" | "scheduled" | "live" | "ended" {
  if (!b.on || !b.title.trim()) return "off";
  if (b.startsAt && today < b.startsAt) return "scheduled";
  if (b.endsAt && today > b.endsAt) return "ended";
  return "live";
}

/** WCAG contrast ratio of white text on [hex]: buttons use white text on the brand colour. */
export function contrastWithWhite(hex: string): number {
  if (!HEX.test(hex)) return 0;
  const lin = (c: number) => { const s = c / 255; return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4; };
  const n = parseInt(hex.slice(1), 16);
  const L = 0.2126 * lin((n >> 16) & 255) + 0.7152 * lin((n >> 8) & 255) + 0.0722 * lin(n & 255);
  return 1.05 / (L + 0.05);
}

/** A lighter tint of [hex] for soft backgrounds (mixes with white). */
export function tint(hex: string, amount: number): string {
  if (!HEX.test(hex)) return hex;
  const n = parseInt(hex.slice(1), 16);
  const mix = (c: number) => Math.round(c + (255 - c) * amount).toString(16).padStart(2, "0");
  return `#${mix((n >> 16) & 255)}${mix((n >> 8) & 255)}${mix(n & 255)}`;
}
