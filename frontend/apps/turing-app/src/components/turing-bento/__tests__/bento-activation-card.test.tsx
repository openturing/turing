import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { BentoActivationCard } from "../bento-activation-card";
import { BentoNoLlmState } from "../bento-no-llm-state";

// The dialog pulls the TurLogo, which pulls the design system; stub it out.
vi.mock("@/components/logo/tur-logo", () => ({
  TurLogo: () => <span data-testid="tur-logo" />,
}));

// `vi.mock` factories are hoisted above the module body, so the spies they
// close over have to be hoisted with them.
const { status, start, poll } = vi.hoisted(() => ({
  status: vi.fn(),
  start: vi.fn(),
  poll: vi.fn(),
}));

vi.mock("@/services/genai/viglet-ai.service", () => ({
  TurVigletAiService: class {
    status = status;
    start = start;
    poll = poll;
  },
}));

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("BentoActivationCard", () => {
  beforeEach(() => {
    status.mockReset();
    start.mockReset();
    poll.mockReset();
  });

  it("offers both ways in, neither styled as the recommendation", () => {
    renderWithProviders(<BentoActivationCard />);

    const managed = screen.getByRole("button", { name: /Activate Viglet AI/ });
    const byok = screen.getByRole("link", { name: /Use my own API key/ });
    expect(managed).toBeInTheDocument();
    expect(byok).toBeInTheDocument();
    // Equal prominence is the point (§XIV.10.3): same tile classes on both.
    expect(managed.className).toContain("bento-tile-clickable");
    expect(byok.className).toContain("bento-tile-clickable");
  });

  it("keeps BYOK pointing inside bento, never back at the console", () => {
    renderWithProviders(<BentoActivationCard />);
    expect(screen.getByRole("link", { name: /Use my own API key/ })).toHaveAttribute(
      "href",
      "/bento/llm/instance/new",
    );
  });

  it("states where prompts go, beside the decision rather than in a footer", () => {
    renderWithProviders(<BentoActivationCard />);
    expect(screen.getByText(/prompts and retrieved content leave this install/)).toBeInTheDocument();
  });

  it("drops the headline in the compact variant embedded by a surface", () => {
    renderWithProviders(<BentoActivationCard variant="compact" />);
    expect(screen.queryByText("Turn on AI")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Activate Viglet AI/ })).toBeInTheDocument();
  });

  it("starts a pairing attempt and shows the code to read out", async () => {
    start.mockResolvedValue({
      deviceCode: "dev-123",
      userCode: "ABCD-EFGH",
      verificationUri: "https://viglet.cloud/activate",
      verificationUriComplete: null,
      intervalSeconds: 60,
      expiresInSeconds: 900,
    });
    poll.mockResolvedValue({ state: "PENDING", llmInstanceId: null, defaultLlmSet: false });

    renderWithProviders(<BentoActivationCard />);
    fireEvent.click(screen.getByRole("button", { name: /Activate Viglet AI/ }));

    await waitFor(() => expect(screen.getByText("ABCD-EFGH")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: /Open the approval page/ })).toHaveAttribute(
      "href",
      "https://viglet.cloud/activate",
    );
    // The opaque poll handle is never displayed.
    expect(screen.queryByText("dev-123")).not.toBeInTheDocument();
  });

  it("reports a failed start instead of waiting forever", async () => {
    start.mockRejectedValue(new Error("offline"));

    renderWithProviders(<BentoActivationCard />);
    fireEvent.click(screen.getByRole("button", { name: /Activate Viglet AI/ }));

    await waitFor(() =>
      expect(
        screen.getByText("Could not reach Viglet AI to start the activation."),
      ).toBeInTheDocument(),
    );
  });
});

describe("BentoNoLlmState", () => {
  it("says what is missing and offers the same two paths", () => {
    renderWithProviders(<BentoNoLlmState header={<h1>Chat</h1>} />);

    expect(screen.getByText("Chat")).toBeInTheDocument();
    expect(screen.getByText("This needs a language model")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Activate Viglet AI/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Use my own API key/ })).toBeInTheDocument();
  });

  it("lets a surface say what it in particular needs a model for", () => {
    renderWithProviders(<BentoNoLlmState description="Agents can be authored now." />);
    expect(screen.getByText("Agents can be authored now.")).toBeInTheDocument();
  });
});
