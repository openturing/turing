"use client"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import type { TurIntegrationAemTargetAttribute } from "@/models/integration/integration-aem-target-attribute.model"
import { IconChevronDown, IconChevronRight, IconPlus, IconTrash } from "@tabler/icons-react"
import { useCallback, useState } from "react"
import { useFieldArray, useFormContext } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { SourceAttributeList } from "./source.attribute.list"

interface TargetAttributeListProps {
  modelFieldPrefix: string;
}

export function TargetAttributeList({ modelFieldPrefix }: Readonly<TargetAttributeListProps>) {
  const { t } = useTranslation();
  const form = useFormContext();
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: modelFieldPrefix,
  });

  const [expandedIndex, setExpandedIndex] = useState<number | null>(null);

  const handleAdd = useCallback(() => {
    append({ name: "", sourceAttrs: [] });
  }, [append]);

  const handleRemove = useCallback((index: number, e: React.MouseEvent) => {
    e.stopPropagation();
    remove(index);
    if (expandedIndex === index) {
      setExpandedIndex(null);
    } else if (expandedIndex !== null && expandedIndex > index) {
      setExpandedIndex(expandedIndex - 1);
    }
  }, [remove, expandedIndex]);

  const toggleExpand = useCallback((index: number) => {
    setExpandedIndex(prev => prev === index ? null : index);
  }, []);

  return (
    <div className="space-y-2">
      {fields.map((field, index) => {
        const target = form.watch(`${modelFieldPrefix}.${index}`) as TurIntegrationAemTargetAttribute;
        const sourceCount = Array.isArray(target?.sourceAttrs) ? target.sourceAttrs.length : 0;
        const isExpanded = expandedIndex === index;

        return (
          <div key={field.id} className="rounded-md border bg-background">
            <div
              className="flex items-center gap-2 p-2 cursor-pointer hover:bg-muted/50 transition-colors"
              onClick={() => toggleExpand(index)}
            >
              {isExpanded
                ? <IconChevronDown className="size-4 shrink-0 text-muted-foreground" />
                : <IconChevronRight className="size-4 shrink-0 text-muted-foreground" />
              }
              <Input
                className="h-8 text-sm"
                placeholder={t("forms.integrationSource.targetAttrNamePlaceholder")}
                {...form.register(`${modelFieldPrefix}.${index}.name`)}
                onClick={(e) => e.stopPropagation()}
              />
              {sourceCount > 0 && (
                <span className="text-xs text-muted-foreground shrink-0">
                  {sourceCount} {t("forms.integrationSource.sourceAttrsCount")}
                </span>
              )}
              <GradientButton
                variant="ghost"
                size="icon"
                type="button"
                className="size-7 shrink-0"
                onClick={(e) => handleRemove(index, e)}
              >
                <IconTrash className="size-3.5 text-red-500" />
              </GradientButton>
            </div>

            {isExpanded && (
              <div className="border-t px-3 py-3 bg-muted/30">
                <p className="text-xs text-muted-foreground mb-2">
                  {t("forms.integrationSource.sourceAttrsDesc")}
                </p>
                <SourceAttributeList
                  fieldPrefix={`${modelFieldPrefix}.${index}.sourceAttrs`}
                />
              </div>
            )}
          </div>
        );
      })}

      <GradientButton variant="outline" size="sm" type="button" onClick={handleAdd}>
        <IconPlus className="size-3.5 mr-1.5" />
        {t("forms.integrationSource.addTargetAttr")}
      </GradientButton>
    </div>
  );
}
