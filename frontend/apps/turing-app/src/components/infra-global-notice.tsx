import { IconLock, IconWorld } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { Badge } from "@/components/ui/badge"

/**
 * T372 — small inline "Global" badge shown on BYO-infra forms/headers when the
 * edited entity belongs to the shared, platform-provided GLOBAL pool
 * (`tenantId === null` with multi-tenancy on). Rendered regardless of role so a
 * platform admin still sees the row is global.
 */
export const GlobalBadge: React.FC<{ className?: string }> = ({ className }) => {
  const { t } = useTranslation()
  return (
    <Badge
      variant="outline"
      className={`gap-1 border-amber-300/60 bg-amber-50 text-amber-900 dark:border-amber-600/40 dark:bg-amber-950/30 dark:text-amber-200 ${className ?? ""}`}
    >
      <IconWorld className="size-3.5" />
      {t("forms.common.global", { defaultValue: "Global" })}
    </Badge>
  )
}

/**
 * T372 — amber read-only banner shown on a BYO-infra form when the GLOBAL pool
 * row may not be edited by the current (non-platform-admin) caller.
 */
export const GlobalReadOnlyNotice: React.FC = () => {
  const { t } = useTranslation()
  return (
    <div className="flex items-center gap-2 rounded-lg border border-amber-300/60 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-600/40 dark:bg-amber-950/30 dark:text-amber-200">
      <IconLock className="size-4 shrink-0" />
      <span>
        {t("forms.common.globalReadOnly", {
          defaultValue:
            "This is a shared platform-provided resource and is read-only.",
        })}
      </span>
    </div>
  )
}
