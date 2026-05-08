import { ROUTES } from "@/app/routes.const";
import { AiAuthoringLayout } from "@/components/ai-authoring/ai-authoring-layout";
import { AiAuthoringPanel } from "@/components/ai-authoring/ai-authoring-panel";
import { PersonaForm } from "@/components/persona/persona.form";
import { LoadProvider } from "@/components/loading-provider";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useAiAuthoring } from "@/hooks/use-ai-authoring";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { PersonaGeneration, TurPersona } from "@/models/persona/persona.model";
import { TurPersonaService } from "@/services/persona/persona.service";
import { useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

/**
 * Persona AI Authoring page — form on the left (reusing PersonaForm), chat
 * on the right. The chat sees the form's snapshot each turn, returns a
 * conversational reply + the updated state, and the form re-populates from
 * the merged result.
 *
 * @since 2026.2.7
 */
const turPersonaService = new TurPersonaService();

export default function PersonaAiChatPage() {
  const { t } = useTranslation();
  const [persona, setPersona] = useState<TurPersona>(emptyPersona());
  const [breadcrumb] = useState<BreadcrumbItem[] | undefined>([
    { label: t("persona.aiChat.breadcrumb") },
  ]);
  useSubPageBreadcrumb(breadcrumb);

  // Mutable ref so the AI hook always reads the latest snapshot — even if
  // multiple turns fire while React hasn't flushed state yet.
  const personaRef = useRef<TurPersona>(persona);
  personaRef.current = persona;

  const getCurrentState = useCallback((): PersonaGeneration => {
    const cur = personaRef.current;
    return {
      name: cur.name ?? "",
      description: cur.description ?? null,
      systemInstruction: cur.systemInstruction ?? null,
      tone: cur.tone ?? null,
      verbosity: cur.verbosity ?? 3,
      languageStyle: cur.languageStyle ?? null,
      mandatoryTerms: cur.mandatoryTerms ?? null,
      forbiddenTerms: cur.forbiddenTerms ?? null,
      enabled: cur.enabled ?? 1,
    };
  }, []);

  const applyState = useCallback((state: PersonaGeneration) => {
    setPersona((prev) => mergeIntoPersona(prev, state));
  }, []);

  const chat = useAiAuthoring<PersonaGeneration>({
    endpoint: (req) => turPersonaService.aiChat(req),
    getCurrentState,
    applyState,
  });

  const tryAgainUrl = useMemo(
    () => `${ROUTES.PERSONA_INSTANCE}/new/ai-chat`,
    [],
  );

  return (
    <LoadProvider checkIsNotUndefined={persona} error={null} tryAgainUrl={tryAgainUrl}>
      <AiAuthoringLayout
        chat={
          <AiAuthoringPanel
            chat={chat}
            title={t("persona.aiChat.panelTitle")}
            subtitle={t("persona.aiChat.panelSubtitle")}
            placeholder={t("persona.aiChat.placeholder")}
            emptyState={t("persona.aiChat.empty")}
          />
        }
      >
        <PersonaForm value={persona} isNew={true} />
      </AiAuthoringLayout>
    </LoadProvider>
  );
}

function emptyPersona(): TurPersona {
  // id is intentionally undefined (not "") so the backend's
  // TurAssignableUuidGenerator generates a fresh UUID — sending an empty
  // string would be preserved verbatim and every AI-chat-created persona
  // would land in the DB with id="".
  return {
    id: undefined as unknown as string,
    name: "",
    description: "",
    systemInstruction: "",
    tone: null,
    verbosity: 3,
    languageStyle: null,
    mandatoryTerms: null,
    forbiddenTerms: null,
    enabled: 1,
    fewShotStore: null,
    brandContextMcpServer: null,
  };
}

/**
 * Merge the LLM-generated snapshot into the form's TurPersona shape.
 * Preserves id and the heavy relations (`fewShotStore`,
 * `brandContextMcpServer`) — the LLM never touches those, the operator
 * wires them after save.
 */
function mergeIntoPersona(prev: TurPersona, gen: PersonaGeneration): TurPersona {
  return {
    ...prev,
    name: gen.name ?? prev.name,
    description: gen.description ?? prev.description,
    systemInstruction: gen.systemInstruction ?? prev.systemInstruction,
    tone: gen.tone ?? prev.tone,
    verbosity: gen.verbosity ?? prev.verbosity ?? 3,
    languageStyle: gen.languageStyle ?? prev.languageStyle,
    mandatoryTerms: gen.mandatoryTerms ?? prev.mandatoryTerms,
    forbiddenTerms: gen.forbiddenTerms ?? prev.forbiddenTerms,
    enabled: gen.enabled ?? prev.enabled ?? 1,
  };
}
