"use client"
import { ROUTES } from "@/app/routes.const"
import { GradientButton } from "@/components/ui/gradient-button"
import { useAemSourceService } from "@/contexts/TuringServiceContext"
import type { TurIntegrationAemPluginModel } from "@/models/integration/integration-aem-plugin-model.model"
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model"
import { IconBox, IconPlus, IconTarget, IconTrash } from "@tabler/icons-react"
import { useCallback } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "sonner"

interface PluginModelListProps {
  source: TurIntegrationAemSource;
  onSourceUpdated: (source: TurIntegrationAemSource) => void;
}

export function PluginModelList({ source, onSourceUpdated }: Readonly<PluginModelListProps>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id, sourceId } = useParams() as { id: string; sourceId: string };
  const turIntegrationAemSourceService = useAemSourceService(id);
  const models = Array.isArray(source.models) ? source.models : [];
  const baseRoute = `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${sourceId}/models`;

  const handleAdd = useCallback(() => {
    navigate(`${baseRoute}/new`);
  }, [navigate, baseRoute]);

  const handleSelect = useCallback((index: number) => {
    navigate(`${baseRoute}/${index}`);
  }, [navigate, baseRoute]);

  const handleRemove = useCallback(async (index: number, e: React.MouseEvent) => {
    e.stopPropagation();
    const updated = {
      ...source,
      models: models.filter((_, i) => i !== index),
    };
    try {
      const result = await turIntegrationAemSourceService.update(updated);
      if (result) {
        onSourceUpdated(updated);
        toast.success(t("forms.integrationSource.modelDeleted"));
      }
    } catch {
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }, [source, models, turIntegrationAemSourceService, onSourceUpdated, t]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {t("forms.integrationSource.modelListDesc", { count: models.length })}
        </p>
        <GradientButton variant="outline" type="button" onClick={handleAdd}>
          <IconPlus className="size-4 mr-2" />
          {t("forms.common.add")}
        </GradientButton>
      </div>

      {models.length === 0 ? (
        <div className="rounded-lg border border-dashed p-8 text-center">
          <IconBox className="mx-auto size-10 text-muted-foreground/50" />
          <p className="mt-2 text-sm text-muted-foreground">
            {t("forms.integrationSource.modelEmpty")}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {models.map((model: TurIntegrationAemPluginModel, index: number) => {
            const targetCount = Array.isArray(model.targetAttrs) ? model.targetAttrs.length : 0;
            return (
              <button
                key={model.id || index}
                type="button"
                className="flex w-full items-center gap-3 rounded-lg border p-3 cursor-pointer hover:bg-muted/50 transition-colors text-left"
                onClick={() => handleSelect(index)}
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium truncate">
                      {model.type || t("forms.integrationSource.modelUntitled")}
                    </span>
                    {model.subType && (
                      <span className="text-xs bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400 px-2 py-0.5 rounded-full">
                        {model.subType}
                      </span>
                    )}
                  </div>
                  {model.className && (
                    <p className="text-xs text-muted-foreground font-mono truncate mt-0.5">
                      {model.className}
                    </p>
                  )}
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  {targetCount > 0 && (
                    <span className="flex items-center gap-1 text-xs bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 px-2 py-0.5 rounded-full font-medium">
                      <IconTarget className="size-3" />
                      {targetCount}
                    </span>
                  )}
                  <GradientButton
                    variant="ghost"
                    size="icon"
                    type="button"
                    className="size-8"
                    onClick={(e) => handleRemove(index, e)}
                  >
                    <IconTrash className="size-4 text-red-500" />
                  </GradientButton>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
