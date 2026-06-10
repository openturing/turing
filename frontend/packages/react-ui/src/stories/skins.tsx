/**
 * The two demo skins shared by every story, plus a `SkinShowcase` helper that
 * renders any headless component side-by-side in both. This is the "living
 * design contract": one implementation, two looks, proven by passing different
 * `classNames` / `labels` / `icons` maps — never by changing the component.
 *
 * `shadcn` mirrors the Turing admin console (neutral zinc); `viglet` mirrors
 * viglet.com (blue→indigo brand gradient). The actual rules live in
 * `skins.css`. A real host would pass its own literal Tailwind / CSS-module
 * classes instead — the library ships no styling.
 */
import type { ReactNode } from "react";
import type { TuringHtmlSandboxClassNames } from "../TuringHtmlSandbox";
import type { TuringCopyButtonClassNames } from "../TuringCopyButton";
import type { TuringThinkingDotsClassNames } from "../TuringThinkingDots";
import type { TuringCodeBlockClassNames } from "../TuringCodeBlock";
import type { TuringD2DiagramClassNames } from "../TuringD2Diagram";
import type { TuringChatMessageClassNames } from "../TuringChatMessage";

export type SkinName = "shadcn" | "viglet";

export const SKIN_CAPTION: Record<SkinName, string> = {
  shadcn: "shadcn · admin console",
  viglet: "viglet brand · viglet.com",
};

/* ───────────────────────── per-component class maps ───────────────────────── */

export const htmlSandboxClassNames: Record<
  SkinName,
  TuringHtmlSandboxClassNames
> = {
  shadcn: {
    root: "shadcn-surface",
    header: "shadcn-bar",
    label: "shadcn-eyebrow",
    actions: "shadcn-actions",
    button: "shadcn-btn",
    iframe: "shadcn-iframe",
    code: "shadcn-pre",
    fullscreenRoot: "sb-fullscreen-root",
    fullscreenHeader: "sb-fullscreen-header",
    fullscreenLabel: "sb-fullscreen-label",
    fullscreenButton: "sb-fullscreen-btn",
    fullscreenIframe: "shadcn-iframe",
  },
  viglet: {
    root: "viglet-surface",
    header: "viglet-bar",
    label: "viglet-eyebrow",
    actions: "viglet-actions",
    button: "viglet-btn",
    iframe: "viglet-iframe",
    code: "viglet-pre",
    fullscreenRoot: "sb-fullscreen-root",
    fullscreenHeader: "sb-fullscreen-header",
    fullscreenLabel: "sb-fullscreen-label",
    fullscreenButton: "sb-fullscreen-btn",
    fullscreenIframe: "viglet-iframe",
  },
};

export const copyButtonClassNames: Record<
  SkinName,
  TuringCopyButtonClassNames
> = {
  shadcn: { button: "shadcn-btn" },
  viglet: { button: "viglet-btn-solo" },
};

export const thinkingDotsClassNames: Record<
  SkinName,
  TuringThinkingDotsClassNames
> = {
  shadcn: { root: "shadcn-dots", dot: "shadcn-dot" },
  viglet: { root: "viglet-dots", dot: "viglet-dot" },
};

export const codeBlockClassNames: Record<SkinName, TuringCodeBlockClassNames> = {
  shadcn: {
    root: "shadcn-code",
    header: "shadcn-bar",
    language: "shadcn-code-lang",
    pre: "shadcn-code-pre",
    copyButton: { button: "shadcn-btn" },
  },
  viglet: {
    root: "viglet-code",
    header: "viglet-bar",
    language: "viglet-code-lang",
    pre: "viglet-code-pre",
    copyButton: { button: "viglet-btn" },
  },
};

export const d2ClassNames: Record<SkinName, TuringD2DiagramClassNames> = {
  shadcn: {
    root: "shadcn-d2",
    loading: "shadcn-d2-loading",
    error: "shadcn-d2-error",
    code: "shadcn-pre",
  },
  viglet: {
    root: "viglet-d2",
    loading: "viglet-d2-loading",
    error: "viglet-d2-error",
    code: "viglet-pre",
  },
};

export const chatMessageClassNames: Record<
  SkinName,
  TuringChatMessageClassNames
> = {
  shadcn: {
    root: "shadcn-msg",
    avatar: "shadcn-msg-avatar",
    body: "shadcn-msg-body",
    header: "shadcn-msg-header",
    name: "shadcn-msg-name",
    status: "shadcn-msg-status",
    content: "shadcn-prose",
    actions: "shadcn-msg-actions",
    footer: "shadcn-msg-footer",
  },
  viglet: {
    root: "viglet-msg",
    avatar: "viglet-msg-avatar",
    body: "viglet-msg-body",
    header: "viglet-msg-header",
    name: "viglet-msg-name",
    status: "viglet-msg-status",
    content: "viglet-prose",
    actions: "viglet-msg-actions",
    footer: "viglet-msg-footer",
  },
};

/** Prose wrapper class for TuringMarkdown / TuringRichContent. */
export const proseClassName: Record<SkinName, string> = {
  shadcn: "shadcn-prose",
  viglet: "viglet-prose",
};

export const chipClassName: Record<SkinName, string> = {
  shadcn: "shadcn-chip",
  viglet: "viglet-chip",
};

/* ───────────────────────── showcase helper ───────────────────────── */

export interface SkinShowcaseProps {
  /** Renders the component under test, dressed in the given skin. */
  children: (skin: SkinName) => ReactNode;
}

/**
 * Renders the same headless component twice — once per skin — in a labeled,
 * responsive two-column grid. Every story uses this so the "two skins, one
 * implementation" contract is visible at a glance.
 */
export function SkinShowcase({ children }: Readonly<SkinShowcaseProps>) {
  const skins: SkinName[] = ["shadcn", "viglet"];
  return (
    <div className="sb-skin-grid">
      {skins.map((skin) => (
        <div key={skin} className="sb-skin-panel">
          <span className="sb-skin-caption" data-skin={skin}>
            {SKIN_CAPTION[skin]}
          </span>
          {children(skin)}
        </div>
      ))}
    </div>
  );
}
