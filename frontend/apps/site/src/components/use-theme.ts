/**
 * T1027 — the theme hook, on its own.
 *
 * It reads a context and throws when there is no provider — no JSX, no state
 * of its own. It sat beside the provider, and a module exporting both a
 * component and a hook reloads whole instead of preserving state, which for a
 * theme provider means every consumer below it remounts on an edit.
 */
import { useContext } from "react";
import { ThemeProviderContext } from "./theme-context";

export const useTheme = () => {
  const context = useContext(ThemeProviderContext);
  if (context === undefined)
    throw new Error("useTheme must be used within a ThemeProvider");
  return context;
};
