import { render, screen } from "@testing-library/react";
import { IconSettings } from "@tabler/icons-react";
import { describe, expect, it } from "vitest";

import {
  SectionCard,
  SectionCardChromeProvider,
  useSectionChrome,
} from "@/components/ui/section-card";

/**
 * T994 — the chrome adapter is imported, not declared here.
 *
 * The adapter's own behaviour is the design system's and is covered there
 * against a fixture ("one form, two chromes", eleven cases). What is still this
 * product's to prove is the half a shim can break: that `@/components/ui/section-card`
 * resolves to the package's `AdaptiveSectionCard` under its old local name, and
 * that a form written the way this product writes them — sections composing
 * `SectionCard.Header` / `.Content` and reading `useSectionChrome` to drop
 * console-only layout — renders its fields exactly once in either chrome.
 *
 * The identity assertion is the one that would catch a bad shim silently: a
 * component that renders is not evidence it is the shared one.
 */
function TuringishSection() {
  // The pattern the real sections use: read the chrome, drop the console's
  // narrow centred column when the full-width bento shell is the host.
  const chrome = useSectionChrome();
  return (
    <div className={chrome === "bento" ? "w-full" : "mx-auto max-w-2xl"}>
      <SectionCard variant="blue">
        <SectionCard.Header icon={IconSettings} title="General" description="How it behaves" />
        <SectionCard.Content>
          <label htmlFor="site-name">Site name</label>
          <input id="site-name" defaultValue="atlas" />
        </SectionCard.Content>
      </SectionCard>
      <span data-testid="chrome">{chrome}</span>
    </div>
  );
}

describe("the product's section-card module", () => {
  it("is the design system's adaptive card under the local name", async () => {
    const { AdaptiveSectionCard } = await import("@viglet/viglet-design-system/bento");

    // Not "it renders" — that a component renders says nothing about which one.
    expect(SectionCard).toBe(AdaptiveSectionCard);
    expect(SectionCard.Header).toBe(AdaptiveSectionCard.Header);
    expect(SectionCard.StaticHeader).toBe(AdaptiveSectionCard.StaticHeader);
    expect(SectionCard.Content).toBe(AdaptiveSectionCard.Content);
  });

  it("defaults to console chrome, which is what the console pages still rely on", () => {
    render(<TuringishSection />);
    expect(screen.getByTestId("chrome").textContent).toBe("console");
    expect(screen.getAllByLabelText("Site name")).toHaveLength(1);
  });

  it("renders the same fields, once, under a bento provider", () => {
    render(
      <SectionCardChromeProvider chrome="bento">
        <TuringishSection />
      </SectionCardChromeProvider>,
    );
    expect(screen.getByTestId("chrome").textContent).toBe("bento");
    // The point of the adapter: one copy of the field logic, either chrome. A
    // second copy here would mean the form was forked rather than adapted.
    expect(screen.getAllByLabelText("Site name")).toHaveLength(1);
    expect(screen.getByText("General")).toBeInTheDocument();
  });

  it("carries the header text through both chromes, so nothing is dropped in one", () => {
    const { unmount } = render(<TuringishSection />);
    expect(screen.getByText("General")).toBeInTheDocument();
    expect(screen.getByText("How it behaves")).toBeInTheDocument();
    unmount();

    render(
      <SectionCardChromeProvider chrome="bento">
        <TuringishSection />
      </SectionCardChromeProvider>,
    );
    expect(screen.getByText("General")).toBeInTheDocument();
    expect(screen.getByText("How it behaves")).toBeInTheDocument();
  });
});
