/**
 * T1027 — the theme context, on its own.
 *
 * The provider renders and the hook reads; a context object is neither, and a
 * module exporting one beside a component reloads whole instead of preserving
 * state.
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

export const ThemeProviderContext =
  createContext<ThemeProviderState>(initialState);
