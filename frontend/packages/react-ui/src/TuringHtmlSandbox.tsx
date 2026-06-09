import {
  useEffect,
  useState,
  type CSSProperties,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";

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
export function TuringHtmlSandbox({
  code,
  height = 320,
  showCodeToggle = false,
  className,
  classNames,
  labels,
  icons,
}: Readonly<TuringHtmlSandboxProps>) {
  const [fullscreen, setFullscreen] = useState(false);
  const [showCode, setShowCode] = useState(false);

  const title = labels?.title ?? "Preview";
  const fullscreenLabel = labels?.fullscreen ?? "Fullscreen";
  const closeLabel = labels?.close ?? "Close";
  const codeLabel = labels?.showCode ?? "Code";
  const previewLabel = labels?.showPreview ?? "Preview";

  const iframeStyle: CSSProperties = {
    width: "100%",
    height,
    border: 0,
    display: "block",
    background: "#fff",
  };

  return (
    <>
      <div className={className ?? classNames?.root} data-turing-html-sandbox="">
        <div className={classNames?.header}>
          <span className={classNames?.label}>{title}</span>
          <span className={classNames?.actions}>
            {showCodeToggle && (
              <button
                type="button"
                className={classNames?.button}
                onClick={() => setShowCode((v) => !v)}
              >
                {showCode
                  ? (icons?.preview ?? previewLabel)
                  : (icons?.code ?? codeLabel)}
              </button>
            )}
            <button
              type="button"
              className={classNames?.button}
              aria-label={fullscreenLabel}
              title={fullscreenLabel}
              onClick={() => setFullscreen(true)}
            >
              {icons?.fullscreen ?? fullscreenLabel}
            </button>
          </span>
        </div>
        {showCode ? (
          <pre className={classNames?.code}>
            <code>{code}</code>
          </pre>
        ) : (
          <iframe
            srcDoc={buildSrcDoc(code)}
            sandbox="allow-scripts"
            className={classNames?.iframe}
            style={iframeStyle}
            title={title}
          />
        )}
      </div>
      {fullscreen && (
        <TuringHtmlSandboxFullscreen
          code={code}
          title={title}
          closeLabel={closeLabel}
          closeIcon={icons?.close}
          classNames={classNames}
          onClose={() => setFullscreen(false)}
        />
      )}
    </>
  );
}

function TuringHtmlSandboxFullscreen({
  code,
  title,
  closeLabel,
  closeIcon,
  classNames,
  onClose,
}: Readonly<{
  code: string;
  title: string;
  closeLabel: string;
  closeIcon?: ReactNode;
  classNames?: TuringHtmlSandboxClassNames;
  onClose: () => void;
}>) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  // SSR guard: the portal target only exists in the browser. Fullscreen is
  // only ever entered from a click, so this is browser-side in practice.
  if (typeof document === "undefined") return null;

  return createPortal(
    <div className={classNames?.fullscreenRoot}>
      <div className={classNames?.fullscreenHeader}>
        <span className={classNames?.fullscreenLabel}>{title}</span>
        <button
          type="button"
          className={classNames?.fullscreenButton}
          aria-label={closeLabel}
          title={closeLabel}
          onClick={onClose}
        >
          {closeIcon ?? closeLabel}
        </button>
      </div>
      <iframe
        srcDoc={buildSrcDoc(code)}
        sandbox="allow-scripts"
        className={classNames?.fullscreenIframe}
        style={{ flex: 1, width: "100%", border: 0, background: "#fff" }}
        title={`${title} (fullscreen)`}
      />
    </div>,
    document.body,
  );
}

/** Wraps a fragment into a minimal self-contained document for the iframe. */
function buildSrcDoc(html: string): string {
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><style>html,body{margin:0;padding:8px;font-family:system-ui,sans-serif;box-sizing:border-box;}</style></head><body>${html}</body></html>`;
}
