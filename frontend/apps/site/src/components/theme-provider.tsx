import { ThemeProviderContext, type Theme } from "./theme-context";
import { useEffect, useState } from "react";


interface ThemeProviderProps {
  children: React.ReactNode;
  defaultTheme?: Theme;
  storageKey?: string;
}



export function ThemeProvider({
  children,
  defaultTheme = "system",
  storageKey = "turing-site-theme",
  ...props
}: ThemeProviderProps) {
  // SSR-safe: during prerender there is no `localStorage`. Both server and the
  // client's first render start from `defaultTheme` (the inline boot script in
  // index.html already applied the stored class to <html>, so there is no
  // flash), then this effect reconciles state from storage on the client.
  const [theme, setTheme] = useState<Theme>(defaultTheme);

  useEffect(() => {
    const stored = localStorage.getItem(storageKey) as Theme | null;
    // T1027: the one place this app has to reconcile with something React
    // cannot see: `localStorage` does not exist during the SSG prerender, so
    // both renders start from `defaultTheme` and this effect corrects the
    // client. The inline boot script in index.html already painted the right
    // class, so there is no flash to see.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (stored && stored !== theme) setTheme(stored);
    // run once on mount to hydrate from storage
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const root = window.document.documentElement;
    root.classList.remove("light", "dark");

    if (theme === "system") {
      const systemTheme = window.matchMedia("(prefers-color-scheme: dark)")
        .matches
        ? "dark"
        : "light";
      root.classList.add(systemTheme);
      return;
    }

    root.classList.add(theme);
  }, [theme]);

  const value = {
    theme,
    setTheme: (theme: Theme) => {
      localStorage.setItem(storageKey, theme);
      setTheme(theme);
    },
  };

  return (
    <ThemeProviderContext.Provider {...props} value={value}>
      {children}
    </ThemeProviderContext.Provider>
  );
}
