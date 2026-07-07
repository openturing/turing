import { useCustomTools, useDeleteCustomTool, useUpdateCustomTool } from "@/api/queries/custom-tool.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import type { TurCustomTool } from "@/models/customtool/custom-tool.model.ts";
import { TurCustomToolService } from "@/services/customtool/custom-tool.service";
import { IconBraces } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BENTO_CUSTOM_TOOL_FORM_ID, BentoCustomToolForm } from "./bento.custom-tool.form";

const service = new TurCustomToolService();

/**
 * Bento Custom Tool detail — T554. Identity in the hero; the LLM description,
 * parameters, and the Groovy editor (CodeMirror + validate + Vibe Coding +
 * live-draft) are reused as-is inside the form body.
 */
export default function BentoCustomToolPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const [tool, setTool] = useState<TurCustomTool | undefined>(isNew ? ({} as TurCustomTool) : undefined);
  const [error, setError] = useState<string | null>(null);

  const updateMutation = useUpdateCustomTool();
  const deleteMutation = useDeleteCustomTool();
  // Warm the list cache so navigating back is instant.
  useCustomTools();

  useEffect(() => {
    if (isNew) {
      setTool({} as TurCustomTool);
      return;
    }
    service.get(id)
      .then(setTool)
      .catch(() => setError(t("common.connectionError", { resource: t("customTool.title") })));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  function shell(entity: TurCustomTool, headlineFallback: string) {
    return (
      <BentoEntityShell
        entity={entity}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("customTool.title")}
        listRoute={ROUTES.BENTO_CUSTOM_TOOL_INSTANCE}
        icon={IconBraces}
        tone="slate"
        formId={BENTO_CUSTOM_TOOL_FORM_ID}
        feature={t("customTool.title")}
        hasStatus
        onUpdate={isNew ? undefined : (next) => updateMutation.mutateAsync(next)}
        onDelete={isNew ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <BentoCustomToolForm value={entity} isNew={isNew} staged={staged} onStateChange={onStateChange} />
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({} as TurCustomTool, t("customTool.newCustomTool"));
  }

  return (
    <LoadProvider checkIsNotUndefined={tool} error={error} tryAgainUrl={`${ROUTES.BENTO_CUSTOM_TOOL_INSTANCE}/${id}`}>
      {tool && shell(tool, tool.title)}
    </LoadProvider>
  );
}
