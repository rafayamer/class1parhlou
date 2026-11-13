import { describe, expect, it } from "vitest";
import sample from "../../supabase/functions/ai/content/sample.json";
import { blueprintProblems, bundleProblems, paperProblems, writtenProblems } from "./validate";
import { estimateCost } from "./cost";
import type { Bundle } from "./types";

const fresh = (): Bundle => structuredClone(sample) as unknown as Bundle;

describe("bundle checks", () => {
  it("the published sample passes", () => {
    expect(bundleProblems(fresh(), fresh().subjectId)).toEqual([]);
  });

  it("a rubric that doesn't add up blocks publishing", () => {
    const b = fresh();
    b.written[0].rubric[0].marks = 2;
    const problems = bundleProblems(b, b.subjectId);
    expect(problems.some((p) => p.message.includes("add up to 4 but the question is worth 3"))).toBe(true);
  });

  it("an MCQ needs exactly one correct option and a reason for every option", () => {
    const b = fresh();
    b.mcqs[0].correctIndex = 7;
    b.quiz[0].rationales = b.quiz[0].rationales.slice(1);
    const msgs = bundleProblems(b, b.subjectId).map((p) => p.message);
    expect(msgs).toContain("exactly one option must be marked correct");
    expect(msgs).toContain("every option needs a reason (why it is right or wrong)");
  });

  it("broken links between notes, modules and units are found", () => {
    const b = fresh();
    b.mcqs[0].noteId = "n-missing";
    b.notes[0].moduleId = "m-missing";
    b.blueprints[0].chapterId = "u-99";
    const msgs = bundleProblems(b, b.subjectId).map((p) => p.message).join("\n");
    expect(msgs).toContain('tutor note "n-missing"');
    expect(msgs).toContain('module "m-missing"');
    expect(msgs).toContain('unit "u-99"');
  });

  it("only YouTube links are accepted for videos", () => {
    const b = fresh();
    b.subject.chapters[0].modules[0].video = { url: "http://example.com/video" };
    expect(bundleProblems(b, b.subjectId).map((p) => p.message)).toContain("video link must be a YouTube https link");
    b.subject.chapters[0].modules[0].video = { url: "https://www.youtube.com/watch?v=abc123" };
    expect(bundleProblems(b, b.subjectId)).toEqual([]);
  });

  it("the wrong subject or duplicate ids are refused", () => {
    const b = fresh();
    expect(bundleProblems(b, "other")[0].message).toContain("subjectId must be other");
    b.written[1].id = b.written[0].id;
    expect(bundleProblems(b, b.subjectId).some((p) => p.message.includes("is used twice"))).toBe(true);
  });
});

describe("papers and blueprints", () => {
  it("blueprint totals must reconcile", () => {
    const bp = fresh().blueprints[0];
    expect(blueprintProblems(bp, "x")).toEqual([]);
    expect(blueprintProblems({ ...bp, totalMarks: 41 }, "x")[0].message).toBe("sections add up to 40 marks but total is 41");
    expect(blueprintProblems({ ...bp, sections: [{ ...bp.sections[0], attempt: 11 }] }, "x").map((p) => p.message)).toContain("attempt exceeds count");
  });

  it("a paper must match its blueprint's counts and marks", () => {
    const b = fresh();
    const paper = b.papers[0];
    const bp = b.blueprints.find((x) => x.id === paper.blueprintId);
    expect(paperProblems(paper, bp)).toEqual([]);
    const short = { ...paper, mcqs: paper.mcqs.slice(1) };
    expect(paperProblems(short, bp)[0].message).toContain("has 9 MCQs but section 1 needs 10");
    const reweighted = { ...paper, written: paper.written.map((w, i) => (i === 0 ? { ...w, marks: 4 } : w)) };
    expect(paperProblems(reweighted, bp).some((p) => p.message.includes("is worth 4 but section 2 questions are worth 3"))).toBe(true);
  });

  it("written questions need model answers and accepted patterns", () => {
    const q = { ...fresh().written[0], modelAnswer: [""], rubric: [{ ...fresh().written[0].rubric[0], accept: [[""]] , marks: 3 }] };
    const msgs = writtenProblems(q, "q").map((p) => p.message);
    expect(msgs).toContain("needs a model answer");
    expect(msgs).toContain("needs at least one accepted answer pattern, with no empty patterns");
  });
});

describe("cost", () => {
  it("estimates spend from tokens and rates", () => {
    const c = estimateCost(2_000_000, 500_000, { usdPerMillionInput: 1.25, usdPerMillionOutput: 10, pkrPerUsd: 280 });
    expect(c.usd).toBeCloseTo(7.5);
    expect(c.pkr).toBeCloseTo(2100);
  });
});
