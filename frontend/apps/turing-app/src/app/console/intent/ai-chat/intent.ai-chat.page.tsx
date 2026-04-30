import { ROUTES } from "@/app/routes.const";
import { AiAuthoringLayout } from "@/components/ai-authoring/ai-authoring-layout";
import { AiAuthoringPanel } from "@/components/ai-authoring/ai-authoring-panel";
import { IntentSettingsForm } from "@/components/intent/intent.settings.form";
import { LoadProvider } from "@/components/loading-provider";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useAiAuthoring } from "@/hooks/use-ai-authoring";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type {
  IntentGeneration,
  TurIntent,
  TurIntentAction,
} from "@/models/intent/intent.model";
import { TurIntentService } from "@/services/intent/intent.service";
import { useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Intent AI Authoring page — form on the left (reusing IntentSettingsForm),
 * chat on the right. The chat sees the form's snapshot each turn, returns
 * a conversational reply + the updated state, and the form is re-populated
 * via a controlled-from-outside ref the form exposes.
 *
 * @since 2026.2.5
 */
const turIntentService = new TurIntentService();

export default function IntentAiChatPage() {
  const { id: agentId } = useParams() as { id: string };
  const { t } = useTranslation();
  const [intent, setIntent] = useState<TurIntent>(emptyIntent());
  const [breadcrumb] = useState<BreadcrumbItem[] | undefined>([
    { label: t("intent.aiChat.breadcrumb") },
  ]);
  useSubPageBreadcrumb(breadcrumb);

  // Mutable ref so the AI hook always reads the latest snapshot — even if
  // multiple turns fire while React hasn't flushed state yet.
  const intentRef = useRef<TurIntent>(intent);
  intentRef.current = intent;

  const getCurrentState = useCallback((): IntentGeneration => {
    const cur = intentRef.current;
    return {
      title: cur.title ?? "",
      description: cur.description ?? null,
      icon: cur.icon ?? null,
      enabled: cur.enabled ?? 1,
      actions: (cur.actions ?? []).map((a) => ({ label: a.label, prompt: a.prompt })),
    };
  }, []);

  const applyState = useCallback((state: IntentGeneration) => {
    setIntent((prev) => mergeIntoIntent(prev, state));
  }, []);

  const chat = useAiAuthoring<IntentGeneration>({
    endpoint: (req) => turIntentService.aiChat(agentId, req),
    getCurrentState,
    applyState,
  });

  const tryAgainUrl = useMemo(
    () => `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/intent/new/ai-chat`,
    [agentId],
  );

  return (
    <LoadProvider checkIsNotUndefined={intent} error={null} tryAgainUrl={tryAgainUrl}>
      <AiAuthoringLayout
        chat={
          <AiAuthoringPanel
            chat={chat}
            title={t("intent.aiChat.panelTitle")}
            subtitle={t("intent.aiChat.panelSubtitle")}
            placeholder={t("intent.aiChat.placeholder")}
            emptyState={t("intent.aiChat.empty")}
          />
        }
      >
        <IntentSettingsForm value={intent} isNew={true} agentId={agentId} />
      </AiAuthoringLayout>
    </LoadProvider>
  );
}

function emptyIntent(): TurIntent {
  /* id is intentionally undefined (not "") so the backend's
     TurAssignableUuidGenerator generates a fresh UUID. Sending an empty
     string would be preserved verbatim and every AI-chat-created intent
     would land in the DB with id="" (collisions on save, broken list links). */
  return {
    id: undefined as unknown as string,
    title: "",
    description: "",
    icon: null,
    enabled: 1,
    sortOrder: 0,
    actions: [],
  };
}

/**
 * Merge an LLM-generated snapshot into the form's TurIntent shape.
 * Preserves any IDs the form already had on existing actions (matched by
 * label) so a subsequent save doesn't recreate them as new rows.
 */
function mergeIntoIntent(prev: TurIntent, gen: IntentGeneration): TurIntent {
  const prevActionsByLabel = new Map(
    (prev.actions ?? []).map((a) => [a.label.trim().toLowerCase(), a]),
  );
  const actions: TurIntentAction[] = (gen.actions ?? []).map((a, i) => {
    const existing = prevActionsByLabel.get(a.label.trim().toLowerCase());
    return {
      id: existing?.id ?? "",
      label: a.label,
      prompt: a.prompt,
      sortOrder: i,
    };
  });
  return {
    ...prev,
    title: gen.title ?? prev.title,
    description: gen.description ?? prev.description,
    icon: gen.icon ?? prev.icon,
    enabled: gen.enabled ?? prev.enabled ?? 1,
    actions,
  };
}
