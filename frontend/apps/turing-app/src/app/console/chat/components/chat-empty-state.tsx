import { Icon } from "@iconify/react"
import { IconCompass, IconCpu2, IconMasksTheater, IconRobot } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"

/**
 * Subset of {@link TurLLMInstance} (admin model) / `TurLlmInstance` (SDK)
 * that this empty-state actually reads. Keeping it minimal lets us accept
 * either source without forcing a wider migration.
 */
interface DisplayInstance {
  title: string
  modelName?: string
  turLLMVendor?: { id: string } | null
}

interface ChatEmptyStateProps {
  variant: "chat" | "semantic" | "agent" | "persona"
  selectedInstance?: DisplayInstance
  agentTitle?: string
  agentIcon?: string | null
  /** Block AI / T581 — persona name shown when {@link variant} is `persona`. */
  personaName?: string
}

export function ChatEmptyState({ variant, selectedInstance, agentTitle, agentIcon, personaName }: ChatEmptyStateProps) {
  const { t } = useTranslation()

  if (variant === "persona") {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4 text-center px-4">
        <div className="rounded-full bg-linear-to-br from-fuchsia-600 to-pink-600 p-4 dark:from-fuchsia-500 dark:to-pink-500">
          <IconMasksTheater className="size-8 text-white" />
        </div>
        <div>
          <h2 className="text-2xl font-semibold mb-2">
            {personaName ?? t("persona.title", { defaultValue: "Persona" })}
          </h2>
          <p className="text-muted-foreground text-sm max-w-md">
            {selectedInstance
              ? t("chat.persona.usingModel", {
                  model: selectedInstance.title,
                  defaultValue: "Talking to this persona using {{model}}",
                })
              : t("chat.selectModelToStart", { defaultValue: "Select a model to start" })}
          </p>
        </div>
      </div>
    )
  }

  if (variant === "chat") {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4 text-center px-4">
        <div className="rounded-full bg-linear-to-br from-blue-600 to-indigo-600 p-4 dark:from-blue-500 dark:to-indigo-500">
          <IconCpu2 className="size-8 text-white" />
        </div>
        <div>
          <h2 className="text-2xl font-semibold mb-2">{t("chat.howCanIHelp")}</h2>
          <p className="text-muted-foreground text-sm max-w-md">
            {selectedInstance ? t("chat.usingModel", { model: selectedInstance.title, vendor: selectedInstance.turLLMVendor?.id ?? "" }) : t("chat.selectModelToChat")}
          </p>
        </div>
      </div>
    )
  }

  if (variant === "semantic") {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4 text-center px-4">
        <div className="rounded-full bg-linear-to-br from-emerald-600 to-teal-600 p-4 dark:from-emerald-500 dark:to-teal-500">
          <IconCompass className="size-8 text-white" />
        </div>
        <div>
          <h2 className="text-2xl font-semibold mb-2">{t("home.features.semanticNavigation.title")}</h2>
          <p className="text-muted-foreground text-sm max-w-md">
            {selectedInstance
              ? t("chat.usingModelForSN", { model: selectedInstance.title })
              : t("chat.selectModelToExplore")}
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center justify-center h-full gap-4 text-center px-4">
      <div className="rounded-full bg-linear-to-br from-violet-600 to-purple-600 p-4 dark:from-violet-500 dark:to-purple-500">
        {agentIcon ? (
          <Icon icon={agentIcon} className="size-8 text-white" />
        ) : (
          <IconRobot className="size-8 text-white" />
        )}
      </div>
      <div>
        <h2 className="text-2xl font-semibold mb-2">{agentTitle ?? t("home.features.aiAgent.title")}</h2>
        <p className="text-muted-foreground text-sm max-w-md">
          {selectedInstance
            ? t("chat.usingModelForAgent", { model: selectedInstance.title })
            : t("chat.selectModelToStartAgent")}
        </p>
      </div>
    </div>
  )
}
