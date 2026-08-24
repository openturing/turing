/**
 * T1027 — the theme hook, on its own.
 *
 * It reads a context and throws when there is no provider — no JSX, no state of
 * its own.
 */
import { useContext } from "react";
import { ThemeProviderContext } from "./theme-context";

export const useTheme = () => {
  const context = useContext(ThemeProviderContext);
  if (context === undefined)
    throw new Error("useTheme must be used within a ThemeProvider");
  return context;
};
