import { useCallback, useState } from "react";
import { IconRun } from "@tabler/icons-react";
import { GradientButton } from "@/components/ui/gradient-button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import { useTranslation } from "react-i18next";

const LANGUAGES = [
  { code: "en", label: "English" },
  { code: "pt", label: "Português" },
] as const;

const STORAGE_KEY = "turing-login-settings";

export interface LoginAnimationSettings {
  animations: boolean;
  explosion: boolean;
  formulas: boolean;
}

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

interface LoginSettingsButtonProps {
  readonly settings: LoginAnimationSettings;
  readonly onUpdate: (patch: Partial<LoginAnimationSettings>) => void;
}

export function LoginSettingsButton({ settings, onUpdate }: LoginSettingsButtonProps) {
  const { t, i18n } = useTranslation();

  return (
    <Popover>
        <PopoverTrigger asChild>
          <GradientButton variant="outline" size="sm">
            <IconRun className="h-[1.2rem] w-[1.2rem]" />
          </GradientButton>
        </PopoverTrigger>
        <PopoverContent align="end" side="bottom" className="w-56 p-3 space-y-3">
          <div className="flex items-center justify-between">
            <label htmlFor="login-anim" className="text-sm text-slate-700 dark:text-slate-300 cursor-pointer">
              {t("login.settingsAnimations", "Animations")}
            </label>
            <Switch
              id="login-anim"
              checked={settings.animations}
              onCheckedChange={(checked) => onUpdate({ animations: checked })}
            />
          </div>
          <div className="flex items-center justify-between">
            <label htmlFor="login-explosion" className="text-sm text-slate-700 dark:text-slate-300 cursor-pointer">
              {t("login.settingsExplosion", "Explosion")}
            </label>
            <Switch
              id="login-explosion"
              checked={settings.explosion}
              onCheckedChange={(checked) => onUpdate({ explosion: checked })}
            />
          </div>
          <div className="flex items-center justify-between">
            <label htmlFor="login-formulas" className="text-sm text-slate-700 dark:text-slate-300 cursor-pointer">
              {t("login.settingsFormulas", "Formulas")}
            </label>
            <Switch
              id="login-formulas"
              checked={settings.formulas}
              onCheckedChange={(checked) => onUpdate({ formulas: checked })}
            />
          </div>
          <Separator />
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-700 dark:text-slate-300">
              {t("login.settingsLanguage", "Language")}
            </span>
            <div className="flex gap-1">
              {LANGUAGES.map((lang) => (
                <button
                  key={lang.code}
                  type="button"
                  onClick={() => i18n.changeLanguage(lang.code)}
                  className={`px-2 py-0.5 rounded text-xs font-medium transition-colors ${
                    i18n.language?.startsWith(lang.code)
                      ? "bg-blue-600 text-white dark:bg-blue-500"
                      : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
                  }`}
                >
                  {lang.label}
                </button>
              ))}
            </div>
          </div>
        </PopoverContent>
      </Popover>
  );
}
