import { Monitor, Moon, Sun } from "lucide-react"

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

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <GradientButton variant="outline" size="sm">
          <Sun className="h-[1.2rem] w-[1.2rem] scale-100 rotate-0 transition-all dark:scale-0 dark:-rotate-90" />
          <Moon className="absolute h-[1.2rem] w-[1.2rem] scale-0 rotate-90 transition-all dark:scale-100 dark:rotate-0" />
          <span className="sr-only">Toggle theme</span>
        </GradientButton>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => setTheme("light")}>
          Light
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => setTheme("dark")}>
          Dark
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => setTheme("system")}>
          System
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

const THEME_OPTIONS = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
] as const

export function ModeToggleSidebar() {
  const { theme, setTheme } = useTheme()
  const current = THEME_OPTIONS.find((t) => t.value === theme) ?? THEME_OPTIONS[2]

  function cycleTheme() {
    const idx = THEME_OPTIONS.findIndex((t) => t.value === theme)
    const next = THEME_OPTIONS[(idx + 1) % THEME_OPTIONS.length]
    setTheme(next.value)
  }

  const CurrentIcon = current.icon

  return (
    <button
      type="button"
      title="Toggle theme"
      onClick={cycleTheme}
      className="md:hidden flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-sidebar-foreground hover:bg-sidebar-accent transition-colors w-full"
    >
      <CurrentIcon className="size-4 shrink-0" />
      <span>{current.label} mode</span>
    </button>
  )
}