/**
 * Atlas Store brand skin for the headless `@viglet/turing-react-ui` components
 * (roadmap T305 token theming).
 *
 * Import `@viglet/turing-react-ui/styles.css` once, then wrap any react-ui
 * subtree in `<div className={ATLAS_UI_THEME_CLASS} style={atlasUiTheme}>` to
 * retheme every headless component (chat message, source chips, search bar,
 * rich content, …) to the Atlas palette without per-slot `classNames` maps.
 *
 * The values mirror the brand tokens in `src/index.css` so the headless
 * components blend seamlessly with the app's own shadcn surfaces.
 */
import { TURING_UI_THEME_CLASS, type TuringUiTheme } from "@viglet/turing-react-ui";

export const ATLAS_UI_THEME_CLASS = TURING_UI_THEME_CLASS;

/** Atlas Store light theme tokens for `@viglet/turing-react-ui`. */
export const atlasUiTheme: TuringUiTheme = {
  "--turing-ui-font":
    'ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif',
  "--turing-ui-text": "#1e1b2e",
  "--turing-ui-text-muted": "#6b7280",
  "--turing-ui-surface": "#ffffff",
  "--turing-ui-surface-muted": "#f6f6fb",
  "--turing-ui-border": "#e4e4ef",
  "--turing-ui-accent": "#4f46e5",
  "--turing-ui-accent-hover": "#4338ca",
  "--turing-ui-on-accent": "#ffffff",
  "--turing-ui-success": "#059669",
  "--turing-ui-danger": "#dc2626",
  "--turing-ui-radius": "0.75rem",
  "--turing-ui-radius-sm": "0.5rem",
};

/** Atlas Store dark theme token overrides (merge over {@link atlasUiTheme}). */
export const atlasUiThemeDark: TuringUiTheme = {
  ...atlasUiTheme,
  "--turing-ui-text": "#ece9f5",
  "--turing-ui-text-muted": "#a1a1b5",
  "--turing-ui-surface": "#221f33",
  "--turing-ui-surface-muted": "#1b1828",
  "--turing-ui-border": "#332f47",
  "--turing-ui-accent": "#8b85f0",
  "--turing-ui-accent-hover": "#a39ef4",
};
