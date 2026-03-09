import { ROUTES } from "@/app/routes.const";
import { IntegrationSourceForm } from "@/components/integration/integration.source.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { GradientButton } from "@/components/ui/gradient-button";
import { useAemSourceService, useConnectorService } from "@/contexts/TuringServiceContext";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { IconGitCommit } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";


export default function IntegrationInstanceSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId } = useParams() as { id: string, sourceId: string };
  const [integrationAemSource, setIntegrationAemSource] = useState<TurIntegrationAemSource>({} as TurIntegrationAemSource);
  const [isNew, setIsNew] = useState<boolean>(true);
  const turIntegrationAemSourceService = useAemSourceService(id);
  const turIntegrationConnectorService = useConnectorService(id);
  const [open, setOpen] = useState(false);
  useEffect(() => {
    if (sourceId !== "new") {
      turIntegrationAemSourceService.get(sourceId).then(setIntegrationAemSource);
      setIsNew(false);
    }
  }, [id, sourceId, turIntegrationAemSourceService]);
  async function onDelete() {
    try {
      if (await turIntegrationAemSourceService.delete(integrationAemSource)) {
        toast.success(`The ${integrationAemSource.name} AEM Source Instance was deleted`);
        navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/source`);
      } else {
        toast.error(`The ${integrationAemSource.name} AEM Source Instance was not deleted`);
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(`The ${integrationAemSource.name} AEM Source Instance was not deleted`);
    }
    setOpen(false);
  }

  const isActionDisabled = isNew || !integrationAemSource.id;

  async function onIndexAll() {
    try {
      const result = await turIntegrationConnectorService.indexAll(integrationAemSource.name);
      if (result) {
        toast.success(`Indexing started for ${integrationAemSource.name}`);
      } else {
        toast.error(`Failed to start indexing for ${integrationAemSource.name}`);
      }
    } catch (error) {
      console.error("Index all error", error);
      toast.error(`Failed to start indexing for ${integrationAemSource.name}`);
    }
  }

  async function onReindexAll() {
    try {
      const result = await turIntegrationConnectorService.reindexAll(integrationAemSource.name);
      if (result) {
        toast.success(`Reindexing started for ${integrationAemSource.name}`);
      } else {
        toast.error(`Failed to start reindexing for ${integrationAemSource.name}`);
      }
    } catch (error) {
      console.error("Reindex all error", error);
      toast.error(`Failed to start reindexing for ${integrationAemSource.name}`);
    }
  }
  return (
    <>
      <SubPageHeader icon={IconGitCommit} name="Sources"
        feature="Sources"
        description="Available AEM sources for indexing and configuration."
        onDelete={onDelete}
        open={open}
        setOpen={setOpen} />

      <div className="flex justify-end pb-4 px-6">
        <div className="flex flex-wrap items-center gap-2 rounded-md border bg-muted/50 px-3 py-2">
          <GradientButton
            type="button"
            variant="outline"
            onClick={onReindexAll}
            disabled={isActionDisabled}
          >
            Reindex all
          </GradientButton>
          <GradientButton
            type="button"
            variant="outline"
            onClick={onIndexAll}
            disabled={isActionDisabled}
          >
            Index all
          </GradientButton>
        </div>
      </div>
      <IntegrationSourceForm value={integrationAemSource} isNew={isNew} integrationId={id} />

    </>
  )
}
