import { act, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";

import {
  clearQuotaRefusal,
  parseQuotaRefusal,
  QUOTA_EXHAUSTED_TYPE,
  reportQuotaRefusal,
  type QuotaRefusal,
} from "@/lib/quota-refusal";

import { BentoQuotaBanner } from "../bento-quota-banner";

const TOKENS_EXHAUSTED: QuotaRefusal = {
  status: 429,
  resource: "monthlyTokens",
  used: 100_000,
  limit: 100_000,
  resetsAt: "2026-09-01T00:00:00-03:00",
  upgradeUrl: "https://viglet.cloud/upgrade?install=i&sig=abc",
  detail: "Plan limit reached for 'monthlyTokens': 100000 of 100000 used.",
};

function renderBanner() {
  return render(
    <MemoryRouter>
      <BentoQuotaBanner />
    </MemoryRouter>,
  );
}

describe("parseQuotaRefusal", () => {
  it("reads the structured refusal off a 429 problem body", () => {
    const refusal = parseQuotaRefusal(429, {
      type: QUOTA_EXHAUSTED_TYPE,
      resource: "monthlyTokens",
      used: 100_000,
      limit: 100_000,
      resetsAt: "2026-09-01T00:00:00-03:00",
      upgradeUrl: "https://viglet.cloud/upgrade",
      detail: "…",
    });

    expect(refusal).toMatchObject({ resource: "monthlyTokens", used: 100_000, status: 429 });
  });

  it("ignores a 429 that is not a plan limit", () => {
    // A provider passing its own rate limit through must not be dressed up as
    // an exhausted credit — the type URI is the gate, not the status code.
    expect(parseQuotaRefusal(429, { type: "tag:viglet.com,2026:errors/internal" })).toBeNull();
    expect(parseQuotaRefusal(500, { type: QUOTA_EXHAUSTED_TYPE })).toBeNull();
  });

  it("omits an upgrade target the server did not send", () => {
    const refusal = parseQuotaRefusal(402, {
      type: QUOTA_EXHAUSTED_TYPE,
      resource: "agents",
      used: 3,
      limit: 3,
    });

    expect(refusal?.upgradeUrl).toBeNull();
    expect(refusal?.resetsAt).toBeNull();
  });
});

describe("BentoQuotaBanner", () => {
  afterEach(() => {
    act(() => clearQuotaRefusal());
  });

  it("renders nothing until a refusal arrives", () => {
    const { container } = renderBanner();
    expect(container).toBeEmptyDOMElement();
  });

  it("states what ran out, when it resets, and offers both exits", () => {
    renderBanner();
    act(() => reportQuotaRefusal(TOKENS_EXHAUSTED));

    expect(screen.getByRole("status")).toBeInTheDocument();
    // Grouping separators are the runner's locale, not ours — compare against
    // the same formatting the component asks the platform for.
    const formatted = (100_000).toLocaleString();
    expect(screen.getByText(new RegExp(`${formatted} of ${formatted} used`))).toBeInTheDocument();
    expect(screen.getByText(/It resets on/)).toBeInTheDocument();

    const upgrade = screen.getByRole("link", { name: /Continue on a paid plan/ });
    const byok = screen.getByRole("link", { name: /Use my own API key/ });
    expect(upgrade).toHaveAttribute("href", TOKENS_EXHAUSTED.upgradeUrl);
    expect(byok).toHaveAttribute("href", "/bento/llm/instance/new");
    // Same weight, same affordance — BYOK is never the downgrade.
    expect(byok.className).toEqual(upgrade.className);
  });

  it("keeps the bring-your-own-key exit when no upgrade target was signed", () => {
    renderBanner();
    act(() =>
      reportQuotaRefusal({
        ...TOKENS_EXHAUSTED,
        status: 402,
        resource: "agents",
        used: 3,
        limit: 3,
        resetsAt: null,
        upgradeUrl: null,
      }),
    );

    expect(screen.queryByRole("link", { name: /Continue on a paid plan/ })).toBeNull();
    expect(screen.getByRole("link", { name: /Use my own API key/ })).toBeInTheDocument();
    expect(screen.getByText(/does not free up on its own/)).toBeInTheDocument();
  });

  it("can be dismissed, and comes back on the next refusal", () => {
    renderBanner();
    act(() => reportQuotaRefusal(TOKENS_EXHAUSTED));

    act(() => screen.getByRole("button", { name: /Dismiss/ }).click());
    expect(screen.queryByRole("status")).toBeNull();

    act(() => reportQuotaRefusal(TOKENS_EXHAUSTED));
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});
