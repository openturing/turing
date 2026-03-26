import { ROUTES } from "@/app/routes.const";
import { IntegrationSourceForm } from "@/components/integration/integration.source.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { GradientButton } from "@/components/ui/gradient-button";
import { useAemSourceService, useConnectorService } from "@/contexts/TuringServiceContext";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconGitCommit } from "@tabler/icons-react";
import axios from "axios";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";


export default function IntegrationInstanceSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId, tab = "general" } = useParams() as { id: string, sourceId: string, tab?: string };
  const [integrationAemSource, setIntegrationAemSource] = useState<TurIntegrationAemSource>({} as TurIntegrationAemSource);
  const [isNew, setIsNew] = useState<boolean>(true);
  const turIntegrationAemSourceService = useAemSourceService(id);
  const turIntegrationConnectorService = useConnectorService(id);
  const [open, setOpen] = useState(false);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  const { t } = useTranslation();

  useEffect(() => {
    if (sourceId !== "new") {
      turIntegrationAemSourceService.get(sourceId).then((source) => {
        setIntegrationAemSource(source);
        if (source.name) {
          setBreadcrumb([{ label: t("integration.sources.title"), href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source` }, { label: source.name }]);
        }
      });
      setIsNew(false);
    } else {
      setBreadcrumb([{ label: t("integration.sources.title"), href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source` }, { label: t("common.new") }]);
    }
  }, [id, sourceId, turIntegrationAemSourceService]);

  useSubPageBreadcrumb(breadcrumb);
  async function onDelete() {
    try {
      if (await turIntegrationAemSourceService.delete(integrationAemSource)) {
        toast.success(t("integration.sources.deleted", { name: integrationAemSource.name }));
        navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/source`);
      } else {
        toast.error(t("integration.sources.notDeleted", { name: integrationAemSource.name }));
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("integration.sources.notDeleted", { name: integrationAemSource.name }));
    }
    setOpen(false);
  }

  const isActionDisabled = isNew || !integrationAemSource.id;

  async function onIndexAll() {
    try {
      const result = await turIntegrationConnectorService.indexAll(integrationAemSource.name);
      if (result) {
        toast.success(t("integration.sources.indexingStarted", { name: integrationAemSource.name }));
      } else {
        toast.error(t("integration.sources.indexingFailed", { name: integrationAemSource.name }));
      }
    } catch (error) {
      console.error("Index all error", error);
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.warning(t("integration.sources.alreadyRunning", { name: integrationAemSource.name }));
        return;
      }
      toast.error(t("integration.sources.indexingFailed", { name: integrationAemSource.name }));
    }
  }

  async function onReindexAll() {
    try {
      const result = await turIntegrationConnectorService.reindexAll(integrationAemSource.name);
      if (result) {
        toast.success(t("integration.sources.reindexingStarted", { name: integrationAemSource.name }));
      } else {
        toast.error(t("integration.sources.reindexingFailed", { name: integrationAemSource.name }));
      }
    } catch (error) {
      console.error("Reindex all error", error);
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.warning(t("integration.sources.alreadyRunning", { name: integrationAemSource.name }));
        return;
      }
      toast.error(t("integration.sources.reindexingFailed", { name: integrationAemSource.name }));
    }
  }

  async function onDryScan() {
    try {
      const result = await turIntegrationConnectorService.auditSource(integrationAemSource.name);
      if (result) {
        toast.success(t("integration.sources.dryScanStarted", { name: integrationAemSource.name }));
      } else {
        toast.error(t("integration.sources.dryScanFailed", { name: integrationAemSource.name }));
      }
    } catch (error) {
      console.error("Dry scan error", error);
      toast.error(t("integration.sources.dryScanFailed", { name: integrationAemSource.name }));
    }
  }
  return (
    <>
      <SubPageHeader icon={IconGitCommit} name={t("integration.sources.title")}
        feature={t("integration.sources.title")}
        description={t("integration.sources.description")}
        onDelete={onDelete}
        open={open}
        setOpen={setOpen} />

      <div className="flex justify-end pb-4 px-6">
        <div className="flex flex-wrap items-center gap-2 rounded-md border bg-muted/50 px-3 py-2">
          <GradientButton
            type="button"
            variant="outline"
            onClick={onDryScan}
            disabled={isActionDisabled}
          >
            {t("integration.sources.dryScan")}
          </GradientButton>
          <GradientButton
            type="button"
            variant="outline"
            onClick={onReindexAll}
            disabled={isActionDisabled}
          >
            {t("integration.sources.reindexAll")}
          </GradientButton>
          <GradientButton
            type="button"
            variant="outline"
            onClick={onIndexAll}
            disabled={isActionDisabled}
          >
            {t("integration.sources.indexAll")}
          </GradientButton>
        </div>
      </div>
      <IntegrationSourceForm value={integrationAemSource} isNew={isNew} integrationId={id} sourceId={sourceId} tab={tab} onSourceUpdated={setIntegrationAemSource} />

    </>
  )
}
