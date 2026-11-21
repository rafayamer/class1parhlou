import { describe, expect, it } from "vitest";
import { whyFailed } from "./aiErrors";

describe("why an AI call failed", () => {
  it("says what to do when OpenAI credit runs out or the key is wrong", () => {
    expect(whyFailed("openai_429_insufficient_quota")).toMatch(/credit is used up/);
    expect(whyFailed("openai_401_invalid_api_key")).toMatch(/OPENAI_API_KEY/);
    expect(whyFailed("openai_429_rate_limit_exceeded")).toMatch(/clears by itself/);
    expect(whyFailed("invalid_reply")).toMatch(/checks/);
    expect(whyFailed(null)).toBe("");
  });
});
