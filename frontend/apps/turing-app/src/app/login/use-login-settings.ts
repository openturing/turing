/**
 * T1024 — the login animation preferences, on their own.
 *
 * The hook reads and writes `localStorage`; the button beside it renders. A
 * module exporting both reloads whole instead of preserving state, and this
 * hook holds exactly the state a reader would lose. Two pages import it — the
 * login page and the setup page — so it was already shared code living in a
 * component file.
 */
import { useCallback, useState } from "react";

export interface LoginAnimationSettings {
  animations: boolean;
  explosion: boolean;
  formulas: boolean;
}

const STORAGE_KEY = "turing-login-settings";

const DEFAULTS: LoginAnimationSettings = {
  animations: true,
  explosion: false,
  formulas: true,
};

function loadSettings(): LoginAnimationSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return { ...DEFAULTS, ...JSON.parse(raw) };
  } catch { /* ignore */ }
  return { ...DEFAULTS };
}

function saveSettings(settings: LoginAnimationSettings) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
}

export function useLoginSettings() {
  const [settings, setSettings] = useState<LoginAnimationSettings>(loadSettings);

  const update = useCallback((patch: Partial<LoginAnimationSettings>) => {
    setSettings(prev => {
      const next = { ...prev, ...patch };
      saveSettings(next);
      return next;
    });
  }, []);

  return { settings, update };
}
