import { describe, expect, it } from "vitest";
import { buildSnSearchLaunchers, type SnLaunchSite } from "../sn-search-launchers";

/**
 * T853 / §LXIV.2 (Block BQ) — the third launcher and its gate. The answer tile's
 * gate is the BACKEND readiness probe (`answerEnabled`), which is why a
 * vectorless site — one that is never ANN-ready by design — still gets the AI
 * lens.
 *
 * @since 2026.3.4
 */

const t = (key: string) => key;

function site(overrides: Partial<SnLaunchSite> = {}): SnLaunchSite {
  return {
    name: "Sample",
    annEnabled: false,
    answerEnabled: false,
    locales: [{ language: "en_US" }],
    ...overrides,
  };
}

describe("buildSnSearchLaunchers — the answer launcher (T853)", () => {
  it("is null when the readiness probe says the site cannot answer", () => {
    expect(buildSnSearchLaunchers(site(), t).answer).toBeNull();
  });

  it("points at /answer/{name} when the probe says the site can answer", () => {
    const { answer } = buildSnSearchLaunchers(site({ answerEnabled: true }), t);
    expect(answer).not.toBeNull();
    expect(answer?.url).toBe("/answer/Sample");
    expect(answer?.label).toBe("answer.openAnswerSearch");
  });

  it("gets the AI lens on a vectorless site, which is never ANN-ready", () => {
    const { ann, answer } = buildSnSearchLaunchers(
      site({ annEnabled: false, answerEnabled: true }),
      t,
    );
    expect(ann).toBeNull();
    expect(answer?.url).toBe("/answer/Sample");
  });

  it("carries a per-locale variant for every site locale, like the other two", () => {
    const { answer } = buildSnSearchLaunchers(
      site({
        answerEnabled: true,
        locales: [{ language: "en_US" }, { language: "pt_BR" }],
      }),
      t,
    );
    expect(answer?.items).toEqual([
      { label: "en_US", url: "/answer/Sample?_setlocale=en_US" },
      { label: "pt_BR", url: "/answer/Sample?_setlocale=pt_BR" },
    ]);
  });

  it("leaves the keyword and ANN launchers untouched", () => {
    const { search, ann } = buildSnSearchLaunchers(
      site({ annEnabled: true, answerEnabled: true }),
      t,
    );
    expect(search.url).toBe("/sn/Sample");
    expect(ann?.url).toBe("/ann/Sample");
  });
});

/**
 * T891 / §LXIV.7 — the operator's labeled way into the keyword lens with the
 * switcher, kept off the base URL because the base is what a visitor is handed.
 */
describe("buildSnSearchLaunchers — the lens-switcher variant (T891)", () => {
  it("offers the opt-in as an item, never as the base URL", () => {
    const { search } = buildSnSearchLaunchers(site(), t);
    expect(search.url).toBe("/sn/Sample");
    expect(search.items).toContainEqual({
      label: "answer.openSearchWithLens",
      url: "/sn/Sample?_lens=1",
    });
  });

  it("appends the opt-in to a site that already has other search variants", () => {
    const { search } = buildSnSearchLaunchers(
      site({ storageEnabled: true, searchTemplate: "atlas" }),
      t,
    );
    expect(search.items.at(-1)).toEqual({
      label: "answer.openSearchWithLens",
      url: "/sn/Sample?_lens=1",
    });
    // The pre-existing Default Search variant survives ahead of it.
    expect(search.items[0].url).toBe("/sn/Sample?_embedded=true");
  });
});
