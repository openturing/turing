import type { ComponentType, ReactNode } from "react";
import { BENTO_TONE_GRADIENTS, type BentoTone } from "./bento-tones";

export interface BentoFormSectionProps {
  icon: ComponentType<{ size?: number }>;
  tone: BentoTone;
  title: string;
  description?: string;
  children: ReactNode;
  /** Optional trailing slot in the header (badges, status pill, etc.). */
  trailing?: ReactNode;
}

/**
 * Frosted-glass section wrapper for Bento forms — the form-page
 * counterpart of `BentoTile`. Groups related fields under a tonal
 * icon chip + title + description, with the same iOS-spring entry
 * and `bento-glass` surface used elsewhere in the bento language.
 *
 * Stays content-agnostic: any react-hook-form field combo can sit
 * inside `children`, so existing FormField/Input/Select/Switch
 * components are reused without modification.
 */
export function BentoFormSection({
  icon: Icon,
  tone,
  title,
  description,
  children,
  trailing,
}: Readonly<BentoFormSectionProps>) {
  return (
    <section className="bento-tile bento-glass relative flex flex-col gap-5 overflow-hidden rounded-3xl p-5 md:p-6">
      <header className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className={`grid h-10 w-10 place-items-center rounded-2xl bg-linear-to-br ${BENTO_TONE_GRADIENTS[tone]} text-white shadow-md`}>
            <Icon size={20} />
          </span>
          <div className="flex flex-col gap-0.5">
            <h3 className="text-base font-semibold tracking-tight md:text-lg">{title}</h3>
            {description && (
              <p className="text-sm text-muted-foreground">{description}</p>
            )}
          </div>
        </div>
        {trailing && <div className="shrink-0">{trailing}</div>}
      </header>
      <div className="flex flex-col gap-5">{children}</div>
    </section>
  );
}
