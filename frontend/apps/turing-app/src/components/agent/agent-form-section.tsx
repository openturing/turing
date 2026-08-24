import type { ReactNode } from "react";
import type { Icon as TablerIcon } from "@tabler/icons-react";

import { BentoFormSection } from "@/components/bento";
import { SectionCard } from "@/components/ui/section-card";
import type { BentoTone } from "@/components/bento";

/**
 * T1021 — the chrome-aware section the seven agent forms share.
 *
 * Each of them declared this same conditional as a component **inside its own
 * render body**. That is a new component type every pass, so React unmounts and
 * remounts the whole subtree rather than updating it: on a form of checkboxes
 * and text inputs, a keystroke anywhere threw away focus and scroll position in
 * the section. Declared once, at module scope, the type is stable and the
 * subtree updates.
 *
 * The design system already ships the contextual version of this idea,
 * `AdaptiveSectionCard`, which reads the chrome off a
 * `SectionCardChromeProvider`. These forms take `chrome` as a prop and their
 * callers set no provider, so adopting it is a separate change to the plumbing
 * rather than a rename — that is where this should end up, not where it can go
 * today.
 */
export interface AgentFormSectionProps {
  /** True under the bento shell; false renders the console card. */
  bento: boolean;
  icon: TablerIcon;
  /** Bento tone for the frosted section's icon chip. */
  tone: BentoTone;
  /** Console `SectionCard` colour. It is not always the same name as `tone`. */
  variant: React.ComponentProps<typeof SectionCard>["variant"];
  title: string;
  description?: string;
  children: ReactNode;
}

export function AgentFormSection({
  bento,
  icon,
  tone,
  variant,
  title,
  description,
  children,
}: Readonly<AgentFormSectionProps>) {
  if (bento) {
    return (
      <BentoFormSection icon={icon} tone={tone} title={title} description={description}>
        {children}
      </BentoFormSection>
    );
  }
  return (
    <SectionCard variant={variant}>
      <SectionCard.Header icon={icon} title={title} description={description} />
      <SectionCard.Content>{children}</SectionCard.Content>
    </SectionCard>
  );
}
