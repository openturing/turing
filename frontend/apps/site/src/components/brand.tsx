import * as React from "react";
import { cn } from "@/lib/utils";
import { VigletLogo } from "@/components/viglet-logo";

/** Original Viglet "Tu" brand mark + wordmark. */
export function Logo({
  className,
  showWord = true,
  size = 32,
}: {
  className?: string;
  showWord?: boolean;
  size?: number;
}) {
  return (
    <span className={cn("inline-flex items-center gap-2 font-extrabold", className)}>
      <VigletLogo identifier="turing" size={size} />
      {showWord && <span>Viglet Turing ES</span>}
    </span>
  );
}

/** Centered max-width container. */
export function Container({
  className,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      className={cn("mx-auto w-full max-w-6xl px-6", className)}
      {...props}
    />
  );
}

/**
 * Horizontally-scrollable table wrapper for mobile. Adds a right-edge fade that
 * hints "more columns →" (shown only on small screens, where the comparison
 * tables scroll). Pair it with a `sticky left-0` first column so the row label
 * stays visible while the value columns scroll.
 */
export function TableScroll({
  className,
  fadeClassName = "from-background",
  children,
}: {
  className?: string;
  fadeClassName?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="relative">
      <div className={cn("overflow-x-auto", className)}>{children}</div>
      <div
        aria-hidden
        className={cn(
          "pointer-events-none absolute inset-y-0 right-0 w-8 bg-gradient-to-l to-transparent sm:hidden",
          fadeClassName
        )}
      />
    </div>
  );
}

/** Gradient eyebrow / kicker text above a heading. */
export function Eyebrow({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-block bg-gradient-to-r from-[#4169E1] to-[#818cf8] bg-clip-text text-xs font-extrabold tracking-[0.1em] text-transparent uppercase">
      {children}
    </span>
  );
}

/** Centered section header: eyebrow + title + description. */
export function SectionHeader({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: React.ReactNode;
  description?: React.ReactNode;
}) {
  return (
    <div className="mx-auto mb-12 max-w-2xl text-center">
      <Eyebrow>{eyebrow}</Eyebrow>
      <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
        {title}
      </h2>
      {description && (
        <p className="mt-3 text-base leading-relaxed text-muted-foreground sm:text-lg">
          {description}
        </p>
      )}
    </div>
  );
}

export const LINKS = {
  docs: "https://docs.viglet.org/turing/",
  // Connectors ship in the Dumont project — the AEM connector docs live under
  // the /dumont route (docs-dumont/connectors/aem.md), not under /turing.
  aemConnector: "https://docs.viglet.org/dumont/connectors/aem",
  // Roadmap/Changelog are rendered markdown (blob/HEAD follows the default branch).
  roadmap: "https://github.com/openviglet/turing-ce/blob/HEAD/docs/ROADMAP.md",
  // User-facing release notes render the single-source changelog.json this site
  // owns and serves (public/changelog.json → turing.viglet.org/changelog.json).
  // The dev-facing ledger lives in docs/CHANGELOG.md (linked as Roadmap above).
  changelog: "https://www.viglet.org/turing/release-notes/",
  npm: "https://www.npmjs.com/package/@viglet/turing-react-sdk",
  cli: "https://www.npmjs.com/package/@viglet/turing-cli",
  storybook: "/react-sdk/",
  viglet: "https://www.viglet.org",
} as const;
