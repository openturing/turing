import type { TurChatResponse } from "@viglet/turing-react-sdk";
import { IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

interface AiChatCardProps {
  chat: TurChatResponse | null;
}

export function AiChatCard({ chat }: Readonly<AiChatCardProps>) {
  const { t } = useTranslation();

  if (!chat?.text) return null;

  return (
    <div className="mb-6 rounded-xl border border-blue-500/20 bg-gradient-to-br from-blue-500/5 via-transparent to-indigo-500/5 p-5">
      <div className="flex items-start gap-3">
        <div className="shrink-0 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 p-2">
          <IconSparkles className="size-4 text-white" />
        </div>
        <div className="min-w-0">
          <h3 className="text-sm font-semibold mb-1.5">{t("search.aiAssistant")}</h3>
          <p className="text-sm text-muted-foreground leading-relaxed whitespace-pre-wrap">
            {chat.text}
          </p>
        </div>
      </div>
    </div>
  );
}
