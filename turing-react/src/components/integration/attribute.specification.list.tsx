"use client"
import { ROUTES } from "@/app/routes.const"
import { BadgeFieldType } from "@/components/badge-field-type"
import { GradientButton } from "@/components/ui/gradient-button"
import type { TurIntegrationAemAttributeSpec } from "@/models/integration/integration-aem-attribute-spec.model"
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model"
import { IconFileDescription, IconPlus, IconTrash } from "@tabler/icons-react"
import { useCallback } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { useAemSourceService } from "@/contexts/TuringServiceContext"
import { toast } from "sonner"

interface AttributeSpecificationListProps {
  source: TurIntegrationAemSource;
  onSourceUpdated: (source: TurIntegrationAemSource) => void;
}

export function AttributeSpecificationList({ source, onSourceUpdated }: Readonly<AttributeSpecificationListProps>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id, sourceId } = useParams() as { id: string; sourceId: string };
  const turIntegrationAemSourceService = useAemSourceService(id);
  const specs = Array.isArray(source.attributeSpecifications) ? source.attributeSpecifications : [];
  const baseRoute = `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${sourceId}/specifications`;

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
      attributeSpecifications: specs.filter((_, i) => i !== index),
    };
    try {
      const result = await turIntegrationAemSourceService.update(updated);
      if (result) {
        onSourceUpdated(updated);
        toast.success(t("forms.integrationSource.specDeleted"));
      }
    } catch {
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }, [source, specs, turIntegrationAemSourceService, onSourceUpdated, t]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {t("forms.integrationSource.specListDesc", { count: specs.length })}
        </p>
        <GradientButton variant="outline" type="button" onClick={handleAdd}>
          <IconPlus className="size-4 mr-2" />
          {t("forms.common.add")}
        </GradientButton>
      </div>

      {specs.length === 0 ? (
        <div className="rounded-lg border border-dashed p-8 text-center">
          <IconFileDescription className="mx-auto size-10 text-muted-foreground/50" />
          <p className="mt-2 text-sm text-muted-foreground">
            {t("forms.integrationSource.specEmpty")}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {specs.map((spec: TurIntegrationAemAttributeSpec, index: number) => (
            <button
              key={spec.id || index}
              type="button"
              className="flex w-full items-center gap-3 rounded-lg border p-3 cursor-pointer hover:bg-muted/50 transition-colors text-left"
              onClick={() => handleSelect(index)}
            >
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium truncate">
                    {spec.name || t("forms.integrationSource.specUntitled")}
                  </span>
                  {spec.mandatory && (
                    <span className="text-xs text-red-500 font-medium">*</span>
                  )}
                </div>
                {spec.className && (
                  <p className="text-xs text-muted-foreground font-mono truncate mt-0.5">
                    {spec.className}
                  </p>
                )}
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {spec.multiValued && (
                  <span className="text-xs bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400 px-2 py-0.5 rounded-full font-medium">
                    {t("forms.integrationSource.multiValued")}
                  </span>
                )}
                {spec.facet && (
                  <span className="text-xs bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400 px-2 py-0.5 rounded-full font-medium">
                    {t("forms.integrationSource.facet")}
                  </span>
                )}
                <BadgeFieldType type={spec.type} variation="long" />
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
          ))}
        </div>
      )}
    </div>
  );
}
