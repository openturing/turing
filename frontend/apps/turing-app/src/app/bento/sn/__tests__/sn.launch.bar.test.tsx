import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import { SnLaunchBar } from "../sn.launch.bar";

/**
 * T853 / §LXIV.2 (Block BQ) — the answer tile is gated by the backend readiness
 * probe, and a closed gate names the condition to fix rather than making the
 * tile disappear.
 *
 * @since 2026.3.4
 */

const probe = vi.hoisted(() => ({ data: undefined as unknown, isLoading: false }));

vi.mock("@/api/queries/features.queries", () => ({
  useFeatures: () => ({ data: { storageEnabled: false, defaultAiAgentRagEnabled: false } }),
}));
vi.mock("@/api/queries/sn-chat-enabled.queries", () => ({
  useSnChatEnabled: () => probe,
}));

function site(): TurSNSite {
  return {
    id: "site-1",
    name: "Sample",
    turSNSiteLocales: [{ language: "en_US" }],
  } as unknown as TurSNSite;
}

describe("SnLaunchBar — the answer tile gate (T853)", () => {
  it("links to /answer/{name} when the probe reports the site can answer", () => {
    probe.data = { enabled: true, reason: "NONE" };
    probe.isLoading = false;

    render(<SnLaunchBar site={site()} />);

    expect(screen.getByText("answer.openAnswerSearch")).toBeInTheDocument();
    expect(screen.queryByText(/^answer\.disabled\./)).not.toBeInTheDocument();
  });

  it("names the vectorless condition to fix instead of hiding the tile", () => {
    probe.data = { enabled: false, reason: "MISSING_DEFAULT_LLM" };
    probe.isLoading = false;

    render(<SnLaunchBar site={site()} />);

    expect(screen.getByText("answer.openAnswerSearch")).toBeInTheDocument();
    expect(screen.getByText("answer.disabled.MISSING_DEFAULT_LLM")).toBeInTheDocument();
  });

  it("falls back to the generic hint when the probe returns no reason", () => {
    probe.data = { enabled: false };
    probe.isLoading = false;

    render(<SnLaunchBar site={site()} />);

    expect(screen.getByText("answer.disabled.UNKNOWN")).toBeInTheDocument();
  });

  it("renders no answer tile at all while the probe is still in flight", () => {
    probe.data = undefined;
    probe.isLoading = true;

    render(<SnLaunchBar site={site()} />);

    expect(screen.queryByText("answer.openAnswerSearch")).not.toBeInTheDocument();
  });
});
