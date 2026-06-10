/**
 * Typed surface for the OPTIONAL token theming (roadmap T305).
 *
 * The look-and-feel of every headless component can be driven by CSS custom
 * properties instead of per-slot `classNames` maps — see
 * `@viglet/turing-react-ui/styles.css`. This module exposes the token names as
 * a typed object plus a {@link TuringUiTheme} helper type, so apps that set the
 * tokens from TypeScript (e.g. an inline `style` object) get autocomplete and
 * are protected from typos:
 *
 * ```tsx
 * import "@viglet/turing-react-ui/styles.css";
 * import { type TuringUiTheme } from "@viglet/turing-react-ui";
 *
 * const brand: TuringUiTheme = {
 *   "--turing-ui-accent": "#7c3aed",
 *   "--turing-ui-radius": "1rem",
 * };
 *
 * <div className="turing-ui-theme" style={brand}>…</div>
 * ```
 *
 * The values here MUST mirror the token defaults declared in
 * `styles/turing-ui.css`; `tokens.test.ts` asserts the two never drift.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
import type { CSSProperties } from "react";

/** The CSS class an app adds to a subtree to opt into token theming. */
export const TURING_UI_THEME_CLASS = "turing-ui-theme" as const;

/**
 * Every `--turing-ui-*` custom property the stylesheet reads, grouped by intent.
 * Use the values as keys when setting tokens (the strings ARE the property
 * names) — e.g. `{ [TURING_UI_TOKENS.accent]: "#7c3aed" }`.
 */
export const TURING_UI_TOKENS = {
  /** Base font stack — inherited by every descendant. */
  font: "--turing-ui-font",
  /** Monospace stack for code / diagram source. */
  fontMono: "--turing-ui-font-mono",
  /** Base prose font size. */
  fontSize: "--turing-ui-font-size",
  /** Primary text colour. */
  text: "--turing-ui-text",
  /** Secondary / caption text colour. */
  textMuted: "--turing-ui-text-muted",
  /** Component background. */
  surface: "--turing-ui-surface",
  /** Header / `<pre>` background. */
  surfaceMuted: "--turing-ui-surface-muted",
  /** Border / divider colour. */
  border: "--turing-ui-border",
  /** Brand / interactive accent (links, avatar). */
  accent: "--turing-ui-accent",
  /** Accent hover state. */
  accentHover: "--turing-ui-accent-hover",
  /** Text colour on top of the accent. */
  onAccent: "--turing-ui-on-accent",
  /** "Copied" confirmation colour. */
  success: "--turing-ui-success",
  /** Error caption colour. */
  danger: "--turing-ui-danger",
  /** Corner radius for cards / surfaces. */
  radius: "--turing-ui-radius",
  /** Corner radius for buttons. */
  radiusSm: "--turing-ui-radius-sm",
  /** Inter-slot gap (e.g. chat message avatar↔body). */
  gap: "--turing-ui-gap",
  /** Inner padding. */
  pad: "--turing-ui-pad",
  /** Fullscreen backdrop colour. */
  overlay: "--turing-ui-overlay",
  /** Thinking-dot diameter. */
  dotSize: "--turing-ui-dot-size",
  /** Thinking-dot colour. */
  dotColor: "--turing-ui-dot-color",
  /** Thinking-dot bounce cycle duration. */
  dotDuration: "--turing-ui-dot-duration",
} as const;

/** A token name, e.g. `"--turing-ui-accent"`. */
export type TuringUiTokenName =
  (typeof TURING_UI_TOKENS)[keyof typeof TURING_UI_TOKENS];

/**
 * A typed `style`-object subset for the theme tokens. Spread it (or assign it)
 * into a `style` prop on the `.turing-ui-theme` wrapper. Every token is optional
 * — set only the ones you want to override; the rest fall back to the
 * stylesheet defaults. Extends {@link CSSProperties} so it also accepts normal
 * style properties on the same element.
 */
export type TuringUiTheme = CSSProperties &
  Partial<Record<TuringUiTokenName, string>>;
