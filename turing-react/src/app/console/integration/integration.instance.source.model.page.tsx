import { ROUTES } from "@/app/routes.const";
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
import { Input } from "@/components/ui/input";
import { SectionCard } from "@/components/ui/section-card";
import { SubPageHeader } from "@/components/sub.page.header";
import { useAemSourceService } from "@/contexts/TuringServiceContext";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurIntegrationAemPluginModel } from "@/models/integration/integration-aem-plugin-model.model";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { TargetAttributeList } from "@/components/integration/target.attribute.list";
import { IconBox, IconSettings, IconTarget } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const emptyModel: TurIntegrationAemPluginModel = {
  id: "",
  type: "",
  subType: "",
  className: "",
  targetAttrs: [],
};

export default function IntegrationInstanceSourceModelPage() {
  const navigate = useNavigate();
  const { id, sourceId, modelIndex } = useParams() as {
    id: string;
    sourceId: string;
    modelIndex: string;
  };
  const { t } = useTranslation();
  const turIntegrationAemSourceService = useAemSourceService(id);
  const [source, setSource] = useState<TurIntegrationAemSource | null>(null);
  const isNew = modelIndex === "new";
  const index = isNew ? -1 : parseInt(modelIndex, 10);

  const modelTabRoute = `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${sourceId}/models`;

  const form = useForm<TurIntegrationAemPluginModel>({
    defaultValues: emptyModel,
  });

  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    turIntegrationAemSourceService.get(sourceId).then((s) => {
      setSource(s);
      const models = Array.isArray(s.models) ? s.models : [];
      if (!isNew && index >= 0 && index < models.length) {
        form.reset(models[index]);
        setBreadcrumb([
          { label: t("integration.sources.title"), href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source` },
          { label: s.name, href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${sourceId}/models` },
          { label: models[index].type || t("forms.integrationSource.modelUntitled") },
        ]);
      } else if (isNew) {
        form.reset(emptyModel);
        setBreadcrumb([
          { label: t("integration.sources.title"), href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source` },
          { label: s.name, href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${sourceId}/models` },
          { label: t("common.new") },
        ]);
      }
    });
  }, [id, sourceId, modelIndex]);

  async function onSubmit(model: TurIntegrationAemPluginModel) {
    if (!source) return;
    try {
      const models = Array.isArray(source.models) ? [...source.models] : [];
      if (isNew) {
        models.push(model);
      } else {
        models[index] = model;
      }
      const updated = { ...source, models };
      const result = await turIntegrationAemSourceService.update(updated);
      if (result) {
        toast.success(t("forms.common.updated", { name: model.type, feature: t("forms.integrationSource.models") }));
        navigate(modelTabRoute);
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
        icon={IconBox}
        feature={t("forms.integrationSource.models")}
        name={form.watch("type") || t("forms.integrationSource.modelUntitled")}
        description={t("forms.integrationSource.modelDetailDesc")}
      />

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-6">
          <SectionCard variant="violet">
            <SectionCard.Header
              icon={IconSettings}
              title={t("forms.integrationSource.modelDetail")}
              description={t("forms.integrationSource.modelDetailDesc")}
            />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="type"
                rules={{ required: t("forms.integrationSource.modelTypeRequired") }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.modelType")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="cq:Page" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.modelTypeDesc")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="subType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.modelSubType")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="cq:PageContent" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.modelSubTypeDesc")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="className"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("forms.integrationSource.modelClassName")}</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="com.example.ModelHandler" />
                    </FormControl>
                    <FormDescription>{t("forms.integrationSource.modelClassNameDesc")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="blue">
            <SectionCard.Header
              icon={IconTarget}
              title={t("forms.integrationSource.targetAttrs")}
              description={t("forms.integrationSource.targetAttrsDesc")}
            />
            <SectionCard.Content>
              <TargetAttributeList modelFieldPrefix="targetAttrs" />
            </SectionCard.Content>
          </SectionCard>

          <FormActions>
            <FormActions.Cancel onClick={() => navigate(modelTabRoute)} />
            <FormActions.Submit />
          </FormActions>
        </form>
      </Form>
    </>
  );
}
