"use client"
import { ROUTES } from "@/app/routes.const"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { IconTargetArrow } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

/**
 * Block AA / §XXVI.8 — floating "Suggest persona for content" entry. Opens the
 * auto-suggest page where pasted content is ranked against the audience
 * personas. Stacked above the "Derive from audio" + "Create with AI" FABs on
 * the persona list page.
 */
export function PersonaSuggestTrigger({ className }: { className?: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return (
    <Button
      type="button"
      size="lg"
      onClick={() => navigate(`${ROUTES.PERSONA_INSTANCE}/suggest`)}
      className={cn(
        "fixed bottom-[10.5rem] right-6 z-30 gap-2 rounded-full shadow-lg",
        "bg-gradient-to-r from-violet-600 to-fuchsia-600 text-white",
        "hover:from-violet-500 hover:to-fuchsia-500",
        className
      )}
    >
      <IconTargetArrow className="size-5" />
      {t("forms.persona.suggest.trigger")}
    </Button>
  )
}
