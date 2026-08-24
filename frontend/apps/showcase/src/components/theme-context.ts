/**
 * T1027 — the theme context, on its own.
 *
 * The provider renders and the hook reads; a context object is neither, and a
 * module that exports one alongside a component reloads whole. The linter asks
 * for exactly this split, and it is right to.
 */
import { createContext } from "react";

export type Theme = "dark" | "light" | "system";

export interface ThemeProviderState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

const initialState: ThemeProviderState = {
  theme: "system",
  setTheme: () => null,
};

export const ThemeProviderContext = createContext<ThemeProviderState>(initialState);
