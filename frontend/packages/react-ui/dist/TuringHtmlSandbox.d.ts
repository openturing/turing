import { type ReactNode } from "react";
/**
 * Per-slot class names. The component ships ZERO visual styling of its own
 * (only functional layout on the iframe), so each app skins it to match its
 * design system by passing Tailwind classes, CSS-module names, etc.
 */
export interface TuringHtmlSandboxClassNames {
    root?: string;
    header?: string;
    label?: string;
    actions?: string;
    button?: string;
    iframe?: string;
    fullscreenRoot?: string;
    fullscreenHeader?: string;
    fullscreenLabel?: string;
    fullscreenButton?: string;
    fullscreenIframe?: string;
    code?: string;
}
/** Human-readable strings — pass localized values from the host app. */
export interface TuringHtmlSandboxLabels {
    /** Header title. Default `"Preview"`. */
    title?: string;
    /** Accessible name for the fullscreen button. Default `"Fullscreen"`. */
    fullscreen?: string;
    /** Accessible name for the close (fullscreen) button. Default `"Close"`. */
    close?: string;
    /** Toggle button text when showing the preview (click → code). Default `"Code"`. */
    showCode?: string;
    /** Toggle button text when showing the code (click → preview). Default `"Preview"`. */
    showPreview?: string;
}
/**
 * Optional icon nodes for the toolbar buttons. When omitted, the button shows
 * its label text instead — so the component has no icon-library dependency and
 * the host app plugs in whatever it uses (@tabler, lucide, an svg, …).
 */
export interface TuringHtmlSandboxIcons {
    fullscreen?: ReactNode;
    close?: ReactNode;
    code?: ReactNode;
    preview?: ReactNode;
}
export interface TuringHtmlSandboxProps {
    /** Self-contained HTML fragment (no `<html>`/`<body>` needed). */
    code: string;
    /** Inline preview height in px. Default `320`. */
    height?: number;
    /** Show a Code/Preview source toggle in the toolbar. Default `false`. */
    showCodeToggle?: boolean;
    /** Convenience alias for {@link TuringHtmlSandboxClassNames.root}. */
    className?: string;
    classNames?: TuringHtmlSandboxClassNames;
    labels?: TuringHtmlSandboxLabels;
    icons?: TuringHtmlSandboxIcons;
}
/**
 * Headless live-preview for an assistant ```html block — UI demos, animations,
 * and games (a `<canvas>` Pong). The fragment runs inside a sandboxed iframe
 * with `allow-scripts` (its JS executes) but WITHOUT `allow-same-origin`, so it
 * has no access to the host page. A fullscreen affordance (Esc to close) and an
 * optional code/preview toggle are built in.
 *
 * <p><b>Design-agnostic by construction:</b> the component renders structure
 * only — it carries no colors, borders, or spacing of its own (just the
 * iframe's functional layout). Skin it via {@link TuringHtmlSandboxProps.classNames}
 * / {@link TuringHtmlSandboxProps.className}, localize via {@code labels}, and
 * supply toolbar {@code icons} from whatever icon set the app uses. This is why
 * the Turing admin console and viglet.com can share one implementation while
 * keeping their distinct looks.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export declare function TuringHtmlSandbox({ code, height, showCodeToggle, className, classNames, labels, icons, }: Readonly<TuringHtmlSandboxProps>): import("react").JSX.Element;
//# sourceMappingURL=TuringHtmlSandbox.d.ts.map