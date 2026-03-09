import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { Badge } from "@/components/ui/badge";
import { TurSEInstanceService } from "@/services/se/se.service";
import { IconDatabase } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turSEInstanceService = new TurSEInstanceService();

export default function SEInstanceCoresPage() {
  const { id } = useParams() as { id: string };
  const [cores, setCores] = useState<string[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turSEInstanceService.getCores(id)
      .then(setCores)
      .catch(() => setError("Connection error or timeout while fetching Solr cores."));
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={cores} error={error} tryAgainUrl={`${ROUTES.SE_INSTANCE}/${id}/cores`}>
      {cores && cores.length > 0 ? (
        <>
          <SubPageHeader icon={IconDatabase} name="Cores" feature="Cores"
            description="Solr cores and collections available in this Search Engine." />
          <div className="px-6 pb-6">
            <div className="grid gap-2">
              {cores.map((core) => (
                <div key={core}
                  className="flex items-center gap-3 rounded-lg border px-4 py-3 bg-background">
                  <IconDatabase className="size-4 text-muted-foreground shrink-0" />
                  <span className="text-sm font-medium flex-1">{core}</span>
                  <Badge variant="secondary">core</Badge>
                </div>
              ))}
            </div>
          </div>
        </>
      ) : (
        <BlankSlate
          icon={IconDatabase}
          title="No cores or collections found."
          description="Make sure the Search Engine is running and accessible at the configured endpoint."
          buttonText="Refresh"
          urlNew={`${ROUTES.SE_INSTANCE}/${id}/cores`}
        />
      )}
    </LoadProvider>
  );
}
