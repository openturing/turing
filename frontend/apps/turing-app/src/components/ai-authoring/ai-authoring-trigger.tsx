"use client"
import { useTranslation } from "react-i18next"
import { IconSparkles } from "@tabler/icons-react"
import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

/**
 * Floating "Create with AI" entry point — used on list pages of any CRUD
 * that has an AI authoring page. Renders a fucshia-tinted FAB-style
 * button anchored to the bottom-right of the viewport.
 *
 * @since 2026.2.5
 */

interface Props {
  /** Where to navigate when the user clicks. */
  readonly to: string
  /** Visible label — defaults to the generic "Create with AI". */
  readonly label?: string
  /** Position override; default is bottom-right within the page container. */
  readonly className?: string
}

export function AiAuthoringTrigger({ to, label, className }: Props) {
  const { t } = useTranslation()
  return (
    <Button
      asChild
      size="lg"
      className={cn(
        "fixed bottom-6 right-6 z-30 gap-2 rounded-full shadow-lg",
        "bg-gradient-to-r from-violet-600 to-fuchsia-600 text-white",
        "hover:from-violet-500 hover:to-fuchsia-500",
        className,
      )}
    >
      <Link to={to}>
        <IconSparkles className="size-5" />
        {label ?? t("aiAuthoring.createWithAi")}
      </Link>
    </Button>
  )
}
