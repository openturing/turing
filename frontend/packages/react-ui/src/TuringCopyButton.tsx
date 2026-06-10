import { useEffect, useRef, useState, type ReactNode } from "react";

/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringCopyButtonClassNames {
  /** Always applied to the `<button>`. */
  button?: string;
  /** Appended while the "copied" confirmation is showing. */
  copied?: string;
  /** Appended in the idle state (before / after the confirmation). */
  idle?: string;
}

/** Human-readable strings — pass localized values from the host app. */
export interface TuringCopyButtonLabels {
  /** Accessible name + tooltip in the idle state. Default `"Copy"`. */
  copy?: string;
  /** Accessible name + tooltip while the confirmation shows. Default `"Copied"`. */
  copied?: string;
}

/**
 * Optional icon nodes for the two states. When omitted, the button shows its
 * label text instead — so the component has no icon-library dependency and the
 * host app plugs in whatever it uses (@tabler, lucide, an svg, …).
 */
export interface TuringCopyButtonIcons {
  copy?: ReactNode;
  copied?: ReactNode;
}

export interface TuringCopyButtonProps {
  /** The text written to the clipboard on click. */
  value: string;
  /** How long the "copied" confirmation stays, in ms. Default `2000`. */
  resetMs?: number;
  /** Called after a successful copy (e.g. for analytics). */
  onCopy?: (value: string) => void;
  /** Convenience alias for {@link TuringCopyButtonClassNames.button}. */
  className?: string;
  classNames?: TuringCopyButtonClassNames;
  labels?: TuringCopyButtonLabels;
  icons?: TuringCopyButtonIcons;
}

/**
 * Headless copy-to-clipboard button with a self-resetting "copied" confirmation.
 * Centralises the {@code navigator.clipboard.writeText} + transient-state idiom
 * that the admin chat list and other surfaces each re-implemented by hand.
 *
 * <p><b>Design-agnostic by construction:</b> it renders a single {@code <button>}
 * and carries no styling of its own. Skin the two states via
 * {@code className}/{@code classNames} ({@code button} is always applied,
 * {@code copied}/{@code idle} toggle with the confirmation), localize via
 * {@code labels}, and supply state {@code icons} from whatever icon set the app
 * uses. This is why the admin console and viglet.com can share one
 * implementation while keeping their distinct looks.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringCopyButton({
  value,
  resetMs = 2000,
  onCopy,
  className,
  classNames,
  labels,
  icons,
}: Readonly<TuringCopyButtonProps>) {
  const [copied, setCopied] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Clear any pending reset on unmount so we never call setState on a gone node.
  useEffect(
    () => () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    },
    [],
  );

  const copyLabel = labels?.copy ?? "Copy";
  const copiedLabel = labels?.copied ?? "Copied";
  const activeLabel = copied ? copiedLabel : copyLabel;

  const handleClick = () => {
    // Guard for non-browser / insecure-context environments where the
    // Clipboard API is unavailable; do nothing rather than throw.
    if (typeof navigator === "undefined" || !navigator.clipboard) return;
    void navigator.clipboard.writeText(value).then(() => {
      onCopy?.(value);
      setCopied(true);
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => setCopied(false), resetMs);
    });
  };

  const buttonClass = [
    className ?? classNames?.button,
    copied ? classNames?.copied : classNames?.idle,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button
      type="button"
      className={buttonClass || undefined}
      onClick={handleClick}
      aria-label={activeLabel}
      aria-live="polite"
      title={activeLabel}
      data-turing-copy-button={copied ? "copied" : "idle"}
    >
      {copied ? (icons?.copied ?? copiedLabel) : (icons?.copy ?? copyLabel)}
    </button>
  );
}
