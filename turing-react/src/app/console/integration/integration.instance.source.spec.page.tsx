import { ROUTES } from "@/app/routes.const";
import { BadgeFieldType } from "@/components/badge-field-type";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { FormActions } from "@/components/ui/form-actions";
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { Input } from "@/components/ui/input";
import { SectionCard } from "@/components/ui/section-card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { SubPageHeader } from "@/components/sub.page.header";
import { useAemSourceService } from "@/contexts/TuringServiceContext";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurIntegrationAemAttributeSpec } from "@/models/integration/integration-aem-attribute-spec.model";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { IconFileDescription, IconSettings } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const SE_FIELD_TYPES = [
  "INT", "LONG", "STRING", "TEXT", "ARRAY", "DATE", "BOOL", "FLOAT", "DOUBLE", "CURRENCY",
] as const;

const emptySpec: TurIntegrationAemAttributeSpec = {
  id: "",
  className: "",
  text: "",
  name: "",
  type: "STRING",
  mandatory: false,
  multiValued: false,
  description: "",
  facet: false,
  facetNames: {},
};

export default function IntegrationInstanceSourceSpecPage() {
  const navigate = useNavigate();
  const { id, sourceId, specIndex } = useParams() as {
    id: string;
    sourceId: string;
    specIndex: string;
  };
  const { t } = useTranslation();
  const turIntegrationAemSourceService = useAemSourceService(id);
  const [source, setSource] = useState<TurIntegrationAemSource | null>(null);
  const isNew = specIndex === "new";
  const index = isNew ? -1 : parseInt(specIndex, 10);

  const specTabRoute = `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${sourceId}/specifications`;

  const form = useForm<TurIntegrationAemAttributeSpec>({
    defaultValues: emptySpec,
  });

  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    turIntegrationAemSourceService.get(sourceId).then((s) => {
      setSource(s);
      const specs = Array.isArray(s.attributeSpecifications)
        ? s.attributeSpecifications
        : [];
      if (!isNew && index >= 0 && index < specs.length) {
        form.reset(specs[index]);
        setBreadcrumb([
          { label: t("integration.sources.title"), href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source` },
          { label: s.name, href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${sourceId}/specifications` },
          { label: specs[index].name || t("forms.integrationSource.specUntitled") },
        ]);
      } else if (isNew) {
        form.reset(emptySpec);
        setBreadcrumb([
          { label: t("integration.sources.title"), href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source` },
          { label: s.name, href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${sourceId}/specifications` },
          { label: t("common.new") },
        ]);
      }
    });
  }, [id, sourceId, specIndex]);

  async function onSubmit(spec: TurIntegrationAemAttributeSpec) {
    if (!source) return;
    try {
      const specs = Array.isArray(source.attributeSpecifications)
        ? [...source.attributeSpecifications]
        : [];
      if (isNew) {
        specs.push(spec);
      } else {
        specs[index] = spec;
      }
      const updated = { ...source, attributeSpecifications: specs };
      const result = await turIntegrationAemSourceService.update(updated);
      if (result) {
        toast.success(t("forms.common.updated", { name: spec.name, feature: t("forms.integrationSource.specifications") }));
        navigate(specTabRoute);
      } else {
        toast.error(t("forms.common.formSubmitFailed"));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <>
      <SubPageHeader
        icon={IconFileDescription}
        feature={t("forms.integrationSource.specifications")}
        name={form.watch("name") || t("forms.integrationSource.specUntitled")}
        description={t("forms.integrationSource.specDetailDesc")}
      />

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-6">
          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconSettings}
              title={t("forms.integrationSource.specDetail")}
              description={t("forms.integrationSource.specDetailDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="name"
                rules={{ required: t("forms.common.nameRequired") }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.common.name")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="title" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.specNameDesc")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="className"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.specClassName")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="com.example.AttributeHandler" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.specClassNameDesc")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="type"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.specType")}</FormLabel>
                    <Select onValueChange={field.onChange} value={field.value || "STRING"}>
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {SE_FIELD_TYPES.map((type) => (
                          <SelectItem key={type} value={type}>
                            <div className="flex items-center gap-2">
                              <BadgeFieldType type={type} variation="short" />
                              <span>{type}</span>
                            </div>
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormDescription>{t("forms.integrationSource.specTypeDesc")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="text"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.specText")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="jcr:content/jcr:title" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.specTextDesc")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="description"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.specDescription")}</FormLabel>
                    <FormControl>
                      <Textarea {...field} rows={3} placeholder={t("forms.integrationSource.specDescriptionPlaceholder")} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="mandatory"
                render={({ field }) => (
                  <FormItemTwoColumns className="rounded-lg border p-3">
                    <FormItemTwoColumns.Left className="space-y-0.5">
                      <FormItemTwoColumns.Label>{t("forms.integrationSource.specMandatory")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.integrationSource.specMandatoryDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch checked={field.value} onCheckedChange={field.onChange} />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />

              <FormField
                control={form.control}
                name="multiValued"
                render={({ field }) => (
                  <FormItemTwoColumns className="rounded-lg border p-3">
                    <FormItemTwoColumns.Left className="space-y-0.5">
                      <FormItemTwoColumns.Label>{t("forms.integrationSource.multiValued")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.integrationSource.specMultiValuedDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch checked={field.value} onCheckedChange={field.onChange} />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />

              <FormField
                control={form.control}
                name="facet"
                render={({ field }) => (
                  <FormItemTwoColumns className="rounded-lg border p-3">
                    <FormItemTwoColumns.Left className="space-y-0.5">
                      <FormItemTwoColumns.Label>{t("forms.integrationSource.facet")}</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        {t("forms.integrationSource.specFacetDesc")}
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch checked={field.value} onCheckedChange={field.onChange} />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <FormActions>
            <FormActions.Cancel onClick={() => navigate(specTabRoute)} />
            <FormActions.Submit />
          </FormActions>
        </form>
      </Form>
    </>
  );
}
