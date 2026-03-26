"use client"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import { IconPlus, IconTrash } from "@tabler/icons-react"
import { useCallback } from "react"
import { useFieldArray, useFormContext } from "react-hook-form"
import { useTranslation } from "react-i18next"

interface SourceAttributeListProps {
  fieldPrefix: string;
}

export function SourceAttributeList({ fieldPrefix }: Readonly<SourceAttributeListProps>) {
  const { t } = useTranslation();
  const form = useFormContext();
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: fieldPrefix,
  });

  const handleAdd = useCallback(() => {
    append({ name: "", className: "", text: "" });
  }, [append]);

  return (
    <div className="space-y-2">
      {fields.map((field, index) => (
        <div key={field.id} className="flex items-center gap-2">
          <Input
            className="h-7 text-xs"
            placeholder={t("forms.integrationSource.sourceAttrName")}
            {...form.register(`${fieldPrefix}.${index}.name`)}
          />
          <Input
            className="h-7 text-xs font-mono"
            placeholder={t("forms.integrationSource.sourceAttrClassName")}
            {...form.register(`${fieldPrefix}.${index}.className`)}
          />
          <Input
            className="h-7 text-xs"
            placeholder={t("forms.integrationSource.sourceAttrText")}
            {...form.register(`${fieldPrefix}.${index}.text`)}
          />
          <GradientButton
            variant="ghost"
            size="icon"
            type="button"
            className="size-7 shrink-0"
            onClick={() => remove(index)}
          >
            <IconTrash className="size-3.5 text-red-500" />
          </GradientButton>
        </div>
      ))}

      <GradientButton variant="outline" size="sm" type="button" onClick={handleAdd}>
        <IconPlus className="size-3.5 mr-1.5" />
        {t("forms.integrationSource.addSourceAttr")}
      </GradientButton>
    </div>
  );
}
