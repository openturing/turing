import { Monitor, Moon, Sun } from "lucide-react"
import { useTranslation } from "react-i18next"

import { useTheme } from "@/components/theme-provider"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { GradientButton } from "./ui/gradient-button"

export function ModeToggle() {
  const { setTheme } = useTheme()
  const { t } = useTranslation()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <GradientButton variant="outline" size="sm">
          <Sun className="h-[1.2rem] w-[1.2rem] scale-100 rotate-0 transition-all dark:scale-0 dark:-rotate-90" />
          <Moon className="absolute h-[1.2rem] w-[1.2rem] scale-0 rotate-90 transition-all dark:scale-100 dark:rotate-0" />
          <span className="sr-only">{t("theme.toggle")}</span>
        </GradientButton>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => setTheme("light")}>
          {t("theme.light")}
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => setTheme("dark")}>
          {t("theme.dark")}
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => setTheme("system")}>
          {t("theme.system")}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

const THEME_OPTIONS = [
  { value: "light", labelKey: "theme.light", icon: Sun },
  { value: "dark", labelKey: "theme.dark", icon: Moon },
  { value: "system", labelKey: "theme.system", icon: Monitor },
] as const

export function ModeToggleSidebar() {
  const { theme, setTheme } = useTheme()
  const { t } = useTranslation()
  const current = THEME_OPTIONS.find((o) => o.value === theme) ?? THEME_OPTIONS[2]

  function cycleTheme() {
    const idx = THEME_OPTIONS.findIndex((o) => o.value === theme)
    const next = THEME_OPTIONS[(idx + 1) % THEME_OPTIONS.length]
    setTheme(next.value)
  }

  const CurrentIcon = current.icon

  return (
    <button
      type="button"
      title={t("theme.toggle")}
      onClick={cycleTheme}
      className="md:hidden flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-sidebar-foreground hover:bg-sidebar-accent transition-colors w-full"
    >
      <CurrentIcon className="size-4 shrink-0" />
      <span>{t("theme.modeLabel", { mode: t(current.labelKey) })}</span>
    </button>
  )
}
