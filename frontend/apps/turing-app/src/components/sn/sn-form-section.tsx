"use client"
import { BentoFormSection } from "@/components/bento"
import type { BentoTone } from "@/components/bento/bento-tones"
import type { ComponentType, ReactNode } from "react"

/**
 * Tone tokens accepted by an SN form section — the `SectionCard` `ColorVariant`
 * set this wrapper was written against. `cyan`/`orange` have no `BentoTone`
 * equivalent and are mapped down.
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
    icon: ComponentType<{ size?: number }>;
    tone: SNFormTone;
    title: string;
    description?: string;
    children: ReactNode;
}

/**
 * Section wrapper shared by the SN sub-section forms (T576).
 *
 * T1000 — it used to take a `chrome` prop and render either the console
 * `SectionCard` or the frosted `BentoFormSection`. Measured before deleting:
 * across the whole app, 46 call sites passed `chrome="bento"` and none passed
 * `chrome="console"`, so the console arm was reachable only by omitting the prop
 * — which no caller did. A branch nothing takes is not a seam; it is a second
 * implementation nobody renders and everybody has to read.
 */
export function SNFormSection({ icon: Icon, tone, title, description, children }: Readonly<Props>) {
    return (
        <BentoFormSection icon={Icon} tone={TO_BENTO_TONE[tone]} title={title} description={description}>
            {children}
        </BentoFormSection>
    );
}
