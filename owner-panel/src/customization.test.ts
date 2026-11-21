import { describe, expect, it } from "vitest";
import { bannerLive, changedSections, contrastWithWhite, defaults, fromSaved, problems, risks, toSaved } from "./customization";

describe("app design", () => {
  it("nothing changed saves nothing, so the app keeps its own defaults", () => {
    expect(toSaved(defaults())).toEqual({});
    expect(fromSaved(null)).toEqual({ ...defaults(), banner: { ...defaults().banner, id: expect.any(String) } });
  });

  it("a saved design reads back the same", () => {
    const c = defaults();
    c.accent = "#4F53D6";
    c.homeMessage = "Board exams in 30 days!";
    c.banner = { ...c.banner, on: true, title: "Exam week", message: "One paper a day.", tone: "warning", audience: "free", startsAt: "2026-12-01", endsAt: "2026-12-07", button: "Join", link: "https://example.com/x" };
    c.features.league = { on: false, rollout: 100 };
    c.features.chest = { on: true, rollout: 30 };
    c.tuning.lessonXp = 25;
    c.minBuild = 80;
    const saved = toSaved(c);
    expect(saved).toMatchObject({
      accent: "#4F53D6", homeMessage: "Board exams in 30 days!", minBuild: 80,
      features: { league: { on: false, rollout: 100 }, chest: { on: true, rollout: 30 } }, tuning: { lessonXp: 25 },
      banner: { on: true, title: "Exam week", tone: "warning", audience: "free", startsAt: "2026-12-01", link: "https://example.com/x" },
    });
    expect(changedSections(fromSaved(JSON.parse(JSON.stringify(saved))), c)).toEqual([]);
  });

  it("broken saved values are clamped the way the app clamps them", () => {
    const c = fromSaved({ accent: "red", tuning: { lessonXp: -5, chestMinXp: 50, chestMaxXp: 10, xpPerMinute: 99 }, features: { videos: false, chest: { rollout: 250 } }, minBuild: -3 });
    expect(c.accent).toBe("");
    expect(c.tuning.lessonXp).toBe(0);
    expect(c.tuning.chestMaxXp).toBeGreaterThanOrEqual(c.tuning.chestMinXp);
    expect(c.tuning.xpPerMinute).toBe(20);
    expect(c.features.videos).toEqual({ on: false, rollout: 100 });
    expect(c.features.chest.rollout).toBe(100);
    expect(c.minBuild).toBe(0);
  });

  it("problems block publishing and point at their section", () => {
    const c = defaults();
    c.banner = { ...c.banner, on: true, title: "", button: "Open", link: "javascript:alert(1)", startsAt: "2026-12-07", endsAt: "2026-12-01" };
    c.tuning.chestMinXp = 40; c.tuning.chestMaxXp = 30;
    const p = problems(c);
    expect(p.map((x) => x.section)).toEqual(expect.arrayContaining(["announcement", "game"]));
    expect(p.some((x) => x.message.includes("https://"))).toBe(true);
    expect(p.some((x) => x.message.includes("ends before it starts"))).toBe(true);
    expect(problems(defaults())).toEqual([]);
  });

  it("risky changes ask first", () => {
    const before = defaults();
    const after = defaults();
    after.maintenance = true; after.minBuild = 90; after.features.league = { on: false, rollout: 100 };
    const r = risks(before, after);
    expect(r).toHaveLength(3);
    expect(r[2]).toContain("League tab");
    expect(risks(before, defaults())).toEqual([]);
  });

  it("the announcement follows its dates, like the app", () => {
    const b = { ...defaults().banner, on: true, title: "Hi", startsAt: "2026-12-01", endsAt: "2026-12-07" };
    expect(bannerLive(b, "2026-11-30")).toBe("scheduled");
    expect(bannerLive(b, "2026-12-07")).toBe("live");
    expect(bannerLive(b, "2026-12-08")).toBe("ended");
    expect(bannerLive({ ...b, on: false }, "2026-12-03")).toBe("off");
  });

  it("brand colours are checked for readable white text", () => {
    expect(contrastWithWhite("#0B8A8A")).toBeGreaterThan(4);
    expect(contrastWithWhite("#33434C")).toBeGreaterThan(7);
    expect(contrastWithWhite("#FFE066")).toBeLessThan(2);
  });
});
