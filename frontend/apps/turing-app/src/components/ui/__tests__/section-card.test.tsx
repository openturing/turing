import { render, screen } from "@testing-library/react";
import { IconSettings } from "@tabler/icons-react";
import { describe, expect, it } from "vitest";

import { SectionCard } from "@/components/ui/section-card";

/**
 * T994 — the section adapter is imported, not declared here.
 *
 * The adapter's own behaviour is the design system's and is covered there. What
 * is still this product's to prove is the half a shim can break: that
 * `@/components/ui/section-card` resolves to the package's
 * `AdaptiveSectionCard` under its old local name, and that a form written the
 * way this product writes them — sections composing `SectionCard.Header` /
 * `.Content` — renders its fields exactly once.
 *
 * The identity assertion is the one that would catch a bad shim silently: a
 * component that renders is not evidence it is the shared one.
 *
 * VDS146 removed the chrome provider and its hook, so the cases that rendered
 * this under each of two chromes went with them. There is one chrome, and the
 * assertions that survive are the ones that were never about which.
 */
function TuringishSection() {
  return (
    <div className="w-full">
      <SectionCard variant="blue">
        <SectionCard.Header icon={IconSettings} title="General" description="How it behaves" />
        <SectionCard.Content>
          <label htmlFor="site-name">Site name</label>
          <input id="site-name" defaultValue="atlas" />
        </SectionCard.Content>
      </SectionCard>
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

  it("renders the fields once, through the adapter rather than beside it", () => {
    render(<TuringishSection />);

    // One copy of the field logic. A second would mean the form was forked
    // rather than adapted, which is what the shim exists to prevent.
    expect(screen.getAllByLabelText("Site name")).toHaveLength(1);
  });

  it("carries the header text through, so nothing is dropped on the way", () => {
    render(<TuringishSection />);

    expect(screen.getByText("General")).toBeInTheDocument();
    expect(screen.getByText("How it behaves")).toBeInTheDocument();
  });
});
