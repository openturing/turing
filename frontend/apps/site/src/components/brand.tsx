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

/** GitHub mark (lucide dropped brand icons, so inline it). */
export function GitHubIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
    </svg>
  );
}

export const REPO = "openviglet/turing-ce";

export const LINKS = {
  docs: "https://docs.viglet.org/turing/",
  // Connectors ship in the Dumont project — the AEM connector docs live under
  // the /dumont route (docs-dumont/connectors/aem.md), not under /turing.
  aemConnector: "https://docs.viglet.org/dumont/connectors/aem",
  github: "https://github.com/openviglet/turing-ce",
  discussions: "https://github.com/openviglet/turing-ce/discussions",
  issues: "https://github.com/openviglet/turing-ce/issues",
  // GitHub renders these markdown files; blob/HEAD follows the default branch.
  roadmap: "https://github.com/openviglet/turing-ce/blob/HEAD/docs/ROADMAP.md",
  changelog: "https://github.com/openviglet/turing-ce/blob/HEAD/docs/CHANGELOG.md",
  npm: "https://www.npmjs.com/package/@viglet/turing-react-sdk",
  cli: "https://www.npmjs.com/package/@viglet/turing-cli",
  storybook: "/react-sdk/",
  viglet: "https://www.viglet.org",
} as const;
