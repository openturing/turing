import { IconCompass, IconCpu2 } from "@tabler/icons-react"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model"

interface ChatEmptyStateProps {
  variant: "chat" | "semantic"
  selectedInstance?: TurLLMInstance
}

export function ChatEmptyState({ variant, selectedInstance }: ChatEmptyStateProps) {
  if (variant === "chat") {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4 text-center px-4">
        <div className="rounded-full bg-gradient-to-br from-blue-600 to-indigo-600 p-4 dark:from-blue-500 dark:to-indigo-500">
          <IconCpu2 className="size-8 text-white" />
        </div>
        <div>
          <h2 className="text-2xl font-semibold mb-2">How can I help you today?</h2>
          <p className="text-muted-foreground text-sm max-w-md">
            {selectedInstance ? `Using ${selectedInstance.title} (${selectedInstance.turLLMVendor?.id ?? ""})` : "Select a language model to start chatting."}
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center justify-center h-full gap-4 text-center px-4">
      <div className="rounded-full bg-gradient-to-br from-emerald-600 to-teal-600 p-4 dark:from-emerald-500 dark:to-teal-500">
        <IconCompass className="size-8 text-white" />
      </div>
      <div>
        <h2 className="text-2xl font-semibold mb-2">Semantic Navigation</h2>
        <p className="text-muted-foreground text-sm max-w-md">
          {selectedInstance
            ? `Ask questions about your indexed content. Using ${selectedInstance.title}.`
            : "Select a language model to start exploring."}
        </p>
      </div>
    </div>
  )
}
