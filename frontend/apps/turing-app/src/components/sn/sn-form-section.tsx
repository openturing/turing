"use client"
import { BentoFormSection } from "@/components/bento"
import type { BentoTone } from "@/components/bento/bento-tones"
import { SectionCard } from "@/components/ui/section-card"
import type { ComponentType, ReactNode } from "react"

export type SNFormChrome = "console" | "bento";

/**
 * Tone tokens accepted by an SN form section — the full `SectionCard`
 * `ColorVariant` set. `cyan`/`orange` have no `BentoTone` equivalent, so they
 * are mapped down for the bento branch.
 */
export type SNFormTone =
    | "blue" | "violet" | "emerald" | "amber" | "rose" | "slate" | "cyan" | "orange";

const TO_BENTO_TONE: Record<SNFormTone, BentoTone> = {
    blue: "blue",
    violet: "violet",
    emerald: "emerald",
    amber: "amber",
    rose: "rose",
    slate: "slate",
    cyan: "blue",
    orange: "amber",
};

interface Props {
    chrome: SNFormChrome;
    icon: ComponentType<{ size?: number }>;
    tone: SNFormTone;
    title: string;
    description?: string;
    /** Console-only: collapsible default state. Ignored in bento. */
    defaultOpen?: boolean;
    children: ReactNode;
}

/**
 * Chrome-aware section wrapper shared by the SN sub-section forms (T576).
 * Renders the console `SectionCard` (collapsible, tonal ring) or the frosted
 * `BentoFormSection` (hover-lift + `bento-glass`) depending on `chrome`, so the
 * same field bodies serve both surfaces without forking. Console is the default;
 * the Bento pages pass `chrome="bento"`.
 */
export function SNFormSection({ chrome, icon: Icon, tone, title, description, defaultOpen, children }: Readonly<Props>) {
    if (chrome === "bento") {
        return (
            <BentoFormSection icon={Icon} tone={TO_BENTO_TONE[tone]} title={title} description={description}>
                {children}
            </BentoFormSection>
        );
    }
    return (
        <SectionCard variant={tone} defaultOpen={defaultOpen}>
            <SectionCard.Header icon={Icon} title={title} description={description} />
            <SectionCard.Content>{children}</SectionCard.Content>
        </SectionCard>
    );
}
